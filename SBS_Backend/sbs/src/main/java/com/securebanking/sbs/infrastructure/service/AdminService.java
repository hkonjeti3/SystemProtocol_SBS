package com.securebanking.sbs.infrastructure.service;
import com.securebanking.sbs.shared.dto.AccountDto;
import com.securebanking.sbs.shared.dto.UserDto;
import com.securebanking.sbs.shared.dto.UserRoleDto;
import com.securebanking.sbs.infrastructure.iservice.IAdmin;
import com.securebanking.sbs.modules.customer.model.Account;
import com.securebanking.sbs.shared.model.User;
import com.securebanking.sbs.shared.model.UserRole;
import com.securebanking.sbs.infrastructure.repository.UserRepo;
import com.securebanking.sbs.infrastructure.service.ActivityLogService;
import com.securebanking.sbs.infrastructure.service.NotificationService;
import com.securebanking.sbs.infrastructure.service.EmailService;
import com.securebanking.sbs.infrastructure.service.SessionManagementService;
import com.securebanking.sbs.shared.model.User;
import com.securebanking.sbs.shared.dto.UserDto;
import com.securebanking.sbs.shared.dto.UserRoleDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import com.securebanking.sbs.core.exception.UserNotFoundException;

@Service
public class AdminService implements IAdmin {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminService.class);

    @Autowired
    public UserRepo userRepo;
    
    @Autowired
    private ActivityLogService activityLogService;
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private EmailService emailService;
    
    @Autowired
    private SessionManagementService sessionManagementService;

    public List<UserDto> getAllUsers() {
        try {
            List<User> users = userRepo.findAll();
            LOGGER.info("Found {} users", users.size());

            return users.stream()
                    .map(this::convertEntityToDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LOGGER.error("Error retrieving users: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve users: " + e.getMessage(), e);
        }
    }

    private UserDto convertEntityToDto(User user) {
        try {
            UserDto userDto = new UserDto();
            userDto.setUserId(user.getUserId());
            userDto.setFirstName(user.getFirstName());
            userDto.setLastName(user.getLastName());
            userDto.setUsername(user.getUsername());
            userDto.setPhoneNumber(user.getPhoneNumber());
            userDto.setAddress(user.getAddress());
            userDto.setStatus(user.getStatus());
            userDto.setEmailAddress(user.getEmailAddress());

            // Handle role safely
            if (user.getRole() != null) {
                UserRoleDto userRoleDto = new UserRoleDto();
                userRoleDto.setRoleId(user.getRole().getRoleId());
                userRoleDto.setRoleName(user.getRole().getRoleName());
                userDto.setRole(userRoleDto);
                
                // Debug: Log role information
                System.out.println("User " + user.getUsername() + " has role: " + user.getRole().getRoleName() + " (ID: " + user.getRole().getRoleId() + ")");
            } else {
                LOGGER.warn("User {} has no role assigned", user.getUserId());
                System.out.println("User " + user.getUsername() + " has NO role assigned");
                // Set a default role or leave it null
                userDto.setRole(null);
            }

            return userDto;
        } catch (Exception e) {
            LOGGER.error("Error converting user {} to DTO: {}", user.getUserId(), e.getMessage(), e);
            throw new RuntimeException("Error converting user to DTO: " + e.getMessage(), e);
        }
    }

    public Optional<User> findUserById(Long userId) {
        LOGGER.info("Finding user by ID: {}", userId);
        try {
            return userRepo.findById(Math.toIntExact(userId));
        } catch (Exception e) {
            LOGGER.error("Error finding user by ID {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Error finding user: " + e.getMessage(), e);
        }
    }

    @Transactional
    public UserDto updateUserStatus(Integer userId, String status, Integer adminUserId) {
        try {
            LOGGER.info("Updating user {} status to {} by admin {}", userId, status, adminUserId);
            
            User user = userRepo.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
            
            String previousStatus = user.getStatus();
            user.setStatus(status);
            User updatedUser = userRepo.save(user);
            
            LOGGER.info("Successfully updated user {} status to {}", userId, status);
            
            // Log activity for admin
            try {
                String adminAction = status.equals("Active") ? "User Activated" : "User Deactivated";
                String adminDescription = String.format("User %s %s by admin", 
                    user.getUsername(), status.equals("Active") ? "activated" : "deactivated");
                
                activityLogService.logActivity(
                    adminUserId,
                    adminAction,
                    adminDescription,
                    String.format("User %s status changed from %s to %s", 
                        user.getUsername(), previousStatus, status)
                );
                
                LOGGER.info("Admin activity logged for user status update by admin {}", adminUserId);
            } catch (Exception e) {
                LOGGER.error("Failed to log admin activity for user status update: {}", e.getMessage());
                // Don't let activity logging failure rollback the entire transaction
            }
            
            // Send notification and email to user
            try {
                String notificationTitle = status.equals("Active") ? "Account Activated" : "Account Deactivated";
                String notificationMessage = status.equals("Active") 
                    ? "Your account has been activated by the administrator. You can now log in and access your account."
                    : "Your account has been deactivated by the administrator. Please contact support for assistance.";
                
                // Create in-app notification
                notificationService.createNotification(
                    userId,
                    "ACCOUNT_STATUS_CHANGE",
                    notificationTitle,
                    notificationMessage,
                    userId
                );
                
                // Send email notification
                emailService.sendNotificationEmail(
                    user.getEmailAddress(), 
                    notificationTitle, 
                    notificationMessage
                );
                
                LOGGER.info("Notification and email sent to user {} for status change to {}", userId, status);
            } catch (Exception e) {
                LOGGER.error("Failed to send notification/email to user {}: {}", userId, e.getMessage());
                // Don't let notification failure rollback the entire transaction
            }
            
            // Handle session management for deactivated users
            if (status.equals("Inactive") || status.equals("Deactivated")) {
                try {
                    // Invalidate all sessions for the deactivated user
                    sessionManagementService.invalidateAllUserSessions(userId);
                    LOGGER.info("All sessions invalidated for deactivated user {}", userId);
                } catch (Exception e) {
                    LOGGER.error("Failed to invalidate sessions for user {}: {}", userId, e.getMessage());
                    // Don't let session invalidation failure rollback the entire transaction
                }
            }
            
            return convertEntityToDto(updatedUser);
        } catch (UserNotFoundException e) {
            LOGGER.error("User not found: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            LOGGER.error("Error updating user status: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update user status: " + e.getMessage(), e);
        }
    }
}
