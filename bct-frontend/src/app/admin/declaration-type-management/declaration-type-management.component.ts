
import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { DeclarationTypeService, DeclarationType, CreateDeclarationTypeRequest } from '../../services/declaration-type.service';
import { ConfirmDialogService } from '../../services/confirm-dialog.service';
import { ToastService } from '../../services/toast.service';

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

  activeTab: 'overview' | 'types' | 'byFrequence' | 'inactifs' = 'overview';

  // Pagination
  currentPage: number = 1;
  itemsPerPage: number = 10;

  // Modals
  showCreateModal: boolean = false;
  showEditModal: boolean = false;
  showDetailsModal: boolean = false;
  formSubmitted: boolean = false;

  selectedDeclarationType: DeclarationType | null = null;

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

  formatOptions = ['XML', 'TXT', 'CSV', 'PDF'];
  frequenceOptions = ['QUOTIDIENNE', 'HEBDOMADAIRE', 'MENSUELLE', 'TRIMESTRIELLE', 'ANNUELLE'];

  constructor(
    private declarationTypeService: DeclarationTypeService,
    private router: Router,
    private confirmDialog: ConfirmDialogService,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.loadDeclarationTypes();
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

  // ========== OVERVIEW COMPUTED ==========

  get freqDistribution() {
    const freqs = [
      { key: 'MENSUELLE',     label: 'Mensuelle',     colorClass: 'fill-blue'   },
      { key: 'TRIMESTRIELLE', label: 'Trimestrielle', colorClass: 'fill-amber'  },
      { key: 'QUOTIDIENNE',   label: 'Quotidienne',   colorClass: 'fill-green'  },
      { key: 'HEBDOMADAIRE',  label: 'Hebdomadaire',  colorClass: 'fill-purple' },
      { key: 'ANNUELLE',      label: 'Annuelle',      colorClass: 'fill-gray'   },
    ];
    const counts = freqs.map(f => ({
      ...f,
      count: this.declarationTypes.filter(d => d.frequence === f.key).length
    }));
    const max = Math.max(1, ...counts.map(c => c.count));
    return counts.map(c => ({
      ...c,
      pct: Math.round((c.count / max) * 100)
    }));
  }

  get inactifTypes(): DeclarationType[] {
    return this.declarationTypes.filter(d => !d.actif);
  }

  get upcomingDeadlines(): DeclarationType[] {
    return this.declarationTypes.filter(d => d.actif).slice(0, 4);
  }

  getTypesByFreq(freq: string): DeclarationType[] {
    return this.declarationTypes.filter(d => d.frequence === freq);
  }

  // ========== PAGINATION ==========

  get paginatedDeclarationTypes(): DeclarationType[] {
    const start = (this.currentPage - 1) * this.itemsPerPage;
    return this.declarationTypes.slice(start, start + this.itemsPerPage);
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.declarationTypes.length / this.itemsPerPage));
  }

  nextPage(): void {
    if (this.currentPage < this.totalPages) this.currentPage++;
  }

  previousPage(): void {
    if (this.currentPage > 1) this.currentPage--;
  }

  // ========== LOAD ==========

  loadDeclarationTypes(): void {
    this.loading = true;
    this.declarationTypeService.getAll().subscribe({
      next: (types) => {
        this.declarationTypes = types;
        this.loading = false;
      },
      error: (err) => {
        console.error('Erreur chargement types:', err);
        this.loading = false;
        this.toast.error('Erreur lors du chargement des types de déclaration.');
      }
    });
  }

  searchDeclarationTypes(): void {
    if (!this.searchQuery.trim()) {
      this.loadDeclarationTypes();
      return;
    }
    const q = this.searchQuery.toLowerCase();
    this.declarationTypes = this.declarationTypes.filter(d =>
      d.code.toLowerCase().includes(q) ||
      d.nom.toLowerCase().includes(q) ||
      d.format.toLowerCase().includes(q) ||
      d.frequence.toLowerCase().includes(q)
    );
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.loadDeclarationTypes();
  }

  navigateTo(route: string): void {
    this.router.navigate([route]);
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
      return;
    }
    this.loading = true;
    this.declarationTypeService.create(this.newDeclarationType).subscribe({
      next: () => {
        this.toast.success('Type de déclaration créé avec succès !');
        this.closeCreateModal();
        this.loadDeclarationTypes();
        this.loading = false;
      },
      error: (err) => {
        const msg = err.error?.message || err.message || 'Erreur inconnue';
        this.toast.error('Erreur lors de la création: ' + msg);
        this.loading = false;
      }
    });
  }

  // ========== EDIT ==========

  openEditModal(type: DeclarationType): void {
    this.editDeclarationType = { ...type };
    this.showEditModal = true;
  }

  closeEditModal(): void {
    this.showEditModal = false;
  }

  saveDeclarationType(): void {
    if (!this.editDeclarationType.id) return;
    if (!this.editDeclarationType.nom || !this.editDeclarationType.dateLimite) {
      this.toast.warning('Veuillez remplir tous les champs obligatoires');
      return;
    }
    this.loading = true;
    this.declarationTypeService.update(this.editDeclarationType.id, this.editDeclarationType).subscribe({
      next: () => {
        this.toast.success('Type de déclaration modifié avec succès !');
        this.closeEditModal();
        this.loadDeclarationTypes();
        this.loading = false;
      },
      error: (err) => {
        const msg = err.error?.message || err.message || 'Erreur inconnue';
        this.toast.error('Erreur lors de la modification: ' + msg);
        this.loading = false;
      }
    });
  }

  // ========== DELETE ==========

  deleteDeclarationType(type: DeclarationType): void {
    if (!type.id) return;
    this.confirmDialog.confirm(
      'Supprimer le type',
      `Supprimer le type "${type.nom}" ?`,
      { detail: 'Cette action est irréversible !', confirmLabel: 'Supprimer', type: 'danger' }
    ).then(confirmed => {
      if (!confirmed) return;
      this.loading = true;
      this.declarationTypeService.delete(type.id!).subscribe({
        next: () => {
          this.toast.success('Type supprimé avec succès !');
          this.loadDeclarationTypes();
          this.loading = false;
        },
        error: (err) => {
          const msg = err.error?.message || err.message;
          this.toast.error('Erreur lors de la suppression: ' + msg);
          this.loading = false;
        }
      });
    });
  }

  // ========== TOGGLE STATUS ==========

  toggleStatus(type: DeclarationType): void {
    if (!type.id) return;
    const action = type.actif ? 'désactiver' : 'activer';
    this.confirmDialog.confirm(
      `${type.actif ? 'Désactiver' : 'Activer'} le type`,
      `Voulez-vous ${action} le type "${type.nom}" ?`,
      { confirmLabel: type.actif ? 'Désactiver' : 'Activer', type: 'warning' }
    ).then(confirmed => {
      if (!confirmed) return;
      this.loading = true;
      this.declarationTypeService.toggleStatus(type.id!).subscribe({
        next: () => {
          this.toast.success(`Type ${action} avec succès !`);
          this.loadDeclarationTypes();
          this.loading = false;
        },
        error: () => {
          this.toast.error(`Erreur lors de l'opération`);
          this.loading = false;
        }
      });
    });
  }

  // ========== DETAILS ==========

  openDetailsModal(type: DeclarationType): void {
    this.selectedDeclarationType = type;
    this.showDetailsModal = true;
  }

  closeDetailsModal(): void {
    this.showDetailsModal = false;
    this.selectedDeclarationType = null;
  }

  get activeDeclarationTypes(): DeclarationType[] {
    return this.declarationTypes.filter(d => d.actif).slice(0, 6);
  }

  getFreqKpiClass(colorClass: string): string {
    const map: Record<string, string> = {
      'fill-blue':   'kpi-navy',
      'fill-amber':  'kpi-amber',
      'fill-green':  'kpi-green',
      'fill-purple': 'kpi-purple',
      'fill-gray':   'kpi-neutral',
    };
    return map[colorClass] || 'kpi-neutral';
  }

  // ========== UTILITIES ==========

  getFormatBadgeClass(format: string): string {
    const map: { [k: string]: string } = {
      'XML': 'format-xml', 'CSV': 'format-csv',
      'JSON': 'format-json', 'PDF': 'format-pdf', 'TXT': 'format-txt'
    };
    return map[format] || 'format-default';
  }

  getFrequenceBadgeClass(frequence: string): string {
    const map: { [k: string]: string } = {
      'QUOTIDIENNE':   'freq-daily',
      'HEBDOMADAIRE':  'freq-weekly',
      'MENSUELLE':     'freq-monthly',
      'TRIMESTRIELLE': 'freq-quarterly',
      'ANNUELLE':      'freq-yearly'
    };
    return map[frequence] || 'freq-default';
  }

  getFreqUrgencyClass(frequence: string): string {
    const map: { [k: string]: string } = {
      'QUOTIDIENNE':  'chip-red',
      'HEBDOMADAIRE': 'chip-amber',
      'MENSUELLE':    'chip-blue',
      'TRIMESTRIELLE':'chip-gray',
      'ANNUELLE':     'chip-gray'
    };
    return map[frequence] || 'chip-gray';
  }
}
