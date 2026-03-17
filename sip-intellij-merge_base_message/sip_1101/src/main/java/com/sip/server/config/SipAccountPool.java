package com.sip.server.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * SIP账号池管理器
 *
 * 负责管理预注册SIP账号的分配和回收
 *
 * 功能:
 * 1. 从配置文件加载预注册账号
 * 2. 为应用用户分配SIP账号
 * 3. 回收未使用的SIP账号
 * 4. 查询账号使用情况
 */
@Slf4j
@Component
public class SipAccountPool {

    @Autowired
    private SipAccountProperties sipProperties;

    /**
     * 账号池 - 使用ConcurrentHashMap保证线程安全
     * Key: SIP username
     * Value: SipAccount
     */
    private final ConcurrentHashMap<String, SipAccount> accountPool = new ConcurrentHashMap<>();

    /**
     * 用户映射表 - 应用用户ID到SIP账号的映射
     * Key: 应用用户ID
     * Value: SIP username
     */
    private final ConcurrentHashMap<Long, String> userMapping = new ConcurrentHashMap<>();

    /**
     * 初始化账号池
     */
    @PostConstruct
    public void initialize() {
        log.info("初始化 SIP 账号池...");

        String domain = sipProperties.getServer().getDomain();
        List<SipAccountProperties.AccountConfig> accounts = sipProperties.getPreRegisteredAccounts();

        if (accounts == null || accounts.isEmpty()) {
            log.warn("未配置预注册SIP账号,请在 application.yml 中配置 sip.pre-registered-accounts");
            return;
        }

        for (SipAccountProperties.AccountConfig config : accounts) {
            SipAccount account = new SipAccount(
                    config.getUsername(),
                    config.getPassword(),
                    config.getDisplayName()
            );
            account.generateSipUri(domain);
            accountPool.put(account.getUsername(), account);
        }

        log.info("SIP 账号池初始化完成,共加载 {} 个账号", accountPool.size());
    }

    /**
     * 为应用用户分配SIP账号
     *
     * @param userId 应用用户ID
     * @param appUsername 应用用户名
     * @return 分配的SIP账号,如果无可用账号则返回null
     */
    public synchronized SipAccount allocateAccount(Long userId, String appUsername) {
        // 检查用户是否已分配过账号
        if (userMapping.containsKey(userId)) {
            String sipUsername = userMapping.get(userId);
            SipAccount account = accountPool.get(sipUsername);
            log.info("用户 {} 已分配SIP账号: {}", appUsername, account.getSipUri());
            return account;
        }

        // 查找未分配的账号
        Optional<SipAccount> availableAccount = accountPool.values().stream()
                .filter(account -> !account.isAllocated())
                .findFirst();

        if (!availableAccount.isPresent()) {
            log.error("SIP账号池已满,无法为用户 {} 分配账号", appUsername);
            return null;
        }

        // 分配账号
        SipAccount account = availableAccount.get();
        account.allocate(userId, appUsername);
        userMapping.put(userId, account.getUsername());

        log.info("为用户 {} (ID:{}) 分配SIP账号: {}", appUsername, userId, account.getSipUri());

        return account;
    }

    /**
     * 释放用户的SIP账号
     *
     * @param userId 应用用户ID
     */
    public synchronized void releaseAccount(Long userId) {
        if (!userMapping.containsKey(userId)) {
            log.warn("用户 {} 未分配SIP账号", userId);
            return;
        }

        String sipUsername = userMapping.remove(userId);
        SipAccount account = accountPool.get(sipUsername);

        if (account != null) {
            String appUsername = account.getAppUsername();
            account.release();
            log.info("释放用户 {} (ID:{}) 的SIP账号: {}", appUsername, userId, account.getSipUri());
        }
    }

    /**
     * 根据用户ID获取已分配的SIP账号
     *
     * @param userId 应用用户ID
     * @return SIP账号,如果未分配则返回null
     */
    public SipAccount getAccountByUserId(Long userId) {
        String sipUsername = userMapping.get(userId);
        return sipUsername != null ? accountPool.get(sipUsername) : null;
    }

    /**
     * 根据SIP用户名获取账号
     *
     * @param sipUsername SIP用户名
     * @return SIP账号
     */
    public SipAccount getAccountByUsername(String sipUsername) {
        return accountPool.get(sipUsername);
    }

    /**
     * 获取账号池统计信息
     *
     * @return 统计信息Map
     */
    public java.util.Map<String, Object> getStatistics() {
        long total = accountPool.size();
        long allocated = accountPool.values().stream().filter(SipAccount::isAllocated).count();
        long available = total - allocated;

        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("total", total);
        stats.put("allocated", allocated);
        stats.put("available", available);
        stats.put("usage", total > 0 ? String.format("%.2f%%", (allocated * 100.0 / total)) : "0%");

        return stats;
    }

    /**
     * 获取所有已分配的账号
     */
    public List<SipAccount> getAllocatedAccounts() {
        return accountPool.values().stream()
                .filter(SipAccount::isAllocated)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有可用账号
     */
    public List<SipAccount> getAvailableAccounts() {
        return accountPool.values().stream()
                .filter(account -> !account.isAllocated())
                .collect(Collectors.toList());
    }

    /**
     * 检查账号池是否已满
     */
    public boolean isFull() {
        return accountPool.values().stream().allMatch(SipAccount::isAllocated);
    }

    /**
     * 获取账号池大小
     */
    public int getPoolSize() {
        return accountPool.size();
    }
}
