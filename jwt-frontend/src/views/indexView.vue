<script setup lang="ts">
import {logout, get} from "@/net"
import router from "@/router";
import {useStore} from "@/store"
import {computed, reactive, ref, watch} from "vue";
import {useRoute} from "vue-router";
import {ElMessage} from "element-plus";
import {
  Back,
  ChatLineSquare,
  Management,
  Message,
  Operation,
  Plus,
  Postcard,
  Promotion,
  Search,
  Setting,
  Star,
  User,
  UserFilled
} from "@element-plus/icons-vue";
// 模板里不能直接写 "@ /picture/..."：vite 不会把普通字符串当模块解析，
// 必须先 import 拿到真实 URL 再绑定，否则图片 404
import logo from "@/picture/changli.jpg";
import NoticeBell from "@/components/forum/NoticeBell.vue";
import ThemeToggle from "@/components/ThemeToggle.vue";


const store = useStore()
const route = useRoute()
const loading = ref(true)

const searchInput = reactive({
  type: '1',
  text: '',
})

/**
 * 右侧「个人区」只在论坛相关路由下出现。
 * 放在 indexView 而不是各页面里，是为了三栏布局只维护一份；
 * 但个人设置页不需要它，所以按路由判断。
 */
const showSidePanel = computed(() => route.path.startsWith('/index/forum'))

get('/api/user/info', (data) => {
  store.user = data
  loading.value = false
})

function userLogout(){
  logout( () => router.push('/'))
}

/**
 * 管理后台的角色判断。
 *
 * 为什么不放在 router.beforeEach：/api/user/info 是异步拉的，守卫执行时
 * store.user.role 可能还是空串，会把合法的管理员一起弹出去。
 * indexView 只在 info 返回后才渲染，判断时机刚好。
 * 真正的权限防线在后端（/api/admin/** 限 ROLE_admin）。
 */
function checkAdminAccess() {
  if (route.path.startsWith('/index/admin') && store.user.role !== 'admin') {
    ElMessage.warning('没有权限访问管理后台')
    router.push('/index/forum')
  }
}

// 用户信息加载完成后先判一次（覆盖「刷新后直接落在管理路由」的情况），
// 之后每次路由变化再判
watch(loading, value => {
  if (!value) checkAdminAccess()
}, {immediate: true})
watch(() => route.path, checkAdminAccess)

/** 右栏「发表帖子」：跳到独立的发帖页（原来是个 720px 的弹窗，写长文太憋屈） */
function openPostEditor() {
  router.push('/index/forum/new')
}

/** 顶栏搜索：回车跳到搜索结果页（复用同一个路由，改 query 即可触发重新查询） */
function doSearch() {
  const keyword = searchInput.text.trim()
  if (!keyword) return
  router.push({path: '/index/forum/search', query: {keyword}})
  searchInput.text = ''
}



</script>

