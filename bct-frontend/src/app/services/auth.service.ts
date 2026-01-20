import { Injectable } from '@angular/core';
import keycloak from './keycloak.service';

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  isLoggedIn(): boolean {
    return !!keycloak.authenticated;
  }

  getUsername(): string | undefined {
    return keycloak.tokenParsed?.['preferred_username'];
  }

  // ===== Roles (WITH "ROLE_" prefix to match Keycloak) =====
  isAdmin(): boolean {
    return keycloak.hasRealmRole('ROLE_ADMIN');
  }

  isAgent(): boolean {
    return keycloak.hasRealmRole('ROLE_AGENT');
  }

  isManager(): boolean {
    return keycloak.hasRealmRole('ROLE_MANAGER');
  }

  isAuditor(): boolean {
    return keycloak.hasRealmRole('ROLE_AUDITOR');
  }

  hasAnyRole(roles: string[]): boolean {
    // ✅ Roles include "ROLE_" prefix
    return roles.some(role => keycloak.hasRealmRole(role));
  }

  // ===== Debug helper =====
  getRoles(): string[] {
    return keycloak.realmAccess?.roles || [];
  }

  // ===== Auth actions =====
  login(): void {
    keycloak.login();
  }

  logout(): void {
    keycloak.logout({ redirectUri: window.location.origin });
  }
}