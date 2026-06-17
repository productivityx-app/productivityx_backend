package com.oussama_chatri.productivityx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootApplication
@EnableAsync
@EnableRetry
@EnableScheduling
public class ProductivityXApplication {

    static {
        // Propagate SecurityContext to @Async threads. Without this,
        // SecurityContextHolder.getContext() returns null in async workers
        // (AI SSE streaming, email sending, etc.).
        SecurityContextHolder.setStrategyName(
                SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
    }

    public static void main(String[] args) {
        SpringApplication.run(ProductivityXApplication.class, args);
    }
}
