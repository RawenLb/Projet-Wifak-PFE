import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import keycloak from './keycloak.service';

export interface KeycloakUser {
  id?: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  enabled: boolean;
  emailVerified?: boolean;
  createdTimestamp?: number;
  roles?: string[];
}

// ✅ MODIFIÉ : password supprimé — l'employé le définit lui-même via l'email d'activation
export interface CreateUserRequest {
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  enabled: boolean;
  roles: string[];
}

export interface RoleDTO {
  id?: string;
  name: string;
  description?: string;
}

@Injectable({
  providedIn: 'root'
})
export class KeycloakAdminService {

  private readonly API_URL = 'http://localhost:8088/api/admin';

  constructor(private http: HttpClient) {}

  // ================= USER MANAGEMENT =================

  getUsers(): Observable<KeycloakUser[]> {
    return this.http.get<KeycloakUser[]>(`${this.API_URL}/users`);
  }

  getUserById(userId: string): Observable<KeycloakUser> {
    return this.http.get<KeycloakUser>(`${this.API_URL}/users/${userId}`);
  }

  searchUsers(query: string): Observable<KeycloakUser[]> {
    return this.http.get<KeycloakUser[]>(
      `${this.API_URL}/users/search?query=${query}`
    );
  }

  createUser(user: CreateUserRequest): Observable<any> {
    return this.http.post(`${this.API_URL}/users`, user);
  }

  updateUser(userId: string, user: Partial<KeycloakUser>): Observable<any> {
    return this.http.put(`${this.API_URL}/users/${userId}`, user);
  }

  deleteUser(userId: string): Observable<any> {
    return this.http.delete(`${this.API_URL}/users/${userId}`);
  }

  toggleUserStatus(userId: string, enabled: boolean): Observable<any> {
    return this.http.patch(
      `${this.API_URL}/users/${userId}/status?enabled=${enabled}`,
      {}
    );
  }

  /**
   * ✅ Renvoyer l'email d'activation (si l'employé n'a pas reçu le premier email)
   */
  sendPasswordResetEmail(userId: string): Observable<any> {
    return this.http.post(
      `${this.API_URL}/users/${userId}/reset-password`,
      {}
    );
  }

  // ================= ROLE MANAGEMENT =================

  getAllRoles(): Observable<RoleDTO[]> {
    return this.http.get<RoleDTO[]>(`${this.API_URL}/roles`);
  }

  getUserRoles(userId: string): Observable<RoleDTO[]> {
    return this.http.get<RoleDTO[]>(
      `${this.API_URL}/users/${userId}/roles`
    );
  }

  assignRoles(userId: string, roleNames: string[]): Observable<any> {
    return this.http.post(
      `${this.API_URL}/users/${userId}/roles`,
      roleNames
    );
  }

  removeRoles(userId: string, roleNames: string[]): Observable<any> {
    return this.http.delete(
      `${this.API_URL}/users/${userId}/roles`,
      { body: roleNames }
    );
  }

  getUsersByRole(roleName: string): Observable<KeycloakUser[]> {
    return this.http.get<KeycloakUser[]>(
      `${this.API_URL}/roles/${roleName}/users`
    );
  }

  // ================= USER INFO =================

  getUsername(): string {
    try {
      const tokenParsed = keycloak.tokenParsed;
      return (
        tokenParsed?.['preferred_username'] ||
        tokenParsed?.['name'] ||
        'Utilisateur'
      );
    } catch (error) {
      return 'Utilisateur';
    }
  }

  getFullName(): string {
    try {
      const tokenParsed = keycloak.tokenParsed;
      const firstName = tokenParsed?.['given_name'] || '';
      const lastName = tokenParsed?.['family_name'] || '';
      if (firstName && lastName) return `${firstName} ${lastName}`;
      return tokenParsed?.['name'] || this.getUsername();
    } catch (error) {
      return 'Utilisateur';
    }
  }

  getEmail(): string {
    try {
      return keycloak.tokenParsed?.['email'] || '';
    } catch (error) {
      return '';
    }
  }

  getCurrentUserRoles(): string[] {
    try {
      const tokenParsed = keycloak.tokenParsed;
      // Keycloak stocke les rôles en minuscules dans realm_access.roles
      // ex: ["admin", "manager", "offline_access", "uma_authorization"]
      const realmRoles  = (tokenParsed?.realm_access?.roles || []) as string[];
      const resourceRoles = (tokenParsed?.resource_access?.['bct-app']?.roles || []) as string[];

      // Normaliser : ajouter ROLE_ si absent pour compatibilité Spring Security
      const normalize = (r: string) => r.startsWith('ROLE_') ? r : `ROLE_${r.toUpperCase()}`;
      return [...realmRoles, ...resourceRoles].map(normalize);
    } catch (error) {
      return [];
    }
  }

  hasRole(roleName: string): boolean {
    try {
      // Vérification directe via Keycloak JS (sans préfixe ROLE_)
      const roleWithoutPrefix = roleName.replace(/^ROLE_/i, '').toLowerCase();
      return keycloak.hasRealmRole(roleWithoutPrefix) ||
             keycloak.hasRealmRole(roleName) ||
             keycloak.hasResourceRole(roleWithoutPrefix, 'bct-app') ||
             keycloak.hasResourceRole(roleName, 'bct-app');
    } catch (error) {
      return false;
    }
  }

  getPrimaryRole(): string {
    const roles = this.getCurrentUserRoles();
    if (roles.includes('ROLE_ADMIN'))   return 'Administrateur';
    if (roles.includes('ROLE_MANAGER')) return 'Manager';
    if (roles.includes('ROLE_AGENT'))   return 'Chargé de Déclaration';
    if (roles.includes('ROLE_AUDITOR')) return 'Auditeur';
    return 'Utilisateur';
  }

  getUserId(): string {
    try {
      return keycloak.tokenParsed?.sub || '';
    } catch (error) {
      return '';
    }
  }

  isAuthenticated(): boolean {
    try {
      return keycloak.authenticated || false;
    } catch (error) {
      return false;
    }
  }

  getToken(): string | undefined {
    try {
      return keycloak.token;
    } catch (error) {
      return undefined;
    }
  }

  // ================= LOGOUT =================

  logout(): void {
    localStorage.clear();
    sessionStorage.clear();
    keycloak.logout({ redirectUri: 'http://localhost:4200/login' });
  }
}