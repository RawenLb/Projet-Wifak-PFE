// src/app/manager-history/manager-history.component.ts
// US-11 / US-12 — Journal d'audit complet pour le Responsable & Auditeur

import { Component, OnInit } from '@angular/core';
import { DeclarationService, Declaration } from '../services/Declaration.service';
import { ValidationService, ValidationLog } from '../services/Validation.service';

export interface AuditEntry {
  declaration: Declaration;
  logs: ValidationLog[];
  expanded: boolean;
}

export interface AuditFilter {
  search: string;
  statut: string;
  action: string;
  periode: string;
  type: string;
  dateDebut: string;
  dateFin: string;
}

@Component({
  selector: 'app-manager-history',
  templateUrl: './manager-history.component.html',
  styleUrls: ['./manager-history.component.scss']
})
export class ManagerHistoryComponent implements OnInit {

  loading = false;
  loadingLogs: Record<number, boolean> = {};

  declarations: Declaration[] = [];
  auditEntries: AuditEntry[] = [];

  // Filtres
  filters: AuditFilter = {
    search: '',
    statut: '',
    action: '',
    periode: '',
    type: '',
    dateDebut: '',
    dateFin: ''
  };

  // Pagination
  pageSize = 15;
  currentPage = 1;

  // Vue
  viewMode: 'table' | 'timeline' = 'table';

  // Stats
  get stats() {
    const all = this.declarations;
    return {
      total:      all.length,
      validees:   all.filter(d => ['VALIDEE', 'ENVOYEE'].includes(d.statut)).length,
      rejetees:   all.filter(d => d.statut === 'REJETEE').length,
      envoyees:   all.filter(d => d.statut === 'ENVOYEE').length,
      enCours:    all.filter(d => ['GENEREE', 'EN_VALIDATION'].includes(d.statut)).length,
    };
  }

  get filteredDeclarations(): Declaration[] {
    return this.declarations.filter(d => {
      const s = this.filters;

      if (s.search) {
        const q = s.search.toLowerCase();
        const matchId   = String(d.id).includes(q);
        const matchCode = d.declarationType?.code?.toLowerCase().includes(q);
        const matchNom  = d.declarationType?.nom?.toLowerCase().includes(q);
        const matchUser = d.generePar?.toLowerCase().includes(q) || d.validePar?.toLowerCase().includes(q);
        if (!matchId && !matchCode && !matchNom && !matchUser) return false;
      }

      if (s.statut   && d.statut !== s.statut) return false;
      if (s.periode  && !d.periode?.includes(s.periode)) return false;
      if (s.type     && d.declarationType?.code !== s.type) return false;

      if (s.dateDebut && d.dateGeneration) {
        const gen = new Date(d.dateGeneration);
        const deb = new Date(s.dateDebut);
        if (gen < deb) return false;
      }

      if (s.dateFin && d.dateGeneration) {
        const gen = new Date(d.dateGeneration);
        const fin = new Date(s.dateFin);
        fin.setHours(23, 59, 59);
        if (gen > fin) return false;
      }

      return true;
    });
  }

  get totalPages(): number {
    return Math.ceil(this.filteredDeclarations.length / this.pageSize);
  }

  get paginatedDeclarations(): Declaration[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredDeclarations.slice(start, start + this.pageSize);
  }

