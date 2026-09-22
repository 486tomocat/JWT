package com.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.entity.dto.Account;

import com.example.entity.vo.request.AccountSaveVO;
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
}
