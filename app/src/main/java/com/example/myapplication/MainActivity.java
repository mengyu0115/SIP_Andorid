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

        // 从 username 提取 SIP number（如 "user102" → 102），用于兼容 PC 端存储
        Long sipNumber = parseSipNumberFromUsername(ServerConfig.getCurrentUsername());

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // 先拉取用户列表，构建 userId → SIP号码 映射
                java.util.Map<Long, String> userIdToSipNumber = buildUserIdToSipNumberMap();

                // 合并两次查询的离线消息（用数据库 id 和 SIP number 各查一次）
                JSONArray mergedArr = new JSONArray();
                java.util.Set<Long> seenMsgIds = new java.util.HashSet<>();

                // 查询 1：用数据库真实 id（Android 端存储方式）
                collectOfflineMessages(userId, mergedArr, seenMsgIds);

                // 查询 2：用 SIP number（PC 端存储方式）
                if (sipNumber != null && sipNumber != userId) {
                    collectOfflineMessages(sipNumber, mergedArr, seenMsgIds);
                }

                if (mergedArr.length() == 0) {
                    Log.d(TAG, "无离线消息");
                    return;
                }

                Log.i(TAG, "收到 " + mergedArr.length() + " 条离线消息");

                // 开启批量模式
                if (messageNotificationListener != null) {
                    messageNotificationListener.setBatchMode(true);
                }

                SipMessageReceiver receiver = SipMessageReceiver.getInstance();
                for (int i = 0; i < mergedArr.length(); i++) {
                    JSONObject msg = mergedArr.getJSONObject(i);
                    long fromUserId = msg.optLong("fromUserId", -1L);
                    int msgType = msg.optInt("msgType", 1);
                    String content = msg.optString("content", "");
                    String fileUrl = msg.optString("fileUrl", null);

                    String sipContent = buildOfflineMessageContent(msgType, content, fileUrl);

                    // 将 fromUserId 映射为 SIP 号码
                    String fromSipNumber = userIdToSipNumber.get(fromUserId);
                    if (fromSipNumber == null) {
                        fromSipNumber = String.valueOf(fromUserId);
                    }
                    String fromUri = "sip:" + fromSipNumber + "@" + ServerConfig.getServerIp();
                    receiver.onSipMessageReceived(fromUri, sipContent);
                }

                if (messageNotificationListener != null) {
                    messageNotificationListener.setBatchMode(false);
                    messageNotificationListener.flushBatchNotifications();
                }

            } catch (Exception e) {
                Log.e(TAG, "加载离线消息失败", e);
                if (messageNotificationListener != null) {
                    messageNotificationListener.setBatchMode(false);
                }
            }
        });
    }

    /**
     * 从 username 解析 SIP number（如 "user102" → 102L）
     */
    private static Long parseSipNumberFromUsername(String username) {
        if (username != null && username.startsWith("user")) {
            try {
                return Long.parseLong(username.substring(4));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 查询离线消息并合并到数组，按消息 id 去重
     */
    private void collectOfflineMessages(long queryUserId, JSONArray outArr, java.util.Set<Long> seenIds) {
        try {
            String raw = ApiClient.getOfflineMessages(queryUserId);
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
            if (arr == null) return;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject msg = arr.getJSONObject(i);
                long msgId = msg.optLong("id", -1L);
                if (msgId > 0 && !seenIds.add(msgId)) continue;
                outArr.put(msg);
            }
        } catch (Exception e) {
            Log.w(TAG, "collectOfflineMessages(" + queryUserId + ") failed", e);
        }
    }

    /**
     * 构建 userId(数据库id) → SIP号码 的映射
     * 例如：{1L: "100", 2L: "101"}（user100 的 id=1, user101 的 id=2）
     */
    private java.util.Map<Long, String> buildUserIdToSipNumberMap() {
        java.util.Map<Long, String> map = new java.util.HashMap<>();
        try {
            String raw = ApiClient.getUserList();
            if (raw == null) return map;

            JSONObject root = new JSONObject(raw);
            if (root.optInt("code", -1) != 200) return map;

            JSONObject data = root.optJSONObject("data");
            JSONArray arr = (data != null) ? data.optJSONArray("list") : root.optJSONArray("data");
            if (arr == null) return map;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                long id = obj.optLong("id", -1L);
                String username = obj.optString("username", "");
                if (id > 0 && !username.isEmpty()) {
                    // "user100" -> "100"
                    String sipNumber = username.startsWith("user") ? username.substring(4) : username;
                    map.put(id, sipNumber);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "构建用户映射失败", e);
        }
        return map;
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
