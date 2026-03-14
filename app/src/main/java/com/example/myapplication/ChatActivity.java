package com.example.myapplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.myapplication.adapter.EmojiAdapter;
import com.example.myapplication.adapter.MessageAdapter;
import com.example.myapplication.databinding.ActivityChatBinding;
import com.example.myapplication.model.ChatMessage;
import com.example.myapplication.network.ApiClient;
import com.example.myapplication.network.OnlineStatusCache;
import com.example.myapplication.network.ServerConfig;
import com.example.myapplication.notification.NotificationHelper;
import com.example.myapplication.notification.UnreadManager;
import com.example.myapplication.sip.SipManager;
import com.example.myapplication.utils.FileDownloadManager;
import com.example.myapplication.utils.VoicePlayManager;
import com.example.myapplication.utils.VoiceRecordManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 聊天页 Activity
 *
 * 复用 app_copy ChatActivity UI 结构，业务逻辑与 PC 端 MainController 完全对齐：
 *
 * 发送消息流程（对应 PC 端 handleSendMessage）：
 *   1. 乐观更新 UI（立即显示发送气泡）
 *   2. SIP MESSAGE 发送（对应 sipMessageManager.sendTextMessage）—— 协议层占位，待接入
 *   3. HTTP 持久化 POST /api/message/send
 *
 * 发送图片流程（对应 PC 端 handleSendImage）：
 *   POST /api/message/send/image （multipart/form-data, file + fromUserId + toUserId）
 *
 * 发送文件流程（对应 PC 端 handleSendFile）：
 *   POST /api/message/send/file （multipart/form-data, file + fromUserId + toUserId）
 *
 * 接收消息（对应 PC 端 onMessageReceived 回调）：
 *   由 SipMessageReceiver 全局单例推送到 UI
 *
 * 历史消息加载（对应 PC 端 loadChatHistory）：
 *   GET /api/message/history?userId1=x&userId2=y&limit=50
 *   msgType: 1=文本 2=图片 3=语音 4=视频 5=文件
 *
 * 消息内容格式（SIP MESSAGE 内容，与 PC 端 MainController 完全统一）：
 *   - 文本：纯文本
 *   - 图片：[图片]<url>
 *   - 文件：[文件]<filename>|<url>
 *   - 语音：[语音]<url>
 *   - 视频：[视频]<url>
 */
