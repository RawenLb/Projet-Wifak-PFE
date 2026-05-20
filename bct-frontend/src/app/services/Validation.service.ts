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

export interface RejectTemplate {
  id: string;
  label: string;
  text: string;
}

@Injectable({ providedIn: 'root' })
export class ValidationService {

  private readonly baseUrl = 'http://localhost:8088/api/validation';

  constructor(private http: HttpClient) {}

  private headers(): HttpHeaders {
    return new HttpHeaders({ 'Content-Type': 'application/json' });
  }

  submitForValidation(id: number, correctionComment?: string): Observable<Declaration> {
  const params = correctionComment ? `?correctionComment=${encodeURIComponent(correctionComment)}` : '';
  return this.http.post<Declaration>(`${this.baseUrl}/${id}/submit${params}`, {}, { headers: this.headers() });
}

  validateDeclaration(id: number): Observable<Declaration> {
    return this.http.post<Declaration>(`${this.baseUrl}/${id}/validate`, {}, { headers: this.headers() });
  }

  rejectDeclaration(id: number, commentaire: string): Observable<Declaration> {
    return this.http.post<Declaration>(`${this.baseUrl}/${id}/reject`, { commentaire }, { headers: this.headers() });
  }

  markAsSent(id: number): Observable<Declaration> {
    return this.http.post<Declaration>(`${this.baseUrl}/${id}/send`, {}, { headers: this.headers() });
  }

  getPendingDeclarations(): Observable<Declaration[]> {
    return this.http.get<Declaration[]>(`${this.baseUrl}/pending`, { headers: this.headers() });
  }

  getStats(): Observable<ValidationStats> {
    return this.http.get<ValidationStats>(`${this.baseUrl}/stats`, { headers: this.headers() });
  }

  getHistory(declarationId: number): Observable<ValidationLog[]> {
    return this.http.get<ValidationLog[]>(`${this.baseUrl}/${declarationId}/history`, { headers: this.headers() });
  }

  getRejectTemplates(): Observable<RejectTemplate[]> {
    return this.http.get<RejectTemplate[]>(`${this.baseUrl}/reject-templates`, { headers: this.headers() });
  }
}