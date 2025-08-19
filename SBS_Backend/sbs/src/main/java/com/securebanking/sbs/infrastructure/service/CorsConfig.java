package com.securebanking.sbs.infrastructure.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.securebanking.sbs.interceptor.RateLimitInterceptor;
import com.securebanking.sbs.interceptor.JwtValidationInterceptor;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;
    
    @Autowired
    private JwtValidationInterceptor jwtValidationInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*") // Allow requests from any origin, you can restrict it to specific origins if needed
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*")
//                .allowCredentials(true)
                .maxAge(3600); // Max age of the CORS Preflight request
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Rate limiting interceptor
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/actuator/**", "/health", "/login", "/register", "/test");
        
        // JWT validation interceptor
        registry.addInterceptor(jwtValidationInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/actuator/**", "/health", "/login", "/register", "/test");
    }
}

