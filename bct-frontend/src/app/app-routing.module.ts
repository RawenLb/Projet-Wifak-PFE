import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { RoleGuard } from './guards/role.guard';
import { HomeComponent } from './home/home.component';
import { UserManagementComponent } from './user-management/user-management.component';
import { DeclarationTypeManagementComponent } from './declaration-type-management/declaration-type-management.component';
import { DashboardLayoutComponent } from './dashboard-layout/dashboard-layout.component';

const routes: Routes = [
  // Public routes WITHOUT dashboard layout
  { 
    path: '', 
    component: DashboardLayoutComponent 
  },
  { 
    path: 'home', 
    component: HomeComponent 
  },
  { 
    path: 'unauthorized', 
    component: HomeComponent 
  },

  // Protected routes WITH dashboard layout
  { 
    path: 'user-management', 
    component: DashboardLayoutComponent,
    canActivate: [RoleGuard],
    data: { roles: ['ROLE_ADMIN'] },
    children: [
      {
        path: '',
        component: UserManagementComponent
      }
    ]
  },
  { 
    path: 'declaration-type-management', 
    component: DashboardLayoutComponent,
    canActivate: [RoleGuard],
    data: { roles: ['ROLE_ADMIN', 'ROLE_MANAGER'] },
    children: [
      {
        path: '',
        component: DeclarationTypeManagementComponent
      }
    ]
  },
  {
    path: 'dashboard',
    component: DashboardLayoutComponent,
    canActivate: [RoleGuard],
    data: { roles: ['ROLE_AGENT', 'ROLE_MANAGER', 'ROLE_ADMIN', 'ROLE_AUDITOR'] },
    children: [
      {
        path: '',
        component: HomeComponent
      }
    ]
  },
  {
    path: 'agent',
    component: DashboardLayoutComponent,
    canActivate: [RoleGuard],
    data: { roles: ['ROLE_AGENT'] },
    children: [
      {
        path: '',
        component: HomeComponent
      }
    ]
  },
  {
    path: 'manager',
    component: DashboardLayoutComponent,
    canActivate: [RoleGuard],
    data: { roles: ['ROLE_MANAGER'] },
    children: [
      {
        path: '',
        component: HomeComponent
      }
    ]
  },
  {
    path: 'auditor',
    component: DashboardLayoutComponent,
    canActivate: [RoleGuard],
    data: { roles: ['ROLE_AUDITOR'] },
    children: [
      {
        path: '',
        component: HomeComponent
      }
    ]
  },

  // Fallback
  { 
    path: '**', 
    redirectTo: '/home' 
  }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule {}