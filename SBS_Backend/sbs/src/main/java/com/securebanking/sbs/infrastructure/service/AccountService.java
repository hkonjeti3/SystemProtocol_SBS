package com.securebanking.sbs.infrastructure.service;

import com.securebanking.sbs.shared.dto.AccountDto;
import com.securebanking.sbs.shared.dto.TransactionDto;
import com.securebanking.sbs.shared.enums.ApprovalStatus;
import com.securebanking.sbs.core.exception.DatabaseOperationException;
import com.securebanking.sbs.core.exception.NoAccountsFoundException;
import com.securebanking.sbs.core.exception.ResourceNotFoundException;
import com.securebanking.sbs.modules.customer.model.Account;
import com.securebanking.sbs.modules.customer.model.Transaction;
import com.securebanking.sbs.shared.model.User;
import com.securebanking.sbs.infrastructure.repository.AccountRepo;
import com.securebanking.sbs.infrastructure.repository.TransactionRepo;
import com.securebanking.sbs.infrastructure.repository.UserRepo;
import com.securebanking.sbs.infrastructure.repository.UserRoleRepo;
import com.securebanking.sbs.infrastructure.service.ActivityLogService;
import com.securebanking.sbs.infrastructure.service.NotificationService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AccountService {

    private AccountDto accountDto;

    @Autowired
    private AccountRepo accountRepo;

    @Autowired
    private UserRoleRepo userRoleRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private TransactionRepo transactionRepo;

    @Autowired
    private ActivityLogService activityLogService;

    @Autowired
    private NotificationService notificationService;

    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);

    public AccountDto createAccount(AccountDto accountDto) {
        try {
            logger.info("Creating account with DTO: userId={}, accountType={}, balance={}, status={}", 
                       accountDto.getUserId(), accountDto.getAccountType(), accountDto.getBalance(), accountDto.getStatus());
            
            User user = userRepo.findById(accountDto.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + accountDto.getUserId()));
            
            logger.info("Found user: {}", user.getUsername());

            Account account = new Account();
            account.setAccountNumber(generateUniqueAccountNumber());
            account.setAccountType(accountDto.getAccountType());
            account.setBalance(accountDto.getBalance());
            account.setStatus(accountDto.getStatus());
            account.setUser(user);
            
            logger.info("Account entity created: accountNumber={}, accountType={}, balance={}, status={}", 
                       account.getAccountNumber(), account.getAccountType(), account.getBalance(), account.getStatus());

            Account savedAccount = accountRepo.save(account);
            
            logger.info("Account saved successfully with ID: {}", savedAccount.getAccountId());
            
            return convertToAccountDto(savedAccount);
        } catch (DataAccessException e) {
            logger.error("Database error while creating account", e);
            throw new DatabaseOperationException("Error occurred while saving the account", e);
        } catch (Exception e) {
            logger.error("Failed to create account", e);
            throw new RuntimeException("Failed to create account: " + e.getMessage(), e);
        }
    }

    private String generateUniqueAccountNumber() {
        // Example strategy: Current time in milliseconds + a random 4-digit number
        return String.valueOf(System.currentTimeMillis()) + (int)(Math.random() * 10000);
    }
    public void updateAccount(Long accountId, AccountDto accountDto) {
        // Fetch the existing account
        Account account = accountRepo.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id " + accountId));

        // Update the account details
        updateAccountDetails(account, accountDto);
        // Save the updated account
        accountRepo.save(account);
    }

    public void updateAccountStatus(Long accountId, String status, Integer adminUserId) {
        logger.info("Updating account {} status to: {} by admin user: {}", accountId, status, adminUserId);
        
        Account account = accountRepo.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id " + accountId));

        String previousStatus = account.getStatus();
        account.setStatus(status);
        accountRepo.save(account);
        
        logger.info("Account {} status updated successfully to: {}", accountId, status);
        
        // Log activity for admin
        try {
            String action = status.equals("Active") ? "Account Activated" : "Account Deactivated";
            String description = String.format("Account #%s %s by admin", account.getAccountNumber(), 
                status.equals("Active") ? "activated" : "deactivated");
            
            // Log activity for the account owner (customer)
            activityLogService.logActivity(
                account.getUser().getUserId(),
                action,
                description,
                String.format("Account status changed from %s to %s", previousStatus, status)
            );
            
            // Log activity for the admin who performed the action
            String adminAction = status.equals("Active") ? "Account Activation" : "Account Deactivation";
            String adminDescription = String.format("Account #%s %s for user %s", 
                account.getAccountNumber(), 
                status.equals("Active") ? "activated" : "deactivated",
                account.getUser().getUsername());
            
            activityLogService.logActivity(
                adminUserId,
                adminAction,
                adminDescription,
                String.format("Account %s status changed from %s to %s for user %s", 
                    account.getAccountNumber(), previousStatus, status, account.getUser().getUsername())
            );
            
            // Send notification to account owner
            String notificationTitle = status.equals("Active") ? "Account Activated" : "Account Deactivated";
            String notificationMessage = String.format("Your account #%s has been %s by the administrator.", 
                account.getAccountNumber(), status.equals("Active") ? "activated" : "deactivated");
            
            notificationService.createNotification(
                account.getUser().getUserId(),
                "ACCOUNT_STATUS_CHANGE",
                notificationTitle,
                notificationMessage,
                accountId.intValue()
            );
            
            logger.info("Activity logged and notification sent for account {} status change by admin {}", accountId, adminUserId);
        } catch (Exception e) {
            logger.error("Failed to log activity or send notification for account {} status change: {}", accountId, e.getMessage());
        }
    }
    public List<AccountDto> getAllAccountsForUser(Integer userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        List<Account> accounts = accountRepo.findByUserId(userId);
        
        // Return empty list instead of throwing exception when no accounts found
        return accounts.stream()
                .map(this::convertToAccountDto)
                .collect(Collectors.toList());
    }

    public List<AccountDto> getAllAccounts() {
        try {
            List<Account> allAccounts = accountRepo.findAll();
            logger.info("Retrieved {} accounts from database", allAccounts.size());
            
            return allAccounts.stream()
                    .map(this::convertToAccountDto)
                    .collect(Collectors.toList());
        } catch (DataAccessException e) {
            logger.error("Database error while retrieving all accounts", e);
            throw new DatabaseOperationException("Error occurred while retrieving accounts", e);
        } catch (Exception e) {
            logger.error("Failed to retrieve all accounts", e);
            throw new RuntimeException("Failed to retrieve accounts: " + e.getMessage(), e);
        }
    }

