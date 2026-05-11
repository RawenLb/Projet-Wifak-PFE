// src/app/chat/chat.component.ts — Wifak Bank Chat

import {
  Component, OnInit, OnDestroy, ViewChild, ElementRef,
  AfterViewChecked, ChangeDetectorRef
} from '@angular/core';
import { Subscription } from 'rxjs';
import { ChatService, ChatUser, Conversation } from '../../services/chat.service';
import { ChatWebSocketService } from '../../services/chat-websocket.service';
import { WebRtcService } from '../../services/webrtc.service';
import keycloak from '../../services/keycloak.service';

@Component({
  selector:    'app-chat',
  templateUrl: './chat.component.html',
  styleUrls:   ['./chat.component.scss']
})
export class ChatComponent implements OnInit, OnDestroy, AfterViewChecked {

  @ViewChild('messagesContainer') private msgContainer!: ElementRef<HTMLDivElement>;
  @ViewChild('inputRef')          private inputRef!:     ElementRef<HTMLTextAreaElement>;
  @ViewChild('fileInput')         private fileInput!:    ElementRef<HTMLInputElement>;

  isOpen        = false;
  contacts:     ChatUser[]     = [];
  activeConv:   Conversation | null = null;
  totalUnread   = 0;
  wsConnected   = false;
  contactSearch = '';
  inputText     = '';
  uploadingFile = false;

  // Voice recording state
  isRecording    = false;
  recordingTime  = 0;
  private mediaRecorder:   MediaRecorder | null = null;
  private audioChunks:     Blob[] = [];
  private recordingTimer:  any;

  myId       = '';
  myInitials = '';
  myRole     = '';

  private typingTimer:  any;
  private isTyping      = false;
  private shouldScroll  = false;
  private subs: Subscription[] = [];

  constructor(
    private chatSvc: ChatService,
    private wsSvc:   ChatWebSocketService,
    public  rtc:     WebRtcService,
    private cdr:     ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    const token     = keycloak.tokenParsed;
    this.myId       = token?.['sub'] ?? '';
    const name      = token?.['name'] ?? token?.['preferred_username'] ?? 'Moi';
    this.myInitials = name.split(' ').map((p: string) => p[0]).slice(0, 2).join('').toUpperCase();
    this.myRole     = this.chatSvc.getMyRole();

    this.chatSvc.init();

    this.subs.push(
      this.chatSvc.contacts$.subscribe(c  => { this.contacts  = c; this.cdr.markForCheck(); }),
      this.chatSvc.activeConv$.subscribe(c => {
        this.activeConv   = c;
        this.shouldScroll = true;
        this.cdr.markForCheck();
      }),
      this.chatSvc.totalUnread$.subscribe(n => { this.totalUnread = n; this.cdr.markForCheck(); }),
      this.chatSvc.chatOpen$.subscribe(o    => { this.isOpen = o; this.cdr.markForCheck(); }),
      this.wsSvc.connected$.subscribe(c     => { this.wsConnected = c; this.cdr.markForCheck(); })
    );
  }

  ngAfterViewChecked(): void {
    if (this.shouldScroll) {
      this.scrollToBottom();
      this.shouldScroll = false;
    }
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
    clearTimeout(this.typingTimer);
    this.stopRecording();
  }

  // ── UI ────────────────────────────────────────────────────────

  toggleChat(): void {
    if (this.isOpen) this.chatSvc.closeChat();
    else             this.chatSvc.chatOpen$.next(true);
  }

  openConversation(c: ChatUser): void {
    this.chatSvc.openConversation(c);
    this.contactSearch = '';
    setTimeout(() => this.inputRef?.nativeElement.focus(), 100);
  }

  backToContacts(): void {
    this.chatSvc.activeConv$.next(null);
  }

  sendMessage(): void {
    if (!this.inputText.trim()) return;
    this.chatSvc.sendMessage(this.inputText);
    this.inputText    = '';
    this.shouldScroll = true;
    this.stopTyping();
    this.autoResizeInput();
  }

