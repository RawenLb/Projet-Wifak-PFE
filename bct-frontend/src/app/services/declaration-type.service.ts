import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

// ✅ Interface DeclarationType - MISE À JOUR
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
  template?: DeclarationTemplate;          // ✅ CHANGÉ: plus templatePath, maintenant objet template
  dateCreation?: string;
  derniereModification?: string;
  creePar?: string;
  modifiePar?: string;
  validationRules?: ValidationRule[];
}

// ✅ NOUVELLE Interface DeclarationTemplate
export interface DeclarationTemplate {
  id?: number;
  templateContent?: string;               // ✅ Contenu du template (XML/TXT/CSV)
  variablesDisponibles?: string;          // ✅ Variables au format JSON
}

// ✅ Interface ValidationRule
export interface ValidationRule {
  id?: number;
  champConcerne: string;
  typeValidation: string;
  messageErreur: string;
  obligatoire: boolean;
}

// ✅ Interface CreateDeclarationTypeRequest - MISE À JOUR
export interface CreateDeclarationTypeRequest {
  code: string;
  nom: string;
  description?: string;
  format: string;
  frequence: string;
  dateLimite: string;
  actif: boolean;
  champsObligatoires?: string;
  template?: DeclarationTemplate;         // ✅ CHANGÉ: objet template au lieu de templatePath
  validationRules?: ValidationRule[];
}

@Injectable({
  providedIn: 'root'
})
export class DeclarationTypeService {
  
  private apiUrl = 'http://localhost:8082/api/admin/declaration-types';

  constructor(private http: HttpClient) {}

  private getHeaders(): HttpHeaders {
    return new HttpHeaders({
      'Content-Type': 'application/json'
    });
  }

  // ✅ Récupérer tous les types
  getAll(): Observable<DeclarationType[]> {
    return this.http.get<DeclarationType[]>(this.apiUrl, {
      headers: this.getHeaders()
    });
  }

  // ✅ Créer un nouveau type
  create(declarationType: CreateDeclarationTypeRequest): Observable<DeclarationType> {
    return this.http.post<DeclarationType>(this.apiUrl, declarationType, {
      headers: this.getHeaders()
    });
  }

  // ✅ Modifier un type existant
  update(id: number, declarationType: DeclarationType): Observable<DeclarationType> {
    return this.http.put<DeclarationType>(`${this.apiUrl}/${id}`, declarationType, {
      headers: this.getHeaders()
    });
  }

  // ✅ Supprimer un type
  delete(id: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/${id}`, {
      headers: this.getHeaders()
    });
  }

  // ✅ Toggle statut actif/inactif
  toggleStatus(id: number): Observable<DeclarationType> {
    return this.http.patch<DeclarationType>(
      `${this.apiUrl}/${id}/toggle`,
      {},
      { headers: this.getHeaders() }
    );
  }

  // ✅ Récupérer les règles de validation d'un type
  getValidationRules(id: number): Observable<ValidationRule[]> {
    return this.http.get<ValidationRule[]>(`${this.apiUrl}/${id}/validation-rules`, {
      headers: this.getHeaders()
    });
  }

  // ✅ Télécharger le template d'un type
  downloadTemplate(id: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${id}/template`, {
      responseType: 'blob'
    });
  }
}