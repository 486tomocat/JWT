<script setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref} from "vue";
import {useRoute, useRouter} from "vue-router";
import {ElMessage} from "element-plus";
import {ArrowLeft} from "@element-plus/icons-vue";
import Quill from "quill";
import "quill/dist/quill.snow.css";
import {useStore} from "@/store";
import {apiForumCreateTopic, apiForumTopic, apiForumTypes, apiForumUpdateTopic} from "@/net/api/forum";
import {apiUploadImage} from "@/net/api/file";
import TopicContent from "@/components/forum/TopicContent.vue";

/**
 * 发帖 / 编辑页（独立页面，不再是弹窗 —— 弹窗只有 720px，写长文很憋屈）。
 *
 * 路由：
 *   /index/forum/new            新建
 *   /index/forum/edit/:tid      编辑
 *
 * 左侧编辑、右侧实时预览。预览直接复用 TopicContent ——
 * 它就是「把 Delta 渲染成只读正文」的组件，所以预览和发布后的详情页天然一致，
 * 不会出现「预览好看、发出去变形」。
 */
const route = useRoute()
const router = useRouter()
const store = useStore()

const MAX_IMAGE_SIZE = 5 * 1024 * 1024

const isEdit = computed(() => route.name === 'forum-edit')
const tid = computed(() => Number(route.params.tid))

const toolbarOptions = [
    ['bold', 'italic', 'underline', 'strike'],
    [{'header': [2, 3, false]}],
    [{'list': 'ordered'}, {'list': 'bullet'}],
    ['blockquote', 'code-block'],
    ['image'],
    ['clean']
]

const holder = ref(null)
const title = ref('')
const type = ref(null)
const types = ref([])
const submitting = ref(false)
const previewContent = ref('')

let quill = null
let previewTimer = null

/** 占位提示的显隐：Quill 只在 text-change 里维护 ql-blank，setContents(..., 'silent') 不会触发 */
function syncBlank() {
    if (!quill) return
    quill.root.classList.toggle('ql-blank', quill.getText().trim().length === 0)
}

/** 图片按钮：选文件 → 上传到 MinIO → 插入 URL（不用 Quill 默认的 base64，那会把正文撑到几 MB） */
function imageHandler() {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = 'image/jpeg,image/png,image/gif,image/webp'
    input.onchange = () => {
        const file = input.files && input.files[0]
        if (!file) return
        if (file.size > MAX_IMAGE_SIZE) {
            return ElMessage.warning('图片不能超过 5MB')
        }
        apiUploadImage(file, url => {
            const range = quill.getSelection(true) || {index: quill.getLength()}
            quill.insertEmbed(range.index, 'image', url, 'user')
            quill.setSelection(range.index + 1, 0, 'user')
            ElMessage.success('图片已插入')
        }, message => ElMessage.warning(message))
    }
    input.click()
}

/** 预览刷新做 300ms 防抖：每敲一个字都重建一次只读 Quill 太浪费 */
function schedulePreview() {
    clearTimeout(previewTimer)
    previewTimer = setTimeout(() => {
        if (quill) {
            previewContent.value = JSON.stringify({ops: quill.getContents().ops})
        }
    }, 300)
}

function initQuill() {
    quill = new Quill(holder.value, {
        theme: 'snow',
        placeholder: '写点什么…',
        modules: {
            toolbar: {
                container: toolbarOptions,
                handlers: {image: imageHandler}
            }
        }
    })
    quill.on('text-change', () => {
        syncBlank()
        schedulePreview()
    })
    syncBlank()
}

function setContent(deltaStr) {
    if (!quill) return
    let ops = []
    if (deltaStr) {
        try {
            const parsed = JSON.parse(deltaStr)
            if (parsed && Array.isArray(parsed.ops)) ops = parsed.ops
        } catch (e) {
            // 内容坏了就当空正文，别让页面打不开
        }
    }
    quill.setContents({ops}, 'silent')
    syncBlank()
    schedulePreview()
}

function loadTypes() {
    apiForumTypes(data => {
        types.value = data || []
        if (!type.value && types.value.length) {
            type.value = types.value[0].id
        }
    })
}

function goBack() {
    if (window.history.state && window.history.state.back) {
        router.back()
    } else {
        router.push('/index/forum')
    }
}

function submit() {
    if (!title.value.trim()) return ElMessage.warning('请填写标题')
    if (!type.value) return ElMessage.warning('请选择分类')
    if (!quill || !quill.getText().trim()) return ElMessage.warning('正文不能为空')

    const content = {ops: quill.getContents().ops}
    submitting.value = true

    const done = msg => {
        submitting.value = false
        ElMessage.success(msg)
        store.forumRefreshTick++
        // 发布后回到广场（编辑则回详情）
        if (isEdit.value) {
            router.push('/index/forum/topic/' + tid.value)
        } else {
            router.push('/index/forum')
        }
    }
    const fail = message => {
        submitting.value = false
        ElMessage.warning(message)
    }

    if (isEdit.value) {
        apiForumUpdateTopic({id: tid.value, type: type.value, title: title.value, content}, () => done('修改成功'), fail)
    } else {
        apiForumCreateTopic({type: type.value, title: title.value, content}, () => done('发表成功'), fail)
    }
}

