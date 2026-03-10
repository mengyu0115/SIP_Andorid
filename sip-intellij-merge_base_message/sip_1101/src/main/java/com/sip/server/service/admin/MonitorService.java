package com.sip.server.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sip.server.entity.CallRecord;
import com.sip.server.entity.Conference;
import com.sip.server.entity.User;
import com.sip.server.mapper.CallRecordMapper;
import com.sip.server.mapper.ConferenceMapper;
import com.sip.server.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 系统监控服务
 *
 * 提供实时系统监控功能：
 * - 在线用户监控
 * - 系统性能指标（CPU、内存、线程）
 * - 实时通话监控
 * - 实时会议监控
 * - 消息发送统计
 * - 系统健康检查
 *
 * @author SIP Team - Member 4
 * @version 1.0
 */
@Service
public class MonitorService {

    private static final Logger logger = LoggerFactory.getLogger(MonitorService.class);

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CallRecordMapper callRecordMapper;

    @Autowired
    private ConferenceMapper conferenceMapper;

    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

    /**
     * 获取在线用户列表
     *
     * @return 在线用户列表
     */
    public List<User> getOnlineUsers() {
        logger.info("查询在线用户列表");
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", 1); // 1 = 在线
        queryWrapper.orderByDesc("last_login_time");
        List<User> users = userMapper.selectList(queryWrapper);
        logger.info("当前在线用户数: {}", users.size());
        return users;
    }

    /**
     * 获取在线用户数
     *
     * @return 在线用户数
     */
    public long getOnlineUserCount() {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", 1);
        Long count = userMapper.selectCount(queryWrapper);
        return count != null ? count : 0;
    }

    /**
     * 获取各状态用户数量
     *
     * @return Map<状态名称, 数量>
     */
    public Map<String, Long> getUserStatusDistribution() {
        logger.info("统计用户状态分布");
        List<User> allUsers = userMapper.selectList(null);

        Map<String, Long> distribution = new HashMap<>();
        distribution.put("离线", allUsers.stream().filter(u -> u.getStatus() == 0).count());
        distribution.put("在线", allUsers.stream().filter(u -> u.getStatus() == 1).count());
        distribution.put("忙碌", allUsers.stream().filter(u -> u.getStatus() == 2).count());
        distribution.put("离开", allUsers.stream().filter(u -> u.getStatus() == 3).count());

        logger.info("用户状态分布: {}", distribution);
        return distribution;
    }

    /**
     * 获取当前进行中的通话数量
     *
     * @return 进行中的通话数
     */
    public long getActiveCallCount() {
        QueryWrapper<CallRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.isNull("end_time"); // 结束时间为空表示通话中
        queryWrapper.eq("status", 1); // 状态为成功
        Long count = callRecordMapper.selectCount(queryWrapper);
        logger.info("当前通话数: {}", count);
        return count != null ? count : 0;
    }

    /**
     * 获取当前进行中的会议数量
     *
     * @return 进行中的会议数
     */
    public long getActiveConferenceCount() {
        QueryWrapper<Conference> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", 1); // 1 = 进行中
        Long count = conferenceMapper.selectCount(queryWrapper);
        logger.info("当前会议数: {}", count);
        return count != null ? count : 0;
    }

    /**
     * 获取今日消息发送数量
     *
     * @return 今日消息数
     */
    public long getTodayMessageCount() {
        // TODO: 需要实现 Message 和 MessageMapper
        logger.info("今日消息数: 0 (功能待实现)");
        return 0;
    }

