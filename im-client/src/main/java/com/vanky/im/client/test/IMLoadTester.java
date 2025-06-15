package com.vanky.im.client.test;

import com.vanky.im.client.netty.NettyClientTCP;
import com.vanky.im.common.util.MsgGenerator;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class IMLoadTester {
    // 模拟用户数
    private static final int USER_COUNT = 1000;
    // 每个用户发送消息数
    private static final int MSG_PER_USER = 100;
    // 统计成功连接数
    private static AtomicInteger successConnections = new AtomicInteger(0);
    // 统计成功发送消息数
    private static AtomicInteger successMessages = new AtomicInteger(0);
    
    public static void main(String[] args) throws InterruptedException {
        String host = "localhost";
        int port = 8900; // TCP端口
        
        CountDownLatch latch = new CountDownLatch(USER_COUNT);
        
        // 创建并启动多个客户端线程
        for (int i = 0; i < USER_COUNT; i++) {
            final int userId = i;
            new Thread(() -> {
                try {
                    NettyClientTCP client = new NettyClientTCP(host, port);
                    client.init();
                    client.connect();
                    
                    // 设置用户ID
                    String userIdStr = "user" + userId;
                    client.setUserId(userIdStr);
                    
                    // 发送登录消息
                    client.sendMessage(MsgGenerator.generateLoginMsg(userIdStr));
                    
                    successConnections.incrementAndGet();
                    
                    // 发送多条消息
                    for (int j = 0; j < MSG_PER_USER; j++) {
                        // 模拟发给随机用户
                        String toUserId = "user" + (int)(Math.random() * USER_COUNT);
                        String content = "测试消息 " + j + " 从 " + userIdStr + " 到 " + toUserId;
                        
                        client.sendMessage(MsgGenerator.generatePrivateMsg(
                            userIdStr, toUserId, content));
                        
                        successMessages.incrementAndGet();
                        
                        // 控制发送速率
                        Thread.sleep(10);
                    }
                    
                    // 等待一段时间后断开连接
                    Thread.sleep(1000);
                    client.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            }).start();
            
            // 控制客户端创建速率
            if (i % 10 == 0) {
                Thread.sleep(100);
            }
        }
        
        // 等待所有客户端完成
        latch.await();
        
        System.out.println("压测完成：");
        System.out.println("成功连接数: " + successConnections.get());
        System.out.println("成功消息数: " + successMessages.get());
    }
}