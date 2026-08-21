-- Phase 7(DB per Service): notification_db에 처음으로 만드는 테이블. legacy의 V6+V8을
-- 합친 것과 컬럼/인덱스는 동일하지만, recipient_id의 FK 제약(REFERENCES users(id))은
-- 의도적으로 넣지 않는다 — users 테이블은 이제 다른 서비스(향후 identity-service) 소유라
-- 물리적으로 같은 스키마에 있지도 않고, 서비스 경계를 넘는 참조는 DB FK가 아니라
-- "Service-level reference"로 취급한다(Groovy_MSA_전환계획.md Phase 7 원칙).
CREATE TABLE `notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `recipient_id` bigint NOT NULL,
  `type` varchar(30) NOT NULL,
  `title` varchar(255) NOT NULL,
  `message` varchar(500) NOT NULL,
  `target_id` bigint DEFAULT NULL,
  `is_read` boolean NOT NULL DEFAULT false,
  `read_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notifications_recipient_unread` (`recipient_id`, `is_read`),
  KEY `idx_notifications_unread_created` (`is_read`, `created_at`),
  KEY `idx_notifications_read_read_at` (`is_read`, `read_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
