package com.sip.client.media.audio;

import lombok.extern.slf4j.Slf4j;

/**
 * 音频编解码器
 * 实现 G.711 μ-law (PCMU) 编解码
 *
 * G.711 是ITU-T定义的音频编解码标准:
 * - μ-law (PCMU): 主要用于北美和日本
 * - A-law (PCMA): 主要用于欧洲和世界其他地区
 *
 * 特点:
 * - 采样率: 8000 Hz
 * - 比特率: 64 kbit/s
 * - 压缩比: 2:1 (16 bit PCM -> 8 bit G.711)
 * - 低延迟,适合实时语音通信
 *
 * @author 成员2
 */
@Slf4j
public class AudioCodec {

    // ========== G.711 μ-law 查找表 ==========
    private static final short[] ULAW_DECODE_TABLE = new short[256];
    private static final byte[] ULAW_ENCODE_TABLE = new byte[65536];

    static {
        // 初始化 μ-law 解码表
        for (int i = 0; i < 256; i++) {
            ULAW_DECODE_TABLE[i] = ulawDecode((byte) i);
        }

        // 初始化 μ-law 编码表 (对称)
        for (int i = 0; i < 65536; i++) {
            short sample = (short) (i - 32768);
            ULAW_ENCODE_TABLE[i] = ulawEncode(sample);
        }
    }

    /**
     * 编码 PCM 16-bit 为 G.711 μ-law 8-bit
     *
     * @param pcmData 16-bit PCM 数据 (Little Endian)
     * @return G.711 μ-law 8-bit 数据
     */
    public static byte[] encodePCMU(byte[] pcmData) {
        if (pcmData.length % 2 != 0) {
            log.warn("PCM 数据长度不是 2 的倍数");
        }

        int sampleCount = pcmData.length / 2;
        byte[] ulawData = new byte[sampleCount];

        for (int i = 0; i < sampleCount; i++) {
            // 从 Little Endian 读取 16-bit sample
            int low = pcmData[i * 2] & 0xFF;
            int high = pcmData[i * 2 + 1] & 0xFF;
            short sample = (short) ((high << 8) | low);

            // 编码为 μ-law
            int index = (sample + 32768) & 0xFFFF;
            ulawData[i] = ULAW_ENCODE_TABLE[index];
        }

        return ulawData;
    }

    /**
     * 解码 G.711 μ-law 8-bit 为 PCM 16-bit
     *
     * @param ulawData G.711 μ-law 8-bit 数据
     * @return 16-bit PCM 数据 (Little Endian)
     */
    public static byte[] decodePCMU(byte[] ulawData) {
        byte[] pcmData = new byte[ulawData.length * 2];

        for (int i = 0; i < ulawData.length; i++) {
            // 解码为 16-bit PCM sample
            short sample = ULAW_DECODE_TABLE[ulawData[i] & 0xFF];

            // 写入 Little Endian
            pcmData[i * 2] = (byte) (sample & 0xFF);         // Low byte
            pcmData[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);  // High byte
        }

        return pcmData;
    }

    /**
     * μ-law 编码算法
     * 将 16-bit linear PCM 转换为 8-bit μ-law
     */
    private static byte ulawEncode(short sample) {
        // μ-law 压缩参数
        final int BIAS = 0x84;
        final int CLIP = 32635;

        int sign;
        int exponent;
        int mantissa;
        int compressedByte;

        // 获取符号位
        sign = (sample >> 8) & 0x80;

        // 取绝对值
        if (sign != 0) {
            sample = (short) -sample;
        }

        // 限幅
        if (sample > CLIP) {
            sample = CLIP;
        }

        // 加偏置
        sample = (short) (sample + BIAS);

        // 计算指数 (exponent)
        exponent = 7;
        for (int expMask = 0x4000; (sample & expMask) == 0 && exponent > 0; exponent--) {
            expMask >>= 1;
        }

        // 计算尾数 (mantissa)
        mantissa = (sample >> (exponent + 3)) & 0x0F;

        // 组合成 μ-law 字节
        compressedByte = ~(sign | (exponent << 4) | mantissa);

        return (byte) compressedByte;
    }

    /**
     * μ-law 解码算法
     * 将 8-bit μ-law 转换为 16-bit linear PCM
     */
    private static short ulawDecode(byte ulawByte) {
        int sign;
        int exponent;
        int mantissa;
        int sample;

        ulawByte = (byte) ~ulawByte;

        // 提取符号、指数、尾数
        sign = (ulawByte & 0x80);
        exponent = (ulawByte >> 4) & 0x07;
        mantissa = ulawByte & 0x0F;

        // 计算 linear value
        sample = ((mantissa << 3) + 0x84) << exponent;
        sample -= 0x84;

        // 应用符号
        if (sign != 0) {
            sample = -sample;
        }

        return (short) sample;
    }

    /**
     * 编码 PCM 16-bit 为 G.711 A-law 8-bit
     * (简化版本,仅用于参考)
     */
    public static byte[] encodePCMA(byte[] pcmData) {
        // 简化实现:直接返回原数据的一半 (实际应实现 A-law 算法)
        log.warn("A-law 编码尚未完全实现,使用 PCMU 代替");
        return encodePCMU(pcmData);
    }

    /**
     * 解码 G.711 A-law 8-bit 为 PCM 16-bit
     * (简化版本,仅用于参考)
     */
    public static byte[] decodePCMA(byte[] alawData) {
        log.warn("A-law 解码尚未完全实现,使用 PCMU 代替");
        return decodePCMU(alawData);
    }

    /**
     * 测试编解码
     */
    public static void main(String[] args) {
        log.info("测试 G.711 μ-law 编解码");

        // 创建测试 PCM 数据 (正弦波)
        int sampleCount = 160;
        byte[] pcmData = new byte[sampleCount * 2];

        for (int i = 0; i < sampleCount; i++) {
            double angle = 2.0 * Math.PI * i / sampleCount;
            short sample = (short) (Short.MAX_VALUE * 0.8 * Math.sin(angle));

            pcmData[i * 2] = (byte) (sample & 0xFF);
            pcmData[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        // 编码
        byte[] ulawData = encodePCMU(pcmData);
        log.info("PCM 长度: {}, μ-law 长度: {}, 压缩比: {}",
            pcmData.length, ulawData.length, (double) pcmData.length / ulawData.length);

        // 解码
        byte[] decodedPcm = decodePCMU(ulawData);
        log.info("解码后 PCM 长度: {}", decodedPcm.length);

        // 计算误差
        long errorSum = 0;
        for (int i = 0; i < pcmData.length; i += 2) {
            int original = (pcmData[i] & 0xFF) | ((pcmData[i + 1] & 0xFF) << 8);
            int decoded = (decodedPcm[i] & 0xFF) | ((decodedPcm[i + 1] & 0xFF) << 8);

            errorSum += Math.abs(original - decoded);
        }

        double avgError = (double) errorSum / sampleCount;
        log.info("平均误差: {}", avgError);
    }
}
