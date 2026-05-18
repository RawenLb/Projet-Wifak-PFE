import { Component, OnInit, Input } from '@angular/core';
import { DeclarationService, Declaration } from '../../services/Declaration.service';
import { ValidationService, ValidationLog } from '../../services/Validation.service';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-treated-declarations',
  templateUrl: './treated-declarations.component.html',
  styleUrls: ['./treated-declarations.component.scss']
})
export class TreatedDeclarationsComponent implements OnInit {

  /** 'agent' → only own declarations, 'manager' → all declarations */
  @Input() mode: 'agent' | 'manager' = 'manager';

  // expose Math for template
  Math = Math;

  loading      = false;
  logsLoading  = false;
  exporting    = false;

  declarations: Declaration[]  = [];
  selected:     Declaration | null = null;
  logs:         ValidationLog[] = [];

  private logsCache = new Map<number, ValidationLog[]>();

  // Filters
  search      = '';
  filterType  = '';
  filterPeriode = '';
  filterAgent = '';

  // Pagination
  currentPage  = 1;
  itemsPerPage = 12;

  constructor(
    private declarationService: DeclarationService,
    private validationService:  ValidationService,
    private toast:              ToastService
  ) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    const obs = this.mode === 'agent'
      ? this.declarationService.getMyDeclarations()
      : this.declarationService.getAllDeclarations();

    obs.subscribe({
      next: (data) => {
        this.declarations = data
          .filter(d => d.statut === 'ENVOYEE')
          .sort((a, b) => {
            const da = a.dateValidation ? new Date(a.dateValidation).getTime() : 0;
            const db = b.dateValidation ? new Date(b.dateValidation).getTime() : 0;
            return db - da;
          });
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  // ── Computed ──────────────────────────────────────────────────────

  get uniqueTypes(): string[] {
    return [...new Set(this.declarations.map(d => d.declarationType?.code || '').filter(Boolean))].sort();
  }

  get uniquePeriodes(): string[] {
    return [...new Set(this.declarations.map(d => d.periode || '').filter(Boolean))].sort().reverse();
  }

  get uniqueAgents(): string[] {
    return [...new Set(this.declarations.map(d => d.generePar || '').filter(Boolean))].sort();
  }

  get filtered(): Declaration[] {
    return this.declarations.filter(d => {
      if (this.search) {
        const q = this.search.toLowerCase();
        const ok = String(d.id).includes(q)
          || d.declarationType?.code?.toLowerCase().includes(q)
          || d.declarationType?.nom?.toLowerCase().includes(q)
          || d.generePar?.toLowerCase().includes(q)
          || d.validePar?.toLowerCase().includes(q)
          || d.periode?.toLowerCase().includes(q);
        if (!ok) return false;
      }
      if (this.filterType    && d.declarationType?.code !== this.filterType)    return false;
      if (this.filterPeriode && !d.periode?.includes(this.filterPeriode))        return false;
      if (this.filterAgent   && d.generePar !== this.filterAgent)                return false;
      return true;
    });
  }

  get totalPages(): number { return Math.max(1, Math.ceil(this.filtered.length / this.itemsPerPage)); }

  get paged(): Declaration[] {
    const s = (this.currentPage - 1) * this.itemsPerPage;
    return this.filtered.slice(s, s + this.itemsPerPage);
  }

  get pages(): number[] {
    const total = this.totalPages;
    if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);
    const p: number[] = [1];
    if (this.currentPage > 3) p.push(-1);
    for (let i = Math.max(2, this.currentPage - 1); i <= Math.min(total - 1, this.currentPage + 1); i++) p.push(i);
    if (this.currentPage < total - 2) p.push(-1);
    p.push(total);
    return p;
  }

  onFilterChange(): void { this.currentPage = 1; }
  resetFilters(): void { this.search = ''; this.filterType = ''; this.filterPeriode = ''; this.filterAgent = ''; this.currentPage = 1; }
  get hasFilters(): boolean { return !!(this.search || this.filterType || this.filterPeriode || this.filterAgent); }

  // ── Detail panel ──────────────────────────────────────────────────

  select(d: Declaration): void {
    if (this.selected?.id === d.id) { this.selected = null; return; }
    this.selected = d;
    this.logs = [];
    if (!d.id) return;
    if (this.logsCache.has(d.id)) { this.logs = this.logsCache.get(d.id)!; return; }
    this.logsLoading = true;
    this.validationService.getHistory(d.id).subscribe({
      next: (l) => { this.logsCache.set(d.id!, l); this.logs = l; this.logsLoading = false; },
      error: () => { this.logsLoading = false; }
    });
  }

  closeDetail(): void { this.selected = null; }

  // ── Download ──────────────────────────────────────────────────────

  download(d: Declaration): void {
    if (!d.id) return;
    this.declarationService.downloadDeclaration(d.id).subscribe({
      next: (blob) => {
        const mime = this.declarationService.resolveMimeType(d.nomFichier || '');
        const url  = URL.createObjectURL(new Blob([blob], { type: mime }));
        const a    = document.createElement('a');
        a.href = url; a.download = d.nomFichier || `declaration_${d.id}`; a.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.toast.error('Erreur lors du téléchargement')
    });
  }

  // ── Export CSV ────────────────────────────────────────────────────

  exportCSV(): void {
    if (this.exporting) return;
    this.exporting = true;
    try {
      const BOM = '\uFEFF';
      const rows = ['ID,Type (code),Type (nom),Période,Agent,Validé par,Date génération,Date validation,Fichier'];
      this.filtered.forEach(d => {
        rows.push([
          d.id ?? '',
          d.declarationType?.code ?? '',
          `"${d.declarationType?.nom ?? ''}"`,
          d.periode ?? '',
          d.generePar ?? '',
          d.validePar ?? '',
          d.dateGeneration ? new Date(d.dateGeneration).toLocaleDateString('fr-FR') : '',
          d.dateValidation ? new Date(d.dateValidation).toLocaleDateString('fr-FR') : '',
          d.nomFichier ?? '',
        ].join(','));
      });
      const blob = new Blob([BOM + rows.join('\n')], { type: 'text/csv;charset=utf-8;' });
      const url  = URL.createObjectURL(blob);
      const a    = document.createElement('a');
      a.href = url;
      a.download = `declarations-traitees-${new Date().toISOString().slice(0,10)}.csv`;
      a.click();
      URL.revokeObjectURL(url);
      this.toast.success(`${this.filtered.length} déclaration(s) exportée(s).`);
    } catch { this.toast.error('Erreur lors de l\'export.'); }
    finally { this.exporting = false; }
  }

  // ── Helpers ───────────────────────────────────────────────────────

  formatDate(d?: string): string {
    if (!d) return '—';
    return new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' });
  }

  getActionLabel(a: string): string {
    return ({ SUBMIT: 'Soumission', VALIDATE: 'Validation', REJECT: 'Rejet', SEND: 'Traitement' } as Record<string,string>)[a] || a;
  }

  getActionClass(a: string): string {
    return ({ SUBMIT: 'act-sub', VALIDATE: 'act-val', REJECT: 'act-rej', SEND: 'act-snd' } as Record<string,string>)[a] || '';
  }

  trackById(_: number, d: Declaration): number { return d.id ?? 0; }
}
