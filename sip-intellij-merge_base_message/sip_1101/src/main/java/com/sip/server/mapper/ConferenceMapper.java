package com.sip.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sip.server.entity.Conference;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会议Mapper接口
 *
 * @author SIP Team - Member 4
 * @version 1.0
 */
@Mapper
public interface ConferenceMapper extends BaseMapper<Conference> {
}
