import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { DeclarationService, Declaration } from '../../services/Declaration.service';
import { ValidationService, ValidationStats, ValidationLog } from '../../services/Validation.service';
import { JiraService, JiraTicketResponse } from '../../services/jira.service';
import { ConfirmDialogService } from '../../services/confirm-dialog.service';
import { ToastService } from '../../services/toast.service';

export interface WeekData { label: string; ok: number; rej: number; enc: number; }
export interface AgentStat { nom: string; initiales: string; total: number; tauxValidation: number; }

@Component({
  selector: 'app-manager-dashboard',
  templateUrl: './manager-dashboard.component.html',
  styleUrls: ['./manager-dashboard.component.scss']
})
export class ManagerDashboardComponent implements OnInit, OnDestroy {

  // ── Données ──────────────────────────────────────────────────
  toutesDeclarations: Declaration[] = [];
  declarationsFiltrees: Declaration[] = [];
  stats: ValidationStats | null = null;

  // ── État ─────────────────────────────────────────────────────
  loading = false;
  filtreStatut = '';
  actionEnCours: Record<number, boolean> = {};
  periodWindow: 7 | 30 | 90 = 30;

  // ── Modal Consultation ────────────────────────────────────────
  showConsultModal = false;
  declarationSelectionnee: Declaration | null = null;
  consultHistorique: ValidationLog[] = [];
  consultHistoriqueLoading = false;

  // ── Modal Rejet ───────────────────────────────────────────────
  showRejetModal = false;
  commentaireRejet = '';
  commentaireRejetTouched = false;
  rejetEnCours = false;

  // ── Modal Historique ──────────────────────────────────────────
  showHistoriqueModal = false;
  historique: ValidationLog[] = [];
  historiqueLoading = false;

  // ── Toast ─────────────────────────────────────────────────────
  message = '';
  messageType = 'success';

  // ── Jira ──────────────────────────────────────────────────────
  jiraLoading: Record<number, boolean> = {};
  private jiraTicketMap = new Map<number, JiraTicketResponse | null>();
  private jiraSub!: Subscription;

  constructor(
    private declarationService: DeclarationService,
    private validationService: ValidationService,
    public jiraService: JiraService,
    private confirmDialog: ConfirmDialogService,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.jiraSub = this.jiraService.ticketMap$.subscribe(map => {
      this.jiraTicketMap = map;
    });
    this.chargerDonnees();
  }

  ngOnDestroy(): void {
    this.jiraSub?.unsubscribe();
  }

  // ─── Chargement ───────────────────────────────────────────────

  chargerDonnees(): void {
    this.loading = true;

    this.validationService.getStats().subscribe({
      next: (s) => this.stats = s,
      error: () => {}
    });

    this.declarationService.getAllDeclarations().subscribe({
      next: (data) => {
        this.toutesDeclarations = data.sort((a, b) => (b.id ?? 0) - (a.id ?? 0));
        this.declarationsFiltrees = [...this.toutesDeclarations];
        this.loading = false;
        const eligible = data.filter(d =>
          ['EN_VALIDATION','VALIDEE','REJETEE','ENVOYEE'].includes(d.statut)
        );
        eligible.forEach(d => {
          if (d.id && !this.jiraTicketMap.has(d.id)) {
            this.jiraLoading[d.id] = true;
            this.jiraService.getTicketForDeclaration(d.id).subscribe({
              next: () => { this.jiraLoading[d.id!] = false; },
              error: () => { this.jiraLoading[d.id!] = false; }
            });
          }
        });
      },
      error: (err) => {
        this.toast.error('Erreur chargement : ' + err.message);
        this.loading = false;
      }
    });
  }

  // ─── Période ──────────────────────────────────────────────────

  setPeriod(p: 7 | 30 | 90): void {
    this.periodWindow = p;
  }

  // ─── Computed ─────────────────────────────────────────────────

  get tauxValidationGlobal(): number {
    if (!this.toutesDeclarations.length) return 0;
    const ok = this.toutesDeclarations.filter(d => ['VALIDEE','ENVOYEE'].includes(d.statut)).length;
    return Math.round((ok / this.toutesDeclarations.length) * 100);
  }

  get rejetsAvecResoumission(): number {
    return Math.min(this.stats?.rejetees ?? 0, Math.floor((this.stats?.rejetees ?? 0) * 0.6));
  }

