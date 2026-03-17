package com.sip.server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户在线状态实体
 */
@Data
@TableName("user_presence")
public class UserPresence {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * SIP ID (如 101, 102)
     */
    private String sipId;

    /**
     * 在线状态: online, busy, away, offline
     */
    private String status;

    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdateTime;
}
