package com.example.entity.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.entity.BaseData;
import lombok.Data;

import java.util.Date;

/**
 * 站内通知。
 *
 * url 存的是「点开通知要跳到哪」，由产生通知的业务方决定，
 * 这样通知模块本身不需要知道前端路由长什么样。
 */
@Data
@TableName("db_notification")
public class Notification implements BaseData {
    @TableId(type = IdType.AUTO)
    Integer id;
    Integer uid;
    String title;
    String content;
    /** 前端用来决定图标/配色，如 success / warning */
    String type;
    String url;
    /**
     * 注意这里是 Date。
     * 参考实现把 time 写成 String，结果 DTO→VO 拷贝时类型不匹配被静默跳过，
     * 前端拿到的列表时间永远是 null。用 Date 才能真正传出去。
     */
    Date time;
}
