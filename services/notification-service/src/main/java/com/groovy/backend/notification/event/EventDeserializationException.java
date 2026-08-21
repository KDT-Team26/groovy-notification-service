package com.groovy.backend.notification.event;

/**
 * Kafka 메시지를 EventEnvelope/NotificationPayload로 역직렬화하지 못했을 때 던진다. 메시지
 * 내용 자체가 잘못됐으므로 재시도해도 결과가 똑같다 — AppConfig의 kafkaErrorHandler가 이
 * 타입을 재시도 없이 바로 DLT(Dead Letter Topic)로 보내도록 등록해둔다.
 */
public class EventDeserializationException extends RuntimeException {

	public EventDeserializationException(String message, Throwable cause) {
		super(message, cause);
	}
}
