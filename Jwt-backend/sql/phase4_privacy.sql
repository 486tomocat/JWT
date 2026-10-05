-- ==================== 阶段 4：隐私设置 ====================
-- 每个用户一行，5 个开关：1 = 公开，0 = 仅自己可见。
-- 缺行时按「全部公开」处理（Service 里用 orElse 兜底），所以不必为历史用户预填。

CREATE TABLE IF NOT EXISTS `db_account_privacy` (
    `id`     INT        NOT NULL                COMMENT '用户 id，与 db_account.id 一致',
    `phone`  TINYINT(1) NOT NULL DEFAULT 1      COMMENT '手机号是否公开',
    `email`  TINYINT(1) NOT NULL DEFAULT 1      COMMENT '邮箱是否公开',
    `wx`     TINYINT(1) NOT NULL DEFAULT 1      COMMENT '微信是否公开',
    `qq`     TINYINT(1) NOT NULL DEFAULT 1      COMMENT 'QQ 是否公开',
    `gender` TINYINT(1) NOT NULL DEFAULT 1      COMMENT '性别是否公开',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户隐私开关（个人主页的资料可见性）';
