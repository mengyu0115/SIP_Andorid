package com.sip.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sip.common.dto.CallRecordDTO;
import com.sip.server.entity.CallRecord;
import com.sip.server.entity.User;
import com.sip.server.mapper.CallRecordMapper;
import com.sip.server.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 通话记录服务
 *
 * @author SIP Team
 * @version 1.0
 */
@Slf4j
@Service
public class CallRecordService {

    @Autowired
    private CallRecordMapper callRecordMapper;

    @Autowired
    private UserMapper userMapper;

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 保存通话记录
     *
     * @param dto 通话记录DTO
     * @return 保存的记录
     */
    public CallRecord saveCallRecord(CallRecordDTO dto) {
        log.info("保存通话记录: {} -> {}, 类型: {}, 时长: {}秒",
                dto.getCallerUsername(), dto.getCalleeUsername(), dto.getCallType(), dto.getDuration());

        // 1. 根据用户名查询用户ID
        User caller = getUserByUsername(dto.getCallerUsername());
        User callee = getUserByUsername(dto.getCalleeUsername());

        if (caller == null) {
            log.error("找不到主叫用户: {}", dto.getCallerUsername());
            throw new RuntimeException("主叫用户不存在: " + dto.getCallerUsername());
        }

        if (callee == null) {
            log.error("找不到被叫用户: {}", dto.getCalleeUsername());
            throw new RuntimeException("被叫用户不存在: " + dto.getCalleeUsername());
        }

        // 2. 转换callType (audio -> 1, video -> 2)
        Integer callType = convertCallType(dto.getCallType());

        // 3. 解析时间
        LocalDateTime startTime = LocalDateTime.parse(dto.getStartTime(), DATE_TIME_FORMATTER);
        LocalDateTime endTime = LocalDateTime.parse(dto.getEndTime(), DATE_TIME_FORMATTER);

        // 4. 创建CallRecord实体
        CallRecord callRecord = new CallRecord();
        callRecord.setCallerId(caller.getId());
        callRecord.setCalleeId(callee.getId());
        callRecord.setCallType(callType);
        callRecord.setStartTime(startTime);
        callRecord.setEndTime(endTime);
        callRecord.setDuration(dto.getDuration().intValue());
        callRecord.setStatus(1); // 1-成功（通话结束后保存的都是成功的）
        callRecord.setCreateTime(LocalDateTime.now());

        // 5. 保存到数据库
        callRecordMapper.insert(callRecord);

        log.info("✅ 通话记录已保存: ID={}", callRecord.getId());
        return callRecord;
    }

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户实体
     */
    private User getUserByUsername(String username) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        return userMapper.selectOne(queryWrapper);
    }

    /**
     * 转换通话类型
     *
     * @param callType 字符串类型 (audio/video)
     * @return 数字类型 (1-音频 2-视频)
     */
    private Integer convertCallType(String callType) {
        if ("audio".equalsIgnoreCase(callType)) {
            return 1;
        } else if ("video".equalsIgnoreCase(callType)) {
            return 2;
        } else {
            log.warn("未知的通话类型: {}, 默认使用音频(1)", callType);
            return 1;
        }
    }

    /**
     * 分页查询通话记录
     *
     * @param userId 用户ID（可选，null表示查询所有）
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public Page<CallRecord> getCallRecords(Long userId, Integer pageNum, Integer pageSize) {
        Page<CallRecord> page = new Page<>(pageNum, pageSize);

        QueryWrapper<CallRecord> queryWrapper = new QueryWrapper<>();
        if (userId != null) {
            // 查询该用户作为主叫或被叫的所有记录
            queryWrapper.and(wrapper ->
                wrapper.eq("caller_id", userId).or().eq("callee_id", userId)
            );
        }

        // 按开始时间倒序排列
        queryWrapper.orderByDesc("start_time");

        return callRecordMapper.selectPage(page, queryWrapper);
    }

    /**
     * 根据ID查询通话记录
     *
     * @param id 记录ID
     * @return 通话记录
     */
    public CallRecord getCallRecordById(Long id) {
        return callRecordMapper.selectById(id);
    }
}
