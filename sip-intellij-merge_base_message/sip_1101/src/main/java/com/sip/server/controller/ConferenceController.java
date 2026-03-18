package com.sip.server.controller;

import com.sip.common.result.Result;
import com.sip.server.entity.Conference;
import com.sip.server.entity.ConferenceParticipant;
import com.sip.server.entity.User;
import com.sip.server.service.ConferenceServiceSimplified;
import com.sip.server.service.UserService;
import com.sip.server.websocket.ConferenceWebSocketService;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会议管理 Controller（简化版）
 *
 * 提供会议数据管理的 REST API
 * 注意：实际的 SIP 会议逻辑由客户端处理
 *
 * @author SIP Team
 * @version 2.0 (Simplified)
 */
@RestController
@RequestMapping("/api/conference")
public class ConferenceController {

    private static final Logger logger = LoggerFactory.getLogger(ConferenceController.class);

    @Autowired
    private ConferenceServiceSimplified conferenceService;

    @Autowired
    private UserService userService;

    @Autowired(required = false)
    private ConferenceWebSocketService webSocketService;

    /**
     * 创建会议记录
     */
    @PostMapping("/create")
    public Result<Conference> createConference(@RequestBody CreateRequest request) {
        logger.info("创建会议: title={}", request.getTitle());

        try {
            Conference conference = conferenceService.createConference(
                request.getCreatorId(),
                request.getTitle()
            );
            return Result.success("会议创建成功", conference);
        } catch (Exception e) {
            logger.error("创建会议失败", e);
            return Result.error(500, "创建失败: " + e.getMessage());
        }
    }

    /**
     * 添加参与者（支持会议ID或会议号）
     */
    @PostMapping("/join")
    public Result<Conference> joinConference(@RequestBody JoinRequest request) {
        logger.info("加入会议: conferenceId={}, conferenceCode={}, userId={}",
            request.getConferenceId(), request.getConferenceCode(), request.getUserId());

        try {
            Conference conference = null;

            // 优先使用会议号
            if (request.getConferenceCode() != null && !request.getConferenceCode().isEmpty()) {
                conference = conferenceService.getConferenceByCode(request.getConferenceCode());
            } else if (request.getConferenceId() != null) {
                conference = conferenceService.getConferenceById(request.getConferenceId());
                if (conference == null) {
                    return Result.error(404, "会议不存在");
                }
                // 检查会议是否活跃
                if (!conference.getIsActive()) {
                    return Result.error(404, "会议已结束");
                }
            } else {
                return Result.error(400, "请提供会议ID或会议号");
            }

            conferenceService.addParticipant(
                conference.getId(),
                request.getUserId(),
                0  // 0-普通参与者
            );

            // 🆕 WebSocket 广播：参与者加入
            if (webSocketService != null) {
                try {
                    User user = userService.getUserById(request.getUserId());
                    String username = user != null ? user.getUsername() : "用户" + request.getUserId();
                    webSocketService.broadcastParticipantJoined(
                        conference.getId(),
                        request.getUserId(),
                        username
                    );
                    logger.info("✅ 已广播参与者加入事件");
                } catch (Exception e) {
                    logger.warn("广播参与者加入失败，但不影响加入操作: {}", e.getMessage());
                }
            }

            return Result.success("加入成功", conference);
        } catch (IllegalArgumentException e) {
            // 会议不存在或已结束
            logger.error("加入会议失败: {}", e.getMessage());
            return Result.error(404, e.getMessage());
        } catch (Exception e) {
            logger.error("加入会议失败", e);
            return Result.error(500, "加入失败: " + e.getMessage());
        }
    }

    /**
     * 查询会议信息（通过ID）
     */
    @GetMapping("/{conferenceId}")
    public Result<Conference> getConference(@PathVariable Long conferenceId) {
        logger.info("查询会议: conferenceId={}", conferenceId);

        try {
            Conference conference = conferenceService.getConferenceById(conferenceId);
            if (conference == null) {
                return Result.error(404, "会议不存在");
            }
            return Result.success("查询成功", conference);
        } catch (Exception e) {
            logger.error("查询会议失败", e);
            return Result.error(500, "查询失败: " + e.getMessage());
        }
    }

    /**
     * 查询活跃会议（通过会议号）
     */
    @GetMapping("/code/{conferenceCode}")
    public Result<Conference> getConferenceByCode(@PathVariable String conferenceCode) {
        logger.info("通过会议号查询会议: conferenceCode={}", conferenceCode);

        try {
            Conference conference = conferenceService.getConferenceByCode(conferenceCode);
            return Result.success("查询成功", conference);
        } catch (IllegalArgumentException e) {
            logger.warn("会议号无效: {}", e.getMessage());
            return Result.error(404, e.getMessage());
        } catch (Exception e) {
            logger.error("查询会议失败", e);
            return Result.error(500, "查询失败: " + e.getMessage());
        }
    }

    /**
     * 查询会议参与者
     */
    @GetMapping("/{conferenceId}/participants")
    public Result<List<ConferenceParticipant>> getParticipants(@PathVariable Long conferenceId) {
        logger.info("查询参与者: conferenceId={}", conferenceId);

        try {
            List<ConferenceParticipant> participants = conferenceService.getParticipants(conferenceId);
            return Result.success("查询成功", participants);
        } catch (Exception e) {
            logger.error("查询参与者失败", e);
            return Result.error(500, "查询失败: " + e.getMessage());
        }
    }

