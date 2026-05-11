// src/app/services/chat-websocket.service.ts — Wifak Bank

import { Injectable, OnDestroy } from '@angular/core';
import { Subject, BehaviorSubject } from 'rxjs';
import keycloak from './keycloak.service';

export interface ChatMessage {
  id:           string;
  senderId:     string;
  senderName:   string;
  senderRole:   string;
  recipientId:  string;
  content:      string;
  timestamp:    string;
  read:         boolean;
  type:         'TEXT' | 'FILE' | 'IMAGE' | 'VOICE' | 'SYSTEM' | 'MISSED_CALL' | 'CALL_ENDED';
  fileName?:    string;
  fileUrl?:     string;
  editedAt?:    string;
  isDeleted?:   boolean;
  isForwarded?: boolean;
}

export interface TypingEvent {
  senderId:    string;
  senderName:  string;
  recipientId: string;
  typing:      boolean;
}

export interface PresenceEvent {
  userId:   string;
  username: string;
  online:   boolean;
}

export type CallType = 'AUDIO' | 'VIDEO';

export interface CallSignal {
  type:      'CALL_OFFER' | 'CALL_ANSWER' | 'CALL_REJECT' | 'CALL_END' | 'CALL_BUSY';
  callId:    string;
  callType:  CallType;
  fromId:    string;
  fromName:  string;
  toId:      string;
  sdp?:      RTCSessionDescriptionInit;
}

export interface IceSignal {
  type:      'ICE_CANDIDATE';
  callId:    string;
  fromId:    string;
  toId:      string;
  candidate: RTCIceCandidateInit;
}

export interface WsEnvelope {
  type:    string;
  payload: any;
}

@Injectable({ providedIn: 'root' })
export class ChatWebSocketService implements OnDestroy {

  private ws:             WebSocket | null = null;
  private reconnectTimer: any = null;
  private reconnectDelay  = 3000;
  private maxReconnects   = 20;
  private reconnectCount  = 0;
  private manualClose     = false;

  readonly message$    = new Subject<ChatMessage>();
  readonly typing$     = new Subject<TypingEvent>();
  readonly presence$   = new Subject<PresenceEvent>();
  readonly history$    = new Subject<ChatMessage[]>();
  readonly connected$  = new BehaviorSubject<boolean>(false);
  readonly callSignal$ = new Subject<CallSignal>();
  readonly iceSignal$  = new Subject<IceSignal>();
  readonly msgEdit$    = new Subject<ChatMessage>();
  readonly msgDelete$  = new Subject<{ messageId: string }>();

  private readonly WS_URL = 'ws://localhost:8083/ws/chat';

  // ── Connect ───────────────────────────────────────────────────

  connect(): void {
    if (this.ws?.readyState === WebSocket.OPEN ||
        this.ws?.readyState === WebSocket.CONNECTING) return;

    const token = keycloak.token;
    if (!token) {
      console.warn('[WS] No token available, retrying in 3s...');
      setTimeout(() => this.connect(), 3000);
      return;
    }

    this.manualClose = false;
    const url = `${this.WS_URL}?token=${encodeURIComponent(token)}`;

    try {
      this.ws = new WebSocket(url);

      this.ws.onopen = () => {
        this.connected$.next(true);
        this.reconnectCount = 0;
        console.log('[WS] Connected to chat server');
      };

      this.ws.onmessage = (event) => {
        try {
          const envelope: WsEnvelope = JSON.parse(event.data);
          this.dispatch(envelope);
        } catch (e) {
          console.warn('[WS] Parse error', e);
        }
      };

      this.ws.onclose = (ev) => {
        this.connected$.next(false);
        console.log('[WS] Closed:', ev.code, ev.reason);
        if (!this.manualClose) this.scheduleReconnect();
      };

      this.ws.onerror = (err) => {
        console.error('[WS] Error:', err);
        this.ws?.close();
      };

    } catch (e) {
      console.error('[WS] Cannot open socket', e);
      this.scheduleReconnect();
    }
  }

  disconnect(): void {
    this.manualClose = true;
    clearTimeout(this.reconnectTimer);
    this.ws?.close();
    this.ws = null;
    this.connected$.next(false);
  }

