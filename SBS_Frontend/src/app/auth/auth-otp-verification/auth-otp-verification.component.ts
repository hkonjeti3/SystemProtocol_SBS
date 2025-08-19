import { Component, OnInit, Input, Output, EventEmitter } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { UserService } from '../../core/services/user.service';

@Component({
  selector: 'app-otp-verification',
  templateUrl: './auth-otp-verification.component.html',
  styleUrls: ['./auth-otp-verification.component.css']
})
export class AuthOtpVerificationComponent implements OnInit {
  @Input() showOtpModal: boolean = false;
  @Input() userEmail: string = '';
  @Output() otpVerified = new EventEmitter<any>();
  @Output() modalClosed = new EventEmitter<void>();

  otpForm: FormGroup;
  isVerifying: boolean = false;
  isResending: boolean = false;
  otpError: boolean = false;
  errorMessage: string = '';

  constructor(
    private fb: FormBuilder,
    private router: Router,
    private userService: UserService
  ) {
    this.otpForm = this.fb.group({
      digit1: ['', [Validators.required, Validators.pattern(/^[0-9]$/)]],
      digit2: ['', [Validators.required, Validators.pattern(/^[0-9]$/)]],
      digit3: ['', [Validators.required, Validators.pattern(/^[0-9]$/)]],
      digit4: ['', [Validators.required, Validators.pattern(/^[0-9]$/)]],
      digit5: ['', [Validators.required, Validators.pattern(/^[0-9]$/)]],
      digit6: ['', [Validators.required, Validators.pattern(/^[0-9]$/)]]
    });
  }

  ngOnInit() {
    // Focus on first input when modal opens
    if (this.showOtpModal) {
      setTimeout(() => {
        const firstInput = document.querySelector('.otp-input') as HTMLInputElement;
        if (firstInput) {
          firstInput.focus();
        }
      }, 100);
    }
  }

  onOtpInput(event: any, digitIndex: number) {
    const input = event.target;
    const value = input.value;
    
    // Only allow numbers
    if (!/^[0-9]$/.test(value)) {
      input.value = '';
      return;
    }

    // Clear error when user starts typing
    if (this.otpError) {
      this.otpError = false;
      this.errorMessage = '';
    }

    // Move to next input
    if (value && digitIndex < 6) {
      const nextInput = document.querySelector(`input[formControlName="digit${digitIndex + 1}"]`) as HTMLInputElement;
      if (nextInput) {
        nextInput.focus();
      }
    }

    // Auto-submit when all digits are filled
    if (this.isOtpComplete()) {
      this.verifyOtp();
    }
  }

  onOtpKeydown(event: any, digitIndex: number) {
    // Handle backspace
    if (event.key === 'Backspace' && !event.target.value && digitIndex > 1) {
      const prevInput = document.querySelector(`input[formControlName="digit${digitIndex - 1}"]`) as HTMLInputElement;
      if (prevInput) {
        prevInput.focus();
      }
    }
  }

  isOtpComplete(): boolean {
    const values = Object.values(this.otpForm.value) as string[];
    return values.every(value => value && value.length === 1);
  }

  getOtpString(): string {
    const values = Object.values(this.otpForm.value) as string[];
    return values.join('');
  }

  verifyOtp() {
    if (this.otpForm.invalid) {
      this.otpError = true;
      this.errorMessage = 'Please enter a complete 6-digit OTP';
      return;
    }

    this.isVerifying = true;
    this.otpError = false;
    this.errorMessage = '';

    const otp = this.getOtpString();
    const requestData = {
      email: this.userEmail,
      otp: otp
    };

    console.log('Sending OTP validation request:', requestData);

    this.userService.validateOtp(requestData).subscribe({
      next: (response: any) => {
        this.isVerifying = false;
        console.log('OTP verification successful:', response);
        
        // Check if the response indicates success
        if (response && response.success) {
          // Emit success event
          this.otpVerified.emit({
            success: true,
            message: response.message || 'OTP verified successfully'
          });
          
          // Close modal
          this.closeOtpModal();
          
          // Navigate based on user role
          this.navigateAfterOtpVerification();
        } else {
          this.otpError = true;
          this.errorMessage = response?.message || 'Invalid OTP. Please try again.';
        }
      },
      error: (error: any) => {
        this.isVerifying = false;
        this.otpError = true;
        console.error('OTP verification failed:', error);
        
        // Handle different error response formats
        if (error.error && error.error.message) {
          this.errorMessage = error.error.message;
        } else if (error.error && typeof error.error === 'string') {
          this.errorMessage = error.error;
        } else if (error.message) {
          this.errorMessage = error.message;
        } else {
          this.errorMessage = 'Invalid OTP. Please try again.';
        }
      }
    });
  }

  resendOtp() {
    this.isResending = true;
    this.otpError = false;
    this.errorMessage = '';

    console.log('Sending resend OTP request for email:', this.userEmail);

    // Call the resend OTP service
    this.userService.resendOtp(this.userEmail).subscribe({
      next: (response: any) => {
        this.isResending = false;
        console.log('OTP resent successfully:', response);
        
        // Check if the response indicates success
        if (response && response.success) {
          this.errorMessage = response.message || 'New OTP sent to your email';
          this.otpError = false;
          
          // Clear form
          this.otpForm.reset();
          
          // Focus on first input
          setTimeout(() => {
            const firstInput = document.querySelector('.otp-input') as HTMLInputElement;
            if (firstInput) {
              firstInput.focus();
            }
          }, 100);
        } else {
          this.otpError = true;
          this.errorMessage = response?.message || 'Failed to resend OTP. Please try again.';
        }
      },
      error: (error: any) => {
        this.isResending = false;
        this.otpError = true;
        console.error('Failed to resend OTP:', error);
        
        // Handle different error response formats
        if (error.error && error.error.message) {
          this.errorMessage = error.error.message;
        } else if (error.error && typeof error.error === 'string') {
          this.errorMessage = error.error;
        } else if (error.message) {
          this.errorMessage = error.message;
        } else {
          this.errorMessage = 'Failed to resend OTP. Please try again.';
        }
      }
    });
  }

  closeOtpModal() {
    this.showOtpModal = false;
    this.otpForm.reset();
    this.otpError = false;
    this.errorMessage = '';
    this.modalClosed.emit();
  }

  private navigateAfterOtpVerification() {
    // Get user role from localStorage or session
    const token = localStorage.getItem('jwtToken');
    if (token) {
      try {
        const decodedToken = JSON.parse(atob(token.split('.')[1]));
        const role = decodedToken.role;
        
        if (role === 4) {
          this.router.navigate(['/home-admin']);
        } else if (role === 6) {
          this.router.navigate(['/intuser-home']);
        } else {
          this.router.navigate(['/home']);
        }
      } catch (error) {
        console.error('Error decoding token:', error);
        this.router.navigate(['/home']);
      }
    } else {
      this.router.navigate(['/home']);
    }
  }
}
