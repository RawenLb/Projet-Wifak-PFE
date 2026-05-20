// src/app/auditor-export/auditor-export.component.ts
// Rapport d'Audit BCT — 8 sections — formats CSV + PDF
import { Component, OnInit } from '@angular/core';
import { AuditorService, AuditLogDTO, AuditStatsDTO } from '../../services/auditor.service';
import { DeclarationService, Declaration } from '../../services/Declaration.service';
import { KeycloakAdminService } from '../../services/keycloak-admin.service';

export interface ExportConfig {
  filterStatut:  string;
  filterType:    string;
  filterPeriode: string;
  dateFrom:      string;
  dateTo:        string;
  auditeurNom:   string;
  format:        'csv' | 'pdf';
}

@Component({
  selector: 'app-auditor-export',
  templateUrl: './auditor-export.component.html',
  styleUrls: ['./auditor-export.component.scss']
})
export class AuditorExportComponent implements OnInit {

  loading   = false;
  exporting = false;

  declarations: Declaration[] = [];
  allLogs: AuditLogDTO[] = [];
  stats: AuditStatsDTO | null = null;

  config: ExportConfig = {
    filterStatut:  '',
    filterType:    '',
    filterPeriode: '',
    dateFrom:      '',
    dateTo:        '',
    auditeurNom:   '',
    format:        'csv',
  };

  constructor(
    private declarationService: DeclarationService,
    private auditorService:     AuditorService,
    private kcAdmin:            KeycloakAdminService
  ) {}

  ngOnInit(): void {
    // Pré-remplir le nom de l'auditeur depuis le token Keycloak
    this.config.auditeurNom = this.kcAdmin.getFullName() || this.kcAdmin.getUsername();
    this.charger();
  }

  charger(): void {
    this.loading = true;
    this.declarationService.getAllDeclarations().subscribe({
      next: (data) => {
        this.declarations = data;
        this.loadLogs();
      },
      error: () => { this.loading = false; }
    });
    this.auditorService.getAuditStats().subscribe({
      next: (s) => { this.stats = s; },
      error: () => {}
    });
  }

