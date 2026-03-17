package com.sip.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通话记录实体类
 *
 * @author SIP Team
 * @version 1.0
 */
@Data
@TableName("call_record")
public class CallRecord {

    /**
     * 通话记录ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 主叫ID
     */
    private Long callerId;

    /**
     * 被叫ID
     */
    private Long calleeId;

    /**
     * 通话类型: 1-音频 2-视频 3-群聊
     */
    private Integer callType;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 通话时长(秒)
     */
    private Integer duration;

    /**
     * 状态: 1-成功 2-未接 3-拒绝 4-取消 5-失败
     */
    private Integer status;

    /**
     * 通话质量: 1-差 2-中 3-良 4-优
     */
    private Integer quality;

    /**
     * RTP发送包数
     */
    private Integer rtpPacketsSent;

    /**
     * RTP接收包数
     */
    private Integer rtpPacketsReceived;

    /**
     * RTP丢包数
     */
    private Integer rtpPacketsLost;

    /**
     * 平均抖动(ms)
     */
    private Float avgJitter;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
