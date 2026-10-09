package com.example.entity.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

/**
 * AI 用量与周额度（一人一行）。
 *
 * 额度**按 token 数**统计、**按週**限制。跨週重置不用定时任务：
 * 每次读到时比对 week_start 与本周周一，不是本週就清零 —— 服务停机也不会漏掉重置。
 */
@Data
@TableName("db_account_ai_usage")
public class AccountAiUsage {

    @TableId(type = IdType.INPUT)
    Integer uid;

    /** 本週已用 token（参与额度判断） */
    Long usedTokens;

    /** 累计已用 token（只做统计） */
    Long totalTokens;

    /** 本条统计所属週的周一 */
    LocalDate weekStart;

    Date updateTime;
}
