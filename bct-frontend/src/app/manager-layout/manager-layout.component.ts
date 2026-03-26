// src/app/manager-layout/manager-layout.component.ts

import { Component, OnInit } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { KeycloakAdminService, KeycloakUser, CreateUserRequest, RoleDTO } from '../services/keycloak-admin.service';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-manager-layout',
  templateUrl: './manager-layout.component.html',
  styleUrls: ['./manager-layout.component.scss']
})
export class ManagerLayoutComponent implements OnInit {
today = new Date();
  sidebarCollapsed = false;
  currentRoute     = '';

  currentUserName     = '';
  currentUserInitials = '';
  currentUserEmail    = '';

  navItems = [
    {
      section: 'Supervision',
      links: [
        { path: '/manager/dashboard',    label: 'Tableau de bord',  icon: 'dashboard',    badge: null,  badgeType: '' },
        { path: '/manager/pending',      label: 'Validation',       icon: 'validation',   badge: '●',   badgeType: 'urgent' },
        { path: '/manager/declarations', label: 'Déclarations',     icon: 'declarations', badge: null,  badgeType: '' },
        { path: '/manager/history',      label: 'Historique',       icon: 'history',      badge: null,  badgeType: '' },
      ]
    },
    {
      section: 'Analyse',
      links: [
        { path: '/manager/calendar',     label: 'Calendrier BCT',   icon: 'calendar',     badge: null,  badgeType: '' },
        { path: '/manager/reports',      label: 'Rapports',         icon: 'reports',      badge: null,  badgeType: '' },
      ]
    },
    {
      section: 'Système',
      links: [
        { path: '/dashboard',            label: 'Admin Dashboard',  icon: 'admin',        badge: null,  badgeType: '' },
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
    this.kcAdmin.logout();
  }
}