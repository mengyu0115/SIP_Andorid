package com.sip.client.ui.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sip.client.util.HttpClientUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通话记录查询控制器
 *
 * @author SIP Team
 * @version 1.0
 */
@Slf4j
public class CallRecordViewController {

    // 表格和列
    @FXML
    private TableView<CallRecordRow> callRecordTable;
    @FXML
    private TableColumn<CallRecordRow, String> idColumn;
    @FXML
    private TableColumn<CallRecordRow, String> typeColumn;
    @FXML
    private TableColumn<CallRecordRow, String> callerColumn;
    @FXML
    private TableColumn<CallRecordRow, String> calleeColumn;
    @FXML
    private TableColumn<CallRecordRow, String> startTimeColumn;
    @FXML
    private TableColumn<CallRecordRow, String> endTimeColumn;
    @FXML
    private TableColumn<CallRecordRow, String> durationColumn;
    @FXML
    private TableColumn<CallRecordRow, String> statusColumn;

    // 筛选和分页控件
    @FXML
    private ComboBox<String> callTypeFilter;
    @FXML
    private ComboBox<String> pageSizeComboBox;
    @FXML
    private Button prevPageButton;
    @FXML
    private Button nextPageButton;
    @FXML
    private Label pageInfoLabel;
    @FXML
    private Label totalRecordsLabel;
    @FXML
    private Label totalDurationLabel;

    // 分页参数
    private int currentPage = 1;
    private int pageSize = 20;
    private int totalPages = 1;
    private long totalRecords = 0;
    private long totalDuration = 0;

    // 当前用户ID
    private Long currentUserId;

    // 日期格式化
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML
    public void initialize() {
        log.info("初始化通话记录窗口");

        // 初始化表格列
        setupTableColumns();

        // 初始化筛选器
        setupFilters();

        // 初始化分页控件
        setupPagination();

        // 加载数据
        loadCallRecords();
    }

