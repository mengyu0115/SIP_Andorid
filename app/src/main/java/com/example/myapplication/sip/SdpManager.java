package com.example.myapplication.sip;

/**
 * SDP (Session Description Protocol) 管理器
 *
 * 对应 PC 端 call/SdpNegotiator.java：
 * - createOffer: 生成 SDP Offer（本地媒体描述）
 * - createAnswer: 生成 SDP Answer（匹配远端 Offer）
 * - parseRemoteSdp: 解析远端 SDP，提取 IP/端口/编解码
 *
 * 编解码支持：
 * - 音频：PCMU/8000 (PT=0), PCMA/8000 (PT=8)
 * - 视频：H264/90000 (PT=96)（预留）
 */
public class SdpManager {

    public static final String CALL_TYPE_AUDIO = "audio";
    public static final String CALL_TYPE_VIDEO = "video";

    /**
     * 生成 SDP Offer
     *
     * @param localIp    本地 IP
     * @param callType   "audio" 或 "video"
     * @param audioPort  本地音频 RTP 端口
     * @param videoPort  本地视频 RTP 端口（仅 video 类型使用）
     */
    public static String createOffer(String localIp, String callType, int audioPort, int videoPort) {
        long sessionId = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        sb.append("v=0\r\n");
        sb.append("o=- ").append(sessionId).append(" ").append(sessionId).append(" IN IP4 ").append(localIp).append("\r\n");
        sb.append("s=SIP Call\r\n");
        sb.append("c=IN IP4 ").append(localIp).append("\r\n");
        sb.append("t=0 0\r\n");

        // 音频媒体行
        sb.append("m=audio ").append(audioPort).append(" RTP/AVP 0 8\r\n");
        sb.append("a=rtpmap:0 PCMU/8000\r\n");
        sb.append("a=rtpmap:8 PCMA/8000\r\n");
        sb.append("a=sendrecv\r\n");

        // 视频媒体行（仅视频通话）
        if (CALL_TYPE_VIDEO.equals(callType) && videoPort > 0) {
            sb.append("m=video ").append(videoPort).append(" RTP/AVP 96\r\n");
            sb.append("a=rtpmap:96 H264/90000\r\n");
            sb.append("a=fmtp:96 profile-level-id=42e01f\r\n");
            sb.append("a=sendrecv\r\n");
        }

        return sb.toString();
    }

    /**
     * 生成 SDP Answer（匹配远端 Offer 的编解码）
     */
    public static String createAnswer(String localIp, String remoteSdp, int audioPort, int videoPort) {
        MediaInfo remote = parseRemoteSdp(remoteSdp);
        long sessionId = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        sb.append("v=0\r\n");
        sb.append("o=- ").append(sessionId).append(" ").append(sessionId).append(" IN IP4 ").append(localIp).append("\r\n");
        sb.append("s=SIP Call\r\n");
        sb.append("c=IN IP4 ").append(localIp).append("\r\n");
        sb.append("t=0 0\r\n");

        if (remote.hasAudio) {
            sb.append("m=audio ").append(audioPort).append(" RTP/AVP 0 8\r\n");
            sb.append("a=rtpmap:0 PCMU/8000\r\n");
            sb.append("a=rtpmap:8 PCMA/8000\r\n");
            sb.append("a=sendrecv\r\n");
        }

        if (remote.hasVideo && videoPort > 0) {
            sb.append("m=video ").append(videoPort).append(" RTP/AVP 96\r\n");
            sb.append("a=rtpmap:96 H264/90000\r\n");
            sb.append("a=fmtp:96 profile-level-id=42e01f\r\n");
            sb.append("a=sendrecv\r\n");
        }

        return sb.toString();
    }

    /**
     * 解析远端 SDP，提取媒体信息
     */
    public static MediaInfo parseRemoteSdp(String sdp) {
        MediaInfo info = new MediaInfo();
        if (sdp == null || sdp.isEmpty()) return info;

        String[] lines = sdp.split("\r?\n");
        for (String line : lines) {
            if (line.startsWith("c=IN IP4 ")) {
                info.remoteIp = line.substring("c=IN IP4 ".length()).trim();
            } else if (line.startsWith("m=audio ")) {
                info.hasAudio = true;
                info.audioPort = parseMediaPort(line);
            } else if (line.startsWith("m=video ")) {
                info.hasVideo = true;
                info.videoPort = parseMediaPort(line);
            }
        }
        return info;
    }

    /**
     * 从 SDP 判断通话类型
     */
    public static String parseCallType(String sdp) {
        if (sdp != null && sdp.contains("m=video ")) {
            return CALL_TYPE_VIDEO;
        }
        return CALL_TYPE_AUDIO;
    }

    private static int parseMediaPort(String mLine) {
        // m=audio 8000 RTP/AVP 0 8
        String[] parts = mLine.split("\\s+");
        if (parts.length >= 2) {
            try {
                return Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    /** 远端 SDP 解析结果 */
    public static class MediaInfo {
        public String remoteIp = "";
        public int audioPort = 0;
        public int videoPort = 0;
        public boolean hasAudio = false;
        public boolean hasVideo = false;

        @Override
        public String toString() {
            return "MediaInfo{ip=" + remoteIp + ", audio=" + audioPort + ", video=" + videoPort + "}";
        }
    }
}
