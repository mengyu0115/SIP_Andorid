package com.sip.common.dto;

import lombok.Data;

/**
 * 通话记录DTO
 * 用于客户端提交通话记录
 *
 * @author SIP Team
 * @version 1.0
 */
@Data
public class CallRecordDTO {

    /**
     * 主叫用户名
     */
    private String callerUsername;

    /**
     * 被叫用户名
     */
    private String calleeUsername;

    /**
     * 通话类型: audio / video
     */
    private String callType;

    /**
     * 开始时间（格式：yyyy-MM-dd HH:mm:ss）
     */
    private String startTime;

    /**
     * 结束时间（格式：yyyy-MM-dd HH:mm:ss）
     */
    private String endTime;

    /**
     * 通话时长(秒)
     */
    private Long duration;
}
