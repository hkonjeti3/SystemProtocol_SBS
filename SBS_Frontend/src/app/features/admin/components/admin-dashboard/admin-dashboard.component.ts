import { Component, OnInit } from '@angular/core';
import { UserService } from '../../../../core/services/user.service';
import { AdminService } from '../../../../core/services/admin.service';
import { AccountService } from '../../../../core/services/account.service';
import { ProfileRequestService } from '../../../../core/services/profile-request.service';
import { ActivityService, ActivityLog } from '../../../../core/services/activity.service';
import { SessionManagerService } from '../../../../core/services/session-manager.service';

@Component({
  selector: 'app-admin-dashboard',
  templateUrl: './admin-dashboard.component.html',
  styleUrls: ['./admin-dashboard.component.css']
})
export class AdminDashboardComponent implements OnInit {
  totalUsers: number = 0;
  totalAccounts: number = 0;
  pendingTransactions: number = 0;
  totalBalance: number = 0;
  recentActivities: any[] = [];
  isLoading: boolean = false;
  error: string = '';
  currentDate: Date = new Date();

  // Approval requests
  pendingAccountRequests: any[] = [];
  pendingProfileRequests: any[] = [];
  isLoadingApprovals: boolean = false;
  isProcessingRequest: { [key: number]: boolean } = {};

  constructor(
    private userService: UserService,
    private adminService: AdminService,
    private accountService: AccountService,
    private profileRequestService: ProfileRequestService,
    private activityService: ActivityService,
    private sessionManager: SessionManagerService
  ) { }

  ngOnInit() {
    this.loadDashboardData();
    this.loadApprovalRequests();
  }

  loadDashboardData() {
    this.isLoading = true;
    this.error = '';

    // Load admin users
    this.adminService.getAllUsers().subscribe({
      next: (response: any) => {
        console.log('Admin users response:', response);
        if (response && response.users) {
          this.totalUsers = response.users.length || 0;
        } else if (Array.isArray(response)) {
          this.totalUsers = response.length || 0;
        } else {
          this.totalUsers = 0;
        }
        this.isLoading = false;
      },
      error: (error: any) => {
        console.error('Error loading users:', error);
        this.error = 'Failed to load user data';
        this.isLoading = false;
      }
    });

    // Load total accounts
    this.loadTotalAccounts();

    // Load pending transactions
    this.loadPendingTransactions();

    // Load recent activities
    this.loadRecentActivities();
  }

  loadTotalAccounts() {
    this.accountService.getAllAccounts().subscribe({
      next: (response: any) => {
        console.log('All accounts response:', response);
        if (response && response.accounts) {
          this.totalAccounts = response.accounts.length || 0;
        } else if (Array.isArray(response)) {
          this.totalAccounts = response.length || 0;
        } else {
          this.totalAccounts = 0;
        }
      },
      error: (error: any) => {
        console.error('Error loading accounts:', error);
        this.totalAccounts = 0;
      }
    });
  }

  loadPendingTransactions() {
    // Load pending transactions from the transaction service
    this.adminService.getPendingTransactions().subscribe({
      next: (response: any) => {
        console.log('Pending transactions response:', response);
        if (response && response.transactions) {
          this.pendingTransactions = response.transactions.length || 0;
        } else if (Array.isArray(response)) {
          this.pendingTransactions = response.length || 0;
        } else {
          this.pendingTransactions = 0;
        }
      },
      error: (error: any) => {
        console.error('Error loading pending transactions:', error);
        this.pendingTransactions = 0;
      }
    });
  }

