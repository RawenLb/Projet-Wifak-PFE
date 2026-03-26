import { Component, OnInit } from '@angular/core';
import { DeclarationService, Declaration, GenerateDeclarationRequest } from '../services/Declaration.service';
import { DeclarationTypeService, DeclarationType } from '../services/declaration-type.service';
import { ValidationService } from '../services/Validation.service'; // ✅ Ajouter cette importation

@Component({
  selector: 'app-declaration-management',
  templateUrl: './declaration-management.component.html',
  styleUrls: ['./declaration-management.component.scss']
})
export class DeclarationManagementComponent implements OnInit {

  declarations: Declaration[] = [];
  declarationTypes: DeclarationType[] = [];

  loadingDeclarations = false;
  loadingTypes        = false;
  loadingAction       = false;

  sidebarCollapsed = false;
  searchQuery      = '';
  filterStatut     = 'ALL';
  filterPeriode    = '';

  currentPage  = 1;
  itemsPerPage = 10;

  // ── Modals ──────────────────────────────────────────────────────
  showGenerateModal = false;
  showDetailsModal  = false;
  formSubmitted     = false;

  selectedDeclarationType: DeclarationType | null = null;
  selectedDeclaration: Declaration | null = null;

  // ── Stepper ─────────────────────────────────────────────────────
  currentStep = 1;  // 1 | 2 | 3

  // ── Generate request ────────────────────────────────────────────
  generateRequest: GenerateDeclarationRequest = {
    declarationTypeId: 0,
    periode: '',
    dateDebut: '',
    dateFin: ''
  };

  // ── XML ─────────────────────────────────────────────────────────
  xsdFile: File | null = null;
  xsdPreviewContent    = '';
  isDraggingXsd        = false;

  // ── CSV config ──────────────────────────────────────────────────
  csvConfig = {
    separator:      ';',
    encoding:       'UTF-8',
    textQualifier:  '"',
    dateFormat:     'yyyy-MM-dd',
    includeHeader:  true,
    includeBOM:     true,
    columns:        ''
  };

  // ── TXT config ──────────────────────────────────────────────────
  txtConfig = {
    structure:           'DELIMITED',
    encoding:            'UTF-8',
    lineEnding:          'CRLF',
    delimiter:           '|',
    lineTemplate:        '',
    fileHeader:          '',
    fileFooter:          '',
    includeLineNumbers:  false,
    includeTimestamp:    true
  };

  // ── TXT placeholder ─────────────────────────────────────────────
  txtLinePlaceholder = 'ex: {code_banque}|{nom_banque}|{periode}|{total_actifs}\nPour cle=valeur:\nCODE_BANQUE={code_banque}\nNOM={nom_banque}';

  // ── SQL ─────────────────────────────────────────────────────────
  sqlQuery      = '';
  testDateDebut = '';
  testDateFin   = '';
  testingSQL    = false;
  sqlTestResult: any = null;

  constructor(
    private declarationService:     DeclarationService,
    private declarationTypeService: DeclarationTypeService,
        private validationService:      ValidationService  // ✅ Ajouter ValidationService

  ) {}

  ngOnInit(): void {
    this.loadDeclarations();
    this.loadDeclarationTypes();
  }

  // ══════════════════════════════════════════════════════
  // LOAD
  // ══════════════════════════════════════════════════════

  loadDeclarations(): void {
    this.loadingDeclarations = true;
    this.declarationService.getMyDeclarations().subscribe({
      next:  (data) => { this.declarations = data; this.loadingDeclarations = false; },
      error: (err)  => { console.error(err); this.declarations = []; this.loadingDeclarations = false; }
    });
  }

  loadDeclarationTypes(): void {
    this.loadingTypes = true;
    this.declarationTypeService.getAll().subscribe({
      next:  (types) => { this.declarationTypes = types.filter(t => t.actif); this.loadingTypes = false; },
      error: (err)   => { console.error(err); this.loadingTypes = false; }
    });
  }

  // ══════════════════════════════════════════════════════
  // FILTRES
  // ══════════════════════════════════════════════════════

  matchesFilters(d: Declaration): boolean {
    if (this.filterStatut !== 'ALL' && d.statut !== this.filterStatut) return false;
    if (this.filterPeriode && d.periode && !d.periode.includes(this.filterPeriode)) return false;
    if (this.searchQuery?.trim()) {
      const q = this.searchQuery.toLowerCase().trim();
      const match = [d.declarationType?.code, d.declarationType?.nom, d.periode, d.statut]
        .some(v => v?.toLowerCase().includes(q));
      if (!match) return false;
    }
    return true;
  }

