<script setup>
import {onMounted, ref} from "vue";
import {ElMessage} from "element-plus";
import {apiAdminEmailList, apiAdminEmailResend} from "@/net/api/admin";
import {formatTime} from "@/utils/format";

/**
 * 邮件记录。
 * 这张表的存在意义就是让 MQ 里那条消息可追踪：失败能被发现，并能从这里重发。
 *
 * status 取值：0 = 待发送（刚入队 / 重发前重置）、1 = 已发送、2 = 失败（进了死信队列）
 */
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

const STATUS = {
    0: {text: '待发送', type: 'info'},
    1: {text: '已发送', type: 'success'},
    2: {text: '失败', type: 'danger'}
}

function statusOf(v) {
    return STATUS[v] || {text: '未知(' + v + ')', type: 'info'}
}

function load() {
    loading.value = true
    apiAdminEmailList(page.value, size.value, data => {
        list.value = data.list || []
        total.value = data.total || 0
        loading.value = false
    }, () => {
        loading.value = false
    })
}

function changePage(p) {
    page.value = p
    load()
}

function changeSize(s) {
    size.value = s
    page.value = 1
    load()
}

function resend(row) {
    apiAdminEmailResend(row.id, () => {
        ElMessage.success('已重新投递到队列')
        load()
    })
}

onMounted(load)
</script>

<template>
    <div class="admin-page">
        <h3 class="page-title">邮件记录</h3>

        <el-table v-loading="loading" :data="list" border stripe>
            <el-table-column type="expand">
                <template #default="{row}">
                    <!-- 用纯文本而不是 v-html：邮件正文里可能带用户可控的帖子标题，
                         直接渲染 HTML 会引入 XSS 面，而这里只要能看到内容就够了 -->
                    <div class="mail-content">{{ row.content }}</div>
                </template>
            </el-table-column>
            <el-table-column prop="id" label="ID" width="70"/>
            <el-table-column prop="email" label="收件人" min-width="180" show-overflow-tooltip/>
            <el-table-column prop="title" label="主题" min-width="200" show-overflow-tooltip/>
            <el-table-column label="时间" width="150">
                <template #default="{row}">{{ formatTime(row.time) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
                <template #default="{row}">
                    <el-tag :type="statusOf(row.status).type" size="small">
                        {{ statusOf(row.status).text }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
                <template #default="{row}">
                    <el-button link type="primary" @click="resend(row)">重发</el-button>
                </template>
            </el-table-column>
        </el-table>

        <el-pagination class="pager" background
                       layout="total, sizes, prev, pager, next"
                       :total="total" :current-page="page" :page-size="size"
                       :page-sizes="[10, 20, 50]"
                       @current-change="changePage" @size-change="changeSize"/>
    </div>
</template>

<style lang="less" scoped>
.admin-page {
    padding: 18px 20px;
    min-height: 100%;
}

.page-title {
    margin: 0 0 14px;
    font-size: 18px;
}

.pager {
    margin-top: 16px;
    justify-content: flex-end;
}

.mail-content {
    padding: 8px 14px;
    font-size: 12px;
    line-height: 20px;
    color: var(--el-text-color-regular);
    white-space: pre-wrap;
    word-break: break-all;
    max-height: 260px;
    overflow: auto;
}
</style>
