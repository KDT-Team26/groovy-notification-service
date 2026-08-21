package com.groovy.backend.notification.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groovy.backend.notification.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	List<Notification> findByRecipientIdAndReadFalseOrderByCreatedAtDesc(Long recipientId);

	Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

	List<Notification> findByRecipientIdAndReadFalse(Long recipientId);

	// 정리 배치 전용. 삭제된 건수를 그대로 반환해서 안읽음/읽음 만료를 각각 따로 로그로 남길 수 있게 한다.
	@Modifying
	@Query("DELETE FROM Notification n WHERE n.read = false AND n.createdAt < :cutoff")
	int deleteExpiredUnread(@Param("cutoff") LocalDateTime cutoff);

	@Modifying
	@Query("DELETE FROM Notification n WHERE n.read = true AND n.readAt < :cutoff")
	int deleteExpiredRead(@Param("cutoff") LocalDateTime cutoff);
}
