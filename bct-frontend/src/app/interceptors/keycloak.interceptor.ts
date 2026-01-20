import { Injectable } from '@angular/core';
import {
  HttpInterceptor,
  HttpRequest,
  HttpHandler,
  HttpEvent
} from '@angular/common/http';
import { Observable, from } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import keycloak from '../services/keycloak.service';

@Injectable()
export class KeycloakInterceptor implements HttpInterceptor {

  intercept(
    req: HttpRequest<any>,
    next: HttpHandler
  ): Observable<HttpEvent<any>> {

    // إذا ما فماش user authenticated، نبعث request كيما هو
    if (!keycloak.authenticated) {
      return next.handle(req);
    }

    // نعمل refresh للتوكن إذا قرّب يوفى
    return from(keycloak.updateToken(30)).pipe(
      switchMap(() => {
        const token = keycloak.token;

        if (token) {
          const authReq = req.clone({
            setHeaders: {
              Authorization: `Bearer ${token}`
            }
          });
          return next.handle(authReq);
        }

        return next.handle(req);
      })
    );
  }
}
