<script setup>
import {nextTick, reactive, ref, watch} from "vue";
import {ElMessage} from "element-plus";
import {Promotion, Refresh} from "@element-plus/icons-vue";
import {apiAiQuota} from "@/net/api/ai";
import {accessHeader} from "@/net";
import {useRouter} from "vue-router";
import {useStore} from "@/store";

/**
 * AI 智能体页面（独立路由 /index/ai）。
 *
 * 后续的 AI 功能都往这一页加 —— 所以结构上留了余地：
 * 消息列表是数据驱动的，滚动、加载态、多轮上下文都封装好了，
 * 想加"总结帖子""代码解释"之类，只要会往 messages 里推一条就行。
 *
 * 对话历史**只存在前端**：刷新页面即重新开始（后端不落库，省一张表）。
 * 传给后端的 history 只取最近 N 条 —— 传太多既费 token 也没必要。
 */
const store = useStore()
const router = useRouter()

const HISTORY_LIMIT = 10

/** 对话持久化的键与上限：存最近 50 条，避免 localStorage 撑爆（上限 5MB） */
const STORAGE_KEY = 'ai-chat-messages'
const MAX_STORED = 50

const GREETING = {
    role: 'assistant',
    content: '你好！我是校园论坛的 AI 智能体 👋\n可以问我技术问题，比如「什么是 JWT」「Redis 缓存怎么保证一致性」。'
}

/**
 * 从 localStorage 恢复对话。
 *
 * 为什么要持久化：本页面是路由组件，**离开路由时会被销毁**，
 * 内存里的 ref 自然就没了 —— 之前去一趟广场再回来，对话就清空了。
 * 存 localStorage 后，"离开再回来"和"刷新页面"两种情况都能保住。
 */
function loadMessages() {
    try {
        const raw = localStorage.getItem(STORAGE_KEY)
        if (raw) {
            const arr = JSON.parse(raw)
            if (Array.isArray(arr) && arr.length) {
                return arr
            }
        }
    } catch (e) {
        // 存坏了就当没有，别让页面打不开
    }
    return [GREETING]
}

/** 本週额度：{ limit, used, remaining }，单位 token */
const quota = ref(null)

/**
 * 是否启用 RAG（检索论坛帖子作为参考资料）。
 * 开启后：后端先向量检索相关帖子 → 拼进提示词 → 回答下方显示引用了哪几篇。
 * 默认开着 —— 这是「论坛 AI 助手」区别于通用聊天机器人的地方。
 */
const useRag = ref(true)

const messages = ref(loadMessages())

// 对话一变就落盘（只留最近 MAX_STORED 条）
watch(messages, () => {
    try {
        const kept = messages.value.slice(-MAX_STORED)
        localStorage.setItem(STORAGE_KEY, JSON.stringify(kept))
    } catch (e) {
        // 存储满了就算了，不影响本次会话
    }
}, {deep: true})
const input = ref('')
const loading = ref(false)
const listRef = ref(null)

/** 滚到底部（等 DOM 更新完再滚，否则新消息还没渲染出来） */
function scrollToBottom() {
    nextTick(() => {
        const el = listRef.value
        if (el) {
            el.scrollTop = el.scrollHeight
        }
    })
}

function send() {
    const text = input.value.trim()
    if (!text) return
    if (loading.value) return
    if (exhausted()) {
        return ElMessage.warning('本週 AI 额度已用完，下週一自动重置')
    }

    // 1. 先把用户消息推上去
    messages.value.push({role: 'user', content: text})
    input.value = ''
    loading.value = true
    scrollToBottom()

    // 2. 取最近的历史（不含刚推的这条，它作为 message 单独传）
    const history = messages.value
        .slice(0, -1)
        .slice(-HISTORY_LIMIT)
        .map(m => ({role: m.role, content: m.content}))

    sendStream(text, history)
}

/**
 * 流式发送（SSE）。
 *
 * ⚠️ 这里**不能用 EventSource** —— 它不支持自定义请求头，带不了 Authorization，
 * 而且只支持 GET（多轮对话的 history 放 query 里会超长）。
 * 所以用 fetch 读 response.body 的 ReadableStream，自己解析 SSE 帧。
 *
 * SSE 帧格式（我们只用 data 和 event 两个字段）：
 *     data: 文本片段\n\n
 *     event: error\ndata: 错误提示\n\n
 */
