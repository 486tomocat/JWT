package com.example.service;

/**
 * AI 额度（按 token 数、按週限制）。
 *
 * 额度由网站统一提供，用户只需消耗 —— 所以这里没有「配置 key」的概念，
 * 只有「还剩多少」和「记一笔消耗」。
 */
public interface AiQuotaService {

    /** 额度快照 */
    record Quota(long limit, long used, long remaining) {
    }

    /** 查询某用户本週额度情况（会自动处理跨週重置） */
    Quota quota(int uid);

    /** 是否还有额度 */
    boolean allowed(int uid);

    /** 记录一次消耗（在 AI 调用成功之后调用，用真实 token 数扣减） */
    void consume(int uid, long tokens);
}
