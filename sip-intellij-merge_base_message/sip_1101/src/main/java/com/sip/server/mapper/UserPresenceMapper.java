package com.sip.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sip.server.entity.UserPresence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户在线状态 Mapper
 */
@Mapper
public interface UserPresenceMapper extends BaseMapper<UserPresence> {

    /**
     * 根据 SIP ID 查询用户状态
     */
    @Select("SELECT * FROM user_presence WHERE sip_id = #{sipId}")
    UserPresence getBySipId(@Param("sipId") String sipId);

    /**
     * 查询所有在线用户 (不包括 offline)
     */
    @Select("SELECT * FROM user_presence WHERE status != 'offline'")
    List<UserPresence> getOnlineUsers();

    /**
     * 查询指定 SIP ID 列表的状态
     */
    @Select("<script>" +
            "SELECT * FROM user_presence WHERE sip_id IN " +
            "<foreach item='item' index='index' collection='sipIds' open='(' separator=',' close=')'>" +
            "#{item}" +
            "</foreach>" +
            "</script>")
    List<UserPresence> getBySipIds(@Param("sipIds") List<String> sipIds);
}