  get pages(): number[] {
    const total = this.totalPages;
    if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);
    const pages: number[] = [];
    if (this.currentPage <= 4) {
      for (let i = 1; i <= 5; i++) pages.push(i);
      pages.push(-1, total);
    } else if (this.currentPage >= total - 3) {
      pages.push(1, -1);
      for (let i = total - 4; i <= total; i++) pages.push(i);
    } else {
      pages.push(1, -1);
      for (let i = this.currentPage - 1; i <= this.currentPage + 1; i++) pages.push(i);
      pages.push(-1, total);
    }
    return pages;
  }

  get uniquePeriodes(): string[] {
    return [...new Set(this.declarations.map(d => d.periode || '').filter(Boolean))].sort().reverse();
  }

  get uniqueTypes(): string[] {
    return [...new Set(this.declarations.map(d => d.declarationType?.code || '').filter(Boolean))].sort();
  }

  // Logs en cache
  private logsCache = new Map<number, ValidationLog[]>();

  constructor(
    private declarationService: DeclarationService,
    private validationService: ValidationService
  ) {}

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.loading = true;
    this.declarationService.getAllDeclarations().subscribe({
      next: (data) => {
        this.declarations = data.sort((a, b) => (b.id ?? 0) - (a.id ?? 0));
        this.loading = false;
        this.currentPage = 1;
      },
      error: () => { this.loading = false; }
    });
  }

  toggleLogs(d: Declaration): void {
    if (!d.id) return;

    const entry = this.auditEntries.find(e => e.declaration.id === d.id);

    if (entry) {
      entry.expanded = !entry.expanded;
      return;
    }

    // Charger les logs si pas encore en cache
    if (this.logsCache.has(d.id)) {
      this.auditEntries.push({ declaration: d, logs: this.logsCache.get(d.id)!, expanded: true });
      return;
    }

    this.loadingLogs[d.id] = true;
    this.validationService.getHistory(d.id).subscribe({
      next: (logs) => {
        this.logsCache.set(d.id!, logs);
        this.loadingLogs[d.id!] = false;

        const existing = this.auditEntries.find(e => e.declaration.id === d.id);
        if (existing) {
          existing.logs = logs;
          existing.expanded = true;
        } else {
          this.auditEntries.push({ declaration: d, logs, expanded: true });
        }
      },
      error: () => {
        this.loadingLogs[d.id!] = false;
      }
    });
  }

  isExpanded(d: Declaration): boolean {
    return this.auditEntries.find(e => e.declaration.id === d.id)?.expanded ?? false;
  }

  getLogsForDeclaration(d: Declaration): ValidationLog[] {
    return this.auditEntries.find(e => e.declaration.id === d.id)?.logs ?? [];
  }

  isLoadingLogs(d: Declaration): boolean {
    return !!this.loadingLogs[d.id ?? -1];
  }

  resetFilters(): void {
    this.filters = { search: '', statut: '', action: '', periode: '', type: '', dateDebut: '', dateFin: '' };
    this.currentPage = 1;
  }

  onFilterChange(): void {
    this.currentPage = 1;
  }

  goToPage(page: number): void {
    if (page < 1 || page > this.totalPages) return;
    this.currentPage = page;
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  exportCSV(): void {
    const rows: string[] = ['ID,Type,Code,Période,Statut,Généré par,Validé par,Date génération,Date validation,Motif rejet'];
    this.filteredDeclarations.forEach(d => {
      rows.push([
        d.id,
        `"${d.declarationType?.nom || ''}"`,
        d.declarationType?.code || '',
        d.periode || '',
        d.statut,
        d.generePar || '',
        d.validePar || '',
        d.dateGeneration ? new Date(d.dateGeneration).toLocaleDateString('fr-FR') : '',
        d.dateValidation ? new Date(d.dateValidation).toLocaleDateString('fr-FR') : '',
        `"${d.commentaireRejet || ''}"`,
      ].join(','));
    });

    const blob = new Blob([rows.join('\n')], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `historique_declarations_${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  // ─── Helpers ──────────────────────────────────────────────────

  getStatutClass(statut: string): string {
    const map: Record<string, string> = {
      'GENEREE': 'sc-generee', 'EN_VALIDATION': 'sc-validation',
      'VALIDEE': 'sc-validee', 'REJETEE': 'sc-rejetee', 'ENVOYEE': 'sc-envoyee',
    };
    return map[statut] || '';
  }

  getStatutLabel(statut: string): string {
    const map: Record<string, string> = {
      'GENEREE': 'Générée', 'EN_VALIDATION': 'En validation',
      'VALIDEE': 'Validée', 'REJETEE': 'Rejetée', 'ENVOYEE': 'Envoyée',
    };
    return map[statut] || statut;
  }

  getActionClass(action: string): string {
    const map: Record<string, string> = {
      'SUBMIT': 'act-submit', 'VALIDATE': 'act-validate',
      'REJECT': 'act-reject', 'SEND': 'act-send',
    };
    return map[action] || '';
  }

  getActionLabel(action: string): string {
    const map: Record<string, string> = {
      'SUBMIT': 'Soumission', 'VALIDATE': 'Validation',
      'REJECT': 'Rejet', 'SEND': 'Envoi BCT',
    };
    return map[action] || action;
  }

  getActionIcon(action: string): string {
    const map: Record<string, string> = {
      'SUBMIT': '📤', 'VALIDATE': '✅', 'REJECT': '❌', 'SEND': '📨',
    };
    return map[action] || '•';
  }

  trackById(_: number, d: Declaration): number { return d.id ?? 0; }
}