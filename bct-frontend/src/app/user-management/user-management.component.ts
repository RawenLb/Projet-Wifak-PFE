import { Component, OnInit } from '@angular/core';
import { KeycloakAdminService, KeycloakUser, CreateUserRequest, RoleDTO } from '../services/keycloak-admin.service';

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
  showPassword: boolean = false;
  formSubmitted: boolean = false;
  
  // Pagination
  currentPage: number = 1;
  itemsPerPage: number = 10;
  
  // New user form
  newUser: CreateUserRequest = {
    username: '',
    email: '',
    firstName: '',
    lastName: '',
    password: '',
    enabled: true,
    roles: []
  };

  // Edit user form
  editUser: KeycloakUser = {
    username: '',
    email: '',
    firstName: '',
    lastName: '',
    enabled: true
  };

  // Selected roles for assignment
  selectedRoles: string[] = [];
  userCurrentRoles: RoleDTO[] = [];

  constructor(private kcAdmin: KeycloakAdminService) {}

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

  // ========== UTILITY FUNCTIONS ==========
  
  getInitials(user: KeycloakUser): string {
    const firstInitial = user.firstName?.charAt(0)?.toUpperCase() || '';
    const lastInitial = user.lastName?.charAt(0)?.toUpperCase() || '';
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
      'linear-gradient(135deg, #a8edea 0%, #fed6e3 100%)',
      'linear-gradient(135deg, #ff9a9e 0%, #fecfef 100%)'
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
    const roleMap: {[key: string]: string} = {
      'ROLE_ADMIN': 'role-admin',
      'ROLE_MANAGER': 'role-manager',
      'ROLE_AGENT': 'role-agent',
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
    return Math.ceil(this.users.length / this.itemsPerPage);
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

  loadUsers(): void {
    this.loading = true;
    this.kcAdmin.getUsers().subscribe({
      next: (users) => {
        this.users = users;
        this.loading = false;
        console.log('✅ Users loaded:', users.length);
      },
      error: (error) => {
        console.error('❌ Error loading users:', error);
        this.loading = false;
        alert('Erreur lors du chargement des utilisateurs. Vérifiez que vous êtes admin.');
      }
    });
  }

  loadRoles(): void {
    this.kcAdmin.getAllRoles().subscribe({
      next: (roles) => {
        this.availableRoles = roles;
        console.log('✅ Roles loaded:', roles.length);
      },
      error: (error) => {
        console.error('❌ Error loading roles:', error);
      }
    });
  }

  searchUsers(): void {
    if (!this.searchQuery.trim()) {
      this.loadUsers();
      return;
    }

    this.loading = true;
    this.kcAdmin.searchUsers(this.searchQuery).subscribe({
      next: (users) => {
        this.users = users;
        this.loading = false;
        console.log('✅ Search results:', users.length);
      },
      error: (error) => {
        console.error('❌ Error searching users:', error);
        this.loading = false;
      }
    });
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.loadUsers();
  }

  // ========== CREATE USER ==========

  openCreateModal(): void {
    this.formSubmitted = false;
    this.showPassword = false;
    this.newUser = {
      username: '',
      email: '',
      firstName: '',
      lastName: '',
      password: '',
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

    if (!this.newUser.username || !this.newUser.email || !this.newUser.password) {
      alert('Veuillez remplir tous les champs obligatoires (username, email, password)');
      return;
    }

    // Email validation
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(this.newUser.email)) {
      alert('Veuillez entrer une adresse email valide');
      return;
    }

    // Password validation
    if (this.newUser.password.length < 8) {
      alert('Le mot de passe doit contenir au moins 8 caractères');
      return;
    }

    this.loading = true;
    this.kcAdmin.createUser(this.newUser).subscribe({
      next: (response) => {
        console.log('✅ User created:', response);
        alert('Utilisateur créé avec succès!');
        this.closeCreateModal();
        this.loadUsers();
        this.loading = false;
      },
      error: (error) => {
        console.error('❌ Error creating user:', error);
        alert('Erreur lors de la création: ' + (error.error?.error || error.message));
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

    // Email validation
    if (this.editUser.email) {
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!emailRegex.test(this.editUser.email)) {
        alert('Veuillez entrer une adresse email valide');
        return;
      }
    }

    this.loading = true;
    this.kcAdmin.updateUser(this.editUser.id, this.editUser).subscribe({
      next: () => {
        console.log('✅ User updated');
        alert('Utilisateur modifié avec succès!');
        this.closeEditModal();
        this.loadUsers();
        this.loading = false;
      },
      error: (error) => {
        console.error('❌ Error updating user:', error);
        alert('Erreur lors de la modification');
        this.loading = false;
      }
    });
  }

  // ========== DELETE USER ==========

  deleteUser(user: KeycloakUser): void {
    if (!user.id) return;

    const confirmDelete = confirm(
      `⚠️ Êtes-vous sûr de vouloir supprimer l'utilisateur "${user.username}"?\n\nCette action est irréversible!`
    );

    if (!confirmDelete) return;

    this.loading = true;
    this.kcAdmin.deleteUser(user.id).subscribe({
      next: () => {
        console.log('✅ User deleted');
        alert('Utilisateur supprimé avec succès!');
        this.loadUsers();
        this.loading = false;
      },
      error: (error) => {
        console.error('❌ Error deleting user:', error);
        alert('Erreur lors de la suppression');
        this.loading = false;
      }
    });
  }

  // ========== TOGGLE STATUS ==========

  toggleUserStatus(user: KeycloakUser): void {
    if (!user.id) return;

    const newStatus = !user.enabled;
    const action = newStatus ? 'activer' : 'désactiver';

    const confirmToggle = confirm(
      `Voulez-vous ${action} l'utilisateur "${user.username}"?`
    );

    if (!confirmToggle) return;

    this.loading = true;
    this.kcAdmin.toggleUserStatus(user.id, newStatus).subscribe({
      next: () => {
        console.log(`✅ User ${action}d`);
        alert(`Utilisateur ${action} avec succès!`);
        this.loadUsers();
        this.loading = false;
      },
      error: (error) => {
        console.error(`❌ Error ${action}ing user:`, error);
        alert(`Erreur lors de l'${action}tion`);
        this.loading = false;
      }
    });
  }

  // ========== ROLE MANAGEMENT ==========

  openRoleModal(user: KeycloakUser): void {
    if (!user.id) return;
    
    this.selectedUser = user;
    this.showRoleModal = true;
    
    // Load user's current roles
    this.kcAdmin.getUserRoles(user.id).subscribe({
      next: (roles) => {
        this.userCurrentRoles = roles;
        console.log('✅ User roles loaded:', roles);
      },
      error: (error) => {
        console.error('❌ Error loading user roles:', error);
      }
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
        console.log('✅ Role assigned');
        alert(`Rôle "${roleName}" assigné avec succès!`);
        this.openRoleModal(this.selectedUser!);
        this.loadUsers();
        this.loading = false;
      },
      error: (error) => {
        console.error('❌ Error assigning role:', error);
        alert('Erreur lors de l\'assignation du rôle');
        this.loading = false;
      }
    });
  }

  removeRole(roleName: string): void {
    if (!this.selectedUser?.id) return;

    const confirmRemove = confirm(
      `Retirer le rôle "${roleName}" de l'utilisateur "${this.selectedUser.username}"?`
    );

    if (!confirmRemove) return;

    this.loading = true;
    this.kcAdmin.removeRoles(this.selectedUser.id, [roleName]).subscribe({
      next: () => {
        console.log('✅ Role removed');
        alert(`Rôle "${roleName}" retiré avec succès!`);
        this.openRoleModal(this.selectedUser!);
        this.loadUsers();
        this.loading = false;
      },
      error: (error) => {
        console.error('❌ Error removing role:', error);
        alert('Erreur lors du retrait du rôle');
        this.loading = false;
      }
    });
  }

  // ========== PASSWORD RESET ==========

  resetPassword(user: KeycloakUser): void {
    if (!user.id) return;

    const confirmReset = confirm(
      `Envoyer un email de réinitialisation de mot de passe à ${user.email}?`
    );

    if (!confirmReset) return;

    this.loading = true;
    this.kcAdmin.sendPasswordResetEmail(user.id).subscribe({
      next: () => {
        console.log('✅ Password reset email sent');
        alert('Email de réinitialisation envoyé avec succès!');
        this.loading = false;
      },
      error: (error) => {
        console.error('❌ Error sending reset email:', error);
        alert('Erreur lors de l\'envoi de l\'email');
        this.loading = false;
      }
    });
  }


   logout(): void {
    if (!confirm('Voulez-vous vous déconnecter ?')) return;
    this.kcAdmin.logout();
  }
}


