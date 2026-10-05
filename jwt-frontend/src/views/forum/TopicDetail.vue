<script setup>
import {computed, onMounted, ref, watch} from "vue";
import {useRoute, useRouter} from "vue-router";
import {ArrowLeft} from "@element-plus/icons-vue";
import {ElMessage, ElMessageBox} from "element-plus";
import {apiForumComments, apiForumDeleteComment, apiForumTopic, apiForumTypes} from "@/net/api/forum";
import {formatTime} from "@/utils/format";
import InteractButton from "@/components/forum/InteractButton.vue";
import TopicCommentList from "@/components/forum/TopicCommentList.vue";
import TopicContent from "@/components/forum/TopicContent.vue";
import CommentEditor from "@/components/forum/CommentEditor.vue";
import {useStore} from "@/store";

/**
 * 帖子详情。
 * 正文与评论都用 Quill 只读渲染（见 TopicContent），因此没有 XSS 面。
 */
const route = useRoute()
const router = useRouter()
const store = useStore()
const tid = Number(route.params.tid)

const topic = ref(null)
const comments = ref([])
const types = ref([])
const notFound = ref(false)
const loading = ref(true)

/**
 * 返回上一页。
 * 如果用户是直接打开详情链接（没有站内上一页），退回广场 ——
 * 否则 router.back() 可能把人带出站点，或者干脆没反应。
 */
function goBack() {
    if (window.history.state && window.history.state.back) {
        router.back()
    } else {
        router.push('/index/forum')
    }
}

/** 详情接口只返回 type 的 id，分类名要另外查一次分类表 */
function typeName(id) {
    const hit = types.value.find(t => t.id === id)
    return hit ? hit.name : ''
}

function loadComments() {
    apiForumComments(tid, 0, data => comments.value = data || [])
}

function load() {
    apiForumTypes(data => types.value = data || [])

    apiForumTopic(tid, data => {
        topic.value = data
        loading.value = false
    }, () => {
        // 后端对「不存在」和「已被隐藏」都返回 code=404
        notFound.value = true
        loading.value = false
    })

    loadComments()
}

/** 帖子作者本人才能编辑（后端 update-topic 也会再校验一次归属与锁定状态） */
const isOwner = computed(() =>
    store.user.id != null && topic.value && topic.value.user && topic.value.user.id === store.user.id)

/** 点作者名进他的个人主页 */
function openAuthor() {
    const uid = topic.value && topic.value.user && topic.value.user.id
    if (uid != null) {
        router.push('/index/user/' + uid)
    }
}

/** 去编辑页（独立页面，不再是弹窗） */
function openEditor() {
    router.push('/index/forum/edit/' + topic.value.id)
}

function removeComment(comment) {
    ElMessageBox.confirm('确定删除这条评论吗？', '提示', {type: 'warning'})
        .then(() => apiForumDeleteComment(comment.id, () => {
            ElMessage.success('已删除')
            loadComments()
        }))
        .catch(() => {
            // 用户点了取消
        })
}

// 发帖 / 编辑成功后刷新详情
watch(() => store.forumRefreshTick, load)

onMounted(load)
</script>

<template>
    <div class="topic-detail" v-loading="loading">
        <div class="back-bar">
            <el-button type="primary" plain @click="goBack">
                <el-icon><ArrowLeft/></el-icon>
                <span style="margin-left: 5px">返回</span>
            </el-button>
        </div>

        <el-empty v-if="notFound" description="帖子不存在或已被屏蔽"/>

        <template v-else-if="topic">
            <h2 class="title">
                <span v-if="topic.locked === 1" class="lock-tag">[已锁定]</span>{{ topic.title }}
                <el-button v-if="isOwner && topic.locked !== 1" link type="primary" size="small"
                           class="edit-btn" @click="openEditor">编辑</el-button>
            </h2>

            <div class="meta">
                <el-avatar :size="26" :src="topic.user && topic.user.avatar"/>
                <span class="author" @click="openAuthor">{{ topic.user && topic.user.username }}</span>
                <span class="sep">·</span>
                <span>{{ formatTime(topic.time) }}</span>
                <el-tag v-if="typeName(topic.type)" size="small" effect="plain">
                    {{ typeName(topic.type) }}
                </el-tag>
            </div>

            <div class="content">
                <TopicContent :content="topic.content"/>
            </div>

            <div class="comments">
                <div class="section-title">全部评论</div>
                <CommentEditor :tid="tid" @submitted="loadComments"/>
                <TopicCommentList :comments="comments" :current-user-id="store.user.id"
                                  @delete="removeComment"/>
            </div>

            <!-- 互动栏放在正文与评论之后（社区产品的常见排布） -->
            <div class="action-bar">
                <InteractButton :tid="tid" type="like"
                                :state="topic.interact && topic.interact.like" :count="topic.like"/>
                <InteractButton :tid="tid" type="collect"
                                :state="topic.interact && topic.interact.collect" :count="topic.collect"/>
                <span class="comment-count">评论 {{ topic.comments }}</span>
            </div>
        </template>
    </div>
</template>

<style lang="less" scoped>
.topic-detail {
    padding: 18px 20px;
    min-height: 100%;
}

.back-bar {
    margin-bottom: 12px;

    /* 比 link 按钮明显得多，但又不像实心主按钮那样抢标题的注意力 */
    :deep(.el-button) {
        font-size: 14px;
        padding: 8px 16px;
        font-weight: 500;
    }
}

.title {
    margin: 0 0 10px;
    font-size: 22px;
    line-height: 30px;

    .lock-tag {
        color: var(--el-color-warning);
        font-size: 15px;
    }

    .edit-btn {
        margin-left: 8px;
        vertical-align: 3px;
    }
}

.meta {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-bottom: 16px;

    .author {
        color: var(--el-color-primary);
        font-size: 13px;
        cursor: pointer;

        &:hover {
            text-decoration: underline;
        }
    }

    .sep {
        color: var(--el-border-color);
    }
}

.content {
    background: #fff;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
    padding: 16px;
    min-height: 80px;
}

.dark .content {
    background: #1f2022;
}

.action-bar {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 16px;
    margin-top: 24px;
    padding-top: 18px;
    border-top: 1px solid var(--el-border-color-lighter);

    .comment-count {
        font-size: 13px;
        color: var(--el-text-color-secondary);
    }
}

.comments {
    margin-top: 18px;

    .section-title {
        font-size: 14px;
        font-weight: 600;
        margin-bottom: 10px;
    }
}
</style>
