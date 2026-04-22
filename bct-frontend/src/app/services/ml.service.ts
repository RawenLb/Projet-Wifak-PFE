// ════════════════════════════════════════════════════════════════════
// ml.service.ts — Angular 17+
// Service HTTP BF17 — Clustering erreurs + suggestions de correction
// Passe par validation-service /api/ml/** (port 8084)
// ════════════════════════════════════════════════════════════════════

import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';


// ════════════════════════════════════════════════════════════════════
// INTERFACES — BF17
// ════════════════════════════════════════════════════════════════════

/** Requête d'analyse d'un commentaire de rejet. */
export interface AnalyzeCommentRequest {
  reject_comment:         string;
  declaration_type_code?: string;
  top_k?:                 number;  // 1–10, défaut 5
}

/** Une suggestion de correction issue de l'historique réel. */
export interface CorrectionSuggestion {
  rank:               number;
  similarity_score:   number;   // 0–1
  reject_comment:     string;   // commentaire similaire original
  correction_applied: string;   // correction qui a fonctionné
  corrected_by:       string;
  delay_hours:        number | null;
  was_validated:      boolean;  // validée par le manager
  cluster_label:      string;
}

/** Réponse complète de l'analyse BF17. */
export interface ErrorAnalysisResponse {
  reject_comment:      string;
  cluster_id:          number;
  cluster_label:       string;
  cluster_keywords:    string[];
  similar_cases_count: number;
  suggestions:         CorrectionSuggestion[];
  success_rate:        number;   // 0–1
  avg_delay_hours:     number | null;
  message:             string;
  // Enrichissements ajoutés par Spring Boot
  declaration_id?:     number;
  declaration_type?:   string;
  periode?:            string;
  statut?:             string;
}

/** Résumé d'un cluster pour le tableau de bord. */
export interface ClusterSummary {
  cluster_id:      number;
  label:           string;
  keywords:        string[];
  count:           number;
  success_rate:    number;
  avg_delay_hours: number | null;
  example:         string;
}

/** Statistiques globales BF17. */
export interface Bf17Stats {
  total_reject_comments:   number;
  n_clusters:              number;
  correction_history_size: number;
  validated_corrections:   number;
  global_resolution_rate:  number;
  cluster_labels:          Record<string, string>;
  trained_at:              string;
  silhouette_score:        number;
}

/** État du ML Service. */
export interface MlHealth {
  status:  string;
  bf17:    string;
  version: string;
}

/** Réponse générique d'entraînement. */
export interface TrainResponse {
  status:  string;
  details: Record<string, unknown>;
}


// ════════════════════════════════════════════════════════════════════
// SERVICE
// ════════════════════════════════════════════════════════════════════

@Injectable({ providedIn: 'root' })
export class MlService {

  /** URL de base — pointe vers validation-service qui proxifie vers ML Service */
private readonly base = `http://localhost:8084/api/ml`;
  constructor(private http: HttpClient) {}

  // ── Headers ────────────────────────────────────────────────────────

  private headers(): HttpHeaders {
    return new HttpHeaders({ 'Content-Type': 'application/json' });
  }

  // ── Gestion d'erreurs centralisée ──────────────────────────────────

  private handleError(err: HttpErrorResponse): Observable<never> {
    const message =
      err.error?.detail ||
      err.error?.error  ||
      err.error?.message ||
      err.message ||
      'Erreur inconnue';
    console.error('[MlService]', err.status, message);
    return throwError(() => new Error(message));
  }

  // ════════════════════════════════════════════════════════════════════
  // HEALTH & DIAGNOSTIC
  // ════════════════════════════════════════════════════════════════════

  /** Vérifie l'état du ML Service. */
  health(): Observable<MlHealth> {
    return this.http
      .get<MlHealth>(`${this.base}/health`, { headers: this.headers() })
      .pipe(catchError(this.handleError));
  }

  /** Diagnostic complet (ADMIN). */
  diagnostics(): Observable<Record<string, unknown>> {
    return this.http
      .get<Record<string, unknown>>(`${this.base}/diagnostics`, { headers: this.headers() })
      .pipe(catchError(this.handleError));
  }

