package com.sip.client.presence;

/**
 * SIP 在线状态枚举
 *
 * 对应 PIDF (Presence Information Data Format) 标准状态
 */
public enum PresenceStatus {

    /**
     * 在线 - open
     */
    ONLINE("open", "在线"),

    /**
     * 离线 - closed
     */
    OFFLINE("closed", "离线"),

    /**
     * 忙碌 - busy
     */
    BUSY("busy", "忙碌"),

    /**
     * 离开 - away
     */
    AWAY("away", "离开");

    private final String pidfStatus;
    private final String displayName;

    PresenceStatus(String pidfStatus, String displayName) {
        this.pidfStatus = pidfStatus;
        this.displayName = displayName;
    }

    /**
     * 获取 PIDF 格式的状态字符串
     */
    public String getPidfStatus() {
        return pidfStatus;
    }

    /**
     * 获取显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 从 PIDF 状态字符串解析枚举
     */
    public static PresenceStatus fromPidf(String pidfStatus) {
        for (PresenceStatus status : values()) {
            if (status.pidfStatus.equalsIgnoreCase(pidfStatus)) {
                return status;
            }
        }
        return OFFLINE; // 默认返回离线
    }

    /**
     * 从数据库状态值解析枚举
     * 0-离线, 1-在线, 2-忙碌, 3-离开
     */
    public static PresenceStatus fromDbValue(int value) {
        switch (value) {
            case 0: return OFFLINE;
            case 1: return ONLINE;
            case 2: return BUSY;
            case 3: return AWAY;
            default: return OFFLINE;
        }
    }

    /**
     * 转换为数据库状态值
     */
    public int toDbValue() {
        switch (this) {
            case OFFLINE: return 0;
            case ONLINE: return 1;
            case BUSY: return 2;
            case AWAY: return 3;
            default: return 0;
        }
    }
}
