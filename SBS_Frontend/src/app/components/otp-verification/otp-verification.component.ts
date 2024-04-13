import { Component, OnInit } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { FormGroup } from '@angular/forms';

import { ActivatedRoute, Router } from '@angular/router';
import { RegisterService } from '../../services/register.service';
import { JwtHelperService } from '../../services/jwt-helper.service'; 
import { UserRoles } from '../../user-roles';


@Component({
  selector: 'app-otp-verification',
  templateUrl: './otp-verification.component.html',
  styleUrls: ['./otp-verification.component.css']
})
export class OtpVerificationComponent implements OnInit {
  otpForm = this.fb.group({
    otp: ['', Validators.required]
  });
  email: string | null = null;
  token: string | undefined;

  constructor(
    private fb: FormBuilder,
    private router: Router,
    private route: ActivatedRoute,
    private registerService:RegisterService,
    private jwtHelper: JwtHelperService
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.email = params['email'] || null;
      console.log(this.email)
    });
  }

  onSubmit() {
    const otp = this.otpForm.value.otp || '';
    console.log(otp)
    if(this.email){
    this.registerService.validateOtp(this.email, otp)
      .subscribe(
        (response: any) => {
          console.log('OTP validation response:', response);
          alert(response);
          this.token = localStorage.getItem('jwtToken') || '{}';
      
        const decodedToken = this.jwtHelper.decodeToken(this.token);
          
        console.log(decodedToken);
        if (decodedToken?.role) {
          if (decodedToken.role === UserRoles.customer || decodedToken.role === UserRoles.merchant) {
            this.router.navigate(['/home']);
          } else if (decodedToken.role === UserRoles.internal) {
            this.router.navigate(['/intuser-home']);
          }
            else if (decodedToken.role === UserRoles.admin) {
              this.router.navigate(['/home-admin']);
            
          }
          
          // Handle the response as needed
        }
        // this.router.navigate(['/login']);
          // Handle the response as needed
        },
        (error: any) => {
          console.error('OTP validation error:', error);
          alert("INVALID OTP");
          this.router.navigate(['/login']);
          // Handle the error as needed
        }
      );
    
  }
  else{
    console.error('Email is null');
  }
}
}
