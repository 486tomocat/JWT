<script setup>
import {ref} from "vue";
import ForumTopicAdmin from "./section/ForumTopicAdmin.vue";
import ForumTopicTypeAdmin from "./section/ForumTopicTypeAdmin.vue";
import ForumTopicProhibitedAdmin from "./section/ForumTopicProhibitedAdmin.vue";

/** 论坛管理：只负责 Tab 切换，三块内容各自独立成组件 */
const tab = ref('topic')
</script>

<template>
    <div class="admin-page">
        <h3 class="page-title">论坛管理</h3>

        <el-tabs v-model="tab">
            <el-tab-pane label="帖子管理" name="topic">
                <ForumTopicAdmin/>
            </el-tab-pane>
            <el-tab-pane label="分类维护" name="type">
                <ForumTopicTypeAdmin/>
            </el-tab-pane>
            <el-tab-pane label="违禁词" name="prohibited">
                <ForumTopicProhibitedAdmin/>
            </el-tab-pane>
        </el-tabs>
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
</style>
