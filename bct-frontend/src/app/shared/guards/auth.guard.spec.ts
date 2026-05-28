import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AuthGuard } from './auth.guard';

// Mock keycloak
import * as keycloakModule from '../../services/keycloak.service';
const mockKeycloak: any = {
  authenticated: false,
  login: jasmine.createSpy('login'),
};
Object.defineProperty(keycloakModule, 'default', {
  get: () => mockKeycloak,
  configurable: true,
});

describe('AuthGuard', () => {
  let guard: AuthGuard;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    router = jasmine.createSpyObj('Router', ['navigate']);
    TestBed.configureTestingModule({
      providers: [
        AuthGuard,
        { provide: Router, useValue: router },
      ],
    });
    guard = TestBed.inject(AuthGuard);
    mockKeycloak.authenticated = false;
    mockKeycloak.login.calls.reset();
  });

  it('canActivate() → true si authentifié', () => {
    mockKeycloak.authenticated = true;
    expect(guard.canActivate()).toBeTrue();
  });

  it('canActivate() → false et appelle keycloak.login() si non authentifié', () => {
    mockKeycloak.authenticated = false;
    const result = guard.canActivate();
    expect(result).toBeFalse();
    expect(mockKeycloak.login).toHaveBeenCalled();
  });

  it('canActivate() → false si authenticated est undefined', () => {
    mockKeycloak.authenticated = undefined;
    const result = guard.canActivate();
    expect(result).toBeFalse();
  });
});
