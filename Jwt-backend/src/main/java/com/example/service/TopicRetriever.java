package com.example.service;

import com.example.entity.dto.Topic;

import java.util.List;

/**
 * 检索层：从论坛帖子里找出与问题相关的若干篇（RAG 的 "R"）。
 *
 * 抽成接口是为了**留出替换空间**：现在做的是向量检索（语义匹配强），
 * 如果哪天 embedding 服务不可用或想省钱，加一个基于 BM25 的实现即可，
 * RagService 一行都不用改。
 */
public interface TopicRetriever {

    /**
     * 检索与问题最相关的帖子。
     *
     * @param question 用户提问
     * @param topK     最多返回几篇
     * @return 按相关度从高到低；检索不可用或没有结果时返回空列表（不抛异常）
     */
    List<Topic> retrieve(String question, int topK);
}
