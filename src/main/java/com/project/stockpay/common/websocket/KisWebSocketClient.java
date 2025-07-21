package com.project.stockpay.common.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.stockpay.common.websocket.dto.KisApiResponse;
import com.project.stockpay.common.websocket.dto.KisApprovalKeyResponse;
import com.project.stockpay.common.websocket.dto.KisWebSocketRequest;
import com.project.stockpay.common.websocket.dto.RealTimeOrderbookDto;
import com.project.stockpay.common.websocket.dto.RealTimeStockPriceDto;
import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import reactor.util.retry.Retry;

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
  private WebSocketConnectionManager connectionManager;
  private String approvalKey;
  private LocalDateTime approvalKeyExpire; // 승인키 만료 시간
  private volatile boolean connected = false;
  private final Object approvalKeyLock = new Object(); // 승인키 갱신 동기화


  /** 승인키 유효성 확인
   * - 승인키는 24시간 만료
   * - 12시간 마다 갱신
   */
  private boolean isApprovalKeyValid() {
    if (approvalKey == null || approvalKeyExpire == null) {
      return false;
    }
    // 만료 30분 전에 미리 갱신
    return LocalDateTime.now().isBefore(approvalKeyExpire.minusMinutes(30));
  }

  // 웹소켓 승인키 발급/갱신
  public String getApprovalKey() {
    try {
      // 기존 승인키가 유효하면 재사용
      if (isApprovalKeyValid()) {
        log.debug("기존 승인키 재사용 (만료시간: {})", approvalKeyExpire);
        return approvalKey;
      }

      log.info("승인키 발급/갱신 시작...");

      // 실전: https://openapi.koreainvestment.com:9443
      // 모의: https://openapivts.koreainvestment.com:29443
      String url = "https://openapivts.koreainvestment.com:29443/oauth2/Approval";

      Map<String, String> requestBody = Map.of(
          "grant_type", "client_credentials",
          "appkey", appKey,
          "secretkey", appSecret
      );

        // 이펙티브 자바 - 제네릭
//      @SuppressWarnings("unchecked")
//      Map<String, Object> response = webClient.post()
//          .uri(url)
//          .header("Content-Type", "application/json")
//          .bodyValue(requestBody)
//          .retrieve()
//          .bodyToMono(Map.class)
//          .block();

      KisApprovalKeyResponse response = webClient.post()
          .uri(url)
          .header("Content-Type", "application/json")
          .bodyValue(requestBody)
          .retrieve()
          .bodyToMono(KisApprovalKeyResponse.class)
          .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(2))
              .doBeforeRetry(retrySignal ->
                  log.warn("승인키 발급 재시도 중... ({}회차)", retrySignal.totalRetries() + 1))
              .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                  new RuntimeException("승인키 발급 재시도 횟수 초과"))
          )
          .block(Duration.ofSeconds(30));

      if (response != null && response.isSuccess()) {
        String oldKey = approvalKey;
        approvalKey = response.getApprovalKey();
        approvalKeyExpire = LocalDateTime.now().plusHours(12); // 12시간 후 만료

        // Redis에 승인키와 만료시간 저장 (서버 재시작 시 복구용)
        saveApprovalKeyToRedis();

        log.info("승인키 발급/갱신 완료 (기존: {}, 신규: {}, 만료: {})",
            oldKey != null ? oldKey.substring(0, 8) : "없음",
            approvalKey.substring(0, 8),
            approvalKeyExpire);
        return approvalKey;
      } else {
        String errorMsg = response != null ? response.getErrorMessage() : "응답 없음";
        throw new RuntimeException("승인키 발급 실패: " + errorMsg);
      }

    } catch (Exception e) {
      log.error("승인키 발급/갱신 실패 - 기존 키로 계속 시도", e);

      // 갱신 실패해도 기존 키가 있으면 일단 사용
      if (approvalKey != null) {
        log.warn("기존 승인키로 계속 진행 (만료 위험 있음)");
        return approvalKey;
      }

      throw new RuntimeException("승인키 발급/갱신 실패", e);
    }
  }

  // Redis에 승인키 저장
  private void saveApprovalKeyToRedis() {
    try {
      String keyPrefix = "kis:approval:";
      redisTemplate.opsForValue().set(keyPrefix + "key", approvalKey, Duration.ofHours(12));
      redisTemplate.opsForValue().set(keyPrefix + "expiry", approvalKeyExpire.toString(), Duration.ofHours(12));
    } catch (Exception e) {
      log.warn("승인키 Redis 저장 실패", e);
    }
  }

  // Redis에서 승인키 복구
  private void loadApprovalKeyFromRedis() {
    try {
      String keyPrefix = "kis:approval:";
      String cachedKey = (String) redisTemplate.opsForValue().get(keyPrefix + "key");
      String cachedExpire = (String) redisTemplate.opsForValue().get(keyPrefix + "expiry");

      if (cachedKey != null && cachedExpire != null) {
        approvalKey = cachedKey;
        approvalKeyExpire = LocalDateTime.parse(cachedExpire);

        if (isApprovalKeyValid()) {
          log.info("Redis에서 승인키 복구 성공 (만료시간: {})", approvalKeyExpire);
        } else {
          log.info("Redis의 승인키가 만료됨 - 새로 발급 필요");
          approvalKey = null;
          approvalKeyExpire = null;
        }
      }
    } catch (Exception e) {
      log.warn("승인키 Redis 복구 실패", e);
    }
  }

  // 승인키 자동 갱신 스케줄러
  private void startApprovalKeyScheduler() {
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "ApprovalKeyScheduler");
      t.setDaemon(true);
      return t;
    });

    // 1시간마다 승인키 유효성 확인 및 갱신
    scheduler.scheduleAtFixedRate(() -> {
      try {
        if (!isApprovalKeyValid()) {
          log.info("승인키 자동 갱신 시작...");
          String newKey = getApprovalKey();
          if (newKey != null) {
            log.info("승인키 자동 갱신 완료");
          }
        } else {
          long remainingHours = Duration.between(LocalDateTime.now(), approvalKeyExpire).toHours();
          log.debug("승인키 유효 ({}시간 남음)", remainingHours);
        }
        // 웹소켓 연결 상태도 함께 확인
        if (!isConnected()) {
          log.warn("웹소켓 연결 끊어짐 감지 - 재연결 시도");
          connectWebSocket();
        }

      } catch (Exception e) {
        log.error("승인키/연결 상태 확인 실패", e);
      }
    }, 0, 1, TimeUnit.HOURS);

    log.info("승인키 자동 갱신 스케줄러 시작 (1시간 간격)");
  }

  // 애플리케이션 시작 시 실행
  @PostConstruct
  public void initialize() {
    log.info("주식 실시간 데이터 서비스 시작...");

    // 1. Redis에서 기존 승인키 복구 시도
    loadApprovalKeyFromRedis();

    // 2. 승인키 자동 갱신 스케줄러 시작
    startApprovalKeyScheduler();

    // 3. 웹소켓 연결 및 주요 종목 구독 시작
    connectAndStartStreaming();
  }

  private void connectAndStartStreaming() {
    try {
      // 승인키 확보
      String approval = getApprovalKey();

      // 웹소켓 연결
      connectWebSocket();

      // 연결 성공 시 주요 종목 자동 구독
      if (connected) {
        subscribeToPopularStocks();
        log.info("실시간 주식 데이터 스트리밍 시작 완료");
      }

    } catch (Exception e) {
      log.error("실시간 데이터 서비스 시작 실패", e);
      // 5초 후 재시도
      scheduleReconnectAttempt(0);
    }
  }

  // 연결 시작 시 승인키 확인
  private void connectWebSocket() {
    int maxAttempts = 5;
    int attempt = 0;

    while (attempt < maxAttempts) {
      try {
        attempt++;
        log.info("웹소켓 연결 시도 ({}회차)...", attempt);

        // 기존 연결이 있으면 정리
        if (connectionManager != null && connectionManager.isRunning()) {
          connectionManager.stop();
          Thread.sleep(1000); // 정리 대기
        }

        org.springframework.web.socket.client.WebSocketClient client = new StandardWebSocketClient();
        connectionManager = new WebSocketConnectionManager(
            client,
            new KisWebSocketHandler(),
            webSocketUrl
        );

        connectionManager.setAutoStartup(true);
        connectionManager.start();

        // 각 시도마다 10초 대기
        if (waitForConnection(10)) {
          log.info("웹소켓 연결 성공 ({}회차)", attempt);
          return; // 성공하면 즉시 종료
        }

        log.warn("웹소켓 연결 실패 ({}회차)", attempt);

        // 마지막 시도가 아니면 지수 백오프로 대기
        if (attempt < maxAttempts) {
          long delay = Math.min(16, (long) Math.pow(2, attempt)); // 2, 4, 8, 16초
          log.info("{}초 후 재시도...", delay);
          Thread.sleep(delay * 1000);
        }

      } catch (Exception e) {
        log.error("웹소켓 연결 시도 실패 ({}회차): {}", attempt, e.getMessage());

        if (attempt < maxAttempts) {
          try {
            long delay = Math.min(16, (long) Math.pow(2, attempt));
            log.info("{}초 후 재시도...", delay);
            Thread.sleep(delay * 1000);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      }
    }
    throw new RuntimeException("웹소켓 연결 실패 - " + maxAttempts + "회 시도 모두 실패");
  }

  // 연결 대기 (실제 연결 상태 확인)
  private boolean waitForConnection(int maxWaitSeconds) {
    int attempts = 0;
    while (!connected && attempts < maxWaitSeconds) {
      try {
        Thread.sleep(1000);
        attempts++;
        log.debug("웹소켓 연결 대기 중... ({}/{}초)", attempts, maxWaitSeconds);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    return connected;
  }

  // 재연결 스케줄링 (지수 백오프 적용)
  private void scheduleReconnectAttempt(int attemptCount) {
    // 지수 백오프: 2^attemptCount 초 대기 (최대 32초)
    long delay = Math.min(32, (long) Math.pow(2, attemptCount));

    log.info("{}초 후 웹소켓 재연결 시도... ({}회차)", delay, attemptCount + 1);

    new Thread(() -> {
      try {
        Thread.sleep(delay * 1000);
        if (attemptCount < 5) {
          connectAndStartStreaming();
        } else {
          log.error("웹소켓 연결 재시도 횟수 초과");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("웹소켓 재연결 스케줄 중단됨");
      } catch (Exception e) {
        log.error("웹소켓 재연결 실패 ({}회차)", attemptCount + 1, e);
        if (attemptCount < 5) {
          scheduleReconnectAttempt(attemptCount + 1);
        }
      }
    }).start();
  }

  // 실시간 주식 체결가 구독
  public void subscribeStockPrice(String stockCode) {
    if (!connected || webSocketSession == null) {
      log.warn("웹소켓이 연결되지 않았습니다");
      return;
    }

    try {
      KisWebSocketRequest request = KisWebSocketRequest.createStockPriceSubscription(
          getApprovalKey(),
          stockCode
      );

      String message = objectMapper.writeValueAsString(request);
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
      KisWebSocketRequest request = KisWebSocketRequest.createStockOrderbookSubscription(
          getApprovalKey(),
          stockCode
      );

      String message = objectMapper.writeValueAsString(request);
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
      KisWebSocketRequest request = KisWebSocketRequest.createUnsubscription(
          getApprovalKey(),
          trId,
          stockCode
      );

      String message = objectMapper.writeValueAsString(request);
      webSocketSession.sendMessage(new TextMessage(message));

      log.info("종목 {} 구독 해제 ({})", stockCode, trId);

    } catch (Exception e) {
      log.error("구독 해제 실패: {} - {}", trId, stockCode, e);
    }
  }

  // 웹소켓 연결 상태 확인
  public boolean isConnected() {
    return connected &&
        webSocketSession != null &&
        webSocketSession.isOpen() &&
        connectionManager != null &&
        connectionManager.isRunning();
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
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
        throws Exception {
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
    public void handleTransportError(WebSocketSession session, Throwable exception)
        throws Exception {
      log.error("웹소켓 전송 오류", exception);
      connected = false;
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
        throws Exception {
      log.warn("웹소켓 연결 종료: {}", status);
      connected = false;
      webSocketSession = null;

      // 재연결 시도
      scheduleReconnectAttempt(0);
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

        switch (trId) {
          case "H0STCNT0":
            processStockPriceData(realData);
            break;
          case "H0STASP0":
            processStockOrderbookData(realData);
            break;
          default:
            log.debug("알 수 없는 TR_ID: {}", trId);
        }
      } else {
        log.warn("실시간 데이터 형식이 잘못됨: {}", data);
      }

    } catch (Exception e) {
      log.error("실시간 데이터 파싱 실패: {}", data, e);
    }
  }

  // JSON 응답 처리
  private void handleJsonResponse(String data) {
    try {
      KisApiResponse<KisApiResponse.KisResponseBody> response = objectMapper.readValue(
          data,
          objectMapper.getTypeFactory().constructParametricType(
              KisApiResponse.class,
              KisApiResponse.KisResponseBody.class
          )
      );

      String trId = response.getHeader() != null ? response.getHeader().getTrId() : "UNKNOWN";

      if ("PINGPONG".equals(trId)) {
        // PING에 대한 PONG 응답
        handlePingPong(data);
      } else {
        // 일반 응답 처리
        if (response.isSuccess()) {
          log.info("API 응답 성공: TR_ID={}, MSG={}", trId, response.getErrorMessage());
        } else {
          log.warn("API 응답 오류: TR_ID={}, MSG={}", trId, response.getErrorMessage());
          updateErrorStats("API 응답 오류: " + response.getErrorMessage());
        }
      }

    } catch (Exception e) {
      log.error("JSON 응답 파싱 실패: {}", data, e);
      updateErrorStats("JSON 응답 파싱 실패: " + e.getMessage());
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

        // 시간 파싱 수정: HHMMSS 형식을 LocalDateTime으로 변환
        LocalDateTime tradeDateTime = parseTradeTime(tradeTime);

        // 실시간 주가 데이터 DTO 생성
        RealTimeStockPriceDto priceData = RealTimeStockPriceDto.builder()
            .stockCode(stockCode)
            .tradeTime(parseTradeTime(tradeTime))
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

        // 통계 업데이트
        updateMessageStats("STOCK_PRICE");
      }

    } catch (Exception e) {
      log.error("주식 체결가 데이터 처리 실패: {}", data, e);

      updateErrorStats("주식 체결가 데이터 처리 실패: " + e.getMessage());
    }
  }

  // 시간 파싱 메서드
  private LocalDateTime parseTradeTime(String tradeTime) {
    try {
      // tradeTime은 HHMMSS 형식 (예: "091205")
      if (tradeTime.length() == 6) {
        int hour = Integer.parseInt(tradeTime.substring(0, 2));
        int minute = Integer.parseInt(tradeTime.substring(2, 4));
        int second = Integer.parseInt(tradeTime.substring(4, 6));

        return LocalDate.now().atTime(hour, minute, second);
      }
    } catch (Exception e) {
      log.warn("시간 파싱 실패: {}", tradeTime, e);
    }

    // 파싱 실패 시 현재 시간 반환
    return LocalDateTime.now();
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

        // 통계 업데이트
        updateMessageStats("ORDERBOOK");
      }

    } catch (Exception e) {
      log.error("주식 호가 데이터 처리 실패: {}", data, e);
      updateErrorStats("주식 호가 데이터 처리 실패: " + e.getMessage());
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

  // 메시지 통계 업데이트
  private void updateMessageStats(String messageType) {
    try {
      String statsKey = "websocket:stats";

      // 총 메시지 수 증가
      redisTemplate.opsForHash().increment(statsKey, "totalMessages", 1);

      // 메시지 타입별 증가
      if ("STOCK_PRICE".equals(messageType)) {
        redisTemplate.opsForHash().increment(statsKey, "priceMessages", 1);
      } else if ("ORDERBOOK".equals(messageType)) {
        redisTemplate.opsForHash().increment(statsKey, "orderbookMessages", 1);
      } else if ("ERROR".equals(messageType)) {
        redisTemplate.opsForHash().increment(statsKey, "errorCount", 1);
      }

      // 마지막 메시지 시간 업데이트 (ISO 문자열로 저장)
      redisTemplate.opsForHash().put(statsKey, "lastMessageTime",
          LocalDateTime.now().toString());

    } catch (Exception e) {
      log.error("통계 업데이트 실패", e);
    }
  }

  // 에러 통계 업데이트
  private void updateErrorStats(String errorMessage) {
    try {
      String statsKey = "websocket:stats";
      redisTemplate.opsForHash().increment(statsKey, "errorCount", 1);
      redisTemplate.opsForHash().increment(statsKey, "totalMessages", 1);
      redisTemplate.opsForHash().put(statsKey, "lastMessageTime",
          LocalDateTime.now().toString());
      redisTemplate.opsForHash().put(statsKey, "lastError", errorMessage);

    } catch (Exception e) {
      log.error("에러 통계 업데이트 실패", e);
    }
  }
}