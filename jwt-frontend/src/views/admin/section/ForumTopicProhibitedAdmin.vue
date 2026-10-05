<script setup>
import {onMounted, ref} from "vue";
import {ElMessage, ElMessageBox} from "element-plus";
import {apiAdminProhibitedList, apiAdminProhibitedSave} from "@/net/api/admin";

/**
 * 违禁词维护。
 *
 * 后端的保存接口是**整体覆盖**语义（收一个字符串数组），所以这里是
 * 「本地编辑标签 → 点保存一次性提交」，而不是每改一个词发一次请求。
 * 保存后后端会热更新词表文件，立即生效，不需要重启。
 */
const words = ref([])
const input = ref('')
const loading = ref(false)
const saving = ref(false)

function load() {
    loading.value = true
    apiAdminProhibitedList(data => {
        words.value = data || []
        loading.value = false
    }, () => {
        loading.value = false
    })
}

function add() {
    const word = input.value.trim()
    if (!word) return
    if (words.value.includes(word)) {
        input.value = ''
        return ElMessage.warning('这个词已经在列表里了')
    }
    words.value.push(word)
    input.value = ''
}

function remove(word) {
    words.value = words.value.filter(w => w !== word)
}

function save() {
    saving.value = true
    apiAdminProhibitedSave([...words.value], () => {
        saving.value = false
        ElMessage.success('已保存，立即生效')
        load()
    }, message => {
        saving.value = false
        ElMessage.warning(message)
    })
}

function clearAll() {
    ElMessageBox.confirm('确定清空整个词表吗？清空后将不再拦截任何词', '警告', {type: 'warning'})
        .then(() => {
            words.value = []
        })
        .catch(() => {
            // 取消
        })
}

onMounted(load)
</script>

<template>
    <div v-loading="loading">
        <el-alert type="info" :closable="false" show-icon class="tip">
            <template #title>
                命中词表的标题/正文会被拒绝发布（不会消耗发帖额度）。
                <b>保存后立即生效，无需重启后端。</b>
            </template>
        </el-alert>

        <div class="toolbar">
            <el-input v-model="input" placeholder="输入要拦截的词" style="width: 220px"
                      clearable @keyup.enter="add"/>
            <el-button @click="add">添加</el-button>
            <div class="spacer"></div>
            <el-button type="danger" plain :disabled="!words.length" @click="clearAll">清空</el-button>
            <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        </div>

        <div class="words">
            <el-empty v-if="!words.length" description="词表为空，当前不拦截任何词" :image-size="60"/>
            <el-tag v-for="w in words" :key="w" closable size="large" class="word"
                    @close="remove(w)">{{ w }}</el-tag>
        </div>

        <p class="count">共 {{ words.length }} 个词</p>
    </div>
</template>

<style lang="less" scoped>
.tip {
    margin-bottom: 14px;
}

.toolbar {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 16px;

    .spacer {
        flex: 1;
    }
}

.words {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    min-height: 60px;
}

.word {
    font-size: 14px;
}

.count {
    margin-top: 14px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
}
</style>
