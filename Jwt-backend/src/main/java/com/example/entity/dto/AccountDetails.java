package com.example.entity.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.entity.BaseData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@TableName("db_account_details")
@AllArgsConstructor
@NoArgsConstructor
public class AccountDetails implements BaseData {
    @TableId
    Integer id;
    int gender;
    String phone;
    String qq;
    String wx;

    /** desc 是 MySQL 保留字，必须反引号转义 */
    @TableField("`desc`")
    String desc;
}
