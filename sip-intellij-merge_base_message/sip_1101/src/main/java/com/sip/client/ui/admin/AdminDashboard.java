package com.sip.client.ui.admin;

import com.sip.server.entity.CallRecord;
import com.sip.server.entity.User;
import com.sip.server.service.admin.CallStatisticsService;
import com.sip.server.service.admin.MonitorService;
import com.sip.server.service.admin.UserAdminService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 管理后台 Dashboard 控制器
 *
 * 提供系统监控和管理功能的可视化界面
 *
 * @author SIP Team - Member 4
 * @version 1.0
 */
@Component
public class AdminDashboard {

    private static final Logger logger = LoggerFactory.getLogger(AdminDashboard.class);

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private CallStatisticsService callStatisticsService;

    @Autowired
    private MonitorService monitorService;

    // ==================== 概览面板 ====================

    @FXML
    private Label totalUsersLabel;

    @FXML
    private Label onlineUsersLabel;

    @FXML
    private Label totalCallsLabel;

    @FXML
    private Label activeCallsLabel;

    @FXML
    private Label totalMessagesLabel;

    @FXML
    private Label todayMessagesLabel;

    @FXML
    private Label cpuLoadLabel;

    @FXML
    private Label memoryUsageLabel;

    @FXML
    private Label threadCountLabel;

    @FXML
    private Label systemUptimeLabel;

    // ==================== 用户管理面板 ====================

    @FXML
    private TableView<User> userTableView;

    @FXML
    private TableColumn<User, Long> userIdColumn;

    @FXML
    private TableColumn<User, String> usernameColumn;

    @FXML
    private TableColumn<User, String> nicknameColumn;

    @FXML
    private TableColumn<User, String> sipUriColumn;

    @FXML
    private TableColumn<User, Integer> statusColumn;

    @FXML
    private TextField searchKeywordField;

    @FXML
    private Button searchButton;

    @FXML
    private Button deleteUserButton;

    @FXML
    private Button resetPasswordButton;

    // ==================== 统计分析面板 ====================

    @FXML
    private PieChart callTypeChart;

    @FXML
    private PieChart callQualityChart;

    @FXML
    private BarChart<String, Number> callHourChart;

    @FXML
    private LineChart<String, Number> dailyCallChart;

    @FXML
    private CategoryAxis callHourXAxis;

    @FXML
    private NumberAxis callHourYAxis;

    @FXML
    private CategoryAxis dailyCallXAxis;

    @FXML
    private NumberAxis dailyCallYAxis;

    // ==================== 系统监控面板 ====================

    @FXML
    private TableView<User> onlineUsersTableView;

    @FXML
    private TableColumn<User, Long> onlineUserIdColumn;

    @FXML
    private TableColumn<User, String> onlineUsernameColumn;

    @FXML
    private TableColumn<User, Integer> onlineStatusColumn;

    @FXML
    private TableView<CallRecord> recentCallsTableView;

    @FXML
    private TableColumn<CallRecord, Long> callIdColumn;

    @FXML
    private TableColumn<CallRecord, Long> callerIdColumn;

    @FXML
    private TableColumn<CallRecord, Long> calleeIdColumn;

    @FXML
    private TableColumn<CallRecord, Integer> callTypeColumn;

    @FXML
    private TableColumn<CallRecord, Integer> callStatusColumn;

    @FXML
    private ListView<String> systemAlertsListView;

    @FXML
    private Button refreshButton;

    // 定时刷新任务
    private Timer refreshTimer;

    /**
     * 初始化方法
     */
    @FXML
    public void initialize() {
        logger.info("初始化管理后台 Dashboard");

        // 初始化表格列
        initializeTableColumns();

        // 加载初始数据
        loadAllData();

        // 启动自动刷新（每5秒）
        startAutoRefresh();

        logger.info("管理后台 Dashboard 初始化完成");
    }

    /**
     * 初始化表格列
     */
    private void initializeTableColumns() {
        // 用户管理表格
        if (userIdColumn != null) {
            userIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
            usernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
            nicknameColumn.setCellValueFactory(new PropertyValueFactory<>("nickname"));
            sipUriColumn.setCellValueFactory(new PropertyValueFactory<>("sipUri"));
            statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        }

        // 在线用户表格
        if (onlineUserIdColumn != null) {
            onlineUserIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
            onlineUsernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
            onlineStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        }

        // 最近通话表格
        if (callIdColumn != null) {
            callIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
            callerIdColumn.setCellValueFactory(new PropertyValueFactory<>("callerId"));
            calleeIdColumn.setCellValueFactory(new PropertyValueFactory<>("calleeId"));
            callTypeColumn.setCellValueFactory(new PropertyValueFactory<>("callType"));
            callStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        }
    }

