package com.sip.client.conference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多方 SDP 协商器 (Multi-party SDP Negotiator)
 *
 * 负责处理会议中的多方 SDP 协商：
 * 1. 解析参与者的 SDP Offer
 * 2. 生成焦点服务器的 SDP Answer
 * 3. 协商媒体参数（编解码器、传输地址等）
 * 4. 管理多路媒体流的端口分配
 *
 * @author SIP Team - Member 4
 * @version 1.0
 */
@Component
public class MultiSdpNegotiator {

    private static final Logger log = LoggerFactory.getLogger(MultiSdpNegotiator.class);

    /**
     * 会议ID到媒体会话的映射
     * Key: conferenceId, Value: ConferenceMediaSession
     */
    private final Map<Long, ConferenceMediaSession> conferenceSessions = new ConcurrentHashMap<>();

    /**
     * 参与者到媒体信息的映射
     * Key: participantUri, Value: ParticipantMediaInfo
     */
    private final Map<String, ParticipantMediaInfo> participantMedia = new ConcurrentHashMap<>();

    /**
     * 焦点服务器的RTP端口池
     */
    private int nextRtpPort = 20000;

    /**
     * 支持的音频编解码器列表（按优先级）
     */
    private static final List<String> SUPPORTED_AUDIO_CODECS = Arrays.asList(
        "PCMU/8000",      // G.711 μ-law
        "PCMA/8000",      // G.711 A-law
        "opus/48000/2",   // Opus
        "G722/8000"       // G.722
    );

    /**
     * 支持的视频编解码器列表（按优先级）
     */
    private static final List<String> SUPPORTED_VIDEO_CODECS = Arrays.asList(
        "H264/90000",     // H.264
        "VP8/90000",      // VP8
        "VP9/90000"       // VP9
    );

    /**
     * 焦点服务器的媒体地址
     */
    private static final String FOCUS_MEDIA_IP = "192.168.1.100"; // TODO: 从配置文件读取

    /**
     * 协商 SDP
     *
     * @param conferenceId 会议ID
     * @param participantUri 参与者URI
     * @param sdpOffer 参与者的SDP Offer
     * @return 焦点服务器的SDP Answer，失败返回null
     */
    public String negotiateSdp(Long conferenceId, String participantUri, String sdpOffer) {
        try {
            log.info("开始协商 SDP: conferenceId={}, participant={}", conferenceId, participantUri);

            // 解析 SDP Offer
            SdpMessage offer = parseSdp(sdpOffer);
            if (offer == null) {
                log.error("解析 SDP Offer 失败");
                return null;
            }

            // 获取或创建会议媒体会话
            ConferenceMediaSession mediaSession = conferenceSessions.computeIfAbsent(
                conferenceId,
                id -> new ConferenceMediaSession(id)
            );

            // 协商音频
            MediaDescription audioDesc = negotiateAudio(offer.getAudioDescription());
            if (audioDesc == null) {
                log.error("音频协商失败");
                return null;
            }

            // 协商视频（可选）
            MediaDescription videoDesc = null;
            if (offer.getVideoDescription() != null) {
                videoDesc = negotiateVideo(offer.getVideoDescription());
            }

            // 分配RTP端口
            int audioRtpPort = allocateRtpPort();
            int videoRtpPort = videoDesc != null ? allocateRtpPort() : 0;

            // 保存参与者媒体信息
            ParticipantMediaInfo mediaInfo = new ParticipantMediaInfo(
                participantUri,
                offer.getConnectionAddress(),
                offer.getAudioDescription().getPort(),
                offer.getVideoDescription() != null ? offer.getVideoDescription().getPort() : 0,
                audioRtpPort,
                videoRtpPort
            );
            participantMedia.put(participantUri, mediaInfo);
            mediaSession.addParticipant(mediaInfo);

            // 生成 SDP Answer
            String sdpAnswer = generateSdpAnswer(offer, audioDesc, videoDesc, audioRtpPort, videoRtpPort);

            log.info("SDP 协商成功: participant={}", participantUri);
            log.debug("SDP Answer:\n{}", sdpAnswer);

            return sdpAnswer;

        } catch (Exception e) {
            log.error("SDP 协商异常", e);
            return null;
        }
    }

    /**
     * 移除参与者的媒体信息
     *
     * @param conferenceId 会议ID
     * @param participantUri 参与者URI
     */
    public void removeParticipant(Long conferenceId, String participantUri) {
        log.info("移除参与者媒体信息: conferenceId={}, participant={}", conferenceId, participantUri);

        ParticipantMediaInfo mediaInfo = participantMedia.remove(participantUri);
        if (mediaInfo != null) {
            // 回收端口
            releaseRtpPort(mediaInfo.getAudioRtpPort());
            if (mediaInfo.getVideoRtpPort() > 0) {
                releaseRtpPort(mediaInfo.getVideoRtpPort());
            }
        }

        ConferenceMediaSession session = conferenceSessions.get(conferenceId);
        if (session != null) {
            session.removeParticipant(participantUri);
            if (session.isEmpty()) {
                conferenceSessions.remove(conferenceId);
            }
        }
    }

