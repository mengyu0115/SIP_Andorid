package com.sip.client.ui;

import com.sip.client.ui.service.SipClientService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

/**
 * SIP 即时通信系统 - JavaFX 客户端启动类（统一版本）
 *
 * 启动流程：
 * 1. 显示登录界面 (login.fxml)
 * 2. 登录成功后跳转到主界面 (main.fxml - 消息聊天)
 * 3. 可选择进入会议界面或管理后台
 *
 * @author SIP Team
 * @version 2.0 - 统一启动入口
 */
@Slf4j
public class Main extends Application {

    private static Stage primaryStage;
    private static SipClientService currentSipService;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;

        try {
            log.info("正在启动 SIP 即时通信客户端...");

            // 加载登录界面
            showLoginView();

            // 设置窗口
            stage.setTitle("SIP 即时通信系统");
            stage.setResizable(false);  // 登录窗口不可调整大小
            stage.setOnCloseRequest(event -> {
                cleanup();
            });
            stage.show();

            log.info("✅ SIP 客户端启动成功！");

        } catch (Exception e) {
            log.error("❌ 启动客户端失败", e);
            e.printStackTrace();
        }
    }

    /**
     * 显示登录界面
     */
    public static void showLoginView() {
        try {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(Main.class.getResource("/css/style.css").toExternalForm());
            primaryStage.setScene(scene);
            primaryStage.setTitle("SIP 即时通信系统 - 登录");
            primaryStage.setResizable(false);
        } catch (Exception e) {
            log.error("加载登录界面失败", e);
        }
    }

    /**
     * 显示主界面
     *
     * @param username    用户名
     * @param userId      用户ID
     * @param sipService  SIP客户端服务
     */
    public static void showMainView(String username, Long userId, SipClientService sipService) {
        try {
            // 保存当前的SipClientService
            currentSipService = sipService;

            FXMLLoader loader = new FXMLLoader(Main.class.getResource("/fxml/main.fxml"));
            Parent root = loader.load();

            // 传递用户名和SipClientService到主界面控制器
            com.sip.client.ui.controller.MainController controller = loader.getController();
            controller.setCurrentUser(username, userId);
            controller.setSipClientService(sipService);

            Scene scene = new Scene(root, 1200, 800);
            scene.getStylesheets().add(Main.class.getResource("/css/style.css").toExternalForm());
            primaryStage.setScene(scene);
            primaryStage.setTitle("SIP 即时通信系统 - " + username);
            primaryStage.setResizable(true);

            log.info("主界面加载成功: {}", username);

        } catch (Exception e) {
            log.error("加载主界面失败", e);
        }
    }

    /**
     * 清理资源
     */
    private void cleanup() {
        log.info("应用程序正在关闭，清理资源...");
        if (currentSipService != null) {
            currentSipService.shutdown();
            currentSipService = null;
        }
    }

    @Override
    public void stop() throws Exception {
        cleanup();
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
