// src/app/manager-layout/manager-layout.component.ts
// Wifak Bank — même style que agent-layout

import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { KeycloakAdminService } from '../../services/keycloak-admin.service';
import { NotificationService } from '../../services/notification.service';
import { ValidationService } from '../../services/Validation.service';
import { ConfirmDialogService } from '../../services/confirm-dialog.service';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-manager-layout',
  templateUrl: './manager-layout.component.html',
  styleUrls: ['./manager-layout.component.scss']
})
export class ManagerLayoutComponent implements OnInit, OnDestroy {

  sidebarCollapsed = false;
  currentRoute     = '';
  pendingCount     = 0;

  pageTitle    = 'Tableau de bord';
  pageSubtitle = 'Vue d\'ensemble des déclarations BCT';

  currentUserName     = '';
  currentUserInitials = '';

  constructor(
    private router:            Router,
    private kcAdmin:           KeycloakAdminService,
    public  notifSvc:          NotificationService,
    private validationService: ValidationService,
    private confirmDialog:     ConfirmDialogService
  ) {}

  ngOnInit(): void {
    this.router.events
      .pipe(filter(e => e instanceof NavigationEnd))
      .subscribe((e: any) => {
        this.currentRoute = e.urlAfterRedirects;
        this.updatePageInfo(e.urlAfterRedirects);
      });

    this.currentRoute = this.router.url;
    this.updatePageInfo(this.router.url);
    this.loadProfile();
    this.notifSvc.loadManagerNotifications();

    this.validationService.getPendingDeclarations().subscribe({
      next: (data) => { this.pendingCount = data.length; },
      error: () => {}
    });
  }

  ngOnDestroy(): void {}

  private loadProfile(): void {
    try {
      this.currentUserName     = this.kcAdmin.getFullName() || this.kcAdmin.getUsername();
      const parts              = this.currentUserName.split(' ');
      this.currentUserInitials = parts
        .filter(p => p.length > 0)
        .slice(0, 2)
        .map(p => p.charAt(0).toUpperCase())
        .join('') || 'M';
    } catch {
      this.currentUserName     = 'Manager';
      this.currentUserInitials = 'M';
    }
  }

  updatePageInfo(url: string): void {
    if (url.includes('/manager/dashboard') || url === '/manager' || url === '/manager/') {
      this.pageTitle    = 'Tableau de bord';
      this.pageSubtitle = 'Vue d\'ensemble des déclarations BCT';
    } else if (url.includes('/manager/pending')) {
      this.pageTitle    = 'Validation';
      this.pageSubtitle = 'Déclarations en attente de validation';
    } else if (url.includes('/manager/history')) {
      this.pageTitle    = 'Historique';
      this.pageSubtitle = 'Journal d\'audit des déclarations';
    } else if (url.includes('/manager/calendar')) {
      this.pageTitle    = 'Calendrier BCT';
      this.pageSubtitle = 'Échéances et planification des déclarations';
    } else if (url.includes('/manager/reports')) {
      this.pageTitle    = 'Rapports';
      this.pageSubtitle = 'Synthèse et analyse des déclarations';
    } else {
      this.pageTitle    = 'Espace Responsable';
      this.pageSubtitle = 'Supervision des déclarations BCT';
    }
  }

  isActive(path: string): boolean { return this.currentRoute.startsWith(path); }
  toggleSidebar(): void { this.sidebarCollapsed = !this.sidebarCollapsed; }
  navigate(path: string): void { this.router.navigate([path]); }

  logout(): void {
    this.confirmDialog.confirm(
      'Déconnexion',
      'Voulez-vous vous déconnecter ?',
      { confirmLabel: 'Déconnecter', type: 'warning' }
    ).then(confirmed => {
      if (confirmed) this.kcAdmin.logout();
    });
  }
}
