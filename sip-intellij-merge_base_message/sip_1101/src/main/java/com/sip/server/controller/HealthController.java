package com.sip.server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 基础健康/连通性测试接口，不依赖数据库
 */
@RestController
public class HealthController {

    @GetMapping("/api/ping")
    public String ping() {
        return "OK";
    }

    @GetMapping("/api/version")
    public String version() {
        return "sip-im-system 1.0";
    }
}

