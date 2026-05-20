import { Component, OnInit, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-pwa-install',
  template: `
    <div class="pwa-banner" *ngIf="!dismissed">
      <img src="assets/icons/icon-72x72.png" alt="Wifak BCT" class="pwa-icon">
      <div class="pwa-text">
        <strong>Installer l'application</strong>
        <span>Accès rapide depuis votre écran d'accueil</span>
      </div>
      <button *ngIf="canInstall" class="pwa-btn" (click)="install()">Installer</button>
      <button class="pwa-copy" (click)="copyLink()">{{ copied ? 'Copié !' : 'Copier' }}</button>
      <button class="pwa-close" (click)="dismiss()">✕</button>
    </div>
  `,
  styles: [`
    .pwa-banner {
      position: fixed;
      bottom: 20px;
      left: 50%;
      transform: translateX(-50%);
      background: #1e1e1e;
      color: white;
      padding: 12px 16px;
      z-index: 99999;
      box-shadow: 0 4px 24px rgba(0,0,0,0.5);
      border-radius: 14px;
      min-width: 320px;
      max-width: 460px;
      display: flex;
      align-items: center;
      gap: 12px;
      border: 1px solid rgba(255,255,255,0.08);
      animation: slideUp 0.4s ease-out;
    }
    @keyframes slideUp {
      from { transform: translateX(-50%) translateY(80px); opacity: 0; }
      to   { transform: translateX(-50%) translateY(0);    opacity: 1; }
    }
    .pwa-icon {
      width: 40px; height: 40px;
      border-radius: 10px; flex-shrink: 0;
      background: white; padding: 3px;
    }
    .pwa-text {
      flex: 1; display: flex; flex-direction: column;
    }
    .pwa-text strong { font-size: 13px; font-weight: 700; }
    .pwa-text span   { font-size: 11px; color: rgba(255,255,255,0.55); margin-top: 2px; }
    .pwa-btn {
      background: #1a3a5c; color: white; border: none;
      border-radius: 8px; padding: 7px 14px;
      font-size: 13px; font-weight: 700; cursor: pointer;
      white-space: nowrap; flex-shrink: 0;
      transition: background 0.15s;
    }
    .pwa-btn:hover { background: #2563a8; }
    .pwa-copy {
      background: rgba(255,255,255,0.1);
      border: 1px solid rgba(255,255,255,0.2);
      color: white; border-radius: 8px;
      padding: 7px 12px; font-size: 12px;
      cursor: pointer; white-space: nowrap; flex-shrink: 0;
      transition: background 0.15s;
    }
    .pwa-copy:hover { background: rgba(255,255,255,0.2); }
    .pwa-close {
      background: transparent; border: none;
      color: rgba(255,255,255,0.4); cursor: pointer;
      font-size: 18px; padding: 2px; flex-shrink: 0;
      line-height: 1;
    }
    .pwa-close:hover { color: white; }
  `]
})
export class PwaInstallComponent implements OnInit {
  dismissed = false;
  canInstall = false;
  copied = false;
  private deferredPrompt: any = null;

  ngOnInit(): void {
    if (sessionStorage.getItem('pwa-dismissed')) {
      this.dismissed = true;
    }
  }

  @HostListener('window:beforeinstallprompt', ['$event'])
  onBeforeInstallPrompt(event: any): void {
    event.preventDefault();
    this.deferredPrompt = event;
    this.canInstall = true;
    this.dismissed = false;
  }

  async install(): Promise<void> {
    if (!this.deferredPrompt) return;
    this.deferredPrompt.prompt();
    const { outcome } = await this.deferredPrompt.userChoice;
    if (outcome === 'accepted') {
      this.dismissed = true;
    }
    this.deferredPrompt = null;
    this.canInstall = false;
  }

  copyLink(): void {
    navigator.clipboard.writeText(window.location.origin).then(() => {
      this.copied = true;
      setTimeout(() => this.copied = false, 2500);
    });
  }

  dismiss(): void {
    this.dismissed = true;
    sessionStorage.setItem('pwa-dismissed', '1');
  }
}
