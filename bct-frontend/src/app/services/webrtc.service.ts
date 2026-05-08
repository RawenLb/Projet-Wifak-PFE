// src/app/services/webrtc.service.ts — Wifak Bank WebRTC

import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Subject, Subscription } from 'rxjs';
import { ChatWebSocketService, CallSignal, IceSignal, CallType } from './chat-websocket.service';
import keycloak from './keycloak.service';

export type CallState = 'IDLE' | 'CALLING' | 'RINGING' | 'CONNECTING' | 'ACTIVE' | 'ENDED';

export interface ActiveCall {
  callId:      string;
  callType:    CallType;
  remoteId:    string;
  remoteName:  string;
  direction:   'OUTGOING' | 'INCOMING';
  state:       CallState;
  startedAt?:  Date;
  duration?:   number;
}

const ICE_SERVERS: RTCIceServer[] = [
  { urls: 'stun:stun.l.google.com:19302' },
  { urls: 'stun:stun1.l.google.com:19302' }
];

const NO_ANSWER_TIMEOUT_MS = 30_000; // 30s sans réponse → appel manqué

@Injectable({ providedIn: 'root' })
export class WebRtcService implements OnDestroy {

  readonly callState$    = new BehaviorSubject<CallState>('IDLE');
  readonly activeCall$   = new BehaviorSubject<ActiveCall | null>(null);
  readonly localStream$  = new BehaviorSubject<MediaStream | null>(null);
  readonly remoteStream$ = new BehaviorSubject<MediaStream | null>(null);
  readonly incomingCall$ = new Subject<ActiveCall>();

  private pc:                RTCPeerConnection | null = null;
  private localStream:       MediaStream | null = null;
  private remoteStream:      MediaStream = new MediaStream();
  private durationTimer:     any;
  private noAnswerTimer:     any;
  private wasAnswered        = false;
  private pendingCandidates: RTCIceCandidateInit[] = [];
  private subs:              Subscription[] = [];

  constructor(private ws: ChatWebSocketService) {
    this.subs.push(
      this.ws.callSignal$.subscribe(sig => this.onCallSignal(sig)),
      this.ws.iceSignal$.subscribe(sig  => this.onIceSignal(sig))
    );
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
    this.cleanUp(false);
  }

  // ── Initiate a call ───────────────────────────────────────────

  async startCall(remoteId: string, remoteName: string, callType: CallType): Promise<void> {
    if (this.callState$.value !== 'IDLE') {
      console.warn('[WebRTC] Already in a call');
      return;
    }

    const callId = this.generateCallId();
    this.wasAnswered = false;

    const call: ActiveCall = {
      callId, callType, remoteId, remoteName,
      direction: 'OUTGOING', state: 'CALLING'
    };
    this.activeCall$.next(call);
    this.callState$.next('CALLING');

    try {
      this.localStream = await this.getUserMedia(callType);
      this.localStream$.next(this.localStream);

      this.createPeerConnection(callId, remoteId);
      this.localStream.getTracks().forEach(t => this.pc!.addTrack(t, this.localStream!));

      const offer = await this.pc!.createOffer({
        offerToReceiveAudio: true,
        offerToReceiveVideo: callType === 'VIDEO'
      });
      await this.pc!.setLocalDescription(offer);
      this.ws.sendCallOffer(callId, callType, remoteId, offer);

      // Timeout si pas de réponse
      this.noAnswerTimer = setTimeout(() => {
        if (!this.wasAnswered && this.callState$.value === 'CALLING') {
          console.log('[WebRTC] No answer timeout — hanging up');
          this.hangUp();
        }
      }, NO_ANSWER_TIMEOUT_MS);

    } catch (err) {
      console.error('[WebRTC] startCall error:', err);
      this.cleanUp(false);
    }
  }

  // ── Accept incoming call ──────────────────────────────────────

