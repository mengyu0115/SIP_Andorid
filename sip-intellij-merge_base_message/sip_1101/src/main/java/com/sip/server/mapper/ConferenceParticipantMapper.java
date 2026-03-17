package com.sip.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sip.server.entity.ConferenceParticipant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会议参与者Mapper接口
 *
 * @author SIP Team - Member 4
 * @version 1.0
 */
@Mapper
public interface ConferenceParticipantMapper extends BaseMapper<ConferenceParticipant> {
}
