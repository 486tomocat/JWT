package com.example.service.impl;

import com.example.entity.dto.Topic;
import com.example.service.RagService;
import com.example.service.TopicRetriever;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class RagServiceImpl implements RagService {

    /** 最多引用几篇帖子。多了既费 token 也容易让模型被无关内容带偏 */
    private static final int TOP_K = 3;

    /**
     * 参考资料的总长度上限（字符）。
     * 每篇只取标题 + 摘要（发帖时生成好的 intro），不塞整篇正文 ——
     * 一次 RAG 问答的 token 消耗控制在 2000 左右。
     */
    private static final int MAX_CONTEXT_CHARS = 1500;

    @Resource
    TopicRetriever topicRetriever;

    @Override
    public RagContext build(String question) {
        List<Topic> topics = topicRetriever.retrieve(question, TOP_K);
        if (topics.isEmpty()) {
            return new RagContext(null, List.of());
        }

        StringBuilder context = new StringBuilder();
        List<Reference> references = new ArrayList<>();
        int used = 0;

        for (Topic topic : topics) {
            String intro = topic.getIntro() == null ? "" : topic.getIntro();
            // 单篇摘要也截断一下，避免某一篇特别长把预算吃光
            if (intro.length() > 400) {
                intro = intro.substring(0, 400) + "…";
            }
            String block = "【标题】" + topic.getTitle() + "\n【摘要】" + intro + "\n";

            if (used + block.length() > MAX_CONTEXT_CHARS) {
                break;
            }
            context.append(block).append("\n");
            used += block.length();
            references.add(new Reference(topic.getId(), topic.getTitle()));
        }

        if (references.isEmpty()) {
            return new RagContext(null, List.of());
        }

        String prompt = """
                你是校园论坛的 AI 助手。下面提供了论坛里与用户问题相关的帖子，请你**优先依据这些资料**回答。
                如果资料不足以回答，可以用你已有的知识补充，但要说明哪部分是你补充的、论坛里并没有。
                不要编造论坛里不存在的内容。

                ===== 论坛相关资料 =====
                %s======================

                用户问题：%s
                """.formatted(context, question);

        log.debug("RAG 命中 {} 篇帖子，上下文 {} 字", references.size(), used);
        return new RagContext(prompt, references);
    }
}
