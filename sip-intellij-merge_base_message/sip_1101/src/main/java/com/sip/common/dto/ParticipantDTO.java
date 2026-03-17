package com.sip.common.dto;

import lombok.Data;

/**
 * 参与者信息DTO
 *
 * @author SIP Team - Member 4
 * @version 1.0
 */
@Data
public class ParticipantDTO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * SIP URI
     */
    private String sipUri;

    /**
     * 角色: 0-普通参与者 1-主持人
     */
    private Integer role;

    /**
     * 加入时间
     */
    private String joinTime;

    /**
     * 是否开启音频
     */
    private Boolean audioEnabled;

    /**
     * 是否开启视频
     */
    private Boolean videoEnabled;
}
