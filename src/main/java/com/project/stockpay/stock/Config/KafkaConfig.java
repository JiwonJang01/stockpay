package com.project.stockpay.stock.Config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 설정
 * - Docker Compose 환경에 맞춘 설정
 * - Producer/Consumer 설정
 * - 주문 처리를 위한 토픽 구성
 * - 재시도 메커니즘 지원
 */
@Configuration
public class KafkaConfig {

  @Value("${spring.kafka.bootstrap-servers}")
  private String bootstrapServers;

  @Value("${spring.kafka.consumer.group-id}")
  private String groupId;

  // Producer 설정

  @Bean
  public ProducerFactory<String, String> producerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

    // 안정성을 위한 설정
    props.put(ProducerConfig.ACKS_CONFIG, "all"); // 모든 replica 확인
    props.put(ProducerConfig.RETRIES_CONFIG, 3); // 재시도 횟수
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // 중복 방지
    props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1); // 순서 보장

    // 성능 최적화
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384); // 배치 크기
    props.put(ProducerConfig.LINGER_MS_CONFIG, 10); // 배치 대기 시간
    props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432); // 버퍼 메모리

    // 압축 설정 (네트워크 효율성)
    props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

    return new DefaultKafkaProducerFactory<>(props);
  }

  @Bean
  public KafkaTemplate<String, String> kafkaTemplate() {
    return new KafkaTemplate<>(producerFactory());
  }

  // Consumer 설정

  @Bean
  public ConsumerFactory<String, String> consumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

    // 안정성을 위한 설정
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // 처음부터 읽기
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // 수동 커밋
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10); // 한 번에 처리할 레코드 수
    props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000); // 세션 타임아웃
    props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000); // 하트비트 간격

    // 중복 처리 방지
    props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory());

    // 수동 ACK 모드 설정
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

    // 동시성 설정 (주문 처리 성능을 위해)
    factory.setConcurrency(3);

    // 에러 핸들링
    factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler());

    // 배치 리스너 비활성화 (개별 메시지 처리)
    factory.setBatchListener(false);

    return factory;
  }

  // ========== 재시도 전용 Consumer 설정 ==========

  @Bean
  public ConsumerFactory<String, String> retryConsumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "stockpay-retry-group"); // 재시도 전용 그룹
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

    // 재시도 전용 설정
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 5); // 재시도는 적은 수로
    props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 60000); // 더 긴 타임아웃
    props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 20000);

    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> retryKafkaListenerContainerFactory() {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(retryConsumerFactory());

    // 수동 ACK 모드
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

    // 재시도용은 동시성을 낮게 설정
    factory.setConcurrency(1);

    // 재시도 전용 에러 핸들러
    factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler());

    return factory;
  }
}