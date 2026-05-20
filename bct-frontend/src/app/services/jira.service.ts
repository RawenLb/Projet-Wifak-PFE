// src/app/services/jira.service.ts
// ✅ Service complet — gestion Jira avec cache et graceful degradation

import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, of, BehaviorSubject, from } from 'rxjs';
import { catchError, tap, mergeMap } from 'rxjs/operators';
export interface JiraTicketResponse {
  declarationId: number;
  jiraTicketKey: string;   // ex: BCT-12
  jiraTicketUrl: string;   // ex: https://DOMAINE.atlassian.net/browse/BCT-12
  jiraStatus: string;      // IN_PROGRESS | VALIDÉE | REJETÉE | ENVOYÉE
  message: string;
}

@Injectable({ providedIn: 'root' })
export class JiraService {

  private readonly baseUrl = 'http://localhost:8088/api/jira';

  // ✅ Cache local pour éviter les requêtes répétées (déclaration → ticket)
  private ticketCache = new Map<number, JiraTicketResponse | null>();
  private _ticketMap = new BehaviorSubject<Map<number, JiraTicketResponse | null>>(new Map());
  ticketMap$ = this._ticketMap.asObservable();

  constructor(private http: HttpClient) {}

  private headers(): HttpHeaders {
    return new HttpHeaders({ 'Content-Type': 'application/json' });
  }

  /**
   * ✅ Récupère le ticket Jira lié à une déclaration.
   * Retourne null si aucun ticket n'existe (404 → null, pas d'erreur).
   */
  getTicketForDeclaration(declarationId: number): Observable<JiraTicketResponse | null> {
    // Servir depuis le cache si disponible
    if (this.ticketCache.has(declarationId)) {
      return of(this.ticketCache.get(declarationId) ?? null);
    }

    return this.http.get<JiraTicketResponse>(
      `${this.baseUrl}/tickets/${declarationId}`,
      { headers: this.headers() }
    ).pipe(
      tap(ticket => this.updateCache(declarationId, ticket)),
      catchError(() => {
        this.updateCache(declarationId, null);
        return of(null);
      })
    );
  }

  /**
   * ✅ Vérifie si un ticket existe pour une déclaration (endpoint léger).
   */
  ticketExists(declarationId: number): Observable<boolean> {
    return this.http.get<boolean>(
      `${this.baseUrl}/tickets/${declarationId}/exists`,
      { headers: this.headers() }
    ).pipe(
      catchError(() => of(false))
    );
  }

  /**
   * ✅ Charge les tickets pour une liste de déclarations (batch).
   * Met à jour le cache et le BehaviorSubject.
   */
// In jira.service.ts — add a real batch endpoint call
loadTicketsForDeclarations(declarationIds: number[]): void {
  const toLoad = declarationIds.filter(id => !this.ticketCache.has(id));
  if (toLoad.length === 0) return;

  // Fire requests with a small concurrency limit using mergeMap
  from(toLoad).pipe(
    mergeMap(id =>
      this.http.get<JiraTicketResponse>(`${this.baseUrl}/tickets/${id}`, 
        { headers: this.headers() }
      ).pipe(
        catchError(() => of(null)),
        tap(ticket => this.updateCache(id, ticket))
      ),
      3  // max 3 concurrent requests
    )
  ).subscribe();
}
  /**
   * ✅ Vide le cache pour une déclaration (après soumission, par exemple).
   */
  invalidateCache(declarationId: number): void {
    this.ticketCache.delete(declarationId);
    const map = new Map(this._ticketMap.value);
    map.delete(declarationId);
    this._ticketMap.next(map);
  }

  /**
   * ✅ Retourne le ticket depuis le cache de manière synchrone.
   */
  getCachedTicket(declarationId: number): JiraTicketResponse | null | undefined {
    return this.ticketCache.get(declarationId);
  }

  /**
   * ✅ Ouvre le ticket dans Jira (nouvelle fenêtre).
   */
  openJiraTicket(ticket: JiraTicketResponse): void {
    if (ticket?.jiraTicketUrl) {
      window.open(ticket.jiraTicketUrl, '_blank', 'noopener,noreferrer');
    }
  }

  /**
   * ✅ Retourne le label UI du statut Jira.
   */
  getJiraStatusLabel(status: string): string {
    const map: Record<string, string> = {
      'TO_DO':       'À faire',
      'IN_PROGRESS': 'En cours',
      'VALIDÉE':     'Validé ✓',
      'REJETÉE':     'Rejeté ✗',
      'ENVOYÉE': 'Traitée 📨',
    };
    return map[status] || status;
  }

  /**
   * ✅ Retourne la classe CSS pour le badge de statut Jira.
   */
  getJiraStatusClass(status: string): string {
    const map: Record<string, string> = {
      'TO_DO':       'jira-todo',
      'IN_PROGRESS': 'jira-progress',
      'VALIDÉE':     'jira-validated',
      'REJETÉE':     'jira-rejected',
      'ENVOYÉE':     'jira-sent',
    };
    return map[status] || 'jira-unknown';
  }

  private updateCache(declarationId: number, ticket: JiraTicketResponse | null): void {
    this.ticketCache.set(declarationId, ticket);
    const map = new Map(this._ticketMap.value);
    map.set(declarationId, ticket);
    this._ticketMap.next(map);
  }
}