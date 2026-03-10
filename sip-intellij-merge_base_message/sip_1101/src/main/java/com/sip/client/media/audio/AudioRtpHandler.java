package com.sip.client.media.audio;

import com.sip.client.media.rtp.RtpPacket;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Random;

/**
 * 音频 RTP 处理器
 * 负责音频 RTP 包的发送和接收
 *
 * 功能:
 * 1. 发送音频 RTP 包 (通过 Netty UDP)
 * 2. 接收音频 RTP 包 (通过 Netty UDP)
 * 3. 集成音频采集、编码、播放、解码
 * 4. RTCP 统计 (发送/接收包数、丢包率等)
 *
 * @author 成员2
 */
@Slf4j
public class AudioRtpHandler {

    // ========== RTP 参数 ==========
    private int payloadType = 0;  // 0 = PCMU (G.711 μ-law)
    private long ssrc;  // Synchronization Source
    private int sequenceNumber;  // 序列号 (递增)
    private long timestamp;  // 时间戳 (递增)

    // ========== 网络参数 ==========
    private String localIp;
    private int localPort;
    private String remoteIp;
    private int remotePort;

    // ========== Netty 组件 ==========
    private EventLoopGroup eventLoopGroup;
    private Channel channel;

    // ========== 音频组件 ==========
    private AudioCapture audioCapture;
    private AudioPlayer audioPlayer;

    // ========== 状态 ==========
    private boolean isSending = false;
    private boolean isReceiving = false;

    // ========== RTCP 统计 ==========
    private long packetsSent = 0;
    private long packetsReceived = 0;
    private long packetsLost = 0;
    private int lastReceivedSeq = -1;

