package com.example.entity.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 帖子评论。quote 指向被回复的评论 id，-1 表示直接回复楼主。
 */
@Data
@TableName("db_topic_comment")
public class TopicComment {

    @TableId(type = IdType.AUTO)
    Integer id;
    Integer uid;
    Integer tid;
    String content;
    Date time;
    Integer quote;
}
