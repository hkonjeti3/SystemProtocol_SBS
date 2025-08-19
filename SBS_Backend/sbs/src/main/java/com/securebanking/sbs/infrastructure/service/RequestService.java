package com.securebanking.sbs.infrastructure.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securebanking.sbs.shared.dto.TransactionAuthorizationDto;
import com.securebanking.sbs.shared.dto.TransactionDto;
import com.securebanking.sbs.shared.dto.UserDto;
import com.securebanking.sbs.shared.dto.UserProfileUpdateRequestDto;
import com.securebanking.sbs.shared.enums.ApprovalStatus;
import com.securebanking.sbs.shared.enums.RequestStatus;
import com.securebanking.sbs.infrastructure.iservice.IRequest;
import com.securebanking.sbs.shared.model.*;
import com.securebanking.sbs.infrastructure.repository.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.securebanking.sbs.infrastructure.service.UserService;
import com.securebanking.sbs.modules.internal_user.model.UserProfileUpdateRequest;
import com.securebanking.sbs.modules.customer.model.Transaction;
import com.securebanking.sbs.modules.customer.model.Account;
import com.securebanking.sbs.modules.customer.model.TransactionAuthorization;

import java.math.BigDecimal;

@Service
public class RequestService implements IRequest {

    @Autowired
    UserRepo userRepo;

    @Autowired
    UserService userService;

    @Autowired
    TransactionAuthorizationRepo transactionAuthorizationRepo;

    @Autowired
    AccountRepo accountRepo;

    @Autowired
    TransactionRepo transactionRepo;

    @Autowired
    AccountService accountService;

    public TransactionAuthorizationDto getAllTranctionRequests(TransactionAuthorizationDto transactionAuthorizationDto) {

        return transactionAuthorizationDto;
    }
//    transactionId,user,Acc-recAcc,tranctiontype,amount, status

    @Autowired
    private UserProfileUpdateRequestRepo userProfileUpdateRequestRepo;
    
    @Autowired
    private ActivityLogService activityLogService;
    
    @Autowired
    private NotificationService notificationService;


    public List<UserProfileUpdateRequestDto> getPendingUpdateRequests() {
        List<UserProfileUpdateRequest> userProfileUpdateRequest = userProfileUpdateRequestRepo.findByStatus(RequestStatus.PENDING);
        List<UserProfileUpdateRequestDto> userProfileUpdateRequestDto= new ArrayList<>();
        userProfileUpdateRequest.forEach(req -> {
            UserProfileUpdateRequestDto singleReq = new UserProfileUpdateRequestDto();
            BeanUtils.copyProperties(req,singleReq);
            User user = req.getUser();
            UserRole userRole = user.getRole();
            Set<User> empty = new HashSet<>();
            userRole.setUsers(empty);
            user.setRole(userRole);
            req.setUser(user);
            userProfileUpdateRequestDto.add(singleReq);
        });
        return userProfileUpdateRequestDto;
    }


