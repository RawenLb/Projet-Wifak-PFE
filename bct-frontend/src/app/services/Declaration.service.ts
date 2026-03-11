import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

// ✅ Aligné avec GenerateDeclarationRequest.java (LocalDate → string "yyyy-MM-dd")
export interface GenerateDeclarationRequest {
  declarationTypeId: number;
  periode: string;    // format: "2025-01"
  dateDebut: string;  // format: "2025-01-01"
  dateFin: string;    // format: "2025-01-31"
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

  // Dates SQL utilisées à la génération
  dateDebut?: string;
  dateFin?: string;

  // Traçabilité
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

  private apiUrl = 'http://localhost:8082/api/declarations';

  constructor(private http: HttpClient) {}

  private getHeaders(): HttpHeaders {
    return new HttpHeaders({ 'Content-Type': 'application/json' });
  }

  // ══════════════════════════════════════════════════════
  // GENERATE
  // ══════════════════════════════════════════════════════

  generateDeclaration(request: GenerateDeclarationRequest): Observable<Declaration> {
    return this.http.post<Declaration>(`${this.apiUrl}/generate`, request, {
      headers: this.getHeaders()
    });
  }

  // ══════════════════════════════════════════════════════
  // READ
  // ══════════════════════════════════════════════════════

  getMyDeclarations(): Observable<Declaration[]> {
    return this.http.get<Declaration[]>(`${this.apiUrl}/my`, {
      headers: this.getHeaders()
    });
  }

  getAllDeclarations(): Observable<Declaration[]> {
    return this.http.get<Declaration[]>(this.apiUrl, {
      headers: this.getHeaders()
    });
  }

  getDeclarationById(id: number): Observable<Declaration> {
    return this.http.get<Declaration>(`${this.apiUrl}/${id}`, {
      headers: this.getHeaders()
    });
  }

  // ══════════════════════════════════════════════════════
  // DOWNLOAD — ✅ CORRIGÉ : MIME type déduit depuis nomFichier
  // ══════════════════════════════════════════════════════

  downloadDeclaration(id: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${id}/download`, { responseType: 'blob' });
  }

  /**
   * ✅ Résout le MIME type selon l'extension du fichier.
   * Utilisé côté frontend pour créer le Blob avec le bon type.
   */
  resolveMimeType(filename: string): string {
    const lower = (filename || '').toLowerCase();
    if (lower.endsWith('.csv'))  return 'text/csv;charset=UTF-8';
    if (lower.endsWith('.txt'))  return 'text/plain;charset=UTF-8';
    if (lower.endsWith('.json')) return 'application/json';
    if (lower.endsWith('.pdf'))  return 'application/pdf';
    return 'application/xml';   // défaut XML
  }

  // ══════════════════════════════════════════════════════
  // WORKFLOW
  // ══════════════════════════════════════════════════════

  submitForValidation(id: number): Observable<Declaration> {
    return this.http.patch<Declaration>(`${this.apiUrl}/${id}/submit`, {}, {
      headers: this.getHeaders()
    });
  }

  validateDeclaration(id: number): Observable<Declaration> {
    return this.http.patch<Declaration>(`${this.apiUrl}/${id}/validate`, {}, {
      headers: this.getHeaders()
    });
  }

  rejectDeclaration(id: number, commentaire: string): Observable<Declaration> {
    return this.http.patch<Declaration>(`${this.apiUrl}/${id}/reject`, { commentaire }, {
      headers: this.getHeaders()
    });
  }

  getPendingDeclarations(): Observable<Declaration[]> {
    return this.http.get<Declaration[]>(`${this.apiUrl}/pending`, {
      headers: this.getHeaders()
    });
  }

  markAsSent(id: number): Observable<Declaration> {
    return this.http.patch<Declaration>(`${this.apiUrl}/${id}/send`, {}, {
      headers: this.getHeaders()
    });
  }

  getStats(): Observable<DeclarationStats> {
    return this.http.get<DeclarationStats>(`${this.apiUrl}/stats`, {
      headers: this.getHeaders()
    });
  }
}