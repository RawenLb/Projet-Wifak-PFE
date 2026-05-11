import { Component, OnInit } from '@angular/core';
import { DeclarationService, Declaration } from '../../services/Declaration.service';
import { ValidationService } from '../../services/Validation.service';

// ─── Interfaces ──────────────────────────────────────────────────────────────

export interface CalendarCell {
  day: number;
  month: number;
  year: number;
  fullDate: Date;
  isOtherMonth: boolean;
  isToday: boolean;
  declarations: Declaration[];
}

export interface DayGroup {
  dateLabel: string;
  date: Date;
  items: Declaration[];
}

export interface WorkflowStep {
  label: string;
  done: boolean;
  current: boolean;
  error: boolean;
  last: boolean;
  cssClass: string;
}

// ─── Component ───────────────────────────────────────────────────────────────

@Component({
  selector: 'app-declaration-calendar',
  templateUrl: './declaration-calendar.component.html',
  styleUrls: ['./declaration-calendar.component.scss']
})
export class DeclarationCalendarComponent implements OnInit {

  // ── State ──────────────────────────────────────────────────────────────────
  declarations: Declaration[] = [];
  loadingDeclarations = false;

  currentMonth: number;
  currentYear: number;
  currentView: 'month' | 'list' = 'month';

  calendarCells: CalendarCell[] = [];

  // ── Modals ─────────────────────────────────────────────────────────────────
  showDetailModal = false;
  showDayModal    = false;
  selectedDeclaration: Declaration | null = null;
  selectedDayCell:     CalendarCell | null = null;