  loadRecentActivities() {
    // Get current user ID from session
    const currentUserId = this.sessionManager.getCurrentUserId();
    
    if (!currentUserId) {
      console.warn('No current user ID found, loading all activities');
      this.loadAllActivities();
      return;
    }

    console.log('Loading activities for current user:', currentUserId);
    
    // Load recent activities for the current user since last login (to avoid showing old activities)
    this.activityService.getUserActivityLogsSinceLastLogin(currentUserId).subscribe({
      next: (activityLogs: ActivityLog[]) => {
        console.log('Loaded user activity logs since last login:', activityLogs);
        
        // Filter out duplicate login activities and convert to dashboard format
        const uniqueActivities = this.filterDuplicateActivities(activityLogs);
        const last24h = this.filterActivitiesLast24Hours(uniqueActivities);
        this.recentActivities = last24h
          .slice(0, 25)
          .map(log => this.activityService.convertToDashboardActivity(log));
        
        console.log('Converted user activities:', this.recentActivities);
      },
      error: (error: any) => {
        console.error('Error loading user activity logs since last login:', error);
        // Fallback to user-specific activities if since-login fails
        this.loadUserActivities(currentUserId);
      }
    });
  }

  filterDuplicateActivities(activities: ActivityLog[]): ActivityLog[] {
    const seen = new Set<string>();
    return activities.filter(activity => {
      // Create a unique key for each activity
      const key = `${activity.action}-${activity.description}-${activity.timestamp}`;
      if (seen.has(key)) {
        return false;
      }
      seen.add(key);
      return true;
    });
  }

  loadUserActivities(userId: number) {
    // Fallback method to load all user activities
    this.activityService.getUserActivityLogs(userId).subscribe({
      next: (activityLogs: ActivityLog[]) => {
        console.log('Loaded all user activity logs:', activityLogs);
        
        // Filter by last 24 hours and convert to dashboard format
        const last24h = this.filterActivitiesLast24Hours(activityLogs);
        this.recentActivities = last24h
          .slice(0, 25)
          .map(log => this.activityService.convertToDashboardActivity(log));
        
        console.log('Converted all user activities:', this.recentActivities);
      },
      error: (error: any) => {
        console.error('Error loading all user activity logs:', error);
        // Fallback to all activities if user-specific loading fails
        this.loadAllActivities();
      }
    });
  }

  loadAllActivities() {
    // Fallback method to load all activities
    this.activityService.getAllActivityLogs().subscribe({
      next: (activityLogs: ActivityLog[]) => {
        console.log('Loaded all activity logs:', activityLogs);
        
        // Filter by last 24 hours and convert to dashboard format
        const last24h = this.filterActivitiesLast24Hours(activityLogs);
        this.recentActivities = last24h
          .slice(0, 25)
          .map(log => this.activityService.convertToDashboardActivity(log));
        
        console.log('Converted all activities:', this.recentActivities);
      },
      error: (error: any) => {
        console.error('Error loading all activity logs:', error);
        // Fallback to empty array if activity logs fail to load
        this.recentActivities = [];
      }
    });
  }

  private filterActivitiesLast24Hours(activities: ActivityLog[]): ActivityLog[] {
    const now = Date.now();
    const cutoff = now - 24 * 60 * 60 * 1000;
    return activities.filter((a) => {
      const t = new Date((a as any).timestamp || (a as any).time || (a as any).createdAt).getTime();
      return !isNaN(t) && t >= cutoff;
    });
  }

  getActivityIcon(type: string): string {
    const iconMap: { [key: string]: string } = {
      'user': 'pi pi-user-plus',
      'transaction': 'pi pi-exchange',
      'account': 'pi pi-credit-card',
      'security': 'pi pi-shield',
      'approval': 'pi pi-check-circle',
      'info': 'pi pi-info-circle'
    };
    return iconMap[type] || 'pi pi-info-circle';
  }

