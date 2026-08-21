package com.groovy.backend.notification.event;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groovy.backend.eventcontract.EventEnvelope;
import com.groovy.backend.eventcontract.notification.NotificationPayload;
import com.groovy.backend.notification.NotificationType;
import com.groovy.backend.notification.inbox.ProcessedEvent;
import com.groovy.backend.notification.inbox.ProcessedEventRepository;
import com.groovy.backend.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MSA 전환 Phase 9: 예전 InternalNotificationController(HTTP)를 대체한다. study/calendar/
 * content-service의 OutboxRelay가 Kafka로 발행한 이벤트를 소비한다.
 *
 * Inbox(멱등성): Kafka는 at-least-once만 보장하므로 같은 메시지가 두 번 배달될 수 있다.
 * eventId가 이미 처리된 적 있으면 조용히 무시한다 — "알림 생성 + Inbox 기록"을 같은
 * @Transactional 안에서 원자적으로 처리해서, 두 작업 사이에 크래시가 나도 상태가 어긋나지 않는다.
 *
 * 에러 처리: 여기서 예외를 삼키지 않고 그대로 던진다 — 컨테이너 레벨의 kafkaErrorHandler
 * (AppConfig 참고)가 재시도/DLT 전송을 담당하기 때문이다. 역직렬화 실패(EventDeserializationException)
 * 처럼 재시도해도 똑같이 실패할 예외는 즉시 DLT로, 그 외 예외(DB 일시 장애 등)는 재시도 후
 * DLT로 보낸다. 여기서 그냥 catch해서 return하면 메시지가 재시도도 DLT 기록도 없이 영구
 * 유실된다(과거 버그).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

	private static final TypeReference<EventEnvelope<JsonNode>> ENVELOPE_TYPE = new TypeReference<>() {
	};

	private final ProcessedEventRepository processedEventRepository;
	private final NotificationService notificationService;
	private final ObjectMapper objectMapper;

	@KafkaListener(topics = "${notification.kafka.topic:notification-events}", groupId = "notification-service")
	@Transactional
	public void onMessage(String message) {
		EventEnvelope<JsonNode> envelope = parseEnvelope(message);
		String eventId = envelope.eventId().toString();

		if (processedEventRepository.existsById(eventId)) {
			log.info("이미 처리한 이벤트, 중복 무시: eventId={}, eventType={}", eventId, envelope.eventType());
			return;
		}

		NotificationPayload payload = parsePayload(envelope);
		notificationService.createAndPublish(
			payload.recipientUserId(),
			NotificationType.valueOf(payload.type()),
			payload.title(),
			payload.message(),
			payload.targetId()
		);
		processedEventRepository.save(new ProcessedEvent(eventId));
		log.info("알림 이벤트 처리 완료: eventId={}, eventType={}", eventId, envelope.eventType());
	}

	private EventEnvelope<JsonNode> parseEnvelope(String message) {
		try {
			return objectMapper.readValue(message, ENVELOPE_TYPE);
		} catch (JsonProcessingException e) {
			throw new EventDeserializationException("이벤트 메시지 파싱 실패: " + message, e);
		}
	}

	private NotificationPayload parsePayload(EventEnvelope<JsonNode> envelope) {
		try {
			return objectMapper.treeToValue(envelope.payload(), NotificationPayload.class);
		} catch (JsonProcessingException e) {
			throw new EventDeserializationException("이벤트 payload 파싱 실패: eventId=" + envelope.eventId(), e);
		}
	}
}
