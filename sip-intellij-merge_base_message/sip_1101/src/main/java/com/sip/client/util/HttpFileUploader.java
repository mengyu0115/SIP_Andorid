package com.sip.client.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sip.common.result.Result;
import com.sip.server.entity.Message;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP文件上传工具类
 * 专门用于富媒体消息（图片/语音/视频/文件）的上传
 *
 * @author SIP Team
 * @version 1.0
 */
@Slf4j
public class HttpFileUploader {

    // ✅ 修复：配置ObjectMapper支持LocalDateTime等Java 8日期时间类型
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());


    /**
     * 上传图片消息
     *
     * @param file 图片文件
     * @param fromUserId 发送者ID
     * @param toUserId 接收者ID
     * @return 消息对象，包含fileUrl等信息
     * @throws Exception 上传失败
     */
    public static Message uploadImage(File file, Long fromUserId, Long toUserId) throws Exception {
        log.info("上传图片消息: from={}, to={}, file={}", fromUserId, toUserId, file.getName());

        // 构建表单数据
        Map<String, String> formData = new HashMap<>();
        formData.put("fromUserId", String.valueOf(fromUserId));
        formData.put("toUserId", String.valueOf(toUserId));
        formData.put("isOffline", "false");

        // 调用HTTP上传，先获取String响应
        String responseJson = HttpClientUtil.uploadFile(
            "/api/message/send/image",
            file,
            formData,
            String.class
        );

        // 手动解析JSON
        Result result = objectMapper.readValue(responseJson, Result.class);

        // 解析响应
        if (result.getCode() == null || result.getCode() != 200) {
            throw new Exception("上传图片失败: " + result.getMessage());
        }

        // 将data转换为Message对象
        Message message = objectMapper.convertValue(result.getData(), Message.class);

        log.info("图片上传成功: fileUrl={}", message.getFileUrl());
        return message;
    }

    /**
     * 上传语音消息
     *
     * @param file 语音文件
     * @param fromUserId 发送者ID
     * @param toUserId 接收者ID
     * @param duration 时长（秒）
     * @return 消息对象
     * @throws Exception 上传失败
     */
    public static Message uploadVoice(File file, Long fromUserId, Long toUserId, Integer duration) throws Exception {
        log.info("上传语音消息: from={}, to={}, file={}, duration={}秒",
                fromUserId, toUserId, file.getName(), duration);

        Map<String, String> formData = new HashMap<>();
        formData.put("fromUserId", String.valueOf(fromUserId));
        formData.put("toUserId", String.valueOf(toUserId));
        formData.put("isOffline", "false");
        if (duration != null) {
            formData.put("duration", String.valueOf(duration));
        }

        String responseJson = HttpClientUtil.uploadFile(
            "/api/message/send/voice",
            file,
            formData,
            String.class
        );

        Result result = objectMapper.readValue(responseJson, Result.class);
        if (result.getCode() == null || result.getCode() != 200) {
            throw new Exception("上传语音失败: " + result.getMessage());
        }

        Message message = objectMapper.convertValue(result.getData(), Message.class);
        log.info("语音上传成功: fileUrl={}", message.getFileUrl());
        return message;
    }

    /**
     * 上传视频消息
     *
     * @param file 视频文件
     * @param fromUserId 发送者ID
     * @param toUserId 接收者ID
     * @param duration 时长（秒）
     * @return 消息对象
     * @throws Exception 上传失败
     */
    public static Message uploadVideo(File file, Long fromUserId, Long toUserId, Integer duration) throws Exception {
        log.info("上传视频消息: from={}, to={}, file={}, duration={}秒",
                fromUserId, toUserId, file.getName(), duration);

        Map<String, String> formData = new HashMap<>();
        formData.put("fromUserId", String.valueOf(fromUserId));
        formData.put("toUserId", String.valueOf(toUserId));
        formData.put("isOffline", "false");
        if (duration != null) {
            formData.put("duration", String.valueOf(duration));
        }

        String responseJson = HttpClientUtil.uploadFile(
            "/api/message/send/video",
            file,
            formData,
            String.class
        );

        Result result = objectMapper.readValue(responseJson, Result.class);
        if (result.getCode() == null || result.getCode() != 200) {
            throw new Exception("上传视频失败: " + result.getMessage());
        }

        Message message = objectMapper.convertValue(result.getData(), Message.class);
        log.info("视频上传成功: fileUrl={}", message.getFileUrl());
        return message;
    }

    /**
     * 上传文件消息
     *
     * @param file 文件
     * @param fromUserId 发送者ID
     * @param toUserId 接收者ID
     * @return 消息对象
     * @throws Exception 上传失败
     */
    public static Message uploadFile(File file, Long fromUserId, Long toUserId) throws Exception {
        log.info("上传文件消息: from={}, to={}, file={}", fromUserId, toUserId, file.getName());

        Map<String, String> formData = new HashMap<>();
        formData.put("fromUserId", String.valueOf(fromUserId));
        formData.put("toUserId", String.valueOf(toUserId));
        formData.put("isOffline", "false");

        String responseJson = HttpClientUtil.uploadFile(
            "/api/message/send/file",
            file,
            formData,
            String.class
        );

        Result result = objectMapper.readValue(responseJson, Result.class);
        if (result.getCode() == null || result.getCode() != 200) {
            throw new Exception("上传文件失败: " + result.getMessage());
        }

        Message message = objectMapper.convertValue(result.getData(), Message.class);
        log.info("文件上传成功: fileUrl={}", message.getFileUrl());
        return message;
    }

    /**
     * 下载文件到指定位置
     *
     * @param fileUrl 文件URL（如：/files/image/2025-12-19/abc.jpg）
     * @param saveToFile 保存到的文件
     * @return 下载的文件
     * @throws Exception 下载失败
     */
    public static File downloadFile(String fileUrl, File saveToFile) throws Exception {
        log.info("下载文件: url={}, saveTo={}", fileUrl, saveToFile.getAbsolutePath());
        return HttpClientUtil.downloadFile(fileUrl, saveToFile);
    }
}