    /**
     * 设置表格列
     */
    private void setupTableColumns() {
        idColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getId()));
        typeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getType()));
        callerColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCaller()));
        calleeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCallee()));
        startTimeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStartTime()));
        endTimeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getEndTime()));
        durationColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDuration()));
        statusColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus()));

        // 设置单元格样式
        typeColumn.setCellFactory(column -> new TableCell<CallRecordRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("视频".equals(item)) {
                        setStyle("-fx-text-fill: #2196F3; -fx-font-weight: bold;");
                    } else if ("音频".equals(item)) {
                        setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
                    }
                }
            }
        });

        statusColumn.setCellFactory(column -> new TableCell<CallRecordRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    switch (item) {
                        case "成功":
                            setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
                            break;
                        case "未接":
                            setStyle("-fx-text-fill: #FF9800; -fx-font-weight: bold;");
                            break;
                        case "拒绝":
                        case "失败":
                            setStyle("-fx-text-fill: #F44336; -fx-font-weight: bold;");
                            break;
                        default:
                            setStyle("-fx-text-fill: #999;");
                    }
                }
            }
        });
    }

    /**
     * 设置筛选器
     */
    private void setupFilters() {
        ObservableList<String> types = FXCollections.observableArrayList("全部", "音频", "视频");
        callTypeFilter.setItems(types);
        callTypeFilter.setValue("全部");

        callTypeFilter.setOnAction(event -> loadCallRecords());
    }

    /**
     * 设置分页控件
     */
    private void setupPagination() {
        // 设置每页数量选项
        ObservableList<String> pageSizes = FXCollections.observableArrayList("10", "20", "50", "100");
        pageSizeComboBox.setItems(pageSizes);
        pageSizeComboBox.setValue("20");

        pageSizeComboBox.setOnAction(event -> {
            String sizeStr = pageSizeComboBox.getValue();
            if (sizeStr != null) {
                pageSize = Integer.parseInt(sizeStr);
                currentPage = 1;
                loadCallRecords();
            }
        });

        updatePaginationButtons();
    }

    /**
     * 加载通话记录
     */
    private void loadCallRecords() {
        new Thread(() -> {
            try {
                log.info("加载通话记录: page={}, size={}", currentPage, pageSize);

                // 构建请求URL
                StringBuilder url = new StringBuilder("/api/call/records?pageNum=")
                        .append(currentPage)
                        .append("&pageSize=")
                        .append(pageSize);

                // 添加用户ID筛选（查询当前用户相关的通话记录）
                if (currentUserId != null) {
                    url.append("&userId=").append(currentUserId);
                }

                // 发送HTTP GET请求
                String response = HttpClientUtil.getRaw(url.toString());
                log.debug("HTTP响应: {}", response);

                // 解析响应
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response);

                if (root == null) {
                    log.error("HTTP响应为空");
                    Platform.runLater(() -> showAlert("错误", "服务器返回空响应"));
                    return;
                }

                // 检查code字段（ApiResponse格式）
                JsonNode codeNode = root.get("code");
                if (codeNode == null) {
                    log.error("响应中缺少code字段");
                    Platform.runLater(() -> showAlert("错误", "服务器响应格式错误"));
                    return;
                }

                // code为200表示成功
                if (codeNode.asInt() == 200) {
                    JsonNode data = root.get("data");
                    if (data == null) {
                        log.error("响应中缺少data字段");
                        Platform.runLater(() -> showAlert("错误", "服务器响应缺少数据"));
                        return;
                    }

                    JsonNode records = data.get("records");
                    if (records == null) {
                        log.warn("响应中缺少records字段，使用空列表");
                        records = mapper.createArrayNode();
                    }

                    // 更新分页信息
                    totalRecords = data.has("total") ? data.get("total").asLong() : 0;
                    totalPages = data.has("pages") ? (int) data.get("pages").asLong() : 1;
                    currentPage = data.has("current") ? (int) data.get("current").asLong() : 1;

                    // 解析通话记录
                    List<CallRecordRow> recordRows = new ArrayList<>();
                    long totalDurationSum = 0;

                    for (JsonNode record : records) {
                        CallRecordRow row = new CallRecordRow();
                        row.setId(record.get("id").asText());

                        // 通话类型
                        int callType = record.get("callType").asInt();
                        row.setType(callType == 1 ? "音频" : callType == 2 ? "视频" : "群聊");

                        // 发起者和接收者（这里使用ID，实际应该查询用户名）
                        row.setCaller("用户" + record.get("callerId").asLong());
                        row.setCallee("用户" + record.get("calleeId").asLong());

                        // 时间
                        String startTime = record.get("startTime").asText();
                        String endTime = record.has("endTime") && !record.get("endTime").isNull()
                                ? record.get("endTime").asText()
                                : "-";
                        row.setStartTime(formatDateTime(startTime));
                        row.setEndTime(formatDateTime(endTime));

                        // 时长
                        int duration = record.has("duration") ? record.get("duration").asInt() : 0;
                        row.setDuration(formatDuration(duration));
                        totalDurationSum += duration;

                        // 状态
                        int status = record.has("status") ? record.get("status").asInt() : 1;
                        row.setStatus(getStatusText(status));

                        recordRows.add(row);
                    }

                    totalDuration = totalDurationSum;

                    // 更新UI
                    Platform.runLater(() -> {
                        callRecordTable.setItems(FXCollections.observableArrayList(recordRows));
                        updateStatistics();
                        updatePaginationButtons();
                    });

                    log.info("✅ 成功加载 {} 条通话记录", recordRows.size());

                } else {
                    // code不是200，表示失败
                    JsonNode messageNode = root.get("message");
                    String errorMsg = messageNode != null ? messageNode.asText() : "未知错误";
                    log.error("加载通话记录失败: code={}, message={}", codeNode.asInt(), errorMsg);
                    Platform.runLater(() -> showAlert("错误", "加载通话记录失败: " + errorMsg));
                }

            } catch (Exception e) {
                log.error("加载通话记录异常", e);
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
                Platform.runLater(() -> showAlert("错误", "加载通话记录失败: " + errorMsg));
            }
        }, "CallRecordLoader").start();
    }

    /**
     * 格式化日期时间
     */
    private String formatDateTime(String dateTime) {
        if (dateTime == null || dateTime.isEmpty() || "-".equals(dateTime)) {
            return "-";
        }
        try {
            // 如果已经是格式化的字符串，直接返回
            if (dateTime.contains(" ")) {
                return dateTime;
            }
            // 否则尝试解析
            LocalDateTime ldt = LocalDateTime.parse(dateTime);
            return ldt.format(DATE_FORMATTER);
        } catch (Exception e) {
            return dateTime;
        }
    }

    /**
     * 格式化通话时长
     */
    private String formatDuration(int seconds) {
        if (seconds <= 0) {
            return "0秒";
        }
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;

        if (hours > 0) {
            return String.format("%d小时%d分%d秒", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%d分%d秒", minutes, secs);
        } else {
            return String.format("%d秒", secs);
        }
    }

    /**
     * 获取状态文本
     */
    private String getStatusText(int status) {
        switch (status) {
            case 1: return "成功";
            case 2: return "未接";
            case 3: return "拒绝";
            case 4: return "取消";
            case 5: return "失败";
            default: return "未知";
        }
    }

    /**
     * 更新统计信息
     */
    private void updateStatistics() {
        totalRecordsLabel.setText("总记录数：" + totalRecords);

        int totalMinutes = (int) (totalDuration / 60);
        totalDurationLabel.setText("总通话时长：" + totalMinutes + "分钟");
    }

    /**
     * 更新分页按钮状态
     */
    private void updatePaginationButtons() {
        prevPageButton.setDisable(currentPage <= 1);
        nextPageButton.setDisable(currentPage >= totalPages);
        pageInfoLabel.setText(String.format("第 %d 页 / 共 %d 页", currentPage, totalPages));
    }

    /**
     * 刷新按钮处理
     */
    @FXML
    private void handleRefresh() {
        log.info("刷新通话记录");
        loadCallRecords();
    }

    /**
     * 上一页按钮处理
     */
    @FXML
    private void handlePrevPage() {
        if (currentPage > 1) {
            currentPage--;
            loadCallRecords();
        }
    }

    /**
     * 下一页按钮处理
     */
    @FXML
    private void handleNextPage() {
        if (currentPage < totalPages) {
            currentPage++;
            loadCallRecords();
        }
    }

    /**
     * 设置当前用户ID
     */
    public void setCurrentUserId(Long userId) {
        this.currentUserId = userId;
        loadCallRecords();
    }

    /**
     * 显示警告对话框
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 通话记录行数据类
     */
    public static class CallRecordRow {
        private String id;
        private String type;
        private String caller;
        private String callee;
        private String startTime;
        private String endTime;
        private String duration;
        private String status;

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getCaller() { return caller; }
        public void setCaller(String caller) { this.caller = caller; }

        public String getCallee() { return callee; }
        public void setCallee(String callee) { this.callee = callee; }

        public String getStartTime() { return startTime; }
        public void setStartTime(String startTime) { this.startTime = startTime; }

        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }

        public String getDuration() { return duration; }
        public void setDuration(String duration) { this.duration = duration; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
