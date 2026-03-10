package com.example.myapplication.notification;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.myapplication.ChatActivity;
import com.example.myapplication.R;

/**
 * 系统通知工具类
 *
 * 负责通知渠道创建、权限检查、消息通知显示和取消。
 * 每个发送者使用稳定的 notificationId，新消息更新而不堆叠。
 */
public class NotificationHelper {

    private static final String CHANNEL_ID = "sip_message_channel";
    private static final String CHANNEL_NAME = "聊天消息";
    private static final String CHANNEL_DESC = "SIP 聊天消息通知";

    public static final int REQUEST_NOTIFICATION_PERMISSION = 1001;

    private final Context context;

    public NotificationHelper(Context context) {
        this.context = context.getApplicationContext();
        createNotificationChannel();
    }

    /** 创建通知渠道（Android 8+ 必需） */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(CHANNEL_DESC);
            channel.enableVibration(true);
            channel.setShowBadge(true);

            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    /** 检查是否拥有通知权限（Android 13+ 需要运行时权限） */
    public boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /** 请求通知权限（Android 13+） */
    public void requestNotificationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATION_PERMISSION);
        }
    }

    /**
     * 显示消息通知
     *
     * @param fromUsername  发送者 SIP 用户名
     * @param fromUserId   发送者用户 ID
     * @param displayName  发送者显示名称
     * @param contentPreview 消息预览内容
     */
    public void showMessageNotification(String fromUsername, long fromUserId,
                                         String displayName, String contentPreview) {
        if (!hasNotificationPermission()) return;

        // 点击通知 → 跳转 ChatActivity
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_USER_ID, fromUserId);
        intent.putExtra(ChatActivity.EXTRA_USER_NAME, displayName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(context,
                (int) fromUserId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String preview = buildPreview(contentPreview);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(displayName)
                .setContentText(preview)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE);

        int notificationId = getNotificationId(fromUsername);
        NotificationManagerCompat.from(context).notify(notificationId, builder.build());
    }

    /** 取消指定发送者的通知（打开聊天时调用） */
    public void cancelNotification(String fromUsername) {
        int notificationId = getNotificationId(fromUsername);
        NotificationManagerCompat.from(context).cancel(notificationId);
    }

    /** 为每个发送者生成稳定的通知 ID */
    private int getNotificationId(String fromUsername) {
        return ("msg_" + fromUsername).hashCode();
    }

    /**
     * 构建消息预览文本
     * 处理特殊消息类型的前缀
     */
    static String buildPreview(String content) {
        if (content == null) return "";
        if (content.startsWith("[图片]") || content.startsWith("[IMAGE]")) return "[图片]";
        if (content.startsWith("[文件]") || content.startsWith("[FILE]")) {
            // 提取文件名
            String data = content.startsWith("[文件]") ? content.substring(4) : content.substring(6);
            String[] parts = data.split("\\|", 2);
            return "[文件] " + parts[0];
        }
        if (content.startsWith("[语音]")) return "[语音]";
        if (content.startsWith("[视频]")) return "[视频]";
        return content;
    }
}
