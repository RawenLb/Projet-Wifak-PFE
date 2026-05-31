// src/app/agent-notifications/agent-notifications.component.ts

import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { NotificationService, AppNotification } from '../../services/notification.service';

export type NotifTab = 'all' | 'reject' | 'deadline' | 'pending';

@Component({
  selector: 'app-agent-notifications',
  templateUrl: './agent-notifications.component.html',
  styleUrls: ['./agent-notifications.component.scss']
})
export class AgentNotificationsComponent implements OnInit, OnDestroy {

  allNotifs: AppNotification[] = [];
  activeTab: NotifTab = 'all';
  private sub!: Subscription;

  constructor(
    public notifSvc: NotificationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.sub = this.notifSvc.notifications$.subscribe(n => (this.allNotifs = n));
    this.notifSvc.loadNotifications();
  }

  ngOnDestroy(): void { this.sub?.unsubscribe(); }

  // ── Onglets ────────────────────────────────────────────────
  setTab(tab: NotifTab): void { this.activeTab = tab; }

  get filtered(): AppNotification[] {
    if (this.activeTab === 'all') return this.allNotifs;
    return this.allNotifs.filter(n => n.type === this.activeTab);
  }

  // ── Compteurs par onglet ───────────────────────────────────
  get totalUnread(): number   { return this.allNotifs.filter(n => n.unread).length; }
  get rejectCount(): number   { return this.allNotifs.filter(n => n.type === 'reject').length; }
  get rejectUnread(): number  { return this.allNotifs.filter(n => n.type === 'reject' && n.unread).length; }
  get deadlineCount(): number { return this.allNotifs.filter(n => n.type === 'deadline').length; }
  get deadlineUnread(): number{ return this.allNotifs.filter(n => n.type === 'deadline' && n.unread).length; }
  get pendingCount(): number  { return this.allNotifs.filter(n => n.type === 'pending').length; }
  get pendingUnread(): number { return this.allNotifs.filter(n => n.type === 'pending' && n.unread).length; }

  // ── Actions ────────────────────────────────────────────────
  markAll(): void { this.notifSvc.markAllRead(); }

  dismiss(id: number, event: Event): void {
    event.stopPropagation();
    this.notifSvc.markRead(id);
  }

  navigate(n: AppNotification): void {
    this.notifSvc.markRead(n.id);
    if (n.type === 'reject') {
      this.router.navigate(['/agent/declarations'], {
        queryParams: { highlight: n.declarationId, action: 'correct' }
      });
    } else if (n.type === 'deadline') {
      this.router.navigate(['/agent/declarations'], {
        queryParams: { highlight: n.declarationId, action: 'submit' }
      });
    } else if (n.type === 'pending') {
      this.router.navigate(['/agent/declarations'], {
        queryParams: { highlight: n.declarationId }
      });
    }
  }

  getActionLabel(n: AppNotification): string {
    if (n.type === 'reject')   return 'Corriger →';
    if (n.type === 'deadline') return 'Soumettre →';
    return 'Voir →';
  }

  getSeverityIcon(type: string): string {
    if (type === 'reject')   return 'danger';
    if (type === 'deadline') return 'warning';
    return 'info';
  }

  formatRelativeTime(date: string | Date | undefined): string {
    if (!date) return '';
    const d = typeof date === 'string' ? new Date(date) : date;
    if (!d || isNaN(d.getTime())) return '';
    const now  = new Date();
    const diff = Math.floor((now.getTime() - d.getTime()) / 1000);
    if (diff < 0)      return 'à venir';
    if (diff < 60)     return "à l'instant";
    if (diff < 3600)   return `il y a ${Math.floor(diff / 60)} min`;
    if (diff < 86400)  return `il y a ${Math.floor(diff / 3600)}h`;
    if (diff < 172800) return 'hier';
    return `il y a ${Math.floor(diff / 86400)}j`;
  }
}