import { NgModule, APP_INITIALIZER } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { HTTP_INTERCEPTORS, HttpClientModule } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

import { KeycloakInterceptor } from './shared/interceptors/keycloak.interceptor';
import keycloak from './services/keycloak.service';

// â”€â”€ Shared â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
import { HomeComponent }             from './shared/home/home.component';
import { NotificationBellComponent } from './shared/notification-bell/notification-bell.component';
import { ChatComponent }             from './shared/chat/chat.component';
import { CallComponent }             from './shared/chat/call.component';
import { ConfirmDialogComponent }    from './shared/confirm-dialog/confirm-dialog.component';
import { ToastComponent }            from './shared/toast/toast.component';

// â”€â”€ Admin â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
import { DashboardLayoutComponent }           from './admin/dashboard-layout/dashboard-layout.component';
import { UserManagementComponent }            from './admin/user-management/user-management.component';
import { DeclarationTypeManagementComponent } from './admin/declaration-type-management/declaration-type-management.component';

// â”€â”€ Manager â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
import { ManagerLayoutComponent }      from './manager/layout/manager-layout.component';
import { ManagerDashboardComponent }   from './manager/dashboard/manager-dashboard.component';
import { ManagerPendingComponent }     from './manager/pending/manager-pending.component';
import { ManagerHistoryComponent }     from './manager/history/manager-history.component';
import { ManagerCalendarComponent }    from './manager/calendar/manager-calendar.component';
import { ManagerReportsComponent }     from './manager/reports/manager-reports.component';
import { ManagerMlDashboardComponent } from './manager/ml-dashboard/manager-ml-dashboard.component';

// â”€â”€ Agent â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
import { AgentLayoutComponent }              from './agent/layout/agent-layout.component';
import { AgentDashboardComponent }           from './agent/dashboard/agent-dashboard.component';
import { DeclarationManagementComponent }    from './agent/declaration-management/declaration-management.component';
import { DeclarationCalendarComponent }      from './agent/declaration-calendar/declaration-calendar.component';
import { DeclarationCorrectionComponent }    from './agent/declaration-correction/declaration-correction.component';
import { AgentDeclarationTypesComponent }    from './agent/declaration-types/agent-declaration-types.component';
import { AgentNotificationsComponent }       from './agent/notifications/agent-notifications.component';

// â”€â”€ Auditor â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
import { AuditorLayoutComponent }   from './auditor/layout/auditor-layout.component';
import { AuditorDashboardComponent } from './auditor/dashboard/auditor-dashboard.component';
import { AuditorHistoryComponent }  from './auditor/history/auditor-history.component';
import { AuditorLogsComponent }     from './auditor/logs/auditor-logs.component';
import { AuditorArchivesComponent } from './auditor/archives/auditor-archives.component';
import { AuditorSearchComponent }   from './auditor/search/auditor-search.component';
import { AuditorExportComponent }   from './auditor/export/auditor-export.component';

export function kcFactory() {
  return () =>
    keycloak.init({
      onLoad: 'login-required',
      checkLoginIframe: false,
      pkceMethod: 'S256'
    }).then((authenticated) => {
      (window as any).keycloak = keycloak;
      console.log('='.repeat(60));
      console.log('ðŸ” KEYCLOAK INITIALIZED');
      console.log('='.repeat(60));
      if (authenticated) {
        console.log('âœ… Authentication: SUCCESS');
        console.log('ðŸ‘¤ Username:', keycloak.tokenParsed?.['preferred_username']);
        console.log('ðŸŽ­ Roles:', keycloak.realmAccess?.roles);
        console.log('');
        console.log('ðŸ’¡ Debug commands:');
        console.log('   window.keycloak.tokenParsed');
        console.log('   window.keycloak.realmAccess.roles');
      } else {
        console.log('âŒ Authentication: FAILED');
      }
      console.log('='.repeat(60));
    }).catch(error => {
      console.error('âŒ Keycloak init failed:', error);
    });
}

@NgModule({
  declarations: [
    AppComponent,

    // â”€â”€ Shared â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    HomeComponent,
    NotificationBellComponent,
    ChatComponent,
    CallComponent,
    ConfirmDialogComponent,
    ToastComponent,

    // â”€â”€ Admin â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    DashboardLayoutComponent,
    UserManagementComponent,
    DeclarationTypeManagementComponent,

    // â”€â”€ Manager â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    ManagerLayoutComponent,
    ManagerDashboardComponent,
    ManagerPendingComponent,
    ManagerHistoryComponent,
    ManagerCalendarComponent,
    ManagerReportsComponent,
    ManagerMlDashboardComponent,

    // â”€â”€ Agent â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    AgentLayoutComponent,
    AgentDashboardComponent,
    DeclarationManagementComponent,
    DeclarationCalendarComponent,
    DeclarationCorrectionComponent,
    AgentDeclarationTypesComponent,
    AgentNotificationsComponent,

    // â”€â”€ Auditor â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    AuditorLayoutComponent,
    AuditorDashboardComponent,
    AuditorHistoryComponent,
    AuditorLogsComponent,
    AuditorArchivesComponent,
    AuditorSearchComponent,
    AuditorExportComponent,
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    HttpClientModule,
    FormsModule,
  ],
  providers: [
    {
      provide: APP_INITIALIZER,
      useFactory: kcFactory,
      multi: true,
    },
    {
      provide: HTTP_INTERCEPTORS,
      useClass: KeycloakInterceptor,
      multi: true,
    },
  ],
  bootstrap: [AppComponent],
})
export class AppModule {}

