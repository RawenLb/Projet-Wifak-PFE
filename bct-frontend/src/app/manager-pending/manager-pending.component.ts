import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { DeclarationService, Declaration } from '../services/Declaration.service';
import {
  ValidationService, ValidationStats, ValidationLog,
  AiValidationResult, AiSummary, ComparisonResult, RejectTemplate
} from '../services/Validation.service';
import { JiraService, JiraTicketResponse } from '../services/jira.service';

type SortField  = 'id' | 'type' | 'periode' | 'date' | 'risque';
type SortDir    = 'asc' | 'desc';
type PriorityFilter = 'ALL' | 'URGENT' | 'RISQUE_ELEVE' | 'ANOMALIE';

@Component({
  selector: 'app-manager-pending',
  templateUrl: './manager-pending.component.html',
  styleUrls: ['./manager-pending.component.scss']
})
export class ManagerPendingComponent implements OnInit, OnDestroy {

  // ── Données ────────────────────────────────────────────
  pending: Declaration[] = [];
  stats: ValidationStats | null = null;
  rejectTemplates: RejectTemplate[] = [];

  // ── Cache AI Summary par déclaration ID ───────────────
  summaryCache: Record<number, AiSummary>          = {};
  summaryLoading: Record<number, boolean>          = {};
  comparisonCache: Record<number, ComparisonResult> = {};

  // ── État général ───────────────────────────────────────
  loading = false;
  actionEnCours: Record<number, boolean> = {};
  lastRefreshed: Date | null = null;
  autoRefreshInterval: any;

  // ── Filtres & tri ──────────────────────────────────────
  searchQuery      = '';
  filtreType       = '';
  priorityFilter: PriorityFilter = 'ALL';
  sortField: SortField = 'risque';
  sortDir: SortDir = 'desc';

  // ── Sélection multiple ─────────────────────────────────
  selectedIds = new Set<number>();
  bulkLoading = false;

  // ── Quick Review Panel (Feature 1 & 2) ─────────────────
  showReviewPanel  = false;
  reviewDeclaration: Declaration | null = null;
  reviewSummary: AiSummary | null = null;
  reviewComparison: ComparisonResult | null = null;
  reviewSummaryLoading = false;
  reviewAiResult: AiValidationResult | null = null;
  reviewAiLoading  = false;
  reviewTab: 'summary' | 'anomalies' | 'comparison' | 'history' = 'summary';
  reviewLogs: ValidationLog[] = [];

  // ── Modal Rejet ────────────────────────────────────────
  showRejetModal           = false;
  declarationSelectionnee: Declaration | null = null;
  commentaireRejet         = '';
  commentaireRejetTouched  = false;
  rejetEnCours             = false;
  selectedTemplate: string  = '';

  // ── Modal Historique ───────────────────────────────────
  showHistoriqueModal = false;
  historique: ValidationLog[] = [];
  historiqueLoading   = false;

  // ── Toast ──────────────────────────────────────────────
  message      = '';
  messageType: 'success' | 'error' | 'info' = 'success';

  // ── Jira ───────────────────────────────────────────────
  jiraLoading: Record<number, boolean> = {};
  private jiraTicketMap = new Map<number, JiraTicketResponse | null>();
  private jiraSub!: Subscription;