    /**
     * 初始化音频 RTP 处理器
     */
    public void initialize(String localIp, int localPort) throws Exception {
        this.localIp = localIp;
        this.localPort = localPort;

        log.info("初始化音频 RTP 处理器: {}:{}", localIp, localPort);

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
            .option(ChannelOption.SO_RCVBUF, 65536)
            .option(ChannelOption.SO_SNDBUF, 65536)
            .option(ChannelOption.SO_REUSEADDR, true)  // ✅ 允许端口重用
            .handler(new ChannelInitializer<NioDatagramChannel>() {
                @Override
                protected void initChannel(NioDatagramChannel ch) throws Exception {
                    ch.pipeline().addLast(new RtpReceiveHandler());
                }
            });

        // 绑定本地端口（增加重试逻辑）
        int maxRetries = 3;
        Exception lastException = null;

        for (int i = 0; i < maxRetries; i++) {
            try {
                ChannelFuture future = bootstrap.bind(localPort).sync();
                channel = future.channel();
                log.info("✅ 音频 RTP Channel 已绑定到端口 {}", localPort);
                lastException = null;
                break;  // 绑定成功，跳出循环
            } catch (Exception e) {
                lastException = e;
                log.warn("绑定音频端口 {} 失败（尝试 {}/{}）: {}",
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
            log.error("❌ 无法绑定音频 RTP 端口 {}，已尝试 {} 次", localPort, maxRetries);
            throw lastException;
        }

        // 3. 初始化音频采集和播放
        audioCapture = new AudioCapture();
        audioCapture.initialize();

        audioPlayer = new AudioPlayer();
        audioPlayer.initialize();

        log.info("✅ 音频 RTP 处理器初始化成功");
    }

    /**
     * 开始发送音频
     */
    public void startSending(String remoteIp, int remotePort) {
        if (isSending) {
            log.warn("音频 RTP 发送已在进行中");
            return;
        }

        this.remoteIp = remoteIp;
        this.remotePort = remotePort;
        this.isSending = true;

        log.info("开始发送音频 RTP: {} -> {}:{}", localPort, remoteIp, remotePort);

        // 开始音频采集
        audioCapture.startCapture(audioData -> {
            try {
                sendAudioRtp(audioData);
            } catch (Exception e) {
                log.error("发送音频 RTP 失败", e);
            }
        });
    }

    /**
     * 停止发送音频
     */
    public void stopSending() {
        if (!isSending) {
            return;
        }

        log.info("停止发送音频 RTP");

        isSending = false;
        audioCapture.stopCapture();
    }

    /**
     * 开始接收音频
     */
    public void startReceiving() {
        if (isReceiving) {
            log.warn("音频 RTP 接收已在进行中");
            return;
        }

        this.isReceiving = true;

        log.info("开始接收音频 RTP");

        // 开始音频播放
        audioPlayer.startPlayback();
    }

    /**
     * 停止接收音频
     */
    public void stopReceiving() {
        if (!isReceiving) {
            return;
        }

        log.info("停止接收音频 RTP");

        isReceiving = false;
        audioPlayer.stopPlayback();
    }

    /**
     * 发送音频 RTP 包
     */
    private void sendAudioRtp(byte[] pcmData) {
        if (!isSending || channel == null) {
            return;
        }

        try {
            // 1. 编码 PCM -> G.711 μ-law
            byte[] ulawData = AudioCodec.encodePCMU(pcmData);

            // 2. 创建 RTP 包
            RtpPacket rtpPacket = new RtpPacket(
                payloadType,
                sequenceNumber++,
                timestamp,
                ssrc,
                ulawData
            );

            // 3. 更新时间戳 (每 20ms 增加 160, 因为采样率是 8000Hz)
            timestamp += 160;

            // 4. 编码 RTP 包
            byte[] rtpData = rtpPacket.encode();

            // 5. 发送 UDP 包
            ByteBuf buffer = Unpooled.wrappedBuffer(rtpData);
            InetSocketAddress remoteAddress = new InetSocketAddress(remoteIp, remotePort);
            DatagramPacket packet = new DatagramPacket(buffer, remoteAddress);

            channel.writeAndFlush(packet).addListener(future -> {
                if (future.isSuccess()) {
                    packetsSent++;
                } else {
                    log.error("发送 RTP 包失败", future.cause());
                }
            });

        } catch (Exception e) {
            log.error("发送音频 RTP 失败", e);
        }
    }

    /**
     * 处理接收到的 RTP 包
     */
    private void handleReceivedRtp(byte[] rtpData) {
        if (!isReceiving) {
            return;
        }

        try {
            // 1. 解码 RTP 包
            RtpPacket rtpPacket = RtpPacket.decode(rtpData);

            packetsReceived++;

            // 2. 检测丢包
            int currentSeq = rtpPacket.getSequenceNumber();
            if (lastReceivedSeq >= 0) {
                int expectedSeq = (lastReceivedSeq + 1) & 0xFFFF;
                if (currentSeq != expectedSeq) {
                    int lost = (currentSeq - expectedSeq + 65536) % 65536;
                    packetsLost += lost;
                    log.debug("检测到丢包: 期望 {}, 实际 {}, 丢失 {}", expectedSeq, currentSeq, lost);
                }
            }
            lastReceivedSeq = currentSeq;

            // 3. 获取 Payload (G.711 μ-law 数据)
            byte[] ulawData = rtpPacket.getPayload();

            // 4. 解码 G.711 -> PCM
            byte[] pcmData = AudioCodec.decodePCMU(ulawData);

            // 5. 播放音频
            audioPlayer.playAudio(pcmData);

        } catch (Exception e) {
            log.error("处理接收到的 RTP 包失败", e);
        }
    }

    /**
     * 关闭音频 RTP 处理器
     */
    public void shutdown() {
        log.info("关闭音频 RTP 处理器");

        stopSending();
        stopReceiving();

        if (channel != null) {
            channel.close();
        }

        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully();
        }

        log.info("音频 RTP 处理器已关闭");
    }

    /**
     * 获取 RTCP 统计信息
     */
    public RtcpStats getRtcpStats() {
        RtcpStats stats = new RtcpStats();
        stats.packetsSent = packetsSent;
        stats.packetsReceived = packetsReceived;
        stats.packetsLost = packetsLost;

        if (packetsReceived > 0) {
            stats.lossRate = (double) packetsLost / (packetsReceived + packetsLost) * 100;
        }

        stats.jitterBufferSize = audioPlayer.getJitterBufferSize();

        return stats;
    }

    // ========== Netty Handler ==========

    /**
     * RTP 接收处理器
     */
    private class RtpReceiveHandler extends SimpleChannelInboundHandler<DatagramPacket> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
            ByteBuf buf = packet.content();
            byte[] rtpData = new byte[buf.readableBytes()];
            buf.readBytes(rtpData);

            // 处理接收到的 RTP 包
            handleReceivedRtp(rtpData);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("RTP 接收异常", cause);
        }
    }

    // ========== RTCP 统计类 ==========

    public static class RtcpStats {
        public long packetsSent;
        public long packetsReceived;
        public long packetsLost;
        public double lossRate;  // 丢包率 (%)
        public int jitterBufferSize;

        @Override
        public String toString() {
            return String.format(
                "RTCP Stats: 发送=%d, 接收=%d, 丢失=%d, 丢包率=%.2f%%, Jitter Buffer=%d",
                packetsSent, packetsReceived, packetsLost, lossRate, jitterBufferSize
            );
        }
    }
}
