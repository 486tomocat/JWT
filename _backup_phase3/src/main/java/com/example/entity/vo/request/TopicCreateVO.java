package com.example.entity.vo.request;

import com.alibaba.fastjson2.JSONObject;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class TopicCreateVO {
    @Min(1)
    int type;

    @Length(min = 1, max = 30)
    String title;

    /** Quill 的 Delta JSON，不直接用 String 是为了让服务端能做结构校验/抽取摘要 */
    JSONObject content;
}
