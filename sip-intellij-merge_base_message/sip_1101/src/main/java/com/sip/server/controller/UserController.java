package com.sip.server.controller;

import com.sip.server.entity.User;
import com.sip.server.service.UserService;
import com.sip.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理控制器
 *
 * 实现 API 接口文档中的用户管理模块
 *
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
@CrossOrigin
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private com.sip.server.util.JwtTokenUtil jwtTokenUtil;

    /**
     * 2.1 用户注册
     * POST /api/user/register
     */
    @PostMapping("/register")
    public Result register(@RequestBody Map<String, String> params) {
        try {
            String username = params.get("username");
            String password = params.get("password");
            String nickname = params.get("nickname");
            String avatar = params.get("avatar");

            // 参数校验
            if (username == null || username.trim().isEmpty()) {
                return Result.error(400, "用户名不能为空");
            }
            if (password == null || password.length() < 6) {
                return Result.error(400, "密码长度不能少于6位");
            }

            User user = userService.register(username, password, nickname, avatar);

            return Result.success("注册成功", user);

        } catch (Exception e) {
            log.error("注册失败", e);
            return Result.error(1001, e.getMessage());
        }
    }

    /**
     * 2.2 用户登录
     * POST /api/user/login
     */
    @PostMapping("/login")
    public Result login(@RequestBody Map<String, String> params) {
        try {
            String username = params.get("username");
            String password = params.get("password");

            if (username == null || password == null) {
                return Result.error(400, "用户名或密码不能为空");
            }

            User user = userService.login(username, password);

            // 生成 JWT Token
            String token = jwtTokenUtil.generateToken(user.getId(), user.getUsername());

            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("expiresIn", 86400);
            data.put("user", user);

            return Result.success("登录成功", data);

        } catch (Exception e) {
            log.error("登录失败", e);
            return Result.error(1002, e.getMessage());
        }
    }

    /**
     * 2.3 用户登出
     * POST /api/user/logout
     */
    @PostMapping("/logout")
    public Result logout(@RequestHeader("Authorization") String token) {
        try {
            // TODO: 清除 token 缓存

            return Result.success("登出成功", null);

        } catch (Exception e) {
            log.error("登出失败", e);
            return Result.error(500, "登出失败");
        }
    }

    /**
     * 2.4 获取用户信息
     * GET /api/user/{id}
     */
    @GetMapping("/{id}")
    public Result getUserInfo(@PathVariable Long id) {
        try {
            User user = userService.getUserById(id);

            if (user == null) {
                return Result.error(1003, "用户不存在");
            }

            return Result.success("success", user);

        } catch (Exception e) {
            log.error("获取用户信息失败", e);
            return Result.error(500, "获取用户信息失败");
        }
    }

    /**
     * 2.5 更新用户信息
     * PUT /api/user/{id}
     */
    @PutMapping("/{id}")
    public Result updateUser(@PathVariable Long id, @RequestBody User user) {
        try {
            user.setId(id);
            userService.updateUser(user);

            return Result.success("更新成功", user);

        } catch (Exception e) {
            log.error("更新用户信息失败", e);
            return Result.error(500, "更新失败");
        }
    }

    /**
     * 2.6 修改密码
     * PUT /api/user/password
     */
    @PutMapping("/password")
    public Result changePassword(@RequestBody Map<String, String> params,
                                 @RequestHeader("Authorization") String authHeader) {
        try {
            // 从 token 中获取 userId
            String token = jwtTokenUtil.extractToken(authHeader);
            Long userId = jwtTokenUtil.getUserIdFromToken(token);

            String oldPassword = params.get("oldPassword");
            String newPassword = params.get("newPassword");

            if (oldPassword == null || newPassword == null) {
                return Result.error(400, "参数错误");
            }

            userService.changePassword(userId, oldPassword, newPassword);

            return Result.success("密码修改成功", null);

        } catch (Exception e) {
            log.error("修改密码失败", e);
            return Result.error(1004, e.getMessage());
        }
    }

    /**
     * 2.7 搜索用户
     * GET /api/user/search
     */
    @GetMapping("/search")
    public Result searchUsers(@RequestParam String keyword) {
        try {
            List<User> users = userService.searchUsers(keyword);

            Map<String, Object> data = new HashMap<>();
            data.put("total", users.size());
            data.put("list", users);

            return Result.success("success", data);

        } catch (Exception e) {
            log.error("搜索用户失败", e);
            return Result.error(500, "搜索失败");
        }
    }

    /**
     * 获取所有用户列表
     * GET /api/user/list
     */
    @GetMapping("/list")
    public Result getAllUsers() {
        try {
            List<User> users = userService.getAllUsers();

            Map<String, Object> data = new HashMap<>();
            data.put("total", users.size());
            data.put("list", users);

            return Result.success("success", data);

        } catch (Exception e) {
            log.error("获取用户列表失败", e);
            return Result.error(500, "获取用户列表失败");
        }
    }

    /**
     * 2.8 更新在线状态
     * PUT /api/user/status
     */
    @PutMapping("/status")
    public Result updateStatus(@RequestBody Map<String, Integer> params,
                              @RequestHeader("Authorization") String authHeader) {
        try {
            // 从 token 中获取 userId
            String token = jwtTokenUtil.extractToken(authHeader);
            Long userId = jwtTokenUtil.getUserIdFromToken(token);

            Integer status = params.get("status");

            if (status == null || status < 0 || status > 3) {
                return Result.error(400, "状态参数错误");
            }

            userService.updateStatus(userId, status);

            return Result.success("状态更新成功", null);

        } catch (Exception e) {
            log.error("更新状态失败", e);
            return Result.error(500, "更新失败");
        }
    }
}