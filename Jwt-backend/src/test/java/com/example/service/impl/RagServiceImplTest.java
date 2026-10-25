package com.example.service.impl;

import com.example.entity.dto.Topic;
import com.example.service.RagService;
import com.example.service.TopicRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RagServiceImpl 单元测试。
 *
 * 检索层（TopicRetriever）用 Mockito 替身，验证「取几篇、拼多长、怎么降级」这三件事。
 */
class RagServiceImplTest {

    private TopicRetriever retriever;
    private RagServiceImpl ragService;

    @BeforeEach
    void setUp() {
        retriever = mock(TopicRetriever.class);
        ragService = new RagServiceImpl();
        ragService.topicRetriever = retriever;
    }

    private Topic topic(int id, String title, String intro) {
        Topic topic = new Topic();
        topic.setId(id);
        topic.setTitle(title);
        topic.setIntro(intro);
        return topic;
    }

    @Test
    @DisplayName("检索为空时不生成 prompt，回退普通问答")
    void emptyHitsFallBack() {
        when(retriever.retrieve(anyString(), eq(3))).thenReturn(List.of());

        RagService.RagContext context = ragService.build("怎么申请奖学金？");

        assertNull(context.prompt(), "没有参考资料时 prompt 必须为 null，由调用方回退");
        assertTrue(context.references().isEmpty());
    }

    @Test
    @DisplayName("命中时按 topK=3 检索，prompt 含标题、摘要、问题与防编造约束")
    void buildsPromptFromHits() {
        when(retriever.retrieve(anyString(), eq(3)))
                .thenReturn(List.of(topic(1, "奖学金申请流程", "每年九月开放，需提交成绩单")));

        RagService.RagContext context = ragService.build("怎么申请奖学金？");

        assertNotNull(context.prompt());
        assertTrue(context.prompt().contains("奖学金申请流程"), "应带上帖子标题作为依据");
        assertTrue(context.prompt().contains("每年九月开放，需提交成绩单"), "应带上摘要内容");
        assertTrue(context.prompt().contains("怎么申请奖学金？"), "应包含用户原始问题");
        assertTrue(context.prompt().contains("不要编造"), "必须保留防编造约束");

        assertEquals(1, context.references().size());
        assertEquals(1, context.references().get(0).id());
        assertEquals("奖学金申请流程", context.references().get(0).title());
    }

    @Test
    @DisplayName("单篇摘要超长会被截断（每篇上限 400 字）")
    void truncatesLongIntro() {
        String longIntro = "字".repeat(1000);
        when(retriever.retrieve(anyString(), eq(3)))
                .thenReturn(List.of(topic(1, "长帖", longIntro)));

        RagService.RagContext context = ragService.build("随便问问");

        assertNotNull(context.prompt());
        assertFalse(context.prompt().contains(longIntro), "1000 字摘要不应被整段塞进 prompt");
        assertTrue(context.prompt().contains("…"), "截断处应有省略标记");
    }

    @Test
    @DisplayName("上下文达到字数上限时提前停止，引用数量随之减少")
    void respectsContextCharBudget() {
        String longTitle = "题".repeat(100);
        String longIntro = "x".repeat(1000);
        when(retriever.retrieve(anyString(), eq(3))).thenReturn(List.of(
                topic(1, longTitle, longIntro),
                topic(2, longTitle, longIntro),
                topic(3, longTitle, longIntro)));

        RagService.RagContext context = ragService.build("问题");

        verify(retriever).retrieve("问题", 3);
        assertEquals(2, context.references().size(),
                "单块约 511 字，两块已 1022 字，第三块会超出 1500 字上限，应被丢弃");
    }
}
