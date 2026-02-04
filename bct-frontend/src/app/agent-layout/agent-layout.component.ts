import { Component, OnInit } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { KeycloakAdminService } from '../services/keycloak-admin.service';

@Component({
  selector: 'app-agent-layout',
  templateUrl: './agent-layout.component.html',
  styleUrls: ['./agent-layout.component.scss']
})
export class AgentLayoutComponent implements OnInit {
  sidebarCollapsed = false;
  pageTitle = 'Mes Déclarations';
  pageSubtitle = 'Générez et suivez vos déclarations BCT';
  currentRoute = '';
  
  // Informations de l'utilisateur
  username = '';
  fullName = '';
  email = '';
  userRole = 'Agent';
  userRoles: string[] = [];

  constructor(
    private router: Router,
    private kcAdmin: KeycloakAdminService
  ) {}

  ngOnInit(): void {
    // Vérifier l'authentification
    if (!this.kcAdmin.isAuthenticated()) {
      console.warn('Utilisateur non authentifié');
      this.router.navigate(['/home']);
      return;
    }

    // Récupérer les informations de l'utilisateur
    this.loadUserInfo();
    
    // Update page title based on route
    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe((event: any) => {
        this.currentRoute = event.url;
        this.updatePageInfo(event.url);
      });

    // Set initial page info
    this.updatePageInfo(this.router.url);
  }

  /**
   * ✅ Charger les informations de l'utilisateur depuis Keycloak
   */
  loadUserInfo(): void {
    try {
      // Nom d'utilisateur
      this.username = this.kcAdmin.getUsername();
      
      // Nom complet
      this.fullName = this.kcAdmin.getFullName();
      
      // Email
      this.email = this.kcAdmin.getEmail();
      
      // Rôles
      this.userRoles = this.kcAdmin.getCurrentUserRoles();
      
      // Rôle principal pour l'affichage
      this.userRole = this.kcAdmin.getPrimaryRole();
      
      console.log('✅ Informations utilisateur chargées:', {
        username: this.username,
        fullName: this.fullName,
        email: this.email,
        role: this.userRole,
        roles: this.userRoles
      });
    } catch (error) {
      console.error('❌ Erreur lors du chargement des informations utilisateur:', error);
      this.username = 'Agent';
      this.userRole = 'Agent';
    }
  }

  /**
   * ✅ Mettre à jour les informations de la page selon la route
   */
updatePageInfo(url: string): void {
  // Handle root agent route
  if (url.includes('/agent/declarations') || url === '/agent' || url === '/agent/') {
    this.pageTitle = 'Mes Déclarations BCT';
    this.pageSubtitle = 'Générez et suivez vos déclarations réglementaires';
  } else if (url.includes('/agent/history')) {
    this.pageTitle = 'Historique des Déclarations';
    this.pageSubtitle = 'Consultez l\'historique complet de vos déclarations';
  } else if (url.includes('/agent/types')) {
    this.pageTitle = 'Types de Déclarations Disponibles';
    this.pageSubtitle = 'Découvrez les types de déclarations que vous pouvez générer';
  } else if (url.includes('/agent/help')) {
    this.pageTitle = 'Aide & Support';
    this.pageSubtitle = 'Documentation et assistance pour vos déclarations';
  } else {
    this.pageTitle = 'Mes Déclarations';
    this.pageSubtitle = 'Espace agent';
  }
}

  /**
   * ✅ Toggle sidebar
   */
  toggleSidebar(): void {
    this.sidebarCollapsed = !this.sidebarCollapsed;
  }

  /**
   * ✅ Vérifier si une route est active
   */
  isActive(route: string): boolean {
    return this.currentRoute.includes(route);
  }

  /**
   * ✅ Déconnexion
   */
  logout(): void {
    if (!confirm('Voulez-vous vous déconnecter ?')) return;
    
    console.log('🔒 Déconnexion de l\'utilisateur:', this.username);
    this.kcAdmin.logout();
  }

  /**
   * ✅ Obtenir les initiales de l'utilisateur pour l'avatar
   */
  getUserInitials(): string {
    // Essayer d'abord avec le nom complet
    if (this.fullName && this.fullName.includes(' ')) {
      const parts = this.fullName.trim().split(' ');
      if (parts.length >= 2) {
        return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
      }
    }
    
    // Sinon utiliser le username
    if (this.username && this.username.length >= 2) {
      return this.username.substring(0, 2).toUpperCase();
    }
    
    // Par défaut
    return 'AG';
  }

  /**
   * ✅ Obtenir le nom à afficher (préférer le nom complet)
   */
  getDisplayName(): string {
    return this.fullName || this.username || 'Agent';
  }

  /**
   * ✅ Vérifier si l'utilisateur a un rôle spécifique
   */
  hasRole(roleName: string): boolean {
    return this.kcAdmin.hasRole(roleName);
  }

  /**
   * ✅ Obtenir la couleur de l'avatar selon le rôle
   */
  getAvatarColor(): string {
    if (this.hasRole('ROLE_ADMIN')) return '#DE350B';
    if (this.hasRole('ROLE_MANAGER')) return '#0052CC';
    if (this.hasRole('ROLE_AGENT')) return '#0065FF';
    if (this.hasRole('ROLE_AUDITOR')) return '#FF5630';
    return '#5E6C84';
  }
}