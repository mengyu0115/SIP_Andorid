package com.sip.client.call;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SDP 协商器
 * 负责生成和解析 SDP (Session Description Protocol) 消息
 *
 * SDP 功能:
 * 1. 创建 SDP Offer (发起呼叫时)
 * 2. 创建 SDP Answer (接听呼叫时)
 * 3. 解析 SDP (提取对方的媒体信息)
 * 4. 媒体格式协商 (音频: G.711/Opus, 视频: H.264)
 *
 * @author 成员2
 */
@Slf4j
public class SdpNegotiator {

    /**
     * 创建 SDP Offer
     *
     * @param localIp 本地 IP
     * @param callType 通话类型: "audio" 或 "video"
     * @param audioPort 音频 RTP 端口
     * @param videoPort 视频 RTP 端口 (video 通话时必须)
     * @return SDP 字符串
     */
    public String createOffer(String localIp, String callType, int audioPort, Integer videoPort) {
        log.info("创建 SDP Offer: 类型={}, 音频端口={}, 视频端口={}",
            callType, audioPort, videoPort);

        StringBuilder sdp = new StringBuilder();

        // ===== Session Description =====
        sdp.append("v=0\r\n");  // Protocol Version
        sdp.append("o=- ").append(System.currentTimeMillis()).append(" ")
           .append(System.currentTimeMillis()).append(" IN IP4 ")
           .append(localIp).append("\r\n");  // Origin
        sdp.append("s=SIP Call\r\n");  // Session Name
        sdp.append("c=IN IP4 ").append(localIp).append("\r\n");  // Connection Info
        sdp.append("t=0 0\r\n");  // Time

        // ===== Media Description - Audio =====
        sdp.append("m=audio ").append(audioPort).append(" RTP/AVP 0 8 111\r\n");
        // 0 = G.711 μ-law (PCMU)
        // 8 = G.711 A-law (PCMA)
        // 111 = Opus

        // Audio Attributes
        sdp.append("a=rtpmap:0 PCMU/8000\r\n");
        sdp.append("a=rtpmap:8 PCMA/8000\r\n");
        sdp.append("a=rtpmap:111 opus/48000/2\r\n");
        sdp.append("a=sendrecv\r\n");  // Send and Receive

        // ===== Media Description - Video (if video call) =====
        if ("video".equals(callType) && videoPort != null) {
            sdp.append("m=video ").append(videoPort).append(" RTP/AVP 96\r\n");
            // 96 = H.264

            // Video Attributes
            sdp.append("a=rtpmap:96 H264/90000\r\n");
            sdp.append("a=fmtp:96 profile-level-id=42e01f\r\n");  // H.264 Baseline Profile
            sdp.append("a=sendrecv\r\n");
        }

        String sdpStr = sdp.toString();
        log.debug("SDP Offer:\n{}", sdpStr);
        return sdpStr;
    }

    /**
     * 创建 SDP Answer
     *
     * @param localIp 本地 IP
     * @param remoteSdpOffer 对方的 SDP Offer
     * @param audioPort 本地音频 RTP 端口
     * @param videoPort 本地视频 RTP 端口
     * @return SDP Answer 字符串
     */
    public String createAnswer(String localIp, String remoteSdpOffer, int audioPort, Integer videoPort) {
        log.info("创建 SDP Answer: 音频端口={}, 视频端口={}", audioPort, videoPort);

        // 解析对方的 SDP Offer
        boolean hasVideo = remoteSdpOffer.contains("m=video");

        StringBuilder sdp = new StringBuilder();

        // ===== Session Description =====
        sdp.append("v=0\r\n");
        sdp.append("o=- ").append(System.currentTimeMillis()).append(" ")
           .append(System.currentTimeMillis()).append(" IN IP4 ")
           .append(localIp).append("\r\n");
        sdp.append("s=SIP Call\r\n");
        sdp.append("c=IN IP4 ").append(localIp).append("\r\n");
        sdp.append("t=0 0\r\n");

        // ===== Media Description - Audio =====
        sdp.append("m=audio ").append(audioPort).append(" RTP/AVP 0 8 111\r\n");
        sdp.append("a=rtpmap:0 PCMU/8000\r\n");
        sdp.append("a=rtpmap:8 PCMA/8000\r\n");
        sdp.append("a=rtpmap:111 opus/48000/2\r\n");
        sdp.append("a=sendrecv\r\n");

        // ===== Media Description - Video =====
        if (hasVideo && videoPort != null) {
            sdp.append("m=video ").append(videoPort).append(" RTP/AVP 96\r\n");
            sdp.append("a=rtpmap:96 H264/90000\r\n");
            sdp.append("a=fmtp:96 profile-level-id=42e01f\r\n");
            sdp.append("a=sendrecv\r\n");
        }

        String sdpStr = sdp.toString();
        log.debug("SDP Answer:\n{}", sdpStr);
        return sdpStr;
    }

