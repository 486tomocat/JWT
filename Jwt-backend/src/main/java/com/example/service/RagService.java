package com.example.service;

import java.util.List;

/**
 * RAG（检索增强生成）：先检索论坛里的相关帖子，再拼成 prompt 交给大模型。
 *
 * 「引用来源」是这个功能最有价值的部分 —— 让用户知道答案有依据、能点进原帖核对，
 * 而不是只能选择相信模型。
 */
public interface RagService {

    /** 被引用的帖子（回传给前端展示） */
    record Reference(int id, String title) {
    }

    /**
     * @param prompt     组装好的完整提示词；没检索到内容时为 null（调用方应回退到普通问答）
     * @param references 本次引用的帖子
     */
    record RagContext(String prompt, List<Reference> references) {
    }

    /** 检索 + 组装。任何失败都降级为「没有参考资料」，不抛异常 */
    RagContext build(String question);
}
