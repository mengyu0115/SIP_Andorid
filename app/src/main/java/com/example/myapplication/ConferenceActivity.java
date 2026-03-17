package com.example.myapplication;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.adapter.ConferenceChatAdapter;
import com.example.myapplication.adapter.ConferenceVideoAdapter;
import com.example.myapplication.conference.ConferenceAudioHandler;
import com.example.myapplication.conference.ConferenceMediaClient;
import com.example.myapplication.conference.ConferenceSignalingClient;
import com.example.myapplication.conference.ConferenceVideoHandler;
import com.example.myapplication.conference.ScreenShareService;
import com.example.myapplication.model.MediaFrameMessage;
import com.example.myapplication.network.ServerConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 会议 Activity
 * 对应 PC 端 ConferenceViewController
 */
public class ConferenceActivity extends AppCompatActivity {

    private static final String TAG = "ConferenceActivity";

    // Intent extras
    private long conferenceId;
    private String conferenceCode;
    private String conferenceTitle;

    // WebSocket clients
    private ConferenceSignalingClient signalingClient;
    private ConferenceMediaClient mediaClient;

    // Media handlers
    private ConferenceAudioHandler audioHandler;
    private ConferenceVideoHandler videoHandler;

    // UI
    private TextView tvConferenceTitle, tvConferenceCode, tvParticipantCount, tvDuration;
    private RecyclerView rvVideoGrid, rvChat;
    private LinearLayout chatPanel;
    private EditText etChatInput;
    private TextView tvMicIcon, tvMicLabel, tvCameraIcon, tvCameraLabel;
    private TextView tvSpeakerIcon, tvSpeakerLabel;
    private TextView tvChatBadge;
    private View btnSwitchCamera;
    private TextView tvScreenShareIcon, tvScreenShareLabel;
    private FrameLayout screenShareOverlay;
    private ImageView ivScreenShare;
    private TextView tvScreenShareOverlayLabel;

    // Adapters
    private ConferenceVideoAdapter videoAdapter;
    private ConferenceChatAdapter chatAdapter;

    // State
    private boolean micMuted = true;
    private boolean cameraOn = false;
    private boolean chatVisible = false;
    private boolean speakerOn = true;
    private boolean screenSharing = false;
    private long screenShareUserId = -1; // who is sharing screen (-1 = none)
    private int unreadChatCount = 0;
    private long startTimeMillis;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private Runnable durationRunnable;

    // Permission launcher
    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<Intent> screenCaptureLauncher;

    public static void start(Context context, long conferenceId, String code, String title) {
        Intent intent = new Intent(context, ConferenceActivity.class);
        intent.putExtra("conferenceId", conferenceId);
        intent.putExtra("conferenceCode", code);
        intent.putExtra("conferenceTitle", title);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 崩溃日志捕获器：写入文件供调试
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                String crash = "Thread: " + t.getName() + "\n" + Log.getStackTraceString(e);
                Log.e(TAG, "!!!!! CRASH CAUGHT !!!!!\n" + crash);
                java.io.FileWriter fw = new java.io.FileWriter(
                        new java.io.File(getExternalFilesDir(null), "conference_crash.txt"));
                fw.write(crash);
                fw.close();
            } catch (Exception ignored) {}
            if (defaultHandler != null) defaultHandler.uncaughtException(t, e);
        });

        setContentView(R.layout.activity_conference);

        conferenceId = getIntent().getLongExtra("conferenceId", -1);
        conferenceCode = getIntent().getStringExtra("conferenceCode");
        conferenceTitle = getIntent().getStringExtra("conferenceTitle");

        initViews();
        initAdapters();
        setupListeners();

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean audioGranted = Boolean.TRUE.equals(result.get(Manifest.permission.RECORD_AUDIO));
                    boolean cameraGranted = Boolean.TRUE.equals(result.get(Manifest.permission.CAMERA));
                    if (audioGranted) {
                        connectAndJoin();
                    } else {
                        Toast.makeText(this, "需要麦克风权限才能加入会议", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
        );

        screenCaptureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        startScreenSharing(result.getResultCode(), result.getData());
                    } else {
                        Toast.makeText(this, "屏幕共享权限被拒绝", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        requestPermissions();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                hangup();
            }
        });
    }

    private void initViews() {
        tvConferenceTitle = findViewById(R.id.tvConferenceTitle);
        tvConferenceCode = findViewById(R.id.tvConferenceCode);
        tvParticipantCount = findViewById(R.id.tvParticipantCount);
        tvDuration = findViewById(R.id.tvDuration);
        rvVideoGrid = findViewById(R.id.rvVideoGrid);
        rvChat = findViewById(R.id.rvChat);
        chatPanel = findViewById(R.id.chatPanel);
        etChatInput = findViewById(R.id.etChatInput);
        tvMicIcon = findViewById(R.id.tvMicIcon);
        tvMicLabel = findViewById(R.id.tvMicLabel);
        tvCameraIcon = findViewById(R.id.tvCameraIcon);
        tvCameraLabel = findViewById(R.id.tvCameraLabel);
        tvSpeakerIcon = findViewById(R.id.tvSpeakerIcon);
        tvSpeakerLabel = findViewById(R.id.tvSpeakerLabel);
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera);
        tvChatBadge = findViewById(R.id.tvChatBadge);
        tvScreenShareIcon = findViewById(R.id.tvScreenShareIcon);
        tvScreenShareLabel = findViewById(R.id.tvScreenShareLabel);
        screenShareOverlay = findViewById(R.id.screenShareOverlay);
        ivScreenShare = findViewById(R.id.ivScreenShare);
        tvScreenShareOverlayLabel = findViewById(R.id.tvScreenShareOverlayLabel);

        tvConferenceTitle.setText(conferenceTitle != null ? conferenceTitle : "会议");
        if (conferenceCode != null && !conferenceCode.isEmpty()) {
            tvConferenceCode.setText("会议号: " + conferenceCode);
            tvConferenceCode.setVisibility(View.VISIBLE);
            tvConferenceCode.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("会议号", conferenceCode));
                Toast.makeText(this, "会议号已复制", Toast.LENGTH_SHORT).show();
            });
        }

        // Keep screen on
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void initAdapters() {
        videoAdapter = new ConferenceVideoAdapter();
        rvVideoGrid.setLayoutManager(new GridLayoutManager(this, 2));
        rvVideoGrid.setAdapter(videoAdapter);

        videoAdapter.setOnParticipantClickListener(participant -> {
            if (participant.lastFrame != null && participant.videoEnabled) {
                showFullscreenVideo(participant);
            }
        });

        chatAdapter = new ConferenceChatAdapter();
        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(chatAdapter);
    }

    private void setupListeners() {
        findViewById(R.id.btnMic).setOnClickListener(v -> toggleMicrophone());
        findViewById(R.id.btnSpeaker).setOnClickListener(v -> toggleSpeaker());
        findViewById(R.id.btnCamera).setOnClickListener(v -> toggleCamera());
        btnSwitchCamera.setOnClickListener(v -> switchCamera());
        findViewById(R.id.btnScreenShare).setOnClickListener(v -> toggleScreenShare());
        findViewById(R.id.btnChat).setOnClickListener(v -> toggleChat());
        findViewById(R.id.btnHangup).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("离开会议")
                    .setMessage("确定要离开当前会议吗？")
                    .setPositiveButton("离开", (d, w) -> hangup())
                    .setNegativeButton("取消", null)
                    .show();
        });
        findViewById(R.id.btnSendChat).setOnClickListener(v -> sendChatMessage());

        tvParticipantCount.setOnClickListener(v -> showParticipantList());

        etChatInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendChatMessage();
                return true;
            }
            return false;
        });
    }

    private void requestPermissions() {
        boolean audioOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean cameraOk = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;

        if (audioOk && cameraOk) {
            connectAndJoin();
        } else {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA
            });
        }
    }

    // ===== WebSocket connection =====

    private void connectAndJoin() {
        String serverIp = ServerConfig.getServerIp();
        int port = ServerConfig.HTTP_PORT;
        String userId = String.valueOf(ServerConfig.getCurrentUserId());
        String username = ServerConfig.getCurrentUsername();

        // Init media handlers
        audioHandler = new ConferenceAudioHandler();
        videoHandler = new ConferenceVideoHandler();

        // Create media client
        mediaClient = new ConferenceMediaClient(serverIp, port, conferenceId, ServerConfig.getCurrentUserId(), username);
        mediaClient.setFrameListener(frame -> handleMediaFrame(frame));

        // Init handlers with media client
        audioHandler.init(mediaClient);
        videoHandler.init(mediaClient, this);
        videoHandler.setFrameCallback(new ConferenceVideoHandler.FrameCallback() {
            @Override
            public void onLocalFrame(Bitmap bitmap) {
                mainHandler.post(() -> {
                    long myId = ServerConfig.getCurrentUserId();
                    ConferenceVideoAdapter.Participant p = videoAdapter.findParticipant(myId);
                    if (p != null) {
                        p.lastFrame = bitmap;
                        p.videoEnabled = true;
                        int idx = videoAdapter.findParticipantIndex(myId);
                        if (idx >= 0) videoAdapter.notifyItemChanged(idx);
                    }
                });
            }

            @Override
            public void onRemoteFrame(long userId2, Bitmap bitmap) {
                mainHandler.post(() -> videoAdapter.updateVideoFrame(userId2, bitmap));
            }
        });

        // Create signaling client
        signalingClient = new ConferenceSignalingClient(serverIp, port, userId, username);
        signalingClient.setListener(new ConferenceSignalingClient.SignalingListener() {
            @Override
            public void onConnected() {
                Log.i(TAG, "Signaling connected, sending JOIN");
                signalingClient.sendJoinConference(conferenceId);

                // Add self to video grid
                long myId = ServerConfig.getCurrentUserId();
                String myName = ServerConfig.getCurrentUsername();
                ConferenceVideoAdapter.Participant self = new ConferenceVideoAdapter.Participant(myId, myName, true);
                self.audioEnabled = !micMuted;
                videoAdapter.addParticipant(self);
                updateParticipantCount();

                startDurationTimer();
            }

            @Override
            public void onDisconnected() {
                Log.i(TAG, "Signaling disconnected");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Signaling error: " + error);
                Toast.makeText(ConferenceActivity.this, "连接错误: " + error, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onMessage(String type, JSONObject json) {
                handleSignalingMessage(type, json);
            }
        });

        // Set audio mode BEFORE connecting WebSockets
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        am.setMode(AudioManager.MODE_IN_COMMUNICATION);
        am.setSpeakerphoneOn(true);

        // Connect both WebSockets (after audio mode is set)
        executor.execute(() -> signalingClient.connect());
        executor.execute(() -> mediaClient.connect());
    }

    // ===== Signaling message handling =====

    private void handleSignalingMessage(String type, JSONObject json) {
        switch (type) {
            case "existing_participants":
                handleExistingParticipants(json);
                break;
            case "participant_joined":
                handleParticipantJoined(json);
                break;
            case "participant_left":
                handleParticipantLeft(json);
                break;
            case "conference_message":
                handleConferenceMessage(json);
                break;
            case "media_status_update":
                handleMediaStatusUpdate(json);
                break;
            case "screen_share_start":
                handleScreenShareStarted(json);
                break;
            case "screen_share_stop":
                handleScreenShareStopped(json);
                break;
            case "CONFERENCE_ENDED":
                handleConferenceEnded();
                break;
        }
    }

    private void handleExistingParticipants(JSONObject json) {
        try {
            JSONArray participants = json.optJSONArray("participants");
            if (participants == null) return;

            long myId = ServerConfig.getCurrentUserId();
            for (int i = 0; i < participants.length(); i++) {
                JSONObject p = participants.getJSONObject(i);
                long userId = Long.parseLong(p.optString("userId", "0"));
                String username = p.optString("username", "");

                if (userId != myId) {
                    ConferenceVideoAdapter.Participant participant =
                            new ConferenceVideoAdapter.Participant(userId, username, false);
                    videoAdapter.addParticipant(participant);
                    Log.i(TAG, "Added existing participant: " + username + " (" + userId + ")");
                }
            }
            updateParticipantCount();
        } catch (Exception e) {
            Log.e(TAG, "Handle existing participants failed", e);
        }
    }

    private void handleParticipantJoined(JSONObject json) {
        try {
            long userId = Long.parseLong(json.optString("userId", "0"));
            String username = json.optString("username", "");
            String nickname = json.optString("nickname", "");
            long myId = ServerConfig.getCurrentUserId();

            if (userId != myId) {
                String displayName = (nickname != null && !nickname.isEmpty()) ? nickname : username;
                ConferenceVideoAdapter.Participant p =
                        new ConferenceVideoAdapter.Participant(userId, displayName, false);
                videoAdapter.addParticipant(p);
                updateParticipantCount();

                addSystemMessage(displayName + " 加入了会议");
                Log.i(TAG, "Participant joined: " + displayName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Handle participant joined failed", e);
        }
    }

    private void handleParticipantLeft(JSONObject json) {
        try {
            long userId = Long.parseLong(json.optString("userId", "0"));
            String username = json.optString("username", "");
            long myId = ServerConfig.getCurrentUserId();

            if (userId != myId) {
                videoAdapter.removeParticipant(userId);
                audioHandler.removeUser(userId);
                updateParticipantCount();

                // If the leaving user was sharing screen, clear overlay
                if (userId == screenShareUserId) {
                    screenShareUserId = -1;
                    screenShareOverlay.setVisibility(View.GONE);
                    rvVideoGrid.setVisibility(View.VISIBLE);
                }

                addSystemMessage(username + " 离开了会议");
                Log.i(TAG, "Participant left: " + username);
            }
        } catch (Exception e) {
            Log.e(TAG, "Handle participant left failed", e);
        }
    }

    private void handleConferenceMessage(JSONObject json) {
        String senderId = json.optString("senderId", json.optString("userId", ""));
        String senderName = json.optString("senderName", json.optString("username", ""));
        String message = json.optString("message", "");
        String myId = String.valueOf(ServerConfig.getCurrentUserId());

        if (!myId.equals(senderId)) {
            chatAdapter.addMessage(new ConferenceChatAdapter.ChatMsg(senderName, message, false, false));
            rvChat.scrollToPosition(chatAdapter.getItemCount() - 1);

            if (!chatVisible) {
                unreadChatCount++;
                updateChatBadge();
            }
        }
    }

    private void handleMediaStatusUpdate(JSONObject json) {
        try {
            // The data may be nested or flat
            JSONObject data = json.has("data") ? json.getJSONObject("data") : json;
            String userId = data.optString("userId", "");
            boolean audioEnabled = data.optBoolean("audioEnabled", false);
            boolean videoEnabled = data.optBoolean("videoEnabled", false);
            String myId = String.valueOf(ServerConfig.getCurrentUserId());

            if (!myId.equals(userId)) {
                long uid = Long.parseLong(userId);
                videoAdapter.updateMediaStatus(uid, audioEnabled, videoEnabled);
            }
        } catch (Exception e) {
            Log.e(TAG, "Handle media status update failed", e);
        }
    }

    private void handleConferenceEnded() {
        Toast.makeText(this, "会议已结束", Toast.LENGTH_SHORT).show();
        hangup();
    }

    // ===== Media frame handling =====

    private void handleMediaFrame(MediaFrameMessage frame) {
        if (frame == null) return;
        long myId = ServerConfig.getCurrentUserId();
        Long frameUserId = frame.getUserId();
        if (frameUserId != null && frameUserId == myId) return;
        if (frameUserId == null) return;

        String mediaType = frame.getMediaType();
        if ("VIDEO".equals(mediaType)) {
            if (frame.getFrameData() != null && videoHandler != null) {
                videoHandler.onVideoFrameReceived(frameUserId, frame.getFrameData());
            }
        } else if ("AUDIO".equals(mediaType)) {
            if (frame.getFrameData() != null && audioHandler != null) {
                audioHandler.onAudioFrameReceived(frameUserId, frame.getFrameData());
            }
        } else if ("SCREEN".equals(mediaType)) {
            if (frame.getFrameData() != null) {
                handleScreenFrame(frameUserId, frame.getFrameData());
            }
        }
    }

    // ===== Control buttons =====

    private void toggleMicrophone() {
        micMuted = !micMuted;

        if (!micMuted) {
            audioHandler.startCapture();
            audioHandler.setMute(false);
            tvMicIcon.setText("🎤");
            tvMicLabel.setText("麦克风");
        } else {
            audioHandler.stopCapture();
            tvMicIcon.setText("🔇");
            tvMicLabel.setText("静音");
        }

        // Update self status
        long myId = ServerConfig.getCurrentUserId();
        ConferenceVideoAdapter.Participant self = videoAdapter.findParticipant(myId);
        if (self != null) {
            self.audioEnabled = !micMuted;
        }

        // Notify others
        if (signalingClient != null && signalingClient.isConnected()) {
            signalingClient.sendMediaStatusUpdate(conferenceId, !micMuted, cameraOn, screenSharing);
        }
    }

    private void toggleCamera() {
        cameraOn = !cameraOn;

        if (cameraOn) {
            videoHandler.startCapture(true);
            tvCameraIcon.setText("📹");
            tvCameraLabel.setText("关闭摄像头");
            btnSwitchCamera.setVisibility(View.VISIBLE);
        } else {
            videoHandler.stopCapture();
            tvCameraIcon.setText("📷");
            tvCameraLabel.setText("摄像头");
            btnSwitchCamera.setVisibility(View.GONE);

            // Clear local preview
            long myId = ServerConfig.getCurrentUserId();
            int idx = videoAdapter.findParticipantIndex(myId);
            if (idx >= 0) {
                ConferenceVideoAdapter.Participant self = videoAdapter.findParticipant(myId);
                if (self != null) {
                    self.lastFrame = null;
                    self.videoEnabled = false;
                    videoAdapter.notifyItemChanged(idx);
                }
            }
        }

        // Update self status
        long myId = ServerConfig.getCurrentUserId();
        ConferenceVideoAdapter.Participant self = videoAdapter.findParticipant(myId);
        if (self != null) {
            self.videoEnabled = cameraOn;
        }

        // Notify others
        if (signalingClient != null && signalingClient.isConnected()) {
            signalingClient.sendMediaStatusUpdate(conferenceId, !micMuted, cameraOn, screenSharing);
        }
    }

    private void toggleChat() {
        chatVisible = !chatVisible;
        if (chatVisible) {
            chatPanel.setVisibility(View.VISIBLE);
            AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
            fadeIn.setDuration(200);
            chatPanel.startAnimation(fadeIn);
            rvChat.scrollToPosition(chatAdapter.getItemCount() - 1);
            unreadChatCount = 0;
            updateChatBadge();
        } else {
            AlphaAnimation fadeOut = new AlphaAnimation(1f, 0f);
            fadeOut.setDuration(200);
            fadeOut.setAnimationListener(new Animation.AnimationListener() {
                @Override public void onAnimationStart(Animation a) {}
                @Override public void onAnimationRepeat(Animation a) {}
                @Override public void onAnimationEnd(Animation a) {
                    chatPanel.setVisibility(View.GONE);
                }
            });
            chatPanel.startAnimation(fadeOut);
        }
    }

    private void toggleSpeaker() {
        speakerOn = !speakerOn;
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        am.setSpeakerphoneOn(speakerOn);
        tvSpeakerIcon.setText(speakerOn ? "🔊" : "🔈");
        tvSpeakerLabel.setText(speakerOn ? "免提" : "听筒");
    }

    private void switchCamera() {
        if (videoHandler != null && cameraOn) {
            videoHandler.switchCamera();
        }
    }

    // ===== Screen sharing =====

    private void toggleScreenShare() {
        if (screenSharing) {
            stopScreenSharing();
        } else {
            // Cannot share screen while someone else is sharing
            if (screenShareUserId > 0) {
                Toast.makeText(this, "其他参与者正在共享屏幕", Toast.LENGTH_SHORT).show();
                return;
            }
            // Request MediaProjection permission
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            screenCaptureLauncher.launch(mpm.createScreenCaptureIntent());
        }
    }

    private void startScreenSharing(int resultCode, Intent data) {
        screenSharing = true;

        // Stop camera if on (same as PC)
        if (cameraOn) {
            toggleCamera();
        }

        // Update UI
        tvScreenShareIcon.setText("🖥");
        tvScreenShareLabel.setText("停止共享");
        tvScreenShareLabel.setTextColor(0xFFFF5252);

        // Set frame listener before starting service
        ScreenShareService.setFrameListener((base64Data, width, height) -> {
            if (mediaClient != null && mediaClient.isConnected()) {
                mediaClient.sendScreenFrame(base64Data, width, height);
            }
        });

        // Start foreground service
        ScreenShareService.start(this, resultCode, data);

        // Notify others via signaling
        if (signalingClient != null && signalingClient.isConnected()) {
            signalingClient.sendScreenShareStart(conferenceId);
            signalingClient.sendMediaStatusUpdate(conferenceId, !micMuted, cameraOn, true);
        }

        addSystemMessage("您开始了屏幕共享");
        Log.i(TAG, "Screen sharing started");
    }

    private void stopScreenSharing() {
        screenSharing = false;

        // Stop service
        ScreenShareService.stop(this);
        ScreenShareService.setFrameListener(null);

        // Update UI
        tvScreenShareIcon.setText("🖥");
        tvScreenShareLabel.setText("共享");
        tvScreenShareLabel.setTextColor(0xFFAAAAAA);

        // Notify others
        if (signalingClient != null && signalingClient.isConnected()) {
            signalingClient.sendScreenShareStop(conferenceId);
            signalingClient.sendMediaStatusUpdate(conferenceId, !micMuted, cameraOn, false);
        }

        addSystemMessage("您停止了屏幕共享");
        Log.i(TAG, "Screen sharing stopped");
    }

    private void handleScreenShareStarted(JSONObject json) {
        try {
            long userId = Long.parseLong(json.optString("userId", "0"));
            String username = json.optString("username", "");
            long myId = ServerConfig.getCurrentUserId();

            if (userId != myId) {
                screenShareUserId = userId;
                // Show overlay
                screenShareOverlay.setVisibility(View.VISIBLE);
                rvVideoGrid.setVisibility(View.GONE);
                tvScreenShareOverlayLabel.setText(username + " 正在共享屏幕");
                addSystemMessage(username + " 开始了屏幕共享");
            }
        } catch (Exception e) {
            Log.e(TAG, "Handle screen share start failed", e);
        }
    }

    private void handleScreenShareStopped(JSONObject json) {
        try {
            long userId = Long.parseLong(json.optString("userId", "0"));
            String username = json.optString("username", "");
            long myId = ServerConfig.getCurrentUserId();

            if (userId != myId) {
                screenShareUserId = -1;
                // Hide overlay
                screenShareOverlay.setVisibility(View.GONE);
                rvVideoGrid.setVisibility(View.VISIBLE);
                addSystemMessage(username + " 停止了屏幕共享");
            }
        } catch (Exception e) {
            Log.e(TAG, "Handle screen share stop failed", e);
        }
    }

    private void handleScreenFrame(long userId, String base64Data) {
        try {
            byte[] jpegBytes = Base64.decode(base64Data, Base64.NO_WRAP);
            Bitmap bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
            if (bmp != null) {
                mainHandler.post(() -> {
                    if (screenShareOverlay.getVisibility() != View.VISIBLE) {
                        // Auto-show overlay if we receive screen frames
                        screenShareUserId = userId;
                        screenShareOverlay.setVisibility(View.VISIBLE);
                        rvVideoGrid.setVisibility(View.GONE);
                    }
                    ivScreenShare.setImageBitmap(bmp);
                });
            }
        } catch (Throwable t) {
            Log.e(TAG, "Decode screen frame failed", t);
        }
    }

    private void sendChatMessage() {
        String text = etChatInput.getText().toString().trim();
        if (text.isEmpty()) return;

        // Show locally
        String myName = ServerConfig.getCurrentUsername();
        chatAdapter.addMessage(new ConferenceChatAdapter.ChatMsg(myName, text, false, true));
        rvChat.scrollToPosition(chatAdapter.getItemCount() - 1);

        // Send via WebSocket
        if (signalingClient != null && signalingClient.isConnected()) {
            signalingClient.sendConferenceMessage(conferenceId, text);
        }

        etChatInput.setText("");
    }

    private void hangup() {
        Log.i(TAG, "Hanging up");

        // Stop screen share if active
        if (screenSharing) {
            ScreenShareService.stop(this);
            ScreenShareService.setFrameListener(null);
            screenSharing = false;
        }

        // Send leave
        if (signalingClient != null && signalingClient.isConnected()) {
            signalingClient.sendLeaveConference(conferenceId);
        }

        // Release media
        if (audioHandler != null) {
            audioHandler.release();
            audioHandler = null;
        }
        if (videoHandler != null) {
            videoHandler.release();
            videoHandler = null;
        }

        // Disconnect WebSockets
        executor.execute(() -> {
            if (signalingClient != null) signalingClient.disconnect();
            if (mediaClient != null) mediaClient.disconnect();
        });

        // Reset audio
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        am.setMode(AudioManager.MODE_NORMAL);

        stopDurationTimer();
        finish();
    }

    // ===== Fullscreen video =====

    private void showFullscreenVideo(ConferenceVideoAdapter.Participant participant) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView imageView = new ImageView(this);
        imageView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setBackgroundColor(0xFF000000);
        imageView.setImageBitmap(participant.lastFrame);
        imageView.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(imageView);
        dialog.show();
    }

    // ===== Helpers =====

    private void showParticipantList() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_participant_list, null);
        RecyclerView rvParticipants = dialogView.findViewById(R.id.rvParticipants);
        TextView tvTitle = dialogView.findViewById(R.id.tvParticipantTitle);

        java.util.List<ConferenceVideoAdapter.Participant> participants = videoAdapter.getParticipants();
        tvTitle.setText("参与者 (" + participants.size() + ")");

        rvParticipants.setLayoutManager(new LinearLayoutManager(this));
        rvParticipants.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_participant, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                ConferenceVideoAdapter.Participant p = participants.get(position);
                TextView tvAvatar = holder.itemView.findViewById(R.id.tvAvatar);
                TextView tvName = holder.itemView.findViewById(R.id.tvParticipantName);
                TextView tvAudio = holder.itemView.findViewById(R.id.tvParticipantAudio);
                TextView tvVideo = holder.itemView.findViewById(R.id.tvParticipantVideo);

                String initial = (p.username != null && !p.username.isEmpty())
                        ? p.username.substring(0, 1).toUpperCase() : "?";
                tvAvatar.setText(initial);
                tvName.setText(p.isLocal ? p.username + " (我)" : p.username);
                tvAudio.setText(p.audioEnabled ? "🎤" : "🔇");
                tvVideo.setText(p.videoEnabled ? "📹" : "📷");
            }

            @Override
            public int getItemCount() {
                return participants.size();
            }
        });

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void updateParticipantCount() {
        tvParticipantCount.setText(videoAdapter.getParticipantCount() + "人");
    }

    private void updateChatBadge() {
        if (unreadChatCount <= 0) {
            tvChatBadge.setVisibility(View.GONE);
        } else {
            tvChatBadge.setText(unreadChatCount > 99 ? "99+" : String.valueOf(unreadChatCount));
            tvChatBadge.setVisibility(View.VISIBLE);
        }
    }

    private void addSystemMessage(String text) {
        chatAdapter.addMessage(new ConferenceChatAdapter.ChatMsg("系统", text, true, false));
        rvChat.scrollToPosition(chatAdapter.getItemCount() - 1);
    }

    private void startDurationTimer() {
        startTimeMillis = System.currentTimeMillis();
        durationRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsed = (System.currentTimeMillis() - startTimeMillis) / 1000;
                long min = elapsed / 60;
                long sec = elapsed % 60;
                tvDuration.setText(String.format("%02d:%02d", min, sec));
                mainHandler.postDelayed(this, 1000);
            }
        };
        mainHandler.post(durationRunnable);
    }

    private void stopDurationTimer() {
        if (durationRunnable != null) {
            mainHandler.removeCallbacks(durationRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopDurationTimer();
        // Stop screen share service if still running
        if (screenSharing) {
            ScreenShareService.stop(this);
            ScreenShareService.setFrameListener(null);
        }
        // Release if not already released by hangup()
        if (audioHandler != null) {
            audioHandler.release();
            audioHandler = null;
        }
        if (videoHandler != null) {
            videoHandler.release();
            videoHandler = null;
        }
        executor.shutdown();
    }
}
