import { Component } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-landing-page',
  templateUrl: './landing-page.component.html',
  styleUrl: './landing-page.component.css'
})
export class LandingPageComponent {
  constructor(private router: Router) {}

  navigateToLogin() {
    this.router.navigate(['/login']); // Ensure you have a route set up for '/login'
  }

  navigateToRegister() {
    this.router.navigate(['/register']); // Ensure you have a route set up for '/register'
  }
}
