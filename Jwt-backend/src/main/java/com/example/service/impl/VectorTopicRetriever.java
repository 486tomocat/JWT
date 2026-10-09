package com.example.service.impl;

import com.example.entity.dto.Topic;
import com.example.entity.dto.TopicIndex;
import com.example.mapper.TopicMapper;
import com.example.repository.TopicIndexRepository;
import com.example.service.TopicRetriever;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 向量检索实现（RAG 的检索层）。
 *
 * 流程：问题 → 向量化 → ES kNN 近邻 → 回 MySQL 取帖子详情。
 *
 * 为什么不直接返回 ES 里的文档：ES 里只索引了 id/title/text（为了索引小），
 * 正文还得回业务库取 —— 和搜索功能用的是同一套思路。
 */
@Slf4j
@Service
public class VectorTopicRetriever implements TopicRetriever {

    /** 候选池大小：ES 先粗筛这么多个，再精排出 topK。比 topK 大一些结果更准 */
    private static final int NUM_CANDIDATES = 50;

    @Resource
    EmbeddingModel embeddingModel;

    @Resource
    TopicIndexRepository topicIndexRepository;

    @Resource
    TopicMapper topicMapper;

    @Override
    public List<Topic> retrieve(String question, int topK) {
        if (question == null || question.isBlank() || topK <= 0) {
            return List.of();
        }
        try {
            // ① 把问题转成向量（用的是配置里的 text-embedding-v3，1024 维）
            float[] embedding = embeddingModel.embed(question);
            List<Float> queryVector = new ArrayList<>(embedding.length);
            for (float v : embedding) {
                queryVector.add(v);
            }

            // ② ES kNN 检索
            List<TopicIndex> hits = topicIndexRepository.knnSearch(queryVector, topK, NUM_CANDIDATES);
            if (hits == null || hits.isEmpty()) {
                return List.of();
            }

            // ③ 回 MySQL 取完整帖子，并按 ES 的相关度顺序重排
            List<Integer> ids = hits.stream().map(TopicIndex::getId).toList();
            Map<Integer, Topic> byId = topicMapper.selectBatchIds(ids).stream()
                    .collect(Collectors.toMap(Topic::getId, t -> t));

            return ids.stream()
                    .map(byId::get)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            // 检索失败不该让整个问答崩掉：降级成"没有参考资料"，让模型自由回答
            log.error("向量检索失败，本次问答将不带论坛上下文：{}", e.getMessage());
            return List.of();
        }
    }
}
