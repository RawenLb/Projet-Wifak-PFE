import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

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

  // ========== USER MANAGEMENT ==========

  /**
   * Get all users
   */
  getUsers(): Observable<KeycloakUser[]> {
    return this.http.get<KeycloakUser[]>(`${this.API_URL}/users`);
  }

  /**
   * Get user by ID
   */
  getUserById(userId: string): Observable<KeycloakUser> {
    return this.http.get<KeycloakUser>(`${this.API_URL}/users/${userId}`);
  }

  /**
   * Search users by username or email
   */
  searchUsers(query: string): Observable<KeycloakUser[]> {
    return this.http.get<KeycloakUser[]>(`${this.API_URL}/users/search?query=${query}`);
  }

  /**
   * Create a new user
   */
  createUser(user: CreateUserRequest): Observable<any> {
    return this.http.post(`${this.API_URL}/users`, user);
  }

  /**
   * Update user
   */
  updateUser(userId: string, user: Partial<KeycloakUser>): Observable<any> {
    return this.http.put(`${this.API_URL}/users/${userId}`, user);
  }

  /**
   * Delete user
   */
  deleteUser(userId: string): Observable<any> {
    return this.http.delete(`${this.API_URL}/users/${userId}`);
  }

  /**
   * Toggle user status (enable/disable)
   */
  toggleUserStatus(userId: string, enabled: boolean): Observable<any> {
    return this.http.patch(`${this.API_URL}/users/${userId}/status?enabled=${enabled}`, {});
  }

  /**
   * Send password reset email
   */
  sendPasswordResetEmail(userId: string): Observable<any> {
    return this.http.post(`${this.API_URL}/users/${userId}/reset-password`, {});
  }

  // ========== ROLE MANAGEMENT ==========

  /**
   * Get all realm roles
   */
  getAllRoles(): Observable<RoleDTO[]> {
    return this.http.get<RoleDTO[]>(`${this.API_URL}/roles`);
  }

  /**
   * Get user's roles
   */
  getUserRoles(userId: string): Observable<RoleDTO[]> {
    return this.http.get<RoleDTO[]>(`${this.API_URL}/users/${userId}/roles`);
  }

  /**
   * Assign roles to user
   */
  assignRoles(userId: string, roleNames: string[]): Observable<any> {
    return this.http.post(`${this.API_URL}/users/${userId}/roles`, roleNames);
  }

  /**
   * Remove roles from user
   */
  removeRoles(userId: string, roleNames: string[]): Observable<any> {
    return this.http.delete(`${this.API_URL}/users/${userId}/roles`, { body: roleNames });
  }

  /**
   * Get users by role
   */
  getUsersByRole(roleName: string): Observable<KeycloakUser[]> {
    return this.http.get<KeycloakUser[]>(`${this.API_URL}/roles/${roleName}/users`);
  }
}