  applyFilters(): void { this.currentPage = 1; }
  clearFilters():  void { this.filterStatut = 'ALL'; this.filterPeriode = ''; this.searchQuery = ''; this.currentPage = 1; }
  clearSearch():   void { this.searchQuery = ''; this.currentPage = 1; }

  // ══════════════════════════════════════════════════════
  // STATS
  // ══════════════════════════════════════════════════════

  getStatsByStatus(statut: string): number {
    return this.declarations.filter(d => d.statut === statut).length;
  }

  // ══════════════════════════════════════════════════════
  // GENERATE MODAL — OPEN / CLOSE
  // ══════════════════════════════════════════════════════

  openGenerateModal(): void {
    this.resetModal();
    this.showGenerateModal = true;
  }

  closeGenerateModal(): void {
    this.showGenerateModal = false;
    this.resetModal();
  }

  private resetModal(): void {
    this.currentStep             = 1;
    this.formSubmitted           = false;
    this.selectedDeclarationType = null;
    this.xsdFile                 = null;
    this.xsdPreviewContent       = '';
    this.isDraggingXsd           = false;
    this.sqlQuery                = '';
    this.sqlTestResult           = null;
    this.generateRequest         = { declarationTypeId: 0, periode: '', dateDebut: '', dateFin: '' };

    this.csvConfig = { separator: ';', encoding: 'UTF-8', textQualifier: '"',
                       dateFormat: 'yyyy-MM-dd', includeHeader: true, includeBOM: true, columns: '' };
    this.txtConfig = { structure: 'DELIMITED', encoding: 'UTF-8', lineEnding: 'CRLF',
                       delimiter: '|', lineTemplate: '', fileHeader: '', fileFooter: '',
                       includeLineNumbers: false, includeTimestamp: true };

    // Pré-remplir dates test avec mois précédent
    const now  = new Date();
    const m    = now.getMonth() === 0 ? 12 : now.getMonth();
    const y    = now.getMonth() === 0 ? now.getFullYear() - 1 : now.getFullYear();
    const last = new Date(y, m, 0).getDate();
    this.testDateDebut = `${y}-${String(m).padStart(2, '0')}-01`;
    this.testDateFin   = `${y}-${String(m).padStart(2, '0')}-${String(last).padStart(2, '0')}`;
  }

  // ══════════════════════════════════════════════════════
  // STEPPER NAVIGATION
  // ══════════════════════════════════════════════════════

  stepNext(): void {
    this.formSubmitted = true;

    if (this.currentStep === 1) {
      if (!this.generateRequest.declarationTypeId || !this.generateRequest.periode) return;
      this.formSubmitted = false;
      this.currentStep = 2;
      return;
    }

    if (this.currentStep === 2) {
      const fmt = this.selectedDeclarationType?.format;
      if (fmt === 'XML' && !this.xsdFile) return;
      if (fmt === 'CSV' && !this.csvConfig.columns?.trim()) return;
      this.formSubmitted = false;
      this.currentStep = 3;
    }
  }

  stepBack(): void {
    if (this.currentStep > 1) {
      this.currentStep--;
    } else {
      this.closeGenerateModal();
    }
  }

  // ══════════════════════════════════════════════════════
  // ÉTAPE 1 — Type / Période
  // ══════════════════════════════════════════════════════

  onDeclarationTypeChange(): void {
    const id = +this.generateRequest.declarationTypeId;
    this.selectedDeclarationType = this.declarationTypes.find(t => t.id === id) ?? null;
    if (this.selectedDeclarationType?.sqlQuery) {
      this.sqlQuery = this.selectedDeclarationType.sqlQuery;
    }
  }

  onPeriodeChange(): void {
    if (!this.generateRequest.periode) return;
    const [y, m] = this.generateRequest.periode.split('-').map(Number);
    this.generateRequest.dateDebut = this.toStr(new Date(y, m - 1, 1));
    this.generateRequest.dateFin   = this.toStr(new Date(y, m, 0));
    this.testDateDebut = this.generateRequest.dateDebut;
    this.testDateFin   = this.generateRequest.dateFin;
  }

