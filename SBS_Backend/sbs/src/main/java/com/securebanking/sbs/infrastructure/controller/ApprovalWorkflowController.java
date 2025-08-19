package com.securebanking.sbs.infrastructure.controller;

import com.securebanking.sbs.infrastructure.service.ApprovalWorkflowService;
import com.securebanking.sbs.shared.dto.AccountRequestDto;
import com.securebanking.sbs.shared.dto.ProfileUpdateRequestDto;
import com.securebanking.sbs.core.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.List;
import com.securebanking.sbs.core.exception.ResourceNotFoundException;

@RestController
@RequestMapping("/api/v1/approval")
@CrossOrigin(origins = "*")
public class ApprovalWorkflowController {
    
    private static final Logger logger = LoggerFactory.getLogger(ApprovalWorkflowController.class);
    
    @Autowired
    private ApprovalWorkflowService approvalWorkflowService;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private com.securebanking.sbs.infrastructure.repository.UserRepo userRepo;
    
    // Test endpoint without authentication
    @GetMapping("/test")
    public ResponseEntity<String> testEndpoint() {
        return ResponseEntity.ok("Approval endpoint is working!");
    }
    
    // Test endpoint for pending requests without authentication (for debugging)
    @GetMapping("/test-pending")
    public ResponseEntity<ApprovalWorkflowService.PendingRequestsDto> getTestPendingRequests() {
        try {
            logger.info("Received test request for pending approvals (no auth required)");
            
            // Use a hardcoded admin user ID for testing
            Integer approverId = 68; // Admin user ID
            
            ApprovalWorkflowService.PendingRequestsDto pendingRequests = 
                approvalWorkflowService.getPendingRequestsForApprover(approverId);
            
            logger.info("Test pending requests retrieved: {} account requests, {} profile requests", 
                       pendingRequests.getAccountRequests().size(), 
                       pendingRequests.getProfileRequests().size());
            
            return ResponseEntity.ok(pendingRequests);
        } catch (Exception e) {
            logger.error("Error getting test pending requests: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    // Test endpoint to verify user lookup
    @GetMapping("/test-user/{userId}")
    public ResponseEntity<String> testUserLookup(@PathVariable Integer userId) {
        try {
            logger.info("Testing user lookup for ID: {}", userId);
            
            // Test user lookup
            var user = userRepo.findById(userId);
            if (user.isPresent()) {
                logger.info("User found: {} with role: {}", user.get().getUsername(), 
                           user.get().getRole() != null ? user.get().getRole().getRoleName() : "NO_ROLE");
                return ResponseEntity.ok("User found: " + user.get().getUsername() + 
                                      " with role: " + (user.get().getRole() != null ? user.get().getRole().getRoleName() : "NO_ROLE"));
            } else {
                logger.warn("User not found with ID: {}", userId);
                return ResponseEntity.ok("User not found with ID: " + userId);
            }
        } catch (Exception e) {
            logger.error("Error in user lookup test: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
    
    // Test endpoint to verify service methods directly
    @GetMapping("/test-service")
    public ResponseEntity<String> testServiceMethods() {
        try {
            logger.info("Testing service methods directly");
            
            // Test 1: User lookup
            var user = userRepo.findById(68);
            if (!user.isPresent()) {
                return ResponseEntity.ok("ERROR: User 68 not found");
            }
            logger.info("User lookup successful: {}", user.get().getUsername());
            
            // Test 2: Role checking
            boolean isApprover = approvalWorkflowService.isApprover(user.get());
            logger.info("Role check result: {}", isApprover);
            
            // Test 3: Get pending requests
            List<AccountRequestDto> accountRequests = approvalWorkflowService.getPendingAccountRequests();
            List<ProfileUpdateRequestDto> profileRequests = approvalWorkflowService.getPendingProfileRequests();
            logger.info("Pending requests: {} account, {} profile", accountRequests.size(), profileRequests.size());
            
            return ResponseEntity.ok("Service test successful - User: " + user.get().getUsername() + 
                                  ", IsApprover: " + isApprover + 
                                  ", AccountRequests: " + accountRequests.size() + 
                                  ", ProfileRequests: " + profileRequests.size());
            
        } catch (Exception e) {
            logger.error("Error in service test: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Service test failed: " + e.getMessage());
        }
    }
    
    // Test endpoint to verify role checking logic
    @GetMapping("/test-role/{userId}")
    public ResponseEntity<String> testRoleChecking(@PathVariable Integer userId) {
        try {
            logger.info("Testing role checking for user ID: {}", userId);
            
            var user = userRepo.findById(userId);
            if (!user.isPresent()) {
                return ResponseEntity.ok("ERROR: User not found");
            }
            
            String roleName = user.get().getRole() != null ? user.get().getRole().getRoleName() : "NO_ROLE";
            Integer roleId = user.get().getRole() != null ? user.get().getRole().getRoleId() : null;
            
            logger.info("User: {}, Role: {} (ID: {})", user.get().getUsername(), roleName, roleId);
            
            boolean isApprover = approvalWorkflowService.isApprover(user.get());
            logger.info("IsApprover result: {}", isApprover);
            
            return ResponseEntity.ok(String.format("User: %s, Role: %s (ID: %s), IsApprover: %s", 
                                                user.get().getUsername(), roleName, roleId, isApprover));
            
        } catch (Exception e) {
            logger.error("Error in role checking test: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
    
    // Get all pending requests for the logged-in approver
    @GetMapping("/pending")
    public ResponseEntity<ApprovalWorkflowService.PendingRequestsDto> getPendingRequests(HttpServletRequest request) {
        logger.info("Received request for pending approvals");
        
        try {
            // Extract and validate token
            String token = extractToken(request);
            if (token == null) {
                logger.error("No valid authorization token found");
                return ResponseEntity.status(401).body(null);
            }
            
            // Extract user ID from JWT token
            Long userIdLong = jwtUtil.extractUserId(token);
            if (userIdLong == null) {
                logger.error("Could not extract user ID from token");
                return ResponseEntity.status(401).body(null);
            }
            
            Integer approverId = userIdLong.intValue();
            logger.info("Approver ID extracted from token: {}", approverId);
            
            // Verify user exists and has proper role
            var user = userRepo.findById(approverId);
            if (!user.isPresent()) {
                logger.error("User not found with ID: {}", approverId);
                return ResponseEntity.status(404).body(null);
            }
            
            logger.info("User found: {} with role: {}", user.get().getUsername(), 
                       user.get().getRole() != null ? user.get().getRole().getRoleName() : "NO_ROLE");
            
            // Check if user has approval privileges
            boolean isApprover = approvalWorkflowService.isApprover(user.get());
            logger.info("User approval privileges check: {}", isApprover);
            
            if (!isApprover) {
                logger.error("User {} does not have approval privileges", user.get().getUsername());
                return ResponseEntity.status(403).body(null);
            }
            
            // Get pending requests
            logger.info("Getting pending requests for user: {}", user.get().getUsername());
            ApprovalWorkflowService.PendingRequestsDto pendingRequests = 
                approvalWorkflowService.getPendingRequestsForApprover(approverId);
            
            logger.info("Pending requests retrieved successfully: {} account requests, {} profile requests", 
                       pendingRequests.getAccountRequests().size(), 
                       pendingRequests.getProfileRequests().size());
            
            return ResponseEntity.ok(pendingRequests);
            
        } catch (ResourceNotFoundException e) {
            logger.error("Resource not found: {}", e.getMessage());
            return ResponseEntity.status(404).body(null);
        } catch (RuntimeException e) {
            logger.error("Runtime error: {}", e.getMessage());
            return ResponseEntity.status(403).body(null);
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(null);
        }
    }
    
    // Approve account creation request
    @PostMapping("/account/approve/{requestId}")
    public ResponseEntity<AccountRequestDto> approveAccountRequest(
            @PathVariable Integer requestId,
            HttpServletRequest httpRequest) {
        try {
            logger.info("=== Account approval request received for request ID: {} ===", requestId);
            
            String token = extractToken(httpRequest);
            Integer approverId = jwtUtil.extractUserId(token).intValue();
            
            logger.info("Approver ID: {}", approverId);
            
            AccountRequestDto approvedRequest = approvalWorkflowService.approveAccountRequest(requestId, approverId);
            
            logger.info("Account approval completed successfully for request ID: {}", requestId);
            return ResponseEntity.ok(approvedRequest);
        } catch (Exception e) {
            logger.error("Error in account approval for request ID {}: {}", requestId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    // Reject account creation request
    @PostMapping("/account/reject/{requestId}")
    public ResponseEntity<AccountRequestDto> rejectAccountRequest(
            @PathVariable Integer requestId,
            @RequestBody(required = false) Map<String, String> requestBody,
            HttpServletRequest httpRequest) {
        try {
            String token = extractToken(httpRequest);
            Integer approverId = jwtUtil.extractUserId(token).intValue();
            
            String reason = requestBody != null ? requestBody.get("reason") : null;
            AccountRequestDto rejectedRequest = approvalWorkflowService.rejectAccountRequest(requestId, approverId, reason);
            return ResponseEntity.ok(rejectedRequest);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    // Approve profile update request
    @PostMapping("/profile/approve/{requestId}")
    public ResponseEntity<ProfileUpdateRequestDto> approveProfileRequest(
            @PathVariable Integer requestId,
            HttpServletRequest httpRequest) {
        try {
            logger.info("=== Profile approval request received for request ID: {} ===", requestId);
            
            String token = extractToken(httpRequest);
            Integer approverId = jwtUtil.extractUserId(token).intValue();
            
            logger.info("Approver ID: {}", approverId);
            
            ProfileUpdateRequestDto approvedRequest = approvalWorkflowService.approveProfileRequest(requestId, approverId);
            
            logger.info("Profile approval completed successfully for request ID: {}", requestId);
            return ResponseEntity.ok(approvedRequest);
        } catch (Exception e) {
            logger.error("Error in profile approval for request ID {}: {}", requestId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    // Reject profile update request
    @PostMapping("/profile/reject/{requestId}")
    public ResponseEntity<ProfileUpdateRequestDto> rejectProfileRequest(
            @PathVariable Integer requestId,
            @RequestBody(required = false) Map<String, String> requestBody,
            HttpServletRequest httpRequest) {
        try {
            logger.info("=== Profile rejection request received for request ID: {} ===", requestId);
            
            String token = extractToken(httpRequest);
            Integer approverId = jwtUtil.extractUserId(token).intValue();
            
            logger.info("Approver ID: {}", approverId);
            
            String reason = requestBody != null ? requestBody.get("reason") : null;
            ProfileUpdateRequestDto rejectedRequest = approvalWorkflowService.rejectProfileRequest(requestId, approverId, reason);
            
            logger.info("Profile rejection completed successfully for request ID: {}", requestId);
            return ResponseEntity.ok(rejectedRequest);
        } catch (Exception e) {
            logger.error("Error in profile rejection for request ID {}: {}", requestId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/transaction/approve/{transactionId}")
    public ResponseEntity<Map<String, Object>> approveTransaction(
            @PathVariable Integer transactionId,
            HttpServletRequest httpRequest) {
        try {
            logger.info("=== Transaction approval request received for transaction ID: {} ===", transactionId);
            
            String token = extractToken(httpRequest);
            if (token == null) {
                logger.warn("No valid token found in request");
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }
            
            Integer approverId = jwtUtil.extractUserId(token).intValue();
            
            logger.info("Approver ID: {}", approverId);
            
            Map<String, Object> result = approvalWorkflowService.approveTransaction(transactionId, approverId);
            
            logger.info("Transaction approval completed successfully for transaction ID: {}", transactionId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error in transaction approval for transaction ID {}: {}", transactionId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/transaction/reject/{transactionId}")
    public ResponseEntity<Map<String, Object>> rejectTransaction(
            @PathVariable Integer transactionId,
            @RequestBody(required = false) Map<String, String> requestBody,
            HttpServletRequest httpRequest) {
        try {
            logger.info("=== Transaction rejection request received for transaction ID: {} ===", transactionId);
            
            String token = extractToken(httpRequest);
            if (token == null) {
                logger.warn("No valid token found in request");
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }
            
            Integer approverId = jwtUtil.extractUserId(token).intValue();
            
            logger.info("Approver ID: {}", approverId);
            
            String reason = requestBody != null ? requestBody.get("reason") : null;
            Map<String, Object> result = approvalWorkflowService.rejectTransaction(transactionId, approverId, reason);
            
            logger.info("Transaction rejection completed successfully for transaction ID: {}", transactionId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error in transaction rejection for transaction ID {}: {}", transactionId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    // Helper method to extract token from request
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        
        if (bearerToken == null) {
            logger.warn("No Authorization header found");
            return null;
        }
        
        if (!bearerToken.startsWith("Bearer ")) {
            logger.warn("Invalid Authorization header format. Expected 'Bearer <token>'");
            return null;
        }
        
        String token = bearerToken.substring(7);
        if (token.trim().isEmpty()) {
            logger.warn("Empty token in Authorization header");
            return null;
        }
        
        logger.info("Token extracted successfully: {}", token.substring(0, Math.min(20, token.length())) + "...");
        return token;
    }
} 