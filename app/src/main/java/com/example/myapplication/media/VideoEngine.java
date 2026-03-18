package com.example.myapplication.media;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 视频引擎 — JPEG RTP 视频收发（流畅版）
 *
 * 核心策略：中等分辨率 + NV21 级旋转 + 分片发送
 * - 480x360 采集 → NV21 旋转 90°/270° → 360x480 竖屏
 * - JPEG 压缩约 2~6KB，保留分片逻辑兼容大帧
 */
public class VideoEngine {

    private static final String TAG = "VideoEngine";

    // 采集分辨率（Camera2 横屏原生）
    public static final int CAPTURE_W = 480;
    public static final int CAPTURE_H = 360;
    static final int FPS = 15;
    static final int JPEG_QUALITY = 50;
    static final int MAX_RTP_PAYLOAD = 1388;  // 1400 - 12(RTP头)

    // RTP
    static final int RTP_HEADER_SIZE = 12;
    static final int RTP_PT = 96;
    static final int MAX_PACKET_SIZE = 1400;
    static final int TIMESTAMP_INCREMENT = 6000; // 90000/15fps

    private DatagramSocket rtpSocket;
    private String remoteIp;
    private int remotePort;
    private int localPort;

    private int ssrc;
    private int seqNum = 0;
    private long rtpTimestamp = 0;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread senderThread;
    private Thread receiverThread;

    private ImageReader imageReader;
    private Surface encoderInputSurface;
    private HandlerThread imageReaderThread;
    private Handler imageReaderHandler;

    private SurfaceView remoteSurfaceView;

    private final Map<Long, TreeMap<Integer, byte[]>> fragmentBuffers = new ConcurrentHashMap<>();

    private volatile byte[] pendingFrame;
    private final Object frameLock = new Object();

    /** 旋转模式：true=270°（前置），false=90°（后置） */
    private volatile boolean frontCamera = true;

    private long packetsSent, packetsReceived, framesDecoded, framesRendered, framesCaptured, framesDropped;

    // 预分配 NV21 旋转缓冲（240x320 竖屏）
    private byte[] rotatedNv21;

    // InetAddress 缓存，避免每包 DNS 解析
    private InetAddress remoteAddr;

    public void init(int localPort, String remoteIp, int remotePort,
                     SurfaceView localView, SurfaceView remoteView) {
        this.localPort = localPort;
        this.remoteIp = remoteIp;
        this.remotePort = remotePort;
        this.remoteSurfaceView = remoteView;
        this.ssrc = (int) (Math.random() * Integer.MAX_VALUE);
        // 预分配旋转缓冲
        this.rotatedNv21 = new byte[CAPTURE_W * CAPTURE_H * 3 / 2];
        try {
            this.remoteAddr = InetAddress.getByName(remoteIp);
        } catch (Exception e) {
            Log.e(TAG, "解析远端IP失败: " + remoteIp, e);
        }
        Log.i(TAG, "初始化: local=" + localPort + ", remote=" + remoteIp + ":" + remotePort);
    }

    public Surface getEncoderInputSurface() { return encoderInputSurface; }

    public void setFrontCamera(boolean front) { this.frontCamera = front; }

