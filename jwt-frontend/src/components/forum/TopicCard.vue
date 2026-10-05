<script setup>
import {useRouter} from "vue-router";
import {formatTime} from "@/utils/format";

const router = useRouter()

/** 点作者名进他的个人主页；stop 一下，别触发卡片本身「进帖子详情」 */
function openAuthor(uid) {
    if (uid != null) {
        router.push('/index/user/' + uid)
    }
}

/**
 * 帖子列表项卡片（展示型组件）。
 * 约定：只吃 props、只 emit 事件，不自己发请求 —— 这样它可以脱离页面单独预览。
 */
defineProps({
    data: {type: Object, required: true}
})
const emit = defineEmits(['click'])

/** 图片加载失败就藏起来，避免一堆破图（后端暂无图片上传，历史地址可能是死的） */
function hideImage(e) {
    e.target.style.display = 'none'
}
</script>

<template>
    <div class="topic-card" @click="emit('click', data)">
        <div class="card-title">
            <el-tag v-if="data.top === 1" type="danger" size="small" effect="dark">置顶</el-tag>
            <span class="title-text">{{ data.title }}</span>
            <el-tag v-if="data.locked === 1" type="info" size="small">已锁定</el-tag>
            <el-tag v-if="data.invisible === 1" type="warning" size="small">已隐藏</el-tag>
        </div>

        <div v-if="data.text" class="card-text">{{ data.text }}</div>

        <div v-if="data.images && data.images.length" class="card-images">
            <img v-for="(img, i) in data.images.slice(0, 3)" :key="i" :src="img" @error="hideImage"/>
        </div>

        <div class="card-meta">
            <span class="author" @click.stop="openAuthor(data.uid)">{{ data.username }}</span>
            <span class="sep">·</span>
            <span>{{ formatTime(data.time) }}</span>
            <span class="spacer"></span>
            <span class="stat">♡ {{ data.like }}</span>
            <span class="stat">★ {{ data.collect }}</span>
        </div>
    </div>
</template>

<style lang="less" scoped>
.topic-card {
    background: #fff;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
    padding: 14px 16px;
    margin-bottom: 10px;
    cursor: pointer;
    transition: box-shadow .15s, transform .15s;

    &:hover {
        box-shadow: 0 2px 12px rgba(0, 0, 0, .08);
        transform: translateY(-1px);
    }
}

.dark .topic-card {
    background: #1f2022;
}

.card-title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 16px;
    font-weight: 600;
    line-height: 24px;

    .title-text {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }
}

.card-text {
    margin-top: 6px;
    font-size: 13px;
    color: var(--el-text-color-regular);
    line-height: 20px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.card-images {
    margin-top: 8px;
    display: flex;
    gap: 6px;

    img {
        width: 88px;
        height: 66px;
        object-fit: cover;
        border-radius: 4px;
        background: var(--el-fill-color-light);
    }
}

.card-meta {
    margin-top: 10px;
    display: flex;
    align-items: center;
    font-size: 12px;
    color: var(--el-text-color-secondary);

    .author {
        color: var(--el-color-primary);

        &:hover {
            text-decoration: underline;
        }
    }

    .sep {
        margin: 0 6px;
    }

    .spacer {
        flex: 1;
    }

    .stat {
        margin-left: 12px;
    }
}
</style>
