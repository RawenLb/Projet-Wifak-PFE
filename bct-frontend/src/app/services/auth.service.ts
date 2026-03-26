// src/app/services/auth.service.ts
// ✅ Fix : lire depuis tokenParsed.realm_access au lieu de keycloak.realmAccess

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

  // ===== Roles =====
  isAdmin(): boolean {
    return this.getRoles().includes('ROLE_ADMIN');
  }

  isAgent(): boolean {
    return this.getRoles().includes('ROLE_AGENT');
  }

  isManager(): boolean {
    return this.getRoles().includes('ROLE_MANAGER');
  }

  isAuditor(): boolean {
    return this.getRoles().includes('ROLE_AUDITOR');
  }

  // ✅ FIX : tokenParsed.realm_access.roles (snake_case) est toujours disponible
  //    keycloak.realmAccess (camelCase, API JS) peut être undefined au runtime
  getRoles(): string[] {
    const token = keycloak.tokenParsed as any;
    return token?.realm_access?.roles ?? [];
  }

  // ✅ FIX : utilise getRoles() au lieu de hasRealmRole() pour la même raison
  hasAnyRole(roles: string[]): boolean {
    const userRoles = this.getRoles();
    return roles.some(role => userRoles.includes(role));
  }

  // ===== Auth actions =====
  login(): void {
    keycloak.login();
  }

  logout(): void {
    keycloak.logout({ redirectUri: window.location.origin });
  }
}