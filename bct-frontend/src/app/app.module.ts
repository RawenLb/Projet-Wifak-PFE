import { NgModule, APP_INITIALIZER, isDevMode } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { HTTP_INTERCEPTORS, HttpClientModule } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ServiceWorkerModule } from '@angular/service-worker';

import { KeycloakInterceptor } from './shared/interceptors/keycloak.interceptor';
import keycloak from './services/keycloak.service';

// -- Shared --
import { HomeComponent }             from './shared/home/home.component';
import { NotificationBellComponent } from './shared/notification-bell/notification-bell.component';
import { ChatComponent }             from './shared/chat/chat.component';
import { CallComponent }             from './shared/chat/call.component';
import { ConfirmDialogComponent }    from './shared/confirm-dialog/confirm-dialog.component';
import { ToastComponent }            from './shared/toast/toast.component';
import { TreatedDeclarationsComponent } from './shared/treated-declarations/treated-declarations.component';

// -- Admin --
import { DashboardLayoutComponent }           from './admin/dashboard-layout/dashboard-layout.component';
import { UserManagementComponent }            from './admin/user-management/user-management.component';
import { DeclarationTypeManagementComponent } from './admin/declaration-type-management/declaration-type-management.component';

// -- Manager --
import { ManagerLayoutComponent }      from './manager/layout/manager-layout.component';
import { ManagerDashboardComponent }   from './manager/dashboard/manager-dashboard.component';
import { ManagerPendingComponent }     from './manager/pending/manager-pending.component';
import { ManagerHistoryComponent }     from './manager/history/manager-history.component';
import { ManagerCalendarComponent }    from './manager/calendar/manager-calendar.component';
import { ManagerReportsComponent }     from './manager/reports/manager-reports.component';
import { ManagerMlDashboardComponent } from './manager/ml-dashboard/manager-ml-dashboard.component';
import { ManagerTreatedComponent }     from './manager/treated/manager-treated.component';

// -- Agent --
import { AgentLayoutComponent }              from './agent/layout/agent-layout.component';
import { AgentDashboardComponent }           from './agent/dashboard/agent-dashboard.component';
import { DeclarationManagementComponent }    from './agent/declaration-management/declaration-management.component';
import { DeclarationCalendarComponent }      from './agent/declaration-calendar/declaration-calendar.component';
import { DeclarationCorrectionComponent }    from './agent/declaration-correction/declaration-correction.component';
import { AgentDeclarationTypesComponent }    from './agent/declaration-types/agent-declaration-types.component';
import { AgentNotificationsComponent }       from './agent/notifications/agent-notifications.component';
import { AgentTreatedComponent }             from './agent/treated/agent-treated.component';

// -- Auditor --
import { AuditorLayoutComponent }    from './auditor/layout/auditor-layout.component';
import { AuditorDashboardComponent } from './auditor/dashboard/auditor-dashboard.component';
import { AuditorHistoryComponent }   from './auditor/history/auditor-history.component';
import { AuditorLogsComponent }      from './auditor/logs/auditor-logs.component';
import { AuditorArchivesComponent }  from './auditor/archives/auditor-archives.component';
import { AuditorSearchComponent }    from './auditor/search/auditor-search.component';
import { AuditorExportComponent }    from './auditor/export/auditor-export.component';

export function kcFactory() {
  return () =>
    keycloak.init({
      onLoad: 'login-required',
      checkLoginIframe: false,
      pkceMethod: 'S256',
      silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
      silentCheckSsoFallback: false
    }).then((authenticated) => {
      (window as any).keycloak = keycloak;
      if (!authenticated) {
        keycloak.login();
      }
    }).catch(error => {
      console.error('Keycloak init failed:', error);
    });
}

@NgModule({
  declarations: [
    AppComponent,

    // -- Shared --
    HomeComponent,
    NotificationBellComponent,
    ChatComponent,
    CallComponent,
    ConfirmDialogComponent,
    ToastComponent,
    TreatedDeclarationsComponent,

    // -- Admin --
    DashboardLayoutComponent,
    UserManagementComponent,
    DeclarationTypeManagementComponent,

    // -- Manager --
    ManagerLayoutComponent,
    ManagerDashboardComponent,
    ManagerPendingComponent,
    ManagerHistoryComponent,
    ManagerCalendarComponent,
    ManagerReportsComponent,
    ManagerMlDashboardComponent,
    ManagerTreatedComponent,

    // -- Agent --
    AgentLayoutComponent,
    AgentDashboardComponent,
    DeclarationManagementComponent,
    DeclarationCalendarComponent,
    DeclarationCorrectionComponent,
    AgentDeclarationTypesComponent,
    AgentNotificationsComponent,
    AgentTreatedComponent,

    // -- Auditor --
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
    ServiceWorkerModule.register('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000'
    }),
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
