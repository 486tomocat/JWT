import {createRouter, createWebHistory} from "vue-router";
import {unauthorized} from "@/net/index.js";
import {ref} from "vue";



const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes:[
        {
            path:'/',
            name:'welcome',
            component: () => import('@/views/WelcomeView.vue'),
            children:[
                {
                    path:'',
                    name:'welcome-login',
                    component: () => import('@/views/welcome/LonginPage.vue')
                }, {
                    path:'register',
                    name:'welcome-register',
                    component: () => import('@/views/welcome/RegisterPage.vue')
                }, {
                    path:'reset',
                    name:'welcome-reset',
                    component: () => import('@/views/welcome/ResetPage.vue')
                }
            ]
        }, {
            path:'/index',
            name:'index',
            // 本轮只做论坛：进 /index 直接落到广场，否则内容区是空白
            redirect: '/index/forum',
            component: () => import('@/views/indexView.vue'),
            children: [
                {
                    path:`user-setting`,
                    name:`user-setting`,
                    component:() => import('@/views/settings/UserSettings.vue')
                }, {
                    // 个人主页：点帖子里的用户名进来
                    path:'user/:id',
                    name:'user-profile',
                    component:() => import('@/views/user/UserProfile.vue')
                }, {
                    path:'forum',
                    name:'forum',
                    component:() => import('@/views/forum/TopicList.vue')
                }, {
                    // 发帖 / 编辑（独立页面，不再是弹窗）
                    path:'forum/new',
                    name:'forum-new',
                    component:() => import('@/views/forum/TopicEditorPage.vue')
                }, {
                    path:'forum/edit/:tid',
                    name:'forum-edit',
                    component:() => import('@/views/forum/TopicEditorPage.vue')
                }, {
                    path:'forum/search',
                    name:'forum-search',
                    component:() => import('@/views/forum/SearchResult.vue')
                }, {
                    path:'forum/topic/:tid',
                    name:'forum-topic',
                    component:() => import('@/views/forum/TopicDetail.vue')
                }, {
                    path:'forum/mine',
                    name:'forum-mine',
                    component:() => import('@/views/forum/MyTopics.vue')
                }, {
                    path:'forum/collect',
                    name:'forum-collect',
                    component:() => import('@/views/forum/MyCollects.vue')
                }, {
                    // 管理后台：/api/admin/** 在后端限 ROLE_admin。
                    // 前端只用「菜单隐藏 + indexView 里的角色判断」收敛入口，真正的防线在后端。
                    path:'admin/user',
                    name:'admin-user',
                    component:() => import('@/views/admin/UserAdmin.vue')
                }, {
                    path:'admin/forum',
                    name:'admin-forum',
                    component:() => import('@/views/admin/ForumAdmin.vue')
                }, {
                    path:'admin/email',
                    name:'admin-email',
                    component:() => import('@/views/admin/EmailAdmin.vue')
                }
            ]
        }
    ]
})

/**
 * 全局守卫。
 * 用「返回值」而不是老的 next() 回调：next 写法在 vue-router 5 里已弃用，
 * 控制台会一直警告。
 */
router.beforeEach((to) => {
    const isUnauthorized = unauthorized()
    if (to.name?.toString().startsWith('welcome-') && !isUnauthorized) {
        return '/index'
    }
    if (to.fullPath.startsWith('/index') && isUnauthorized) {
        return '/'
    }
    return true
})

export default router