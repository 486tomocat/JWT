package com.example.controller.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.entity.PageRestBean;
import com.example.entity.RestBean;
import com.example.entity.dto.Account;
import com.example.entity.vo.request.AccountPasswordVO;
import com.example.entity.vo.request.AccountSaveVO;
import com.example.entity.vo.response.AccountVO;
import com.example.entity.vo.response.UserAdminDetailVO;
import com.example.service.AccountDetailsService;
import com.example.service.AccountService;
import com.example.utils.Const;
import com.example.utils.ControllerUtils;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台 · 用户管理。
 *
 * /api/admin/** 在 SecurityConfiguration 里被限制为 admin 角色，
 * 所以这里不需要再写一遍权限判断——权限是「入口」的事，业务代码不该重复关心。
 */
@RestController
@RequestMapping("/api/admin/user")
public class AccountAdminController {

    @Resource
    AccountService service;

    @Resource
    AccountDetailsService detailsService;

    @Resource
    ControllerUtils utils;

    /**
     * 用户列表。keyword 支持「用户名模糊」或「用户 id 精确」两种搜法。
     *
     * 条件包在 and(...) 里而不是直接 .eq().or().like()：
     * 后者在 keyword 为空时会留下一个悬空的 OR，拼出的 SQL 可能直接语法错误。
     */
    @GetMapping("/list")
    public PageRestBean<AccountVO> accountList(@RequestParam @Min(value = 0, message = "参数不能为负") int page,
                                               @RequestParam @Min(value = 1, message = "参数必须大于 0") int size,
                                               @RequestParam(required = false) String keyword) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        Page<Account> accountPage = service.page(Page.of(page, size), Wrappers.<Account>query()
                .and(kw != null, w -> {
                    w.like("username", "%" + kw + "%");
                    // 只有纯数字才当作 id 查，否则 MySQL 会把 'abc' 隐式转成 0
                    if (kw.chars().allMatch(Character::isDigit)) {
                        w.or().eq("id", kw);
                    }
                })
                .orderByAsc("id"));
        List<AccountVO> list = accountPage.getRecords().stream()
                .map(account -> account.asViewObject(AccountVO.class))
                .toList();
        return PageRestBean.success(list, accountPage.getTotal(), accountPage.getCurrent());
    }

    @GetMapping("/detail")
    public RestBean<UserAdminDetailVO> accountDetail(@RequestParam @Min(value = 1, message = "参数必须大于 0") int id) {
        Account account = service.findAccountById(id);
        if (account == null) {
            return RestBean.failure(400, "用户不存在");
        }
        return RestBean.success(new UserAdminDetailVO(
                account.asViewObject(AccountVO.class),
                detailsService.findAccountDetailsById(id)
        ));
    }

    /** 保存用户资料 / 角色 / 禁言 / 封禁 */
    @PostMapping("/save")
    public RestBean<Void> saveAccount(@RequestBody @Valid AccountSaveVO vo,
                                      @RequestAttribute(Const.ATTR_USER_ID) int uid) {
        // 防呆：管理员把自己封了 / 降级了，就没人能再进后台
        if (uid == vo.getId()) {
            return RestBean.failure(400, "不能修改自己的账号信息");
        }
        return utils.messageHandle(() -> service.saveAccountByAdmin(vo));
    }

    @PostMapping("/change-password")
    public RestBean<Void> changePassword(@RequestBody @Valid AccountPasswordVO vo,
                                         @RequestAttribute(Const.ATTR_USER_ID) int uid) {
        if (uid == vo.getId()) {
            return RestBean.failure(400, "请到个人设置中修改自己的密码");
        }
        if (service.findAccountById(vo.getId()) == null) {
            return RestBean.failure(400, "用户不存在");
        }
        service.modifyPassword(vo.getId(), vo.getNewPassword());
        return RestBean.success();
    }
}
