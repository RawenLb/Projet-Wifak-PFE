import { Component, OnInit } from '@angular/core';
import { DeclarationTypeService, DeclarationType, CreateDeclarationTypeRequest, ValidationRule } from '../services/declaration-type.service';

@Component({
  selector: 'app-declaration-type-management',
  templateUrl: './declaration-type-management.component.html',
  styleUrls: ['./declaration-type-management.component.scss']
})
export class DeclarationTypeManagementComponent implements OnInit {
  
  declarationTypes: DeclarationType[] = [];
  loading: boolean = false;
  sidebarCollapsed: boolean = false;
  
  searchQuery: string = '';
  
  // Pagination
  currentPage: number = 1;
  itemsPerPage: number = 10;
  
  // Modals
  showCreateModal: boolean = false;
  showEditModal: boolean = false;
  showDetailsModal: boolean = false;
  formSubmitted: boolean = false;
  
  // Validation errors
  validationErrors: string[] = [];
  
  // ✅ Initialisation complète avec template
  newDeclarationType: CreateDeclarationTypeRequest = {
    code: '',
    nom: '',
    description: '',
    format: 'XML',
    frequence: 'MENSUELLE',
    dateLimite: '',
    actif: true,
    champsObligatoires: '',
    template: {
      templateContent: '',
      variablesDisponibles: ''
    },
    validationRules: []
  };

  editDeclarationType: DeclarationType = {
    id: 0,
    code: '',
    nom: '',
    description: '',
    format: 'XML',
    frequence: 'MENSUELLE',
    dateLimite: '',
    actif: true,
    champsObligatoires: '',
    template: {
      templateContent: '',
      variablesDisponibles: ''
    },
    validationRules: []
  };

  selectedDeclarationType: DeclarationType | null = null;

  newValidationRule: ValidationRule = {
    champConcerne: '',
    typeValidation: 'CHAMP_OBLIGATOIRE',
    messageErreur: '',
    obligatoire: true
  };

  // Options
  formatOptions = ['XML', 'CSV', 'JSON', 'PDF', 'TXT'];
  frequenceOptions = ['QUOTIDIENNE', 'HEBDOMADAIRE', 'MENSUELLE', 'TRIMESTRIELLE', 'ANNUELLE'];
  
  validationTypeOptions = [
    'CHAMP_OBLIGATOIRE',
    'FORMAT_DATE',
    'FORMAT_MONTANT',
    'LONGUEUR_MIN',
    'LONGUEUR_MAX',
    'VALEUR_NUMERIQUE',
    'VALEUR_POSITIVE'
  ];

  constructor(private declarationTypeService: DeclarationTypeService) {}

  ngOnInit(): void {
    this.loadDeclarationTypes();
  }

  // ========== VALIDATION METHODS ==========
  
  /**
   * ✅ Valider le code (lettres majuscules, chiffres et underscore uniquement)
   */
  validateCode(code: string): boolean {
    const codePattern = /^[A-Z0-9_]+$/;
    return codePattern.test(code);
  }

  /**
   * ✅ Valider le format JSON des variables
   */
  validateJsonFormat(jsonString: string): boolean {
    if (!jsonString || jsonString.trim() === '') {
      return true; // JSON vide est accepté
    }
    
    try {
      JSON.parse(jsonString);
      return true;
    } catch (e) {
      return false;
    }
  }

  /**
   * ✅ Extraire les variables du template (format {{VARIABLE}})
   */
  extractTemplateVariables(templateContent: string): string[] {
    if (!templateContent) return [];
    
    const regex = /\{\{([A-Z_0-9]+)\}\}/g;
    const variables: string[] = [];
    let match;
    
    while ((match = regex.exec(templateContent)) !== null) {
      if (!variables.includes(match[1])) {
        variables.push(match[1]);
      }
    }
    
    return variables;
  }

