package com.project.stockpay.common.websocket.config;

import com.project.stockpay.common.websocket.KisWebSocketClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class WebSocketHealthIndicator implements HealthIndicator {

    private final KisWebSocketClient webSocketClient;

    @Override
    public Health health() {
        boolean connected = webSocketClient.isConnected();

        if (connected) {
            return Health.up()
                    .withDetail("status", "웹소켓 연결됨")
                    .withDetail("timestamp", LocalDateTime.now().toString())
                    .build();
        } else {
            return Health.down()
                    .withDetail("status", "웹소켓 연결 안됨")
                    .withDetail("timestamp", LocalDateTime.now().toString())
                    .build();
        }
    }
}