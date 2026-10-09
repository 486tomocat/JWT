package com.example.entity.vo.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.util.List;

/** AI 问答的入参 */
@Data
public class AiChatVO {

    @NotBlank(message = "请输入问题")
    @Length(max = 2000, message = "问题最多 2000 字")
    String message;

    /**
     * 多轮对话历史（**由前端传，后端不落库**）。
     * 前端只传最近若干条即可 —— 传太多既费 token 也没必要。
     */
    List<Message> history;

    /**
     * 是否启用 RAG（检索论坛帖子作为参考资料）。
     * 为 true 时后端会先检索相关帖子、拼进提示词，并把引用来源一起返回。
     */
    Boolean rag;

    @Data
    public static class Message {
        /** user 或 assistant */
        String role;
        String content;
    }
}