  /**
   * ✅ Valider la cohérence entre template et variables JSON
   */
  validateTemplateConsistency(templateContent: string, variablesJson: string): string[] {
    const errors: string[] = [];
    
    if (!templateContent || !variablesJson) {
      return errors;
    }

    try {
      const templateVars = this.extractTemplateVariables(templateContent);
      const jsonVars = JSON.parse(variablesJson);
      const jsonKeys = Object.keys(jsonVars);

      // Vérifier que chaque variable du JSON existe dans le template
      jsonKeys.forEach(key => {
        if (!templateVars.includes(key)) {
          errors.push(`⚠️ Variable "${key}" définie dans JSON mais absente du template`);
        }
      });

      // Vérifier que chaque variable du template est dans le JSON
      templateVars.forEach(varName => {
        if (!jsonKeys.includes(varName)) {
          errors.push(`⚠️ Variable "{{${varName}}}" présente dans le template mais absente du JSON`);
        }
      });

    } catch (e) {
      errors.push('❌ Erreur lors de la validation: format JSON invalide');
    }

    return errors;
  }

  /**
   * ✅ Validation complète avant création/modification
   */
  validateDeclarationType(declarationType: CreateDeclarationTypeRequest | DeclarationType): string[] {
    const errors: string[] = [];

    // 1. Champs obligatoires
    if (!declarationType.code || declarationType.code.trim() === '') {
      errors.push('❌ Le code est obligatoire');
    }

    if (!declarationType.nom || declarationType.nom.trim() === '') {
      errors.push('❌ Le nom est obligatoire');
    }

    if (!declarationType.dateLimite || declarationType.dateLimite.trim() === '') {
      errors.push('❌ La date limite est obligatoire');
    }

    // 2. Validation du format du code
    if (declarationType.code && !this.validateCode(declarationType.code)) {
      errors.push('❌ Le code doit contenir uniquement des lettres majuscules, chiffres et underscores');
    }

    // 3. Validation du template
    if (declarationType.template) {
      // Valider le JSON des variables
      if (declarationType.template.variablesDisponibles && 
          !this.validateJsonFormat(declarationType.template.variablesDisponibles)) {
        errors.push('❌ Le format JSON des variables est invalide');
      }

      // Valider la cohérence template/JSON
      if (declarationType.template.templateContent && declarationType.template.variablesDisponibles) {
        const consistencyErrors = this.validateTemplateConsistency(
          declarationType.template.templateContent,
          declarationType.template.variablesDisponibles
        );
        errors.push(...consistencyErrors);
      }
    }

    return errors;
  }

  // ========== SIDEBAR ==========
  toggleSidebar(): void {
    this.sidebarCollapsed = !this.sidebarCollapsed;
  }

  // ========== STATS ==========
  getActiveCount(): number {
    return this.declarationTypes.filter(d => d.actif).length;
  }

  getInactiveCount(): number {
    return this.declarationTypes.filter(d => !d.actif).length;
  }

  getMonthlyCount(): number {
    return this.declarationTypes.filter(d => d.frequence === 'MENSUELLE').length;
  }

  // ========== PAGINATION ==========
  get paginatedDeclarationTypes(): DeclarationType[] {
    const start = (this.currentPage - 1) * this.itemsPerPage;
    return this.declarationTypes.slice(start, start + this.itemsPerPage);
  }

  get totalPages(): number {
    return Math.ceil(this.declarationTypes.length / this.itemsPerPage);
  }

  nextPage(): void {
    if (this.currentPage < this.totalPages) {
      this.currentPage++;
    }
  }

  previousPage(): void {
    if (this.currentPage > 1) {
      this.currentPage--;
    }
  }

  // ========== LOAD DATA ==========
  loadDeclarationTypes(): void {
    this.loading = true;
    this.declarationTypeService.getAll().subscribe({
      next: (types) => {
        this.declarationTypes = types;
        this.loading = false;
        console.log('✅ Declaration types loaded:', types.length);
      },
      error: (error) => {
        console.error('❌ Error loading declaration types:', error);
        this.loading = false;
        alert('Erreur lors du chargement des types de déclaration.');
      }
    });
  }

  searchDeclarationTypes(): void {
    if (!this.searchQuery.trim()) {
      this.loadDeclarationTypes();
      return;
    }

    const query = this.searchQuery.toLowerCase();
    this.declarationTypes = this.declarationTypes.filter(d => 
      d.code.toLowerCase().includes(query) ||
      d.nom.toLowerCase().includes(query) ||
      (d.description && d.description.toLowerCase().includes(query)) ||
      d.format.toLowerCase().includes(query) ||
      d.frequence.toLowerCase().includes(query)
    );
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.loadDeclarationTypes();
  }

