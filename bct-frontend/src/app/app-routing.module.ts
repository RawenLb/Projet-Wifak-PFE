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

      // ✅ Types BCT en lecture seule — réutilise le composant existant
      { path: 'types',          component: DeclarationTypeManagementComponent },

      // ✅ Notifications — redirige vers déclarations avec filtre rejetées
      // Si vous avez un composant dédié, remplacez HomeComponent ci-dessous
      // { path: 'notifications',  component: AgentNotificationsComponent },
      { path: 'notifications',  redirectTo: 'declarations', pathMatch: 'full' },
    ]
  },
  // ── Manager Space ────────────────────────────────────────────
  {
    path: 'manager',
    component: ManagerLayoutComponent,
    canActivate: [RoleGuard],
    data: { roles: ['ROLE_MANAGER', 'ROLE_ADMIN'] },
    children: [
      { path: '',          redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: ManagerDashboardComponent },
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