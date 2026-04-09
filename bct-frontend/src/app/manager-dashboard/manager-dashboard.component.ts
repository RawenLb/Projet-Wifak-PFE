// src/app/manager-dashboard/manager-dashboard.component.ts
// ✅ MODIFIÉ — intégration JiraService complète

import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { DeclarationService, Declaration } from '../services/Declaration.service';
import { ValidationService, ValidationStats, ValidationLog } from '../services/Validation.service';
import { JiraService, JiraTicketResponse } from '../services/jira.service';

@Component({
  selector: 'app-manager-dashboard',
  templateUrl: './manager-dashboard.component.html',
  styleUrls: ['./manager-dashboard.component.scss']
})
export class ManagerDashboardComponent implements OnInit, OnDestroy {

  // ── Données ────────────────────────────────────────────────────
  pending: Declaration[] = [];
  toutesDeclarations: Declaration[] = [];
  declarationsFiltrees: Declaration[] = [];
  stats: ValidationStats | null = null;

  // ── État UI ────────────────────────────────────────────────────
  loading = false;
  filtreStatut = '';
  actionEnCours: Record<number, boolean> = {};

  // ── Modal Rejet ────────────────────────────────────────────────
  showRejetModal = false;
  declarationSelectionnee: Declaration | null = null;
  commentaireRejet = '';
  commentaireRejetTouched = false;
  rejetEnCours = false;

  // ── Modal Historique ───────────────────────────────────────────
  showHistoriqueModal = false;
  historique: ValidationLog[] = [];
  historiqueLoading = false;

  // ── Toast ──────────────────────────────────────────────────────
  message = '';
  messageType = 'success';

  // ── Jira ───────────────────────────────────────────────────────
  jiraLoading: Record<number, boolean> = {};
  private jiraTicketMap = new Map<number, JiraTicketResponse | null>();
  private jiraSub!: Subscription;

  constructor(
    private declarationService: DeclarationService,
    private validationService: ValidationService,
    public jiraService: JiraService
  ) {}

  ngOnInit(): void {
    // S'abonner au cache Jira
    this.jiraSub = this.jiraService.ticketMap$.subscribe(map => {
      this.jiraTicketMap = map;
    });
    this.chargerDonnees();
  }

  ngOnDestroy(): void {
    this.jiraSub?.unsubscribe();
  }

  // ─── Chargement ───────────────────────────────────────────────

  chargerDonnees(): void {
    this.loading = true;

    this.validationService.getStats().subscribe({
      next: (s) => this.stats = s,
      error: () => console.warn('Stats indisponibles')
    });

    this.validationService.getPendingDeclarations().subscribe({
      next: (data) => {
        this.pending = data.sort((a, b) => (b.id ?? 0) - (a.id ?? 0));
        // ✅ Charger les tickets Jira pour les déclarations en attente
        this.loadJiraTicketsForList(this.pending);
      },
      error: (err) => this.showMessage('Erreur chargement pending : ' + err.message, 'error')
    });

    this.declarationService.getAllDeclarations().subscribe({
      next: (data) => {
        this.toutesDeclarations = data.sort((a, b) => (b.id ?? 0) - (a.id ?? 0));
        this.declarationsFiltrees = [...this.toutesDeclarations];
        this.loading = false;
        // ✅ Charger les tickets Jira pour toutes les déclarations éligibles
        const eligible = data.filter(d =>
          ['EN_VALIDATION', 'VALIDEE', 'REJETEE', 'ENVOYEE'].includes(d.statut)
        );
        this.loadJiraTicketsForList(eligible);
      },
      error: (err) => {
        this.showMessage('Erreur chargement déclarations : ' + err.message, 'error');
        this.loading = false;
      }
    });
  }

  // ─── Jira ──────────────────────────────────────────────────────

  private loadJiraTicketsForList(declarations: Declaration[]): void {
    declarations.forEach(d => {
      if (d.id && !this.jiraTicketMap.has(d.id)) {
        this.jiraLoading[d.id] = true;
        this.jiraService.getTicketForDeclaration(d.id).subscribe({
          next: () => { this.jiraLoading[d.id!] = false; },
          error: () => { this.jiraLoading[d.id!] = false; }
        });
      }
    });
  }

  getJiraTicket(declarationId: number): JiraTicketResponse | null {
    return this.jiraTicketMap.get(declarationId) ?? null;
  }

  ouvrirJira(d: Declaration): void {
    const ticket = this.getJiraTicket(d.id!);
    if (ticket) this.jiraService.openJiraTicket(ticket);
  }

  // ─── Filtre ───────────────────────────────────────────────────

  filtrerDeclarations(): void {
    if (!this.filtreStatut) {
      this.declarationsFiltrees = [...this.toutesDeclarations];
    } else {
      this.declarationsFiltrees = this.toutesDeclarations.filter(d => d.statut === this.filtreStatut);
    }
  }

  // ─── VALIDER ──────────────────────────────────────────────────

