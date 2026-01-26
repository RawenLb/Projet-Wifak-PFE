import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface DeclarationType {
  id?: number;
  code: string;
  nom: string;
  format: string;
  frequence: string;
  dateLimite: string;
  actif: boolean;
}

export interface CreateDeclarationTypeRequest {
  code: string;
  nom: string;
  format: string;
  frequence: string;
  dateLimite: string;
  actif: boolean;
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

  getAll(): Observable<DeclarationType[]> {
    return this.http.get<DeclarationType[]>(this.apiUrl, {
      headers: this.getHeaders()
    });
  }

  create(declarationType: CreateDeclarationTypeRequest): Observable<DeclarationType> {
    return this.http.post<DeclarationType>(this.apiUrl, declarationType, {
      headers: this.getHeaders()
    });
  }

  update(id: number, declarationType: DeclarationType): Observable<DeclarationType> {
    return this.http.put<DeclarationType>(`${this.apiUrl}/${id}`, declarationType, {
      headers: this.getHeaders()
    });
  }

  delete(id: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/${id}`, {
      headers: this.getHeaders()
    });
  }

 toggleStatus(id: number): Observable<DeclarationType> {
  return this.http.patch<DeclarationType>(
    `${this.apiUrl}/${id}/toggle`,
    {},
    { headers: this.getHeaders(), withCredentials: true } // مهم
  );
}

}