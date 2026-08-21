-- MSA 전환 Phase 9: Inbox(멱등성) 테이블. Kafka에서 같은 이벤트가 두 번 배달돼도
-- event_id 기본키 덕분에 두 번째부터는 조용히 무시된다(NotificationEventConsumer 참고).
CREATE TABLE `processed_events` (
  `event_id` varchar(36) NOT NULL,
  `processed_at` datetime(6) NOT NULL,
  PRIMARY KEY (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
