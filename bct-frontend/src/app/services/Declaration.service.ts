// src/app/services/declaration.service.ts
// ✅ Ne contient PLUS les méthodes workflow (submit/validate/reject/send/pending)
// ✅ Ces méthodes sont maintenant dans ValidationService → /api/validation/**

import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

 
// ── Interfaces à ajouter ──────────────────────────────────────────
 
export interface FieldMapping {
  xsdFieldName:  string;
  xsdFieldPath:  string;
  xsdType:       string;
  required:      boolean;
  source:        'SQL' | 'STATIC' | 'NONE';
  sqlColumn:     string;
  staticValue:   string;
}
 
export interface AnalyzeMappingRequest {
  declarationTypeId: number;
  dateDebut?:        string;
  dateFin?:          string;
}
 
export interface GenerateWithMappingRequest {
  declarationTypeId: number;
  periode:           string;
  dateDebut:         string;
  dateFin:           string;
  mappings:          FieldMapping[];
}
 
export interface MappingAnalysisResponse {
  applicable:         boolean;
  xsdFields:          XsdField[];
  sqlColumns:         string[];
  autoMapped:         Record<string, string>;
  unmappedXsdFields:  string[];
  unmappedSqlColumns: string[];
  compatibilityScore: number;
  summary:            string;
  declarationTypeCode: string;
  declarationTypeNom:  string;
}
 
export interface XsdField {
  name:          string;
  path:          string;
  type:          string;
  required:      boolean;
  defaultValue?: string;
  documentation?: string;
  maxOccurs:     number;
}
 
// ── Méthodes à ajouter dans DeclarationService ───────────────────
 


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
  dateFin?: any;        // may be string "2026-04-30" or array [2026,4,30] from Jackson
  sqlQueryUsed?: string;
  xsdFileNameUsed?: string;
  dateGeneration?: any; // may be string or array [2026,4,5,14,30,0] from Jackson
  dateValidation?: any; // may be string or array from Jackson
  dateEnvoi?: any;      // may be string or array from Jackson
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



    patchXmlContent(id: number, xmlContent: string): Observable<Declaration> {
    return this.http.patch<Declaration>(
      `${this.apiUrl}/${id}/content`,
      { xmlContent },
      { headers: this.headers() }
    );
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
  // ─── ✅ NOUVEAU — Mise à jour d'une déclaration ───────────────
  // Appelle PUT /api/declarations/{id} avec les nouvelles données
  updateDeclaration(id: number, request: GenerateDeclarationRequest): Observable<Declaration> {
    return this.http.put<Declaration>(`${this.apiUrl}/${id}`, request, { headers: this.headers() });
  }
 
  // ─── ✅ NOUVEAU — Suppression d'une déclaration ───────────────
  // Appelle DELETE /api/declarations/{id}
  deleteDeclaration(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`, { headers: this.headers() });
  }

  /**
 * ✅ Analyse la compatibilité XSD ↔ SQL pour un type de déclaration.
 * Retourne les champs XSD, les colonnes SQL et le mapping automatique.
 *
 * POST /api/declarations/analyze-mapping
 */
analyzeMappingHttp(req: AnalyzeMappingRequest): Observable<MappingAnalysisResponse> {
  return this.http.post<MappingAnalysisResponse>(
    `${this.apiUrl}/analyze-mapping`,
    req,
    { headers: this.headers() }
  );
}
 
/**
 * ✅ Génère une déclaration XML en utilisant le mapping XSD ↔ SQL validé.
 *
 * POST /api/declarations/generate-with-mapping
 */
generateDeclarationWithMapping(req: GenerateWithMappingRequest): Observable<Declaration> {
  return this.http.post<Declaration>(
    `${this.apiUrl}/generate-with-mapping`,
    req,
    { headers: this.headers() }
  );
}
}