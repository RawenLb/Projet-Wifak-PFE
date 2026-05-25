// src/app/services/chat.service.ts — Wifak Bank

import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Subscription } from 'rxjs';
import {
  ChatWebSocketService,
  ChatMessage,
  TypingEvent,
  PresenceEvent
} from './chat-websocket.service';
import keycloak from './keycloak.service';

export interface ChatUser {
  id:        string;
  username:  string;
  fullName:  string;
  role:      string;
  online:    boolean;
  unread:    number;
  lastMsg?:  string;
  lastTime?: string;
}

export interface Conversation {
  partner:  ChatUser;
  messages: ChatMessage[];
  typing:   boolean;
}

@Injectable({ providedIn: 'root' })
export class ChatService implements OnDestroy {

  private readonly API    = 'http://localhost:8083/api/chat';
  private readonly UPLOAD = 'http://localhost:8083/api/chat/upload';

  readonly contacts$    = new BehaviorSubject<ChatUser[]>([]);
  readonly activeConv$  = new BehaviorSubject<Conversation | null>(null);
  readonly totalUnread$ = new BehaviorSubject<number>(0);
  readonly chatOpen$    = new BehaviorSubject<boolean>(false);

  private subs:        Subscription[] = [];
  private initialized  = false;

  constructor(
    private ws:   ChatWebSocketService,
    private http: HttpClient
  ) {
    // Auto-connect WS immediately so calls can be received even before chat is opened
    this.ws.connect();
  }

  // ── Lifecycle ─────────────────────────────────────────────────

  init(): void {
    if (this.initialized) return;
    this.initialized = true;

    this.ws.connect();
    this.loadContacts();

    this.subs.push(
      this.ws.message$.subscribe(msg  => this.onMessage(msg)),
      this.ws.typing$.subscribe(evt   => this.onTyping(evt)),
      this.ws.presence$.subscribe(evt => this.onPresence(evt)),
      this.ws.history$.subscribe(msgs => this.onHistory(msgs)),
      this.ws.msgEdit$.subscribe(msg  => this.onMsgEdit(msg)),
      this.ws.msgDelete$.subscribe(ev => this.onMsgDelete(ev))
    );
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
    this.ws.disconnect();
    this.initialized = false;
  }

  // ── Contacts ──────────────────────────────────────────────────

  loadContacts(): void {
    this.http.get<any[]>(`${this.API}/contacts`).subscribe({
      next: raw => {
        const users: ChatUser[] = raw.map(u => ({
          id:       u.id,
          username: u.username,
          fullName: u.fullName || u.username,
          role:     u.role,
          online:   u.online  ?? false,
          unread:   u.unread  ?? 0,
          lastMsg:  u.lastMsg  ?? undefined,
          lastTime: u.lastTime ? this.normalizeDate(u.lastTime) : undefined
        }));
        this.contacts$.next(users);
        this.recalcUnread(users);
      },
      error: err => console.error('[Chat] Failed to load contacts:', err)
    });
  }

  // ── Open conversation ─────────────────────────────────────────

  openConversation(partner: ChatUser): void {
    const existing = this.activeConv$.value;
    if (existing?.partner.id === partner.id) {
      this.chatOpen$.next(true);
      return;
    }
    const conv: Conversation = { partner, messages: [], typing: false };
    this.activeConv$.next(conv);
    this.chatOpen$.next(true);

    const contacts = this.contacts$.value.map(c =>
      c.id === partner.id ? { ...c, unread: 0 } : c
    );
    this.contacts$.next(contacts);
    this.recalcUnread(contacts);

    this.http.post(`${this.API}/read/${partner.id}`, {}).subscribe();
    this.ws.requestHistory(partner.id);
  }

  closeChat(): void { this.chatOpen$.next(false); }

  // ── Send text ─────────────────────────────────────────────────

  sendMessage(content: string): void {
    const conv = this.activeConv$.value;
    if (!conv || !content.trim()) return;

    const token = keycloak.tokenParsed;
    this.ws.sendMessage({
      senderId:    token?.['sub']                ?? '',
      senderName:  token?.['preferred_username'] ?? '',
      senderRole:  this.getMyRole(),
      recipientId: conv.partner.id,
      content:     content.trim(),
      type:        'TEXT'
    });

    const optimistic: ChatMessage = {
      id:          `tmp-${Date.now()}`,
      senderId:    token?.['sub'] ?? '',
      senderName:  token?.['preferred_username'] ?? '',
      senderRole:  this.getMyRole(),
      recipientId: conv.partner.id,
      content:     content.trim(),
      timestamp:   new Date().toISOString(),
      read:        false,
      type:        'TEXT'
    };
    this.appendMessage(optimistic);
  }

