-- =====================================================================
-- 校园论坛 · 论坛模块建表脚本（对应数据库 template）
-- 在 MySQL 客户端里直接执行即可，脚本可重复运行。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `template` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `template`;

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
-- 帖子分类
-- 注意 desc 是 MySQL 保留字，建表和查询都必须加反引号
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `db_topic_type` (
  `id`    int NOT NULL AUTO_INCREMENT,
  `name`  varchar(255) DEFAULT NULL,
  `desc`  varchar(255) DEFAULT NULL,
  `color` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
