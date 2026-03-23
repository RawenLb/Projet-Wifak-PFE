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

  
  activeTab: 'overview' | 'declarations' | 'alerts' = 'overview';
  searchQuery = '';
 
  recentDeclarations = [
    { code: 'BCT_01', type: 'Hebdomadaire', statut: 'En attente', echeance: 'J-1', urgent: true },
    { code: 'BCT_03', type: 'Mensuelle',    statut: 'Validée',    echeance: '10/04', urgent: false },
    { code: 'BCT_07', type: 'Trimestrielle',statut: 'Rejetée',    echeance: 'En retard', urgent: true },
    { code: 'BCT_02', type: 'Mensuelle',    statut: 'Générée',    echeance: '15/04', urgent: false },
    { code: 'BCT_05', type: 'Hebdomadaire', statut: 'Envoyée',    echeance: '24/03', urgent: false },
  ];
 
  allDeclarations = [
    { code: 'BCT_01', nom: 'Déclaration hebdomadaire positions', frequence: 'Hebdo',    format: 'XML', statut: 'En attente', agent: 'A. Ben Ali',        echeance: '25/03', urgent: true  },
    { code: 'BCT_03', nom: 'Rapport mensuel portefeuille',       frequence: 'Mensuel',  format: 'TXT', statut: 'Validée',    agent: 'S. Tlili',          echeance: '10/04', urgent: false },
    { code: 'BCT_07', nom: 'Déclaration trimestrielle crédits',  frequence: 'Trimestr.',format: 'XML', statut: 'Rejetée',    agent: 'M. Hamdi',          echeance: 'Retard', urgent: true  },
    { code: 'BCT_02', nom: 'Déclaration mensuelle dépôts',       frequence: 'Mensuel',  format: 'TXT', statut: 'Générée',    agent: 'A. Ben Ali',        echeance: '15/04', urgent: false },
    { code: 'BCT_05', nom: 'Déclaration hebdomadaire liquidité', frequence: 'Hebdo',    format: 'XML', statut: 'Envoyée',    agent: 'S. Tlili',          echeance: '24/03', urgent: false },
  ];
 
  recentUsers = [
    { initials: 'AK', name: 'Admin Karim',        email: 'a.karim@wifak.tn',       role: 'Administrateur',  active: true,  color: '#1E40AF' },
    { initials: 'ST', name: 'Sarra Tlili',         email: 's.tlili@wifak.tn',        role: 'Agent Déclarant', active: true,  color: '#15803D' },
    { initials: 'MK', name: 'Mohamed Kameleddine', email: 'm.kameleddine@wifak.tn', role: 'Responsable',     active: true,  color: '#B45309' },
    { initials: 'RB', name: 'Rim Ben Amor',        email: 'r.benamor@wifak.tn',     role: 'Auditeur',        active: true,  color: '#6B7280' },
    { initials: 'AB', name: 'Aymen Ben Ali',       email: 'a.benali@wifak.tn',      role: 'Agent Déclarant', active: false, color: '#9CA3AF' },
  ];
 
  iaAlerts = [
    { level: 'high',   levelLabel: 'Élevé',  text: '<b>BCT_07</b> — Anomalie détectée : variation +58% vs historique 12 mois',  time: 'Il y a 2h' },
    { level: 'medium', levelLabel: 'Moyen',  text: '<b>BCT_01</b> — Risque de retard estimé 72%. Échéance demain.',              time: 'Il y a 4h' },
    { level: 'medium', levelLabel: 'Moyen',  text: '<b>BCT_03</b> — Champ obligatoire manquant détecté avant envoi',             time: 'Hier' },
    { level: 'info',   levelLabel: 'Info',   text: '<b>BCT_09</b> — Rappel automatique J-5 envoyé à 3 agents',                  time: 'Hier' },
  ];
 
  allAlerts = [
    { level: 'high',   text: '<b>BCT_07 — Anomalie critique</b> : variation de +58% détectée par rapport à la moyenne des 12 derniers mois. Validation bloquée.', time: '25/03/2025 à 09:14', score: '92/100' },
    { level: 'medium', text: '<b>BCT_01 — Risque de retard élevé</b> : probabilité 72%. Échéance demain à 17h00. Déclaration en attente de validation.',           time: '25/03/2025 à 08:30', score: '72/100' },
    { level: 'medium', text: '<b>BCT_03 — Champ obligatoire</b> : le champ "Code_ISIN" est manquant dans la ligne 47 du fichier TXT. Blocage conformité BCT.',     time: '24/03/2025 à 14:52', score: '55/100' },
    { level: 'info',   text: '<b>BCT_09 — Rappel J-5</b> : notification envoyée à 3 agents déclarants pour l\'échéance du 30/03.',                                 time: '24/03/2025 à 08:00', score: '—' },
  ];
 
}