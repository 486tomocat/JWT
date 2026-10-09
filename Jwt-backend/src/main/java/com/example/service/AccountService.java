package com.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.entity.dto.Account;

import com.example.entity.vo.request.AccountSaveVO;
import com.example.entity.vo.request.ChangePasswordVO;
import com.example.entity.vo.request.ModifyEmailVO;
import com.example.entity.vo.request.ConfirmResetVO;
import com.example.entity.vo.request.EmailRegisterVO;
import com.example.entity.vo.request.EmailResetVO;
import jakarta.validation.Valid;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * 账号业务接口。
 *
 * 约定：返回 String 的方法，null = 成功，非 null = 失败原因。
 */
public interface AccountService extends IService<Account>, UserDetailsService {
    Account findAccountByNameOrEmail(String text);
    Account findAccountById(int id);
    String registerEmailVerifyCode(String type, String email, String ip);
    String registerEmailAccount(@Valid EmailRegisterVO vo);
    String resetConfirm(ConfirmResetVO vo);
    String resetEmailAccountPassword(EmailResetVO vo);

    /** 管理后台：保存用户资料 / 角色 / 禁言 / 封禁 */
    String saveAccountByAdmin(AccountSaveVO vo);

    /** 管理后台：直接重置某个用户的密码（不需要原密码） */
    void modifyPassword(int id, String newPassword);

    /** 用户自助：修改绑定邮箱（需要新邮箱收到的验证码，且该邮箱未被他人占用） */
    String modifyEmail(int id, ModifyEmailVO vo);

    /** 用户自助：修改密码（需要校验原密码） */
    String changePassword(int id, ChangePasswordVO vo);

    /**
     * 修改头像。
     * 传入的是上传接口返回的图片 URL —— 所以调用方必须**先上传再保存**。
     */
    String modifyAvatar(int id, String avatar);
}
