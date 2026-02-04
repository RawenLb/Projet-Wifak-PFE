import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import keycloak from '../services/keycloak.service';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {
  
  // Test results
  publicResult: string = '';
  adminResult: string = '';
  agentResult: string = '';
  managerResult: string = '';
  auditorResult: string = '';
  
  // Show/hide test panel
  showTests: boolean = false;

  constructor(
    private router: Router, 
    public auth: AuthService,
    private http: HttpClient
  ) {}

  // ✅ NOUVEAU: Redirection automatique selon le rôle au chargement
  ngOnInit(): void {
    // Si l'utilisateur est déjà authentifié, rediriger vers son dashboard
    if (this.isLoggedIn()) {
      this.redirectBasedOnRole();
    }
  }

  /**
   * ✅ NOUVEAU: Rediriger l'utilisateur selon son rôle
   */
  redirectBasedOnRole(): void {
    console.log('🔄 Vérification du rôle pour redirection...');
    
    if (this.auth.isAgent()) {
      console.log('✅ Rôle: AGENT → Redirection vers /agent/declarations');
      this.router.navigate(['/agent/declarations']);
    } 
    else if (this.auth.isAdmin()) {
      console.log('✅ Rôle: ADMIN → Redirection vers /dashboard');
      this.router.navigate(['/dashboard']);
    } 
    else if (this.auth.isManager()) {
      console.log('✅ Rôle: MANAGER → Redirection vers /manager');
      this.router.navigate(['/manager']);
    } 
    else if (this.auth.isAuditor()) {
      console.log('✅ Rôle: AUDITOR → Redirection vers /auditor');
      this.router.navigate(['/auditor']);
    }
    else {
      console.log('⚠️ Rôle non reconnu, reste sur la page home');
    }
  }

  login() {
    keycloak.login();
  }

  logout() {
    keycloak.logout({ redirectUri: 'http://localhost:4200' });
  }

  register() {
    keycloak.register();
  }

  isLoggedIn(): boolean {
    return !!keycloak.authenticated;
  }

  // ✅ MODIFIÉ: goToDashboard maintenant utilise redirectBasedOnRole
  goToDashboard() {
    if (this.isLoggedIn()) {
      this.redirectBasedOnRole();
    } else {
      this.login();
    }
  }

  toggleTests() {
    this.showTests = !this.showTests;
  }

  // ========== BACKEND API TESTS ==========

  testPublic(): void {
    console.log('📡 Testing PUBLIC endpoint...');
    this.publicResult = '⏳ Loading...';
    
    this.http.get('http://localhost:8082/api/test/public/hello', { responseType: 'text' })
      .subscribe({
        next: (response) => {
          this.publicResult = '✅ ' + response;
          console.log('✅ Public endpoint response:', response);
        },
        error: (error) => {
          this.publicResult = '❌ Error: ' + error.message;
          console.error('❌ Public endpoint error:', error);
        }
      });
  }

  testAdmin(): void {
    console.log('📡 Testing ADMIN endpoint...');
    this.adminResult = '⏳ Loading...';
    
    this.http.get('http://localhost:8082/api/test/admin', { responseType: 'text' })
      .subscribe({
        next: (response) => {
          this.adminResult = '✅ ' + response;
          console.log('✅ Admin endpoint response:', response);
        },
        error: (error) => {
          this.adminResult = '❌ Error ' + error.status + ': Access Denied';
          console.error('❌ Admin endpoint error:', error);
        }
      });
  }

  testAgent(): void {
    console.log('📡 Testing AGENT endpoint...');
    this.agentResult = '⏳ Loading...';
    
    this.http.get('http://localhost:8082/api/test/agent', { responseType: 'text' })
      .subscribe({
        next: (response) => {
          this.agentResult = '✅ ' + response;
          console.log('✅ Agent endpoint response:', response);
        },
        error: (error) => {
          this.agentResult = '❌ Error ' + error.status + ': Access Denied';
          console.error('❌ Agent endpoint error:', error);
        }
      });
  }

  testManager(): void {
    console.log('📡 Testing MANAGER endpoint...');
    this.managerResult = '⏳ Loading...';
    
    this.http.get('http://localhost:8082/api/test/manager', { responseType: 'text' })
      .subscribe({
        next: (response) => {
          this.managerResult = '✅ ' + response;
          console.log('✅ Manager endpoint response:', response);
        },
        error: (error) => {
          this.managerResult = '❌ Error ' + error.status + ': Access Denied';
          console.error('❌ Manager endpoint error:', error);
        }
      });
  }

  testAuditor(): void {
    console.log('📡 Testing AUDITOR endpoint...');
    this.auditorResult = '⏳ Loading...';
    
    this.http.get('http://localhost:8082/api/test/auditor', { responseType: 'text' })
      .subscribe({
        next: (response) => {
          this.auditorResult = '✅ ' + response;
          console.log('✅ Auditor endpoint response:', response);
        },
        error: (error) => {
          this.auditorResult = '❌ Error ' + error.status + ': Access Denied';
          console.error('❌ Auditor endpoint error:', error);
        }
      });
  }
}