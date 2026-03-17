package com.sip.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sip.server.entity.Conference;
import com.sip.server.entity.ConferenceParticipant;
import com.sip.server.mapper.ConferenceMapper;
import com.sip.server.mapper.ConferenceParticipantMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

/**
 * 会议服务层（简化版 - 只做数据库操作）
 *
 * 注意：这个版本只负责会议数据的存储和查询
 * 实际的 SIP 会议逻辑由客户端的 ConferenceManager 处理
 *
 * @author SIP Team
 * @version 2.0 (Simplified)
 */
@Slf4j
@Service
public class ConferenceServiceSimplified {

    @Autowired
    private ConferenceMapper conferenceMapper;

    @Autowired
    private ConferenceParticipantMapper participantMapper;

    private static final Random random = new Random();

    /**
     * 生成6位不重复的会议号
     */
    private String generateConferenceCode() {
        for (int i = 0; i < 10; i++) {  // 最多尝试10次
            String code = String.format("%06d", random.nextInt(1000000));

            // 检查会议号是否已被使用
            QueryWrapper<Conference> wrapper = new QueryWrapper<>();
            wrapper.eq("conference_code", code);
            wrapper.eq("is_active", true);  // 只检查活跃会议

            if (conferenceMapper.selectOne(wrapper) == null) {
                return code;
            }
        }
        throw new RuntimeException("无法生成唯一会议号,请稍后重试");
    }

    /**
     * 创建会议记录
     */
    @Transactional
    public Conference createConference(Long creatorId, String title) {
        log.info("创建会议记录: creatorId={}, title={}", creatorId, title);

        String conferenceCode = generateConferenceCode();
        String conferenceUri = "sip:conf-" + System.currentTimeMillis() + "@localhost";

        Conference conference = new Conference();
        conference.setConferenceCode(conferenceCode);
        conference.setConferenceUri(conferenceUri);
        conference.setCreatorId(creatorId);
        conference.setTitle(title);
        conference.setStatus(2);  // 2-待开始
        conference.setIsActive(true);  // 新建会议默认为活跃
        conference.setCreateTime(LocalDateTime.now());

        conferenceMapper.insert(conference);

        log.info("✅ 会议创建成功: conferenceCode={}, conferenceUri={}", conferenceCode, conferenceUri);
        return conference;
    }

    /**
     * 添加参与者
     */
    @Transactional
    public void addParticipant(Long conferenceId, Long userId, Integer role) {
        log.info("添加参与者: conferenceId={}, userId={}, role={}", conferenceId, userId, role);

        // ✅ 先检查会议是否存在
        Conference conference = conferenceMapper.selectById(conferenceId);
        if (conference == null) {
            log.error("❌ 会议不存在: conferenceId={}", conferenceId);
            throw new IllegalArgumentException("会议不存在: 会议ID " + conferenceId + " 未创建");
        }

        // 检查用户是否已经是参与者（避免重复加入）
        QueryWrapper<ConferenceParticipant> wrapper = new QueryWrapper<>();
        wrapper.eq("conference_id", conferenceId);
        wrapper.eq("user_id", userId);
        ConferenceParticipant existing = participantMapper.selectOne(wrapper);
        if (existing != null) {
            log.info("用户已经是会议参与者，跳过添加: conferenceId={}, userId={}", conferenceId, userId);
            return;
        }

        ConferenceParticipant participant = new ConferenceParticipant();
        participant.setConferenceId(conferenceId);
        participant.setUserId(userId);
        participant.setRole(role);  // 0-普通参与者, 1-主持人
        participant.setJoinTime(LocalDateTime.now());

        participantMapper.insert(participant);
        log.info("✅ 参与者添加成功: conferenceId={}, userId={}", conferenceId, userId);
    }

    /**
     * 根据ID查询会议信息
     */
    public Conference getConferenceById(Long id) {
        return conferenceMapper.selectById(id);
    }

    /**
     * 根据URI查询会议信息
     */
    public Conference getConferenceByUri(String conferenceUri) {
        QueryWrapper<Conference> wrapper = new QueryWrapper<>();
        wrapper.eq("conference_uri", conferenceUri);
        return conferenceMapper.selectOne(wrapper);
    }

    /**
     * 根据会议号查询活跃会议
     */
    public Conference getConferenceByCode(String conferenceCode) {
        QueryWrapper<Conference> wrapper = new QueryWrapper<>();
        wrapper.eq("conference_code", conferenceCode);
        wrapper.eq("is_active", true);  // 只查询活跃会议
        Conference conference = conferenceMapper.selectOne(wrapper);

        if (conference == null) {
            log.warn("会议不存在或已结束: conferenceCode={}", conferenceCode);
            throw new IllegalArgumentException("会议号不存在或会议已结束");
        }

        return conference;
    }

    /**
     * 结束会议 (将会议标记为非活跃)
     */
    @Transactional
    public void endConference(Long conferenceId) {
        Conference conference = conferenceMapper.selectById(conferenceId);
        if (conference != null) {
            conference.setIsActive(false);  // 标记为历史会议
            conference.setStatus(0);  // 0-已结束
            conference.setEndTime(LocalDateTime.now());
            conferenceMapper.updateById(conference);
            log.info("✅ 会议已结束: conferenceId={}, conferenceCode={}", conferenceId, conference.getConferenceCode());
        }
    }

    /**
     * 查询会议参与者
     */
    public List<ConferenceParticipant> getParticipants(Long conferenceId) {
        QueryWrapper<ConferenceParticipant> wrapper = new QueryWrapper<>();
        wrapper.eq("conference_id", conferenceId);
        return participantMapper.selectList(wrapper);
    }

    /**
     * 更新会议状态
     */
    @Transactional
    public void updateStatus(Long conferenceId, Integer status) {
        Conference conference = conferenceMapper.selectById(conferenceId);
        if (conference != null) {
            conference.setStatus(status);
            conferenceMapper.updateById(conference);
            log.info("✅ 会议状态已更新: conferenceId={}, status={}", conferenceId, status);
        }
    }

    /**
     * 删除会议
     */
    @Transactional
    public void deleteConference(Long conferenceId) {
        conferenceMapper.deleteById(conferenceId);

        QueryWrapper<ConferenceParticipant> pWrapper = new QueryWrapper<>();
        pWrapper.eq("conference_id", conferenceId);
        participantMapper.delete(pWrapper);

        log.info("✅ 会议已删除: conferenceId={}", conferenceId);
    }

    /**
     * 查询用户创建的所有会议
     */
    public List<Conference> getUserConferences(Long creatorId) {
        QueryWrapper<Conference> wrapper = new QueryWrapper<>();
        wrapper.eq("creator_id", creatorId);
        wrapper.orderByDesc("create_time");
        return conferenceMapper.selectList(wrapper);
    }

    /**
     * 更新会议时间
     */
    @Transactional
    public void updateTime(Long conferenceId, LocalDateTime startTime, LocalDateTime endTime) {
        Conference conference = conferenceMapper.selectById(conferenceId);
        if (conference != null) {
            conference.setStartTime(startTime);
            conference.setEndTime(endTime);
            conferenceMapper.updateById(conference);
        }
    }
}
