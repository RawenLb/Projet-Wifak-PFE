import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import keycloak from '../../services/keycloak.service';

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

type PwaWindow = Window & {
  __pwaInstallEvent?: BeforeInstallPromptEvent;
};

@Component({
  selector: 'app-landing',
  templateUrl: './landing.component.html',
  styleUrls: ['./landing.component.scss']
})
export class LandingComponent implements OnInit, OnDestroy {

  canInstall = false;
  installDone = false;
  currentYear = new Date().getFullYear();
  private deferredPrompt: BeforeInstallPromptEvent | null = null;
  private readyListener: (() => void) | null = null;

  private get pwaWindow(): PwaWindow { return window as PwaWindow; }

  constructor(private router: Router, private authService: AuthService) {}

  ngOnInit(): void {
    // Si déjà authentifié (session existante ou callback traité), rediriger
    if (this.authService.isLoggedIn()) {
      this.redirectByRole();
      return;
    }

    // Attendre que Keycloak finisse son init (cas du callback code=...)
    // keycloak.onAuthSuccess se déclenche quand le token est obtenu
    keycloak.onAuthSuccess = () => {
      this.redirectByRole();
    };

    if (this.pwaWindow.__pwaInstallEvent) {
      this.deferredPrompt = this.pwaWindow.__pwaInstallEvent;
      this.canInstall = true;
    }

    this.readyListener = () => {
      if (this.pwaWindow.__pwaInstallEvent) {
        this.deferredPrompt = this.pwaWindow.__pwaInstallEvent;
        this.canInstall = true;
      }
    };
    window.addEventListener('pwa-install-ready', this.readyListener);
  }

  ngOnDestroy(): void {
    if (this.readyListener) {
      window.removeEventListener('pwa-install-ready', this.readyListener);
    }
    // Nettoyer le listener Keycloak
    keycloak.onAuthSuccess = undefined;
  }

  private redirectByRole(): void {
    const roles = this.authService.getRoles();
    if (roles.includes('ROLE_ADMIN'))   { this.router.navigate(['/dashboard']); return; }
    if (roles.includes('ROLE_MANAGER')) { this.router.navigate(['/manager/dashboard']); return; }
    if (roles.includes('ROLE_AGENT'))   { this.router.navigate(['/agent/dashboard']); return; }
    if (roles.includes('ROLE_AUDITOR')) { this.router.navigate(['/auditor/dashboard']); return; }
    this.router.navigate(['/unauthorized']);
  }

  @HostListener('window:beforeinstallprompt', ['$event'])
  onBeforeInstallPrompt(event: BeforeInstallPromptEvent): void {
    event.preventDefault();
    this.deferredPrompt = event;
    this.pwaWindow.__pwaInstallEvent = event;
    this.canInstall = true;
  }

  @HostListener('window:appinstalled')
  onAppInstalled(): void {
    this.canInstall = false;
    this.installDone = true;
    this.deferredPrompt = null;
    this.pwaWindow.__pwaInstallEvent = undefined;
  }

  async install(): Promise<void> {
    if (!this.deferredPrompt) return;
    this.deferredPrompt.prompt();
    const { outcome } = await this.deferredPrompt.userChoice;
    if (outcome === 'accepted') {
      this.installDone = true;
      this.canInstall = false;
    }
    this.deferredPrompt = null;
    this.pwaWindow.__pwaInstallEvent = undefined;
  }

  goToLogin(): void {
    keycloak.login({
      redirectUri: window.location.origin
    });
  }
}
