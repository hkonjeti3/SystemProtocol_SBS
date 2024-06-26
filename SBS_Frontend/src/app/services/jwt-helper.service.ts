import { Injectable } from '@angular/core';
import { Router } from '@angular/router';

@Injectable({
  providedIn: 'root'
})
export class JwtHelperService {
  constructor(private router: Router) {}

  decodeToken(token: string): DecodedToken  {
    // try {
      const decodedToken = JSON.parse(atob(token.split('.')[1]));
      return {
        userId: decodedToken.userId,
        email: decodedToken.email,
        username: decodedToken.username,
        exp: decodedToken.exp,
        iat: decodedToken.iat,
        role: decodedToken.role
      };
    // } catch (error) {
    //   console.error('Error decoding token:', error);
     
    // }
  }

  removeToken(token: string): void {
    localStorage.removeItem(token);
  }

  isTokenExpired(token: string): boolean {
    const decodedToken = this.decodeToken(token);
    console.log(decodedToken)
    if (decodedToken?.exp === undefined) {
      return false;
    }
    const expirationDate = new Date(0);
    expirationDate.setUTCSeconds(decodedToken.exp);
    return expirationDate.valueOf() <= new Date().valueOf();
  }

  // checkSessionValidityMultiple(role1: number | undefined, role2: number | undefined): Boolean {
  //   const token = localStorage.getItem('jwtToken')|| '{}';
  //   const decodedToken = this.decodeToken(token)
  //   if (token && this.isTokenExpired(token) || (role1 != decodedToken?.role && role2 != decodedToken?.role)) {
  //     localStorage.removeItem('jwtToken');
  //     this.router.navigate(['/login']);
  //     return false;
  //   }
  //   return true;
  // }

  checkSessionValidity(role: number | undefined): Boolean {
    const token = localStorage.getItem('jwtToken')|| '{}';
    const decodedToken = this.decodeToken(token)
    if (token && this.isTokenExpired(token) || role != decodedToken?.role) {
      localStorage.removeItem('jwtToken');
      this.router.navigate(['/login']);
      return false;
    }
    return true;
  }

  checkSessionValidityMultiple(role1: number | undefined, role2: number | undefined): Boolean {
    const token = localStorage.getItem('jwtToken')|| '{}';
    const decodedToken = this.decodeToken(token)
    if (token && this.isTokenExpired(token) || (role1 != decodedToken?.role && role2 != decodedToken?.role)) {
      localStorage.removeItem('jwtToken');
      this.router.navigate(['/login']);
      return false;
    }
    return true;
  }

}
interface DecodedToken {
  userId: number;
  email: string;
  exp: number;
  username: string;
  iat: number;
  role: number;
}
