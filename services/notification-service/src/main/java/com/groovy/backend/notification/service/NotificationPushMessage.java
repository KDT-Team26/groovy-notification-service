package com.groovy.backend.notification.service;

import com.groovy.backend.notification.dto.NotificationResponse;

/**
 * Redis pub/sub 채널로 보내는 전송용 메시지. 알림 자체(NotificationResponse)는 프론트에
 * 그대로 내려주는 응답 모양이라 recipientUserId를 안 담고 있어서, 어느 로컬 SseEmitter로
 * 보내야 할지 판단할 수 있도록 별도로 감싼다.
 */
public record NotificationPushMessage(
	Long recipientUserId,
	NotificationResponse notification
) {
}
