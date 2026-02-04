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
  loading: boolean = false;
  sidebarCollapsed: boolean = false;
  
  searchQuery: string = '';
  
  // Pagination
  currentPage: number = 1;
  itemsPerPage: number = 10;
  
  // Modals
  showGenerateModal: boolean = false;
  showDetailsModal: boolean = false;
  showRejectModal: boolean = false;
  formSubmitted: boolean = false;
  
  // Génération
  selectedDeclarationType: DeclarationType | null = null;
  generateRequest: GenerateDeclarationRequest = {
    declarationTypeId: 0,
    periode: '',
    data: {}
  };
  
  // Détails
  selectedDeclaration: Declaration | null = null;
  
  // Rejet
  rejectComment: string = '';
  declarationToReject: Declaration | null = null;
  
  // Filtres
  filterStatut: string = 'ALL';
  filterPeriode: string = '';
  
  constructor(
    private declarationService: DeclarationService,
    private declarationTypeService: DeclarationTypeService
  ) {}

  ngOnInit(): void {
    this.loadDeclarations();
    this.loadDeclarationTypes();
  }

  // ========== LOAD DATA ==========
  
  loadDeclarations(): void {
    this.loading = true;
    this.declarationService.getMyDeclarations().subscribe({
      next: (declarations) => {
        this.declarations = declarations;
        this.loading = false;
        console.log('✅ Declarations loaded:', declarations.length);
      },
      error: (error) => {
        console.error('❌ Error loading declarations:', error);
        this.loading = false;
        alert('Erreur lors du chargement des déclarations.');
      }
    });
  }

  loadDeclarationTypes(): void {
    this.declarationTypeService.getAll().subscribe({
      next: (types) => {
        // Filtrer uniquement les types actifs
        this.declarationTypes = types.filter(t => t.actif);
        console.log('✅ Declaration types loaded:', this.declarationTypes.length);
      },
      error: (error) => {
        console.error('❌ Error loading declaration types:', error);
      }
    });
  }

  // ========== STATS ==========
  
  getStatsByStatus(statut: string): number {
    return this.declarations.filter(d => d.statut === statut).length;
  }

  // ========== PAGINATION ==========
  
  get filteredDeclarations(): Declaration[] {
    let filtered = [...this.declarations];
    
    // Filtre par statut
    if (this.filterStatut !== 'ALL') {
      filtered = filtered.filter(d => d.statut === this.filterStatut);
    }
    
    // Filtre par période
    if (this.filterPeriode) {
      filtered = filtered.filter(d => d.periode.includes(this.filterPeriode));
    }
    
    // Filtre par recherche
    if (this.searchQuery) {
      const query = this.searchQuery.toLowerCase();
      filtered = filtered.filter(d => 
        d.declarationType?.code.toLowerCase().includes(query) ||
        d.declarationType?.nom.toLowerCase().includes(query) ||
        d.periode.toLowerCase().includes(query) ||
        d.statut.toLowerCase().includes(query)
      );
    }
    
    return filtered;
  }

  get paginatedDeclarations(): Declaration[] {
    const start = (this.currentPage - 1) * this.itemsPerPage;
    return this.filteredDeclarations.slice(start, start + this.itemsPerPage);
  }

  get totalPages(): number {
    return Math.ceil(this.filteredDeclarations.length / this.itemsPerPage);
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

  // ========== SEARCH & FILTERS ==========
  
  searchDeclarations(): void {
    this.currentPage = 1; // Reset to first page
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.currentPage = 1;
  }

  applyFilters(): void {
    this.currentPage = 1;
  }

  clearFilters(): void {
    this.filterStatut = 'ALL';
    this.filterPeriode = '';
    this.currentPage = 1;
  }

  // ========== GENERATE MODAL ==========
  
  openGenerateModal(): void {
    this.formSubmitted = false;
    this.selectedDeclarationType = null;
    this.generateRequest = {
      declarationTypeId: 0,
      periode: '',
      data: {}
    };
    this.showGenerateModal = true;
  }

  closeGenerateModal(): void {
    this.showGenerateModal = false;
    this.formSubmitted = false;
  }

  onDeclarationTypeChange(): void {
    if (this.generateRequest.declarationTypeId) {
      this.selectedDeclarationType = this.declarationTypes.find(
        t => t.id === this.generateRequest.declarationTypeId
      ) || null;
      
      // Initialiser les données selon les variables du template
      if (this.selectedDeclarationType?.template?.variablesDisponibles) {
        try {
          const variables = JSON.parse(this.selectedDeclarationType.template.variablesDisponibles);
          this.generateRequest.data = {};
          Object.keys(variables).forEach(key => {
            this.generateRequest.data[key] = '';
          });
        } catch (e) {
          console.error('❌ Error parsing template variables:', e);
        }
      }
    }
  }

  getTemplateVariables(): string[] {
    if (!this.selectedDeclarationType?.template?.variablesDisponibles) {
      return [];
    }
    
    try {
      const variables = JSON.parse(this.selectedDeclarationType.template.variablesDisponibles);
      return Object.keys(variables);
    } catch (e) {
      return [];
    }
  }

  generateDeclaration(): void {
    this.formSubmitted = true;

    // Validation
    if (!this.generateRequest.declarationTypeId || !this.generateRequest.periode) {
      alert('⚠️ Veuillez remplir tous les champs obligatoires');
      return;
    }

    // Vérifier que toutes les variables sont remplies
    const variables = this.getTemplateVariables();
    const missingVars = variables.filter(v => !this.generateRequest.data[v]);
    
    if (missingVars.length > 0) {
      alert(`⚠️ Variables manquantes: ${missingVars.join(', ')}`);
      return;
    }

    this.loading = true;
    this.declarationService.generateDeclaration(this.generateRequest).subscribe({
      next: (declaration) => {
        console.log('✅ Declaration generated:', declaration);
        alert('✅ Déclaration générée avec succès!');
        this.closeGenerateModal();
        this.loadDeclarations();
        this.loading = false;
      },
      error: (error) => {
        console.error('❌ Error generating declaration:', error);
        const errorMsg = error.error?.message || error.message || 'Erreur inconnue';
        alert('❌ Erreur lors de la génération:\n\n' + errorMsg);
        this.loading = false;
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

    this.declarationService.downloadDeclaration(declaration.id).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = declaration.nomFichier || 'declaration.txt';
        link.click();
        window.URL.revokeObjectURL(url);
        console.log('✅ Declaration downloaded');
      },
      error: (error) => {
        console.error('❌ Error downloading declaration:', error);
        alert('❌ Erreur lors du téléchargement');
      }
    });
  }

  submitForValidation(declaration: Declaration): void {
    if (!declaration.id) return;

    const confirmSubmit = confirm(
      `Voulez-vous soumettre cette déclaration pour validation?\n\nType: ${declaration.declarationType?.nom}\nPériode: ${declaration.periode}`
    );

    if (!confirmSubmit) return;

    this.loading = true;
    this.declarationService.submitForValidation(declaration.id).subscribe({
      next: () => {
        console.log('✅ Declaration submitted');
        alert('✅ Déclaration soumise pour validation!');
        this.loadDeclarations();
        this.loading = false;
      },
      error: (error) => {
        console.error('❌ Error submitting declaration:', error);
        alert('❌ Erreur lors de la soumission');
        this.loading = false;
      }
    });
  }

  openRejectModal(declaration: Declaration): void {
    this.declarationToReject = declaration;
    this.rejectComment = '';
    this.showRejectModal = true;
  }

  closeRejectModal(): void {
    this.showRejectModal = false;
    this.declarationToReject = null;
    this.rejectComment = '';
  }

  rejectDeclaration(): void {
    if (!this.declarationToReject?.id || !this.rejectComment.trim()) {
      alert('⚠️ Veuillez saisir un commentaire de rejet');
      return;
    }

    this.loading = true;
    this.declarationService.rejectDeclaration(
      this.declarationToReject.id,
      this.rejectComment
    ).subscribe({
      next: () => {
        console.log('✅ Declaration rejected');
        alert('✅ Déclaration rejetée');
        this.closeRejectModal();
        this.loadDeclarations();
        this.loading = false;
      },
      error: (error) => {
        console.error('❌ Error rejecting declaration:', error);
        alert('❌ Erreur lors du rejet');
        this.loading = false;
      }
    });
  }

  // ========== UTILITY ==========
  
  getStatusBadgeClass(statut: string): string {
    const classes: {[key: string]: string} = {
      'BROUILLON': 'status-draft',
      'GENEREE': 'status-generated',
      'EN_VALIDATION': 'status-pending',
      'VALIDEE': 'status-validated',
      'REJETEE': 'status-rejected',
      'ENVOYEE': 'status-sent'
    };
    return classes[statut] || 'status-default';
  }

  getStatusLabel(statut: string): string {
    const labels: {[key: string]: string} = {
      'BROUILLON': 'Brouillon',
      'GENEREE': 'Générée',
      'EN_VALIDATION': 'En validation',
      'VALIDEE': 'Validée',
      'REJETEE': 'Rejetée',
      'ENVOYEE': 'Envoyée'
    };
    return labels[statut] || statut;
  }

  canSubmit(declaration: Declaration): boolean {
    return declaration.statut === 'GENEREE' || declaration.statut === 'REJETEE';
  }

  canDownload(declaration: Declaration): boolean {
    return !!declaration.contenuFichier;
  }

  formatDate(dateString: string | undefined): string {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return date.toLocaleDateString('fr-FR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }
}