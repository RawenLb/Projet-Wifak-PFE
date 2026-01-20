import { NgModule, APP_INITIALIZER } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { HTTP_INTERCEPTORS, HttpClientModule } from '@angular/common/http';
import { FormsModule } from '@angular/forms'; // ✅ IMPORTANT: Pour ngModel
import { KeycloakInterceptor } from './interceptors/keycloak.interceptor';
import keycloak from './services/keycloak.service';
import { HomeComponent } from './home/home.component';
import { UserManagementComponent } from './user-management/user-management.component';

export function kcFactory() {
  return () =>
    keycloak.init({
      onLoad: 'login-required',
      checkLoginIframe: false,
      pkceMethod: 'S256'
    }).then((authenticated) => {
      // ✅ Expose keycloak globally for debugging
      (window as any).keycloak = keycloak;
      
      console.log('='.repeat(60));
      console.log('🔐 KEYCLOAK INITIALIZED');
      console.log('='.repeat(60));
      
      if (authenticated) {
        console.log('✅ Authentication: SUCCESS');
        console.log('👤 Username:', keycloak.tokenParsed?.['preferred_username']);
        console.log('🎭 Roles:', keycloak.realmAccess?.roles);
        console.log('');
        console.log('💡 Debug commands:');
        console.log('   window.keycloak.tokenParsed');
        console.log('   window.keycloak.realmAccess.roles');
      } else {
        console.log('❌ Authentication: FAILED');
      }
      
      console.log('='.repeat(60));
    }).catch(error => {
      console.error('❌ Keycloak init failed:', error);
    });
}

@NgModule({
  declarations: [
    AppComponent,
    HomeComponent,
    UserManagementComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    HttpClientModule,
    FormsModule  // ✅ IMPORTANT: Nécessaire pour [(ngModel)]
  ],
  providers: [
    {
      provide: APP_INITIALIZER,
      useFactory: kcFactory,
      multi: true
    },
    {
      provide: HTTP_INTERCEPTORS,
      useClass: KeycloakInterceptor,
      multi: true
    }
  ],
  bootstrap: [AppComponent]
})
export class AppModule {}