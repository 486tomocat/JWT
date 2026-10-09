-- =====================================================================
-- 校园论坛 · 阶段 3 建表 / 改表脚本（数据库 template）
-- 对应功能：站内通知、用户封禁/禁言、邮件记录（MQ 死信队列）
-- 脚本可重复执行，不会破坏已有数据。
-- 用法：mysql -u root -p123456 < "D:/code/Java/Jwt/Jwt-backend/sql/phase3_schema.sql"
-- 注意：如果阶段 2 的 forum_schema.sql 还没执行过，请先执行它。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `template` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `template`;

-- ---------------------------------------------------------------
-- 1. 用户表的封禁 / 禁言 / 头像字段
--    MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，
--    所以用存储过程先查 information_schema，缺哪列补哪列。
-- ---------------------------------------------------------------
DROP PROCEDURE IF EXISTS `add_column_if_absent`;
DELIMITER $$
CREATE PROCEDURE `add_column_if_absent`(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl VARCHAR(255))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col) THEN
    SET @s = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN `', col, '` ', ddl);
    PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL `add_column_if_absent`('db_account', 'avatar', 'varchar(255) DEFAULT NULL');
CALL `add_column_if_absent`('db_account', 'mute',   'tinyint DEFAULT 0 COMMENT ''禁言：1=禁止发帖/评论''');
CALL `add_column_if_absent`('db_account', 'banned', 'tinyint DEFAULT 0 COMMENT ''封禁：1=禁止登录''');

DROP PROCEDURE IF EXISTS `add_column_if_absent`;

-- ---------------------------------------------------------------
-- 2. 站内通知
--    索引 (uid, time) 支撑「查我的通知并按时间倒序」这一唯一查询模式。
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `db_notification` (
  `id`      int NOT NULL AUTO_INCREMENT,
  `uid`     int DEFAULT NULL COMMENT '接收者用户 id',
  `title`   varchar(255) DEFAULT NULL,
  `content` varchar(255) DEFAULT NULL,
  `type`    varchar(255) DEFAULT NULL,
  `url`     varchar(255) DEFAULT NULL COMMENT '点开后跳转的前端路由',
  `time`    datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_uid_time` (`uid`, `time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------
-- 3. 邮件发送记录（MQ 死信队列的状态载体）
--    status: 0=发送中 1=成功 2=失败（进死信队列）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `db_email_record` (
  `id`      int NOT NULL AUTO_INCREMENT,
  `email`   varchar(255) DEFAULT NULL,
  `title`   varchar(255) DEFAULT NULL,
  `content` varchar(255) DEFAULT NULL,
  `time`    datetime DEFAULT NULL,
  `status`  tinyint DEFAULT NULL COMMENT '0=发送中 1=成功 2=失败',
  PRIMARY KEY (`id`),
  KEY `idx_time` (`time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
