package com.example.entity.vo.response;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 帖子列表项。text 是从富文本抽取的纯文本预览，images 是正文里的图片地址。
 */
@Data
public class TopicPreviewVO {
    int id;
    int type;
    String title;
    String text;
    List<String> images;
    Date time;
    Integer uid;
    String username;
    String avatar;
    int like;
    int collect;
    int top;
    int locked;
    int invisible;
}
