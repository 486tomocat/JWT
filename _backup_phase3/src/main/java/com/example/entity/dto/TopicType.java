package com.example.entity.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.entity.BaseData;
import lombok.Data;

/**
 * 帖子分类。
 * 注意：desc 是 MySQL 的保留字，必须用反引号包起来，否则生成的 SQL 会语法错误。
 */
@Data
@TableName("db_topic_type")
public class TopicType implements BaseData {

    Integer id;
    String name;

    @TableField("`desc`")
    String desc;

    String color;
}
