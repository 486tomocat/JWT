import {get, post} from "@/net";

/**
 * 论坛接口封装。
 *
 * 约定（与项目既有 net/api/*.js 一致）：
 * 这里只负责「拼路径 + 发请求 + 把结果回调出去」，不写业务逻辑
 * （排序、过滤、提示文案都留给页面），这样接口变了只改这一个文件。
 *
 * 回调风格：success(data) / failure(message, code, url)，由 net/index.js 统一分流。
 */

/** 分类列表 */
export const apiForumTypes = (success, failure) =>
    get('/api/forum/types', success, failure)

/** 帖子分页列表：page 从 0 开始；type = 0 表示「全部」 */
export const apiForumTopicList = (page, type, success, failure) =>
    get(`/api/forum/list-topic?page=${page}&type=${type}`, success, failure)

/**
 * 关键词搜索（后端走 Elasticsearch）。
 * 与 /list-topic 不同：这个接口**带 total**（ES 的命中数），响应是
 * {code, data: {list, total, page}, message}
 */
export const apiForumSearch = (keyword, page, success, failure) =>
    get(`/api/forum/search?keyword=${encodeURIComponent(keyword)}&page=${page}`, success, failure)

/** 置顶帖 */
export const apiForumTopTopics = (success, failure) =>
    get('/api/forum/top-topic', success, failure)

/** 帖子详情：返回体里带当前登录用户对该帖的点赞/收藏状态 */
export const apiForumTopic = (tid, success, failure) =>
    get(`/api/forum/topic?tid=${tid}`, success, failure)

/** 评论分页：page 从 0 开始，后端每页 10 条 */
export const apiForumComments = (tid, page, success, failure) =>
    get(`/api/forum/comments?tid=${tid}&page=${page}`, success, failure)

/** 点赞 / 收藏：type = like | collect，state = true（选中）/ false（取消） */
export const apiForumInteract = (tid, type, state, success, failure) =>
    get(`/api/forum/interact?tid=${tid}&type=${type}&state=${state}`, success, failure)

// ==================== 发帖 / 编辑 ====================

/** 发帖：content 必须传 Quill Delta 的**对象**（不是字符串） */
export const apiForumCreateTopic = (data, success, failure) =>
    post('/api/forum/create-topic', data, success, failure)

/** 编辑帖子：data 需含 id / type / title / content（Delta 对象） */
export const apiForumUpdateTopic = (data, success, failure) =>
    post('/api/forum/update-topic', data, success, failure)

/** 删除自己的帖子 */
export const apiForumDeleteTopic = (tid, success, failure) =>
    get(`/api/forum/delete-topic?tid=${tid}`, success, failure)

/** 我收藏的帖子：后端返回 TopicPreviewVO，与广场列表同结构，可直接复用 TopicCard */
export const apiForumCollects = (success, failure) =>
    get('/api/forum/collects', success, failure)

/** 我的帖子：后端返回 TopicPreviewVO，与广场列表同结构，可直接复用 TopicCard */
export const apiForumUserTopic = (success, failure) =>
    get('/api/forum/user-topic', success, failure)

// ==================== 评论 ====================

/** 发评论：content 要传 Delta 的**字符串**（和发帖刚好相反，别搞混） */
export const apiForumAddComment = (data, success, failure) =>
    post('/api/forum/add-comment', data, success, failure)

/** 删除自己的评论 */
export const apiForumDeleteComment = (id, success, failure) =>
    get(`/api/forum/delete-comment?id=${id}`, success, failure)

// ==================== 站内通知 ====================

export const apiNotificationList = (success, failure) =>
    get('/api/notification/list', success, failure)

export const apiNotificationDelete = (id, success, failure) =>
    get(`/api/notification/delete?id=${id}`, success, failure)

export const apiNotificationDeleteAll = (success, failure) =>
    get('/api/notification/delete-all', success, failure)
