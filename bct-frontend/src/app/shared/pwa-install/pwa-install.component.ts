import { Component, OnInit, HostListener } from '@angular/core';

@Component({
  selector: 'app-pwa-install',
  template: `
    <div *ngIf="showBanner" class="pwa-install-banner">
      <div class="pwa-install-content">
        <img src="assets/icons/icon-72x72.png" alt="Wifak BCT" class="pwa-icon">
        <div class="pwa-text">
          <strong>Banque Wifak BCT</strong>
          <span>Installer l'application sur votre appareil</span>
        </div>
        <div class="pwa-actions">
          <button class="pwa-btn-install" (click)="install()">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="7 10 12 15 17 10"/>
              <line x1="12" y1="15" x2="12" y2="3"/>
            </svg>
            Installer
          </button>
          <button class="pwa-btn-close" (click)="dismiss()" aria-label="Fermer">✕</button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .pwa-install-banner {
      position: fixed;
      bottom: 0;
      left: 0;
      right: 0;
      background: linear-gradient(135deg, #1a3a5c 0%, #2563a8 100%);
      color: white;
      padding: 12px 20px;
      z-index: 9999;
      box-shadow: 0 -4px 20px rgba(0,0,0,0.3);
      animation: slideUp 0.4s ease-out;
    }
    @keyframes slideUp {
      from { transform: translateY(100%); opacity: 0; }
      to   { transform: translateY(0);    opacity: 1; }
    }
    .pwa-install-content {
      display: flex;
      align-items: center;
      gap: 14px;
      max-width: 900px;
      margin: 0 auto;
    }
    .pwa-icon {
      width: 44px;
      height: 44px;
      border-radius: 10px;
      flex-shrink: 0;
    }
    .pwa-text {
      flex: 1;
      display: flex;
      flex-direction: column;
    }
    .pwa-text strong {
      font-size: 15px;
      font-weight: 700;
    }
    .pwa-text span {
      font-size: 12px;
      opacity: 0.85;
      margin-top: 2px;
    }
    .pwa-actions {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .pwa-btn-install {
      display: flex;
      align-items: center;
      gap: 6px;
      background: white;
      color: #1a3a5c;
      border: none;
      border-radius: 8px;
      padding: 8px 18px;
      font-size: 14px;
      font-weight: 700;
      cursor: pointer;
      transition: transform 0.15s, box-shadow 0.15s;
    }
    .pwa-btn-install:hover {
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba(0,0,0,0.2);
    }
    .pwa-btn-close {
      background: transparent;
      border: 1px solid rgba(255,255,255,0.4);
      color: white;
      border-radius: 6px;
      width: 32px;
      height: 32px;
      cursor: pointer;
      font-size: 14px;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: background 0.15s;
    }
    .pwa-btn-close:hover {
      background: rgba(255,255,255,0.15);
    }
    @media (max-width: 480px) {
      .pwa-text span { display: none; }
      .pwa-install-banner { padding: 10px 14px; }
    }
  `]
})
export class PwaInstallComponent implements OnInit {
  showBanner = false;
  private deferredPrompt: any = null;

  ngOnInit(): void {
    // Vérifier si déjà installée
    if (window.matchMedia('(display-mode: standalone)').matches) {
      return;
    }
    // Vérifier si déjà dismissée
    if (localStorage.getItem('pwa-install-dismissed')) {
      return;
    }
  }

  @HostListener('window:beforeinstallprompt', ['$event'])
  onBeforeInstallPrompt(event: any): void {
    event.preventDefault();
    this.deferredPrompt = event;
    this.showBanner = true;
  }

  async install(): Promise<void> {
    if (!this.deferredPrompt) return;
    this.deferredPrompt.prompt();
    const { outcome } = await this.deferredPrompt.userChoice;
    if (outcome === 'accepted') {
      this.showBanner = false;
    }
    this.deferredPrompt = null;
  }

  dismiss(): void {
    this.showBanner = false;
    localStorage.setItem('pwa-install-dismissed', '1');
  }
}