    /**
     * 加载所有数据
     */
    private void loadAllData() {
        Platform.runLater(() -> {
            try {
                loadOverviewData();
                loadUserData();
                loadStatisticsData();
                loadMonitorData();
            } catch (Exception e) {
                logger.error("加载数据失败", e);
                showError("加载数据失败: " + e.getMessage());
            }
        });
    }

    /**
     * 加载概览数据
     */
    private void loadOverviewData() {
        if (totalUsersLabel == null) return;

        Map<String, Object> summary = monitorService.getSystemSummary();

        totalUsersLabel.setText(String.valueOf(summary.get("totalUsers")));
        onlineUsersLabel.setText(String.valueOf(summary.get("onlineUsers")));
        totalCallsLabel.setText(String.valueOf(summary.get("totalCalls")));
        activeCallsLabel.setText(String.valueOf(summary.get("activeCalls")));
        totalMessagesLabel.setText(String.valueOf(summary.get("totalMessages")));
        todayMessagesLabel.setText(String.valueOf(summary.get("todayMessages")));

        // 系统性能指标
        Object cpuLoad = summary.get("cpuLoad");
        Object memoryUsage = summary.get("heapMemoryUsage");
        Object threadCount = summary.get("threadCount");
        Object uptimeMinutes = summary.get("uptimeMinutes");

        cpuLoadLabel.setText(cpuLoad != null ? String.format("%.2f", cpuLoad) : "N/A");
        memoryUsageLabel.setText(memoryUsage != null ? String.format("%.2f%%", memoryUsage) : "N/A");
        threadCountLabel.setText(threadCount != null ? String.valueOf(threadCount) : "N/A");
        systemUptimeLabel.setText(uptimeMinutes != null ? formatUptime((Long) uptimeMinutes) : "N/A");

        logger.debug("概览数据已更新");
    }

    /**
     * 加载用户数据
     */
    private void loadUserData() {
        if (userTableView == null) return;

        List<User> users = userAdminService.listAllUsers();
        ObservableList<User> userList = FXCollections.observableArrayList(users);
        userTableView.setItems(userList);

        logger.debug("用户数据已加载: {} 个用户", users.size());
    }

    /**
     * 加载统计数据
     */
    private void loadStatisticsData() {
        // 通话类型分布
        if (callTypeChart != null) {
            Map<String, Long> typeDistribution = callStatisticsService.getCallTypeDistribution();
            ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();

            typeDistribution.forEach((type, count) -> {
                pieChartData.add(new PieChart.Data(type + " (" + count + ")", count));
            });

            callTypeChart.setData(pieChartData);
        }

        // 通话质量分布
        if (callQualityChart != null) {
            Map<String, Long> qualityDistribution = callStatisticsService.getCallQualityDistribution();
            ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();

            qualityDistribution.forEach((quality, count) -> {
                pieChartData.add(new PieChart.Data(quality + " (" + count + ")", count));
            });

            callQualityChart.setData(pieChartData);
        }

        // 通话时段分布
        if (callHourChart != null) {
            Map<Integer, Long> hourDistribution = callStatisticsService.getCallHourDistribution();
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("通话次数");

            for (int hour = 0; hour < 24; hour++) {
                Long count = hourDistribution.getOrDefault(hour, 0L);
                series.getData().add(new XYChart.Data<>(hour + ":00", count));
            }

            callHourChart.getData().clear();
            callHourChart.getData().add(series);
        }

        // 每日通话统计
        if (dailyCallChart != null) {
            List<Map<String, Object>> dailyStats = callStatisticsService.getDailyCallStats(7);
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("每日通话");

            dailyStats.forEach(stat -> {
                String date = (String) stat.get("date");
                Long count = (Long) stat.get("count");
                series.getData().add(new XYChart.Data<>(date.substring(5), count));
            });

            dailyCallChart.getData().clear();
            dailyCallChart.getData().add(series);
        }

        logger.debug("统计数据已加载");
    }

