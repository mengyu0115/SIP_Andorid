-- ============================================
-- SIP IM系统 - 快速数据库初始化脚本
-- ============================================
-- 说明：本脚本仅检查和创建缺失的表，不会删除已有数据
-- 适用场景：在已有部分表的数据库中，补充缺失的表
-- ============================================

USE sip;

-- ============================================
-- 检查并创建 call_record 表（通话记录表）
-- ============================================
CREATE TABLE IF NOT EXISTS `call_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `caller_id` bigint(20) NOT NULL COMMENT '主叫 ID',
  `callee_id` bigint(20) NOT NULL COMMENT '被叫 ID',
  `call_type` tinyint(4) NOT NULL COMMENT '通话类型: 1音频 2视频 3群聊',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `duration` int(11) DEFAULT NULL COMMENT '通话时长(秒)',
  `status` tinyint(4) DEFAULT NULL COMMENT '状态: 1成功 2未接 3拒绝 4取消 5失败',
  `quality` tinyint(4) DEFAULT NULL COMMENT '通话质量: 1差 2中 3良 4优',
  `rtp_packets_sent` int(11) DEFAULT NULL COMMENT 'RTP 发送包数',
  `rtp_packets_received` int(11) DEFAULT NULL COMMENT 'RTP 接收包数',
  `rtp_packets_lost` int(11) DEFAULT NULL COMMENT 'RTP 丢包数',
  `avg_jitter` float DEFAULT NULL COMMENT '平均抖动(ms)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_caller` (`caller_id`,`start_time`),
  KEY `idx_callee` (`callee_id`,`start_time`),
  KEY `idx_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通话记录表';

-- ============================================
-- 检查并创建 file_info 表（文件信息表）
-- ============================================
CREATE TABLE IF NOT EXISTS `file_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL COMMENT '上传者 ID',
  `file_name` varchar(255) NOT NULL COMMENT '文件名',
  `file_type` varchar(50) DEFAULT NULL COMMENT '文件类型 (MIME Type)',
  `file_size` bigint(20) DEFAULT NULL COMMENT '文件大小(字节)',
  `file_path` varchar(500) DEFAULT NULL COMMENT '存储路径',
  `file_url` varchar(500) DEFAULT NULL COMMENT '访问 URL',
  `md5` varchar(32) DEFAULT NULL COMMENT '文件 MD5 (用于去重)',
  `upload_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_md5` (`md5`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件表';

-- ============================================
-- 检查并更新 message 表（消息表）
-- ============================================
-- 注意：如果message表已存在，我们只添加缺失的列
CREATE TABLE IF NOT EXISTS `message` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `from_user_id` bigint(20) NOT NULL COMMENT '发送者 ID',
  `to_user_id` bigint(20) NOT NULL COMMENT '接收者 ID',
  `msg_type` tinyint(4) NOT NULL COMMENT '消息类型: 1文字 2图片 3语音 4视频 5文件',
  `content` text COMMENT '消息内容 (文字内容或文件URL)',
  `file_url` varchar(500) DEFAULT NULL COMMENT '文件 URL',
  `file_size` bigint(20) DEFAULT NULL COMMENT '文件大小(字节)',
  `duration` int(11) DEFAULT NULL COMMENT '语音/视频时长(秒)',
  `is_read` tinyint(4) DEFAULT '0' COMMENT '是否已读: 0未读 1已读',
  `is_offline` tinyint(4) DEFAULT '0' COMMENT '是否离线消息: 0在线 1离线',
  `send_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_from_user` (`from_user_id`),
  KEY `idx_to_user` (`to_user_id`,`is_read`),
  KEY `idx_send_time` (`send_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';

-- ============================================
-- 为已存在的message表添加缺失的列（如果需要）
-- ============================================
-- 添加 file_url 列（如果不存在）
SET @column_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'sip'
    AND TABLE_NAME = 'message'
    AND COLUMN_NAME = 'file_url'
);

SET @sql = IF(@column_exists = 0,
  'ALTER TABLE `message` ADD COLUMN `file_url` varchar(500) DEFAULT NULL COMMENT "文件 URL"',
  'SELECT "file_url column already exists" AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 file_size 列（如果不存在）
SET @column_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'sip'
    AND TABLE_NAME = 'message'
    AND COLUMN_NAME = 'file_size'
);

SET @sql = IF(@column_exists = 0,
  'ALTER TABLE `message` ADD COLUMN `file_size` bigint(20) DEFAULT NULL COMMENT "文件大小(字节)"',
  'SELECT "file_size column already exists" AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 duration 列（如果不存在）
SET @column_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'sip'
    AND TABLE_NAME = 'message'
    AND COLUMN_NAME = 'duration'
);

SET @sql = IF(@column_exists = 0,
  'ALTER TABLE `message` ADD COLUMN `duration` int(11) DEFAULT NULL COMMENT "语音/视频时长(秒)"',
  'SELECT "duration column already exists" AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================
-- 验证表结构
-- ============================================
SELECT '✅ 数据库初始化完成！' AS status;

SELECT
  CASE
    WHEN COUNT(*) = 3 THEN '✅ 所有必需表已创建'
    ELSE '⚠️  部分表缺失，请检查'
  END AS table_check
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = 'sip'
  AND TABLE_NAME IN ('message', 'file_info', 'call_record');

-- 显示表信息
SELECT
  TABLE_NAME AS '表名',
  TABLE_ROWS AS '行数',
  ROUND(DATA_LENGTH/1024/1024, 2) AS '数据大小(MB)',
  TABLE_COMMENT AS '说明'
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = 'sip'
  AND TABLE_NAME IN ('message', 'file_info', 'call_record', 'user', 'conference')
ORDER BY TABLE_NAME;
