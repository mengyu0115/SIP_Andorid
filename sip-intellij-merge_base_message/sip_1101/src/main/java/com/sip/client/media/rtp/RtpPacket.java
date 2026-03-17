package com.sip.client.media.rtp;

import lombok.Data;

import java.nio.ByteBuffer;

/**
 * RTP 包封装
 * RFC 3550 - RTP: A Transport Protocol for Real-Time Applications
 *
 * RTP Header 格式:
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |V=2|P|X|  CC   |M|     PT      |       sequence number         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                           timestamp                           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |           synchronization source (SSRC) identifier            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 * @author 成员2
 */
@Data
public class RtpPacket {

    // ========== RTP Header Fields ==========
    private int version = 2;          // Version (V): 2 bits, 固定为 2
    private boolean padding = false;  // Padding (P): 1 bit
    private boolean extension = false;// Extension (X): 1 bit
    private int csrcCount = 0;        // CSRC count (CC): 4 bits
    private boolean marker = false;   // Marker (M): 1 bit
    private int payloadType;          // Payload Type (PT): 7 bits
    private int sequenceNumber;       // Sequence Number: 16 bits
    private long timestamp;           // Timestamp: 32 bits
    private long ssrc;                // SSRC: 32 bits

    // ========== Payload ==========
    private byte[] payload;

    // ========== Constants ==========
    public static final int RTP_HEADER_SIZE = 12;  // 固定 12 字节

    /**
     * 构造 RTP 包
     */
    public RtpPacket(int payloadType, int sequenceNumber, long timestamp, long ssrc, byte[] payload) {
        this.payloadType = payloadType;
        this.sequenceNumber = sequenceNumber;
        this.timestamp = timestamp;
        this.ssrc = ssrc;
        this.payload = payload;
    }

    /**
     * 将 RTP 包编码为字节数组 (用于网络发送)
     *
     * @return RTP 字节数组
     */
    public byte[] encode() {
        int totalLength = RTP_HEADER_SIZE + payload.length;
        ByteBuffer buffer = ByteBuffer.allocate(totalLength);

        // Byte 0: V(2) | P(1) | X(1) | CC(4)
        int byte0 = (version << 6) | (padding ? 0x20 : 0) | (extension ? 0x10 : 0) | csrcCount;
        buffer.put((byte) byte0);

        // Byte 1: M(1) | PT(7)
        int byte1 = (marker ? 0x80 : 0) | (payloadType & 0x7F);
        buffer.put((byte) byte1);

        // Byte 2-3: Sequence Number (16 bits)
        buffer.putShort((short) sequenceNumber);

        // Byte 4-7: Timestamp (32 bits)
        buffer.putInt((int) timestamp);

        // Byte 8-11: SSRC (32 bits)
        buffer.putInt((int) ssrc);

        // Payload
        buffer.put(payload);

        return buffer.array();
    }

    /**
     * 从字节数组解码 RTP 包
     *
     * @param data RTP 字节数组
     * @return RTP 包对象
     */
    public static RtpPacket decode(byte[] data) {
        if (data.length < RTP_HEADER_SIZE) {
            throw new IllegalArgumentException("Invalid RTP packet: too short");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);

        // Byte 0
        int byte0 = buffer.get() & 0xFF;
        int version = (byte0 >> 6) & 0x03;
        boolean padding = (byte0 & 0x20) != 0;
        boolean extension = (byte0 & 0x10) != 0;
        int csrcCount = byte0 & 0x0F;

        // Byte 1
        int byte1 = buffer.get() & 0xFF;
        boolean marker = (byte1 & 0x80) != 0;
        int payloadType = byte1 & 0x7F;

        // Byte 2-3: Sequence Number
        int sequenceNumber = buffer.getShort() & 0xFFFF;

        // Byte 4-7: Timestamp
        long timestamp = buffer.getInt() & 0xFFFFFFFFL;

        // Byte 8-11: SSRC
        long ssrc = buffer.getInt() & 0xFFFFFFFFL;

        // Payload
        int payloadLength = data.length - RTP_HEADER_SIZE;
        byte[] payload = new byte[payloadLength];
        buffer.get(payload);

        RtpPacket packet = new RtpPacket(payloadType, sequenceNumber, timestamp, ssrc, payload);
        packet.setVersion(version);
        packet.setPadding(padding);
        packet.setExtension(extension);
        packet.setCsrcCount(csrcCount);
        packet.setMarker(marker);

        return packet;
    }

    /**
     * 获取 RTP 包总长度
     */
    public int getLength() {
        return RTP_HEADER_SIZE + payload.length;
    }
}
