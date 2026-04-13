// src/app/manager-pending/manager-pending.component.ts
// US-08 — Validation dédiée : page focalisée uniquement sur EN_VALIDATION

import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { DeclarationService, Declaration } from '../services/Declaration.service';
import { ValidationService, ValidationStats, ValidationLog } from '../services/Validation.service';
import { JiraService, JiraTicketResponse } from '../services/jira.service';

type SortField = 'id' | 'type' | 'periode' | 'date';
type SortDir   = 'asc' | 'desc';

@Component({
  selector: 'app-manager-pending',
  templateUrl: './manager-pending.component.html',
  styleUrls: ['./manager-pending.component.scss']
})
export class ManagerPendingComponent implements OnInit, OnDestroy {

  // ── Données ────────────────────────────────────────────
  pending: Declaration[] = [];
  stats: ValidationStats | null = null;

  // ── État ───────────────────────────────────────────────
  loading = false;
  actionEnCours: Record<number, boolean> = {};
  lastRefreshed: Date | null = null;
  autoRefreshInterval: any;

  // ── Filtres & tri ──────────────────────────────────────
  searchQuery = '';
  filtreType  = '';
  sortField: SortField = 'id';
  sortDir: SortDir = 'desc';

  // ── Sélection multiple ─────────────────────────────────
  selectedIds = new Set<number>();
  bulkLoading = false;

  // ── Modal Consultation ─────────────────────────────────
  showConsultModal = false;
  declarationSelectionnee: Declaration | null = null;
  consultLogs: ValidationLog[] = [];
  consultLogsLoading = false;

  // ── Modal Rejet ────────────────────────────────────────
  showRejetModal = false;
  commentaireRejet = '';
  commentaireRejetTouched = false;
  rejetEnCours = false;

  // ── Modal Historique ───────────────────────────────────
  showHistoriqueModal = false;
  historique: ValidationLog[] = [];
  historiqueLoading = false;

  // ── Toast ──────────────────────────────────────────────
  message = '';
  messageType: 'success' | 'error' | 'info' = 'success';

  // ── Jira ───────────────────────────────────────────────
  jiraLoading: Record<number, boolean> = {};
  private jiraTicketMap = new Map<number, JiraTicketResponse | null>();
  private jiraSub!: Subscription;

  constructor(
    private declarationService: DeclarationService,
    private validationService: ValidationService,
    public jiraService: JiraService
  ) {}

  ngOnInit(): void {
    this.jiraSub = this.jiraService.ticketMap$.subscribe(map => {
      this.jiraTicketMap = map;
    });
    this.charger();
    // Auto-refresh toutes les 60 secondes
    this.autoRefreshInterval = setInterval(() => this.charger(true), 60000);
  }

  ngOnDestroy(): void {
    this.jiraSub?.unsubscribe();
    clearInterval(this.autoRefreshInterval);
  }

  // ─── Chargement ───────────────────────────────────────

  charger(silent = false): void {
    if (!silent) this.loading = true;

    this.validationService.getStats().subscribe({
      next: (s) => this.stats = s,
      error: () => {}
    });

    this.validationService.getPendingDeclarations().subscribe({
      next: (data) => {
        this.pending = data;
        this.lastRefreshed = new Date();
        this.loading = false;
        // Charger les tickets Jira
        data.forEach(d => {
          if (d.id && !this.jiraTicketMap.has(d.id)) {
            this.jiraLoading[d.id] = true;
            this.jiraService.getTicketForDeclaration(d.id).subscribe({
              next: () => { this.jiraLoading[d.id!] = false; },
              error: () => { this.jiraLoading[d.id!] = false; }
            });
          }
        });
      },
      error: () => {
        this.loading = false;
        if (!silent) this.showToast('Erreur chargement des déclarations en attente', 'error');
      }
    });
  }

  // ─── Filtres & tri ────────────────────────────────────

  get uniqueTypes(): string[] {
    return [...new Set(this.pending.map(d => d.declarationType?.code || '').filter(Boolean))].sort();
  }

