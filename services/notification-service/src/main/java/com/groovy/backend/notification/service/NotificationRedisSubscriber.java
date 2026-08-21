package com.groovy.backend.notification.service;

import java.nio.charset.StandardCharsets;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 모든 백엔드 인스턴스가 이 리스너로 같은 Redis 채널을 구독한다. 알림이 어느 인스턴스에서
 * 발행됐든, 이 리스너를 통해 각 인스턴스가 자기 로컬 SseEmitter만 확인해서 push한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRedisSubscriber implements MessageListener {

	private final NotificationService notificationService;
	private final ObjectMapper objectMapper;

	@Override
	public void onMessage(Message message, byte[] pattern) {
		try {
			String payload = new String(message.getBody(), StandardCharsets.UTF_8);
			NotificationPushMessage pushMessage = objectMapper.readValue(payload, NotificationPushMessage.class);
			notificationService.pushToLocalEmitters(pushMessage);
		} catch (Exception e) {
			log.error("알림 push 메시지 처리 실패", e);
		}
	}
}
