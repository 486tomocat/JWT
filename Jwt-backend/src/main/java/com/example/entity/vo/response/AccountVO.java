package com.example.entity.vo.response;

import lombok.Data;

import java.util.Date;

/**
 * 用户信息视图对象。
 * 注意这里绝不能出现 password 字段——VO 是靠同名字段反射拷贝的，
 * 只要不声明，密码就永远不会被序列化出去。
 */
@Data
public class AccountVO {
    Integer id;
    String username;
    String email;
    String role;
    String avatar;
    Date registerTime;
    boolean mute;
    boolean banned;
}
