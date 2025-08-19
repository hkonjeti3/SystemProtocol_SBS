// funds.component.ts
import { Component } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-customer-funds',
  templateUrl: './customer-funds.component.html',
  styleUrls: ['./customer-funds.component.css']
})
export class CustomerFundsComponent {
  constructor(private router: Router) {}

  performTransfer(selectedOption: string) {
    // Redirect to another component based on the selected option
    switch (selectedOption) {

      case 'within-bank':
        this.router.navigate(['/tf_within']); // Redirect to tf_within component
        break;
      case 'outside-bank':
        this.router.navigate(['/tf_outside']);
        break;
      default:
        // Handle other cases or show an error message
        console.error('Invalid option selected');
        break;
    }
  }
}

