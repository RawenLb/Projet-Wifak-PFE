// src/app/agent-declaration-types/agent-declaration-types.component.ts
import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { DeclarationTypeService, DeclarationType } from '../../services/declaration-type.service';

@Component({
  selector: 'app-agent-declaration-types',
  templateUrl: './agent-declaration-types.component.html',
  styleUrls:  ['./agent-declaration-types.component.scss']
})
export class AgentDeclarationTypesComponent implements OnInit {

  allTypes:      DeclarationType[] = [];
  filteredTypes: DeclarationType[] = [];
  loading        = false;

  // Filtres
  searchQuery  = '';
  filterFormat = '';
  filterFreq   = '';

  // Vue
  viewMode: 'table' | 'grid' = 'table';

  // Modal détail
  showDetail    = false;
  selectedType: DeclarationType | null = null;

  // ── Stats ──────────────────────────────────────────────────
  get activeCount():  number { return this.allTypes.filter(t => t.actif).length; }
  get monthlyCount(): number { return this.allTypes.filter(t => t.frequence === 'MENSUELLE').length; }
  get xmlCount():     number { return this.allTypes.filter(t => t.format === 'XML').length; }

  constructor(
    private declarationTypeService: DeclarationTypeService,
    private router: Router
  ) {}

  ngOnInit(): void { this.loadTypes(); }

  loadTypes(): void {
    this.loading = true;
    this.declarationTypeService.getAll().subscribe({
      next: (types) => {
        // L'agent ne voit que les types actifs
        this.allTypes      = types.filter(t => t.actif);
        this.filteredTypes = [...this.allTypes];
        this.loading       = false;
      },
      error: () => {
        this.allTypes      = [];
        this.filteredTypes = [];
        this.loading       = false;
      }
    });
  }

  applyFilters(): void {
    const q = this.searchQuery.toLowerCase().trim();
    this.filteredTypes = this.allTypes.filter(t => {
      const matchSearch = !q || [t.code, t.nom, t.format, t.frequence]
        .some(v => v?.toLowerCase().includes(q));
      const matchFormat = !this.filterFormat || t.format === this.filterFormat;
      const matchFreq   = !this.filterFreq   || t.frequence === this.filterFreq;
      return matchSearch && matchFormat && matchFreq;
    });
  }

  clearSearch(): void {
    this.searchQuery  = '';
    this.filterFormat = '';
    this.filterFreq   = '';
    this.filteredTypes = [...this.allTypes];
  }

  // ── Modal ──────────────────────────────────────────────────
  openDetail(t: DeclarationType): void {
    this.selectedType = t;
    this.showDetail   = true;
  }

  closeDetail(): void {
    this.showDetail   = false;
    this.selectedType = null;
  }

  goToNewDeclaration(): void {
    this.closeDetail();
    this.router.navigate(['/agent/declarations']);
  }

  // ── Badge helpers ──────────────────────────────────────────
  getFormatClass(format: string): string {
    const m: Record<string, string> = {
      XML: 'fmt-xml', CSV: 'fmt-csv', TXT: 'fmt-txt', PDF: 'fmt-pdf'
    };
    return m[format] || 'fmt-default';
  }

  getFreqClass(freq: string): string {
    const m: Record<string, string> = {
      QUOTIDIENNE:   'freq-daily',
      HEBDOMADAIRE:  'freq-weekly',
      MENSUELLE:     'freq-monthly',
      TRIMESTRIELLE: 'freq-quarterly',
      ANNUELLE:      'freq-yearly'
    };
    return m[freq] || 'freq-default';
  }
}