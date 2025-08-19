
// user-details.component.ts
import { Component, OnInit, OnDestroy } from '@angular/core';
import { UserDetailsService } from '../../../../core/services/user-details.service';
import { interval, Observable, Subject } from 'rxjs';
import { takeUntil, switchMap } from 'rxjs/operators';
//import { UserService } from '../../../../core/services/user.service';

@Component({
  selector: 'app-admin-user-details',
  templateUrl: './admin-user-details.component.html',
  styleUrls: ['./admin-user-details.component.css']
})
export class AdminUserDetailsComponent implements OnInit, OnDestroy {
  users: any[] = [];
  private unsubscribe$: Subject<void> = new Subject<void>();

  constructor(private userService: UserDetailsService) { }

  ngOnInit(): void {
    // Fetch user details initially
    this.fetchUserDetails();

    // Fetch user details every 10 seconds (adjust the interval as needed)
    interval(10000)
      .pipe(
        takeUntil(this.unsubscribe$),
        switchMap(() => this.fetchUserDetails())
      )
      .subscribe(
        (data: any[]) => {
          this.users = data;
        },
        (error) => {
          console.error('Error fetching user details:', error);
        }
      );
  }

  ngOnDestroy(): void {
    // Unsubscribe to prevent memory leaks
    this.unsubscribe$.next();
    this.unsubscribe$.complete();
  }

  private fetchUserDetails(): Observable<any[]> {
    return this.userService.getUserDetails();
  }
}