  // ── Send file / image / voice ─────────────────────────────────

  sendFile(file: File): Promise<void> {
    const conv = this.activeConv$.value;
    if (!conv) return Promise.resolve();

    const formData = new FormData();
    formData.append('file', file);

    return new Promise((resolve, reject) => {
      this.http.post<{ url: string; fileName: string; type: string }>(
        this.UPLOAD, formData
      ).subscribe({
        next: res => {
          const token = keycloak.tokenParsed;
          const msgType = (res.type as ChatMessage['type']) || 'FILE';

          this.ws.sendMessage({
            senderId:    token?.['sub']                ?? '',
            senderName:  token?.['preferred_username'] ?? '',
            senderRole:  this.getMyRole(),
            recipientId: conv.partner.id,
            content:     res.fileName,
            type:        msgType,
            fileName:    res.fileName,
            fileUrl:     res.url
          });

          const optimistic: ChatMessage = {
            id:          `tmp-${Date.now()}`,
            senderId:    token?.['sub'] ?? '',
            senderName:  token?.['preferred_username'] ?? '',
            senderRole:  this.getMyRole(),
            recipientId: conv.partner.id,
            content:     res.fileName,
            timestamp:   new Date().toISOString(),
            read:        false,
            type:        msgType,
            fileName:    res.fileName,
            fileUrl:     res.url
          };
          this.appendMessage(optimistic);
          resolve();
        },
        error: err => {
          console.error('[Chat] File upload failed:', err);
          reject(err);
        }
      });
    });
  }

  sendTyping(typing: boolean): void {
    const conv = this.activeConv$.value;
    if (!conv) return;
    this.ws.sendTyping(conv.partner.id, typing);
  }

  // ── Message actions ───────────────────────────────────────────

  editMessage(messageId: string, newContent: string): void {
    this.ws.sendEditMessage(messageId, newContent);
    // Optimistic update
    const conv = this.activeConv$.value;
    if (!conv) return;
    const msgs = conv.messages.map(m =>
      m.id === messageId
        ? { ...m, content: newContent, editedAt: new Date().toISOString() }
        : m
    );
    this.activeConv$.next({ ...conv, messages: msgs });
  }

  deleteMessage(messageId: string): void {
    this.ws.sendDeleteMessage(messageId);
    // Optimistic update
    const conv = this.activeConv$.value;
    if (!conv) return;
    const msgs = conv.messages.map(m =>
      m.id === messageId
        ? { ...m, isDeleted: true, content: '', fileName: undefined, fileUrl: undefined }
        : m
    );
    this.activeConv$.next({ ...conv, messages: msgs });
  }

  forwardMessage(messageId: string, targetPartnerId: string): void {
    this.ws.sendForwardMessage(messageId, targetPartnerId);
  }

  // ── WS event handlers ─────────────────────────────────────────

  private onMessage(msg: ChatMessage): void {
    const normalized = this.normalizeMessage(msg);
    const myId  = this.getMyId();
    const conv  = this.activeConv$.value;
    const isActive = conv?.partner.id === normalized.senderId
                  || conv?.partner.id === normalized.recipientId;

    if (isActive) {
      this.appendMessage(normalized);
      if (normalized.senderId !== myId) {
        this.ws.markRead(normalized.id, normalized.senderId);
        this.http.post(`${this.API}/read/${normalized.senderId}`, {}).subscribe();
      }
    } else if (normalized.senderId !== myId) {
      const contacts = this.contacts$.value.map(c =>
        c.id === normalized.senderId
          ? { ...c, unread: c.unread + 1, lastMsg: normalized.content, lastTime: normalized.timestamp }
          : c
      );
      this.contacts$.next(contacts);
      this.recalcUnread(contacts);
    }
  }

  private onTyping(evt: TypingEvent): void {
    const conv = this.activeConv$.value;
    if (!conv || conv.partner.id !== evt.senderId) return;
    this.activeConv$.next({ ...conv, typing: evt.typing });
  }

  private onPresence(evt: PresenceEvent): void {
    const contacts = this.contacts$.value.map(c =>
      c.id === evt.userId ? { ...c, online: evt.online } : c
    );
    this.contacts$.next(contacts);
    const conv = this.activeConv$.value;
    if (conv?.partner.id === evt.userId) {
      this.activeConv$.next({ ...conv, partner: { ...conv.partner, online: evt.online } });
    }
  }