  send(envelope: WsEnvelope): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(envelope));
    } else {
      console.warn('[WS] Cannot send — not connected. State:', this.ws?.readyState);
    }
  }

  // ── Chat helpers ──────────────────────────────────────────────

  sendMessage(msg: Omit<ChatMessage, 'id' | 'timestamp' | 'read'>): void {
    this.send({ type: 'MESSAGE', payload: msg });
  }

  sendTyping(recipientId: string, typing: boolean): void {
    const t = keycloak.tokenParsed;
    this.send({
      type: 'TYPING',
      payload: { senderId: t?.['sub'], senderName: t?.['preferred_username'], recipientId, typing }
    });
  }

  markRead(messageId: string, senderId: string): void {
    this.send({ type: 'READ', payload: { messageId, senderId } });
  }

  requestHistory(withUserId: string): void {
    this.send({ type: 'HISTORY', payload: { withUserId } });
  }

  // ── WebRTC signaling ──────────────────────────────────────────

  sendCallOffer(callId: string, callType: CallType, toId: string, sdp: RTCSessionDescriptionInit): void {
    const t = keycloak.tokenParsed;
    this.send({
      type: 'CALL_OFFER',
      payload: {
        type: 'CALL_OFFER', callId, callType,
        fromId: t?.['sub'], fromName: t?.['preferred_username'] ?? '', toId, sdp
      }
    });
  }

  sendCallAnswer(callId: string, callType: CallType, toId: string, sdp: RTCSessionDescriptionInit): void {
    const t = keycloak.tokenParsed;
    this.send({
      type: 'CALL_ANSWER',
      payload: {
        type: 'CALL_ANSWER', callId, callType,
        fromId: t?.['sub'], fromName: t?.['preferred_username'] ?? '', toId, sdp
      }
    });
  }

  sendCallReject(callId: string, toId: string, callType: CallType = 'AUDIO'): void {
    const t = keycloak.tokenParsed;
    this.send({
      type: 'CALL_REJECT',
      payload: {
        type: 'CALL_REJECT', callId, callType,
        fromId: t?.['sub'], fromName: t?.['preferred_username'] ?? '', toId
      }
    });
  }

  sendCallEnd(callId: string, toId: string, callType: CallType = 'AUDIO', wasAnswered = false, durationSec = 0): void {
    const t = keycloak.tokenParsed;
    this.send({
      type: 'CALL_END',
      payload: {
        type: 'CALL_END', callId, callType,
        fromId: t?.['sub'], fromName: t?.['preferred_username'] ?? '', toId,
        wasAnswered, durationSec
      }
    });
  }

  sendIceCandidate(callId: string, toId: string, candidate: RTCIceCandidateInit): void {
    const t = keycloak.tokenParsed;
    this.send({
      type: 'ICE_CANDIDATE',
      payload: {
        type: 'ICE_CANDIDATE', callId,
        fromId: t?.['sub'], toId, candidate
      }
    });
  }

  // ── Message actions ───────────────────────────────────────────

  sendEditMessage(messageId: string, content: string): void {
    this.send({ type: 'MSG_EDIT', payload: { messageId, content } });
  }

  sendDeleteMessage(messageId: string): void {
    this.send({ type: 'MSG_DELETE', payload: { messageId } });
  }

  sendForwardMessage(messageId: string, targetId: string): void {
    this.send({ type: 'MSG_FORWARD', payload: { messageId, targetId } });
  }

  // ── Dispatch ──────────────────────────────────────────────────

  private dispatch(envelope: WsEnvelope): void {
    switch (envelope.type) {
      case 'MESSAGE':       this.message$.next(envelope.payload);    break;
      case 'TYPING':        this.typing$.next(envelope.payload);     break;
      case 'PRESENCE':      this.presence$.next(envelope.payload);   break;
      case 'HISTORY':       this.history$.next(envelope.payload);    break;
      case 'CALL_OFFER':
      case 'CALL_ANSWER':
      case 'CALL_REJECT':
      case 'CALL_END':
      case 'CALL_BUSY':     this.callSignal$.next(envelope.payload); break;
      case 'ICE_CANDIDATE': this.iceSignal$.next(envelope.payload);  break;
      case 'MSG_EDIT':      this.msgEdit$.next(envelope.payload);    break;
      case 'MSG_DELETE':    this.msgDelete$.next(envelope.payload);  break;
      default:
        console.debug('[WS] Unhandled type:', envelope.type);
    }
  }

  private scheduleReconnect(): void {
    if (this.reconnectCount >= this.maxReconnects) {
      console.warn('[WS] Max reconnects reached');
      return;
    }
    this.reconnectCount++;
    const delay = Math.min(this.reconnectDelay * this.reconnectCount, 30000);
    console.log(`[WS] Reconnecting in ${delay}ms (attempt ${this.reconnectCount})`);
    this.reconnectTimer = setTimeout(() => this.connect(), delay);
  }

  ngOnDestroy(): void { this.disconnect(); }
}
