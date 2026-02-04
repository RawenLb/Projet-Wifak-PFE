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

export interface CreateUserRequest {
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  password: string;
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

  private readonly API_URL = 'http://localhost:8082/api/admin';

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

  /**
   * ✅ Récupérer le nom d'utilisateur depuis Keycloak
   */
  getUsername(): string {
    try {
      const tokenParsed = keycloak.tokenParsed;
return (
  tokenParsed?.['preferred_username'] ||
  tokenParsed?.['name'] ||
  'Utilisateur'
);
    } catch (error) {
      console.error('Erreur lors de la récupération du username:', error);
      return 'Utilisateur';
    }
  }

  /**
   * ✅ Récupérer le nom complet de l'utilisateur
   */
  getFullName(): string {
    try {
      const tokenParsed = keycloak.tokenParsed;
    const firstName = tokenParsed?.['given_name'] || '';
const lastName = tokenParsed?.['family_name'] || '';

      
      if (firstName && lastName) {
        return `${firstName} ${lastName}`;
      }
      
return tokenParsed?.['name'] || this.getUsername();
    } catch (error) {
      console.error('Erreur lors de la récupération du nom complet:', error);
      return 'Utilisateur';
    }
  }

  /**
   * ✅ Récupérer l'email de l'utilisateur
   */
  getEmail(): string {
    try {
      const tokenParsed = keycloak.tokenParsed;
return tokenParsed?.['email'] || '';
    } catch (error) {
      console.error('Erreur lors de la récupération de l\'email:', error);
      return '';
    }
  }

  /**
   * ✅ Récupérer les rôles de l'utilisateur connecté
   */
  getCurrentUserRoles(): string[] {
    try {
      const tokenParsed = keycloak.tokenParsed;
      const realmRoles = tokenParsed?.realm_access?.roles || [];
      const resourceRoles = tokenParsed?.resource_access?.['bct-app']?.roles || [];
      
      return [...realmRoles, ...resourceRoles];
    } catch (error) {
      console.error('Erreur lors de la récupération des rôles:', error);
      return [];
    }
  }

  /**
   * ✅ Vérifier si l'utilisateur a un rôle spécifique
   */
  hasRole(roleName: string): boolean {
    try {
      return keycloak.hasRealmRole(roleName) || keycloak.hasResourceRole(roleName, 'bct-app');
    } catch (error) {
      console.error('Erreur lors de la vérification du rôle:', error);
      return false;
    }
  }

  /**
   * ✅ Récupérer le rôle principal de l'utilisateur (pour l'affichage)
   */
  getPrimaryRole(): string {
    const roles = this.getCurrentUserRoles();
    
    // Ordre de priorité des rôles
    if (roles.includes('ROLE_ADMIN')) return 'Administrateur';
    if (roles.includes('ROLE_MANAGER')) return 'Manager';
    if (roles.includes('ROLE_AGENT')) return 'Agent';
    if (roles.includes('ROLE_AUDITOR')) return 'Auditeur';
    
    return 'Utilisateur';
  }

  /**
   * ✅ Récupérer l'ID utilisateur
   */
  getUserId(): string {
    try {
      const tokenParsed = keycloak.tokenParsed;
      return tokenParsed?.sub || '';
    } catch (error) {
      console.error('Erreur lors de la récupération de l\'ID utilisateur:', error);
      return '';
    }
  }

  /**
   * ✅ Vérifier si l'utilisateur est authentifié
   */
  isAuthenticated(): boolean {
    try {
      return keycloak.authenticated || false;
    } catch (error) {
      console.error('Erreur lors de la vérification de l\'authentification:', error);
      return false;
    }
  }

  /**
   * ✅ Récupérer le token
   */
  getToken(): string | undefined {
    try {
      return keycloak.token;
    } catch (error) {
      console.error('Erreur lors de la récupération du token:', error);
      return undefined;
    }
  }

  // ================= LOGOUT =================

  logout(): void {
    localStorage.clear();
    sessionStorage.clear();

    keycloak.logout({
      redirectUri: 'http://localhost:4200/login'
    });
  }
}