-- ============================================================
-- AI 用量与额度（阶段 5）
-- 额度按 **token 数** 统计，**每周上限**（默认 200000，可在 application-dev.yml 调）
--
-- 为什么用「周起始日」而不是定时任务重置：
--   读取时比对 week_start 与本週周一，不是本週就清零 ——
--   不用定时任务，也不怕服务在周一 0 点没开机导致重置丢失。
-- ============================================================

CREATE TABLE IF NOT EXISTS `db_account_ai_usage`
(
    `uid`          INT      NOT NULL COMMENT '用户 id',
    `used_tokens`  BIGINT   NOT NULL DEFAULT 0 COMMENT '本週已用 token 数（参与额度判断）',
    `total_tokens` BIGINT   NOT NULL DEFAULT 0 COMMENT '累计已用 token 数（只做统计，不参与限制）',
    `week_start`   DATE     NOT NULL COMMENT '本条统计所属週的周一，用于跨週重置',
    `update_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`uid`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI 用量与周额度';
