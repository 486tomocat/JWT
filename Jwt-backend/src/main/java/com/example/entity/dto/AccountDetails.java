package com.example.entity.dto;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("db_account_details")
public class AccountDetails {
    @TableId
    int id;
    int gender;
    String phone;
    String qq;
    String wx;
    String desc;

}