  get filteredPending(): Declaration[] {
    let list = [...this.pending];

    if (this.searchQuery) {
      const q = this.searchQuery.toLowerCase();
      list = list.filter(d =>
        String(d.id).includes(q) ||
        d.declarationType?.code?.toLowerCase().includes(q) ||
        d.declarationType?.nom?.toLowerCase().includes(q) ||
        d.generePar?.toLowerCase().includes(q) ||
        d.periode?.toLowerCase().includes(q)
      );
    }

    if (this.filtreType) {
      list = list.filter(d => d.declarationType?.code === this.filtreType);
    }

    // Tri
    list.sort((a, b) => {
      let va: any, vb: any;
      switch (this.sortField) {
        case 'id':      va = a.id ?? 0; vb = b.id ?? 0; break;
        case 'type':    va = a.declarationType?.code ?? ''; vb = b.declarationType?.code ?? ''; break;
        case 'periode': va = a.periode ?? ''; vb = b.periode ?? ''; break;
        case 'date':    va = a.dateGeneration ? new Date(a.dateGeneration).getTime() : 0;
                        vb = b.dateGeneration ? new Date(b.dateGeneration).getTime() : 0; break;
      }
      const cmp = va < vb ? -1 : va > vb ? 1 : 0;
      return this.sortDir === 'asc' ? cmp : -cmp;
    });

    return list;
  }

  toggleSort(field: SortField): void {
    if (this.sortField === field) {
      this.sortDir = this.sortDir === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortField = field;
      this.sortDir = 'desc';
    }
  }

  // ─── Sélection multiple ───────────────────────────────

  toggleSelect(id: number): void {
    if (this.selectedIds.has(id)) this.selectedIds.delete(id);
    else this.selectedIds.add(id);
  }

  toggleSelectAll(): void {
    if (this.selectedIds.size === this.filteredPending.length) {
      this.selectedIds.clear();
    } else {
      this.filteredPending.forEach(d => { if (d.id) this.selectedIds.add(d.id); });
    }
  }

  get allSelected(): boolean {
    return this.filteredPending.length > 0 && this.selectedIds.size === this.filteredPending.length;
  }

  get someSelected(): boolean {
    return this.selectedIds.size > 0 && !this.allSelected;
  }

  async validerSelection(): Promise<void> {
    if (this.selectedIds.size === 0) return;
    const ids = [...this.selectedIds];
    const msg = `Valider les ${ids.length} déclaration(s) sélectionnée(s) ?`;
    if (!confirm(msg)) return;

    this.bulkLoading = true;
    let success = 0;
    let errors = 0;

    for (const id of ids) {
      try {
        await this.validationService.validateDeclaration(id).toPromise();
        success++;
        this.pending = this.pending.filter(x => x.id !== id);
        this.selectedIds.delete(id);
        this.jiraService.invalidateCache(id);
      } catch {
        errors++;
      }
    }

    this.bulkLoading = false;
    this.rafraichirStats();
    if (errors === 0) {
      this.showToast(`✅ ${success} déclaration(s) validée(s) avec succès.`, 'success');
    } else {
      this.showToast(`✅ ${success} validée(s), ❌ ${errors} erreur(s).`, 'error');
    }
  }

  // ─── Actions individuelles ─────────────────────────────

  valider(d: Declaration): void {
    if (!d.id) return;
    if (!confirm(`Valider la déclaration #${d.id} — ${d.declarationType?.nom} (${d.periode}) ?`)) return;

    this.actionEnCours[d.id] = true;
    this.validationService.validateDeclaration(d.id).subscribe({
      next: () => {
        this.pending = this.pending.filter(x => x.id !== d.id);
        this.actionEnCours[d.id!] = false;
        this.rafraichirStats();
        this.jiraService.invalidateCache(d.id!);
        this.showToast(`✅ Déclaration #${d.id} validée avec succès.`, 'success');
      },
      error: (err) => {
        this.actionEnCours[d.id!] = false;
        this.showToast('❌ ' + (err.error?.error || err.message), 'error');
      }
    });
  }

  ouvrirRejet(d: Declaration): void {
    this.declarationSelectionnee = d;
    this.commentaireRejet = '';
    this.commentaireRejetTouched = false;
    this.showRejetModal = true;
  }

  fermerRejet(): void {
    this.showRejetModal = false;
    this.declarationSelectionnee = null;
    this.commentaireRejet = '';
    this.rejetEnCours = false;
  }

  confirmerRejet(): void {
    this.commentaireRejetTouched = true;
    if (!this.declarationSelectionnee?.id || !this.commentaireRejet.trim()) return;

    const id = this.declarationSelectionnee.id;
    this.rejetEnCours = true;

    this.validationService.rejectDeclaration(id, this.commentaireRejet.trim()).subscribe({
      next: () => {
        this.pending = this.pending.filter(x => x.id !== id);
        this.fermerRejet();
        this.rafraichirStats();
        this.jiraService.invalidateCache(id);
        this.showToast(`❌ Déclaration #${id} rejetée.`, 'error');
      },
      error: (err) => {
        this.rejetEnCours = false;
        this.showToast('Erreur : ' + (err.error?.error || err.message), 'error');
      }
    });
  }