  // ── AI Analysis (panel Mistral) ───────────────────────
  aiResult: AiValidationResult | null = null;
  aiLoading      = false;
  showAiPanel    = false;
  aiDeclarationId: number | null = null;
  aiStep         = 0;
  aiStepMessage  = '';
  aiElapsed      = 0;
  aiTip          = '';
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
    this.jiraSub = this.jiraService.ticketMap$.subscribe(map => this.jiraTicketMap = map);
    this.charger();
    this.loadRejectTemplates();
    this.autoRefreshInterval = setInterval(() => this.charger(true), 60000);
  }

  ngOnDestroy(): void {
    this.jiraSub?.unsubscribe();
    clearInterval(this.autoRefreshInterval);
    clearInterval(this.aiTimer);
  }

  charger(silent = false): void {
    if (!silent) this.loading = true;
    this.validationService.getStats().subscribe({ next: s => this.stats = s, error: () => {} });
    this.validationService.getPendingDeclarations().subscribe({
      next: data => {
        this.pending = data;
        this.lastRefreshed = new Date();
        this.loading = false;
        // Pré-charger les summaries pour les indicateurs visuels de risque (Feature 3)
        data.forEach(d => {
          if (d.id) {
            this.loadSummaryForCard(d.id);
            if (!this.jiraTicketMap.has(d.id)) {
              this.jiraLoading[d.id] = true;
              this.jiraService.getTicketForDeclaration(d.id).subscribe({
                next: () => { this.jiraLoading[d.id!] = false; },
                error: () => { this.jiraLoading[d.id!] = false; }
              });
            }
          }
        });
      },
      error: () => {
        this.loading = false;
        if (!silent) this.showToast('Erreur chargement des déclarations en attente', 'error');
      }
    });
  }

  // ── Feature 1 & 3 : Pré-chargement AI Summary pour indicateurs visuels ──
  loadSummaryForCard(id: number): void {
    if (this.summaryCache[id] || this.summaryLoading[id]) return;
    this.summaryLoading[id] = true;
    this.validationService.getAiSummary(id).subscribe({
      next: s => { this.summaryCache[id] = s; this.summaryLoading[id] = false; },
      error: () => { this.summaryLoading[id] = false; }
    });
  }

  // Feature 3 : Indicateur de risque pour la carte
  getRiskBadge(id: number): { level: string; label: string; icon: string } | null {
    const s = this.summaryCache[id];
    if (!s) return null;
    if (s.riskLevel === 'ELEVE')  return { level: 'rouge',  label: 'Risque élevé',  icon: '🔴' };
    if (s.riskLevel === 'MOYEN')  return { level: 'amber',  label: 'Risque moyen',  icon: '🟡' };
    return                               { level: 'vert',   label: 'Risque faible', icon: '🟢' };
  }

  getVariationIcon(val: number): string {
    if (val > 5)  return '↑';
    if (val < -5) return '↓';
    return '→';
  }

  getVariationClass(val: number): string {
    if (val > 20)  return 'var-danger';
    if (val > 5)   return 'var-up';
    if (val < -5)  return 'var-down';
    return 'var-stable';
  }

  // ── Feature 4 : Filtrage intelligent par priorité ──────
  get uniqueTypes(): string[] {
    return [...new Set(this.pending.map(d => d.declarationType?.code || '').filter(Boolean))].sort();
  }

  get filteredPending(): Declaration[] {
    let list = [...this.pending];

    // Filtre texte
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

    // Filtre type
    if (this.filtreType) list = list.filter(d => d.declarationType?.code === this.filtreType);

    // Feature 4 : Filtre priorité
    if (this.priorityFilter === 'RISQUE_ELEVE') {
      list = list.filter(d => d.id && this.summaryCache[d.id]?.riskLevel === 'ELEVE');
    } else if (this.priorityFilter === 'ANOMALIE') {
      list = list.filter(d => d.id && (
        (this.summaryCache[d.id]?.nombreAnomaliesCritiques ?? 0) > 0 ||
        (this.summaryCache[d.id]?.nombreAnomaliesMajeures ?? 0) > 0
      ));
    }

    // Tri
    list.sort((a, b) => {
      let va: any, vb: any;
      switch (this.sortField) {
        case 'id':      va = a.id ?? 0;       vb = b.id ?? 0; break;
        case 'type':    va = a.declarationType?.code ?? ''; vb = b.declarationType?.code ?? ''; break;
        case 'periode': va = a.periode ?? '';  vb = b.periode ?? ''; break;
        case 'date':    va = a.dateGeneration ? new Date(a.dateGeneration).getTime() : 0;
                        vb = b.dateGeneration ? new Date(b.dateGeneration).getTime() : 0; break;
        // Feature 4 : Tri par risque (score décroissant)
        case 'risque':
          va = a.id ? (this.summaryCache[a.id]?.riskScore ?? 50) : 50;
          vb = b.id ? (this.summaryCache[b.id]?.riskScore ?? 50) : 50;
          // Risque élevé = score BAS → apparaît en premier (tri asc sur score = tri desc sur risque)
          return this.sortDir === 'desc' ? va - vb : vb - va;
      }
      const cmp = va < vb ? -1 : va > vb ? 1 : 0;
      return this.sortDir === 'asc' ? cmp : -cmp;
    });

    return list;
  }

  setPriorityFilter(f: PriorityFilter): void { this.priorityFilter = f; }

  toggleSort(field: SortField): void {
    if (this.sortField === field) this.sortDir = this.sortDir === 'asc' ? 'desc' : 'asc';
    else { this.sortField = field; this.sortDir = field === 'risque' ? 'asc' : 'desc'; }
  }

  // ── Feature 1 : Quick Review Panel ────────────────────
  ouvrirReviewPanel(d: Declaration): void {
    this.reviewDeclaration = d;
    this.reviewSummary     = null;
    this.reviewComparison  = null;
    this.reviewAiResult    = null;
    this.reviewLogs        = [];
    this.reviewTab         = 'summary';
    this.showReviewPanel   = true;
    this.reviewSummaryLoading = true;

    if (d.id) {
      // Summary
      const cached = this.summaryCache[d.id];
      if (cached) {
        this.reviewSummary = cached;
        this.reviewSummaryLoading = false;
      } else {
        this.validationService.getAiSummary(d.id).subscribe({
          next: s => { this.reviewSummary = s; this.summaryCache[d.id!] = s; this.reviewSummaryLoading = false; },
          error: () => { this.reviewSummaryLoading = false; }
        });
      }
      // Historique
      this.validationService.getHistory(d.id).subscribe({
        next: logs => { this.reviewLogs = logs; }, error: () => {}
      });
    }
  }

  fermerReviewPanel(): void {
    this.showReviewPanel  = false;
    this.reviewDeclaration = null;
    this.reviewSummary    = null;
    this.reviewAiResult   = null;
  }

  lancerAiDepuisReview(): void {
    if (!this.reviewDeclaration?.id) return;
    this.reviewAiLoading = true;
    this.reviewAiResult  = null;
    this.validationService.aiAnalysis(this.reviewDeclaration.id).subscribe({
      next: r => { this.reviewAiResult = r; this.reviewAiLoading = false; },
      error: () => {
        this.reviewAiLoading = false;
        this.reviewAiResult = { valid: false, score: 0, anomalies: ['Erreur — vérifiez qu\'Ollama est démarré'], recommendation: 'REVIEW' };
      }
    });
  }

  validerDepuisReview(): void {
    const d = this.reviewDeclaration;
    this.fermerReviewPanel();
    if (d) setTimeout(() => this.valider(d), 100);
  }

  rejeterDepuisReview(): void {
    const d = this.reviewDeclaration;
    this.fermerReviewPanel();
    if (d) setTimeout(() => this.ouvrirRejet(d), 100);
  }

  // ── Actions CRUD ───────────────────────────────────────
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
      error: err => {
        this.actionEnCours[d.id!] = false;
        this.showToast('❌ ' + (err.error?.error || err.message), 'error');
      }
    });
  }

  ouvrirRejet(d: Declaration): void {
    this.declarationSelectionnee    = d;
    this.commentaireRejet           = '';
    this.commentaireRejetTouched    = false;
    this.selectedTemplate           = '';
    this.showRejetModal             = true;
  }

  fermerRejet(): void {
    this.showRejetModal          = false;
    this.declarationSelectionnee = null;
    this.commentaireRejet        = '';
    this.rejetEnCours            = false;
    this.selectedTemplate        = '';
  }

  // Feature 5 : Appliquer un template de rejet
  appliquerTemplate(template: RejectTemplate): void {
    this.commentaireRejet = template.text;
    this.selectedTemplate = template.id;
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
      error: err => {
        this.rejetEnCours = false;
        this.showToast('Erreur : ' + (err.error?.error || err.message), 'error');
      }
    });
  }

  // Feature 5 : Chargement des templates
  loadRejectTemplates(): void {
    this.validationService.getRejectTemplates().subscribe({
      next: t => this.rejectTemplates = t,
      error: () => {}
    });
  }

  // ── AI Analysis Panel (Mistral) ────────────────────────
  lancerAiAnalysis(d: Declaration): void {
    if (!d.id) return;
    this.aiResult = null; this.aiLoading = true; this.showAiPanel = true;
    this.aiDeclarationId = d.id; this.aiElapsed = 0; this.aiStep = 0;
    this.aiStepMessage = this.AI_STEPS[0]; this.aiTip = this.AI_TIPS[0];
    clearInterval(this.aiTimer);
    this.aiTimer = setInterval(() => {
      this.aiElapsed++;
      const nextStep = Math.min(Math.floor(this.aiElapsed / 8), this.AI_STEPS.length - 1);
      if (nextStep !== this.aiStep) {
        this.aiStep = nextStep;
        this.aiStepMessage = this.AI_STEPS[this.aiStep];
        this.aiTip = this.AI_TIPS[this.aiStep];
      }
    }, 1000);
    this.validationService.aiAnalysis(d.id).subscribe({
      next: result => { clearInterval(this.aiTimer); this.aiResult = result; this.aiLoading = false; },
      error: () => {
        clearInterval(this.aiTimer); this.aiLoading = false;
        this.aiResult = { valid: false, score: 0, anomalies: ['Erreur ou timeout — vérifiez qu\'Ollama est démarré sur le port 11434'], recommendation: 'REVIEW' };
      }
    });
  }

  fermerAiPanel(): void {
    clearInterval(this.aiTimer);
    this.showAiPanel = false; this.aiResult = null; this.aiDeclarationId = null; this.aiLoading = false;
  }

  // ── Bulk ───────────────────────────────────────────────
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
      errors === 0 ? 'success' : 'error');
  }

  // ── Modal Historique ───────────────────────────────────
  voirHistorique(d: Declaration): void {
    if (!d.id) return;
    this.declarationSelectionnee = d;
    this.historique = []; this.historiqueLoading = true; this.showHistoriqueModal = true;
    this.validationService.getHistory(d.id).subscribe({
      next: logs => { this.historique = logs; this.historiqueLoading = false; },
      error: () => { this.historiqueLoading = false; }
    });
  }

  fermerHistorique(): void {
    this.showHistoriqueModal = false;
    this.historique = [];
    this.declarationSelectionnee = null;
  }

  // ── Helpers ────────────────────────────────────────────
  getScoreColor(): string {
    if (!this.aiResult) return '';
    if (this.aiResult.score >= 75) return 'score-green';
    if (this.aiResult.score >= 40) return 'score-amber';
    return 'score-red';
  }

  getRecoClass(): string {
    if (!this.aiResult) return '';
    return { 'VALIDATE': 'reco-validate', 'REVIEW': 'reco-review', 'REJECT': 'reco-reject' }[this.aiResult.recommendation] || '';
  }

  getRecoLabel(): string {
    if (!this.aiResult) return '';
    return { 'VALIDATE': '✅ Valider', 'REVIEW': '⚠️ Réviser', 'REJECT': '❌ Rejeter' }[this.aiResult.recommendation] || this.aiResult.recommendation;
  }

  getSummaryScoreColor(score: number): string {
    if (score >= 70) return 'score-green';
    if (score >= 40) return 'score-amber';
    return 'score-red';
  }

  formatMontant(val: number): string {
    if (val >= 1_000_000) return (val / 1_000_000).toFixed(2) + ' M';
    if (val >= 1_000)     return (val / 1_000).toFixed(1) + ' K';
    return val.toFixed(0);
  }

  formatVariation(val: number): string {
    return (val >= 0 ? '+' : '') + val.toFixed(1) + '%';
  }

  getJiraTicket(id: number): JiraTicketResponse | null { return this.jiraTicketMap.get(id) ?? null; }

  ouvrirJira(d: Declaration): void {
    const ticket = this.getJiraTicket(d.id!);
    if (ticket) this.jiraService.openJiraTicket(ticket);
  }

  download(d: Declaration): void {
    if (!d.id) return;
    this.declarationService.downloadDeclaration(d.id).subscribe({
      next: blob => {
        const mime = this.declarationService.resolveMimeType(d.nomFichier || '');
        const url = window.URL.createObjectURL(new Blob([blob], { type: mime }));
        const a = document.createElement('a'); a.href = url;
        a.download = d.nomFichier || `declaration_${d.id}`; a.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => this.showToast('❌ Erreur téléchargement', 'error')
    });
  }

  private rafraichirStats(): void {
    this.validationService.getStats().subscribe({ next: s => this.stats = s, error: () => {} });
  }

  private showToast(msg: string, type: 'success' | 'error' | 'info'): void {
    this.message = msg; this.messageType = type;
    setTimeout(() => this.message = '', 5000);
  }

  getStatutClass(s: string): string {
    return ({ GENEREE: 'sc-generee', EN_VALIDATION: 'sc-validation', VALIDEE: 'sc-validee', REJETEE: 'sc-rejetee', ENVOYEE: 'sc-envoyee' } as Record<string, string>)[s] || '';
  }

  getStatutLabel(s: string): string {
    return ({ GENEREE: 'Générée', EN_VALIDATION: 'En validation', VALIDEE: 'Validée', REJETEE: 'Rejetée', ENVOYEE: 'Envoyée' } as Record<string, string>)[s] || s;
  }

  getActionClass(a: string): string {
    return ({ SUBMIT: 'act-submit', VALIDATE: 'act-validate', REJECT: 'act-reject', SEND: 'act-send' } as Record<string, string>)[a] || '';
  }

  getActionLabel(a: string): string {
    return ({ SUBMIT: '📤 Soumission', VALIDATE: '✅ Validation', REJECT: '❌ Rejet', SEND: '📨 Envoi BCT' } as Record<string, string>)[a] || a;
  }

  getSortIcon(field: SortField): string {
    if (this.sortField !== field) return '↕';
    return this.sortDir === 'asc' ? '↑' : '↓';
  }

  trackById(_: number, d: Declaration): number { return d.id ?? 0; }

  formatTime(date: Date): string {
    return date.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }

  // Couleur barre classe de risque
  getClasseColor(classe: string): string {
    return ({ A: '#34d399', B: '#fbbf24', C: '#fb923c', D: '#f87171' } as Record<string, string>)[classe.toUpperCase()] || '#7e90b0';
  }

  // Données pour le mini graphe de répartition
  getClasseBarWidth(count: number, total: number): number {
    return total > 0 ? Math.round((count / total) * 100) : 0;
  }
}