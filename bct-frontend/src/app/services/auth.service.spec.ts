// auth.service.spec.ts — Karma/Jasmine compatible (pas de jest.mock)
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';

// ── Mock du module keycloak.service ──────────────────────────────
// On remplace l'import ES module par un objet contrôlable
const mockKeycloak = {
  authenticated: false as boolean | undefined,
  tokenParsed: null as any,
  login: jasmine.createSpy('login'),
  logout: jasmine.createSpy('logout'),
};

// Patch le module avant que le service ne l'importe
// (Angular TestBed recompile à chaque test, donc on patche via le prototype)
import * as keycloakModule from './keycloak.service';
Object.defineProperty(keycloakModule, 'default', {
  get: () => mockKeycloak,
  configurable: true,
});

describe('AuthService', () => {
  let service: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [AuthService] });
    service = TestBed.inject(AuthService);
    // Reset mock state
    mockKeycloak.authenticated = false;
    mockKeycloak.tokenParsed = null;
    mockKeycloak.login.calls.reset();
    mockKeycloak.logout.calls.reset();
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

  it('isLoggedIn() → false si authenticated est undefined', () => {
    mockKeycloak.authenticated = undefined;
    expect(service.isLoggedIn()).toBeFalse();
  });

  // ── getRoles ────────────────────────────────────────────────────

  it('getRoles() → [] si tokenParsed null', () => {
    mockKeycloak.tokenParsed = null;
    expect(service.getRoles()).toEqual([]);
  });

  it('getRoles() → [] si realm_access absent', () => {
    mockKeycloak.tokenParsed = {};
    expect(service.getRoles()).toEqual([]);
  });

  it('getRoles() → [] si roles absent dans realm_access', () => {
    mockKeycloak.tokenParsed = { realm_access: {} };
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

  it('hasAnyRole() → false si tokenParsed null', () => {
    mockKeycloak.tokenParsed = null;
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

  it('isAgent() → false si pas ROLE_AGENT', () => {
    mockKeycloak.tokenParsed = { realm_access: { roles: ['ROLE_ADMIN'] } };
    expect(service.isAgent()).toBeFalse();
  });

  it('isManager() → true si ROLE_MANAGER', () => {
    mockKeycloak.tokenParsed = { realm_access: { roles: ['ROLE_MANAGER'] } };
    expect(service.isManager()).toBeTrue();
  });

  it('isManager() → false si pas ROLE_MANAGER', () => {
    mockKeycloak.tokenParsed = { realm_access: { roles: ['ROLE_AGENT'] } };
    expect(service.isManager()).toBeFalse();
  });

  it('isAuditor() → true si ROLE_AUDITOR', () => {
    mockKeycloak.tokenParsed = { realm_access: { roles: ['ROLE_AUDITOR'] } };
    expect(service.isAuditor()).toBeTrue();
  });

  it('isAuditor() → false si pas ROLE_AUDITOR', () => {
    mockKeycloak.tokenParsed = { realm_access: { roles: ['ROLE_AGENT'] } };
    expect(service.isAuditor()).toBeFalse();
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

  it('getUsername() → undefined si preferred_username absent', () => {
    mockKeycloak.tokenParsed = { sub: 'some-id' };
    expect(service.getUsername()).toBeUndefined();
  });

  // ── login / logout ──────────────────────────────────────────────

  it('login() → appelle keycloak.login()', () => {
    service.login();
    expect(mockKeycloak.login).toHaveBeenCalled();
  });

  it('logout() → appelle keycloak.logout()', () => {
    service.logout();
    expect(mockKeycloak.logout).toHaveBeenCalled();
  });
});
