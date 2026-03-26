// src/app/guards/role-redirect.guard.ts
// ✅ Redirige automatiquement vers le bon espace selon le rôle après login

import { Injectable } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Injectable({ providedIn: 'root' })
export class RoleRedirectGuard implements CanActivate {

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  canActivate(): boolean {
    if (!this.authService.isLoggedIn()) {
      this.authService.login();
      return false;
    }

    const roles = this.authService.getRoles();

    console.log('[RoleRedirectGuard] Redirecting based on roles:', roles);

    // Priorité : ADMIN > MANAGER > AGENT > AUDITOR
    if (roles.includes('ROLE_ADMIN')) {
      this.router.navigate(['/dashboard']);
      return false;
    }

    if (roles.includes('ROLE_MANAGER')) {
      this.router.navigate(['/manager/dashboard']);
      return false;
    }

    if (roles.includes('ROLE_AGENT')) {
      this.router.navigate(['/agent/dashboard']);
      return false;
    }

    if (roles.includes('ROLE_AUDITOR')) {
      this.router.navigate(['/auditor']);
      return false;
    }

    // Rôle inconnu → page unauthorized
    this.router.navigate(['/unauthorized']);
    return false;
  }
}