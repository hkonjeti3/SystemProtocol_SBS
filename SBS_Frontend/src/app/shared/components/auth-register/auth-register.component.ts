import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { UserService } from '../../../core/services/user.service';
import { passwordMatchValidator } from '../../password-match.directive';

@Component({
  selector: 'app-auth-register',
  templateUrl: './auth-register.component.html',
  styleUrls: ['./auth-register.component.css']
})
export class AuthRegisterComponent {
  registerForm: FormGroup;
  isLoading: boolean = false;

  constructor(
    private fb: FormBuilder,
    private router: Router,
    private userService: UserService
  ) {
    this.registerForm = this.fb.group({
      firstName: ['', Validators.required],
      lastName: ['', Validators.required],
      username: ['', Validators.required],
      password: ['', Validators.required],
      confirmPassword: ['', Validators.required],
      phoneNumber: ['', Validators.required],
      emailAddress: ['', [Validators.required, Validators.email]],
      address: ['', Validators.required]
    }, {
      validators: passwordMatchValidator
    });
  }

  get firstName() {
    return this.registerForm.get('firstName');
  }

  get lastName() {
    return this.registerForm.get('lastName');
  }

  get username() {
    return this.registerForm.get('username');
  }

  get password() {
    return this.registerForm.get('password');
  }

  get confirmPassword() {
    return this.registerForm.get('confirmPassword');
  }

  get phoneNumber() {
    return this.registerForm.get('phoneNumber');
  }

  get email() {
    return this.registerForm.get('emailAddress');
  }

  get address() {
    return this.registerForm.get('address');
  }

  onSubmit() {
    if (this.registerForm.valid) {
      this.isLoading = true;
      
      const userData = {
        firstName: this.registerForm.value.firstName,
        lastName: this.registerForm.value.lastName,
        username: this.registerForm.value.username,
        password: this.registerForm.value.password,
        phoneNumber: this.registerForm.value.phoneNumber,
        emailAddress: this.registerForm.value.emailAddress,
        address: this.registerForm.value.address
      };

      this.userService.register(userData).subscribe({
        next: (response: any) => {
          this.isLoading = false;
          console.log('Registration successful:', response);
          alert('Registration successful! Please login.');
          this.router.navigate(['/login']);
        },
        error: (error: any) => {
          this.isLoading = false;
          console.error('Registration error:', error);
          
          let errorMessage = 'Registration failed. Please try again.';
          if (error.error && error.error.message) {
            errorMessage = error.error.message;
          }
          
          alert(errorMessage);
        }
      });
    }
  }
}