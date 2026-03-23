import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface DeclarationType {
  id?: number;
  code: string;
  nom: string;
  description?: string;
  format: string;
  frequence: string;
  dateLimite: string;
  actif: boolean;
  champsObligatoires?: string;
  template?: DeclarationTemplate;
  dateCreation?: string;
  derniereModification?: string;
  creePar?: string;
  modifiePar?: string;
  validationRules?: ValidationRule[];

  // XSD et SQL
  xsdContent?: string;
  xsdFileName?: string;
  sqlQuery?: string;
}

export interface DeclarationTemplate {
  id?: number;
  templateContent?: string;
  variablesDisponibles?: string;
}

export interface ValidationRule {
  id?: number;
  champConcerne: string;
  typeValidation: string;
  messageErreur: string;
  obligatoire: boolean;
}

export interface CreateDeclarationTypeRequest {
  code: string;
  nom: string;
  description?: string;
  format: string;
  frequence: string;
  dateLimite: string;
  actif: boolean;
  champsObligatoires?: string;
  template?: DeclarationTemplate;
  validationRules?: ValidationRule[];
}

// ✅ Résultat du test SQL renvoyé par le backend
export interface SqlTestResult {
  success: boolean;
  nombreLignes?: number;
  colonnesDisponibles?: string[];
  error?: string;
}

@Injectable({
  providedIn: 'root'
})
export class DeclarationTypeService {

  private apiUrl = 'http://localhost:8088/api/admin/declaration-types';

  constructor(private http: HttpClient) {}

  private getHeaders(): HttpHeaders {
    return new HttpHeaders({ 'Content-Type': 'application/json' });
  }

  // ========== CRUD ==========

  getAll(): Observable<DeclarationType[]> {
    return this.http.get<DeclarationType[]>(this.apiUrl, { headers: this.getHeaders() });
  }

  create(declarationType: CreateDeclarationTypeRequest): Observable<DeclarationType> {
    return this.http.post<DeclarationType>(this.apiUrl, declarationType, { headers: this.getHeaders() });
  }

  update(id: number, declarationType: DeclarationType): Observable<DeclarationType> {
    return this.http.put<DeclarationType>(`${this.apiUrl}/${id}`, declarationType, { headers: this.getHeaders() });
  }

  delete(id: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/${id}`, { headers: this.getHeaders() });
  }

  toggleStatus(id: number): Observable<DeclarationType> {
    return this.http.patch<DeclarationType>(`${this.apiUrl}/${id}/toggle`, {}, { headers: this.getHeaders() });
  }

  getValidationRules(id: number): Observable<ValidationRule[]> {
    return this.http.get<ValidationRule[]>(`${this.apiUrl}/${id}/validation-rules`, { headers: this.getHeaders() });
  }

  downloadTemplate(id: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${id}/template`, { responseType: 'blob' });
  }

  // ========== XSD ==========

  uploadXsd(id: number, file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    // ⚠️ PAS de Content-Type header — le browser set automatiquement le multipart boundary
    return this.http.post(`${this.apiUrl}/${id}/xsd`, formData);
  }

  downloadXsd(id: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${id}/xsd/download`, { responseType: 'blob' });
  }

  // ========== SQL ==========

  // ✅ Body: { sqlQuery: "SELECT ..." } — aligné avec PATCH /sql backend
  saveSqlQuery(id: number, sqlQuery: string): Observable<DeclarationType> {
    return this.http.patch<DeclarationType>(
      `${this.apiUrl}/${id}/sql`,
      { sqlQuery },
      { headers: this.getHeaders() }
    );
  }

  // ✅ Body: { dateDebut: "2025-01-01", dateFin: "2025-01-31" }
  testSqlQuery(id: number, dateDebut: string, dateFin: string): Observable<SqlTestResult> {
    return this.http.post<SqlTestResult>(
      `${this.apiUrl}/${id}/sql/test`,
      { dateDebut, dateFin },
      { headers: this.getHeaders() }
    );
  }
}