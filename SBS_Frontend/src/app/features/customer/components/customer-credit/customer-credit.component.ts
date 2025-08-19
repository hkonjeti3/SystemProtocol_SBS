import { Component, OnInit } from '@angular/core';
import { NgForm } from '@angular/forms';
import { CreditService } from '../../../../core/services/credit.service';
import { transaction } from '../../../../core/services/transaction';
import { account } from '../../../../core/services/account';
import { user } from "../../../../core/services/user";
import { UserService } from '../../../../core/services/user.service';
import { decodeToken } from '../../../../core/utils/jwt-helper';

function isTransactionType(type: string): type is 'CREDIT' | 'DEBIT' {
  return type === 'CREDIT' || type === 'DEBIT';
}

@Component({
  selector: 'app-customer-credit',
  templateUrl: './customer-credit.component.html',
  styleUrls: ['./customer-credit.component.css']
})
export class CustomerCreditComponent implements OnInit {
  senderAcc = new account();
  receiverAcc = new account();
  user = new user();
  transaction = new transaction(this.user);
  userId: number | null = null;
  token: string | undefined;

  constructor(
    private creditService: CreditService,
    private userService: UserService
  ) {}

  ngOnInit(): void {
    const token = localStorage.getItem('jwtToken');
    if (token) {
      const decodedToken = decodeToken(token);
      const userId = decodedToken?.userId;
      if (userId) {
        this.initializeTransaction(userId);
      } else {
        console.error('User ID is not present in the decoded token');
      }
    } else {
      console.error('JWT Token not found in local storage');
    }
  }

  initializeTransaction(userId: number): void {
    this.userId = userId;
    this.user.userId = userId;
    this.transaction = new transaction(this.user);
  }

  submitTransaction(form: NgForm) {
    if (form.valid) {
      const upperCaseType = form.value.transactionType.toUpperCase();
      
      // Set the transaction properties with account numbers as strings
      this.transaction.senderAccountNumber = this.senderAcc.accountNumber || '';
      this.transaction.receiverAccountNumber = this.receiverAcc.accountNumber || '';
      this.transaction.transactionType = form.value.transactionType;
      this.transaction.amount = form.value.amount;
      
      // Ensure the user object is properly set with userId
      if (this.userId) {
        this.user.userId = this.userId;
        this.transaction.user = this.user;
      }
      
      console.log('Form values:', form.value);
      console.log('Transaction object:', this.transaction);
      
      if (isTransactionType(upperCaseType)) {
        console.log('Submitting transaction:', this.transaction);
        this.creditService.performTransaction(upperCaseType, this.transaction)
          .subscribe({
            next: (response: any) => {
              console.log('Transaction request successful:', response);
              alert(`${upperCaseType} transaction request created successfully! Your transaction is pending approval.`);
              this.resetForm();
            },
            error: (error) => {
              console.error('Transaction request failed:', error);
              let errorMessage = 'Transaction request failed';
              if (error.error && error.error.message) {
                errorMessage = error.error.message;
              } else if (error.message) {
                errorMessage = error.message;
              }
              alert(`Error: ${errorMessage}`);
            }
          });
      } else {
        console.error('Invalid transaction type');
        alert('Please select a valid transaction type (Credit or Debit)');
      }
    } else {
      alert('Please fill in all required fields correctly');
    }
  }

  resetForm() {
    this.transaction = new transaction(this.user);
    this.senderAcc = new account();
    this.receiverAcc = new account();
  }
}