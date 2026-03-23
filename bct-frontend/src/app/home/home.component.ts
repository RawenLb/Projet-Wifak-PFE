import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { KeycloakAdminService, KeycloakUser } from '../services/keycloak-admin.service';
import { DeclarationTypeService, DeclarationType } from '../services/declaration-type.service';
import { DeclarationService, Declaration, DeclarationStats } from '../services/declaration.service';
@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {

activeTab: 'overview' | 'declarations' | 'types' = 'overview';  // ← remplace 'alerts' par 'types'

today = new Date().toLocaleDateString('fr-FR', { 
  weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' 
});
  searchQuery = '';

  // Real data
  recentUsers: KeycloakUser[] = [];
  recentDeclarationTypes: DeclarationType[] = [];
  allDeclarations: Declaration[] = [];
  stats: DeclarationStats = {
    total: 0, generees: 0, enValidation: 0,
    validees: 0, rejetees: 0, envoyees: 0
  };

  loadingUsers = false;
  loadingTypes = false;
  loadingDeclarations = false;

  // Upcoming deadlines (from declaration types with dateLimite)
  upcomingDeadlines: DeclarationType[] = [];

  constructor(
    private router: Router,
    private kcAdmin: KeycloakAdminService,
    private declarationTypeService: DeclarationTypeService,
    private declarationService: DeclarationService
  ) {}

  ngOnInit(): void {
    this.loadUsers();
    this.loadDeclarationTypes();
    this.loadDeclarations();
    this.loadStats();
  }

  loadUsers(): void {
    this.loadingUsers = true;
    this.kcAdmin.getUsers().subscribe({
      next: (users) => {
        this.recentUsers = users.slice(0, 5); // 5 most recent
        this.loadingUsers = false;
      },
      error: () => { this.loadingUsers = false; }
    });
  }

  loadDeclarationTypes(): void {
    this.loadingTypes = true;
    this.declarationTypeService.getAll().subscribe({
      next: (types) => {
        this.recentDeclarationTypes = types.slice(0, 5);
        this.upcomingDeadlines = types.filter(t => t.actif).slice(0, 4);
        this.loadingTypes = false;
      },
      error: () => { this.loadingTypes = false; }
    });
  }

  loadDeclarations(): void {
    this.loadingDeclarations = true;
    this.declarationService.getAllDeclarations().subscribe({
      next: (declarations) => {
        this.allDeclarations = declarations;
        this.loadingDeclarations = false;
      },
      error: () => { this.loadingDeclarations = false; }
    });
  }

  loadStats(): void {
    this.declarationService.getStats().subscribe({
      next: (stats) => { this.stats = stats; },
      error: () => {}
    });
  }

  // Declarations filtered for tab
  get filteredDeclarations(): Declaration[] {
    if (!this.searchQuery.trim()) return this.allDeclarations;
    const q = this.searchQuery.toLowerCase();
    return this.allDeclarations.filter(d =>
      d.declarationType?.code?.toLowerCase().includes(q) ||
      d.declarationType?.nom?.toLowerCase().includes(q) ||
      d.statut?.toLowerCase().includes(q)
    );
  }

  get recentDeclarations(): Declaration[] {
    return this.allDeclarations.slice(0, 5);
  }

  // Helpers
  getInitials(user: KeycloakUser): string {
    const f = user.firstName?.charAt(0)?.toUpperCase() || '';
    const l = user.lastName?.charAt(0)?.toUpperCase() || '';
    return f + l || user.username?.charAt(0)?.toUpperCase() || '?';
  }

  getAvatarColor(username: string): string {
    const colors = ['#1E40AF','#15803D','#B45309','#6B7280','#7C3AED','#DC2626'];
    const hash = (username || '').split('').reduce((acc, c) => c.charCodeAt(0) + ((acc << 5) - acc), 0);
    return colors[Math.abs(hash) % colors.length];
  }

  getPrimaryRole(user: KeycloakUser): string {
    const roles = user.roles || [];
    if (roles.includes('ROLE_ADMIN')) return 'Administrateur';
    if (roles.includes('ROLE_MANAGER')) return 'Responsable';
    if (roles.includes('ROLE_AGENT')) return 'Agent Déclarant';
    if (roles.includes('ROLE_AUDITOR')) return 'Auditeur';
    return 'Utilisateur';
  }

  getRoleClass(user: KeycloakUser): string {
    const roles = user.roles || [];
    if (roles.includes('ROLE_ADMIN')) return 'role-admin';
    if (roles.includes('ROLE_MANAGER')) return 'role-manager';
    if (roles.includes('ROLE_AGENT')) return 'role-agent';
    if (roles.includes('ROLE_AUDITOR')) return 'role-auditor';
    return '';
  }

  getStatutClass(statut: string): string {
    const map: Record<string, string> = {
      'EN_VALIDATION': 'chip-amber',
      'VALIDEE': 'chip-green',
      'ENVOYEE': 'chip-green',
      'REJETEE': 'chip-red',
      'GENEREE': 'chip-blue',
      'BROUILLON': 'chip-gray'
    };
    return map[statut] || 'chip-gray';
  }

  getFormatClass(format: string): string {
    const map: Record<string, string> = {
      'XML': 'format-xml', 'CSV': 'format-csv',
      'TXT': 'format-txt', 'PDF': 'format-pdf'
    };
    return map[format] || '';
  }

  getFreqClass(freq: string): string {
    const map: Record<string, string> = {
      'MENSUELLE': 'freq-monthly', 'HEBDOMADAIRE': 'freq-weekly',
      'QUOTIDIENNE': 'freq-daily', 'TRIMESTRIELLE': 'freq-quarterly',
      'ANNUELLE': 'freq-yearly'
    };
    return map[freq] || '';
  }

  navigateTo(route: string): void {
    this.router.navigate([route]);
  }
}