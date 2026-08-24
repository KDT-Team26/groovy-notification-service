-- MSA 전환 Phase 7(DB per Service): 하나의 MySQL 컨테이너 안에서 스키마 소유권부터 분리한다.
-- legacy-monolith(groovy_db)는 삭제됐다. 여기서는 notification-service 전용 스키마 + 그 스키마에만
-- 접근 가능한 전용 계정을 추가로 만든다.
--
-- 소유권 이전(groovy-infra#7): 이 스크립트는 원래 groovy-infra/mysql-init/01-notification-service.sql로
-- platform(mysql)이 소유하고 있었으나, platform이 서비스 Secret을 역참조하는 문제를 없애기 위해
-- 이 레포로 이전했다. 실행 메커니즘(누가/언제 이 스크립트를 돌릴지)은 아직 연결되지 않았고
-- 후속 이슈에서 다룬다 — 지금은 파일 소유권 이전까지만이 이번 작업 범위다.
--
-- 비밀번호를 파일에 고정값으로 넣은 건 이 스택이 로컬 검증 전용이기 때문이다. 실제 배포에서는
-- 시크릿 매니저 등으로 주입해야 한다.

CREATE DATABASE IF NOT EXISTS notification_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'notification_service'@'%'
  IDENTIFIED BY 'notification_service_local_only_pw';

-- notification_service 계정은 notification_db에만 권한이 있다. groovy_db(legacy가 쓰는
-- study/user/memoir/calendar 테이블들)에는 아무 권한도 주지 않는다 — 완료 기준
-- "코드에서 cross-schema JOIN이 물리적으로 실행 불가능함"을 계정 레벨에서 강제한다.
GRANT ALL PRIVILEGES ON notification_db.* TO 'notification_service'@'%';

FLUSH PRIVILEGES;
