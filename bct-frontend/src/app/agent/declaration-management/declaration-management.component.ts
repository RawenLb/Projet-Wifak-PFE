// src/app/declaration-management/declaration-management.component.ts
// ✅ VERSION CORRIGÉE — Flux XSD upload → SQL → Mapping → Génération

import { Component, OnInit } from '@angular/core';
import {
  DeclarationService,
  Declaration,
  GenerateDeclarationRequest,
  FieldMapping,
  MappingAnalysisResponse
} from '../../services/Declaration.service';
import { DeclarationTypeService, DeclarationType } from '../../services/declaration-type.service';
import { ValidationService } from '../../services/Validation.service';
import { JiraService, JiraTicketResponse } from '../../services/jira.service';
import { ConfirmDialogService } from '../../services/confirm-dialog.service';
import { ToastService } from '../../services/toast.service';

// Re-export local interface for template usage
export interface FieldMappingLocal {
  xsdFieldName:  string;
  xsdFieldPath:  string;
  xsdType:       string;
  required:      boolean;
  source:        'SQL' | 'STATIC' | 'NONE';
  sqlColumn:     string;
  staticValue:   string;
}

@Component({
  selector:    'app-declaration-management',
  templateUrl: './declaration-management.component.html',
  styleUrls:   ['./declaration-management.component.scss']
})
export class DeclarationManagementComponent implements OnInit {

  // ── Données ────────────────────────────────────────────────────
  declarations:     Declaration[]     = [];
  declarationTypes: DeclarationType[] = [];

  // ── États chargement ────────────────────────────────────────────
  loadingDeclarations = false;
  loadingTypes        = false;
  loadingAction       = false;

  // ── Layout ──────────────────────────────────────────────────────
  sidebarCollapsed = false;

  // ── Filtres ─────────────────────────────────────────────────────
  searchQuery   = '';
  filterStatut  = 'ALL';
  filterPeriode = '';
  currentPage   = 1;
  itemsPerPage  = 10;

  // ── Modals ──────────────────────────────────────────────────────
  showGenerateModal = false;
  showDetailsModal  = false;
  showEditModal     = false;
  showDeleteModal   = false;
  showCorrectModal  = false;
  formSubmitted     = false;

  selectedDeclarationType: DeclarationType | null = null;
  selectedDeclaration:     Declaration    | null = null;

  // ── Stepper génération ──────────────────────────────────────────
  currentStep = 1;

  // ── Requête de génération ────────────────────────────────────────
  generateRequest: GenerateDeclarationRequest = {
    declarationTypeId: 0,
    periode:   '',
    dateDebut: '',
    dateFin:   ''
  };

  // ── Edit ────────────────────────────────────────────────────────
  editForm: GenerateDeclarationRequest = {
    declarationTypeId: 0,
    periode:   '',
    dateDebut: '',
    dateFin:   ''
  };
  editSelectedType: DeclarationType | null = null;

  // ── Delete ──────────────────────────────────────────────────────
  declarationToDelete: Declaration | null = null;
  deleteConfirmText    = '';

  // ── Correction après rejet ───────────────────────────────────────
  correctSqlQuery           = '';
  correctTestDateDebut      = '';
  correctTestDateFin        = '';
  correctTestingSQL         = false;
  correctSqlTestResult: any = null;
  correctXsdFile: File | null = null;
  correctXsdPreviewContent  = '';
  correctIsDraggingXsd      = false;

  // ── XSD (génération) ────────────────────────────────────────────
  xsdFile:          File | null = null;
  xsdPreviewContent = '';
  isDraggingXsd     = false;

  // ── SQL (génération) ────────────────────────────────────────────
  sqlQuery:      string    = '';
  testDateDebut  = '';
  testDateFin    = '';
  testingSQL     = false;
  sqlTestResult: any = null;

  // ── Jira ─────────────────────────────────────────────────────────
  jiraTickets:    Map<number, JiraTicketResponse> = new Map();
  jiraLoadingMap: Record<number, boolean>         = {};

  // ── Mapping XSD ↔ SQL ─────────────────────────────────────────────
  mappingAnalysis:      MappingAnalysisResponse | null = null;
  fieldMappings:        FieldMappingLocal[]            = [];
  analyzingMapping      = false;
  mappingAnalysisError: string | null                  = null;
  selectedMappingIndex: number | null                  = null;

  // ── Flags internes ───────────────────────────────────────────────
  /** XSD a été uploadé avec succès pendant l'étape 2 */
   xsdUploaded = false;
  /** SQL a été sauvegardé avec succès pendant l'étape 3 */
   sqlSaved    = false;

  constructor(
    private declarationService:     DeclarationService,
    private declarationTypeService: DeclarationTypeService,
    private validationService:      ValidationService,
    private jiraService:            JiraService,
    private confirmDialog:          ConfirmDialogService,
    private toast:                  ToastService
  ) {}

  ngOnInit(): void {
    this.loadDeclarations();
    this.loadDeclarationTypes();
  }

  // ══════════════════════════════════════════════════════
  // CHARGEMENT
  // ══════════════════════════════════════════════════════

