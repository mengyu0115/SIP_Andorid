package com.example.myapplication;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.myapplication.databinding.ActivityCallBinding;
import com.example.myapplication.media.AudioEngine;
import com.example.myapplication.media.VideoEngine;
import com.example.myapplication.sip.SdpManager;
import com.example.myapplication.sip.SipCallHandler;
import com.example.myapplication.sip.SipManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 通话界面
 *
 * 对应 PC 端 CallWindowController.java：
 * - 去电模式：显示"呼叫中..." → "对方振铃..." → "通话中 00:00"
 * - 来电模式：显示接听/拒接按钮
 * - 通话中：显示静音/免提控制 + 通话计时
 * - 视频通话：显示本地摄像头预览 + 切换前后摄像头
 *
 * 启动方式：
 *   去电: CallActivity.startOutgoing(context, targetUser, callType)
 *   来电: CallActivity.startIncoming(context, remoteUser, callType, callId)
 */
public class CallActivity extends AppCompatActivity {

    private static final String TAG = "CallActivity";

    public static final String EXTRA_MODE = "call_mode";
    public static final String EXTRA_REMOTE_USER = "remote_user";
    public static final String EXTRA_CALL_TYPE = "call_type";
    public static final String EXTRA_CALL_ID = "call_id";

    public static final String MODE_OUTGOING = "outgoing";
    public static final String MODE_INCOMING = "incoming";

