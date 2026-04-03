// src/app/agent-layout/agent-layout.component.ts
// ✅ MODIFIÉ — ajout de NotificationService + suppression du loadRejectedCount manuel

import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { KeycloakAdminService } from '../services/keycloak-admin.service';
import { NotificationService } from '../services/notification.service';   // ← AJOUT

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

  // ✅ REMPLACE l'ancien rejectedCount manuel — maintenant via NotificationService
  get rejectedCount(): number {
    return this.notifSvc.all
      .filter(n => n.type === 'reject' && n.unread)
      .length;
  }

  constructor(
    private router:   Router,
    private kcAdmin:  KeycloakAdminService,
    public notifSvc:  NotificationService    // ← AJOUT (public pour le template)
  ) {}

  ngOnInit(): void {
    if (!this.kcAdmin.isAuthenticated()) {
      this.router.navigate(['/home']);
      return;
    }

    this.loadUserInfo();

    // ✅ Charge les notifications au démarrage (remplace loadRejectedCount)
    this.notifSvc.loadNotifications();

    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe((event: any) => {
        this.currentRoute = event.url;
        this.updatePageInfo(event.url);
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

  toggleSidebar(): void { this.sidebarCollapsed = !this.sidebarCollapsed; }

  isActive(route: string): boolean { return this.currentRoute.includes(route); }

  logout(): void {
    if (!confirm('Voulez-vous vous déconnecter ?')) return;
    this.kcAdmin.logout();
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