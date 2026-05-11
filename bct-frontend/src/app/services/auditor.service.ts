// src/app/services/auditor.service.ts
// Service dédié à l'espace auditeur — lecture seule
// Routes backend : /api/audit/** → workflow-declaration (port 8084)
//                  /api/declarations/** → workflow-declaration (port 8084)
//                  /api/validation/** → workflow-declaration (port 8084)

import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

// ── DTOs ──────────────────────────────────────────────────────────

export interface AuditLogDTO {
  id: number;
  declarationId: number;
  declarationCode?: string;
  declarationNom?: string;
  declarationPeriode?: string;
  declarationStatut?: string;
  action: string;
  statutAvant?: string;
  statutApres?: string;
  effectuePar: string;
  commentaire?: string;
  dateAction: string;
}

export interface AuditStatsDTO {
  // Déclarations
  totalDeclarations: number;
  generees:          number;
  enValidation:      number;
  validees:          number;
  rejetees:          number;
  envoyees:          number;
  // Logs
  totalLogs:         number;
  totalSoumissions:  number;
  totalValidations:  number;
  totalRejets:       number;
  totalEnvois:       number;
  // Taux
  tauxValidation:    number;
  tauxRejet:         number;
  // Top utilisateurs
  topAgents:    { username: string; count: number }[];
  topManagers:  { username: string; count: number }[];
  // Répartition
  actionCounts: Record<string, number>;
}

export interface AuditSearchParams {
  action?:      string;
  effectuePar?: string;
  from?:        string;  // ISO date YYYY-MM-DD
  to?:          string;  // ISO date YYYY-MM-DD
}

@Injectable({ providedIn: 'root' })
export class AuditorService {

  private readonly auditUrl       = 'http://localhost:8088/api/audit';
  private readonly declarationUrl = 'http://localhost:8088/api/declarations';
  private readonly validationUrl  = 'http://localhost:8088/api/validation';

  constructor(private http: HttpClient) {}

  private headers(): HttpHeaders {
    return new HttpHeaders({ 'Content-Type': 'application/json' });
  }

  // ══════════════════════════════════════════════════════════════
  // AUDIT LOGS — /api/audit/**
  // ══════════════════════════════════════════════════════════════

  /**
   * Tous les logs de traçabilité enrichis (horodatage, utilisateur, action, déclaration).
   * GET /api/audit/logs
   */
  getAllLogs(): Observable<AuditLogDTO[]> {
    return this.http.get<AuditLogDTO[]>(`${this.auditUrl}/logs`, { headers: this.headers() });
  }

  /**
   * Recherche filtrée des logs.
   * GET /api/audit/logs/search?action=VALIDATE&effectuePar=john&from=2025-01-01&to=2025-12-31
   */
  searchLogs(params: AuditSearchParams): Observable<AuditLogDTO[]> {
    let httpParams = new HttpParams();
    if (params.action      && params.action.trim())      httpParams = httpParams.set('action',      params.action.trim());
    if (params.effectuePar && params.effectuePar.trim()) httpParams = httpParams.set('effectuePar', params.effectuePar.trim());
    if (params.from)                                     httpParams = httpParams.set('from',        params.from);
    if (params.to)                                       httpParams = httpParams.set('to',          params.to);

    return this.http.get<AuditLogDTO[]>(`${this.auditUrl}/logs/search`, {
      headers: this.headers(),
      params:  httpParams
    });
  }

  /**
   * Logs d'une déclaration spécifique.
   * GET /api/audit/logs/declaration/{id}
   */
  getLogsByDeclaration(declarationId: number): Observable<AuditLogDTO[]> {
    return this.http.get<AuditLogDTO[]>(
      `${this.auditUrl}/logs/declaration/${declarationId}`,
      { headers: this.headers() }
    );
  }

  /**
   * Liste des utilisateurs distincts (pour les filtres).
   * GET /api/audit/users
   */
  getDistinctUsers(): Observable<string[]> {
    return this.http.get<string[]>(`${this.auditUrl}/users`, { headers: this.headers() });
  }

  /**
   * Statistiques complètes pour le tableau de bord auditeur.
   * GET /api/audit/stats
   */
  getAuditStats(): Observable<AuditStatsDTO> {
    return this.http.get<AuditStatsDTO>(`${this.auditUrl}/stats`, { headers: this.headers() });
  }

  // ══════════════════════════════════════════════════════════════
  // DÉCLARATIONS — /api/declarations/**
  // (AUDITOR autorisé via @PreAuthorize mis à jour dans bct-backend)
  // ══════════════════════════════════════════════════════════════

  /**
   * Toutes les déclarations (lecture seule pour l'auditeur).
   * GET /api/declarations
   */
  getAllDeclarations(): Observable<any[]> {
    return this.http.get<any[]>(this.declarationUrl, { headers: this.headers() });
  }

  /**
   * Une déclaration par ID.
   * GET /api/declarations/{id}
   */
  getDeclarationById(id: number): Observable<any> {
    return this.http.get<any>(`${this.declarationUrl}/${id}`, { headers: this.headers() });
  }

  /**
   * Statistiques des déclarations.
   * GET /api/declarations/stats
   */
  getDeclarationStats(): Observable<any> {
    return this.http.get<any>(`${this.declarationUrl}/stats`, { headers: this.headers() });
  }

  // ══════════════════════════════════════════════════════════════
  // VALIDATION HISTORY — /api/validation/{id}/history
  // (AUDITOR autorisé via @PreAuthorize mis à jour dans validation-service)
  // ══════════════════════════════════════════════════════════════

  /**
   * Historique de validation d'une déclaration spécifique.
   * GET /api/validation/{id}/history
   */
  getValidationHistory(declarationId: number): Observable<any[]> {
    return this.http.get<any[]>(
      `${this.validationUrl}/${declarationId}/history`,
      { headers: this.headers() }
    );
  }
}
