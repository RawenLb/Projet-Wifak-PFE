// src/app/auditor-search/auditor-search.component.ts
// BF9 / US-14 — Recherche et filtrage avancé
import { Component, OnInit } from '@angular/core';
import { DeclarationService, Declaration } from '../services/Declaration.service';

@Component({
  selector: 'app-auditor-search',
  templateUrl: './auditor-search.component.html',
  styleUrls: ['./auditor-search.component.scss']
})
export class AuditorSearchComponent implements OnInit {

  loading = false;
  allDeclarations: Declaration[] = [];
  selectedDeclaration: Declaration | null = null;

  // Filtres
  searchQuery  = '';
  filterType   = '';
  filterPeriode = '';
  filterStatut = '';
  filterAgent  = '';
  filterDateFrom = '';
  filterDateTo   = '';

  // Pagination
  currentPage = 1;
  pageSize    = 20;

  constructor(private declarationService: DeclarationService) {}

  ngOnInit(): void { this.charger(); }

  charger(): void {
    this.loading = true;
    this.declarationService.getAllDeclarations().subscribe({
      next: (data) => {
        this.allDeclarations = data.sort((a, b) => (b.id ?? 0) - (a.id ?? 0));
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  get uniqueTypes(): string[] {
    return [...new Set(this.allDeclarations.map(d => d.declarationType?.code || '').filter(Boolean))].sort();
  }

  get uniquePeriodes(): string[] {
    return [...new Set(this.allDeclarations.map(d => d.periode || '').filter(Boolean))].sort().reverse();
  }

  get uniqueAgents(): string[] {
    return [...new Set(this.allDeclarations.map(d => d.generePar || '').filter(Boolean))].sort();
  }

  get filteredDeclarations(): Declaration[] {
    return this.allDeclarations.filter(d => {
      if (this.searchQuery) {
        const q = this.searchQuery.toLowerCase();
        const ok = String(d.id).includes(q)
          || d.declarationType?.code?.toLowerCase().includes(q)
          || d.declarationType?.nom?.toLowerCase().includes(q)
          || d.generePar?.toLowerCase().includes(q)
          || d.validePar?.toLowerCase().includes(q)
          || d.periode?.toLowerCase().includes(q);
        if (!ok) return false;
      }
      if (this.filterType    && d.declarationType?.code !== this.filterType) return false;
      if (this.filterPeriode && !d.periode?.includes(this.filterPeriode)) return false;
      if (this.filterStatut  && d.statut !== this.filterStatut) return false;
      if (this.filterAgent   && d.generePar !== this.filterAgent) return false;
      if (this.filterDateFrom) {
        const from = new Date(this.filterDateFrom);
        const gen  = d.dateGeneration ? new Date(d.dateGeneration) : null;
        if (!gen || gen < from) return false;
      }
      if (this.filterDateTo) {
        const to  = new Date(this.filterDateTo);
        const gen = d.dateGeneration ? new Date(d.dateGeneration) : null;
        if (!gen || gen > to) return false;
      }
      return true;
    });
  }

  get pagedDeclarations(): Declaration[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredDeclarations.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.ceil(this.filteredDeclarations.length / this.pageSize);
  }

  get pages(): number[] {
    const total = this.totalPages;
    if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);
    const pages: number[] = [1];
    if (this.currentPage > 3) pages.push(-1);
    for (let i = Math.max(2, this.currentPage - 1); i <= Math.min(total - 1, this.currentPage + 1); i++) {
      pages.push(i);
    }
    if (this.currentPage < total - 2) pages.push(-1);
    pages.push(total);
    return pages;
  }

  onFilterChange(): void { this.currentPage = 1; }

  resetFilters(): void {
    this.searchQuery   = '';
    this.filterType    = '';
    this.filterPeriode = '';
    this.filterStatut  = '';
    this.filterAgent   = '';
    this.filterDateFrom = '';
    this.filterDateTo   = '';
    this.currentPage   = 1;
  }

  get hasActiveFilters(): boolean {
    return !!(this.searchQuery || this.filterType || this.filterPeriode ||
              this.filterStatut || this.filterAgent || this.filterDateFrom || this.filterDateTo);
  }

  selectDeclaration(d: Declaration): void {
    this.selectedDeclaration = this.selectedDeclaration?.id === d.id ? null : d;
  }

  exportCSV(): void {
    const rows = ['ID,Type,Code,Période,Statut,Généré par,Validé par,Date génération,Date validation'];
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
      ].join(','));
    });
    const blob = new Blob([rows.join('\n')], { type: 'text/csv;charset=utf-8;' });
    const url  = URL.createObjectURL(blob);
    const a    = document.createElement('a');
    a.href     = url;
    a.download = `recherche_${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  getBadgeClass(statut: string): string {
    const m: Record<string, string> = {
      'VALIDEE': 'badge-g', 'ENVOYEE': 'badge-g',
      'REJETEE': 'badge-r', 'EN_VALIDATION': 'badge-a',
      'GENEREE': 'badge-x'
    };
    return m[statut] || 'badge-x';
  }

  getStatutLabel(statut: string): string {
    const m: Record<string, string> = {
      'GENEREE': 'Générée', 'EN_VALIDATION': 'En validation',
      'VALIDEE': 'Validée', 'REJETEE': 'Rejetée', 'ENVOYEE': 'Envoyée BCT'
    };
    return m[statut] || statut;
  }

  trackById(_: number, d: Declaration): number { return d.id ?? 0; }
}
