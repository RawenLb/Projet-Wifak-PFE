import { Component, OnInit } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { KeycloakAdminService } from '../services/keycloak-admin.service';

@Component({
  selector: 'app-dashboard-layout',
  templateUrl: './dashboard-layout.component.html',
  styleUrls: ['./dashboard-layout.component.scss']
})
export class DashboardLayoutComponent implements OnInit {
  sidebarCollapsed = false;
  pageTitle = 'Tableau de bord';
  pageSubtitle = 'Vue d\'ensemble de votre espace d\'administration';
  currentRoute = '';

  constructor(
    private router: Router,
    private kcAdmin: KeycloakAdminService
  ) {}

  ngOnInit(): void {
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

  updatePageInfo(url: string): void {
    if (url.includes('user-management') || url.includes('users')) {
      this.pageTitle = 'Gestion des Utilisateurs';
      this.pageSubtitle = 'Gérez les comptes utilisateurs et leurs permissions';
    } else if (url.includes('declaration-type')) {
      this.pageTitle = 'Gestion des Types de Déclarations';
      this.pageSubtitle = 'Configurez les types de déclarations réglementaires BCT';
    } else {
      this.pageTitle = 'Tableau de bord';
      this.pageSubtitle = 'Vue d\'ensemble de votre espace d\'administration';
    }
  }

  toggleSidebar(): void {
    this.sidebarCollapsed = !this.sidebarCollapsed;
  }

  isActive(route: string): boolean {
    return this.currentRoute.includes(route);
  }

  logout(): void {
    if (!confirm('Voulez-vous vous déconnecter ?')) return;
    this.kcAdmin.logout();
  }
}