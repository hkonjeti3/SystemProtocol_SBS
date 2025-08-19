import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, throwError, of } from 'rxjs';
import { tap, map, catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class TransactionService {
  private baseUrl = environment.apiUrl;

  constructor(private http: HttpClient) { }

  getAllTransactions(): Observable<any[]> {
    const url = `${this.baseUrl}/transactions/all`;
    
    return this.http.get<any>(url)
      .pipe(
        tap((response: any) => {
          console.log('Transactions API response:', response);
        }),
        map((response: any) => {
          if (response && response.transactions) {
            return response.transactions;
          } else if (Array.isArray(response)) {
            return response;
          } else if (response && response.error) {
            // Handle case where backend returns error info but with 200 status
            console.warn('Backend returned error info:', response.error);
            return []; // Return empty array for error cases
          } else {
            console.warn('Invalid response format from server, returning empty array');
            return [];
          }
        }),
        catchError((error: any) => {
          console.error('Error from transactions API:', error);
          let errorMessage = 'Failed to retrieve transactions';
          
          if (error.error && error.error.message) {
            errorMessage = error.error.message;
          } else if (error.message) {
            errorMessage = error.message;
          }
          
          // Return empty array instead of throwing error for better UX
          console.warn('Returning empty array due to error:', errorMessage);
          return of([]);
        })
      );
  }

  getTransactionHistory(userId: number): Observable<any[]> {
    const url = `${this.baseUrl}/transactions/history/${userId}`;
    
    return this.http.get<any[]>(url)
      .pipe(
        tap((response: any) => {
          console.log('Transaction history response:', response);
        }),
        catchError((error: any) => {
          console.error('Error getting transaction history:', error);
          let errorMessage = 'Failed to retrieve transaction history';
          
          if (error.error && error.error.message) {
            errorMessage = error.error.message;
          } else if (error.message) {
            errorMessage = error.message;
          }
          
          return throwError(() => new Error(errorMessage));
        })
      );
  }

  getPendingTransactions(userId: number): Observable<any[]> {
    const url = `${this.baseUrl}/transactions/pending/${userId}`;
    
    return this.http.get<any[]>(url)
      .pipe(
        tap((response: any) => {
          console.log('Pending transactions response:', response);
        }),
        catchError((error: any) => {
          console.error('Error getting pending transactions:', error);
          let errorMessage = 'Failed to retrieve pending transactions';
          
          if (error.error && error.error.message) {
            errorMessage = error.error.message;
          } else if (error.message) {
            errorMessage = error.message;
          }
          
          return throwError(() => new Error(errorMessage));
        })
      );
  }

  approveTransaction(transactionId: number): Observable<any> {
    const url = `${this.baseUrl}/approval/transaction/approve/${transactionId}`;
    
    // Get JWT token from localStorage
    const token = localStorage.getItem('jwtToken');
    const headers = new HttpHeaders({
      'Content-Type': 'application/json'
    });
    
    // Add Authorization header if token exists
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
    
    return this.http.post(url, {}, { headers })
      .pipe(
        tap((response: any) => {
          console.log('Transaction approved successfully:', response);
        }),
        catchError((error: any) => {
          console.error('Error approving transaction:', error);
          let errorMessage = 'Failed to approve transaction';
          
          if (error.error && error.error.message) {
            errorMessage = error.error.message;
          } else if (error.message) {
            errorMessage = error.message;
          }
          
          return throwError(() => new Error(errorMessage));
        })
      );
  }

  rejectTransaction(transactionId: number): Observable<any> {
    const url = `${this.baseUrl}/approval/transaction/reject/${transactionId}`;
    
    // Get JWT token from localStorage
    const token = localStorage.getItem('jwtToken');
    const headers = new HttpHeaders({
      'Content-Type': 'application/json'
    });
    
    // Add Authorization header if token exists
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
    
    return this.http.post(url, {}, { headers })
      .pipe(
        tap((response: any) => {
          console.log('Transaction rejected successfully:', response);
        }),
        catchError((error: any) => {
          console.error('Error rejecting transaction:', error);
          let errorMessage = 'Failed to reject transaction';
          
          if (error.error && error.error.message) {
            errorMessage = error.error.message;
          } else if (error.message) {
            errorMessage = error.message;
          }
          
          return throwError(() => new Error(errorMessage));
        })
      );
  }

  requestMoney(requestData: any): Observable<any> {
    const url = `${this.baseUrl}/transactions/request-money`;
    
    return this.http.post(url, requestData)
      .pipe(
        tap((response: any) => {
          console.log('Money request sent successfully:', response);
        }),
        catchError((error: any) => {
          console.error('Error requesting money:', error);
          let errorMessage = 'Failed to send money request';
          
          if (error.error && error.error.message) {
            errorMessage = error.error.message;
          } else if (error.message) {
            errorMessage = error.message;
          }
          
          return throwError(() => new Error(errorMessage));
        })
      );
  }

  sendMoney(transferData: any): Observable<any> {
    const url = `${this.baseUrl}/transactions/send-money`;
    
    return this.http.post(url, transferData)
      .pipe(
        tap((response: any) => {
          console.log('Money sent successfully:', response);
        }),
        catchError((error: any) => {
          console.error('Error sending money:', error);
          let errorMessage = 'Failed to send money';
          
          if (error.error && error.error.message) {
            errorMessage = error.error.message;
          } else if (error.message) {
            errorMessage = error.message;
          }
          
          return throwError(() => new Error(errorMessage));
        })
      );
  }
} 