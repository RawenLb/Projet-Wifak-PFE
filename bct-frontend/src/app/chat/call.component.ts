// src/app/chat/call.component.ts — WebRTC Call UI — Wifak Bank

import {
  Component, OnInit, OnDestroy, AfterViewChecked,
  ViewChild, ElementRef, ChangeDetectorRef, ChangeDetectionStrategy
} from '@angular/core';
import { Subscription } from 'rxjs';
import { WebRtcService, ActiveCall, CallState } from '../services/webrtc.service';

@Component({
  selector:        'app-call',
  templateUrl:     './call.component.html',
  styleUrls:       ['./call.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CallComponent implements OnInit, OnDestroy, AfterViewChecked {

  // Ces éléments sont TOUJOURS dans le DOM (hors *ngIf)
  @ViewChild('localVideo')  localVideoRef!:  ElementRef<HTMLVideoElement>;
  @ViewChild('remoteVideo') remoteVideoRef!: ElementRef<HTMLVideoElement>;

  call:        ActiveCall | null = null;
  callState:   CallState = 'IDLE';
  isMuted      = false;
  isCameraOff  = false;
  ringTimer    = 0;

  private ringInterval:   any;
  private subs:           Subscription[] = [];
  private pendingLocal:   MediaStream | null = null;
  private pendingRemote:  MediaStream | null = null;

  constructor(
    public  rtc: WebRtcService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.subs.push(

      this.rtc.activeCall$.subscribe(c => {
        this.call = c;
        this.cdr.markForCheck();
      }),

      this.rtc.callState$.subscribe(s => {
        this.callState = s;
        if (s === 'RINGING') this.startRingTimer();
        else                 this.stopRingTimer();
        if (s === 'IDLE' || s === 'ENDED') {
          this.isMuted = false;
          this.isCameraOff = false;
          // Libérer les streams des éléments vidéo
          if (this.localVideoRef?.nativeElement)  this.localVideoRef.nativeElement.srcObject  = null;
          if (this.remoteVideoRef?.nativeElement) this.remoteVideoRef.nativeElement.srcObject = null;
          this.pendingLocal  = null;
          this.pendingRemote = null;
        }
        this.cdr.markForCheck();
      }),

      this.rtc.localStream$.subscribe(stream => {
        this.pendingLocal = stream;
        this.attachStreams();
        this.cdr.markForCheck();
      }),

      this.rtc.remoteStream$.subscribe(stream => {
        this.pendingRemote = stream;
        this.attachStreams();
        this.cdr.markForCheck();
      })
    );
  }

  ngAfterViewChecked(): void {
    // Réessayer l'attachement si les éléments viennent d'apparaître
    this.attachStreams();
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
    this.stopRingTimer();
  }

  // ── Attachement des streams ───────────────────────────────────

  private attachStreams(): void {
    if (this.pendingLocal && this.localVideoRef?.nativeElement) {
      const el = this.localVideoRef.nativeElement;
      if (el.srcObject !== this.pendingLocal) {
        el.srcObject = this.pendingLocal;
        el.muted = true;
        el.play().catch(() => {});
        // Déplacer l'élément dans le miroir de l'overlay si visible
        this.moveVideoToOverlay('local', el);
      }
    }

    if (this.pendingRemote && this.remoteVideoRef?.nativeElement) {
      const el = this.remoteVideoRef.nativeElement;
      if (el.srcObject !== this.pendingRemote) {
        el.srcObject = this.pendingRemote;
        el.play().catch(() => {});
        this.moveVideoToOverlay('remote', el);
      }
    }
  }

  private moveVideoToOverlay(type: 'local' | 'remote', videoEl: HTMLVideoElement): void {
    const mirrorId = type === 'local' ? 'call-local-mirror' : 'call-remote-mirror';
    const mirror = document.getElementById(mirrorId);
    if (mirror && !mirror.contains(videoEl)) {
      // Appliquer les classes CSS appropriées
      videoEl.className = type === 'local' ? 'call-local-video' : 'call-remote-video';
      videoEl.style.display = '';
      mirror.appendChild(videoEl);
    }
  }

  // ── Actions ───────────────────────────────────────────────────

  accept(): void { this.rtc.acceptCall(); }

  reject(): void {
    if (this.callState === 'RINGING') this.rtc.rejectCall();
  }

  hangUp(): void { this.rtc.hangUp(); }

  toggleMute(): void {
    this.isMuted = this.rtc.toggleMute();
    this.cdr.markForCheck();
  }

  toggleCamera(): void {
    this.isCameraOff = this.rtc.toggleCamera();
    this.cdr.markForCheck();
  }

  // ── Helpers ───────────────────────────────────────────────────

  get isVisible(): boolean {
    return this.callState !== 'IDLE' && this.callState !== 'ENDED';
  }

  get isVideo(): boolean {
    return this.call?.callType === 'VIDEO';
  }

  get isActive(): boolean {
    return this.callState === 'ACTIVE';
  }

  get statusLabel(): string {
    switch (this.callState) {
      case 'CALLING':    return 'Appel en cours...';
      case 'RINGING':    return 'Appel entrant';
      case 'CONNECTING': return 'Connexion...';
      case 'ACTIVE':     return this.formatDuration(this.call?.duration ?? 0);
      default:           return '';
    }
  }

  formatDuration(secs: number): string {
    const m = Math.floor(secs / 60).toString().padStart(2, '0');
    const s = (secs % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  }

  getInitials(name: string): string {
    return (name || '?').split(' ').map(p => p[0]).slice(0, 2).join('').toUpperCase();
  }

  private startRingTimer(): void {
    this.stopRingTimer();
    this.ringTimer = 0;
    this.ringInterval = setInterval(() => {
      this.ringTimer++;
      this.cdr.markForCheck();
      if (this.ringTimer >= 45 && this.callState === 'RINGING') {
        this.reject();
      }
    }, 1000);
  }

  private stopRingTimer(): void {
    clearInterval(this.ringInterval);
    this.ringTimer = 0;
  }
}
