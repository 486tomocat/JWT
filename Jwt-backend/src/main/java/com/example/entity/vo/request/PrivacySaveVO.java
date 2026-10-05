package com.example.entity.vo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 修改某一个隐私开关：type 是字段名，status = true 表示公开 */
@Data
public class PrivacySaveVO {

    @NotBlank(message = "字段名不能为空")
    @Pattern(regexp = "(phone|email|wx|qq|gender)", message = "不支持的字段名")
    String type;

    boolean status;
}
