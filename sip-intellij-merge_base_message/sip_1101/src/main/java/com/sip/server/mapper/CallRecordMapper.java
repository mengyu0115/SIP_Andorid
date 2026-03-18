package com.sip.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sip.server.entity.CallRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通话记录Mapper接口
 *
 * @author SIP Team
 * @version 1.0
 */
@Mapper
public interface CallRecordMapper extends BaseMapper<CallRecord> {
}
