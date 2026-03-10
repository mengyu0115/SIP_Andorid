package com.example.myapplication.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.myapplication.LoginActivity;
import com.example.myapplication.R;
import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.ServerConfig;
import com.example.myapplication.service.HeartbeatService;
import com.example.myapplication.sip.SipManager;

import java.util.concurrent.Executors;

/**
 * 我的 Fragment
 *
 * 对应 PC 端 MainController.handleLogout()：
 * - 显示当前用户信息（用户名、ID、SIP 注册状态）
 * - 退出登录：SIP 注销 + HTTP 登出 + 停止心跳 + 清空配置 + 返回登录页
 */
public class ProfileFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvUsername = view.findViewById(R.id.tvUsername);
        TextView tvUserId = view.findViewById(R.id.tvUserId);
        TextView tvSipStatus = view.findViewById(R.id.tvSipStatus);
        Button btnLogout = view.findViewById(R.id.btnLogout);

        // 显示用户信息
        String username = ServerConfig.getCurrentUsername();
        Long userId = ServerConfig.getCurrentUserId();
        boolean sipRegistered = SipManager.getInstance().isRegistered();

        tvUsername.setText(username != null ? username : "未登录");
        tvUserId.setText(userId != null ? ("ID: " + userId) : "ID: --");
        tvSipStatus.setText(sipRegistered ? "SIP: 已注册" : "SIP: 未注册");

        // 退出登录
        btnLogout.setOnClickListener(v -> {
            btnLogout.setEnabled(false);
            btnLogout.setText("退出中...");
            doLogout();
        });
    }

    /**
     * 登出流程（对应 PC 端 MainController.handleLogout）：
     * 1. SIP REGISTER Expires=0（注销）
     * 2. POST /api/online/logout/{userId}
     * 3. 停止心跳
     * 4. 清空 ServerConfig
     * 5. 返回 LoginActivity
     */
    private void doLogout() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // 1. SIP 注销
                SipManager.getInstance().unregister();

                // 2. HTTP 登出
                Long userId = ServerConfig.getCurrentUserId();
                if (userId != null) {
                    ApiClient.logoutOnlineStatus(userId);
                }

                // 3. 停止心跳
                HeartbeatService.getInstance().stop();

                // 4. 清空配置
                ServerConfig.clear();

            } catch (Exception ignored) {
            } finally {
                // 5. 返回登录页
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        Intent intent = new Intent(requireContext(), LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    });
                }
            }
        });
    }
}
