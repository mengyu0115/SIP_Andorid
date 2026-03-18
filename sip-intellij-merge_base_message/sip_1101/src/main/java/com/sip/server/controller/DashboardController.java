package com.sip.server.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 后端管理界面控制器
 *
 * 提供Web界面访问入口
 *
 * @author SIP Team
 * @version 1.0
 */
@Controller
public class DashboardController {

    /**
     * 首页重定向到管理面板
     */
    @GetMapping("/")
    public String index() {
        return "redirect:/dashboard";
    }

    /**
     * 管理面板首页
     * 访问: http://10.29.209.85:8081/dashboard
     * 或: http://10.29.209.85:8081/view/dashboard
     */
    @GetMapping({"/dashboard", "/view/dashboard"})
    public String dashboard() {
        return "dashboard";
    }

    /**
     * 在线用户可视化页面
     * 访问: http://10.29.209.85:8081/online-users
     * 或: http://10.29.209.85:8081/view/online-users
     */
    @GetMapping({"/online-users", "/view/online-users"})
    public String onlineUsersView() {
        return "online-users";
    }

    /**
     * 会议管理可视化页面
     * 访问: http://10.29.209.85:8081/conferences
     * 或: http://10.29.209.85:8081/view/conferences
     */
    @GetMapping({"/conferences", "/view/conferences"})
    public String conferencesView() {
        return "conferences";
    }

    /**
     * 系统监控可视化页面
     * 访问: http://10.29.209.85:8081/system-monitor
     * 或: http://10.29.209.85:8081/view/system-monitor
     */
    @GetMapping({"/system-monitor", "/view/system-monitor"})
    public String systemMonitorView() {
        return "system-monitor";
    }

    /**
     * 通话记录可视化页面
     * 访问: http://10.29.209.85:8081/call-records
     * 或: http://10.29.209.85:8081/view/call-records
     */
    @GetMapping({"/call-records", "/view/call-records"})
    public String callRecordsView() {
        return "call-records";
    }

    /**
     * 聊天记录查询页面
     * 访问: http://10.29.209.85:8081/chat-history
     * 或: http://10.29.209.85:8081/view/chat-history
     */
    @GetMapping({"/chat-history", "/view/chat-history"})
    public String chatHistoryView() {
        return "chat-history";
    }
}