  valider(d: Declaration): void {
    if (!d.id) return;
    if (!confirm(`Valider la déclaration #${d.id} — ${d.declarationType?.nom} (${d.periode}) ?`)) return;

    this.actionEnCours[d.id] = true;
    this.validationService.validateDeclaration(d.id).subscribe({
      next: (updated) => {
        this.pending = this.pending.filter(x => x.id !== d.id);
        this.mettreAJourListe(updated);
        this.actionEnCours[d.id!] = false;
        this.rafraichirStats();
        // Invalider et recharger le cache Jira (statut changé → VALIDÉE)
        this.jiraService.invalidateCache(d.id!);
        setTimeout(() => {
          this.jiraService.getTicketForDeclaration(d.id!).subscribe();
        }, 1500);
        this.showMessage(`✅ Déclaration #${d.id} validée avec succès.`, 'success');
      },
      error: (err) => {
        this.actionEnCours[d.id!] = false;
        this.showMessage('❌ ' + (err.error?.error || err.message), 'error');
      }
    });
  }

  // ─── OUVRIR MODAL REJET ───────────────────────────────────────

  ouvrirRejet(d: Declaration): void {
    this.declarationSelectionnee = d;
    this.commentaireRejet = '';
    this.commentaireRejetTouched = false;
    this.showRejetModal = true;
  }

  fermerRejet(): void {
    this.showRejetModal = false;
    this.declarationSelectionnee = null;
    this.commentaireRejet = '';
    this.rejetEnCours = false;
  }

  // ─── CONFIRMER REJET ──────────────────────────────────────────

  confirmerRejet(): void {
    this.commentaireRejetTouched = true;
    if (!this.declarationSelectionnee?.id || !this.commentaireRejet.trim()) return;

    const id = this.declarationSelectionnee.id;
    this.rejetEnCours = true;

    this.validationService.rejectDeclaration(id, this.commentaireRejet.trim()).subscribe({
      next: (updated) => {
        this.pending = this.pending.filter(x => x.id !== id);
        this.mettreAJourListe(updated);
        this.fermerRejet();
        this.rafraichirStats();
        // Invalider et recharger le cache Jira (statut changé → REJETÉE)
        this.jiraService.invalidateCache(id);
        setTimeout(() => {
          this.jiraService.getTicketForDeclaration(id).subscribe();
        }, 1500);
        this.showMessage(`❌ Déclaration #${id} rejetée.`, 'error');
      },
      error: (err) => {
        this.rejetEnCours = false;
        this.showMessage('Erreur : ' + (err.error?.error || err.message), 'error');
      }
    });
  }

  // ─── TÉLÉCHARGER ─────────────────────────────────────────────

  download(d: Declaration): void {
    if (!d.id) return;
    this.declarationService.downloadDeclaration(d.id).subscribe({
      next: (blob) => {
        const mime = this.declarationService.resolveMimeType(d.nomFichier || '');
        const url = window.URL.createObjectURL(new Blob([blob], { type: mime }));
        const a = document.createElement('a');
        a.href = url;
        a.download = d.nomFichier || `declaration_${d.id}`;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => this.showMessage('❌ Erreur téléchargement', 'error')
    });
  }

  // ─── HISTORIQUE ───────────────────────────────────────────────

  voirHistorique(d: Declaration): void {
    if (!d.id) return;
    this.declarationSelectionnee = d;
    this.historique = [];
    this.historiqueLoading = true;
    this.showHistoriqueModal = true;

    this.validationService.getHistory(d.id).subscribe({
      next: (logs) => { this.historique = logs; this.historiqueLoading = false; },
      error: () => { this.historiqueLoading = false; this.showMessage('Historique indisponible', 'error'); }
    });
  }

  fermerHistorique(): void {
    this.showHistoriqueModal = false;
    this.historique = [];
    this.declarationSelectionnee = null;
  }

  // ─── Helpers ──────────────────────────────────────────────────

  private mettreAJourListe(updated: Declaration): void {
    const idx = this.toutesDeclarations.findIndex(x => x.id === updated.id);
    if (idx !== -1) this.toutesDeclarations[idx] = updated;
    this.filtrerDeclarations();
  }

  private rafraichirStats(): void {
    this.validationService.getStats().subscribe({
      next: (s) => this.stats = s,
      error: () => {}
    });
  }

  getStatutClass(statut: string): string {
    const map: Record<string, string> = {
      'GENEREE':       'statut-generee',
      'EN_VALIDATION': 'statut-validation',
      'VALIDEE':       'statut-validee',
      'REJETEE':       'statut-rejetee',
      'ENVOYEE':       'statut-envoyee',
    };
    return map[statut] || '';
  }

  getStatutLabel(statut: string): string {
    const map: Record<string, string> = {
      'GENEREE':       'Générée',
      'EN_VALIDATION': 'En validation',
      'VALIDEE':       'Validée ✓',
      'REJETEE':       'Rejetée ✗',
      'ENVOYEE':       'Envoyée',
    };
    return map[statut] || statut;
  }

  getActionClass(action: string): string {
    const map: Record<string, string> = {
      'SUBMIT':   'action-submit',
      'VALIDATE': 'action-validate',
      'REJECT':   'action-reject',
      'SEND':     'action-send',
    };
    return map[action] || '';
  }

  getActionLabel(action: string): string {
    const map: Record<string, string> = {
      'SUBMIT':   '📤 Soumission',
      'VALIDATE': '✅ Validation',
      'REJECT':   '❌ Rejet',
      'SEND':     '📨 Envoi BCT',
    };
    return map[action] || action;
  }

  private showMessage(msg: string, type: 'success' | 'error'): void {
    this.message = msg;
    this.messageType = type;
    setTimeout(() => this.message = '', 5000);
  }
}