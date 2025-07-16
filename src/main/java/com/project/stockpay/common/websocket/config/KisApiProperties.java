package com.project.stockpay.common.websocket.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kis")
@Data
public class KisApiProperties {
    private String appKey;
    private String appSecret;
    private String baseUrl;
    private String mockUrl;
    private String webSocketUrl;
    private String webSocketMockUrl;

    @Data
    public static class WebSocket {
        private int connectionTimeout = 30000;
        private int reconnectInterval = 5000;
        private int maxRetryAttempts = 10;
    }

    private WebSocket webSocket = new WebSocket();
}