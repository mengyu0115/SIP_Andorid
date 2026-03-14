package com.sip.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 消息实体类
 *
 * @author SIP Team - Member 3
 * @version 1.0
 */
@Data
@TableName("message")
public class Message {

    /**
     * 消息ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 发送者ID
     */
    private Long fromUserId;

    /**
     * 接收者ID
     */
    private Long toUserId;

    /**
     * 消息类型: 1-文字 2-图片 3-语音 4-视频 5-文件
     */
    private Integer msgType;

    /**
     * 消息内容 (文字内容或文件URL)
     */
    private String content;

    /**
     * 文件URL
     */
    private String fileUrl;

    /**
     * 文件大小(字节)
     */
    private Long fileSize;

    /**
     * 语音/视频时长(秒)
     */
    private Integer duration;

    /**
     * 是否已读: 0-未读 1-已读
     */
    private Integer isRead;

    /**
     * 是否离线消息: 0-在线 1-离线
     */
    private Integer isOffline;

    /**
     * 发送时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime sendTime;

    /**
     * 获取时间戳（毫秒）
     * 用于客户端直接显示，避免时区转换问题
     */
    @JsonProperty("timestamp")
    public long getTimestamp() {
        if (sendTime == null) {
            return System.currentTimeMillis();
        }
        // 将LocalDateTime转换为毫秒时间戳
        return sendTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
