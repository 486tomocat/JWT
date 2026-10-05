package com.example.entity.vo.request;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
@AllArgsConstructor
public class ConfirmResetVO {
    @Email(message = "邮箱格式不正确")
    String email;

    @Length(min = 6, max = 6, message = "验证码为 6 位数字")
    String code;
}
