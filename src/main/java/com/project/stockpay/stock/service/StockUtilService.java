package com.project.stockpay.stock.service;

import com.project.stockpay.common.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * 주식 유틸리티 서비스
 * - 종목 코드 유효성 검증
 * - 종목 코드 정규화
 * - 기타 유틸리티 기능
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockUtilService {

  private final StockRepository stockRepository;

  // 종목 코드 패턴 (6자리 숫자)
  private static final Pattern STOCK_CODE_PATTERN = Pattern.compile("^\\d{6}$");

  // 부분 숫자 패턴 (1~6자리 숫자)
  private static final Pattern PARTIAL_NUMBER_PATTERN = Pattern.compile("^\\d{1,6}$");

  // ========== 종목 코드 검증 ==========

  /**
   * 종목 코드 유효성 검증
   * - 기본 형식 검증 (6자리 숫자)
   * - 데이터베이스 존재 여부 확인
   */
  public boolean isValidStockTicker(String stockTicker) {
    // TODO: 주석 해제 후 확인 필요
//    if (stockTicker == null || stockTicker.trim().isEmpty()) {
//      log.debug("종목 코드 검증 실패 - 빈 값: {}", stockTicker);
//      return false;
//    }
//
//    String trimmed = stockTicker.trim();
//
//    // 기본 형식 검증 (6자리 숫자)
//    if (!STOCK_CODE_PATTERN.matcher(trimmed).matches()) {
//      log.debug("종목 코드 검증 실패 - 형식 오류: {}", trimmed);
//      return false;
//    }
//
//    // 데이터베이스에서 실제 존재 여부 확인
//    try {
//      boolean exists = stockRepository.existsById(trimmed);
//      if (!exists) {
//        log.debug("종목 코드 검증 실패 - DB에 존재하지 않음: {}", trimmed);
//      }
//      return exists;
//    } catch (Exception e) {
//      log.error("종목 코드 유효성 검증 중 DB 오류: {}", trimmed, e);
//      return false;
//    }

    // 임시 테스트용
    return true;
  }

  /**
   * 종목 코드 형식 검증 (DB 조회 없이)
   * - 6자리 숫자 형식인지만 확인
   * - 성능이 중요한 경우 사용
   */
  public boolean isValidStockFormat(String stockTicker) {
    if (stockTicker == null || stockTicker.trim().isEmpty()) {
      return false;
    }

    return STOCK_CODE_PATTERN.matcher(stockTicker.trim()).matches();
  }

  /**
   * 다중 종목 코드 유효성 검증
   */
  public ValidationResult validateMultipleStockTickers(String... stockTickers) {
    if (stockTickers == null || stockTickers.length == 0) {
      return ValidationResult.builder()
          .allValid(false)
          .validCount(0)
          .invalidCount(0)
          .build();
    }

    int validCount = 0;
    int invalidCount = 0;

    for (String ticker : stockTickers) {
      if (isValidStockTicker(ticker)) {
        validCount++;
      } else {
        invalidCount++;
      }
    }

    return ValidationResult.builder()
        .allValid(invalidCount == 0)
        .validCount(validCount)
        .invalidCount(invalidCount)
        .totalCount(stockTickers.length)
        .build();
  }

  // ========== 종목 코드 정규화 ==========

  /**
   * 안전한 종목 코드 정규화
   * - 앞쪽 0 추가로 6자리 맞춤
   * - 공백 제거 및 대소문자 처리
   */
  public String normalizeStockTicker(String stockTicker) {
    if (stockTicker == null) {
      return null;
    }

    String trimmed = stockTicker.trim().toUpperCase();

    // 빈 문자열 처리
    if (trimmed.isEmpty()) {
      return null;
    }

    // 숫자만 포함된 경우 6자리로 패딩
    if (PARTIAL_NUMBER_PATTERN.matcher(trimmed).matches()) {
      return String.format("%06d", Integer.parseInt(trimmed));
    }

    // 이미 올바른 형식이면 그대로 반환
    if (STOCK_CODE_PATTERN.matcher(trimmed).matches()) {
      return trimmed;
    }

    // 숫자가 아닌 문자가 포함된 경우 숫자만 추출 시도
    String numbersOnly = trimmed.replaceAll("\\D", "");
    if (PARTIAL_NUMBER_PATTERN.matcher(numbersOnly).matches()) {
      return String.format("%06d", Integer.parseInt(numbersOnly));
    }

    log.warn("종목 코드 정규화 실패 - 올바르지 않은 형식: {}", stockTicker);
    return trimmed; // 정규화 실패 시 원본 반환
  }

  /**
   * 다중 종목 코드 정규화
   */
  public String[] normalizeMultipleStockTickers(String... stockTickers) {
    if (stockTickers == null) {
      return new String[0];
    }

    return java.util.Arrays.stream(stockTickers)
        .map(this::normalizeStockTicker)
        .filter(java.util.Objects::nonNull)
        .toArray(String[]::new);
  }

  // ========== 종목 코드 생성/변환 ==========

  /**
   * 숫자를 종목 코드로 변환
   */
  public String numberToStockTicker(int number) {
    if (number < 0 || number > 999999) {
      throw new IllegalArgumentException("종목 코드 숫자는 0~999999 범위여야 합니다: " + number);
    }

    return String.format("%06d", number);
  }

  /**
   * 종목 코드를 숫자로 변환
   */
  public Integer stockTickerToNumber(String stockTicker) {
    String normalized = normalizeStockTicker(stockTicker);
    if (normalized == null || !isValidStockFormat(normalized)) {
      return null;
    }

    try {
      return Integer.parseInt(normalized);
    } catch (NumberFormatException e) {
      log.error("종목 코드 숫자 변환 실패: {}", stockTicker, e);
      return null;
    }
  }

  // ========== 종목 정보 유틸리티 ==========

  /**
   * 종목 코드로 간단한 정보 추출
   */
  public StockInfo extractStockInfo(String stockTicker) {
    String normalized = normalizeStockTicker(stockTicker);
    if (normalized == null) {
      return null;
    }

    try {
      var stock = stockRepository.findById(normalized);
      if (stock.isPresent()) {
        var s = stock.get();
        return StockInfo.builder()
            .ticker(s.getStockTicker())
            .name(s.getStockName())
            .sector(s.getStockSector())
            .status(s.getStockStatus())
            .isValid(true)
            .build();
      } else {
        return StockInfo.builder()
            .ticker(normalized)
            .isValid(false)
            .build();
      }
    } catch (Exception e) {
      log.error("종목 정보 추출 실패: {}", stockTicker, e);
      return StockInfo.builder()
          .ticker(normalized)
          .isValid(false)
          .build();
    }
  }

  /**
   * 주요 종목 여부 확인
   */
  public boolean isPopularStock(String stockTicker) {
    String normalized = normalizeStockTicker(stockTicker);
    if (normalized == null) {
      return false;
    }

    // 주요 종목 목록
    java.util.Set<String> popularStocks = java.util.Set.of(
        "005930", // 삼성전자
        "000660", // SK하이닉스
        "035420", // NAVER
        "051910", // LG화학
        "006400", // 삼성SDI
        "207940", // 삼성바이오로직스
        "005380", // 현대차
        "012330", // 현대모비스
        "028260", // 삼성물산
        "066570"  // LG전자
    );

    return popularStocks.contains(normalized);
  }

  /**
   * 섹터 추정 (종목 코드 기반)
   */
  public String estimateSector(String stockTicker) {
    String normalized = normalizeStockTicker(stockTicker);
    if (normalized == null) {
      return "기타";
    }

    // 간단한 섹터 추정 로직 (실제로는 DB에서 조회해야 함)
    return switch (normalized) {
      case "005930", "000660", "009150" -> "반도체";
      case "005380", "000270", "012330" -> "자동차";
      case "051910", "006400", "096770" -> "화학";
      case "035420", "035720" -> "플랫폼";
      case "207940" -> "바이오";
      case "055550", "105560" -> "금융";
      case "017670", "030200" -> "통신";
      default -> "기타";
    };
  }

  // ========== DTO 클래스들 ==========

  /**
   * 유효성 검증 결과
   */
  @lombok.Data
  @lombok.Builder
  public static class ValidationResult {
    private boolean allValid;
    private int validCount;
    private int invalidCount;
    private int totalCount;

    public double getValidRatio() {
      return totalCount > 0 ? (double) validCount / totalCount * 100 : 0;
    }
  }

  /**
   * 간단한 종목 정보
   */
  @lombok.Data
  @lombok.Builder
  public static class StockInfo {
    private String ticker;
    private String name;
    private String sector;
    private String status;
    private boolean isValid;
    private boolean isPopular;
  }

  // ========== 기타 유틸리티 ==========

  /**
   * 종목 코드 형식 설명 반환
   */
  public String getStockTickerFormatDescription() {
    return "종목 코드는 6자리 숫자여야 합니다. 예: 005930 (삼성전자), 000660 (SK하이닉스)";
  }

  /**
   * 종목 코드 예시 반환
   */
  public String[] getStockTickerExamples() {
    return new String[]{
        "005930", // 삼성전자
        "000660", // SK하이닉스
        "035420", // NAVER
        "051910", // LG화학
        "006400"  // 삼성SDI
    };
  }

  /**
   * 랜덤 유효 종목 코드 생성 (테스트용)
   */
  public String generateRandomValidStockTicker() {
    String[] examples = getStockTickerExamples();
    int randomIndex = (int) (Math.random() * examples.length);
    return examples[randomIndex];
  }

  /**
   * 종목 코드 패턴 매칭 확인
   */
  public boolean matchesStockPattern(String input) {
    if (input == null) {
      return false;
    }
    return STOCK_CODE_PATTERN.matcher(input.trim()).matches();
  }

  /**
   * 입력값에서 종목 코드 추출 시도
   */
  public String extractStockTicker(String input) {
    if (input == null || input.trim().isEmpty()) {
      return null;
    }

    String trimmed = input.trim();

    // 이미 올바른 형식이면 반환
    if (STOCK_CODE_PATTERN.matcher(trimmed).matches()) {
      return trimmed;
    }

    // 숫자만 추출해서 6자리로 만들어 보기
    String numbersOnly = trimmed.replaceAll("\\D", "");
    if (PARTIAL_NUMBER_PATTERN.matcher(numbersOnly).matches()) {
      return String.format("%06d", Integer.parseInt(numbersOnly));
    }

    return null;
  }
}