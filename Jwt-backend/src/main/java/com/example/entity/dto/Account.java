package com.example.entity.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.entity.BaseData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 数据库中的用户信息。
 *
 * 阶段 3 新增三个字段，各自解决一个具体问题：
 * - banned（封禁）：登录直接被拒；已经签发的令牌也会在 JwtAuthorizeFilter 里被拉黑；
 * - mute（禁言）：还能登录、浏览、点赞，但发帖 / 评论 / 编辑会被业务层拒绝；
 * - avatar（头像）：为后续图片模块预留，现在只参与 VO 拷贝。
 *
 * 另外把原来的「全参构造」调用点改成了 setter：
 * 字段少的时候 new Account(a, b, c...) 还能读，字段一多就是灾难
 * （阶段 1 那个 userId 被写成 1 的 bug，本质就是位置参数看串了）。
 */
@Data
@TableName("db_account")
@NoArgsConstructor
@AllArgsConstructor
public class Account implements BaseData {
    @TableId(type = IdType.AUTO)
    Integer id;
    String username;
    String password;
    String email;
    String role;
    String avatar;
    Date registerTime;
    boolean mute;
    boolean banned;
}
