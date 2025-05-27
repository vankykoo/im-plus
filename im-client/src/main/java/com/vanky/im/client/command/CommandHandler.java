package com.vanky.im.client.command;

import com.vanky.im.client.netty.NettyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author vanky
 * @date 2025/5/27
 * @description 命令处理器
 */
public class CommandHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(CommandHandler.class);
    
    /**
     * 处理用户输入的命令
     * 
     * @param command 命令
     * @param client 客户端
     * @return 如果返回true表示需要断开连接
     */
    public static boolean handleCommand(String command, NettyClient client) {
        // 转换为小写以便匹配
        String cmd = command.trim().toLowerCase();
        
        switch (cmd) {
            case "exit":
                return true;
            case "logout":
                logger.info("执行退出登录命令");
                client.logout();
                return true;
            case "help":
                printHelp();
                return false;
            default:
                return false;
        }
    }
    
    /**
     * 打印帮助信息
     */
    private static void printHelp() {
        System.out.println("可用命令:");
        System.out.println("  logout - 退出登录");
        System.out.println("  exit   - 断开连接");
        System.out.println("  help   - 显示帮助信息");
    }
} 