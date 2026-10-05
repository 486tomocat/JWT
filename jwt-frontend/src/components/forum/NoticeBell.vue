<script setup>
import {onMounted, ref} from "vue";
import {useRouter} from "vue-router";
import {ElMessage, ElMessageBox} from "element-plus";
import {Bell} from "@element-plus/icons-vue";
import {apiNotificationDelete, apiNotificationDeleteAll, apiNotificationList} from "@/net/api/forum";
import {formatTime} from "@/utils/format";

/**
 * 顶栏通知铃铛。
 *
 * 红点只表示「有没有通知」（列表非空），不做未读数字 ——
 * 后端没有未读计数字段，要显示数字就得先改后端，本轮不做。
 */
const router = useRouter()
const items = ref([])

function load() {
    apiNotificationList(data => items.value = data || [])
}

function remove(id) {
    apiNotificationDelete(id, () => {
        items.value = items.value.filter(item => item.id !== id)
    })
}

function clearAll() {
    ElMessageBox.confirm('确定清空全部通知吗？', '提示', {type: 'warning'})
        .then(() => apiNotificationDeleteAll(() => {
            items.value = []
            ElMessage.success('已清空通知')
        }))
        .catch(() => {
            // 用户点了取消，什么都不做
        })
}

/**
 * 通知里的 url 是后端按参考项目写死的 /index/topic-detail/{id}，
 * 而本项目的路由是 /index/forum/topic/:tid。
 * 这里从末尾取数字 id 再跳正确路由，避免点通知 404。
 */
function jump(item) {
    const matched = /(\d+)\s*$/.exec(item.url || '')
    if (matched) {
        router.push('/index/forum/topic/' + matched[1])
    }
}

// 进页面就先拉一次，红点才有意义
onMounted(load)
</script>

<template>
    <el-popover placement="bottom-end" :width="340" trigger="click" @show="load">
        <template #reference>
            <div class="bell">
                <el-badge :is-dot="items.length > 0" :offset="[-2, 4]">
                    <el-icon :size="20"><Bell/></el-icon>
                </el-badge>
            </div>
        </template>

        <div class="notice-panel">
            <div class="panel-head">
                <span class="panel-title">通知</span>
                <el-button v-if="items.length" link type="primary" size="small" @click="clearAll">
                    全部清空
                </el-button>
            </div>

            <el-empty v-if="!items.length" description="暂无通知" :image-size="50"/>

            <div v-for="item in items" :key="item.id" class="notice-item" @click="jump(item)">
                <div class="title">{{ item.title }}</div>
                <div class="content">{{ item.content }}</div>
                <div class="meta">
                    <span>{{ formatTime(item.time) }}</span>
                    <el-button link type="danger" size="small" @click.stop="remove(item.id)">删除</el-button>
                </div>
            </div>
        </div>
    </el-popover>
</template>

<style lang="less" scoped>
.bell {
    cursor: pointer;
    display: flex;
    align-items: center;
    padding: 0 8px;
    color: var(--el-text-color-regular);

    &:hover {
        color: var(--el-color-primary);
    }
}

.panel-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 8px;
    border-bottom: 1px solid var(--el-border-color-lighter);
    margin-bottom: 8px;

    .panel-title {
        font-weight: 600;
        font-size: 14px;
    }
}

.notice-item {
    padding: 8px;
    border-radius: 4px;
    cursor: pointer;

    &:hover {
        background: var(--el-fill-color-light);
    }

    .title {
        font-size: 13px;
        font-weight: 600;
        margin-bottom: 3px;
    }

    .content {
        font-size: 12px;
        color: var(--el-text-color-regular);
        line-height: 18px;
    }

    .meta {
        display: flex;
        align-items: center;
        justify-content: space-between;
        font-size: 11px;
        color: var(--el-text-color-secondary);
        margin-top: 4px;
    }
}
</style>
