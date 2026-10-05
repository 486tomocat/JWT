<script setup>
import {onBeforeUnmount, onMounted, ref} from "vue";
import {ElMessage} from "element-plus";
import Quill from "quill";
import "quill/dist/quill.snow.css";
import {apiForumAddComment} from "@/net/api/forum";

/**
 * 评论输入框。
 *
 * 用极简工具栏（加粗/斜体/列表/引用）：评论是短文本，塞满工具栏又占地又分心。
 *
 * ⚠️ 提交时要把 Delta 转成**字符串** —— 后端 AddCommentVO.content 是 String，
 * 而发帖接口收的是对象。两个接口在这点上不一致，很容易踩。
 */
const props = defineProps({
    tid: {type: Number, required: true}
})
const emit = defineEmits(['submitted'])

const holder = ref(null)
const submitting = ref(false)
let quill = null

const toolbarOptions = [
    ['bold', 'italic'],
    [{'list': 'ordered'}, {'list': 'bullet'}],
    ['blockquote']
]

/**
 * 同步占位提示的显隐。
 *
 * Quill 只在 text-change 事件里 toggle ql-blank，而 setContents(..., 'silent')
 * 不触发该事件 —— 内容变了、class 没变，占位文字就会残留在已有内容上。
 * 这里统一手动维护，只用公开 API，不碰 Quill 内部方法。
 */
function syncBlank() {
    if (!quill) return
    quill.root.classList.toggle('ql-blank', quill.getText().trim().length === 0)
}

onMounted(() => {
    quill = new Quill(holder.value, {
        theme: 'snow',
        placeholder: '说点什么…',
        modules: {toolbar: toolbarOptions}
    })
    quill.on('text-change', syncBlank)
    syncBlank()
})

function submit() {
    if (!quill || !quill.getText().trim()) return ElMessage.warning('评论不能为空')

    submitting.value = true
    // quote = -1 表示直接评论帖子（不是回复某条评论）
    const content = JSON.stringify({ops: quill.getContents().ops})

    apiForumAddComment({tid: props.tid, content, quote: -1}, () => {
        submitting.value = false
        quill.setContents({ops: []}, 'silent')
        syncBlank()
        ElMessage.success('评论成功')
        emit('submitted')
    }, message => {
        submitting.value = false
        ElMessage.warning(message)
    })
}

onBeforeUnmount(() => {
    quill = null
})
</script>

<template>
    <div class="comment-editor">
        <div ref="holder"></div>
        <div class="actions">
            <el-button type="primary" size="small" :loading="submitting" @click="submit">发表评论</el-button>
        </div>
    </div>
</template>

<style lang="less" scoped>
.comment-editor {
    border: 1px solid var(--el-border-color);
    border-radius: 6px;
    overflow: hidden;
    margin-bottom: 16px;
    background: var(--el-fill-color-blank);

    :deep(.ql-container) {
        border: none;
        font-size: 14px;
        background: var(--el-fill-color-blank);
        color: var(--el-text-color-primary);
    }

    :deep(.ql-editor) {
        min-height: 70px;
        color: var(--el-text-color-primary);
    }

    /* 占位文字跟随主题，别用 Quill 自带的 rgba(0,0,0,.6) */
    :deep(.ql-editor.ql-blank::before) {
        color: var(--el-text-color-placeholder);
        font-style: normal;
    }

    :deep(.ql-toolbar) {
        border: none;
        border-bottom: 1px solid var(--el-border-color-lighter);
        background: var(--el-fill-color-light);
    }

    /* 工具栏图标是 SVG，颜色由 CSS 控制，暗色下必须覆盖 */
    :deep(.ql-snow .ql-stroke) {
        stroke: var(--el-text-color-primary);
    }

    :deep(.ql-snow .ql-fill) {
        fill: var(--el-text-color-primary);
    }

    :deep(.ql-snow .ql-picker) {
        color: var(--el-text-color-primary);
    }

    .actions {
        padding: 8px;
        text-align: right;
        border-top: 1px solid var(--el-border-color-lighter);
    }
}
</style>
