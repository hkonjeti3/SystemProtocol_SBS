package com.securebanking.sbs.infrastructure.service;

import com.securebanking.sbs.modules.internal_user.model.AccountDeletionRequest;
import com.securebanking.sbs.modules.customer.model.Account;
import com.securebanking.sbs.shared.dto.AccountDeletionRequestDto;
import com.securebanking.sbs.infrastructure.repository.AccountDeletionRequestRepo;
import com.securebanking.sbs.infrastructure.repository.AccountRepo;
import com.securebanking.sbs.infrastructure.repository.UserRepo;
import com.securebanking.sbs.shared.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountDeletionRequestService {
    
    @Autowired
    private AccountDeletionRequestRepo accountDeletionRequestRepo;
    
    @Autowired
    private AccountRepo accountRepo;
    
    @Autowired
    private UserRepo userRepo;
    
    @Autowired
    private ActivityLogService activityLogService;
    
    @Autowired
    private NotificationService notificationService;
    
    /**
     * Submit a new account deletion request
     */
    @Transactional
    public AccountDeletionRequestDto submitDeletionRequest(Integer userId, Long accountId, String reason) {
        // Validate account exists and belongs to user
        Account account = accountRepo.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        
        if (!account.getUser().getUserId().equals(userId)) {
            throw new RuntimeException("Account does not belong to user");
        }
        
        // Check if account has zero balance
        BigDecimal balance = new BigDecimal(account.getBalance());
        if (balance.compareTo(BigDecimal.ZERO) != 0) {
            throw new RuntimeException("Cannot delete account with non-zero balance. Current balance: $" + account.getBalance());
        }
        
        // Check if account is already active
        if (!"Active".equalsIgnoreCase(account.getStatus())) {
            throw new RuntimeException("Cannot delete inactive account");
        }
        
        // Check if there's already a pending deletion request for this account
        if (accountDeletionRequestRepo.existsByAccountIdAndStatus(accountId, "Pending")) {
            throw new RuntimeException("Account deletion request already pending");
        }
        
        // Create deletion request
        AccountDeletionRequest deletionRequest = new AccountDeletionRequest(
            userId, accountId, account.getAccountNumber(), 
            account.getAccountType(), account.getBalance(), reason
        );
        
        AccountDeletionRequest savedRequest = accountDeletionRequestRepo.save(deletionRequest);
        
        // Log activity
        activityLogService.logActivity(
            userId,
            "Account Deletion Request",
            "Requested deletion of account " + account.getAccountNumber(),
            "Account deletion request submitted for " + account.getAccountType() + " account with reason: " + reason
        );
        
        // Send notification
        notificationService.createNotification(
            userId,
            "account_deletion_request",
            "Account Deletion Request Submitted",
            "Your request to delete account " + account.getAccountNumber() + " has been submitted and is pending approval.",
            accountId.intValue()
        );
        
        return convertToDto(savedRequest);
    }
    
    /**
     * Get all pending deletion requests
     */
    public List<AccountDeletionRequestDto> getPendingDeletionRequests() {
        List<AccountDeletionRequest> requests = accountDeletionRequestRepo.findByStatusOrderByTimestampDesc("Pending");
        return requests.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    /**
     * Get deletion requests for a specific user
     */
    public List<AccountDeletionRequestDto> getUserDeletionRequests(Integer userId) {
        List<AccountDeletionRequest> requests = accountDeletionRequestRepo.findByUserIdOrderByTimestampDesc(userId);
        return requests.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    /**
     * Approve account deletion request
     */
    @Transactional
    public void approveDeletionRequest(Integer requestId, Integer adminUserId) {
        AccountDeletionRequest request = accountDeletionRequestRepo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Deletion request not found"));
        
        if (!"Pending".equals(request.getStatus())) {
            throw new RuntimeException("Request is not in pending status");
        }
        
        // Update request status
        request.setStatus("Approved");
        request.setApproverId(adminUserId);
        request.setApprovalDate(LocalDateTime.now());
        accountDeletionRequestRepo.save(request);
        
        // Mark account as deleted (soft delete by setting status to Deleted)
        Account account = accountRepo.findById(request.getAccountId())
                .orElseThrow(() -> new RuntimeException("Account not found"));
        account.setStatus("Deleted");
        accountRepo.save(account);
        
        // Log admin activity
        activityLogService.logActivity(
            adminUserId,
            "Account Deletion Approved",
            "Approved deletion of account " + request.getAccountNumber(),
            "Account deletion request approved for user " + request.getUserId() + ". Account: " + request.getAccountNumber()
        );
        
        // Log user activity
        activityLogService.logActivity(
            request.getUserId(),
            "Account Deletion Approved",
            "Account deletion request approved for account " + request.getAccountNumber(),
            "Your request to delete " + request.getAccountType() + " account has been approved by admin"
        );
        
        // Send notification to user
        notificationService.createNotification(
            request.getUserId(),
            "account_deletion_approved",
            "Account Deletion Approved",
            "Your request to delete account " + request.getAccountNumber() + " has been approved. The account has been deleted.",
            request.getAccountId().intValue()
        );
    }
    
    /**
     * Reject account deletion request
     */
    @Transactional
    public void rejectDeletionRequest(Integer requestId, Integer adminUserId, String rejectionReason) {
        AccountDeletionRequest request = accountDeletionRequestRepo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Deletion request not found"));
        
        if (!"Pending".equals(request.getStatus())) {
            throw new RuntimeException("Request is not in pending status");
        }
        
        // Update request status
        request.setStatus("Rejected");
        request.setApproverId(adminUserId);
        request.setApprovalDate(LocalDateTime.now());
        request.setRejectionReason(rejectionReason);
        accountDeletionRequestRepo.save(request);
        
        // Log admin activity
        activityLogService.logActivity(
            adminUserId,
            "Account Deletion Rejected",
            "Rejected deletion of account " + request.getAccountNumber(),
            "Account deletion request rejected for user " + request.getUserId() + ". Reason: " + rejectionReason
        );
        
        // Log user activity
        activityLogService.logActivity(
            request.getUserId(),
            "Account Deletion Rejected",
            "Account deletion request rejected for account " + request.getAccountNumber(),
            "Your request to delete " + request.getAccountType() + " account has been rejected. Reason: " + rejectionReason
        );
        
        // Send notification to user
        notificationService.createNotification(
            request.getUserId(),
            "account_deletion_rejected",
            "Account Deletion Rejected",
            "Your request to delete account " + request.getAccountNumber() + " has been rejected. Reason: " + rejectionReason,
            request.getAccountId().intValue()
        );
    }
    
    /**
     * Convert entity to DTO
     */
    private AccountDeletionRequestDto convertToDto(AccountDeletionRequest request) {
        AccountDeletionRequestDto dto = new AccountDeletionRequestDto(
            request.getId(), request.getUserId(), request.getAccountId(),
            request.getAccountNumber(), request.getAccountType(), request.getCurrentBalance(),
            request.getReason(), request.getStatus(), request.getTimestamp()
        );
        
        // Set additional fields
        dto.setApproverId(request.getApproverId());
        dto.setApprovalDate(request.getApprovalDate());
        dto.setRejectionReason(request.getRejectionReason());
        
        // Get username for display
        if (request.getUser() != null) {
            dto.setUsername(request.getUser().getUsername());
        }
        
        return dto;
    }
}
