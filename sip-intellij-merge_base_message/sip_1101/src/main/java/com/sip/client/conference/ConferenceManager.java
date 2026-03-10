package com.sip.client.conference;

/**
 * 会议管理器（客户端） - 待实现
 *
 * ⚠️ 原 ConferenceManager 有架构问题（使用了服务器端依赖），已备份为 ConferenceManager.java.old
 *
 * 正确的实现方式：
 * 1. 使用 SIP 协议创建和管理会议（SIP INVITE, BYE等）
 * 2. 通过 HTTP REST API 与服务器同步会议数据
 * 3. 不直接访问数据库（客户端不应该有 @Autowired 或 Mapper）
 *
 * 当前状态：使用 SipConferenceManager 替代
 *
 * @author SIP Team
 * @version 2.0
 */
public class ConferenceManager {

    private ConferenceManager() {
        // 暂时禁用
        throw new UnsupportedOperationException("该类需要重构，请使用 SipConferenceManager");
    }
}
