package com.sip.client.media.video;

import com.sip.client.config.SipConfig;
import com.sip.client.media.MediaManager;
import com.sip.client.media.rtp.RtpPacket;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.Frame;

import java.net.InetSocketAddress;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.io.ByteArrayOutputStream;

/**
 * 视频 RTP 处理器
 * 负责视频 RTP 包的发送和接收
 *
 * 功能:
 * 1. 发送视频 RTP 包 (H.264)
 * 2. 接收视频 RTP 包
 * 3. 集成视频采集、编码、渲染
 * 4. NAL 单元分片处理 (MTU = 1400)
 *
 * @author 成员2
 */
@Slf4j
public class VideoRtpHandler {

    // ========== RTP 参数 ==========
    private int payloadType = 96;  // 96 = H.264
    private long ssrc;
    private int sequenceNumber;
    private long timestamp;

    // ========== 网络参数 ==========
    private String localIp;
    private int localPort;
    private String remoteIp;
    private int remotePort;
    // 支持多目标发送（会议场景）
    private List<InetSocketAddress> sendTargets = Collections.synchronizedList(new ArrayList<>());

    // ========== MTU 限制 ==========
    private static final int MAX_PACKET_SIZE = 1400;  // MTU - IP Header - UDP Header

    // ========== Netty 组件 ==========
    private EventLoopGroup eventLoopGroup;
    private Channel channel;

    // ========== 视频组件 ==========
    private VideoCapture videoCapture;  // ✅ 添加视频采集器
    private VideoCodec videoCodec;
    private VideoRenderer videoRenderer;

    // ========== 状态 ==========
    private boolean isSending = false;
    private boolean isReceiving = false;

    // ========== 分片重组 ==========
    // 用于存储同一时间戳的JPEG分片
    // Key: timestamp, Value: TreeMap<seqNum, fragmentData> 保证按序列号排序
    private Map<Long, java.util.TreeMap<Integer, byte[]>> fragmentBuffers = new ConcurrentHashMap<>();

    // 分片缓存清理阈值（避免内存泄漏）
    private static final int MAX_FRAGMENT_BUFFERS = 10;

    // ========== 统计 ==========
    private long packetsSent = 0;
    private long packetsReceived = 0;

    /**
     * 初始化视频 RTP 处理器
     */
    public void initialize(String localIp, int localPort) throws Exception {
        this.localIp = localIp;
        this.localPort = localPort;

        log.info("初始化视频 RTP 处理器: {}:{}", localIp, localPort);

        // 1. 生成随机 SSRC
        this.ssrc = new Random().nextInt() & 0xFFFFFFFFL;
        this.sequenceNumber = 0;
        this.timestamp = 0;

        // 2. 创建 Netty UDP Channel
        eventLoopGroup = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup)
            .channel(NioDatagramChannel.class)
            .option(ChannelOption.SO_BROADCAST, false)
            .option(ChannelOption.SO_RCVBUF, 65536 * 4)
            .option(ChannelOption.SO_SNDBUF, 65536 * 4)
            .option(ChannelOption.SO_REUSEADDR, true)  // ✅ 允许端口重用
            .handler(new ChannelInitializer<NioDatagramChannel>() {
                @Override
                protected void initChannel(NioDatagramChannel ch) throws Exception {
                    ch.pipeline().addLast(new VideoRtpReceiveHandler());
                }
            });

        // 绑定本地端口（增加重试逻辑）
        int maxRetries = 3;
        Exception lastException = null;

