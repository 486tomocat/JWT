import {defineStore} from "pinia";

export const useStore = defineStore('general', {
    state: () => {
        return {
            user: {
                id: null,
                username: '',
                email: '',
                role: '',
                registerTime: null
            },

            /**
             * 论坛数据版本号：发帖 / 编辑 / 评论成功后就 +1。
             * 列表页和详情页 watch 它来重新拉数据，比整页刷新优雅，
             * 也避免引入一套前端缓存同步逻辑。
             */
            forumRefreshTick: 0
        }
    }
})
