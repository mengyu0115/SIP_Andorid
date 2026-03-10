package com.sip.server.controller;

import com.sip.common.result.Result;
import com.sip.server.entity.Message;
import com.sip.server.entity.FileInfo;
import com.sip.server.service.MessageService;
import com.sip.server.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 消息控制器
 *
 * @author SIP Team - Member 3
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/message")
public class MessageController {

    @Autowired
    private MessageService messageService;

    @Autowired
    private FileService fileService;

    /**
     * 获取离线消息
     *
     * @param userId 用户ID
     * @return 离线消息列表
     */
    @GetMapping("/offline/{userId}")
    public Result<List<Message>> getOfflineMessages(@PathVariable Long userId) {
        log.info("获取用户离线消息: userId={}", userId);
        try {
            List<Message> messages = messageService.getOfflineMessages(userId);
            return Result.success(messages);
        } catch (Exception e) {
            log.error("获取离线消息失败", e);
            return Result.error("获取离线消息失败: " + e.getMessage());
        }
    }

    /**
     * 获取聊天记录
     *
     * @param userId1 用户1 ID
     * @param userId2 用户2 ID
     * @param limit 获取数量
     * @return 聊天记录列表
     */
    @GetMapping("/history")
    public Result<List<Message>> getChatHistory(
            @RequestParam Long userId1,
            @RequestParam Long userId2,
            @RequestParam(defaultValue = "50") int limit) {
        log.info("获取聊天记录: userId1={}, userId2={}, limit={}", userId1, userId2, limit);
        try {
            List<Message> messages = messageService.getChatHistory(userId1, userId2, limit);
            return Result.success(messages);
        } catch (Exception e) {
            log.error("获取聊天记录失败", e);
            return Result.error("获取聊天记录失败: " + e.getMessage());
        }
    }

    /**
     * 标记消息为已读
     *
     * @param messageId 消息ID
     * @return 操作结果
     */
    @PutMapping("/read/{messageId}")
    public Result<Void> markAsRead(@PathVariable Long messageId) {
        log.info("标记消息已读: messageId={}", messageId);
        try {
            messageService.markAsRead(messageId);
            return Result.success();
        } catch (Exception e) {
            log.error("标记消息已读失败", e);
            return Result.error("标记消息已读失败: " + e.getMessage());
        }
    }

    /**
     * 批量标记消息为已读
     *
     * @param userId 当前用户ID
     * @param otherUserId 对方用户ID
     * @return 操作结果
     */
    @PutMapping("/read/batch")
    public Result<Void> markMessagesAsRead(@RequestParam Long userId, @RequestParam Long otherUserId) {
        log.info("批量标记消息已读: userId={}, otherUserId={}", userId, otherUserId);
        try {
            messageService.markMessagesAsRead(userId, otherUserId);
            return Result.success();
        } catch (Exception e) {
            log.error("批量标记消息已读失败", e);
            return Result.error("批量标记消息已读失败: " + e.getMessage());
        }
    }

    /**
     * 发送消息（保存到数据库）
     *
     * @param message 消息对象
     * @return 操作结果
     */
    @PostMapping("/send")
    public Result<Message> sendMessage(@RequestBody Message message) {
        log.info("保存消息: fromUserId={}, toUserId={}, type={}",
                message.getFromUserId(), message.getToUserId(), message.getMsgType());
        try {
            Message savedMessage = messageService.saveMessage(message);
            return Result.success(savedMessage);
        } catch (Exception e) {
            log.error("保存消息失败", e);
            return Result.error("保存消息失败: " + e.getMessage());
        }
    }

