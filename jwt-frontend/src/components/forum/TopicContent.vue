<script setup>
import {onBeforeUnmount, onMounted, ref, watch} from "vue";
import Quill from "quill";
import "quill/dist/quill.snow.css";

/**
 * 富文本只读渲染。
 *
 * 为什么用 Quill 的只读实例，而不是把 Delta 转成 HTML 再 v-html：
 * Quill 按 Delta 的结构白名单渲染，用户写不进任意标签，天然没有 XSS 面；
 * v-html 则要先自己净化，漏一处就是漏洞。
 *
 * 样式交由父级容器决定（本组件只清掉 Quill 自带的边框与内边距），
 * 这样同一个组件既能用在详情页的大卡片里，也能用在评论的小块里。
 */
const props = defineProps({
    content: {type: String, default: ''}    // 服务端返回的 Delta JSON 字符串
})

const holder = ref(null)
let quill = null

function parseOps(content) {
    if (!content) return null
    try {
        const delta = typeof content === 'string' ? JSON.parse(content) : content
        return delta && Array.isArray(delta.ops) ? delta.ops : null
    } catch (e) {
        // 内容坏了也不该让整个页面崩掉，降级为空内容
        return null
    }
}

function render() {
    if (!quill) return
    const ops = parseOps(props.content)
    quill.setContents(ops ? {ops} : {ops: []}, 'silent')
}

onMounted(() => {
    quill = new Quill(holder.value, {
        readOnly: true,
        modules: {toolbar: false},
        theme: 'snow'
    })
    render()
})

watch(() => props.content, render)

onBeforeUnmount(() => {
    quill = null
})
</script>

<template>
    <div class="topic-content">
        <div ref="holder"></div>
        <div v-if="!content" class="empty">（没有正文）</div>
    </div>
</template>

<style lang="less" scoped>
/*
 * 正文排版规范。
 *
 * 这一套同时作用于「详情页正文」和「发帖页的预览面板」——
 * 两处共用本组件，所以改一次两边一起生效，不会出现「预览和实际不一样」。
 *
 * 最关键的一条是段落间距：Quill 默认给 <p> 的 margin 是 0，
 * 于是连着写几段话会全挤在一起，读起来非常累。
 */
.topic-content {
    :deep(.ql-container) {
        border: none;
        font-family: inherit;
        height: auto;
    }

    :deep(.ql-editor) {
        padding: 0;
        font-size: 15px;
        line-height: 1.8;
        color: var(--el-text-color-primary);
        height: auto;
        overflow-y: visible;
        word-break: break-word;
    }

    /* 只读时不需要 Quill 的聚焦轮廓 */
    :deep(.ql-editor:focus) {
        outline: none;
    }

    /* ---- 段落 ---- */
    :deep(.ql-editor p) {
        margin: 0 0 14px;
    }

    :deep(.ql-editor p:last-child) {
        margin-bottom: 0;
    }

    /* ---- 标题 ---- */
    :deep(.ql-editor h1),
    :deep(.ql-editor h2),
    :deep(.ql-editor h3) {
        font-weight: 600;
        line-height: 1.4;
        margin: 22px 0 12px;
    }

    :deep(.ql-editor h1) { font-size: 22px; }
    :deep(.ql-editor h2) { font-size: 19px; }
    :deep(.ql-editor h3) { font-size: 17px; }

    /* ---- 列表：缩进 + 项间距，默认太挤 ---- */
    :deep(.ql-editor ul),
    :deep(.ql-editor ol) {
        margin: 0 0 14px;
        padding-left: 24px;
    }

    :deep(.ql-editor li) {
        margin-bottom: 6px;
        line-height: 1.8;
    }

    :deep(.ql-editor li:last-child) {
        margin-bottom: 0;
    }

    /* ---- 引用：左侧色条 + 浅底，和正文区分开 ---- */
    :deep(.ql-editor blockquote) {
        margin: 14px 0;
        padding: 8px 14px;
        border-left: 3px solid var(--el-color-primary);
        background: var(--el-fill-color-light);
        border-radius: 0 4px 4px 0;
        color: var(--el-text-color-regular);
    }

    :deep(.ql-editor blockquote p) {
        margin: 0;
    }

    /* ---- 代码块 ---- */
    :deep(.ql-editor pre) {
        margin: 14px 0;
        padding: 12px 14px;
        background: var(--el-fill-color-light);
        border-radius: 6px;
        font-size: 13px;
        line-height: 1.6;
        white-space: pre-wrap;
        overflow-x: auto;
    }

    /* ---- 链接 ---- */
    :deep(.ql-editor a) {
        color: var(--el-color-primary);
        text-decoration: none;
    }

    :deep(.ql-editor a:hover) {
        text-decoration: underline;
    }

    /*
     * ---- 图片 ----
     * Quill 不给 img 任何限制，图片会按原始尺寸把整栏撑开（大屏下中栏能到 1200px+）。
     * 限制到 560px 并居中：这是刻意的，接近常见阅读列的宽度。
     */
    :deep(.ql-editor img) {
        display: block;
        max-width: min(100%, 560px);
        height: auto;
        margin: 16px auto;
        border-radius: 6px;
    }
}

.empty {
    font-size: 13px;
    color: var(--el-text-color-secondary);
}
</style>
