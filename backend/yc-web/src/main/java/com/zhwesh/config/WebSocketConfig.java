package com.zhwesh.config;

import com.zhwesh.websocket.JwtHandshakeInterceptor;
import com.zhwesh.websocket.MarketWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置：行情推送端点 /ws
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MarketWebSocketHandler marketWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Autowired
    public WebSocketConfig(MarketWebSocketHandler marketWebSocketHandler,
        JwtHandshakeInterceptor jwtHandshakeInterceptor) {
        this.marketWebSocketHandler = marketWebSocketHandler;
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(marketWebSocketHandler, "/ws")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns("*");
    }
}
