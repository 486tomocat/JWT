package com.example.repository;

import com.example.entity.dto.TopicIndex;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

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

    /**
     * 向量近邻检索（RAG 用）。
     *
     * ES 的 kNN 查询：在向量空间里找离 query_vector 最近的 k 篇。
     *   - num_candidates 是"候选池"，比 k 大一些能让结果更准（ES 先粗筛 50 个再精排）
     *   - filter 掉已隐藏的帖子
     *   - ?0 = 查询向量（float[]），?1 = k，?2 = 候选数
     *
     * ⚠️ 用 List<Float> 而不是 float[]：Spring Data ES 对数组参数的序列化更稳。
     */
    @Query("""
            {
              "knn": {
                "field": "denseVector",
                "query_vector": ?0,
                "k": ?1,
                "num_candidates": ?2,
                "filter": { "term": { "invisible": 0 } }
              }
            }
            """)
    List<TopicIndex> knnSearch(List<Float> queryVector, int k, int numCandidates);
}