    public UserProfileUpdateRequestDto createUpdateProfileRequest(UserProfileUpdateRequestDto userProfileUpdateRequestDto) {
        UserProfileUpdateRequest userProfileUpdateRequest = new UserProfileUpdateRequest();
        if(userProfileUpdateRequestDto.getId() != null){
            userProfileUpdateRequest = userProfileUpdateRequestRepo.findById(userProfileUpdateRequestDto.getId()).get();
            if(userProfileUpdateRequest.getApprovalStatus() == ApprovalStatus.PENDING && userProfileUpdateRequestDto.getApprovalStatus()==ApprovalStatus.APPROVED){
                userProfileUpdateRequest.setApprovalStatus(ApprovalStatus.APPROVED);
                userProfileUpdateRequest.setApprover(userProfileUpdateRequestDto.getApprover());
                userProfileUpdateRequest.setApprovalDate(LocalDateTime.now());

                ObjectMapper mapper = new ObjectMapper();
                UserDto updatedUserData = null;
                try {
                    updatedUserData = mapper.readValue(userProfileUpdateRequest.getUpdateData(), UserDto.class);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                HttpStatus userUpdateStatus= userService.createOrUpdateUser(updatedUserData);
                if(userUpdateStatus.equals(HttpStatus.OK)){
                    userProfileUpdateRequest.setStatus(RequestStatus.UPDATED);
                }
                else{
                    userProfileUpdateRequest.setStatus(RequestStatus.PENDING);
                }

                userProfileUpdateRequest.setLastModifiedtime(LocalDateTime.now());
                userProfileUpdateRequest = userProfileUpdateRequestRepo.save(userProfileUpdateRequest);
            }
            else if (userProfileUpdateRequest.getApprovalStatus() == ApprovalStatus.PENDING && userProfileUpdateRequestDto.getApprovalStatus()==ApprovalStatus.REJECTED){
                userProfileUpdateRequest.setApprovalStatus(ApprovalStatus.REJECTED);
                userProfileUpdateRequest.setApprovalDate(LocalDateTime.now());
                userProfileUpdateRequest.setStatus(RequestStatus.REJECTED);
                userProfileUpdateRequest.setLastModifiedtime(LocalDateTime.now());
                userProfileUpdateRequest = userProfileUpdateRequestRepo.save(userProfileUpdateRequest);
            }


        }
        else{
            BeanUtils.copyProperties(userProfileUpdateRequestDto,userProfileUpdateRequest);
            userProfileUpdateRequest.setUser(userRepo.findById(userProfileUpdateRequestDto.getUser().getUserId()).get());
            userProfileUpdateRequest.setStatus(RequestStatus.CREATED);
            userProfileUpdateRequest.setApprovalStatus(ApprovalStatus.PENDING);
            userProfileUpdateRequest.setRequestDate(LocalDateTime.now());
            userProfileUpdateRequest = userProfileUpdateRequestRepo.save(userProfileUpdateRequest);
        }

        BeanUtils.copyProperties(userProfileUpdateRequest,userProfileUpdateRequestDto);
        return userProfileUpdateRequestDto;
    }

//    public UserProfileUpdateRequestDto ApproveProfileRequest(UserProfileUpdateRequestDto userProfileUpdateRequestDto) {
//        UserProfileUpdateRequest userProfileUpdateRequest = userProfileUpdateRequestRepo.findById(userProfileUpdateRequestDto.getId()).get();
//        userProfileUpdateRequest.setApprovalStatus(ApprovalStatus.APPROVED);
//        User aprrover= userRepo.findById(userProfileUpdateRequestDto.getApprover().getUserId()).get();
//        userProfileUpdateRequest.setApprover(aprrover);
//        userProfileUpdateRequest.setApprovalDate(LocalDateTime.now());
//        userProfileUpdateRequest.setLastModifiedtime(LocalDateTime.now());
//        userProfileUpdateRequest = userProfileUpdateRequestRepo.save(userProfileUpdateRequest);
//        BeanUtils.copyProperties(userProfileUpdateRequest,userProfileUpdateRequestDto);
//    return userProfileUpdateRequestDto;
//    }
    public TransactionDto createTransactionRequest(TransactionDto transactionDto) {
        Transaction transaction = new Transaction();
        if (transactionDto.getTransactionId() == null) {
            try {
                //creation of request
                //GET SENDER,RECEIVER ACCOUNT DETAILS,user,transaction type,amount
                User user = userRepo.findById(transactionDto.getUser().getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found with ID: " + transactionDto.getUser().getUserId()));

                Account senderAcc = accountRepo.findbyaccountnumber(transactionDto.getSenderAccountNumber());
                if (senderAcc == null) {
                    throw new RuntimeException("Sender account not found with number: " + transactionDto.getSenderAccountNumber());
                }
                
                Account receiverAcc = accountRepo.findbyaccountnumber(transactionDto.getReceiverAccountNumber());
                if (receiverAcc == null) {
                    throw new RuntimeException("Receiver account not found with number: " + transactionDto.getReceiverAccountNumber());
                }
                
                // VALIDATION: Check if both accounts are active
                if (!"ACTIVE".equalsIgnoreCase(senderAcc.getStatus())) {
                    throw new RuntimeException("Sender account is not active. Current status: " + senderAcc.getStatus());
                }
                
                if (!"ACTIVE".equalsIgnoreCase(receiverAcc.getStatus())) {
                    throw new RuntimeException("Receiver account is not active. Current status: " + receiverAcc.getStatus());
                }
                
                // VALIDATION: Check if sender account has sufficient balance for DEBIT transactions
                if ("DEBIT".equalsIgnoreCase(transactionDto.getTransactionType())) {
                    try {
                        BigDecimal senderBalance = new BigDecimal(senderAcc.getBalance());
                        BigDecimal transactionAmount = new BigDecimal(transactionDto.getAmount());
                        
                        if (senderBalance.compareTo(transactionAmount) < 0) {
                            throw new RuntimeException("Insufficient funds in sender account. Available: $" + senderBalance + ", Required: $" + transactionAmount);
                        }
                    } catch (NumberFormatException e) {
                        throw new RuntimeException("Invalid amount format: " + transactionDto.getAmount());
                    }
                }
                
                // VALIDATION: Check if sender account belongs to the requesting user
                if (!senderAcc.getUser().getUserId().equals(user.getUserId())) {
                    throw new RuntimeException("Sender account does not belong to the requesting user");
                }
                
                transaction.setSenderAcc(senderAcc);
                transaction.setReceiverAcc(receiverAcc);
                transaction.setUser(user);
                transaction.setTransactionType(transactionDto.getTransactionType());
                transaction.setAmount(transactionDto.getAmount());
                transaction.setStatus(RequestStatus.CREATED.toString());
                transaction.setCreatedtime(LocalDateTime.now());

                transaction = transactionRepo.save(transaction);
                if (transaction.getTransactionId() == null){
                    throw new RuntimeException("Error saving transaction");
                }
                
                TransactionAuthorization transactionAuthorization = new TransactionAuthorization();
                transactionAuthorization.setTransaction(transaction);
                transactionAuthorization.setStatus(ApprovalStatus.PENDING.toString());
                transactionAuthorization.setCreatedtime(LocalDateTime.now());
                transactionAuthorization = transactionAuthorizationRepo.save(transactionAuthorization);

                if (transactionAuthorization.getAuthorizationId() == null){
                    throw new RuntimeException("Error creating transaction request");
                }
                
                transaction.setStatus(RequestStatus.PENDING.toString());
                transaction.setLastModifiedtime(LocalDateTime.now());
                transaction = transactionRepo.save(transaction);
                
                // ACTIVITY LOGGING: Log the transaction request creation
                activityLogService.logActivity(
                    user.getUserId(),
                    "Transaction Request Created",
                    "Transaction request created for " + transactionDto.getTransactionType() + " operation",
                    "Amount: $" + transactionDto.getAmount() + ", Sender: " + senderAcc.getAccountNumber() + 
                    ", Receiver: " + receiverAcc.getAccountNumber() + ", Status: PENDING"
                );
                
                BeanUtils.copyProperties(transaction, transactionDto);
                
            } catch (Exception e) {
                throw new RuntimeException("Failed to create transaction request: " + e.getMessage(), e);
            }
        }
        return transactionDto;
    }

    public TransactionDto createDeleteTransactionRequest(TransactionDto transactionDto) {
        Transaction transaction =new Transaction();
        if (transactionDto.getTransactionId() == null) {
            //creation of request
            //GET SENDER,RECEIVER ACCOUNT DETAILS,user,transaction type,amount
            User user = userRepo.findById(transactionDto.getUser().getUserId()).get();

            Account senderAcc = accountRepo.findbyaccountnumber(transactionDto.getSenderAcc().getAccountNumber());
            //Account receiverAcc = accountRepo.findbyaccountnumber(transactionDto.getReceiverAcc().getAccountNumber());
            transaction.setSenderAcc(senderAcc);
            //transaction.setReceiverAcc(receiverAcc);
            transaction.setUser(user);
            transaction.setTransactionType(transactionDto.getTransactionType());
            //transaction.setAmount(transactionDto.getAmount());
            transaction.setStatus(RequestStatus.CREATED.toString());
            transaction.setCreatedtime(LocalDateTime.now());

            transaction=transactionRepo.save(transaction);
//        BeanUtils.copyProperties(transaction,transactionDto);
            if (transaction.getTransactionId() == null){
                throw new RuntimeException("Error saving transaction");
            }
            TransactionAuthorization transactionAuthorization = new TransactionAuthorization();
            transactionAuthorization.setTransaction(transaction);
            transactionAuthorization.setStatus(ApprovalStatus.PENDING.toString());
            transactionAuthorization.setCreatedtime(LocalDateTime.now());
            transactionAuthorization=transactionAuthorizationRepo.save(transactionAuthorization);

            if (transactionAuthorization.getAuthorizationId() == null){
                throw new RuntimeException("Error creating transaction request");
            }
            transaction.setStatus(RequestStatus.PENDING.toString());
            transaction.setLastModifiedtime(LocalDateTime.now());
            transaction=transactionRepo.save(transaction);
            BeanUtils.copyProperties(transaction,transactionDto);
        }
        return transactionDto;
    }

    public TransactionAuthorizationDto approveTransactionRequest(TransactionAuthorizationDto transactionAuthorizationDto) {
        TransactionAuthorization transactionAuthorization = new TransactionAuthorization();
        transactionAuthorization=transactionAuthorizationRepo.findById(transactionAuthorizationDto.getAuthorizationId()).get();
        Transaction transaction=transactionRepo.findById(transactionAuthorization.getTransaction().getTransactionId()).get();
        if (transaction.getStatus().equals(RequestStatus.PENDING.toString())  && transactionAuthorization.getStatus().equals(ApprovalStatus.PENDING.toString())){
            User approver = userRepo.findByUsername(transactionAuthorizationDto.getUser().getUsername());
            if (approver == null) {
                throw new RuntimeException("Approver user not found");
            }
            transactionAuthorization.setTransaction(transaction);
            transactionAuthorization.setStatus(ApprovalStatus.APPROVED.toString());
            transactionAuthorization.setLastModifiedtime(LocalDateTime.now());
            transactionAuthorization.setUser(approver);
            transactionAuthorization=transactionAuthorizationRepo.save(transactionAuthorization);
            switch (transaction.getTransactionType()) {
                case "TRANSFER_FUNDS":
                    accountService.transferFunds(transaction);
                    break;
                case "DEBIT":
                case "CREDIT": // Both "DEBIT" and "CREDIT" will execute this code
                    accountService.executeTransaction(transaction);
                    break;
                case "DELETE":
                    accountService.delete(transaction);
                    // Add more cases for other transaction types
                default:
                    throw new RuntimeException("Unsupported transaction type");
            }
            
            // Log activity for transaction approval (Admin side)
            activityLogService.logActivity(
                approver.getUserId(),
                "Transaction Approved",
                "Transaction " + transaction.getTransactionId() + " approved by admin",
                "Transaction Type: " + transaction.getTransactionType() + ", Amount: $" + transaction.getAmount() + 
                ", Customer: " + transaction.getUser().getUsername()
            );
            
            // Log activity for transaction approval (Customer side)
            activityLogService.logActivity(
                transaction.getUser().getUserId(),
                "Transaction Approved",
                "Your " + transaction.getTransactionType() + " transaction has been approved",
                "Transaction ID: " + transaction.getTransactionId() + ", Amount: $" + transaction.getAmount() + 
                ", Approved by: " + approver.getUsername()
            );
            
            // Create notification for customer
            notificationService.createNotification(
                transaction.getUser().getUserId(),
                "transaction_approved",
                "Transaction Approved",
                "Your " + transaction.getTransactionType() + " transaction of $" + transaction.getAmount() + " has been approved and completed.",
                transaction.getTransactionId()
            );
            
            BeanUtils.copyProperties(transactionAuthorizationDto,transactionAuthorization);
        }
        else{
            throw new RuntimeException( "check the request again");
        }

        return transactionAuthorizationDto;
    }

    public TransactionAuthorizationDto rejectTransactionRequest(TransactionAuthorizationDto transactionAuthorizationDto) {

        TransactionAuthorization transactionAuthorization = new TransactionAuthorization();
        transactionAuthorization=transactionAuthorizationRepo.findById(transactionAuthorizationDto.getAuthorizationId()).get();
        Transaction transaction=transactionRepo.findById(transactionAuthorization.getTransaction().getTransactionId()).get();
        if (transaction.getStatus().equals(RequestStatus.PENDING.toString())  && transactionAuthorization.getStatus().equals(ApprovalStatus.PENDING.toString())){
            User approver = userRepo.findByUsername(transactionAuthorizationDto.getUser().getUsername());
            transactionAuthorization.setTransaction(transaction);
            transactionAuthorization.setStatus(ApprovalStatus.REJECTED.toString());
            transactionAuthorization.setLastModifiedtime(LocalDateTime.now());
            transactionAuthorization.setUser(approver);
            transactionAuthorization=transactionAuthorizationRepo.save(transactionAuthorization);
            
            // Log activity for transaction rejection (Admin side)
            activityLogService.logActivity(
                approver.getUserId(),
                "Transaction Rejected",
                "Transaction " + transaction.getTransactionId() + " rejected by admin",
                "Transaction Type: " + transaction.getTransactionType() + ", Amount: $" + transaction.getAmount() + 
                ", Customer: " + transaction.getUser().getUsername()
            );
            
            // Log activity for transaction rejection (Customer side)
            activityLogService.logActivity(
                transaction.getUser().getUserId(),
                "Transaction Rejected",
                "Your " + transaction.getTransactionType() + " transaction has been rejected",
                "Transaction ID: " + transaction.getTransactionId() + ", Amount: $" + transaction.getAmount() + 
                ", Rejected by: " + approver.getUsername()
            );
            
            // Create notification for customer
            notificationService.createNotification(
                transaction.getUser().getUserId(),
                "transaction_rejected",
                "Transaction Rejected",
                "Your " + transaction.getTransactionType() + " transaction of $" + transaction.getAmount() + " has been rejected.",
                transaction.getTransactionId()
            );
            
            BeanUtils.copyProperties(transactionAuthorizationDto,transactionAuthorization);
        }
        else{
            throw new RuntimeException( "check the request again");
        }

        return transactionAuthorizationDto;
    }
}

