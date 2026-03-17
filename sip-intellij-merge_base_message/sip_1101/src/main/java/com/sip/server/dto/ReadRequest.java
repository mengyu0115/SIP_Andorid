package com.sip.server.dto;

import lombok.Data;

/**
 * 标记消息已读请求
 */
@Data
public class ReadRequest {
    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 消息ID列表
     */
    private Long[] messageIds;
}
