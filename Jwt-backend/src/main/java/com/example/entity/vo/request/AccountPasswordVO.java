package com.example.entity.vo.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/** 管理后台「重置用户密码」的入参 */
@Data
public class AccountPasswordVO {

    @Min(value = 1, message = "用户 id 非法")
    int id;

    @NotBlank(message = "新密码不能为空")
    @Length(min = 6, max = 20, message = "密码长度必须在 6-20 之间")
    String newPassword;
}
