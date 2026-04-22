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

export interface AiValidationResult {
  valid: boolean;
  score: number;
  anomalies: string[];
  recommendation: 'VALIDATE' | 'REJECT' | 'REVIEW';
}

export interface AnomalieDetail {
  ligne: number;
  client: string;
  type: string;
  severity: 'CRITIQUE' | 'MAJEURE' | 'MINEURE';
  detail: string;
}

export interface AiSummary {
  periode: string;
  dateDebut: string;
  dateFin: string;
  codeDeclaration: string;
  nombreLignes: number;
  totalMontantCredit: number;
  totalMontantImpaye: number;
  totalProvision: number;
  tauxImpayeGlobal: number;
  repartitionClasses: Record<string, number>;
  anomaliesDetaillees: AnomalieDetail[];
  nombreAnomaliesCritiques: number;
  nombreAnomaliesMajeures: number;
  riskScore: number;
  riskLevel: 'FAIBLE' | 'MOYEN' | 'ELEVE';
}

export interface ComparisonResult {
  available: boolean;
  message?: string;
  variationCredit: number;
  variationImpaye: number;
  variationProvision: number;
  variationLignes: number;
  alerteVariation: boolean;
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

  submitForValidation(id: number): Observable<Declaration> {
    return this.http.post<Declaration>(`${this.baseUrl}/${id}/submit`, {}, { headers: this.headers() });
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

  // Feature : Analyse IA complète (score Mistral)
  aiAnalysis(id: number): Observable<AiValidationResult> {
    return this.http.get<AiValidationResult>(`${this.baseUrl}/${id}/ai-analysis`, { headers: this.headers() });
  }

  // Feature 1 : AI Summary structuré
  getAiSummary(id: number): Observable<AiSummary> {
    return this.http.get<AiSummary>(`${this.baseUrl}/${id}/ai-summary`, { headers: this.headers() });
  }

  // Feature 2 & 3 : Comparaison période précédente + variation
  compareWithPrevious(id: number, previousId: number): Observable<ComparisonResult> {
    return this.http.get<ComparisonResult>(`${this.baseUrl}/${id}/compare/${previousId}`, { headers: this.headers() });
  }

  // Feature 5 : Templates de rejet prédéfinis
  getRejectTemplates(): Observable<RejectTemplate[]> {
    return this.http.get<RejectTemplate[]>(`${this.baseUrl}/reject-templates`, { headers: this.headers() });
  }
}