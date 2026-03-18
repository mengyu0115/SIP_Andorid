package com.sip.client.message;

import com.sip.client.core.SipManager;
import com.sip.client.message.SipMessageManager.IncomingMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.Scanner;

/**
 * SIP 消息功能 Demo 演示程序
 *
 * 功能:
 * 1. 初始化 SIP 核心模块
 * 2. 初始化 SIP 消息模块
 * 3. 发送文本消息
 * 4. 接收并显示消息
 *
 * 使用方法:
 * 1. 启动两个客户端实例（Alice和Bob）
 * 2. Alice 给 Bob 发消息
 * 3. Bob 收到消息并显示
 *
 * @author 成员3
 * @version 1.0
 */
@Slf4j
public class MessageDemo {

    public static void main(String[] args) {
        log.info("========================================");
        log.info("    SIP 消息功能 Demo - 成员3模块      ");
        log.info("========================================");

        Scanner scanner = new Scanner(System.in);

        try {
            // Step 1: 输入用户名
            System.out.print("请输入您的用户名 (例如: alice 或 bob): ");
            String username = scanner.nextLine().trim();

            // Step 2: 配置参数
            String localIp = "0.0.0.0";
            int localPort = getPortForUser(username);  // 不同用户使用不同端口
            String serverHost = "10.29.209.85";  // 本地测试，指向本地Kamailio
            int serverPort = 5060;

            log.info("========================================");
            log.info("当前用户: {}", username);
            log.info("本地监听: {}:{}", localIp, localPort);
            log.info("Kamailio服务器: {}:{}", serverHost, serverPort);
            log.info("========================================");

            // Step 3: 初始化 core/SipManager
            log.info("正在初始化 SIP 核心模块...");
            SipManager sipManager = SipManager.getInstance();
            sipManager.initialize(localIp, localPort);

            // Step 4: 初始化 message/SipMessageManager
            log.info("正在初始化 SIP 消息模块...");
            SipMessageManager messageManager = SipMessageManager.getInstance();
            messageManager.initialize(username, serverHost, serverPort);

            // Step 5: 设置消息接收回调
            messageManager.setMessageCallback(message -> {
                System.out.println("\n╔══════════════════════════════════════╗");
                System.out.println("║        收到新消息！               ║");
                System.out.println("╠══════════════════════════════════════╣");
                System.out.println("║ 发送者: " + message.getFromUsername());
                System.out.println("║ 内容  : " + message.getContent());
                System.out.println("╚══════════════════════════════════════╝\n");
            });

            log.info("========================================");
            log.info("初始化完成！现在可以发送和接收消息了");
            log.info("========================================\n");

            // Step 6: 进入命令行交互模式
            showHelp();

            while (true) {
                System.out.print(username + " > ");
                String command = scanner.nextLine().trim();

                if (command.isEmpty()) {
                    continue;
                }

                if (command.equalsIgnoreCase("quit") || command.equalsIgnoreCase("exit")) {
                    break;
                }

                if (command.equalsIgnoreCase("help")) {
                    showHelp();
                    continue;
                }

                // 解析命令: send <username> <message>
                if (command.startsWith("send ")) {
                    String[] parts = command.split(" ", 3);
                    if (parts.length < 3) {
                        System.out.println("错误: 格式应为 send <用户名> <消息内容>");
                        continue;
                    }

                    String toUser = parts[1];
                    String messageContent = parts[2];

                    System.out.println("\n正在发送消息给 " + toUser + "...");
                    boolean success = messageManager.sendTextMessage(toUser, messageContent);

                    if (success) {
                        System.out.println("✓ 消息发送成功\n");
                    } else {
                        System.out.println("✗ 消息发送失败\n");
                    }
                } else {
                    System.out.println("未知命令: " + command);
                    System.out.println("输入 help 查看帮助");
                }
            }

            // Step 7: 清理资源
            log.info("\n正在关闭 SIP 消息管理器...");
            messageManager.shutdown();

            log.info("正在关闭 SIP 核心模块...");
            sipManager.shutdown();

            log.info("程序已退出，再见！");

        } catch (Exception e) {
            log.error("程序运行出错", e);
        } finally {
            scanner.close();
        }
    }

    /**
     * 显示帮助信息
     */
    private static void showHelp() {
        System.out.println("\n╔══════════════════════════════════════╗");
        System.out.println("║         命令帮助                   ║");
        System.out.println("╠══════════════════════════════════════╣");
        System.out.println("║ send <用户名> <消息>  - 发送消息    ║");
        System.out.println("║ help                  - 显示帮助    ║");
        System.out.println("║ quit / exit           - 退出程序    ║");
        System.out.println("╚══════════════════════════════════════╝\n");
        System.out.println("示例:");
        System.out.println("  send bob Hello, Bob!");
        System.out.println("  send alice How are you?");
        System.out.println();
    }

    /**
     * 根据用户名分配端口（避免端口冲突）
     */
    private static int getPortForUser(String username) {
        int basePort = 5061;

        switch (username.toLowerCase()) {
            case "alice":
                return basePort;
            case "bob":
                return basePort + 1;
            case "charlie":
                return basePort + 2;
            default:
                // 使用用户名哈希值分配端口
                return basePort + Math.abs(username.hashCode() % 10);
        }
    }
}
