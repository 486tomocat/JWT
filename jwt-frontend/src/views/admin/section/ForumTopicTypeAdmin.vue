<script setup>
import {onMounted, reactive, ref} from "vue";
import {ElMessage, ElMessageBox} from "element-plus";
import {apiAdminTypeCreate, apiAdminTypeDelete, apiAdminTypeUpdate} from "@/net/api/admin";
import {apiForumTypes} from "@/net/api/forum";

/**
 * 分类维护：增 / 改 / 删。
 * 列表复用论坛公开接口 /api/forum/types（管理端没有单独的读接口），
 * 写操作才走 /api/admin/forum/*。
 */
const list = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive({id: null, name: '', desc: '', color: '#409eff'})

function load() {
    loading.value = true
    apiForumTypes(data => {
        list.value = data || []
        loading.value = false
    }, () => {
        loading.value = false
    })
}

function openCreate() {
    Object.assign(form, {id: null, name: '', desc: '', color: '#409eff'})
    dialogVisible.value = true
}

function openEdit(row) {
    Object.assign(form, {id: row.id, name: row.name, desc: row.desc || '', color: row.color || '#409eff'})
    dialogVisible.value = true
}

function save() {
    if (!form.name.trim()) return ElMessage.warning('请填写分类名称')

    saving.value = true
    const done = () => {
        saving.value = false
        dialogVisible.value = false
        ElMessage.success('保存成功')
        load()
    }
    const fail = message => {
        saving.value = false
        ElMessage.warning(message)
    }

    if (form.id) {
        apiAdminTypeUpdate({id: form.id, name: form.name, desc: form.desc, color: form.color}, done, fail)
    } else {
        apiAdminTypeCreate({name: form.name, desc: form.desc, color: form.color}, done, fail)
    }
}

function remove(row) {
    ElMessageBox.confirm(`确定删除分类「${row.name}」吗？该分类下的帖子会变成「无分类」`, '提示', {type: 'warning'})
        .then(() => apiAdminTypeDelete(row.id, () => {
            ElMessage.success('已删除')
            load()
        }))
        .catch(() => {
            // 取消
        })
}

onMounted(load)
</script>

<template>
    <div>
        <div class="toolbar">
            <el-button type="primary" @click="openCreate">新建分类</el-button>
            <span class="hint">分类会出现在广场顶部的 Tab 与发帖弹窗里</span>
        </div>

        <el-table v-loading="loading" :data="list" border stripe>
            <el-table-column prop="id" label="ID" width="70"/>
            <el-table-column prop="name" label="名称" min-width="140"/>
            <el-table-column prop="desc" label="描述" min-width="200" show-overflow-tooltip/>
            <el-table-column label="颜色" width="110">
                <template #default="{row}">
                    <span class="color-cell">
                        <span class="dot" :style="{background: row.color || '#909399'}"></span>
                        {{ row.color || '—' }}
                    </span>
                </template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
                <template #default="{row}">
                    <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                    <el-button link type="danger" @click="remove(row)">删除</el-button>
                </template>
            </el-table-column>
        </el-table>

        <el-dialog v-model="dialogVisible" :title="form.id ? '编辑分类' : '新建分类'"
                   width="440px" align-center>
            <el-form label-width="70px">
                <el-form-item label="名称">
                    <el-input v-model="form.name" maxlength="20" show-word-limit placeholder="如：日常闲聊"/>
                </el-form-item>
                <el-form-item label="描述">
                    <el-input v-model="form.desc" maxlength="100" placeholder="一句话说明这个分类"/>
                </el-form-item>
                <el-form-item label="颜色">
                    <el-color-picker v-model="form.color"/>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="save">保存</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<style lang="less" scoped>
.toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 14px;
}

.hint {
    font-size: 12px;
    color: var(--el-text-color-secondary);
}

.color-cell {
    display: flex;
    align-items: center;
    font-size: 12px;
}

.dot {
    width: 12px;
    height: 12px;
    border-radius: 50%;
    margin-right: 6px;
    display: inline-block;
}
</style>
