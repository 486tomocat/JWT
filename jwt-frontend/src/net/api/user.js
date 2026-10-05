import {get, post} from "@/net";

/**
 * 用户自身相关接口（/api/user 与 /api/auth 里与账号有关的部分）。
 * 职责边界：论坛在 forum.js，管理后台在 admin.js，这里只放「用户操作自己账号」的接口。
 */

/** 当前登录用户的账号信息（含角色、封禁/禁言状态） */
export const apiUserInfo = (success, failure) =>
    get('/api/user/info', success, failure)

/** 当前登录用户的资料（性别 / 手机 / QQ / 微信 / 简介） */
export const apiUserDetails = (success, failure) =>
    get('/api/user/details', success, failure)

/** 保存资料；用户名被占用时后端返回 400 */
export const apiSaveDetails = (data, success, failure) =>
    post('/api/user/save-details', data, success, failure)

/**
 * 申请邮箱验证码。
 * type = register（注册）| reset（找回密码）| modify（改绑邮箱）
 * 注意：改绑邮箱的验证码是发到**新邮箱**的，用来证明你确实拥有它。
 */
export const apiAskEmailCode = (email, type, success, failure) =>
    get(`/api/auth/ask-code?email=${encodeURIComponent(email)}&type=${type}`, success, failure)

/** 修改绑定邮箱：需要新邮箱收到的验证码 */
export const apiModifyEmail = (data, success, failure) =>
    post('/api/user/modify-email', data, success, failure)

/** 修改密码：需要原密码 */
export const apiChangePassword = (data, success, failure) =>
    post('/api/user/change-password', data, success, failure)

// ==================== 隐私开关 ====================

/** 读取自己的隐私开关（决定个人主页上哪些资料对别人可见） */
export const apiUserPrivacy = (success, failure) =>
    get('/api/user/privacy', success, failure)

/** 修改单个开关：data = {type, status}，type 是字段名，status = true 表示公开 */
export const apiSavePrivacy = (data, success, failure) =>
    post('/api/user/save-privacy', data, success, failure)

// ==================== 个人主页 ====================

/**
 * 某个用户的个人主页。
 * 看别人时，对方设为不公开的字段后端会直接返回 null。
 */
export const apiUserProfile = (id, success, failure) =>
    get(`/api/user/profile?id=${id}`, success, failure)

/** 某个用户发的帖子：不传 uid = 自己；个人主页里传 uid */
export const apiUserTopics = (uid, success, failure) =>
    get(`/api/forum/user-topic?uid=${uid}`, success, failure)
