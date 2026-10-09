package com.example.service.impl;

import com.example.entity.dto.AccountAiUsage;
import com.example.mapper.AccountAiUsageMapper;
import com.example.service.AiQuotaService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;

@Slf4j
@Service
public class AiQuotaServiceImpl implements AiQuotaService {

    /** 每週 token 上限，默认 200000，可在 application-dev.yml 调整 */
    @Value("${forum.ai.weekly-token-limit:200000}")
    long weeklyLimit;

    @Resource
    AccountAiUsageMapper usageMapper;

    @Override
    public Quota quota(int uid) {
        AccountAiUsage usage = usageMapper.selectById(uid);
        // 没有记录、或者记录还停留在上一週 → 本週用 0
        if (usage == null || !mondayOfThisWeek().equals(usage.getWeekStart())) {
            return new Quota(weeklyLimit, 0, weeklyLimit);
        }
        long used = usage.getUsedTokens() == null ? 0 : usage.getUsedTokens();
        return new Quota(weeklyLimit, used, Math.max(0, weeklyLimit - used));
    }

    @Override
    public boolean allowed(int uid) {
        return quota(uid).remaining() > 0;
    }

    @Override
    public void consume(int uid, long tokens) {
        if (tokens <= 0) {
            return;
        }
        LocalDate monday = mondayOfThisWeek();
        AccountAiUsage usage = usageMapper.selectById(uid);
        try {
            if (usage == null) {
                usage = new AccountAiUsage();
                usage.setUid(uid);
                usage.setUsedTokens(tokens);
                usage.setTotalTokens(tokens);
                usage.setWeekStart(monday);
                usageMapper.insert(usage);
                return;
            }
            // 跨週了：本週计数从这次的消耗重新开始
            if (!monday.equals(usage.getWeekStart())) {
                usage.setUsedTokens(tokens);
                usage.setWeekStart(monday);
            } else {
                usage.setUsedTokens((usage.getUsedTokens() == null ? 0 : usage.getUsedTokens()) + tokens);
            }
            usage.setTotalTokens((usage.getTotalTokens() == null ? 0 : usage.getTotalTokens()) + tokens);
            usageMapper.updateById(usage);
        } catch (Exception e) {
            // 记账失败不该让用户白问一次 —— 记日志即可
            log.error("记录 AI 用量失败(uid={}, tokens={})：{}", uid, tokens, e.getMessage());
        }
    }

    /** 本週的周一（ISO 标准：周一为一週之始） */
    private LocalDate mondayOfThisWeek() {
        return LocalDate.now().with(DayOfWeek.MONDAY);
    }
}
