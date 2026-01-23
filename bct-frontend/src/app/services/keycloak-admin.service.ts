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

  // ================= LOGOUT =================

    logout(): void {
    localStorage.clear();
    sessionStorage.clear();

    keycloak.logout({
      redirectUri: 'http://localhost:4200/login'
    });
  }

}
