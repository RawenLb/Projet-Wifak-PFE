import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';

// Mock du module keycloak
const mockKeycloak: any = {
  authenticated: false,
  tokenParsed: null,
  login: jasmine.createSpy('login'),
  logout: jasmine.createSpy('logout'),
};

jest.mock('./keycloak.service', () => mockKeycloak);

describe('AuthService', () => {
  let service: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [AuthService] });
    service = TestBed.inject(AuthService);
    // Reset mock
    mockKeycloak.authenticated = false;
    mockKeycloak.tokenParsed = null;
  });

  // ── isLoggedIn ──────────────────────────────────────────────────

  it('isLoggedIn() → false si non authentifié', () => {
    mockKeycloak.authenticated = false;
    expect(service.isLoggedIn()).toBeFalse();
  });

  it('isLoggedIn() → true si authentifié', () => {
    mockKeycloak.authenticated = true;
    expect(service.isLoggedIn()).toBeTrue();
  });

  // ── getRoles ────────────────────────────────────────────────────

  it('getRoles() → [] si tokenParsed null', () => {
    mockKeycloak.tokenParsed = null;
    expect(service.getRoles()).toEqual([]);
  });

  it('getRoles() → retourne les rôles du token', () => {
    mockKeycloak.tokenParsed = {
      realm_access: { roles: ['ROLE_ADMIN', 'ROLE_AGENT'] }
    };
    expect(service.getRoles()).toContain('ROLE_ADMIN');
    expect(service.getRoles()).toContain('ROLE_AGENT');
  });

  // ── hasAnyRole ──────────────────────────────────────────────────

  it('hasAnyRole() → true si au moins un rôle correspond', () => {
    mockKeycloak.tokenParsed = {
      realm_access: { roles: ['ROLE_MANAGER'] }
    };
    expect(service.hasAnyRole(['ROLE_ADMIN', 'ROLE_MANAGER'])).toBeTrue();
  });

  it('hasAnyRole() → false si aucun rôle ne correspond', () => {
    mockKeycloak.tokenParsed = {
      realm_access: { roles: ['ROLE_AGENT'] }
    };
    expect(service.hasAnyRole(['ROLE_ADMIN', 'ROLE_MANAGER'])).toBeFalse();
  });

  it('hasAnyRole() → false si roles vide', () => {
    mockKeycloak.tokenParsed = { realm_access: { roles: [] } };
    expect(service.hasAnyRole(['ROLE_ADMIN'])).toBeFalse();
  });

  // ── isAdmin / isAgent / isManager / isAuditor ───────────────────

  it('isAdmin() → true si ROLE_ADMIN', () => {
    mockKeycloak.tokenParsed = { realm_access: { roles: ['ROLE_ADMIN'] } };
    expect(service.isAdmin()).toBeTrue();
  });

  it('isAdmin() → false si pas ROLE_ADMIN', () => {
    mockKeycloak.tokenParsed = { realm_access: { roles: ['ROLE_AGENT'] } };
    expect(service.isAdmin()).toBeFalse();
  });

  it('isAgent() → true si ROLE_AGENT', () => {
    mockKeycloak.tokenParsed = { realm_access: { roles: ['ROLE_AGENT'] } };
    expect(service.isAgent()).toBeTrue();
  });

  it('isManager() → true si ROLE_MANAGER', () => {
    mockKeycloak.tokenParsed = { realm_access: { roles: ['ROLE_MANAGER'] } };
    expect(service.isManager()).toBeTrue();
  });

  it('isAuditor() → true si ROLE_AUDITOR', () => {
    mockKeycloak.tokenParsed = { realm_access: { roles: ['ROLE_AUDITOR'] } };
    expect(service.isAuditor()).toBeTrue();
  });

  // ── getUsername ─────────────────────────────────────────────────

  it('getUsername() → undefined si tokenParsed null', () => {
    mockKeycloak.tokenParsed = null;
    expect(service.getUsername()).toBeUndefined();
  });

  it('getUsername() → retourne le preferred_username', () => {
    mockKeycloak.tokenParsed = { preferred_username: 'rawena' };
    expect(service.getUsername()).toBe('rawena');
  });
});
