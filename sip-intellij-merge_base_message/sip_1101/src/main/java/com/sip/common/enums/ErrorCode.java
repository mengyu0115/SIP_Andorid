package com.sip.common.enums;

/**
 * 错误码枚举
 *
 * @author SIP Team - 成员3
 */
public enum ErrorCode {

    // 系统错误码 (1xxx)
    SUCCESS(200, "操作成功"),
    SYSTEM_ERROR(500, "系统错误"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),

    // 用户相关 (2xxx)
    USER_NOT_EXIST(2001, "用户不存在"),
    USERNAME_EXIST(2002, "用户名已存在"),
    PASSWORD_ERROR(2003, "密码错误"),
    USER_OFFLINE(2004, "用户离线"),

    // 消息相关 (3xxx)
    MESSAGE_SEND_FAIL(3001, "消息发送失败"),
    MESSAGE_NOT_EXIST(3002, "消息不存在"),
    MESSAGE_RECALL_TIMEOUT(3003, "消息撤回超时(超过2分钟)"),

    // 文件相关 (4xxx)
    FILE_UPLOAD_FAIL(4001, "文件上传失败"),
    FILE_TYPE_NOT_SUPPORT(4002, "文件类型不支持"),
    FILE_SIZE_EXCEED(4003, "文件大小超限(最大100MB)"),

    // 好友相关 (5xxx)
    FRIEND_EXIST(5001, "好友已存在"),
    FRIEND_NOT_EXIST(5002, "好友不存在"),
    CANNOT_ADD_SELF(5003, "不能添加自己为好友");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