//    public void requestTransfer(Long fromAccountId, Long toAccountId, String transactionType, String amount) {
//        Transaction transaction = new Transaction();
//        transaction.setSenderAcc(accountRepo.findById(fromAccountId)
//                .orElseThrow(() -> new ResourceNotFoundException("From Account not found with id " + fromAccountId)));
//        transaction.setReceiverAcc(accountRepo.findById(toAccountId)
//                .orElseThrow(() -> new ResourceNotFoundException("To Account not found with id " + toAccountId)));
//        transaction.setAmount(amount);
//        transaction.setStatus("Pending");
//
//        transactionRepo.save(transaction);
//    }


    public void transferFunds(Transaction transaction) {
        BigDecimal amount = new BigDecimal(transaction.getAmount());

        Account senderAccount = transaction.getSenderAcc();
        Account receiverAccount = transaction.getReceiverAcc();

        BigDecimal senderBalance = new BigDecimal(senderAccount.getBalance());
        if (senderBalance.compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient funds in the sender account");
        }

        BigDecimal newSenderBalance = senderBalance.subtract(amount);
        BigDecimal newReceiverBalance = new BigDecimal(receiverAccount.getBalance()).add(amount);

        senderAccount.setBalance(newSenderBalance.toString());
        receiverAccount.setBalance(newReceiverBalance.toString());
        accountRepo.save(senderAccount);
        accountRepo.save(receiverAccount);
        transaction.setStatus(ApprovalStatus.COMPLETED.toString()); // use the appropriate status
        transactionRepo.save(transaction);
    }

    @Transactional
    public void executeTransaction(Transaction transaction) {
        Account senderAccount = transaction.getSenderAcc();
        Account receiverAccount = transaction.getReceiverAcc();
        BigDecimal amount = new BigDecimal(transaction.getAmount());
        String transactionType = transaction.getTransactionType();

        if ("CREDIT".equalsIgnoreCase(transactionType)) {
            // CREDIT transaction: Money flows INTO receiver account
            // Sender account balance decreases, receiver account balance increases
            
            // Ensure sender has sufficient funds
            BigDecimal senderBalance = new BigDecimal(senderAccount.getBalance());
            if (senderBalance.compareTo(amount) < 0) {
                throw new RuntimeException("Insufficient funds in sender account for CREDIT transaction");
            }

            // Debit from sender (decrease balance)
            BigDecimal newSenderBalance = senderBalance.subtract(amount);
            senderAccount.setBalance(newSenderBalance.toPlainString());
            accountRepo.save(senderAccount);

            // Credit to receiver (increase balance)
            BigDecimal receiverBalance = new BigDecimal(receiverAccount.getBalance());
            BigDecimal newReceiverBalance = receiverBalance.add(amount);
            receiverAccount.setBalance(newReceiverBalance.toPlainString());
            accountRepo.save(receiverAccount);

        } else if ("DEBIT".equalsIgnoreCase(transactionType)) {
            // DEBIT transaction: Money flows OUT OF sender account
            // Sender account balance decreases, receiver account balance increases
            
            // Ensure sender has sufficient funds
            BigDecimal senderBalance = new BigDecimal(senderAccount.getBalance());
            if (senderBalance.compareTo(amount) < 0) {
                throw new RuntimeException("Insufficient funds in sender account for DEBIT transaction");
            }

            // Debit from sender (decrease balance)
            BigDecimal newSenderBalance = senderBalance.subtract(amount);
            senderAccount.setBalance(newSenderBalance.toPlainString());
            accountRepo.save(senderAccount);

            // Credit to receiver (increase balance)
            BigDecimal receiverBalance = new BigDecimal(receiverAccount.getBalance());
            BigDecimal newReceiverBalance = receiverBalance.add(amount);
            receiverAccount.setBalance(newReceiverBalance.toPlainString());
            accountRepo.save(receiverAccount);

        } else {
            // Default behavior for other transaction types (e.g., TRANSFER_FUNDS)
            // Ensure sender has sufficient funds
            BigDecimal senderBalance = new BigDecimal(senderAccount.getBalance());
            if (senderBalance.compareTo(amount) < 0) {
                throw new RuntimeException("Insufficient funds");
            }

            // Debit from sender
            BigDecimal newSenderBalance = senderBalance.subtract(amount);
            senderAccount.setBalance(newSenderBalance.toPlainString());
            accountRepo.save(senderAccount);

            // Credit to receiver
            BigDecimal receiverBalance = new BigDecimal(receiverAccount.getBalance());
            BigDecimal newReceiverBalance = receiverBalance.add(amount);
            receiverAccount.setBalance(newReceiverBalance.toPlainString());
            accountRepo.save(receiverAccount);
        }

        // Update transaction status
        transaction.setStatus(ApprovalStatus.COMPLETED.toString());
        transactionRepo.save(transaction);
    }

    @Transactional
    public void delete(Transaction transaction) {
        Account accountToDelete = transaction.getSenderAcc(); // Assuming the account to delete is the sender

        // Check if the account has a zero balance before proceeding
        BigDecimal balance = new BigDecimal(accountToDelete.getBalance());
        if (balance.compareTo(BigDecimal.ZERO) != 0) {
            throw new RuntimeException("Account balance must be zero to delete");
        }
        // Optionally, check for no pending transactions
        // This part depends on how you track transactions in your system
//        boolean hasPendingTransactions = transactionRepo.findByAccountAndStatus(
//                accountToDelete, "PENDING").stream().findAny().isPresent();
//        if (hasPendingTransactions) {
//            throw new RuntimeException("Account has pending transactions and cannot be deleted");
//        }
        // Proceed with account deletion
        accountRepo.delete(accountToDelete);

        // Optionally, update the transaction to reflect the account deletion
        transaction.setStatus(ApprovalStatus.DELETED.toString());
        transactionRepo.save(transaction);
    }

