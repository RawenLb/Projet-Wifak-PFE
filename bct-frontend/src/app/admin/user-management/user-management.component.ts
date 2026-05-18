
import { Component, OnInit } from '@angular/core';
import { KeycloakAdminService, KeycloakUser, CreateUserRequest, RoleDTO } from '../../services/keycloak-admin.service';
import { ConfirmDialogService } from '../../services/confirm-dialog.service';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-user-management',
  templateUrl: './user-management.component.html',
  styleUrls: ['./user-management.component.scss']
})
export class UserManagementComponent implements OnInit {

  users: KeycloakUser[] = [];
  availableRoles: RoleDTO[] = [];

  loading: boolean = false;
  selectedUser: KeycloakUser | null = null;
  showCreateModal: boolean = false;
  showEditModal: boolean = false;
  showRoleModal: boolean = false;

  searchQuery: string = '';
  sidebarCollapsed: boolean = false;
  formSubmitted: boolean = false;

  activeTab: 'overview' | 'users' | 'roles' | 'activity' = 'overview';

  // Pagination
  currentPage: number = 1;
  itemsPerPage: number = 10;

  newUser: CreateUserRequest = {
    username: '',
    email: '',
    firstName: '',
    lastName: '',
    enabled: true,
    roles: []
  };

  editUser: KeycloakUser = {
    username: '',
    email: '',
    firstName: '',
    lastName: '',
    enabled: true
  };

  selectedRoles: string[] = [];
  userCurrentRoles: RoleDTO[] = [];

  constructor(
    private kcAdmin: KeycloakAdminService,
    private confirmDialog: ConfirmDialogService,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.loadUsers();
    this.loadRoles();
  }

  // ========== SIDEBAR ==========

  toggleSidebar(): void {
    this.sidebarCollapsed = !this.sidebarCollapsed;
  }

  // ========== STATS ==========

  getActiveUsersCount(): number {
    return this.users.filter(u => u.enabled).length;
  }

  getInactiveUsersCount(): number {
    return this.users.filter(u => !u.enabled).length;
  }

  getVerifiedEmailsCount(): number {
    return this.users.filter(u => u.emailVerified).length;
  }

  getVerifiedPct(): number {
    if (!this.users.length) return 0;
    return Math.round((this.getVerifiedEmailsCount() / this.users.length) * 100);
  }

  getNewThisMonth(): number {
    const now = Date.now();
    const thirtyDays = 30 * 24 * 60 * 60 * 1000;
    return this.users.filter(u =>
      u.createdTimestamp && (now - u.createdTimestamp) < thirtyDays
    ).length;
  }

  getUserCountByRole(roleName: string): number {
    return this.users.filter(u => u.roles?.includes(roleName)).length;
  }

  get roleDistribution() {
    const roles = [
      { name: 'ROLE_ADMIN',   label: 'Administrateur', colorClass: 'fill-red' },
      { name: 'ROLE_MANAGER', label: 'Manager',         colorClass: 'fill-amber' },
      { name: 'ROLE_AGENT',   label: 'Agent déclarant', colorClass: 'fill-blue' },
      { name: 'ROLE_AUDITOR', label: 'Auditeur',        colorClass: 'fill-green' },
    ];
    const counts = roles.map(r => ({
      ...r,
      count: this.getUserCountByRole(r.name)
    }));
    const max = Math.max(1, ...counts.map(c => c.count));
    return counts.map(c => ({
      ...c,
      pct: Math.round((c.count / max) * 100)
    }));
  }

  get recentActivity() {
    const types = [
      { description: 'Compte créé',                   label: 'Nouveau',  dotClass: 'dot-green', chipClass: 'chip-green' },
      { description: 'Rôle modifié',                  label: 'Modifié',  dotClass: 'dot-amber', chipClass: 'chip-amber' },
      { description: 'Compte désactivé',              label: 'Inactif',  dotClass: 'dot-red',   chipClass: 'chip-red'   },
      { description: "Email d'activation renvoyé",    label: 'Email',    dotClass: 'dot-blue',  chipClass: 'chip-blue'  },
    ];
    return this.users.slice(0, 5).map((u, i) => ({
      username: u.username,
      ...types[i % types.length]
    }));
  }

  // ========== UTILITY FUNCTIONS ==========

  getInitials(user: KeycloakUser): string {
    const firstInitial = user.firstName?.charAt(0)?.toUpperCase() || '';
    const lastInitial  = user.lastName?.charAt(0)?.toUpperCase() || '';
    return firstInitial + lastInitial || user.username?.charAt(0)?.toUpperCase() || '?';
  }

  getAvatarColor(username: string): string {
    const colors = [
      'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
      'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
      'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
      'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
      'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
      'linear-gradient(135deg, #30cfd0 0%, #330867 100%)',
    ];
    const hash = username?.split('').reduce((acc, char) => {
      return char.charCodeAt(0) + ((acc << 5) - acc);
    }, 0) || 0;
    return colors[Math.abs(hash) % colors.length];
  }

  formatRoleName(role: string): string {
    return role.replace('ROLE_', '');
  }

