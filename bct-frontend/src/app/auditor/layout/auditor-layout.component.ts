// src/app/auditor-layout/auditor-layout.component.ts
import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { KeycloakAdminService } from '../../services/keycloak-admin.service';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-auditor-layout',
  templateUrl: './auditor-layout.component.html',
  styleUrls: ['./auditor-layout.component.scss']
})
export class AuditorLayoutComponent implements OnInit, OnDestroy {

  sidebarCollapsed = false;
  currentRoute     = '';

  pageTitle    = 'Tableau de bord';
  pageSubtitle = 'Vue d\'ensemble des déclarations BCT';

  currentUserName     = '';
  currentUserInitials = '';

  constructor(
    private router:  Router,
    private kcAdmin: KeycloakAdminService
  ) {}

  ngOnInit(): void {
    this.router.events
      .pipe(filter(e => e instanceof NavigationEnd))
      .subscribe((e) => {
        const navEnd = e as NavigationEnd;
        this.currentRoute = navEnd.urlAfterRedirects;
        this.updatePageInfo(navEnd.urlAfterRedirects);
      });

    this.currentRoute = this.router.url;
    this.updatePageInfo(this.router.url);
    this.loadProfile();
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
        .join('') || 'AU';
    } catch {
      this.currentUserName     = 'Auditeur';
      this.currentUserInitials = 'AU';
    }
  }

  updatePageInfo(url: string): void {
    if (url.includes('/auditor/dashboard') || url === '/auditor' || url === '/auditor/') {
      this.pageTitle    = 'Tableau de bord';
      this.pageSubtitle = 'Vue d\'ensemble des déclarations BCT';
    } else if (url.includes('/auditor/history')) {
      this.pageTitle    = 'Historique des déclarations';
      this.pageSubtitle = 'Suivi complet du cycle de vie de chaque déclaration';
    } else if (url.includes('/auditor/logs')) {
      this.pageTitle    = 'Journaux de traçabilité';
      this.pageSubtitle = 'Toutes les actions effectuées sur la plateforme';
    } else if (url.includes('/auditor/archives')) {
      this.pageTitle    = 'Archives';
      this.pageSubtitle = 'Déclarations validées et envoyées — conservation long terme';
    } else if (url.includes('/auditor/search')) {
      this.pageTitle    = 'Recherche avancée';
      this.pageSubtitle = 'Filtrage par type, période, statut et agent';
    } else if (url.includes('/auditor/export')) {
      this.pageTitle    = 'Export rapports d\'audit';
      this.pageSubtitle = 'Préparez vos rapports pour audits internes et externes';
    } else {
      this.pageTitle    = 'Espace Auditeur';
      this.pageSubtitle = 'Consultation en lecture seule';
    }
  }

  isActive(path: string): boolean { return this.currentRoute.startsWith(path); }
  toggleSidebar(): void { this.sidebarCollapsed = !this.sidebarCollapsed; }
  navigate(path: string): void { this.router.navigate([path]); }

  logout(): void {
    if (!confirm('Voulez-vous vous déconnecter ?')) return;
    this.kcAdmin.logout();
  }
}