  // Approval Request Methods
  public loadApprovalRequests(): void {
    this.isLoadingApprovals = true;
    
    // Debug: Check if JWT token exists
    const token = localStorage.getItem('jwtToken');
    console.log('JWT Token exists:', !!token);
    if (token) {
      console.log('JWT Token (first 50 chars):', token.substring(0, 50) + '...');
    } else {
      console.error('No JWT token found in localStorage');
      this.isLoadingApprovals = false;
      return;
    }
    
    // Load pending account requests
    this.accountService.getPendingAccountRequests().subscribe({
      next: (accountRequests: any[]) => {
        this.pendingAccountRequests = accountRequests;
        console.log('Loaded pending account requests:', accountRequests);
      },
      error: (error: any) => {
        console.error('Error loading pending account requests:', error);
        this.pendingAccountRequests = [];
        // Show user-friendly error message
        if (error.status === 400) {
          console.error('Bad request - possible authentication or authorization issue');
        }
      }
    });

    // Load pending profile requests
    this.profileRequestService.getPendingProfileRequests().subscribe({
      next: (profileRequests: any[]) => {
        this.pendingProfileRequests = profileRequests;
        console.log('Loaded pending profile requests:', profileRequests);
        this.isLoadingApprovals = false;
      },
      error: (error: any) => {
        console.error('Error loading pending profile requests:', error);
        this.pendingProfileRequests = [];
        this.isLoadingApprovals = false;
        // Show user-friendly error message
        if (error.status === 400) {
          console.error('Bad request - possible authentication or authorization issue');
        }
      }
    });
  }

  public approveAccountRequest(request: any): void {
    if (!request.id) {
      console.error('Request ID is missing');
      return;
    }

    this.isProcessingRequest[request.id] = true;
    
    this.accountService.approveAccountRequest(request.id).subscribe({
      next: (response: any) => {
        console.log('Account request approved:', response);
        // Remove from pending list
        this.pendingAccountRequests = this.pendingAccountRequests.filter(r => r.id !== request.id);
        this.isProcessingRequest[request.id] = false;
        alert('Account creation request approved successfully!');
      },
      error: (error: any) => {
        console.error('Error approving account request:', error);
        this.isProcessingRequest[request.id] = false;
        alert(`Failed to approve account request: ${error.message || 'Unknown error'}`);
      }
    });
  }

  public rejectAccountRequest(request: any): void {
    if (!request.id) {
      console.error('Request ID is missing');
      return;
    }

    const reason = prompt('Please provide a reason for rejection (optional):') || undefined;
    this.isProcessingRequest[request.id] = true;
    
    this.accountService.rejectAccountRequest(request.id, reason).subscribe({
      next: (response: any) => {
        console.log('Account request rejected:', response);
        // Remove from pending list
        this.pendingAccountRequests = this.pendingAccountRequests.filter(r => r.id !== request.id);
        this.isProcessingRequest[request.id] = false;
        alert('Account creation request rejected successfully!');
      },
      error: (error: any) => {
        console.error('Error rejecting account request:', error);
        this.isProcessingRequest[request.id] = false;
        alert(`Failed to reject account request: ${error.message || 'Unknown error'}`);
      }
    });
  }

  public approveProfileRequest(request: any): void {
    if (!request.id) {
      console.error('Request ID is missing');
      return;
    }

    this.isProcessingRequest[request.id] = true;
    
    this.profileRequestService.approveProfileRequest(request.id).subscribe({
      next: (response: any) => {
        console.log('Profile request approved:', response);
        // Remove from pending list
        this.pendingProfileRequests = this.pendingProfileRequests.filter(r => r.id !== request.id);
        this.isProcessingRequest[request.id] = false;
        alert('Profile update request approved successfully!');
      },
      error: (error: any) => {
        console.error('Error approving profile request:', error);
        this.isProcessingRequest[request.id] = false;
        alert(`Failed to approve profile request: ${error.message || 'Unknown error'}`);
      }
    });
  }

  public rejectProfileRequest(request: any): void {
    if (!request.id) {
      console.error('Request ID is missing');
      return;
    }

    const reason = prompt('Please provide a reason for rejection (optional):') || undefined;
    this.isProcessingRequest[request.id] = true;
    
    this.profileRequestService.rejectProfileRequest(request.id, reason).subscribe({
      next: (response: any) => {
        console.log('Profile request rejected:', response);
        // Remove from pending list
        this.pendingProfileRequests = this.pendingProfileRequests.filter(r => r.id !== request.id);
        this.isProcessingRequest[request.id] = false;
        alert('Profile update request rejected successfully!');
      },
      error: (error: any) => {
        console.error('Error rejecting profile request:', error);
        this.isProcessingRequest[request.id] = false;
        alert(`Failed to reject profile request: ${error.message || 'Unknown error'}`);
      }
    });
  }
}