    /**
     * 加载监控数据
     */
    private void loadMonitorData() {
        // 在线用户
        if (onlineUsersTableView != null) {
            List<User> onlineUsers = monitorService.getOnlineUsers();
            ObservableList<User> onlineUserList = FXCollections.observableArrayList(onlineUsers);
            onlineUsersTableView.setItems(onlineUserList);
        }

        // 最近通话
        if (recentCallsTableView != null) {
            List<CallRecord> recentCalls = monitorService.getRecentCalls(20);
            ObservableList<CallRecord> callList = FXCollections.observableArrayList(recentCalls);
            recentCallsTableView.setItems(callList);
        }

        // 系统告警
        if (systemAlertsListView != null) {
            List<Map<String, Object>> alerts = monitorService.getSystemAlerts();
            ObservableList<String> alertMessages = FXCollections.observableArrayList();

            alerts.forEach(alert -> {
                String level = (String) alert.get("level");
                String message = (String) alert.get("message");
                alertMessages.add("[" + level + "] " + message);
            });

            systemAlertsListView.setItems(alertMessages);
        }

        logger.debug("监控数据已加载");
    }

    /**
     * 搜索用户
     */
    @FXML
    private void handleSearchUsers() {
        String keyword = searchKeywordField.getText();
        logger.info("搜索用户: keyword={}", keyword);

        if (keyword == null || keyword.trim().isEmpty()) {
            loadUserData();
            return;
        }

        List<User> users = userAdminService.searchUsers(keyword);
        ObservableList<User> userList = FXCollections.observableArrayList(users);
        userTableView.setItems(userList);

        showInfo("找到 " + users.size() + " 个用户");
    }

    /**
     * 删除用户
     */
    @FXML
    private void handleDeleteUser() {
        User selectedUser = userTableView.getSelectionModel().getSelectedItem();
        if (selectedUser == null) {
            showWarning("请先选择要删除的用户");
            return;
        }

        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("确认删除");
        confirmAlert.setHeaderText("删除用户");
        confirmAlert.setContentText("确定要删除用户 " + selectedUser.getUsername() + " 吗？");

        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                boolean success = userAdminService.deleteUser(selectedUser.getId());
                if (success) {
                    showInfo("用户删除成功");
                    loadUserData();
                } else {
                    showError("用户删除失败");
                }
            }
        });
    }

    /**
     * 重置密码
     */
    @FXML
    private void handleResetPassword() {
        User selectedUser = userTableView.getSelectionModel().getSelectedItem();
        if (selectedUser == null) {
            showWarning("请先选择要重置密码的用户");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("123456");
        dialog.setTitle("重置密码");
        dialog.setHeaderText("重置用户密码");
        dialog.setContentText("请输入新密码:");

        dialog.showAndWait().ifPresent(newPassword -> {
            boolean success = userAdminService.resetPassword(selectedUser.getId(), newPassword);
            if (success) {
                showInfo("密码重置成功");
            } else {
                showError("密码重置失败");
            }
        });
    }

    /**
     * 刷新数据
     */
    @FXML
    private void handleRefresh() {
        logger.info("手动刷新数据");
        loadAllData();
        showInfo("数据已刷新");
    }

    /**
     * 启动自动刷新
     */
    private void startAutoRefresh() {
        refreshTimer = new Timer(true);
        refreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    try {
                        loadOverviewData();
                        loadMonitorData();
                    } catch (Exception e) {
                        logger.error("自动刷新失败", e);
                    }
                });
            }
        }, 5000, 5000); // 5秒刷新一次

        logger.info("自动刷新已启动");
    }

    /**
     * 停止自动刷新
     */
    public void stopAutoRefresh() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
            logger.info("自动刷新已停止");
        }
    }

    /**
     * 格式化运行时间
     */
    private String formatUptime(long minutes) {
        long days = minutes / (60 * 24);
        long hours = (minutes % (60 * 24)) / 60;
        long mins = minutes % 60;

        if (days > 0) {
            return String.format("%d天%d小时%d分钟", days, hours, mins);
        } else if (hours > 0) {
            return String.format("%d小时%d分钟", hours, mins);
        } else {
            return String.format("%d分钟", mins);
        }
    }

    // ==================== 提示信息 ====================

    private void showInfo(String message) {
        showAlert(Alert.AlertType.INFORMATION, "提示", message);
    }

    private void showWarning(String message) {
        showAlert(Alert.AlertType.WARNING, "警告", message);
    }

    private void showError(String message) {
        showAlert(Alert.AlertType.ERROR, "错误", message);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
