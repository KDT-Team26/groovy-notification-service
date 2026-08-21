package com.groovy.backend.notification.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.groovy.backend.eventcontract.EventEnvelope;
import com.groovy.backend.eventcontract.notification.NotificationPayload;

/**
 * Phase 13: study/calendar/content-service(Producer)와 이 서비스(Consumer)가 Kafka로 주고받는
 * EventEnvelope+NotificationPayload(둘 다 event-contract 모듈의 공유 타입)에 대한 Message
 * Contract Test.
 *
 * NotificationEventConsumer.onMessage(String)가 실제로 하는 두 단계 역직렬화
 * (EventEnvelope로 한 번, payload를 다시 NotificationPayload로 한 번)를 그대로 재현한다 —
 * 발행하는 서비스가 이벤트 스키마를 이 서비스와 조율 없이 바꾸면(필드명 변경/삭제 등) 이
 * 테스트가 CI에서 바로 깨진다.
 */
class NotificationEventConsumerContractTest {

	private static final TypeReference<EventEnvelope<JsonNode>> ENVELOPE_TYPE = new TypeReference<>() {
	};

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	@Test
	void 계약_픽스처를_NotificationEventConsumer와_동일한_2단계_역직렬화로_처리할_수_있다() throws Exception {
		String rawMessage = readFixtureAsString();

		EventEnvelope<JsonNode> envelope = objectMapper.readValue(rawMessage, ENVELOPE_TYPE);
		assertThat(envelope.eventId()).isEqualTo(UUID.fromString("b2bd3f9c-0ab6-474c-ab8e-2efdfbfa2453"));
		assertThat(envelope.eventType()).isEqualTo("APPLICATION_RECEIVED");

		NotificationPayload payload = objectMapper.treeToValue(envelope.payload(), NotificationPayload.class);

		assertThat(payload.recipientUserId()).isEqualTo(1L);
		assertThat(payload.type()).isEqualTo("APPLICATION_RECEIVED");
		assertThat(payload.title()).isEqualTo("새로운 참여 신청이 도착했습니다");
		assertThat(payload.message()).isNotBlank();
		assertThat(payload.targetId()).isEqualTo(1L);
	}

	private String readFixtureAsString() throws Exception {
		try (InputStream in = getClass().getResourceAsStream("/contracts/application-received-event.json")) {
			return new String(in.readAllBytes());
		}
	}
}