  // ========== CREATE ==========
  openCreateModal(): void {
    this.formSubmitted = false;
    this.validationErrors = [];
    
    this.newDeclarationType = {
      code: '',
      nom: '',
      description: '',
      format: 'XML',
      frequence: 'MENSUELLE',
      dateLimite: '',
      actif: true,
      champsObligatoires: '',
      template: {
        templateContent: '',
        variablesDisponibles: ''
      },
      validationRules: []
    };
    this.showCreateModal = true;
  }

  closeCreateModal(): void {
    this.showCreateModal = false;
    this.formSubmitted = false;
    this.validationErrors = [];
  }

  createDeclarationType(): void {
    this.formSubmitted = true;
    this.validationErrors = [];

    // ✅ Validation complète
    const errors = this.validateDeclarationType(this.newDeclarationType);
    
    if (errors.length > 0) {
      this.validationErrors = errors;
      
      // Afficher les erreurs dans une alerte
      const errorMessage = errors.join('\n');
      alert('❌ Erreurs de validation:\n\n' + errorMessage);
      return;
    }

    this.loading = true;
    this.declarationTypeService.create(this.newDeclarationType).subscribe({
      next: () => {
        console.log('✅ Declaration type created');
        alert('✅ Type de déclaration créé avec succès!');
        this.closeCreateModal();
        this.loadDeclarationTypes();
        this.loading = false;
      },
      error: (error) => {
        console.error('❌ Error creating declaration type:', error);
        const errorMsg = error.error?.message || error.error?.error || error.message || 'Erreur inconnue';
        alert('❌ Erreur lors de la création:\n\n' + errorMsg);
        this.loading = false;
      }
    });
  }

  // ========== VALIDATION RULES ==========
  addValidationRule(): void {
    if (!this.newValidationRule.champConcerne || !this.newValidationRule.messageErreur) {
      alert('⚠️ Veuillez remplir tous les champs de la règle');
      return;
    }

    const targetRules = this.showCreateModal 
      ? this.newDeclarationType.validationRules 
      : this.editDeclarationType.validationRules;

    if (!targetRules) {
      if (this.showCreateModal) {
        this.newDeclarationType.validationRules = [];
      } else {
        this.editDeclarationType.validationRules = [];
      }
    }

    const rules = this.showCreateModal 
      ? this.newDeclarationType.validationRules! 
      : this.editDeclarationType.validationRules!;

    rules.push({...this.newValidationRule});

    // Reset
    this.newValidationRule = {
      champConcerne: '',
      typeValidation: 'CHAMP_OBLIGATOIRE',
      messageErreur: '',
      obligatoire: true
    };
  }

  removeValidationRule(index: number): void {
    if (this.showCreateModal) {
      if (this.newDeclarationType.validationRules) {
        this.newDeclarationType.validationRules.splice(index, 1);
      }
    } else {
      if (this.editDeclarationType.validationRules) {
        this.editDeclarationType.validationRules.splice(index, 1);
      }
    }
  }

  // ========== EDIT ==========
  openEditModal(declarationType: DeclarationType): void {
    this.validationErrors = [];
    
    this.editDeclarationType = { 
      ...declarationType,
      template: declarationType.template ? {
        ...declarationType.template
      } : {
        templateContent: '',
        variablesDisponibles: ''
      }
    };
    
    if (declarationType.validationRules) {
      this.editDeclarationType.validationRules = [...declarationType.validationRules];
    }
    
    this.showEditModal = true;
  }

  closeEditModal(): void {
    this.showEditModal = false;
    this.validationErrors = [];
  }

  saveDeclarationType(): void {
    if (!this.editDeclarationType.id) return;

    this.validationErrors = [];

    // ✅ Validation complète
    const errors = this.validateDeclarationType(this.editDeclarationType);
    
    if (errors.length > 0) {
      this.validationErrors = errors;
      const errorMessage = errors.join('\n');
      alert('❌ Erreurs de validation:\n\n' + errorMessage);
      return;
    }

    this.loading = true;
    this.declarationTypeService.update(this.editDeclarationType.id, this.editDeclarationType).subscribe({
      next: () => {
        console.log('✅ Declaration type updated');
        alert('✅ Type de déclaration modifié avec succès!');
        this.closeEditModal();
        this.loadDeclarationTypes();
        this.loading = false;
      },
      error: (error) => {
        console.error('❌ Error updating declaration type:', error);
        const errorMsg = error.error?.message || error.error?.error || error.message || 'Erreur inconnue';
        alert('❌ Erreur lors de la modification:\n\n' + errorMsg);
        this.loading = false;
      }
    });
  }

