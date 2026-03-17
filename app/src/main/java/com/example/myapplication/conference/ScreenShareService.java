package com.example.myapplication.conference;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.myapplication.R;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * 屏幕共享前台服务
 * Android 10+ 要求 MediaProjection 必须在前台服务中运行
 *
 * 采集管线：MediaProjection → VirtualDisplay → ImageReader(RGBA) → Bitmap → JPEG(Q=50) → Base64 → 回调
 */
public class ScreenShareService extends Service {

    private static final String TAG = "ScreenShareService";
    private static final String CHANNEL_ID = "screen_share_channel";
    private static final int NOTIFICATION_ID = 2001;

    private static final int CAPTURE_W = 480;
    private static final int CAPTURE_H = 800;
    private static final int JPEG_QUALITY = 50;
    private static final long FRAME_INTERVAL_MS = 200; // 5 FPS

    // Static callback — Activity sets this before starting the service
    private static volatile ScreenFrameListener frameListener;
    private static volatile boolean running = false;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread captureThread;
    private Handler captureHandler;
    private long lastFrameTime = 0;

    public interface ScreenFrameListener {
        void onScreenFrame(String base64Data, int width, int height);
    }

    public static void setFrameListener(ScreenFrameListener listener) {
        frameListener = listener;
    }

    public static boolean isRunning() {
        return running;
    }

    public static void start(Context context, int resultCode, Intent data) {
        Intent intent = new Intent(context, ScreenShareService.class);
        intent.putExtra("resultCode", resultCode);
        intent.putExtra("data", data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, ScreenShareService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Show foreground notification first (must call within 5 seconds)
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("屏幕共享中")
                .setContentText("正在共享您的屏幕到会议")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        // Get MediaProjection
        int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
        Intent data = intent.getParcelableExtra("data");
        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.e(TAG, "Invalid MediaProjection result");
            stopSelf();
            return START_NOT_STICKY;
        }

        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpm.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            Log.e(TAG, "Failed to get MediaProjection");
            stopSelf();
            return START_NOT_STICKY;
        }

        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                Log.i(TAG, "MediaProjection stopped by system");
                cleanup();
                stopSelf();
            }
        }, null);

        startCapture();
        running = true;
        Log.i(TAG, "Screen share started");

        return START_NOT_STICKY;
    }

    private void startCapture() {
        captureThread = new HandlerThread("ScreenCapture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int density = metrics.densityDpi;

        imageReader = ImageReader.newInstance(CAPTURE_W, CAPTURE_H, PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image == null) return;

                long now = System.currentTimeMillis();
                if (now - lastFrameTime < FRAME_INTERVAL_MS) return;
                lastFrameTime = now;

                String base64 = encodeImage(image);
                if (base64 != null && frameListener != null) {
                    frameListener.onScreenFrame(base64, CAPTURE_W, CAPTURE_H);
                }
            } catch (Exception e) {
                Log.e(TAG, "Capture frame failed", e);
            } finally {
                if (image != null) image.close();
            }
        }, captureHandler);

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenShare",
                CAPTURE_W, CAPTURE_H, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null, captureHandler
        );
    }

    private String encodeImage(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * CAPTURE_W;

        // Create bitmap from RGBA buffer
        Bitmap bitmap = Bitmap.createBitmap(
                CAPTURE_W + rowPadding / pixelStride, CAPTURE_H,
                Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);

        // Crop if there's padding
        if (rowPadding > 0) {
            Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, CAPTURE_W, CAPTURE_H);
            bitmap.recycle();
            bitmap = cropped;
        }

        // Compress to JPEG
        ByteArrayOutputStream out = new ByteArrayOutputStream(16384);
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
        bitmap.recycle();

        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    private void cleanup() {
        running = false;
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        if (captureThread != null) {
            captureThread.quitSafely();
            captureThread = null;
        }
    }

    @Override
    public void onDestroy() {
        cleanup();
        Log.i(TAG, "Screen share service destroyed");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "屏幕共享", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("屏幕共享进行中");
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }
}
