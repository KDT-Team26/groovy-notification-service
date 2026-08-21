package com.groovy.backend.notification.service;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groovy.backend.notification.Notification;
import com.groovy.backend.notification.NotificationType;
import com.groovy.backend.notification.dto.NotificationResponse;
import com.groovy.backend.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MSA 전환 Phase 6: legacy 시절과 달리 User 테이블에 접근하지 않는다. recipientId(Long)만
 * 있으면 알림을 만들고 조회/필터링할 수 있어서, User 존재 여부를 확인하던 조회 자체가
 * 필요 없어졌다(recipientId는 이미 검증된 다른 서비스 내부 데이터에서 오기 때문).
 * "이 요청을 보낸 사람이 누구인지"는 이제 email 조회가 아니라 JWT의 uid 클레임으로 바로 안다
 * (NotificationController가 @AuthenticationPrincipal Long userId로 직접 받는다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

	public static final String REDIS_CHANNEL = "notifications";
	private static final String TICKET_KEY_PREFIX = "notification:subscribe-ticket:";
	private static final Duration TICKET_TTL = Duration.ofSeconds(30);
	private static final Long SSE_NO_TIMEOUT = 0L;

	private final NotificationRepository notificationRepository;
	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	// 유저 한 명이 탭을 여러 개 열 수 있어 리스트로 관리한다. 이 맵은 이 서버 프로세스가
	// 직접 들고 있는 로컬 연결만 담으므로, 인스턴스가 여러 대여도 각자 자기 것만 관리하면 된다.
	private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emittersByUserId = new ConcurrentHashMap<>();

	// --- 알림 생성(InternalNotificationController 전용) ---

	@Transactional
	public void createAndPublish(Long recipientUserId, NotificationType type, String title, String message, Long targetId) {
		Notification notification = notificationRepository.save(Notification.builder()
			.recipientId(recipientUserId)
			.type(type)
			.title(title)
			.message(message)
			.targetId(targetId)
			.build());

		publishToRedis(recipientUserId, NotificationResponse.from(notification));
	}

	private void publishToRedis(Long recipientUserId, NotificationResponse response) {
		try {
			String payload = objectMapper.writeValueAsString(new NotificationPushMessage(recipientUserId, response));
			redisTemplate.convertAndSend(REDIS_CHANNEL, payload);
		} catch (JsonProcessingException e) {
			log.error("알림 push 메시지 직렬화 실패: recipientUserId={}", recipientUserId, e);
		}
	}

	// --- 조회/읽음 처리 ---

	public List<NotificationResponse> listUnread(Long userId) {
		return notificationRepository.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(userId).stream()
			.map(NotificationResponse::from)
			.toList();
	}

	@Transactional
	public void markRead(Long userId, Long notificationId) {
		notificationRepository.findByIdAndRecipientId(notificationId, userId)
			.ifPresent(Notification::markRead);
	}

	@Transactional
	public void markAllRead(Long userId) {
		notificationRepository.findByRecipientIdAndReadFalse(userId)
			.forEach(Notification::markRead);
	}

	// --- SSE 구독 ---

	// EventSource는 커스텀 헤더를 못 보내므로, 짧게 사는 1회용 티켓으로 신원을 확인한다.
	public String issueTicket(Long userId) {
		String ticket = UUID.randomUUID().toString();
		redisTemplate.opsForValue().set(TICKET_KEY_PREFIX + ticket, String.valueOf(userId), TICKET_TTL);
		return ticket;
	}

	// 1회용이므로 조회 즉시 지운다. 실패(만료/이미 사용됨)하면 null.
	public Long consumeTicket(String ticket) {
		String key = TICKET_KEY_PREFIX + ticket;
		String userId = redisTemplate.opsForValue().get(key);
		if (userId == null) {
			return null;
		}
		redisTemplate.delete(key);
		return Long.valueOf(userId);
	}

	public SseEmitter subscribe(Long userId) {
		SseEmitter emitter = new SseEmitter(SSE_NO_TIMEOUT);
		CopyOnWriteArrayList<SseEmitter> emitters = emittersByUserId.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>());
		emitters.add(emitter);

		emitter.onCompletion(() -> removeEmitter(userId, emitter));
		emitter.onTimeout(() -> removeEmitter(userId, emitter));
		emitter.onError(e -> removeEmitter(userId, emitter));

		try {
			emitter.send(SseEmitter.event().name("connected").data("ok"));
		} catch (IOException e) {
			removeEmitter(userId, emitter);
		}

		return emitter;
	}

	private void removeEmitter(Long userId, SseEmitter emitter) {
		CopyOnWriteArrayList<SseEmitter> emitters = emittersByUserId.get(userId);
		if (emitters == null) {
			return;
		}
		emitters.remove(emitter);
		if (emitters.isEmpty()) {
			emittersByUserId.remove(userId);
		}
	}

	// Redis 구독자(NotificationRedisSubscriber)가 채널 메시지를 받으면 호출한다.
	// 이 인스턴스가 그 유저의 연결을 안 들고 있으면(=수신자가 다른 인스턴스에 붙어 있으면) 아무 일도 안 한다.
	public void pushToLocalEmitters(NotificationPushMessage message) {
		CopyOnWriteArrayList<SseEmitter> emitters = emittersByUserId.get(message.recipientUserId());
		if (emitters == null || emitters.isEmpty()) {
			return;
		}

		for (SseEmitter emitter : emitters) {
			try {
				emitter.send(SseEmitter.event().name("notification").data(message.notification()));
			} catch (IOException e) {
				removeEmitter(message.recipientUserId(), emitter);
			}
		}
	}
}