    /**
     * 解析对方的 SDP,提取媒体信息
     *
     * @param remoteSdp 对方的 SDP 字符串
     * @return 媒体信息 Map
     *         - remoteIp: 对方 IP
     *         - audioPort: 对方音频 RTP 端口
     *         - videoPort: 对方视频 RTP 端口 (如果有)
     *         - hasAudio: 是否有音频
     *         - hasVideo: 是否有视频
     *         - audioCodec: 音频编码 (PCMU/PCMA/opus)
     *         - videoCodec: 视频编码 (H264)
     */
    public Map<String, Object> parseRemoteSdp(String remoteSdp) {
        log.info("解析对方的 SDP");

        Map<String, Object> mediaInfo = new HashMap<>();

        try {
            // 1. 解析 Connection Address (c=)
            Pattern connPattern = Pattern.compile("c=IN IP4 ([\\d.]+)");
            Matcher connMatcher = connPattern.matcher(remoteSdp);
            if (connMatcher.find()) {
                String remoteIp = connMatcher.group(1);
                mediaInfo.put("remoteIp", remoteIp);
                log.debug("对方 IP: {}", remoteIp);
            }

            // 2. 解析 Audio Media (m=audio)
            Pattern audioPattern = Pattern.compile("m=audio (\\d+) RTP/AVP ([\\d\\s]+)");
            Matcher audioMatcher = audioPattern.matcher(remoteSdp);
            if (audioMatcher.find()) {
                int audioPort = Integer.parseInt(audioMatcher.group(1));
                String payloadTypes = audioMatcher.group(2);

                mediaInfo.put("hasAudio", true);
                mediaInfo.put("audioPort", audioPort);
                mediaInfo.put("audioPayloadTypes", payloadTypes);

                log.debug("对方音频端口: {}, Payload Types: {}", audioPort, payloadTypes);

                // 解析音频编码
                String audioCodec = parseAudioCodec(remoteSdp, payloadTypes);
                mediaInfo.put("audioCodec", audioCodec);
            } else {
                mediaInfo.put("hasAudio", false);
            }

            // 3. 解析 Video Media (m=video)
            Pattern videoPattern = Pattern.compile("m=video (\\d+) RTP/AVP (\\d+)");
            Matcher videoMatcher = videoPattern.matcher(remoteSdp);
            if (videoMatcher.find()) {
                int videoPort = Integer.parseInt(videoMatcher.group(1));
                String videoPayloadType = videoMatcher.group(2);

                mediaInfo.put("hasVideo", true);
                mediaInfo.put("videoPort", videoPort);
                mediaInfo.put("videoPayloadType", videoPayloadType);

                log.debug("对方视频端口: {}, Payload Type: {}", videoPort, videoPayloadType);

                // 通常 96 = H.264
                mediaInfo.put("videoCodec", "H264");
            } else {
                mediaInfo.put("hasVideo", false);
            }

            log.info("SDP 解析完成: {}", mediaInfo);

        } catch (Exception e) {
            log.error("解析 SDP 失败", e);
        }

        return mediaInfo;
    }

    /**
     * 解析通话类型 (audio or video)
     */
    public String parseCallType(String remoteSdp) {
        boolean hasVideo = remoteSdp.contains("m=video");
        return hasVideo ? "video" : "audio";
    }

    /**
     * 解析音频编码
     */
    private String parseAudioCodec(String sdp, String payloadTypes) {
        // 优先选择 Opus (111), 其次 PCMU (0), 最后 PCMA (8)
        if (payloadTypes.contains("111")) {
            return "opus";
        } else if (payloadTypes.contains("0")) {
            return "PCMU";
        } else if (payloadTypes.contains("8")) {
            return "PCMA";
        } else {
            return "unknown";
        }
    }

    /**
     * 获取默认音频 Payload Type
     */
    public int getDefaultAudioPayloadType() {
        return 0;  // PCMU (G.711 μ-law)
    }

    /**
     * 获取默认视频 Payload Type
     */
    public int getDefaultVideoPayloadType() {
        return 96;  // H.264
    }
}