    /**
     * 发送图片消息
     *
     * @param file 图片文件
     * @param fromUserId 发送者ID
     * @param toUserId 接收者ID
     * @param isOffline 是否离线消息
     * @return 操作结果
     */
    @PostMapping("/send/image")
    public Result<Message> sendImageMessage(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long fromUserId,
            @RequestParam Long toUserId,
            @RequestParam(defaultValue = "false") boolean isOffline) {
        log.info("发送图片消息: from={}, to={}, file={}", fromUserId, toUserId, file.getOriginalFilename());
        try {
            // 上传文件（传入发送者ID）
            FileInfo fileInfo = fileService.upload(file, "image", fromUserId);

            // 保存图片消息
            Message message = messageService.sendImageMessage(
                    fromUserId, toUserId,
                    fileInfo.getFileUrl(),
                    fileInfo.getFileSize(),
                    isOffline
            );
            return Result.success(message);
        } catch (Exception e) {
            log.error("发送图片消息失败", e);
            return Result.error("发送图片消息失败: " + e.getMessage());
        }
    }

    /**
     * 发送语音消息
     *
     * @param file 语音文件
     * @param fromUserId 发送者ID
     * @param toUserId 接收者ID
     * @param duration 时长（秒）
     * @param isOffline 是否离线消息
     * @return 操作结果
     */
    @PostMapping("/send/voice")
    public Result<Message> sendVoiceMessage(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long fromUserId,
            @RequestParam Long toUserId,
            @RequestParam(required = false) Integer duration,
            @RequestParam(defaultValue = "false") boolean isOffline) {
        log.info("发送语音消息: from={}, to={}, file={}, duration={}",
                fromUserId, toUserId, file.getOriginalFilename(), duration);
        try {
            // 上传文件（传入发送者ID）
            FileInfo fileInfo = fileService.upload(file, "voice", fromUserId);

            // 保存语音消息
            Message message = messageService.sendVoiceMessage(
                    fromUserId, toUserId,
                    fileInfo.getFileUrl(),
                    fileInfo.getFileSize(),
                    duration,
                    isOffline
            );
            return Result.success(message);
        } catch (Exception e) {
            log.error("发送语音消息失败", e);
            return Result.error("发送语音消息失败: " + e.getMessage());
        }
    }

    /**
     * 发送视频消息
     *
     * @param file 视频文件
     * @param fromUserId 发送者ID
     * @param toUserId 接收者ID
     * @param duration 时长（秒）
     * @param isOffline 是否离线消息
     * @return 操作结果
     */
    @PostMapping("/send/video")
    public Result<Message> sendVideoMessage(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long fromUserId,
            @RequestParam Long toUserId,
            @RequestParam(required = false) Integer duration,
            @RequestParam(defaultValue = "false") boolean isOffline) {
        log.info("发送视频消息: from={}, to={}, file={}, duration={}",
                fromUserId, toUserId, file.getOriginalFilename(), duration);
        try {
            // 上传文件（传入发送者ID）
            FileInfo fileInfo = fileService.upload(file, "video", fromUserId);

            // 保存视频消息
            Message message = messageService.sendVideoMessage(
                    fromUserId, toUserId,
                    fileInfo.getFileUrl(),
                    fileInfo.getFileSize(),
                    duration,
                    isOffline
            );
            return Result.success(message);
        } catch (Exception e) {
            log.error("发送视频消息失败", e);
            return Result.error("发送视频消息失败: " + e.getMessage());
        }
    }

    /**
     * 发送文件消息
     *
     * @param file 文件
     * @param fromUserId 发送者ID
     * @param toUserId 接收者ID
     * @param isOffline 是否离线消息
     * @return 操作结果
     */
    @PostMapping("/send/file")
    public Result<Message> sendFileMessage(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long fromUserId,
            @RequestParam Long toUserId,
            @RequestParam(defaultValue = "false") boolean isOffline) {
        log.info("发送文件消息: from={}, to={}, file={}", fromUserId, toUserId, file.getOriginalFilename());
        try {
            // 上传文件（传入发送者ID）
            FileInfo fileInfo = fileService.upload(file, "file", fromUserId);

            // 保存文件消息
            Message message = messageService.sendFileMessage(
                    fromUserId, toUserId,
                    fileInfo.getFileUrl(),
                    fileInfo.getFileSize(),
                    file.getOriginalFilename(),
                    isOffline
            );
            return Result.success(message);
        } catch (Exception e) {
            log.error("发送文件消息失败", e);
            return Result.error("发送文件消息失败: " + e.getMessage());
        }
    }
}
