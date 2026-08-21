package com.groovy.backend.notification.inbox;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * MSA 전환 Phase 9: Inbox. Kafka는 at-least-once만 보장하므로(같은 메시지가 두 번 배달될 수
 * 있음), eventId를 기본키로 삼아 "이미 처리한 이벤트인지"를 확인하는 용도로만 쓴다. 실제 알림
 * 생성(NotificationService.createAndPublish)과 이 테이블에 기록하는 것이 같은 트랜잭션
 * 안에서 일어나야 "알림은 만들었는데 처리 기록은 안 남았다"는 불일치가 생기지 않는다.
 */
@Entity
@Table(name = "processed_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent {

	@Id
	@Column(name = "event_id", length = 36)
	private String eventId;

	@Column(name = "processed_at", nullable = false)
	private LocalDateTime processedAt;

	public ProcessedEvent(String eventId) {
		this.eventId = eventId;
		this.processedAt = LocalDateTime.now();
	}
}
