<script setup>
import {onMounted, ref} from "vue";
import {ElMessage, ElMessageBox} from "element-plus";
import {apiAdminChangeTopicType, apiAdminTopicDelete, apiAdminTopicList, apiAdminTopicStatus} from "@/net/api/admin";
import {apiForumTypes} from "@/net/api/forum";
import {formatTime} from "@/utils/format";

/**
 * 帖子管理：置顶 / 锁定 / 隐藏 / 删除 / 改分类。
 * 三个状态接口入参一致（{tid, status}），所以共用一个 toggleStatus。
 */
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const types = ref([])
const loading = ref(false)

function load() {
    loading.value = true
    apiAdminTopicList(page.value, size.value, keyword.value, data => {
        list.value = data.list || []
        total.value = data.total || 0
        loading.value = false
    }, () => {
        loading.value = false
    })
}

function loadTypes() {
    apiForumTypes(data => types.value = data || [])
}

function search() {
    page.value = 1
    load()
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

const FIELD = {top: 'top', locked: 'locked', invisible: 'invisible'}

/** 切换置顶/锁定/隐藏：先请求成功再改本地，避免请求失败时界面与后端不一致 */
function toggleStatus(row, action) {
    const field = FIELD[action]
    const next = row[field] !== 1
    apiAdminTopicStatus(action, row.id, next, () => {
        row[field] = next ? 1 : 0
        ElMessage.success('已更新')
    })
}

function changeType(row, type) {
    apiAdminChangeTopicType(row.id, type, () => {
        row.type = type
        ElMessage.success('分类已更新')
    })
}

function remove(row) {
    ElMessageBox.confirm(`确定删除帖子「${row.title}」吗？此操作不可撤销`, '提示', {type: 'warning'})
        .then(() => apiAdminTopicDelete(row.id, () => {
            ElMessage.success('已删除')
            load()
        }))
        .catch(() => {
            // 取消
        })
}

onMounted(() => {
    loadTypes()
    load()
})
</script>

<template>
    <div>
        <div class="toolbar">
            <el-input v-model="keyword" placeholder="按标题搜索" style="width: 260px"
                      clearable @keyup.enter="search" @clear="search"/>
            <el-button type="primary" @click="search">搜索</el-button>
        </div>

        <el-table v-loading="loading" :data="list" border stripe>
            <el-table-column prop="id" label="ID" width="70"/>
            <el-table-column label="标题" min-width="200" show-overflow-tooltip>
                <template #default="{row}">
                    <span class="title-text">{{ row.title }}</span>
                    <el-tag v-if="row.top === 1" type="danger" size="small" effect="dark">置顶</el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="username" label="作者" width="110"/>
            <el-table-column label="分类" width="150">
                <template #default="{row}">
                    <el-select :model-value="row.type" size="small" style="width: 130px"
                               @change="v => changeType(row, v)">
                        <el-option v-for="t in types" :key="t.id" :label="t.name" :value="t.id"/>
                    </el-select>
                </template>
            </el-table-column>
            <el-table-column label="时间" width="140">
                <template #default="{row}">{{ formatTime(row.time) }}</template>
            </el-table-column>
            <el-table-column label="置顶" width="70">
                <template #default="{row}">
                    <el-switch :model-value="row.top === 1" size="small"
                               @change="() => toggleStatus(row, 'top')"/>
                </template>
            </el-table-column>
            <el-table-column label="锁定" width="70">
                <template #default="{row}">
                    <el-switch :model-value="row.locked === 1" size="small"
                               @change="() => toggleStatus(row, 'locked')"/>
                </template>
            </el-table-column>
            <el-table-column label="隐藏" width="70">
                <template #default="{row}">
                    <el-switch :model-value="row.invisible === 1" size="small"
                               @change="() => toggleStatus(row, 'invisible')"/>
                </template>
            </el-table-column>
            <el-table-column label="操作" width="80" fixed="right">
                <template #default="{row}">
                    <el-button link type="danger" @click="remove(row)">删除</el-button>
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
.toolbar {
    display: flex;
    gap: 10px;
    margin-bottom: 14px;
}

.pager {
    margin-top: 16px;
    justify-content: flex-end;
}

.title-text + .el-tag {
    margin-left: 6px;
}
</style>
