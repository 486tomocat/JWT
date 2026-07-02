<script setup lang="ts">
import {logout,get} from "@/net"
import router from "@/router";
import {useStore} from "@/store"
import {reactive, ref} from "vue";
import {
  Back,
  ChatLineSquare,
  DataBoard,
  Location,
  Medal, Message, Operation, Promotion, Search,
  Service,
  Setting,
  Star,
  User,
  Warning
} from "@element-plus/icons-vue";

const store = useStore()
const loading = ref(true)

const searchInput = reactive({
  type: '1',
  text:'',

})

get('/api/user/info', (data) => {
  store.user = data
  loading.value = false
})

function userLogout(){
  logout( () => router.push('/'))
}

</script>

<template>
  <div class="main-content" v-loading="loading" element-loading-text="正在进入，请稍后...">
    <el-container style="height: 100%" v-if="!loading">
      <el-header class="main-content-header">
        <el-image class="logo" src="https://element-plus.org/images/element-plus-logo.svg"></el-image>
        <div style="flex: 1; padding: 0 20px;text-align: center ">
          <el-input v-model="searchInput.text" style="width: 100%;max-width: 500px" placeholder="搜索想要的wiki。。。">
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
            <template #append>
              <el-select style="width: 120px" v-model="searchInput.type">
                <el-option value="1" label="FPS"/>
                <el-option value="2" label="恐怖游戏"/>
                <el-option value="3" label="沙盒建造"/>
                <el-option value="4" label="模拟经营"/>
              </el-select>
            </template>
          </el-input>
        </div>
        <div style="flex: 1" class="user-info">
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
              <el-dropdown-item>
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
        <el-aside width="230px">
          <el-scrollbar>
            <el-menu
                router
                :default-active="$route.path"
                style=" min-height: calc(100vh - 55px)">
              <el-sub-menu index="1">
                <template #title>
                  <el-icon><location/></el-icon>
                  <span>校园论坛<b/></span>
                </template>
                <el-menu-item index="1-1">
                  <template #title>
                    <el-icon><ChatLineSquare/></el-icon>
                    广场
                  </template>
                </el-menu-item>
                <el-menu-item>
                  <template #title>
                    <el-icon><Star /></el-icon>
                    收藏贴
                  </template>
                </el-menu-item>
                <el-menu-item>
                  <template #title>
                    <el-icon><Service /></el-icon>
                    客服
                  </template>
                </el-menu-item>
                <el-menu-item>
                  <template #title>
                    <el-icon><Medal /></el-icon>
                    赞助作者
                    <el-tag style="margin-left: 10px" size="small">成为会员</el-tag>
                  </template>
                </el-menu-item>
              </el-sub-menu>
              <el-sub-menu index="2">
                <template #title>
                  <el-icon><Promotion /></el-icon>
                  <span>探索与发现<b/></span>
                </template>
                <el-menu-item>
                  <template #title>
                    <el-icon><Medal /></el-icon>
                    竞赛查询
                  </template>
                </el-menu-item>
                <el-menu-item>
                  <template #title>
                    <el-icon><DataBoard /></el-icon>
                    班级课程
                  </template>
                </el-menu-item>
              </el-sub-menu>
              <el-sub-menu index="3">
                <template #title>
                  <el-icon><Setting /></el-icon>
                  <span>个人设置<b/></span>
                </template>
                <el-menu-item index="/index/user-setting">
                  <template #title>
                    <el-icon><User /></el-icon>
                    个人信息设置
                  </template>
                </el-menu-item>
                <el-menu-item>
                  <template #title>
                    <el-icon><Warning /></el-icon>
                    账号安全设置
                  </template>
                </el-menu-item>
              </el-sub-menu>
            </el-menu>
          </el-scrollbar>
        </el-aside>
        <el-main class="main-content-page">
          <el-scrollbar style="height: calc(100vh - 55px)">
            <router-view v-slot="{ Component }">
              <transition name="el-fade-in-linear" mode="out-in">
                <component :is="Component" style="height: 100%"/>
              </transition>
            </router-view>
          </el-scrollbar>
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<style lang="less" scoped>

.main-content-page {
  padding: 0;
  background-color: #f7f8fa;
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
    height: 32px;
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




</style>