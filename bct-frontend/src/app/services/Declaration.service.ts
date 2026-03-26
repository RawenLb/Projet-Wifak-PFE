// src/app/services/declaration.service.ts
// ✅ Ne contient PLUS les méthodes workflow (submit/validate/reject/send/pending)
// ✅ Ces méthodes sont maintenant dans ValidationService → /api/validation/**

import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface GenerateDeclarationRequest {
  declarationTypeId: number;
  periode: string;    // "2025-01"
  dateDebut: string;  // "2025-01-01"
  dateFin: string;    // "2025-01-31"
}

export interface Declaration {
  id?: number;
  declarationType?: {
    id: number;
    code: string;
    nom: string;
    format: string;
    frequence: string;
    dateLimite?: string;
  };
  statut: string;
  periode: string;
  nomFichier?: string;
  contenuFichier?: string;
  dateDebut?: string;
  dateFin?: string;
  sqlQueryUsed?: string;
  xsdFileNameUsed?: string;
  dateGeneration?: string;
  dateValidation?: string;
  dateEnvoi?: string;
  generePar?: string;
  validePar?: string;
  commentaireRejet?: string;
}

export interface DeclarationStats {
  total: number;
  generees: number;
  enValidation: number;
  validees: number;
  rejetees: number;
  envoyees: number;
}

@Injectable({
  providedIn: 'root'
})
export class DeclarationService {

  // /api/declarations/** → routé vers bct-backend (8082) par l'API Gateway
  private readonly apiUrl = 'http://localhost:8088/api/declarations';

  constructor(private http: HttpClient) {}

  private headers(): HttpHeaders {
    return new HttpHeaders({ 'Content-Type': 'application/json' });
  }

  // ─── Génération ───────────────────────────────────────────────
  generateDeclaration(request: GenerateDeclarationRequest): Observable<Declaration> {
    return this.http.post<Declaration>(`${this.apiUrl}/generate`, request, { headers: this.headers() });
  }

  // ─── Lecture ──────────────────────────────────────────────────
  getMyDeclarations(): Observable<Declaration[]> {
    return this.http.get<Declaration[]>(`${this.apiUrl}/my`, { headers: this.headers() });
  }

  getAllDeclarations(): Observable<Declaration[]> {
    return this.http.get<Declaration[]>(this.apiUrl, { headers: this.headers() });
  }

  getDeclarationById(id: number): Observable<Declaration> {
    return this.http.get<Declaration>(`${this.apiUrl}/${id}`, { headers: this.headers() });
  }

  // ─── Téléchargement ───────────────────────────────────────────
  downloadDeclaration(id: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${id}/download`, { responseType: 'blob' });
  }

  resolveMimeType(filename: string): string {
    const lower = (filename || '').toLowerCase();
    if (lower.endsWith('.csv'))  return 'text/csv;charset=UTF-8';
    if (lower.endsWith('.txt'))  return 'text/plain;charset=UTF-8';
    if (lower.endsWith('.json')) return 'application/json';
    if (lower.endsWith('.pdf'))  return 'application/pdf';
    return 'application/xml';
  }

  // ─── Stats (lecture seule depuis declaration-service) ─────────
  getStats(): Observable<DeclarationStats> {
    return this.http.get<DeclarationStats>(`${this.apiUrl}/stats`, { headers: this.headers() });
  }
}