package com.example.service.impl;

import com.example.entity.vo.request.AiChatVO;
import com.example.service.AiService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 问答实现。
 *
 * ⚠️ 这里**没有出现任何厂商名字**：连哪个服务完全由配置决定
 * （application-dev.yml 的 spring.ai.openai.base-url / chat.options.model），
 * 所以把 DeepSeek 换成百炼、Ollama、OpenAI 时，这个类一个字都不用改。
 */
@Slf4j
@Service
public class AiServiceImpl implements AiService {

    /** 占位 key 的前缀，与 application-dev.yml 里的默认值一致 */
    private static final String PLACEHOLDER = "placeholder";

    private static final String SYSTEM_PROMPT = """
            你是「校园论坛」的 AI 智能体，帮助同学们解答技术问题、总结帖子内容、提供学习建议。

            要求：
            1. 用简体中文回答，语气友好、简洁，不要啰嗦。
            2. 回答技术问题时给出具体做法或代码要点，不要只讲概念。
            3. 不确定的内容要明说，不要编造。
            """;

    @Value("${spring.ai.openai.api-key:}")
    String apiKey;

    @Resource
    ChatClient.Builder chatClientBuilder;

    /** ChatClient 是无状态的，构造一次复用即可（每次请求只是传不同的 messages） */
    private volatile ChatClient chatClient;

    @Override
    public boolean available() {
        return apiKey != null
                && !apiKey.isBlank()
                && !apiKey.startsWith(PLACEHOLDER);
    }

    @Override
    public AiReply chat(List<AiChatVO.Message> history, String message, String context) {
        if (!available()) {
            return null;
        }
        try {
            List<Message> messages = buildMessages(history, message, context);

            ChatResponse response = client().prompt()
                    .messages(messages)
                    .call()
                    .chatResponse();
            if (response == null || response.getResult() == null) {
                return null;
            }
            String content = response.getResult().getOutput().getText();
            // token 用量：Spring AI 从响应元数据里带回来，拿不到就按 0 记（不影响回复）
            long tokens = 0;
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                Integer total = response.getMetadata().getUsage().getTotalTokens();
                tokens = total == null ? 0 : total;
            }
            return new AiReply(content, tokens);
        } catch (Exception e) {
            // 网络不通、key 无效、额度用尽等都归到这里，返回 null 由 Controller 转成友好提示
            log.error("AI 调用失败：{}", e.getMessage());
            return null;
        }
    }

    @Override
    public Flux<String> chatStream(List<AiChatVO.Message> history, String message, String context) {
        if (!available()) {
            return null;
        }
        try {
            return client().prompt()
                    .messages(buildMessages(history, message, context))
                    .stream()
                    .content();
        } catch (Exception e) {
            log.error("AI 流式调用失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 组装消息列表：系统提示词 + 历史对话 + 本次提问。
     * 流式与非流式共用，避免两处逻辑走偏。
     *
     * context 非空表示这是 RAG 问答 —— 它已经是一段完整的提示词（含论坛资料和用户问题），
     * 所以直接作为最后一轮提问发出去，不再额外拼 message。
     */
    private List<Message> buildMessages(List<AiChatVO.Message> history, String message, String context) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));

        // 历史对话：让模型知道上文，实现多轮
        if (history != null) {
            for (AiChatVO.Message m : history) {
                if (m == null || m.getContent() == null || m.getContent().isBlank()) {
                    continue;
                }
                if ("assistant".equals(m.getRole())) {
                    messages.add(new AssistantMessage(m.getContent()));
                } else {
                    messages.add(new UserMessage(m.getContent()));
                }
            }
        }
        messages.add(new UserMessage(context != null && !context.isBlank() ? context : message));
        return messages;
    }

    /** 延迟构造：避免没配 key 时（AutoConfiguration 仍会创建 Builder）在启动阶段就出问题 */
    private ChatClient client() {
        ChatClient local = chatClient;
        if (local == null) {
            synchronized (this) {
                local = chatClient;
                if (local == null) {
                    local = chatClientBuilder.build();
                    chatClient = local;
                }
            }
        }
        return local;
    }
}
