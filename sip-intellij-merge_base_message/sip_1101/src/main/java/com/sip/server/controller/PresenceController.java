package com.sip.server.controller;

import com.sip.server.service.UserPresenceService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户在线状态 REST API
 * 替代 SIP PUBLISH/SUBSCRIBE，提供 HTTP 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/presence")
public class PresenceController {

    @Autowired
    private UserPresenceService userPresenceService;

    /**
     * 发布用户状态
     *
     * POST /api/presence/publish
     * Body: {"sipId": "101", "status": "online"}
     */
    @PostMapping("/publish")
    public ResponseEntity<?> publishStatus(@RequestBody PublishRequest request) {
        log.info("收到状态发布请求: sipId={}, status={}", request.getSipId(), request.getStatus());

        // 验证状态值
        if (!isValidStatus(request.getStatus())) {
            return ResponseEntity.badRequest().body("Invalid status: " + request.getStatus());
        }

        userPresenceService.publishStatus(request.getSipId(), request.getStatus());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Status published successfully");
        response.put("sipId", request.getSipId());
        response.put("status", request.getStatus());

        return ResponseEntity.ok(response);
    }

    /**
     * 获取单个用户状态
     *
     * GET /api/presence/status/{sipId}
     */
    @GetMapping("/status/{sipId}")
    public ResponseEntity<?> getStatus(@PathVariable String sipId) {
        String status = userPresenceService.getStatus(sipId);

        Map<String, Object> response = new HashMap<>();
        response.put("sipId", sipId);
        response.put("status", status);

        return ResponseEntity.ok(response);
    }

    /**
     * 批量获取用户状态
     *
     * POST /api/presence/statuses
     * Body: {"sipIds": ["101", "102", "103"]}
     */
    @PostMapping("/statuses")
    public ResponseEntity<?> getStatuses(@RequestBody StatusesRequest request) {
        Map<String, String> statuses = userPresenceService.getStatuses(request.getSipIds());

        Map<String, Object> response = new HashMap<>();
        response.put("statuses", statuses);

        return ResponseEntity.ok(response);
    }

    /**
     * 获取所有在线用户
     *
     * GET /api/presence/online
     */
    @GetMapping("/online")
    public ResponseEntity<?> getOnlineUsers() {
        return ResponseEntity.ok(userPresenceService.getOnlineUsers());
    }

    /**
     * 验证状态值是否合法
     */
    private boolean isValidStatus(String status) {
        return status.equals("online") || status.equals("busy") ||
                status.equals("away") || status.equals("offline");
    }

    /**
     * 发布状态请求
     */
    @Data
    public static class PublishRequest {
        private String sipId;
        private String status;
    }

    /**
     * 批量查询状态请求
     */
    @Data
    public static class StatusesRequest {
        private List<String> sipIds;
    }
}
