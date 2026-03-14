package com.example.myapplication.model;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 消息数据模型
 *
 * 对应服务端 com.sip.server.entity.Message
 * 用于聊天页本地展示，以及 POST /api/message/send 持久化
 */
public class ChatMessage {

    // 消息类型（发送方向）
    public static final int TYPE_SENT = 1;
    public static final int TYPE_RECEIVED = 2;
    public static final int TYPE_DATE_HEADER = 3;  // 日期分组头

    // 消息内容类型（对应 msgType）
    public static final String MSG_TYPE_TEXT = "text";
    public static final String MSG_TYPE_IMAGE = "image";
    public static final String MSG_TYPE_VOICE = "voice";
    public static final String MSG_TYPE_VIDEO = "video";
    public static final String MSG_TYPE_FILE = "file";

    // 基础字段
    private Long id;
    private Long fromUserId;
    private Long toUserId;
    private String fromUsername;  // 展示用
    private String content;
    private String msgType;
    private long timestamp;
    private int viewType;  // TYPE_SENT / TYPE_RECEIVED / TYPE_DATE_HEADER

    // 文件相关字段
    private String fileUrl;      // 文件URL
    private Long fileSize;       // 文件大小
    private Integer duration;    // 语音/视频时长（秒）
    private String fileName;     // 文件名（用于文件类型）

    // 日期分组头专用字段
    private String dateText;  // 显示的日期文本，如"今天 3月12日"

    public ChatMessage() {}

    // ===== 构造方法 =====

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

    // 构造图片消息
    public static ChatMessage image(Long fromUserId, Long toUserId, String fromUsername,
                                    String fileUrl, Long fileSize, int viewType) {
        ChatMessage m = new ChatMessage();
        m.fromUserId = fromUserId;
        m.toUserId = toUserId;
        m.fromUsername = fromUsername;
        m.msgType = MSG_TYPE_IMAGE;
        m.fileUrl = fileUrl;
        m.fileSize = fileSize;
        m.timestamp = System.currentTimeMillis();
        m.viewType = viewType;
        return m;
    }

    // 构造文件消息
    public static ChatMessage file(Long fromUserId, Long toUserId, String fromUsername,
                                   String fileName, String fileUrl, Long fileSize, int viewType) {
        ChatMessage m = new ChatMessage();
        m.fromUserId = fromUserId;
        m.toUserId = toUserId;
        m.fromUsername = fromUsername;
        m.msgType = MSG_TYPE_FILE;
        m.fileName = fileName;
        m.fileUrl = fileUrl;
        m.fileSize = fileSize;
        m.timestamp = System.currentTimeMillis();
        m.viewType = viewType;
        return m;
    }

    // 构造日期分组头
    public static ChatMessage dateHeader(long timestamp) {
        ChatMessage msg = new ChatMessage();
        msg.setViewType(TYPE_DATE_HEADER);
        msg.setTimestamp(timestamp);
        msg.setDateText(formatDateHeader(timestamp));
        return msg;
    }

    // ===== Getter/Setter =====

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

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getDateText() { return dateText; }
    public void setDateText(String dateText) { this.dateText = dateText; }

    // ===== 判断消息类型的便捷方法 =====

    public boolean isText() { return MSG_TYPE_TEXT.equals(msgType); }
    public boolean isImage() { return MSG_TYPE_IMAGE.equals(msgType); }
    public boolean isVoice() { return MSG_TYPE_VOICE.equals(msgType); }
    public boolean isVideo() { return MSG_TYPE_VIDEO.equals(msgType); }
    public boolean isFile() { return MSG_TYPE_FILE.equals(msgType); }
    public boolean isFileMessage() { return isImage() || isVoice() || isVideo() || isFile(); }

    // ===== 静态方法：日期格式化 =====

    /**
     * 格式化日期分组头文本
     * 规则：
     * - 今天 → "今天 3月12日"
     * - 昨天 → "昨天 3月11日"
     * - 本周 → "周三 3月10日"
     * - 跨年 → "2024年12月25日"
     */
    private static String formatDateHeader(long timestamp) {
        Calendar msgCal = Calendar.getInstance();
        msgCal.setTimeInMillis(timestamp);

        Calendar todayCal = Calendar.getInstance();
        todayCal.set(Calendar.HOUR_OF_DAY, 0);
        todayCal.set(Calendar.MINUTE, 0);
        todayCal.set(Calendar.SECOND, 0);
        todayCal.set(Calendar.MILLISECOND, 0);

        Calendar yesterdayCal = (Calendar) todayCal.clone();
        yesterdayCal.add(Calendar.DAY_OF_MONTH, -1);

        // 今天
        if (msgCal.getTimeInMillis() >= todayCal.getTimeInMillis()) {
            return "今天 " + new SimpleDateFormat("M月d日", Locale.CHINA).format(msgCal.getTime());
        }

        // 昨天
        if (msgCal.getTimeInMillis() >= yesterdayCal.getTimeInMillis()) {
            return "昨天 " + new SimpleDateFormat("M月d日", Locale.CHINA).format(msgCal.getTime());
        }

        // 判断是否跨年
        int msgYear = msgCal.get(Calendar.YEAR);
        int currentYear = todayCal.get(Calendar.YEAR);

        if (msgYear == currentYear) {
            // 同年，显示"周三 3月10日"
            int dayOfWeek = msgCal.get(Calendar.DAY_OF_WEEK);
            String[] weekDays = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
            String weekDay = weekDays[dayOfWeek - 1];
            return weekDay + " " + new SimpleDateFormat("M月d日", Locale.CHINA).format(msgCal.getTime());
        } else {
            // 跨年，显示"2024年12月25日"
            return new SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(msgCal.getTime());
        }
    }
}
