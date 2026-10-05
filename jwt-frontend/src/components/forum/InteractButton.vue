<script setup>
import {computed, ref, watch} from "vue";
import {apiForumInteract} from "@/net/api/forum";

/**
 * 点赞 / 收藏按钮。
 * 数据由父级传入；本组件只负责「点一下 → 先改本地 → 调接口 → 失败回滚」。
 *
 * count 允许为 null：此时按钮只切换状态、不显示数字（保留这个分支是为了兼容
 * 可能拿不到计数的场景，正常详情页与列表页都会传真实数字）。
 */
const props = defineProps({
    tid: {type: Number, required: true},
    type: {type: String, required: true},        // like | collect
    state: {type: Boolean, default: false},
    count: {type: Number, default: null}
})

const hasCount = computed(() => props.count !== null && props.count !== undefined)
const active = ref(props.state)
const num = ref(hasCount.value ? props.count : 0)
const pending = ref(false)

watch(() => props.state, v => active.value = v)
watch(() => props.count, v => num.value = hasCount.value ? v : 0)

function toggle() {
    if (pending.value) return
    const next = !active.value
    const prevActive = active.value
    const prevNum = num.value

    active.value = next
    if (hasCount.value) {
        num.value = next ? num.value + 1 : Math.max(0, num.value - 1)
    }
    pending.value = true

    apiForumInteract(props.tid, props.type, next, () => {
        pending.value = false
    }, () => {
        // 失败回滚，避免界面与后端不一致
        active.value = prevActive
        num.value = prevNum
        pending.value = false
    })
}
</script>

<template>
    <span class="interact-btn" :class="{active}" @click.stop="toggle">
        <span class="icon">{{ active ? (type === 'like' ? '♥' : '★') : (type === 'like' ? '♡' : '☆') }}</span>
        <span class="label">{{ type === 'like' ? '点赞' : '收藏' }}</span>
        <span v-if="hasCount" class="num">{{ num }}</span>
    </span>
</template>

<style lang="less" scoped>
.interact-btn {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 4px 12px;
    border: 1px solid var(--el-border-color);
    border-radius: 14px;
    font-size: 13px;
    cursor: pointer;
    user-select: none;
    transition: all .15s;

    &:hover {
        border-color: var(--el-color-primary);
        color: var(--el-color-primary);
    }

    &.active {
        border-color: var(--el-color-primary);
        color: var(--el-color-primary);
        background: var(--el-color-primary-light-9);
    }

    .icon {
        font-size: 15px;
    }

    .num {
        color: var(--el-text-color-secondary);
    }
}
</style>
