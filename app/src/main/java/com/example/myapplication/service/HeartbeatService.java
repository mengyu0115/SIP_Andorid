package com.example.myapplication.service;

import android.util.Log;

import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.ServerConfig;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 心跳保活服务
 *
 * 对应 PC 端 MainController.onlineStatusTimer：
 * 每 30 秒调用 POST /api/online/heartbeat/{userId} 维持在线状态
 */
public class HeartbeatService {

    private static final String TAG = "HeartbeatService";
    private static final long INTERVAL_SECONDS = 30;

    private static HeartbeatService instance;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> task;

    public static HeartbeatService getInstance() {
        if (instance == null) {
            synchronized (HeartbeatService.class) {
                if (instance == null) instance = new HeartbeatService();
            }
        }
        return instance;
    }

    private HeartbeatService() {}

    /** 开始心跳（登录成功后调用） */
    public void start() {
        if (task != null && !task.isCancelled()) return;

        Log.i(TAG, "启动心跳保活，间隔 " + INTERVAL_SECONDS + "s");
        task = scheduler.scheduleAtFixedRate(() -> {
            Long userId = ServerConfig.getCurrentUserId();
            if (userId == null) return;
            try {
                ApiClient.heartbeat(userId);
                Log.d(TAG, "心跳发送成功: userId=" + userId);
            } catch (Exception e) {
                Log.w(TAG, "心跳发送失败", e);
            }
        }, INTERVAL_SECONDS, INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /** 停止心跳（登出时调用） */
    public void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
            Log.i(TAG, "心跳保活已停止");
        }
    }
}
