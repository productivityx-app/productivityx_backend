package com.oussama_chatri.productivityx.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oussama_chatri.productivityx.core.filter.IdempotencyFilter;
import com.oussama_chatri.productivityx.core.filter.RequestLoggingFilter;
import com.oussama_chatri.productivityx.core.filter.SimpleCorsFilter;
import com.oussama_chatri.productivityx.core.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Enables @PreAuthorize, @PostAuthorize, @Secured
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter      jwtAuthFilter;
    private final UserDetailsService userDetailsService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;
    private final RequestLoggingFilter requestLoggingFilter;

    @Value("${app.allowed-origins:http://localhost:8080}")
    private String allowedOrigins;

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/refresh",
            "/actuator/health",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/api/v1/auth/verify-otp",
            "/api/v1/auth/verify-forgot-otp",
            "/ws/**"
    };

    /**
     * IdempotencyFilter bean — instantiated here so Spring injects the
     * correct Redis template and ObjectMapper instances. Registered as a servlet
     * filter via addFilterAfter so it runs after JWT auth is complete (userId needed
     * for the Redis key).
     */
    @Bean
    public IdempotencyFilter idempotencyFilter() {
        return new IdempotencyFilter(redisTemplate, objectMapper);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(requestLoggingFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write(
                                    "{\"success\":false,\"errorCode\":\"AUTH_000\",\"message\":\"Authentication required.\"}"
                            );
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Admin endpoints require ADMIN authority
                        .requestMatchers("/api/v1/admin/**").hasAuthority("ADMIN")
                        .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider())
                // JwtAuthFilter runs first — sets SecurityContext with userId
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // IdempotencyFilter runs after JWT — userId is available in SecurityContext
                .addFilterAfter(idempotencyFilter(), JwtAuthFilter.class)
                .build();
    }

    @Bean
    public FilterRegistrationBean<SimpleCorsFilter> corsFilterRegistration() {
        FilterRegistrationBean<SimpleCorsFilter> bean = new FilterRegistrationBean<>(
                new SimpleCorsFilter(allowedOrigins)
        );
        bean.addUrlPatterns("/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        bean.setName("simpleCorsFilter");
        return bean;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
