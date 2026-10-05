<script setup>
import {onMounted, ref} from "vue";
import {useRouter} from "vue-router";
import {Promotion} from "@element-plus/icons-vue";
import {apiForumTopicList, apiForumTopTopics, apiForumTypes} from "@/net/api/forum";
import TopicCard from "@/components/forum/TopicCard.vue";

const router = useRouter()

const types = ref([])
const activeType = ref(0)     // 0 = 全部
const tops = ref([])
const list = ref([])
const page = ref(0)           // 对外 0 基；后端收到后 +1 转成 MyBatis-Plus 的 1 基页码
const loading = ref(false)

const PAGE_SIZE = 10

function loadTypes() {
    apiForumTypes(data => types.value = data || [])
}

function loadTops() {
    apiForumTopTopics(data => tops.value = data || [])
}

function loadList() {
    loading.value = true
    apiForumTopicList(page.value, activeType.value, data => {
        // 后端列表接口永远返回数组（空页是 []），这里不需要判 null
        list.value = data || []
        loading.value = false
    }, () => {
        loading.value = false
    })
}

function changeType(type) {
    if (activeType.value === type) return
    activeType.value = type
    page.value = 0
    loadList()
}

/**
 * 翻页。
 * 后端的分页接口只返回当页数组、没有 total，所以这里用「本页是否满页」
 * 判断有没有下一页，而不是 el-pagination（它需要 total 才能算总页数）。
 */
function turnPage(delta) {
    if (delta < 0 && page.value === 0) return
    if (delta > 0 && list.value.length < PAGE_SIZE) return
    page.value += delta
    loadList()
}

function openTopic(topic) {
    router.push(`/index/forum/topic/${topic.id}`)
}

onMounted(() => {
    loadTypes()
    loadTops()
    loadList()
})
</script>

<template>
    <div class="topic-list">
        <div class="type-bar">
            <span class="type-item" :class="{active: activeType === 0}" @click="changeType(0)">全部</span>
            <span v-for="t in types" :key="t.id" class="type-item"
                  :class="{active: activeType === t.id}" @click="changeType(t.id)">{{ t.name }}</span>
        </div>

        <div v-if="tops.length" class="top-block">
            <div class="block-label">置顶</div>
            <div v-for="t in tops" :key="t.id" class="top-item" @click="openTopic(t)">
                <el-icon><Promotion/></el-icon>
                <span class="top-title">{{ t.title }}</span>
            </div>
        </div>

        <div v-loading="loading" class="list-block">
            <TopicCard v-for="item in list" :key="item.id" :data="item" @click="openTopic"/>
            <el-empty v-if="!loading && !list.length" description="这个分类还没有帖子"/>
        </div>

        <div class="pager">
            <el-button :disabled="page === 0" @click="turnPage(-1)">上一页</el-button>
            <span class="page-info">第 {{ page + 1 }} 页</span>
            <el-button :disabled="list.length < PAGE_SIZE" @click="turnPage(1)">下一页</el-button>
        </div>
    </div>
</template>

<style lang="less" scoped>
.topic-list {
    padding: 16px;
}

.type-bar {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 14px;
}

.type-item {
    padding: 4px 14px;
    font-size: 13px;
    border-radius: 14px;
    border: 1px solid var(--el-border-color);
    cursor: pointer;
    transition: all .15s;
    user-select: none;

    &:hover {
        border-color: var(--el-color-primary);
        color: var(--el-color-primary);
    }

    &.active {
        background: var(--el-color-primary);
        border-color: var(--el-color-primary);
        color: #fff;
    }
}

.top-block {
    background: #fff;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
    padding: 10px 16px;
    margin-bottom: 12px;
}

.dark .top-block {
    background: #1f2022;
}

.block-label {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-bottom: 6px;
}

.top-item {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 14px;
    line-height: 28px;
    cursor: pointer;
    color: var(--el-color-primary);

    .top-title {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    &:hover {
        text-decoration: underline;
    }
}

.pager {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 14px;
    padding: 10px 0 20px;

    .page-info {
        font-size: 13px;
        color: var(--el-text-color-secondary);
    }
}
</style>