  private onHistory(msgs: unknown[]): void {
    const conv = this.activeConv$.value;
    if (!conv) return;
    const normalized = msgs.map(m => this.normalizeMessage(m as Record<string, unknown>));
    this.activeConv$.next({ ...conv, messages: normalized });
  }

  private onMsgEdit(updated: unknown): void {
    const conv = this.activeConv$.value;
    if (!conv) return;
    const normalized = this.normalizeMessage(updated as Record<string, unknown>);
    const msgs = conv.messages.map(m => m.id === normalized.id ? normalized : m);
    this.activeConv$.next({ ...conv, messages: msgs });
  }  private onMsgDelete(ev: { messageId: string }): void {
    const conv = this.activeConv$.value;
    if (!conv) return;
    const msgs = conv.messages.map(m =>
      String(m.id) === String(ev.messageId)
        ? { ...m, isDeleted: true, content: '', fileName: undefined, fileUrl: undefined }
        : m
    );
    this.activeConv$.next({ ...conv, messages: msgs });
  }

  private appendMessage(msg: ChatMessage): void {
    const conv = this.activeConv$.value;
    if (!conv) return;
    const msgs = [...conv.messages];
    const optIdx = msgs.findIndex(m =>
      m.id.startsWith('tmp-') &&
      m.senderId === msg.senderId &&
      m.content  === msg.content
    );
    if (optIdx !== -1) msgs.splice(optIdx, 1, msg);
    else msgs.push(msg);
    this.activeConv$.next({ ...conv, messages: msgs });

    const contacts = this.contacts$.value.map(c => {
      const isPartner = c.id === msg.senderId || c.id === msg.recipientId;
      return isPartner && c.id !== this.getMyId()
        ? { ...c, lastMsg: msg.content, lastTime: msg.timestamp }
        : c;
    });
    this.contacts$.next(contacts);
  }

  private recalcUnread(contacts: ChatUser[]): void {
    this.totalUnread$.next(contacts.reduce((s, c) => s + (c.unread || 0), 0));
  }

  // ── Normalization ─────────────────────────────────────────────

  private normalizeMessage(raw: Record<string, unknown> | ChatMessage): ChatMessage {
    const r = raw as Record<string, unknown>;
    let type = ((r['type'] ?? 'TEXT') as string).toUpperCase();

    if (type === 'FILE') {
      const name = (r['fileName'] ?? r['content'] ?? r['fileUrl'] ?? '') as string;
      if (/\.(jpg|jpeg|png|gif|webp|bmp|svg)$/i.test(name)) {
        type = 'IMAGE';
      }
    }

    if (!['TEXT','FILE','IMAGE','VOICE','SYSTEM','MISSED_CALL','CALL_ENDED'].includes(type)) {
      type = 'TEXT';
    }

    return {
      id:          String(r['id'] ?? `srv-${Date.now()}`),
      senderId:    (r['senderId']    as string) ?? '',
      senderName:  (r['senderName']  as string) ?? '',
      senderRole:  (r['senderRole']  as string) ?? '',
      recipientId: (r['recipientId'] as string) ?? '',
      content:     (r['content']     as string) ?? '',
      timestamp:   this.normalizeDate(r['timestamp'] ?? r['sentAt']),
      read:        (r['read'] ?? r['isRead'] ?? false) as boolean,
      type:        type as ChatMessage['type'],
      fileName:    (r['fileName']   as string) ?? undefined,
      fileUrl:     (r['fileUrl']    as string) ?? undefined,
      editedAt:    r['editedAt']   ? this.normalizeDate(r['editedAt']) : undefined,
      isDeleted:   (r['isDeleted']  as boolean) ?? false,
      isForwarded: (r['isForwarded'] as boolean) ?? false
    };
  }

  private normalizeDate(val: unknown): string {
    if (!val) return new Date().toISOString();
    if (typeof val === 'string') return val;
    if (typeof val === 'number') return new Date(val).toISOString();
    if (Array.isArray(val)) {
      const [y, mo, d, h = 0, min = 0, s = 0] = val as number[];
      return new Date(y, mo - 1, d, h, min, s).toISOString();
    }
    return new Date().toISOString();
  }

  // ── Identity ──────────────────────────────────────────────────

  getMyId(): string {
    return keycloak.tokenParsed?.['sub'] ?? '';
  }

  getMyRole(): string {
    const roles: string[] = keycloak.realmAccess?.roles ?? [];
    if (roles.includes('ROLE_MANAGER')) return 'MANAGER';
    if (roles.includes('ROLE_AGENT'))   return 'AGENT';
    return 'USER';
  }
}
