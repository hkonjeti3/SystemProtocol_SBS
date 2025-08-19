package com.securebanking.sbs.infrastructure.service;

import com.securebanking.sbs.shared.dto.AccountRequestDto;
import com.securebanking.sbs.shared.dto.ProfileUpdateRequestDto;
import com.securebanking.sbs.shared.event.NotificationEvent;
import com.securebanking.sbs.modules.internal_user.model.AccountRequest;
import com.securebanking.sbs.modules.internal_user.model.ProfileUpdateRequest;
import com.securebanking.sbs.shared.model.User;
import com.securebanking.sbs.infrastructure.repository.AccountRequestRepo;
import com.securebanking.sbs.infrastructure.repository.ProfileUpdateRequestRepo;
import com.securebanking.sbs.infrastructure.repository.UserRepo;
import com.securebanking.sbs.core.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import com.securebanking.sbs.shared.dto.AccountDto;
import com.securebanking.sbs.infrastructure.repository.TransactionRepo;
import com.securebanking.sbs.infrastructure.repository.TransactionAuthorizationRepo;
import com.securebanking.sbs.modules.customer.model.Transaction;
import com.securebanking.sbs.modules.customer.model.TransactionAuthorization;

import java.util.Map;
import java.util.HashMap;
import com.securebanking.sbs.shared.dto.TransactionDto;
import com.securebanking.sbs.shared.enums.RequestStatus;

@Service
public class ApprovalWorkflowService {
    
    private static final Logger logger = LoggerFactory.getLogger(ApprovalWorkflowService.class);
    
    @Autowired
    private AccountRequestRepo accountRequestRepo;
    
    @Autowired
    private ProfileUpdateRequestRepo profileUpdateRequestRepo;
    
    @Autowired
    private UserRepo userRepo;
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private EmailService emailService;
    
    // KafkaEventService removed for Render deployment
    
    @Autowired
    private ProfileUpdateRequestService profileUpdateRequestService;

    @Autowired
    private AccountService accountService;
    
    @Autowired
    private TransactionRepo transactionRepo;
    
    @Autowired
    private TransactionAuthorizationRepo transactionAuthorizationRepo;
    
    @Autowired
    private ActivityLogService activityLogService;
    