async function sendStream(text, history) {
    // 先占一个空气泡，后面逐块往里追加 —— 这样用户能立刻看到"AI 开始回答了"
    const reply = reactive({role: 'assistant', content: '', error: false, references: []})
    messages.value.push(reply)
    scrollToBottom()

    let reader = null
    try {
        const res = await fetch('/api/ai/chat/stream', {
            method: 'POST',
            headers: {'Content-Type': 'application/json', ...accessHeader()},
            body: JSON.stringify({message: text, history, rag: useRag.value})
        })

        if (!res.ok || !res.body) {
            reply.content = '⚠️ AI 暂时没有响应，请稍后再试'
            reply.error = true
            return
        }

        reader = res.body.getReader()
        const decoder = new TextDecoder('utf-8')
        let buffer = ''

        while (true) {
            const {done, value} = await reader.read()
            if (done) break
            buffer += decoder.decode(value, {stream: true})

            // SSE 以空行分帧；最后一段可能不完整，留在 buffer 里等下一块
            const frames = buffer.split('\n\n')
            buffer = frames.pop()
            for (const frame of frames) {
                handleFrame(frame, reply)
            }
            scrollToBottom()
        }

        // 收尾：把可能残留的最后一帧也处理掉
        if (buffer.trim()) {
            handleFrame(buffer, reply)
        }

        if (!reply.content) {
            reply.content = '⚠️ AI 没有返回内容，请重试'
            reply.error = true
        }
    } catch (e) {
        if (!reply.content) {
            reply.content = '⚠️ ' + (e.message || '连接中断')
            reply.error = true
        }
    } finally {
        loading.value = false
        scrollToBottom()
        // 流结束才扣额度，刷新一下顶部的剩余数
        loadQuota()
    }
}

/** 正在等待/接收回答且还没有任何内容的 AI 气泡 —— 显示三个跳动的点 */
function isThinking(m) {
    return m.role === 'assistant' && !m.content && !m.error && loading.value
}

/** 解析单个 SSE 帧：可能有 event: 行，data: 行可能有多条 */
function handleFrame(frame, reply) {
    let event = 'message'
    const dataLines = []
    for (const line of frame.split('\n')) {
        if (line.startsWith('event:')) {
            event = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
            dataLines.push(line.slice(5).replace(/^ /, ''))
        }
    }
    if (!dataLines.length) return

    const data = dataLines.join('\n')
    if (event === 'error') {
        reply.content += (reply.content ? '\n' : '') + '⚠️ ' + data
        reply.error = true
    } else if (event === 'refs') {
        // 引用来源：后端在正文开始前先发一条，前端存到气泡上
        try {
            reply.references = JSON.parse(data)
        } catch (e) {
            reply.references = []
        }
    } else {
        reply.content += data
    }
}

/** 点引用来源 → 跳到那篇帖子 */
function openTopic(id) {
    router.push('/index/forum/topic/' + id)
}

function loadQuota() {
    apiAiQuota(data => {
        quota.value = data
    }, () => {
        // 额度查不到不影响聊天本身，静默即可
    })
}

/** 数字加千分位，200000 看着太费劲 */
function fmt(n) {
    return n == null ? '-' : Number(n).toLocaleString('en-US')
}

/** 本週额度是否已用完 */
const exhausted = () => quota.value != null && quota.value.remaining <= 0

function clear() {
    messages.value = [{
        role: 'assistant',
        content: '对话已清空，重新开始吧 👋'
    }]
    try {
        localStorage.removeItem(STORAGE_KEY)
    } catch (e) {
        // 忽略
    }
    ElMessage.success('已清空对话')
}

// 进页面就拉一次额度
loadQuota()
</script>

<template>
    <div class="ai-page">
        <div class="ai-header">
            <div class="left">
                <span class="title">AI 智能体</span>
                <span class="sub">基于大模型的论坛问答助手</span>
            </div>
            <div class="right">
                <!-- RAG 开关：开启后先检索论坛帖子作为参考资料 -->
                <el-tooltip placement="bottom"
                            content="开启后，AI 会先检索论坛里相关的帖子，再基于它们回答，并在下方标出引用来源">
                    <el-switch v-model="useRag" active-text="基于论坛内容" size="small"/>
                </el-tooltip>

                <!-- 额度按 token 计、每週重置（后端 forum.ai.weekly-token-limit） -->
                <el-tooltip v-if="quota" placement="bottom"
                            :content="`本週额度 ${fmt(quota.limit)} tokens（每週一重置）`">
                    <span class="quota" :class="{danger: quota.remaining <= 0, warn: quota.remaining > 0 && quota.remaining < quota.limit * 0.2}">
                        本週剩余 {{ fmt(quota.remaining) }} / {{ fmt(quota.limit) }}
                    </span>
                </el-tooltip>
                <el-button :icon="Refresh" text @click="clear">清空对话</el-button>
            </div>
        </div>

        <div class="ai-body" ref="listRef">
            <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
                <!-- 用户消息带真实头像（没上传过头像时 el-avatar 会显示下面的首字兜底） -->
                <el-avatar v-if="m.role === 'user'" :size="32" :src="store.user.avatar"
                           class="user-avatar">
                    {{ (store.user.username || '我').charAt(0) }}
                </el-avatar>
                <div v-else class="avatar">AI</div>

                <div class="msg-body">
                    <div v-if="m.role === 'user'" class="name">{{ store.user.username || '我' }}</div>
                    <!-- 流式回答时气泡是空的，先显示三个点；有内容后就替换成文字 -->
                    <div class="bubble" :class="{error: m.error, thinking: isThinking(m)}">
                        <template v-if="m.content">{{ m.content }}</template>
                        <template v-else>
                            <span class="dot"/><span class="dot"/><span class="dot"/>
                        </template>
                    </div>

                    <!-- 引用来源：让用户知道答案有依据、能点进原帖核对 -->
                    <div v-if="m.references && m.references.length" class="refs">
                        <span class="refs-label">📎 参考了 {{ m.references.length }} 篇帖子：</span>
                        <a v-for="r in m.references" :key="r.id" class="ref-item"
                           @click="openTopic(r.id)">{{ r.title }}</a>
                    </div>
                </div>
            </div>
        </div>

        <div class="ai-input">
            <el-input v-model="input" type="textarea" :rows="2" resize="none"
                      maxlength="2000" :disabled="exhausted()"
                      :placeholder="exhausted() ? '本週额度已用完，下週一自动重置' : '输入你的问题，Enter 发送，Shift+Enter 换行'"
                      @keydown.enter.exact.prevent="send"/>
            <el-button type="primary" :icon="Promotion" :loading="loading"
                       :disabled="!input.trim() || exhausted()" @click="send">发送</el-button>
        </div>
    </div>
