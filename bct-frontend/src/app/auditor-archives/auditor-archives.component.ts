// src/app/auditor-archives/auditor-archives.component.ts
// BF8 / US-15 — Consultation des archives (lecture seule)
import { Component, OnInit } from '@angular/core';
import { DeclarationService, Declaration } from '../services/Declaration.service';

@Component({
  selector: 'app-auditor-archives',
  templateUrl: './auditor-archives.component.html',
  styleUrls: ['./auditor-archives.component.scss']
})
export class AuditorArchivesComponent implements OnInit {

  loading = false;
  allDeclarations: Declaration[] = [];
  selectedDeclaration: Declaration | null = null;

  filterPeriode = '';
  filterType    = '';
  filterStatut  = '';

  constructor(private declarationService: DeclarationService) {}

  ngOnInit(): void { this.charger(); }

  charger(): void {
    this.loading = true;
    this.declarationService.getAllDeclarations().subscribe({
      next: (data) => {
        // Archives = toutes les déclarations envoyées + validées (long terme)
        this.allDeclarations = data.sort((a, b) => (b.id ?? 0) - (a.id ?? 0));
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  get archives(): Declaration[] {
    return this.allDeclarations.filter(d =>
      ['VALIDEE', 'ENVOYEE'].includes(d.statut)
    );
  }

  get filteredArchives(): Declaration[] {
    return this.archives.filter(d => {
      if (this.filterPeriode && !d.periode?.includes(this.filterPeriode)) return false;
      if (this.filterType    && d.declarationType?.code !== this.filterType) return false;
      if (this.filterStatut  && d.statut !== this.filterStatut) return false;
      return true;
    });
  }

  get uniquePeriodes(): string[] {
    return [...new Set(this.archives.map(d => d.periode || '').filter(Boolean))].sort().reverse();
  }

  get uniqueTypes(): string[] {
    return [...new Set(this.archives.map(d => d.declarationType?.code || '').filter(Boolean))].sort();
  }

  // Grouper par période pour affichage chronologique
  get groupedByPeriode(): { periode: string; declarations: Declaration[] }[] {
    const map = new Map<string, Declaration[]>();
    this.filteredArchives.forEach(d => {
      const key = d.periode || 'Sans période';
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(d);
    });
    return Array.from(map.entries())
      .sort((a, b) => b[0].localeCompare(a[0]))
      .map(([periode, declarations]) => ({ periode, declarations }));
  }

  selectDeclaration(d: Declaration): void {
    this.selectedDeclaration = this.selectedDeclaration?.id === d.id ? null : d;
  }

  exportCSV(): void {
    const rows = ['ID,Type,Code,Période,Statut,Généré par,Validé par,Date génération,Date validation,Date envoi'];
    this.filteredArchives.forEach(d => {
      rows.push([
        d.id,
        `"${d.declarationType?.nom || ''}"`,
        d.declarationType?.code || '',
        d.periode || '',
        d.statut,
        d.generePar || '',
        d.validePar || '',
        d.dateGeneration ? new Date(d.dateGeneration).toLocaleDateString('fr-FR') : '',
        d.dateValidation ? new Date(d.dateValidation).toLocaleDateString('fr-FR') : '',
        d.dateEnvoi      ? new Date(d.dateEnvoi).toLocaleDateString('fr-FR')      : '',
      ].join(','));
    });
    const blob = new Blob([rows.join('\n')], { type: 'text/csv;charset=utf-8;' });
    const url  = URL.createObjectURL(blob);
    const a    = document.createElement('a');
    a.href     = url;
    a.download = `archives_${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  getBadgeClass(statut: string): string {
    return statut === 'ENVOYEE' ? 'badge-g' : 'badge-v';
  }

  getStatutLabel(statut: string): string {
    return statut === 'ENVOYEE' ? 'Envoyée BCT' : 'Validée';
  }

  onFilterChange(): void {}
}
