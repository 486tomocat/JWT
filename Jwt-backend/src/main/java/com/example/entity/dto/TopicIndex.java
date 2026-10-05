package com.example.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * 帖子的搜索索引。
 *
 * 设计上**不追求"把帖子完整存一份"**：ES 只负责「按关键词找出相关帖子的 id」，
 * 拿到 id 之后仍然回 MySQL 查详情。这样：
 *   - 索引很小、重建很快；
 *   - 不会出现「ES 里的点赞数和 MySQL 不一致」这类同步问题。
 * 代价是多一次数据库查询 —— 对一页 10 条来说完全值得。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "topic")
public class TopicIndex {

    @Id
    Integer id;

    /** 标题权重更高（查询时用 title^2），所以单独一个字段 */
    @Field(type = FieldType.Text)
    String title;

    @Field(type = FieldType.Text)
    String text;

    /** 隐藏帖不进搜索结果，用它做过滤（Keyword 类型才能精确 term 匹配） */
    @Field(type = FieldType.Integer)
    Integer invisible;
}
