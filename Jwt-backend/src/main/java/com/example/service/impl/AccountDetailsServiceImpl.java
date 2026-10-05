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
        // 两种情况放行：
        //   account == null                     → 这个用户名没人用
        //   account.getId() 就是自己             → 用户没改名（或改成了自己现在的名字）
        // 只有「用户名被**别人**占用」才拒绝。
        //
        // ⚠️ 这里踩过两个坑，都来自参考实现：
        //   1) 下面 service.update()... 链式调用原来漏了终结方法 .update()，SQL 从未执行；
        //   2) 判断条件原来写成 !Objects.equals(...)，导致「用户名被自己占用」时返回 false
        //      —— 也就是**只要不改用户名，整个表单必然保存失败**。
        if (account == null || Objects.equals(account.getId(), id)) {
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
