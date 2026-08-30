package com.groovy.backend.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.groovy.backend.observability.TracingConfig;

/**
 * 공통 코드 분리(groovy-common) 후: observability 모듈의 TracingConfig(@Configuration)가
 * 이 서비스 base 패키지 밖이라 @Import 로 가져온다. (outbox 는 소비 쪽이라 미사용)
 */
@SpringBootApplication
@EnableJpaAuditing
@Import(TracingConfig.class)
public class NotificationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificationServiceApplication.class, args);
	}
}
