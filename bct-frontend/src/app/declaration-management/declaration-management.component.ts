import { Component, OnInit } from '@angular/core';
import { DeclarationService, Declaration, GenerateDeclarationRequest } from '../services/Declaration.service';
import { DeclarationTypeService, DeclarationType } from '../services/declaration-type.service';

@Component({
  selector: 'app-declaration-management',
  templateUrl: './declaration-management.component.html',
  styleUrls: ['./declaration-management.component.scss']
})
export class DeclarationManagementComponent implements OnInit {

  declarations: Declaration[] = [];
  declarationTypes: DeclarationType[] = [];

  loadingDeclarations: boolean = false;
  loadingTypes: boolean = false;
  loadingAction: boolean = false;

  sidebarCollapsed: boolean = false;
  searchQuery: string = '';
  filterStatut: string = 'ALL';
  filterPeriode: string = '';

  currentPage: number = 1;
  itemsPerPage: number = 10;

  showGenerateModal: boolean = false;
  showDetailsModal: boolean = false;
  showConfigModal: boolean = false;
  formSubmitted: boolean = false;

  selectedDeclarationType: DeclarationType | null = null;
  generateRequest: GenerateDeclarationRequest = {
    declarationTypeId: 0,
    periode: '',
    dateDebut: '',
    dateFin: ''
  };

  selectedDeclaration: Declaration | null = null;

  configDeclarationType: DeclarationType | null = null;
  xsdFile: File | null = null;
  uploadingXsd: boolean = false;
  sqlQuery: string = '';
  savingSQL: boolean = false;
  testDateDebut: string = '';
  testDateFin: string = '';
  testingSQL: boolean = false;
  sqlTestResult: any = null;

  constructor(
    private declarationService: DeclarationService,
    private declarationTypeService: DeclarationTypeService
  ) {}

  ngOnInit(): void {
    this.loadDeclarations();
    this.loadDeclarationTypes();
  }

  // ========== LOAD ==========

  loadDeclarations(): void {
    this.loadingDeclarations = true;
    this.declarationService.getMyDeclarations().subscribe({
      next: (data) => {
        console.log('✅ Déclarations reçues:', data.length, data);
        this.declarations = data;
        this.loadingDeclarations = false;
      },
      error: (err) => {
        console.error('❌ Erreur chargement déclarations:', err);
        this.declarations = [];
        this.loadingDeclarations = false;
      }
    });
  }

  loadDeclarationTypes(): void {
    this.loadingTypes = true;
    this.declarationTypeService.getAll().subscribe({
      next: (types) => {
        this.declarationTypes = types.filter(t => t.actif);
        this.loadingTypes = false;
      },
      error: (err) => {
        console.error('❌ Erreur chargement types:', err);
        this.loadingTypes = false;
      }
    });
  }

  // ========== FILTRES ==========

  // ✅ Méthode appelée directement dans le template *ngIf sur chaque ligne
  matchesFilters(declaration: Declaration): boolean {
    // Filtre statut
    if (this.filterStatut !== 'ALL' && declaration.statut !== this.filterStatut) {
      return false;
    }
    // Filtre période
    if (this.filterPeriode && declaration.periode) {
      if (!declaration.periode.includes(this.filterPeriode)) return false;
    }
    // Recherche texte
    if (this.searchQuery && this.searchQuery.trim() !== '') {
      const q = this.searchQuery.toLowerCase().trim();
      const inCode = declaration.declarationType?.code?.toLowerCase().includes(q) ?? false;
      const inNom  = declaration.declarationType?.nom?.toLowerCase().includes(q) ?? false;
      const inPer  = declaration.periode?.toLowerCase().includes(q) ?? false;
      const inStat = declaration.statut?.toLowerCase().includes(q) ?? false;
      if (!inCode && !inNom && !inPer && !inStat) return false;
    }
    return true;
  }

  applyFilters(): void {
    this.currentPage = 1;
  }

  clearFilters(): void {
    this.filterStatut = 'ALL';
    this.filterPeriode = '';
    this.searchQuery = '';
    this.currentPage = 1;
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.currentPage = 1;
  }

  // ========== STATS ==========

  getStatsByStatus(statut: string): number {
    return this.declarations.filter(d => d.statut === statut).length;
  }

  // ========== GENERATE MODAL ==========

  openGenerateModal(): void {
    this.formSubmitted = false;
    this.selectedDeclarationType = null;
    this.generateRequest = { declarationTypeId: 0, periode: '', dateDebut: '', dateFin: '' };
    this.showGenerateModal = true;
  }

  closeGenerateModal(): void {
    this.showGenerateModal = false;
    this.formSubmitted = false;
  }

