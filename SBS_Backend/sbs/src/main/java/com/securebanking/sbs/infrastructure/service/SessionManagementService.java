package com.securebanking.sbs.infrastructure.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Service for managing user sessions and JWT token blacklisting
 * Uses Redis to store blacklisted tokens for deactivated users
 */
@Service
public class SessionManagementService {
    
    private static final Logger logger = LoggerFactory.getLogger(SessionManagementService.class);
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    private static final String BLACKLIST_PREFIX = "jwt_blacklist:";
    private static final String USER_SESSIONS_PREFIX = "user_sessions:";
    
    /**
     * Blacklist a JWT token for a deactivated user
     * @param token JWT token to blacklist
     * @param userId User ID whose token is being blacklisted
     * @param reason Reason for blacklisting (e.g., "Account Deactivated")
     */
    public void blacklistToken(String token, Integer userId, String reason) {
        try {
            String blacklistKey = BLACKLIST_PREFIX + token;
            String blacklistValue = String.format("userId:%d,reason:%s,timestamp:%d", 
                userId, reason, System.currentTimeMillis());
            
            // Store in Redis with expiration (24 hours from now)
            redisTemplate.opsForValue().set(blacklistKey, blacklistValue, 24, TimeUnit.HOURS);
            
            logger.info("JWT token blacklisted for user {}: {}", userId, reason);
        } catch (Exception e) {
            logger.error("Failed to blacklist JWT token for user {}: {}", userId, e.getMessage());
            // Don't throw exception as this shouldn't block the main operation
        }
    }
    
    /**
     * Check if a JWT token is blacklisted
     * @param token JWT token to check
     * @return true if token is blacklisted, false otherwise
     */
    public boolean isTokenBlacklisted(String token) {
        try {
            String blacklistKey = BLACKLIST_PREFIX + token;
            String blacklistValue = redisTemplate.opsForValue().get(blacklistKey);
            return blacklistValue != null;
        } catch (Exception e) {
            logger.error("Failed to check token blacklist status: {}", e.getMessage());
            return false; // Default to not blacklisted if check fails
        }
    }
    
    /**
     * Store user session information
     * @param userId User ID
     * @param sessionInfo Session information (IP, user agent, etc.)
     * @param expirationMinutes Minutes until session expires
     */
    public void storeUserSession(Integer userId, String sessionInfo, int expirationMinutes) {
        try {
            String sessionKey = USER_SESSIONS_PREFIX + userId;
            redisTemplate.opsForValue().set(sessionKey, sessionInfo, expirationMinutes, TimeUnit.MINUTES);
            logger.debug("User session stored for user {}: {}", userId, sessionInfo);
        } catch (Exception e) {
            logger.error("Failed to store user session for user {}: {}", userId, e.getMessage());
        }
    }
    
    /**
     * Remove user session information
     * @param userId User ID
     */
    public void removeUserSession(Integer userId) {
        try {
            String sessionKey = USER_SESSIONS_PREFIX + userId;
            redisTemplate.delete(sessionKey);
            logger.info("User session removed for user {}", userId);
        } catch (Exception e) {
            logger.error("Failed to remove user session for user {}: {}", userId, e.getMessage());
        }
    }
    
    /**
     * Get user session information
     * @param userId User ID
     * @return Session information or null if not found
     */
    public String getUserSession(Integer userId) {
        try {
            String sessionKey = USER_SESSIONS_PREFIX + userId;
            return redisTemplate.opsForValue().get(sessionKey);
        } catch (Exception e) {
            logger.error("Failed to get user session for user {}: {}", userId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Invalidate all sessions for a user (useful for account deactivation)
     * @param userId User ID
     */
    public void invalidateAllUserSessions(Integer userId) {
        try {
            // Remove user session
            removeUserSession(userId);
            
            // Note: For a production system, you might want to implement
            // a more sophisticated session tracking mechanism that can
            // invalidate multiple active sessions for the same user
            
            logger.info("All sessions invalidated for user {}", userId);
        } catch (Exception e) {
            logger.error("Failed to invalidate sessions for user {}: {}", userId, e.getMessage());
        }
    }
}
