// src/app/auditor-history/auditor-history.component.ts
// BF6 / US-11 — Historique complet des déclarations (lecture seule)
import { Component, OnInit } from '@angular/core';
import { AuditorService, AuditLogDTO } from '../services/auditor.service';
import { DeclarationService, Declaration } from '../services/Declaration.service';

export interface HistoryFilter {
  search:  string;
  statut:  string;
  periode: string;
  type:    string;
}

@Component({
  selector: 'app-auditor-history',
  templateUrl: './auditor-history.component.html',
  styleUrls: ['./auditor-history.component.scss']
})
export class AuditorHistoryComponent implements OnInit {

  loading     = false;
  logsLoading = false;

  declarations: Declaration[] = [];
  selectedDeclaration: Declaration | null = null;
  selectedId: number | null = null;
  currentLogs: AuditLogDTO[] = [];

  private logsCache = new Map<number, AuditLogDTO[]>();

  filters: HistoryFilter = { search: '', statut: '', periode: '', type: '' };

  constructor(
    private auditorService:     AuditorService,
    private declarationService: DeclarationService
  ) {}

  ngOnInit(): void { this.charger(); }

  charger(): void {
    this.loading = true;
    this.declarationService.getAllDeclarations().subscribe({
      next: (data) => {
        this.declarations = data.sort((a, b) => (b.id ?? 0) - (a.id ?? 0));
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  get stats() {
    const all = this.declarations;
    return {
      total:    all.length,
      validees: all.filter(d => ['VALIDEE', 'ENVOYEE'].includes(d.statut)).length,
      rejetees: all.filter(d => d.statut === 'REJETEE').length,
      envoyees: all.filter(d => d.statut === 'ENVOYEE').length,
      enCours:  all.filter(d => ['GENEREE', 'EN_VALIDATION'].includes(d.statut)).length,
    };
  }

  get filteredDeclarations(): Declaration[] {
    return this.declarations.filter(d => {
      const f = this.filters;
      if (f.search) {
        const q = f.search.toLowerCase();
        const ok = String(d.id).includes(q)
          || d.declarationType?.code?.toLowerCase().includes(q)
          || d.declarationType?.nom?.toLowerCase().includes(q)
          || d.generePar?.toLowerCase().includes(q)
          || d.validePar?.toLowerCase().includes(q);
        if (!ok) return false;
      }
      if (f.statut  && d.statut !== f.statut) return false;
      if (f.periode && !d.periode?.includes(f.periode)) return false;
      if (f.type    && d.declarationType?.code !== f.type) return false;
      return true;
    });
  }

  get uniquePeriodes(): string[] {
    return [...new Set(this.declarations.map(d => d.periode || '').filter(Boolean))].sort().reverse();
  }

  get uniqueTypes(): string[] {
    return [...new Set(this.declarations.map(d => d.declarationType?.code || '').filter(Boolean))].sort();
  }

  setStatut(statut: string): void { this.filters.statut = statut; }
  onFilterChange(): void {}

  selectDeclaration(d: Declaration): void {
    this.selectedDeclaration = d;
    this.selectedId = d.id ?? null;
    this.currentLogs = [];

    if (!d.id) return;

    if (this.logsCache.has(d.id)) {
      this.currentLogs = this.logsCache.get(d.id)!;
      return;
    }

    this.logsLoading = true;
    this.auditorService.getLogsByDeclaration(d.id).subscribe({
      next: (logs: AuditLogDTO[]) => {
        this.logsCache.set(d.id!, logs);
        this.currentLogs = logs;
        this.logsLoading = false;
      },
      error: () => { this.logsLoading = false; }
    });
  }

  exportCSV(): void {
    const rows = ['ID,Type,Code,Période,Statut,Généré par,Validé par,Date génération,Date validation,Motif rejet'];
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
    const url  = URL.createObjectURL(blob);
    const a    = document.createElement('a');
    a.href     = url;
    a.download = `historique_audit_${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  exportSingle(d: Declaration): void {
    const rows = ['ID,Type,Période,Statut,Généré par,Validé par,Date génération,Date validation'];
    rows.push([
      d.id, `"${d.declarationType?.nom || ''}"`, d.periode || '', d.statut,
      d.generePar || '', d.validePar || '',
      d.dateGeneration ? new Date(d.dateGeneration).toLocaleDateString('fr-FR') : '',
      d.dateValidation ? new Date(d.dateValidation).toLocaleDateString('fr-FR') : '',
    ].join(','));
    const blob = new Blob([rows.join('\n')], { type: 'text/csv;charset=utf-8;' });
    const url  = URL.createObjectURL(blob);
    const a    = document.createElement('a');
    a.href     = url;
    a.download = `decl_${d.id}_${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  // ─── Helpers ───────────────────────────────────────────
  getDotClass(statut: string): string {
    const m: Record<string, string> = {
      'VALIDEE': 'dot-green', 'ENVOYEE': 'dot-green',
      'REJETEE': 'dot-red',   'EN_VALIDATION': 'dot-amber',
      'GENEREE': 'dot-muted'
    };
    return m[statut] || 'dot-muted';
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

  getActionDotClass(action: string): string {
    const m: Record<string, string> = {
      'SUBMIT': 'td-sub', 'VALIDATE': 'td-val', 'REJECT': 'td-rej', 'SEND': 'td-snd'
    };
    return m[action] || '';
  }

  getActionTextClass(action: string): string {
    const m: Record<string, string> = {
      'SUBMIT': 'ta-sub', 'VALIDATE': 'ta-val', 'REJECT': 'ta-rej', 'SEND': 'ta-snd'
    };
    return m[action] || '';
  }

  getActionLabel(action: string): string {
    const m: Record<string, string> = {
      'SUBMIT': 'Soumission', 'VALIDATE': 'Validation',
      'REJECT': 'Rejet',      'SEND': 'Envoi BCT'
    };
    return m[action] || action;
  }

  getActionIcon(action: string): string {
    const m: Record<string, string> = { 'SUBMIT': '→', 'VALIDATE': '✓', 'REJECT': '✕', 'SEND': '✉' };
    return m[action] || '·';
  }

  trackById(_: number, d: Declaration): number { return d.id ?? 0; }
}
