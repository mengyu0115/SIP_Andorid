package com.example.myapplication.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.ChatActivity;
import com.example.myapplication.R;
import com.example.myapplication.adapter.ConversationAdapter;
import com.example.myapplication.adapter.ConversationItem;
import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.OnlineStatusCache;
import com.example.myapplication.network.ServerConfig;
import com.example.myapplication.notification.NotificationHelper;
import com.example.myapplication.notification.UnreadManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 会话/消息列表 Fragment
 *
 * 完全对齐 PC 端 MainController.loadFriendList() + refreshUserList()：
 * 1. 同时拉取 GET /api/user/list（全部用户）和 GET /api/online/users（在线用户）
 * 2. 过滤自己，在线用户显示"在线"，离线显示"离线"
 * 3. 每 3 秒自动刷新（对应 PC 端 startOnlineStatusRefresh 每 3 秒刷新）
 * 4. 点击 item 跳转 ChatActivity（对应 PC 端 openChat(friendName)）
 * 5. 集成未读计数角标
 */
public class ConversationFragment extends Fragment {

    private static final String TAG = "ConversationFragment";
    private static final long REFRESH_INTERVAL_SECONDS = 3;

    private ConversationAdapter adapter;
    private RecyclerView rvConversations;

    // 定时刷新（对应 PC 端 onlineStatusTimer）
    private ScheduledExecutorService refreshScheduler;
    private ScheduledFuture<?> refreshTask;

