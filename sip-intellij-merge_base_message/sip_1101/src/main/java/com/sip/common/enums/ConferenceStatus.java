package com.sip.common.enums;

/**
 * 会议状态枚举
 *
 * @author SIP Team - Member 4
 * @version 1.0
 */
public enum ConferenceStatus {

    /**
     * 已结束
     */
    ENDED(0, "已结束"),

    /**
     * 进行中
     */
    IN_PROGRESS(1, "进行中"),

    /**
     * 待开始
     */
    PENDING(2, "待开始");

    private final Integer code;
    private final String description;

    ConferenceStatus(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static ConferenceStatus fromCode(Integer code) {
        for (ConferenceStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
