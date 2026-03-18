package com.example.myapplication;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.myapplication.adapter.MainPagerAdapter;
import com.example.myapplication.databinding.ActivityMainBinding;
import com.example.myapplication.fragment.ConversationFragment;
import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.ServerConfig;
import com.example.myapplication.notification.MessageNotificationListener;
import com.example.myapplication.notification.NotificationHelper;
import com.example.myapplication.notification.UnreadManager;
import com.example.myapplication.service.HeartbeatService;
import com.example.myapplication.sip.SdpManager;
import com.example.myapplication.sip.SipCallHandler;
import com.example.myapplication.sip.SipManager;

import com.google.android.material.badge.BadgeDrawable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.Executors;

/**
 * 主 Activity
 *
 * 单 Activity 架构，使用 ViewPager2 + BottomNavigationView 管理三个主页面：
 * - Tab 0: ConversationFragment（消息/会话列表）
 * - Tab 1: ContactsFragment（联系人，占位）
 * - Tab 2: ProfileFragment（我的，占位）
 */
public class MainActivity extends AppCompatActivity
        implements ConversationFragment.NotificationListenerProvider {

    private static final String TAG = "MainActivity";

    private ActivityMainBinding binding;

    private NotificationHelper notificationHelper;
    private MessageNotificationListener messageNotificationListener;
    private UnreadManager.UnreadCountChangeListener unreadBadgeListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupViewPager();
        setupBottomNavigation();
        setupNotifications();
        setupIncomingCallListener();

        // 拉取离线消息（对应 PC 端登录后加载离线消息的逻辑）
        loadOfflineMessages();
    }

    @Override
    public MessageNotificationListener getMessageNotificationListener() {
        return messageNotificationListener;
    }

    private void setupViewPager() {
        MainPagerAdapter adapter = new MainPagerAdapter(this);
        binding.viewPager.setAdapter(adapter);

        // 禁止手指左右滑动切换 Tab，只允许底部导航切换
        binding.viewPager.setUserInputEnabled(false);

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                switch (position) {
                    case 0:
                        binding.bottomNav.setSelectedItemId(R.id.nav_conversation);
                        break;
                    case 1:
                        binding.bottomNav.setSelectedItemId(R.id.nav_contacts);
                        break;
                    case 2:
                        binding.bottomNav.setSelectedItemId(R.id.nav_conference);
                        break;
                    case 3:
                        binding.bottomNav.setSelectedItemId(R.id.nav_profile);
                        break;
                }
            }
        });
    }

    private void setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_conversation) {
                binding.viewPager.setCurrentItem(0, false);
                return true;
            } else if (id == R.id.nav_contacts) {
                binding.viewPager.setCurrentItem(1, false);
                return true;
            } else if (id == R.id.nav_conference) {
                binding.viewPager.setCurrentItem(2, false);
                return true;
            } else if (id == R.id.nav_profile) {
                binding.viewPager.setCurrentItem(3, false);
                return true;
            }
            return false;
        });

        // 默认选中消息 Tab
        binding.bottomNav.setSelectedItemId(R.id.nav_conversation);
    }

    /**
     * 初始化通知系统
     * 1. 创建 NotificationHelper（含通知渠道）
     * 2. 请求通知权限（Android 13+）
     * 3. 创建 MessageNotificationListener 并注册到 SipMessageReceiver
     * 4. 注册 UnreadManager 监听器 → 更新 BottomNav badge
     */
    private void setupNotifications() {
        // 1. 初始化通知工具
        notificationHelper = new NotificationHelper(this);

        // 2. 请求通知权限
        if (!notificationHelper.hasNotificationPermission()) {
            notificationHelper.requestNotificationPermission(this);
        }

        // 3. 创建全局消息通知监听器并注册
        messageNotificationListener = new MessageNotificationListener(notificationHelper);
        SipMessageReceiver.getInstance().addListener(messageNotificationListener);

        // 4. 注册未读数变化监听器 → 更新 BottomNav badge
        unreadBadgeListener = this::updateBottomNavBadge;
        UnreadManager.getInstance().addListener(unreadBadgeListener);
    }

    /**
     * 更新 BottomNav "消息" Tab 的红色 badge
     */
    private void updateBottomNavBadge(int totalUnread) {
        BadgeDrawable badge = binding.bottomNav.getOrCreateBadge(R.id.nav_conversation);
        if (totalUnread > 0) {
            badge.setVisible(true);
            badge.setNumber(totalUnread);
        } else {
            badge.setVisible(false);
            badge.clearNumber();
        }
    }

    /**
     * 注册全局来电监听器
     * 当收到 SIP INVITE 时启动 CallActivity（来电模式）
     */
    private void setupIncomingCallListener() {
        SipManager.getInstance().setIncomingCallListener(new SipCallHandler.CallEventListener() {
            @Override
            public void onCalling(String remoteUser, String callType) {}

            @Override
            public void onIncomingCall(String remoteUser, String callType, String callId) {
                runOnUiThread(() -> {
                    CallActivity.startIncoming(MainActivity.this, remoteUser, callType, callId);
                });
            }

            @Override
            public void onRinging() {}

            @Override
            public void onCallEstablished(SdpManager.MediaInfo remoteMedia) {}

            @Override
            public void onCallEnded(String reason) {}

            @Override
            public void onCallFailed(String reason) {}
        });
    }

    /**
     * 加载离线消息
     *
     * GET /api/message/offline/{userId} 返回该用户不在线期间收到的消息列表。
     * 将每条离线消息通过 SipMessageReceiver 路由，
     * 如果此时 ChatActivity 已打开对应会话，则消息会实时显示。
     *
     * 注意：ChatActivity.loadChatHistory() 也会拉取最近 50 条历史，
     * 此处主要目的是触发服务端离线消息标记为已投递，并在主界面尽早通知。
     */
    private void loadOfflineMessages() {
        Long userId = ServerConfig.getCurrentUserId();
        if (userId == null) return;

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String raw = ApiClient.getOfflineMessages(userId);
                if (raw == null) return;

                JSONObject root = new JSONObject(raw);
                if (root.optInt("code", -1) != 200) return;

                JSONArray arr = null;
                if (root.opt("data") instanceof JSONArray) {
                    arr = root.getJSONArray("data");
                } else if (root.opt("data") instanceof JSONObject) {
                    JSONObject dataObj = root.getJSONObject("data");
                    arr = dataObj.optJSONArray("list");
                }

                if (arr == null || arr.length() == 0) {
                    Log.d(TAG, "无离线消息");
                    return;
                }

                Log.i(TAG, "收到 " + arr.length() + " 条离线消息");

                // 开启批量模式，避免离线消息大量通知同时弹出
                if (messageNotificationListener != null) {
                    messageNotificationListener.setBatchMode(true);
                }

                SipMessageReceiver receiver = SipMessageReceiver.getInstance();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject msg = arr.getJSONObject(i);
                    long fromUserId = msg.optLong("fromUserId", -1L);
                    int msgType = msg.optInt("msgType", 1);
                    String content = msg.optString("content", "");
                    String fileUrl = msg.optString("fileUrl", null);

                    // 构造与 SIP MESSAGE 一致的内容格式，便于 ChatActivity 统一解析
                    String sipContent = buildOfflineMessageContent(msgType, content, fileUrl);

                    // 用 fromUserId 作为发送者标识（SipMessageReceiver 会提取用户名）
                    String fromUri = "sip:" + fromUserId + "@" + ServerConfig.getServerIp();
                    receiver.onSipMessageReceived(fromUri, sipContent);
                }

                // 关闭批量模式并 flush 汇总通知
                if (messageNotificationListener != null) {
                    messageNotificationListener.setBatchMode(false);
                    messageNotificationListener.flushBatchNotifications();
                }

            } catch (Exception e) {
                Log.e(TAG, "加载离线消息失败", e);
                // 确保异常时也关闭批量模式
                if (messageNotificationListener != null) {
                    messageNotificationListener.setBatchMode(false);
                }
            }
        });
    }

    /**
     * 根据 msgType 构造与 SIP MESSAGE 一致的消息内容
     * msgType: 1=文本 2=图片 3=语音 4=视频 5=文件
     */
    private String buildOfflineMessageContent(int msgType, String content, String fileUrl) {
        switch (msgType) {
            case 2: // 图片
                return "[图片]" + (fileUrl != null ? fileUrl : content);
            case 3: // 语音
                return "[语音]" + (fileUrl != null ? fileUrl : content);
            case 4: // 视频
                return "[视频]" + (fileUrl != null ? fileUrl : content);
            case 5: // 文件
                String fileName = (content != null && !content.isEmpty()) ? content : "file";
                return "[文件]" + fileName + "|" + (fileUrl != null ? fileUrl : "");
            default: // 文本
                return content;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清理通知监听器
        if (messageNotificationListener != null) {
            SipMessageReceiver.getInstance().removeListener(messageNotificationListener);
        }
        if (unreadBadgeListener != null) {
            UnreadManager.getInstance().removeListener(unreadBadgeListener);
        }

        // App 退出时自动下线（对应 PC 端窗口关闭时的清理逻辑）
        // SIP 注销（UDP 发一个包，很快）
        try {
            SipManager.getInstance().unregister();
        } catch (Exception ignored) {}

        // HTTP 登出（新线程，因为 onDestroy 中不宜阻塞）
        Long userId = ServerConfig.getCurrentUserId();
        if (userId != null) {
            new Thread(() -> {
                try {
                    ApiClient.logoutOnlineStatus(userId);
                } catch (Exception ignored) {}
            }).start();
        }

        // 停止心跳
        HeartbeatService.getInstance().stop();
    }
}