  // ─── Modal Consultation ───────────────────────────────

  ouvrirConsultation(d: Declaration): void {
    this.declarationSelectionnee = d;
    this.consultLogs = [];
    this.consultLogsLoading = true;
    this.showConsultModal = true;

    if (d.id) {
      this.validationService.getHistory(d.id).subscribe({
        next: (logs) => { this.consultLogs = logs; this.consultLogsLoading = false; },
        error: () => { this.consultLogsLoading = false; }
      });
    }
  }

  fermerConsultation(): void {
    this.showConsultModal = false;
    this.consultLogs = [];
  }

  fermerEtValider(): void {
    const d = this.declarationSelectionnee;
    this.fermerConsultation();
    if (d) setTimeout(() => this.valider(d), 100);
  }

  fermerEtRejeter(): void {
    const d = this.declarationSelectionnee;
    this.fermerConsultation();
    if (d) setTimeout(() => this.ouvrirRejet(d), 100);
  }

  // ─── Historique ───────────────────────────────────────

  voirHistorique(d: Declaration): void {
    if (!d.id) return;
    this.declarationSelectionnee = d;
    this.historique = [];
    this.historiqueLoading = true;
    this.showHistoriqueModal = true;

    this.validationService.getHistory(d.id).subscribe({
      next: (logs) => { this.historique = logs; this.historiqueLoading = false; },
      error: () => { this.historiqueLoading = false; }
    });
  }

  fermerHistorique(): void {
    this.showHistoriqueModal = false;
    this.historique = [];
    this.declarationSelectionnee = null;
  }

  // ─── Jira ─────────────────────────────────────────────

  getJiraTicket(id: number): JiraTicketResponse | null {
    return this.jiraTicketMap.get(id) ?? null;
  }

  ouvrirJira(d: Declaration): void {
    const ticket = this.getJiraTicket(d.id!);
    if (ticket) this.jiraService.openJiraTicket(ticket);
  }

  // ─── Download ─────────────────────────────────────────

  download(d: Declaration): void {
    if (!d.id) return;
    this.declarationService.downloadDeclaration(d.id).subscribe({
      next: (blob) => {
        const mime = this.declarationService.resolveMimeType(d.nomFichier || '');
        const url = window.URL.createObjectURL(new Blob([blob], { type: mime }));
        const a = document.createElement('a');
        a.href = url;
        a.download = d.nomFichier || `declaration_${d.id}`;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => this.showToast('❌ Erreur téléchargement', 'error')
    });
  }

  // ─── Helpers ──────────────────────────────────────────

  private rafraichirStats(): void {
    this.validationService.getStats().subscribe({ next: (s) => this.stats = s, error: () => {} });
  }

  private showToast(msg: string, type: 'success' | 'error' | 'info'): void {
    this.message = msg;
    this.messageType = type;
    setTimeout(() => this.message = '', 5000);
  }

  getStatutClass(s: string): string {
    const map: Record<string, string> = {
      'GENEREE': 'sc-generee', 'EN_VALIDATION': 'sc-validation',
      'VALIDEE': 'sc-validee', 'REJETEE': 'sc-rejetee', 'ENVOYEE': 'sc-envoyee',
    };
    return map[s] || '';
  }

  getStatutLabel(s: string): string {
    const map: Record<string, string> = {
      'GENEREE': 'Générée', 'EN_VALIDATION': 'En validation',
      'VALIDEE': 'Validée', 'REJETEE': 'Rejetée', 'ENVOYEE': 'Envoyée',
    };
    return map[s] || s;
  }

  getActionClass(a: string): string {
    const map: Record<string, string> = { 'SUBMIT': 'act-submit', 'VALIDATE': 'act-validate', 'REJECT': 'act-reject', 'SEND': 'act-send' };
    return map[a] || '';
  }

  getActionLabel(a: string): string {
    const map: Record<string, string> = { 'SUBMIT': '📤 Soumission', 'VALIDATE': '✅ Validation', 'REJECT': '❌ Rejet', 'SEND': '📨 Envoi BCT' };
    return map[a] || a;
  }

  getSortIcon(field: SortField): string {
    if (this.sortField !== field) return '↕';
    return this.sortDir === 'asc' ? '↑' : '↓';
  }

  trackById(_: number, d: Declaration): number { return d.id ?? 0; }

  formatTime(date: Date): string {
    return date.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }
}