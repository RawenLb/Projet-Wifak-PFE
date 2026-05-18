// src/app/app-routing.module.ts
// Route '/' utilise RoleRedirectGuard → redirige vers le bon espace selon rôle

import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { RoleGuard }         from './shared/guards/role.guard';
import { RoleRedirectGuard } from './shared/guards/Role-redirect.guard';

// ── Shared ────────────────────────────────────────────────────────────
import { HomeComponent } from './shared/home/home.component';

// ── Admin ─────────────────────────────────────────────────────────────
import { DashboardLayoutComponent }           from './admin/dashboard-layout/dashboard-layout.component';
import { UserManagementComponent }            from './admin/user-management/user-management.component';
import { DeclarationTypeManagementComponent } from './admin/declaration-type-management/declaration-type-management.component';

// ── Manager ───────────────────────────────────────────────────────────
import { ManagerLayoutComponent }      from './manager/layout/manager-layout.component';
import { ManagerDashboardComponent }   from './manager/dashboard/manager-dashboard.component';
import { ManagerPendingComponent }     from './manager/pending/manager-pending.component';
import { ManagerHistoryComponent }     from './manager/history/manager-history.component';
import { ManagerCalendarComponent }    from './manager/calendar/manager-calendar.component';
import { ManagerReportsComponent }     from './manager/reports/manager-reports.component';
import { ManagerMlDashboardComponent } from './manager/ml-dashboard/manager-ml-dashboard.component';
import { ManagerTreatedComponent }     from './manager/treated/manager-treated.component';

// ── Agent ─────────────────────────────────────────────────────────────
import { AgentLayoutComponent }           from './agent/layout/agent-layout.component';
import { AgentDashboardComponent }        from './agent/dashboard/agent-dashboard.component';
import { DeclarationManagementComponent } from './agent/declaration-management/declaration-management.component';
import { DeclarationCalendarComponent }   from './agent/declaration-calendar/declaration-calendar.component';
import { AgentDeclarationTypesComponent } from './agent/declaration-types/agent-declaration-types.component';
import { AgentNotificationsComponent }    from './agent/notifications/agent-notifications.component';
import { AgentTreatedComponent }          from './agent/treated/agent-treated.component';

// ── Auditor ───────────────────────────────────────────────────────────
import { AuditorLayoutComponent }    from './auditor/layout/auditor-layout.component';
import { AuditorDashboardComponent } from './auditor/dashboard/auditor-dashboard.component';
import { AuditorHistoryComponent }   from './auditor/history/auditor-history.component';
import { AuditorLogsComponent }      from './auditor/logs/auditor-logs.component';
import { AuditorArchivesComponent }  from './auditor/archives/auditor-archives.component';
import { AuditorSearchComponent }    from './auditor/search/auditor-search.component';
import { AuditorExportComponent }    from './auditor/export/auditor-export.component';

const routes: Routes = [

  // ── Root : redirige selon le rôle ──────────────────────────────────
  {
    path: '',
    canActivate: [RoleRedirectGuard],
    component: HomeComponent,
  },

  // ── Pages publiques ─────────────────────────────────────────────────
  { path: 'home',         component: HomeComponent },
  { path: 'unauthorized', component: HomeComponent },

  // ══════════════════════════════════════════════════════════════════
  // ADMIN
  // ══════════════════════════════════════════════════════════════════

  {
    path: 'dashboard',
    component: DashboardLayoutComponent,
    canActivate: [RoleGuard],
    data: { roles: ['ROLE_ADMIN'] },
    children: [
      { path: '', component: HomeComponent },
    ],
  },
  {
    path: 'user-management',
    component: DashboardLayoutComponent,
    canActivate: [RoleGuard],
    data: { roles: ['ROLE_ADMIN'] },
    children: [{ path: '', component: UserManagementComponent }],
  },
  {
    path: 'declaration-type-management',
    component: DashboardLayoutComponent,
    canActivate: [RoleGuard],
    data: { roles: ['ROLE_ADMIN', 'ROLE_MANAGER'] },
    children: [{ path: '', component: DeclarationTypeManagementComponent }],
  },

  // ══════════════════════════════════════════════════════════════════
  // AGENT
  // ══════════════════════════════════════════════════════════════════

  {
    path: 'agent',
    component: AgentLayoutComponent,
    canActivate: [RoleGuard],
    data: { roles: ['ROLE_AGENT'] },
    children: [
      { path: '',              redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard',     component: AgentDashboardComponent },
      { path: 'declarations',  component: DeclarationManagementComponent },
      { path: 'calendar',      component: DeclarationCalendarComponent },
      { path: 'types',         component: AgentDeclarationTypesComponent },
      { path: 'notifications', component: AgentNotificationsComponent },
      { path: 'ml',            component: ManagerMlDashboardComponent },
      { path: 'treated',       component: AgentTreatedComponent },
    ],
  },

  // ══════════════════════════════════════════════════════════════════
  // MANAGER
  // ══════════════════════════════════════════════════════════════════

  {
    path: 'manager',
    component: ManagerLayoutComponent,
    canActivate: [RoleGuard],
    data: { roles: ['ROLE_MANAGER', 'ROLE_ADMIN'] },
    children: [
      { path: '',          redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: ManagerDashboardComponent },
      { path: 'pending',   component: ManagerPendingComponent },
      { path: 'history',   component: ManagerHistoryComponent },
      { path: 'calendar',  component: ManagerCalendarComponent },
      { path: 'reports',   component: ManagerReportsComponent },
      { path: 'ml',        component: ManagerMlDashboardComponent },
      { path: 'treated',   component: ManagerTreatedComponent },
    ],
  },

  // ══════════════════════════════════════════════════════════════════
  // AUDITOR
  // ══════════════════════════════════════════════════════════════════

  {
    path: 'auditor',
    component: AuditorLayoutComponent,
    canActivate: [RoleGuard],
    data: { roles: ['ROLE_AUDITOR'] },
    children: [
      { path: '',          redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: AuditorDashboardComponent },
      { path: 'history',   component: AuditorHistoryComponent },
      { path: 'logs',      component: AuditorLogsComponent },
      { path: 'archives',  component: AuditorArchivesComponent },
      { path: 'search',    component: AuditorSearchComponent },
      { path: 'export',    component: AuditorExportComponent },
    ],
  },

  // ── Fallback ────────────────────────────────────────────────────────
  { path: '**', redirectTo: '' },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule],
})
export class AppRoutingModule {}
