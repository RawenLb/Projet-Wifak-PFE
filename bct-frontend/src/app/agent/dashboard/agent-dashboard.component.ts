import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { DeclarationService, Declaration } from '../../services/Declaration.service';

@Component({
  selector: 'app-agent-dashboard',
  templateUrl: './agent-dashboard.component.html',
  styleUrls: ['./agent-dashboard.component.scss']
})
export class AgentDashboardComponent implements OnInit {

  declarations: Declaration[] = [];
  loading = false;

  // ── Stat cards ─────────────────────────────────────────────────
  get totalCount():      number { return this.declarations.length; }
  get pendingCount():    number { return this.count('EN_VALIDATION'); }
  get validatedCount():  number { return this.count('VALIDEE'); }
  get rejectedCount():   number { return this.count('REJETEE'); }
  get sentCount():       number { return this.count('ENVOYEE'); }

  // ── Déclarations récentes (5 dernières) ────────────────────────
  get recentDeclarations(): Declaration[] {
    return [...this.declarations]
      .sort((a, b) => {
        const da = a.dateGeneration ? new Date(a.dateGeneration).getTime() : 0;
        const db = b.dateGeneration ? new Date(b.dateGeneration).getTime() : 0;
        return db - da;
      })
      .slice(0, 5);
  }

  // ── Prochaines échéances (non envoyées, avec dateLimite) ───────
  get upcomingDeadlines(): Declaration[] {
    const today = new Date();
    return this.declarations
      .filter(d => !['ENVOYEE', 'VALIDEE'].includes(d.statut) && this.getDeadlineDate(d) !== null)
      .sort((a, b) => {
        const da = this.getDeadlineDate(a)!.getTime();
        const db = this.getDeadlineDate(b)!.getTime();
        return da - db;
      })
      .slice(0, 4);
  }

  // ── Bannière rejetées ──────────────────────────────────────────
  get hasRejected(): boolean { return this.rejectedCount > 0; }

  constructor(
    private declarationService: DeclarationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading = true;
    this.declarationService.getMyDeclarations().subscribe({
      next:  (data) => { this.declarations = data; this.loading = false; },
      error: ()     => { this.declarations = []; this.loading = false; }
    });
  }

  // ── Helpers ────────────────────────────────────────────────────

  count(statut: string): number {
    return this.declarations.filter(d => d.statut === statut).length;
  }

  getDeadlineDate(d: Declaration): Date | null {
    if (d.dateGeneration) {
      const gen = new Date(d.dateGeneration);
      if (!isNaN(gen.getTime())) return gen;
    }
    if (d.declarationType?.dateLimite && d.periode) {
      const raw = d.declarationType.dateLimite.toString().trim();
      if (/^\d{1,2}$/.test(raw)) {
        const [y, m] = d.periode.split('-').map(Number);
        const nextMonth = m === 12 ? 0 : m;
        const nextYear  = m === 12 ? y + 1 : y;
        return new Date(nextYear, nextMonth, parseInt(raw, 10));
      }
      const parsed = new Date(d.declarationType.dateLimite);
      if (!isNaN(parsed.getTime())) return parsed;
    }
    if (d.periode) {
      const [y, m] = d.periode.split('-').map(Number);
      return new Date(y, m, 0);
    }
    return null;
  }

  getDaysUntilDeadline(d: Declaration): number {
    const dl = this.getDeadlineDate(d);
    if (!dl) return 0;
    return Math.ceil((dl.getTime() - new Date().getTime()) / 86400000);
  }

  isOverdue(d: Declaration): boolean {
    if (['VALIDEE', 'ENVOYEE'].includes(d.statut)) return false;
    const dl = this.getDeadlineDate(d);
    return !!dl && dl < new Date();
  }

  getDeadlineLabel(d: Declaration): string {
    const days = this.getDaysUntilDeadline(d);
    if (this.isOverdue(d)) return `${Math.abs(days)}j de retard`;
    if (days === 0) return "Aujourd'hui";
    if (days === 1) return 'Demain';
    return `${days}j`;
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
      ENVOYEE: 'Traitée'
    };
    return m[s] || s;
  }

  formatDate(d?: string): string {
    if (!d) return '—';
    try {
      return new Date(d).toLocaleDateString('fr-FR', {
        day: '2-digit', month: '2-digit', year: 'numeric'
      });
    } catch { return d; }
  }

  formatDeadlineDate(d: Declaration): string {
    const dl = this.getDeadlineDate(d);
    if (!dl) return '—';
    return dl.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' });
  }

  // ── Navigation ─────────────────────────────────────────────────
  goToDeclarations():        void { this.router.navigate(['/agent/declarations']); }
  goToRejected():            void { this.router.navigate(['/agent/declarations']); }
  goToCalendar():            void { this.router.navigate(['/agent/calendar']); }
  openNewDeclaration():      void { this.router.navigate(['/agent/declarations']); }
}

