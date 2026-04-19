package com.oussama_chatri.productivityx.core.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class BrevoConfig {

    @Value("${app.brevo.api-key}")
    private String apiKey;

    @Getter
    @Value("${app.brevo.base-url:https://api.brevo.com/v3}")
    private String baseUrl;

    @Bean(name = "brevoRestTemplate")
    public RestTemplate brevoRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().set("api-key", apiKey);
            request.getHeaders().set("Content-Type", "application/json");
            request.getHeaders().set("Accept", "application/json");
            return execution.execute(request, body);
        });
        return restTemplate;
    }

}
