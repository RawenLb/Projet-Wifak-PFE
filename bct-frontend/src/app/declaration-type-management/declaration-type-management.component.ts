import { Component, OnInit } from '@angular/core';
import { DeclarationTypeService, DeclarationType, CreateDeclarationTypeRequest } from '../services/declaration-type.service';
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
  formSubmitted: boolean = false;
  
  // Forms
  newDeclarationType: CreateDeclarationTypeRequest = {
    code: '',
    nom: '',
    format: 'XML',
    frequence: 'MENSUELLE',
    dateLimite: '',
    actif: true
  };

  editDeclarationType: DeclarationType = {
    id: 0,
    code: '',
    nom: '',
    format: 'XML',
    frequence: 'MENSUELLE',
    dateLimite: '',
    actif: true
  };

  // Options
  formatOptions = ['XML', 'CSV', 'JSON', 'PDF'];
  frequenceOptions = ['QUOTIDIENNE', 'HEBDOMADAIRE', 'MENSUELLE', 'TRIMESTRIELLE', 'ANNUELLE'];

  constructor(private declarationTypeService: DeclarationTypeService) {}

  ngOnInit(): void {
    this.loadDeclarationTypes();
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

  getXmlCount(): number {
    return this.declarationTypes.filter(d => d.format === 'XML').length;
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
    this.newDeclarationType = {
      code: '',
      nom: '',
      format: 'XML',
      frequence: 'MENSUELLE',
      dateLimite: '',
      actif: true
    };
    this.showCreateModal = true;
  }

  closeCreateModal(): void {
    this.showCreateModal = false;
    this.formSubmitted = false;
  }

  createDeclarationType(): void {
    this.formSubmitted = true;

    if (!this.newDeclarationType.code || !this.newDeclarationType.nom || !this.newDeclarationType.dateLimite) {
      alert('Veuillez remplir tous les champs obligatoires');
      return;
    }

    this.loading = true;
    this.declarationTypeService.create(this.newDeclarationType).subscribe({
      next: () => {
        console.log('✅ Declaration type created');
        alert('Type de déclaration créé avec succès!');
        this.closeCreateModal();
        this.loadDeclarationTypes();
        this.loading = false;
      },
      error: (error) => {
        console.error('❌ Error creating declaration type:', error);
        alert('Erreur lors de la création: ' + (error.error?.message || error.message));
        this.loading = false;
      }
    });
  }

  // ========== EDIT ==========
  openEditModal(declarationType: DeclarationType): void {
    this.editDeclarationType = { ...declarationType };
    this.showEditModal = true;
  }

  closeEditModal(): void {
    this.showEditModal = false;
  }

  saveDeclarationType(): void {
    if (!this.editDeclarationType.id) return;

    if (!this.editDeclarationType.code || !this.editDeclarationType.nom || !this.editDeclarationType.dateLimite) {
      alert('Veuillez remplir tous les champs obligatoires');
      return;
    }

    this.loading = true;
    this.declarationTypeService.update(this.editDeclarationType.id, this.editDeclarationType).subscribe({
      next: () => {
        console.log('✅ Declaration type updated');
        alert('Type de déclaration modifié avec succès!');
        this.closeEditModal();
        this.loadDeclarationTypes();
        this.loading = false;
      },
      error: (error) => {
        console.error('❌ Error updating declaration type:', error);
        alert('Erreur lors de la modification');
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
        alert('Type de déclaration supprimé avec succès!');
        this.loadDeclarationTypes();
        this.loading = false;
      },
      error: (error) => {
        console.error('❌ Error deleting declaration type:', error);
        alert('Erreur lors de la suppression');
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
        alert(`Type de déclaration ${action} avec succès!`);
        this.loadDeclarationTypes();
        this.loading = false;
      },
      error: (error) => {
        console.error(`❌ Error ${action}ing declaration type:`, error);
        alert(`Erreur lors de l'${action}tion`);
        this.loading = false;
      }
    });
  }

  // ========== UTILITY ==========
  getFormatBadgeClass(format: string): string {
    const classes: {[key: string]: string} = {
      'XML': 'format-xml',
      'CSV': 'format-csv',
      'JSON': 'format-json',
      'PDF': 'format-pdf'
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