  get weeklyData(): WeekData[] {
    const decls = this.toutesDeclarations;
    const weeks = this.periodWindow === 7 ? 1 : this.periodWindow === 30 ? 4 : 12;
    const result: WeekData[] = [];
    const now = new Date();
    const perWeek = Math.ceil(decls.length / weeks);
    for (let i = 0; i < Math.min(weeks, 6); i++) {
      const slice = decls.slice(i * perWeek, (i + 1) * perWeek);
      result.push({
        label: `S${i + 1}`,
        ok:  slice.filter(d => ['VALIDEE','ENVOYEE'].includes(d.statut)).length,
        rej: slice.filter(d => d.statut === 'REJETEE').length,
        enc: slice.filter(d => d.statut === 'EN_VALIDATION').length,
      });
    }
    return result;
  }

  get maxWeekTotal(): number {
    const maxW = Math.max(...this.weeklyData.map(w => w.ok + w.rej + w.enc), 1);
    return maxW;
  }

  barHeight(val: number): number {
    return Math.round((val / this.maxWeekTotal) * 130);
  }

  get agentStats(): AgentStat[] {
    const map = new Map<string, { total: number; ok: number }>();
    this.toutesDeclarations.forEach(d => {
      const nom = d.generePar || 'Inconnu';
      if (!map.has(nom)) map.set(nom, { total: 0, ok: 0 });
      const e = map.get(nom)!;
      e.total++;
      if (['VALIDEE','ENVOYEE'].includes(d.statut)) e.ok++;
    });
    return Array.from(map.entries())
      .map(([nom, v]) => ({
        nom,
        initiales: nom.split(' ').map(p => p[0]?.toUpperCase() || '').join('').slice(0, 2),
        total: v.total,
        tauxValidation: v.total ? Math.round((v.ok / v.total) * 100) : 0
      }))
      .sort((a, b) => b.tauxValidation - a.tauxValidation)
      .slice(0, 5);
  }

  // ─── Filtre ───────────────────────────────────────────────────

  filtrerDeclarations(): void {
    if (!this.filtreStatut) {
      this.declarationsFiltrees = [...this.toutesDeclarations];
    } else {
      this.declarationsFiltrees = this.toutesDeclarations.filter(d => d.statut === this.filtreStatut);
    }
  }

  // ─── Jira ──────────────────────────────────────────────────────

  getJiraTicket(id: number): JiraTicketResponse | null {
    return this.jiraTicketMap.get(id) ?? null;
  }

  ouvrirJira(d: Declaration): void {
    const ticket = this.getJiraTicket(d.id!);
    if (ticket) this.jiraService.openJiraTicket(ticket);
  }

  // ─── Consultation ─────────────────────────────────────────────

  ouvrirConsultation(d: Declaration): void {
    this.declarationSelectionnee = d;
    this.consultHistorique = [];
    this.consultHistoriqueLoading = true;
    this.showConsultModal = true;
    if (d.id) {
      this.validationService.getHistory(d.id).subscribe({
        next: (logs) => { this.consultHistorique = logs; this.consultHistoriqueLoading = false; },
        error: () => { this.consultHistoriqueLoading = false; }
      });
    }
  }

  fermerConsultation(): void {
    this.showConsultModal = false;
    this.consultHistorique = [];
  }

  fermerEtValider(): void {
    const d = this.declarationSelectionnee;
    this.showConsultModal = false;
    if (d) setTimeout(() => this.valider(d), 100);
  }

  fermerEtRejeter(): void {
    const d = this.declarationSelectionnee;
    this.showConsultModal = false;
    if (d) setTimeout(() => this.ouvrirRejet(d), 100);
  }

  // ─── Valider ──────────────────────────────────────────────────

  valider(d: Declaration): void {
    if (!d.id) return;
    this.confirmDialog.confirm(
      'Valider la déclaration',
      `Valider la déclaration #${d.id} — ${d.declarationType?.nom} ?`,
      {
        detail: `Période : ${d.periode}\nAgent : ${d.generePar ?? '-'}`,
        confirmLabel: 'Valider',
        type: 'info'
      }
    ).then(confirmed => {
      if (!confirmed) return;
      this.actionEnCours[d.id!] = true;
      this.validationService.validateDeclaration(d.id!).subscribe({
        next: (updated) => {
          this.mettreAJourListe(updated);
          this.actionEnCours[d.id!] = false;
          this.rafraichirStats();
          this.jiraService.invalidateCache(d.id!);
          this.toast.success(`Déclaration #${d.id} validée avec succès.`);
        },
        error: (err) => {
          this.actionEnCours[d.id!] = false;
          this.toast.error(err.error?.error || err.message || 'Erreur lors de la validation');
        }
      });
    });
  }