  private loadLogs(): void {
    this.auditorService.getAllLogs().subscribe({
      next: (logs) => { this.allLogs = logs; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  // ── Computed ──────────────────────────────────────────────────

  get uniqueTypes(): string[] {
    return [...new Set(this.declarations.map(d => d.declarationType?.code || '').filter(Boolean))].sort();
  }

  get uniquePeriodes(): string[] {
    return [...new Set(this.declarations.map(d => d.periode || '').filter(Boolean))].sort().reverse();
  }

  get filteredDeclarations(): Declaration[] {
    return this.declarations.filter(d => {
      if (this.config.filterStatut  && d.statut !== this.config.filterStatut) return false;
      if (this.config.filterType    && d.declarationType?.code !== this.config.filterType) return false;
      if (this.config.filterPeriode && !d.periode?.includes(this.config.filterPeriode)) return false;
      if (this.config.dateFrom) {
        const from = new Date(this.config.dateFrom);
        const gen  = d.dateGeneration ? new Date(d.dateGeneration) : null;
        if (!gen || gen < from) return false;
      }
      if (this.config.dateTo) {
        const to  = new Date(this.config.dateTo);
        const gen = d.dateGeneration ? new Date(d.dateGeneration) : null;
        if (!gen || gen > to) return false;
      }
      return true;
    });
  }

  get filteredLogs(): AuditLogDTO[] {
    const ids = new Set(this.filteredDeclarations.map(d => d.id));
    return this.allLogs.filter(l => ids.has(l.declarationId));
  }

  /** Déclarations rejetées (anomalies) */
  get rejectedDeclarations(): Declaration[] {
    return this.filteredDeclarations.filter(d => d.statut === 'REJETEE');
  }

  /** Déclarations envoyées (respect des échéances) */
  get sentDeclarations(): Declaration[] {
    return this.filteredDeclarations.filter(d => d.statut === 'ENVOYEE');
  }

  /** Activité par utilisateur : { username → { soumissions, validations, rejets } } */
  get activityByUser(): { username: string; soumissions: number; validations: number; rejets: number; total: number }[] {
    const map = new Map<string, { soumissions: number; validations: number; rejets: number }>();
    this.filteredLogs.forEach(log => {
      if (!map.has(log.effectuePar)) {
        map.set(log.effectuePar, { soumissions: 0, validations: 0, rejets: 0 });
      }
      const u = map.get(log.effectuePar)!;
      if (log.action === 'SUBMIT')   u.soumissions++;
      if (log.action === 'VALIDATE') u.validations++;
      if (log.action === 'REJECT')   u.rejets++;
    });
    return Array.from(map.entries())
      .map(([username, v]) => ({ username, ...v, total: v.soumissions + v.validations + v.rejets }))
      .sort((a, b) => b.total - a.total);
  }

  get previewCount(): { declarations: number; logs: number } {
    return {
      declarations: this.filteredDeclarations.length,
      logs:         this.filteredLogs.length,
    };
  }

  // ── Export ────────────────────────────────────────────────────

  exportReport(): void {
    this.config.format === 'csv' ? this.exportCSV() : this.exportPDF();
  }

  /** Cellule CSV RFC 4180 — guillemets toujours, retours à la ligne supprimés */
  private c(value: any): string {
    if (value === null || value === undefined) return '""';
    const s = String(value).replace(/[\r\n]+/g, ' ').replace(/"/g, '""');
    return `"${s}"`;
  }

  /** Escape HTML entities to prevent XSS in generated PDF HTML */
  private esc(v: any): string {
    if (v === null || v === undefined) return '';
    return String(v)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  /** Ligne de titre de section */
  private section(title: string, SEP: string): string {
    return `${this.c(title)}${SEP}${this.c('')}${SEP}${this.c('')}`;
  }

  /** Ligne vide */
  private empty(): string { return ''; }


  private exportCSV(): void {
    this.exporting = true;

    const BOM  = '\uFEFF';   // BOM UTF-8 — accents corrects dans Excel FR
    const SEP  = ';';        // séparateur point-virgule (standard Excel FR/TN)
    const CRLF = '\r\n';
    const now  = new Date();
    const rows: string[] = [];

    // ════════════════════════════════════════════════════════════
    // SECTION 1 — EN-TÊTE
    // ════════════════════════════════════════════════════════════
    rows.push(this.section('RAPPORT D\'AUDIT BCT — WIFAK BANK', SEP));
    rows.push(this.empty());
    rows.push([this.c('Banque'),    this.c('WIFAK BANK')].join(SEP));
    rows.push([this.c('Auditeur'),  this.c(this.config.auditeurNom || 'Non renseigné')].join(SEP));
    rows.push([this.c('Généré le'), this.c(now.toLocaleString('fr-FR'))].join(SEP));
    rows.push([this.c('Période couverte'),
      this.c((this.config.dateFrom || 'Toutes') + ' → ' + (this.config.dateTo || 'Toutes'))
    ].join(SEP));
    rows.push([this.c('Filtre statut'),  this.c(this.config.filterStatut  || 'Tous')].join(SEP));
    rows.push([this.c('Filtre type'),    this.c(this.config.filterType    || 'Tous')].join(SEP));
    rows.push([this.c('Filtre période'), this.c(this.config.filterPeriode || 'Toutes')].join(SEP));
    rows.push(this.empty());

    // ════════════════════════════════════════════════════════════
    // SECTION 2 — SYNTHÈSE GLOBALE (KPIs)
    // ════════════════════════════════════════════════════════════
    rows.push(this.section('SYNTHÈSE GLOBALE (KPIs)', SEP));
    rows.push(this.empty());
    rows.push([this.c('Indicateur'), this.c('Valeur'), this.c('Détail')].join(SEP));

    const total    = this.filteredDeclarations.length;
    const validees = this.filteredDeclarations.filter(d => ['VALIDEE','ENVOYEE'].includes(d.statut)).length;
    const rejetees = this.filteredDeclarations.filter(d => d.statut === 'REJETEE').length;
    const envoyees = this.filteredDeclarations.filter(d => d.statut === 'ENVOYEE').length;
    const enCours  = this.filteredDeclarations.filter(d => ['GENEREE','EN_VALIDATION'].includes(d.statut)).length;
    const tauxVal  = total > 0 ? Math.round((validees / total) * 100) : 0;
    const tauxRej  = total > 0 ? Math.round((rejetees / total) * 100) : 0;

    rows.push([this.c('Total déclarations'),   this.c(total),    this.c('Périmètre filtré')].join(SEP));
    rows.push([this.c('Déclarations validées'), this.c(validees), this.c('Statut VALIDEE ou ENVOYEE')].join(SEP));
    rows.push([this.c('Déclarations envoyées'), this.c(envoyees), this.c('Statut ENVOYEE (traitées)')].join(SEP));
    rows.push([this.c('Déclarations rejetées'), this.c(rejetees), this.c('Statut REJETEE')].join(SEP));
    rows.push([this.c('En cours de traitement'), this.c(enCours), this.c('Statut GENEREE ou EN_VALIDATION')].join(SEP));
    rows.push([this.c('Taux de validation'),    this.c(tauxVal + ' %'), this.c('(Validées + Envoyées) / Total')].join(SEP));
    rows.push([this.c('Taux de rejet'),         this.c(tauxRej + ' %'), this.c('Rejetées / Total')].join(SEP));
    rows.push([this.c('Total actions tracées'), this.c(this.filteredLogs.length), this.c('Journaux d\'audit')].join(SEP));
    rows.push([this.c('Soumissions'),           this.c(this.filteredLogs.filter(l => l.action === 'SUBMIT').length),   this.c('')].join(SEP));
    rows.push([this.c('Validations'),           this.c(this.filteredLogs.filter(l => l.action === 'VALIDATE').length), this.c('')].join(SEP));
    rows.push([this.c('Rejets'),                this.c(this.filteredLogs.filter(l => l.action === 'REJECT').length),   this.c('')].join(SEP));
    rows.push([this.c('Envois BCT'),            this.c(this.filteredLogs.filter(l => l.action === 'SEND').length),     this.c('')].join(SEP));
    rows.push(this.empty());

    // ════════════════════════════════════════════════════════════
    // SECTION 3 — LISTE DES DÉCLARATIONS (détaillée)
    // ════════════════════════════════════════════════════════════
    rows.push(this.section('LISTE DES DÉCLARATIONS (' + total + ' entrées)', SEP));
    rows.push(this.empty());
    rows.push([
      'ID', 'Code type', 'Nom déclaration', 'Période', 'Statut',
      'Généré par', 'Validé par',
      'Date génération', 'Date validation', 'Date envoi BCT',
      'Motif rejet',
    ].map(h => this.c(h)).join(SEP));

    this.filteredDeclarations.forEach(d => {
      rows.push([
        d.id                                                                       ?? '',
        d.declarationType?.code                                                    ?? '',
        d.declarationType?.nom                                                     ?? '',
        d.periode                                                                  ?? '',
        d.statut                                                                   ?? '',
        d.generePar                                                                ?? '',
        d.validePar                                                                ?? '',
        d.dateGeneration ? new Date(d.dateGeneration).toLocaleDateString('fr-FR')  : '',
        d.dateValidation ? new Date(d.dateValidation).toLocaleDateString('fr-FR')  : '',
        d.dateEnvoi      ? new Date(d.dateEnvoi).toLocaleDateString('fr-FR')       : '',
        d.commentaireRejet                                                         ?? '',
      ].map(v => this.c(v)).join(SEP));
    });
    rows.push(this.empty());

    // ════════════════════════════════════════════════════════════
    // SECTION 4 — JOURNAL DES ACTIONS (logs)
    // ════════════════════════════════════════════════════════════
    rows.push(this.section('JOURNAL DES ACTIONS (' + this.filteredLogs.length + ' entrées)', SEP));
    rows.push(this.empty());
    rows.push([
      'ID Log', 'Déclaration ID', 'Code type', 'Période',
      'Action', 'Statut avant', 'Statut après',
      'Effectué par', 'Date action', 'Commentaire',
    ].map(h => this.c(h)).join(SEP));

    this.filteredLogs.forEach(log => {
      rows.push([
        log.id                                                                     ?? '',
        log.declarationId                                                          ?? '',
        log.declarationCode                                                        ?? '',
        log.declarationPeriode                                                     ?? '',
        log.action                                                                 ?? '',
        log.statutAvant                                                            ?? '',
        log.statutApres                                                            ?? '',
        log.effectuePar                                                            ?? '',
        log.dateAction ? new Date(log.dateAction).toLocaleString('fr-FR')          : '',
        log.commentaire                                                            ?? '',
      ].map(v => this.c(v)).join(SEP));
    });
    rows.push(this.empty());

    // ════════════════════════════════════════════════════════════
    // SECTION 5 — ANOMALIES & REJETS
    // ════════════════════════════════════════════════════════════
    rows.push(this.section('ANOMALIES & REJETS (' + this.rejectedDeclarations.length + ' déclarations rejetées)', SEP));
    rows.push(this.empty());

    if (this.rejectedDeclarations.length === 0) {
      rows.push([this.c('Aucune anomalie détectée dans la période sélectionnée.'), this.c(''), this.c('')].join(SEP));
    } else {
      rows.push([
        'ID', 'Code type', 'Nom déclaration', 'Période',
        'Généré par', 'Rejeté par', 'Date rejet', 'Motif du rejet',
      ].map(h => this.c(h)).join(SEP));

      this.rejectedDeclarations.forEach(d => {
        const rejectLog = this.filteredLogs.find(
          l => l.declarationId === d.id && l.action === 'REJECT'
        );
        rows.push([
          d.id                                                                     ?? '',
          d.declarationType?.code                                                  ?? '',
          d.declarationType?.nom                                                   ?? '',
          d.periode                                                                ?? '',
          d.generePar                                                              ?? '',
          d.validePar                                                              ?? '',
          rejectLog?.dateAction
            ? new Date(rejectLog.dateAction).toLocaleDateString('fr-FR')
            : (d.dateValidation ? new Date(d.dateValidation).toLocaleDateString('fr-FR') : ''),
          d.commentaireRejet                                                       ?? '',
        ].map(v => this.c(v)).join(SEP));
      });
    }
    rows.push(this.empty());

    // ════════════════════════════════════════════════════════════
    // SECTION 6 — RESPECT DES ÉCHÉANCES
    // ════════════════════════════════════════════════════════════
    rows.push(this.section('RESPECT DES ÉCHÉANCES', SEP));
    rows.push(this.empty());
    rows.push([
      'Indicateur', 'Valeur', 'Détail',
    ].map(h => this.c(h)).join(SEP));

    rows.push([
      this.c('Déclarations envoyées à la BCT'),
      this.c(this.sentDeclarations.length),
      this.c('Statut ENVOYEE'),
    ].join(SEP));
    rows.push([
      this.c('Déclarations en attente d\'envoi'),
      this.c(this.filteredDeclarations.filter(d => d.statut === 'VALIDEE').length),
      this.c('Validées mais non encore envoyées'),
    ].join(SEP));
    rows.push([
      this.c('Déclarations non finalisées'),
      this.c(enCours),
      this.c('GENEREE ou EN_VALIDATION'),
    ].join(SEP));
    rows.push(this.empty());

    if (this.sentDeclarations.length > 0) {
      rows.push([this.c('Détail des déclarations envoyées :'), this.c(''), this.c('')].join(SEP));
      rows.push([
        'ID', 'Code type', 'Période', 'Généré par', 'Validé par', 'Date envoi BCT',
      ].map(h => this.c(h)).join(SEP));

      this.sentDeclarations.forEach(d => {
        rows.push([
          d.id                                                                     ?? '',
          d.declarationType?.code                                                  ?? '',
          d.periode                                                                ?? '',
          d.generePar                                                              ?? '',
          d.validePar                                                              ?? '',
          d.dateEnvoi ? new Date(d.dateEnvoi).toLocaleDateString('fr-FR')          : '',
        ].map(v => this.c(v)).join(SEP));
      });
    }
    rows.push(this.empty());

    // ════════════════════════════════════════════════════════════
    // SECTION 7 — ACTIVITÉ PAR UTILISATEUR
    // ════════════════════════════════════════════════════════════
    rows.push(this.section('ACTIVITÉ PAR UTILISATEUR', SEP));
    rows.push(this.empty());
    rows.push([
      'Utilisateur', 'Soumissions', 'Validations', 'Rejets', 'Total actions',
    ].map(h => this.c(h)).join(SEP));

    if (this.activityByUser.length === 0) {
      rows.push([this.c('Aucune activité dans la période sélectionnée.'), this.c(''), this.c(''), this.c(''), this.c('')].join(SEP));
    } else {
      this.activityByUser.forEach(u => {
        rows.push([
          u.username,
          u.soumissions,
          u.validations,
          u.rejets,
          u.total,
        ].map(v => this.c(v)).join(SEP));
      });
    }
    rows.push(this.empty());

    // ════════════════════════════════════════════════════════════
    // SECTION 8 — CONCLUSION DE L'AUDITEUR
    // ════════════════════════════════════════════════════════════
    rows.push(this.section('CONCLUSION DE L\'AUDITEUR', SEP));
    rows.push(this.empty());
    rows.push([this.c('Auditeur'),    this.c(this.config.auditeurNom || 'Non renseigné')].join(SEP));
    rows.push([this.c('Date'),        this.c(now.toLocaleDateString('fr-FR'))].join(SEP));
    rows.push([this.c('Rapport généré par'), this.c('Système d\'audit BCT — Wifak Bank')].join(SEP));
    rows.push(this.empty());
    rows.push([this.c('Résumé exécutif :'), this.c(''), this.c('')].join(SEP));
    rows.push([
      this.c('Sur ' + total + ' déclarations analysées, ' +
        validees + ' ont été validées (' + tauxVal + '%), ' +
        envoyees + ' traitées, et ' +
        rejetees + ' ont été rejetées (' + tauxRej + '%). ' +
        this.activityByUser.length + ' utilisateur(s) ont effectué des actions sur la plateforme.'),
      this.c(''), this.c(''),
    ].join(SEP));
    rows.push(this.empty());
    rows.push([this.c('--- Fin du rapport d\'audit BCT ---'), this.c(''), this.c('')].join(SEP));

    // ── Téléchargement ────────────────────────────────────────
    const content = BOM + rows.join(CRLF);
    const blob    = new Blob([content], { type: 'text/csv;charset=utf-8;' });
    const url     = URL.createObjectURL(blob);
    const a       = document.createElement('a');
    a.href        = url;
    a.download    = `rapport_audit_BCT_${now.toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    this.exporting = false;
  }


  private exportPDF(): void {
    this.exporting = true;
    const now      = new Date();
    const nowStr   = now.toLocaleString('fr-FR');
    const dateStr  = now.toLocaleDateString('fr-FR');

    const total    = this.filteredDeclarations.length;
    const validees = this.filteredDeclarations.filter(d => ['VALIDEE', 'ENVOYEE'].includes(d.statut)).length;
    const rejetees = this.filteredDeclarations.filter(d => d.statut === 'REJETEE').length;
    const envoyees = this.filteredDeclarations.filter(d => d.statut === 'ENVOYEE').length;
    const enCours  = this.filteredDeclarations.filter(d => ['GENEREE', 'EN_VALIDATION'].includes(d.statut)).length;
    const tauxVal  = total > 0 ? Math.round((validees / total) * 100) : 0;
    const tauxRej  = total > 0 ? Math.round((rejetees / total) * 100) : 0;
    const totalLogs = this.filteredLogs.length;
    const nbSoumissions = this.filteredLogs.filter(l => l.action === 'SUBMIT').length;
    const nbValidations = this.filteredLogs.filter(l => l.action === 'VALIDATE').length;
    const nbRejets      = this.filteredLogs.filter(l => l.action === 'REJECT').length;
    const nbEnvois      = this.filteredLogs.filter(l => l.action === 'SEND').length;

    const auditeur = this.esc(this.config.auditeurNom || 'Non renseigné');
    const periode  = this.esc(
      (this.config.dateFrom || 'Toutes') + ' → ' + (this.config.dateTo || 'Toutes')
    );
    const filtreStatut  = this.esc(this.config.filterStatut  || 'Tous');
    const filtreType    = this.esc(this.config.filterType    || 'Tous');
    const filtrePeriode = this.esc(this.config.filterPeriode || 'Toutes');

    // ── Helper: statut badge ──────────────────────────────────
    const statutBadge = (s: string): string => {
      const colors: Record<string, string> = {
        'VALIDEE':       '#0A7A5A',
        'REJETEE':       '#C8192E',
        'EN_VALIDATION': '#B8651A',
        'ENVOYEE':       '#5B35C2',
        'GENEREE':       '#1B4FBC',
      };
      const bg = colors[s] || '#6c757d';
      return `<span style="background:${bg};color:#fff;padding:2px 8px;border-radius:10px;font-size:11px;font-weight:600;white-space:nowrap;">${this.esc(s)}</span>`;
    };

    // ── Helper: action badge ──────────────────────────────────
    const actionBadge = (a: string): string => {
      const colors: Record<string, string> = {
        'SUBMIT':   '#1B4FBC',
        'VALIDATE': '#0A7A5A',
        'REJECT':   '#C8192E',
        'SEND':     '#5B35C2',
      };
      const bg = colors[a] || '#6c757d';
      return `<span style="background:${bg};color:#fff;padding:2px 8px;border-radius:10px;font-size:11px;font-weight:600;white-space:nowrap;">${this.esc(a)}</span>`;
    };

    // ── Helper: section title bar ─────────────────────────────
    const sectionTitle = (num: string, title: string): string =>
      `<div style="background:#0D2B5E;color:#fff;padding:10px 16px;margin:24px 0 12px 0;border-radius:4px;font-size:14px;font-weight:700;letter-spacing:0.5px;">
        <span style="opacity:0.7;margin-right:8px;">${this.esc(num)}</span>${this.esc(title)}
      </div>`;

    // ── Helper: table header row ──────────────────────────────
    const th = (cols: string[]): string =>
      `<tr>${cols.map(c => `<th style="background:#0D2B5E;color:#fff;padding:8px 10px;text-align:left;font-size:12px;border:1px solid #dee2e6;">${c}</th>`).join('')}</tr>`;

    // ── Helper: table data row ────────────────────────────────
    const tr = (cells: string[], idx: number): string => {
      const bg = idx % 2 === 0 ? '#f8f9fa' : '#ffffff';
      return `<tr style="background:${bg};">${cells.map(c => `<td style="padding:7px 10px;font-size:12px;border:1px solid #dee2e6;vertical-align:top;">${c}</td>`).join('')}</tr>`;
    };

    // ── Helper: KPI card ──────────────────────────────────────
    const kpiCard = (label: string, value: string | number, color: string): string =>
      `<div style="background:${color};color:#fff;border-radius:8px;padding:16px 12px;text-align:center;min-width:120px;">
        <div style="font-size:28px;font-weight:700;line-height:1.1;">${this.esc(String(value))}</div>
        <div style="font-size:11px;margin-top:4px;opacity:0.9;">${this.esc(label)}</div>
      </div>`;

    // ── Helper: progress bar ──────────────────────────────────
    const progressBar = (label: string, pct: number, color: string): string =>
      `<div style="margin:8px 0;">
        <div style="display:flex;justify-content:space-between;font-size:12px;margin-bottom:3px;">
          <span>${this.esc(label)}</span><span style="font-weight:700;">${pct}%</span>
        </div>
        <div style="background:#e9ecef;border-radius:4px;height:12px;overflow:hidden;">
          <div style="background:${color};width:${pct}%;height:100%;border-radius:4px;"></div>
        </div>
      </div>`;

    // ════════════════════════════════════════════════════════════
    // SECTION 1 — EN-TÊTE INFO GRID
    // ════════════════════════════════════════════════════════════
    const infoRow = (label: string, value: string): string =>
      `<div style="padding:8px 12px;border-bottom:1px solid #e9ecef;display:flex;gap:12px;">
        <span style="font-weight:600;color:#0D2B5E;min-width:160px;font-size:12px;">${this.esc(label)}</span>
        <span style="font-size:12px;color:#333;">${value}</span>
      </div>`;

    const section1 = `
      ${sectionTitle('1.', 'En-tête du rapport')}
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:0;border:1px solid #dee2e6;border-radius:4px;overflow:hidden;">
        <div>
          ${infoRow('Banque', 'WIFAK BANK')}
          ${infoRow('Auditeur', auditeur)}
          ${infoRow('Date de génération', this.esc(nowStr))}
          ${infoRow('Période couverte', periode)}
        </div>
        <div>
          ${infoRow('Filtre statut', filtreStatut)}
          ${infoRow('Filtre type', filtreType)}
          ${infoRow('Filtre période', filtrePeriode)}
          ${infoRow('Déclarations analysées', String(total))}
        </div>
      </div>`;

    // ════════════════════════════════════════════════════════════
    // SECTION 2 — SYNTHÈSE GLOBALE (KPIs)
    // ════════════════════════════════════════════════════════════
    const section2 = `
      ${sectionTitle('2.', 'Synthèse globale (KPIs)')}
      <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin-bottom:16px;">
        ${kpiCard('Total déclarations', total, '#0D2B5E')}
        ${kpiCard('Validées', validees, '#0A7A5A')}
        ${kpiCard('Envoyées BCT', envoyees, '#5B35C2')}
        ${kpiCard('Rejetées', rejetees, '#C8192E')}
        ${kpiCard('En cours', enCours, '#B8651A')}
        ${kpiCard('Actions tracées', totalLogs, '#1B4FBC')}
      </div>
      <div style="margin-bottom:16px;">
        ${progressBar('Taux de validation', tauxVal, '#0A7A5A')}
        ${progressBar('Taux de rejet', tauxRej, '#C8192E')}
      </div>
      <table style="width:100%;border-collapse:collapse;">
        ${th(['Type d\'action', 'Nombre'])}
        ${tr([`Soumissions`, String(nbSoumissions)], 0)}
        ${tr([`Validations`, String(nbValidations)], 1)}
        ${tr([`Rejets`, String(nbRejets)], 2)}
        ${tr([`Envois BCT`, String(nbEnvois)], 3)}
      </table>`;

    // ════════════════════════════════════════════════════════════
    // SECTION 3 — LISTE DES DÉCLARATIONS
    // ════════════════════════════════════════════════════════════
    const declRows = this.filteredDeclarations.map((d, i) =>
      tr([
        this.esc(d.id),
        this.esc(d.declarationType?.code),
        this.esc(d.declarationType?.nom),
        this.esc(d.periode),
        statutBadge(d.statut || ''),
        this.esc(d.generePar),
        this.esc(d.validePar),
        d.dateGeneration ? this.esc(new Date(d.dateGeneration).toLocaleDateString('fr-FR')) : '—',
        d.dateValidation ? this.esc(new Date(d.dateValidation).toLocaleDateString('fr-FR')) : '—',
      ], i)
    ).join('');

    const section3 = `
      ${sectionTitle('3.', `Liste des déclarations (${total} entrées)`)}
      <table style="width:100%;border-collapse:collapse;">
        ${th(['ID', 'Code', 'Nom', 'Période', 'Statut', 'Généré par', 'Validé par', 'Date génération', 'Date validation'])}
        ${declRows || tr(['<em style="color:#888;">Aucune déclaration</em>', '', '', '', '', '', '', '', ''], 0)}
      </table>`;

    // ════════════════════════════════════════════════════════════
    // SECTION 4 — JOURNAL DES ACTIONS
    // ════════════════════════════════════════════════════════════
    const logRows = this.filteredLogs.map((log, i) =>
      tr([
        this.esc(log.id),
        this.esc(log.declarationId),
        this.esc(log.declarationCode),
        actionBadge(log.action || ''),
        `${this.esc(log.statutAvant)} → ${this.esc(log.statutApres)}`,
        this.esc(log.effectuePar),
        log.dateAction ? this.esc(new Date(log.dateAction).toLocaleString('fr-FR')) : '—',
        this.esc(log.commentaire),
      ], i)
    ).join('');

    const section4 = `
      ${sectionTitle('4.', `Journal des actions (${totalLogs} entrées)`)}
      <table style="width:100%;border-collapse:collapse;">
        ${th(['ID Log', 'Décl. ID', 'Code', 'Action', 'Statut avant→après', 'Effectué par', 'Date action', 'Commentaire'])}
        ${logRows || tr(['<em style="color:#888;">Aucun log</em>', '', '', '', '', '', '', ''], 0)}
      </table>`;

    // ════════════════════════════════════════════════════════════
    // SECTION 5 — ANOMALIES & REJETS
    // ════════════════════════════════════════════════════════════
    let section5Body: string;
    if (this.rejectedDeclarations.length === 0) {
      section5Body = `<div style="background:#d4edda;color:#155724;border:1px solid #c3e6cb;border-radius:4px;padding:14px 16px;font-size:13px;">
        ✅ Aucune anomalie détectée dans la période sélectionnée.
      </div>`;
    } else {
      const rejRows = this.rejectedDeclarations.map((d, i) => {
        const rejectLog = this.filteredLogs.find(l => l.declarationId === d.id && l.action === 'REJECT');
        const dateRejet = rejectLog?.dateAction
          ? new Date(rejectLog.dateAction).toLocaleDateString('fr-FR')
          : (d.dateValidation ? new Date(d.dateValidation).toLocaleDateString('fr-FR') : '—');
        return tr([
          this.esc(d.id),
          this.esc(d.declarationType?.code),
          this.esc(d.declarationType?.nom),
          this.esc(d.periode),
          this.esc(d.generePar),
          this.esc(d.validePar),
          this.esc(dateRejet),
          `<span style="color:#C8192E;font-weight:600;">${this.esc(d.commentaireRejet)}</span>`,
        ], i);
      }).join('');
      section5Body = `<table style="width:100%;border-collapse:collapse;">
        ${th(['ID', 'Code', 'Nom', 'Période', 'Généré par', 'Rejeté par', 'Date rejet', 'Motif'])}
        ${rejRows}
      </table>`;
    }

    const section5 = `
      ${sectionTitle('5.', `Anomalies & rejets (${this.rejectedDeclarations.length} déclarations rejetées)`)}
      ${section5Body}`;

    // ════════════════════════════════════════════════════════════
    // SECTION 6 — RESPECT DES ÉCHÉANCES
    // ════════════════════════════════════════════════════════════
    const enAttenteEnvoi = this.filteredDeclarations.filter(d => d.statut === 'VALIDEE').length;

    const echeanceCard = (label: string, value: number, color: string): string =>
      `<div style="background:${color};color:#fff;border-radius:8px;padding:14px 12px;text-align:center;flex:1;">
        <div style="font-size:24px;font-weight:700;">${value}</div>
        <div style="font-size:11px;margin-top:4px;opacity:0.9;">${this.esc(label)}</div>
      </div>`;

    const sentRows = this.sentDeclarations.map((d, i) =>
      tr([
        this.esc(d.id),
        this.esc(d.declarationType?.code),
        this.esc(d.periode),
        this.esc(d.generePar),
        this.esc(d.validePar),
        d.dateEnvoi ? this.esc(new Date(d.dateEnvoi).toLocaleDateString('fr-FR')) : '—',
      ], i)
    ).join('');

    const section6 = `
      ${sectionTitle('6.', 'Respect des échéances')}
      <div style="display:flex;gap:12px;margin-bottom:16px;">
        ${echeanceCard('Envoyées BCT', envoyees, '#5B35C2')}
        ${echeanceCard('En attente d\'envoi', enAttenteEnvoi, '#B8651A')}
        ${echeanceCard('Non finalisées', enCours, '#6c757d')}
      </div>
      <table style="width:100%;border-collapse:collapse;">
        ${th(['ID', 'Code', 'Période', 'Généré par', 'Validé par', 'Date envoi BCT'])}
        ${sentRows || tr(['<em style="color:#888;">Aucune déclaration envoyée</em>', '', '', '', '', ''], 0)}
      </table>`;

    // ════════════════════════════════════════════════════════════
    // SECTION 7 — ACTIVITÉ PAR UTILISATEUR
    // ════════════════════════════════════════════════════════════
    const maxTotal = this.activityByUser.reduce((m, u) => Math.max(m, u.total), 1);

    const userRows = this.activityByUser.map((u, i) => {
      const pct = Math.round((u.total / maxTotal) * 100);
      const bar = `<div style="background:#e9ecef;border-radius:3px;height:8px;margin-top:4px;overflow:hidden;">
        <div style="background:#0D2B5E;width:${pct}%;height:100%;border-radius:3px;"></div>
      </div>`;
      return tr([
        this.esc(u.username),
        String(u.soumissions),
        String(u.validations),
        String(u.rejets),
        `<div>${u.total}${bar}</div>`,
      ], i);
    }).join('');

    const section7 = `
      ${sectionTitle('7.', 'Activité par utilisateur')}
      <table style="width:100%;border-collapse:collapse;">
        ${th(['Utilisateur', 'Soumissions', 'Validations', 'Rejets', 'Total'])}
        ${userRows || tr(['<em style="color:#888;">Aucune activité</em>', '', '', '', ''], 0)}
      </table>`;

    // ════════════════════════════════════════════════════════════
    // SECTION 8 — CONCLUSION DE L'AUDITEUR
    // ════════════════════════════════════════════════════════════
    const resume = `Sur ${total} déclarations analysées, ${validees} ont été validées (${tauxVal}%), ` +
      `${envoyees} traitées, et ${rejetees} ont été rejetées (${tauxRej}%). ` +
      `${this.activityByUser.length} utilisateur(s) ont effectué des actions sur la plateforme.`;

    const section8 = `
      ${sectionTitle('8.', 'Conclusion de l\'auditeur')}
      <div style="background:#f8f9fa;border:1px solid #dee2e6;border-radius:4px;padding:16px;margin-bottom:20px;">
        <p style="font-size:13px;line-height:1.7;color:#333;margin:0 0 12px 0;">${this.esc(resume)}</p>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-top:16px;">
          <div>
            <div style="font-size:11px;color:#888;margin-bottom:4px;">Auditeur</div>
            <div style="font-size:13px;font-weight:600;color:#0D2B5E;">${auditeur}</div>
          </div>
          <div>
            <div style="font-size:11px;color:#888;margin-bottom:4px;">Date</div>
            <div style="font-size:13px;font-weight:600;color:#0D2B5E;">${this.esc(dateStr)}</div>
          </div>
        </div>
        <div style="margin-top:24px;border-top:1px solid #dee2e6;padding-top:16px;">
          <div style="font-size:11px;color:#888;margin-bottom:8px;">Signature de l'auditeur</div>
          <div style="border-bottom:1px solid #333;width:240px;height:40px;"></div>
        </div>
      </div>`;

    // ════════════════════════════════════════════════════════════
    // FULL HTML DOCUMENT
    // ════════════════════════════════════════════════════════════
    const html = `<!DOCTYPE html>
<html lang="fr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Rapport d'Audit BCT — WIFAK BANK</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: Arial, Helvetica, sans-serif;
      font-size: 13px;
      color: #212529;
      background: #fff;
      padding: 20mm;
    }
    @media print {
      @page { size: A4; margin: 15mm; }
      body { padding: 0; }
      .no-print { display: none !important; }
    }
    /* Footer via CSS counter */
    @media print {
      body { counter-reset: page; }
      .page-footer::after {
        counter-increment: page;
        content: "Page " counter(page);
      }
    }
    .page-footer {
      position: fixed;
      bottom: 0;
      left: 0;
      right: 0;
      background: #f8f9fa;
      border-top: 1px solid #dee2e6;
      padding: 6px 20mm;
      font-size: 10px;
      color: #6c757d;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
  </style>
</head>
<body>
  <!-- HEADER -->
  <div style="background:#0D2B5E;color:#fff;padding:24px 28px;border-radius:6px;margin-bottom:24px;">
    <div style="display:flex;justify-content:space-between;align-items:flex-start;">
      <div>
        <div style="font-size:22px;font-weight:700;letter-spacing:1px;">RAPPORT D'AUDIT BCT</div>
        <div style="font-size:14px;opacity:0.85;margin-top:4px;">WIFAK BANK</div>
      </div>
      <div style="text-align:right;font-size:12px;opacity:0.85;">
        <div><strong>Auditeur :</strong> ${auditeur}</div>
        <div><strong>Généré le :</strong> ${this.esc(nowStr)}</div>
        <div><strong>Période :</strong> ${periode}</div>
      </div>
    </div>
  </div>

  <!-- SECTIONS -->
  ${section1}
  ${section2}
  ${section3}
  ${section4}
  ${section5}
  ${section6}
  ${section7}
  ${section8}

  <!-- FOOTER -->
  <div class="page-footer">
    <span>Rapport d'Audit BCT — WIFAK BANK — Confidentiel</span>
    <span class="page-footer"></span>
  </div>
</body>
</html>`;

    // ── Open new window and print ─────────────────────────────
    const win = window.open('', '_blank');
    if (win) {
      win.document.write(html);
      win.document.close();
      setTimeout(() => {
        win.print();
        win.close();
      }, 500);
    }
    this.exporting = false;
  }

  getStatutLabel(statut: string): string {
    const m: Record<string, string> = {
      'GENEREE':       'Générée',
      'EN_VALIDATION': 'En validation',
      'VALIDEE':       'Validée',
      'REJETEE':       'Rejetée',
      'ENVOYEE': 'Traitée',
    };
    return m[statut] || statut;
  }
}
