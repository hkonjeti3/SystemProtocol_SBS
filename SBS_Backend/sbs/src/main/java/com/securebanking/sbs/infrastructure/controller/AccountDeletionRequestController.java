package com.securebanking.sbs.infrastructure.controller;

import com.securebanking.sbs.shared.dto.AccountDeletionRequestDto;
import com.securebanking.sbs.infrastructure.service.AccountDeletionRequestService;
import com.securebanking.sbs.core.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import com.securebanking.sbs.shared.model.User;
import com.securebanking.sbs.infrastructure.repository.UserRepo;

@RestController
@RequestMapping("/api/v1/account-deletion")
@CrossOrigin(origins = "*")
public class AccountDeletionRequestController {
    
    @Autowired
    private AccountDeletionRequestService accountDeletionRequestService;
    
    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepo userRepo;
    
    /**
     * Submit account deletion request (Customer)
     */
    @PostMapping("/request")
    public ResponseEntity<?> submitDeletionRequest(@RequestBody Map<String, Object> request, HttpServletRequest httpRequest) {
        try {
            String token = extractToken(httpRequest);
            if (token == null) {
                return ResponseEntity.status(401).body("Unauthorized: No token provided");
            }
            
            Long userId = jwtUtil.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(401).body("Unauthorized: Invalid token");
            }
            
            Long accountId = Long.valueOf(request.get("accountId").toString());
            String reason = request.get("reason").toString();
            
            AccountDeletionRequestDto result = accountDeletionRequestService.submitDeletionRequest(userId.intValue(), accountId, reason);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
    
    /**
     * Get user's deletion requests (Customer)
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AccountDeletionRequestDto>> getUserDeletionRequests(@PathVariable Integer userId, HttpServletRequest httpRequest) {
        try {
            String token = extractToken(httpRequest);
            if (token == null) {
                return ResponseEntity.status(401).body(null);
            }
            
            // Verify the user is requesting their own data or is admin
            Long tokenUserId = jwtUtil.extractUserId(token);
            if (tokenUserId == null || (!tokenUserId.equals(userId.longValue()) && !isAdmin(token))) {
                return ResponseEntity.status(403).body(null);
            }
            
            List<AccountDeletionRequestDto> requests = accountDeletionRequestService.getUserDeletionRequests(userId);
            return ResponseEntity.ok(requests);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    /**
     * Get all pending deletion requests (Admin)
     */
    @GetMapping("/pending")
    public ResponseEntity<List<AccountDeletionRequestDto>> getPendingDeletionRequests(HttpServletRequest httpRequest) {
        try {
            String token = extractToken(httpRequest);
            if (token == null) {
                return ResponseEntity.status(401).body(null);
            }
            
            if (!isAdmin(token)) {
                return ResponseEntity.status(403).body(null);
            }
            
            List<AccountDeletionRequestDto> requests = accountDeletionRequestService.getPendingDeletionRequests();
            return ResponseEntity.ok(requests);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    /**
     * Approve deletion request (Admin)
     */
    @PostMapping("/approve/{requestId}")
    public ResponseEntity<?> approveDeletionRequest(@PathVariable Integer requestId, HttpServletRequest httpRequest) {
        try {
            String token = extractToken(httpRequest);
            if (token == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Unauthorized: No token provided"));
            }
            
            if (!isAdmin(token)) {
                return ResponseEntity.status(403).body(Map.of("error", "Forbidden: Admin access required"));
            }
            
            Long adminUserId = jwtUtil.extractUserId(token);
            accountDeletionRequestService.approveDeletionRequest(requestId, adminUserId.intValue());
            
            return ResponseEntity.ok(Map.of("message", "Account deletion request approved successfully", "requestId", requestId));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Error: " + e.getMessage()));
        }
    }
    
    /**
     * Reject deletion request (Admin)
     */
    @PostMapping("/reject/{requestId}")
    public ResponseEntity<?> rejectDeletionRequest(@PathVariable Integer requestId, @RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        try {
            String token = extractToken(httpRequest);
            if (token == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Unauthorized: No token provided"));
            }
            
            if (!isAdmin(token)) {
                return ResponseEntity.status(403).body(Map.of("error", "Forbidden: Admin access required"));
            }
            
            Long adminUserId = jwtUtil.extractUserId(token);
            String rejectionReason = request.get("reason");
            
            if (rejectionReason == null || rejectionReason.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Rejection reason is required"));
            }
            
            accountDeletionRequestService.rejectDeletionRequest(requestId, adminUserId.intValue(), rejectionReason);
            
            return ResponseEntity.ok(Map.of("message", "Account deletion request rejected successfully", "requestId", requestId, "reason", rejectionReason));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Error: " + e.getMessage()));
        }
    }
    
    /**
     * Extract JWT token from request
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    
    /**
     * Check if user is admin - improved role checking
     */
    private boolean isAdmin(String token) {
        try {
            Long userId = jwtUtil.extractUserId(token);
            if (userId == null) {
                return false;
            }
            
            // Check if user exists and has admin role (role ID 4)
            User user = userRepo.findById(userId.intValue()).orElse(null);
            if (user == null) {
                return false;
            }
            
            // Check if user has admin role
            return user.getRole() != null && user.getRole().getRoleId() == 4;
                    
        } catch (Exception e) {
            return false;
        }
    }
}
