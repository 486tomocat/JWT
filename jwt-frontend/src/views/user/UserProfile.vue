<script setup>
import {computed, onMounted, ref, watch} from "vue";
import {useRoute, useRouter} from "vue-router";
import {apiUserProfile, apiUserTopics} from "@/net/api/user";
import {formatTime} from "@/utils/format";
import TopicCard from "@/components/forum/TopicCard.vue";

/**
 * 个人主页。
 *
 * 资料是否可见由**后端**决定：对方设为「不公开」的字段，接口里返回的就是 null，
 * 这里只负责把 null 显示成「未公开」。
 * 看自己的主页永远全部可见，此时 null 就是「没填」。
 */
const route = useRoute()
const router = useRouter()

const profile = ref(null)
const topics = ref([])
const notFound = ref(false)
const loading = ref(true)

const uid = computed(() => Number(route.params.id))

/** 资料行；值为空时按「未公开 / 未填写」显示，不留白 */
const detailRows = computed(() => {
    const p = profile.value
    if (!p) return []
    return [
        {label: '性别', value: p.gender === 0 ? '男' : p.gender === 1 ? '女' : null},
        {label: 'QQ', value: p.qq},
        {label: '微信', value: p.wx},
        {label: '邮箱', value: p.email},
        {label: '手机', value: p.phone},
    ]
})

const emptyText = computed(() => profile.value?.self ? '未填写' : '未公开')

function load() {
    loading.value = true
    notFound.value = false

    apiUserProfile(uid.value, data => {
        profile.value = data
        loading.value = false
    }, () => {
        notFound.value = true
        loading.value = false
    })

    apiUserTopics(uid.value, data => topics.value = data || [])
}

function openTopic(topic) {
    router.push('/index/forum/topic/' + topic.id)
}

// 从一个人的主页跳去另一个人的主页时，组件复用了但 id 变了，要重新拉
watch(uid, load)
onMounted(load)
</script>

<template>
    <div class="profile-page" v-loading="loading">
        <el-empty v-if="notFound" description="用户不存在"/>

        <template v-else-if="profile">
            <div class="header">
                <el-avatar :size="72" :src="profile.avatar"/>
                <div class="info">
                    <div class="name-row">
                        <span class="name">{{ profile.username }}</span>
                        <el-tag v-if="profile.role === 'admin'" type="danger" size="small">管理员</el-tag>
                        <el-tag v-if="profile.self" type="info" size="small">这是你自己</el-tag>
                    </div>
                    <div class="desc">{{ profile.desc || '这个人很懒，什么都没写' }}</div>
                    <div class="meta">注册于 {{ formatTime(profile.registerTime) }}</div>
                </div>
            </div>

            <div class="card">
                <div class="card-title">资料</div>
                <div class="rows">
                    <div v-for="row in detailRows" :key="row.label" class="row">
                        <span class="label">{{ row.label }}</span>
                        <span v-if="row.value" class="value">{{ row.value }}</span>
                        <span v-else class="hidden">{{ emptyText }}</span>
                    </div>
                </div>
            </div>

            <div class="card">
                <div class="card-title">
                    {{ profile.self ? '我的帖子' : 'TA 的帖子' }}（{{ topics.length }}）
                </div>
                <el-empty v-if="!topics.length" description="还没有发过帖子" :image-size="60"/>
                <TopicCard v-for="item in topics" :key="item.id" :data="item" @click="openTopic"/>
            </div>
        </template>
    </div>
</template>

<style lang="less" scoped>
.profile-page {
    padding: 18px 20px;
    min-height: 100%;
}

.header {
    display: flex;
    align-items: center;
    gap: 18px;
    background: #fff;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
    padding: 20px;
    margin-bottom: 12px;
}

.dark .header {
    background: #1f2022;
}

.info {
    flex: 1;
    min-width: 0;
}

.name-row {
    display: flex;
    align-items: center;
    gap: 8px;

    .name {
        font-size: 20px;
        font-weight: 600;
    }
}

.desc {
    margin-top: 6px;
    font-size: 13px;
    color: var(--el-text-color-regular);
}

.meta {
    margin-top: 6px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
}

.card {
    background: #fff;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
    padding: 16px 20px;
    margin-bottom: 12px;
}

.dark .card {
    background: #1f2022;
}

.card-title {
    font-size: 15px;
    font-weight: 600;
    padding-bottom: 10px;
    margin-bottom: 12px;
    border-bottom: 1px solid var(--el-border-color-lighter);
}

.rows {
    display: flex;
    flex-wrap: wrap;
}

.row {
    width: 50%;
    display: flex;
    font-size: 14px;
    line-height: 32px;

    .label {
        width: 56px;
        color: var(--el-text-color-secondary);
        flex-shrink: 0;
    }

    .value {
        color: var(--el-text-color-primary);
        word-break: break-all;
    }

    .hidden {
        color: var(--el-text-color-placeholder);
    }
}

@media (max-width: 900px) {
    .row {
        width: 100%;
    }
}
</style>
