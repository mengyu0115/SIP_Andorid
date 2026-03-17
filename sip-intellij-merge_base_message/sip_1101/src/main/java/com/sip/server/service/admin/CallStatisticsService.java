package com.sip.server.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sip.server.entity.CallRecord;
import com.sip.server.mapper.CallRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 通话统计服务
 *
 * 提供通话数据统计分析功能：
 * - 总通话次数/时长
 * - 平均通话时长
 * - 通话成功率
 * - 通话类型分布
 * - 通话质量分析
 * - 时段分析
 * - 导出报表
 *
 * @author SIP Team - Member 4
 * @version 1.0
 */
@Service
public class CallStatisticsService {

    private static final Logger logger = LoggerFactory.getLogger(CallStatisticsService.class);

    @Autowired
    private CallRecordMapper callRecordMapper;

    /**
     * 获取总通话次数
     *
     * @return 总通话次数
     */
    public long getTotalCallCount() {
        Long count = callRecordMapper.selectCount(null);
        logger.info("总通话次数: {}", count);
        return count != null ? count : 0;
    }

    /**
     * 获取成功通话次数
     *
     * @return 成功通话次数
     */
    public long getSuccessCallCount() {
        QueryWrapper<CallRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", 1); // 1 = 成功
        Long count = callRecordMapper.selectCount(queryWrapper);
        logger.info("成功通话次数: {}", count);
        return count != null ? count : 0;
    }

    /**
     * 获取通话成功率
     *
     * @return 通话成功率 (0-100)
     */
    public double getCallSuccessRate() {
        long total = getTotalCallCount();
        if (total == 0) {
            return 0.0;
        }
        long success = getSuccessCallCount();
        double rate = (success * 100.0) / total;
        logger.info("通话成功率: {:.2f}%", rate);
        return rate;
    }

    /**
     * 获取总通话时长（分钟）
     *
     * @return 总通话时长
     */
    public long getTotalCallDuration() {
        QueryWrapper<CallRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("IFNULL(SUM(duration), 0) as total_duration");
        queryWrapper.eq("status", 1); // 只统计成功的通话

        List<Map<String, Object>> result = callRecordMapper.selectMaps(queryWrapper);
        long totalSeconds = 0;
        if (!result.isEmpty()) {
            Object value = result.get(0).get("total_duration");
            totalSeconds = value != null ? Long.parseLong(value.toString()) : 0;
        }

        long totalMinutes = totalSeconds / 60;
        logger.info("总通话时长: {} 分钟 ({} 秒)", totalMinutes, totalSeconds);
        return totalMinutes;
    }

    /**
     * 获取平均通话时长（秒）
     *
     * @return 平均通话时长
     */
    public double getAvgCallDuration() {
        QueryWrapper<CallRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("IFNULL(AVG(duration), 0) as avg_duration");
        queryWrapper.eq("status", 1); // 只统计成功的通话

        List<Map<String, Object>> result = callRecordMapper.selectMaps(queryWrapper);
        double avgDuration = 0.0;
        if (!result.isEmpty()) {
            Object value = result.get(0).get("avg_duration");
            avgDuration = value != null ? Double.parseDouble(value.toString()) : 0.0;
        }

        logger.info("平均通话时长: {:.2f} 秒", avgDuration);
        return avgDuration;
    }

    /**
     * 获取今日通话次数
     *
     * @return 今日通话次数
     */
    public long getTodayCallCount() {
        QueryWrapper<CallRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.apply("DATE(start_time) = CURDATE()");
        Long count = callRecordMapper.selectCount(queryWrapper);
        logger.info("今日通话次数: {}", count);
        return count != null ? count : 0;
    }

    /**
     * 获取指定日期范围的通话次数
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 通话次数
     */
    public long getCallCountByDateRange(LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = LocalDateTime.of(startDate, LocalTime.MIN);
        LocalDateTime endDateTime = LocalDateTime.of(endDate, LocalTime.MAX);

        QueryWrapper<CallRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.between("start_time", startDateTime, endDateTime);

        Long count = callRecordMapper.selectCount(queryWrapper);
        logger.info("日期范围 {} 到 {} 的通话次数: {}", startDate, endDate, count);
        return count != null ? count : 0;
    }

