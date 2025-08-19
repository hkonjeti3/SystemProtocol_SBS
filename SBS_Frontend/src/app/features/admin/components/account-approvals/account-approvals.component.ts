import { Component, OnInit } from '@angular/core';
import { AccountService, AccountRequest } from '../../../../core/services/account.service';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../../environments/environment';

@Component({
  selector: 'app-account-approvals',
  templateUrl: './account-approvals.component.html',
  styleUrls: ['./account-approvals.component.css']
})
export class AccountApprovalsComponent implements OnInit {
  pendingAccountRequests: AccountRequest[] = [];
  pendingDeletionRequests: any[] = [];
  loading: boolean = false;
  loadingDeletions: boolean = false;
  error: string = '';
  showDenyModal: boolean = false;
  showDeletionDenyModal: boolean = false;
  denyReason: string = '';
  deletionDenyReason: string = '';
  selectedRequestId: number | null = null;
  selectedRequestType: string = '';
  selectedDeletionRequestId: number | null = null;
  deletionError: string = '';

  constructor(
    private accountService: AccountService,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    this.loadPendingRequests();
    this.loadPendingDeletionRequests();
  }

  loadPendingRequests() {
    this.loading = true;
    this.error = '';

    this.accountService.getPendingAccountRequests().subscribe({
      next: (response) => {
        this.pendingAccountRequests = response || [];
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading pending account requests:', error);
        this.error = 'Failed to load pending account requests';
        this.loading = false;
      }
    });
  }

  loadPendingDeletionRequests() {
    this.loadingDeletions = true;

    this.http.get<any[]>(`${environment.apiUrl}/account-deletion/pending`).subscribe({
      next: (response) => {
        this.pendingDeletionRequests = response || [];
        this.loadingDeletions = false;
        console.log('Pending deletion requests loaded:', response);
      },
      error: (error) => {
        console.error('Error loading pending deletion requests:', error);
        this.loadingDeletions = false;
      }
    });
  }

  // Account Creation Request Methods
  approveAccountRequest(requestId: number) {
    this.accountService.approveAccountRequest(requestId).subscribe({
      next: (response) => {
        console.log('Account request approved:', response);
        this.loadPendingRequests(); // Reload the list
      },
      error: (error) => {
        console.error('Error approving account request:', error);
        this.error = 'Failed to approve account request';
      }
    });
  }

  // Account Deletion Request Methods
  approveDeletionRequest(requestId: number) {
    if (confirm('Are you sure you want to approve this account deletion request? This action cannot be undone.')) {
      this.http.post<any>(`${environment.apiUrl}/account-deletion/approve/${requestId}`, {}).subscribe({
        next: (response) => {
          console.log('Deletion request approved:', response);
          alert('Account deletion request approved successfully!');
          this.loadPendingDeletionRequests(); // Refresh the list
        },
        error: (error) => {
          console.error('Error approving deletion request:', error);
          const errorMessage = error.error?.error || error.error?.message || 'Unknown error';
          alert('Error approving deletion request: ' + errorMessage);
        }
      });
    }
  }

  rejectDeletionRequest(requestId: number) {
    this.selectedDeletionRequestId = requestId;
    this.deletionDenyReason = '';
    this.deletionError = '';
    this.showDeletionDenyModal = true;
  }

  openDeletionDenyModal(requestId: number) {
    this.selectedDeletionRequestId = requestId;
    this.deletionDenyReason = '';
    this.deletionError = '';
    this.showDeletionDenyModal = true;
  }

  closeDeletionDenyModal() {
    this.showDeletionDenyModal = false;
    this.selectedDeletionRequestId = null;
    this.deletionDenyReason = '';
    this.deletionError = '';
  }

  confirmDeletionDeny() {
    if (!this.selectedDeletionRequestId) return;
    
    if (!this.deletionDenyReason.trim()) {
      this.deletionError = 'Please provide a reason for denial';
      return;
    }

    this.http.post<any>(`${environment.apiUrl}/account-deletion/reject/${this.selectedDeletionRequestId}`, {
      reason: this.deletionDenyReason.trim()
    }).subscribe({
      next: (response) => {
        console.log('Deletion request rejected successfully:', response);
        alert('Account deletion request rejected successfully!');
        this.closeDeletionDenyModal();
        this.loadPendingDeletionRequests(); // Refresh the list
      },
      error: (error) => {
        console.error('Error rejecting deletion request:', error);
        const errorMessage = error.error?.error || error.error?.message || 'Unknown error';
        this.deletionError = 'Error rejecting deletion request: ' + errorMessage;
      }
    });
  }

  // Common Methods
  denyRequestWithReason(requestId: number, requestType: string) {
    this.selectedRequestId = requestId;
    this.selectedRequestType = requestType;
    this.denyReason = '';
    this.showDenyModal = true;
  }

  closeDenyModal() {
    this.showDenyModal = false;
    this.selectedRequestId = null;
    this.selectedRequestType = '';
    this.denyReason = '';
  }

  confirmDeny() {
    if (!this.selectedRequestId || !this.selectedRequestType) return;
    
    if (!this.denyReason.trim()) {
      this.error = 'Please provide a reason for denial';
      return;
    }

    if (this.selectedRequestType === 'creation') {
      this.accountService.rejectAccountRequest(this.selectedRequestId, this.denyReason).subscribe({
        next: (response) => {
          console.log('Account request denied:', response);
          this.closeDenyModal();
          this.loadPendingRequests(); // Reload the list
        },
        error: (error) => {
          console.error('Error denying account request:', error);
          this.error = 'Failed to deny account request';
        }
      });
    }
    // For deletion requests, we use the separate reject method above
  }

  confirmDenyRequest() {
    this.confirmDeny();
  }

  denyRequest(requestId: number, reason: string) {
    if (!reason.trim()) {
      this.error = 'Please provide a reason for denial';
      return;
    }

    this.accountService.rejectAccountRequest(requestId, reason).subscribe({
      next: (response) => {
        console.log('Account request denied:', response);
        this.loadPendingRequests(); // Reload the list
      },
      error: (error) => {
        console.error('Error denying account request:', error);
        this.error = 'Failed to deny account request';
      }
    });
  }

  // Helper Methods
  getStatusClass(status: string): string {
    switch (status?.toLowerCase()) {
      case 'pending':
        return 'status-pending';
      case 'approved':
        return 'status-approved';
      case 'rejected':
        return 'status-rejected';
      default:
        return 'status-unknown';
    }
  }

  formatDate(dateString: string): string {
    if (!dateString) return 'N/A';
    try {
      return new Date(dateString).toLocaleString();
    } catch (error) {
      return 'Invalid Date';
    }
  }

  refreshAll() {
    this.loadPendingRequests();
    this.loadPendingDeletionRequests();
  }
} 