  loadDeclarations(): void {
    this.loadingDeclarations = true;
    this.declarationService.getMyDeclarations().subscribe({
      next: (data) => {
        this.declarations        = data;
        this.loadingDeclarations = false;
        this.loadJiraTickets(data);
      },
      error: () => {
        this.declarations        = [];
        this.loadingDeclarations = false;
      }
    });
  }

  loadDeclarationTypes(): void {
    this.loadingTypes = true;
    this.declarationTypeService.getAll().subscribe({
      next:  (types) => { this.declarationTypes = types.filter(t => t.actif); this.loadingTypes = false; },
      error: ()      => { this.loadingTypes = false; }
    });
  }

  // ── Jira ──────────────────────────────────────────────────────

  isJiraEligible(d: Declaration): boolean {
    return ['GENEREE', 'EN_VALIDATION', 'VALIDEE', 'REJETEE', 'ENVOYEE'].includes(d.statut);
  }

  private loadJiraTickets(declarations: Declaration[]): void {
    declarations
      .filter(d => this.isJiraEligible(d) && d.id)
      .forEach(d => {
        this.jiraLoadingMap[d.id!] = true;
        const delay = d.statut === 'GENEREE' ? 1500 : 0;
        setTimeout(() => {
          this.jiraService.getTicketForDeclaration(d.id!).subscribe(ticket => {
            this.jiraLoadingMap[d.id!] = false;
            if (ticket) this.jiraTickets.set(d.id!, ticket);
          });
        }, delay);
      });
  }

  getJiraTicket(id: number | undefined): JiraTicketResponse | null {
    if (!id) return null;
    return this.jiraTickets.get(id) ?? null;
  }

  // ══════════════════════════════════════════════════════
  // FILTRES
  // ══════════════════════════════════════════════════════

  matchesFilters(d: Declaration): boolean {
    if (this.filterStatut !== 'ALL' && d.statut !== this.filterStatut) return false;
    if (this.filterPeriode && d.periode && !d.periode.includes(this.filterPeriode)) return false;
    if (this.searchQuery?.trim()) {
      const q     = this.searchQuery.toLowerCase().trim();
      const match = [d.declarationType?.code, d.declarationType?.nom, d.periode, d.statut]
                      .some(v => v?.toLowerCase().includes(q));
      if (!match) return false;
    }
    return true;
  }

  applyFilters():  void { this.currentPage = 1; }
  clearFilters():  void { this.filterStatut = 'ALL'; this.filterPeriode = ''; this.searchQuery = ''; this.currentPage = 1; }
  clearSearch():   void { this.searchQuery = ''; this.currentPage = 1; }

  getStatsByStatus(statut: string): number {
    return this.declarations.filter(d => d.statut === statut).length;
  }

  // ══════════════════════════════════════════════════════
  // PAGINATION
  // ══════════════════════════════════════════════════════

