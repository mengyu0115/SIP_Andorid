package com.sip.server.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sip.server.entity.User;
import com.sip.server.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户管理服务
 *
 * 提供后台用户管理功能：
 * - 用户列表查询（分页）
 * - 删除用户
 * - 重置密码
 * - 禁用/启用用户
 * - 用户统计
 *
 * @author SIP Team - Member 4
 * @version 1.0
 */
@Service
public class UserAdminService {

    private static final Logger logger = LoggerFactory.getLogger(UserAdminService.class);

    @Autowired
    private UserMapper userMapper;

    /**
     * 获取用户列表（分页）
     *
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param keyword 搜索关键词（用户名/昵称）
     * @return 用户列表分页
     */
    public IPage<User> listUsers(int pageNum, int pageSize, String keyword) {
        logger.info("查询用户列表: pageNum={}, pageSize={}, keyword={}", pageNum, pageSize, keyword);

        Page<User> page = new Page<>(pageNum, pageSize);
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();

        // 搜索条件
        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(wrapper -> wrapper
                    .like("username", keyword)
                    .or()
                    .like("nickname", keyword)
                    .or()
                    .like("sip_id", keyword) // 修改为 sip_id
            );
        }

        // 按创建时间倒序
        queryWrapper.orderByDesc("create_time");

        IPage<User> result = userMapper.selectPage(page, queryWrapper);
        logger.info("查询到 {} 个用户", result.getRecords().size());

        return result;
    }

    /**
     * 获取所有用户（不分页）
     *
     * @return 用户列表
     */
    public List<User> listAllUsers() {
        logger.info("查询所有用户");
        return userMapper.selectList(null);
    }

    /**
     * 根据ID获取用户详情
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    public User getUserById(Long userId) {
        logger.info("查询用户详情: userId={}", userId);
        return userMapper.selectById(userId);
    }

    /**
     * 删除用户
     *
     * @param userId 用户ID
     * @return 是否删除成功
     */
    @Transactional
    public boolean deleteUser(Long userId) {
        logger.info("删除用户: userId={}", userId);

        User user = userMapper.selectById(userId);
        if (user == null) {
            logger.warn("用户不存在: userId={}", userId);
            return false;
        }

        int result = userMapper.deleteById(userId);
        boolean success = result > 0;

        if (success) {
            logger.info("用户删除成功: userId={}, username={}", userId, user.getUsername());
        } else {
            logger.error("用户删除失败: userId={}", userId);
        }

        return success;
    }

    /**
     * 批量删除用户
     *
     * @param userIds 用户ID列表
     * @return 删除成功的数量
     */
    @Transactional
    public int batchDeleteUsers(List<Long> userIds) {
        logger.info("批量删除用户: userIds={}", userIds);

        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }

        int deletedCount = userMapper.deleteBatchIds(userIds);
        logger.info("批量删除完成: 成功删除 {} 个用户", deletedCount);

        return deletedCount;
    }

    /**
     * 重置用户密码
     *
     * @param userId 用户ID
     * @param newPassword 新密码
     * @return 是否重置成功
     */
    @Transactional
    public boolean resetPassword(Long userId, String newPassword) {
        logger.info("重置用户密码: userId={}", userId);

        User user = userMapper.selectById(userId);
        if (user == null) {
            logger.warn("用户不存在: userId={}", userId);
            return false;
        }

        // 直接设置为新密码（明文或简单处理，根据你的需求，这里暂时保持简单赋值）
        // 如果你需要加密，请确保与 UserService.login 中的校验逻辑一致
        // 既然 UserService.login 用的是明文对比，这里也存明文
        user.setPassword(newPassword);
        // user.setUpdateTime(LocalDateTime.now()); // 已删除字段

        int result = userMapper.updateById(user);
        boolean success = result > 0;

        if (success) {
            logger.info("密码重置成功: userId={}, username={}", userId, user.getUsername());
        } else {
            logger.error("密码重置失败: userId={}", userId);
        }

        return success;
    }

    /**
     * 修改用户状态（在线/离线/忙碌/离开）
     *
     * @param userId 用户ID
     * @param status 状态码 (0离线 1在线 2忙碌 3离开)
     * @return 是否修改成功
     */
    @Transactional
    public boolean updateUserStatus(Long userId, Integer status) {
        logger.info("修改用户状态: userId={}, status={}", userId, status);

        User user = userMapper.selectById(userId);
        if (user == null) {
            logger.warn("用户不存在: userId={}", userId);
            return false;
        }

        user.setStatus(status);
        // user.setUpdateTime(LocalDateTime.now()); // 已删除字段

        int result = userMapper.updateById(user);
        boolean success = result > 0;

        if (success) {
            logger.info("用户状态修改成功: userId={}, status={}", userId, status);
        }

        return success;
    }

    /**
     * 获取用户总数
     *
     * @return 用户总数
     */
    public long getTotalUserCount() {
        Long count = userMapper.selectCount(null);
        logger.info("用户总数: {}", count);
        return count != null ? count : 0;
    }

    /**
     * 获取在线用户数
     *
     * @return 在线用户数
     */
    public long getOnlineUserCount() {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", 1); // 1 = 在线
        Long count = userMapper.selectCount(queryWrapper);
        logger.info("在线用户数: {}", count);
        return count != null ? count : 0;
    }

    /**
     * 获取今日新增用户数
     *
     * @return 今日新增用户数
     */
    public long getTodayNewUserCount() {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.apply("DATE(create_time) = CURDATE()");
        Long count = userMapper.selectCount(queryWrapper);
        logger.info("今日新增用户数: {}", count);
        return count != null ? count : 0;
    }

    /**
     * 搜索用户
     *
     * @param keyword 关键词
     * @return 用户列表
     */
    public List<User> searchUsers(String keyword) {
        logger.info("搜索用户: keyword={}", keyword);

        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper
                .like("username", keyword)
                .or()
                .like("nickname", keyword)
                .or()
                .like("sip_id", keyword) // 修改为 sip_id
        );
        queryWrapper.orderByDesc("create_time");

        List<User> users = userMapper.selectList(queryWrapper);
        logger.info("搜索到 {} 个用户", users.size());

        return users;
    }
}