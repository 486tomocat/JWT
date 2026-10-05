<script setup>
import {computed} from "vue";
import {useDark, useToggle} from "@vueuse/core";
import {Moon, Sunny} from "@element-plus/icons-vue";

/**
 * 主题切换。
 *
 * useDark() 默认就是给 <html> 切换 .dark class —— 与 element-plus 暗色变量的
 * 选择器 (html.dark) 以及项目自己的 .dark 后代样式都吻合，所以不需要额外配置。
 * 选择结果持久化在 localStorage，首次进入跟随系统偏好。
 */
const isDark = useDark()
const toggleDark = useToggle(isDark)

const tip = computed(() => isDark.value ? '切换到浅色' : '切换到深色')
</script>

<template>
    <el-tooltip :content="tip" placement="bottom">
        <div class="theme-toggle" @click="toggleDark()">
            <el-icon :size="19">
                <Sunny v-if="isDark"/>
                <Moon v-else/>
            </el-icon>
        </div>
    </el-tooltip>
</template>

<style lang="less" scoped>
.theme-toggle {
    cursor: pointer;
    display: flex;
    align-items: center;
    padding: 0 8px;
    color: var(--el-text-color-regular);

    &:hover {
        color: var(--el-color-primary);
    }
}
</style>
