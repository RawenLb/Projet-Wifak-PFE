// src/app/components/manager-dashboard/manager-dashboard.component.ts

import { Component, OnInit } from '@angular/core';
import { DeclarationService, Declaration, DeclarationStats } from '../services/Declaration.service';
import { ValidationService, ValidationStats, ValidationLog } from '../services/Validation.service';

@Component({
  selector: 'app-manager-dashboard',
  templateUrl: './manager-dashboard.component.html',
  styleUrls: ['./manager-dashboard.component.scss']
})
export class ManagerDashboardComponent implements OnInit {

  // ── Données ────────────────────────────────────────────────────
  pending: Declaration[] = [];          // EN_VALIDATION uniquement
  toutesDeclarations: Declaration[] = []; // Toutes les déclarations
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

  constructor(
    private declarationService: DeclarationService,
    private validationService: ValidationService   // ✅ workflow via validation-service
  ) {}

  ngOnInit(): void {
    this.chargerDonnees();
  }

  // ─── Chargement initial ────────────────────────────────────────
  chargerDonnees(): void {
    this.loading = true;

    // 1. Stats (depuis validation-service)
    this.validationService.getStats().subscribe({
      next: (s) => this.stats = s,
      error: () => console.warn('Stats indisponibles')
    });

    // 2. Déclarations en attente (depuis validation-service)
    this.validationService.getPendingDeclarations().subscribe({
      next: (data) => {
        this.pending = data.sort((a, b) => (b.id ?? 0) - (a.id ?? 0));
      },
      error: (err) => this.showMessage('Erreur chargement pending : ' + err.message, 'error')
    });

    // 3. Toutes les déclarations (depuis declaration-service)
    this.declarationService.getAllDeclarations().subscribe({
      next: (data) => {
        this.toutesDeclarations = data.sort((a, b) => (b.id ?? 0) - (a.id ?? 0));
        this.declarationsFiltrees = [...this.toutesDeclarations];
        this.loading = false;
      },
      error: (err) => {
        this.showMessage('Erreur chargement déclarations : ' + err.message, 'error');
        this.loading = false;
      }
    });
  }

  // ─── Filtre statut ─────────────────────────────────────────────
  filtrerDeclarations(): void {
    if (!this.filtreStatut) {
      this.declarationsFiltrees = [...this.toutesDeclarations];
    } else {
      this.declarationsFiltrees = this.toutesDeclarations.filter(
        d => d.statut === this.filtreStatut
      );
    }
  }

  // ─── VALIDER ───────────────────────────────────────────────────
  // ✅ POST /api/validation/{id}/validate → validation-service
  valider(d: Declaration): void {
    if (!d.id) return;
    if (!confirm(`Valider la déclaration #${d.id} — ${d.declarationType?.nom} (${d.periode}) ?`)) return;

    this.actionEnCours[d.id] = true;

    this.validationService.validateDeclaration(d.id).subscribe({
      next: (updated) => {
        // Retirer de la liste pending
        this.pending = this.pending.filter(x => x.id !== d.id);
        // Mettre à jour dans toutes les déclarations
        this.mettreAJourListe(updated);
        this.actionEnCours[d.id!] = false;
        // Rafraîchir les stats
        this.rafraichirStats();
        this.showMessage(`✅ Déclaration #${d.id} validée avec succès.`, 'success');
      },
      error: (err) => {
        this.actionEnCours[d.id!] = false;
        this.showMessage('❌ ' + (err.error?.error || err.message), 'error');
      }
    });
  }

  // ─── OUVRIR MODAL REJET ────────────────────────────────────────
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

  // ─── CONFIRMER REJET ───────────────────────────────────────────
  // ✅ POST /api/validation/{id}/reject → validation-service
  confirmerRejet(): void {
    this.commentaireRejetTouched = true;
    if (!this.declarationSelectionnee?.id || !this.commentaireRejet.trim()) return;

    const id = this.declarationSelectionnee.id;
    this.rejetEnCours = true;

    this.validationService.rejectDeclaration(id, this.commentaireRejet.trim()).subscribe({
      next: (updated) => {
        // Retirer de la liste pending
        this.pending = this.pending.filter(x => x.id !== id);
        // Mettre à jour dans toutes les déclarations
        this.mettreAJourListe(updated);
        this.fermerRejet();
        this.rafraichirStats();
        this.showMessage(`❌ Déclaration #${id} rejetée.`, 'error');
      },
      error: (err) => {
        this.rejetEnCours = false;
        this.showMessage('Erreur : ' + (err.error?.error || err.message), 'error');
      }
    });
  }

  // ─── TÉLÉCHARGER ──────────────────────────────────────────────
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

  // ─── HISTORIQUE ────────────────────────────────────────────────
  // ✅ GET /api/validation/{id}/history → validation-service
  voirHistorique(d: Declaration): void {
    if (!d.id) return;
    this.declarationSelectionnee = d;
    this.historique = [];
    this.historiqueLoading = true;
    this.showHistoriqueModal = true;

    this.validationService.getHistory(d.id).subscribe({
      next: (logs) => {
        this.historique = logs;
        this.historiqueLoading = false;
      },
      error: () => {
        this.historiqueLoading = false;
        this.showMessage('Historique indisponible', 'error');
      }
    });
  }

  fermerHistorique(): void {
    this.showHistoriqueModal = false;
    this.historique = [];
    this.declarationSelectionnee = null;
  }

  // ─── Helpers ───────────────────────────────────────────────────
  private mettreAJourListe(updated: Declaration): void {
    const idx = this.toutesDeclarations.findIndex(x => x.id === updated.id);
    if (idx !== -1) {
      this.toutesDeclarations[idx] = updated;
    }
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
      'GENEREE':        'statut-generee',
      'EN_VALIDATION':  'statut-validation',
      'VALIDEE':        'statut-validee',
      'REJETEE':        'statut-rejetee',
      'ENVOYEE':        'statut-envoyee',
    };
    return map[statut] || '';
  }

  getStatutLabel(statut: string): string {
    const map: Record<string, string> = {
      'GENEREE':        'Générée',
      'EN_VALIDATION':  'En validation',
      'VALIDEE':        'Validée ✓',
      'REJETEE':        'Rejetée ✗',
      'ENVOYEE':        'Envoyée',
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