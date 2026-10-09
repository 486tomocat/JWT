package com.example.service;

import com.example.entity.vo.request.AiChatVO;

import java.util.List;

/**
 * AI 问答。
 *
 * 设计上**不认任何厂商**：DeepSeek / 阿里百炼 / Ollama / OpenAI 的 API 都兼容 OpenAI 协议，
 * 具体连哪个由 application-dev.yml 的 spring.ai.openai.{base-url, chat.options.model} 决定，
 * 换服务商**只改配置，这里一行代码都不用动**。
 */
public interface AiService {

    /** API key 是否已配置（没配时走友好提示，而不是抛异常） */
    boolean available();

    /**
     * 模型回复 + 本次消耗的 token 数。
     * 带上 token 是因为额度**按 token 计**（见 AiQuotaService），调用方需要拿它去记账。
     */
    record AiReply(String content, long tokens) {
    }

    /**
     * 单轮问答（自带历史上下文）。
     *
     * @param history 之前的对话（可为 null）
     * @param message 本次提问
     * @param context RAG 组装好的提示词（含论坛参考资料）；为空时走普通问答
     * @return 回复与消耗；失败返回 null
     */
    AiReply chat(List<AiChatVO.Message> history, String message, String context);

    /**
     * 流式问答（SSE）。
     *
     * 返回 Reactor 的 Flux，由 Controller 桥接成 SseEmitter —— 我们用的是 Spring MVC
     * 而不是 WebFlux，所以需要一个"Flux → SseEmitter"的适配。
     *
     * ⚠️ 注意：流式模式下**拿不到 token 用量**（Spring AI 的 stream 不保证返回 usage 元数据），
     * 所以额度按"一次提问"估算扣减，见 AiController。
     *
     * @param history 之前的对话（可为 null）
     * @param message 本次提问
     * @param context RAG 组装好的提示词；为空时走普通问答
     * @return 逐块输出的文本流；未配置 key 时返回 null
     */
    reactor.core.publisher.Flux<String> chatStream(List<AiChatVO.Message> history, String message, String context);
}
