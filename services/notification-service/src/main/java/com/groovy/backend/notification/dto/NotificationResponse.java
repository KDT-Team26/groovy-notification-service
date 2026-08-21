package com.groovy.backend.notification.dto;

import com.groovy.backend.notification.Notification;
import com.groovy.backend.notification.NotificationType;

public record NotificationResponse(
	String id,
	NotificationType type,
	String title,
	String message,
	String targetId,
	String createdAt
) {

	public static NotificationResponse from(Notification notification) {
		return new NotificationResponse(
			String.valueOf(notification.getId()),
			notification.getType(),
			notification.getTitle(),
			notification.getMessage(),
			notification.getTargetId() != null ? String.valueOf(notification.getTargetId()) : null,
			notification.getCreatedAt().toString()
		);
	}
}
