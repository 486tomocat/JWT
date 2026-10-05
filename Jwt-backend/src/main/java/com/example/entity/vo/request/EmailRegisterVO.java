package com.example.entity.vo.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class EmailRegisterVO {

    @Email(message = "邮箱格式不正确")
    @Length(min = 4, message = "邮箱长度不足")
    String email;

    @Length(min = 6, max = 6, message = "验证码为 6 位数字")
    String code;

    @Pattern(regexp = "^[a-zA-Z0-9\\u4e00-\\u9fa5]+$", message = "用户名只能包含中英文和数字")
    @Length(min = 1, max = 10, message = "用户名长度必须在 1-10 之间")
    String username;

    @Length(min = 6, max = 20, message = "密码长度必须在 6-20 之间")
    String password;
}