    /**
     * 更新会议状态
     */
    @PutMapping("/{conferenceId}/status")
    public Result<String> updateStatus(@PathVariable Long conferenceId,
                                       @RequestBody StatusRequest request) {
        logger.info("更新会议状态: conferenceId={}, status={}", conferenceId, request.getStatus());

        try {
            conferenceService.updateStatus(conferenceId, request.getStatus());

            // 🆕 WebSocket 广播：状态变化
            if (webSocketService != null) {
                try {
                    String statusDesc = getStatusDescription(request.getStatus());
                    webSocketService.broadcastConferenceStatusChanged(
                        conferenceId,
                        request.getStatus(),
                        statusDesc
                    );
                    logger.info("✅ 已广播会议状态变化");
                } catch (Exception e) {
                    logger.warn("广播状态变化失败: {}", e.getMessage());
                }
            }

            return Result.success("更新成功", null);
        } catch (Exception e) {
            logger.error("更新状态失败", e);
            return Result.error(500, "更新失败: " + e.getMessage());
        }
    }

    /**
     * 结束会议（标记为非活跃）
     */
    @PostMapping("/{conferenceId}/end")
    public Result<String> endConference(@PathVariable Long conferenceId) {
        logger.info("结束会议: conferenceId={}", conferenceId);

        try {
            // 🆕 WebSocket 广播：会议结束（在实际结束之前发送，确保参与者收到通知）
            if (webSocketService != null) {
                try {
                    webSocketService.broadcastConferenceEnded(conferenceId, "主持人结束了会议");
                    logger.info("✅ 已广播会议结束事件");
                } catch (Exception e) {
                    logger.warn("广播会议结束失败: {}", e.getMessage());
                }
            }

            conferenceService.endConference(conferenceId);
            return Result.success("会议已结束", null);
        } catch (Exception e) {
            logger.error("结束会议失败", e);
            return Result.error(500, "结束失败: " + e.getMessage());
        }
    }

    /**
     * 查询用户的会议列表
     */
    @GetMapping("/user/{creatorId}")
    public Result<List<Conference>> getUserConferences(@PathVariable Long creatorId) {
        logger.info("查询用户会议: creatorId={}", creatorId);

        try {
            List<Conference> conferences = conferenceService.getUserConferences(creatorId);
            return Result.success("查询成功", conferences);
        } catch (Exception e) {
            logger.error("查询用户会议失败", e);
            return Result.error(500, "查询失败: " + e.getMessage());
        }
    }

    /**
     * 查询所有会议（含时长、创建者用户名、参与者用户名，供前端管理页面使用）
     *
     * GET /api/conference/list
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> listAllConferences() {
        logger.info("查询所有会议记录");

        try {
            List<Conference> all = conferenceService.getAllConferences();
            List<Map<String, Object>> result = new ArrayList<>();

            for (Conference conf : all) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", conf.getId());
                item.put("conferenceCode", conf.getConferenceCode());
                item.put("title", conf.getTitle());
                item.put("startTime", conf.getStartTime());
                item.put("endTime", conf.getEndTime());
                item.put("status", conf.getStatus());
                item.put("isActive", conf.getIsActive());

                // 计算会议时长（秒）
                if (conf.getStartTime() != null && conf.getEndTime() != null) {
                    long durationSeconds = Duration.between(conf.getStartTime(), conf.getEndTime()).getSeconds();
                    item.put("duration", durationSeconds);
                } else {
                    item.put("duration", null);
                }

                // 创建者用户名
                User creator = userService.getUserById(conf.getCreatorId());
                item.put("creatorName", creator != null ? creator.getUsername() : "未知用户");

                // 参与者用户名列表
                List<ConferenceParticipant> participants = conferenceService.getParticipants(conf.getId());
                List<String> participantNames = new ArrayList<>();
                for (ConferenceParticipant p : participants) {
                    User pUser = userService.getUserById(p.getUserId());
                    participantNames.add(pUser != null ? pUser.getUsername() : "未知用户");
                }
                item.put("participants", participantNames);
                item.put("participantCount", participantNames.size());

                result.add(item);
            }

            return Result.success(result);
        } catch (Exception e) {
            logger.error("查询所有会议失败", e);
            return Result.error(500, "查询失败: " + e.getMessage());
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 获取状态描述
     */
    private String getStatusDescription(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "已结束";
            case 1: return "进行中";
            case 2: return "待开始";
            default: return "未知状态";
        }
    }

    // ========== 请求对象 ==========

    @Data
    public static class CreateRequest {
        private Long creatorId;
        private String title;
    }

    @Data
    public static class JoinRequest {
        private Long conferenceId;      // 会议ID（可选）
        private String conferenceCode;  // 会议号（可选，优先使用）
        private Long userId;            // 用户ID（必填）
    }

    @Data
    public static class StatusRequest {
        private Integer status;  // 0-已结束 1-进行中 2-待开始
    }
}
