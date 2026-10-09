-- =====================================================================
-- 校园论坛 · 完整建表脚本（数据库 template）
-- =====================================================================
--
-- 【这是唯一的建表入口】
--
-- docker-compose 会把本目录挂载到 MySQL 容器的 /docker-entrypoint-initdb.d，
-- 容器**首次**启动时自动执行本脚本，建出全部 11 张表。
--
-- 也可以在 MySQL 客户端里手工执行：
--     docker exec -i forum-mysql mysql -uroot -p123456 < schema.sql
--
-- 【为什么合并成一个文件】
--   原先拆成 forum_schema / phase3 / phase4 / phase5 四个文件，
--   docker-entrypoint-initdb.d 按**文件名字母序**执行 —— 顺序纯靠巧合，
--   而且 phase3 要 ALTER db_account，那张表却没在任何脚本里，
--   导致全新环境初始化时 phase3 报错中断，phase4、phase5 全都没跑。
--   合并后顺序由本文件从上到下固定，不再依赖文件名。
--
-- 【幂等】
--   所有 CREATE 都带 IF NOT EXISTS，重复执行不会报错、不会破坏数据。
--
-- 【执行顺序】
--   1. 用户账号（db_account / db_account_details）
--   2. 论坛核心（db_topic 系列）
--   3. 通知与邮件（阶段 3）
--   4. 隐私设置（阶段 4）
--   5. AI 额度（阶段 5）
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `template` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `template`;


-- =====================================================================
-- 第 1 部分：用户账号
-- =====================================================================