public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";

    public static final String EXTRA_USER_ID = "userId";
    public static final String EXTRA_USER_NAME = "userName";

    // msgType 常量（对应服务端 Message.msgType）
    private static final int MSG_TYPE_TEXT = 1;
    private static final int MSG_TYPE_IMAGE = 2;
    private static final int MSG_TYPE_VOICE = 3;
    private static final int MSG_TYPE_VIDEO = 4;
    private static final int MSG_TYPE_FILE = 5;

    private static final List<String> EMOJI_LIST = Arrays.asList(
        "😀","😃","😄","😁","😆","😅","🤣","😂",
        "🙂","🙃","😉","😊","😇","🥰","😍","🤩",
        "😘","😗","😚","😙","😋","😛","😜","🤪",
        "😝","🤑","🤗","🤭","🤫","🤔","🤐","🤨",
        "😐","😑","😶","😏","😒","🙄","😬","🤥",
        "😌","😔","😪","🤤","😴","😷","🤒","🤕",
        "🤢","🤮","🤧","🥵","🥶","😎","🤓","🧐",
        "😕","😟","🙁","☹️","😮","😯","😲","😳",
        "🥺","😦","😧","😨","😰","😥","😢","😭",
        "😱","😖","😣","😞","😓","😩","😫","🥱",
        "👍","👎","👌","✌️","🤞","🤟","🤘","🤙",
        "👏","🙌","👐","🤲","🙏","💪","❤️","🧡",
        "💛","💚","💙","💜","🖤","🤍","🤎","💔"
    );

    private ActivityChatBinding binding;
    private MessageAdapter messageAdapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private Long targetUserId;
    private String targetUserName;

    /** 用于标识当前聊天对象的 SIP ID（与 UnreadManager 的 key 一致） */
    private String targetSipId;

    /** 消息接收回调（用于 addListener/removeListener） */
    private SipMessageReceiver.MessageCallback messageCallback;

    // 图片选择器
    private final ActivityResultLauncher<String> imagePickerLauncher =
        registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) handleSendImage(uri);
        });

    // 视频选择器
    private final ActivityResultLauncher<String> videoPickerLauncher =
        registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) handleSendVideo(uri);
        });

    // 文件选择器
    private final ActivityResultLauncher<String> filePickerLauncher =
        registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) handleSendFile(uri);
        });

    // 录音权限请求
    private final ActivityResultLauncher<String> recordPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                // 权限已授予
            } else {
                showToast("需要录音权限才能发送语音");
            }
        });

    // 存储权限请求（用于文件下载）
    private final ActivityResultLauncher<String> storagePermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                showToast("存储权限已授予");
            } else {
                // Android 11+ 可以在没有存储权限的情况下访问公共目录
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    showToast("可以正常下载文件到公共目录");
                } else {
                    showToast("需要存储权限才能下载文件");
                }
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 初始化文件下载管理器
        FileDownloadManager.getInstance().init(this);

        targetUserId = getIntent().getLongExtra(EXTRA_USER_ID, -1L);
        targetUserName = getIntent().getStringExtra(EXTRA_USER_NAME);
        if (targetUserName == null) targetUserName = "未知用户";

        if (targetUserId == -1L) {
            Log.e(TAG, "targetUserId 缺失");
            finish();
            return;
        }

        setupToolbar();
        setupRecyclerView();
        setupEmojiPanel();
        setupInputArea();
        registerMessageReceiver();

        // 计算 SIP ID（与 SipMessageReceiver 提取的 fromUsername 一致）
        targetSipId = String.valueOf(targetUserId);

        loadChatHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 跟踪当前聊天 + 清除未读 + 取消通知
        UnreadManager.getInstance().setCurrentChatUsername(targetSipId);
        UnreadManager.getInstance().clearUnread(targetSipId);
        new NotificationHelper(this).cancelNotification(targetSipId);
    }

    @Override
    protected void onPause() {
        super.onPause();
        UnreadManager.getInstance().setCurrentChatUsername(null);
    }

    // ===== 标题栏 =====

    private void setupToolbar() {
        binding.tvUserName.setText(targetUserName);
        binding.ivBack.setOnClickListener(v -> finish());

        // 语音通话按钮
        binding.btnVoiceCall.setOnClickListener(v -> {
            CallActivity.startOutgoing(this, targetUserName,
                    com.example.myapplication.sip.SipCallHandler.CALL_TYPE_AUDIO);
        });

        // 视频通话按钮
        binding.btnVideoCall.setOnClickListener(v -> {
            CallActivity.startOutgoing(this, targetUserName,
                    com.example.myapplication.sip.SipCallHandler.CALL_TYPE_VIDEO);
        });
    }

    // ===== 消息列表 =====

    private void setupRecyclerView() {
        messageAdapter = new MessageAdapter(this);
        binding.rvMessages.setLayoutManager(new LinearLayoutManager(this));
        binding.rvMessages.setAdapter(messageAdapter);
    }

    // ===== 表情面板（同 app_copy，8列网格） =====

    private void setupEmojiPanel() {
        EmojiAdapter emojiAdapter = new EmojiAdapter(EMOJI_LIST, emoji -> {
            String cur = binding.etMessage.getText().toString();
            binding.etMessage.setText(cur + emoji);
            binding.etMessage.setSelection(binding.etMessage.getText().length());
        });
        binding.rvEmojis.setLayoutManager(new GridLayoutManager(this, 8));
        binding.rvEmojis.setAdapter(emojiAdapter);

        binding.btnEmoji.setOnClickListener(v -> {
            if (binding.emojiPanel.getVisibility() == View.GONE) {
                binding.emojiPanel.setVisibility(View.VISIBLE);
            } else {
                binding.emojiPanel.setVisibility(View.GONE);
            }
        });
    }

    // ===== 输入区域 =====

    private boolean isVoiceMode = false;  // 是否为语音输入模式

    private void setupInputArea() {
        // 语音/键盘切换
        binding.btnVoiceToggle.setOnClickListener(v -> {
            isVoiceMode = !isVoiceMode;
            if (isVoiceMode) {
                // 切换到语音模式
                binding.etMessage.setVisibility(View.GONE);
                binding.btnVoiceRecord.setVisibility(View.VISIBLE);
                binding.btnVoiceToggle.setImageResource(android.R.drawable.ic_menu_edit);
            } else {
                // 切换到键盘模式
                binding.etMessage.setVisibility(View.VISIBLE);
                binding.btnVoiceRecord.setVisibility(View.GONE);
                binding.btnVoiceToggle.setImageResource(android.R.drawable.ic_btn_speak_now);
            }
        });

        // 按住说话按钮
        binding.btnVoiceRecord.setOnTouchListener(new View.OnTouchListener() {
            private float startY;
            private static final float CANCEL_THRESHOLD = 200; // 上滑200像素取消
            private boolean isRecording = false;  // 添加录音状态标志

            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                // 检查录音权限
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    if (ContextCompat.checkSelfPermission(ChatActivity.this, Manifest.permission.RECORD_AUDIO)
                            != PackageManager.PERMISSION_GRANTED) {
                        recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
                        return false;
                    }
                }

                float deltaY;
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        startY = event.getY();
                        isRecording = true;  // 标记开始录音
                        binding.btnVoiceRecord.setText("松开发送");
                        VoiceRecordManager.getInstance().startRecord(
                                ChatActivity.this,
                                new VoiceRecordManager.RecordCallback() {
                                    @Override
                                    public void onStart() {
                                        // 开始录音
                                    }

                                    @Override
                                    public void onProgress(int duration) {
                                        // 更新录音时长显示
                                        if (isRecording) {  // 只有在录音状态才更新
                                            binding.btnVoiceRecord.setText("正在录音 " + duration + "s");
                                        }
                                    }

                                    @Override
                                    public void onStop(int duration, String filePath) {
                                        isRecording = false;  // 标记录音结束
                                        binding.btnVoiceRecord.setText("按住说话");
                                        handleSendVoice(filePath, duration);
                                    }

                                    @Override
                                    public void onError(String error) {
                                        isRecording = false;  // 标记录音结束
                                        binding.btnVoiceRecord.setText("按住说话");
                                        showToast("录音失败: " + error);
                                    }

                                    @Override
                                    public void onCancel() {
                                        isRecording = false;  // 标记录音取消
                                        binding.btnVoiceRecord.setText("按住说话");
                                        showToast("录音时间太短");
                                    }
                                }
                        );
                        return true;

                    case android.view.MotionEvent.ACTION_MOVE:
                        // 检测上滑取消（只有在录音状态才更新文字）
                        if (isRecording) {
                            deltaY = startY - event.getY();
                            if (deltaY > CANCEL_THRESHOLD) {
                                binding.btnVoiceRecord.setText("松开取消");
                            } else {
                                binding.btnVoiceRecord.setText("松开发送");
                            }
                        }
                        return true;

                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        isRecording = false;  // 标记触摸结束
                        deltaY = startY - event.getY();
                        if (deltaY > CANCEL_THRESHOLD) {
                            // 上滑取消
                            VoiceRecordManager.getInstance().cancelRecord();
                        } else {
                            // 停止录音
                            VoiceRecordManager.getInstance().stopRecord();
                        }
                        // 恢复按钮文字（防止回调未及时执行）
                        binding.btnVoiceRecord.postDelayed(() -> {
                            if (!VoiceRecordManager.getInstance().isRecording()) {
                                binding.btnVoiceRecord.setText("按住说话");
                            }
                        }, 100);
                        return true;
                }
                return false;
            }
        });

        // 发送文本（对应 PC 端 handleSendMessage）
        binding.btnSend.setOnClickListener(v -> {
            String content = binding.etMessage.getText().toString().trim();
            if (content.isEmpty()) return;
            binding.etMessage.getText().clear();
            binding.emojiPanel.setVisibility(View.GONE);
            doSendTextMessage(content);
        });

        // 发送图片（对应 PC 端 handleSendImage）
        binding.btnImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        // 发送视频（对应 PC 端 handleSendVideo）
        binding.btnVideo.setOnClickListener(v -> videoPickerLauncher.launch("video/*"));
    }

    // ===== 注册消息接收回调（对应 PC 端 onMessageReceived） =====

    private void registerMessageReceiver() {
        messageCallback = (fromUsername, content) -> {
            // 只处理当前聊天对象的消息（对应 PC 端 if fromUsername.equals(currentChatFriend)）
            // SIP 用户名可能是 sipUri 中的用户部分，也可能就是 username，都做匹配
            String sipName = ServerConfig.getSipUsername();
            boolean isFromTarget = fromUsername.equals(targetUserName)
                    || fromUsername.equals(String.valueOf(targetUserId))
                    || (sipName != null && targetUserName.contains(fromUsername));

            if (!isFromTarget) {
                Log.d(TAG, "收到来自 " + fromUsername + " 的消息，非当前聊天对象，忽略");
                return;
            }

            // 解析消息内容格式（同 PC 端 IncomingMessage 的判断逻辑）
            ChatMessage msg = buildReceivedMessage(content);
            runOnUiThread(() -> {
                messageAdapter.appendMessage(msg);
                scrollToBottom();
            });
        };
        SipMessageReceiver.getInstance().addListener(messageCallback);
    }

    /**
     * 根据 SIP MESSAGE 内容构造接收消息
     * 格式兼容 PC 端（中文前缀）和旧 Android 端（英文前缀）：
     *   [图片]<url> 或 [IMAGE]<url>  → 图片消息
     *   [文件]<name>|<url> 或 [FILE]<name>|<url> → 文件消息
     *   [语音]<url> → 语音消息
     *   [视频]<url> → 视频消息
     *   其他 → 文本消息
     */
    private ChatMessage buildReceivedMessage(String content) {
        ChatMessage msg;

        if (content.startsWith("[图片]")) {
            // 图片消息：提取 URL 并创建图片消息对象
            String fileUrl = content.substring(4);
            msg = ChatMessage.image(
                    targetUserId,
                    ServerConfig.getCurrentUserId(),
                    targetUserName,
                    fileUrl,
                    0L,
                    ChatMessage.TYPE_RECEIVED
            );
        } else if (content.startsWith("[IMAGE]")) {
            // 兼容英文前缀
            String fileUrl = content.substring(7);
            msg = ChatMessage.image(
                    targetUserId,
                    ServerConfig.getCurrentUserId(),
                    targetUserName,
                    fileUrl,
                    0L,
                    ChatMessage.TYPE_RECEIVED
            );
        } else if (content.startsWith("[文件]")) {
            // 文件消息：[文件]<name>|<url>
            String fileData = content.substring(4);
            String[] parts = fileData.split("\\|", 2);
            String fileName = parts.length > 0 ? parts[0] : "未知文件";
            String fileUrl = parts.length > 1 ? parts[1] : "";
            msg = ChatMessage.file(
                    targetUserId,
                    ServerConfig.getCurrentUserId(),
                    targetUserName,
                    fileName,
                    fileUrl,
                    0L,
                    ChatMessage.TYPE_RECEIVED
            );
        } else if (content.startsWith("[FILE]")) {
            // 兼容英文前缀
            String fileData = content.substring(6);
            String[] parts = fileData.split("\\|", 2);
            String fileName = parts.length > 0 ? parts[0] : "未知文件";
            String fileUrl = parts.length > 1 ? parts[1] : "";
            msg = ChatMessage.file(
                    targetUserId,
                    ServerConfig.getCurrentUserId(),
                    targetUserName,
                    fileName,
                    fileUrl,
                    0L,
                    ChatMessage.TYPE_RECEIVED
            );
        } else if (content.startsWith("[语音]")) {
            // 语音消息
            String fileUrl = content.substring(4);
            msg = ChatMessage.text(
                    targetUserId,
                    ServerConfig.getCurrentUserId(),
                    targetUserName,
                    "🎤 [语音]",
                    ChatMessage.TYPE_RECEIVED
            );
            msg.setMsgType(ChatMessage.MSG_TYPE_VOICE);
            msg.setFileUrl(fileUrl);
        } else if (content.startsWith("[视频]")) {
            // 视频消息
            String fileUrl = content.substring(4);
            msg = ChatMessage.text(
                    targetUserId,
                    ServerConfig.getCurrentUserId(),
                    targetUserName,
                    "🎬 [视频]",
                    ChatMessage.TYPE_RECEIVED
            );
            msg.setMsgType(ChatMessage.MSG_TYPE_VIDEO);
            msg.setFileUrl(fileUrl);
        } else {
            // 文本消息
            msg = ChatMessage.text(
                    targetUserId,
                    ServerConfig.getCurrentUserId(),
                    targetUserName,
                    content,
                    ChatMessage.TYPE_RECEIVED
            );
        }

        return msg;
    }

    // ===== 加载历史消息（对应 PC 端 loadChatHistory） =====

    private void loadChatHistory() {
        Long myId = ServerConfig.getCurrentUserId();
        if (myId == null) return;

        executor.execute(() -> {
            try {
                String raw = ApiClient.getChatHistory(myId, targetUserId, 50);
                if (raw == null) return;

                JSONObject root = new JSONObject(raw);
                if (root.optInt("code", -1) != 200) return;

                // data 可能是数组，也可能是包含 list 的对象
                List<ChatMessage> messages = new ArrayList<>();
                JSONArray arr = null;
                if (root.opt("data") instanceof JSONArray) {
                    arr = root.getJSONArray("data");
                } else if (root.opt("data") instanceof JSONObject) {
                    JSONObject dataObj = root.getJSONObject("data");
                    arr = dataObj.optJSONArray("list");
                }

                if (arr == null) return;

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    long fromId = obj.optLong("fromUserId", -1L);
                    int msgType = obj.optInt("msgType", MSG_TYPE_TEXT);
                    String content = obj.optString("content", "");
                    String fileUrl = obj.optString("fileUrl", null);
                    Long fileSize = obj.optLong("fileSize", 0L);
                    Integer duration = obj.optInt("duration", 0);

                    // 直接使用 timestamp 字段（long类型毫秒数），不做任何时区转换
                    // 这样数据库存什么时间，客户端就显示什么时间
                    long ts = obj.optLong("timestamp", System.currentTimeMillis());

                    boolean isSent = (fromId == myId);
                    int viewType = isSent ? ChatMessage.TYPE_SENT : ChatMessage.TYPE_RECEIVED;

                    // 根据类型创建消息对象
                    ChatMessage msg;
                    String fromUsername = isSent ? ServerConfig.getCurrentUsername() : targetUserName;

                    switch (msgType) {
                        case MSG_TYPE_IMAGE:
                            // 图片消息
                            msg = ChatMessage.image(fromId, targetUserId, fromUsername,
                                    fileUrl, fileSize, viewType);
                            msg.setTimestamp(ts);
                            msg.setContent(content); // 可选的描述
                            break;

                        case MSG_TYPE_FILE:
                            // 文件消息
                            msg = ChatMessage.file(fromId, targetUserId, fromUsername,
                                    content, fileUrl, fileSize, viewType);
                            msg.setTimestamp(ts);
                            break;

                        case MSG_TYPE_VOICE:
                        case MSG_TYPE_VIDEO:
                            // 语音/视频消息（暂用文本显示）
                            msg = ChatMessage.text(fromId, targetUserId, fromUsername,
                                    buildDisplayContent(msgType, content, fileUrl), viewType);
                            msg.setTimestamp(ts);
                            msg.setMsgType(msgType == MSG_TYPE_VOICE ? ChatMessage.MSG_TYPE_VOICE : ChatMessage.MSG_TYPE_VIDEO);
                            msg.setFileUrl(fileUrl);
                            msg.setFileSize(fileSize);
                            msg.setDuration(duration);
                            break;

                        default:
                            // 文本消息
                            msg = ChatMessage.text(fromId, targetUserId, fromUsername,
                                    content, viewType);
                            msg.setTimestamp(ts);
                            break;
                    }

                    messages.add(msg);
                }

                // 按时间戳升序排序（确保消息按时间顺序显示）
                messages.sort((m1, m2) -> Long.compare(m1.getTimestamp(), m2.getTimestamp()));

                runOnUiThread(() -> {
                    messageAdapter.submitList(messages);
                    scrollToBottom();
                });

            } catch (Exception e) {
                Log.e(TAG, "加载历史消息失败", e);
            }
        });
    }

    /**
     * 根据 msgType 构造显示内容
     * 对应 PC 端 loadChatHistory 中按 msgType 调用不同气泡方法的逻辑
     */
    private String buildDisplayContent(int msgType, String content, String fileUrl) {
        switch (msgType) {
            case MSG_TYPE_IMAGE:
                return "🖼 [图片] " + (content != null ? content : "");
            case MSG_TYPE_VOICE:
                return "🎤 [语音] " + (content != null ? content : "");
            case MSG_TYPE_VIDEO:
                return "🎬 [视频] " + (content != null ? content : "");
            case MSG_TYPE_FILE:
                return "📄 [文件] " + (content != null ? content : "");
            default:
                return content != null ? content : "";
        }
    }

    // ===== 发送文本（对应 PC 端 handleSendMessage） =====

    private void doSendTextMessage(String content) {
        Long myId = ServerConfig.getCurrentUserId();
        if (myId == null) { Toast.makeText(this, "未登录", Toast.LENGTH_SHORT).show(); return; }

        // 1. 乐观更新 UI（对应 PC 端 addMessageBubble(message, true)）
        ChatMessage msg = ChatMessage.text(myId, targetUserId,
                ServerConfig.getCurrentUsername(), content, ChatMessage.TYPE_SENT);
        messageAdapter.appendMessage(msg);
        scrollToBottom();

        executor.execute(() -> {
            // 2. SIP MESSAGE 发送（对应 PC 端 sipMessageManager.sendTextMessage）
            // SIP 号码提取：对应 PC 端 extractSipUsernameFromUsername
            String sipTarget = extractSipTarget(targetUserName);
            Log.i(TAG, "SIP MESSAGE -> " + sipTarget + ": " + content);
            SipManager.getInstance().sendMessage(sipTarget, content);

            // 3. HTTP 持久化（对应 PC 端的服务端 /api/message/send）
            persistTextMessage(myId, targetUserId, content);
        });
    }

    // ===== 发送图片（对应 PC 端 handleSendImage → sendImageMessage） =====

    private void handleSendImage(Uri uri) {
        Long myId = ServerConfig.getCurrentUserId();
        if (myId == null) return;

        String fileName = getFileName(uri);

        // 乐观更新：创建图片消息对象（而不是文本消息）
        ChatMessage preview = ChatMessage.image(myId, targetUserId,
                ServerConfig.getCurrentUsername(), null, 0L, ChatMessage.TYPE_SENT);
        preview.setContent(fileName); // 设置文件名作为描述
        messageAdapter.appendMessage(preview);
        scrollToBottom();

        executor.execute(() -> {
            try {
                byte[] fileBytes = readBytes(uri);
                if (fileBytes == null) { showToast("读取图片失败"); return; }

                // POST /api/message/send/image（对应 PC 端 sendImageMessage → HttpClientUtil.uploadFile）
                int offline = OnlineStatusCache.getInstance().isOnline(targetUserId) ? 0 : 1;
                String result = uploadMediaMessage("/api/message/send/image",
                        fileBytes, fileName, myId, targetUserId, offline);

                if (result != null) {
                    // SIP 发送图片 URL（对应 PC 端 sipMessageManager.sendImageMessage → [IMAGE]url）
                    JSONObject res = new JSONObject(result);
                    if (res.optInt("code", -1) == 200) {
                        JSONObject data = res.optJSONObject("data");
                        if (data != null) {
                            String fileUrl = data.optString("fileUrl", "");
                            if (!fileUrl.isEmpty()) {
                                // 更新消息对象的 fileUrl
                                preview.setFileUrl(fileUrl);
                                runOnUiThread(() -> messageAdapter.notifyDataSetChanged());

                                String sipContent = "[图片]" + fileUrl;
                                String sipTarget = extractSipTarget(targetUserName);
                                Log.i(TAG, "SIP IMAGE -> " + sipTarget);
                                SipManager.getInstance().sendMessage(sipTarget, sipContent);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "发送图片失败", e);
                showToast("发送图片失败: " + e.getMessage());
            }
        });
    }

    // ===== 发送文件（对应 PC 端 handleSendFile → sendFileMessage） =====

    private void handleSendFile(Uri uri) {
        Long myId = ServerConfig.getCurrentUserId();
        if (myId == null) return;

        String fileName = getFileName(uri);
        String previewContent = "📄 [文件] " + fileName;

        // 乐观更新（对应 PC 端 addMessageBubble("[文件] " + file.getName(), true)）
        ChatMessage preview = ChatMessage.text(myId, targetUserId,
                ServerConfig.getCurrentUsername(), previewContent, ChatMessage.TYPE_SENT);
        messageAdapter.appendMessage(preview);
        scrollToBottom();

        executor.execute(() -> {
            try {
                byte[] fileBytes = readBytes(uri);
                if (fileBytes == null) { showToast("读取文件失败"); return; }

                // POST /api/message/send/file（对应 PC 端 sendFileMessage → HttpClientUtil.uploadFile）
                int offline = OnlineStatusCache.getInstance().isOnline(targetUserId) ? 0 : 1;
                String result = uploadMediaMessage("/api/message/send/file",
                        fileBytes, fileName, myId, targetUserId, offline);

                if (result != null) {
                    JSONObject res = new JSONObject(result);
                    if (res.optInt("code", -1) == 200) {
                        JSONObject data = res.optJSONObject("data");
                        if (data != null) {
                            String fileUrl = data.optString("fileUrl", "");
                            if (!fileUrl.isEmpty()) {
                                // SIP 发送文件消息格式：[文件]filename|url（与PC端一致）
                                String sipContent = "[文件]" + fileName + "|" + fileUrl;
                                String sipTarget = extractSipTarget(targetUserName);
                                Log.i(TAG, "SIP FILE -> " + sipTarget);
                                SipManager.getInstance().sendMessage(sipTarget, sipContent);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "发送文件失败", e);
                showToast("发送文件失败: " + e.getMessage());
            }
        });
    }

    // ===== 发送语音（对应 PC 端 handleSendVoice → sendVoiceMessage） =====

    private void handleSendVoice(String filePath, int duration) {
        Long myId = ServerConfig.getCurrentUserId();
        if (myId == null) return;

        String fileName = new java.io.File(filePath).getName();
        String previewContent = "🎤 [语音] " + duration + "秒";

        // 乐观更新
        ChatMessage preview = ChatMessage.text(myId, targetUserId,
                ServerConfig.getCurrentUsername(), previewContent, ChatMessage.TYPE_SENT);
        preview.setMsgType(ChatMessage.MSG_TYPE_VOICE);
        preview.setDuration(duration);
        preview.setFileName(fileName);  // 设置文件名
        messageAdapter.appendMessage(preview);
        scrollToBottom();

        executor.execute(() -> {
            try {
                byte[] fileBytes = readBytesFromFile(filePath);
                if (fileBytes == null) { showToast("读取语音失败"); return; }

                int offline = OnlineStatusCache.getInstance().isOnline(targetUserId) ? 0 : 1;
                // 使用新的方法，传递 duration
                String result = uploadMediaMessage("/api/message/send/voice",
                        fileBytes, fileName, myId, targetUserId, offline, duration);

                if (result != null) {
                    JSONObject res = new JSONObject(result);
                    if (res.optInt("code", -1) == 200) {
                        JSONObject data = res.optJSONObject("data");
                        if (data != null) {
                            String fileUrl = data.optString("fileUrl", "");
                            if (!fileUrl.isEmpty()) {
                                preview.setFileUrl(fileUrl);
                                runOnUiThread(() -> messageAdapter.notifyDataSetChanged());

                                // SIP 消息包含时长信息
                                String sipContent = "[语音]" + duration + "|" + fileUrl;
                                String sipTarget = extractSipTarget(targetUserName);
                                Log.i(TAG, "SIP VOICE -> " + sipTarget);
                                SipManager.getInstance().sendMessage(sipTarget, sipContent);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "发送语音失败", e);
                showToast("发送语音失败: " + e.getMessage());
            }
        });
    }

    // ===== 发送视频（对应 PC 端 handleSendVideo → sendVideoMessage） =====

    private void handleSendVideo(Uri uri) {
        Long myId = ServerConfig.getCurrentUserId();
        if (myId == null) return;

        String fileName = getFileName(uri);
        String previewContent = "🎬 [视频] " + fileName;

        // 乐观更新
        ChatMessage preview = ChatMessage.text(myId, targetUserId,
                ServerConfig.getCurrentUsername(), previewContent, ChatMessage.TYPE_SENT);
        preview.setMsgType(ChatMessage.MSG_TYPE_VIDEO);
        messageAdapter.appendMessage(preview);
        scrollToBottom();

        executor.execute(() -> {
            try {
                byte[] fileBytes = readBytes(uri);
                if (fileBytes == null) { showToast("读取视频失败"); return; }

                int offline = OnlineStatusCache.getInstance().isOnline(targetUserId) ? 0 : 1;
                String result = uploadMediaMessage("/api/message/send/video",
                        fileBytes, fileName, myId, targetUserId, offline);

                if (result != null) {
                    JSONObject res = new JSONObject(result);
                    if (res.optInt("code", -1) == 200) {
                        JSONObject data = res.optJSONObject("data");
                        if (data != null) {
                            String fileUrl = data.optString("fileUrl", "");
                            if (!fileUrl.isEmpty()) {
                                preview.setFileUrl(fileUrl);
                                runOnUiThread(() -> messageAdapter.notifyDataSetChanged());

                                String sipContent = "[视频]" + fileUrl;
                                String sipTarget = extractSipTarget(targetUserName);
                                Log.i(TAG, "SIP VIDEO -> " + sipTarget);
                                SipManager.getInstance().sendMessage(sipTarget, sipContent);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "发送视频失败", e);
                showToast("发送视频失败: " + e.getMessage());
            }
        });
    }

    // ===== HTTP 工具 =====

    /**
     * 持久化文本消息到服务器
     * POST /api/message/send
     * Body: {fromUserId, toUserId, content, msgType:1, isOffline}
     * isOffline 根据对方在线状态动态判断
     */
    private void persistTextMessage(Long fromId, Long toId, String content) {
        try {
            int isOffline = OnlineStatusCache.getInstance().isOnline(toId) ? 0 : 1;
            JSONObject body = new JSONObject();
            body.put("fromUserId", fromId);
            body.put("toUserId", toId);
            body.put("content", content);
            body.put("msgType", MSG_TYPE_TEXT);
            body.put("isOffline", isOffline);
            String result = ApiClient.sendMessageToServer(body.toString());
            Log.d(TAG, "文本消息持久化 (isOffline=" + isOffline + "): " + result);
        } catch (Exception e) {
            Log.e(TAG, "文本消息持久化失败", e);
        }
    }

    /**
     * 上传媒体文件并发送消息
     * POST /api/message/send/image 或 /api/message/send/file
     * multipart/form-data: file + fromUserId + toUserId + isOffline
     * 对应 PC 端 HttpClientUtil.uploadFile
     */
    private String uploadMediaMessage(String path, byte[] fileBytes, String fileName,
                                      Long fromId, Long toId, int isOffline) throws Exception {
        return uploadMediaMessage(path, fileBytes, fileName, fromId, toId, isOffline, null);
    }

    /**
     * 上传媒体文件并发送消息（支持时长）
     * POST /api/message/send/voice 或 /api/message/send/video
     * multipart/form-data: file + fromUserId + toUserId + isOffline + duration
     */
    private String uploadMediaMessage(String path, byte[] fileBytes, String fileName,
                                      Long fromId, Long toId, int isOffline, Integer duration) throws Exception {
        String boundary = "----Boundary" + UUID.randomUUID().toString().replace("-", "");
        String urlStr = ServerConfig.getBaseUrl() + path;

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setRequestProperty("Authorization", "Bearer " + ServerConfig.getAuthToken());
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(60_000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                String CRLF = "\r\n";

                // fromUserId
                os.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
                os.write(("Content-Disposition: form-data; name=\"fromUserId\"" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
                os.write((fromId.toString() + CRLF).getBytes(StandardCharsets.UTF_8));

                // toUserId
                os.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
                os.write(("Content-Disposition: form-data; name=\"toUserId\"" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
                os.write((toId.toString() + CRLF).getBytes(StandardCharsets.UTF_8));

                // isOffline
                os.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
                os.write(("Content-Disposition: form-data; name=\"isOffline\"" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
                os.write((String.valueOf(isOffline) + CRLF).getBytes(StandardCharsets.UTF_8));

                // duration（如果是语音或视频）
                if (duration != null) {
                    os.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
                    os.write(("Content-Disposition: form-data; name=\"duration\"" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
                    os.write((String.valueOf(duration) + CRLF).getBytes(StandardCharsets.UTF_8));
                }

                // file
                String contentType = getContentType(fileName);
                os.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
                os.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"" + CRLF).getBytes(StandardCharsets.UTF_8));
                os.write(("Content-Type: " + contentType + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
                os.write(fileBytes);
                os.write(CRLF.getBytes(StandardCharsets.UTF_8));

                // 结束
                os.write(("--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) return null;
            return new String(ApiClient.readAllBytes(is), StandardCharsets.UTF_8);

        } finally {
            conn.disconnect();
        }
    }

    // ===== 工具方法 =====

    /**
     * 提取 SIP 目标用户名
     * 对应 PC 端 extractSipUsernameFromUsername：username → sipNumber
     * 例如 user100 → 100
     */
    private String extractSipTarget(String username) {
        if (username == null) return "";
        if (username.startsWith("user")) return username.substring(4);
        return username;
    }

    private byte[] readBytes(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            return ApiClient.readAllBytes(is);
        } catch (Exception e) {
            Log.e(TAG, "readBytes failed", e);
            return null;
        }
    }

    private byte[] readBytesFromFile(String filePath) {
        try (InputStream is = new java.io.FileInputStream(filePath)) {
            return ApiClient.readAllBytes(is);
        } catch (Exception e) {
            Log.e(TAG, "readBytesFromFile failed", e);
            return null;
        }
    }

    private String getFileName(Uri uri) {
        String path = uri.getPath();
        if (path == null) return "file_" + System.currentTimeMillis();

        // 尝试从 Uri 获取文件名
        String fileName = null;
        if ("content".equals(uri.getScheme())) {
            try {
                android.database.Cursor cursor = getContentResolver().query(
                        uri,
                        new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
                        null, null, null
                );
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                        if (nameIndex >= 0) {
                            fileName = cursor.getString(nameIndex);
                        }
                    }
                    cursor.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "获取文件名失败", e);
            }
        }

        // 如果无法从数据库获取，则从路径提取
        if (fileName == null) {
            int slash = path.lastIndexOf('/');
            fileName = slash >= 0 ? path.substring(slash + 1) : path;
        }

        // 确保文件名不为空
        if (fileName == null || fileName.isEmpty()) {
            fileName = "file_" + System.currentTimeMillis();
        }

        return fileName;
    }

    /**
     * 根据文件名获取 Content-Type
     * 对应 PC 端 HttpClientUtil.getContentType
     */
    private String getContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".m4a")) return "audio/mp4";  // 语音录音格式
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "application/msword";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return "application/vnd.ms-excel";
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) return "application/vnd.ms-powerpoint";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".rar")) return "application/x-rar-compressed";
        return "application/octet-stream";
    }

    private void scrollToBottom() {
        int count = messageAdapter.getItemCount();
        if (count > 0) binding.rvMessages.smoothScrollToPosition(count - 1);
    }

    private void showToast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messageCallback != null) {
            SipMessageReceiver.getInstance().removeListener(messageCallback);
            messageCallback = null;
        }
        UnreadManager.getInstance().setCurrentChatUsername(null);
        VoiceRecordManager.getInstance().release();
        VoicePlayManager.getInstance().release();
        executor.shutdown();
    }
}