//    @Transactional
//    public void credit(Transaction transaction) {
//        Account receiverAccount = transaction.getReceiverAcc();
//        BigDecimal amount = new BigDecimal(transaction.getAmount());
//
//        // Calculate new balance
//        BigDecimal newReceiverBalance = new BigDecimal(receiverAccount.getBalance()).add(amount);
//
//        // Convert the BigDecimal balance back to String and update the account
//        receiverAccount.setBalance(newReceiverBalance.toPlainString());
//
//        // Save the updated account
//        accountRepo.save(receiverAccount);
//
//        // Optionally, update transaction status or perform additional actions
//        transaction.setStatus(ApprovalStatus.COMPLETED.toString());
//        transactionRepo.save(transaction);
//    }
//
//    @Transactional
//    public void debit(Transaction transaction) {
//        Account senderAccount = transaction.getSenderAcc();
//        BigDecimal amount = new BigDecimal(transaction.getAmount());
//
//        // Ensure there are sufficient funds
//        BigDecimal senderBalance = new BigDecimal(senderAccount.getBalance());
//        if (senderBalance.compareTo(amount) < 0) {
//            throw new RuntimeException("Insufficient funds");
//        }
//        // Calculate new balance
//        BigDecimal newSenderBalance = senderBalance.subtract(amount);
//
//        // Convert the BigDecimal balance back to String and update the account
//        senderAccount.setBalance(newSenderBalance.toPlainString());
//
//        // Save the updated account
//        accountRepo.save(senderAccount);
//
//        // Optionally, update transaction status or perform additional actions
//        transaction.setStatus(ApprovalStatus.COMPLETED.toString());
//        transactionRepo.save(transaction);
//    }

    public AccountDto convertToAccountDto(Account account) {
        AccountDto dto = new AccountDto();
        dto.setAccountId(account.getAccountId());
        dto.setUserId(account.getUser().getId()); // This assumes a getter exists in the User class
        dto.setAccountNumber(account.getAccountNumber());
        dto.setAccountType(account.getAccountType());
        dto.setBalance(account.getBalance());
        dto.setStatus(account.getStatus());
        return dto;
    }

    private void updateAccountDetails(Account account, AccountDto accountDto){
        account.setAccountType(accountDto.getAccountType());
        account.setBalance(accountDto.getBalance());
        account.setStatus(accountDto.getStatus());
    }
}
