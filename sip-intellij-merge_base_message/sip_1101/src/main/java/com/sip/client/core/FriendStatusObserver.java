package com.sip.client.core;

import com.sip.client.core.SipClientManager;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 好友状态观察者
 * 用于UI组件观察和显示好友在线状态
 *
 * 功能:
 * 1. 管理好友状态与UI组件的映射
 * 2. 实时更新UI显示
 * 3. 提供状态颜色映射
 *
 */
@Slf4j
public class FriendStatusObserver implements SipClientManager.FriendStatusListener {

    // 好友 -> 状态指示器Label 的映射
    private Map<String, Label> friendStatusLabelMap = new HashMap<>();

    /**
     * 注册好友状态指示器
     *
     * @param friendUsername 好友用户名
     * @param statusLabel 状态指示器 Label
     */
    public void registerFriendStatusLabel(String friendUsername, Label statusLabel) {
        friendStatusLabelMap.put(friendUsername, statusLabel);
        log.debug("注册好友状态指示器: {}", friendUsername);
    }

    /**
     * 移除好友状态指示器
     *
     * @param friendUsername 好友用户名
     */
    public void unregisterFriendStatusLabel(String friendUsername) {
        friendStatusLabelMap.remove(friendUsername);
        log.debug("移除好友状态指示器: {}", friendUsername);
    }

    /**
     * 好友状态变更回调 (来自 SipClientManager)
     *
     * @param friendUsername 好友用户名
     * @param status 新状态
     */
    @Override
    public void onFriendStatusChanged(String friendUsername, String status) {
        log.info("收到好友状态变更通知: {} -> {}", friendUsername, status);

        // 更新对应的 Label
        Label statusLabel = friendStatusLabelMap.get(friendUsername);
        if (statusLabel != null) {
            updateStatusLabel(statusLabel, status);
        } else {
            log.warn("未找到好友 {} 的状态指示器", friendUsername);
        }
    }

    /**
     * 更新状态 Label 的颜色和文本
     *
     * @param statusLabel 状态 Label
     * @param status 状态字符串
     */
    private void updateStatusLabel(Label statusLabel, String status) {
        Color color = getStatusColor(status);
        String text = getStatusText(status);

        statusLabel.setText(text);
        statusLabel.setTextFill(color);

        log.debug("更新状态 Label: {} - {}", text, color);
    }

    /**
     * 获取状态对应的颜色
     *
     * @param status 状态字符串
     * @return 颜色
     */
    public static Color getStatusColor(String status) {
        switch (status.toLowerCase()) {
            case "online":
                return Color.web("#00FF00"); // 绿色 - 在线
            case "busy":
                return Color.web("#FF0000"); // 红色 - 忙碌
            case "away":
                return Color.web("#FFD700"); // 黄色 - 离开
            case "offline":
                return Color.web("#808080"); // 灰色 - 离线
            default:
                return Color.web("#999999"); // 默认灰色
        }
    }

    /**
     * 获取状态对应的中文文本
     *
     * @param status 状态字符串
     * @return 中文文本
     */
    public static String getStatusText(String status) {
        switch (status.toLowerCase()) {
            case "online":
                return "●";  // 实心圆点 - 在线
            case "busy":
                return "●";  // 实心圆点 - 忙碌
            case "away":
                return "●";  // 实心圆点 - 离开
            case "offline":
                return "○";  // 空心圆点 - 离线
            default:
                return "?";
        }
    }

    /**
     * 清空所有注册的状态指示器
     */
    public void clear() {
        friendStatusLabelMap.clear();
        log.info("清空所有好友状态指示器");
    }
}