  async acceptCall(): Promise<void> {
    const call = this.activeCall$.value;
    if (!call || call.state !== 'RINGING') return;

    if (!this.pc) {
      console.error('[WebRTC] No peer connection — rejecting');
      this.rejectCall();
      return;
    }

    try {
      this.localStream = await this.getUserMedia(call.callType);
      this.localStream$.next(this.localStream);

      this.localStream.getTracks().forEach(t => this.pc!.addTrack(t, this.localStream!));

      const answer = await this.pc!.createAnswer();
      await this.pc!.setLocalDescription(answer);
      this.ws.sendCallAnswer(call.callId, call.callType, call.remoteId, answer);

      // Mettre à jour l'état
      this.activeCall$.next({ ...call, state: 'CONNECTING' });
      this.callState$.next('CONNECTING');

      // Vider les candidats ICE en attente
      for (const c of this.pendingCandidates) {
        try { await this.pc!.addIceCandidate(new RTCIceCandidate(c)); } catch {}
      }
      this.pendingCandidates = [];

    } catch (err) {
      console.error('[WebRTC] acceptCall error:', err);
      this.rejectCall();
    }
  }

  // ── Reject incoming call ──────────────────────────────────────

  rejectCall(): void {
    const call = this.activeCall$.value;
    if (!call) return;
    this.ws.sendCallReject(call.callId, call.remoteId, call.callType);
    this.cleanUp(false);
  }

  // ── Hang up ───────────────────────────────────────────────────

  hangUp(): void {
    const call  = this.activeCall$.value;
    const state = this.callState$.value;
    if (call && state !== 'IDLE' && state !== 'ENDED') {
      const durationSec = call.duration ?? 0;
      this.ws.sendCallEnd(call.callId, call.remoteId, call.callType, this.wasAnswered, durationSec);
    }
    this.cleanUp(false);
  }

  // ── Toggle audio/video ────────────────────────────────────────

  toggleMute(): boolean {
    const track = this.localStream?.getAudioTracks()[0];
    if (!track) return false;
    track.enabled = !track.enabled;
    return !track.enabled; // true = muté
  }

  toggleCamera(): boolean {
    const track = this.localStream?.getVideoTracks()[0];
    if (!track) return false;
    track.enabled = !track.enabled;
    return !track.enabled; // true = caméra coupée
  }

  isMuted():     boolean { return !(this.localStream?.getAudioTracks()[0]?.enabled ?? true); }
  isCameraOff(): boolean { return !(this.localStream?.getVideoTracks()[0]?.enabled ?? true); }

  // ── Incoming signal handlers ──────────────────────────────────

  private async onCallSignal(sig: CallSignal): Promise<void> {
    const myId = keycloak.tokenParsed?.['sub'];

    switch (sig.type) {

      case 'CALL_OFFER': {
        if (sig.toId !== myId) return;

        // Déjà en appel → BUSY
        if (this.callState$.value !== 'IDLE') {
          this.ws.send({
            type: 'CALL_BUSY',
            payload: {
              type: 'CALL_BUSY', callId: sig.callId, callType: sig.callType,
              fromId: myId, fromName: '', toId: sig.fromId
            }
          });
          return;
        }

        const call: ActiveCall = {
          callId:     sig.callId,
          callType:   sig.callType,
          remoteId:   sig.fromId,
          remoteName: sig.fromName,
          direction:  'INCOMING',
          state:      'RINGING'
        };
        this.activeCall$.next(call);
        this.callState$.next('RINGING');

        // Créer la PC et définir la description distante maintenant
        // pour être prêt à répondre immédiatement
        this.createPeerConnection(sig.callId, sig.fromId);
        if (sig.sdp) {
          try {
            await this.pc!.setRemoteDescription(new RTCSessionDescription(sig.sdp));
          } catch (e) {
            console.error('[WebRTC] setRemoteDescription (offer) failed:', e);
          }
        }

        this.incomingCall$.next(call);
        break;
      }

      case 'CALL_ANSWER': {
        if (sig.toId !== myId || !this.pc) return;
        this.wasAnswered = true;
        clearTimeout(this.noAnswerTimer);

        if (sig.sdp) {
          try {
            await this.pc.setRemoteDescription(new RTCSessionDescription(sig.sdp));
            const call = this.activeCall$.value;
            if (call) {
              this.activeCall$.next({ ...call, state: 'CONNECTING' });
              this.callState$.next('CONNECTING');
            }
            // Vider les candidats ICE en attente
            for (const c of this.pendingCandidates) {
              try { await this.pc.addIceCandidate(new RTCIceCandidate(c)); } catch {}
            }
            this.pendingCandidates = [];
          } catch (e) {
            console.error('[WebRTC] setRemoteDescription (answer) failed:', e);
          }
        }
        break;
      }

      case 'CALL_REJECT':
      case 'CALL_END':
      case 'CALL_BUSY': {
        if (sig.toId !== myId) return;
        clearTimeout(this.noAnswerTimer);
        this.cleanUp(false);
        break;
      }
    }
  }

