<script setup>
import {onMounted, ref, watch} from "vue";
import {useRouter} from "vue-router";
import {apiForumCollects} from "@/net/api/forum";
import TopicCard from "@/components/forum/TopicCard.vue";
import {useStore} from "@/store";

/**
 * 我的收藏。
 * 后端 /collects 返回的就是 TopicPreviewVO（和广场列表同一种结构），
 * 所以这里可以直接复用 TopicCard。
 */
const store = useStore()
const router = useRouter()

const list = ref([])
const loading = ref(false)

function load() {
    loading.value = true
    apiForumCollects(data => {
        list.value = data || []
        loading.value = false
    }, () => {
        loading.value = false
    })
}

function open(item) {
    router.push('/index/forum/topic/' + item.id)
}

// 点赞/收藏状态变化后自动刷新
watch(() => store.forumRefreshTick, load)
onMounted(load)
</script>

<template>
    <div class="my-collects" v-loading="loading">
        <h3 class="page-title">我的收藏</h3>

        <el-empty v-if="!list.length && !loading" description="还没有收藏任何帖子"/>

        <TopicCard v-for="item in list" :key="item.id" :data="item" @click="open"/>
    </div>
</template>

<style lang="less" scoped>
.my-collects {
    padding: 16px;
    min-height: 100%;
}

.page-title {
    margin: 0 0 14px;
    font-size: 18px;
}
</style>