  private toStr(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  // ══════════════════════════════════════════════════════
  // ÉTAPE 2 — XML
  // ══════════════════════════════════════════════════════

  onXsdFileChange(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    if (!file.name.toLowerCase().endsWith('.xsd')) { alert('Fichier .xsd uniquement'); return; }
    this.xsdFile = file;
    const reader = new FileReader();
    reader.onload = (e) => {
      const txt = e.target?.result as string;
      this.xsdPreviewContent = txt.length > 800 ? txt.substring(0, 800) + '\n...' : txt;
    };
    reader.readAsText(file);
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDraggingXsd = true;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDraggingXsd = false;
    const file = event.dataTransfer?.files[0];
    if (!file?.name.toLowerCase().endsWith('.xsd')) { alert('Fichier .xsd uniquement'); return; }
    this.xsdFile = file;
    const reader = new FileReader();
    reader.onload = (e) => {
      const txt = e.target?.result as string;
      this.xsdPreviewContent = txt.substring(0, 800);
    };
    reader.readAsText(file);
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} o`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} Ko`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`;
  }

  copyToClipboard(text: string): void {
    navigator.clipboard.writeText(text).then(() => alert('Copié !'));
  }

  // ══════════════════════════════════════════════════════
  // ÉTAPE 2 — CSV helpers
  // ══════════════════════════════════════════════════════

  getCsvColumns(): string[] {
    return (this.csvConfig.columns || '').split(',').filter(c => c.trim());
  }

  // ══════════════════════════════════════════════════════
  // ÉTAPE 2 — TXT helpers
  // ══════════════════════════════════════════════════════

  getTxtPreview(): string {
    const lines: string[] = [];
    if (this.txtConfig.fileHeader)
      lines.push(this.txtConfig.fileHeader.replace('{periode}', this.generateRequest.periode || 'AAAA-MM'));
    if (this.txtConfig.includeTimestamp)
      lines.push(`GENERE_LE=${new Date().toISOString().split('T')[0]}`);
    if (this.txtConfig.lineTemplate) {
      lines.push('--- DONNEES ---');
      lines.push(this.txtConfig.lineTemplate.split('\n')[0]);
      lines.push('...');
    }
    if (this.txtConfig.fileFooter)
      lines.push(this.txtConfig.fileFooter.replace('{date_generation}', new Date().toISOString().split('T')[0]));
    return lines.join('\n') || "(remplissez les champs ci-dessus pour voir l'apercu)";
  }

  // ══════════════════════════════════════════════════════
  // ÉTAPE 3 — SQL
  // ══════════════════════════════════════════════════════

  formatSql(): void {
    if (!this.sqlQuery) return;
    const kw = ['SELECT','FROM','WHERE','JOIN','LEFT JOIN','RIGHT JOIN','INNER JOIN',
                 'ON','AND','OR','ORDER BY','GROUP BY','HAVING','BETWEEN','LIMIT'];
    let q = this.sqlQuery.replace(/\s+/g, ' ').trim();
    kw.forEach(k => { q = q.replace(new RegExp(`\\b${k}\\b`, 'gi'), `\n${k}`); });
    this.sqlQuery = q.trim();
  }

  clearSql(): void {
    if (confirm('Effacer la requête SQL ?')) { this.sqlQuery = ''; this.sqlTestResult = null; }
  }

  testSql(): void {
    if (!this.sqlQuery?.trim())      { alert('Saisissez une requête SQL'); return; }
    if (!this.testDateDebut || !this.testDateFin) { alert('Saisissez les dates de test'); return; }
    if (!this.generateRequest.declarationTypeId)  { alert('Sélectionnez d\'abord un type'); return; }

    this.testingSQL    = true;
    this.sqlTestResult = null;

    this.declarationTypeService.saveSqlQuery(this.generateRequest.declarationTypeId, this.sqlQuery)
      .subscribe({
        next: () => {
          this.declarationTypeService.testSqlQuery(
            this.generateRequest.declarationTypeId, this.testDateDebut, this.testDateFin
          ).subscribe({
            next:  (r)   => { this.sqlTestResult = r; this.testingSQL = false; },
            error: (err) => {
              this.sqlTestResult = { success: false, error: err.error?.error || err.message };
              this.testingSQL = false;
            }
          });
        },
        error: () => {
          this.sqlTestResult = { success: false, error: 'Impossible de sauvegarder la requête SQL' };
          this.testingSQL = false;
        }
      });
  }

  // ══════════════════════════════════════════════════════
  // GENERATE
  // ══════════════════════════════════════════════════════

  generateDeclaration(): void {
    this.formSubmitted = true;
    const fmt = this.selectedDeclarationType?.format;

    if (!this.generateRequest.declarationTypeId) { alert('Type obligatoire'); return; }
    if (!this.generateRequest.periode)            { alert('Période obligatoire'); return; }
    if (fmt === 'XML' && !this.xsdFile)           { alert('Fichier XSD obligatoire'); return; }
    if (fmt === 'CSV' && !this.csvConfig.columns) { alert('Colonnes CSV obligatoires'); return; }
    if (!this.sqlQuery?.trim())                   { alert('Requête SQL obligatoire'); return; }
    if (!this.sqlQuery.trim().toUpperCase().startsWith('SELECT')) {
      alert('La requête doit commencer par SELECT');
      return;
    }

    this.loadingAction = true;

    const doGenerate = () => {
      this.declarationTypeService.saveSqlQuery(this.generateRequest.declarationTypeId, this.sqlQuery)
        .subscribe({
          next: () => {
            this.declarationService.generateDeclaration(this.generateRequest).subscribe({
              next: () => {
                alert('Déclaration générée avec succès !');
                this.closeGenerateModal();
                this.loadDeclarations();
                this.loadingAction = false;
              },
              error: (err) => {
                alert('Erreur génération : ' + (err.error?.message || err.message || 'Erreur inconnue'));
                this.loadingAction = false;
              }
            });
          },
          error: (err) => {
            alert('Erreur sauvegarde SQL : ' + (err.error?.error || err.message));
            this.loadingAction = false;
          }
        });
    };

    if (fmt === 'XML' && this.xsdFile) {
      this.declarationTypeService.uploadXsd(this.generateRequest.declarationTypeId, this.xsdFile)
        .subscribe({
          next:  () => doGenerate(),
          error: (err) => {
            alert('Erreur upload XSD : ' + (err.error?.error || err.message));
            this.loadingAction = false;
          }
        });
    } else {
      doGenerate();
    }
  }

  // ══════════════════════════════════════════════════════
  // DETAILS MODAL
  // ══════════════════════════════════════════════════════

  openDetailsModal(d: Declaration): void  { this.selectedDeclaration = d; this.showDetailsModal = true; }
  closeDetailsModal(): void               { this.showDetailsModal = false; this.selectedDeclaration = null; }

  // ══════════════════════════════════════════════════════
  // ACTIONS
  // ══════════════════════════════════════════════════════

  downloadDeclaration(declaration: Declaration): void {
    if (!declaration.id) return;

    // ✅ Si le contenu est déjà en mémoire — créer le Blob avec le bon MIME type
    if (declaration.contenuFichier) {
      const mimeType = this.declarationService.resolveMimeType(declaration.nomFichier || '');
      const blob = new Blob([declaration.contenuFichier], { type: mimeType });
      this.triggerDownload(blob, declaration.nomFichier || 'declaration');
      return;
    }

    // ✅ Sinon — télécharger depuis le serveur (le ContentType est géré côté backend)
    this.declarationService.downloadDeclaration(declaration.id).subscribe({
      next:  (blob) => this.triggerDownload(blob, declaration.nomFichier || 'declaration'),
      error: ()     => alert('Erreur lors du téléchargement')
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

  submitForValidation(declaration: Declaration): void {
    if (!declaration.id) return;
    if (!confirm(
      `Soumettre pour validation ?\n\nType : ${declaration.declarationType?.nom}\nPériode : ${declaration.periode}`
    )) return;
    this.loadingAction = true;
    this.validationService.submitForValidation(declaration.id).subscribe({
      next:  () => { alert('Soumis pour validation !'); this.loadDeclarations(); this.loadingAction = false; },
      error: (err) => {
        alert('Erreur : ' + (err.error?.message || err.message));
        this.loadingAction = false;
      }
    });
  }

  // ══════════════════════════════════════════════════════
  // HELPERS
  // ══════════════════════════════════════════════════════

  getSqlColumns(): string {
    return (this.sqlTestResult?.colonnesDisponibles ?? []).join(' - ');
  }

  canSubmit(d: Declaration): boolean   { return d.statut === 'GENEREE' || d.statut === 'REJETEE'; }
  canDownload(d: Declaration): boolean { return !!d.id && !!d.nomFichier; }

  getStatusBadgeClass(s: string): string {
    const m: any = {
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
    const m: any = {
      BROUILLON:     'Brouillon',
      GENEREE:       'Générée',
      EN_VALIDATION: 'En validation',
      VALIDEE:       'Validée',
      REJETEE:       'Rejetée',
      ENVOYEE:       'Envoyée'
    };
    return m[s] || s;
  }

  formatDate(d?: string): string {
    if (!d) return '-';
    try {
      return new Date(d).toLocaleDateString('fr-FR', {
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit'
      });
    } catch {
      return d;
    }
  }

  canSendToBCT(d: Declaration): boolean {
  return d.statut === 'VALIDEE';
}

sendToBCT(declaration: Declaration): void {
  if (!declaration.id) return;
  if (!confirm(
    `Envoyer à la BCT ?\n\nType : ${declaration.declarationType?.nom}\nPériode : ${declaration.periode}\n\n⚠️ Action irréversible.`
  )) return;

  this.loadingAction = true;
this.validationService.markAsSent(declaration.id).subscribe({    next: () => {
      alert(`✅ Déclaration ${declaration.declarationType?.code} envoyée à la BCT !`);
      this.loadDeclarations();
      this.loadingAction = false;
    },
    error: (err) => {
      alert('Erreur : ' + (err.error?.message || err.message));
      this.loadingAction = false;
    }
  });
}
}