package com.sip.client.message.handler;

import com.sip.client.message.SipMessageManager.IncomingMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息处理器 - 处理接收到的消息
 *
 * @author 成员3
 * @version 1.0
 */
@Slf4j
public class MessageHandler {

    /**
     * 处理文本消息
     */
    public void handleTextMessage(IncomingMessage message) {
        log.info("[处理文本消息] From: {}, Content: {}",
            message.getFromUsername(),
            message.getContent());

        // TODO: 这里可以添加业务逻辑
        // 例如：保存到数据库、触发通知等
    }

    /**
     * 处理图片消息
     */
    public void handleImageMessage(IncomingMessage message) {
        String imageUrl = message.getImageUrl();
        log.info("[处理图片消息] From: {}, URL: {}",
            message.getFromUsername(),
            imageUrl);

        // TODO: 下载图片、显示预览等
    }

    /**
     * 处理文件消息
     */
    public void handleFileMessage(IncomingMessage message) {
        String[] fileInfo = message.getFileInfo();
        if (fileInfo != null && fileInfo.length == 2) {
            String fileName = fileInfo[0];
            String fileUrl = fileInfo[1];

            log.info("[处理文件消息] From: {}, 文件名: {}, URL: {}",
                message.getFromUsername(),
                fileName,
                fileUrl);

            // TODO: 下载文件等
        }
    }

    /**
     * 分发消息到对应的处理方法
     */
    public void dispatch(IncomingMessage message) {
        if (message.isImageMessage()) {
            handleImageMessage(message);
        } else if (message.isFileMessage()) {
            handleFileMessage(message);
        } else {
            handleTextMessage(message);
        }
    }
}
