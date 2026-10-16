package com.janus.client.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.util.HashMap;
import java.util.Map;

/**
 * 将 {@code janus.client.redis.*} 配置映射为 Spring Boot 官方 {@code spring.data.redis.*} 配置。
 *
 * <p>在 Spring 应用上下文刷新前执行，复用 Spring Boot 官方 Redis (Lettuce) 自动装配，
 * 业务系统只需在 application.yml 配置 {@code janus.client.redis.*}，无需显式声明 {@code spring.data.redis.*}。</p>
 *
 * @author ppp
 */
public class JanusRedisEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    public static final String PROPERTY_SOURCE_NAME = "janusClientRedisPropertySource";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String enabled = environment.getProperty("janus.client.enabled");
        if ("false".equalsIgnoreCase(enabled)) {
            return;
        }

        Map<String, Object> redisProperties = new HashMap<>();

        String host = environment.getProperty("janus.client.redis.host");
        if (host == null || host.isBlank()) {
            // 未显式配置 redis.host 时，尝试自动从 janus.client.server-url 解析主机名，降低客户端接入心智负担
            String serverUrl = environment.getProperty("janus.client.server-url");
            if (serverUrl != null && !serverUrl.isBlank()) {
                try {
                    java.net.URI uri = java.net.URI.create(serverUrl.trim());
                    if (uri.getHost() != null && !uri.getHost().isBlank()) {
                        host = uri.getHost();
                    }
                } catch (Exception ignored) {
                }
            }
            if (host == null || host.isBlank()) {
                host = "localhost";
            }
        }
        String port = environment.getProperty("janus.client.redis.port", "26379");
        String password = environment.getProperty("janus.client.redis.password", "janus@123");
        String database = environment.getProperty("janus.client.redis.database", "0");
        String timeout = environment.getProperty("janus.client.redis.timeout", "10s");

        redisProperties.put("spring.data.redis.host", host);
        redisProperties.put("spring.data.redis.port", Integer.parseInt(port.trim()));
        if (password != null && !password.isBlank()) {
            redisProperties.put("spring.data.redis.password", password);
        }
        redisProperties.put("spring.data.redis.database", Integer.parseInt(database.trim()));
        redisProperties.put("spring.data.redis.timeout", timeout);

        MutablePropertySources propertySources = environment.getPropertySources();
        if (propertySources.contains(PROPERTY_SOURCE_NAME)) {
            propertySources.replace(PROPERTY_SOURCE_NAME, new MapPropertySource(PROPERTY_SOURCE_NAME, redisProperties));
        } else {
            // 使用 addFirst 插入到最高优先级，确保 janus.client.redis.* 成为唯一事实源，
            // 避免业务 yml 中遗留或意外引入的 spring.data.redis.* 静默覆盖而破坏模式二连接一致性
            propertySources.addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, redisProperties));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
