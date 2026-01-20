import { Injectable } from '@angular/core';
import { CanActivate, ActivatedRouteSnapshot, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Injectable({
  providedIn: 'root'
})
export class RoleGuard implements CanActivate {

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  canActivate(route: ActivatedRouteSnapshot): boolean {
    // Get required roles from route data
    const expectedRoles: string[] = route.data['roles'];

    console.log('🔒 RoleGuard checking access...');
    console.log('   Required roles:', expectedRoles);
    console.log('   User roles:', this.authService.getRoles());

    // Check if user is logged in
    if (!this.authService.isLoggedIn()) {
      console.log('❌ User not logged in - redirecting to login');
      this.authService.login();
      return false;
    }

    // Check if user has required role
    if (this.authService.hasAnyRole(expectedRoles)) {
      console.log('✅ Access granted');
      return true;
    }

    // Access denied
    console.log('❌ Access denied - user lacks required role');
    this.router.navigate(['/unauthorized']);
    return false;
  }
}