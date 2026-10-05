package com.example.controller;

import com.example.entity.RestBean;
import com.example.entity.dto.Account;
import com.example.entity.dto.AccountDetails;
import com.example.entity.vo.response.AccountDetailsVO;
import com.example.entity.vo.response.AccountVO;
import com.example.entity.vo.response.UserProfileVO;
import com.example.entity.dto.AccountPrivacy;
import com.example.entity.vo.request.ChangePasswordVO;
import com.example.entity.vo.request.PrivacySaveVO;
import com.example.entity.vo.request.DetailsSaveVO;
import com.example.entity.vo.request.ModifyEmailVO;
import com.example.service.AccountDetailsService;
import com.example.service.AccountPrivacyService;
import com.example.service.AccountService;
import com.example.utils.Const;
import com.example.utils.ControllerUtils;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/user")
public class AccountController {

    @Resource
    AccountService service;

    @Resource
    AccountDetailsService detailsService;

    @Resource
    ControllerUtils utils;

    @Resource
    AccountPrivacyService privacyService;

    @GetMapping("/info")
    public RestBean<AccountVO> info(@RequestAttribute(Const.ATTR_USER_ID) int id) {
        Account account = service.findAccountById(id);
        return RestBean.success(account.asViewObject(AccountVO.class));
    }

    @GetMapping("/details")
    public RestBean<AccountDetailsVO> details(@RequestAttribute(Const.ATTR_USER_ID) int id) {
        AccountDetails details = Optional
                .ofNullable(detailsService.findAccountDetailsById(id))
                .orElse(new AccountDetails());
        return RestBean.success(details.asViewObject(AccountDetailsVO.class));
    }

    @PostMapping("/save-details")
    public RestBean<Void> saveDetails(@RequestAttribute(Const.ATTR_USER_ID) int id,
                                      @RequestBody @Valid DetailsSaveVO vo) {
        boolean success = detailsService.saveAccountDetails(id, vo);
        return success ? RestBean.success() : RestBean.failure(400, "用户名已被其他用户使用，请重新输入");
    }

    /** 修改绑定邮箱：需先往新邮箱申请验证码（type=modify） */
    @PostMapping("/modify-email")
    public RestBean<Void> modifyEmail(@RequestAttribute(Const.ATTR_USER_ID) int id,
                                      @RequestBody @Valid ModifyEmailVO vo) {
        return utils.messageHandle(() -> service.modifyEmail(id, vo));
    }

    /** 修改密码：需校验原密码 */
    @PostMapping("/change-password")
    public RestBean<Void> changePassword(@RequestAttribute(Const.ATTR_USER_ID) int id,
                                         @RequestBody @Valid ChangePasswordVO vo) {
        return utils.messageHandle(() -> service.changePassword(id, vo));
    }

    // ==================== 隐私开关 ====================

    /** 读取自己的隐私开关（个人主页上哪些资料对别人可见） */
    @GetMapping("/privacy")
    public RestBean<AccountPrivacy> privacy(@RequestAttribute(Const.ATTR_USER_ID) int id) {
        return RestBean.success(privacyService.accountPrivacy(id));
    }

    /** 修改某一个开关，入参 {type, status} */
    @PostMapping("/save-privacy")
    public RestBean<Void> savePrivacy(@RequestAttribute(Const.ATTR_USER_ID) int id,
                                      @RequestBody @Valid PrivacySaveVO vo) {
        privacyService.savePrivacy(id, vo);
        return RestBean.success();
    }

    // ==================== 个人主页 ====================

    /**
     * 查看某个用户的个人主页。
     *
     * 看别人时，对方设为「不公开」的字段在这里就置成 null —— 过滤发生在服务端，
     * 前端拿不到值，绕过前端直接调接口也一样拿不到。
     * 看自己的主页永远显示全部（self = true）。
     */
    @GetMapping("/profile")
    public RestBean<UserProfileVO> profile(@RequestParam @Min(value = 1, message = "参数必须大于 0") int id,
                                           @RequestAttribute(Const.ATTR_USER_ID) int uid) {
        Account account = service.findAccountById(id);
        if (account == null) {
            return RestBean.failure(400, "用户不存在");
        }

        boolean self = id == uid;
        AccountPrivacy privacy = privacyService.accountPrivacy(id);
        // 本人不过滤
        List<String> hidden = self ? List.of() : privacy.hiddenFields();
        AccountDetails details = detailsService.findAccountDetailsById(id);

        UserProfileVO vo = new UserProfileVO();
        vo.setId(account.getId());
        vo.setUsername(account.getUsername());
        vo.setAvatar(account.getAvatar());
        vo.setRole(account.getRole());
        vo.setRegisterTime(account.getRegisterTime());
        vo.setSelf(self);

        if (details != null) {
            vo.setDesc(details.getDesc());
            if (!hidden.contains("gender")) vo.setGender(details.getGender());
            if (!hidden.contains("phone")) vo.setPhone(details.getPhone());
            if (!hidden.contains("qq")) vo.setQq(details.getQq());
            if (!hidden.contains("wx")) vo.setWx(details.getWx());
        }
        // 邮箱在 db_account 上，不在 details 里
        if (!hidden.contains("email")) vo.setEmail(account.getEmail());

        return RestBean.success(vo);
    }
}
