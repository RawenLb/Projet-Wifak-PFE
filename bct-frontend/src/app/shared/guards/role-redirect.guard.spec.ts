import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RoleRedirectGuard } from './Role-redirect.guard';
import { AuthService } from '../../services/auth.service';

describe('RoleRedirectGuard', () => {
  let guard: RoleRedirectGuard;
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    authService = jasmine.createSpyObj('AuthService', ['isLoggedIn', 'getRoles']);
    router = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        RoleRedirectGuard,
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
      ],
    });
    guard = TestBed.inject(RoleRedirectGuard);
  });

  it('canActivate() → false et redirige vers / si non connecté', () => {
    authService.isLoggedIn.and.returnValue(false);
    const result = guard.canActivate();
    expect(result).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });

  it('canActivate() → redirige vers /dashboard si ROLE_ADMIN', () => {
    authService.isLoggedIn.and.returnValue(true);
    authService.getRoles.and.returnValue(['ROLE_ADMIN']);
    guard.canActivate();
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('canActivate() → redirige vers /manager/dashboard si ROLE_MANAGER', () => {
    authService.isLoggedIn.and.returnValue(true);
    authService.getRoles.and.returnValue(['ROLE_MANAGER']);
    guard.canActivate();
    expect(router.navigate).toHaveBeenCalledWith(['/manager/dashboard']);
  });

  it('canActivate() → redirige vers /agent/dashboard si ROLE_AGENT', () => {
    authService.isLoggedIn.and.returnValue(true);
    authService.getRoles.and.returnValue(['ROLE_AGENT']);
    guard.canActivate();
    expect(router.navigate).toHaveBeenCalledWith(['/agent/dashboard']);
  });

  it('canActivate() → redirige vers /auditor si ROLE_AUDITOR', () => {
    authService.isLoggedIn.and.returnValue(true);
    authService.getRoles.and.returnValue(['ROLE_AUDITOR']);
    guard.canActivate();
    expect(router.navigate).toHaveBeenCalledWith(['/auditor']);
  });

  it('canActivate() → redirige vers /unauthorized si rôle inconnu', () => {
    authService.isLoggedIn.and.returnValue(true);
    authService.getRoles.and.returnValue(['ROLE_UNKNOWN']);
    guard.canActivate();
    expect(router.navigate).toHaveBeenCalledWith(['/unauthorized']);
  });

  it('canActivate() → priorité ADMIN > MANAGER', () => {
    authService.isLoggedIn.and.returnValue(true);
    authService.getRoles.and.returnValue(['ROLE_ADMIN', 'ROLE_MANAGER']);
    guard.canActivate();
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('canActivate() → retourne toujours false (redirige)', () => {
    authService.isLoggedIn.and.returnValue(true);
    authService.getRoles.and.returnValue(['ROLE_ADMIN']);
    expect(guard.canActivate()).toBeFalse();
  });
});
