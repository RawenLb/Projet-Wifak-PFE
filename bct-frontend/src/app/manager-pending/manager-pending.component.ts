import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { DeclarationService, Declaration } from '../services/Declaration.service';
import { ValidationService, ValidationStats, ValidationLog, AiValidationResult } from '../services/Validation.service';
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

  // ── AI Analysis ───────────────────────────────────────
  aiResult: AiValidationResult | null = null;
  aiLoading = false;
  showAiPanel = false;
  aiDeclarationId: number | null = null;

  // ✅ Loader animé
  aiStep = 0;
  aiStepMessage = '';
  aiElapsed = 0;
  aiTip = '';
  private aiTimer: any;

  private readonly AI_STEPS = [
    'Chargement du fichier XML...',
    'Envoi à Mistral via Ollama...',
    'Analyse des champs BCT en cours...',
    'Génération du rapport de conformité...'
  ];

  private readonly AI_TIPS = [
    '💡 Ollama analyse localement — aucune donnée n\'est envoyée au cloud.',
    '⏱ Mistral prend ~15-45s pour une analyse complète.',
    '🔍 Vérification des champs obligatoires, montants et cohérence des données.',
    '✅ Le résultat inclura un score 0-100 et une recommandation détaillée.'
  ];

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
    this.autoRefreshInterval = setInterval(() => this.charger(true), 60000);
  }

  ngOnDestroy(): void {
    this.jiraSub?.unsubscribe();
    clearInterval(this.autoRefreshInterval);
    clearInterval(this.aiTimer); // ✅ Nettoyage timer IA
  }

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

  // ── AI Analysis ───────────────────────────────────────

  lancerAiAnalysis(d: Declaration): void {
    if (!d.id) return;

    // Réinitialisation complète
    this.aiResult       = null;
    this.aiLoading      = true;
    this.showAiPanel    = true;
    this.aiDeclarationId = d.id;
    this.aiElapsed      = 0;
    this.aiStep         = 0;
    this.aiStepMessage  = this.AI_STEPS[0];
    this.aiTip          = this.AI_TIPS[0];

    // ✅ Timer de progression animé
    clearInterval(this.aiTimer);
    this.aiTimer = setInterval(() => {
      this.aiElapsed++;
      const nextStep = Math.min(Math.floor(this.aiElapsed / 8), this.AI_STEPS.length - 1);
      if (nextStep !== this.aiStep) {
        this.aiStep        = nextStep;
        this.aiStepMessage = this.AI_STEPS[this.aiStep];
        this.aiTip         = this.AI_TIPS[this.aiStep];
      }
    }, 1000);

    this.validationService.aiAnalysis(d.id).subscribe({
      next: (result) => {
        clearInterval(this.aiTimer);
        this.aiResult  = result;
        this.aiLoading = false;
      },
      error: () => {
        clearInterval(this.aiTimer);
        this.aiLoading = false;
        this.aiResult = {
          valid: false,
          score: 0,
          anomalies: ['Erreur ou timeout — vérifiez qu\'Ollama est démarré sur le port 11434'],
          recommendation: 'REVIEW'
        };
      }
    });
  }

  fermerAiPanel(): void {
    clearInterval(this.aiTimer); // ✅ Stopper le timer si on ferme avant la fin
    this.showAiPanel     = false;
    this.aiResult        = null;
    this.aiDeclarationId = null;
    this.aiLoading       = false;
  }

  getScoreColor(): string {
    if (!this.aiResult) return '';
    if (this.aiResult.score >= 75) return 'score-green';
    if (this.aiResult.score >= 40) return 'score-amber';
    return 'score-red';
  }

  getRecoClass(): string {
    if (!this.aiResult) return '';
    const map: Record<string, string> = {
      'VALIDATE': 'reco-validate',
      'REVIEW':   'reco-review',
      'REJECT':   'reco-reject'
    };
    return map[this.aiResult.recommendation] || '';
  }

  getRecoLabel(): string {
    if (!this.aiResult) return '';
    const map: Record<string, string> = {
      'VALIDATE': '✅ Valider',
      'REVIEW':   '⚠️ Réviser',
      'REJECT':   '❌ Rejeter'
    };
    return map[this.aiResult.recommendation] || this.aiResult.recommendation;
  }

  // ── Filtres & tri ─────────────────────────────────────

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

    list.sort((a, b) => {
      let va: any, vb: any;
      switch (this.sortField) {
        case 'id':      va = a.id ?? 0;       vb = b.id ?? 0; break;
        case 'type':    va = a.declarationType?.code ?? ''; vb = b.declarationType?.code ?? ''; break;
        case 'periode': va = a.periode ?? '';  vb = b.periode ?? ''; break;
        case 'date':    va = a.dateGeneration ? new Date(a.dateGeneration).getTime() : 0;
                        vb = b.dateGeneration ? new Date(b.dateGeneration).getTime() : 0; break;
      }
      const cmp = va < vb ? -1 : va > vb ? 1 : 0;
      return this.sortDir === 'asc' ? cmp : -cmp;
    });

    return list;
  }

  toggleSort(field: SortField): void {
    if (this.sortField === field) this.sortDir = this.sortDir === 'asc' ? 'desc' : 'asc';
    else { this.sortField = field; this.sortDir = 'desc'; }
  }

  toggleSelect(id: number): void {
    if (this.selectedIds.has(id)) this.selectedIds.delete(id);
    else this.selectedIds.add(id);
  }

  async validerSelection(): Promise<void> {
    if (this.selectedIds.size === 0) return;
    const ids = [...this.selectedIds];
    if (!confirm(`Valider les ${ids.length} déclaration(s) sélectionnée(s) ?`)) return;

    this.bulkLoading = true;
    let success = 0, errors = 0;

    for (const id of ids) {
      try {
        await this.validationService.validateDeclaration(id).toPromise();
        success++;
        this.pending = this.pending.filter(x => x.id !== id);
        this.selectedIds.delete(id);
        this.jiraService.invalidateCache(id);
      } catch { errors++; }
    }

    this.bulkLoading = false;
    this.rafraichirStats();
    this.showToast(errors === 0
      ? `✅ ${success} déclaration(s) validée(s).`
      : `✅ ${success} validée(s), ❌ ${errors} erreur(s).`,
      errors === 0 ? 'success' : 'error'
    );
  }

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

  getJiraTicket(id: number): JiraTicketResponse | null {
    return this.jiraTicketMap.get(id) ?? null;
  }

  ouvrirJira(d: Declaration): void {
    const ticket = this.getJiraTicket(d.id!);
    if (ticket) this.jiraService.openJiraTicket(ticket);
  }

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
    const map: Record<string, string> = {
      'SUBMIT': 'act-submit', 'VALIDATE': 'act-validate',
      'REJECT': 'act-reject', 'SEND': 'act-send'
    };
    return map[a] || '';
  }

  getActionLabel(a: string): string {
    const map: Record<string, string> = {
      'SUBMIT': '📤 Soumission', 'VALIDATE': '✅ Validation',
      'REJECT': '❌ Rejet', 'SEND': '📨 Envoi BCT'
    };
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