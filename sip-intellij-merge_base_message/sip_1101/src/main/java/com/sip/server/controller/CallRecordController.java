package com.sip.server.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sip.common.dto.CallRecordDTO;
import com.sip.common.result.Result;
import com.sip.server.entity.CallRecord;
import com.sip.server.entity.User;
import com.sip.server.mapper.UserMapper;
import com.sip.server.response.ApiResponse;
import com.sip.server.service.CallRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通话记录控制器
 * 提供通话记录的保存和查询接口
 *
 * @author SIP Team
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/call")
public class CallRecordController {

    @Autowired
    private CallRecordService callRecordService;

    @Autowired
    private UserMapper userMapper;

    /**
     * 保存通话记录
     *
     * @param dto 通话记录DTO
     * @return 保存结果
     */
    @PostMapping("/record")
    public ApiResponse<CallRecord> saveCallRecord(@RequestBody CallRecordDTO dto) {
        try {
            log.info("收到保存通话记录请求: {} -> {}", dto.getCallerUsername(), dto.getCalleeUsername());

            CallRecord callRecord = callRecordService.saveCallRecord(dto);

            return ApiResponse.success(callRecord);

        } catch (Exception e) {
            log.error("保存通话记录失败", e);
            return ApiResponse.error("保存通话记录失败: " + e.getMessage());
        }
    }

    /**
     * 分页查询通话记录
     *
     * @param userId 用户ID（可选）
     * @param pageNum 页码（默认1）
     * @param pageSize 每页大小（默认10）
     * @return 分页结果
     */
    @GetMapping("/records")
    public ApiResponse<Page<CallRecord>> getCallRecords(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {

        try {
            log.info("查询通话记录: userId={}, page={}, size={}", userId, pageNum, pageSize);

            Page<CallRecord> page = callRecordService.getCallRecords(userId, pageNum, pageSize);

            return ApiResponse.success(page);

        } catch (Exception e) {
            log.error("查询通话记录失败", e);
            return ApiResponse.error("查询通话记录失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询通话记录
     *
     * @param id 记录ID
     * @return 通话记录
     */
    @GetMapping("/record/{id}")
    public ApiResponse<CallRecord> getCallRecord(@PathVariable Long id) {
        try {
            CallRecord callRecord = callRecordService.getCallRecordById(id);

            if (callRecord == null) {
                return ApiResponse.error("通话记录不存在");
            }

            return ApiResponse.success(callRecord);

        } catch (Exception e) {
            log.error("查询通话记录失败", e);
            return ApiResponse.error("查询通话记录失败: " + e.getMessage());
        }
    }

    /**
     * 分页查询通话记录（带用户名，供前端管理页面使用）
     *
     * GET /api/call/records-with-name
     */
    @GetMapping("/records-with-name")
    public ApiResponse<Map<String, Object>> getCallRecordsWithName(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {

        try {
            log.info("查询通话记录（带用户名）: userId={}, page={}, size={}", userId, pageNum, pageSize);

            Page<CallRecord> page = callRecordService.getCallRecords(userId, pageNum, pageSize);

            List<Map<String, Object>> records = new ArrayList<>();
            for (CallRecord record : page.getRecords()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", record.getId());
                item.put("callType", record.getCallType());
                item.put("startTime", record.getStartTime());
                item.put("endTime", record.getEndTime());
                item.put("duration", record.getDuration());
                item.put("status", record.getStatus());

                // 关联用户名
                User caller = userMapper.selectById(record.getCallerId());
                User callee = userMapper.selectById(record.getCalleeId());
                item.put("callerName", caller != null ? caller.getUsername() : "未知用户");
                item.put("calleeName", callee != null ? callee.getUsername() : "未知用户");

                records.add(item);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("records", records);
            result.put("total", page.getTotal());
            result.put("pages", page.getPages());
            result.put("current", page.getCurrent());

            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("查询通话记录失败", e);
            return ApiResponse.error("查询通话记录失败: " + e.getMessage());
        }
    }
}
