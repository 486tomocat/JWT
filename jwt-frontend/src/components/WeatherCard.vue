<script setup>
import {computed, onMounted, ref} from "vue";
import {ElMessage, ElMessageBox} from "element-plus";
import {Edit} from "@element-plus/icons-vue";
import {apiWeather} from "@/net/api/weather";

/**
 * 右栏的天气卡片。
 *
 * 数据来自后端 /api/weather（后端再调 Open-Meteo，免费无需 key）。
 * 城市选择记在 localStorage —— 不做 IP 自动定位：本地开发拿到的是 127.0.0.1，
 * 而且为一个天气卡片再引一个 IP 库不划算。手动切一次、记住，体验就够好了。
 */
const CITY_KEY = 'weather-city'

const WEEK_NAMES = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']

/**
 * 今天的日期与星期。
 * 用**本地时间**而不是后端返回的观测时间 —— 用户想看的是"我这儿今天几号"。
 */
const today = computed(() => {
    const d = new Date()
    return `${d.getMonth() + 1}月${d.getDate()}日 ${WEEK_NAMES[d.getDay()]}`
})

const data = ref(null)
const loading = ref(false)

function load() {
    const city = localStorage.getItem(CITY_KEY) || ''
    loading.value = true
    apiWeather(city, res => {
        data.value = res
        loading.value = false
    }, message => {
        loading.value = false
        ElMessage.warning(message)
    })
}

/** 点卡片切换城市 */
function changeCity() {
    ElMessageBox.prompt('输入城市名（中文或拼音都行）', '切换城市', {
        confirmButtonText: '查询',
        cancelButtonText: '取消',
        inputValue: data.value ? data.value.city : '',
        inputValidator: v => (v && v.trim() ? true : '城市名不能为空')
    }).then(({value}) => {
        localStorage.setItem(CITY_KEY, value.trim())
        load()
    }).catch(() => {
        // 用户取消，什么都不做
    })
}

onMounted(load)
</script>

<template>
    <div class="weather-card" v-loading="loading">
        <template v-if="data">
            <div class="top">
                <span class="city">{{ data.city }}</span>
                <el-tooltip content="切换城市" placement="top">
                    <el-icon class="edit" @click="changeCity"><Edit/></el-icon>
                </el-tooltip>
            </div>
            <div class="temp">
                {{ Math.round(data.temperature) }}<span class="unit">°C</span>
            </div>
            <div class="desc">
                {{ data.weather }}
                <span class="wind">风速 {{ Math.round(data.windSpeed) }} km/h</span>
            </div>
            <div class="date">{{ today }}</div>
        </template>
        <div v-else class="failed" @click="load">
            天气加载失败，点击重试
        </div>
    </div>
</template>

<style lang="less" scoped>
.weather-card {
    border-radius: 8px;
    padding: 14px 16px;
    margin-bottom: 12px;
    background: linear-gradient(135deg, #6db3f2 0%, #1e69de 100%);
    color: #fff;
    cursor: default;
    user-select: none;
    min-height: 96px;
}

.top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 13px;
    opacity: .92;

    .edit {
        cursor: pointer;
        transition: transform .2s;

        &:hover {
            transform: scale(1.2);
        }
    }
}

.temp {
    font-size: 30px;
    font-weight: 600;
    line-height: 1.2;
    margin: 4px 0 2px;

    .unit {
        font-size: 15px;
        font-weight: 400;
        margin-left: 2px;
    }
}

.desc {
    font-size: 12px;
    opacity: .92;

    .wind {
        margin-left: 6px;
    }
}

.date {
    font-size: 12px;
    opacity: .82;
    margin-top: 8px;
    padding-top: 8px;
    border-top: 1px solid rgba(255, 255, 255, .25);
}

.failed {
    font-size: 13px;
    text-align: center;
    line-height: 66px;
    opacity: .9;
    cursor: pointer;
}
</style>
