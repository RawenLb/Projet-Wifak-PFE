// src/app/manager-layout/manager-layout.component.ts
// ✅ MODIFIÉ — ajout de NotificationService.loadManagerNotifications()

import { Component, OnInit, OnDestroy, HostBinding } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { KeycloakAdminService } from '../services/keycloak-admin.service';
import { NotificationService } from '../services/notification.service';   // ← AJOUT
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

  @HostBinding('class.theme-light')
  get themeLight(): boolean { return this.isLightTheme; }

  currentUserName     = '';
  currentUserInitials = '';
  currentUserEmail    = '';

  navItems = [
    {
      section: 'Supervision',
      links: [
        { path: '/manager/dashboard',    label: 'Tableau de bord',  icon: 'dashboard',    badge: null },
        { path: '/manager/pending',      label: 'Validation',       icon: 'validation',   badge: '●'  },
        { path: '/manager/declarations', label: 'Déclarations',     icon: 'declarations', badge: null },
        { path: '/manager/history',      label: 'Historique',       icon: 'history',      badge: null },
      ]
    },
    {
      section: 'Analyse',
      links: [
        { path: '/manager/calendar',     label: 'Calendrier BCT',   icon: 'calendar',     badge: null },
        { path: '/manager/reports',      label: 'Rapports',         icon: 'reports',      badge: null },
      ]
    },
    {
      section: 'Système',
      links: [
        { path: '/dashboard',            label: 'Admin Dashboard',  icon: 'admin',        badge: null },
      ]
    }
  ];

  constructor(
    private router: Router,
    private kcAdmin: KeycloakAdminService,
    public notifSvc: NotificationService    // ← AJOUT (public pour le template)
  ) {}

  ngOnInit(): void {
    this.router.events
      .pipe(filter(e => e instanceof NavigationEnd))
      .subscribe((e: any) => { this.currentRoute = e.urlAfterRedirects; });

    this.currentRoute = this.router.url;
    this.loadProfile();

    // ✅ Charge les notifications manager (déclarations EN_VALIDATION)
    this.notifSvc.loadManagerNotifications();

    // Restaurer la préférence de thème
    const saved = localStorage.getItem('wifak-theme');
    if (saved === 'light') {
      this.isLightTheme = true;
      this.applyBodyClass(true);
    }
  }

  ngOnDestroy(): void {
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

  toggleSidebar(): void { this.sidebarCollapsed = !this.sidebarCollapsed; }

  navigate(path: string): void { this.router.navigate([path]); }

  logout(): void {
    if (!confirm('Voulez-vous vous déconnecter ?')) return;
    this.applyBodyClass(false);
    this.kcAdmin.logout();
  }
}
