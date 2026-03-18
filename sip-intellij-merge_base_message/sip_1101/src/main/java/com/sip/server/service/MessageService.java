package com.sip.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sip.server.entity.Message;
import com.sip.server.entity.User;
import com.sip.server.mapper.MessageMapper;
import com.sip.server.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息服务类
 *
 * @author SIP Team - Member 3
 * @version 1.0
 */
@Slf4j
@Service
public class MessageService {

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private UserMapper userMapper;

    /**
     * SIP号码 → 数据库真实id 的缓存
     * PC 端 getUserIdByUsername 曾用 username 数字部分（如 100）当 userId，
     * 实际数据库中 user100 的 id 可能是 1。此缓存用于自动纠正。
     */
    private final ConcurrentHashMap<Long, Long> sipNumberToRealIdCache = new ConcurrentHashMap<>();

    /**
     * 保存消息（自动纠正错误的 userId）
     */
    public Message saveMessage(Message message) {
        if (message.getSendTime() == null) {
            message.setSendTime(LocalDateTime.now());
        }
        // 自动纠正 fromUserId / toUserId（兼容 PC 端用 SIP number 代替真实 id 的问题）
        message.setFromUserId(resolveRealUserId(message.getFromUserId()));
        message.setToUserId(resolveRealUserId(message.getToUserId()));

        messageMapper.insert(message);
        log.info("消息已保存: id={}, type={}, fromUser={}, toUser={}",
                message.getId(), message.getMsgType(), message.getFromUserId(), message.getToUserId());
        return message;
    }

    /**
     * 将可能是 SIP number 的 userId 转换为数据库真实 id。
     * 如果 userId 在 user 表中存在则直接返回；
     * 否则尝试查找 username = "user" + userId 的用户，返回其真实 id。
     */
    private Long resolveRealUserId(Long userId) {
        if (userId == null) return null;

        // 先查缓存
        Long cached = sipNumberToRealIdCache.get(userId);
        if (cached != null) return cached;

        // 检查该 id 是否在 user 表中直接存在
        User user = userMapper.selectById(userId);
        if (user != null) {
            // 是真实 id，缓存并返回
            sipNumberToRealIdCache.put(userId, userId);
            return userId;
        }

        // 不存在，尝试按 username = "user" + userId 查找
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", "user" + userId);
        User found = userMapper.selectOne(wrapper);
        if (found != null) {
            log.info("userId 自动纠正: {} -> {} (username={})", userId, found.getId(), found.getUsername());
            sipNumberToRealIdCache.put(userId, found.getId());
            return found.getId();
        }

        // 都找不到，原样返回
        return userId;
    }

    /**
     * 发送文本消息
     */
    public Message sendTextMessage(Long fromUserId, Long toUserId, String content, boolean isOffline) {
        Message message = new Message();
        message.setFromUserId(fromUserId);
        message.setToUserId(toUserId);
        message.setMsgType(1); // 1-文字
        message.setContent(content);
        message.setIsRead(0);
        message.setIsOffline(isOffline ? 1 : 0);
        message.setSendTime(LocalDateTime.now());
        return saveMessage(message);
    }

    /**
     * 发送图片消息
     */
    public Message sendImageMessage(Long fromUserId, Long toUserId, String fileUrl, Long fileSize, boolean isOffline) {
        Message message = new Message();
        message.setFromUserId(fromUserId);
        message.setToUserId(toUserId);
        message.setMsgType(2); // 2-图片
        message.setContent("[图片]");
        message.setFileUrl(fileUrl);
        message.setFileSize(fileSize);
        message.setIsRead(0);
        message.setIsOffline(isOffline ? 1 : 0);
        message.setSendTime(LocalDateTime.now());
        return saveMessage(message);
    }

    /**
     * 发送语音消息
     */
    public Message sendVoiceMessage(Long fromUserId, Long toUserId, String fileUrl, Long fileSize, Integer duration, boolean isOffline) {
        Message message = new Message();
        message.setFromUserId(fromUserId);
        message.setToUserId(toUserId);
        message.setMsgType(3); // 3-语音
        message.setContent("[语音]");
        message.setFileUrl(fileUrl);
        message.setFileSize(fileSize);
        message.setDuration(duration);
        message.setIsRead(0);
        message.setIsOffline(isOffline ? 1 : 0);
        message.setSendTime(LocalDateTime.now());
        return saveMessage(message);
    }

    /**
     * 发送视频消息
     */
    public Message sendVideoMessage(Long fromUserId, Long toUserId, String fileUrl, Long fileSize, Integer duration, boolean isOffline) {
        Message message = new Message();
        message.setFromUserId(fromUserId);
        message.setToUserId(toUserId);
        message.setMsgType(4); // 4-视频
        message.setContent("[视频]");
        message.setFileUrl(fileUrl);
        message.setFileSize(fileSize);
        message.setDuration(duration);
        message.setIsRead(0);
        message.setIsOffline(isOffline ? 1 : 0);
        message.setSendTime(LocalDateTime.now());
        return saveMessage(message);
    }

    /**
     * 发送文件消息
     */
    public Message sendFileMessage(Long fromUserId, Long toUserId, String fileUrl, Long fileSize, String fileName, boolean isOffline) {
        Message message = new Message();
        message.setFromUserId(fromUserId);
        message.setToUserId(toUserId);
        message.setMsgType(5); // 5-文件
        message.setContent(fileName != null ? "[文件] " + fileName : "[文件]");
        message.setFileUrl(fileUrl);
        message.setFileSize(fileSize);
        message.setIsRead(0);
        message.setIsOffline(isOffline ? 1 : 0);
        message.setSendTime(LocalDateTime.now());
        return saveMessage(message);
    }

