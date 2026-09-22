package com.example.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.entity.dto.Account;
import com.example.entity.dto.AccountDetails;
import com.example.entity.vo.request.DetailsSaveVO;
import com.example.mapper.AccountDetailsMapper;
import com.example.service.AccountDetailsService;
import com.example.service.AccountService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AccountDetailsServiceImpl extends ServiceImpl<AccountDetailsMapper, AccountDetails> implements AccountDetailsService {

    @Resource
    AccountService service;

    @Override
    public AccountDetails findAccountDetailsById(int id) {
        return this.getById(id);
    }

    @Override
    public boolean saveAccountDetails(int id, DetailsSaveVO vo) {
        Account account = service.findAccountByNameOrEmail(vo.getUsername());
        // 用户名没被别人占用才允许改。
        // 原来的写法是 service.update().eq(...).set(...) 后面缺了终结方法 .update()，
        // 那串条件只是构造了一个 Wrapper 对象，SQL 从来没有执行过 —— 改用户名一直是无效操作。
        if (account == null || !Objects.equals(account.getId(), id)) {
            service.update()
                    .eq("id", id)
                    .set("username", vo.getUsername())
                    .update();
            this.saveOrUpdate(new AccountDetails(
                    id, vo.getGender(), vo.getPhone(),
                    vo.getQq(), vo.getWx(), vo.getDesc()
            ));
            return true;
        }
        return false;
    }
}