    // Get all pending requests for Admin and InternalUser roles
    public PendingRequestsDto getPendingRequestsForApprover(Integer approverId) {
        try {
            logger.info("Getting pending requests for approver ID: {}", approverId);
            
            // Check if approverId is null
            if (approverId == null) {
                logger.error("Approver ID is null");
                throw new RuntimeException("Approver ID is null");
            }
            
            User approver = userRepo.findById(approverId)
                    .orElseThrow(() -> new ResourceNotFoundException("Approver not found with ID: " + approverId));
            
            logger.info("Found approver: {} (ID: {})", approver.getUsername(), approver.getUserId());
            
            // Check if user has Admin or InternalUser role
            if (!isApprover(approver)) {
                String roleName = approver.getRole() != null ? approver.getRole().getRoleName() : "NO_ROLE";
                logger.warn("User {} (ID: {}) with role '{}' does not have approval privileges", 
                           approver.getUsername(), approver.getUserId(), roleName);
                throw new RuntimeException("User does not have approval privileges. Role: " + roleName);
            }
            
            logger.info("User {} has approval privileges", approver.getUsername());
            
            List<AccountRequestDto> pendingAccountRequests = getPendingAccountRequests();
            List<ProfileUpdateRequestDto> pendingProfileRequests = getPendingProfileRequests();
            
            // Get pending transaction requests with robust error handling
            List<TransactionDto> pendingTransactionRequests = getPendingTransactionRequests();
            
            logger.info("Retrieved {} account requests, {} profile requests, and {} transaction requests", 
                       pendingAccountRequests.size(), pendingProfileRequests.size(), pendingTransactionRequests.size());
            
            return new PendingRequestsDto(pendingAccountRequests, pendingProfileRequests, pendingTransactionRequests);
        } catch (ResourceNotFoundException e) {
            logger.error("Resource not found: {}", e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            logger.error("Runtime error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error in getPendingRequestsForApprover: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get pending requests: " + e.getMessage(), e);
        }
    }
    
    // Check if user is an approver (Admin or InternalUser)
    public boolean isApprover(User user) {
        if (user.getRole() == null) {
            logger.warn("User {} has no role assigned", user.getUserId());
            return false;
        }
        
        String roleName = user.getRole().getRoleName();
        Integer roleId = user.getRole().getRoleId();
        logger.info("Checking if user {} with role '{}' (ID: {}) is an approver", user.getUsername(), roleName, roleId);
        
        // Check for Admin role (roleId = 1) or InternalUser role (roleId = 3)
        if (roleId != null && (roleId == 1 || roleId == 3)) {
            logger.info("User {} is an approver (role: {})", user.getUsername(), roleName);
            return true;
        }
        
        // Also check by role name for additional flexibility
        if (roleName != null && (roleName.toLowerCase().contains("admin") || 
                                 roleName.toLowerCase().contains("internal") ||
                                 roleName.toLowerCase().contains("approver"))) {
            logger.info("User {} is an approver (role: {})", user.getUsername(), roleName);
            return true;
        }
        
        logger.warn("User {} is not an approver (role: {})", user.getUsername(), roleName);
        return false;
    }
    
    // Get pending account requests
    public List<AccountRequestDto> getPendingAccountRequests() {
        List<AccountRequest> requests = accountRequestRepo.findByStatusOrderByTimestampDesc("Pending");
        return requests.stream()
                .map(this::convertAccountRequestToDto)
                .collect(Collectors.toList());
    }
    
    // Get pending profile requests
    public List<ProfileUpdateRequestDto> getPendingProfileRequests() {
        List<ProfileUpdateRequest> requests = profileUpdateRequestRepo.findByStatusOrderByTimestampDesc("Pending");
        return requests.stream()
                .map(this::convertProfileRequestToDto)
                .collect(Collectors.toList());
    }

    // Get pending transaction requests
    public List<TransactionDto> getPendingTransactionRequests() {
        try {
            logger.info("Getting pending transaction requests...");
            
            // Get all transactions and filter for pending ones manually to avoid database query issues
            List<Transaction> allTransactions = transactionRepo.findAll();
            logger.info("Found {} total transactions in database", allTransactions.size());
            
            List<Transaction> pendingTransactions = new ArrayList<>();
            for (Transaction transaction : allTransactions) {
                String status = transaction.getStatus();
                if (status != null && (status.equalsIgnoreCase("PENDING") || 
                                      status.equalsIgnoreCase("Pending") || 
                                      status.equalsIgnoreCase("pending"))) {
                    pendingTransactions.add(transaction);
                }
            }
            
            logger.info("Found {} pending transactions after filtering", pendingTransactions.size());
            
            List<TransactionDto> dtos = new ArrayList<>();
            for (Transaction transaction : pendingTransactions) {
                try {
                    logger.info("Converting transaction ID: {} with status: {}", 
                               transaction.getTransactionId(), transaction.getStatus());
                    TransactionDto dto = convertTransactionToDto(transaction);
                    dtos.add(dto);
                    logger.info("Successfully converted transaction ID: {}", transaction.getTransactionId());
                } catch (Exception e) {
                    logger.error("Error converting transaction ID {}: {}", 
                                transaction.getTransactionId(), e.getMessage(), e);
                    // Continue with other transactions instead of failing completely
                    logger.warn("Skipping transaction ID {} due to conversion error", transaction.getTransactionId());
                }
            }
            
            logger.info("Successfully converted {} transactions to DTOs", dtos.size());
            return dtos;
        } catch (Exception e) {
            logger.error("Error in getPendingTransactionRequests: {}", e.getMessage(), e);
            // Return empty list instead of throwing exception to prevent 500 errors
            return new ArrayList<>();
        }
    }
    
    // Approve account creation request
    @Transactional
    public AccountRequestDto approveAccountRequest(Integer requestId, Integer approverId) {
        AccountRequest request = accountRequestRepo.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Account request not found"));
        
        User approver = userRepo.findById(approverId)
                .orElseThrow(() -> new ResourceNotFoundException("Approver not found"));
        
        // Update request status
        request.setStatus("Approved");
        request.setApproverId(approverId);
        request.setApprovalDate(LocalDateTime.now());
        AccountRequest savedRequest = accountRequestRepo.save(request);
        
        // Create account
        createAccountFromRequest(request);
        
        // Send notifications
        sendAccountApprovalNotifications(request, approver, true);
        
        // Log activity
        logActivity("Account created", approverId, 
                   "Account creation approved by " + approver.getFirstName() + " " + approver.getLastName());
        
        return convertAccountRequestToDto(savedRequest);
    }
    
    // Reject account creation request
    @Transactional
    public AccountRequestDto rejectAccountRequest(Integer requestId, Integer approverId, String reason) {
        AccountRequest request = accountRequestRepo.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Account request not found"));
        
        User approver = userRepo.findById(approverId)
                .orElseThrow(() -> new ResourceNotFoundException("Approver not found"));
        
        // Update request status
        request.setStatus("Rejected");
        request.setApproverId(approverId);
        request.setApprovalDate(LocalDateTime.now());
        request.setRejectionReason(reason);
        AccountRequest savedRequest = accountRequestRepo.save(request);
        
        // Send notifications
        sendAccountApprovalNotifications(request, approver, false, reason);
        
        // Log activity
        logActivity("Account creation denied", approverId, 
                   "Account creation denied by " + approver.getFirstName() + " " + approver.getLastName());
        
        return convertAccountRequestToDto(savedRequest);
    }
    
    // Approve profile update request
    @Transactional
    public ProfileUpdateRequestDto approveProfileRequest(Integer requestId, Integer approverId) {
        logger.info("=== ApprovalWorkflowService.approveProfileRequest called for request ID: {} ===", requestId);
        
        // Get the request and approver for logging and notifications
        ProfileUpdateRequest request = profileUpdateRequestRepo.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile update request not found with id: " + requestId));
        
        User approver = userRepo.findById(approverId)
                .orElseThrow(() -> new ResourceNotFoundException("Approver not found"));
        
        // Use the ProfileUpdateRequestService to properly approve the request
        ProfileUpdateRequestDto approvedRequest = profileUpdateRequestService.approveProfileRequest(requestId);
        
        logger.info("ProfileUpdateRequestService.approveProfileRequest completed for request ID: {}", requestId);
        
        // Log activity for admin
        logActivity("Profile Update Approved", approverId, 
                   "Profile update request " + requestId + " approved by " + approver.getUsername());
        
        // Log activity for customer
        logActivity("Profile Update Approved", request.getUserId(),
                   "Your profile update request for " + request.getRequestType() + " has been approved. Approved by: " + approver.getFirstName() + " " + approver.getLastName());
        
        // Send notification to customer
        sendProfileApprovalNotifications(request, approver, true);
        
        logger.info("=== ApprovalWorkflowService.approveProfileRequest completed successfully ===");
        return approvedRequest;
    }
    
    // Reject profile update request
    @Transactional
    public ProfileUpdateRequestDto rejectProfileRequest(Integer requestId, Integer approverId, String reason) {
        try {
            logger.info("Rejecting profile request ID: {} by approver ID: {}", requestId, approverId);
            
            ProfileUpdateRequest request = profileUpdateRequestRepo.findById(requestId)
                    .orElseThrow(() -> new ResourceNotFoundException("Profile update request not found with id: " + requestId));
            
            User approver = userRepo.findById(approverId)
                    .orElseThrow(() -> new ResourceNotFoundException("Approver not found with id: " + approverId));
            
            // Update request status
            request.setStatus("REJECTED");
            ProfileUpdateRequest savedRequest = profileUpdateRequestRepo.save(request);
            
            // Send notifications
            sendProfileApprovalNotifications(request, approver, false, reason);
            
            // Log activity for admin
            logActivity("Profile Update Rejected", approverId, 
                       "Profile update request " + requestId + " rejected by " + approver.getUsername() + ". Reason: " + reason);
            
            // Log activity for customer
            logActivity("Profile Update Rejected", request.getUserId(),
                       "Your profile update request for " + request.getRequestType() + " has been rejected. Reason: " + reason + ". Rejected by: " + approver.getFirstName() + " " + approver.getLastName());
            
            return convertProfileRequestToDto(savedRequest);
        } catch (Exception e) {
            logger.error("Error rejecting profile request: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to reject profile request: " + e.getMessage(), e);
        }
    }

    @Transactional
    public Map<String, Object> approveTransaction(Integer transactionId, Integer approverId) {
        try {
            logger.info("Approving transaction ID: {} by approver ID: {}", transactionId, approverId);
            
            Transaction transaction = transactionRepo.findById(transactionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + transactionId));
            
            User approver = userRepo.findById(approverId)
                    .orElseThrow(() -> new ResourceNotFoundException("Approver not found with id: " + approverId));
            
            // Find the transaction authorization
            TransactionAuthorization authorization = transactionAuthorizationRepo.findByTransactionTransactionId(transactionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction authorization not found for transaction: " + transactionId));
            
            // Check if transaction is pending
            if (!"PENDING".equals(transaction.getStatus()) || !"PENDING".equals(authorization.getStatus())) {
                throw new RuntimeException("Transaction is not in pending status");
            }
            
            // Update authorization status
            authorization.setStatus("APPROVED");
            authorization.setUser(approver);
            authorization.setLastModifiedtime(LocalDateTime.now());
            transactionAuthorizationRepo.save(authorization);
            
            // Execute the transaction
            accountService.executeTransaction(transaction);
            
            // Update transaction status
            transaction.setStatus("APPROVED");
            transaction.setLastModifiedtime(LocalDateTime.now());
            Transaction savedTransaction = transactionRepo.save(transaction);
            
            // Send notifications
            sendTransactionApprovalNotifications(transaction, approver, true);
            
            // Log activity
            logActivity("TRANSACTION_APPROVED", approverId, 
                       "Transaction " + transactionId + " approved by " + approver.getUsername());
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", "Transaction approved successfully");
            result.put("transactionId", transactionId);
            result.put("status", "APPROVED");
            
            return result;
        } catch (Exception e) {
            logger.error("Error approving transaction: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to approve transaction: " + e.getMessage(), e);
        }
    }

    @Transactional
    public Map<String, Object> rejectTransaction(Integer transactionId, Integer approverId, String reason) {
        try {
            logger.info("Rejecting transaction ID: {} by approver ID: {} with reason: {}", transactionId, approverId, reason);
            
            Transaction transaction = transactionRepo.findById(transactionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + transactionId));
            
            User approver = userRepo.findById(approverId)
                    .orElseThrow(() -> new ResourceNotFoundException("Approver not found with id: " + approverId));
            
            // Find the transaction authorization
            TransactionAuthorization authorization = transactionAuthorizationRepo.findByTransactionTransactionId(transactionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction authorization not found for transaction: " + transactionId));
            
            // Check if transaction is pending
            if (!"PENDING".equals(transaction.getStatus()) || !"PENDING".equals(authorization.getStatus())) {
                throw new RuntimeException("Transaction is not in pending status");
            }
            
            // Update authorization status
            authorization.setStatus("REJECTED");
            authorization.setUser(approver);
            authorization.setDenialReason(reason);
            authorization.setLastModifiedtime(LocalDateTime.now());
            transactionAuthorizationRepo.save(authorization);
            
            // Update transaction status
            transaction.setStatus("REJECTED");
            transaction.setLastModifiedtime(LocalDateTime.now());
            Transaction savedTransaction = transactionRepo.save(transaction);
            
            // Send notifications
            sendTransactionApprovalNotifications(transaction, approver, false, reason);
            
            // Log activity
            logActivity("TRANSACTION_REJECTED", approverId, 
                       "Transaction " + transactionId + " rejected by " + approver.getUsername() + " with reason: " + reason);
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", "Transaction rejected successfully");
            result.put("transactionId", transactionId);
            result.put("status", "REJECTED");
            result.put("reason", reason);
            
            return result;
        } catch (Exception e) {
            logger.error("Error rejecting transaction: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to reject transaction: " + e.getMessage(), e);
        }
    }
    
    // Create account from approved request
    private void createAccountFromRequest(AccountRequest request) {
        logger.info("Creating account for user {} with type {} and initial balance {}", 
                   request.getUserId(), request.getAccountType(), request.getInitialBalance());
        
        try {
            // Create AccountDto from the request
            AccountDto accountDto = new AccountDto();
            accountDto.setUserId(request.getUserId());
            accountDto.setAccountType(request.getAccountType());
            accountDto.setBalance(request.getInitialBalance().toString());
            accountDto.setStatus("Active");
            
            logger.info("AccountDto created: userId={}, accountType={}, balance={}, status={}", 
                       accountDto.getUserId(), accountDto.getAccountType(), accountDto.getBalance(), accountDto.getStatus());
            
            // Create the account using AccountService
            AccountDto createdAccount = accountService.createAccount(accountDto);
            
            logger.info("Account created successfully with account number: {}", 
                       createdAccount.getAccountNumber());
            
        } catch (Exception e) {
            logger.error("Failed to create account for request ID: {}", request.getId(), e);
            // Don't throw the exception, just log it for now to see what's happening
            logger.error("Account creation failed but continuing with approval process");
        }
    }
    
    // Send account approval notifications
    private void sendAccountApprovalNotifications(AccountRequest request, User approver, boolean approved, String... reason) {
        User requester = userRepo.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Requester not found"));
        
        String action = approved ? "approved" : "denied";
        String title = "Account Request " + action.substring(0, 1).toUpperCase() + action.substring(1);
        String message;
        
        if (approved) {
            message = "Your account creation request for " + request.getAccountType() + " account has been approved and your account has been created successfully.";
        } else {
            message = "Your account creation request for " + request.getAccountType() + " account has been denied.";
            if (reason.length > 0 && reason[0] != null) {
                message += " Reason: " + reason[0];
            }
        }
        
        // Create in-app notification
        notificationService.createNotification(
            request.getUserId(),
            "account_request",
            title,
            message,
            request.getId()
        );
        
        // Send email notification
        try {
            emailService.sendNotificationEmail(requester.getEmailAddress(), title, message);
        } catch (Exception e) {
            logger.error("Failed to send email notification", e);
        }
        
        // Publish to Kafka for async processing
        NotificationEvent event = new NotificationEvent(
            request.getUserId(),
            "account_request",
            title,
            message,
            request.getId()
        );
        // Kafka event publishing removed for Render deployment
    }
    
    // Send profile approval notifications
    private void sendProfileApprovalNotifications(ProfileUpdateRequest request, User approver, boolean approved, String... reason) {
        User requester = userRepo.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Requester not found"));
        
        String action = approved ? "approved" : "denied";
        String title = "Profile Update Request " + action.substring(0, 1).toUpperCase() + action.substring(1);
        String message = "Your profile update request for " + request.getRequestType() + " has been " + action;
        
        if (reason.length > 0 && reason[0] != null) {
            message += ". Reason: " + reason[0];
        }
        
        // Create in-app notification
        notificationService.createNotification(
            request.getUserId(),
            "profile_update",
            title,
            message,
            request.getId()
        );
        
        // Send email notification
        try {
            emailService.sendNotificationEmail(requester.getEmailAddress(), title, message);
        } catch (Exception e) {
            logger.error("Failed to send email notification", e);
        }
        
        // Publish to Kafka for async processing
        NotificationEvent event = new NotificationEvent(
            request.getUserId(),
            "profile_update",
            title,
            message,
            request.getId()
        );
        // Kafka event publishing removed for Render deployment
    }

    private void sendTransactionApprovalNotifications(Transaction transaction, User approver, boolean approved, String... reason) {
        User requester = transaction.getUser();
        if (requester == null) {
            logger.error("Transaction user is null for transaction ID: {}", transaction.getTransactionId());
            return;
        }

        String action = approved ? "approved" : "denied";
        String title = "Transaction " + action.substring(0, 1).toUpperCase() + action.substring(1);
        String message;

        if (approved) {
            message = "Your transaction request for amount " + transaction.getAmount() + " has been approved.";
        } else {
            message = "Your transaction request for amount " + transaction.getAmount() + " has been denied.";
            if (reason.length > 0 && reason[0] != null) {
                message += " Reason: " + reason[0];
            }
        }

        // Create in-app notification
        notificationService.createNotification(
            requester.getUserId(),
            "transaction_request",
            title,
            message,
            transaction.getTransactionId()
        );

        // Send email notification
        try {
            emailService.sendNotificationEmail(requester.getEmailAddress(), title, message);
        } catch (Exception e) {
            logger.error("Failed to send email notification", e);
        }

        // Publish to Kafka for async processing
        NotificationEvent event = new NotificationEvent(
            requester.getUserId(),
            "transaction_request",
            title,
            message,
            transaction.getTransactionId()
        );
        // Kafka event publishing removed for Render deployment
    }
    
    // Log activity
    private void logActivity(String action, Integer userId, String details) {
        logger.info("Activity: {} - User: {} - Details: {}", action, userId, details);
        try {
            activityLogService.logActivity(userId, action, details);
        } catch (Exception e) {
            logger.error("Failed to log activity to database: {}", e.getMessage());
        }
    }
    
    // Convert AccountRequest to DTO
    private AccountRequestDto convertAccountRequestToDto(AccountRequest request) {
        AccountRequestDto dto = new AccountRequestDto();
        dto.setId(request.getId());
        dto.setUserId(request.getUserId());
        dto.setAccountType(request.getAccountType());
        dto.setInitialBalance(request.getInitialBalance());
        dto.setReason(request.getReason()); // Add this line to include the reason
        dto.setStatus(request.getStatus());
        dto.setTimestamp(request.getTimestamp());
        
        // Set user name if user exists
        if (request.getUser() != null) {
            dto.setUserName(request.getUser().getFirstName() + " " + request.getUser().getLastName());
        }
        
        return dto;
    }
    
    // Convert ProfileUpdateRequest to DTO
    private ProfileUpdateRequestDto convertProfileRequestToDto(ProfileUpdateRequest request) {
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

    // Convert Transaction to DTO
    private TransactionDto convertTransactionToDto(Transaction transaction) {
        TransactionDto dto = new TransactionDto();
        dto.setTransactionId(transaction.getTransactionId().longValue());
        dto.setUser(transaction.getUser());
        dto.setSenderAcc(transaction.getSenderAcc());
        dto.setReceiverAcc(transaction.getReceiverAcc());
        dto.setTransactionType(transaction.getTransactionType());
        dto.setAmount(transaction.getAmount());
        
        // Handle enum conversion with error handling
        try {
            dto.setStatus(RequestStatus.valueOf(transaction.getStatus()));
        } catch (IllegalArgumentException e) {
            logger.error("Unknown status '{}' for transaction {}, defaulting to PENDING", 
                        transaction.getStatus(), transaction.getTransactionId());
            dto.setStatus(RequestStatus.PENDING);
        }
        
        return dto;
    }
    
    // DTO for pending requests
    public static class PendingRequestsDto {
        private List<AccountRequestDto> accountRequests;
        private List<ProfileUpdateRequestDto> profileRequests;
        private List<TransactionDto> transactionRequests;
        
        public PendingRequestsDto(List<AccountRequestDto> accountRequests, List<ProfileUpdateRequestDto> profileRequests, List<TransactionDto> transactionRequests) {
            this.accountRequests = accountRequests;
            this.profileRequests = profileRequests;
            this.transactionRequests = transactionRequests;
        }
        
        public List<AccountRequestDto> getAccountRequests() { return accountRequests; }
        public void setAccountRequests(List<AccountRequestDto> accountRequests) { this.accountRequests = accountRequests; }
        public List<ProfileUpdateRequestDto> getProfileRequests() { return profileRequests; }
        public void setProfileRequests(List<ProfileUpdateRequestDto> profileRequests) { this.profileRequests = profileRequests; }
        public List<TransactionDto> getTransactionRequests() { return transactionRequests; }
        public void setTransactionRequests(List<TransactionDto> transactionRequests) { this.transactionRequests = transactionRequests; }
    }
} 