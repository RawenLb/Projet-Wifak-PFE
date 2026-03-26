// src/app/services/validation.service.ts
// ✅ Pointe vers le validation-service (8084) via l'API Gateway (8088)

import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Declaration } from './Declaration.service';

export interface ValidationStats {
  total: number;
  generees: number;
  enValidation: number;
  validees: number;
  rejetees: number;
  envoyees: number;
}

export interface ValidationLog {
  id: number;
  declarationId: number;
  action: 'SUBMIT' | 'VALIDATE' | 'REJECT' | 'SEND';
  statutAvant: string;
  statutApres: string;
  effectuePar: string;
  commentaire?: string;
  dateAction: string;
}

@Injectable({
  providedIn: 'root'
})
export class ValidationService {

  // ✅ /api/validation/** → routé vers validation-service (8084) par l'API Gateway
  private readonly baseUrl = 'http://localhost:8088/api/validation';

  constructor(private http: HttpClient) {}

  private headers(): HttpHeaders {
    return new HttpHeaders({ 'Content-Type': 'application/json' });
  }

  // ─── 1. Soumettre pour validation (AGENT) ─────────────────────
  // GENEREE | REJETEE → EN_VALIDATION
  submitForValidation(id: number): Observable<Declaration> {
    return this.http.post<Declaration>(`${this.baseUrl}/${id}/submit`, {}, { headers: this.headers() });
  }

  // ─── 2. Valider (MANAGER) ─────────────────────────────────────
  // EN_VALIDATION → VALIDEE
  validateDeclaration(id: number): Observable<Declaration> {
    return this.http.post<Declaration>(`${this.baseUrl}/${id}/validate`, {}, { headers: this.headers() });
  }

  // ─── 3. Rejeter avec commentaire (MANAGER) ────────────────────
  // EN_VALIDATION → REJETEE
  rejectDeclaration(id: number, commentaire: string): Observable<Declaration> {
    return this.http.post<Declaration>(`${this.baseUrl}/${id}/reject`, { commentaire }, { headers: this.headers() });
  }

  // ─── 4. Marquer comme envoyée (AGENT | MANAGER | ADMIN) ───────
  // VALIDEE → ENVOYEE
  markAsSent(id: number): Observable<Declaration> {
    return this.http.post<Declaration>(`${this.baseUrl}/${id}/send`, {}, { headers: this.headers() });
  }

  // ─── 5. Déclarations en attente (MANAGER) ─────────────────────
  getPendingDeclarations(): Observable<Declaration[]> {
    return this.http.get<Declaration[]>(`${this.baseUrl}/pending`, { headers: this.headers() });
  }

  // ─── 6. Statistiques workflow (MANAGER | ADMIN) ───────────────
  getStats(): Observable<ValidationStats> {
    return this.http.get<ValidationStats>(`${this.baseUrl}/stats`, { headers: this.headers() });
  }

  // ─── 7. Historique d'une déclaration ──────────────────────────
  getHistory(declarationId: number): Observable<ValidationLog[]> {
    return this.http.get<ValidationLog[]>(`${this.baseUrl}/${declarationId}/history`, { headers: this.headers() });
  }
}