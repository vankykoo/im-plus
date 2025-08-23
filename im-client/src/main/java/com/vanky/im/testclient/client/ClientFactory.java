package com.vanky.im.testclient.client;

/**
 * 客户端工厂类
 * 根据指定的类型创建并返回一个 IMClient 实例。
 * 这使得上层应用可以轻松地在 TCP 和 WebSocket 之间切换，而无需更改业务逻辑代码。
 *
 * @author vanky
 * @since 2025-08-23
 */
public class ClientFactory {

    /**
     * 创建一个 IMClient 实例。
     *
     * @param type           客户端类型，支持 "tcp" 或 "websocket"。
     * @param userId         用户ID。
     * @param token          用户令牌。
     * @param messageHandler 消息处理器，用于回调上层业务逻辑。
     * @return 一个实现了 IMClient 接口的客户端实例。
     * @throws IllegalArgumentException 如果传入了不支持的客户端类型。
     */
    public static IMClient createClient(String type, String userId, String token, IMClient.MessageHandler messageHandler) {
        if (type == null) {
            throw new IllegalArgumentException("客户端类型不能为空");
        }

        switch (type.toLowerCase()) {
            case "tcp":
                return new NettyTcpClient(userId, token, messageHandler);
            case "websocket":
                return new RealWebSocketClient(userId, token, messageHandler);
            default:
                throw new IllegalArgumentException("不支持的客户端类型: " + type + "。有效类型为 'tcp' 或 'websocket'。");
        }
    }
}