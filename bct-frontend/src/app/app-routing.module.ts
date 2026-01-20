import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { RoleGuard } from './guards/role.guard';
import { HomeComponent } from './home/home.component';
import { UserManagementComponent } from './user-management/user-management.component';

const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'home', component: HomeComponent },
  
  { 
    path: 'user-management', 
    component: UserManagementComponent,
    canActivate: [RoleGuard],
    data: { roles: ['ROLE_ADMIN'] }
  },
  {
    path: 'agent',
    component: HomeComponent,
    canActivate: [RoleGuard],
    data: { roles: ['ROLE_AGENT'] } // ✅ WITH "ROLE_" prefix
  },
  {
    path: 'manager',
    component: HomeComponent,
    canActivate: [RoleGuard],
    data: { roles: ['ROLE_MANAGER'] } // ✅ WITH "ROLE_" prefix
  },
  {
    path: 'auditor',
    component: HomeComponent,
    canActivate: [RoleGuard],
    data: { roles: ['ROLE_AUDITOR'] } // ✅ WITH "ROLE_" prefix
  },
  
  {
    path: 'unauthorized',
    component: HomeComponent
  }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule {}