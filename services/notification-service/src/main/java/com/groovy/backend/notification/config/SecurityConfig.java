package com.groovy.backend.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.groovy.backend.common.auth.JwtAuthenticationEntryPoint;
import com.groovy.backend.security.JwtAuthenticationFilter;
import com.groovy.backend.security.TokenProvider;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

	// Phase 9: 서비스 간 알림 생성 요청은 더 이상 HTTP(/internal/notifications)가 아니라
	// Kafka(NotificationEventConsumer)로 들어온다 — "/internal/**" permitAll 규칙은 그래서 삭제.
	private static final String[] PERMIT_ALL_PATTERNS = {
		"/actuator/health",
		"/actuator/prometheus",
		// 브라우저 EventSource가 커스텀 헤더(Authorization)를 못 보내므로 여기만 인증 필터를 우회하고,
		// 신원은 NotificationController가 쿼리파라미터 ticket으로 직접 확인한다.
		"/api/notifications/subscribe"
	};

	private final TokenProvider tokenProvider;
	private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(PERMIT_ALL_PATTERNS).permitAll()
				.anyRequest().authenticated())
			.exceptionHandling(handler -> handler.authenticationEntryPoint(jwtAuthenticationEntryPoint))
			.anonymous(AbstractHttpConfigurer::disable)
			.addFilterBefore(new JwtAuthenticationFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}
