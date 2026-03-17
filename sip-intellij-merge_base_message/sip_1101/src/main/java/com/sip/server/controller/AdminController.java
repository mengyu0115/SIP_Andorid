package com.sip.server.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sip.common.result.Result;
import com.sip.server.entity.CallRecord;
import com.sip.server.entity.Message;
import com.sip.server.entity.User;
import com.sip.server.service.MessageService;
import com.sip.server.service.admin.CallStatisticsService;
import com.sip.server.service.admin.MonitorService;
import com.sip.server.service.admin.UserAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台控制器
 *
 * 提供后台管理 REST API：
 * - 用户管理
 * - 统计分析
 * - 系统监控
 * - 数据导出
 *
 * @author SIP Team - Member 4
 * @version 1.0
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private CallStatisticsService callStatisticsService;

    @Autowired
    private MonitorService monitorService;

    @Autowired
    private MessageService messageService;

    /**
     * 8.1 获取用户列表
     *
     * GET /api/admin/users
     */
    @GetMapping("/users")
    public Result<Map<String, Object>> listUsers(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer status,
        @RequestParam(defaultValue = "1") int pageNum,
        @RequestParam(defaultValue = "10") int pageSize
    ) {
        logger.info("查询用户列表: keyword={}, status={}, pageNum={}, pageSize={}",
                    keyword, status, pageNum, pageSize);

        try {
            IPage<User> page = userAdminService.listUsers(pageNum, pageSize, keyword);

            Map<String, Object> data = new HashMap<>();
            data.put("total", page.getTotal());
            data.put("pageNum", page.getCurrent());
            data.put("pageSize", page.getSize());
            data.put("pages", page.getPages());
            data.put("list", page.getRecords());

            return Result.success(data);
        } catch (Exception e) {
            logger.error("查询用户列表失败", e);
            return Result.error(500, "查询失败: " + e.getMessage());
        }
    }

    /**
     * 8.2 删除用户
     *
     * DELETE /api/admin/user/{id}
     */
    @DeleteMapping("/user/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        logger.info("删除用户: id={}", id);

        try {
            boolean success = userAdminService.deleteUser(id);
            if (success) {
                return Result.success("删除成功", null);
            } else {
                return Result.error(404, "用户不存在");
            }
        } catch (Exception e) {
            logger.error("删除用户失败", e);
            return Result.error(500, "删除失败: " + e.getMessage());
        }
    }

    /**
     * 8.3 重置用户密码
     *
     * PUT /api/admin/user/{id}/reset-password
     */
    @PutMapping("/user/{id}/reset-password")
    public Result<Void> resetPassword(
        @PathVariable Long id,
        @RequestBody Map<String, String> request
    ) {
        logger.info("重置用户密码: id={}", id);

        try {
            String newPassword = request.get("newPassword");
            if (newPassword == null || newPassword.trim().isEmpty()) {
                return Result.error(400, "新密码不能为空");
            }

            boolean success = userAdminService.resetPassword(id, newPassword);
            if (success) {
                return Result.success("密码重置成功", null);
            } else {
                return Result.error(404, "用户不存在");
            }
        } catch (Exception e) {
            logger.error("重置密码失败", e);
            return Result.error(500, "重置失败: " + e.getMessage());
        }
    }

    /**
     * 8.4 获取系统统计信息
     *
     * GET /api/admin/stats
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getSystemStats() {
        logger.info("获取系统统计信息");

        try {
            Map<String, Object> stats = monitorService.getSystemSummary();
            return Result.success(stats);
        } catch (Exception e) {
            logger.error("获取统计信息失败", e);
            return Result.error(500, "获取失败: " + e.getMessage());
        }
    }

    /**
     * 8.5 获取通话统计
     *
     * GET /api/admin/call-stats
     */
    @GetMapping("/call-stats")
    public Result<Map<String, Object>> getCallStats(
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate
    ) {
        logger.info("获取通话统计: startDate={}, endDate={}", startDate, endDate);

        try {
            Map<String, Object> stats = new HashMap<>();

            // 基础统计
            stats.put("totalCalls", callStatisticsService.getTotalCallCount());
            stats.put("successCalls", callStatisticsService.getSuccessCallCount());
            stats.put("avgDuration", callStatisticsService.getAvgCallDuration());
            stats.put("totalDuration", callStatisticsService.getTotalCallDuration());

            // 失败和未接通话
            long totalCalls = callStatisticsService.getTotalCallCount();
            long successCalls = callStatisticsService.getSuccessCallCount();
            stats.put("failedCalls", totalCalls - successCalls);

            Map<String, Long> statusDist = callStatisticsService.getCallStatusDistribution();
            stats.put("missedCalls", statusDist.getOrDefault("未接", 0L));

            // 通话类型分布
            Map<String, Long> typeDist = callStatisticsService.getCallTypeDistribution();
            Map<String, Object> callsByType = new HashMap<>();
            callsByType.put("audio", typeDist.getOrDefault("音频", 0L));
            callsByType.put("video", typeDist.getOrDefault("视频", 0L));
            callsByType.put("conference", typeDist.getOrDefault("会议", 0L));
            stats.put("callsByType", callsByType);

            // 通话质量分布
            Map<String, Long> qualityDist = callStatisticsService.getCallQualityDistribution();
            Map<String, Object> callsByQuality = new HashMap<>();
            callsByQuality.put("excellent", qualityDist.getOrDefault("优", 0L));
            callsByQuality.put("good", qualityDist.getOrDefault("良", 0L));
            callsByQuality.put("fair", qualityDist.getOrDefault("中", 0L));
            callsByQuality.put("poor", qualityDist.getOrDefault("差", 0L));
            stats.put("callsByQuality", callsByQuality);

            return Result.success(stats);
        } catch (Exception e) {
            logger.error("获取通话统计失败", e);
            return Result.error(500, "获取失败: " + e.getMessage());
        }
    }

    /**
     * 8.6 获取消息统计
     *
     * GET /api/admin/message-stats
     */
    @GetMapping("/message-stats")
    public Result<Map<String, Object>> getMessageStats(
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate
    ) {
        logger.info("获取消息统计: startDate={}, endDate={}", startDate, endDate);

        try {
            Map<String, Object> stats = new HashMap<>();

            // TODO: 需要 MessageMapper 支持，暂时返回模拟数据
            stats.put("totalMessages", 0);
            stats.put("messagesByType", new HashMap<String, Long>());
            stats.put("avgMessagesPerUser", 0);
            stats.put("peakHour", "20:00-21:00");
            stats.put("dailyTrend", List.of());

            return Result.success(stats);
        } catch (Exception e) {
            logger.error("获取消息统计失败", e);
            return Result.error(500, "获取失败: " + e.getMessage());
        }
    }

    /**
     * 8.7 获取在线用户
     *
     * GET /api/admin/online-users
     */
    @GetMapping("/online-users")
    public Result<Map<String, Object>> getOnlineUsers() {
        logger.info("获取在线用户列表");

        try {
            List<User> onlineUsers = monitorService.getOnlineUsers();

            Map<String, Object> data = new HashMap<>();
            data.put("total", onlineUsers.size());
            data.put("users", onlineUsers);

            return Result.success(data);
        } catch (Exception e) {
            logger.error("获取在线用户失败", e);
            return Result.error(500, "获取失败: " + e.getMessage());
        }
    }

    /**
     * 8.9 导出数据报表
     *
     * GET /api/admin/export/report
     */
    @GetMapping("/export/report")
    public ResponseEntity<String> exportReport(
        @RequestParam String reportType,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate
    ) {
        logger.info("导出报表: reportType={}, startDate={}, endDate={}",
                    reportType, startDate, endDate);

        try {
            String csvContent;

            switch (reportType) {
                case "call":
                    csvContent = callStatisticsService.exportStatisticsReport(startDate, endDate);
                    break;

                case "user":
                    // TODO: 实现用户报表导出
                    csvContent = "用户报表暂未实现\n";
                    break;

                case "message":
                    // TODO: 实现消息报表导出
                    csvContent = "消息报表暂未实现\n";
                    break;

                default:
                    return ResponseEntity.badRequest()
                        .body("不支持的报表类型: " + reportType);
            }

            String filename = String.format("%s_report_%s_%s.csv",
                                          reportType, startDate, endDate);

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                       "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvContent);

        } catch (Exception e) {
            logger.error("导出报表失败", e);
            return ResponseEntity.internalServerError()
                .body("导出失败: " + e.getMessage());
        }
    }

    /**
     * 获取系统性能指标
     *
     * GET /api/admin/monitor
     */
    @GetMapping("/monitor")
    public Result<Map<String, Object>> getSystemMonitor() {
        logger.info("获取系统监控数据");

        try {
            Map<String, Object> metrics = monitorService.getRealtimeMetrics();
            return Result.success(metrics);
        } catch (Exception e) {
            logger.error("获取监控数据失败", e);
            return Result.error(500, "获取失败: " + e.getMessage());
        }
    }

    /**
     * 系统健康检查
     *
     * GET /api/admin/health
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> healthCheck() {
        logger.info("执行健康检查");

        try {
            Map<String, Object> health = monitorService.healthCheck();
            return Result.success(health);
        } catch (Exception e) {
            logger.error("健康检查失败", e);
            return Result.error(500, "检查失败: " + e.getMessage());
        }
    }

    /**
     * 获取系统告警
     *
     * GET /api/admin/alerts
     */
    @GetMapping("/alerts")
    public Result<List<Map<String, Object>>> getSystemAlerts() {
        logger.info("获取系统告警");

        try {
            List<Map<String, Object>> alerts = monitorService.getSystemAlerts();
            return Result.success(alerts);
        } catch (Exception e) {
            logger.error("获取告警失败", e);
            return Result.error(500, "获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户状态分布
     *
     * GET /api/admin/user-status-dist
     */
    @GetMapping("/user-status-dist")
    public Result<Map<String, Long>> getUserStatusDistribution() {
        logger.info("获取用户状态分布");

        try {
            Map<String, Long> distribution = monitorService.getUserStatusDistribution();
            return Result.success(distribution);
        } catch (Exception e) {
            logger.error("获取用户状态分布失败", e);
            return Result.error(500, "获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取通话类型分布
     *
     * GET /api/admin/call-type-dist
     */
    @GetMapping("/call-type-dist")
    public Result<Map<String, Long>> getCallTypeDistribution() {
        logger.info("获取通话类型分布");

        try {
            Map<String, Long> distribution = callStatisticsService.getCallTypeDistribution();
            return Result.success(distribution);
        } catch (Exception e) {
            logger.error("获取通话类型分布失败", e);
            return Result.error(500, "获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取每日通话统计
     *
     * GET /api/admin/daily-call-stats
     */
    @GetMapping("/daily-call-stats")
    public Result<List<Map<String, Object>>> getDailyCallStats(
        @RequestParam(defaultValue = "7") int days
    ) {
        logger.info("获取最近 {} 天的通话统计", days);

        try {
            List<Map<String, Object>> stats = callStatisticsService.getDailyCallStats(days);
            return Result.success(stats);
        } catch (Exception e) {
            logger.error("获取每日统计失败", e);
            return Result.error(500, "获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取通话时段分布
     *
     * GET /api/admin/call-hour-dist
     */
    @GetMapping("/call-hour-dist")
    public Result<Map<Integer, Long>> getCallHourDistribution() {
        logger.info("获取通话时段分布");

        try {
            Map<Integer, Long> distribution = callStatisticsService.getCallHourDistribution();
            return Result.success(distribution);
        } catch (Exception e) {
            logger.error("获取时段分布失败", e);
            return Result.error(500, "获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取热门通话用户
     *
     * GET /api/admin/top-callers
     */
    @GetMapping("/top-callers")
    public Result<List<Map<String, Object>>> getTopCallers(
        @RequestParam(defaultValue = "10") int topN
    ) {
        logger.info("获取通话 TOP {}", topN);

        try {
            List<Map<String, Object>> topCallers = callStatisticsService.getTopCallers(topN);
            return Result.success(topCallers);
        } catch (Exception e) {
            logger.error("获取热门用户失败", e);
            return Result.error(500, "获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取最近活跃用户
     *
     * GET /api/admin/recent-active-users
     */
    @GetMapping("/recent-active-users")
    public Result<List<User>> getRecentActiveUsers(
        @RequestParam(defaultValue = "10") int limit
    ) {
        logger.info("获取最近活跃用户 TOP {}", limit);

        try {
            List<User> users = monitorService.getRecentActiveUsers(limit);
            return Result.success(users);
        } catch (Exception e) {
            logger.error("获取活跃用户失败", e);
            return Result.error(500, "获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取最近通话记录
     *
     * GET /api/admin/recent-calls
     */
    @GetMapping("/recent-calls")
    public Result<List<CallRecord>> getRecentCalls(
        @RequestParam(defaultValue = "20") int limit
    ) {
        logger.info("获取最近通话记录 {} 条", limit);

        try {
            List<CallRecord> calls = monitorService.getRecentCalls(limit);
            return Result.success(calls);
        } catch (Exception e) {
            logger.error("获取通话记录失败", e);
            return Result.error(500, "获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取RTP统计
     *
     * GET /api/admin/rtp-stats
     */
    @GetMapping("/rtp-stats")
    public Result<Map<String, Object>> getRtpStatistics() {
        logger.info("获取RTP统计信息");

        try {
            Map<String, Object> stats = callStatisticsService.getRtpStatistics();
            return Result.success(stats);
        } catch (Exception e) {
            logger.error("获取RTP统计失败", e);
            return Result.error(500, "获取失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除用户
     *
     * DELETE /api/admin/users/batch
     */
    @DeleteMapping("/users/batch")
    public Result<Map<String, Object>> batchDeleteUsers(
        @RequestBody Map<String, List<Long>> request
    ) {
        List<Long> userIds = request.get("userIds");
        logger.info("批量删除用户: userIds={}", userIds);

        try {
            int deletedCount = userAdminService.batchDeleteUsers(userIds);

            Map<String, Object> result = new HashMap<>();
            result.put("deletedCount", deletedCount);

            return Result.success("批量删除成功", result);
        } catch (Exception e) {
            logger.error("批量删除失败", e);
            return Result.error(500, "删除失败: " + e.getMessage());
        }
    }

    /**
     * 修改用户状态
     *
     * PUT /api/admin/user/{id}/status
     */
    @PutMapping("/user/{id}/status")
    public Result<Void> updateUserStatus(
        @PathVariable Long id,
        @RequestBody Map<String, Integer> request
    ) {
        Integer status = request.get("status");
        logger.info("修改用户状态: id={}, status={}", id, status);

        try {
            boolean success = userAdminService.updateUserStatus(id, status);
            if (success) {
                return Result.success("状态修改成功", null);
            } else {
                return Result.error(404, "用户不存在");
            }
        } catch (Exception e) {
            logger.error("修改状态失败", e);
            return Result.error(500, "修改失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户对话列表
     *
     * GET /api/admin/user/{userId}/conversations
     */
    @GetMapping("/user/{userId}/conversations")
    public Result<List<Map<String, Object>>> getUserConversations(@PathVariable Long userId) {
        logger.info("获取用户对话列表: userId={}", userId);

        try {
            // 获取所有对话用户ID
            List<Long> conversationUserIds = messageService.getUserConversations(userId);

            // 构建返回数据
            List<Map<String, Object>> conversations = new java.util.ArrayList<>();
            for (Long otherUserId : conversationUserIds) {
                User otherUser = userAdminService.getUserById(otherUserId);
                if (otherUser == null) {
                    continue;
                }

                Message lastMessage = messageService.getLastMessage(userId, otherUserId);

                Map<String, Object> conversation = new HashMap<>();
                conversation.put("userId", otherUser.getId());
                conversation.put("username", otherUser.getUsername());
                conversation.put("nickname", otherUser.getNickname());
                conversation.put("avatar", otherUser.getAvatar());

                if (lastMessage != null) {
                    conversation.put("lastMessageContent", lastMessage.getContent());
                    conversation.put("lastMessageTime", lastMessage.getSendTime());
                    conversation.put("lastMessageType", lastMessage.getMsgType());
                }

                int unreadCount = messageService.getUnreadCount(userId, otherUserId);
                conversation.put("unreadCount", unreadCount);

                conversations.add(conversation);
            }

            // 按最后消息时间排序（降序）
            conversations.sort((c1, c2) -> {
                java.time.LocalDateTime t1 = (java.time.LocalDateTime) c1.get("lastMessageTime");
                java.time.LocalDateTime t2 = (java.time.LocalDateTime) c2.get("lastMessageTime");
                if (t1 == null && t2 == null) return 0;
                if (t1 == null) return 1;
                if (t2 == null) return -1;
                return t2.compareTo(t1);
            });

            return Result.success(conversations);

        } catch (Exception e) {
            logger.error("获取对话列表失败", e);
            return Result.error(500, "获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取两个用户之间的聊天记录（分页）
     *
     * GET /api/admin/user/{userId}/messages?otherUserId=&pageNum=&pageSize=
     */
    @GetMapping("/user/{userId}/messages")
    public Result<Page<Message>> getChatHistory(
        @PathVariable Long userId,
        @RequestParam Long otherUserId,
        @RequestParam(defaultValue = "1") Integer pageNum,
        @RequestParam(defaultValue = "20") Integer pageSize
    ) {
        logger.info("获取聊天记录: userId={}, otherUserId={}, page={}, size={}",
                    userId, otherUserId, pageNum, pageSize);

        try {
            Page<Message> page = messageService.getChatHistoryPaginated(
                userId, otherUserId, pageNum, pageSize
            );

            return Result.success(page);

        } catch (Exception e) {
            logger.error("获取聊天记录失败", e);
            return Result.error(500, "获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户列表（简化版，用于聊天记录查询界面）
     *
     * GET /api/admin/users/list?keyword=
     */
    @GetMapping("/users/list")
    public Result<List<User>> listUsersSimple(
        @RequestParam(required = false) String keyword
    ) {
        logger.info("查询用户列表（简化版）: keyword={}", keyword);

        try {
            IPage<User> page = userAdminService.listUsers(1, 100, keyword);
            return Result.success(page.getRecords());
        } catch (Exception e) {
            logger.error("查询用户列表失败", e);
            return Result.error(500, "查询失败: " + e.getMessage());
        }
    }
}
