import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { decodeToken } from '../../../../core/utils/jwt-helper';
import { TransactionService } from '../../../../core/services/transaction.service';
import { AccountService } from '../../../../core/services/account.service';

@Component({
  selector: 'app-customer-send-money',
  templateUrl: './customer-send-money.component.html',
  styleUrls: ['./customer-send-money.component.css']
})
export class CustomerSendMoneyComponent implements OnInit {
  sendForm: FormGroup;
  isLoading = false;
  userFirstName: string = 'User';
  userAccounts: any[] = [];

  constructor(
    private fb: FormBuilder,
    public router: Router,
    private route: ActivatedRoute,
    private transactionService: TransactionService,
    private accountService: AccountService
  ) {
    this.sendForm = this.fb.group({
      senderAccount: ['', [Validators.required]],
      recipientEmail: ['', [Validators.required, Validators.email]],
      amount: ['', [Validators.required, Validators.min(0.01)]],
      description: ['', [Validators.required, Validators.minLength(5)]]
    });
  }

  ngOnInit() {
    this.loadUserData();
    this.loadUserAccounts();
    this.handleQueryParams();
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

  private loadUserAccounts() {
    const token = localStorage.getItem('jwtToken');
    if (token) {
      const decodedToken = decodeToken(token);
      if (decodedToken && decodedToken.userId) {
        this.accountService.getUserAccounts(decodedToken.userId)
          .subscribe({
            next: (accounts) => {
              this.userAccounts = accounts;
              this.isLoading = false;
            },
            error: (error: any) => {
              console.error('Error loading accounts:', error);
              this.isLoading = false;
            }
          });
      }
    }
  }

  private handleQueryParams() {
    this.route.queryParams.subscribe(params => {
      if (params['fromAccount']) {
        this.sendForm.patchValue({
          senderAccount: params['fromAccount']
        });
      }
    });
  }

  onSubmit() {
    if (this.sendForm.valid) {
      this.isLoading = true;
      
      const sendData = {
        ...this.sendForm.value,
        sendDate: new Date().toISOString()
      };

      this.transactionService.sendMoney(sendData)
        .subscribe({
          next: (response) => {
            this.isLoading = false;
            alert('Money sent successfully!');
            this.router.navigate(['/tran-his']);
          },
          error: (error) => {
            this.isLoading = false;
            console.error('Error sending money:', error);
            alert('Failed to send money. Please try again.');
          }
        });
    }
  }

  get senderAccount() {
    return this.sendForm.get('senderAccount');
  }

  get recipientEmail() {
    return this.sendForm.get('recipientEmail');
  }

  get amount() {
    return this.sendForm.get('amount');
  }

  get description() {
    return this.sendForm.get('description');
  }
} 