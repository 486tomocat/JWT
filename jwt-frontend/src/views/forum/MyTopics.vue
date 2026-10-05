<script setup>
import {onMounted, ref, watch} from "vue";
import {useRouter} from "vue-router";
import {ElMessage, ElMessageBox} from "element-plus";
import {apiForumDeleteTopic, apiForumUserTopic} from "@/net/api/forum";
import TopicCard from "@/components/forum/TopicCard.vue";
import {useStore} from "@/store";

/**
 * 我的帖子。
 *
 * 后端 /user-topic 现在返回 TopicPreviewVO（与广场列表同结构），
 * 所以这里直接复用 TopicCard；「删除」作为卡片右上角的独立按钮叠在上面。
 */
const store = useStore()
const router = useRouter()

const list = ref([])
const loading = ref(false)

function load() {
    loading.value = true
    apiForumUserTopic(data => {
        list.value = data || []
        loading.value = false
    }, () => {
        loading.value = false
    })
}

function open(item) {
    router.push('/index/forum/topic/' + item.id)
}

function remove(item) {
    ElMessageBox.confirm(`确定删除「${item.title}」吗？`, '提示', {type: 'warning'})
        .then(() => apiForumDeleteTopic(item.id, () => {
            ElMessage.success('已删除')
            load()
        }))
        .catch(() => {
            // 用户点了取消
        })
}

// 发帖 / 编辑成功后自动刷新
watch(() => store.forumRefreshTick, load)
onMounted(load)
</script>

<template>
    <div class="my-topics" v-loading="loading">
        <h3 class="page-title">我的帖子</h3>

        <el-empty v-if="!list.length && !loading" description="还没有发过帖子"/>

        <div v-for="item in list" :key="item.id" class="row">
            <TopicCard :data="item" @click="open"/>
            <el-button class="del" link type="danger" @click.stop="remove(item)">删除</el-button>
        </div>
    </div>
</template>

<style lang="less" scoped>
.my-topics {
    padding: 16px;
    min-height: 100%;
}

.page-title {
    margin: 0 0 14px;
    font-size: 18px;
}

.row {
    position: relative;

    /* 删除按钮叠在卡片右上角：卡片本身整块可点，所以要 stop 冒泡 */
    .del {
        position: absolute;
        top: 12px;
        right: 14px;
    }
}
</style>
