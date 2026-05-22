import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { KeycloakAdminService } from '../../services/keycloak-admin.service';
import { NotificationService } from '../../services/notification.service';
import { ConfirmDialogService } from '../../services/confirm-dialog.service';

@Component({
  selector: 'app-agent-layout',
  templateUrl: './agent-layout.component.html',
  styleUrls: ['./agent-layout.component.scss']
})
export class AgentLayoutComponent implements OnInit, OnDestroy {

  sidebarCollapsed = false;
  pageTitle        = 'Mes Déclarations';
  pageSubtitle     = 'Générez et suivez vos déclarations BCT';
  currentRoute     = '';

  username  = '';
  fullName  = '';
  email     = '';
  userRole  = 'Agent';
  userRoles: string[] = [];

  get rejectedCount(): number {
    return this.notifSvc.all
      .filter(n => n.type === 'reject' && n.unread)
      .length;
  }

  constructor(
    private router:        Router,
    private kcAdmin:       KeycloakAdminService,
    public  notifSvc:      NotificationService,
    private confirmDialog: ConfirmDialogService
  ) {}

  ngOnInit(): void {
    if (!this.kcAdmin.isAuthenticated()) {
      this.router.navigate(['/home']);
      return;
    }

    this.loadUserInfo();
    this.notifSvc.loadNotifications();

    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe((event) => {
        const navEnd = event as NavigationEnd;
        this.currentRoute = navEnd.url;
        this.updatePageInfo(navEnd.url);
      });

    this.currentRoute = this.router.url;
    this.updatePageInfo(this.router.url);
  }

  ngOnDestroy(): void {}

  loadUserInfo(): void {
    try {
      this.username  = this.kcAdmin.getUsername();
      this.fullName  = this.kcAdmin.getFullName();
      this.email     = this.kcAdmin.getEmail();
      this.userRoles = this.kcAdmin.getCurrentUserRoles();
      this.userRole  = this.kcAdmin.getPrimaryRole();
    } catch {
      this.username = 'Agent';
      this.userRole = 'Agent';
    }
  }

  updatePageInfo(url: string): void {
    if (url.includes('/agent/dashboard') || url === '/agent' || url === '/agent/') {
      this.pageTitle    = 'Tableau de bord';
      this.pageSubtitle = 'Vue d\'ensemble de vos déclarations BCT';
    } else if (url.includes('/agent/declarations')) {
      this.pageTitle    = 'Mes Déclarations BCT';
      this.pageSubtitle = 'Générez et suivez vos déclarations réglementaires';
    } else if (url.includes('/agent/calendar')) {
      this.pageTitle    = 'Calendrier des Échéances';
      this.pageSubtitle = 'Visualisez et suivez vos échéances déclaratives BCT';
 } else if (url.includes('/agent/ml')) {
      this.pageTitle    = 'Aide intelligente';
      this.pageSubtitle = 'Corriger vos déclarations réglementaires';
    } else if (url.includes('/agent/types')) {
      this.pageTitle    = 'Types de Déclarations BCT';
      this.pageSubtitle = 'Catalogue des types de déclarations disponibles';
    } else if (url.includes('/agent/notifications')) {
      this.pageTitle    = 'Notifications';
      this.pageSubtitle = 'Vos alertes et messages de validation';
    } else if (url.includes('/agent/treated')) {
      this.pageTitle    = 'Déclarations traitées';
      this.pageSubtitle = 'Déclarations ayant complété leur cycle de traitement';
    } else {
      this.pageTitle    = 'Mes Déclarations';
      this.pageSubtitle = 'Espace Chargé de Déclaration';
    }
  }

  toggleSidebar(): void { this.sidebarCollapsed = !this.sidebarCollapsed; }
  isActive(route: string): boolean { return this.currentRoute.includes(route); }

  logout(): void {
    this.confirmDialog.confirm(
      'Déconnexion',
      'Voulez-vous vous déconnecter ?',
      { confirmLabel: 'Déconnecter', type: 'warning' }
    ).then(confirmed => {
      if (confirmed) this.kcAdmin.logout();
    });
  }

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

  getDisplayName(): string { return this.fullName || this.username || 'Agent'; }
  hasRole(roleName: string): boolean { return this.kcAdmin.hasRole(roleName); }
}