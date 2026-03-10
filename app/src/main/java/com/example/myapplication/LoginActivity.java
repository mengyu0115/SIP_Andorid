package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.databinding.ActivityLoginBinding;
import com.example.myapplication.model.ApiResult;
import com.example.myapplication.model.LoginData;
import com.example.myapplication.model.UserInfo;
import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.ServerConfig;
import com.example.myapplication.service.HeartbeatService;
import com.example.myapplication.sip.SipManager;

import java.lang.ref.WeakReference;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 登录 Activity
 *
 * 登录流程与 PC 端 LoginController.handleLogin() 完全一致：
 * 1. POST /api/user/login → 获取 token + 用户信息（sipUri、sipPassword）
 * 2. 保存 token、userId、sipUri、sipPassword 到 ServerConfig
 * 3. 初始化 SIP 协议栈（只初始化一次，synchronized 块）
 * 4. 发起 SIP REGISTER（含 10 秒超时保护）
 * 5. SIP 注册成功回调 → POST /api/online/login + 跳转 MainActivity
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    // SIP 服务器地址：与 HTTP 后端同一台服务器（用户在登录界面输入）

    /** SIP 注册超时（毫秒） */
    private static final long SIP_REGISTER_TIMEOUT_MS = 10_000;

    // 防止重复初始化 SIP（volatile 修复线程可见性）
    private static volatile boolean sipInitialized = false;
    private static final Object initLock = new Object();

    // 防止重复打开主窗口
    private volatile boolean mainWindowOpened = false;

    private ActivityLoginBinding binding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** SIP 注册超时 Runnable */
    private final Runnable sipTimeoutRunnable = () -> {
        if (!mainWindowOpened) {
            showError("SIP 注册超时，请检查网络或服务器地址");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupClickListeners();
    }

    private void setupClickListeners() {
        binding.btnLogin.setOnClickListener(v -> {
            String username = binding.etUsername.getText().toString().trim();
            String password = binding.etPassword.getText().toString().trim();
            String serverIp = binding.etServerIp.getText().toString().trim();

            if (username.isEmpty()) {
                Toast.makeText(this, "请输入用户名", Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.isEmpty()) {
                Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show();
                return;
            }
            if (serverIp.isEmpty()) {
                Toast.makeText(this, "请输入服务器 IP", Toast.LENGTH_SHORT).show();
                return;
            }

            binding.btnLogin.setEnabled(false);
            binding.btnLogin.setText("连接中...");
            doLogin(serverIp, username, password);
        });

        binding.btnSkipLogin.setOnClickListener(v -> goToMain());
    }

    private void doLogin(String serverIp, String username, String password) {
        executor.execute(() -> {
            try {
                // Step 1: HTTP 登录
                ServerConfig.setServerIp(serverIp);
                showStatus("正在登录...");
                ApiResult<LoginData> result = ApiClient.login(serverIp, username, password);

                if (result == null) { showError("服务器无响应"); return; }
                if (!result.isSuccess()) { showError("登录失败：" + result.getMessage()); return; }

                LoginData data = result.getData();
                if (data == null || data.getToken() == null) { showError("响应数据格式错误"); return; }

                UserInfo user = data.getUser();
                if (user == null) { showError("登录失败：用户数据缺失"); return; }

                String sipUri = user.getSipUri();
                String sipPassword = user.getSipPassword();
                if (sipUri == null || sipUri.isEmpty()) { showError("登录失败：SIP URI 信息缺失"); return; }
                if (sipPassword == null || sipPassword.isEmpty()) { showError("登录失败：SIP 密码信息缺失"); return; }

                final String sipUsername = sipUri.split("@")[0].replace("sip:", "");
                final String token = data.getToken();
                final Long userId = user.getId();

                Log.i(TAG, "登录成功：username=" + username + ", userId=" + userId + ", sipUri=" + sipUri);

                // Step 2: 保存全局配置
                ServerConfig.setAuthToken(token);
                ServerConfig.setCurrentUserId(userId);
                ServerConfig.setCurrentUsername(username);
                ServerConfig.setSipUri(sipUri);
                ServerConfig.setSipPassword(sipPassword);

                // Step 3: 初始化 SIP（只一次）
                SipManager sipManager = SipManager.getInstance();
                synchronized (initLock) {
                    if (!sipInitialized) {
                        String localIp = getPreferredLocalIp(serverIp);
                        int localPort = 5061 + (int) (Math.random() * 1000);
                        Log.i(TAG, "初始化 SIP 协议栈: " + localIp + ":" + localPort);
                        sipManager.initialize(localIp, localPort);
                        sipInitialized = true;
                    }
                }

                // Step 4: 设置回调 + SIP REGISTER
                // 使用 WeakReference 避免 Activity 泄漏
                WeakReference<LoginActivity> weakThis = new WeakReference<>(this);

                sipManager.setCallback(new SipManager.RegisterCallback() {
                    @Override
                    public void onRegisterSuccess() {
                        Log.i(TAG, "SIP 注册成功");
                        LoginActivity activity = weakThis.get();
                        if (activity == null || activity.isFinishing()) return;

                        if (!mainWindowOpened) {
                            mainWindowOpened = true;
                            // 取消超时
                            mainHandler.removeCallbacks(sipTimeoutRunnable);
                            // Step 5: 在线状态 + 心跳 + 跳转
                            ApiClient.registerOnlineStatus(userId, token);
                            HeartbeatService.getInstance().start();
                            activity.runOnUiThread(activity::goToMain);
                        }
                    }

                    @Override
                    public void onRegisterFailed(String reason) {
                        Log.e(TAG, "SIP 注册失败: " + reason);
                        LoginActivity activity = weakThis.get();
                        if (activity == null || activity.isFinishing()) return;
                        mainHandler.removeCallbacks(sipTimeoutRunnable);
                        activity.runOnUiThread(() -> activity.showError("SIP 注册失败: " + reason));
                    }

                    @Override
                    public void onUnregisterSuccess() {
                        Log.i(TAG, "SIP 注销成功");
                    }
                });

                showStatus("正在注册到SIP服务器...");
                // 启动超时保护
                mainHandler.postDelayed(sipTimeoutRunnable, SIP_REGISTER_TIMEOUT_MS);
                sipManager.register(sipUsername, sipPassword, serverIp);

            } catch (Exception e) {
                Log.e(TAG, "doLogin exception", e);
                showError("连接失败：" + e.getMessage());
            }
        });
    }

    private String getPreferredLocalIp(String sipServer) {
        try {
            int firstDot = sipServer.indexOf('.');
            int secondDot = sipServer.indexOf('.', firstDot + 1);
            String serverPrefix = (firstDot > 0 && secondDot > firstDot)
                    ? sipServer.substring(0, secondDot) : sipServer;

            String fallbackIp = null;
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;

                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith(serverPrefix)) {
                            Log.i(TAG, "找到与 SIP 服务器同网段的本地 IP: " + ip);
                            return ip;
                        }
                        if (fallbackIp == null) fallbackIp = ip;
                    }
                }
            }
            if (fallbackIp != null) return fallbackIp;
        } catch (Exception e) {
            Log.e(TAG, "获取本地 IP 失败: " + e.getMessage());
        }
        try { return InetAddress.getLocalHost().getHostAddress(); }
        catch (Exception e) { return "127.0.0.1"; }
    }

    private void showStatus(String msg) {
        runOnUiThread(() -> binding.btnLogin.setText(msg));
    }

    private void showError(String msg) {
        runOnUiThread(() -> {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            binding.btnLogin.setEnabled(true);
            binding.btnLogin.setText("登录");
        });
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacks(sipTimeoutRunnable);
        executor.shutdown();
    }
}