-- ---------------------------------------------------------------
-- 用户账号
--
-- 列名按 MyBatis-Plus 默认规则（驼峰转下划线）与实体类对应：
--   registerTime -> register_time
--   mute / banned / avatar 是阶段 3 新增的字段，这里**直接建在表里**，
--   所以不再需要旧脚本里那段「查 information_schema 再 ALTER」的存储过程。
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `db_account` (
  `id`            int          NOT NULL AUTO_INCREMENT COMMENT '用户 id，自增',
  `username`      varchar(255) DEFAULT NULL                COMMENT '用户名',
  `password`      varchar(255) DEFAULT NULL                COMMENT 'BCrypt 加密后的密码，不存明文',
  `email`         varchar(255) DEFAULT NULL                COMMENT '邮箱，用于登录和找回密码',
  `role`          varchar(255) DEFAULT NULL                COMMENT '角色：user / admin',
  `avatar`        varchar(255) DEFAULT NULL                COMMENT '头像 URL（对象存储里的文件地址）',
  `register_time` datetime     DEFAULT NULL                COMMENT '注册时间',
  `mute`          tinyint      DEFAULT 0                   COMMENT '禁言：1=禁止发帖/评论',
  `banned`        tinyint      DEFAULT 0                   COMMENT '封禁：1=禁止登录',
  PRIMARY KEY (`id`),
  -- 注册时 Service 层已手工查重；唯一索引是双保险，防止并发注册绕过检查。
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户账号';

-- ---------------------------------------------------------------
-- 用户详细资料（与 db_account 一对一）
--
-- 主键**不是自增**：id 就来自 db_account.id，一个用户一行。
-- desc 是 MySQL 保留字，必须加反引号 —— 实体类里也写了 @TableField("`desc`")。
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `db_account_details` (
  `id`     int          NOT NULL                COMMENT '用户 id，与 db_account.id 一致',
  `gender` int          DEFAULT 0               COMMENT '性别：0=保密 1=男 2=女',
  `phone`  varchar(255) DEFAULT NULL            COMMENT '手机号',
  `qq`     varchar(255) DEFAULT NULL            COMMENT 'QQ 号',
  `wx`     varchar(255) DEFAULT NULL            COMMENT '微信号',
  `desc`   varchar(255) DEFAULT NULL            COMMENT '个人简介',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户详细资料';


-- =====================================================================
-- 第 2 部分：论坛核心
-- =====================================================================

-- ---------------------------------------------------------------
-- 帖子
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `db_topic` (
  `id`        int NOT NULL AUTO_INCREMENT,
  `title`     varchar(255) DEFAULT NULL,
  `intro`     text,
  `content`   text COMMENT 'Quill Delta JSON',
  `uid`       int DEFAULT NULL,
  `type`      int DEFAULT NULL,
  `time`      datetime DEFAULT NULL,
  `top`       tinyint DEFAULT '0',
  `locked`    tinyint DEFAULT '0',
  `invisible` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_type_time` (`type`, `time`),
  KEY `idx_time` (`time`),
  KEY `idx_uid` (`uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------
-- 帖子分类（desc 是保留字，建表和查询都要加反引号）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `db_topic_type` (
  `id`    int NOT NULL AUTO_INCREMENT,
  `name`  varchar(255) DEFAULT NULL,
  `desc`  varchar(255) DEFAULT NULL,
  `color` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 分类初始数据（IGNORE 保证重复执行不报错）
INSERT IGNORE INTO `db_topic_type` (`id`, `name`, `desc`, `color`) VALUES
  (1, '日常闲聊', '分享你的校园日常', '#1E56FF'),
  (2, '真诚交友', '寻找志同道合的朋友', '#CE1EFF'),
  (3, '问题反馈', '反馈你遇到的问题', '#E07373'),
  (4, '恋爱官宣', '展示你的恋爱成果', '#E0CE73');

-- ---------------------------------------------------------------
-- 评论（quote 指向被回复的评论 id，-1 表示直接回复帖子）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `db_topic_comment` (
  `id`      int NOT NULL AUTO_INCREMENT,
  `uid`     int DEFAULT NULL,
  `tid`     int DEFAULT NULL,
  `content` text,
  `time`    datetime DEFAULT NULL,
  `quote`   int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_tid_time` (`tid`, `time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------
-- 点赞 / 收藏（联合唯一索引防止同一用户重复互动）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `db_topic_interact_like` (
  `tid`  int DEFAULT NULL,
  `uid`  int DEFAULT NULL,
  `time` datetime DEFAULT NULL,
  UNIQUE KEY `tid_uid_like` (`tid`, `uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `db_topic_interact_collect` (
  `tid`  int DEFAULT NULL,
  `uid`  int DEFAULT NULL,
  `time` datetime DEFAULT NULL,
  UNIQUE KEY `tid_uid_collect` (`tid`, `uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- =====================================================================
-- 第 3 部分：站内通知与邮件记录（阶段 3）
-- =====================================================================

-- ---------------------------------------------------------------
-- 站内通知
-- 索引 (uid, time) 支撑「查我的通知并按时间倒序」这一唯一查询模式。
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
-- 邮件发送记录（MQ 死信队列的状态载体）
-- status: 0=发送中 1=成功 2=失败（进死信队列）
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


-- =====================================================================
-- 第 4 部分：隐私设置（阶段 4）
-- =====================================================================

-- 每个用户一行，5 个开关：1 = 公开，0 = 仅自己可见。
-- 缺行时按「全部公开」处理（Service 里用 orElse 兜底），所以不必为历史用户预填。
CREATE TABLE IF NOT EXISTS `db_account_privacy` (
  `id`     int        NOT NULL                COMMENT '用户 id，与 db_account.id 一致',
  `phone`  tinyint(1) NOT NULL DEFAULT 1      COMMENT '手机号是否公开',
  `email`  tinyint(1) NOT NULL DEFAULT 1      COMMENT '邮箱是否公开',
  `wx`     tinyint(1) NOT NULL DEFAULT 1      COMMENT '微信是否公开',
  `qq`     tinyint(1) NOT NULL DEFAULT 1      COMMENT 'QQ 是否公开',
  `gender` tinyint(1) NOT NULL DEFAULT 1      COMMENT '性别是否公开',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户隐私开关（个人主页的资料可见性）';


-- =====================================================================
-- 第 5 部分：AI 用量与额度（阶段 5）
-- =====================================================================

-- 额度按 token 数统计，每周上限（默认 200000，可在 application-dev.yml 调）。
--
-- 为什么用「周起始日」而不是定时任务重置：
--   读取时比对 week_start 与本周周一，不是本周就清零 ——
--   不用定时任务，也不怕服务在周一 0 点没开机导致重置丢失。
CREATE TABLE IF NOT EXISTS `db_account_ai_usage` (
  `uid`          int      NOT NULL COMMENT '用户 id',
  `used_tokens`  bigint   NOT NULL DEFAULT 0 COMMENT '本周已用 token 数（参与额度判断）',
  `total_tokens` bigint   NOT NULL DEFAULT 0 COMMENT '累计已用 token 数（只做统计，不参与限制）',
  `week_start`   date     NOT NULL COMMENT '本条统计所属周的周一，用于跨周重置',
  `update_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 用量与周额度';
