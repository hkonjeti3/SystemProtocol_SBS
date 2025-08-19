import { Component, OnInit } from '@angular/core';
import { decodeToken } from '../../../../core/utils/jwt-helper';
import { DashboardService } from '../../../../core/services/dashboard.service';

@Component({
  selector: 'app-customer-dashboard',
  templateUrl: './customer-dashboard.component.html',
  styleUrl: './customer-dashboard.component.css'
})
export class CustomerDashboardComponent implements OnInit {
  userFirstName: string = 'User';
  currentDate: Date = new Date();
  totalAccounts: number = 0;
  totalBalance: string = '0.00';
  recentTransactions: number = 0;
  recentActivities: any[] = [];
  isLoading: boolean = true;
  activityLoading: boolean = true;

  constructor(private dashboardService: DashboardService) {}

  ngOnInit() {
    this.loadUserData();
    this.loadDashboardData();
    this.loadActivities();
  }

  private loadUserData() {
    const token = localStorage.getItem('jwtToken');
    if (token) {
      const decodedToken = decodeToken(token);
      if (decodedToken) {
        this.userFirstName = decodedToken.firstName || decodedToken.username || 'User';
        console.log('Home user data loaded:', { firstName: decodedToken.firstName, username: decodedToken.username });
      }
    }
  }

  private loadDashboardData() {
    const token = localStorage.getItem('jwtToken');
    if (token) {
      const decodedToken = decodeToken(token);
      if (decodedToken && decodedToken.userId) {
        this.isLoading = true;
        
        this.dashboardService.getDashboardStats(decodedToken.userId).subscribe({
          next: (data: any) => {
            this.totalAccounts = data.totalAccounts || 0;
            this.totalBalance = data.totalBalance || '0.00';
            this.recentTransactions = data.recentTransactions || 0;
            this.isLoading = false;
            console.log('Dashboard stats loaded:', data);
          },
          error: (error: any) => {
            console.error('Error loading dashboard data:', error);
            this.isLoading = false;
            // Fallback to default values
            this.totalAccounts = 0;
            this.totalBalance = '0.00';
            this.recentTransactions = 0;
          }
        });
      }
    }
  }

  private loadActivities() {
    const token = localStorage.getItem('jwtToken');
    if (token) {
      const decodedToken = decodeToken(token);
      if (decodedToken && decodedToken.userId) {
        this.activityLoading = true;
        
        console.log('Loading activities for user:', decodedToken.userId);
        
        // First try to get activities since last login
        this.dashboardService.getUserActivityLogsSinceLastLogin(decodedToken.userId).subscribe({
          next: (data: any) => {
            console.log('Activities since last login:', data);
            this.processActivities(data);
            this.activityLoading = false;
          },
          error: (error: any) => {
            console.error('Error loading activities since last login:', error);
            
            // Fallback to regular activity logs
            this.dashboardService.getUserActivityLogs(decodedToken.userId).subscribe({
              next: (data: any) => {
                console.log('Regular activities loaded (fallback):', data);
                this.processActivities(data);
                this.activityLoading = false;
              },
              error: (error: any) => {
                console.error('Error loading regular activities:', error);
                
                // Final fallback - try to get all activities
                this.dashboardService.getActivityLogs().subscribe({
                  next: (data: any) => {
                    console.log('All activities loaded (final fallback):', data);
                    // Filter for current user's activities
                    const userActivities = data.filter((activity: any) => 
                      activity.userId === decodedToken.userId
                    );
                    this.processActivities(userActivities);
                    this.activityLoading = false;
                  },
                  error: (error: any) => {
                    console.error('Error loading all activities:', error);
                    this.recentActivities = [];
                    this.activityLoading = false;
                  }
                });
              }
            });
          }
        });
      }
    }
  }

  private processActivities(activities: any[]) {
    if (!activities || activities.length === 0) {
      console.log('No activities found, creating sample data');
      this.createSampleActivities();
      return;
    }

    // Filter out duplicate activities
    const uniqueActivities = this.filterDuplicateActivities(activities);
    console.log('Unique activities after filtering:', uniqueActivities);
    
    // Filter for last 24 hours
    const last24h = this.filterActivitiesLast24Hours(uniqueActivities);
    console.log('Activities in last 24 hours:', last24h);
    
    // Map to display format
    this.recentActivities = last24h.slice(0, 25).map((activity: any) => ({
      title: activity.action || activity.description || 'Activity',
      time: new Date(activity.timestamp).toLocaleString(),
      amount: this.extractAmount(activity.description || ''),
      type: this.determineActivityType(activity.action || ''),
      icon: this.getActivityIcon(activity.action || '')
    }));
    
    console.log('Final processed activities:', this.recentActivities);
  }

  private createSampleActivities() {
    // Create sample activities for demonstration
    const now = new Date();
    this.recentActivities = [
      {
        title: 'Account Login',
        time: now.toLocaleString(),
        amount: '',
        type: 'info',
        icon: 'pi pi-sign-in'
      },
      {
        title: 'Dashboard Viewed',
        time: new Date(now.getTime() - 1000 * 60 * 30).toLocaleString(), // 30 minutes ago
        amount: '',
        type: 'info',
        icon: 'pi pi-home'
      }
    ];
  }

  private extractAmount(description: string): string {
    const amountMatch = description.match(/\$?(\d+(?:\.\d{2})?)/);
    return amountMatch ? amountMatch[0] : '';
  }

  private determineActivityType(action: string): string {
    if (action.toLowerCase().includes('credit') || action.toLowerCase().includes('deposit')) {
      return 'credit';
    } else if (action.toLowerCase().includes('debit') || action.toLowerCase().includes('withdrawal')) {
      return 'debit';
    } else if (action.toLowerCase().includes('transfer')) {
      return 'transfer';
    } else if (action.toLowerCase().includes('login')) {
      return 'info';
    } else {
      return 'info';
    }
  }

  private getActivityIcon(action: string): string {
    if (action.toLowerCase().includes('credit') || action.toLowerCase().includes('deposit')) {
      return 'pi pi-arrow-down';
    } else if (action.toLowerCase().includes('debit') || action.toLowerCase().includes('withdrawal')) {
      return 'pi pi-arrow-up';
    } else if (action.toLowerCase().includes('transfer')) {
      return 'pi pi-exchange';
    } else if (action.toLowerCase().includes('login')) {
      return 'pi pi-sign-in';
    } else if (action.toLowerCase().includes('account')) {
      return 'pi pi-credit-card';
    } else {
      return 'pi pi-info-circle';
    }
  }

  filterDuplicateActivities(activities: any[]): any[] {
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

  private filterActivitiesLast24Hours(activities: any[]): any[] {
    const now = Date.now();
    const cutoff = now - 24 * 60 * 60 * 1000;
    return activities.filter((a) => {
      const t = new Date(a.timestamp || a.time || a.createdAt).getTime();
      return !isNaN(t) && t >= cutoff;
    });
  }
}
