package com.sip.common.dto;

import lombok.Data;
import java.util.Date;
import java.util.List;

/**
 * 会议信息DTO
 *
 * @author SIP Team - Member 4
 * @version 1.0
 */
@Data
public class ConferenceDTO {

    /**
     * 会议ID
     */
    private Long id;

    /**
     * 会议URI
     */
    private String conferenceUri;

    /**
     * 会议标题
     */
    private String title;

    /**
     * 创建者ID
     */
    private Long creatorId;

    /**
     * 创建者昵称
     */
    private String creatorNickname;

    /**
     * 会议状态
     */
    private Integer status;

    /**
     * 当前参与人数
     */
    private Integer currentParticipants;

    /**
     * 最大参与人数
     */
    private Integer maxParticipants;

    /**
     * 参与者列表
     */
    private List<ParticipantDTO> participants;

    /**
     * 创建时间（用于同步会议时间显示）
     */
    private Date createTime;

    /**
     * 开始时间
     */
    private String startTime;

    /**
     * 结束时间
     */
    private String endTime;
}
