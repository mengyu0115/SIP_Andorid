package com.example.myapplication.model;

/**
 * 消息数据模型
 *
 * 对应服务端 com.sip.server.entity.Message
 * 用于聊天页本地展示，以及 POST /api/message/send 持久化
 */
public class ChatMessage {

    // 消息类型
    public static final int TYPE_SENT = 1;
    public static final int TYPE_RECEIVED = 2;
    public static final int TYPE_TIMESTAMP = 3;

    // 消息内容类型（对应 msgType）
    public static final String MSG_TYPE_TEXT = "text";
    public static final String MSG_TYPE_IMAGE = "image";
    public static final String MSG_TYPE_FILE = "file";

    private Long id;
    private Long fromUserId;
    private Long toUserId;
    private String fromUsername;  // 展示用
    private String content;
    private String msgType;
    private long timestamp;
    private int viewType;  // TYPE_SENT / TYPE_RECEIVED / TYPE_TIMESTAMP

    public ChatMessage() {}

    // 构造文本消息
    public static ChatMessage text(Long fromUserId, Long toUserId, String fromUsername,
                                   String content, int viewType) {
        ChatMessage m = new ChatMessage();
        m.fromUserId = fromUserId;
        m.toUserId = toUserId;
        m.fromUsername = fromUsername;
        m.content = content;
        m.msgType = MSG_TYPE_TEXT;
        m.timestamp = System.currentTimeMillis();
        m.viewType = viewType;
        return m;
    }

    // 构造时间戳分隔项
    public static ChatMessage timestamp(long time) {
        ChatMessage m = new ChatMessage();
        m.timestamp = time;
        m.viewType = TYPE_TIMESTAMP;
        return m;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFromUserId() { return fromUserId; }
    public void setFromUserId(Long fromUserId) { this.fromUserId = fromUserId; }

    public Long getToUserId() { return toUserId; }
    public void setToUserId(Long toUserId) { this.toUserId = toUserId; }

    public String getFromUsername() { return fromUsername; }
    public void setFromUsername(String fromUsername) { this.fromUsername = fromUsername; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getMsgType() { return msgType; }
    public void setMsgType(String msgType) { this.msgType = msgType; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public int getViewType() { return viewType; }
    public void setViewType(int viewType) { this.viewType = viewType; }
}
