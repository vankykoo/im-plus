package com.vanky.im.gateway.netty.handler;

import com.vanky.im.common.util.TokenUtil;
import com.vanky.im.gateway.session.UserChannelManager;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ChannelHandler.Sharable
public class HttpAuthHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger logger = LoggerFactory.getLogger(HttpAuthHandler.class);

    @Autowired
    private TokenUtil tokenUtil;

    @Autowired
    private UserChannelManager userChannelManager;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (!request.decoderResult().isSuccess() || !"websocket".equals(request.headers().get("Upgrade"))) {
            // 非 WebSocket 握手请求，直接传递
            ctx.fireChannelRead(request.retain());
            return;
        }

        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        Map<String, List<String>> parameters = decoder.parameters();
        String token = getParameter(parameters, "token");
        String userId = getParameter(parameters, "userId");

        if (token == null || userId == null) {
            sendAuthFailedResponse(ctx, "Missing token or userId");
            return;
        }

        String userIdFromToken = tokenUtil.verifyToken(token);
        if (!userId.equals(userIdFromToken)) {
            sendAuthFailedResponse(ctx, "Invalid token");
            return;
        }

        // 认证成功，将 userId 存入 channel 属性
        ctx.channel().attr(UserChannelManager.USER_ID_ATTR).set(userId);
        userChannelManager.bindChannel(userId, ctx.channel());

        // 将 URI 重写为不带参数的路径，以便 WebSocketServerProtocolHandler 匹配
        request.setUri(decoder.path());

        // 将请求传递给下一个 handler
        ctx.fireChannelRead(request.retain());
    }

    private void sendAuthFailedResponse(ChannelHandlerContext ctx, String message) {
        logger.warn("WebSocket认证失败: {}", message);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED);
        ctx.writeAndFlush(response);
        ctx.close();
    }

    private String getParameter(Map<String, List<String>> parameters, String name) {
        List<String> values = parameters.get(name);
        if (values != null && !values.isEmpty()) {
            return values.get(0);
        }
        return null;
    }
}