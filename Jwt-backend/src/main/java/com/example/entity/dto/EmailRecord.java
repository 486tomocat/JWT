package com.example.entity.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * 邮件发送记录。
 *
 * 为什么要有这张表：MQ 只是「投递管道」，消息投出去之后是死是活没人知道。
 * 先把记录落库（status=发送中），发送成功改成 1、进死信队列改成 2，
 * 管理后台就能看到每一封邮件的真实状态，也支持「重发」。
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
@TableName("db_email_record")
public class EmailRecord {
    @TableId(type = IdType.AUTO)
    Integer id;
    String email;
    String title;
    String content;
    Date time;
    /** 发信状态: 0.发送中 1.成功 2.失败 */
    Integer status;

    public EmailRecord(String email, String title, String content) {
        this.email = email;
        this.title = title;
        this.content = content;
        this.status = 0;
        this.time = new Date();
    }
}
