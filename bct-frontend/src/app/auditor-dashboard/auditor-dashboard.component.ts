// src/app/auditor-dashboard/auditor-dashboard.component.ts
import { Component, OnInit } from '@angular/core';
import { AuditorService, AuditStatsDTO } from '../services/auditor.service';
import { Declaration } from '../services/Declaration.service';

@Component({
  selector: 'app-auditor-dashboard',
  templateUrl: './auditor-dashboard.component.html',
  styleUrls: ['./auditor-dashboard.component.scss']
})
export class AuditorDashboardComponent implements OnInit {

  loading = false;

  stats: AuditStatsDTO = {
    totalDeclarations: 0, generees: 0, enValidation: 0,
    validees: 0, rejetees: 0, envoyees: 0,
    totalLogs: 0, totalSoumissions: 0, totalValidations: 0,
    totalRejets: 0, totalEnvois: 0,
    tauxValidation: 0, tauxRejet: 0,
    topAgents: [], topManagers: [], actionCounts: {}
  };

  recentDeclarations: Declaration[] = [];

  constructor(private auditorService: AuditorService) {}

  ngOnInit(): void {
    this.loadStats();
    this.loadRecent();
  }

  loadStats(): void {
    this.auditorService.getAuditStats().subscribe({
      next: (s) => { this.stats = s; },
      error: (err) => { console.error('Stats error', err); }
    });
  }

  loadRecent(): void {
    this.loading = true;
    this.auditorService.getAllDeclarations().subscribe({
      next: (data) => {
        this.recentDeclarations = data
          .sort((a: Declaration, b: Declaration) => (b.id ?? 0) - (a.id ?? 0))
          .slice(0, 8);
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  getBadgeClass(statut: string): string {
    const m: Record<string, string> = {
      'VALIDEE':      'status-validated',
      'ENVOYEE':      'status-sent',
      'REJETEE':      'status-rejected',
      'EN_VALIDATION':'status-pending',
      'GENEREE':      'status-generated'
    };
    return m[statut] || 'status-draft';
  }

  getStatutLabel(statut: string): string {
    const m: Record<string, string> = {
      'GENEREE': 'Générée', 'EN_VALIDATION': 'En validation',
      'VALIDEE': 'Validée', 'REJETEE': 'Rejetée', 'ENVOYEE': 'Envoyée BCT'
    };
    return m[statut] || statut;
  }

  get tauxValidation(): number { return this.stats.tauxValidation ?? 0; }
  get tauxRejet(): number      { return this.stats.tauxRejet ?? 0; }
}
