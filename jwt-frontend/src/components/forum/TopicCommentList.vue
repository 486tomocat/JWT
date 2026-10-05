<script setup>
import {formatTime} from "@/utils/format";
import TopicContent from "@/components/forum/TopicContent.vue";

/**
 * 评论列表（展示型组件）：只吃 props，不发请求。
 * 评论正文同样走 TopicContent 的只读渲染（deltaToText 只作为兜底工具保留在 utils 里）。
 */
const props = defineProps({
    comments: {type: Array, default: () => []},
    /** 当前登录用户 id：用来判断哪条评论能显示「删除」 */
    currentUserId: {type: Number, default: null}
})
const emit = defineEmits(['delete'])

function isMine(comment) {
    return props.currentUserId != null && comment.user && comment.user.id === props.currentUserId
}
</script>

<template>
    <div class="comment-list">
        <el-empty v-if="!comments.length" description="还没有评论" :image-size="60"/>

        <div v-for="c in comments" :key="c.id" class="comment">
            <el-avatar :size="30" :src="c.user && c.user.avatar"/>
            <div class="body">
                <div class="head">
                    <span class="author">{{ c.user && c.user.username }}</span>
                    <span class="time">{{ formatTime(c.time) }}</span>
                    <el-button v-if="isMine(c)" link type="danger" size="small"
                               class="del" @click="emit('delete', c)">删除</el-button>
                </div>
                <div v-if="c.quote" class="quote">回复：{{ c.quote }}</div>
                <div class="text">
                    <TopicContent :content="c.content"/>
                </div>
            </div>
        </div>
    </div>
</template>

<style lang="less" scoped>
.comment {
    display: flex;
    gap: 10px;
    background: #fff;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
    padding: 12px 14px;
    margin-bottom: 8px;
}

.dark .comment {
    background: #1f2022;
}

.body {
    flex: 1;
    min-width: 0;
}

.head {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-bottom: 5px;

    .author {
        color: var(--el-color-primary);
        font-size: 13px;
    }

    /* 删除按钮推到最右边 */
    .del {
        margin-left: auto;
    }
}

.quote {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    border-left: 3px solid var(--el-border-color);
    padding-left: 8px;
    margin-bottom: 6px;
    white-space: pre-wrap;
}

.text {
    font-size: 14px;
    word-break: break-word;
}
</style>