// 挂载：建编辑器、拉分类；编辑模式还要回填原内容
onMounted(() => {
    loadTypes()
    nextTick(() => {
        initQuill()
        if (isEdit.value) {
            apiForumTopic(tid.value, data => {
                title.value = data.title
                type.value = data.type
                setContent(data.content)
                quill.setSelection(quill.getLength(), 0)
            }, () => {
                ElMessage.warning('帖子不存在或已被屏蔽')
                router.push('/index/forum')
            })
        }
    })
})

onBeforeUnmount(() => {
    clearTimeout(previewTimer)
    quill = null
})
</script>

<template>
    <div class="editor-page">
        <div class="back-bar">
            <el-button type="primary" plain @click="goBack">
                <el-icon><ArrowLeft/></el-icon>
                <span style="margin-left: 5px">返回</span>
            </el-button>
            <span class="page-title">{{ isEdit ? '编辑帖子' : '发表帖子' }}</span>
        </div>

        <div class="layout">
            <!-- ============ 左：编辑 ============ -->
            <div class="edit-pane">
                <el-input v-model="title" maxlength="30" show-word-limit size="large"
                          placeholder="填写标题"/>

                <div class="editor-holder">
                    <div ref="holder"></div>
                </div>

                <div class="bottom-bar">
                    <el-select v-model="type" placeholder="请选择分类" style="width: 200px">
                        <el-option v-for="t in types" :key="t.id" :label="t.name" :value="t.id"/>
                    </el-select>
                    <div class="spacer"></div>
                    <el-button @click="goBack">取消</el-button>
                    <el-button type="primary" :loading="submitting" @click="submit">
                        {{ isEdit ? '保存修改' : '发布' }}
                    </el-button>
                </div>
            </div>

            <!-- ============ 右：实时预览 ============ -->
            <div class="preview-pane">
                <div class="preview-label">预览</div>
                <div class="preview-body">
                    <div class="preview-head">
                        <el-avatar :size="30" :src="store.user.avatar">
                            {{ (store.user.username || '我').charAt(0) }}
                        </el-avatar>
                        <span class="name">{{ store.user.username }}</span>
                    </div>
                    <h2 class="preview-title">{{ title || '标题' }}</h2>
                    <TopicContent :content="previewContent"/>
                </div>
            </div>
        </div>
    </div>
</template>

<style lang="less" scoped>
.editor-page {
    padding: 16px;
    min-height: 100%;
}

.back-bar {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-bottom: 12px;

    :deep(.el-button) {
        font-size: 14px;
        padding: 8px 16px;
        font-weight: 500;
    }

    .page-title {
        font-size: 16px;
        font-weight: 600;
    }
}

.layout {
    display: flex;
    gap: 16px;
    align-items: flex-start;
}

/* ---------- 左：编辑 ---------- */
.edit-pane {
    flex: 1;
    min-width: 0;
    background: #fff;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 8px;
    padding: 16px;
}

.dark .edit-pane {
    background: #1f2022;
}

.editor-holder {
    margin-top: 12px;

    :deep(.ql-container) {
        min-height: 420px;
        font-size: 15px;
        background: var(--el-fill-color-blank);
        color: var(--el-text-color-primary);
    }

    :deep(.ql-editor) {
        min-height: 420px;
        color: var(--el-text-color-primary);
        line-height: 1.8;
    }

    /* 与详情页同一套尺寸规则：编辑时看到多大，发布后就是多大 */
    :deep(.ql-editor img) {
        display: block;
        max-width: min(100%, 560px);
        height: auto;
        margin: 16px auto;
        border-radius: 6px;
    }

    :deep(.ql-editor.ql-blank::before) {
        color: var(--el-text-color-placeholder);
        font-style: normal;
    }

    :deep(.ql-toolbar) {
        background: var(--el-fill-color-light);
    }

    :deep(.ql-snow .ql-stroke) {
        stroke: var(--el-text-color-primary);
    }

    :deep(.ql-snow .ql-fill) {
        fill: var(--el-text-color-primary);
    }

    :deep(.ql-snow .ql-picker) {
        color: var(--el-text-color-primary);
    }
}

.bottom-bar {
    display: flex;
    align-items: center;
    margin-top: 14px;

    .spacer {
        flex: 1;
    }
}

/* ---------- 右：预览 ---------- */
.preview-pane {
    width: 340px;
    flex-shrink: 0;
    background: #fff;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 8px;
    padding: 14px;
    position: sticky;
    top: 16px;
    max-height: calc(100vh - 130px);
    overflow-y: auto;
}

.dark .preview-pane {
    background: #1f2022;
}

.preview-label {
    font-size: 13px;
    color: var(--el-text-color-secondary);
    padding-bottom: 10px;
    margin-bottom: 12px;
    border-bottom: 1px solid var(--el-border-color-lighter);
}

.preview-head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;

    .name {
        font-size: 13px;
        color: var(--el-color-primary);
    }
}

.preview-title {
    margin: 0 0 12px;
    font-size: 18px;
    line-height: 1.4;
    word-break: break-word;
}

/* 窄屏时预览折到下面，避免把编辑区挤没了 */
@media (max-width: 1200px) {
    .layout {
        flex-direction: column;
    }

    .preview-pane {
        width: 100%;
        position: static;
        max-height: none;
    }
}
</style>
