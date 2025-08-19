import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { decodeToken } from '../../../../core/utils/jwt-helper';
import { TransactionService } from '../../../../core/services/transaction.service';

@Component({
  selector: 'app-customer-request-money',
  templateUrl: './customer-request-money.component.html',
  styleUrls: ['./customer-request-money.component.css']
})
export class CustomerRequestMoneyComponent implements OnInit {
  requestForm: FormGroup;
  isLoading = false;
  userFirstName: string = 'User';

  constructor(
    private fb: FormBuilder,
    public router: Router,
    private transactionService: TransactionService
  ) {
    this.requestForm = this.fb.group({
      recipientEmail: ['', [Validators.required, Validators.email]],
      amount: ['', [Validators.required, Validators.min(0.01)]],
      description: ['', [Validators.required, Validators.minLength(5)]],
      accountNumber: ['', [Validators.required]]
    });
  }

  ngOnInit() {
    this.loadUserData();
  }

  private loadUserData() {
    const token = localStorage.getItem('jwtToken');
    if (token) {
      const decodedToken = decodeToken(token);
      if (decodedToken) {
        this.userFirstName = decodedToken.firstName || decodedToken.username || 'User';
      }
    }
  }

  onSubmit() {
    if (this.requestForm.valid) {
      this.isLoading = true;

      const requestData = {
        ...this.requestForm.value,
        requestDate: new Date().toISOString()
      };

      this.transactionService.requestMoney(requestData)
        .subscribe({
          next: (response) => {
            this.isLoading = false;
            alert('Money request sent successfully!');
            this.router.navigate(['/tran-his']);
          },
          error: (error) => {
            this.isLoading = false;
            console.error('Error sending money request:', error);
            alert('Failed to send money request. Please try again.');
          }
        });
    }
  }

  get recipientEmail() {
    return this.requestForm.get('recipientEmail');
  }

  get amount() {
    return this.requestForm.get('amount');
  }

  get description() {
    return this.requestForm.get('description');
  }

  get accountNumber() {
    return this.requestForm.get('accountNumber');
  }
} 