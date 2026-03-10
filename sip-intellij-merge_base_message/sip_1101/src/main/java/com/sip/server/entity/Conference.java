package com.sip.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会议实体类
 *
 * @author SIP Team - Member 4
 * @version 1.0
 */
@Data
@TableName("conference")
public class Conference {

    /**
     * 会议ID (数据库自增主键,仅内部使用)
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会议号 (6位随机数字,用户加入会议使用)
     */
    private String conferenceCode;

    /**
     * 是否活跃 (true-活跃会议, false-历史会议)
     */
    private Boolean isActive;

    /**
     * 会议URI (唯一标识)
     */
    private String conferenceUri;

    /**
     * 创建者ID
     */
    private Long creatorId;

    /**
     * 会议标题
     */
    private String title;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 会议状态: 0-已结束 1-进行中 2-待开始
     */
    private Integer status;

    /**
     * 最大参与人数
     */
    private Integer maxParticipants;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
