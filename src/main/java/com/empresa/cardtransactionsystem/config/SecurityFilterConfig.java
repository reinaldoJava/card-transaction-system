package com.empresa.cardtransactionsystem.config;

import com.empresa.cardtransactionsystem.adapters.inbound.rest.filter.JwtAuthenticationFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;

@Configuration
@ConditionalOnProperty(name = "security.jwt.filter.enabled", matchIfMissing = true)
public class SecurityFilterConfig {

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilter(SecretKey jwtSecretKey) {
        var filter = new JwtAuthenticationFilter(jwtSecretKey);
        var registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/process", "/status/*");
        registration.setOrder(1);
        return registration;
    }
}
