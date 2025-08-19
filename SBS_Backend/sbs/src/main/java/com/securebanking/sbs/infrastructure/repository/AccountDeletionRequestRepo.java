package com.securebanking.sbs.infrastructure.repository;

import com.securebanking.sbs.modules.internal_user.model.AccountDeletionRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountDeletionRequestRepo extends JpaRepository<AccountDeletionRequest, Integer> {
    
    // Find all pending deletion requests
    List<AccountDeletionRequest> findByStatusOrderByTimestampDesc(String status);
    
    // Find deletion requests by user ID
    List<AccountDeletionRequest> findByUserIdOrderByTimestampDesc(Integer userId);
    
    // Find deletion requests by account ID
    List<AccountDeletionRequest> findByAccountIdOrderByTimestampDesc(Long accountId);
    
    // Find pending deletion requests for a specific user
    List<AccountDeletionRequest> findByUserIdAndStatusOrderByTimestampDesc(Integer userId, String status);
    
    // Check if there's already a pending deletion request for an account
    boolean existsByAccountIdAndStatus(Long accountId, String status);
    
    // Find deletion requests by status with pagination
    @Query("SELECT r FROM AccountDeletionRequest r WHERE r.status = :status ORDER BY r.timestamp DESC")
    List<AccountDeletionRequest> findPendingDeletionRequests(@Param("status") String status);
    
    // Find deletion requests by user ID and status
    @Query("SELECT r FROM AccountDeletionRequest r WHERE r.userId = :userId AND r.status = :status ORDER BY r.timestamp DESC")
    List<AccountDeletionRequest> findUserDeletionRequests(@Param("userId") Integer userId, @Param("status") String status);
}
