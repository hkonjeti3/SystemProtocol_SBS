package com.securebanking.sbs.shared.dto;

import java.time.LocalDateTime;

public class AccountDeletionRequestDto {
    private Integer id;
    private Integer userId;
    private Long accountId;
    private String accountNumber;
    private String accountType;
    private String currentBalance;
    private String reason;
    private String status;
    private LocalDateTime timestamp;
    private Integer approverId;
    private LocalDateTime approvalDate;
    private String rejectionReason;
    private String username; // For display purposes

    // Constructors
    public AccountDeletionRequestDto() {}

    public AccountDeletionRequestDto(Integer id, Integer userId, Long accountId, String accountNumber, 
                                   String accountType, String currentBalance, String reason, String status, 
                                   LocalDateTime timestamp) {
        this.id = id;
        this.userId = userId;
        this.accountId = accountId;
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.currentBalance = currentBalance;
        this.reason = reason;
        this.status = status;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(String currentBalance) {
        this.currentBalance = currentBalance;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getApproverId() {
        return approverId;
    }

    public void setApproverId(Integer approverId) {
        this.approverId = approverId;
    }

    public LocalDateTime getApprovalDate() {
        return approvalDate;
    }

    public void setApprovalDate(LocalDateTime approvalDate) {
        this.approvalDate = approvalDate;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
