
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

LOCK TABLES `call_record` WRITE;
/*!40000 ALTER TABLE `call_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `call_record` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `conference` WRITE;
/*!40000 ALTER TABLE `conference` DISABLE KEYS */;
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (1,NULL,1,'sip:conf-1765020963395@localhost',1,'100',NULL,NULL,2,10,'2025-12-06 19:36:03');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (2,NULL,1,'sip:conf-1765037510035@localhost',1,'会议 123',NULL,NULL,2,10,'2025-12-07 00:11:50');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (3,NULL,1,'sip:conf-1765037525285@localhost',1,'nihao',NULL,NULL,2,10,'2025-12-07 00:12:05');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (4,NULL,1,'sip:conf-1765040228112@localhost',1,'会议 1231231',NULL,NULL,2,10,'2025-12-07 00:57:08');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (5,NULL,1,'sip:conf-1765040232600@localhost',1,'1',NULL,NULL,2,10,'2025-12-07 00:57:13');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (6,NULL,1,'sip:conf-1765042021749@localhost',1,'11',NULL,NULL,2,10,'2025-12-07 01:27:02');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (7,'888888',0,'sip:conf-test@localhost',1,'测试会议888888',NULL,'2025-12-09 01:42:03',0,10,'2025-12-08 01:33:51');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (8,'379703',1,'sip:conf-1765216498046@localhost',1,'邱皓玮的个人会议',NULL,NULL,2,10,'2025-12-09 01:54:58');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (9,'482280',1,'sip:conf-1765256855381@localhost',1,'邱皓玮',NULL,NULL,2,10,'2025-12-09 13:07:35');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (10,'822905',1,'sip:conf-1765279920855@localhost',1,'邱皓玮',NULL,NULL,2,10,'2025-12-09 19:32:01');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (11,'985225',1,'sip:conf-1765282031262@localhost',1,'邱皓玮',NULL,NULL,2,10,'2025-12-09 20:07:11');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (12,'727295',1,'sip:conf-1765332813146@localhost',1,'邱皓玮',NULL,NULL,2,10,'2025-12-10 10:13:33');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (13,'750150',1,'sip:conf-1765354107685@localhost',1,'邱皓玮',NULL,NULL,2,10,'2025-12-10 16:08:28');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (14,'659597',1,'sip:conf-1765391226970@localhost',1,'qhw',NULL,NULL,2,10,'2025-12-11 02:27:07');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (15,'143731',0,'sip:conf-1765432473549@localhost',1,'qhw',NULL,'2025-12-11 13:54:59',0,10,'2025-12-11 13:54:34');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (16,'106137',1,'sip:conf-1765432508644@localhost',1,'qhw',NULL,NULL,2,10,'2025-12-11 13:55:09');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (17,'976739',1,'sip:conf-1765432525465@localhost',1,'qhw',NULL,NULL,2,10,'2025-12-11 13:55:25');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (18,'547799',0,'sip:conf-1765436717590@localhost',1,'qhw',NULL,'2025-12-11 15:07:01',0,10,'2025-12-11 15:05:18');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (19,'407707',1,'sip:conf-1765446060512@localhost',1,'qhw',NULL,NULL,2,10,'2025-12-11 17:41:01');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (20,'759072',1,'sip:conf-1765472619106@localhost',1,'shw',NULL,NULL,2,10,'2025-12-12 01:03:39');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (21,'210570',1,'sip:conf-1765481246011@localhost',2,'qhw',NULL,NULL,2,10,'2025-12-12 03:27:26');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (22,'652847',0,'sip:conf-1765481268018@localhost',1,'qhw',NULL,'2025-12-12 03:29:05',0,10,'2025-12-12 03:27:48');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (23,'221705',1,'sip:conf-1765528866172@localhost',1,'qhw',NULL,NULL,2,10,'2025-12-12 16:41:06');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (24,'328843',1,'sip:conf-1765558756210@localhost',1,'qhw',NULL,NULL,2,10,'2025-12-13 00:59:16');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (25,'566543',0,'sip:conf-1765603416734@localhost',1,'qhw',NULL,'2025-12-13 13:25:12',0,10,'2025-12-13 13:23:37');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (26,'210725',1,'sip:conf-1765603521077@localhost',2,'qhw',NULL,NULL,2,10,'2025-12-13 13:25:21');
INSERT INTO `conference` (`id`, `conference_code`, `is_active`, `conference_uri`, `creator_id`, `title`, `start_time`, `end_time`, `status`, `max_participants`, `create_time`) VALUES (27,'029403',1,'sip:conf-1765611724156@localhost',1,'qhw',NULL,NULL,2,10,'2025-12-13 15:42:04');
/*!40000 ALTER TABLE `conference` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `conference_participant` WRITE;
/*!40000 ALTER TABLE `conference_participant` DISABLE KEYS */;
INSERT INTO `conference_participant` (`id`, `conference_id`, `user_id`, `join_time`, `leave_time`, `role`) VALUES (1,1,2,'2025-12-06 19:37:07',NULL,0);
INSERT INTO `conference_participant` (`id`, `conference_id`, `user_id`, `join_time`, `leave_time`, `role`) VALUES (2,1,1,'2025-12-06 19:37:58',NULL,0);
INSERT INTO `conference_participant` (`id`, `conference_id`, `user_id`, `join_time`, `leave_time`, `role`) VALUES (3,6,1,'2025-12-07 01:30:29',NULL,0);
INSERT INTO `conference_participant` (`id`, `conference_id`, `user_id`, `join_time`, `leave_time`, `role`) VALUES (4,2,1,'2025-12-07 01:32:43',NULL,0);
INSERT INTO `conference_participant` (`id`, `conference_id`, `user_id`, `join_time`, `leave_time`, `role`) VALUES (5,7,1,'2025-12-09 01:23:06',NULL,0);
INSERT INTO `conference_participant` (`id`, `conference_id`, `user_id`, `join_time`, `leave_time`, `role`) VALUES (6,8,1,'2025-12-09 01:55:37',NULL,0);
INSERT INTO `conference_participant` (`id`, `conference_id`, `user_id`, `join_time`, `leave_time`, `role`) VALUES (7,9,2,'2025-12-09 13:08:46',NULL,0);
INSERT INTO `conference_participant` (`id`, `conference_id`, `user_id`, `join_time`, `leave_time`, `role`) VALUES (8,10,2,'2025-12-09 19:33:32',NULL,0);
INSERT INTO `conference_participant` (`id`, `conference_id`, `user_id`, `join_time`, `leave_time`, `role`) VALUES (9,11,2,'2025-12-09 20:08:26',NULL,0);
INSERT INTO `conference_participant` (`id`, `conference_id`, `user_id`, `join_time`, `leave_time`, `role`) VALUES (10,12,2,'2025-12-10 10:14:33',NULL,0);
INSERT INTO `conference_participant` (`id`, `conference_id`, `user_id`, `join_time`, `leave_time`, `role`) VALUES (11,14,1,'2025-12-11 02:28:28',NULL,0);
INSERT INTO `conference_participant` (`id`, `conference_id`, `user_id`, `join_time`, `leave_time`, `role`) VALUES (12,17,2,'2025-12-11 13:56:16',NULL,0);
INSERT INTO `conference_participant` (`id`, `conference_id`, `user_id`, `join_time`, `leave_time`, `role`) VALUES (13,18,2,'2025-12-11 15:06:08',NULL,0);
INSERT INTO `conference_participant` (`id`, `conference_id`, `user_id`, `join_time`, `leave_time`, `role`) VALUES (14,19,2,'2025-12-11 17:41:15',NULL,0);
INSERT INTO `conference_participant` (`id`, `conference_id`, `user_id`, `join_time`, `leave_time`, `role`) VALUES (15,20,2,'2025-12-12 01:04:09',NULL,0);
INSERT INTO `conference_participant` (`id`, `conference_id`, `user_id`, `join_time`, `leave_time`, `role`) VALUES (16,22,1,'2025-12-12 03:28:11',NULL,0);
INSERT INTO `conference_participant` (`id`, `conference_id`, `user_id`, `join_time`, `leave_time`, `role`) VALUES (17,22,2,'2025-12-12 03:28:26',NULL,0);
INSERT INTO `conference_participant` (`id`, `conference_id`, `user_id`, `join_time`, `leave_time`, `role`) VALUES (18,23,2,'2025-12-12 16:41:16',NULL,0);
INSERT INTO `conference_participant` (`id`, `conference_id`, `user_id`, `join_time`, `leave_time`, `role`) VALUES (19,24,2,'2025-12-13 00:59:29',NULL,0);
INSERT INTO `conference_participant` (`id`, `conference_id`, `user_id`, `join_time`, `leave_time`, `role`) VALUES (20,25,2,'2025-12-13 13:24:22',NULL,0);
INSERT INTO `conference_participant` (`id`, `conference_id`, `user_id`, `join_time`, `leave_time`, `role`) VALUES (21,26,2,'2025-12-13 13:25:28',NULL,0);
INSERT INTO `conference_participant` (`id`, `conference_id`, `user_id`, `join_time`, `leave_time`, `role`) VALUES (22,26,1,'2025-12-13 13:25:40',NULL,0);
INSERT INTO `conference_participant` (`id`, `conference_id`, `user_id`, `join_time`, `leave_time`, `role`) VALUES (23,27,2,'2025-12-13 15:42:14',NULL,0);
/*!40000 ALTER TABLE `conference_participant` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `file_info` WRITE;
/*!40000 ALTER TABLE `file_info` DISABLE KEYS */;
/*!40000 ALTER TABLE `file_info` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `friend` WRITE;
/*!40000 ALTER TABLE `friend` DISABLE KEYS */;
/*!40000 ALTER TABLE `friend` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `message` WRITE;
/*!40000 ALTER TABLE `message` DISABLE KEYS */;
/*!40000 ALTER TABLE `message` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
INSERT INTO `user` (`id`, `username`, `password`, `nickname`, `avatar`, `sip_uri`, `sip_password`, `login_token`, `login_device`, `status`, `create_time`, `update_time`, `last_login_time`) VALUES (1,'user100','100','user100',NULL,'sip:100@10.129.114.129','100','1-user100-1765612436480-43926','Unknown Device',1,'2025-11-29 13:31:52','2025-12-13 15:53:56','2025-12-13 15:53:56');
INSERT INTO `user` (`id`, `username`, `password`, `nickname`, `avatar`, `sip_uri`, `sip_password`, `login_token`, `login_device`, `status`, `create_time`, `update_time`, `last_login_time`) VALUES (2,'user101','101','user101',NULL,'sip:101@10.129.114.129','101','2-user101-1765612435652-98893','Unknown Device',1,'2025-11-29 13:31:52','2025-12-13 15:53:56','2025-12-13 15:53:56');
INSERT INTO `user` (`id`, `username`, `password`, `nickname`, `avatar`, `sip_uri`, `sip_password`, `login_token`, `login_device`, `status`, `create_time`, `update_time`, `last_login_time`) VALUES (3,'user102','102','user102',NULL,'sip:102@10.129.114.129','102',NULL,NULL,1,'2025-11-29 13:31:52','2025-12-11 17:43:33','2025-12-05 14:33:15');
INSERT INTO `user` (`id`, `username`, `password`, `nickname`, `avatar`, `sip_uri`, `sip_password`, `login_token`, `login_device`, `status`, `create_time`, `update_time`, `last_login_time`) VALUES (4,'user103','103','user103',NULL,'sip:103@10.129.114.129','103',NULL,NULL,0,'2025-11-29 13:31:52','2025-12-11 17:43:37',NULL);
INSERT INTO `user` (`id`, `username`, `password`, `nickname`, `avatar`, `sip_uri`, `sip_password`, `login_token`, `login_device`, `status`, `create_time`, `update_time`, `last_login_time`) VALUES (5,'user104','104','user104',NULL,'sip:104@10.129.114.129','104',NULL,NULL,0,'2025-11-29 13:31:52','2025-12-11 17:44:01',NULL);
/*!40000 ALTER TABLE `user` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

