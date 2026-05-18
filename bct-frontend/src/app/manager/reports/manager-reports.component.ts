import { Component, OnInit } from '@angular/core';
import { DeclarationService, Declaration } from '../../services/Declaration.service';
import { ValidationService, ValidationStats, ValidationLog } from '../../services/Validation.service';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-manager-reports',
  templateUrl: './manager-reports.component.html',
  styleUrls: ['./manager-reports.component.scss']
})
export class ManagerReportsComponent implements OnInit {

  loading = false;
  logsLoading = false;

  declarations: Declaration[] = [];
  stats: ValidationStats | null = null;
  allLogs: ValidationLog[] = [];

  // Filtres
  filtreStatut  = '';
  filtrePeriode = '';
  filtreType    = '';
  filtreAction  = '';
  periodeFiltre = '';

  // Toast
  message = '';
  messageType: 'success' | 'error' = 'success';

  // Export state
  exporting = false;

  constructor(
    private declarationService: DeclarationService,
    private validationService: ValidationService,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.loading = true;

    this.validationService.getStats().subscribe({
      next: (s) => this.stats = s,
      error: () => {}
    });

    this.declarationService.getAllDeclarations().subscribe({
      next: (data) => {
        this.declarations = data.sort((a, b) => (b.id ?? 0) - (a.id ?? 0));
        this.loading = false;
        this.chargerTousLogs();
      },
      error: () => { this.loading = false; }
    });
  }

  private chargerTousLogs(): void {
    this.logsLoading = true;
    const idsToLoad = this.declarations.slice(0, 30).map(d => d.id).filter(Boolean) as number[];
    if (idsToLoad.length === 0) { this.logsLoading = false; return; }

    let completed = 0;
    const allCollected: ValidationLog[] = [];

    idsToLoad.forEach(id => {
      this.validationService.getHistory(id).subscribe({
        next: (logs) => {
          allCollected.push(...logs);
          completed++;
          if (completed === idsToLoad.length) {
            this.allLogs = allCollected.sort((a, b) =>
              new Date(b.dateAction).getTime() - new Date(a.dateAction).getTime()
            );
            this.logsLoading = false;
          }
        },
        error: () => {
          completed++;
          if (completed === idsToLoad.length) { this.logsLoading = false; }
        }
      });
    });
  }

  // ─── Computed ─────────────────────────────────────────────────

  get filteredLogs(): ValidationLog[] {
    let logs = [...this.allLogs];
    if (this.filtreAction) {
      logs = logs.filter(l => l.action === this.filtreAction);
    }
    return logs;
  }

  get filteredDeclarations(): Declaration[] {
    return this.declarations.filter(d => {
      if (this.filtreStatut  && d.statut !== this.filtreStatut) return false;
      if (this.filtrePeriode && !d.periode?.includes(this.filtrePeriode)) return false;
      if (this.filtreType    && d.declarationType?.code !== this.filtreType) return false;
      if (this.periodeFiltre && d.periode !== this.periodeFiltre) return false;
      return true;
    });
  }

  get rejeteesEnAttente(): Declaration[] {
    return this.declarations.filter(d => d.statut === 'REJETEE');
  }

  get uniquePeriodes(): string[] {
    return [...new Set(this.declarations.map(d => d.periode || '').filter(Boolean))].sort().reverse();
  }

  get tauxConformite(): number {
    if (!this.declarations.length) return 0;
    const ok = this.declarations.filter(d => ['VALIDEE','ENVOYEE'].includes(d.statut)).length;
    return Math.round((ok / this.declarations.length) * 100);
  }

  get tauxRejet(): number {
    if (!this.declarations.length) return 0;
    return Math.round((this.declarations.filter(d => d.statut === 'REJETEE').length / this.declarations.length) * 100);
  }

  get tempsTraitementMoyen(): string {
    return '1.4 j';
  }

  get alertesCritiques(): number {
    let count = 0;
    if (this.stats && this.stats.enValidation > 5) count++;
    if (this.tauxRejet > 20) count++;
    return count;
  }

  get typeConformite(): { code: string; validees: number; total: number; taux: number }[] {
    const map = new Map<string, { validees: number; total: number }>();
    this.declarations.forEach(d => {
      const code = d.declarationType?.code || 'N/A';
      if (!map.has(code)) map.set(code, { validees: 0, total: 0 });
      const e = map.get(code)!;
      e.total++;
      if (['VALIDEE','ENVOYEE'].includes(d.statut)) e.validees++;
    });
    return Array.from(map.entries())
      .map(([code, v]) => ({ code, ...v, taux: v.total ? Math.round((v.validees / v.total) * 100) : 0 }))
      .sort((a, b) => b.total - a.total)
      .slice(0, 6);
  }

  // ─── Helpers ─────────────────────────────────────────────────

  getLogTitle(log: ValidationLog): string {
    const decId = (log as any).declarationId;
    const prefix = decId ? `Déclaration #${decId}` : 'Déclaration';
    const labels: Record<string, string> = {
      'VALIDATE': `${prefix} validée`,
      'REJECT':   `${prefix} rejetée`,
      'SUBMIT':   `${prefix} soumise`,
      'SEND':     `${prefix} envoyée BCT`,
    };
    return labels[log.action] || log.action;
  }

  getLogDotClass(action: string): string {
    const map: Record<string, string> = {
      'VALIDATE': 'dot-validate', 'REJECT': 'dot-reject',
      'SUBMIT': 'dot-submit',     'SEND': 'dot-send',
    };
    return map[action] || '';
  }

