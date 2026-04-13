// src/app/manager-reports/manager-reports.component.ts
// US-19 — Dashboard de synthèse + statistiques pour le Responsable

import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { DeclarationService, Declaration, DeclarationStats } from '../services/Declaration.service';
import { DeclarationTypeService, DeclarationType } from '../services/declaration-type.service';

export interface PeriodeStat {
  periode: string;
  total: number;
  validees: number;
  rejetees: number;
  envoyees: number;
  tauxValidation: number;
}

@Component({
  selector: 'app-manager-reports',
  templateUrl: './manager-reports.component.html',
  styleUrls: ['./manager-reports.component.scss']
})
export class ManagerReportsComponent implements OnInit {

  loading = false;
  declarations: Declaration[] = [];
  declarationTypes: DeclarationType[] = [];
  stats: DeclarationStats | null = null;

  // Filtres
  filtreStatut = '';
  filtrePeriode = '';
  filtreType = '';

  get filteredDeclarations(): Declaration[] {
    return this.declarations.filter(d => {
      if (this.filtreStatut  && d.statut !== this.filtreStatut) return false;
      if (this.filtrePeriode && !d.periode?.includes(this.filtrePeriode)) return false;
      if (this.filtreType    && d.declarationType?.code !== this.filtreType) return false;
      return true;
    });
  }

  // Stats par période
  get periodeStats(): PeriodeStat[] {
    const map = new Map<string, Declaration[]>();
    this.declarations.forEach(d => {
      const p = d.periode || 'N/A';
      if (!map.has(p)) map.set(p, []);
      map.get(p)!.push(d);
    });
    const result: PeriodeStat[] = [];
    map.forEach((decls, periode) => {
      const total    = decls.length;
      const validees = decls.filter(d => d.statut === 'VALIDEE').length;
      const rejetees = decls.filter(d => d.statut === 'REJETEE').length;
      const envoyees = decls.filter(d => d.statut === 'ENVOYEE').length;
      result.push({
        periode, total, validees, rejetees, envoyees,
        tauxValidation: total ? Math.round((validees / total) * 100) : 0
      });
    });
    return result.sort((a, b) => b.periode.localeCompare(a.periode)).slice(0, 8);
  }

  // Stats par type
  get typeStats() {
    const map = new Map<string, { total: number; validees: number; rejetees: number; nom: string }>();
    this.declarations.forEach(d => {
      const code = d.declarationType?.code || 'N/A';
      const nom  = d.declarationType?.nom  || code;
      if (!map.has(code)) map.set(code, { total: 0, validees: 0, rejetees: 0, nom });
      const entry = map.get(code)!;
      entry.total++;
      if (d.statut === 'VALIDEE' || d.statut === 'ENVOYEE') entry.validees++;
      if (d.statut === 'REJETEE') entry.rejetees++;
    });
    return Array.from(map.entries())
      .map(([code, v]) => ({ code, ...v, taux: v.total ? Math.round((v.validees / v.total) * 100) : 0 }))
      .sort((a, b) => b.total - a.total)
      .slice(0, 8);
  }

  get maxTypeTotal(): number {
    const stats = this.typeStats;
    return stats.length ? Math.max(...stats.map(s => s.total)) : 1;
  }

  get uniquePeriodes(): string[] {
    return [...new Set(this.declarations.map(d => d.periode || '').filter(Boolean))].sort().reverse();
  }

  get uniqueTypes(): string[] {
    return [...new Set(this.declarations.map(d => d.declarationType?.code || '').filter(Boolean))];
  }

  // Taux de validation global
  get tauxValidationGlobal(): number {
    if (!this.declarations.length) return 0;
    const validees = this.declarations.filter(d => ['VALIDEE', 'ENVOYEE'].includes(d.statut)).length;
    return Math.round((validees / this.declarations.length) * 100);
  }

  // Délai moyen (simulation en jours)
  get delaiMoyen(): number { return 2; }

  constructor(
    private declarationService: DeclarationService,
    private declarationTypeService: DeclarationTypeService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.loading = true;
    this.declarationService.getAllDeclarations().subscribe({
      next: (data) => {
        this.declarations = data;
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
    this.declarationService.getStats().subscribe({
      next: (s) => this.stats = s,
      error: () => {}
    });
    this.declarationTypeService.getAll().subscribe({
      next: (t) => this.declarationTypes = t,
      error: () => {}
    });
  }

  voirDeclaration(d: Declaration): void {
    this.router.navigate(['/manager/dashboard'], {
      queryParams: { highlight: d.id }
    });
  }

  getStatutClass(statut: string): string {
    const map: Record<string, string> = {
      'GENEREE': 'sc-generee', 'EN_VALIDATION': 'sc-validation',
      'VALIDEE': 'sc-validee', 'REJETEE': 'sc-rejetee', 'ENVOYEE': 'sc-envoyee',
    };
    return map[statut] || '';
  }

  getStatutLabel(statut: string): string {
    const map: Record<string, string> = {
      'GENEREE': 'Générée', 'EN_VALIDATION': 'En validation',
      'VALIDEE': 'Validée', 'REJETEE': 'Rejetée', 'ENVOYEE': 'Envoyée',
    };
    return map[statut] || statut;
  }

  getBarWidth(val: number, max: number): number {
    return max ? Math.round((val / max) * 100) : 0;
  }
}