  onKeyDown(e: KeyboardEvent): void {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      this.sendMessage();
    }
  }

  onInput(): void {
    this.autoResizeInput();
    if (!this.isTyping) {
      this.isTyping = true;
      this.chatSvc.sendTyping(true);
    }
    clearTimeout(this.typingTimer);
    this.typingTimer = setTimeout(() => this.stopTyping(), 2000);
  }

  private stopTyping(): void {
    if (this.isTyping) {
      this.isTyping = false;
      this.chatSvc.sendTyping(false);
    }
  }

  // ── File upload ───────────────────────────────────────────────

  triggerFileInput(): void {
    this.fileInput?.nativeElement.click();
  }

  onFileSelected(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;

    if (file.size > 20 * 1024 * 1024) {
      alert('Fichier trop volumineux (max 20 Mo)');
      (event.target as HTMLInputElement).value = '';
      return;
    }

    this.uploadingFile = true;
    this.chatSvc.sendFile(file).finally(() => {
      this.uploadingFile = false;
      this.cdr.markForCheck();
    });

    (event.target as HTMLInputElement).value = '';
  }

  openFile(url: string): void {
    if (!url) return;
    // Files are served without auth (UUID names) — direct open works
    window.open(url, '_blank', 'noopener,noreferrer');
  }

  downloadFile(url: string, fileName: string): void {
    if (!url) return;
    // Create a temporary anchor to trigger download
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName || 'download';
    a.target = '_blank';
    a.rel = 'noopener';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  }

  onImgError(event: Event): void {
    // If image fails to load, replace with a broken image placeholder
    const img = event.target as HTMLImageElement;
    img.style.display = 'none';
    const parent = img.parentElement;
    if (parent) {
      parent.innerHTML = '<div style="padding:8px;font-size:12px;opacity:.6">Image non disponible</div>';
    }
  }

  // ── Voice recording ───────────────────────────────────────────

  async startRecording(): Promise<void> {
    if (this.isRecording) return;
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      this.audioChunks = [];

      // Prefer webm/opus, fallback to whatever is supported
      const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
        ? 'audio/webm;codecs=opus'
        : MediaRecorder.isTypeSupported('audio/ogg;codecs=opus')
          ? 'audio/ogg;codecs=opus'
          : '';

      this.mediaRecorder = mimeType
        ? new MediaRecorder(stream, { mimeType })
        : new MediaRecorder(stream);

      this.mediaRecorder.ondataavailable = (e) => {
        if (e.data.size > 0) this.audioChunks.push(e.data);
      };

      this.mediaRecorder.onstop = () => {
        stream.getTracks().forEach(t => t.stop());
        const blob = new Blob(this.audioChunks, {
          type: this.mediaRecorder?.mimeType || 'audio/webm'
        });
        this.sendVoiceMessage(blob);
      };

      this.mediaRecorder.start(100); // collect data every 100ms
      this.isRecording   = true;
      this.recordingTime = 0;

      this.recordingTimer = setInterval(() => {
        this.recordingTime++;
        this.cdr.markForCheck();
        // Auto-stop at 2 minutes
        if (this.recordingTime >= 120) this.stopRecording();
      }, 1000);

      this.cdr.markForCheck();
    } catch (err) {
      console.error('[Voice] Microphone access denied:', err);
      alert('Accès au microphone refusé. Veuillez autoriser l\'accès dans les paramètres du navigateur.');
    }
  }

  stopRecording(): void {
    clearInterval(this.recordingTimer);
    if (this.mediaRecorder?.state === 'recording') {
      this.mediaRecorder.stop();
    }
    this.isRecording   = false;
    this.recordingTime = 0;
    this.cdr.markForCheck();
  }

  cancelRecording(): void {
    clearInterval(this.recordingTimer);
    if (this.mediaRecorder?.state === 'recording') {
      // Override onstop to discard
      this.mediaRecorder.onstop = () => {
        this.mediaRecorder?.stream?.getTracks().forEach(t => t.stop());
      };
      this.mediaRecorder.stop();
    }
    this.audioChunks   = [];
    this.isRecording   = false;
    this.recordingTime = 0;
    this.cdr.markForCheck();
  }

  private sendVoiceMessage(blob: Blob): void {
    const ext  = blob.type.includes('ogg') ? '.ogg' : '.webm';
    const file = new File([blob], `voice-${Date.now()}${ext}`, { type: blob.type });
    this.uploadingFile = true;
    this.chatSvc.sendFile(file).finally(() => {
      this.uploadingFile = false;
      this.cdr.markForCheck();
    });
  }

  formatRecordingTime(secs: number): string {
    const m = Math.floor(secs / 60).toString().padStart(2, '0');
    const s = (secs % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  }

  // ── WebRTC calls ──────────────────────────────────────────────

  startAudioCall(): void {
    const conv = this.activeConv;
    if (!conv) return;
    this.rtc.startCall(conv.partner.id, conv.partner.fullName, 'AUDIO');
  }

  startVideoCall(): void {
    const conv = this.activeConv;
    if (!conv) return;
    this.rtc.startCall(conv.partner.id, conv.partner.fullName, 'VIDEO');
  }

  // ── Helpers ───────────────────────────────────────────────────

  get filteredContacts(): ChatUser[] {
    const q = this.contactSearch.toLowerCase().trim();
    if (!q) return this.contacts;
    return this.contacts.filter(c =>
      c.fullName.toLowerCase().includes(q) ||
      c.username.toLowerCase().includes(q)
    );
  }

  getInitials(name: string): string {
    return (name || '?').split(' ').map(p => p[0]).slice(0, 2).join('').toUpperCase();
  }

  getAvatarColor(username: string): string {
    const colors = [
      'linear-gradient(135deg,#1B4FBC,#0D2B5E)',
      'linear-gradient(135deg,#0A7A5A,#065F3A)',
      'linear-gradient(135deg,#B8651A,#92400E)',
      'linear-gradient(135deg,#5B35C2,#3B1FA0)',
      'linear-gradient(135deg,#C8192E,#A01020)',
      'linear-gradient(135deg,#0F72C2,#0A4E8A)',
    ];
    const hash = (username || '').split('').reduce((a, c) => c.charCodeAt(0) + ((a << 5) - a), 0);
    return colors[Math.abs(hash) % colors.length];
  }

  formatTime(iso: string): string {
    if (!iso) return '';
    const d    = new Date(iso);
    const now  = new Date();
    const diff = now.getTime() - d.getTime();
    if (diff < 60000)    return 'maintenant';
    if (diff < 3600000)  return `${Math.floor(diff / 60000)}m`;
    if (diff < 86400000) return d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
    return d.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' });
  }

  formatMsgTime(iso: string): string {
    if (!iso) return '';
    return new Date(iso).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  }

  formatDateSep(iso: string): string {
    const d         = new Date(iso);
    const today     = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(today.getDate() - 1);
    if (d.toDateString() === today.toDateString())     return "Aujourd'hui";
    if (d.toDateString() === yesterday.toDateString()) return 'Hier';
    return d.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });
  }

  showDateSep(i: number): boolean {
    if (!this.activeConv) return false;
    const msgs = this.activeConv.messages;
    if (i === 0) return true;
    const prev = new Date(msgs[i - 1].timestamp).toDateString();
    const curr = new Date(msgs[i].timestamp).toDateString();
    return prev !== curr;
  }

  private scrollToBottom(): void {
    try {
      const el = this.msgContainer?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    } catch {}
  }

  private autoResizeInput(): void {
    const el = this.inputRef?.nativeElement;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = Math.min(el.scrollHeight, 120) + 'px';
  }
}
