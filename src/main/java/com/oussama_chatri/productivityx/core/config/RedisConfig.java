package com.oussama_chatri.productivityx.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

import java.net.URI;

@Configuration
@EnableRedisRepositories
public class RedisConfig {

    @Value("${spring.data.redis.url:redis://localhost:6379}")
    private String redisUrl;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        URI uri = URI.create(redisUrl);
        String host = uri.getHost();
        int    port = uri.getPort() == -1 ? 6379 : uri.getPort();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(host, port);

        // TLS for Upstash (rediss://)
        if ("rediss".equalsIgnoreCase(uri.getScheme())) {
            factory.setUseSsl(true);
        }

        // Password extracted from userInfo (Upstash format: :password@host)
        String userInfo = uri.getUserInfo();
        if (userInfo != null && userInfo.contains(":")) {
            factory.setPassword(userInfo.split(":", 2)[1]);
        }

        return factory;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
