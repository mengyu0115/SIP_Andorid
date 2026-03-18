package com.example.myapplication.conference;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Base64;
import android.util.Log;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * 会议视频处理器
 * 通过 WebSocket 发送完整 JPEG Base64（与 PC 端兼容）
 *
 * 采集管线：Camera2 → ImageReader(320x240, YUV) → NV21 → 旋转 → JPEG(Q=70) → Base64 → WebSocket
 * 显示管线：WebSocket → Base64 → JPEG bytes → BitmapFactory → FrameCallback
 */
public class ConferenceVideoHandler {

    private static final String TAG = "ConferenceVideo";

    private static final int CAPTURE_W = 320;
    private static final int CAPTURE_H = 240;
    private static final int JPEG_QUALITY = 70;
    private static final long FRAME_INTERVAL_MS = 100; // 10 FPS

    private ConferenceMediaClient mediaClient;
    private Context context;

    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private volatile boolean capturing = false;
    private volatile boolean frontCamera = true;
    private long lastFrameTime = 0;

    // Preallocated rotation buffer
    private byte[] rotatedNv21;

    private FrameCallback frameCallback;

    public interface FrameCallback {
        void onLocalFrame(Bitmap bitmap);
        void onRemoteFrame(long userId, Bitmap bitmap);
    }

    public void init(ConferenceMediaClient client, Context context) {
        this.mediaClient = client;
        this.context = context;
        this.rotatedNv21 = new byte[CAPTURE_W * CAPTURE_H * 3 / 2];
    }

    public void setFrameCallback(FrameCallback callback) {
        this.frameCallback = callback;
    }

