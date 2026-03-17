package com.sip.common.enums;

/**
 * 参与者角色枚举
 *
 * @author SIP Team - Member 4
 * @version 1.0
 */
public enum ParticipantRole {

    /**
     * 普通参与者
     */
    PARTICIPANT(0, "普通参与者"),

    /**
     * 主持人
     */
    MODERATOR(1, "主持人");

    private final Integer code;
    private final String description;

    ParticipantRole(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static ParticipantRole fromCode(Integer code) {
        for (ParticipantRole role : values()) {
            if (role.code.equals(code)) {
                return role;
            }
        }
        return null;
    }
}
