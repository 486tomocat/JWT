package com.example.entity.vo.request;

import jakarta.validation.constraints.Email;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class EmailResetVO {
    @Email(message = "邮箱格式不正确")
    String email;

    @Length(min = 6, max = 6, message = "验证码为 6 位数字")
    String code;

    @Length(min = 5, max = 20, message = "密码长度必须在 5-20 之间")
    String password;
}
