package com.example.entity.vo.request;

import jakarta.validation.constraints.Email;
import org.hibernate.validator.constraints.Length;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 修改绑定邮箱。
 * 验证码是发到「新邮箱」的 —— 能收到就证明用户确实拥有它。
 */
@Data
public class ModifyEmailVO {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    String email;

    @NotBlank(message = "验证码不能为空")
    @Length(min = 6, max = 6, message = "验证码为 6 位数字")
    String code;
}
