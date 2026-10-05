package com.example.entity.vo.request;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;
import lombok.Data;

/**
 * 修改密码。
 * 字段名统一用驼峰（参考项目写的是 new_password），与项目其它 VO 保持一致。
 */
@Data
public class ChangePasswordVO {

    @NotBlank(message = "请输入原密码")
    String password;

    @NotBlank(message = "请输入新密码")
    @Length(min = 6, max = 20, message = "新密码长度必须在 6-20 之间")
    String newPassword;
}
