import {get, post} from "@/net";

/**
 * 查询本週 AI 额度使用情况。
 * 返回 { limit, used, remaining }（单位：token）
 */
export const apiAiQuota = (success, failure) => get('/api/ai/quota', success, failure)

/**
 * 向 AI 提问。
 *
 * @param message 本次问题
 * @param history 之前的对话（只传最近若干条，后端不落库）
 */
export const apiAiChat = (message, history, success, failure) =>
    post('/api/ai/chat', {message, history}, success, failure)