  getRoleClass(role: string): string {
    const roleMap: { [key: string]: string } = {
      'ROLE_ADMIN':   'role-admin',
      'ROLE_MANAGER': 'role-manager',
      'ROLE_AGENT':   'role-agent',
      'ROLE_AUDITOR': 'role-auditor'
    };
    return roleMap[role] || 'role-default';
  }

  // ========== PAGINATION ==========

  get paginatedUsers(): KeycloakUser[] {
    const start = (this.currentPage - 1) * this.itemsPerPage;
    return this.users.slice(start, start + this.itemsPerPage);
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.users.length / this.itemsPerPage));
  }

  nextPage(): void {
    if (this.currentPage < this.totalPages) this.currentPage++;
  }

  previousPage(): void {
    if (this.currentPage > 1) this.currentPage--;
  }

  // ========== LOAD DATA ==========

  loadUsers(): void {
    this.loading = true;
    this.kcAdmin.getUsers().subscribe({
      next: (users) => {
        this.users = users;
        this.loading = false;
      },
      error: (error) => {
        console.error('Erreur chargement utilisateurs:', error);
        this.loading = false;
        this.toast.error('Erreur lors du chargement des utilisateurs.');
      }
    });
  }

  loadRoles(): void {
    this.kcAdmin.getAllRoles().subscribe({
      next: (roles) => { this.availableRoles = roles; },
      error: (error) => { console.error('Erreur chargement rôles:', error); }
    });
  }

  searchUsers(): void {
    if (!this.searchQuery.trim()) {
      this.loadUsers();
      return;
    }
    this.loading = true;
    this.kcAdmin.searchUsers(this.searchQuery).subscribe({
      next: (users) => { this.users = users; this.loading = false; },
      error: (error) => { console.error('Erreur recherche:', error); this.loading = false; }
    });
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.loadUsers();
  }

  // ========== CREATE USER ==========

  openCreateModal(): void {
    this.formSubmitted = false;
    this.newUser = {
      username: '',
      email: '',
      firstName: '',
      lastName: '',
      enabled: true,
      roles: []
    };
    this.showCreateModal = true;
  }

  closeCreateModal(): void {
    this.showCreateModal = false;
    this.formSubmitted = false;
  }

  createUser(): void {
    this.formSubmitted = true;
    if (!this.newUser.username || !this.newUser.email) {
      this.toast.warning("Veuillez remplir tous les champs obligatoires (nom d'utilisateur, email)");
      return;
    }
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(this.newUser.email)) {
      this.toast.warning('Veuillez entrer une adresse email valide');
      return;
    }
    this.loading = true;
    this.kcAdmin.createUser(this.newUser).subscribe({
      next: () => {
        this.toast.success(`Compte créé avec succès ! Un email d'activation a été envoyé à ${this.newUser.email}.`);
        this.closeCreateModal();
        this.loadUsers();
        this.loading = false;
      },
      error: (error) => {
        this.toast.error('Erreur lors de la création: ' + (error.error?.error || error.message));
        this.loading = false;
      }
    });
  }

  toggleRoleSelection(roleName: string): void {
    const index = this.newUser.roles.indexOf(roleName);
    if (index > -1) {
      this.newUser.roles.splice(index, 1);
    } else {
      this.newUser.roles.push(roleName);
    }
  }

  isRoleSelected(roleName: string): boolean {
    return this.newUser.roles.includes(roleName);
  }

  // ========== EDIT USER ==========

  openEditModal(user: KeycloakUser): void {
    this.editUser = { ...user };
    this.showEditModal = true;
  }

  closeEditModal(): void {
    this.showEditModal = false;
  }

  saveUser(): void {
    if (!this.editUser.id) return;
    if (this.editUser.email) {
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!emailRegex.test(this.editUser.email)) {
        this.toast.warning('Veuillez entrer une adresse email valide');
        return;
      }
    }
    this.loading = true;
    this.kcAdmin.updateUser(this.editUser.id, this.editUser).subscribe({
      next: () => {
        this.toast.success('Utilisateur modifié avec succès !');
        this.closeEditModal();
        this.loadUsers();
        this.loading = false;
      },
      error: (error) => {
        console.error('Erreur modification:', error);
        this.toast.error('Erreur lors de la modification');
        this.loading = false;
      }
    });
  }

  // ========== DELETE USER ==========

  deleteUser(user: KeycloakUser): void {
    if (!user.id) return;
    this.confirmDialog.confirm(
      'Supprimer l\'utilisateur',
      `Supprimer l'utilisateur "${user.username}" ?`,
      { detail: 'Cette action est irréversible !', confirmLabel: 'Supprimer', type: 'danger' }
    ).then(confirmed => {
      if (!confirmed) return;
      this.loading = true;
      this.kcAdmin.deleteUser(user.id!).subscribe({
        next: () => {
          this.toast.success('Utilisateur supprimé avec succès !');
          this.loadUsers();
          this.loading = false;
        },
        error: (error) => {
          console.error('Erreur suppression:', error);
          this.toast.error('Erreur lors de la suppression');
          this.loading = false;
        }
      });
    });
  }

  // ========== TOGGLE STATUS ==========

  toggleUserStatus(user: KeycloakUser): void {
    if (!user.id) return;
    const newStatus = !user.enabled;
    const action = newStatus ? 'activer' : 'désactiver';
    this.confirmDialog.confirm(
      `${newStatus ? 'Activer' : 'Désactiver'} l'utilisateur`,
      `Voulez-vous ${action} l'utilisateur "${user.username}" ?`,
      { confirmLabel: newStatus ? 'Activer' : 'Désactiver', type: 'warning' }
    ).then(confirmed => {
      if (!confirmed) return;
      this.loading = true;
      this.kcAdmin.toggleUserStatus(user.id!, newStatus).subscribe({
        next: () => {
          this.toast.success(`Utilisateur ${action} avec succès !`);
          this.loadUsers();
          this.loading = false;
        },
        error: () => {
          this.toast.error("Erreur lors de l'opération");
          this.loading = false;
        }
      });
    });
  }

  // ========== ROLE MANAGEMENT ==========

  openRoleModal(user: KeycloakUser): void {
    if (!user.id) return;
    this.selectedUser = user;
    this.showRoleModal = true;
    this.kcAdmin.getUserRoles(user.id).subscribe({
      next: (roles) => { this.userCurrentRoles = roles; },
      error: (error) => { console.error('Erreur chargement rôles utilisateur:', error); }
    });
  }

  closeRoleModal(): void {
    this.showRoleModal = false;
    this.selectedUser = null;
    this.userCurrentRoles = [];
  }

  hasRole(roleName: string): boolean {
    return this.userCurrentRoles.some(r => r.name === roleName);
  }

  assignRole(roleName: string): void {
    if (!this.selectedUser?.id) return;
    this.loading = true;
    this.kcAdmin.assignRoles(this.selectedUser.id, [roleName]).subscribe({
      next: () => {
        this.toast.success(`Rôle "${roleName}" assigné avec succès !`);
        this.openRoleModal(this.selectedUser!);
        this.loadUsers();
        this.loading = false;
      },
      error: () => {
        this.toast.error("Erreur lors de l'assignation du rôle");
        this.loading = false;
      }
    });
  }

  removeRole(roleName: string): void {
    if (!this.selectedUser?.id) return;
    this.confirmDialog.confirm(
      'Retirer le rôle',
      `Retirer le rôle "${roleName}" de "${this.selectedUser.username}" ?`,
      { confirmLabel: 'Retirer', type: 'warning' }
    ).then(confirmed => {
      if (!confirmed) return;
      this.loading = true;
      this.kcAdmin.removeRoles(this.selectedUser!.id!, [roleName]).subscribe({
        next: () => {
          this.toast.success(`Rôle "${roleName}" retiré avec succès !`);
          this.openRoleModal(this.selectedUser!);
          this.loadUsers();
          this.loading = false;
        },
        error: () => {
          this.toast.error('Erreur lors du retrait du rôle');
          this.loading = false;
        }
      });
    });
  }

  // ========== PASSWORD RESET ==========

  resetPassword(user: KeycloakUser): void {
    if (!user.id) return;
    this.confirmDialog.confirm(
      'Renvoyer l\'email d\'activation',
      `Renvoyer l'email d'activation à ${user.email} ?`,
      { confirmLabel: 'Envoyer', type: 'info' }
    ).then(confirmed => {
      if (!confirmed) return;
      this.loading = true;
      this.kcAdmin.sendPasswordResetEmail(user.id!).subscribe({
        next: () => {
          this.toast.success(`Email d'activation renvoyé à ${user.email} !`);
          this.loading = false;
        },
        error: () => {
          this.toast.error("Erreur lors de l'envoi de l'email");
          this.loading = false;
        }
      });
    });
  }

  // ========== ROLE PICKER HELPERS ==========

  getRoleKey(roleName: string): string {
    const name = roleName.replace('ROLE_', '').toLowerCase();
    if (name.includes('admin'))   return 'admin';
    if (name.includes('manager')) return 'manager';
    if (name.includes('agent'))   return 'agent';
    if (name.includes('auditor')) return 'auditor';
    return name;
  }

  getRoleDefaultDesc(roleName: string): string {
    const key = this.getRoleKey(roleName);
    const descs: Record<string, string> = {
      admin:   'Accès complet à toutes les fonctionnalités',
      manager: 'Validation et supervision des déclarations',
      agent:   'Soumission et gestion des déclarations BCT',
      auditor: 'Consultation et audit du journal des opérations',
    };
    return descs[key] || 'Accès selon les permissions configurées';
  }

  getSelectedRolesCount(): number {
    return this.newUser.roles.length;
  }

  getSelectedRoleNames(): string[] {
    return this.newUser.roles;
  }

  // ========== LOGOUT ==========

  logout(): void {
    this.confirmDialog.confirm(
      'Déconnexion',
      'Voulez-vous vous déconnecter ?',
      { confirmLabel: 'Déconnecter', type: 'warning' }
    ).then(confirmed => {
      if (confirmed) this.kcAdmin.logout();
    });
  }
}