    /**
     * 获取系统性能指标
     *
     * @return 性能指标Map
     */
    public Map<String, Object> getSystemMetrics() {
        logger.info("获取系统性能指标");
        Map<String, Object> metrics = new HashMap<>();

        // CPU负载
        double cpuLoad = osBean.getSystemLoadAverage();
        metrics.put("cpuLoad", cpuLoad);
        metrics.put("availableProcessors", osBean.getAvailableProcessors());

        // 内存使用情况
        long heapMemoryUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMemoryMax = memoryBean.getHeapMemoryUsage().getMax();
        double heapMemoryUsage = (heapMemoryUsed * 100.0) / heapMemoryMax;

        metrics.put("heapMemoryUsed", heapMemoryUsed / (1024 * 1024)); // MB
        metrics.put("heapMemoryMax", heapMemoryMax / (1024 * 1024)); // MB
        metrics.put("heapMemoryUsage", heapMemoryUsage); // %

        long nonHeapMemoryUsed = memoryBean.getNonHeapMemoryUsage().getUsed();
        metrics.put("nonHeapMemoryUsed", nonHeapMemoryUsed / (1024 * 1024)); // MB

        // 线程信息
        int threadCount = threadBean.getThreadCount();
        int peakThreadCount = threadBean.getPeakThreadCount();
        long totalStartedThreadCount = threadBean.getTotalStartedThreadCount();

        metrics.put("threadCount", threadCount);
        metrics.put("peakThreadCount", peakThreadCount);
        metrics.put("totalStartedThreadCount", totalStartedThreadCount);

        // JVM运行时间
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        metrics.put("uptimeMillis", uptime);
        metrics.put("uptimeMinutes", uptime / (1000 * 60));

        logger.info("系统指标: {}", metrics);
        return metrics;
    }

    /**
     * 获取系统摘要信息
     *
     * @return 系统摘要
     */
    public Map<String, Object> getSystemSummary() {
        logger.info("获取系统摘要");
        Map<String, Object> summary = new HashMap<>();

        // 用户统计
        summary.put("totalUsers", userMapper.selectCount(null));
        summary.put("onlineUsers", getOnlineUserCount());

        // 通话统计
        summary.put("totalCalls", callRecordMapper.selectCount(null));
        summary.put("activeCalls", getActiveCallCount());

        // 会议统计
        summary.put("totalConferences", conferenceMapper.selectCount(null));
        summary.put("activeConferences", getActiveConferenceCount());

        // 消息统计 (TODO: 需要实现 MessageMapper)
        summary.put("totalMessages", 0);
        summary.put("todayMessages", getTodayMessageCount());

        // 系统性能
        Map<String, Object> metrics = getSystemMetrics();
        summary.put("cpuLoad", metrics.get("cpuLoad"));
        summary.put("heapMemoryUsage", metrics.get("heapMemoryUsage"));
        summary.put("threadCount", metrics.get("threadCount"));

        // 系统运行时间
        summary.put("uptimeMinutes", metrics.get("uptimeMinutes"));

        logger.info("系统摘要: {}", summary);
        return summary;
    }

    /**
     * 获取最近活跃的用户
     *
     * @param limit 返回数量
     * @return 用户列表
     */
    public List<User> getRecentActiveUsers(int limit) {
        logger.info("获取最近活跃用户 TOP {}", limit);
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("last_login_time");
        queryWrapper.last("LIMIT " + limit);
        List<User> users = userMapper.selectList(queryWrapper);
        logger.info("查询到 {} 个活跃用户", users.size());
        return users;
    }

    /**
     * 获取最近的通话记录
     *
     * @param limit 返回数量
     * @return 通话记录列表
     */
    public List<CallRecord> getRecentCalls(int limit) {
        logger.info("获取最近通话记录 {} 条", limit);
        QueryWrapper<CallRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("start_time");
        queryWrapper.last("LIMIT " + limit);
        List<CallRecord> calls = callRecordMapper.selectList(queryWrapper);
        logger.info("查询到 {} 条通话记录", calls.size());
        return calls;
    }

