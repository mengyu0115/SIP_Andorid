package com.sip.server.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 页面路由控制器
 * 用于返回HTML页面（不是REST API）
 *
 * 注意：登录和注册已迁移到JavaFX桌面端
 * 网页端只保留后台管理功能
 */
@Controller
public class PageController {

    /**
     * 登录页面 - 已禁用，请使用JavaFX客户端
     */
    @GetMapping("/login")
    public String loginPage() {
        // 返回提示页面，告知用户使用JavaFX客户端
        return "redirect:/users"; // 临时重定向到用户管理页面
    }

    /**
     * 注册页面 - 已禁用，请使用JavaFX客户端
     */
    @GetMapping("/register")
    public String registerPage() {
        // 返回提示页面，告知用户使用JavaFX客户端
        return "redirect:/users"; // 临时重定向到用户管理页面
    }

    /**
     * 用户管理页面（后台管理功能保留）
     */
    @GetMapping("/users")
    public String usersPage() {
        return "users";
    }
}
