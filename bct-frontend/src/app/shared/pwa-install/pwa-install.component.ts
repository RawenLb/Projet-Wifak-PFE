import { Component, OnInit, HostListener } from '@angular/core';

@Component({
  selector: 'app-pwa-install',
  template: `
    <div class="pwa-install-banner" *ngIf="!dismissed">
      <div class="pwa-install-content">
        <img src="assets/icons/icon-72x72.png" alt="Wifak BCT" class="pwa-icon">
        <div class="pwa-text">
          <strong>Banque Wifak BCT</strong>
          <span>Installez l'application pour un accès rapide depuis votre bureau</span>
        </div>
        <div class="pwa-actions">
          <!-- Bouton natif si disponible -->
          <button *ngIf="canInstall" class="pwa-btn-install" (click)="install()">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="7 10 12 15 17 10"/>
              <line x1="12" y1="15" x2="12" y2="3"/>
            </svg>
            Installer
          </button>
          <!-- Lien partageable si pas de prompt natif -->
          <a *ngIf="!canInstall" [href]="installUrl" target="_blank" class="pwa-btn-install">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="7 10 12 15 17 10"/>
              <line x1="12" y1="15" x2="12" y2="3"/>
            </svg>
            Installer
          </a>
          <button class="pwa-btn-copy" (click)="copyLink()" title="Copier le lien d'installation">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
              <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
            </svg>
            {{ copied ? 'Copié !' : 'Copier le lien' }}
          </button>
          <button class="pwa-btn-close" (click)="dismiss()" aria-label="Fermer">✕</button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .pwa-install-banner {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      background: linear-gradient(90deg, #1a3a5c 0%, #1e5fa8 60%, #c8102e 100%);
      color: white;
      padding: 10px 20px;
      z-index: 99999;
      box-shadow: 0 2px 16px rgba(0,0,0,0.25);
      animation: slideDown 0.4s ease-out;
    }
    @keyframes slideDown {
      from { transform: translateY(-100%); opacity: 0; }
      to   { transform: translateY(0);     opacity: 1; }
    }
    .pwa-install-content {
      display: flex;
      align-items: center;
      gap: 14px;
      max-width: 1100px;
      margin: 0 auto;
    }
    .pwa-icon {
      width: 38px;
      height: 38px;
      border-radius: 8px;
      flex-shrink: 0;
      background: white;
      padding: 3px;
    }
    .pwa-text {
      flex: 1;
      display: flex;
      flex-direction: column;
    }
    .pwa-text strong {
      font-size: 14px;
      font-weight: 700;
      letter-spacing: 0.3px;
    }
    .pwa-text span {
      font-size: 12px;
      opacity: 0.85;
      margin-top: 1px;
    }
    .pwa-actions {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-shrink: 0;
    }
    .pwa-btn-install {
      display: flex;
      align-items: center;
      gap: 6px;
      background: white;
      color: #1a3a5c;
      border: none;
      border-radius: 7px;
      padding: 7px 16px;
      font-size: 13px;
      font-weight: 700;
      cursor: pointer;
      text-decoration: none;
      transition: transform 0.15s, box-shadow 0.15s;
    }
    .pwa-btn-install:hover {
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba(0,0,0,0.2);
    }
    .pwa-btn-copy {
      display: flex;
      align-items: center;
      gap: 5px;
      background: rgba(255,255,255,0.15);
      border: 1px solid rgba(255,255,255,0.35);
      color: white;
      border-radius: 7px;
      padding: 7px 12px;
      font-size: 12px;
      cursor: pointer;
      transition: background 0.15s;
      white-space: nowrap;
    }
    .pwa-btn-copy:hover {
      background: rgba(255,255,255,0.25);
    }
    .pwa-btn-close {
      background: transparent;
      border: 1px solid rgba(255,255,255,0.3);
      color: white;
      border-radius: 6px;
      width: 30px;
      height: 30px;
      cursor: pointer;
      font-size: 13px;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: background 0.15s;
      flex-shrink: 0;
    }
    .pwa-btn-close:hover {
      background: rgba(255,255,255,0.15);
    }
    @media (max-width: 600px) {
      .pwa-text span { display: none; }
      .pwa-btn-copy  { display: none; }
      .pwa-install-banner { padding: 8px 12px; }
    }
  `]
})
export class PwaInstallComponent implements OnInit {
  dismissed = false;
  canInstall = false;
  copied = false;
  installUrl = window.location.origin;
  private deferredPrompt: any = null;

  ngOnInit(): void {
    // Toujours afficher la bannière (même si déjà installée)
    // sauf si l'utilisateur l'a fermée dans cette session
    const sessionDismissed = sessionStorage.getItem('pwa-banner-dismissed');
    if (sessionDismissed) {
      this.dismissed = true;
    }
  }

  @HostListener('window:beforeinstallprompt', ['$event'])
  onBeforeInstallPrompt(event: any): void {
    event.preventDefault();
    this.deferredPrompt = event;
    this.canInstall = true;
    this.dismissed = false; // Réafficher si prompt disponible
  }

  async install(): Promise<void> {
    if (!this.deferredPrompt) return;
    this.deferredPrompt.prompt();
    const { outcome } = await this.deferredPrompt.userChoice;
    if (outcome === 'accepted') {
      this.canInstall = false;
    }
    this.deferredPrompt = null;
  }

  copyLink(): void {
    const link = `${window.location.origin}`;
    navigator.clipboard.writeText(link).then(() => {
      this.copied = true;
      setTimeout(() => this.copied = false, 2500);
    });
  }

  dismiss(): void {
    this.dismissed = true;
    // Seulement pour la session courante — réapparaît à la prochaine visite
    sessionStorage.setItem('pwa-banner-dismissed', '1');
  }
}
