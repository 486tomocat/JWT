<script setup lang="ts">

</script>

<template>
  <div style="width: 100vw;height: 100vh;overflow: hidden;display: flex">
    <div style="flex: 1;background-color: black">
      <el-image style="width: 100%;height: 100%" fit="cover"
          src="src/picture/anno.jpg"></el-image>
    </div>
    <div class="welcome-title">
      <div style="font-size: 30px;font-weight: bold">欢迎来到此网站</div>
      <div style="margin-top: 10px">玩的开心，保持进步</div>
      <div style="margin-top: 5px">随时访问</div>

    </div>
    <div class="right-card" >
      <router-view v-slot="{ Component}">
        <transition name="el-fade-in-linear"  mode="out-in">
          <component :is="Component"/>
        </transition>
      </router-view>
    </div>
  </div>
</template>

<style scoped>

.welcome-title{
  position: absolute;
  bottom: 30px;
  left: 30px;
  color:black;
  text-shadow:0 0 10px black;
}

.right-card {
  width: 400px;
  z-index: 1;
  background-color: var(--el-bg-color);
}
</style>