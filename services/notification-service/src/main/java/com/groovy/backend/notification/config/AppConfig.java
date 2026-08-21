package com.groovy.backend.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.groovy.backend.notification.event.EventDeserializationException;

// @EnableScheduling: 알림 정리 배치(NotificationCleanupScheduler)의 @Scheduled가 동작하려면 필요.
// @EnableKafka: Phase 9 — NotificationEventConsumer의 @KafkaListener가 동작하려면 필요.
@Configuration
@EnableScheduling
@EnableKafka
public class AppConfig {

	// JacksonAutoConfiguration이 자동으로 만들어주는 건 Boot 4의 네이티브 tools.jackson
	// ObjectMapper라, Redis pub/sub 직렬화 + NotificationEventConsumer의 EventEnvelope 역직렬화
	// (구 Jackson 2, com.fasterxml.jackson)에 쓸 빈은 여기서 직접 등록한다(legacy의 AsyncConfig와
	// 동일한 이유). EventEnvelope의 Instant occurredAt을 다루므로 JavaTimeModule을 함께
	// 등록해야 한다(직접 만든 ObjectMapper는 클래스패스에 jsr310 모듈이 있어도 자동으로
	// 등록되지 않는다).
	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper().registerModule(new JavaTimeModule());
	}

	// Kafka consumer 에러 처리(DLQ). Spring Boot의 Kafka 오토컨피그는 이 타입의 빈이 하나
	// 있으면 자동으로 리스너 컨테이너 팩토리에 연결해준다(별도 팩토리 빈을 새로 만들 필요 없음).
	//
	// EventDeserializationException(역직렬화 실패)/IllegalArgumentException(NotificationType
	// 값 불일치)처럼 재시도해도 결과가 똑같은 예외는 즉시 <topic>.DLT로 보내고, 그 외 예외
	// (DB 일시 장애 등)는 2초 간격으로 3번 재시도한 뒤에도 실패하면 DLT로 보낸다. 이게 없으면
	// poison-pill 메시지가 컨슈머 기본 정책상 무한 재시도되며 해당 파티션 처리가 멈춘다.
	@Bean
	public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
		DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3));
		errorHandler.addNotRetryableExceptions(EventDeserializationException.class, IllegalArgumentException.class);
		return errorHandler;
	}
}
