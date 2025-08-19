import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import { transaction } from './transaction'; // Adjust the import path as necessary
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})

export class CreditService {
  private baseUrl = `${environment.apiUrl}/account/`;
  constructor(private http: HttpClient) { }

  performTransaction(transactionType: 'CREDIT' | 'DEBIT', transactionData: transaction): Observable<any> {
    const url = `${this.baseUrl}${transactionType.toLowerCase()}/request`;
    console.log('Sending transaction request:', transactionData, 'Type:', transactionType, 'URL:', url);
    
    // Get JWT token from localStorage
    const token = localStorage.getItem('jwtToken');
    const headers = new HttpHeaders({
      'Content-Type': 'application/json'
    });
    
    // Add Authorization header if token exists
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
    
    return this.http.post(url, transactionData, {
      headers: headers,
      responseType: 'text' // Expect text response from backend
    }).pipe(
      tap(response => console.log(`Transaction ${transactionType} request processed:`, response)),
      catchError(this.handleError)
    );
  }

  private handleError(error: any) {
    console.error('CreditService error:', error);
    return throwError(() => new Error('Error occurred during the transaction request'));
  }
}