    // 未读计数变化监听器
    private UnreadManager.UnreadCountChangeListener unreadListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_conversation, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvConversations = view.findViewById(R.id.rvConversations);
        rvConversations.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvConversations.addItemDecoration(
                new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));

        adapter = new ConversationAdapter();
        adapter.setOnItemClickListener(item -> {
            // 清除未读计数 + 取消通知
            String sipId = String.valueOf(item.getUserId());
            UnreadManager.getInstance().clearUnread(sipId);
            new NotificationHelper(requireContext()).cancelNotification(sipId);

            // 点击跳转聊天页（对应 PC 端 openChat(friendName)）
            Intent intent = new Intent(requireContext(), ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_USER_ID, item.getUserId());
            intent.putExtra(ChatActivity.EXTRA_USER_NAME, item.getName());
            startActivity(intent);
        });
        rvConversations.setAdapter(adapter);

        // 注册未读数变化监听器：收到变化时刷新列表
        unreadListener = totalUnread -> refreshUserList();
        UnreadManager.getInstance().addListener(unreadListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 立即刷新一次，然后启动定时器（对应 PC 端 startOnlineStatusRefresh）
        refreshUserList();
        startAutoRefresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopAutoRefresh();
    }

    /**
     * 启动定时刷新（对应 PC 端 startOnlineStatusRefresh，间隔 3 秒）
     */
    private void startAutoRefresh() {
        if (refreshScheduler == null || refreshScheduler.isShutdown()) {
            refreshScheduler = Executors.newSingleThreadScheduledExecutor();
        }
        if (refreshTask == null || refreshTask.isCancelled()) {
            refreshTask = refreshScheduler.scheduleAtFixedRate(
                    this::refreshUserList,
                    REFRESH_INTERVAL_SECONDS,
                    REFRESH_INTERVAL_SECONDS,
                    TimeUnit.SECONDS
            );
        }
    }

    private void stopAutoRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
    }

    /**
     * 刷新用户列表
     * 对应 PC 端 refreshUserList()：
     * 1. fetchAllUsers() → GET /api/user/list
     * 2. fetchOnlineUsers() → GET /api/online/users
     * 3. 合并：在线标"在线"，离线标"离线"，过滤自己
     * 4. 查询每个用户的未读数
     */
    private void refreshUserList() {
        if (refreshScheduler == null || refreshScheduler.isShutdown()) return;
        refreshScheduler.execute(() -> {
            try {
                // 1. 获取全部用户列表（对应 PC 端 fetchAllUsers）
                List<UserEntry> allUsers = fetchAllUsers();

                // 2. 获取在线用户集合（对应 PC 端 fetchOnlineUsers）
                Set<Long> onlineIds = fetchOnlineUserIds();

                // 同步更新在线状态缓存（供 ChatActivity 发送消息时判断 isOffline）
                OnlineStatusCache.getInstance().updateOnlineIds(onlineIds);

                Long myId = ServerConfig.getCurrentUserId();
                UnreadManager unreadManager = UnreadManager.getInstance();

                // 3. 合并构建列表（对应 PC 端平台线程中更新 friendListView）
                List<ConversationItem> items = new ArrayList<>();
                for (UserEntry u : allUsers) {
                    if (myId != null && u.id == myId) continue; // 过滤自己
                    boolean isOnline = onlineIds.contains(u.id);
                    String status = isOnline ? "在线" : "离线";

                    // 查询未读计数（用 userId 作为 key，与 SipMessageReceiver 提取的 fromUsername 一致）
                    int unread = unreadManager.getUnreadCount(String.valueOf(u.id));
                    items.add(new ConversationItem(u.id, u.username, status, status, unread));

                    // 缓存用户信息到 MessageNotificationListener（通知显示名称用）
                    cacheUserInfoForNotification(String.valueOf(u.id), u.id, u.username);
                }

                if (items.isEmpty()) {
                    return;
                }

                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> adapter.submitList(items));
                }

            } catch (Exception e) {
                Log.e(TAG, "刷新用户列表失败", e);
            }
        });
    }

    /**
     * 缓存用户信息到 MessageNotificationListener
     * 通过 MainActivity 获取 listener 实例
     */
    private void cacheUserInfoForNotification(String sipUsername, long userId, String displayName) {
        if (getActivity() instanceof NotificationListenerProvider) {
            com.example.myapplication.notification.MessageNotificationListener listener =
                    ((NotificationListenerProvider) getActivity()).getMessageNotificationListener();
            if (listener != null) {
                listener.cacheUserDisplayInfo(sipUsername, userId, displayName);
            }
        }
    }

    /** 接口：由 MainActivity 实现，用于获取 MessageNotificationListener 实例 */
    public interface NotificationListenerProvider {
        com.example.myapplication.notification.MessageNotificationListener getMessageNotificationListener();
    }

    /** 获取全部用户列表 → GET /api/user/list */
    private List<UserEntry> fetchAllUsers() {
        List<UserEntry> result = new ArrayList<>();
        try {
            String raw = ApiClient.getUserList();
            if (raw == null) return result;

            JSONObject root = new JSONObject(raw);
            if (root.optInt("code", -1) != 200) return result;

            // data 结构：{total, list: [{id, username, ...}]}
            JSONObject data = root.optJSONObject("data");
            JSONArray arr = (data != null) ? data.optJSONArray("list") : root.optJSONArray("data");
            if (arr == null) return result;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                long id = obj.optLong("id", -1L);
                String username = obj.optString("username", "");
                if (id > 0 && !username.isEmpty()) {
                    result.add(new UserEntry(id, username));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchAllUsers failed", e);
        }
        return result;
    }

    /** 获取在线用户 ID 集合 → GET /api/online/users */
    private Set<Long> fetchOnlineUserIds() {
        Set<Long> ids = new HashSet<>();
        try {
            String raw = ApiClient.getOnlineUsers();
            if (raw == null) return ids;

            JSONObject root = new JSONObject(raw);
            if (root.optInt("code", -1) != 200) return ids;

            JSONArray arr = root.optJSONArray("data");
            if (arr == null) return ids;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                long userId = obj.optLong("userId", -1L);
                if (userId > 0) ids.add(userId);
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchOnlineUserIds failed", e);
        }
        return ids;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 移除未读数变化监听器
        if (unreadListener != null) {
            UnreadManager.getInstance().removeListener(unreadListener);
            unreadListener = null;
        }
        if (refreshScheduler != null) {
            refreshScheduler.shutdownNow();
        }
    }

    /** 内部用户条目 */
    private static class UserEntry {
        final long id;
        final String username;
        UserEntry(long id, String username) {
            this.id = id;
            this.username = username;
        }
    }
}
