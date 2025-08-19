import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../../environments/environment';

@Component({
  selector: 'app-account-deletion-approvals',
  templateUrl: './account-deletion-approvals.component.html',
  styleUrls: ['./account-deletion-approvals.component.css']
})
export class AccountDeletionApprovalsComponent implements OnInit {
  pendingDeletionRequests: any[] = [];
  loading: boolean = false;
  error: string = '';

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.loadPendingDeletionRequests();
  }

  loadPendingDeletionRequests(): void {
    this.loading = true;
    this.error = '';

    this.http.get<any[]>(`${environment.apiUrl}/account-deletion/pending`).subscribe({
      next: (requests) => {
        this.pendingDeletionRequests = requests;
        this.loading = false;
        console.log('Pending deletion requests loaded:', requests);
      },
      error: (error) => {
        console.error('Error loading pending deletion requests:', error);
        this.error = 'Failed to load pending deletion requests';
        this.loading = false;
      }
    });
  }

  approveDeletionRequest(requestId: number): void {
    if (confirm('Are you sure you want to approve this account deletion request? This action cannot be undone.')) {
      this.http.post<any>(`${environment.apiUrl}/account-deletion/approve/${requestId}`, {}).subscribe({
        next: (response) => {
          console.log('Deletion request approved:', response);
          alert('Account deletion request approved successfully!');
          this.loadPendingDeletionRequests(); // Refresh the list
        },
        error: (error) => {
          console.error('Error approving deletion request:', error);
          alert('Error approving deletion request: ' + (error.error || 'Unknown error'));
        }
      });
    }
  }

  rejectDeletionRequest(requestId: number): void {
    const rejectionReason = prompt('Please provide a reason for rejecting this deletion request:');
    
    if (rejectionReason && rejectionReason.trim()) {
      this.http.post<any>(`${environment.apiUrl}/account-deletion/reject/${requestId}`, {
        reason: rejectionReason.trim()
      }).subscribe({
        next: (response) => {
          console.log('Deletion request rejected:', response);
          alert('Account deletion request rejected successfully!');
          this.loadPendingDeletionRequests(); // Refresh the list
        },
        error: (error) => {
          console.error('Error rejecting deletion request:', error);
          alert('Error rejecting deletion request: ' + (error.error || 'Unknown error'));
        }
      });
    } else if (rejectionReason !== null) {
      alert('Please provide a reason for rejection.');
    }
  }

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
}