</template>

<style lang="less" scoped>
.ai-page {
    display: flex;
    flex-direction: column;
    height: calc(100vh - 55px);
    padding: 16px;
    box-sizing: border-box;
}

.ai-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 12px;
    margin-bottom: 12px;
    border-bottom: 1px solid var(--el-border-color-lighter);

    .title {
        font-size: 17px;
        font-weight: 600;
    }

    .sub {
        font-size: 12px;
        color: var(--el-text-color-secondary);
        margin-left: 8px;
    }
}

.right {
    display: flex;
    align-items: center;
    gap: 6px;
}

.quota {
    font-size: 12px;
    padding: 3px 10px;
    border-radius: 10px;
    background: var(--el-fill-color-light);
    color: var(--el-text-color-secondary);

    &.warn {
        background: var(--el-color-warning-light-9);
        color: var(--el-color-warning);
    }

    &.danger {
        background: var(--el-color-danger-light-9);
        color: var(--el-color-danger);
    }
}

.ai-body {
    flex: 1;
    overflow-y: auto;
    padding: 4px 6px 12px;

    /* 滚动条细一点，别抢视线 */
    &::-webkit-scrollbar {
        width: 6px;
    }

    &::-webkit-scrollbar-thumb {
        background: var(--el-border-color);
        border-radius: 3px;
    }
}

.msg {
    display: flex;
    gap: 10px;
    margin-bottom: 18px;

    &.user {
        flex-direction: row-reverse;

        .bubble {
            background: var(--el-color-primary);
            color: #fff;
        }

        /* 用户侧的名字与气泡都靠右 */
        .msg-body {
            align-items: flex-end;
        }

        .name {
            text-align: right;
        }
    }

    &.assistant .avatar {
        background: #23c6c8;
    }
}

/* 气泡外面再包一层：为了在气泡上方放「用户名」 */
.msg-body {
    display: flex;
    flex-direction: column;
    min-width: 0;
    max-width: 70%;
}

.name {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin: 0 2px 4px;
    line-height: 1;
}

.user-avatar {
    flex-shrink: 0;
    background: var(--el-color-primary);
    color: #fff;
    font-size: 13px;
}

.avatar {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    color: #fff;
    background: var(--el-color-primary);
}

.bubble {
    padding: 10px 14px;
    border-radius: 8px;
    font-size: 14px;
    line-height: 1.75;
    white-space: pre-wrap;
    word-break: break-word;
    background: var(--el-fill-color-light);
    color: var(--el-text-color-primary);

    &.error {
        background: var(--el-color-warning-light-9);
        color: var(--el-color-warning);
    }

    &.thinking {
        display: flex;
        align-items: center;
        gap: 5px;
        padding: 14px;
    }

    .dot {
        width: 6px;
        height: 6px;
        border-radius: 50%;
        background: var(--el-text-color-placeholder);
        animation: blink 1.4s infinite both;

        &:nth-child(2) { animation-delay: .2s; }
        &:nth-child(3) { animation-delay: .4s; }
    }
}

.refs {
    margin-top: 8px;
    padding: 8px 12px;
    border-radius: 6px;
    background: var(--el-fill-color-lighter);
    font-size: 12px;
    line-height: 1.9;

    .refs-label {
        color: var(--el-text-color-secondary);
        margin-right: 4px;
    }

    .ref-item {
        display: inline-block;
        margin-right: 10px;
        color: var(--el-color-primary);
        cursor: pointer;
        text-decoration: none;

        &:hover {
            text-decoration: underline;
        }

        &::before {
            content: '· ';
        }
    }
}

@keyframes blink {
    0%, 80%, 100% { opacity: .3; transform: translateY(0); }
    40% { opacity: 1; transform: translateY(-3px); }
}

.ai-input {
    display: flex;
    gap: 10px;
    align-items: flex-end;
    padding-top: 12px;
    border-top: 1px solid var(--el-border-color-lighter);

    :deep(.el-textarea__inner) {
        font-family: inherit;
    }

    .el-button {
        height: 54px;
        padding: 0 20px;
    }
}
</style>
