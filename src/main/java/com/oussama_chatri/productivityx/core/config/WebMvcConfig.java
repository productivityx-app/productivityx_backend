package com.oussama_chatri.productivityx.core.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final AsyncTaskExecutor sseTaskExecutor;

    public WebMvcConfig(@Qualifier("sseTaskExecutor") AsyncTaskExecutor sseTaskExecutor) {
        this.sseTaskExecutor = sseTaskExecutor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(SSE_TIMEOUT_MS);
        configurer.setTaskExecutor(sseTaskExecutor);
    }
}