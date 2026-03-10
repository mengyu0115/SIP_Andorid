package com.sip.server.controller;

import com.sip.common.result.Result;
import com.sip.client.core.SipManager;
import com.sip.client.media.MediaManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试控制器 - 用于验证核心模块集成
 *
 * @author SIP Team - Member 4
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/test")
public class TestController {

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("服务运行正常");
    }

    /**
     * 检查核心模块状态
     */
    @GetMapping("/core-status")
    public Result<Map<String, Object>> getCoreStatus() {
        log.info("检查核心模块状态");

        Map<String, Object> status = new HashMap<>();

        try {
            // 检查 SipManager
            SipManager sipManager = SipManager.getInstance();
            boolean sipInitialized = sipManager.isInitialized();

            Map<String, Object> sipStatus = new HashMap<>();
            sipStatus.put("initialized", sipInitialized);
            if (sipInitialized) {
                sipStatus.put("localIp", sipManager.getLocalIp());
                sipStatus.put("localPort", sipManager.getLocalPort());
                sipStatus.put("status", "✓ 已初始化");
            } else {
                sipStatus.put("status", "✗ 未初始化");
            }
            status.put("sipManager", sipStatus);

        } catch (Exception e) {
            log.error("获取 SipManager 状态失败", e);
            Map<String, Object> sipStatus = new HashMap<>();
            sipStatus.put("status", "✗ 错误: " + e.getMessage());
            status.put("sipManager", sipStatus);
        }

        try {
            // 检查 MediaManager
            MediaManager mediaManager = MediaManager.getInstance();
            boolean mediaInitialized = mediaManager.isInitialized();

            Map<String, Object> mediaStatus = new HashMap<>();
            mediaStatus.put("initialized", mediaInitialized);
            if (mediaInitialized) {
                mediaStatus.put("localIp", mediaManager.getLocalIp());
                mediaStatus.put("audioPort", mediaManager.getCurrentAudioPort());
                mediaStatus.put("videoPort", mediaManager.getCurrentVideoPort());
                mediaStatus.put("status", "✓ 已初始化");
            } else {
                mediaStatus.put("status", "✗ 未初始化");
            }
            status.put("mediaManager", mediaStatus);

        } catch (Exception e) {
            log.error("获取 MediaManager 状态失败", e);
            Map<String, Object> mediaStatus = new HashMap<>();
            mediaStatus.put("status", "✗ 错误: " + e.getMessage());
            status.put("mediaManager", mediaStatus);
        }

        return Result.success("核心模块状态", status);
    }

    /**
     * 获取媒体统计信息
     */
    @GetMapping("/media-stats")
    public Result<String> getMediaStats() {
        try {
            MediaManager mediaManager = MediaManager.getInstance();
            if (!mediaManager.isInitialized()) {
                return Result.error("MediaManager 未初始化");
            }

            String stats = mediaManager.getRtcpStats();
            return Result.success("媒体统计信息", stats);

        } catch (Exception e) {
            log.error("获取媒体统计失败", e);
            return Result.error("获取媒体统计失败: " + e.getMessage());
        }
    }
}
