package com.example.repository;

import com.example.entity.dto.TopicIndex;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface TopicIndexRepository extends ElasticsearchRepository<TopicIndex, Integer> {

    /**
     * 多字段检索：标题权重是正文的 2 倍（title^2），并过滤掉已隐藏的帖子。
     *
     * 用 @Query 而不是方法名派生：后者拼不出「权重 + filter」这种组合，
     * 而且一旦加上 invisible 条件，方法名会长到没法读。
     *
     * ⚠️ type = phrase_prefix 不能省：
     *   standard 分析器把英文按【整词】切（"xixi" 就是一个 token），
     *   默认的 match 查询要求 token 完全相等，于是搜 "xi" 匹配不到 "xixi"。
     *   加上 phrase_prefix 后允许「最后一个词做前缀匹配」，搜 "xi" 就能命中 "xixi"。
     *   （中文本来就逐字切分，不受此影响 —— 搜"手机"一直能匹配"手机保护壳"。）
     */
    @Query("""
            {
              "bool": {
                "must": [
                  {
                    "multi_match": {
                      "query": "?0",
                      "fields": ["title^2", "text"],
                      "type": "phrase_prefix"
                    }
                  }
                ],
                "filter": [
                  { "term": { "invisible": 0 } }
                ]
              }
            }
            """)
    Page<TopicIndex> searchByKeyword(String keyword, Pageable pageable);
}
