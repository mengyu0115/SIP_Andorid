package com.sip.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sip.server.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户Mapper接口
 *
 * @author SIP Team
 * @version 1.0
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
