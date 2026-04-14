// src/app/manager-layout/manager-layout.component.ts
// ✅ MISE À JOUR — navItems complétés avec toutes les routes

import { Component, OnInit, OnDestroy, HostBinding } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { KeycloakAdminService } from '../services/keycloak-admin.service';
import { NotificationService } from '../services/notification.service';
import { ValidationService } from '../services/Validation.service';
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
  pendingCount     = 0;

  @HostBinding('class.theme-light')
  get themeLight(): boolean { return this.isLightTheme; }

  currentUserName     = '';
  currentUserInitials = '';
  currentUserEmail    = '';

  // ✅ SIDEBAR COMPLÈTE — toutes les routes du CDC
  navItems = [
    {
      section: 'Supervision',
      links: [
        { path: '/manager/dashboard',    label: 'Tableau de bord',  icon: 'dashboard',    badge: null },
        { path: '/manager/pending',      label: 'Validation',       icon: 'validation',   badge: '●'  },
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
    public notifSvc: NotificationService,
    private validationService: ValidationService
  ) {}

  ngOnInit(): void {
    this.router.events
      .pipe(filter(e => e instanceof NavigationEnd))
      .subscribe((e: any) => { this.currentRoute = e.urlAfterRedirects; });

    this.currentRoute = this.router.url;
    this.loadProfile();
    this.notifSvc.loadManagerNotifications();

    // Charger le nombre de déclarations en attente pour le badge
    this.validationService.getPendingDeclarations().subscribe({
      next: (data) => {
        this.pendingCount = data.length;
        // Mettre à jour le badge dynamiquement
        const pendingLink = this.navItems[0].links.find(l => l.path === '/manager/pending');
        if (pendingLink && this.pendingCount > 0) {
          pendingLink.badge = String(this.pendingCount);
        }
      },
      error: () => {}
    });

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
    if (light) document.body.classList.add('theme-light');
    else       document.body.classList.remove('theme-light');
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