package com.sip.server.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 消息视图对象
 */
@Data
public class MessageVO {
    /**
     * 消息ID
     */
    private Long id;

    /**
     * 发送者用户ID
     */
    private Long fromUserId;

    /**
     * 接收者用户ID
     */
    private Long toUserId;

    /**
     * 消息类型: 1文本 2图片 3语音 4视频 5文件
     */
    private Integer msgType;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 文件URL
     */
    private String fileUrl;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 时长(语音/视频)
     */
    private Integer duration;

    /**
     * 是否已读: 0未读 1已读
     */
    private Integer isRead;

    /**
     * 发送时间
     */
    private LocalDateTime sendTime;

    /**
     * 发送者用户名(扩展字段)
     */
    private String fromUserName;

    /**
     * 接收者用户名(扩展字段)
     */
    private String toUserName;
}
