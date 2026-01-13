import { Component } from '@angular/core';
import keycloak from './services/keycloak.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent {
  title = 'bct-frontend';

  login() {
    keycloak.login();
  }

  logout() {
    keycloak.logout({
      redirectUri: 'http://localhost:4200'
    });
  }

  register() {
    keycloak.register();
  }

  isLoggedIn(): boolean {
    return !!keycloak.authenticated;
  }
}
