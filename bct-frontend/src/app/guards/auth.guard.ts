import { Injectable } from '@angular/core';
import { CanActivate } from '@angular/router';
import keycloak from '../services/keycloak.service';

@Injectable({ providedIn: 'root' })
export class AuthGuard implements CanActivate {
  canActivate(): boolean {
    return !!keycloak.authenticated;
  }
}