    public void startEncoder() {
        if (imageReader != null) return;
        try {
            rtpSocket = new DatagramSocket(localPort);
            rtpSocket.setSoTimeout(500);
            rtpSocket.setReceiveBufferSize(512 * 1024);
            rtpSocket.setSendBufferSize(256 * 1024);

            imageReaderThread = new HandlerThread("ImageReader");
            imageReaderThread.start();
            imageReaderHandler = new Handler(imageReaderThread.getLooper());

            imageReader = ImageReader.newInstance(CAPTURE_W, CAPTURE_H, ImageFormat.YUV_420_888, 2);
            encoderInputSurface = imageReader.getSurface();

            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image == null) return;
                    framesCaptured++;

                    byte[] jpeg = encodeFrame(image);
                    if (jpeg != null) {
                        synchronized (frameLock) {
                            pendingFrame = jpeg;
                            frameLock.notify();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "帧处理失败", e);
                } finally {
                    if (image != null) image.close();
                }
            }, imageReaderHandler);

            running.set(true);
            senderThread = new Thread(this::senderLoop, "VideoSend");
            senderThread.setDaemon(true);
            senderThread.start();

            Log.i(TAG, "编码器启动: " + CAPTURE_W + "x" + CAPTURE_H + " Q=" + JPEG_QUALITY);
        } catch (Exception e) {
            Log.e(TAG, "编码器启动失败", e);
            stopAll();
        }
    }

    public void startDecoder() {
        if (rtpSocket == null) return;
        SurfaceHolder holder = remoteSurfaceView.getHolder();
        if (holder.getSurface() != null && holder.getSurface().isValid()) {
            startRecvThread();
        } else {
            holder.addCallback(new SurfaceHolder.Callback() {
                public void surfaceCreated(SurfaceHolder h) { startRecvThread(); }
                public void surfaceChanged(SurfaceHolder h, int f, int w, int ht) {}
                public void surfaceDestroyed(SurfaceHolder h) {}
            });
        }
    }

    private void startRecvThread() {
        if (!running.get()) running.set(true);
        receiverThread = new Thread(this::receiverLoop, "VideoRecv");
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    public void stop() {
        running.set(false);
        synchronized (frameLock) { frameLock.notify(); }
        stopAll();
        Log.i(TAG, "停止: cap=" + framesCaptured + " sent=" + packetsSent
                + " recv=" + packetsReceived + " render=" + framesRendered
                + " drop=" + framesDropped);
    }

    private void stopAll() {
        if (imageReader != null) { imageReader.close(); imageReader = null; }
        encoderInputSurface = null;
        if (rtpSocket != null && !rtpSocket.isClosed()) { rtpSocket.close(); rtpSocket = null; }
        if (imageReaderThread != null) { imageReaderThread.quitSafely(); imageReaderThread = null; }
        fragmentBuffers.clear();
    }

    // ===== 编码：YUV → NV21旋转 → JPEG（全在字节层面，不经过 Bitmap）=====

    private byte[] encodeFrame(Image image) {
        Image.Plane[] planes = image.getPlanes();
        int w = image.getWidth();   // 320
        int h = image.getHeight();  // 240

        byte[] nv21 = yuv420ToNv21(planes, w, h);

        // NV21 级别旋转：横屏 320x240 → 竖屏 240x320
        int newW, newH;
        byte[] rotated;
        if (frontCamera) {
            rotated = rotateNV21_270(nv21, w, h); // 前置：270°
            newW = h; newH = w; // 240x320
        } else {
            rotated = rotateNV21_90(nv21, w, h);  // 后置：90°
            newW = h; newH = w; // 240x320
        }

        // JPEG 压缩
        YuvImage yuvImg = new YuvImage(rotated, ImageFormat.NV21, newW, newH, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        yuvImg.compressToJpeg(new Rect(0, 0, newW, newH), JPEG_QUALITY, out);
        byte[] jpeg = out.toByteArray();

        // 如果超过单包限制，降低质量重压
        if (jpeg.length > MAX_RTP_PAYLOAD) {
            out.reset();
            int lowerQ = Math.max(15, JPEG_QUALITY * MAX_RTP_PAYLOAD / jpeg.length);
            yuvImg.compressToJpeg(new Rect(0, 0, newW, newH), lowerQ, out);
            jpeg = out.toByteArray();
        }

        if (framesCaptured <= 3) {
            Log.i(TAG, "[编码] #" + framesCaptured + ": " + jpeg.length + "B ("
                    + newW + "x" + newH + ")");
        }
        return jpeg;
    }

    /** 顺时针旋转 90°: (w,h) → (h,w) */
    private byte[] rotateNV21_90(byte[] nv21, int w, int h) {
        int size = w * h;
        byte[] out = rotatedNv21;

        // Y 平面旋转
        int idx = 0;
        for (int x = 0; x < w; x++) {
            for (int y = h - 1; y >= 0; y--) {
                out[idx++] = nv21[y * w + x];
            }
        }

        // UV 平面旋转 (NV21: VUVU...)
        int uvH = h / 2, uvW = w / 2;
        for (int x = 0; x < uvW; x++) {
            for (int y = uvH - 1; y >= 0; y--) {
                int srcIdx = size + (y * uvW + x) * 2;
                out[idx++] = nv21[srcIdx];     // V
                out[idx++] = nv21[srcIdx + 1]; // U
            }
        }
        return out;
    }

    /** 顺时针旋转 270°（等效逆时针 90°）: (w,h) → (h,w) */
    private byte[] rotateNV21_270(byte[] nv21, int w, int h) {
        int size = w * h;
        byte[] out = rotatedNv21;

        // Y 平面旋转
        int idx = 0;
        for (int x = w - 1; x >= 0; x--) {
            for (int y = 0; y < h; y++) {
                out[idx++] = nv21[y * w + x];
            }
        }

        // UV 平面旋转
        int uvH = h / 2, uvW = w / 2;
        for (int x = uvW - 1; x >= 0; x--) {
            for (int y = 0; y < uvH; y++) {
                int srcIdx = size + (y * uvW + x) * 2;
                out[idx++] = nv21[srcIdx];     // V
                out[idx++] = nv21[srcIdx + 1]; // U
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

        // Y
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

        // VU interleaved (NV21)
        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                int i = row * uvStride + col * uvPix;
                nv21[pos++] = vBuf.get(i);
                nv21[pos++] = uBuf.get(i);
            }
        }
        return nv21;
    }

    // ===== 发送 =====

    private void senderLoop() {
        long interval = 1000 / FPS;
        Log.i(TAG, "[发送] 启动 → " + remoteIp + ":" + remotePort);

        while (running.get()) {
            try {
                byte[] frame;
                synchronized (frameLock) {
                    while (pendingFrame == null && running.get())
                        frameLock.wait(interval);
                    frame = pendingFrame;
                    pendingFrame = null;
                }
                if (frame == null || !running.get()) continue;

                if (frame.length <= MAX_RTP_PAYLOAD) {
                    sendSingle(frame);
                } else {
                    sendFragmented(frame);
                }
                rtpTimestamp += TIMESTAMP_INCREMENT;

                if (framesCaptured <= 5) {
                    Log.i(TAG, "[发送] #" + framesCaptured + ": " + frame.length + "B"
                            + (frame.length <= MAX_RTP_PAYLOAD ? " [单包]" : " [分片]"));
                } else if (packetsSent % 150 == 0) {
                    Log.d(TAG, "[发送] pkts=" + packetsSent + " frames=" + framesCaptured);
                }

                Thread.sleep(interval);
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                if (running.get()) Log.e(TAG, "[发送] err", e);
            }
        }
    }

    private void sendSingle(byte[] data) throws Exception {
        byte[] pkt = new byte[RTP_HEADER_SIZE + data.length];
        writeRtpHeader(pkt, true);
        System.arraycopy(data, 0, pkt, RTP_HEADER_SIZE, data.length);
        rtpSocket.send(new DatagramPacket(pkt, pkt.length, remoteAddr, remotePort));
        packetsSent++;
    }

    private void sendFragmented(byte[] data) throws Exception {
        int off = 0, rem = data.length;
        while (rem > 0) {
            int sz = Math.min(MAX_RTP_PAYLOAD, rem);
            boolean last = (rem == sz);
            byte[] pkt = new byte[RTP_HEADER_SIZE + sz];
            writeRtpHeader(pkt, last);
            System.arraycopy(data, off, pkt, RTP_HEADER_SIZE, sz);
            rtpSocket.send(new DatagramPacket(pkt, pkt.length, remoteAddr, remotePort));
            packetsSent++;
            off += sz;
            rem -= sz;
        }
    }

    private void writeRtpHeader(byte[] pkt, boolean marker) {
        pkt[0] = (byte) 0x80;
        pkt[1] = (byte) ((marker ? 0x80 : 0x00) | RTP_PT);
        pkt[2] = (byte) ((seqNum >> 8) & 0xFF);
        pkt[3] = (byte) (seqNum & 0xFF);
        seqNum = (seqNum + 1) & 0xFFFF;
        long ts = rtpTimestamp;
        pkt[4] = (byte) ((ts >> 24) & 0xFF);
        pkt[5] = (byte) ((ts >> 16) & 0xFF);
        pkt[6] = (byte) ((ts >> 8) & 0xFF);
        pkt[7] = (byte) (ts & 0xFF);
        pkt[8] = (byte) ((ssrc >> 24) & 0xFF);
        pkt[9] = (byte) ((ssrc >> 16) & 0xFF);
        pkt[10] = (byte) ((ssrc >> 8) & 0xFF);
        pkt[11] = (byte) (ssrc & 0xFF);
    }

    // ===== 接收 =====

    private void receiverLoop() {
        byte[] buf = new byte[2048]; // 单包不超过 1412B，2KB 足够
        Log.i(TAG, "[接收] 启动, port=" + localPort);

        while (running.get()) {
            try {
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                rtpSocket.receive(dp);
                packetsReceived++;

                int len = dp.getLength();
                if (len <= RTP_HEADER_SIZE) continue;

                int payLen = len - RTP_HEADER_SIZE;
                boolean marker = (buf[1] & 0x80) != 0;
                long ts = ((long)(buf[4] & 0xFF) << 24) | ((long)(buf[5] & 0xFF) << 16)
                        | ((long)(buf[6] & 0xFF) << 8) | (long)(buf[7] & 0xFF);
                int seq = ((buf[2] & 0xFF) << 8) | (buf[3] & 0xFF);

                byte[] payload = new byte[payLen];
                System.arraycopy(buf, RTP_HEADER_SIZE, payload, 0, payLen);

                // 单包帧（大多数情况）：marker=true 且无之前的分片
                if (marker && !fragmentBuffers.containsKey(ts)) {
                    framesDecoded++;
                    if (isValidJpeg(payload)) {
                        renderJpeg(payload);
                    } else {
                        framesDropped++;
                    }
                    continue;
                }

                // 分片处理
                if (!marker) {
                    fragmentBuffers.computeIfAbsent(ts, k -> new TreeMap<>()).put(seq, payload);
                    // 清理过期
                    if (fragmentBuffers.size() > 8) {
                        Long oldest = null;
                        for (Long k : fragmentBuffers.keySet())
                            if (oldest == null || k < oldest) oldest = k;
                        if (oldest != null && oldest != ts) fragmentBuffers.remove(oldest);
                    }
                    continue;
                }

                // 最后一个分片
                TreeMap<Integer, byte[]> frags = fragmentBuffers.remove(ts);
                framesDecoded++;
                if (frags == null || frags.isEmpty()) {
                    if (isValidJpeg(payload)) renderJpeg(payload);
                    else framesDropped++;
                    continue;
                }

                frags.put(seq, payload);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                for (byte[] f : frags.values()) baos.write(f);
                byte[] jpeg = baos.toByteArray();

                if (isValidJpeg(jpeg)) {
                    renderJpeg(jpeg);
                } else {
                    framesDropped++;
                }

            } catch (java.net.SocketTimeoutException ignored) {
            } catch (Exception e) {
                if (running.get()) Log.e(TAG, "[接收] err", e);
            }
        }
    }

    private boolean isValidJpeg(byte[] d) {
        return d.length >= 4
                && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8
                && (d[d.length - 2] & 0xFF) == 0xFF && (d[d.length - 1] & 0xFF) == 0xD9;
    }

    private void renderJpeg(byte[] jpeg) {
        try {
            Bitmap bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
            if (bmp == null) return;

            SurfaceHolder holder = remoteSurfaceView.getHolder();
            if (holder.getSurface() == null || !holder.getSurface().isValid()) {
                bmp.recycle();
                return;
            }

            Canvas canvas = holder.lockCanvas();
            if (canvas != null) {
                try {
                    canvas.drawBitmap(bmp, null,
                            new Rect(0, 0, canvas.getWidth(), canvas.getHeight()), null);
                    framesRendered++;
                } finally {
                    holder.unlockCanvasAndPost(canvas);
                }
            }
            bmp.recycle();
        } catch (Exception e) {
            if (running.get()) Log.e(TAG, "[渲染] err", e);
        }
    }
}