    /**
     * 获取通话类型分布
     *
     * @return Map<类型名称, 数量>
     */
    public Map<String, Long> getCallTypeDistribution() {
        logger.info("统计通话类型分布");

        List<CallRecord> allCalls = callRecordMapper.selectList(null);
        Map<String, Long> distribution = new HashMap<>();

        distribution.put("音频", allCalls.stream().filter(c -> c.getCallType() == 1).count());
        distribution.put("视频", allCalls.stream().filter(c -> c.getCallType() == 2).count());
        distribution.put("会议", allCalls.stream().filter(c -> c.getCallType() == 3).count());

        logger.info("通话类型分布: {}", distribution);
        return distribution;
    }

    /**
     * 获取通话状态分布
     *
     * @return Map<状态名称, 数量>
     */
    public Map<String, Long> getCallStatusDistribution() {
        logger.info("统计通话状态分布");

        List<CallRecord> allCalls = callRecordMapper.selectList(null);
        Map<String, Long> distribution = new HashMap<>();

        distribution.put("成功", allCalls.stream().filter(c -> c.getStatus() == 1).count());
        distribution.put("未接", allCalls.stream().filter(c -> c.getStatus() == 2).count());
        distribution.put("拒绝", allCalls.stream().filter(c -> c.getStatus() == 3).count());
        distribution.put("取消", allCalls.stream().filter(c -> c.getStatus() == 4).count());
        distribution.put("失败", allCalls.stream().filter(c -> c.getStatus() == 5).count());

        logger.info("通话状态分布: {}", distribution);
        return distribution;
    }

    /**
     * 获取通话质量分布
     *
     * @return Map<质量等级, 数量>
     */
    public Map<String, Long> getCallQualityDistribution() {
        logger.info("统计通话质量分布");

        QueryWrapper<CallRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.isNotNull("quality");
        List<CallRecord> callsWithQuality = callRecordMapper.selectList(queryWrapper);

        Map<String, Long> distribution = new HashMap<>();
        distribution.put("差", callsWithQuality.stream().filter(c -> c.getQuality() == 1).count());
        distribution.put("中", callsWithQuality.stream().filter(c -> c.getQuality() == 2).count());
        distribution.put("良", callsWithQuality.stream().filter(c -> c.getQuality() == 3).count());
        distribution.put("优", callsWithQuality.stream().filter(c -> c.getQuality() == 4).count());

        logger.info("通话质量分布: {}", distribution);
        return distribution;
    }

    /**
     * 获取最近N天的每日通话统计
     *
     * @param days 天数
     * @return List<Map<日期, 数据>>
     */
    public List<Map<String, Object>> getDailyCallStats(int days) {
        logger.info("获取最近 {} 天的通话统计", days);

        List<Map<String, Object>> stats = new ArrayList<>();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            Map<String, Object> dayStat = new HashMap<>();
            dayStat.put("date", date.format(DateTimeFormatter.ISO_DATE));
            dayStat.put("count", getCallCountByDateRange(date, date));
            stats.add(dayStat);
        }

