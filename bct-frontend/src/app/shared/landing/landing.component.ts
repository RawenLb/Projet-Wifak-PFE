import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-landing',
  templateUrl: './landing.component.html',
  styleUrls: ['./landing.component.scss']
})
export class LandingComponent implements OnInit, OnDestroy {

  canInstall = false;
  installDone = false;
  currentYear = new Date().getFullYear();
  private deferredPrompt: any = null;
  private readyListener: any;

  constructor(private router: Router, private authService: AuthService) {}

  ngOnInit(): void {
    // Si déjà connecté → rediriger vers le bon dashboard selon le rôle
    if (this.authService.isLoggedIn()) {
      this.redirectByRole();
      return;
    }

    // Récupérer l'event capturé dans index.html
    const win = window as any;
    if (win.__pwaInstallEvent) {
      this.deferredPrompt = win.__pwaInstallEvent;
      this.canInstall = true;
    }

    this.readyListener = () => {
      const w = window as any;
      if (w.__pwaInstallEvent) {
        this.deferredPrompt = w.__pwaInstallEvent;
        this.canInstall = true;
      }
    };
    window.addEventListener('pwa-install-ready', this.readyListener);
  }

  ngOnDestroy(): void {
    if (this.readyListener) {
      window.removeEventListener('pwa-install-ready', this.readyListener);
    }
  }

  private redirectByRole(): void {
    const roles = this.authService.getRoles();
    if (roles.includes('ROLE_ADMIN'))    { this.router.navigate(['/dashboard']); return; }
    if (roles.includes('ROLE_MANAGER'))  { this.router.navigate(['/manager/dashboard']); return; }
    if (roles.includes('ROLE_AGENT'))    { this.router.navigate(['/agent/dashboard']); return; }
    if (roles.includes('ROLE_AUDITOR'))  { this.router.navigate(['/auditor/dashboard']); return; }
    this.router.navigate(['/unauthorized']);
  }

  @HostListener('window:beforeinstallprompt', ['$event'])
  onBeforeInstallPrompt(event: any): void {
    event.preventDefault();
    this.deferredPrompt = event;
    (window as any).__pwaInstallEvent = event;
    this.canInstall = true;
  }

  @HostListener('window:appinstalled')
  onAppInstalled(): void {
    this.canInstall = false;
    this.installDone = true;
    this.deferredPrompt = null;
    (window as any).__pwaInstallEvent = null;
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
    (window as any).__pwaInstallEvent = null;
  }

  goToLogin(): void {
    // Redirection directe vers Keycloak sans passer par l'instance JS
    const redirectUri = encodeURIComponent(window.location.origin);
    window.location.href = `http://localhost:8081/realms/bct-realm/protocol/openid-connect/auth?client_id=bct-frontend&redirect_uri=${redirectUri}&response_type=code&scope=openid`;
  }
}
