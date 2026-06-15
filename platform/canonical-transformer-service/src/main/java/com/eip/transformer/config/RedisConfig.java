package com.eip.transformer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Redis configuration that keeps auto-configuration active but allows
 * the application to start even if Redis is unavailable.
 * The EventBusPublisher handles Redis unavailability gracefully.
 */
@Configuration
@Import(RedisAutoConfiguration.class)
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);
}