    /**
     * 获取用户的离线消息
     */
    public List<Message> getOfflineMessages(Long userId) {
        Long realId = resolveRealUserId(userId);
        QueryWrapper<Message> wrapper = new QueryWrapper<>();
        wrapper.eq("to_user_id", realId)
               .eq("is_offline", 1)
               .eq("is_read", 0)
               .orderByAsc("send_time");
        return messageMapper.selectList(wrapper);
    }

    /**
     * 标记消息为已读
     */
    public void markAsRead(Long messageId) {
        Message message = messageMapper.selectById(messageId);
        if (message != null) {
            message.setIsRead(1);
            messageMapper.updateById(message);
        }
    }

    /**
     * 批量标记消息为已读
     */
    public void markMessagesAsRead(Long userId, Long otherUserId) {
        QueryWrapper<Message> wrapper = new QueryWrapper<>();
        wrapper.eq("from_user_id", otherUserId)
               .eq("to_user_id", userId)
               .eq("is_read", 0);

        List<Message> messages = messageMapper.selectList(wrapper);
        for (Message message : messages) {
            message.setIsRead(1);
            messageMapper.updateById(message);
        }
        log.info("批量标记消息为已读: {} 条消息", messages.size());
    }

    /**
     * 获取两个用户之间的聊天记录
     */
    public List<Message> getChatHistory(Long userId1, Long userId2, int limit) {
        Long realId1 = resolveRealUserId(userId1);
        Long realId2 = resolveRealUserId(userId2);
        QueryWrapper<Message> wrapper = new QueryWrapper<>();
        wrapper.and(w -> w
            .and(w1 -> w1.eq("from_user_id", realId1).eq("to_user_id", realId2))
            .or(w2 -> w2.eq("from_user_id", realId2).eq("to_user_id", realId1))
        );
        wrapper.orderByDesc("send_time").last("LIMIT " + limit);
        List<Message> messages = messageMapper.selectList(wrapper);
        // 反转列表，使最新消息在最后
        java.util.Collections.reverse(messages);
        return messages;
    }

    /**
     * 获取未读消息数量
     */
    public int getUnreadCount(Long userId, Long otherUserId) {
        QueryWrapper<Message> wrapper = new QueryWrapper<>();
        wrapper.eq("from_user_id", otherUserId)
               .eq("to_user_id", userId)
               .eq("is_read", 0);
        return Math.toIntExact(messageMapper.selectCount(wrapper));
    }

    /**
     * 获取用户的所有对话列表（去重）
     * 返回与该用户有过聊天记录的所有其他用户ID
     */
    public List<Long> getUserConversations(Long userId) {
        // 使用原生SQL查询，获取所有聊天过的用户ID（去重）
        String sql = "SELECT DISTINCT " +
                     "CASE " +
                     "  WHEN from_user_id = " + userId + " THEN to_user_id " +
                     "  ELSE from_user_id " +
                     "END AS other_user_id " +
                     "FROM message " +
                     "WHERE from_user_id = " + userId + " OR to_user_id = " + userId;

        List<Long> userIds = new java.util.ArrayList<>();
        try {
            // 使用MyBatis-Plus执行原生SQL
            List<java.util.Map<String, Object>> results = messageMapper.selectMaps(
                new QueryWrapper<Message>().apply(sql.replace("FROM message WHERE", "AND"))
                    .eq("from_user_id", userId)
                    .or()
                    .eq("to_user_id", userId)
                    .groupBy("other_user_id")
            );

            // 由于上述方法可能不工作，使用更简单的方法
            // 获取作为发送者的所有接收者ID
            QueryWrapper<Message> wrapper1 = new QueryWrapper<>();
            wrapper1.select("DISTINCT to_user_id").eq("from_user_id", userId);
            List<Message> sentMessages = messageMapper.selectList(wrapper1);
            for (Message msg : sentMessages) {
                if (!userIds.contains(msg.getToUserId())) {
                    userIds.add(msg.getToUserId());
                }
            }

            // 获取作为接收者的所有发送者ID
            QueryWrapper<Message> wrapper2 = new QueryWrapper<>();
            wrapper2.select("DISTINCT from_user_id").eq("to_user_id", userId);
            List<Message> receivedMessages = messageMapper.selectList(wrapper2);
            for (Message msg : receivedMessages) {
                if (!userIds.contains(msg.getFromUserId())) {
                    userIds.add(msg.getFromUserId());
                }
            }

        } catch (Exception e) {
            log.error("获取对话列表失败", e);
        }

        return userIds;
    }

    /**
     * 获取两个用户之间的最后一条消息
     */
    public Message getLastMessage(Long userId1, Long userId2) {
        QueryWrapper<Message> wrapper = new QueryWrapper<>();
        wrapper.and(w -> w
            .and(w1 -> w1.eq("from_user_id", userId1).eq("to_user_id", userId2))
            .or(w2 -> w2.eq("from_user_id", userId2).eq("to_user_id", userId1))
        );
        wrapper.orderByDesc("send_time").last("LIMIT 1");
        List<Message> messages = messageMapper.selectList(wrapper);
        return messages.isEmpty() ? null : messages.get(0);
    }

    /**
     * 分页获取两个用户之间的聊天记录
     */
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<Message> getChatHistoryPaginated(
            Long userId1, Long userId2, Integer pageNum, Integer pageSize) {

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Message> page =
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);

        QueryWrapper<Message> wrapper = new QueryWrapper<>();
        wrapper.and(w -> w
            .and(w1 -> w1.eq("from_user_id", userId1).eq("to_user_id", userId2))
            .or(w2 -> w2.eq("from_user_id", userId2).eq("to_user_id", userId1))
        );
        wrapper.orderByDesc("send_time");

        return messageMapper.selectPage(page, wrapper);
    }
}
