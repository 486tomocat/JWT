package com.example.entity.vo.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class DetailsSaveVO {
    @Pattern(regexp = "^[a-zA-Z0-9\\u4e00-\\u9fa5]+$", message = "用户名只能包含中英文和数字")
    @Length(min = 1, max = 10, message = "用户名长度必须在 1-10 之间")
    String username;

    @Min(value = 0, message = "性别取值非法")
    @Max(value = 1, message = "性别取值非法")
    int gender;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误")
    @Length(max = 11, message = "手机号最多 11 位")
    String phone;

    @Length(max = 13, message = "QQ 号最多 13 位")
    String qq;

    @Length(max = 20, message = "微信号最多 20 位")
    String wx;

    @Length(max = 100, message = "个人简介最多 100 个字")
    String desc;
}
