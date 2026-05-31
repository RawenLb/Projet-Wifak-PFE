// src/app/manager-calendar/manager-calendar.component.ts

import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { DeclarationService, Declaration } from '../../services/Declaration.service';
import { DeclarationTypeService, DeclarationType } from '../../services/declaration-type.service';

export interface CalendarEvent {
  declarationId?: number;
  typeId?: number;
  code: string;
  nom: string;
  statut: string;
  periode: string;
  frequence: string;
  generePar?: string;
  dateLimiteRaw?: Date;
}

export interface CalendarCell {
  day: number | null;
  date?: Date;
  isToday: boolean;
  events: CalendarEvent[];
}

@Component({
  selector: 'app-manager-calendar',
  templateUrl: './manager-calendar.component.html',
  styleUrls: ['./manager-calendar.component.scss']
})
export class ManagerCalendarComponent implements OnInit {

  loading = false;
  currentDate = new Date();

  declarations: Declaration[] = [];
  declarationTypes: DeclarationType[] = [];

  calendarCells: CalendarCell[] = [];
  selectedCell: CalendarCell | null = null;

  dayHeaders = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];

  legend = [
    { cls: 'ev-green',  label: 'Validée / Envoyée' },
    { cls: 'ev-amber',  label: 'En cours / Générée' },
    { cls: 'ev-red',    label: 'Rejetée / En retard' },
    { cls: 'ev-blue',   label: 'En validation' },
  ];

  get currentMonthLabel(): string {
    return this.currentDate.toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' });
  }

  get monthStats() {
    const cells = this.calendarCells.filter(c => c.day && c.events.length > 0);
    const allEvents = cells.flatMap(c => c.events);
    return {
      total:    allEvents.length,
      enCours:  allEvents.filter(e => ['GENEREE', 'BROUILLON'].includes(e.statut)).length,
      enRetard: allEvents.filter(e => e.statut === 'REJETEE').length,
      validees: allEvents.filter(e => ['VALIDEE', 'ENVOYEE'].includes(e.statut)).length,
    };
  }

  get overdueDeclarations(): Declaration[] {
    return this.declarations.filter(d =>
      d.statut === 'REJETEE' ||
      (d.statut === 'GENEREE' && this.isPastDeadline(d))
    ).slice(0, 8);
  }

  constructor(
    private declarationService: DeclarationService,
    private declarationTypeService: DeclarationTypeService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.chargerDonnees();
  }

  chargerDonnees(): void {
    this.loading = true;
    this.declarationService.getAllDeclarations().subscribe({
      next: (data) => {
        this.declarations = data;
        this.declarationTypeService.getAll().subscribe({
          next: (types) => {
            this.declarationTypes = types;
            this.buildCalendar();
            this.loading = false;
          },
          error: () => {
            this.buildCalendar();
            this.loading = false;
          }
        });
      },
      error: () => { this.loading = false; }
    });
  }

  buildCalendar(): void {
    const year  = this.currentDate.getFullYear();
    const month = this.currentDate.getMonth();
    const today = new Date();

    const firstDay = new Date(year, month, 1);
    const lastDay  = new Date(year, month + 1, 0);
    const totalDays = lastDay.getDate();

    // Lundi = 0 … Dimanche = 6
    let startOffset = firstDay.getDay() - 1;
    if (startOffset < 0) startOffset = 6;

    this.calendarCells = [];

    // Cellules vides avant le 1er
    for (let i = 0; i < startOffset; i++) {
      this.calendarCells.push({ day: null, isToday: false, events: [] });
    }

    // Jours du mois
    for (let d = 1; d <= totalDays; d++) {
      const date = new Date(year, month, d);
      const isToday = date.toDateString() === today.toDateString();
      const events  = this.getEventsForDay(date);
      this.calendarCells.push({ day: d, date, isToday, events });
    }
  }

  getEventsForDay(date: Date): CalendarEvent[] {
    const events: CalendarEvent[] = [];
    const dateStr = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;

    // Déclarations dont la période correspond au mois courant
    this.declarations.forEach(d => {
      if (!d.periode) return;
      const periodeNorm = d.periode.replace('/', '-').replace(' ', '-');
      if (periodeNorm.includes(dateStr) || d.periode.includes(dateStr)) {
        // Placer l'événement au dernier jour ouvrable estimé (simplification: jour 25 si mensuel)
        const targetDay = this.getTargetDay(d, date);
        if (date.getDate() === targetDay) {
          events.push({
            declarationId: d.id,
            code:      d.declarationType?.code || '—',
            nom:       d.declarationType?.nom  || '—',
            statut:    d.statut,
            periode:   d.periode,
            frequence: d.declarationType?.frequence || '—',
            generePar: d.generePar,
          });
        }
      }
    });

    // Types BCT actifs — date limite de dépôt
    this.declarationTypes.filter(t => t.actif).forEach(t => {
      const dateLimite = this.parseDateLimite(t.dateLimite, date);
      if (dateLimite && dateLimite.getDate() === date.getDate()) {
        // Vérifier si déjà couvert par une déclaration
        const alreadyCovered = events.some(e => e.code === t.code);
        if (!alreadyCovered) {
          events.push({
            typeId:    t.id,
            code:      t.code,
            nom:       t.nom,
            statut:    'ECHEANCE',
            periode:   dateStr,
            frequence: t.frequence,
          });
        }
      }
    });

    return events;
  }

  private getTargetDay(d: Declaration, date: Date): number {
    const freq = d.declarationType?.frequence;
    if (freq === 'MENSUELLE')      return 25;
    if (freq === 'TRIMESTRIELLE')  return 20;
    if (freq === 'QUOTIDIENNE')    return date.getDate();
    if (freq === 'HEBDOMADAIRE')   return date.getDate();
    return 25;
  }

  private parseDateLimite(dateLimite: string | undefined, currentDate: Date): Date | null {
    if (!dateLimite) return null;
    // Format "J-5" → dernier jour - 5
    const match = dateLimite.match(/J-?(\d+)/i);
    if (match) {
      const lastDay = new Date(currentDate.getFullYear(), currentDate.getMonth() + 1, 0);
      lastDay.setDate(lastDay.getDate() - parseInt(match[1]));
      return lastDay;
    }
    // Format "25" → jour 25 du mois
    const dayNum = parseInt(dateLimite);
    if (!isNaN(dayNum) && dayNum >= 1 && dayNum <= 31) {
      return new Date(currentDate.getFullYear(), currentDate.getMonth(), dayNum);
    }
    return null;
  }

  private isPastDeadline(d: Declaration): boolean {
    return false; // À affiner avec la logique métier réelle
  }

  selectDay(cell: CalendarCell): void {
    if (cell.events.length === 0) {
      this.selectedCell = null;
      return;
    }
    this.selectedCell = cell;
  }

  openEvent(e: CalendarEvent): void {
    if (e.declarationId) {
      this.router.navigate(['/manager/pending'], {
        queryParams: { highlight: e.declarationId }
      });
    }
  }

  voirDeclaration(e: CalendarEvent): void {
    if (e.declarationId) {
      this.router.navigate(['/manager/pending'], {
        queryParams: { highlight: e.declarationId }
      });
    }
  }

  voirDeclarationById(d: Declaration): void {
    this.router.navigate(['/manager/pending'], {
      queryParams: { highlight: d.id }
    });
  }

  previousMonth(): void {
    this.currentDate = new Date(
      this.currentDate.getFullYear(),
      this.currentDate.getMonth() - 1,
      1
    );
    this.selectedCell = null;
    this.buildCalendar();
  }

  nextMonth(): void {
    this.currentDate = new Date(
      this.currentDate.getFullYear(),
      this.currentDate.getMonth() + 1,
      1
    );
    this.selectedCell = null;
    this.buildCalendar();
  }

  goToToday(): void {
    this.currentDate = new Date();
    this.selectedCell = null;
    this.buildCalendar();
  }

  getEventClass(e: CalendarEvent): string {
    const map: Record<string, string> = {
      'VALIDEE':       'ev-green',
      'ENVOYEE':       'ev-green',
      'EN_VALIDATION': 'ev-blue',
      'GENEREE':       'ev-amber',
      'BROUILLON':     'ev-amber',
      'REJETEE':       'ev-red',
      'ECHEANCE':      'ev-gray',
    };
    return map[e.statut] || 'ev-gray';
  }

  getDotClass(statut: string): string {
    const map: Record<string, string> = {
      'VALIDEE': 'dot-green', 'ENVOYEE': 'dot-green',
      'EN_VALIDATION': 'dot-blue',
      'GENEREE': 'dot-amber', 'BROUILLON': 'dot-amber',
      'REJETEE': 'dot-red',
      'ECHEANCE': 'dot-gray',
    };
    return map[statut] || 'dot-gray';
  }

  getStatutClass(statut: string): string {
    const map: Record<string, string> = {
      'GENEREE': 'sc-generee', 'EN_VALIDATION': 'sc-validation',
      'VALIDEE': 'sc-validee', 'REJETEE': 'sc-rejetee',
      'ENVOYEE': 'sc-envoyee',
    };
    return map[statut] || '';
  }

  getStatutLabel(statut: string): string {
    const map: Record<string, string> = {
      'GENEREE': 'Générée', 'EN_VALIDATION': 'En validation',
      'VALIDEE': 'Validée', 'REJETEE': 'Rejetée', 'ENVOYEE': 'Traitée',
    };
    return map[statut] || statut;
  }
}