  // ─── Rejet ────────────────────────────────────────────────────

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
      next: (updated) => {
        this.mettreAJourListe(updated);
        this.fermerRejet();
        this.rafraichirStats();
        this.jiraService.invalidateCache(id);
        this.toast.error(`Déclaration #${id} rejetée.`);
      },
      error: (err) => {
        this.rejetEnCours = false;
        this.toast.error('Erreur : ' + (err.error?.error || err.message));
      }
    });
  }

  // ─── Historique ───────────────────────────────────────────────

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

  // ─── Download ─────────────────────────────────────────────────

  download(d: Declaration): void {
    if (!d.id) return;
    this.declarationService.downloadDeclaration(d.id).subscribe({
      next: (blob) => {
        const mime = this.declarationService.resolveMimeType(d.nomFichier || '');
        const url = window.URL.createObjectURL(new Blob([blob], { type: mime }));
        const a = document.createElement('a'); a.href = url;
        a.download = d.nomFichier || `declaration_${d.id}`; a.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => this.toast.error('Erreur téléchargement')
    });
  }

  // ─── Helpers ──────────────────────────────────────────────────

  getDonut(val: number, total: number): string {
    if (!total) return '0 88';
    const pct = Math.round((val / total) * 88);
    return `${pct} ${88 - pct}`;
  }

  getPct(val: number, total: number): number {
    if (!total) return 0;
    return Math.round((val / total) * 100);
  }

  getRankClass(i: number): string {
    return ['rank-1','rank-2','rank-3'][i] || 'rank-n';
  }

  getAvColor(i: number): string {
    const colors = ['#2563eb','#16a34a','#d97706','#6d28d9','#9f1239'];
    return colors[i % colors.length];
  }

  getAgentBarClass(taux: number): string {
    if (taux >= 80) return 'agent-bar-ok';
    if (taux >= 60) return 'agent-bar-warn';
    return 'agent-bar-bad';
  }

  getAgentPctClass(taux: number): string {
    if (taux >= 80) return 'pct-ok';
    if (taux >= 60) return 'pct-warn';
    return 'pct-bad';
  }

  private mettreAJourListe(updated: Declaration): void {
    const idx = this.toutesDeclarations.findIndex(x => x.id === updated.id);
    if (idx !== -1) this.toutesDeclarations[idx] = updated;
    this.filtrerDeclarations();
  }

  private rafraichirStats(): void {
    this.validationService.getStats().subscribe({ next: (s) => this.stats = s, error: () => {} });
  }

  getStatutClass(statut: string): string {
    const map: Record<string,string> = {
      'GENEREE':'statut-generee', 'EN_VALIDATION':'statut-validation',
      'VALIDEE':'statut-validee', 'REJETEE':'statut-rejetee', 'ENVOYEE':'statut-envoyee',
    };
    return map[statut] || '';
  }

  getStatutLabel(statut: string): string {
    const map: Record<string,string> = {
      'GENEREE':'Générée', 'EN_VALIDATION':'En validation',
      'VALIDEE':'Validée ✓', 'REJETEE':'Rejetée ✗', 'ENVOYEE': 'Traitée',
    };
    return map[statut] || statut;
  }

  getActionClass(action: string): string {
    const map: Record<string,string> = {
      'SUBMIT':'act-submit', 'VALIDATE':'act-validate',
      'REJECT':'act-reject', 'SEND':'act-send',
    };
    return map[action] || '';
  }

  getActionLabel(action: string): string {
    const map: Record<string,string> = {
      'SUBMIT':'📤 Soumission', 'VALIDATE':'✅ Validation',
      'REJECT':'❌ Rejet',      'SEND': '📨 Traitement',
    };
    return map[action] || action;
  }

  private showMessage(msg: string, type: 'success' | 'error'): void {
    if (type === 'success') this.toast.success(msg);
    else this.toast.error(msg);
    this.message = msg;
    this.messageType = type;
    setTimeout(() => this.message = '', 5000);
  }
}