    /** 启动去电 */
    public static void startOutgoing(Context context, String targetUser, String callType) {
        Intent intent = new Intent(context, CallActivity.class);
        intent.putExtra(EXTRA_MODE, MODE_OUTGOING);
        intent.putExtra(EXTRA_REMOTE_USER, targetUser);
        intent.putExtra(EXTRA_CALL_TYPE, callType);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /** 启动来电 */
    public static void startIncoming(Context context, String remoteUser, String callType, String callId) {
        Intent intent = new Intent(context, CallActivity.class);
        intent.putExtra(EXTRA_MODE, MODE_INCOMING);
        intent.putExtra(EXTRA_REMOTE_USER, remoteUser);
        intent.putExtra(EXTRA_CALL_TYPE, callType);
        intent.putExtra(EXTRA_CALL_ID, callId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private ActivityCallBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String mode;
    private String remoteUser;
    private String callType;
    private String callId;

    private AudioEngine audioEngine;
    private VideoEngine videoEngine;
    private int localAudioPort;
    private int localVideoPort;

    private boolean isMuted = false;
    private boolean isSpeakerOn = false;

    // ===== 视频相关 =====
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private boolean usingFrontCamera = true;
    private boolean isVideoCall = false;

    // 通话计时
    private long callStartTime;
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (binding == null) return;
            long elapsed = (System.currentTimeMillis() - callStartTime) / 1000;
            long min = elapsed / 60;
            long sec = elapsed % 60;
            binding.tvCallDuration.setText(String.format("%02d:%02d", min, sec));
            handler.postDelayed(this, 1000);
        }
    };

    // 权限请求
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean audioGranted = result.get(Manifest.permission.RECORD_AUDIO);
                if (audioGranted != null && audioGranted) {
                    proceedWithCall();
                } else {
                    Toast.makeText(this, "需要麦克风权限才能进行通话", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

    /** 保存 CallActivity 注册前 SipCallHandler 上的旧 listener（即 MainActivity 的全局来电 listener） */
    private SipCallHandler.CallEventListener previousListener;

    // 通话事件监听
    private final SipCallHandler.CallEventListener callListener = new SipCallHandler.CallEventListener() {
        @Override
        public void onCalling(String remoteUser, String callType) {
            runOnUiThread(() -> {
                binding.tvCallStatus.setText("呼叫中...");
            });
        }

        @Override
        public void onIncomingCall(String remoteUser, String callType, String callId) {
            // 来电已在创建 Activity 时处理
        }

        @Override
        public void onRinging() {
            runOnUiThread(() -> {
                binding.tvCallStatus.setText("对方振铃...");
            });
        }

        @Override
        public void onCallEstablished(SdpManager.MediaInfo remoteMedia) {
            runOnUiThread(() -> {
                binding.tvCallStatus.setText("通话中");
                binding.tvCallDuration.setVisibility(View.VISIBLE);
                binding.layoutControls.setVisibility(View.VISIBLE);

                // 来电模式：隐藏接听/拒接，只保留挂断
                binding.btnAccept.setVisibility(View.GONE);
                binding.btnReject.setVisibility(View.GONE);

                startCallTimer();
                startAudio(remoteMedia);

                // 视频通话：启动视频引擎 + 打开摄像头
                if (isVideoCall) {
                    startVideo(remoteMedia);
                    openCamera();
                }
            });
        }

        @Override
        public void onCallEnded(String reason) {
            runOnUiThread(() -> {
                binding.tvCallStatus.setText(reason);
                stopCallTimer();
                stopAudio();
                stopVideo();
                closeCamera();
                handler.postDelayed(() -> finish(), 1500);
            });
        }

        @Override
        public void onCallFailed(String reason) {
            runOnUiThread(() -> {
                binding.tvCallStatus.setText("呼叫失败: " + reason);
                stopAudio();
                stopVideo();
                closeCamera();
                handler.postDelayed(() -> finish(), 2000);
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCallBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mode = getIntent().getStringExtra(EXTRA_MODE);
        remoteUser = getIntent().getStringExtra(EXTRA_REMOTE_USER);
        callType = getIntent().getStringExtra(EXTRA_CALL_TYPE);
        callId = getIntent().getStringExtra(EXTRA_CALL_ID);

        if (mode == null) mode = MODE_OUTGOING;
        if (remoteUser == null) remoteUser = "未知";
        if (callType == null) callType = SipCallHandler.CALL_TYPE_AUDIO;

        isVideoCall = SipCallHandler.CALL_TYPE_VIDEO.equals(callType);

        // 分配本地 RTP 端口
        localAudioPort = SipCallHandler.allocateRtpPort();
        localVideoPort = SipCallHandler.allocateRtpPort();

        setupUI();
        setupButtons();

        // 注册通话事件监听（保存旧 listener 以便 onDestroy 恢复）
        SipCallHandler callHandler = SipManager.getInstance().getCallHandler();
        if (callHandler != null) {
            previousListener = callHandler.getCallEventListener();
            callHandler.setCallEventListener(callListener);
        }

        // 初始化摄像头线程
        if (isVideoCall) {
            cameraThread = new HandlerThread("CameraThread");
            cameraThread.start();
            cameraHandler = new Handler(cameraThread.getLooper());
        }

        // 检查权限后开始通话
        checkPermissionAndProceed();
    }

    private void setupUI() {
        binding.tvRemoteUser.setText(remoteUser);

        if (MODE_INCOMING.equals(mode)) {
            binding.tvCallStatus.setText("来电...");
            binding.btnAccept.setVisibility(View.VISIBLE);
            binding.btnReject.setVisibility(View.VISIBLE);
        } else {
            binding.tvCallStatus.setText("准备呼叫...");
            binding.btnAccept.setVisibility(View.GONE);
            binding.btnReject.setVisibility(View.GONE);
        }

        // 视频通话：显示本地预览区域 + 切换摄像头按钮 + 隐藏语音图标
        if (isVideoCall) {
            binding.surfaceLocalVideo.setVisibility(View.VISIBLE);
            binding.surfaceRemoteVideo.setVisibility(View.VISIBLE);
            binding.btnSwitchCamera.setVisibility(View.VISIBLE);
            binding.ivCallTypeIcon.setVisibility(View.GONE);

            // 本地小窗浮在远端全屏画面之上
            binding.surfaceLocalVideo.setZOrderMediaOverlay(true);
        }
    }

    private void setupButtons() {
        // 挂断
        binding.btnHangup.setOnClickListener(v -> {
            SipCallHandler callHandler = SipManager.getInstance().getCallHandler();
            if (callHandler != null) {
                callHandler.hangup();
            }
            stopAudio();
            stopVideo();
            closeCamera();
            stopCallTimer();
            binding.tvCallStatus.setText("通话结束");
            handler.postDelayed(this::finish, 1000);
        });

        // 接听（来电模式）
        binding.btnAccept.setOnClickListener(v -> {
            SipCallHandler callHandler = SipManager.getInstance().getCallHandler();
            if (callHandler != null) {
                callHandler.answerCall(localAudioPort, localVideoPort);
            }
            binding.btnAccept.setVisibility(View.GONE);
            binding.btnReject.setVisibility(View.GONE);
            binding.tvCallStatus.setText("接听中...");
        });

        // 拒接（来电模式）
        binding.btnReject.setOnClickListener(v -> {
            SipCallHandler callHandler = SipManager.getInstance().getCallHandler();
            if (callHandler != null) {
                callHandler.rejectCall();
            }
            binding.tvCallStatus.setText("已拒接");
            handler.postDelayed(this::finish, 1000);
        });

        // 静音
        binding.btnMute.setOnClickListener(v -> {
            isMuted = !isMuted;
            if (audioEngine != null) audioEngine.setMute(isMuted);
            binding.tvMute.setText(isMuted ? "取消静音" : "静音");
            binding.ivMute.setAlpha(isMuted ? 0.5f : 1.0f);
        });

        // 免提
        binding.btnSpeaker.setOnClickListener(v -> {
            isSpeakerOn = !isSpeakerOn;
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.setSpeakerphoneOn(isSpeakerOn);
            }
            binding.tvSpeaker.setText(isSpeakerOn ? "关闭免提" : "免提");
            binding.ivSpeaker.setAlpha(isSpeakerOn ? 0.5f : 1.0f);
        });

        // 切换前后摄像头
        binding.btnSwitchCamera.setOnClickListener(v -> {
            usingFrontCamera = !usingFrontCamera;
            if (videoEngine != null) {
                videoEngine.setFrontCamera(usingFrontCamera);
            }
            closeCamera();
            openCamera();
        });
    }

    private void checkPermissionAndProceed() {
        boolean audioOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        boolean cameraOk = !isVideoCall || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;

        if (audioOk && cameraOk) {
            proceedWithCall();
        } else {
            // 请求所需权限
            if (isVideoCall) {
                permissionLauncher.launch(new String[]{
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.CAMERA
                });
            } else {
                permissionLauncher.launch(new String[]{Manifest.permission.RECORD_AUDIO});
            }
        }
    }

    private void proceedWithCall() {
        if (MODE_OUTGOING.equals(mode)) {
            // 去电：发起 INVITE
            SipCallHandler callHandler = SipManager.getInstance().getCallHandler();
            if (callHandler != null) {
                String sipTarget = extractSipTarget(remoteUser);
                callHandler.makeCall(sipTarget, callType, localAudioPort, localVideoPort);
            } else {
                binding.tvCallStatus.setText("SIP 未初始化");
                handler.postDelayed(this::finish, 2000);
            }
        }
        // 来电模式：等待用户点击接听
    }

    // ===== 摄像头控制 =====

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "无摄像头权限");
            return;
        }

        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager == null) return;

        try {
            String cameraId = findCameraId(cameraManager, usingFrontCamera);
            if (cameraId == null) {
                Log.e(TAG, "未找到" + (usingFrontCamera ? "前置" : "后置") + "摄像头");
                return;
            }

            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    startPreview();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.e(TAG, "摄像头打开失败: error=" + error);
                    camera.close();
                    cameraDevice = null;
                }
            }, cameraHandler);
        } catch (CameraAccessException | SecurityException e) {
            Log.e(TAG, "打开摄像头失败", e);
        }
    }

    private void startPreview() {
        if (cameraDevice == null) return;

        SurfaceHolder holder = binding.surfaceLocalVideo.getHolder();
        if (holder.getSurface() == null || !holder.getSurface().isValid()) {
            // Surface 还没准备好，等 callback
            holder.addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(@NonNull SurfaceHolder h) {
                    createPreviewSession(h.getSurface());
                }
                @Override
                public void surfaceChanged(@NonNull SurfaceHolder h, int f, int w, int ht) {}
                @Override
                public void surfaceDestroyed(@NonNull SurfaceHolder h) {
                    closeCamera();
                }
            });
        } else {
            createPreviewSession(holder.getSurface());
        }
    }

    private void createPreviewSession(Surface surface) {
        if (cameraDevice == null) return;

        try {
            // 判断是否有编码器 Surface（视频通话且 VideoEngine 已启动）
            Surface encoderSurface = (videoEngine != null) ? videoEngine.getEncoderInputSurface() : null;
            boolean hasEncoder = encoderSurface != null && encoderSurface.isValid();

            Log.i(TAG, "创建Camera2会话: hasEncoder=" + hasEncoder
                    + ", videoEngine=" + (videoEngine != null)
                    + ", encoderSurface=" + (encoderSurface != null)
                    + ", encoderValid=" + (encoderSurface != null && encoderSurface.isValid()));

            int template = hasEncoder ? CameraDevice.TEMPLATE_RECORD : CameraDevice.TEMPLATE_PREVIEW;
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(template);
            builder.addTarget(surface);

            List<Surface> targets;
            if (hasEncoder) {
                builder.addTarget(encoderSurface);
                targets = Arrays.asList(surface, encoderSurface);
                Log.i(TAG, "Camera2 双输出: 预览 + ImageReader编码器");
            } else {
                targets = Collections.singletonList(surface);
                Log.i(TAG, "Camera2 单输出: 仅预览");
            }

            cameraDevice.createCaptureSession(targets,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                builder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                                session.setRepeatingRequest(builder.build(), null, cameraHandler);
                                Log.i(TAG, "摄像头预览已启动");
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "启动预览失败", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "摄像头预览配置失败");
                        }
                    }, cameraHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "创建预览会话失败", e);
        }
    }

    private void closeCamera() {
        if (captureSession != null) {
            try { captureSession.close(); } catch (Exception ignored) {}
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private String findCameraId(CameraManager manager, boolean front) throws CameraAccessException {
        int targetFacing = front ? CameraCharacteristics.LENS_FACING_FRONT
                : CameraCharacteristics.LENS_FACING_BACK;
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics chars = manager.getCameraCharacteristics(id);
            Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == targetFacing) return id;
        }
        // fallback: 返回第一个可用摄像头
        String[] ids = manager.getCameraIdList();
        return ids.length > 0 ? ids[0] : null;
    }

    // ===== 音频控制 =====

    private void startAudio(SdpManager.MediaInfo remoteMedia) {
        if (remoteMedia == null || remoteMedia.remoteIp.isEmpty() || remoteMedia.audioPort <= 0) {
            Log.e(TAG, "远端媒体信息无效: " + remoteMedia);
            return;
        }

        // 设置通话音频模式
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.setMode(AudioManager.MODE_IN_COMMUNICATION);
            am.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }

        audioEngine = new AudioEngine();
        audioEngine.init(localAudioPort, remoteMedia.remoteIp, remoteMedia.audioPort);
        audioEngine.start();
        Log.i(TAG, "音频已启动");
    }

    private void stopAudio() {
        if (audioEngine != null) {
            audioEngine.stop();
            audioEngine = null;
        }

        // 恢复音频模式
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.setMode(AudioManager.MODE_NORMAL);
            am.setSpeakerphoneOn(false);
            am.abandonAudioFocus(null);
        }
    }

    // ===== 视频控制 =====

    private void startVideo(SdpManager.MediaInfo remoteMedia) {
        if (!isVideoCall) return;
        if (remoteMedia == null || !remoteMedia.hasVideo || remoteMedia.videoPort <= 0) {
            Log.w(TAG, "远端无视频媒体信息，跳过视频引擎: " + remoteMedia);
            return;
        }

        Log.i(TAG, "===== 启动视频引擎 =====");
        Log.i(TAG, "  本地视频端口(接收): " + localVideoPort);
        Log.i(TAG, "  远端IP: " + remoteMedia.remoteIp);
        Log.i(TAG, "  远端视频端口(发送目标): " + remoteMedia.videoPort);
        Log.i(TAG, "  远端音频端口: " + remoteMedia.audioPort);
        Log.i(TAG, "  远端SDP hasVideo: " + remoteMedia.hasVideo);

        videoEngine = new VideoEngine();
        videoEngine.init(localVideoPort, remoteMedia.remoteIp, remoteMedia.videoPort,
                binding.surfaceLocalVideo, binding.surfaceRemoteVideo);

        // 设置摄像头方向：前置 270° 旋转，后置 90° 旋转
        videoEngine.setFrontCamera(usingFrontCamera);

        // 先启动编码器（获取 encoderInputSurface 供 Camera2 使用）
        videoEngine.startEncoder();
        // 启动解码器（准备接收远端视频）
        videoEngine.startDecoder();

        Log.i(TAG, "视频引擎已启动, encoderSurface="
                + (videoEngine.getEncoderInputSurface() != null ? "valid" : "null"));
    }

    private void stopVideo() {
        if (videoEngine != null) {
            videoEngine.stop();
            videoEngine = null;
        }
    }

    // ===== 通话计时 =====

    private void startCallTimer() {
        callStartTime = System.currentTimeMillis();
        handler.post(timerRunnable);
    }

    private void stopCallTimer() {
        handler.removeCallbacks(timerRunnable);
    }

    // ===== 工具 =====

    private String extractSipTarget(String username) {
        if (username == null) return "";
        if (username.startsWith("user")) return username.substring(4);
        return username;
    }

    @Override
    public void onBackPressed() {
        // 通话中禁止返回键
        SipCallHandler callHandler = SipManager.getInstance().getCallHandler();
        if (callHandler != null && callHandler.getState() == SipCallHandler.CallState.ACTIVE) {
            return;
        }
        // 非通话中状态允许返回（会自动挂断）
        if (callHandler != null) {
            callHandler.hangup();
        }
        stopAudio();
        stopVideo();
        closeCamera();
        stopCallTimer();
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCallTimer();
        stopAudio();
        stopVideo();
        closeCamera();

        // 停止摄像头线程
        if (cameraThread != null) {
            cameraThread.quitSafely();
            cameraThread = null;
        }

        // 恢复 MainActivity 的全局来电监听器（而不是设 null，否则下次来电不会弹页面）
        SipCallHandler callHandler = SipManager.getInstance().getCallHandler();
        if (callHandler != null) {
            callHandler.setCallEventListener(previousListener);
        }
    }
}