<template>
  <div class="main-content" v-loading="loading" element-loading-text="正在进入，请稍后...">
    <el-container style="height: 100%" v-if="!loading">
      <el-header class="main-content-header">
        <el-image class="logo" :src="logo" alt="logo"></el-image>
        <div style="flex: 1; padding: 0 20px;text-align: center ">
          <el-input v-model="searchInput.text" style="width: 100%;max-width: 500px"
                    placeholder="搜索帖子" clearable @keyup.enter="doSearch">
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>
        <div style="flex: 1" class="user-info">
          <ThemeToggle/>
          <NoticeBell/>
          <div class="profile">
            <div>{{ store.user.username }}</div>
            <div>{{ store.user.email }}</div>
          </div>
          <el-dropdown>
            <el-avatar src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
            <template #dropdown>
              <el-dropdown-item>
                <el-icon><Operation/></el-icon>
                个人设置
              </el-dropdown-item>
              <el-dropdown-item disabled>
                <el-icon><Star /></el-icon>
                收藏列表
              </el-dropdown-item>
              <el-dropdown-item @click="userLogout" divided>
                <el-icon><Back/></el-icon>
                退出登录
              </el-dropdown-item>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-container style="height: calc(100vh - 55px)">
        <!-- 左栏：功能导航 -->
        <el-aside width="180px" class="nav-aside">
          <el-scrollbar>
            <el-menu router :default-active="route.path" style="min-height: calc(100vh - 55px)">
              <el-sub-menu index="forum">
                <template #title>
                  <el-icon><ChatLineSquare/></el-icon>
                  <span>校园论坛</span>
                </template>
                <el-menu-item index="/index/forum">
                  <el-icon><Promotion/></el-icon>
                  <span>广场</span>
                </el-menu-item>
                <el-menu-item index="/index/forum/mine">
                  <el-icon><Star/></el-icon>
                  <span>我的帖子</span>
                </el-menu-item>
                <el-menu-item index="/index/forum/collect">
                  <el-icon><Star/></el-icon>
                  <span>我的收藏</span>
                </el-menu-item>
              </el-sub-menu>

              <el-sub-menu index="setting">
                <template #title>
                  <el-icon><Setting/></el-icon>
                  <span>个人设置</span>
                </template>
                <el-menu-item index="/index/user-setting">
                  <el-icon><User/></el-icon>
                  <span>个人信息设置</span>
                </el-menu-item>
                <el-menu-item :index="'/index/user/' + store.user.id">
                  <el-icon><Postcard/></el-icon>
                  <span>我的主页</span>
                </el-menu-item>
              </el-sub-menu>

              <!-- 管理后台入口：只有 admin 角色才渲染（后端另有校验） -->
              <el-sub-menu v-if="store.user.role === 'admin'" index="admin">
                <template #title>
                  <el-icon><Management/></el-icon>
                  <span>系统管理</span>
                </template>
                <el-menu-item index="/index/admin/user">
                  <el-icon><UserFilled/></el-icon>
                  <span>用户管理</span>
                </el-menu-item>
                <el-menu-item index="/index/admin/forum">
                  <el-icon><ChatLineSquare/></el-icon>
                  <span>论坛管理</span>
                </el-menu-item>
                <el-menu-item index="/index/admin/email">
                  <el-icon><Message/></el-icon>
                  <span>邮件记录</span>
                </el-menu-item>
              </el-sub-menu>
            </el-menu>
          </el-scrollbar>
        </el-aside>

        <!-- 中栏：内容 -->
        <el-main class="main-content-page">
          <el-scrollbar style="height: calc(100vh - 55px)">
            <!-- 限宽容器：内容列不随屏幕无限变宽，行长更好读（图片另有 560px 上限） -->
            <div class="content-wrap">
              <router-view v-slot="{ Component }">
                <transition name="el-fade-in-linear" mode="out-in">
                  <component :is="Component" style="height: 100%"/>
                </transition>
              </router-view>
            </div>
          </el-scrollbar>
        </el-main>

        <!-- 右栏：个人区（仅论坛路由） -->
        <el-aside v-if="showSidePanel" width="220px" class="side-panel">
          <el-scrollbar style="height: calc(100vh - 55px)">
            <div class="side-block">
              <el-button type="primary" style="width: 100%" @click="openPostEditor">
                <el-icon><Plus/></el-icon>
                <span style="margin-left: 4px">发表帖子</span>
              </el-button>
            </div>

            <div class="side-block">
              <div class="side-title">我的</div>
              <div class="side-item" @click="router.push('/index/forum/mine')">我的帖子</div>
              <div class="side-item" @click="router.push('/index/forum/collect')">我的收藏</div>
            </div>
          </el-scrollbar>
        </el-aside>
      </el-container>
    </el-container>

  </div>
</template>

<style lang="less" scoped>

.main-content-page {
  padding: 0;
  background-color: #f7f8fa;
}

/*
 * 内容列限宽并居中。
 * 中栏是 flex: 1，屏幕越宽它越宽 —— 不限的话正文会拉成一行 1200px+，很难读。
 * 注意限宽的是【里面的内容】，不是 el-main 本身：否则背景色只铺中间一条，两边露白。
 * 980px 是个折中：论坛列表和详情都舒服，管理后台那些多列表格也放得下。
 */
.content-wrap {
  max-width: 980px;
  margin: 0 auto;
}

.dark .main-content-page {
  background-color: #242628;
}

.main-content {
  height: 100vh;
  width: 100vw;
}

.main-content-header {
  border-bottom: solid 1px var(--el-border-color);
  height: 55px;
  display: flex;
  align-items: center;
  box-sizing: border-box;

  .logo {
    height: 34px;
    width: 34px;
    border-radius: 50%;
    flex-shrink: 0;
  }

  .user-info {
    display: flex;
    justify-content: flex-end;
    align-items: center;
    .el-avatar:hover {
      cursor: pointer;
    }

    .profile {
      text-align: right;
      margin-right: 20px;

      :first-child {
        font-size: 18px;
        font-weight: bold;
        line-height: 20px;

      }

      :last-child {
        font-size: 10px;
        color: grey;
      }
    }
  }
}

.nav-aside {
  border-right: solid 1px var(--el-border-color);
}

.side-panel {
  border-left: solid 1px var(--el-border-color);
  background-color: #fff;
}

.dark .side-panel {
  background-color: #1f2022;
}

.side-block {
  padding: 16px;

  & + .side-block {
    border-top: solid 1px var(--el-border-color);
  }
}

.side-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.side-title {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.side-item {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
  line-height: 32px;
  cursor: pointer;
  border-radius: 4px;
  padding: 0 6px;
  margin: 0 -6px;

  &:hover {
    background: var(--el-fill-color-light);
    color: var(--el-color-primary);
  }

  &.is-disabled {
    color: var(--el-text-color-disabled);
    cursor: default;
  }
}

.side-count {
  color: var(--el-text-color-secondary);
}

/* 窄屏时收起右栏，避免中栏被挤扁 */
@media (max-width: 1100px) {
  .side-panel {
    display: none;
  }
}

</style>
