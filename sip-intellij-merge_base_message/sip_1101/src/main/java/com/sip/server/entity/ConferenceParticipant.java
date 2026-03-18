package com.sip.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会议参与者实体类
 *
 * @author SIP Team - Member 4
 * @version 1.0
 */
@Data
@TableName("conference_participant")
public class ConferenceParticipant {

    /**
     * 参与者记录ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会议ID
     */
    private Long conferenceId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 加入时间
     */
    private LocalDateTime joinTime;

    /**
     * 离开时间
     */
    private LocalDateTime leaveTime;

    /**
     * 角色: 0-普通参与者 1-主持人
     */
    private Integer role;
}
