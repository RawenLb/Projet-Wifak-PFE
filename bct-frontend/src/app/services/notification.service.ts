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
  time: string;       // formatted display string
  rawDate?: string;   // ISO date string for relative time calculation
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
            const rejectDate = this.parseDate(d.dateValidation) || this.parseDate(d.dateGeneration);
            notifs.push({
              id: idCounter++,
              type: 'reject',
              severity: 'danger',
              title: 'Déclaration rejetée — action requise',
              msg: `Votre déclaration ${code} a été rejetée. Veuillez procéder aux corrections nécessaires.`,
              decl: code,
              periode: d.periode,
              statut: d.statut,
              time: rejectDate ? this.formatTime(rejectDate) : '',
              rawDate: rejectDate ? rejectDate.toISOString() : undefined,
              unread: true,
              motif: d.commentaireRejet || undefined,
              declarationId: d.id!
            });
          }

          // ── 2. ÉCHÉANCES PROCHES (≤ 2 jours) ────────────────
          if (!['ENVOYEE', 'VALIDEE'].includes(d.statut) && d.dateFin) {
            const fin = this.parseDate(d.dateFin);
            if (fin) {
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
                  time: this.formatTime(fin),
                  rawDate: fin.toISOString(),
                  unread: true,
                  daysLeft,
                  declarationId: d.id!
                });
              }
            }
          }

          // ── 3. EN VALIDATION (info pour l'agent) ────────────
          if (d.statut === 'EN_VALIDATION') {
            const genDate = this.parseDate(d.dateGeneration);
            notifs.push({
              id: idCounter++,
              type: 'pending',
              severity: 'info',
              title: 'Déclaration en attente de validation',
              msg: `La déclaration ${code} est en cours de validation par le responsable.`,
              decl: code,
              periode: d.periode,
              statut: d.statut,
              time: genDate ? this.formatTime(genDate) : '',
              rawDate: genDate ? genDate.toISOString() : undefined,
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
            const genDate = this.parseDate(d.dateGeneration);
            notifs.push({
              id: idCounter++,
              type: 'pending',
              severity: 'warning',
              title: 'Déclaration en attente de validation',
              msg: `La déclaration ${code} est en attente de validation. Soumise par ${d.generePar}.`,
              decl: code,
              periode: d.periode,
              statut: d.statut,
              time: genDate ? this.formatTime(genDate) : '',
              rawDate: genDate ? genDate.toISOString() : undefined,
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

  /**
   * Parse a date value that may come from Jackson as:
   * - an ISO string:       "2026-04-05T14:30:00"  (LocalDateTime with @JsonFormat)
   * - a date string:       "2026-04-30"            (LocalDate with @JsonFormat)
   * - a Jackson array:     [2026, 4, 5, 14, 30, 0] (LocalDateTime without @JsonFormat)
   * - a LocalDate array:   [2026, 4, 30]            (LocalDate without @JsonFormat)
   * Returns a valid Date or null.
   */
  private parseDate(value: any): Date | null {
    if (!value) return null;

    // Array format from Jackson: [year, month, day] or [year, month, day, hour, min, sec, nano?]
    if (Array.isArray(value)) {
      const [year, month, day, hour = 0, min = 0, sec = 0] = value as number[];
      if (!year || !month || !day) return null;
      // Jackson months are 1-based, JS Date months are 0-based
      const d = new Date(year, month - 1, day, hour, min, sec);
      return isNaN(d.getTime()) ? null : d;
    }

    // String format — ISO datetime or date
    if (typeof value === 'string' && value.length >= 10) {
      const d = new Date(value);
      return isNaN(d.getTime()) ? null : d;
    }

    return null;
  }

  private formatTime(date: Date): string {
    try {
      return date.toLocaleDateString('fr-FR', {
        day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit'
      });
    } catch { return ''; }
  }
}