    /**
     * 启动摄像头采集
     */
    public void startCapture(boolean front) {
        if (capturing) return;
        this.frontCamera = front;

        cameraThread = new HandlerThread("ConferenceCamera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());

        imageReader = ImageReader.newInstance(CAPTURE_W, CAPTURE_H, ImageFormat.YUV_420_888, 2);
        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image == null || !capturing) return;

                long now = System.currentTimeMillis();
                if (now - lastFrameTime < FRAME_INTERVAL_MS) return;
                lastFrameTime = now;

                byte[] jpeg = encodeFrame(image);
                if (jpeg == null) return;

                // Local preview
                if (frameCallback != null) {
                    Bitmap bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                    if (bmp != null) {
                        frameCallback.onLocalFrame(bmp);
                    }
                }

                // Send via WebSocket
                if (mediaClient != null && mediaClient.isConnected()) {
                    String base64Data = Base64.encodeToString(jpeg, Base64.NO_WRAP);
                    // After rotation, width and height are swapped
                    mediaClient.sendVideoFrame(base64Data, CAPTURE_H, CAPTURE_W);
                }
            } catch (Exception e) {
                Log.e(TAG, "Process frame failed", e);
            } finally {
                if (image != null) image.close();
            }
        }, cameraHandler);

        openCamera();
    }

    /**
     * 停止摄像头采集
     */
    public void stopCapture() {
        capturing = false;
        closeCamera();
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (cameraThread != null) {
            cameraThread.quitSafely();
            cameraThread = null;
        }
    }

    /**
     * 切换前后摄像头
     */
    public void switchCamera() {
        boolean wasCapturing = capturing;
        if (wasCapturing) {
            stopCapture();
        }
        frontCamera = !frontCamera;
        if (wasCapturing) {
            startCapture(frontCamera);
        }
    }

    public boolean isFrontCamera() {
        return frontCamera;
    }

    /**
     * 接收远端视频帧
     */
    public void onVideoFrameReceived(long userId, String base64Data) {
        try {
            byte[] jpegBytes = Base64.decode(base64Data, Base64.NO_WRAP);
            Bitmap bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
            if (bmp != null && frameCallback != null) {
                frameCallback.onRemoteFrame(userId, bmp);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Decode remote video failed: userId=" + userId, t);
        }
    }

    // ===== Camera2 management =====

    private void openCamera() {
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            String cameraId = findCameraId(manager, frontCamera);
            if (cameraId == null) {
                Log.e(TAG, "No camera found");
                return;
            }

            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    capturing = true;
                    createCaptureSession();
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    Log.e(TAG, "Camera error: " + error);
                    camera.close();
                    cameraDevice = null;
                }
            }, cameraHandler);

        } catch (SecurityException e) {
            Log.e(TAG, "Camera permission not granted", e);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera access failed", e);
        }
    }

    private void createCaptureSession() {
        if (cameraDevice == null || imageReader == null) return;

        try {
            Surface surface = imageReader.getSurface();
            cameraDevice.createCaptureSession(
                    Arrays.asList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                builder.addTarget(surface);
                                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                                session.setRepeatingRequest(builder.build(), null, cameraHandler);
                                Log.i(TAG, "Camera capture started: " + CAPTURE_W + "x" + CAPTURE_H);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Set repeating request failed", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.e(TAG, "Camera session configure failed");
                        }
                    },
                    cameraHandler
            );
        } catch (CameraAccessException e) {
            Log.e(TAG, "Create capture session failed", e);
        }
    }

    private void closeCamera() {
        if (captureSession != null) {
            try { captureSession.close(); } catch (Exception e) { /* ignore */ }
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private String findCameraId(CameraManager manager, boolean front) throws CameraAccessException {
        int targetFacing = front ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK;
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics chars = manager.getCameraCharacteristics(id);
            Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == targetFacing) {
                return id;
            }
        }
        return null;
    }

    // ===== Frame encoding (copied from VideoEngine) =====

    private byte[] encodeFrame(Image image) {
        Image.Plane[] planes = image.getPlanes();
        int w = image.getWidth();
        int h = image.getHeight();

        byte[] nv21 = yuv420ToNv21(planes, w, h);

        // Rotate to portrait
        int newW, newH;
        byte[] rotated;
        if (frontCamera) {
            rotated = rotateNV21_270(nv21, w, h);
        } else {
            rotated = rotateNV21_90(nv21, w, h);
        }
        newW = h;
        newH = w;

        YuvImage yuvImg = new YuvImage(rotated, ImageFormat.NV21, newW, newH, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        yuvImg.compressToJpeg(new Rect(0, 0, newW, newH), JPEG_QUALITY, out);
        return out.toByteArray();
    }

    private byte[] rotateNV21_90(byte[] nv21, int w, int h) {
        int size = w * h;
        byte[] out = rotatedNv21;
        int idx = 0;
        for (int x = 0; x < w; x++) {
            for (int y = h - 1; y >= 0; y--) {
                out[idx++] = nv21[y * w + x];
            }
        }
        int uvH = h / 2, uvW = w / 2;
        for (int x = 0; x < uvW; x++) {
            for (int y = uvH - 1; y >= 0; y--) {
                int srcIdx = size + (y * uvW + x) * 2;
                out[idx++] = nv21[srcIdx];
                out[idx++] = nv21[srcIdx + 1];
            }
        }
        return out;
    }

    private byte[] rotateNV21_270(byte[] nv21, int w, int h) {
        int size = w * h;
        byte[] out = rotatedNv21;
        int idx = 0;
        for (int x = w - 1; x >= 0; x--) {
            for (int y = 0; y < h; y++) {
                out[idx++] = nv21[y * w + x];
            }
        }
        int uvH = h / 2, uvW = w / 2;
        for (int x = uvW - 1; x >= 0; x--) {
            for (int y = 0; y < uvH; y++) {
                int srcIdx = size + (y * uvW + x) * 2;
                out[idx++] = nv21[srcIdx];
                out[idx++] = nv21[srcIdx + 1];
            }
        }
        return out;
    }

    private byte[] yuv420ToNv21(Image.Plane[] planes, int width, int height) {
        int ySize = width * height;
        int uvSize = ySize / 2;
        byte[] nv21 = new byte[ySize + uvSize];

        ByteBuffer yBuf = planes[0].getBuffer();
        int yStride = planes[0].getRowStride();
        int yPix = planes[0].getPixelStride();

        ByteBuffer uBuf = planes[1].getBuffer();
        ByteBuffer vBuf = planes[2].getBuffer();
        int uvStride = planes[1].getRowStride();
        int uvPix = planes[1].getPixelStride();

        int pos = 0;
        if (yStride == width && yPix == 1) {
            yBuf.get(nv21, 0, ySize);
            pos = ySize;
        } else {
            for (int row = 0; row < height; row++) {
                yBuf.position(row * yStride);
                for (int col = 0; col < width; col++) {
                    nv21[pos++] = yBuf.get();
                    if (yPix > 1 && col < width - 1)
                        yBuf.position(yBuf.position() + yPix - 1);
                }
            }
        }

        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                int i = row * uvStride + col * uvPix;
                nv21[pos++] = vBuf.get(i);
                nv21[pos++] = uBuf.get(i);
            }
        }
        return nv21;
    }

    public boolean isCapturing() {
        return capturing;
    }

    /**
     * 释放所有资源
     */
    public void release() {
        stopCapture();
        Log.i(TAG, "All video resources released");
    }
}
