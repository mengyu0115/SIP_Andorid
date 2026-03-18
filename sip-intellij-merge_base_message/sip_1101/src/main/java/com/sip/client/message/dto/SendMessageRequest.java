package com.sip.client.message.dto;

import lombok.Data;

/**
 * 发送消息请求DTO
 *
 * @author 成员3
 */
@Data
public class SendMessageRequest {

    /**
     * 发送者ID
     */
    private Long fromUserId;

    /**
     * 接收者ID
     */
    private Long toUserId;

    /**
     * 消息类型: 1-文本 2-图片 3-语音 4-视频 5-文件
     */
    private Integer msgType = 1;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 文件URL (图片/语音/视频/文件消息使用)
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
}