  onDeclarationTypeChange(): void {
    const id = +this.generateRequest.declarationTypeId;
    this.selectedDeclarationType = this.declarationTypes.find(t => t.id === id) || null;
  }

  onPeriodeChange(): void {
    if (this.generateRequest.periode) {
      const [year, month] = this.generateRequest.periode.split('-').map(Number);
      const dateDebut = new Date(year, month - 1, 1);
      const dateFin   = new Date(year, month, 0);
      this.generateRequest.dateDebut = this.formatDateToString(dateDebut);
      this.generateRequest.dateFin   = this.formatDateToString(dateFin);
    }
  }

  private formatDateToString(date: Date): string {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }

  hasSqlConfigured(type: DeclarationType): boolean {
    return !!type.sqlQuery && type.sqlQuery.trim().length > 0;
  }

  hasXsdConfigured(type: DeclarationType): boolean {
    return !!type.xsdFileName && type.xsdFileName.trim().length > 0;
  }

  generateDeclaration(): void {
    this.formSubmitted = true;

    if (!this.generateRequest.declarationTypeId || this.generateRequest.declarationTypeId === 0) {
      alert('⚠️ Veuillez sélectionner un type de déclaration');
      return;
    }
    if (!this.generateRequest.periode) {
      alert('⚠️ Veuillez sélectionner une période');
      return;
    }
    if (!this.generateRequest.dateDebut || !this.generateRequest.dateFin) {
      alert('⚠️ Les dates de début et fin sont requises');
      return;
    }
    if (this.selectedDeclarationType && !this.hasSqlConfigured(this.selectedDeclarationType)) {
      alert('⚠️ Ce type n\'a pas de requête SQL configurée.');
      return;
    }
    if (this.selectedDeclarationType && !this.hasXsdConfigured(this.selectedDeclarationType)) {
      alert('⚠️ Ce type n\'a pas de fichier XSD configuré.');
      return;
    }

    this.loadingAction = true;
    this.declarationService.generateDeclaration(this.generateRequest).subscribe({
      next: () => {
        alert('✅ Déclaration générée avec succès!');
        this.closeGenerateModal();
        this.loadDeclarations();
        this.loadingAction = false;
      },
      error: (err) => {
        console.error('❌ Erreur génération:', err);
        alert('❌ Erreur:\n\n' + (err.error?.message || err.message || 'Erreur inconnue'));
        this.loadingAction = false;
      }
    });
  }

  // ========== CONFIG MODAL ==========

  openConfigModal(type: DeclarationType): void {
    this.configDeclarationType = { ...type };
    this.xsdFile = null;
    this.sqlQuery = type.sqlQuery || '';
    this.sqlTestResult = null;

    const now = new Date();
    const m = now.getMonth() === 0 ? 12 : now.getMonth();
    const y = now.getMonth() === 0 ? now.getFullYear() - 1 : now.getFullYear();
    const lastDay = new Date(y, m, 0).getDate();
    this.testDateDebut = `${y}-${String(m).padStart(2, '0')}-01`;
    this.testDateFin   = `${y}-${String(m).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`;

    this.showConfigModal = true;
  }

  closeConfigModal(): void {
    this.showConfigModal = false;
    this.configDeclarationType = null;
    this.xsdFile = null;
    this.sqlQuery = '';
    this.sqlTestResult = null;
    this.loadDeclarationTypes();
  }

  onXsdFileChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const file = input.files[0];
      if (!file.name.toLowerCase().endsWith('.xsd')) {
        alert('⚠️ Veuillez sélectionner un fichier .xsd');
        return;
      }
      this.xsdFile = file;
    }
  }

  uploadXsd(): void {
    if (!this.xsdFile || !this.configDeclarationType?.id) return;
    this.uploadingXsd = true;
    this.declarationTypeService.uploadXsd(this.configDeclarationType.id, this.xsdFile).subscribe({
      next: () => {
        alert(`✅ XSD "${this.xsdFile!.name}" uploadé avec succès!`);
        this.configDeclarationType!.xsdFileName = this.xsdFile!.name;
        this.uploadingXsd = false;
        this.loadDeclarationTypes();
      },
      error: (err) => {
        alert('❌ Erreur upload:\n\n' + (err.error?.error || err.message));
        this.uploadingXsd = false;
      }
    });
  }

  downloadXsd(): void {
    if (!this.configDeclarationType?.id) return;
    this.declarationTypeService.downloadXsd(this.configDeclarationType.id).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = this.configDeclarationType?.xsdFileName || 'schema.xsd';
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => alert('❌ Erreur téléchargement XSD')
    });
  }

  testSql(): void {
    if (!this.sqlQuery.trim()) { alert('⚠️ Saisir une requête SQL'); return; }
    if (!this.testDateDebut || !this.testDateFin) { alert('⚠️ Saisir les dates de test'); return; }
    if (!this.configDeclarationType?.id) return;

    this.testingSQL = true;
    this.sqlTestResult = null;

    this.declarationTypeService.saveSqlQuery(this.configDeclarationType.id, this.sqlQuery).subscribe({
      next: () => {
        this.declarationTypeService.testSqlQuery(
          this.configDeclarationType!.id!,
          this.testDateDebut,
          this.testDateFin
        ).subscribe({
          next: (result) => { this.sqlTestResult = result; this.testingSQL = false; },
          error: (err) => {
            this.sqlTestResult = { success: false, error: err.error?.error || err.message };
            this.testingSQL = false;
          }
        });
      },
      error: () => {
        this.sqlTestResult = { success: false, error: 'Impossible de sauvegarder la SQL' };
        this.testingSQL = false;
      }
    });
  }

  saveSql(): void {
    if (!this.sqlQuery.trim()) { alert('⚠️ La requête SQL ne peut pas être vide'); return; }
    const upper = this.sqlQuery.trim().toUpperCase();
    if (!upper.startsWith('SELECT') && !upper.startsWith('(SELECT')) {
      alert('⚠️ La requête SQL doit commencer par SELECT');
      return;
    }
    if (!this.configDeclarationType?.id) return;

    this.savingSQL = true;
    this.declarationTypeService.saveSqlQuery(this.configDeclarationType.id, this.sqlQuery).subscribe({
      next: () => {
        alert('✅ Requête SQL sauvegardée!');
        this.configDeclarationType!.sqlQuery = this.sqlQuery;
        this.savingSQL = false;
        this.loadDeclarationTypes();
      },
      error: (err) => {
        alert('❌ Erreur:\n\n' + (err.error?.error || err.message));
        this.savingSQL = false;
      }
    });
  }

  // ========== DETAILS MODAL ==========

  openDetailsModal(declaration: Declaration): void {
    this.selectedDeclaration = declaration;
    this.showDetailsModal = true;
  }

  closeDetailsModal(): void {
    this.showDetailsModal = false;
    this.selectedDeclaration = null;
  }

  // ========== ACTIONS ==========
downloadDeclaration(declaration: Declaration): void {
  if (!declaration.id) return;

  // ✅ Utilise contenuFichier déjà chargé — pas d'appel API supplémentaire
  if (declaration.contenuFichier) {
    const blob = new Blob([declaration.contenuFichier], { type: 'application/xml' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = declaration.nomFichier || 'declaration.xml';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
    return;
  }

  // Fallback → appel API si contenuFichier absent
  this.declarationService.downloadDeclaration(declaration.id).subscribe({
    next: (blob) => {
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = declaration.nomFichier || 'declaration.xml';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    },
    error: () => alert('❌ Erreur lors du téléchargement')
  });
}
  submitForValidation(declaration: Declaration): void {
    if (!declaration.id) return;
    if (!confirm(`Soumettre pour validation?\n\nType: ${declaration.declarationType?.nom}\nPériode: ${declaration.periode}`)) return;

    this.loadingAction = true;
    this.declarationService.submitForValidation(declaration.id).subscribe({
      next: () => {
        alert('✅ Déclaration soumise pour validation!');
        this.loadDeclarations();
        this.loadingAction = false;
      },
      error: (err) => {
        alert('❌ Erreur:\n' + (err.error?.message || err.message));
        this.loadingAction = false;
      }
    });
  }

  // ========== HELPERS ==========

  canSubmit(declaration: Declaration): boolean {
    return declaration.statut === 'GENEREE' || declaration.statut === 'REJETEE';
  }

  canDownload(declaration: Declaration): boolean {
    return !!declaration.id && !!declaration.nomFichier;
  }

  getStatusBadgeClass(statut: string): string {
    const map: { [k: string]: string } = {
      'BROUILLON':    'status-draft',
      'GENEREE':      'status-generated',
      'EN_VALIDATION':'status-pending',
      'VALIDEE':      'status-validated',
      'REJETEE':      'status-rejected',
      'ENVOYEE':      'status-sent'
    };
    return map[statut] || 'status-default';
  }

  getStatusLabel(statut: string): string {
    const map: { [k: string]: string } = {
      'BROUILLON':    'Brouillon',
      'GENEREE':      'Générée',
      'EN_VALIDATION':'En validation',
      'VALIDEE':      'Validée',
      'REJETEE':      'Rejetée',
      'ENVOYEE':      'Envoyée'
    };
    return map[statut] || statut;
  }

  formatDate(dateString: string | undefined): string {
    if (!dateString) return '-';
    try {
      return new Date(dateString).toLocaleDateString('fr-FR', {
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit'
      });
    } catch {
      return dateString;
    }
  }
}