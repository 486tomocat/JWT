package com.example.entity.vo.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/** 管理后台「新增帖子分类」的入参 */
@Data
public class TopicTypeCreateVO {

    @NotBlank(message = "分类名称不能为空")
    @Length(max = 20, message = "分类名称最多 20 个字")
    String name;

    @Length(max = 100, message = "分类描述最多 100 个字")
    String desc;

    /** 前端展示用的十六进制色值，例如 #1E56FF */
    @Length(max = 20, message = "颜色值最多 20 个字符")
    String color;
}
