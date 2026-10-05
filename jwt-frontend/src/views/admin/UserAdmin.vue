<script setup>
import {onMounted, reactive, ref} from "vue";
import {ElMessage} from "element-plus";
import {apiAdminUserChangePassword, apiAdminUserDetail, apiAdminUserList, apiAdminUserSave} from "@/net/api/admin";
import {formatTime} from "@/utils/format";
import {useStore} from "@/store";

/**
 * 用户管理。
 *
 * ⚠️ 分页基准：管理接口的 page 是 **1 基**（后端直接 Page.of(page, size)），
 * 与论坛的 /list-topic（前端 0 基、后端 +1）不同。el-pagination 也是 1 基，直接对齐。
 */
const store = useStore()

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const loading = ref(false)

const editVisible = ref(false)
const saving = ref(false)
const editForm = reactive({id: null, username: '', email: '', role: 'user', mute: false, banned: false})

const pwdVisible = ref(false)
const pwdSaving = ref(false)
const pwdForm = reactive({id: null, username: '', newPassword: ''})

function load() {
    loading.value = true
    apiAdminUserList(page.value, size.value, keyword.value, data => {
        list.value = data.list || []
        total.value = data.total || 0
        loading.value = false
    }, () => {
        loading.value = false
    })
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

/** 详情接口返回 {account, detail}，这里取 account 的字段回填表单 */
function openEdit(row) {
    apiAdminUserDetail(row.id, data => {
        Object.assign(editForm, {
            id: data.account.id,
            username: data.account.username,
            email: data.account.email,
            role: data.account.role,
            mute: data.account.mute,
            banned: data.account.banned
        })
        editVisible.value = true
    })
}

function saveEdit() {
    saving.value = true
    // 不带 detail：用户资料（性别/手机/QQ/微信/签名）属于个人设置的范围，后台不动它
    apiAdminUserSave({...editForm}, () => {
        saving.value = false
        editVisible.value = false
        ElMessage.success('保存成功')
        load()
    }, message => {
        saving.value = false
        ElMessage.warning(message)
    })
}

function openPwd(row) {
    pwdForm.id = row.id
    pwdForm.username = row.username
    pwdForm.newPassword = ''
    pwdVisible.value = true
}

function savePwd() {
    if (!pwdForm.newPassword || pwdForm.newPassword.length < 6) {
        return ElMessage.warning('密码长度必须在 6-20 之间')
    }
    pwdSaving.value = true
    apiAdminUserChangePassword({id: pwdForm.id, newPassword: pwdForm.newPassword}, () => {
        pwdSaving.value = false
        pwdVisible.value = false
        ElMessage.success('密码已修改')
    }, message => {
        pwdSaving.value = false
        ElMessage.warning(message)
    })
}

/** 管理员不能改自己的账号（后端也会拦，这里只是别让人白点一下） */
function isSelf(id) {
    return store.user.id != null && store.user.id === id
}

onMounted(load)
</script>

<template>
    <div class="admin-page">
        <h3 class="page-title">用户管理</h3>

        <div class="toolbar">
            <el-input v-model="keyword" placeholder="用户名（模糊）或用户 id（精确）"
                      style="width: 280px" clearable @keyup.enter="search" @clear="search"/>
            <el-button type="primary" @click="search">搜索</el-button>
        </div>

        <el-table v-loading="loading" :data="list" border stripe>
            <el-table-column prop="id" label="ID" width="70"/>
            <el-table-column prop="username" label="用户名" min-width="120"/>
            <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip/>
            <el-table-column label="角色" width="90">
                <template #default="{row}">
                    <el-tag :type="row.role === 'admin' ? 'danger' : 'info'" size="small">{{ row.role }}</el-tag>
                </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
                <template #default="{row}">
                    <el-tag v-if="row.banned" type="danger" size="small">已封禁</el-tag>
                    <el-tag v-else-if="row.mute" type="warning" size="small">已禁言</el-tag>
                    <el-tag v-else type="success" size="small">正常</el-tag>
                </template>
            </el-table-column>
            <el-table-column label="注册时间" width="150">
                <template #default="{row}">{{ formatTime(row.registerTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
                <template #default="{row}">
                    <el-button link type="primary" :disabled="isSelf(row.id)" @click="openEdit(row)">编辑</el-button>
                    <el-button link type="warning" :disabled="isSelf(row.id)" @click="openPwd(row)">改密码</el-button>
                </template>
            </el-table-column>
        </el-table>

        <el-pagination class="pager" background
                       layout="total, sizes, prev, pager, next"
                       :total="total" :current-page="page" :page-size="size"
                       :page-sizes="[10, 20, 50]"
                       @current-change="changePage" @size-change="changeSize"/>

        <el-dialog v-model="editVisible" title="编辑用户" width="460px" align-center>
            <el-form label-width="70px">
                <el-form-item label="用户名">
                    <el-input v-model="editForm.username" maxlength="10"/>
                </el-form-item>
                <el-form-item label="邮箱">
                    <el-input v-model="editForm.email"/>
                </el-form-item>
                <el-form-item label="角色">
                    <el-select v-model="editForm.role" style="width: 160px">
                        <el-option label="普通用户" value="user"/>
                        <el-option label="管理员" value="admin"/>
                    </el-select>
                </el-form-item>
                <el-form-item label="禁言">
                    <el-switch v-model="editForm.mute"/>
                    <span class="hint">开启后只能浏览，不能发帖/评论</span>
                </el-form-item>
                <el-form-item label="封禁">
                    <el-switch v-model="editForm.banned"/>
                    <span class="hint">开启后旧令牌立即失效，且无法重新登录</span>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="editVisible = false">取消</el-button>
                <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
            </template>
        </el-dialog>

        <el-dialog v-model="pwdVisible" title="修改密码" width="420px" align-center>
            <el-form label-width="70px">
                <el-form-item label="用户">
                    <span>{{ pwdForm.username }}</span>
                </el-form-item>
                <el-form-item label="新密码">
                    <el-input v-model="pwdForm.newPassword" type="password" show-password
                              placeholder="6-20 位"/>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="pwdVisible = false">取消</el-button>
                <el-button type="primary" :loading="pwdSaving" @click="savePwd">确定</el-button>
            </template>
        </el-dialog>
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

.toolbar {
    display: flex;
    gap: 10px;
    margin-bottom: 14px;
}

.pager {
    margin-top: 16px;
    justify-content: flex-end;
}

.hint {
    margin-left: 10px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
}
</style>
