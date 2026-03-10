package com.sip.server.dto;

import lombok.Data;

/**
 * 消息传输对象
 */
@Data
public class MessageDTO {
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
     * 文件URL(可选)
     */
    private String fileUrl;

    /**
     * 文件大小(可选)
     */
    private Long fileSize;

    /**
     * 时长(可选,用于语音/视频)
     */
    private Integer duration;
}
