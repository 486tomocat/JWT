package com.example.service;

import com.example.entity.dto.Topic;

import java.util.List;

/**
 * 帖子搜索（Elasticsearch）。
 *
 * 只负责「找到 id」这一件事 —— 拿到 id 后由 TopicService 回 MySQL 取完整数据。
 * 这样索引可以很小，也不会出现索引与数据库字段不一致的问题。
 */
public interface TopicSearchService {

    /** 全量重建索引（应用启动就绪时调用；ES 不可用时静默跳过，不影响其它功能） */
    void rebuildIndex();

    /** 增量：帖子新增或修改后同步 */
    void saveIndex(Topic topic);

    /** 增量：帖子删除后同步 */
    void deleteIndex(int id);

    /** 搜索命中结果 */
    record SearchHits(List<Integer> ids, long total) {
    }

    /**
     * 按关键词搜索。
     *
     * @param page 0 基页码
     * @param size 每页条数
     */
    SearchHits search(String keyword, int page, int size);
}
