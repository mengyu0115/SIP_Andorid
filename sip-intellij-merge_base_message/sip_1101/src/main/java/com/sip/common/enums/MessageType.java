package com.sip.common.enums;

/**
 * 消息类型枚举
 *
 * @author SIP Team - 成员3
 */
public enum MessageType {

    TEXT(1, "文本消息"),
    IMAGE(2, "图片消息"),
    VOICE(3, "语音消息"),
    VIDEO(4, "视频消息"),
    FILE(5, "文件消息");

    private final Integer code;
    private final String description;

    MessageType(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static MessageType fromCode(Integer code) {
        for (MessageType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return TEXT;
    }
}