        return stats;
    }

    /**
     * 获取通话时段分布（24小时）
     *
     * @return Map<小时, 通话次数>
     */
    public Map<Integer, Long> getCallHourDistribution() {
        logger.info("统计通话时段分布");

        List<CallRecord> allCalls = callRecordMapper.selectList(null);
        Map<Integer, Long> distribution = allCalls.stream()
            .filter(c -> c.getStartTime() != null)
            .collect(Collectors.groupingBy(
                c -> c.getStartTime().getHour(),
                Collectors.counting()
            ));

        logger.info("通话时段分布: {}", distribution);
        return distribution;
    }

    /**
     * 获取热门通话用户 TOP N
     *
     * @param topN 返回前N个
     * @return List<Map<用户ID, 通话次数>>
     */
    public List<Map<String, Object>> getTopCallers(int topN) {
        logger.info("获取通话次数 TOP {}", topN);

        List<CallRecord> allCalls = callRecordMapper.selectList(null);

        // 统计每个用户的通话次数（作为主叫）
        Map<Long, Long> callerCounts = allCalls.stream()
            .collect(Collectors.groupingBy(
                CallRecord::getCallerId,
                Collectors.counting()
            ));

        // 排序并取TOP N
        List<Map<String, Object>> topCallers = callerCounts.entrySet().stream()
            .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
            .limit(topN)
            .map(entry -> {
                Map<String, Object> map = new HashMap<>();
                map.put("userId", entry.getKey());
                map.put("callCount", entry.getValue());
                return map;
            })
            .collect(Collectors.toList());

        logger.info("TOP {} 用户: {}", topN, topCallers);
        return topCallers;
    }

    /**
     * 获取平均通话质量分数
     *
     * @return 平均质量分数 (1-4)
     */
    public double getAvgCallQuality() {
        QueryWrapper<CallRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("IFNULL(AVG(quality), 0) as avg_quality");
        queryWrapper.isNotNull("quality");

        List<Map<String, Object>> result = callRecordMapper.selectMaps(queryWrapper);
        double avgQuality = 0.0;
        if (!result.isEmpty()) {
            Object value = result.get(0).get("avg_quality");
            avgQuality = value != null ? Double.parseDouble(value.toString()) : 0.0;
        }

        logger.info("平均通话质量: {:.2f}", avgQuality);
        return avgQuality;
    }

    /**
     * 获取RTP统计信息
     *
     * @return Map<统计项, 值>
     */
    public Map<String, Object> getRtpStatistics() {
        logger.info("统计RTP数据");

        QueryWrapper<CallRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.select(
            "IFNULL(SUM(rtp_packets_sent), 0) as total_packets_sent",
            "IFNULL(SUM(rtp_packets_received), 0) as total_packets_received",
            "IFNULL(SUM(rtp_packets_lost), 0) as total_packets_lost",
            "IFNULL(AVG(avg_jitter), 0) as avg_jitter"
        );
        queryWrapper.eq("status", 1); // 只统计成功的通话

        List<Map<String, Object>> result = callRecordMapper.selectMaps(queryWrapper);
        Map<String, Object> stats = new HashMap<>();

        if (!result.isEmpty()) {
            Map<String, Object> row = result.get(0);
            stats.put("totalPacketsSent", row.get("total_packets_sent"));
            stats.put("totalPacketsReceived", row.get("total_packets_received"));
            stats.put("totalPacketsLost", row.get("total_packets_lost"));
            stats.put("avgJitter", row.get("avg_jitter"));

            // 计算丢包率
            long sent = Long.parseLong(row.get("total_packets_sent").toString());
            long lost = Long.parseLong(row.get("total_packets_lost").toString());
            double lossRate = sent > 0 ? (lost * 100.0 / sent) : 0.0;
            stats.put("packetLossRate", lossRate);
        }

        logger.info("RTP统计: {}", stats);
        return stats;
    }

    /**
     * 导出统计报表（CSV格式）
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return CSV字符串
     */
    public String exportStatisticsReport(LocalDate startDate, LocalDate endDate) {
        logger.info("导出统计报表: {} 到 {}", startDate, endDate);

        LocalDateTime startDateTime = LocalDateTime.of(startDate, LocalTime.MIN);
        LocalDateTime endDateTime = LocalDateTime.of(endDate, LocalTime.MAX);

        QueryWrapper<CallRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.between("start_time", startDateTime, endDateTime);
        queryWrapper.orderByDesc("start_time");

        List<CallRecord> records = callRecordMapper.selectList(queryWrapper);

        // 生成CSV
        StringBuilder csv = new StringBuilder();
        csv.append("ID,主叫ID,被叫ID,通话类型,开始时间,结束时间,时长(秒),状态,质量\n");

        for (CallRecord record : records) {
            csv.append(record.getId()).append(",");
            csv.append(record.getCallerId()).append(",");
            csv.append(record.getCalleeId()).append(",");
            csv.append(getCallTypeName(record.getCallType())).append(",");
            csv.append(record.getStartTime()).append(",");
            csv.append(record.getEndTime()).append(",");
            csv.append(record.getDuration()).append(",");
            csv.append(getCallStatusName(record.getStatus())).append(",");
            csv.append(getCallQualityName(record.getQuality())).append("\n");
        }

        logger.info("报表导出完成，共 {} 条记录", records.size());
        return csv.toString();
    }

    // 辅助方法：获取通话类型名称
    private String getCallTypeName(Integer type) {
        if (type == null) return "未知";
        switch (type) {
            case 1: return "音频";
            case 2: return "视频";
            case 3: return "会议";
            default: return "未知";
        }
    }

    // 辅助方法：获取通话状态名称
    private String getCallStatusName(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 1: return "成功";
            case 2: return "未接";
            case 3: return "拒绝";
            case 4: return "取消";
            case 5: return "失败";
            default: return "未知";
        }
    }

    // 辅助方法：获取通话质量名称
    private String getCallQualityName(Integer quality) {
        if (quality == null) return "未知";
        switch (quality) {
            case 1: return "差";
            case 2: return "中";
            case 3: return "良";
            case 4: return "优";
            default: return "未知";
        }
    }
}
