import {get, post} from "@/net";

/**
 * 管理后台接口封装（前缀 /api/admin）。
 *
 * 这组接口在 SecurityConfiguration 里被限制为 admin 角色，普通用户调用会拿到 401/403。
 * 前端只负责「别让入口出现在普通人眼前」，真正的权限校验在后端。
 *
 * ⚠️ 分页响应结构与论坛列表接口不同：
 *   管理后台统一是 {code, data:{list, total, page}, message}（有 total，可以用 el-pagination）；
 *   论坛的 /list-topic 只返回当页数组、没有 total。
 */

// ==================== 用户管理 ====================

/** 用户分页列表。keyword 支持「用户名模糊」，纯数字时额外按用户 id 精确匹配 */
export const apiAdminUserList = (page, size, keyword, success, failure) =>
    get(`/api/admin/user/list?page=${page}&size=${size}&keyword=${encodeURIComponent(keyword ?? '')}`,
        success, failure)

/** 用户详情：返回 {account, detail}（注意是单数 detail） */
export const apiAdminUserDetail = (id, success, failure) =>
    get(`/api/admin/user/detail?id=${id}`, success, failure)

/** 保存用户资料 / 角色 / 禁言 / 封禁（管理员不能改自己的账号，后端会拦） */
export const apiAdminUserSave = (data, success, failure) =>
    post('/api/admin/user/save', data, success, failure)

/** 修改指定用户的密码（走这里就绕开了邮箱验证码，属于管理手段） */
export const apiAdminUserChangePassword = (data, success, failure) =>
    post('/api/admin/user/change-password', data, success, failure)

// ==================== 论坛管理 ====================

export const apiAdminTopicList = (page, size, keyword, success, failure) =>
    get(`/api/admin/forum/list?page=${page}&size=${size}&keyword=${encodeURIComponent(keyword ?? '')}`,
        success, failure)

export const apiAdminTopicDelete = (tid, success, failure) =>
    get(`/api/admin/forum/delete?tid=${tid}`, success, failure)

/**
 * 置顶 / 锁定 / 隐藏。
 * 后端三个接口的入参完全一样（{tid, status}），所以这里用一个函数按 action 分发。
 * action 取值：top | locked | invisible
 */
export const apiAdminTopicStatus = (action, tid, status, success, failure) =>
    post(`/api/admin/forum/${action}`, {tid, status}, success, failure)

export const apiAdminProhibitedList = (success, failure) =>
    get('/api/admin/forum/prohibited-list', success, failure)

/** 保存违禁词表。后端语义是「整体覆盖」，所以前端传的是完整的字符串数组 */
export const apiAdminProhibitedSave = (words, success, failure) =>
    post('/api/admin/forum/prohibited-save', words, success, failure)

export const apiAdminTypeCreate = (data, success, failure) =>
    post('/api/admin/forum/create-type', data, success, failure)

export const apiAdminTypeUpdate = (data, success, failure) =>
    post('/api/admin/forum/update-type', data, success, failure)

export const apiAdminTypeDelete = (id, success, failure) =>
    get(`/api/admin/forum/delete-type?tid=${id}`, success, failure)

/** 把某个帖子挪到另一个分类下 */
export const apiAdminChangeTopicType = (tid, type, success, failure) =>
    get(`/api/admin/forum/change-topic-type?tid=${tid}&type=${type}`, success, failure)

// ==================== 邮件记录 ====================

export const apiAdminEmailList = (page, size, success, failure) =>
    get(`/api/admin/email/list?page=${page}&size=${size}`, success, failure)

/** 重发某条邮件记录（失败的邮件靠它补救） */
export const apiAdminEmailResend = (id, success, failure) =>
    get(`/api/admin/email/resend?id=${id}`, success, failure)
