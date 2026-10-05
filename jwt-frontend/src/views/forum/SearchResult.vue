<script setup>
import {onMounted, ref, watch} from "vue";
import {useRoute, useRouter} from "vue-router";
import {ArrowLeft} from "@element-plus/icons-vue";
import {apiForumSearch} from "@/net/api/forum";
import TopicCard from "@/components/forum/TopicCard.vue";

/**
 * 搜索结果页。
 *
 * 与广场的区别：这个接口带 total（来自 ES 的命中数），所以能用 el-pagination 显示总页数；
 * 广场那个接口没有 total，只能「上一页/下一页」。
 */
const route = useRoute()
const router = useRouter()

const list = ref([])
const total = ref(0)
const page = ref(0)
const loading = ref(false)

const keyword = () => (route.query.keyword || '').toString()

function load() {
    const kw = keyword()
    if (!kw) {
        list.value = []
        total.value = 0
        return
    }
    loading.value = true
    apiForumSearch(kw, page.value, data => {
        list.value = data.list || []
        total.value = data.total || 0
        loading.value = false
    }, () => {
        loading.value = false
    })
}

function changePage(p) {
    // el-pagination 是 1 基，后端接口是 0 基
    page.value = p - 1
    load()
}

function openTopic(topic) {
    router.push('/index/forum/topic/' + topic.id)
}

/**
 * 返回上一页。
 * 与帖子详情页同样的处理：直接打开搜索链接时没有站内上一页，
 * 这时退回广场，避免 router.back() 把人带出站点。
 */
function goBack() {
    if (window.history.state && window.history.state.back) {
        router.back()
    } else {
        router.push('/index/forum')
    }
}

// 换关键词时从头开始，并滚动回顶部
watch(() => route.query.keyword, () => {
    page.value = 0
    load()
})

onMounted(load)
</script>

<template>
    <div class="search-result" v-loading="loading">
        <div class="back-bar">
            <el-button type="primary" plain @click="goBack">
                <el-icon><ArrowLeft/></el-icon>
                <span style="margin-left: 5px">返回</span>
            </el-button>
        </div>

        <div class="head">
            <span class="label">搜索</span>
            <span class="keyword">{{ keyword() }}</span>
            <span v-if="total" class="count">共 {{ total }} 条结果</span>
        </div>

        <el-empty v-if="!loading && !list.length"
                  :description="keyword() ? '没有找到相关帖子' : '请输入关键词搜索'"/>

        <TopicCard v-for="item in list" :key="item.id" :data="item" @click="openTopic"/>

        <el-pagination v-if="total > 10" class="pager" background
                       layout="prev, pager, next"
                       :total="total" :current-page="page + 1" :page-size="10"
                       @current-change="changePage"/>
    </div>
</template>

<style lang="less" scoped>
.search-result {
    padding: 16px;
    min-height: 100%;
}

.back-bar {
    margin-bottom: 12px;

    :deep(.el-button) {
        font-size: 14px;
        padding: 8px 16px;
        font-weight: 500;
    }
}

.head {
    display: flex;
    align-items: baseline;
    gap: 8px;
    margin-bottom: 14px;

    .label {
        font-size: 13px;
        color: var(--el-text-color-secondary);
    }

    .keyword {
        font-size: 18px;
        font-weight: 600;
    }

    .count {
        font-size: 12px;
        color: var(--el-text-color-secondary);
    }
}

.pager {
    margin-top: 16px;
    justify-content: center;
}
</style>