  private async onIceSignal(sig: IceSignal): Promise<void> {
    const myId = keycloak.tokenParsed?.['sub'];
    if (sig.toId !== myId) return;

    if (!this.pc || !this.pc.remoteDescription) {
      // Mettre en file d'attente — description distante pas encore définie
      this.pendingCandidates.push(sig.candidate);
      return;
    }

    try {
      await this.pc.addIceCandidate(new RTCIceCandidate(sig.candidate));
    } catch (e) {
      console.warn('[WebRTC] addIceCandidate error:', e);
    }
  }

  // ── RTCPeerConnection setup ───────────────────────────────────

  private createPeerConnection(callId: string, remoteId: string): void {
    if (this.pc) { this.pc.close(); this.pc = null; }

    this.remoteStream = new MediaStream();
    this.remoteStream$.next(this.remoteStream);

    this.pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });

    this.pc.onicecandidate = (event) => {
      if (event.candidate) {
        this.ws.sendIceCandidate(callId, remoteId, event.candidate.toJSON());
      }
    };

    this.pc.onconnectionstatechange = () => {
      const state = this.pc?.connectionState;
      console.log('[WebRTC] Connection state:', state);
      switch (state) {
        case 'connected': {
          const call = this.activeCall$.value;
          if (call) {
            const updated = { ...call, state: 'ACTIVE' as CallState, startedAt: new Date() };
            this.activeCall$.next(updated);
            this.callState$.next('ACTIVE');
            this.startDurationTimer();
          }
          break;
        }
        case 'failed':
        case 'disconnected':
        case 'closed':
          this.cleanUp(false);
          break;
      }
    };

    this.pc.ontrack = (event) => {
      event.streams[0]?.getTracks().forEach(t => {
        if (!this.remoteStream.getTracks().includes(t)) {
          this.remoteStream.addTrack(t);
        }
      });
      // Émettre le stream mis à jour
      this.remoteStream$.next(this.remoteStream);
    };
  }

  // ── Media ─────────────────────────────────────────────────────

  private async getUserMedia(callType: CallType): Promise<MediaStream> {
    try {
      return await navigator.mediaDevices.getUserMedia({
        audio: true,
        video: callType === 'VIDEO'
          ? { width: { ideal: 1280 }, height: { ideal: 720 }, facingMode: 'user' }
          : false
      });
    } catch (err: any) {
      if (callType === 'VIDEO' && (err.name === 'NotFoundError' || err.name === 'DevicesNotFoundError')) {
        console.warn('[WebRTC] Caméra non trouvée, repli sur audio uniquement');
        return navigator.mediaDevices.getUserMedia({ audio: true, video: false });
      }
      throw err;
    }
  }

  // ── Cleanup ───────────────────────────────────────────────────

  private cleanUp(sendEnd: boolean): void {
    clearInterval(this.durationTimer);
    clearTimeout(this.noAnswerTimer);
    this.pendingCandidates = [];
    this.wasAnswered = false;

    // Arrêter les pistes locales
    this.localStream?.getTracks().forEach(t => t.stop());
    this.localStream = null;
    this.localStream$.next(null);
    this.remoteStream$.next(null);

    // Fermer la connexion peer
    if (this.pc) {
      this.pc.onconnectionstatechange = null;
      this.pc.onicecandidate = null;
      this.pc.ontrack = null;
      this.pc.close();
      this.pc = null;
    }

    // Réinitialiser l'état — d'abord ENDED puis IDLE après un délai
    this.activeCall$.next(null);
    this.callState$.next('ENDED');
    setTimeout(() => {
      if (this.callState$.value === 'ENDED') {
        this.callState$.next('IDLE');
      }
    }, 600);
  }

  private startDurationTimer(): void {
    clearInterval(this.durationTimer);
    this.durationTimer = setInterval(() => {
      const call = this.activeCall$.value;
      if (!call?.startedAt) return;
      const secs = Math.floor((Date.now() - call.startedAt.getTime()) / 1000);
      this.activeCall$.next({ ...call, duration: secs });
    }, 1000);
  }

  private generateCallId(): string {
    return `call-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  }
}
