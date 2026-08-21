package com.groovy.backend.notification.service;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.groovy.backend.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 알림 테이블이 무한정 쌓이지 않도록 매일 정리한다.
 * - 안읽은 알림: 생성된 지 {@value UNREAD_RETENTION_DAYS}일 지나면 삭제(로그인을 오래 안 해도 무한 적재되지 않게)
 * - 읽은 알림: 읽은(=알림함 화면에서 사라진) 지 {@value READ_RETENTION_DAYS}일 지나면 삭제
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

	private static final int UNREAD_RETENTION_DAYS = 7;
	private static final int READ_RETENTION_DAYS = 3;

	private final NotificationRepository notificationRepository;

	@Scheduled(cron = "0 0 3 * * *") // 매일 새벽 3시
	@Transactional
	public void cleanupExpiredNotifications() {
		LocalDateTime unreadCutoff = LocalDateTime.now().minusDays(UNREAD_RETENTION_DAYS);
		int deletedUnread = notificationRepository.deleteExpiredUnread(unreadCutoff);
		log.info("만료된 안읽음 알림 삭제: {}건 (생성 후 {}일 경과 기준)", deletedUnread, UNREAD_RETENTION_DAYS);

		LocalDateTime readCutoff = LocalDateTime.now().minusDays(READ_RETENTION_DAYS);
		int deletedRead = notificationRepository.deleteExpiredRead(readCutoff);
		log.info("만료된 읽음 알림 삭제: {}건 (읽은 후 {}일 경과 기준)", deletedRead, READ_RETENTION_DAYS);
	}
}