    /**
     * 系统健康检查
     *
     * @return 健康状态Map
     */
    public Map<String, Object> healthCheck() {
        logger.info("执行系统健康检查");
        Map<String, Object> health = new HashMap<>();

        boolean isHealthy = true;
        List<String> warnings = new ArrayList<>();

        // 检查数据库连接
        try {
            userMapper.selectCount(null);
            health.put("database", "OK");
        } catch (Exception e) {
            health.put("database", "ERROR");
            warnings.add("Database connection failed");
            isHealthy = false;
        }

        // 检查内存使用
        Map<String, Object> metrics = getSystemMetrics();
        double heapUsage = (double) metrics.get("heapMemoryUsage");
        if (heapUsage > 90) {
            warnings.add("High memory usage: " + String.format("%.2f", heapUsage) + "%");
            isHealthy = false;
        }
        health.put("memoryUsage", heapUsage + "%");

        // 检查线程数
        int threadCount = (int) metrics.get("threadCount");
        if (threadCount > 500) {
            warnings.add("High thread count: " + threadCount);
            isHealthy = false;
        }
        health.put("threadCount", threadCount);

        // 整体健康状态
        health.put("status", isHealthy ? "HEALTHY" : "UNHEALTHY");
        health.put("warnings", warnings);
        health.put("timestamp", LocalDateTime.now());

        logger.info("健康检查完成: status={}, warnings={}", isHealthy ? "HEALTHY" : "UNHEALTHY", warnings.size());
        return health;
    }

    /**
     * 获取实时指标（用于实时监控面板）
     *
     * @return 实时指标
     */
    public Map<String, Object> getRealtimeMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        // 在线状态
        metrics.put("onlineUsers", getOnlineUserCount());
        metrics.put("activeCalls", getActiveCallCount());
        metrics.put("activeConferences", getActiveConferenceCount());

        // 系统性能
        Map<String, Object> sysMetrics = getSystemMetrics();
        metrics.put("cpuLoad", sysMetrics.get("cpuLoad"));
        metrics.put("memoryUsage", sysMetrics.get("heapMemoryUsage"));
        metrics.put("threadCount", sysMetrics.get("threadCount"));

        // 时间戳
        metrics.put("timestamp", System.currentTimeMillis());

        return metrics;
    }

    /**
     * 获取系统告警信息
     *
     * @return 告警列表
     */
    public List<Map<String, Object>> getSystemAlerts() {
        logger.info("检查系统告警");
        List<Map<String, Object>> alerts = new ArrayList<>();

        // 检查内存告警
        Map<String, Object> metrics = getSystemMetrics();
        double heapUsage = (double) metrics.get("heapMemoryUsage");
        if (heapUsage > 80) {
            Map<String, Object> alert = new HashMap<>();
            alert.put("type", "MEMORY");
            alert.put("level", heapUsage > 90 ? "CRITICAL" : "WARNING");
            alert.put("message", String.format("Memory usage is high: %.2f%%", heapUsage));
            alert.put("timestamp", LocalDateTime.now());
            alerts.add(alert);
        }

        // 检查线程告警
        int threadCount = (int) metrics.get("threadCount");
        if (threadCount > 400) {
            Map<String, Object> alert = new HashMap<>();
            alert.put("type", "THREAD");
            alert.put("level", threadCount > 500 ? "CRITICAL" : "WARNING");
            alert.put("message", String.format("Thread count is high: %d", threadCount));
            alert.put("timestamp", LocalDateTime.now());
            alerts.add(alert);
        }

        // 检查CPU告警
        double cpuLoad = (double) metrics.get("cpuLoad");
        if (cpuLoad > 0.8) {
            Map<String, Object> alert = new HashMap<>();
            alert.put("type", "CPU");
            alert.put("level", cpuLoad > 0.9 ? "CRITICAL" : "WARNING");
            alert.put("message", String.format("CPU load is high: %.2f", cpuLoad));
            alert.put("timestamp", LocalDateTime.now());
            alerts.add(alert);
        }

        logger.info("系统告警数: {}", alerts.size());
        return alerts;
    }
}
