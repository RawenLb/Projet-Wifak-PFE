// src/app/app-routing.module.ts
// ✅ Route '/' utilise RoleRedirectGuard → redirige vers le bon espace selon rôle

import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { RoleGuard } from './guards/role.guard';
import { RoleRedirectGuard } from './guards/Role-redirect.guard';
import { HomeComponent } from './home/home.component';
import { UserManagementComponent } from './user-management/user-management.component';
import { DeclarationTypeManagementComponent } from './declaration-type-management/declaration-type-management.component';
import { DashboardLayoutComponent } from './dashboard-layout/dashboard-layout.component';
import { AgentLayoutComponent } from './agent-layout/agent-layout.component';
import { DeclarationManagementComponent } from './declaration-management/declaration-management.component';
import { DeclarationCalendarComponent } from './declaration-calendar/declaration-calendar.component';
import { AgentDashboardComponent } from './agent-dashboard/agent-dashboard.component';
import { ManagerDashboardComponent } from './manager-dashboard/manager-dashboard.component';
import { ManagerLayoutComponent } from './manager-layout/manager-layout.component';
import { AgentDeclarationTypesComponent } from './agent-declaration-types/agent-declaration-types.component';
import { AgentNotificationsComponent } from './agent-notifications/agent-notifications.component';

// ✅ NOUVEAUX imports manager
import { ManagerCalendarComponent } from './manager-calendar/manager-calendar.component';
import { ManagerReportsComponent } from './manager-reports/manager-reports.component';
 import { ManagerHistoryComponent } from './manager-history/manager-history.component';
import { ManagerPendingComponent } from './manager-pending/manager-pending.component';
import { ManagerMlDashboardComponent } from './manager-ml-dashboard/manager-ml-dashboard.component';

const routes: Routes = [

  // ── Root : redirige selon le rôle ──────────────────────────
  // ✅ Manager → /manager/dashboard, Admin → /dashboard, Agent → /agent/dashboard
  {
    path: '',
    canActivate: [RoleRedirectGuard],
    component: HomeComponent  // jamais affiché, le guard redirige toujours
  },

  // ── Pages publiques ─────────────────────────────────────────
  { path: 'home',         component: HomeComponent },
  { path: 'unauthorized', component: HomeComponent },

  // ── Admin Dashboard ─────────────────────────────────────────
  {
    path: 'dashboard',
    component: DashboardLayoutComponent,
    canActivate: [RoleGuard],
    data: { roles: ['ROLE_ADMIN'] },
    children: [
      { path: '', component: HomeComponent }
    ]
  },

  // ── User Management ─────────────────────────────────────────
  {
    path: 'user-management',
    component: DashboardLayoutComponent,
    canActivate: [RoleGuard],
    data: { roles: ['ROLE_ADMIN'] },
    children: [{ path: '', component: UserManagementComponent }]
  },

  // ── Declaration Types ───────────────────────────────────────
  {
    path: 'declaration-type-management',
    component: DashboardLayoutComponent,
    canActivate: [RoleGuard],
    data: { roles: ['ROLE_ADMIN', 'ROLE_MANAGER'] },
    children: [{ path: '', component: DeclarationTypeManagementComponent }]
  },

  // ── Agent Space ─────────────────────────────────────────────
  {
    path: 'agent',
    component: AgentLayoutComponent,
    canActivate: [RoleGuard],
    data: { roles: ['ROLE_AGENT'] },
    children: [
      { path: '',               redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard',      component: AgentDashboardComponent },
      { path: 'declarations',   component: DeclarationManagementComponent },
      { path: 'calendar',       component: DeclarationCalendarComponent },
      { path: 'ml', component: ManagerMlDashboardComponent },


      // ✅ Types BCT en lecture seule — réutilise le composant existant
{ path: 'types', component: AgentDeclarationTypesComponent },

      // ✅ Notifications — redirige vers déclarations avec filtre rejetées
      // Si vous avez un composant dédié, remplacez HomeComponent ci-dessous
      // { path: 'notifications',  component: AgentNotificationsComponent },
      { path: 'notifications',  component: AgentNotificationsComponent },
    ]
  },
  // ── Manager Space ────────────────────────────────────────────
   {
    path: 'manager',
    component: ManagerLayoutComponent,
    canActivate: [RoleGuard],
    data: { roles: ['ROLE_MANAGER', 'ROLE_ADMIN'] },
    children: [
      { path: '',              redirectTo: 'dashboard', pathMatch: 'full' },
 
      // ✅ Tableau de bord (KPI + pending + toutes décl.)
      { path: 'dashboard',    component: ManagerDashboardComponent },
 
      // ✅ Validation dédiée — même composant, filtre EN_VALIDATION automatique
      // À remplacer par ManagerPendingComponent dès sa création
      { path: 'pending',      component: ManagerPendingComponent },
 
      // ✅ Toutes les déclarations avec filtres avancés
      // À remplacer par ManagerDeclarationsComponent
 
      // ✅ Historique / journal d'audit
      // À remplacer par ManagerHistoryComponent
      { path: 'history',      component: ManagerHistoryComponent  },
 
      // ✅ Calendrier BCT — US-18
      { path: 'calendar',     component: ManagerCalendarComponent },
      
 
      // ✅ Rapports & synthèse — US-19
      { path: 'reports',      component: ManagerReportsComponent },

    ]
  },

  // ── Auditor ─────────────────────────────────────────────────
  {
    path: 'auditor',
    component: DashboardLayoutComponent,
    canActivate: [RoleGuard],
    data: { roles: ['ROLE_AUDITOR'] },
    children: [{ path: '', component: HomeComponent }]
  },

  // ── Fallback ────────────────────────────────────────────────
  { path: '**', redirectTo: '' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule {}