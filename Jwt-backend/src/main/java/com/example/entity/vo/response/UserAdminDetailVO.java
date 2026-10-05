package com.example.entity.vo.response;

import com.example.entity.dto.AccountDetails;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 管理后台「用户详情」响应：账号信息 + 详细资料。
 * 隐私设置（AccountPrivacy）属于阶段 4，这里先不返回。
 */
@Data
@AllArgsConstructor
public class UserAdminDetailVO {
    AccountVO account;
    AccountDetails detail;
}
