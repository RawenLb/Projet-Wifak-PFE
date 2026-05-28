import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { KeycloakAdminService, KeycloakUser, CreateUserRequest, RoleDTO } from './keycloak-admin.service';

// Mock keycloak module
import * as keycloakModule from './keycloak.service';
const mockKeycloak: any = {
  authenticated: true,
  tokenParsed: {
    preferred_username: 'admin',
    given_name: 'Admin',
    family_name: 'User',
    name: 'Admin User',
    email: 'admin@test.com',
    sub: 'user-id-123',
    realm_access: { roles: ['ROLE_ADMIN'] },
    resource_access: {},
  },
  token: 'mock-token',
  hasRealmRole: jasmine.createSpy('hasRealmRole').and.returnValue(false),
  hasResourceRole: jasmine.createSpy('hasResourceRole').and.returnValue(false),
  logout: jasmine.createSpy('logout'),
};
Object.defineProperty(keycloakModule, 'default', {
  get: () => mockKeycloak,
  configurable: true,
});

describe('KeycloakAdminService', () => {
  let service: KeycloakAdminService;
  let httpMock: HttpTestingController;
  const API = 'http://localhost:8088/api/admin';

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [KeycloakAdminService],
    });
    service = TestBed.inject(KeycloakAdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // ── getUsers ────────────────────────────────────────────────────

  it('getUsers() → GET /api/admin/users', () => {
    const users: KeycloakUser[] = [
      { username: 'alice', email: 'alice@test.com', firstName: 'Alice', lastName: 'A', enabled: true },
    ];
    service.getUsers().subscribe(res => expect(res).toEqual(users));
    httpMock.expectOne(`${API}/users`).flush(users);
  });

  // ── getUserById ─────────────────────────────────────────────────

  it('getUserById() → GET /api/admin/users/:id', () => {
    const user: KeycloakUser = { id: 'u1', username: 'bob', email: 'bob@test.com', firstName: 'Bob', lastName: 'B', enabled: true };
    service.getUserById('u1').subscribe(res => expect(res.username).toBe('bob'));
    httpMock.expectOne(`${API}/users/u1`).flush(user);
  });

  // ── searchUsers ─────────────────────────────────────────────────

  it('searchUsers() → GET /api/admin/users/search?query=...', () => {
    service.searchUsers('alice').subscribe();
    httpMock.expectOne(`${API}/users/search?query=alice`).flush([]);
  });

  // ── createUser ──────────────────────────────────────────────────

  it('createUser() → POST /api/admin/users', () => {
    const req: CreateUserRequest = {
      username: 'newuser', email: 'new@test.com',
      firstName: 'New', lastName: 'User', enabled: true, roles: ['ROLE_AGENT'],
    };
    service.createUser(req).subscribe(res => expect(res.userId).toBe('new-id'));
    const r = httpMock.expectOne(`${API}/users`);
    expect(r.request.method).toBe('POST');
    expect(r.request.body).toEqual(req);
    r.flush({ userId: 'new-id' });
  });

  // ── updateUser ──────────────────────────────────────────────────

  it('updateUser() → PUT /api/admin/users/:id', () => {
    service.updateUser('u1', { email: 'updated@test.com' }).subscribe();
    const r = httpMock.expectOne(`${API}/users/u1`);
    expect(r.request.method).toBe('PUT');
    r.flush({ message: 'updated' });
  });

  // ── deleteUser ──────────────────────────────────────────────────

  it('deleteUser() → DELETE /api/admin/users/:id', () => {
    service.deleteUser('u1').subscribe();
    const r = httpMock.expectOne(`${API}/users/u1`);
    expect(r.request.method).toBe('DELETE');
    r.flush({ message: 'deleted' });
  });

  // ── toggleUserStatus ────────────────────────────────────────────

  it('toggleUserStatus() → PATCH /api/admin/users/:id/status', () => {
    service.toggleUserStatus('u1', false).subscribe();
    const r = httpMock.expectOne(`${API}/users/u1/status?enabled=false`);
    expect(r.request.method).toBe('PATCH');
    r.flush({ message: 'updated' });
  });

  // ── sendPasswordResetEmail ──────────────────────────────────────

  it('sendPasswordResetEmail() → POST /api/admin/users/:id/reset-password', () => {
    service.sendPasswordResetEmail('u1').subscribe();
    const r = httpMock.expectOne(`${API}/users/u1/reset-password`);
    expect(r.request.method).toBe('POST');
    r.flush({ message: 'sent' });
  });

  // ── getAllRoles ─────────────────────────────────────────────────

  it('getAllRoles() → GET /api/admin/roles', () => {
    const roles: RoleDTO[] = [{ name: 'ROLE_ADMIN' }, { name: 'ROLE_AGENT' }];
    service.getAllRoles().subscribe(res => expect(res.length).toBe(2));
    httpMock.expectOne(`${API}/roles`).flush(roles);
  });

  // ── getUserRoles ────────────────────────────────────────────────

  it('getUserRoles() → GET /api/admin/users/:id/roles', () => {
    service.getUserRoles('u1').subscribe();
    httpMock.expectOne(`${API}/users/u1/roles`).flush([]);
  });

  // ── assignRoles ─────────────────────────────────────────────────

  it('assignRoles() → POST /api/admin/users/:id/roles', () => {
    service.assignRoles('u1', ['ROLE_AGENT']).subscribe();
    const r = httpMock.expectOne(`${API}/users/u1/roles`);
    expect(r.request.method).toBe('POST');
    r.flush({ message: 'assigned' });
  });

  // ── removeRoles ─────────────────────────────────────────────────

  it('removeRoles() → DELETE /api/admin/users/:id/roles', () => {
    service.removeRoles('u1', ['ROLE_AGENT']).subscribe();
    const r = httpMock.expectOne(`${API}/users/u1/roles`);
    expect(r.request.method).toBe('DELETE');
    r.flush({ message: 'removed' });
  });

  // ── getUsersByRole ──────────────────────────────────────────────

  it('getUsersByRole() → GET /api/admin/roles/:roleName/users', () => {
    service.getUsersByRole('ROLE_AGENT').subscribe();
    httpMock.expectOne(`${API}/roles/ROLE_AGENT/users`).flush([]);
  });

  // ── méthodes locales (token) ────────────────────────────────────

  it('getUsername() → retourne preferred_username', () => {
    expect(service.getUsername()).toBe('admin');
  });

  it('getFullName() → retourne prénom + nom', () => {
    expect(service.getFullName()).toBe('Admin User');
  });

  it('getEmail() → retourne email du token', () => {
    expect(service.getEmail()).toBe('admin@test.com');
  });

  it('getUserId() → retourne sub du token', () => {
    expect(service.getUserId()).toBe('user-id-123');
  });

  it('isAuthenticated() → true si keycloak.authenticated', () => {
    expect(service.isAuthenticated()).toBeTrue();
  });

  it('getToken() → retourne le token', () => {
    expect(service.getToken()).toBe('mock-token');
  });

  it('getPrimaryRole() → Administrateur si ROLE_ADMIN', () => {
    mockKeycloak.tokenParsed.realm_access.roles = ['ROLE_ADMIN'];
    expect(service.getPrimaryRole()).toBe('Administrateur');
  });

  it('getPrimaryRole() → Manager si ROLE_MANAGER', () => {
    mockKeycloak.tokenParsed.realm_access.roles = ['ROLE_MANAGER'];
    expect(service.getPrimaryRole()).toBe('Manager');
  });

  it('getPrimaryRole() → Chargé de Déclaration si ROLE_AGENT', () => {
    mockKeycloak.tokenParsed.realm_access.roles = ['ROLE_AGENT'];
    expect(service.getPrimaryRole()).toBe('Chargé de Déclaration');
  });

  it('getPrimaryRole() → Auditeur si ROLE_AUDITOR', () => {
    mockKeycloak.tokenParsed.realm_access.roles = ['ROLE_AUDITOR'];
    expect(service.getPrimaryRole()).toBe('Auditeur');
  });

  it('getPrimaryRole() → Utilisateur si rôle inconnu', () => {
    mockKeycloak.tokenParsed.realm_access.roles = [];
    expect(service.getPrimaryRole()).toBe('Utilisateur');
  });

  it('getCurrentUserRoles() → retourne les rôles realm', () => {
    mockKeycloak.tokenParsed.realm_access.roles = ['ROLE_ADMIN'];
    const roles = service.getCurrentUserRoles();
    expect(roles).toContain('ROLE_ADMIN');
  });
});
