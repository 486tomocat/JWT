package com.example.entity.vo.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AddCommentVO {
    @Min(1)
    int tid;

    /** 同样是 Quill 的 Delta JSON，这里用字符串接收 */
    String content;

    /** 引用的评论 id，-1 表示直接回复帖子 */
    @Min(-1)
    int quote;
}
