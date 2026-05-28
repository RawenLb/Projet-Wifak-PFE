import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { RoleGuard } from './role.guard';
import { AuthService } from '../../services/auth.service';

describe('RoleGuard', () => {
  let guard: RoleGuard;
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    authService = jasmine.createSpyObj('AuthService', [
      'isLoggedIn', 'getRoles', 'hasAnyRole',
    ]);
    router = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        RoleGuard,
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
      ],
    });
    guard = TestBed.inject(RoleGuard);
  });

  function makeRoute(roles: string[]): ActivatedRouteSnapshot {
    const route = new ActivatedRouteSnapshot();
    (route as any).data = { roles };
    return route;
  }

  it('canActivate() → false et redirige vers / si non connecté', () => {
    authService.isLoggedIn.and.returnValue(false);
    const result = guard.canActivate(makeRoute(['ROLE_ADMIN']));
    expect(result).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });

  it('canActivate() → true si connecté et rôle correct', () => {
    authService.isLoggedIn.and.returnValue(true);
    authService.getRoles.and.returnValue(['ROLE_ADMIN']);
    authService.hasAnyRole.and.returnValue(true);
    const result = guard.canActivate(makeRoute(['ROLE_ADMIN']));
    expect(result).toBeTrue();
  });

  it('canActivate() → false et redirige vers /unauthorized si rôle incorrect', () => {
    authService.isLoggedIn.and.returnValue(true);
    authService.getRoles.and.returnValue(['ROLE_AGENT']);
    authService.hasAnyRole.and.returnValue(false);
    const result = guard.canActivate(makeRoute(['ROLE_ADMIN']));
    expect(result).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/unauthorized']);
  });

  it('canActivate() → true si plusieurs rôles requis et user en a un', () => {
    authService.isLoggedIn.and.returnValue(true);
    authService.getRoles.and.returnValue(['ROLE_MANAGER']);
    authService.hasAnyRole.and.returnValue(true);
    const result = guard.canActivate(makeRoute(['ROLE_ADMIN', 'ROLE_MANAGER']));
    expect(result).toBeTrue();
  });
});
