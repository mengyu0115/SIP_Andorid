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
import com.example.myapplication.adapter.ContactAdapter;
import com.example.myapplication.adapter.ContactItem;
import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.ServerConfig;

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
 * 联系人 Fragment（Tab 1）
 *
 * 完全对齐 PC 端 MainController.loadFriendList() + refreshUserList()：
 * 1. 同时拉取 GET /api/user/list（全部用户）和 GET /api/online/users（在线用户 ID 集合）
 * 2. 过滤自己，合并在线状态
 * 3. 在线用户排前面，离线用户排后面
 * 4. 在线绿点 #4CAF50，离线红点 #F44336（对应 PC 端 UserListCell）
 * 5. 每 3 秒自动刷新（对应 PC 端 startOnlineStatusRefresh，间隔 3 秒）
 * 6. 点击 → ChatActivity（对应 PC 端 openChat(friendName)）
 */
public class ContactsFragment extends Fragment {

    private static final String TAG = "ContactsFragment";
    private static final long REFRESH_INTERVAL_SECONDS = 3;

    private ContactAdapter adapter;
    private RecyclerView rvContacts;

    // 定时刷新（对应 PC 端 onlineStatusTimer）
    private ScheduledExecutorService refreshScheduler;
    private ScheduledFuture<?> refreshTask;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_contacts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvContacts = view.findViewById(R.id.rvContacts);
        rvContacts.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvContacts.addItemDecoration(
                new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));

        adapter = new ContactAdapter();
        adapter.setOnItemClickListener(item -> {
            // 点击跳转聊天页（对应 PC 端 openChat(friendName)）
            Intent intent = new Intent(requireContext(), ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_USER_ID, item.getUserId());
            intent.putExtra(ChatActivity.EXTRA_USER_NAME, item.getUsername());
            startActivity(intent);
        });
        rvContacts.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 立即刷新一次，然后启动定时器（对应 PC 端 loadFriendList → refreshUserList + startOnlineStatusRefresh）
        refreshContactList();
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
                    this::refreshContactList,
                    REFRESH_INTERVAL_SECONDS,
                    REFRESH_INTERVAL_SECONDS,
                    TimeUnit.SECONDS
            );
        }
    }

    /**
     * 停止定时刷新（对应 PC 端 stopOnlineStatusRefresh）
     */
    private void stopAutoRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
    }

    /**
     * 刷新联系人列表
     *
     * 对应 PC 端 refreshUserList()：
     * 1. fetchAllUsers() → GET /api/user/list
     * 2. fetchOnlineUserIds() → GET /api/online/users
     * 3. 合并：在线排前面，离线排后面，过滤自己
     */
    private void refreshContactList() {
        if (refreshScheduler == null || refreshScheduler.isShutdown()) return;
        refreshScheduler.execute(() -> {
            try {
                // 1. 获取全部用户列表（对应 PC 端 fetchAllUsers）
                List<UserEntry> allUsers = fetchAllUsers();

                // 2. 获取在线用户集合（对应 PC 端 fetchOnlineUsers）
                Set<Long> onlineIds = fetchOnlineUserIds();

                Long myId = ServerConfig.getCurrentUserId();

                // 3. 合并构建列表，在线排前、离线排后（对应 PC 端隐含排序）
                List<ContactItem> onlineItems = new ArrayList<>();
                List<ContactItem> offlineItems = new ArrayList<>();

                for (UserEntry u : allUsers) {
                    if (myId != null && u.id == myId) continue; // 过滤自己
                    boolean isOnline = onlineIds.contains(u.id);
                    ContactItem item = new ContactItem(u.id, u.username, isOnline);
                    if (isOnline) {
                        onlineItems.add(item);
                    } else {
                        offlineItems.add(item);
                    }
                }

                List<ContactItem> merged = new ArrayList<>(onlineItems.size() + offlineItems.size());
                merged.addAll(onlineItems);
                merged.addAll(offlineItems);

                // 4. 更新 UI（对应 PC 端 Platform.runLater）
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> adapter.submitList(merged));
                }

            } catch (Exception e) {
                Log.e(TAG, "刷新联系人列表失败", e);
            }
        });
    }

    // ===== API 调用（与 ConversationFragment 相同逻辑） =====

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
