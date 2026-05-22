// src/app/services/notification.service.ts
// ✅ NOUVEAU FICHIER — génère les notifications depuis les déclarations existantes

import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { DeclarationService, Declaration } from './Declaration.service';

export interface AppNotification {
  id: number;
  type: 'deadline' | 'reject' | 'pending';
  severity: 'danger' | 'warning' | 'info' | 'success';
  title: string;
  msg: string;
  decl: string;
  periode: string;
  statut: string;
  time: string;
  unread: boolean;
  daysLeft?: number;
  motif?: string;
  declarationId: number;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {

  private _notifications = new BehaviorSubject<AppNotification[]>([]);
  notifications$ = this._notifications.asObservable();

  get unreadCount(): number {
    return this._notifications.value.filter(n => n.unread).length;
  }

  get all(): AppNotification[] {
    return this._notifications.value;
  }

  constructor(private declarationService: DeclarationService) {}

  // ─── Appelé depuis ngOnInit du layout ───────────────────────
  loadNotifications(): void {
    this.declarationService.getMyDeclarations().subscribe({
      next: (decls) => {
        const notifs: AppNotification[] = [];
        const today = new Date();
        let idCounter = 1;

        decls.forEach(d => {
          const code = `${d.declarationType?.code}_${d.id}`;

          // ── 1. REJETÉES ─────────────────────────────────────
          if (d.statut === 'REJETEE') {
            notifs.push({
              id: idCounter++,
              type: 'reject',
              severity: 'danger',
              title: 'Déclaration rejetée — action requise',
              msg: `Votre déclaration ${code} a été rejetée. Veuillez procéder aux corrections nécessaires.`,
              decl: code,
              periode: d.periode,
              statut: d.statut,
              time: d.dateValidation ? this.formatTime(d.dateValidation) : '',
              unread: true,
              motif: d.commentaireRejet || undefined,
              declarationId: d.id!
            });
          }

          // ── 2. ÉCHÉANCES PROCHES (≤ 2 jours) ────────────────
          if (!['ENVOYEE', 'VALIDEE'].includes(d.statut) && d.dateFin) {
            const fin = new Date(d.dateFin as string);
            const daysLeft = Math.ceil((fin.getTime() - today.getTime()) / 86400000);
            if (daysLeft >= 0 && daysLeft <= 2) {
              notifs.push({
                id: idCounter++,
                type: 'deadline',
                severity: daysLeft === 0 ? 'danger' : 'warning',
                title: daysLeft === 0
                  ? `Échéance aujourd'hui — ${code}`
                  : `Échéance dans ${daysLeft} jour(s) — ${code}`,
                msg: daysLeft === 0
                  ? `La déclaration ${code} arrive à échéance aujourd'hui. Soumettez-la immédiatement.`
                  : `Échéance dans ${daysLeft} jour(s) — déclaration ${code}.`,
                decl: code,
                periode: d.periode,
                statut: d.statut,
                time: "Aujourd'hui 08:00",
                unread: true,
                daysLeft,
                declarationId: d.id!
              });
            }
          }

          // ── 3. EN VALIDATION (info pour l'agent) ────────────
          if (d.statut === 'EN_VALIDATION') {
            notifs.push({
              id: idCounter++,
              type: 'pending',
              severity: 'info',
              title: 'Déclaration en attente de validation',
              msg: `La déclaration ${code} est en cours de validation par le responsable.`,
              decl: code,
              periode: d.periode,
              statut: d.statut,
              time: d.dateGeneration ? this.formatTime(d.dateGeneration) : '',
              unread: false,
              declarationId: d.id!
            });
          }
        });

        // Tri : unread d'abord, puis par sévérité
        const severityOrder = { danger: 0, warning: 1, info: 2, success: 3 };
        notifs.sort((a, b) => {
          if (a.unread !== b.unread) return a.unread ? -1 : 1;
          return severityOrder[a.severity] - severityOrder[b.severity];
        });

        this._notifications.next(notifs);
      },
      error: () => this._notifications.next([])
    });
  }

  // ─── Pour le manager : charge toutes les déclarations ───────
  loadManagerNotifications(): void {
    this.declarationService.getAllDeclarations().subscribe({
      next: (decls) => {
        const notifs: AppNotification[] = [];
        let idCounter = 1;

        decls
          .filter(d => d.statut === 'EN_VALIDATION')
          .forEach(d => {
            const code = `${d.declarationType?.code}_${d.id}`;
            notifs.push({
              id: idCounter++,
              type: 'pending',
              severity: 'warning',
              title: 'Déclaration en attente de validation',
              msg: `La déclaration ${code} est en attente de validation. Soumise par ${d.generePar}.`,
              decl: code,
              periode: d.periode,
              statut: d.statut,
              time: d.dateGeneration ? this.formatTime(d.dateGeneration) : '',
              unread: true,
              declarationId: d.id!
            });
          });

        this._notifications.next(notifs);
      },
      error: () => this._notifications.next([])
    });
  }

  markAllRead(): void {
    const updated = this._notifications.value.map(n => ({ ...n, unread: false }));
    this._notifications.next(updated);
  }

  markRead(id: number): void {
    const updated = this._notifications.value.map(n =>
      n.id === id ? { ...n, unread: false } : n
    );
    this._notifications.next(updated);
  }

  private formatTime(dateStr: string): string {
    try {
      return new Date(dateStr).toLocaleDateString('fr-FR', {
        day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit'
      });
    } catch { return ''; }
  }
}