  // ════════════════════════════════════════════════════════════════════
  // BF17 — ANALYSE
  // ════════════════════════════════════════════════════════════════════

  /**
   * Analyse un commentaire de rejet saisi manuellement.
   *
   * Retourne le cluster + suggestions de corrections historiques.
   * Message type : "Dans 80% des cas similaires, la correction appliquée a été : ..."
   */
  analyzeRejectComment(req: AnalyzeCommentRequest): Observable<ErrorAnalysisResponse> {
    const body = {
      reject_comment:        req.reject_comment,
      declaration_type_code: req.declaration_type_code ?? null,
      top_k:                 req.top_k ?? 5,
    };
    return this.http
      .post<ErrorAnalysisResponse>(
        `${this.base}/bf17/analyze-comment`,
        body,
        { headers: this.headers() }
      )
      .pipe(catchError(this.handleError));
  }

  /**
   * Récupère automatiquement le commentaire de rejet d'une déclaration
   * depuis la BD, puis l'analyse via BF17.
   *
   * À appeler quand l'agent ouvre une déclaration REJETÉE.
   */
  getSuggestionsForDeclaration(declarationId: number): Observable<ErrorAnalysisResponse> {
    return this.http
      .get<ErrorAnalysisResponse>(
        `${this.base}/bf17/declaration/${declarationId}/suggestions`,
        { headers: this.headers() }
      )
      .pipe(catchError(this.handleError));
  }

  // ════════════════════════════════════════════════════════════════════
  // BF17 — CLUSTERS & STATS
  // ════════════════════════════════════════════════════════════════════

  /** Tous les clusters d'erreurs identifiés (tableau de bord manager). */
  getClusters(): Observable<ClusterSummary[]> {
    return this.http
      .get<ClusterSummary[]>(`${this.base}/bf17/clusters`, { headers: this.headers() })
      .pipe(catchError(this.handleError));
  }

  /** Statistiques globales BF17. */
  getStats(): Observable<Bf17Stats> {
    return this.http
      .get<Bf17Stats>(`${this.base}/bf17/stats`, { headers: this.headers() })
      .pipe(catchError(this.handleError));
  }

  // ════════════════════════════════════════════════════════════════════
  // BF17 — ENTRAÎNEMENT (ADMIN)
  // ════════════════════════════════════════════════════════════════════

  /** Lance le ré-entraînement BF17 en arrière-plan. */
  trainModel(): Observable<TrainResponse> {
    return this.http
      .post<TrainResponse>(`${this.base}/bf17/train`, {}, { headers: this.headers() })
      .pipe(catchError(this.handleError));
  }

  /** Alias train-all (bouton global dashboard). */
  trainAll(): Observable<TrainResponse> {
    return this.http
      .post<TrainResponse>(`${this.base}/train-all`, {}, { headers: this.headers() })
      .pipe(catchError(this.handleError));
  }

  // ════════════════════════════════════════════════════════════════════
  // HELPERS — Formatage pour l'affichage
  // ════════════════════════════════════════════════════════════════════

  /** Formate un taux en pourcentage lisible : 0.825 → "82.5%" */
  formatRate(rate: number): string {
    return `${(Math.min(rate, 1) * 100).toFixed(1)}%`;
  }

  /** Formate un délai en heures en texte lisible. */
  formatDelay(hours: number | null): string {
    if (hours === null || hours === undefined) return '—';
    if (hours < 1)  return 'moins d\'une heure';
    if (hours < 24) return `${Math.round(hours)}h`;
    return `${(hours / 24).toFixed(1)} jour(s)`;
  }

  /** Couleur par cluster (cycle de 8 couleurs). */
  clusterColor(index: number): string {
    const COLORS = [
      '#388bfd', '#34d399', '#fbbf24', '#f87171',
      '#a78bfa', '#fb923c', '#60a5fa', '#4ade80',
    ];
    return COLORS[index % COLORS.length];
  }
}