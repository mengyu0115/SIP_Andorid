package com.sip.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sip.server.entity.Message;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息Mapper接口
 *
 * @author SIP Team - Member 3
 * @version 1.0
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