  // ========== DELETE ==========
  deleteDeclarationType(declarationType: DeclarationType): void {
    if (!declarationType.id) return;

    const confirmDelete = confirm(
      `⚠️ Êtes-vous sûr de vouloir supprimer le type "${declarationType.nom}"?\n\nCette action est irréversible!`
    );

    if (!confirmDelete) return;

    this.loading = true;
    this.declarationTypeService.delete(declarationType.id).subscribe({
      next: () => {
        console.log('✅ Declaration type deleted');
        alert('✅ Type de déclaration supprimé avec succès!');
        this.loadDeclarationTypes();
        this.loading = false;
      },
      error: (error) => {
        console.error('❌ Error deleting declaration type:', error);
        const errorMsg = error.error?.message || error.error?.error || error.message;
        alert('❌ Erreur lors de la suppression:\n\n' + errorMsg);
        this.loading = false;
      }
    });
  }

  // ========== TOGGLE STATUS ==========
  toggleStatus(declarationType: DeclarationType): void {
    if (!declarationType.id) return;

    const action = declarationType.actif ? 'désactiver' : 'activer';
    const confirmToggle = confirm(`Voulez-vous ${action} le type "${declarationType.nom}"?`);

    if (!confirmToggle) return;

    this.loading = true;
    this.declarationTypeService.toggleStatus(declarationType.id).subscribe({
      next: () => {
        console.log(`✅ Declaration type ${action}d`);
        alert(`✅ Type de déclaration ${action} avec succès!`);
        this.loadDeclarationTypes();
        this.loading = false;
      },
      error: (error) => {
        console.error(`❌ Error ${action}ing declaration type:`, error);
        alert(`❌ Erreur lors de l'${action}tion`);
        this.loading = false;
      }
    });
  }

  // ========== DETAILS ==========
  openDetailsModal(declarationType: DeclarationType): void {
    this.selectedDeclarationType = declarationType;
    this.showDetailsModal = true;
  }

  closeDetailsModal(): void {
    this.showDetailsModal = false;
    this.selectedDeclarationType = null;
  }

  downloadTemplate(declarationType: DeclarationType): void {
  if (!declarationType.id) return;

  this.declarationTypeService.downloadTemplate(declarationType.id).subscribe({
    next: (blob) => {
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      
      // ✅ FIX: Utiliser le format dynamique (ex: csv, xml, json)
      // On convertit en minuscule pour l'extension du fichier
      const extension = declarationType.format.toLowerCase();
      link.download = `template_${declarationType.code}.${extension}`;
      
      link.click();
      window.URL.revokeObjectURL(url);
      console.log(`✅ Template downloaded as ${extension}`);
    },
    error: (error) => {
      console.error('❌ Error downloading template:', error);
      alert('❌ Erreur lors du téléchargement du template');
    }
  });
}

  getChampsObligatoiresArray(champsObligatoires?: string): string[] {
    if (!champsObligatoires) return [];
    return champsObligatoires.split(',').map(c => c.trim()).filter(c => c);
  }

  // ========== UTILITY ==========
  getFormatBadgeClass(format: string): string {
    const classes: {[key: string]: string} = {
      'XML': 'format-xml',
      'CSV': 'format-csv',
      'JSON': 'format-json',
      'PDF': 'format-pdf',
      'TXT': 'format-txt'
    };
    return classes[format] || 'format-default';
  }

  getFrequenceBadgeClass(frequence: string): string {
    const classes: {[key: string]: string} = {
      'QUOTIDIENNE': 'freq-daily',
      'HEBDOMADAIRE': 'freq-weekly',
      'MENSUELLE': 'freq-monthly',
      'TRIMESTRIELLE': 'freq-quarterly',
      'ANNUELLE': 'freq-yearly'
    };
    return classes[frequence] || 'freq-default';
  }

  logout(): void {
    if (!confirm('Voulez-vous vous déconnecter ?')) return;
    // Call your logout service here
  }
}