    /**
     * 协商音频媒体
     */
    private MediaDescription negotiateAudio(MediaDescription offerAudio) {
        if (offerAudio == null || offerAudio.getCodecs().isEmpty()) {
            return null;
        }

        // 找到第一个支持的编解码器
        for (String supportedCodec : SUPPORTED_AUDIO_CODECS) {
            for (String offerCodec : offerAudio.getCodecs()) {
                if (offerCodec.equalsIgnoreCase(supportedCodec)) {
                    MediaDescription answer = new MediaDescription("audio");
                    answer.addCodec(offerCodec);
                    answer.setPayloadType(offerAudio.getPayloadType());
                    log.debug("音频协商成功: codec={}", offerCodec);
                    return answer;
                }
            }
        }

        log.warn("没有找到支持的音频编解码器");
        return null;
    }

    /**
     * 协商视频媒体
     */
    private MediaDescription negotiateVideo(MediaDescription offerVideo) {
        if (offerVideo == null || offerVideo.getCodecs().isEmpty()) {
            return null;
        }

        // 找到第一个支持的编解码器
        for (String supportedCodec : SUPPORTED_VIDEO_CODECS) {
            for (String offerCodec : offerVideo.getCodecs()) {
                if (offerCodec.equalsIgnoreCase(supportedCodec)) {
                    MediaDescription answer = new MediaDescription("video");
                    answer.addCodec(offerCodec);
                    answer.setPayloadType(offerVideo.getPayloadType());
                    log.debug("视频协商成功: codec={}", offerCodec);
                    return answer;
                }
            }
        }

        log.warn("没有找到支持的视频编解码器");
        return null;
    }

    /**
     * 生成 SDP Answer
     */
    private String generateSdpAnswer(SdpMessage offer, MediaDescription audioDesc,
                                     MediaDescription videoDesc, int audioRtpPort, int videoRtpPort) {
        StringBuilder sdp = new StringBuilder();

        // v= (version)
        sdp.append("v=0\r\n");

        // o= (origin)
        sdp.append(String.format("o=FocusServer %d %d IN IP4 %s\r\n",
            offer.getSessionId(), offer.getSessionVersion(), FOCUS_MEDIA_IP));

        // s= (session name)
        sdp.append("s=SIP Conference\r\n");

        // c= (connection)
        sdp.append(String.format("c=IN IP4 %s\r\n", FOCUS_MEDIA_IP));

        // t= (time)
        sdp.append("t=0 0\r\n");

        // 音频 m= (media)
        sdp.append(String.format("m=audio %d RTP/AVP %d\r\n",
            audioRtpPort, audioDesc.getPayloadType()));
        sdp.append(String.format("a=rtpmap:%d %s\r\n",
            audioDesc.getPayloadType(), audioDesc.getCodecs().get(0)));
        sdp.append("a=sendrecv\r\n");

        // 视频 m= (media) - 可选
        if (videoDesc != null && videoRtpPort > 0) {
            sdp.append(String.format("m=video %d RTP/AVP %d\r\n",
                videoRtpPort, videoDesc.getPayloadType()));
            sdp.append(String.format("a=rtpmap:%d %s\r\n",
                videoDesc.getPayloadType(), videoDesc.getCodecs().get(0)));
            sdp.append("a=sendrecv\r\n");
        }

        return sdp.toString();
    }