  get filteredDeclarations(): Declaration[] {
    return this.declarations.filter(d => this.matchesFilters(d));
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filteredDeclarations.length / this.itemsPerPage));
  }

  get paginatedDeclarations(): Declaration[] {
    const start = (this.currentPage - 1) * this.itemsPerPage;
    return this.filteredDeclarations.slice(start, start + this.itemsPerPage);
  }

  get paginationStart(): number {
    return this.filteredDeclarations.length === 0 ? 0 : (this.currentPage - 1) * this.itemsPerPage + 1;
  }

  get paginationEnd(): number {
    return Math.min(this.currentPage * this.itemsPerPage, this.filteredDeclarations.length);
  }

  get pageNumbers(): number[] {
    const total = this.totalPages;
    const current = this.currentPage;
    const pages: number[] = [];
    if (total <= 7) {
      for (let i = 1; i <= total; i++) pages.push(i);
    } else {
      pages.push(1);
      if (current > 3) pages.push(-1); // ellipsis
      for (let i = Math.max(2, current - 1); i <= Math.min(total - 1, current + 1); i++) pages.push(i);
      if (current < total - 2) pages.push(-1); // ellipsis
      pages.push(total);
    }
    return pages;
  }

  goToPage(page: number): void {
    if (page < 1 || page > this.totalPages) return;
    this.currentPage = page;
  }

  nextPage(): void { if (this.currentPage < this.totalPages) this.currentPage++; }
  prevPage(): void { if (this.currentPage > 1) this.currentPage--; }

  changeItemsPerPage(n: number): void { this.itemsPerPage = n; this.currentPage = 1; }

  openGenerateModal():  void { this.resetGenerateModal(); this.showGenerateModal = true; }
  closeGenerateModal(): void { this.showGenerateModal = false; this.resetGenerateModal(); }

  private resetGenerateModal(): void {
    this.currentStep             = 1;
    this.formSubmitted           = false;
    this.selectedDeclarationType = null;
    this.xsdFile                 = null;
    this.xsdPreviewContent       = '';
    this.isDraggingXsd           = false;
    this.xsdUploaded             = false;
    this.sqlSaved                = false;
    this.sqlQuery                = '';
    this.sqlTestResult           = null;
    this.generateRequest         = { declarationTypeId: 0, periode: '', dateDebut: '', dateFin: '' };
    this.mappingAnalysis         = null;
    this.fieldMappings           = [];
    this.analyzingMapping        = false;
    this.mappingAnalysisError    = null;
    this.selectedMappingIndex    = null;

    const now  = new Date();
    const m    = now.getMonth() === 0 ? 12 : now.getMonth();
    const y    = now.getMonth() === 0 ? now.getFullYear() - 1 : now.getFullYear();
    const last = new Date(y, m, 0).getDate();
    this.testDateDebut = `${y}-${String(m).padStart(2, '0')}-01`;
    this.testDateFin   = `${y}-${String(m).padStart(2, '0')}-${String(last).padStart(2, '0')}`;
  }

  // ════════════════════════════════════════════════════════════════
  // STEPPER — navigation entre étapes
  // ════════════════════════════════════════════════════════════════

  /**
   * Avancement Étape 1→2→3 (puis 3→4 pour XML via goToMappingStep).
   * Chaque transition valide et, si besoin, persiste les données côté serveur.
   */
  stepNext(): void {
    this.formSubmitted = true;

    // ── Étape 1 : valider type + période ──────────────────────────
    if (this.currentStep === 1) {
      if (!this.generateRequest.declarationTypeId || !this.generateRequest.periode) return;
      this.formSubmitted = false;
      this.currentStep   = 2;
      return;
    }

    // ── Étape 2 : upload XSD (XML uniquement) ──────────────────────
    if (this.currentStep === 2) {
      const fmt = this.selectedDeclarationType?.format;
      if (fmt === 'XML' && !this.xsdFile) return;

      if (fmt === 'XML' && this.xsdFile && !this.xsdUploaded) {
        // Upload immédiat ici pour ne pas bloquer l'étape 4
        this.loadingAction = true;
        this.declarationTypeService.uploadXsd(
          this.generateRequest.declarationTypeId, this.xsdFile
        ).subscribe({
          next: () => {
            this.xsdUploaded   = true;
            this.loadingAction = false;
            this.formSubmitted = false;
            this.currentStep   = 3;
          },
          error: (err: any) => {
            this.loadingAction = false;
            this.toast.error('Erreur upload XSD : ' + (err.error?.error || err.message));
          }
        });
        return;
      }

      this.formSubmitted = false;
      this.currentStep   = 3;
      return;
    }
  }

  stepBack(): void {
    if (this.currentStep === 4) {
      this.currentStep = 3;
    } else if (this.currentStep > 1) {
      this.currentStep--;
    } else {
      this.closeGenerateModal();
    }
  }

  onDeclarationTypeChange(): void {
    const id = +this.generateRequest.declarationTypeId;
    this.selectedDeclarationType = this.declarationTypes.find(t => t.id === id) ?? null;
    this.xsdUploaded = false; // Reset si on change de type
    this.sqlSaved    = false;
    if (this.selectedDeclarationType?.sqlQuery) {
      this.sqlQuery = this.selectedDeclarationType.sqlQuery;
    }
  }

  onPeriodeChange(): void {
    if (!this.generateRequest.periode) return;
    const [y, m]                    = this.generateRequest.periode.split('-').map(Number);
    this.generateRequest.dateDebut  = this.toIso(new Date(y, m - 1, 1));
    this.generateRequest.dateFin    = this.toIso(new Date(y, m, 0));
    this.testDateDebut              = this.generateRequest.dateDebut;
    this.testDateFin                = this.generateRequest.dateFin;
  }

  private toIso(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  // ── XSD ──────────────────────────────────────────────────────

  onXsdFileChange(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    if (!file.name.toLowerCase().endsWith('.xsd')) { this.toast.warning('Fichier .xsd uniquement'); return; }
    this.xsdFile     = file;
    this.xsdUploaded = false; // Nouveau fichier → reset flag
    const reader = new FileReader();
    reader.onload = (e) => {
      const txt = e.target?.result as string;
      this.xsdPreviewContent = txt.length > 800 ? txt.substring(0, 800) + '\n...' : txt;
    };
    reader.readAsText(file);
  }

  onDragOver(event: DragEvent): void { event.preventDefault(); this.isDraggingXsd = true; }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDraggingXsd = false;
    const file = event.dataTransfer?.files[0];
    if (!file?.name.toLowerCase().endsWith('.xsd')) { this.toast.warning('Fichier .xsd uniquement'); return; }
    this.xsdFile     = file;
    this.xsdUploaded = false;
    const reader = new FileReader();
    reader.onload = (e) => { this.xsdPreviewContent = (e.target?.result as string).substring(0, 800); };
    reader.readAsText(file);
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024)    return `${bytes} o`;
    if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} Ko`;
    return `${(bytes / 1048576).toFixed(1)} Mo`;
  }

  // ── SQL ──────────────────────────────────────────────────────

  formatSql(): void {
    if (!this.sqlQuery) return;
    const kw = ['SELECT','FROM','WHERE','JOIN','LEFT JOIN','RIGHT JOIN','INNER JOIN',
                 'ON','AND','OR','ORDER BY','GROUP BY','HAVING','BETWEEN','LIMIT'];
    let q = this.sqlQuery.replace(/\s+/g, ' ').trim();
    kw.forEach(k => { q = q.replace(new RegExp(`\\b${k}\\b`, 'gi'), `\n${k}`); });
    this.sqlQuery = q.trim();
  }

  async clearSql(): Promise<void> {
    if (await this.confirmDialog.confirm('Effacer la requête SQL', 'Effacer la requête SQL ?', { confirmLabel: 'Effacer', type: 'warning' })) { this.sqlQuery = ''; this.sqlTestResult = null; this.sqlSaved = false; }
  }

  testSql(): void {
    if (!this.sqlQuery?.trim())                   { this.toast.warning('Saisissez une requête SQL'); return; }
    if (!this.testDateDebut || !this.testDateFin)  { this.toast.warning('Saisissez les dates de test'); return; }
    if (!this.generateRequest.declarationTypeId)   { this.toast.warning('Sélectionnez un type'); return; }

    this.testingSQL    = true;
    this.sqlTestResult = null;
    this.sqlSaved      = false;

    this.declarationTypeService.saveSqlQuery(this.generateRequest.declarationTypeId, this.sqlQuery)
      .subscribe({
        next: () => {
          this.sqlSaved = true;
          this.declarationTypeService.testSqlQuery(
            this.generateRequest.declarationTypeId,
            this.testDateDebut,
            this.testDateFin
          ).subscribe({
            next:  (r)   => { this.sqlTestResult = r; this.testingSQL = false; },
            error: (err) => {
              this.sqlTestResult = { success: false, error: err.error?.error || err.message };
              this.testingSQL    = false;
            }
          });
        },
        error: () => {
          this.sqlTestResult = { success: false, error: 'Impossible de sauvegarder la requête SQL' };
          this.testingSQL    = false;
        }
      });
  }

  // ══════════════════════════════════════════════════════
  // ÉTAPE 3 → 4  :  Sauvegarde SQL + Analyse Mapping
  // ══════════════════════════════════════════════════════

  /**
   * ✅ CORRIGÉ — Flux complet :
   *   1. Valide la requête SQL
   *   2. Sauvegarde la SQL (si pas déjà fait via testSql)
   *   3. Lance l'analyse XSD ↔ SQL via le backend
   *   4. Construit les fieldMappings avec pré-remplissage auto
   *   5. Passe à l'étape 4
   */
  goToMappingStep(): void {
    this.formSubmitted = true;

    if (!this.sqlQuery?.trim()) {
      this.toast.warning('La requête SQL est obligatoire');
      return;
    }
    if (!this.sqlQuery.trim().toUpperCase().startsWith('SELECT')) {
      this.toast.warning('La requête doit commencer par SELECT');
      return;
    }

    this.analyzingMapping     = true;
    this.mappingAnalysisError = null;
    this.selectedMappingIndex = null;

    const runAnalysis = () => {
      this.declarationService.analyzeMappingHttp({
        declarationTypeId: this.generateRequest.declarationTypeId,
        dateDebut:         this.generateRequest.dateDebut,
        dateFin:           this.generateRequest.dateFin
      }).subscribe({
        next: (analysis: MappingAnalysisResponse) => {
          this.mappingAnalysis  = analysis;
          this.analyzingMapping = false;
          this.buildFieldMappings(analysis);
          this.currentStep = 4;
        },
        error: (err: any) => {
          this.mappingAnalysisError = err.error?.error || err.message || 'Erreur analyse mapping';
          this.analyzingMapping     = false;
          // On passe quand même à l'étape 4 pour permettre un mapping manuel
          this.currentStep = 4;
        }
      });
    };

    if (this.sqlSaved) {
      // SQL déjà persistée → analyse directe
      runAnalysis();
    } else {
      this.declarationTypeService.saveSqlQuery(
        this.generateRequest.declarationTypeId, this.sqlQuery
      ).subscribe({
        next: () => {
          this.sqlSaved = true;
          runAnalysis();
        },
        error: () => {
          this.mappingAnalysisError = 'Impossible de sauvegarder la requête SQL';
          this.analyzingMapping     = false;
          this.currentStep          = 4;
        }
      });
    }
  }

  // ══════════════════════════════════════════════════════
  // CONSTRUCTION DES FIELD MAPPINGS
  // ══════════════════════════════════════════════════════

  /**
   * Construit le tableau fieldMappings depuis l'analyse XSD ↔ SQL.
   * Pré-remplit avec l'auto-mapping détecté par le backend.
   * Les champs non trouvés en SQL restent en mode NONE (à configurer manuellement).
   */
  private buildFieldMappings(analysis: MappingAnalysisResponse): void {
    if (!analysis?.xsdFields) { this.fieldMappings = []; return; }

    this.fieldMappings = analysis.xsdFields.map((field) => {
      const autoCol = analysis.autoMapped?.[field.name] ?? null;
      return {
        xsdFieldName: field.name,
        xsdFieldPath: field.path,
        xsdType:      field.type,
        required:     field.required,
        source:       autoCol ? 'SQL' : 'NONE',
        sqlColumn:    autoCol ?? '',
        staticValue:  ''
      } as FieldMappingLocal;
    });
  }

  // ── Interactions Mapping ─────────────────────────────────────────

  selectMappingField(index: number): void {
    this.selectedMappingIndex = index;
  }

  /**
   * Assigne une colonne SQL au champ XSD sélectionné.
   * Passe automatiquement au prochain champ obligatoire non mappé.
   */
  assignSqlColumn(col: string): void {
    if (this.selectedMappingIndex === null) return;
    const fm = this.fieldMappings[this.selectedMappingIndex];
    if (fm.source === 'STATIC') return;   // en mode statique, pas de clic SQL

    fm.source    = 'SQL';
    fm.sqlColumn = col;

    // Auto-avance vers le prochain champ obligatoire non mappé
    const nextRequired = this.fieldMappings.findIndex(
      (f, i) => i > this.selectedMappingIndex! && f.required && f.source === 'NONE'
    );
    if (nextRequired !== -1) {
      this.selectedMappingIndex = nextRequired;
    } else {
      // Sinon prochain champ non mappé (obligatoire ou non)
      const next = this.fieldMappings.findIndex(
        (f, i) => i > this.selectedMappingIndex! && f.source === 'NONE'
      );
      if (next !== -1) this.selectedMappingIndex = next;
    }
  }

  isSqlColumnUsed(col: string): boolean {
    return this.fieldMappings.some(
      (fm, i) => fm.source === 'SQL' && fm.sqlColumn === col && i !== this.selectedMappingIndex
    );
  }

  setMappingSource(index: number, source: 'SQL' | 'STATIC' | 'NONE'): void {
    const fm = this.fieldMappings[index];
    fm.source = source;
    if (source !== 'SQL')    fm.sqlColumn   = '';
    if (source !== 'STATIC') fm.staticValue = '';
    this.selectedMappingIndex = index;
  }

  countMappedFields(): number {
    return this.fieldMappings.filter(m => m.source !== 'NONE').length;
  }

  getMissingRequiredCount(): number {
    return this.fieldMappings.filter(m => m.required && m.source === 'NONE').length;
  }

  getUnusedSqlColumns(): string[] {
    if (!this.mappingAnalysis?.sqlColumns) return [];
    const usedCols = new Set(
      this.fieldMappings.filter(m => m.source === 'SQL' && m.sqlColumn).map(m => m.sqlColumn)
    );
    return this.mappingAnalysis.sqlColumns.filter(c => !usedCols.has(c));
  }

  async resetMapping(): Promise<void> {
    if (!await this.confirmDialog.confirm('Réinitialiser le mapping', 'Réinitialiser tous les mappings ?', { confirmLabel: 'Réinitialiser', type: 'warning' })) return;
    if (this.mappingAnalysis) this.buildFieldMappings(this.mappingAnalysis);
    this.selectedMappingIndex = null;
  }

  // ══════════════════════════════════════════════════════
  // GÉNÉRATION CSV / TXT (sans mapping)
  // ══════════════════════════════════════════════════════

  generateDeclaration(): void {
    this.formSubmitted = true;
    const fmt = this.selectedDeclarationType?.format;
    if (!this.generateRequest.declarationTypeId) { this.toast.warning('Type obligatoire'); return; }
    if (!this.generateRequest.periode)            { this.toast.warning('Période obligatoire'); return; }
    if (!this.sqlQuery?.trim())                   { this.toast.warning('Requête SQL obligatoire'); return; }
    if (!this.sqlQuery.trim().toUpperCase().startsWith('SELECT')) {
      this.toast.warning('La requête doit commencer par SELECT'); return;
    }

    this.loadingAction = true;

    const doGenerate = () => {
      this.declarationTypeService.saveSqlQuery(this.generateRequest.declarationTypeId, this.sqlQuery)
        .subscribe({
          next: () => {
            this.declarationService.generateDeclaration(this.generateRequest).subscribe({
              next: (_saved) => {
                this.toast.success('Déclaration générée avec succès !');
                this.closeGenerateModal();
                this.loadingAction = false;
                setTimeout(() => this.loadDeclarations(), 2500);
              },
              error: (err) => {
                this.toast.error('Erreur génération : ' + (err.error?.message || err.message || 'Erreur inconnue'));
                this.loadingAction = false;
              }
            });
          },
          error: (err) => {
            this.toast.error('Erreur sauvegarde SQL : ' + (err.error?.error || err.message));
            this.loadingAction = false;
          }
        });
    };

    // CSV/TXT n'a pas de XSD
    if (fmt === 'XML' && this.xsdFile && !this.xsdUploaded) {
      this.declarationTypeService.uploadXsd(this.generateRequest.declarationTypeId, this.xsdFile)
        .subscribe({
          next:  () => { this.xsdUploaded = true; doGenerate(); },
          error: (err) => { this.toast.error('Erreur upload XSD : ' + (err.error?.error || err.message)); this.loadingAction = false; }
        });
    } else {
      doGenerate();
    }
  }

  // ══════════════════════════════════════════════════════
  // ✅ GÉNÉRATION XML AVEC MAPPING — Étape 4
  // ══════════════════════════════════════════════════════

  /**
   * ✅ CORRIGÉ — Validation complète puis envoi au backend.
   *  - Vérifie champs obligatoires
   *  - Vérifie champs SQL sans colonne sélectionnée
   *  - Envoie le mapping au format attendu par le DTO Java
   */
  generateDeclarationWithMapping(): void {
    this.formSubmitted = true;

    // ── Validation ────────────────────────────────────────────────
    const missingRequired = this.fieldMappings.filter(m => m.required && m.source === 'NONE');
    if (missingRequired.length > 0) {
      this.toast.warning(`${missingRequired.length} champ(s) obligatoire(s) sans valeur : ${missingRequired.map(m => m.xsdFieldName).join(', ')}`);
      const idx = this.fieldMappings.findIndex(m => m.required && m.source === 'NONE');
      if (idx !== -1) this.selectedMappingIndex = idx;
      return;
    }

    const sqlWithoutCol = this.fieldMappings.filter(m => m.source === 'SQL' && !m.sqlColumn.trim());
    if (sqlWithoutCol.length > 0) {
      this.toast.warning(`${sqlWithoutCol.length} champ(s) SQL sans colonne : ${sqlWithoutCol.map(m => m.xsdFieldName).join(', ')}`);
      const idx = this.fieldMappings.findIndex(m => m.source === 'SQL' && !m.sqlColumn.trim());
      if (idx !== -1) this.selectedMappingIndex = idx;
      return;
    }

    const staticWithoutValue = this.fieldMappings.filter(
      m => m.required && m.source === 'STATIC' && !m.staticValue.trim()
    );
    if (staticWithoutValue.length > 0) {
      this.toast.warning(`${staticWithoutValue.length} champ(s) statique(s) sans valeur : ${staticWithoutValue.map(m => m.xsdFieldName).join(', ')}`);
      const idx = this.fieldMappings.findIndex(
        m => m.required && m.source === 'STATIC' && !m.staticValue.trim()
      );
      if (idx !== -1) this.selectedMappingIndex = idx;
      return;
    }

    this.loadingAction = true;

    // ── Envoi au backend ──────────────────────────────────────────
    const doGenerate = () => {
      this.declarationService.generateDeclarationWithMapping({
        declarationTypeId: this.generateRequest.declarationTypeId,
        periode:           this.generateRequest.periode,
        dateDebut:         this.generateRequest.dateDebut,
        dateFin:           this.generateRequest.dateFin,
        mappings:          this.fieldMappings as FieldMapping[]
      }).subscribe({
        next: (_saved: any) => {
          this.toast.success('Déclaration XML générée avec succès !');
          this.closeGenerateModal();
          this.loadingAction = false;
          setTimeout(() => this.loadDeclarations(), 2500);
        },
        error: (err: any) => {
          this.toast.error('Erreur génération : ' + (err.error?.error || err.message));
          this.loadingAction = false;
        }
      });
    };

    // Sauvegarde SQL si nécessaire (peut avoir changé après l'étape 3)
    if (!this.sqlSaved) {
      this.declarationTypeService.saveSqlQuery(
        this.generateRequest.declarationTypeId, this.sqlQuery
      ).subscribe({
        next:  () => { this.sqlSaved = true; doGenerate(); },
        error: (err: any) => {
          this.toast.error('Erreur sauvegarde SQL : ' + (err.error?.error || err.message));
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
  // EDIT MODAL
  // ══════════════════════════════════════════════════════

  canEdit(d: Declaration): boolean { return d.statut === 'GENEREE'; }

  openEditModal(d: Declaration): void {
    this.selectedDeclaration = d;
    this.editForm = {
      declarationTypeId: d.declarationType?.id ?? 0,
      periode:           d.periode ?? '',
      dateDebut:         d.dateDebut ?? '',
      dateFin:           d.dateFin  ?? ''
    };
    this.editSelectedType = this.declarationTypes.find(t => t.id === d.declarationType?.id) ?? null;
    this.formSubmitted    = false;
    this.showEditModal    = true;
  }

  closeEditModal(): void {
    this.showEditModal       = false;
    this.selectedDeclaration = null;
    this.editSelectedType    = null;
    this.formSubmitted       = false;
  }

  onEditTypeChange(): void {
    const id = +this.editForm.declarationTypeId;
    this.editSelectedType = this.declarationTypes.find(t => t.id === id) ?? null;
  }

  onEditPeriodeChange(): void {
    if (!this.editForm.periode) return;
    const [y, m]           = this.editForm.periode.split('-').map(Number);
    this.editForm.dateDebut = this.toIso(new Date(y, m - 1, 1));
    this.editForm.dateFin   = this.toIso(new Date(y, m, 0));
  }

  saveEdit(): void {
    this.formSubmitted = true;
    if (!this.editForm.declarationTypeId || !this.editForm.periode) return;
    if (!this.selectedDeclaration?.id) return;

    this.loadingAction = true;
    this.declarationService
      .updateDeclaration(this.selectedDeclaration.id, this.editForm)
      .subscribe({
        next: () => {
          this.toast.success('Déclaration mise à jour avec succès !');
          this.closeEditModal();
          this.loadDeclarations();
          this.loadingAction = false;
        },
        error: (err) => {
          this.toast.error('Erreur : ' + (err.error?.message || err.message || 'Erreur inconnue'));
          this.loadingAction = false;
        }
      });
  }

  // ══════════════════════════════════════════════════════
  // DELETE MODAL
  // ══════════════════════════════════════════════════════

  canDelete(d: Declaration): boolean {
    return ['BROUILLON', 'GENEREE', 'REJETEE'].includes(d.statut);
  }

  openDeleteModal(d: Declaration): void {
    this.declarationToDelete = d;
    this.deleteConfirmText   = '';
    this.showDeleteModal     = true;
  }

  closeDeleteModal(): void {
    this.showDeleteModal     = false;
    this.declarationToDelete = null;
    this.deleteConfirmText   = '';
  }

  get deleteConfirmValid(): boolean {
    return this.deleteConfirmText.trim().toLowerCase() === 'supprimer';
  }

  confirmDelete(): void {
    if (!this.deleteConfirmValid || !this.declarationToDelete?.id) return;
    this.loadingAction = true;
    this.declarationService.deleteDeclaration(this.declarationToDelete.id).subscribe({
      next: () => {
        this.closeDeleteModal();
        this.loadDeclarations();
        this.loadingAction = false;
      },
      error: (err) => {
        this.toast.error('Erreur suppression : ' + (err.error?.message || err.message));
        this.loadingAction = false;
      }
    });
  }

  // ══════════════════════════════════════════════════════
  // CORRECT MODAL
  // ══════════════════════════════════════════════════════

  canCorrect(d: Declaration): boolean { return d.statut === 'REJETEE'; }

  openCorrectModal(d: Declaration): void {
    this.selectedDeclaration = d;
    this.formSubmitted       = false;
    this.showCorrectModal    = true;
  }

  closeCorrectModal(): void {
    this.showCorrectModal    = false;
    this.selectedDeclaration = null;
  }

 onCorrectionSaved(event: { declaration: Declaration, comment: string }): void {
  const updated = event.declaration;
  const correctionComment = event.comment;
  this.closeCorrectModal();

  // Appel à la soumission avec le commentaire
  this.validationService.submitForValidation(updated.id!, correctionComment).subscribe({
    next: () => {
      this.toast.success('Déclaration corrigée et soumise pour validation !');
      this.loadDeclarations();
    },
    error: (err) => {
      this.toast.warning('Correction OK mais erreur soumission : ' + (err.error?.message || err.message));
      this.loadDeclarations();
    }
  });

  if (updated.id) this.jiraTickets.delete(updated.id);
  setTimeout(() => this.loadDeclarations(), 1000);
}

  onCorrectOverlayClick(event: MouseEvent): void {
    this.closeCorrectModal();
  }

  onCorrectXsdFileChange(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    if (!file.name.toLowerCase().endsWith('.xsd')) { this.toast.warning('Fichier .xsd uniquement'); return; }
    this.correctXsdFile = file;
    const reader = new FileReader();
    reader.onload = (e) => {
      const txt = e.target?.result as string;
      this.correctXsdPreviewContent = txt.length > 600 ? txt.substring(0, 600) + '\n...' : txt;
    };
    reader.readAsText(file);
  }

  onCorrectDragOver(event: DragEvent): void { event.preventDefault(); this.correctIsDraggingXsd = true; }

  onCorrectDrop(event: DragEvent): void {
    event.preventDefault();
    this.correctIsDraggingXsd = false;
    const file = event.dataTransfer?.files[0];
    if (!file?.name.toLowerCase().endsWith('.xsd')) { this.toast.warning('Fichier .xsd uniquement'); return; }
    this.correctXsdFile = file;
    const reader = new FileReader();
    reader.onload = (e) => { this.correctXsdPreviewContent = (e.target?.result as string).substring(0, 600); };
    reader.readAsText(file);
  }

  testCorrectSql(): void {
    if (!this.correctSqlQuery?.trim())                          { this.toast.warning('Saisissez une requête SQL'); return; }
    if (!this.correctTestDateDebut || !this.correctTestDateFin) { this.toast.warning('Saisissez les dates de test'); return; }
    if (!this.selectedDeclaration?.declarationType?.id)         return;

    this.correctTestingSQL    = true;
    this.correctSqlTestResult = null;
    const typeId = this.selectedDeclaration.declarationType.id;

    this.declarationTypeService.saveSqlQuery(typeId, this.correctSqlQuery).subscribe({
      next: () => {
        this.declarationTypeService
          .testSqlQuery(typeId, this.correctTestDateDebut, this.correctTestDateFin)
          .subscribe({
            next:  (r)   => { this.correctSqlTestResult = r; this.correctTestingSQL = false; },
            error: (err) => {
              this.correctSqlTestResult = { success: false, error: err.error?.error || err.message };
              this.correctTestingSQL    = false;
            }
          });
      },
      error: () => {
        this.correctSqlTestResult = { success: false, error: 'Impossible de sauvegarder la requête SQL' };
        this.correctTestingSQL    = false;
      }
    });
  }

  formatSqlCorrect(): void {
    if (!this.correctSqlQuery) return;
    const kw = ['SELECT','FROM','WHERE','JOIN','LEFT JOIN','RIGHT JOIN','INNER JOIN',
                 'ON','AND','OR','ORDER BY','GROUP BY','HAVING','BETWEEN','LIMIT'];
    let q = this.correctSqlQuery.replace(/\s+/g, ' ').trim();
    kw.forEach(k => { q = q.replace(new RegExp(`\\b${k}\\b`, 'gi'), `\n${k}`); });
    this.correctSqlQuery = q.trim();
  }

  saveAndResubmit(): void {
    this.formSubmitted = true;
    if (!this.correctSqlQuery?.trim()) { this.toast.warning('La requête SQL est obligatoire'); return; }
    if (!this.selectedDeclaration?.id) return;
    if (!this.selectedDeclaration?.declarationType?.id) return;

    this.loadingAction = true;
    const typeId = this.selectedDeclaration.declarationType.id;
    const decl   = this.selectedDeclaration;

    const doCorrectAndResubmit = () => {
      this.declarationTypeService.saveSqlQuery(typeId, this.correctSqlQuery).subscribe({
        next: () => {
          const req: GenerateDeclarationRequest = {
            declarationTypeId: typeId,
            periode:           decl.periode,
            dateDebut:         decl.dateDebut ?? '',
            dateFin:           decl.dateFin   ?? ''
          };
          this.declarationService.updateDeclaration(decl.id!, req).subscribe({
            next: (updated) => {
              this.jiraTickets.delete(updated.id!);
              this.validationService.submitForValidation(updated.id!).subscribe({
                next: () => {
                  this.toast.success('Déclaration corrigée et soumise pour validation !');
                  this.closeCorrectModal();
                  this.loadDeclarations();
                  this.loadingAction = false;
                },
                error: (err) => {
                  this.toast.warning('Correction OK mais erreur soumission : ' + (err.error?.message || err.message));
                  this.closeCorrectModal();
                  this.loadDeclarations();
                  this.loadingAction = false;
                }
              });
            },
            error: (err) => {
              this.toast.error('Erreur régénération : ' + (err.error?.message || err.message));
              this.loadingAction = false;
            }
          });
        },
        error: (err) => {
          this.toast.error('Erreur sauvegarde SQL : ' + (err.error?.error || err.message));
          this.loadingAction = false;
        }
      });
    };

    if (this.correctXsdFile) {
      this.declarationTypeService.uploadXsd(typeId, this.correctXsdFile).subscribe({
        next:  () => doCorrectAndResubmit(),
        error: (err) => {
          this.toast.error('Erreur upload XSD : ' + (err.error?.error || err.message));
          this.loadingAction = false;
        }
      });
    } else {
      doCorrectAndResubmit();
    }
  }

  // ══════════════════════════════════════════════════════
  // ACTIONS TABLE
  // ══════════════════════════════════════════════════════

  downloadDeclaration(declaration: Declaration): void {
    if (!declaration.id) return;
    if (declaration.contenuFichier) {
      const mimeType = this.declarationService.resolveMimeType(declaration.nomFichier || '');
      const blob     = new Blob([declaration.contenuFichier], { type: mimeType });
      this.triggerDownload(blob, declaration.nomFichier || 'declaration');
      return;
    }
    this.declarationService.downloadDeclaration(declaration.id).subscribe({
      next:  (blob) => this.triggerDownload(blob, declaration.nomFichier || 'declaration'),
      error: ()     => this.toast.error('Erreur lors du téléchargement')
    });
  }

  private triggerDownload(blob: Blob, filename: string): void {
    const url = window.URL.createObjectURL(blob);
    const a   = document.createElement('a');
    a.href    = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
  }

  async submitForValidation(declaration: Declaration): Promise<void> {
    if (!declaration.id) return;
    if (!await this.confirmDialog.confirm(
      'Soumettre pour validation',
      'Soumettre cette déclaration pour validation ?',
      { detail: `Type : ${declaration.declarationType?.nom}\nPériode : ${declaration.periode}`, confirmLabel: 'Soumettre', type: 'info' }
    )) return;
    this.loadingAction = true;
    this.jiraTickets.delete(declaration.id);
    this.validationService.submitForValidation(declaration.id).subscribe({
      next: () => {
        this.toast.success('Soumis pour validation !');
        this.loadDeclarations();
        this.loadingAction = false;
      },
      error: (err) => {
        this.toast.error('Erreur : ' + (err.error?.message || err.message));
        this.loadingAction = false;
      }
    });
  }

  canSubmit(d: Declaration): boolean    { return d.statut === 'GENEREE'; }
  canDownload(d: Declaration): boolean  { return !!d.id && !!d.nomFichier; }
  canSendToBCT(d: Declaration): boolean { return d.statut === 'VALIDEE'; }

  async sendToBCT(declaration: Declaration): Promise<void> {
    if (!declaration.id) return;
    if (!await this.confirmDialog.confirm(
      'Terminer la déclaration',
      'Marquer cette déclaration comme traitée ?',
      { detail: `Type : ${declaration.declarationType?.nom}\nPériode : ${declaration.periode}\n\n⚠️ Action irréversible.`, confirmLabel: 'Terminer', type: 'info' }
    )) return;
    this.loadingAction = true;
    this.validationService.markAsSent(declaration.id).subscribe({
      next: () => {
        this.toast.success(`Déclaration ${declaration.declarationType?.code} traitée !`);
        this.jiraTickets.delete(declaration.id!);
        this.loadDeclarations();
        this.loadingAction = false;
      },
      error: (err) => {
        this.toast.error('Erreur : ' + (err.error?.message || err.message));
        this.loadingAction = false;
      }
    });
  }

  // ══════════════════════════════════════════════════════
  // HELPERS UI
  // ══════════════════════════════════════════════════════

  getSqlColumns():        string { return (this.sqlTestResult?.colonnesDisponibles ?? []).join(' - '); }
  getCorrectSqlColumns(): string { return (this.correctSqlTestResult?.colonnesDisponibles ?? []).join(' - '); }

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
    if (!d) return '-';
    try {
      return new Date(d).toLocaleDateString('fr-FR', {
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit'
      });
    } catch { return d; }
  }
}