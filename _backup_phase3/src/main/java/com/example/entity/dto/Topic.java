package com.example.entity.dto;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.entity.BaseData;
import lombok.Data;

import java.util.Date;

/**
 * 帖子实体。
 * content 存的是 Quill 富文本的 Delta JSON 字符串（{"ops":[...]}），
 * intro 是从 content 里抽出来的纯文本摘要，用于列表展示和搜索。
 */
@Data
@TableName("db_topic")
public class Topic implements BaseData {

    @TableId(type = IdType.AUTO)
    Integer id;
    String title;
    String intro;
    String content;
    Integer type;
    Date time;
    Integer uid;
    Integer top;
    Integer locked;
    Integer invisible;

    /** 从 Delta JSON 中抽取纯文本，生成摘要 */
    public static String recreateIntro(JSONObject content) {
        StringBuilder builder = new StringBuilder();
        JSONArray ops = content.getJSONArray("ops");
        for (Object op : ops) {
            Object insert = JSONObject.from(op).get("insert");
            if (insert instanceof String text) {
                builder.append(text);
            }
        }
        return builder.toString();
    }

    public void createIntro() {
        this.intro = recreateIntro(JSONObject.parseObject(this.content));
    }
}
