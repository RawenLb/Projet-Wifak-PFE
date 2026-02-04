import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

// ✅ Interface Declaration
export interface Declaration {
  id?: number;
  declarationType?: {
    id: number;
    code: string;
    nom: string;
    format: string;
    frequence: string;
  };
  statut: string;
  periode: string;
  nomFichier?: string;
  contenuFichier?: string;
  dateGeneration?: string;
  dateValidation?: string;
  dateEnvoi?: string;
  generePar?: string;
  validePar?: string;
  commentaireRejet?: string;
}

// ✅ Interface pour la génération
export interface GenerateDeclarationRequest {
  declarationTypeId: number;
  periode: string;
  data: { [key: string]: string };
}

// ✅ Interface pour les stats
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
    return new HttpHeaders({
      'Content-Type': 'application/json'
    });
  }

  // ✅ Générer une nouvelle déclaration
  generateDeclaration(request: GenerateDeclarationRequest): Observable<Declaration> {
    return this.http.post<Declaration>(`${this.apiUrl}/generate`, request, {
      headers: this.getHeaders()
    });
  }

  // ✅ Récupérer mes déclarations
  getMyDeclarations(): Observable<Declaration[]> {
    return this.http.get<Declaration[]>(`${this.apiUrl}/my`, {
      headers: this.getHeaders()
    });
  }

  // ✅ Récupérer toutes les déclarations (Manager/Admin)
  getAllDeclarations(): Observable<Declaration[]> {
    return this.http.get<Declaration[]>(this.apiUrl, {
      headers: this.getHeaders()
    });
  }

  // ✅ Récupérer une déclaration par ID
  getDeclarationById(id: number): Observable<Declaration> {
    return this.http.get<Declaration>(`${this.apiUrl}/${id}`, {
      headers: this.getHeaders()
    });
  }

  // ✅ Télécharger le fichier d'une déclaration
  downloadDeclaration(id: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${id}/download`, {
      responseType: 'blob'
    });
  }

  // ✅ Soumettre pour validation
  submitForValidation(id: number): Observable<Declaration> {
    return this.http.patch<Declaration>(
      `${this.apiUrl}/${id}/submit`,
      {},
      { headers: this.getHeaders() }
    );
  }

  // ✅ Valider une déclaration (Manager)
  validateDeclaration(id: number): Observable<Declaration> {
    return this.http.patch<Declaration>(
      `${this.apiUrl}/${id}/validate`,
      {},
      { headers: this.getHeaders() }
    );
  }

  // ✅ Rejeter une déclaration (Manager)
  rejectDeclaration(id: number, commentaire: string): Observable<Declaration> {
    return this.http.patch<Declaration>(
      `${this.apiUrl}/${id}/reject`,
      { commentaire },
      { headers: this.getHeaders() }
    );
  }

  // ✅ Récupérer les déclarations en attente (Manager)
  getPendingDeclarations(): Observable<Declaration[]> {
    return this.http.get<Declaration[]>(`${this.apiUrl}/pending`, {
      headers: this.getHeaders()
    });
  }

  // ✅ Marquer comme envoyée (Manager/Admin)
  markAsSent(id: number): Observable<Declaration> {
    return this.http.patch<Declaration>(
      `${this.apiUrl}/${id}/send`,
      {},
      { headers: this.getHeaders() }
    );
  }
}