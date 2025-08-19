import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { UserService } from '../../../../core/services/user.service'; // Update with the correct path
import { ReactiveFormsModule } from '@angular/forms';
import { decodeToken } from '../../../../core/utils/jwt-helper';
import { Router } from '@angular/router';

@Component({
  selector: 'app-update-component',
  templateUrl: './customer-profile-update.component.html',
  styleUrls: ['./customer-profile-update.component.css']
})
export class CustomerProfileUpdateComponent implements OnInit {
  updateForm!: FormGroup;
  userData: any;
  token: string | undefined;
  decodedToken: any;
  id: any;
  updatedData: any;

  constructor(private formBuilder: FormBuilder, private userService: UserService,private router: Router) {}

  ngOnInit() {
    this.token = localStorage.getItem('jwtToken') || '{}';
    this.decodedToken = decodeToken(this.token);
    this.userData = history.state.userData;
    this.initForm();
  }

  initForm() {
    this.updateForm = this.formBuilder.group({
      firstName: [this.userData.firstName, Validators.required],
      lastName: [this.userData.lastName, Validators.required],
      email: [this.userData.emailAddress, [Validators.required, Validators.email]],
      address: [this.userData.address],
      phoneNumber: [this.userData.phoneNumber]
      // Add more form controls as needed
    });
  }

  onSubmit() {
    if (this.updateForm.valid) {
      
      
       this.updatedData = {
        userId: this.userData.userId,
        firstName: this.updateForm.value.firstName,
        lastName: this.updateForm.value.lastName,
        emailAddress: this.updateForm.value.email,
        address: this.updateForm.value.address,
        phoneNumber: this.updateForm.value.phoneNumber
        // Add more fields as needed
      };
      const userDetails: any = {
        user:{
          userId: this.decodedToken.userId 
        },
        updateData: JSON.stringify(this.updatedData)

      };
      console.log(userDetails)
    
      this.userService.updateUserData(userDetails).subscribe(
        () => {
          console.log('User data updated successfully');
          this.router.navigate(['/profile']);
          // Optionally, you can navigate to another page or show a success message
        },
        (error: any) => {
          console.error('Error updating user data:', error);
          alert('Failed to update user data. Please try again.');
        }
      );
      
    }
  }
}
