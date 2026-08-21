package com.groovy.backend.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.groovy.backend.notification.service.NotificationRedisSubscriber;
import com.groovy.backend.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;

/**
 * RedisConnectionFactory/StringRedisTemplate은 spring-boot-starter-data-redis가 붙어있으면
 * Spring Boot가 spring.data.redis.host/port 설정으로 자동 구성해준다. 여기서는 pub/sub
 * 구독을 위한 RedisMessageListenerContainer만 직접 등록한다.
 */
@Configuration
@RequiredArgsConstructor
public class RedisConfig {

	private final NotificationRedisSubscriber notificationRedisSubscriber;

	@Bean
	public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		container.addMessageListener(notificationRedisSubscriber, new ChannelTopic(NotificationService.REDIS_CHANNEL));
		return container;
	}
}
