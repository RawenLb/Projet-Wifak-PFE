// src/app/declaration-management/declaration-management.component.ts
// ✅ VERSION FINALE — Nouvelle interface mapping XSD ↔ SQL (Step 4)

import { Component, OnInit } from '@angular/core';
import {
  DeclarationService,
  Declaration,
  GenerateDeclarationRequest
} from '../services/Declaration.service';
import { DeclarationTypeService, DeclarationType } from '../services/declaration-type.service';
import { ValidationService } from '../services/Validation.service';
import { JiraService, JiraTicketResponse } from '../services/jira.service';

export interface FieldMapping {
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

  // ── Mapping XSD ↔ SQL ────────────────────────────────────────────
  mappingAnalysis:       any = null;
  fieldMappings:         FieldMapping[] = [];
  analyzingMapping       = false;
  mappingAnalysisError:  string | null = null;
  selectedMappingIndex:  number | null = null;

  constructor(
    private declarationService:     DeclarationService,
    private declarationTypeService: DeclarationTypeService,
    private validationService:      ValidationService,
    private jiraService:            JiraService
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
  // GENERATE MODAL
  // ══════════════════════════════════════════════════════

  openGenerateModal(): void { this.resetGenerateModal(); this.showGenerateModal = true; }
  closeGenerateModal(): void { this.showGenerateModal = false; this.resetGenerateModal(); }

  private resetGenerateModal(): void {
    this.currentStep             = 1;
    this.formSubmitted           = false;
    this.selectedDeclarationType = null;
    this.xsdFile                 = null;
    this.xsdPreviewContent       = '';
    this.isDraggingXsd           = false;
    this.sqlQuery                = '';
    this.sqlTestResult           = null;
    this.generateRequest         = { declarationTypeId: 0, periode: '', dateDebut: '', dateFin: '' };
    // Reset mapping
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

  // ── Stepper ────────────────────────────────────────────────────

  stepNext(): void {
    this.formSubmitted = true;
    if (this.currentStep === 1) {
      if (!this.generateRequest.declarationTypeId || !this.generateRequest.periode) return;
      this.formSubmitted = false;
      this.currentStep   = 2;
      return;
    }
    if (this.currentStep === 2) {
      const fmt = this.selectedDeclarationType?.format;
      if (fmt === 'XML' && !this.xsdFile) return;
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
    if (!file.name.toLowerCase().endsWith('.xsd')) { alert('Fichier .xsd uniquement'); return; }
    this.xsdFile = file;
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
    if (!file?.name.toLowerCase().endsWith('.xsd')) { alert('Fichier .xsd uniquement'); return; }
    this.xsdFile = file;
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

  clearSql(): void {
    if (confirm('Effacer la requête SQL ?')) { this.sqlQuery = ''; this.sqlTestResult = null; }
  }

  testSql(): void {
    if (!this.sqlQuery?.trim())                  { alert('Saisissez une requête SQL'); return; }
    if (!this.testDateDebut || !this.testDateFin) { alert('Saisissez les dates de test'); return; }
    if (!this.generateRequest.declarationTypeId) { alert('Sélectionnez un type'); return; }

    this.testingSQL    = true;
    this.sqlTestResult = null;

    this.declarationTypeService.saveSqlQuery(this.generateRequest.declarationTypeId, this.sqlQuery)
      .subscribe({
        next: () => {
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

  // ── Génération standard (CSV/TXT) ──────────────────────────────

  generateDeclaration(): void {
    this.formSubmitted = true;
    const fmt = this.selectedDeclarationType?.format;
    if (!this.generateRequest.declarationTypeId) { alert('Type obligatoire'); return; }
    if (!this.generateRequest.periode)            { alert('Période obligatoire'); return; }
    if (fmt === 'XML' && !this.xsdFile)           { alert('Fichier XSD obligatoire'); return; }
    if (!this.sqlQuery?.trim())                   { alert('Requête SQL obligatoire'); return; }
    if (!this.sqlQuery.trim().toUpperCase().startsWith('SELECT')) {
      alert('La requête doit commencer par SELECT'); return;
    }

    this.loadingAction = true;

    const doGenerate = () => {
      this.declarationTypeService.saveSqlQuery(this.generateRequest.declarationTypeId, this.sqlQuery)
        .subscribe({
          next: () => {
            this.declarationService.generateDeclaration(this.generateRequest).subscribe({
              next: (_saved) => {
                alert('Déclaration générée avec succès !');
                this.closeGenerateModal();
                this.loadingAction = false;
                setTimeout(() => { this.loadDeclarations(); }, 2500);
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
          error: (err) => { alert('Erreur upload XSD : ' + (err.error?.error || err.message)); this.loadingAction = false; }
        });
    } else {
      doGenerate();
    }
  }

  // ══════════════════════════════════════════════════════
  // ÉTAPE 4 — MAPPING XSD ↔ SQL
  // ══════════════════════════════════════════════════════

  goToMappingStep(): void {
    this.formSubmitted = true;
    if (!this.sqlQuery?.trim()) { alert('La requête SQL est obligatoire'); return; }
    if (!this.sqlQuery.trim().toUpperCase().startsWith('SELECT')) {
      alert('La requête doit commencer par SELECT'); return;
    }

    this.analyzingMapping     = true;
    this.mappingAnalysisError = null;
    this.selectedMappingIndex = null;

    this.declarationTypeService.saveSqlQuery(
      this.generateRequest.declarationTypeId, this.sqlQuery
    ).subscribe({
      next: () => {
        this.declarationService.analyzeMappingHttp({
          declarationTypeId: this.generateRequest.declarationTypeId,
          dateDebut:         this.generateRequest.dateDebut,
          dateFin:           this.generateRequest.dateFin
        }).subscribe({
          next: (analysis: any) => {
            this.mappingAnalysis  = analysis;
            this.analyzingMapping = false;
            this.buildFieldMappings(analysis);
            this.currentStep = 4;
          },
          error: (err: any) => {
            this.mappingAnalysisError = err.error?.error || err.message || 'Erreur analyse mapping';
            this.analyzingMapping     = false;
            this.currentStep = 4;
          }
        });
      },
      error: () => {
        this.mappingAnalysisError = 'Impossible de sauvegarder la requête SQL';
        this.analyzingMapping     = false;
        this.currentStep          = 4;
      }
    });
  }

  private buildFieldMappings(analysis: any): void {
    if (!analysis?.xsdFields) { this.fieldMappings = []; return; }
    this.fieldMappings = (analysis.xsdFields as any[]).map((field: any) => {
      const autoCol = analysis.autoMapped?.[field.name] ?? null;
      return {
        xsdFieldName: field.name,
        xsdFieldPath: field.path,
        xsdType:      field.type,
        required:     field.required,
        source:       autoCol ? 'SQL' : 'NONE',
        sqlColumn:    autoCol ?? '',
        staticValue:  ''
      } as FieldMapping;
    });
  }

  // ── Sélectionner un champ XSD ────────────────────────────────────
  selectMappingField(index: number): void {
    this.selectedMappingIndex = index;
  }

  // ── Assigner une colonne SQL au champ sélectionné ─────────────────
  assignSqlColumn(col: string): void {
    if (this.selectedMappingIndex === null) return;
    const fm = this.fieldMappings[this.selectedMappingIndex];
    if (fm.source === 'STATIC') return;

    fm.source    = 'SQL';
    fm.sqlColumn = col;

    // Passer automatiquement au prochain champ non mappé
    const next = this.fieldMappings.findIndex(
      (f, i) => i > this.selectedMappingIndex! && f.source === 'NONE'
    );
    if (next !== -1) {
      this.selectedMappingIndex = next;
    }
  }

  // ── Vérifier si une colonne SQL est déjà utilisée ─────────────────
  isSqlColumnUsed(col: string): boolean {
    return this.fieldMappings.some(
      (fm, i) => fm.source === 'SQL' && fm.sqlColumn === col && i !== this.selectedMappingIndex
    );
  }

  // ── Changer le mode source d'un champ ────────────────────────────
  setMappingSource(index: number, source: 'SQL' | 'STATIC' | 'NONE'): void {
    this.fieldMappings[index].source = source;
    if (source !== 'SQL')    this.fieldMappings[index].sqlColumn   = '';
    if (source !== 'STATIC') this.fieldMappings[index].staticValue = '';
    this.selectedMappingIndex = index;
  }

  // ── Compter les champs mappés ─────────────────────────────────────
  countMappedFields(): number {
    return this.fieldMappings.filter(m => m.source !== 'NONE').length;
  }

  // ── Colonnes SQL non utilisées ────────────────────────────────────
  getUnusedSqlColumns(): string[] {
    if (!this.mappingAnalysis?.sqlColumns) return [];
    const usedCols = new Set(
      this.fieldMappings
        .filter(m => m.source === 'SQL' && m.sqlColumn)
        .map(m => m.sqlColumn)
    );
    return (this.mappingAnalysis.sqlColumns as string[]).filter((c: string) => !usedCols.has(c));
  }

  // ── Champs obligatoires sans mapping ─────────────────────────────
  getMissingRequiredCount(): number {
    return this.fieldMappings.filter(m => m.required && m.source === 'NONE').length;
  }

  // ── Réinitialiser tous les mappings ──────────────────────────────
  resetMapping(): void {
    if (!confirm('Réinitialiser tous les mappings ?')) return;
    if (this.mappingAnalysis) {
      this.buildFieldMappings(this.mappingAnalysis);
    }
    this.selectedMappingIndex = null;
  }

  // ── Générer avec mapping ──────────────────────────────────────────
  generateDeclarationWithMapping(): void {
    this.formSubmitted = true;

    const missing = this.getMissingRequiredCount();
    if (missing > 0) {
      alert(`${missing} champ(s) obligatoire(s) du XSD n'ont pas de valeur. Assignez une colonne SQL ou une valeur statique.`);
      return;
    }

    const sqlWithoutCol = this.fieldMappings.filter(m => m.source === 'SQL' && !m.sqlColumn);
    if (sqlWithoutCol.length > 0) {
      alert(`${sqlWithoutCol.length} champ(s) marqués "SQL" n'ont pas de colonne sélectionnée.`);
      return;
    }

    this.loadingAction = true;

    const doUploadXsdThenGenerate = () => {
      this.declarationTypeService
        .saveSqlQuery(this.generateRequest.declarationTypeId, this.sqlQuery)
        .subscribe({
          next: () => {
            this.declarationService.generateDeclarationWithMapping({
              declarationTypeId: this.generateRequest.declarationTypeId,
              periode:           this.generateRequest.periode,
              dateDebut:         this.generateRequest.dateDebut,
              dateFin:           this.generateRequest.dateFin,
              mappings:          this.fieldMappings
            }).subscribe({
              next: (_saved: any) => {
                alert('✅ Déclaration générée avec mapping !');
                this.closeGenerateModal();
                this.loadingAction = false;
                setTimeout(() => this.loadDeclarations(), 2500);
              },
              error: (err: any) => {
                alert('❌ Erreur génération : ' + (err.error?.error || err.message));
                this.loadingAction = false;
              }
            });
          },
          error: (err: any) => {
            alert('Erreur sauvegarde SQL : ' + (err.error?.error || err.message));
            this.loadingAction = false;
          }
        });
    };

    if (this.xsdFile) {
      this.declarationTypeService.uploadXsd(
        this.generateRequest.declarationTypeId, this.xsdFile
      ).subscribe({
        next:  () => doUploadXsdThenGenerate(),
        error: (err: any) => {
          alert('Erreur upload XSD : ' + (err.error?.error || err.message));
          this.loadingAction = false;
        }
      });
    } else {
      doUploadXsdThenGenerate();
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
          alert('Déclaration mise à jour avec succès !');
          this.closeEditModal();
          this.loadDeclarations();
          this.loadingAction = false;
        },
        error: (err) => {
          alert('Erreur : ' + (err.error?.message || err.message || 'Erreur inconnue'));
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
        alert('Erreur suppression : ' + (err.error?.message || err.message));
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


   onCorrectionSaved(updated: Declaration): void {
    this.closeCorrectModal();
    // Supprimer le ticket Jira en cache pour forcer un rechargement
    if (updated.id) this.jiraTickets.delete(updated.id);
    // Recharger la liste avec un petit délai pour laisser le backend traiter
    setTimeout(() => this.loadDeclarations(), 1000);
  }
 
  /**
   * Fermer en cliquant sur l'overlay (en dehors de la modal)
   */
  onCorrectOverlayClick(event: MouseEvent): void {
    // La propagation est stopée sur modal-content, donc
    // ce handler n'est déclenché que sur l'overlay lui-même
    this.closeCorrectModal();
  }
  
  onCorrectXsdFileChange(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    if (!file.name.toLowerCase().endsWith('.xsd')) { alert('Fichier .xsd uniquement'); return; }
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
    if (!file?.name.toLowerCase().endsWith('.xsd')) { alert('Fichier .xsd uniquement'); return; }
    this.correctXsdFile = file;
    const reader = new FileReader();
    reader.onload = (e) => { this.correctXsdPreviewContent = (e.target?.result as string).substring(0, 600); };
    reader.readAsText(file);
  }

  testCorrectSql(): void {
    if (!this.correctSqlQuery?.trim())                          { alert('Saisissez une requête SQL'); return; }
    if (!this.correctTestDateDebut || !this.correctTestDateFin) { alert('Saisissez les dates de test'); return; }
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
    if (!this.correctSqlQuery?.trim()) { alert('La requête SQL est obligatoire'); return; }
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
                  alert('✅ Déclaration corrigée et soumise pour validation !');
                  this.closeCorrectModal();
                  this.loadDeclarations();
                  this.loadingAction = false;
                },
                error: (err) => {
                  alert('⚠️ Correction OK mais erreur soumission.\n' + (err.error?.message || err.message));
                  this.closeCorrectModal();
                  this.loadDeclarations();
                  this.loadingAction = false;
                }
              });
            },
            error: (err) => {
              alert('❌ Erreur régénération : ' + (err.error?.message || err.message));
              this.loadingAction = false;
            }
          });
        },
        error: (err) => {
          alert('❌ Erreur sauvegarde SQL : ' + (err.error?.error || err.message));
          this.loadingAction = false;
        }
      });
    };

    if (this.correctXsdFile) {
      this.declarationTypeService.uploadXsd(typeId, this.correctXsdFile).subscribe({
        next:  () => doCorrectAndResubmit(),
        error: (err) => {
          alert('❌ Erreur upload XSD : ' + (err.error?.error || err.message));
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
      error: ()     => alert('Erreur lors du téléchargement')
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

  submitForValidation(declaration: Declaration): void {
    if (!declaration.id) return;
    if (!confirm(`Soumettre pour validation ?\n\nType : ${declaration.declarationType?.nom}\nPériode : ${declaration.periode}`)) return;
    this.loadingAction = true;
    this.jiraTickets.delete(declaration.id);
    this.validationService.submitForValidation(declaration.id).subscribe({
      next: () => {
        alert('Soumis pour validation !');
        this.loadDeclarations();
        this.loadingAction = false;
      },
      error: (err) => {
        alert('Erreur : ' + (err.error?.message || err.message));
        this.loadingAction = false;
      }
    });
  }

  canSubmit(d: Declaration): boolean    { return d.statut === 'GENEREE'; }
  canDownload(d: Declaration): boolean  { return !!d.id && !!d.nomFichier; }
  canSendToBCT(d: Declaration): boolean { return d.statut === 'VALIDEE'; }

  sendToBCT(declaration: Declaration): void {
    if (!declaration.id) return;
    if (!confirm(`Envoyer à la BCT ?\n\nType : ${declaration.declarationType?.nom}\nPériode : ${declaration.periode}\n\n⚠️ Action irréversible.`)) return;
    this.loadingAction = true;
    this.validationService.markAsSent(declaration.id).subscribe({
      next: () => {
        alert(`✅ Déclaration ${declaration.declarationType?.code} envoyée à la BCT !`);
        this.jiraTickets.delete(declaration.id!);
        this.loadDeclarations();
        this.loadingAction = false;
      },
      error: (err) => {
        alert('Erreur : ' + (err.error?.message || err.message));
        this.loadingAction = false;
      }
    });
  }

  // ══════════════════════════════════════════════════════
  // HELPERS UI
  // ══════════════════════════════════════════════════════

  getSqlColumns(): string        { return (this.sqlTestResult?.colonnesDisponibles ?? []).join(' - '); }
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
    } catch { return d; }
  }
}