// src/app/auditor-logs/auditor-logs.component.ts
// US-12 — Journaux de traçabilité / Logs d'actions (lecture seule)
import { Component, OnInit } from '@angular/core';
import { AuditorService, AuditLogDTO, AuditSearchParams } from '../../services/auditor.service';

@Component({
  selector: 'app-auditor-logs',
  templateUrl: './auditor-logs.component.html',
  styleUrls: ['./auditor-logs.component.scss']
})
export class AuditorLogsComponent implements OnInit {

  loading = false;

  allLogs: AuditLogDTO[] = [];

  searchQuery  = '';
  filterAction = '';
  filterUser   = '';
  filterFrom   = '';
  filterTo     = '';

  currentPage = 1;
  pageSize    = 50;

  availableUsers: string[] = [];

  constructor(private auditorService: AuditorService) {}

  ngOnInit(): void {
    this.charger();
    this.loadUsers();
  }

  charger(): void {
    this.loading = true;
    this.auditorService.getAllLogs().subscribe({
      next: (data) => {
        this.allLogs = data;
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  loadUsers(): void {
    this.auditorService.getDistinctUsers().subscribe({
      next: (users) => { this.availableUsers = users; },
      error: () => {}
    });
  }

  applySearch(): void {
    this.loading = true;
    this.currentPage = 1;

    const params: AuditSearchParams = {
      action:      this.filterAction || undefined,
      effectuePar: this.filterUser   || undefined,
      from:        this.filterFrom   || undefined,
      to:          this.filterTo     || undefined,
    };

    // Si aucun filtre serveur, on recharge tout
    const hasServerFilter = params.action || params.effectuePar || params.from || params.to;

    if (hasServerFilter) {
      this.auditorService.searchLogs(params).subscribe({
        next: (data) => { this.allLogs = data; this.loading = false; },
        error: () => { this.loading = false; }
      });
    } else {
      this.auditorService.getAllLogs().subscribe({
        next: (data) => { this.allLogs = data; this.loading = false; },
        error: () => { this.loading = false; }
      });
    }
  }

  onFilterChange(): void {
    this.currentPage = 1;
    this.applySearch();
  }

  get uniqueUsers(): string[] { return this.availableUsers; }

  get filteredLogs(): AuditLogDTO[] {
    if (!this.searchQuery) return this.allLogs;
    const q = this.searchQuery.toLowerCase();
    return this.allLogs.filter(log =>
      String(log.declarationId).includes(q)
      || log.effectuePar?.toLowerCase().includes(q)
      || log.declarationCode?.toLowerCase().includes(q)
      || log.action?.toLowerCase().includes(q)
      || log.declarationPeriode?.toLowerCase().includes(q)
    );
  }

  get pagedLogs(): AuditLogDTO[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredLogs.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.ceil(this.filteredLogs.length / this.pageSize);
  }

  get pages(): number[] {
    const total = this.totalPages;
    if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);
    const pages: number[] = [1];
    if (this.currentPage > 3) pages.push(-1);
    for (let i = Math.max(2, this.currentPage - 1); i <= Math.min(total - 1, this.currentPage + 1); i++) {
      pages.push(i);
    }
    if (this.currentPage < total - 2) pages.push(-1);
    pages.push(total);
    return pages;
  }

  exportCSV(): void {
    const rows = ['ID Log,Déclaration ID,Code,Période,Action,Statut avant,Statut après,Effectué par,Date action'];
    this.filteredLogs.forEach(log => {
      rows.push([
        log.id,
        log.declarationId,
        log.declarationCode || '',
        log.declarationPeriode || '',
        log.action,
        log.statutAvant || '',
        log.statutApres || '',
        log.effectuePar,
        new Date(log.dateAction).toLocaleString('fr-FR'),
      ].join(','));
    });
    const blob = new Blob([rows.join('\n')], { type: 'text/csv;charset=utf-8;' });
    const url  = URL.createObjectURL(blob);
    const a    = document.createElement('a');
    a.href     = url;
    a.download = `journaux_audit_${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  getActionClass(action: string): string {
    const m: Record<string, string> = {
      'SUBMIT': 'act-sub', 'VALIDATE': 'act-val', 'REJECT': 'act-rej', 'SEND': 'act-snd'
    };
    return m[action] || '';
  }

  getActionLabel(action: string): string {
    const m: Record<string, string> = {
      'SUBMIT': 'Soumission', 'VALIDATE': 'Validation',
      'REJECT': 'Rejet',      'SEND': 'Traitement'
    };
    return m[action] || action;
  }

  getActionIcon(action: string): string {
    const m: Record<string, string> = { 'SUBMIT': '→', 'VALIDATE': '✓', 'REJECT': '✕', 'SEND': '✉' };
    return m[action] || '·';
  }
}
