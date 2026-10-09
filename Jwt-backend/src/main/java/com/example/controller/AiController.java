package com.example.controller;

import com.example.entity.RestBean;
import com.example.entity.vo.request.AiChatVO;
import com.example.service.AiQuotaService;
import com.example.service.AiService;
import com.example.service.RagService;
import com.example.utils.Const;
import com.example.utils.FlowUtils;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

/**
 * AI 智能体。
 *
 * API key 由**网站统一提供**（在 application-dev.yml 配置），用户不需要也不应该接触它；
 * 每人按 **token 数 / 每週** 限额度，见 AiQuotaService。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
public class AiController {

    /** 每用户每分钟最多提问次数 —— 防止连点误刷（週额度是另一道闸） */
    private static final int LIMIT_PER_MINUTE = 5;

    /** SSE 连接最长保持 5 分钟 —— 大模型长回答够用，也能避免连接泄漏 */
    private static final long STREAM_TIMEOUT_MS = 5 * 60 * 1000L;

    @Resource
    AiService aiService;

    @Resource
    AiQuotaService aiQuotaService;

    @Resource
    RagService ragService;

    @Resource
    FlowUtils flowUtils;

    /** 查询自己本週的额度使用情况（前端显示「本週剩余 xxx / 200000」） */
    @GetMapping("/quota")
    public RestBean<AiQuotaService.Quota> quota(@RequestAttribute(Const.ATTR_USER_ID) int uid) {
        return RestBean.success(aiQuotaService.quota(uid));
    }

    /**
     * 流式问答（SSE）。
     *
     * 为什么用 POST 而不是标准 SSE 的 GET：多轮对话的 history 放在 query 里会超长；
     * 而且前端本来就不能用 EventSource（它不支持自定义请求头，没法带 Authorization），
     * 只能用 fetch + ReadableStream 读流 —— 那用 POST 就理所当然了。
     *
     * ⚠️ 流式模式下拿不到 token 用量（Spring AI 的 stream 不保证回传 usage 元数据），
     * 所以额度按**字符数估算**扣减，见 estimateTokens。
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody @Valid AiChatVO vo,
                                 @RequestAttribute(Const.ATTR_USER_ID) int uid) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);

        String error = preCheck(uid);
        if (error != null) {
            return failStream(emitter, error);
        }

        RagService.RagContext rag = buildRagContext(vo);

        // 引用来源在流开始时先推一条（格式：event=refs，data=JSON），
        // 前端拿到后先渲染"参考了哪几篇"，正文随后逐字流出来
        if (!rag.references().isEmpty()) {
            try {
                emitter.send(SseEmitter.event().name("refs")
                        .data(com.alibaba.fastjson2.JSONObject.toJSONString(rag.references())));
            } catch (Exception ignored) {
            }
        }

        Flux<String> flux = aiService.chatStream(vo.getHistory(), vo.getMessage(), rag.prompt());
        if (flux == null) {
            return failStream(emitter, "AI 暂时没有响应，请稍后再试");
        }

        StringBuilder full = new StringBuilder();
        flux.subscribe(
                chunk -> {
                    full.append(chunk);
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (Exception e) {
                        // 客户端提前断开（比如用户关页面），这不是错误，忽略即可
                    }
                },
                err -> {
                    log.error("SSE 流异常：{}", err.getMessage());
                    try {
                        emitter.send(SseEmitter.event().name("error").data("AI 响应中断，请重试"));
                    } catch (Exception ignored) {
                    }
                    emitter.complete();
                },
                () -> {
                    // 流正常结束：这时候才知道完整回复，按估算扣额度
                    aiQuotaService.consume(uid, estimateTokens(vo.getMessage(), full.toString()));
                    emitter.complete();
                }
        );
        return emitter;
    }

    /**
     * 按需构建 RAG 上下文。
     * 没开 rag 或检索不到内容时返回空上下文（prompt 为 null），调用方自动降级为普通问答。
     */
    private RagService.RagContext buildRagContext(AiChatVO vo) {
        if (vo.getRag() == null || !vo.getRag()) {
            return new RagService.RagContext(null, java.util.List.of());
        }
        return ragService.build(vo.getMessage());
    }

    /** 问答响应：正文 + 引用的帖子 */
    public record AiChatResponse(String reply, java.util.List<RagService.Reference> references) {
    }

    /**
     * 流式模式下用的是估算：中文大致 1 字 1 token，英文约 4 字符 1 token，
     * 这里统一按「字符数 / 2」粗算 —— 宁可少算也不多扣用户额度。
     */
    private long estimateTokens(String question, String answer) {
        int chars = (question == null ? 0 : question.length()) + (answer == null ? 0 : answer.length());
        return Math.max(1, chars / 2L);
    }

    /** 流式接口的前置校验失败：用一条 error 事件告诉前端，而不是改成非 SSE 响应 */
    private SseEmitter failStream(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
        } catch (Exception ignored) {
        }
        emitter.complete();
        return emitter;
    }

    /**
     * 提问前的统一校验（普通接口与流式接口共用）。
     *
     * @return 通过返回 null；不通过返回给用户看的提示
     */
    private String preCheck(int uid) {
        // 没配 key 时给明确指引，而不是抛 500 —— 没配 AI 时整个项目也照常可用
        if (!aiService.available()) {
            return "AI 功能尚未配置，请在 application-dev.yml 中配置 API key";
        }
        if (!flowUtils.limitPeriodCounterCheck(Const.AI_CHAT_COUNTER + uid, LIMIT_PER_MINUTE, 60)) {
            return "提问太频繁了，请稍后再试";
        }
        // 週额度：用完就停（注意是"用完"而非"这一问会超" —— 超一点点由下一次拦住，
        // 不因为估算 token 而拒绝用户已经打好的问题）
        if (!aiQuotaService.allowed(uid)) {
            AiQuotaService.Quota q = aiQuotaService.quota(uid);
            return "本週 AI 额度已用完（" + q.limit() + " tokens），下週一自动重置";
        }
        return null;
    }

    /** 按提示语的类型给个合适的业务码 */
    private int errorCode(int uid) {
        if (!aiService.available()) return 400;
        if (!aiQuotaService.allowed(uid)) return 403;
        return 429;
    }

    @PostMapping("/chat")
    public RestBean<AiChatResponse> chat(@RequestBody @Valid AiChatVO vo,
                                 @RequestAttribute(Const.ATTR_USER_ID) int uid) {
        String error = preCheck(uid);
        if (error != null) {
            return RestBean.failure(errorCode(uid), error);
        }

        RagService.RagContext rag = buildRagContext(vo);
        AiService.AiReply reply = aiService.chat(vo.getHistory(), vo.getMessage(), rag.prompt());
        if (reply == null || reply.content() == null || reply.content().isBlank()) {
            return RestBean.failure(400, "AI 暂时没有响应，请稍后再试");
        }

        // 调用成功才扣额度，用模型回报的真实 token 数
        aiQuotaService.consume(uid, reply.tokens());
        return RestBean.success(new AiChatResponse(reply.content(), rag.references()));
    }
}
