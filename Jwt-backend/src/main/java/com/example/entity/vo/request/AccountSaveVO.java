package com.example.entity.vo.request;

import com.example.entity.dto.AccountDetails;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/**
 * 管理后台「保存用户信息」的入参。
 *
 * 对比参考实现：那里用 @RequestBody JSONObject 手工 getInteger/getString，
 * 字段名写错要到运行时才炸；这里用强类型 VO + Bean Validation，
 * 在进入 Controller 方法之前就完成校验，Controller 只管调 Service。
 *
 * 刻意不包含 password：改密码走单独的 /change-password 接口，
 * 避免「保存资料顺手把密码置空」这类事故。
 */
@Data
public class AccountSaveVO {

    @Min(value = 1, message = "用户 id 非法")
    int id;

    @Length(min = 1, max = 10, message = "用户名长度必须在 1-10 之间")
    @Pattern(regexp = "^[a-zA-Z0-9\\u4e00-\\u9fa5]+$", message = "用户名只能包含中英文和数字")
    String username;

    @Email(message = "邮箱格式不正确")
    String email;

    @Pattern(regexp = "^(user|admin)$", message = "角色只能是 user 或 admin")
    String role;

    /** 禁言：仍可登录浏览，但不能发帖 / 评论 / 编辑 */
    boolean mute;

    /** 封禁：直接拒绝登录，并作废已签发的令牌 */
    boolean banned;

    /** 详细资料（性别 / 手机 / QQ / 微信 / 简介），允许为空 */
    AccountDetails detail;
}
