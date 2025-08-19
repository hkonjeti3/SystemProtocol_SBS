package com.securebanking.sbs.interceptor;

import com.securebanking.sbs.infrastructure.service.SessionManagementService;
import com.securebanking.sbs.infrastructure.service.UserService;
import com.securebanking.sbs.shared.dto.UserDto;
import com.securebanking.sbs.core.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor to validate JWT tokens and check for blacklisted tokens
 * Automatically handles deactivated users by rejecting their requests
 */
@Component
public class JwtValidationInterceptor implements HandlerInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtValidationInterceptor.class);
    
    @Autowired
    private SessionManagementService sessionManagementService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Skip validation for public endpoints
        String requestURI = request.getRequestURI();
        if (isPublicEndpoint(requestURI)) {
            return true;
        }
        
        // Extract JWT token
        String token = extractToken(request);
        if (token == null) {
            logger.warn("No JWT token found in request to: {}", requestURI);
            return true; // Let the security filter handle authentication
        }
        
        try {
            // Check if token is blacklisted
            if (sessionManagementService.isTokenBlacklisted(token)) {
                logger.warn("Blacklisted JWT token detected for request to: {}", requestURI);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Token invalidated\",\"message\":\"Your account has been deactivated. Please contact support.\"}");
                response.setContentType("application/json");
                return false;
            }
            
            // Extract user ID from token
            Long userId = jwtUtil.extractUserId(token);
            if (userId != null) {
                // Check if user is still active
                try {
                    UserDto user = userService.getUserById(userId.intValue());
                    if (user != null && !"Active".equals(user.getStatus())) {
                        logger.warn("Inactive user {} attempting to access: {}", userId, requestURI);
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.getWriter().write("{\"error\":\"Account deactivated\",\"message\":\"Your account has been deactivated. Please contact support.\"}");
                        response.setContentType("application/json");
                        return false;
                    }
                } catch (Exception e) {
                    logger.warn("Could not verify user status for user {}: {}", userId, e.getMessage());
                    // Continue with the request if we can't verify user status
                }
            }
            
            return true;
        } catch (Exception e) {
            logger.error("Error validating JWT token: {}", e.getMessage());
            return true; // Let the security filter handle the error
        }
    }
    
    /**
     * Extract JWT token from Authorization header
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    
    /**
     * Check if the endpoint is public (no authentication required)
     */
    private boolean isPublicEndpoint(String requestURI) {
        return requestURI.contains("/login") ||
               requestURI.contains("/register") ||
               requestURI.contains("/test") ||
               requestURI.contains("/health") ||
               requestURI.contains("/swagger") ||
               requestURI.contains("/v3/api-docs");
    }
}
