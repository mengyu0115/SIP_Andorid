package com.sip.server.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sip.common.dto.CallRecordDTO;
import com.sip.common.result.Result;
import com.sip.server.entity.CallRecord;
import com.sip.server.response.ApiResponse;
import com.sip.server.service.CallRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
}
