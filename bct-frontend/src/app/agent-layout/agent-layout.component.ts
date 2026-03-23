import { Component, OnInit } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { KeycloakAdminService } from '../services/keycloak-admin.service';
import { DeclarationService } from '../services/declaration.service';
@Component({
  selector: 'app-agent-layout',
  templateUrl: './agent-layout.component.html',
  styleUrls: ['./agent-layout.component.scss']
})
export class AgentLayoutComponent implements OnInit {

  sidebarCollapsed = false;
  pageTitle        = 'Mes Déclarations';
  pageSubtitle     = 'Générez et suivez vos déclarations BCT';
  currentRoute     = '';

  // ── Informations utilisateur ───────────────────────────────────────────────
  username  = '';
  fullName  = '';
  email     = '';
  userRole  = 'Agent';
  userRoles: string[] = [];

  // ── Badge rejetées ─────────────────────────────────────────────────────────
  rejectedCount = 0;

  constructor(
    private router:             Router,
    private kcAdmin:            KeycloakAdminService,
    private declarationService: DeclarationService   // ✅ injecté
  ) {}

  ngOnInit(): void {
    // Vérifier l'authentification
    if (!this.kcAdmin.isAuthenticated()) {
      console.warn('Utilisateur non authentifié');
      this.router.navigate(['/home']);
      return;
    }

    // Charger les infos utilisateur
    this.loadUserInfo();

    // ✅ Charger le badge au démarrage
    this.loadRejectedCount();

    // Mettre à jour le titre + badge à chaque navigation
    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe((event: any) => {
        this.currentRoute = event.url;
        this.updatePageInfo(event.url);
        // ✅ Rafraîchir le badge à chaque changement de page
        this.loadRejectedCount();
      });

    // Initialiser avec la route actuelle
    this.updatePageInfo(this.router.url);
  }

  // ══════════════════════════════════════════════════════════════════════════
  // BADGE — Déclarations rejetées
  // ══════════════════════════════════════════════════════════════════════════

// TEST TEMPORAIRE — supprimer après vérification
loadRejectedCount(): void {
  this.declarationService.getMyDeclarations().subscribe({
    next: (data) => {
      this.rejectedCount = data.filter(d => d.statut === 'REJETEE').length;
      console.log('🔴 rejectedCount:', this.rejectedCount);  // ← شوف في console
      console.log('📋 statuts:', data.map(d => d.statut));   // ← شوف الـ statuts الموجودة
    },
    error: () => { this.rejectedCount = 0; }
  });
}

  // ══════════════════════════════════════════════════════════════════════════
  // USER INFO
  // ══════════════════════════════════════════════════════════════════════════

  loadUserInfo(): void {
    try {
      this.username  = this.kcAdmin.getUsername();
      this.fullName  = this.kcAdmin.getFullName();
      this.email     = this.kcAdmin.getEmail();
      this.userRoles = this.kcAdmin.getCurrentUserRoles();
      this.userRole  = this.kcAdmin.getPrimaryRole();

      console.log('✅ Informations utilisateur chargées:', {
        username: this.username,
        fullName: this.fullName,
        email:    this.email,
        role:     this.userRole,
        roles:    this.userRoles
      });
    } catch (error) {
      console.error('❌ Erreur chargement utilisateur:', error);
      this.username = 'Agent';
      this.userRole = 'Agent';
    }
  }

  // ══════════════════════════════════════════════════════════════════════════
  // PAGE INFO
  // ══════════════════════════════════════════════════════════════════════════

  updatePageInfo(url: string): void {
    if (url.includes('/agent/declarations') || url === '/agent' || url === '/agent/') {
      this.pageTitle    = 'Mes Déclarations BCT';
      this.pageSubtitle = 'Générez et suivez vos déclarations réglementaires';
    } else if (url.includes('/agent/calendar')) {
      this.pageTitle    = 'Calendrier des Échéances';
      this.pageSubtitle = 'Visualisez et suivez vos échéances déclaratives BCT';
    } else if (url.includes('/agent/history')) {
      this.pageTitle    = 'Historique des Déclarations';
      this.pageSubtitle = 'Consultez l\'historique complet de vos déclarations';
    } else if (url.includes('/agent/types')) {
      this.pageTitle    = 'Types de Déclarations Disponibles';
      this.pageSubtitle = 'Découvrez les types de déclarations que vous pouvez générer';
    } else if (url.includes('/agent/help')) {
      this.pageTitle    = 'Aide & Support';
      this.pageSubtitle = 'Documentation et assistance pour vos déclarations';
    } else {
      this.pageTitle    = 'Mes Déclarations';
      this.pageSubtitle = 'Espace agent';
    }
  }

  // ══════════════════════════════════════════════════════════════════════════
  // SIDEBAR
  // ══════════════════════════════════════════════════════════════════════════

  toggleSidebar(): void {
    this.sidebarCollapsed = !this.sidebarCollapsed;
  }

  isActive(route: string): boolean {
    return this.currentRoute.includes(route);
  }

  // ══════════════════════════════════════════════════════════════════════════
  // AUTH
  // ══════════════════════════════════════════════════════════════════════════

  logout(): void {
    if (!confirm('Voulez-vous vous déconnecter ?')) return;
    console.log('🔒 Déconnexion de l\'utilisateur:', this.username);
    this.kcAdmin.logout();
  }

  // ══════════════════════════════════════════════════════════════════════════
  // HELPERS UTILISATEUR
  // ══════════════════════════════════════════════════════════════════════════

  getUserInitials(): string {
    if (this.fullName && this.fullName.includes(' ')) {
      const parts = this.fullName.trim().split(' ');
      if (parts.length >= 2) {
        return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
      }
    }
    if (this.username && this.username.length >= 2) {
      return this.username.substring(0, 2).toUpperCase();
    }
    return 'AG';
  }

  getDisplayName(): string {
    return this.fullName || this.username || 'Agent';
  }

  hasRole(roleName: string): boolean {
    return this.kcAdmin.hasRole(roleName);
  }

  getAvatarColor(): string {
    if (this.hasRole('ROLE_ADMIN'))   return '#DE350B';
    if (this.hasRole('ROLE_MANAGER')) return '#0052CC';
    if (this.hasRole('ROLE_AGENT'))   return '#0065FF';
    if (this.hasRole('ROLE_AUDITOR')) return '#FF5630';
    return '#5E6C84';
  }
}