  // ── Constants ──────────────────────────────────────────────────────────────
  weekDays = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];

  monthNames = [
    'Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin',
    'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre'
  ];

  constructor(private declarationService: DeclarationService,   private validationService: ValidationService  // ✅ Ajouter
) {
    const today = new Date();
    this.currentMonth = today.getMonth();   // 0-indexed
    this.currentYear  = today.getFullYear();
  }

  ngOnInit(): void {
    this.loadDeclarations();
  }

  // ══════════════════════════════════════════════════════════════════════════
  // DATA LOADING
  // ══════════════════════════════════════════════════════════════════════════

  loadDeclarations(): void {
    this.loadingDeclarations = true;
    this.declarationService.getMyDeclarations().subscribe({
      next: (data) => {
        this.declarations = data;

        // ── Navigation automatique vers le mois le plus pertinent ────────────
        // On cherche la déclaration dont la deadline est la plus proche d'aujourd'hui
        if (data.length > 0) {
          const today = new Date();

          const datesWithDecl = data
            .map(d => ({ decl: d, date: this.getDeadlineDate(d) }))
            .filter(x => x.date !== null) as { decl: Declaration; date: Date }[];

          if (datesWithDecl.length > 0) {
            // Priorité 1 : déclarations futures ou du jour
            const future = datesWithDecl.filter(x => x.date >= today);
            // Priorité 2 : sinon la plus récente dans le passé
            const target = future.length > 0
              ? future.reduce((prev, curr) =>
                  curr.date.getTime() < prev.date.getTime() ? curr : prev)
              : datesWithDecl.reduce((prev, curr) =>
                  Math.abs(curr.date.getTime() - today.getTime()) <
                  Math.abs(prev.date.getTime() - today.getTime()) ? curr : prev);

            this.currentMonth = target.date.getMonth();
            this.currentYear  = target.date.getFullYear();
          }
        }

        this.buildCalendar();
        this.loadingDeclarations = false;
      },
      error: (err) => {
        console.error(err);
        this.declarations = [];
        this.buildCalendar();
        this.loadingDeclarations = false;
      }
    });
  }

  // ══════════════════════════════════════════════════════════════════════════
  // DEADLINE DATE RESOLVER
  //
  // Ordre de priorité :
  //   1. dateGeneration  → date réelle, afficher la déclaration ici
  //   2. dateLimite = numéro de jour + période  → deadline BCT
  //   3. dateLimite = date ISO complète
  //   4. Dernier jour du mois de la période  (fallback)
  // ══════════════════════════════════════════════════════════════════════════

  getDeadlineDate(d: Declaration): Date | null {

    // ── PRIORITÉ 1 : dateGeneration ─────────────────────────────────────────
    // La déclaration existe déjà → on l'affiche sur sa date de génération
    if (d.dateGeneration) {
      const gen = new Date(d.dateGeneration);
      if (!isNaN(gen.getTime())) return gen;
    }

    // ── PRIORITÉ 2 : dateLimite = numéro de jour (ex: "10", "15", "20") ────
    // La BCT impose souvent "le 10 du mois suivant la période"
    if (d.declarationType?.dateLimite && d.periode) {
      const raw = d.declarationType.dateLimite.toString().trim();

      if (/^\d{1,2}$/.test(raw)) {
        const [y, m] = d.periode.split('-').map(Number);
        const day    = parseInt(raw, 10);
        // Deadline = ce jour dans le mois suivant la période
        const nextMonth = m === 12 ? 0 : m;        // mois 0-indexed
        const nextYear  = m === 12 ? y + 1 : y;
        return new Date(nextYear, nextMonth, day);
      }

      // ── PRIORITÉ 3 : dateLimite = date ISO complète ──────────────────────
      const parsed = new Date(d.declarationType.dateLimite);
      if (!isNaN(parsed.getTime())) return parsed;
    }

    // ── PRIORITÉ 4 : dernier jour du mois de la période ──────────────────
    if (d.periode) {
      const [y, m] = d.periode.split('-').map(Number);
      return new Date(y, m, 0); // new Date(y, m, 0) = dernier jour du mois m
    }

    return null;
  }

  // ══════════════════════════════════════════════════════════════════════════
  // CALENDAR BUILDER
  // ══════════════════════════════════════════════════════════════════════════

  buildCalendar(): void {
    const cells: CalendarCell[] = [];
    const today = new Date();

    // Premier jour du mois affiché
    const firstDay = new Date(this.currentYear, this.currentMonth, 1);
    // Décalage lundi=0 … dimanche=6
    let startOffset = firstDay.getDay() - 1;
    if (startOffset < 0) startOffset = 6;

    // Nombre de jours dans le mois courant
    const daysInMonth   = new Date(this.currentYear, this.currentMonth + 1, 0).getDate();
    // Nombre de jours dans le mois précédent
    const prevMonthDays = new Date(this.currentYear, this.currentMonth, 0).getDate();

    // ── Cellules du mois précédent (padding gauche) ───────────────────────
    for (let i = startOffset - 1; i >= 0; i--) {
      const day   = prevMonthDays - i;
      const month = this.currentMonth === 0 ? 11 : this.currentMonth - 1;
      const year  = this.currentMonth === 0 ? this.currentYear - 1 : this.currentYear;
      const date  = new Date(year, month, day);
      cells.push({
        day, month, year, fullDate: date,
        isOtherMonth: true,
        isToday: this.isSameDay(date, today),
        declarations: this.getDeclarationsForDate(date)
      });
    }

    // ── Cellules du mois courant ──────────────────────────────────────────
    for (let day = 1; day <= daysInMonth; day++) {
      const date = new Date(this.currentYear, this.currentMonth, day);
      cells.push({
        day, month: this.currentMonth, year: this.currentYear, fullDate: date,
        isOtherMonth: false,
        isToday: this.isSameDay(date, today),
        declarations: this.getDeclarationsForDate(date)
      });
    }

    // ── Cellules du mois suivant (padding droit — 42 cellules total) ──────
    const remaining = 42 - cells.length;
    const nextMonth = this.currentMonth === 11 ? 0  : this.currentMonth + 1;
    const nextYear  = this.currentMonth === 11 ? this.currentYear + 1 : this.currentYear;
    for (let day = 1; day <= remaining; day++) {
      const date = new Date(nextYear, nextMonth, day);
      cells.push({
        day, month: nextMonth, year: nextYear, fullDate: date,
        isOtherMonth: true,
        isToday: this.isSameDay(date, today),
        declarations: this.getDeclarationsForDate(date)
      });
    }

    this.calendarCells = cells;
  }

  // ── Retourne les déclarations dont la deadline tombe sur cette date ───────
  private getDeclarationsForDate(date: Date): Declaration[] {
    return this.declarations.filter(d => {
      const deadline = this.getDeadlineDate(d);
      if (!deadline) return false;
      return this.isSameDay(deadline, date);
    });
  }

  // ══════════════════════════════════════════════════════════════════════════
  // NAVIGATION
  // ══════════════════════════════════════════════════════════════════════════

  prevMonth(): void {
    if (this.currentMonth === 0) {
      this.currentMonth = 11;
      this.currentYear--;
    } else {
      this.currentMonth--;
    }
    this.buildCalendar();
  }

  nextMonth(): void {
    if (this.currentMonth === 11) {
      this.currentMonth = 0;
      this.currentYear++;
    } else {
      this.currentMonth++;
    }
    this.buildCalendar();
  }

  goToToday(): void {
    const today       = new Date();
    this.currentMonth = today.getMonth();
    this.currentYear  = today.getFullYear();
    this.buildCalendar();
  }

  setView(v: 'month' | 'list'): void {
    this.currentView = v;
  }

  // ══════════════════════════════════════════════════════════════════════════
  // QUICK STATS
  // ══════════════════════════════════════════════════════════════════════════

  getOverdueCount(): number {
    return this.declarations.filter(d => this.isOverdue(d)).length;
  }

  getUpcomingCount(): number {
    const today = new Date();
    const in7   = new Date();
    in7.setDate(in7.getDate() + 7);
    return this.declarations.filter(d => {
      if (this.isOverdue(d)) return false;
      const dl = this.getDeadlineDate(d);
      return dl && dl >= today && dl <= in7;
    }).length;
  }

  // ══════════════════════════════════════════════════════════════════════════
  // LIST VIEW HELPERS
  // ══════════════════════════════════════════════════════════════════════════

  getDeclarationsForCurrentMonth(): Declaration[] {
    return this.calendarCells
      .filter(c => !c.isOtherMonth)
      .flatMap(c => c.declarations);
  }

  getGroupedDeclarationsForMonth(): DayGroup[] {
    const map = new Map<string, DayGroup>();

    this.calendarCells
      .filter(c => !c.isOtherMonth && c.declarations.length > 0)
      .forEach(c => {
        const key   = c.fullDate.toISOString().split('T')[0];
        const label = c.fullDate.toLocaleDateString('fr-FR', {
          weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'
        });
        if (!map.has(key)) {
          map.set(key, { dateLabel: label, date: c.fullDate, items: [] });
        }
        map.get(key)!.items.push(...c.declarations);
      });

    return Array.from(map.values()).sort((a, b) => a.date.getTime() - b.date.getTime());
  }

  // ══════════════════════════════════════════════════════════════════════════
  // MODAL HANDLERS
  // ══════════════════════════════════════════════════════════════════════════

  onDayClick(cell: CalendarCell): void {
    if (cell.declarations.length === 1) {
      this.openDetailModal(cell.declarations[0]);
    } else if (cell.declarations.length > 1) {
      this.openDayModal(cell);
    }
  }

  openDetailModal(d: Declaration): void {
    this.selectedDeclaration = d;
    this.showDetailModal     = true;
  }

  closeDetailModal(): void {
    this.showDetailModal     = false;
    this.selectedDeclaration = null;
  }

  openDayModal(cell: CalendarCell): void {
    this.selectedDayCell = cell;
    this.showDayModal    = true;
  }

  closeDayModal(): void {
    this.showDayModal    = false;
    this.selectedDayCell = null;
  }

  // ══════════════════════════════════════════════════════════════════════════
  // WORKFLOW TIMELINE
  // ══════════════════════════════════════════════════════════════════════════

  getWorkflowSteps(d: Declaration | null): WorkflowStep[] {
    if (!d) return [];
    const statut = d.statut;

    const order = ['BROUILLON', 'GENEREE', 'EN_VALIDATION', 'VALIDEE', 'ENVOYEE'];
    const labels: Record<string, string> = {
      BROUILLON:     'Brouillon',
      GENEREE:       'Générée',
      EN_VALIDATION: 'En validation',
      VALIDEE:       'Validée',
      ENVOYEE:       'Envoyée'
    };

    if (statut === 'REJETEE') {
      return [
        { label: 'Générée',       done: true,  current: false, error: false, last: false, cssClass: 'step-done'  },
        { label: 'En validation', done: false, current: false, error: false, last: false, cssClass: 'step-done'  },
        { label: 'Rejetée',       done: false, current: true,  error: true,  last: true,  cssClass: 'step-error' }
      ];
    }

    const currentIdx = order.indexOf(statut);
    return order.map((s, i) => ({
      label:    labels[s],
      done:     i < currentIdx,
      current:  i === currentIdx,
      error:    false,
      last:     i === order.length - 1,
      cssClass: i < currentIdx   ? 'step-done'
              : i === currentIdx ? 'step-current'
              :                    'step-future'
    }));
  }

  // ══════════════════════════════════════════════════════════════════════════
  // ACTIONS
  // ══════════════════════════════════════════════════════════════════════════

  downloadDeclaration(d: Declaration): void {
    if (!d.id) return;

    if (d.contenuFichier) {
      const mimeType = this.declarationService.resolveMimeType(d.nomFichier || '');
      const blob     = new Blob([d.contenuFichier], { type: mimeType });
      this.triggerDownload(blob, d.nomFichier || 'declaration');
      return;
    }

    this.declarationService.downloadDeclaration(d.id).subscribe({
      next:  blob => this.triggerDownload(blob, d.nomFichier || 'declaration'),
      error: ()   => alert('Erreur lors du téléchargement')
    });
  }

  private triggerDownload(blob: Blob, filename: string): void {
    const url = window.URL.createObjectURL(blob);
    const a   = document.createElement('a');
    a.href     = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
  }

  submitForValidation(d: Declaration): void {
    if (!d.id) return;
    if (!confirm(
      `Soumettre la déclaration ${d.declarationType?.code} (${d.periode}) pour validation ?`
    )) return;

    this.validationService.submitForValidation(d.id).subscribe({
      next:  () => { alert('Soumis pour validation !'); this.loadDeclarations(); },
      error: err => alert('Erreur : ' + (err.error?.message || err.message))
    });
  }

  // ══════════════════════════════════════════════════════════════════════════
  // HELPERS & CSS CLASS MAPS
  // ══════════════════════════════════════════════════════════════════════════

  isOverdue(d: Declaration): boolean {
    if (['VALIDEE', 'ENVOYEE'].includes(d.statut)) return false;
    const dl = this.getDeadlineDate(d);
    return !!dl && dl < new Date();
  }

  canSubmit(d: Declaration | null): boolean {
    return !!d && (d.statut === 'GENEREE' || d.statut === 'REJETEE');
  }

  getDeadlineLabel(d: Declaration): string {
    const dl = this.getDeadlineDate(d);
    if (!dl) return '—';

    if (this.isOverdue(d)) {
      const diffDays = Math.ceil((new Date().getTime() - dl.getTime()) / 86400000);
      return `En retard de ${diffDays}j`;
    }

    const diffDays = Math.ceil((dl.getTime() - new Date().getTime()) / 86400000);
    if (diffDays === 0) return "Aujourd'hui";
    if (diffDays === 1) return 'Demain';
    return `Dans ${diffDays}j`;
  }

  getEventClass(d: Declaration): string {
    if (this.isOverdue(d)) return 'event-overdue';
    const map: Record<string, string> = {
      VALIDEE:       'event-validated',
      ENVOYEE:       'event-sent',
      EN_VALIDATION: 'event-pending',
      GENEREE:       'event-generated',
      BROUILLON:     'event-draft',
      REJETEE:       'event-rejected'
    };
    return map[d.statut] || 'event-default';
  }

  getListItemClass(d: Declaration): string {
    if (this.isOverdue(d)) return 'item-overdue';
    const map: Record<string, string> = {
      VALIDEE:       'item-validated',
      ENVOYEE:       'item-sent',
      EN_VALIDATION: 'item-pending',
      GENEREE:       'item-generated',
      BROUILLON:     'item-draft',
      REJETEE:       'item-rejected'
    };
    return map[d.statut] || '';
  }

  getStatusBadgeClass(s: string): string {
    const m: Record<string, string> = {
      BROUILLON:     'status-draft',
      GENEREE:       'status-generated',
      EN_VALIDATION: 'status-pending',
      VALIDEE:       'status-validated',
      REJETEE:       'status-rejected',
      ENVOYEE:       'status-sent'
    };
    return m[s] || 'status-default';
  }

  getStatusLabel(s: string): string {
    const m: Record<string, string> = {
      BROUILLON:     'Brouillon',
      GENEREE:       'Générée',
      EN_VALIDATION: 'En validation',
      VALIDEE:       'Validée',
      REJETEE:       'Rejetée',
      ENVOYEE:       'Envoyée'
    };
    return m[s] || s;
  }

  getIconClass(s: string | undefined): string {
    const m: Record<string, string> = {
      VALIDEE:       'icon-validated',
      ENVOYEE:       'icon-sent',
      EN_VALIDATION: 'icon-pending',
      REJETEE:       'icon-rejected',
      GENEREE:       'icon-generated',
      BROUILLON:     'icon-draft'
    };
    return m[s || ''] || 'icon-default';
  }

  getMonthName(year: number, month: number): string {
    return this.monthNames[month];
  }

  formatDate(d?: string): string {
    if (!d) return '—';
    try {
      return new Date(d).toLocaleDateString('fr-FR', {
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit'
      });
    } catch { return d; }
  }

  private isSameDay(a: Date, b: Date): boolean {
    return a.getFullYear() === b.getFullYear() &&
           a.getMonth()    === b.getMonth()    &&
           a.getDate()     === b.getDate();
  }
}