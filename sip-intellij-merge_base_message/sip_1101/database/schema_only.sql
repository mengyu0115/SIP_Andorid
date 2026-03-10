
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `sip` /*!40100 DEFAULT CHARACTER SET utf8 */;

USE `sip`;
DROP TABLE IF EXISTS `call_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `call_record` (
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
  KEY `idx_start_time` (`start_time`),
  CONSTRAINT `call_record_ibfk_1` FOREIGN KEY (`caller_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `call_record_ibfk_2` FOREIGN KEY (`callee_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通话记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `conference`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `conference` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `conference_code` varchar(10) DEFAULT NULL COMMENT '会议号(6位随机数字,用户加入会议使用)',
  `is_active` tinyint(1) DEFAULT '1' COMMENT '是否活跃(true-活跃会议,false-历史会议)',
  `conference_uri` varchar(100) NOT NULL COMMENT '会议 URI',
  `creator_id` bigint(20) NOT NULL COMMENT '创建者 ID',
  `title` varchar(100) DEFAULT NULL COMMENT '会议标题',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `status` tinyint(4) DEFAULT '1' COMMENT '状态: 0已结束 1进行中',
  `max_participants` int(11) DEFAULT '10' COMMENT '最大参与人数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `conference_uri` (`conference_uri`),
  KEY `idx_creator` (`creator_id`),
  KEY `idx_conference_code` (`conference_code`),
  KEY `idx_is_active` (`is_active`),
  CONSTRAINT `conference_ibfk_1` FOREIGN KEY (`creator_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COMMENT='会议表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `conference_participant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `conference_participant` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `conference_id` bigint(20) NOT NULL COMMENT '会议 ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户 ID',
  `join_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `leave_time` datetime DEFAULT NULL COMMENT '离开时间',
  `role` tinyint(4) DEFAULT '0' COMMENT '角色: 0普通 1主持人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conf_user` (`conference_id`,`user_id`),
  KEY `idx_conference` (`conference_id`),
  KEY `idx_user` (`user_id`),
  CONSTRAINT `conference_participant_ibfk_1` FOREIGN KEY (`conference_id`) REFERENCES `conference` (`id`) ON DELETE CASCADE,
  CONSTRAINT `conference_participant_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COMMENT='会议参与者表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `file_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `file_info` (
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
  KEY `idx_md5` (`md5`),
  CONSTRAINT `file_info_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `friend`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `friend` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL COMMENT '用户 ID',
  `friend_id` bigint(20) NOT NULL COMMENT '好友 ID',
  `remark` varchar(100) DEFAULT NULL COMMENT '备注名',
  `group_name` varchar(50) DEFAULT '我的好友' COMMENT '分组名称',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_friend` (`user_id`,`friend_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `friend_id` (`friend_id`),
  CONSTRAINT `friend_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `friend_ibfk_2` FOREIGN KEY (`friend_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友关系表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `message` (
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
  KEY `idx_send_time` (`send_time`),
  CONSTRAINT `message_ibfk_1` FOREIGN KEY (`from_user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `message_ibfk_2` FOREIGN KEY (`to_user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(255) NOT NULL COMMENT '密码(BCrypt加密)',
  `nickname` varchar(100) DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像 URL',
  `sip_uri` varchar(100) NOT NULL COMMENT 'SIP URI (sip:user@domain)',
  `sip_password` varchar(100) NOT NULL COMMENT 'SIP 密码',
  `login_token` varchar(500) DEFAULT NULL COMMENT '当前登录token',
  `login_device` varchar(255) DEFAULT NULL COMMENT '登录设备信息',
  `status` tinyint(4) DEFAULT '1' COMMENT '在线状态: 0离线 1在线 2忙碌 3离开',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `sip_uri` (`sip_uri`),
  KEY `idx_username` (`username`),
  KEY `idx_sip_uri` (`sip_uri`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

