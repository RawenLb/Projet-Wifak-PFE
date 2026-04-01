// src/app/manager-layout/manager-layout.component.ts
// Fix thème : :host.theme-light (layout) + body.theme-light (enfants)

import { Component, OnInit, OnDestroy, HostBinding } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { KeycloakAdminService } from '../services/keycloak-admin.service';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-manager-layout',
  templateUrl: './manager-layout.component.html',
  styleUrls: ['./manager-layout.component.scss']
})
export class ManagerLayoutComponent implements OnInit, OnDestroy {

  today            = new Date();
  sidebarCollapsed = false;
  currentRoute     = '';
  isLightTheme     = false;

  /**
   * Applique .theme-light sur <app-manager-layout>
   * → active :host.theme-light dans le layout SCSS
   */
  @HostBinding('class.theme-light')
  get themeLight(): boolean { return this.isLightTheme; }

  currentUserName     = '';
  currentUserInitials = '';
  currentUserEmail    = '';

  navItems = [
    {
      section: 'Supervision',
      links: [
        { path: '/manager/dashboard',    label: 'Tableau de bord',  icon: 'dashboard',    badge: null, badgeType: '' },
        { path: '/manager/pending',      label: 'Validation',       icon: 'validation',   badge: '●',  badgeType: 'urgent' },
        { path: '/manager/declarations', label: 'Déclarations',     icon: 'declarations', badge: null, badgeType: '' },
        { path: '/manager/history',      label: 'Historique',       icon: 'history',      badge: null, badgeType: '' },
      ]
    },
    {
      section: 'Analyse',
      links: [
        { path: '/manager/calendar',     label: 'Calendrier BCT',   icon: 'calendar',     badge: null, badgeType: '' },
        { path: '/manager/reports',      label: 'Rapports',         icon: 'reports',      badge: null, badgeType: '' },
      ]
    },
    {
      section: 'Système',
      links: [
        { path: '/dashboard',            label: 'Admin Dashboard',  icon: 'admin',        badge: null, badgeType: '' },
      ]
    }
  ];

  constructor(
    private router: Router,
    private kcAdmin: KeycloakAdminService
  ) {}

  ngOnInit(): void {
    this.router.events
      .pipe(filter(e => e instanceof NavigationEnd))
      .subscribe((e: any) => { this.currentRoute = e.urlAfterRedirects; });

    this.currentRoute = this.router.url;
    this.loadProfile();

    // Restaurer la préférence sauvegardée
    const saved = localStorage.getItem('wifak-theme');
    if (saved === 'light') {
      this.isLightTheme = true;
      this.applyBodyClass(true);
    }
  }

  ngOnDestroy(): void {
    // Nettoyer la classe body au démontage
    this.applyBodyClass(false);
  }

  private loadProfile(): void {
    try {
      this.currentUserName     = this.kcAdmin.getFullName();
      this.currentUserEmail    = this.kcAdmin.getEmail();
      const nameParts          = this.currentUserName.split(' ');
      this.currentUserInitials = nameParts
        .filter(p => p.length > 0)
        .slice(0, 2)
        .map(p => p.charAt(0).toUpperCase())
        .join('') || 'M';
    } catch {
      this.currentUserName     = 'Manager';
      this.currentUserInitials = 'M';
    }
  }

  toggleTheme(): void {
    this.isLightTheme = !this.isLightTheme;
    localStorage.setItem('wifak-theme', this.isLightTheme ? 'light' : 'dark');

    /**
     * Applique aussi sur <body>
     * → active :host-context(body.theme-light) dans les composants enfants
     *   (dashboard, pending, declarations, etc.)
     */
    this.applyBodyClass(this.isLightTheme);
  }

  private applyBodyClass(light: boolean): void {
    if (light) {
      document.body.classList.add('theme-light');
    } else {
      document.body.classList.remove('theme-light');
    }
  }

  isActive(path: string): boolean {
    return this.currentRoute.startsWith(path);
  }

  toggleSidebar(): void {
    this.sidebarCollapsed = !this.sidebarCollapsed;
  }

  navigate(path: string): void {
    this.router.navigate([path]);
  }

  logout(): void {
    if (!confirm('Voulez-vous vous déconnecter ?')) return;
    this.applyBodyClass(false);
    this.kcAdmin.logout();
  }
}