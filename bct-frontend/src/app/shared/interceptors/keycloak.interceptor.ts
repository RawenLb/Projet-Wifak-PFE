import { Injectable } from '@angular/core';
import {
  HttpInterceptor,
  HttpRequest,
  HttpHandler,
  HttpEvent
} from '@angular/common/http';
import { Observable, from, throwError } from 'rxjs';
import { switchMap, catchError } from 'rxjs/operators';

import keycloak from '../../services/keycloak.service';

@Injectable()
export class KeycloakInterceptor implements HttpInterceptor {

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {

    // Pas authentifié → envoyer sans token
    if (!keycloak.authenticated) {
      return next.handle(req);
    }

    // Vérifier si le token est disponible directement
    const currentToken = keycloak.token;
    if (currentToken && !this.isTokenExpiringSoon(currentToken)) {
      // Token valide et pas près d'expirer → l'attacher directement
      return next.handle(this.addToken(req, currentToken));
    }

    // Token expiré ou proche d'expiration → forcer le refresh
    return from(keycloak.updateToken(60)).pipe(
      switchMap(() => {
        const token = keycloak.token;
        if (token) {
          return next.handle(this.addToken(req, token));
        }
        // Pas de token après refresh → forcer re-login
        keycloak.login({ redirectUri: window.location.href });
        return throwError(() => new Error('Token not available after refresh'));
      }),
      catchError((err) => {
        console.error('[KeycloakInterceptor] Token refresh failed:', err);
        // Session expirée → re-login
        keycloak.login({ redirectUri: window.location.href });
        return throwError(() => new Error('Session expired, redirecting to login'));
      })
    );
  }

  private addToken(req: HttpRequest<any>, token: string): HttpRequest<any> {
    return req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  private isTokenExpiringSoon(token: string): boolean {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const exp = payload.exp as number;
      const now = Math.floor(Date.now() / 1000);
      return exp - now < 30; // expire dans moins de 30 secondes
    } catch {
      return true; // en cas de doute, forcer le refresh
    }
  }
}
