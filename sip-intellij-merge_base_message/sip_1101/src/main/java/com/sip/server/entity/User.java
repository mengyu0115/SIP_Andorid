package com.sip.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String password;
    private String nickname;
    private String avatar;
    private Integer status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    // SIP相关字段 - 匹配数据库列名
    @TableField("sip_uri")
    private String sipUri;          // 对应数据库的 sip_uri 列

    @TableField("sip_password")
    private String sipPassword;     // 对应数据库的 sip_password 列

    // 登录会话管理字段
    @TableField("login_token")
    private String loginToken;      // 当前登录token

    @TableField("login_device")
    private String loginDevice;     // 登录设备信息

}