package com.example.entity.vo.response;

import lombok.Data;

import java.util.Date;

/**
 * 个人主页的数据。
 *
 * 被主人设为「不公开」的字段在这里直接是 null —— 后端过滤掉的，
 * 而不是前端「拿到但藏着不显示」。这样即使有人绕过前端直接调接口，也拿不到那些值。
 */
@Data
public class UserProfileVO {
    Integer id;
    String username;
    String avatar;
    String desc;
    String role;
    Date registerTime;

    Integer gender;
    String qq;
    String wx;
    String phone;
    String email;

    /** 访问者是不是主页主人：本人永远看得到全部字段 */
    boolean self;
}