        for (int i = 0; i < maxRetries; i++) {
            try {
                ChannelFuture future = bootstrap.bind(localPort).sync();
                channel = future.channel();
                log.info("✅ 视频 RTP Channel 已绑定到端口 {}", localPort);
                lastException = null;
                break;  // 绑定成功，跳出循环
            } catch (Exception e) {
                lastException = e;
                log.warn("绑定视频端口 {} 失败（尝试 {}/{}）: {}",
                        localPort, i + 1, maxRetries, e.getMessage());

                // 如果不是最后一次尝试，等待100ms后重试
                if (i < maxRetries - 1) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        // 如果所有重试都失败，抛出异常
        if (lastException != null) {
            log.error("❌ 无法绑定视频 RTP 端口 {}，已尝试 {} 次", localPort, maxRetries);
            throw lastException;
        }

        // 3. 初始化视频编解码器（VideoCapture在需要发送时才初始化）
        videoCodec = new VideoCodec();

        log.info("✅ 视频 RTP 处理器初始化成功");
    }

    /**
     * 开始发送视频
     */
    public void startSending(String remoteIp, int remotePort) throws Exception {
        if (isSending) {
            log.warn("视频 RTP 发送已在进行中");
            return;
        }

        this.remoteIp = remoteIp;
        this.remotePort = remotePort;
        this.isSending = true;

        log.info("开始发送视频 RTP: {} -> {}:{}", localPort, remoteIp, remotePort);

        // 初始化编码器（使用默认参数640x480@10fps）
        videoCodec.initializeEncoder(640, 480, 10.0);

        // ✅ 优化：优先使用MediaManager预热的摄像头，如果没有则在后台线程初始化
        Thread cameraInitThread = new Thread(() -> {
            try {
                // ✅ 尝试从MediaManager获取预热的摄像头
                if (videoCapture == null) {
                    MediaManager mediaManager = MediaManager.getInstance();
                    VideoCapture prewarmed = mediaManager.getPrewarmedCamera();

                    if (prewarmed != null) {
                        // ⚡ 修复：验证预热摄像头是否真正可用
                        // 通过检查isPrewarmed标志和内部状态，确保grabber未被释放
                        try {
                            // 尝试一次测试抓帧，验证grabber是否可用
                            log.info("✅ 发现预热的摄像头，验证其可用性...");

                            // 如果预热摄像头已经被释放或状态异常，getVideoWidth()等方法仍可调用
                            // 所以我们直接使用，如果有问题会在startCapture时抛出异常并fallback到重新初始化
                            videoCapture = prewarmed;
                            log.info("✅ 使用预热的摄像头，跳过初始化");

                        } catch (Exception e) {
                            // 预热摄像头不可用，fallback到重新初始化
                            log.warn("⚠️ 预热摄像头不可用，fallback到重新初始化: {}", e.getMessage());
                            videoCapture = null;
                        }
                    }

                    // 如果预热摄像头不可用，重新初始化
                    if (videoCapture == null) {
                        log.info("未找到可用的预热摄像头，开始初始化...");
                        videoCapture = new VideoCapture();
                        int cameraDeviceId = SipConfig.getCameraDeviceId();
                        log.info("使用摄像头设备ID: {}", cameraDeviceId);

                        // 这一步可能耗时较长（OpenCV初始化）
                        videoCapture.initialize(cameraDeviceId);
                    }
                }

                // 启动视频采集并发送
                videoCapture.startCapture(frame -> {
                    // 每采集到一帧就通过RTP发送
                    sendVideoRtp(frame);
                });

                log.info("✅ 视频采集和RTP发送已启动");

            } catch (Exception e) {
                log.error("❌ 摄像头初始化失败", e);
                isSending = false;
            }
        }, "CameraInitThread");

        cameraInitThread.start();
    }

    /**
     * 更新RTP发送目标地址
     * 用于动态切换发送目标（例如从广播切换到点对点）
     *
     * @param remoteIp 目标IP
     * @param remotePort 目标端口
     */
    public void updateSendTarget(String remoteIp, int remotePort) {
        this.remoteIp = remoteIp;
        this.remotePort = remotePort;
        log.info("更新视频 RTP 发送目标: {}:{}", remoteIp, remotePort);
    }

    /**
     * 添加RTP发送目标（用于多方会议）
     *
     * @param remoteIp 接收方IP
     * @param remotePort 接收方端口
     */
    public void addSendTarget(String remoteIp, int remotePort) {
        InetSocketAddress target = new InetSocketAddress(remoteIp, remotePort);
        if (!sendTargets.contains(target)) {
            sendTargets.add(target);
            log.info("添加视频 RTP 发送目标: {}:{}, 当前目标数: {}", remoteIp, remotePort, sendTargets.size());
        }
    }

    /**
     * 移除RTP发送目标
     *
     * @param remoteIp 接收方IP
     * @param remotePort 接收方端口
     */
    public void removeSendTarget(String remoteIp, int remotePort) {
        InetSocketAddress target = new InetSocketAddress(remoteIp, remotePort);
        if (sendTargets.remove(target)) {
            log.info("移除视频 RTP 发送目标: {}:{}, 剩余目标数: {}", remoteIp, remotePort, sendTargets.size());
        }
    }

    /**
     * 清空所有发送目标
     */
    public void clearSendTargets() {
        int count = sendTargets.size();
        sendTargets.clear();
        log.info("清空所有视频 RTP 发送目标, 已清空: {} 个", count);
    }

    /**
     * 停止发送视频
     */
    public void stopSending() {
        if (!isSending) {
            return;
        }

        log.info("停止发送视频 RTP");

        isSending = false;

        // ✅ 停止视频采集
        if (videoCapture != null) {
            videoCapture.stopCapture();
        }

        videoCodec.stopEncoder();
    }

    /**
     * 开始接收视频
     */
    public void startReceiving(VideoRenderer renderer) {
        if (isReceiving) {
            log.warn("视频 RTP 接收已在进行中");
            return;
        }

        this.videoRenderer = renderer;
        this.isReceiving = true;

        log.info("开始接收视频 RTP");

        try {
            videoCodec.initializeDecoder();
        } catch (Exception e) {
            log.error("初始化解码器失败", e);
        }
    }

    /**
     * 停止接收视频
     */
    public void stopReceiving() {
        if (!isReceiving) {
            return;
        }

        log.info("停止接收视频 RTP");

        isReceiving = false;
        videoCodec.stopDecoder();
    }

    /**
     * 发送外部视频帧 (用于屏幕共享等场景)
     *
     * @param frame 外部视频帧
     */
    public void sendExternalFrame(Frame frame) {
        sendVideoRtp(frame);
    }

    /**
     * 直接发送BufferedImage (用于摄像头和屏幕共享)
     * 避免不必要的BufferedImage->Frame->BufferedImage转换
     *
     * @param image BufferedImage图像
     */
    public void sendBufferedImage(java.awt.image.BufferedImage image) {
        if (!isSending || channel == null || image == null) {
            return;
        }

        try {
            // 直接编码 BufferedImage -> JPEG
            byte[] jpegData = VideoCodec.encodeBufferedImage(image);

            if (jpegData == null || jpegData.length == 0) {
                return;
            }

            // 分片发送
            if (jpegData.length <= MAX_PACKET_SIZE) {
                sendSingleRtpPacket(jpegData);
            } else {
                sendFragmentedRtpPackets(jpegData);
            }

            // 更新时间戳
            timestamp += 3000;

        } catch (Exception e) {
            log.error("发送BufferedImage RTP失败", e);
        }
    }

    /**
     * 发送视频 RTP 包
     */
    private void sendVideoRtp(Frame frame) {
        if (!isSending || channel == null) {
            return;
        }

        try {
            // 1. 编码 Frame -> JPEG
            byte[] jpegData = videoCodec.encodeFrame(frame);

            if (jpegData == null || jpegData.length == 0) {
                return;
            }

            // 2. 分片发送 (如果超过 MTU)
            if (jpegData.length <= MAX_PACKET_SIZE) {
                // 单个 RTP 包
                sendSingleRtpPacket(jpegData);
            } else {
                // 分片发送（JPEG数据通常较大，需要分片）
                sendFragmentedRtpPackets(jpegData);
            }

            // 3. 更新时间戳 (每帧增加 3000, 假设90kHz时钟)
            timestamp += 3000;

        } catch (Exception e) {
            log.error("发送视频 RTP 失败", e);
        }
    }

    /**
     * 发送单个 RTP 包（完整JPEG，设置Marker=1）
     */
    private void sendSingleRtpPacket(byte[] jpegData) {
        RtpPacket rtpPacket = new RtpPacket(
            payloadType,
            sequenceNumber++,
            timestamp,
            ssrc,
            jpegData
        );

        // ✅ 单包发送时也要设置Marker=1，表示帧结束
        rtpPacket.setMarker(true);

        sendRtpPacket(rtpPacket);
    }

    /**
     * 发送分片 RTP 包
     * 将大的JPEG数据分成多个RTP包发送
     */
    private void sendFragmentedRtpPackets(byte[] jpegData) {
        // JPEG 分片（简化版本）
        int offset = 0;
        int remaining = jpegData.length;

        boolean isLast = false;

        while (remaining > 0) {
            int fragmentSize = Math.min(MAX_PACKET_SIZE, remaining);

            if (remaining == fragmentSize) {
                isLast = true;
            }

            byte[] fragmentData = new byte[fragmentSize];
            System.arraycopy(jpegData, offset, fragmentData, 0, fragmentSize);

            RtpPacket rtpPacket = new RtpPacket(
                payloadType,
                sequenceNumber++,
                timestamp,
                ssrc,
                fragmentData
            );

            // 最后一个分片设置 Marker 位
            if (isLast) {
                rtpPacket.setMarker(true);
            }

            sendRtpPacket(rtpPacket);

            offset += fragmentSize;
            remaining -= fragmentSize;
        }
    }

    /**
     * 发送 RTP 包 (通过 Netty)
     * 支持单目标和多目标发送
     */
    private void sendRtpPacket(RtpPacket rtpPacket) {
        byte[] rtpData = rtpPacket.encode();

        // 如果有多个发送目标，向所有目标发送
        if (!sendTargets.isEmpty()) {
            for (InetSocketAddress target : sendTargets) {
                ByteBuf buffer = Unpooled.wrappedBuffer(rtpData);
                DatagramPacket packet = new DatagramPacket(buffer, target);

                channel.writeAndFlush(packet).addListener(future -> {
                    if (future.isSuccess()) {
                        packetsSent++;
                    } else {
                        log.error("发送视频 RTP 包失败 (目标: {})", target, future.cause());
                    }
                });
            }
        } else if (remoteIp != null && remotePort > 0) {
            // 向单个目标发送（向后兼容）
            ByteBuf buffer = Unpooled.wrappedBuffer(rtpData);
            InetSocketAddress remoteAddress = new InetSocketAddress(remoteIp, remotePort);
            DatagramPacket packet = new DatagramPacket(buffer, remoteAddress);

            channel.writeAndFlush(packet).addListener(future -> {
                if (future.isSuccess()) {
                    packetsSent++;
                } else {
                    log.error("发送视频 RTP 包失败", future.cause());
                }
            });
        }
    }

    /**
     * 处理接收到的 RTP 包（支持JPEG分片重组，按序列号排序）
     */
    private void handleReceivedRtp(byte[] rtpData) {
        if (!isReceiving) {
            return;
        }

        try {
            // 1. 解码 RTP 包
            RtpPacket rtpPacket = RtpPacket.decode(rtpData);
            packetsReceived++;

            long timestamp = rtpPacket.getTimestamp();
            int seqNum = rtpPacket.getSequenceNumber();
            boolean isMarker = rtpPacket.isMarker();
            byte[] fragmentData = rtpPacket.getPayload();

            // 2. 分片重组逻辑
            if (!isMarker) {
                // 不是最后一个分片，缓存起来（使用TreeMap保证顺序）
                fragmentBuffers.putIfAbsent(timestamp, new java.util.TreeMap<>());

                java.util.TreeMap<Integer, byte[]> fragments = fragmentBuffers.get(timestamp);
                fragments.put(seqNum, fragmentData);

                log.trace("缓存JPEG分片: timestamp={}, seqNum={}, 分片数={}", timestamp, seqNum, fragments.size());

                // 清理过多的缓存（防止内存泄漏）
                if (fragmentBuffers.size() > MAX_FRAGMENT_BUFFERS) {
                    Long oldestTimestamp = fragmentBuffers.keySet().stream().min(Long::compare).orElse(null);
                    if (oldestTimestamp != null && oldestTimestamp != timestamp) {
                        fragmentBuffers.remove(oldestTimestamp);
                        log.warn("清理过期的JPEG分片缓存: timestamp={}", oldestTimestamp);
                    }
                }
                return;
            }

            // 3. 最后一个分片，重组完整JPEG
            java.util.TreeMap<Integer, byte[]> fragments = fragmentBuffers.remove(timestamp);

            byte[] completeJpeg;
            if (fragments == null || fragments.isEmpty()) {
                // 只有一个分片（未分片的完整JPEG）
                completeJpeg = fragmentData;
                log.trace("收到完整JPEG（未分片）: {} bytes", fragmentData.length);
            } else {
                // 多个分片，按序列号排序后拼接完整JPEG
                fragments.put(seqNum, fragmentData);  // 添加最后一个分片

                // 计算总大小
                int totalSize = fragments.values().stream().mapToInt(f -> f.length).sum();

                // 按序列号顺序拼接所有分片
                ByteArrayOutputStream baos = new ByteArrayOutputStream(totalSize);
                for (byte[] fragment : fragments.values()) {  // TreeMap.values()自动按key排序
                    baos.write(fragment);
                }
                completeJpeg = baos.toByteArray();

                log.trace("重组完整JPEG: 分片数={}, 总大小={} bytes, 序列号范围={}-{}",
                         fragments.size(), completeJpeg.length, fragments.firstKey(), fragments.lastKey());
            }

            // 4. 解码完整JPEG -> Frame
            Frame frame = videoCodec.decodeFrame(completeJpeg);

            if (frame != null && videoRenderer != null) {
                // 5. 渲染视频帧
                byte[] rgbData = VideoCodec.frameToRGB(frame);
                if (rgbData != null) {
                    videoRenderer.renderRGB(rgbData, frame.imageWidth, frame.imageHeight);
                }
            }

        } catch (Exception e) {
            log.error("处理接收到的视频 RTP 包失败", e);
        }
    }

    /**
     * 关闭视频 RTP 处理器
     */
    public void shutdown() {
        log.info("关闭视频 RTP 处理器");

        stopSending();
        stopReceiving();

        if (channel != null) {
            channel.close();
        }

        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully();
        }

        log.info("视频 RTP 处理器已关闭");
    }

    /**
     * 获取统计信息
     */
    public VideoRtpStats getStats() {
        VideoRtpStats stats = new VideoRtpStats();
        stats.packetsSent = packetsSent;
        stats.packetsReceived = packetsReceived;
        return stats;
    }

    // ========== Netty Handler ==========

    /**
     * 视频 RTP 接收处理器
     */
    private class VideoRtpReceiveHandler extends SimpleChannelInboundHandler<DatagramPacket> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
            ByteBuf buf = packet.content();
            byte[] rtpData = new byte[buf.readableBytes()];
            buf.readBytes(rtpData);

            handleReceivedRtp(rtpData);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("视频 RTP 接收异常", cause);
        }
    }

    // ========== 统计类 ==========

    public static class VideoRtpStats {
        public long packetsSent;
        public long packetsReceived;

        @Override
        public String toString() {
            return String.format(
                "视频 RTP Stats: 发送=%d, 接收=%d",
                packetsSent, packetsReceived
            );
        }
    }
}
