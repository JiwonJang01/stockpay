package com.project.stockpay.common.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.stockpay.common.websocket.dto.RealTimeOrderbookDto;
import com.project.stockpay.common.websocket.dto.RealTimeStockPriceDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class KisWebSocketClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${kis.app-key}")
    private String appKey;

    @Value("${kis.app-secret}")
    private String appSecret;

    // 실전: ws://ops.koreainvestment.com:21000
    // 모의: ws://ops.koreainvestment.com:31000
    @Value("${kis.websocket-url:ws://ops.koreainvestment.com:31000}")
    private String webSocketUrl;

    private WebSocketSession webSocketSession;
    private String approvalKey;
    private volatile boolean connected = false;

    // 웹소켓 승인키 발급
    public String getApprovalKey() {
        try {
            if (approvalKey != null) {
                return approvalKey;
            }

            // 실전: https://openapi.koreainvestment.com:9443
            // 모의: https://openapivts.koreainvestment.com:29443
            String url = "https://openapivts.koreainvestment.com:29443/oauth2/Approval"; // 모의투자용

            Map<String, String> requestBody = Map.of(
                    "grant_type", "client_credentials",
                    "appkey", appKey,
                    "secretkey", appSecret
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("approval_key")) {
                approvalKey = (String) response.get("approval_key");
                log.info("웹소켓 승인키 발급 성공: {}", approvalKey);
                return approvalKey;
            }

        } catch (Exception e) {
            log.error("웹소켓 승인키 발급 실패", e);
        }

        throw new RuntimeException("웹소켓 승인키 발급 실패");
    }

    // 웹소켓 연결
    @PostConstruct
    public void connect() {
        try {
            log.info("한국투자증권 웹소켓 연결 시작...");

            // 승인키 발급
            String approval = getApprovalKey();

            log.info("WebSocket URL: {}", webSocketUrl);  // URL 확인용 로그 추가

            // 웹소켓 연결
            org.springframework.web.socket.client.WebSocketClient client = new StandardWebSocketClient();
            WebSocketConnectionManager connectionManager = new WebSocketConnectionManager(
                    client,
                    new KisWebSocketHandler(),
                    webSocketUrl
            );

            connectionManager.setAutoStartup(true);
            connectionManager.start();

            // 연결 대기
            int maxAttempts = 30;
            int attempts = 0;
            while (!connected && attempts < maxAttempts) {
                Thread.sleep(1000);
                attempts++;
                log.debug("웹소켓 연결 대기 중... ({}/{})", attempts, maxAttempts);
            }

            if (connected) {
                log.info("한국투자증권 웹소켓 연결 성공");
            } else {
                log.error("한국투자증권 웹소켓 연결 실패 (타임아웃)");
            }

        } catch (Exception e) {
            log.error("웹소켓 연결 실패", e);
        }
    }

    // 실시간 주식 체결가 구독
    public void subscribeStockPrice(String stockCode) {
        if (!connected || webSocketSession == null) {
            log.warn("웹소켓이 연결되지 않았습니다");
            return;
        }

        try {
            Map<String, Object> subscribeMessage = Map.of(
                    "header", Map.of(
                            "approval_key", getApprovalKey(),
                            "custtype", "P",  // 개인
                            "tr_type", "1",   // 등록
                            "content-type", "utf-8"
                    ),
                    "body", Map.of(
                            "input", Map.of(
                                    "tr_id", "H0STCNT0",  // 주식체결 TR_ID
                                    "tr_key", stockCode   // 종목코드
                            )
                    )
            );

            String message = objectMapper.writeValueAsString(subscribeMessage);
            webSocketSession.sendMessage(new TextMessage(message));

            log.info("종목 {} 실시간 체결가 구독 요청", stockCode);

        } catch (Exception e) {
            log.error("실시간 구독 실패: {}", stockCode, e);
        }
    }

    // 실시간 주식 호가 구독
    public void subscribeStockOrderbook(String stockCode) {
        if (!connected || webSocketSession == null) {
            log.warn("웹소켓이 연결되지 않았습니다");
            return;
        }

        try {
            Map<String, Object> subscribeMessage = Map.of(
                    "header", Map.of(
                            "approval_key", getApprovalKey(),
                            "custtype", "P",
                            "tr_type", "1",
                            "content-type", "utf-8"
                    ),
                    "body", Map.of(
                            "input", Map.of(
                                    "tr_id", "H0STASP0",  // 주식호가 TR_ID
                                    "tr_key", stockCode
                            )
                    )
            );

            String message = objectMapper.writeValueAsString(subscribeMessage);
            webSocketSession.sendMessage(new TextMessage(message));

            log.info("종목 {} 실시간 호가 구독 요청", stockCode);

        } catch (Exception e) {
            log.error("실시간 호가 구독 실패: {}", stockCode, e);
        }
    }

    // 구독 해제
    public void unsubscribe(String trId, String stockCode) {
        if (!connected || webSocketSession == null) {
            return;
        }

        try {
            Map<String, Object> unsubscribeMessage = Map.of(
                    "header", Map.of(
                            "approval_key", getApprovalKey(),
                            "custtype", "P",
                            "tr_type", "2",  // 해제
                            "content-type", "utf-8"
                    ),
                    "body", Map.of(
                            "input", Map.of(
                                    "tr_id", trId,
                                    "tr_key", stockCode
                            )
                    )
            );

            String message = objectMapper.writeValueAsString(unsubscribeMessage);
            webSocketSession.sendMessage(new TextMessage(message));

            log.info("종목 {} 구독 해제 ({})", stockCode, trId);

        } catch (Exception e) {
            log.error("구독 해제 실패: {} - {}", trId, stockCode, e);
        }
    }

    // 웹소켓 연결 상태 확인
    public boolean isConnected() {
        return connected && webSocketSession != null && webSocketSession.isOpen();
    }

    // 웹소켓 핸들러
    private class KisWebSocketHandler extends TextWebSocketHandler {

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            webSocketSession = session;
            connected = true;
            log.info("한국투자증권 웹소켓 연결 성공: {}", session.getId());

            // 연결 후 주요 종목 자동 구독 (테스트용)
            subscribeToPopularStocks();
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            String payload = message.getPayload();

            try {
                // 실시간 데이터 처리
                if (payload.startsWith("0") || payload.startsWith("1")) {
                    handleRealtimeData(payload);
                } else {
                    // JSON 응답 처리
                    handleJsonResponse(payload);
                }

            } catch (Exception e) {
                log.error("웹소켓 메시지 처리 실패: {}", payload, e);
            }
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
            log.error("웹소켓 전송 오류", exception);
            connected = false;
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
            log.warn("웹소켓 연결 종료: {}", status);
            connected = false;
            webSocketSession = null;

            // 재연결 시도
            scheduleReconnect();
        }
    }

    // 실시간 데이터 처리
    private void handleRealtimeData(String data) {
        try {
            String[] parts = data.split("\\|", 4);

            if (parts.length >= 4) {
                String dataType = parts[0];  // 0: 실시간, 1: 체결통보
                String trId = parts[1];      // TR_ID
                String dataCount = parts[2]; // 데이터 개수
                String realData = parts[3];  // 실제 데이터

                log.debug("실시간 데이터 수신: TR_ID={}, Count={}", trId, dataCount);

                if ("H0STCNT0".equals(trId)) {
                    // 주식 체결가 데이터 처리
                    processStockPriceData(realData);
                } else if ("H0STASP0".equals(trId)) {
                    // 주식 호가 데이터 처리
                    processStockOrderbookData(realData);
                }
            }

        } catch (Exception e) {
            log.error("실시간 데이터 파싱 실패: {}", data, e);
        }
    }

    // JSON 응답 처리
    @SuppressWarnings("unchecked")
    private void handleJsonResponse(String data) {
        try {
            Map<String, Object> response = objectMapper.readValue(data, Map.class);
            Map<String, Object> header = (Map<String, Object>) response.get("header");
            String trId = (String) header.get("tr_id");

            if ("PINGPONG".equals(trId)) {
                // PING에 대한 PONG 응답
                handlePingPong(data);
            } else {
                // 일반 응답 처리
                Map<String, Object> body = (Map<String, Object>) response.get("body");
                String rtCd = (String) body.get("rt_cd");
                String msg = (String) body.get("msg1");

                if ("0".equals(rtCd)) {
                    log.info("API 응답 성공: TR_ID={}, MSG={}", trId, msg);
                } else {
                    log.warn("API 응답 오류: TR_ID={}, CODE={}, MSG={}", trId, rtCd, msg);
                }
            }

        } catch (Exception e) {
            log.error("JSON 응답 파싱 실패: {}", data, e);
        }
    }

    // 주식 체결가 데이터 처리
    private void processStockPriceData(String data) {
        try {
            String[] fields = data.split("\\^");

            if (fields.length >= 15) {
                String stockCode = fields[0];      // 종목코드
                String tradeTime = fields[1];      // 체결시간
                String currentPrice = fields[2];   // 현재가
                String changeSign = fields[3];     // 전일대비부호
                String changeAmount = fields[4];   // 전일대비
                String changeRate = fields[5];     // 전일대비율
                String volume = fields[13];        // 누적거래량
                String tradingValue = fields[14];  // 누적거래대금

                // 실시간 주가 데이터 DTO 생성
                RealTimeStockPriceDto priceData = RealTimeStockPriceDto.builder()
                        .stockCode(stockCode)
                        .tradeTime(tradeTime)
                        .currentPrice(Integer.parseInt(currentPrice))
                        .changeSign(changeSign)
                        .changeAmount(Integer.parseInt(changeAmount))
                        .changeRate(Double.parseDouble(changeRate))
                        .volume(Long.parseLong(volume))
                        .tradingValue(Long.parseLong(tradingValue))
                        .timestamp(System.currentTimeMillis())
                        .build();

                // Redis 캐시에 저장
                String cacheKey = "realtime:stock:" + stockCode;
                redisTemplate.opsForValue().set(cacheKey, priceData, Duration.ofMinutes(1));

                log.debug("실시간 주가 업데이트: {} - {}", stockCode, currentPrice);

                // TODO: 웹소켓으로 클라이언트에 전송
                // messagingTemplate.convertAndSend("/topic/stock/" + stockCode, priceData);
            }

        } catch (Exception e) {
            log.error("주식 체결가 데이터 처리 실패: {}", data, e);
        }
    }

    // 주식 호가 데이터 처리
    private void processStockOrderbookData(String data) {
        try {
            String[] fields = data.split("\\^");

            if (fields.length >= 60) {
                String stockCode = fields[0];  // 종목코드

                // 매도호가 1~10 (fields[3]~[12])
                // 매수호가 1~10 (fields[13]~[22])
                // 매도잔량 1~10 (fields[23]~[32])
                // 매수잔량 1~10 (fields[33]~[42])

                RealTimeOrderbookDto orderbookData = RealTimeOrderbookDto.builder()
                        .stockCode(stockCode)
                        .sellPrices(extractPrices(fields, 3, 12))   // 매도호가
                        .buyPrices(extractPrices(fields, 13, 22))   // 매수호가
                        .sellVolumes(extractVolumes(fields, 23, 32)) // 매도잔량
                        .buyVolumes(extractVolumes(fields, 33, 42))  // 매수잔량
                        .timestamp(System.currentTimeMillis())
                        .build();

                // Redis 캐시에 저장
                String cacheKey = "realtime:orderbook:" + stockCode;
                redisTemplate.opsForValue().set(cacheKey, orderbookData, Duration.ofMinutes(1));

                log.debug("실시간 호가 업데이트: {}", stockCode);
            }

        } catch (Exception e) {
            log.error("주식 호가 데이터 처리 실패: {}", data, e);
        }
    }

    // PING/PONG 처리
    private void handlePingPong(String data) {
        try {
            if (webSocketSession != null && webSocketSession.isOpen()) {
                // PONG 응답
                PongMessage pongMessage = new PongMessage(ByteBuffer.wrap(data.getBytes()));
                webSocketSession.sendMessage(pongMessage);
                log.debug("PONG 응답 전송");
            }
        } catch (Exception e) {
            log.error("PING/PONG 처리 실패", e);
        }
    }

    // 주요 종목 자동 구독
    private void subscribeToPopularStocks() {
        // 테스트용 주요 종목들
        String[] popularStocks = {
                "005930", // 삼성전자
                "000660", // SK하이닉스
                "035420", // NAVER
                "051910", // LG화학
                "006400"  // 삼성SDI
        };

        for (String stockCode : popularStocks) {
            try {
                subscribeStockPrice(stockCode);
                Thread.sleep(100); // 0.1초 간격
            } catch (Exception e) {
                log.error("종목 구독 실패: {}", stockCode, e);
            }
        }
    }

    // 재연결 스케줄링
    private void scheduleReconnect() {
        // 5초 후 재연결 시도
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                log.info("웹소켓 재연결 시도...");
                connect();
            } catch (Exception e) {
                log.error("웹소켓 재연결 실패", e);
            }
        }).start();
    }

    // 가격 배열 추출
    private Integer[] extractPrices(String[] fields, int start, int end) {
        Integer[] prices = new Integer[end - start + 1];
        for (int i = start; i <= end; i++) {
            try {
                prices[i - start] = Integer.parseInt(fields[i]);
            } catch (Exception e) {
                prices[i - start] = 0;
            }
        }
        return prices;
    }

    // 잔량 배열 추출
    private Long[] extractVolumes(String[] fields, int start, int end) {
        Long[] volumes = new Long[end - start + 1];
        for (int i = start; i <= end; i++) {
            try {
                volumes[i - start] = Long.parseLong(fields[i]);
            } catch (Exception e) {
                volumes[i - start] = 0L;
            }
        }
        return volumes;
    }
}