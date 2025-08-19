package com.securebanking.sbs.infrastructure.service;

import com.securebanking.sbs.shared.dto.ProfileUpdateRequestDto;
import com.securebanking.sbs.shared.event.NotificationEvent;
import com.securebanking.sbs.modules.internal_user.model.ProfileUpdateRequest;
import com.securebanking.sbs.shared.model.User;
import com.securebanking.sbs.infrastructure.repository.ProfileUpdateRequestRepo;
import com.securebanking.sbs.infrastructure.repository.UserRepo;
import com.securebanking.sbs.core.exception.ResourceNotFoundException;
import com.securebanking.sbs.infrastructure.service.ActivityLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProfileUpdateRequestService {
    
    @Autowired
    private ProfileUpdateRequestRepo profileUpdateRequestRepo;
    
    @Autowired
    private UserRepo userRepo;
    
    @Autowired
    private ActivityLogService activityLogService;
    
    // KafkaProducerService removed for Render deployment
    
    // Submit a new profile update request
    @Transactional
    public ProfileUpdateRequestDto submitProfileUpdateRequest(ProfileUpdateRequestDto requestDto) {
        // Validate user exists
        User user = userRepo.findById(requestDto.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + requestDto.getUserId()));
        
        // Create new profile update request
        ProfileUpdateRequest request = new ProfileUpdateRequest(
                requestDto.getUserId(),
                requestDto.getRequestType(),
                requestDto.getCurrentValue(),
                requestDto.getRequestedValue(),
                requestDto.getReason()
        );
        
        // Save the request
        ProfileUpdateRequest savedRequest = profileUpdateRequestRepo.save(request);
        
        // Log activity for customer
        try {
            activityLogService.logActivity(
                requestDto.getUserId(),
                "Profile Update Request Submitted",
                "Profile update request submitted for " + requestDto.getRequestType(),
                "Current Value: " + requestDto.getCurrentValue() + ", Requested Value: " + requestDto.getRequestedValue() + ", Reason: " + requestDto.getReason()
            );
            System.out.println("Activity logged for profile update request submission");
        } catch (Exception e) {
            System.out.println("Warning: Failed to log activity for submission: " + e.getMessage());
            // Don't let activity logging failure rollback the entire transaction
        }
        
        // Create notification for request submission
        try {
            createNotificationEvent(request, "submitted");
            System.out.println("Notification event created for request submission");
        } catch (Exception e) {
            System.out.println("Warning: Failed to create notification event for submission: " + e.getMessage());
            // Don't let notification failure rollback the entire transaction
        }
        
        // Kafka event publishing removed for Render deployment
        
        // Convert to DTO and return
        return convertToDto(savedRequest);
    }
    
    // Get all profile update requests for a user
    public List<ProfileUpdateRequestDto> getUserProfileRequests(Integer userId) {
        List<ProfileUpdateRequest> requests = profileUpdateRequestRepo.findByUserIdOrderByTimestampDesc(userId);
        return requests.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    // Get all pending profile update requests (for admin/internal users)
    public List<ProfileUpdateRequestDto> getPendingProfileRequests() {
        List<ProfileUpdateRequest> requests = profileUpdateRequestRepo.findByStatusOrderByTimestampDesc("Pending");
        return requests.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    // Approve a profile update request
    @Transactional
    public ProfileUpdateRequestDto approveProfileRequest(Integer requestId) {
        System.out.println("=== ProfileUpdateRequestService.approveProfileRequest called for request ID: " + requestId + " ===");
        
        try {
            ProfileUpdateRequest request = profileUpdateRequestRepo.findById(requestId)
                    .orElseThrow(() -> new ResourceNotFoundException("Profile update request not found with ID: " + requestId));
            
            System.out.println("Found profile update request: " + request.getRequestType() + " for user ID: " + request.getUserId());
            
            // Update the request status
            request.setStatus("Approved");
            ProfileUpdateRequest savedRequest = profileUpdateRequestRepo.save(request);
            
            System.out.println("Profile update request status updated to 'Approved'");
            
            // Update the user's profile
            updateUserProfile(request);
            
            System.out.println("User profile updated successfully");
            
            // Note: Notifications are now handled by ApprovalWorkflowService to avoid duplicates
            System.out.println("Profile update approved - notifications will be sent by ApprovalWorkflowService");
            
            ProfileUpdateRequestDto result = convertToDto(savedRequest);
            System.out.println("=== ProfileUpdateRequestService.approveProfileRequest completed successfully ===");
            return result;
            
        } catch (Exception e) {
            System.out.println("ERROR in approveProfileRequest: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    // Reject a profile update request
    @Transactional
    public ProfileUpdateRequestDto rejectProfileRequest(Integer requestId, String reason) {
        ProfileUpdateRequest request = profileUpdateRequestRepo.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile update request not found with ID: " + requestId));
        
        // Update the request status
        request.setStatus("Rejected");
        ProfileUpdateRequest savedRequest = profileUpdateRequestRepo.save(request);
        
        // Note: Notifications are now handled by ApprovalWorkflowService to avoid duplicates
        System.out.println("Profile update rejected - notifications will be sent by ApprovalWorkflowService");
        
        return convertToDto(savedRequest);
    }
    
    // Update user profile based on approved request
    private void updateUserProfile(ProfileUpdateRequest request) {
        System.out.println("=== Starting updateUserProfile for request ID: " + request.getId() + " ===");
        System.out.println("Request details: " + request.getRequestType() + " from '" + request.getCurrentValue() + "' to '" + request.getRequestedValue() + "'");
        
        try {
            User user = userRepo.findById(request.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + request.getUserId()));
            
            System.out.println("Found user: " + user.getUsername() + " (ID: " + user.getUserId() + ")");
            System.out.println("Current user data - firstName: '" + user.getFirstName() + "', lastName: '" + user.getLastName() + "', email: '" + user.getEmailAddress() + "', phone: '" + user.getPhoneNumber() + "', address: '" + user.getAddress() + "'");
            
            // Update the appropriate field based on request type
            switch (request.getRequestType()) {
                case "First Name Update":
                    System.out.println("Updating firstName from '" + user.getFirstName() + "' to '" + request.getRequestedValue() + "'");
                    user.setFirstName(request.getRequestedValue());
                    break;
                case "Last Name Update":
                    System.out.println("Updating lastName from '" + user.getLastName() + "' to '" + request.getRequestedValue() + "'");
                    user.setLastName(request.getRequestedValue());
                    break;
                case "Email Address Update":
                    System.out.println("Updating emailAddress from '" + user.getEmailAddress() + "' to '" + request.getRequestedValue() + "'");
                    user.setEmailAddress(request.getRequestedValue());
                    break;
                case "Phone Number Update":
                    System.out.println("Updating phoneNumber from '" + user.getPhoneNumber() + "' to '" + request.getRequestedValue() + "'");
                    user.setPhoneNumber(request.getRequestedValue());
                    break;
                case "Address Update":
                    System.out.println("Updating address from '" + user.getAddress() + "' to '" + request.getRequestedValue() + "'");
                    user.setAddress(request.getRequestedValue());
                    break;
                default:
                    throw new IllegalArgumentException("Unknown request type: " + request.getRequestType());
            }
            
            System.out.println("About to save user with updated data...");
            User savedUser = userRepo.save(user);
            System.out.println("User saved successfully. Updated user data - firstName: '" + savedUser.getFirstName() + "', lastName: '" + savedUser.getLastName() + "', email: '" + savedUser.getEmailAddress() + "', phone: '" + savedUser.getPhoneNumber() + "', address: '" + savedUser.getAddress() + "'");
            System.out.println("=== Completed updateUserProfile ===");
            
        } catch (Exception e) {
            System.out.println("ERROR in updateUserProfile: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    // Create notification event and publish to Kafka
    private void createNotificationEvent(ProfileUpdateRequest request, String action, String... reason) {
        String title;
        String message;
        
        switch (action) {
            case "submitted":
                title = "Profile Update Request Submitted";
                message = "Your profile update request for " + request.getRequestType() + " has been submitted and is pending approval.";
                break;
            case "approved":
                title = "Profile Update Request Approved";
                message = "Your profile update request for " + request.getRequestType() + " has been approved and your profile has been updated.";
                break;
            case "rejected":
                title = "Profile Update Request Rejected";
                message = "Your profile update request for " + request.getRequestType() + " has been rejected.";
                if (reason.length > 0 && reason[0] != null) {
                    message += " Reason: " + reason[0];
                }
                break;
            default:
                title = "Profile Update Request " + action.substring(0, 1).toUpperCase() + action.substring(1);
                message = "Your profile update request for " + request.getRequestType() + " has been " + action;
                if (reason.length > 0 && reason[0] != null) {
                    message += ". Reason: " + reason[0];
                }
        }
        
        NotificationEvent event = new NotificationEvent(
                request.getUserId(),
                "profile_update",
                title,
                message,
                request.getId()
        );
        
        // Kafka event publishing removed for Render deployment
    }
    
    // Convert entity to DTO
    private ProfileUpdateRequestDto convertToDto(ProfileUpdateRequest request) {
        ProfileUpdateRequestDto dto = new ProfileUpdateRequestDto();
        dto.setId(request.getId());
        dto.setUserId(request.getUserId());
        dto.setRequestType(request.getRequestType());
        dto.setCurrentValue(request.getCurrentValue());
        dto.setRequestedValue(request.getRequestedValue());
        dto.setReason(request.getReason());
        dto.setStatus(request.getStatus());
        dto.setTimestamp(request.getTimestamp());
        
        // Set user name if user exists
        if (request.getUser() != null) {
            dto.setUserName(request.getUser().getFirstName() + " " + request.getUser().getLastName());
        }
        
        return dto;
    }
} 