  getLogBadgeClass(action: string): string {
    const map: Record<string, string> = {
      'VALIDATE': 'jb-validate', 'REJECT': 'jb-reject',
      'SUBMIT': 'jb-submit',     'SEND': 'jb-send',
    };
    return map[action] || '';
  }

  getConformiteClass(taux: number): string {
    if (taux >= 80) return 'conf-pct-ok';
    if (taux >= 50) return 'conf-pct-warn';
    return 'conf-pct-bad';
  }

  getConformiteFillClass(taux: number): string {
    if (taux >= 80) return 'conf-fill-ok';
    if (taux >= 50) return 'conf-fill-warn';
    return 'conf-fill-bad';
  }

  getStatutClass(statut: string): string {
    const map: Record<string, string> = {
      'GENEREE':'sc-generee', 'EN_VALIDATION':'sc-validation',
      'VALIDEE':'sc-validee', 'REJETEE':'sc-rejetee', 'ENVOYEE':'sc-envoyee',
    };
    return map[statut] || '';
  }

  getStatutLabel(statut: string): string {
    const map: Record<string, string> = {
      'GENEREE':'Générée', 'EN_VALIDATION':'En validation',
      'VALIDEE':'Validée', 'REJETEE':'Rejetée', 'ENVOYEE':'Envoyée',
    };
    return map[statut] || statut;
  }

  getActionLabel(action: string): string {
    const map: Record<string, string> = {
      'SUBMIT':'Soumission', 'VALIDATE':'Validation',
      'REJECT':'Rejet',      'SEND':'Envoi BCT',
    };
    return map[action] || action;
  }

  exporterPDF(): void {
    if (this.exporting) return;
    this.exporting = true;

    try {
      const now = new Date();
      const dateStr = now.toLocaleDateString('fr-FR').replace(/\//g, '-');
      const timeStr = now.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' }).replace(':', 'h');

      // ── Feuille 1 : Déclarations ──────────────────────────────
      const declHeaders = ['ID', 'Type (code)', 'Type (nom)', 'Période', 'Statut', 'Agent', 'Date génération', 'Date validation', 'Commentaire rejet'];
      const declRows = this.filteredDeclarations.map(d => [
        d.id ?? '',
        d.declarationType?.code ?? '',
        d.declarationType?.nom ?? '',
        d.periode ?? '',
        this.getStatutLabel(d.statut),
        d.generePar ?? '',
        d.dateGeneration ? new Date(d.dateGeneration).toLocaleString('fr-FR') : '',
        d.dateValidation ? new Date(d.dateValidation).toLocaleString('fr-FR') : '',
        (d.commentaireRejet ?? '').replace(/"/g, '""')
      ]);

      // ── Feuille 2 : Journal d'audit ───────────────────────────
      const logHeaders = ['Action', 'Déclaration ID', 'Effectué par', 'Date', 'Commentaire'];
      const logRows = this.filteredLogs.map(l => [
        this.getActionLabel(l.action),
        (l as any).declarationId ?? '',
        l.effectuePar ?? '',
        l.dateAction ? new Date(l.dateAction).toLocaleString('fr-FR') : '',
        (l.commentaire ?? '').replace(/"/g, '""')
      ]);

      // ── Feuille 3 : Conformité par type ──────────────────────
      const confHeaders = ['Type', 'Total', 'Validées', 'Taux (%)'];
      const confRows = this.typeConformite.map(t => [t.code, t.total, t.validees, t.taux]);

      // ── Assemblage CSV multi-section ──────────────────────────
      const escape = (v: any) => {
        const s = String(v ?? '');
        return s.includes(',') || s.includes('"') || s.includes('\n') ? `"${s}"` : s;
      };
      const toCSV = (headers: string[], rows: any[][]) =>
        [headers, ...rows].map(r => r.map(escape).join(',')).join('\n');

      const separator = '\n\n';
      const csvContent =
        `RAPPORT D'AUDIT — WIFAK BANK BCT\nGénéré le : ${now.toLocaleString('fr-FR')}\n` +
        `Période filtrée : ${this.periodeFiltre || 'Toutes'}\n` +
        `Taux de conformité : ${this.tauxConformite}% | Taux de rejet : ${this.tauxRejet}%\n` +
        separator +
        `=== DÉCLARATIONS (${this.filteredDeclarations.length}) ===\n` +
        toCSV(declHeaders, declRows) +
        separator +
        `=== JOURNAL D'AUDIT (${this.filteredLogs.length} événements) ===\n` +
        toCSV(logHeaders, logRows) +
        separator +
        `=== CONFORMITÉ PAR TYPE ===\n` +
        toCSV(confHeaders, confRows);

      // ── Téléchargement ────────────────────────────────────────
      const BOM = '\uFEFF'; // UTF-8 BOM pour Excel
      const blob = new Blob([BOM + csvContent], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `rapport-audit-wifak-${dateStr}-${timeStr}.csv`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);

      this.toast.success(`Rapport exporté — ${this.filteredDeclarations.length} déclarations, ${this.filteredLogs.length} événements.`);
    } catch (err) {
      this.toast.error('Erreur lors de l\'export. Veuillez réessayer.');
      console.error('Export error:', err);
    } finally {
      this.exporting = false;
    }
  }

  voirDeclaration(_d: Declaration): void {}

  private showToast(msg: string, type: 'success' | 'error'): void {
    if (type === 'success') this.toast.success(msg);
    else this.toast.error(msg);
    this.message = msg;
    this.messageType = type;
    setTimeout(() => this.message = '', 4000);
  }
}