    /**
     * 解析 SDP 消息
     */
    private SdpMessage parseSdp(String sdp) {
        try {
            SdpMessage message = new SdpMessage();

            String[] lines = sdp.split("\r\n");
            MediaDescription currentMedia = null;

            for (String line : lines) {
                if (line.startsWith("o=")) {
                    // o=username sess-id sess-version nettype addrtype unicast-address
                    String[] parts = line.substring(2).split(" ");
                    if (parts.length >= 3) {
                        message.setSessionId(Long.parseLong(parts[1]));
                        message.setSessionVersion(Long.parseLong(parts[2]));
                    }
                } else if (line.startsWith("c=")) {
                    // c=IN IP4 192.168.1.100
                    String[] parts = line.substring(2).split(" ");
                    if (parts.length >= 3) {
                        message.setConnectionAddress(parts[2]);
                    }
                } else if (line.startsWith("m=")) {
                    // m=audio 5004 RTP/AVP 0
                    String[] parts = line.substring(2).split(" ");
                    if (parts.length >= 4) {
                        String mediaType = parts[0];
                        int port = Integer.parseInt(parts[1]);
                        int payloadType = Integer.parseInt(parts[3]);

                        currentMedia = new MediaDescription(mediaType);
                        currentMedia.setPort(port);
                        currentMedia.setPayloadType(payloadType);

                        if ("audio".equals(mediaType)) {
                            message.setAudioDescription(currentMedia);
                        } else if ("video".equals(mediaType)) {
                            message.setVideoDescription(currentMedia);
                        }
                    }
                } else if (line.startsWith("a=rtpmap:") && currentMedia != null) {
                    // a=rtpmap:0 PCMU/8000
                    Pattern pattern = Pattern.compile("a=rtpmap:\\d+ (.+)");
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        currentMedia.addCodec(matcher.group(1));
                    }
                }
            }

            return message;

        } catch (Exception e) {
            log.error("解析 SDP 失败", e);
            return null;
        }
    }

    /**
     * 分配 RTP 端口（偶数端口）
     */
    private synchronized int allocateRtpPort() {
        int port = nextRtpPort;
        nextRtpPort += 2; // RTP用偶数端口，RTCP用奇数端口
        if (nextRtpPort > 65000) {
            nextRtpPort = 20000; // 循环使用
        }
        log.debug("分配 RTP 端口: {}", port);
        return port;
    }

    /**
     * 释放 RTP 端口
     */
    private void releaseRtpPort(int port) {
        log.debug("释放 RTP 端口: {}", port);
        // TODO: 实现端口池管理，允许端口重用
    }

    /**
     * 获取参与者的媒体信息
     */
    public ParticipantMediaInfo getParticipantMediaInfo(String participantUri) {
        return participantMedia.get(participantUri);
    }

    /**
     * 获取会议的所有参与者媒体信息
     */
    public List<ParticipantMediaInfo> getConferenceParticipants(Long conferenceId) {
        ConferenceMediaSession session = conferenceSessions.get(conferenceId);
        return session != null ? new ArrayList<>(session.getParticipants()) : new ArrayList<>();
    }

    // ==================== 内部类 ====================

    /**
     * SDP 消息类
     */
    private static class SdpMessage {
        private long sessionId;
        private long sessionVersion;
        private String connectionAddress;
        private MediaDescription audioDescription;
        private MediaDescription videoDescription;

        public long getSessionId() { return sessionId; }
        public void setSessionId(long sessionId) { this.sessionId = sessionId; }

        public long getSessionVersion() { return sessionVersion; }
        public void setSessionVersion(long sessionVersion) { this.sessionVersion = sessionVersion; }

        public String getConnectionAddress() { return connectionAddress; }
        public void setConnectionAddress(String connectionAddress) { this.connectionAddress = connectionAddress; }

        public MediaDescription getAudioDescription() { return audioDescription; }
        public void setAudioDescription(MediaDescription audioDescription) { this.audioDescription = audioDescription; }

        public MediaDescription getVideoDescription() { return videoDescription; }
        public void setVideoDescription(MediaDescription videoDescription) { this.videoDescription = videoDescription; }
    }

    /**
     * 媒体描述类
     */
    private static class MediaDescription {
        private final String mediaType; // "audio" or "video"
        private int port;
        private int payloadType;
        private final List<String> codecs = new ArrayList<>();

        public MediaDescription(String mediaType) {
            this.mediaType = mediaType;
        }

        public String getMediaType() { return mediaType; }

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }

        public int getPayloadType() { return payloadType; }
        public void setPayloadType(int payloadType) { this.payloadType = payloadType; }

        public List<String> getCodecs() { return codecs; }
        public void addCodec(String codec) { this.codecs.add(codec); }
    }

    /**
     * 参与者媒体信息类
     */
    public static class ParticipantMediaInfo {
        private final String participantUri;
        private final String remoteIp;
        private final int remoteAudioPort;
        private final int remoteVideoPort;
        private final int audioRtpPort;
        private final int videoRtpPort;

        public ParticipantMediaInfo(String participantUri, String remoteIp,
                                   int remoteAudioPort, int remoteVideoPort,
                                   int audioRtpPort, int videoRtpPort) {
            this.participantUri = participantUri;
            this.remoteIp = remoteIp;
            this.remoteAudioPort = remoteAudioPort;
            this.remoteVideoPort = remoteVideoPort;
            this.audioRtpPort = audioRtpPort;
            this.videoRtpPort = videoRtpPort;
        }

        public String getParticipantUri() { return participantUri; }
        public String getRemoteIp() { return remoteIp; }
        public int getRemoteAudioPort() { return remoteAudioPort; }
        public int getRemoteVideoPort() { return remoteVideoPort; }
        public int getAudioRtpPort() { return audioRtpPort; }
        public int getVideoRtpPort() { return videoRtpPort; }
    }

    /**
     * 会议媒体会话类
     */
    private static class ConferenceMediaSession {
        private final Long conferenceId;
        private final Map<String, ParticipantMediaInfo> participants = new ConcurrentHashMap<>();

        public ConferenceMediaSession(Long conferenceId) {
            this.conferenceId = conferenceId;
        }

        public void addParticipant(ParticipantMediaInfo mediaInfo) {
            participants.put(mediaInfo.getParticipantUri(), mediaInfo);
        }

        public void removeParticipant(String participantUri) {
            participants.remove(participantUri);
        }

        public boolean isEmpty() {
            return participants.isEmpty();
        }

        public Collection<ParticipantMediaInfo> getParticipants() {
            return participants.values();
        }
    }
}
