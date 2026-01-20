import { Injectable } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import keycloak from '../services/keycloak.service';

@Injectable({ 
  providedIn: 'root' 
})
export class AuthGuard implements CanActivate {
  
  constructor(private router: Router) {}

  canActivate(): boolean {
    if (keycloak.authenticated) {
      return true;
    }
    
    console.log('❌ Not authenticated - redirecting to login');
    keycloak.login();
    return false;
  }
}