package com.unoparty.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    public static final String[] ORIGIN_PATTERNS = {
            "http://localhost:*",
            "http://127.0.0.1:*",
            "https://*.netlify.app",
            "https://*.netlify.com"
    };

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOriginPatterns(ORIGIN_PATTERNS)
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
                // Health is useful for keep-alive pings from the SPA
                registry.addMapping("/api/health")
                        .allowedOriginPatterns(ORIGIN_PATTERNS)
                        .allowedMethods("GET", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}
