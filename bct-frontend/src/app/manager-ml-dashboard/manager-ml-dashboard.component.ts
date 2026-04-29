import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil, finalize } from 'rxjs/operators';
import {
  MlService, MlHealth, Bf17Stats, ClusterSummary,
  ErrorAnalysisResponse, CorrectionSuggestion,
} from '../services/ml.service';

@Component({
  selector:    'app-manager-ml-dashboard',
  templateUrl: './manager-ml-dashboard.component.html',
  styleUrls:   ['./manager-ml-dashboard.component.scss'],
})
export class ManagerMlDashboardComponent implements OnInit, OnDestroy {

  private destroy$ = new Subject<void>();

  health:        MlHealth | null = null;
  healthLoading  = true;
  serviceDown    = false;

  stats:        Bf17Stats | null = null;
  statsLoading  = false;

  clusters:        ClusterSummary[] = [];
  clustersLoading  = false;

  commentInput     = '';
  analysisLoading  = false;
  analysisResult:  ErrorAnalysisResponse | null = null;
  analysisError    = '';

  trainLoading = false;
  trainMessage = '';

  resultTab: 'suggestions' | 'cluster' = 'suggestions';

  /** Index de la suggestion ouverte (-1 = aucune) */
  activeSuggestion = 0;

  readonly quickExamples: string[] = [
    'Le montant brut est négatif pour plusieurs lignes de la classe 3.',
    "Le format de la date d'échéance est incorrect (attendu YYYY-MM-DD).",
    'Le champ RIB est vide pour 12 enregistrements — ce champ est obligatoire.',
    'La structure XML ne correspond pas au schéma XSD attendu pour BCT_01.',
    'La provision est insuffisante pour les crédits de classe D (taux réglementaire : 50%).',
    'Le montant impayé dépasse le montant du crédit accordé pour 3 lignes.',
    'Doublons détectés sur IdClient pour les lignes 7 et 14.',
    'La période déclarée ne correspond pas aux dates de début et de fin.',
  ];

  constructor(public ml: MlService) {}

  ngOnInit(): void {
    this.loadHealth();
    this.loadStats();
    this.loadClusters();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadHealth(): void {
    this.healthLoading = true;
    this.serviceDown   = false;
    this.ml.health()
      .pipe(takeUntil(this.destroy$), finalize(() => this.healthLoading = false))
      .subscribe({
        next:  h => { this.health = h; this.serviceDown = h.status !== 'UP'; },
        error: () => { this.serviceDown = true; },
      });
  }

  loadStats(): void {
    this.statsLoading = true;
    this.ml.getStats()
      .pipe(takeUntil(this.destroy$), finalize(() => this.statsLoading = false))
      .subscribe({ next: s => this.stats = s, error: () => {} });
  }

  loadClusters(): void {
    this.clustersLoading = true;
    this.ml.getClusters()
      .pipe(takeUntil(this.destroy$), finalize(() => this.clustersLoading = false))
      .subscribe({ next: c => this.clusters = c, error: () => {} });
  }

  analyze(): void {
    const comment = this.commentInput.trim();
    if (!comment || this.analysisLoading) return;

    this.analysisLoading  = true;
    this.analysisResult   = null;
    this.analysisError    = '';
    this.resultTab        = 'suggestions';
    this.activeSuggestion = 0;

    this.ml.analyzeRejectComment({ reject_comment: comment, top_k: 5 })
      .pipe(takeUntil(this.destroy$), finalize(() => this.analysisLoading = false))
      .subscribe({
        next:  result => this.analysisResult = result,
        error: err    => this.analysisError  = err.message || 'Erreur lors de l\'analyse',
      });
  }

  loadExample(example: string): void {
    this.commentInput   = example;
    this.analysisResult = null;
    this.analysisError  = '';
  }

  resetAnalysis(): void {
    this.commentInput     = '';
    this.analysisResult   = null;
    this.analysisError    = '';
    this.activeSuggestion = 0;
  }

  /** Ouvre / ferme une suggestion au clic. */
  toggleSuggestion(index: number): void {
    this.activeSuggestion = this.activeSuggestion === index ? -1 : index;
  }

  trainModel(): void {
    if (this.trainLoading) return;
    this.trainLoading = true;
    this.trainMessage = '';
    this.ml.trainAll()
      .pipe(takeUntil(this.destroy$), finalize(() => this.trainLoading = false))
      .subscribe({
        next: () => {
          this.trainMessage = '✅ Ré-entraînement lancé en arrière-plan. Actualisation dans 5s...';
          setTimeout(() => {
            this.loadHealth();
            this.loadStats();
            this.loadClusters();
            this.trainMessage = '';
          }, 5000);
        },
        error: err => { this.trainMessage = `❌ Erreur : ${err.message}`; },
      });
  }

  formatRate(rate: number): string  { return this.ml.formatRate(rate); }
  formatDelay(hours: number | null): string { return this.ml.formatDelay(hours); }
  clusterColor(index: number): string { return this.ml.clusterColor(index); }

  healthStatusClass(status: string): string {
    if (status === 'UP')   return 'status-up';
    if (status === 'DOWN') return 'status-down';
    return 'status-unknown';
  }

  bf17BadgeClass(bf17: string): string {
    if (bf17?.startsWith('OK')) return 'badge-ok';
    if (bf17 === 'NOT_TRAINED') return 'badge-warn';
    return 'badge-err';
  }

  rateClass(rate: number): string {
    if (rate >= 0.6) return 'rate-high';
    if (rate >= 0.3) return 'rate-medium';
    return 'rate-low';
  }

  trackByCluster(_: number, c: ClusterSummary): number   { return c.cluster_id; }
  trackBySuggestion(_: number, s: CorrectionSuggestion): number { return s.rank; }

  get canAnalyze(): boolean {
    return this.commentInput.trim().length >= 5 && !this.analysisLoading && !this.serviceDown;
  }

  get serviceDownMessage(): string {
    return 'ML Service inaccessible. Lancez : cd ml-service && python run.py';
  }

  get mainSuccessPercent(): number {
    if (!this.analysisResult) return 0;
    const rate = this.analysisResult.success_rate || 0;
    const sim  = this.analysisResult.suggestions?.[0]?.similarity_score || 0;
    return Math.round(Math.max(rate, sim) * 100);
  }

  onEnter(event: Event): void {
    const e = event as KeyboardEvent;
    if (!e.shiftKey) { e.preventDefault(); this.analyze(); }
  }
}