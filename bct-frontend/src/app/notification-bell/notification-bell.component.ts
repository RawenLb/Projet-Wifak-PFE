// src/app/components/notification-bell/notification-bell.component.ts
// ✅ NOUVEAU FICHIER — cloche de notifications dans le topbar

import {
  Component, OnInit, OnDestroy, HostListener
} from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { NotificationService, AppNotification } from '../services/notification.service';

@Component({
  selector: 'app-notification-bell',
  templateUrl: './notification-bell.component.html',
  styleUrls: ['./notification-bell.component.scss']
})
export class NotificationBellComponent implements OnInit, OnDestroy {

  open = false;
  tab: 'all' | 'deadline' | 'reject' | 'pending' = 'all';
  private sub!: Subscription;
  allNotifs: AppNotification[] = [];

  get unreadCount(): number { return this.notifSvc.unreadCount; }

  get deadlineUnread(): number {
    return this.allNotifs.filter(n => n.type === 'deadline' && n.unread).length;
  }
  get rejectUnread(): number {
    return this.allNotifs.filter(n => n.type === 'reject' && n.unread).length;
  }
  get pendingUnread(): number {
    return this.allNotifs.filter(n => n.type === 'pending' && n.unread).length;
  }

  get filtered(): AppNotification[] {
    if (this.tab === 'all') return this.allNotifs;
    return this.allNotifs.filter(n => n.type === this.tab);
  }

  constructor(
    public notifSvc: NotificationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.sub = this.notifSvc.notifications$.subscribe(n => (this.allNotifs = n));
  }

  ngOnDestroy(): void { this.sub?.unsubscribe(); }

  toggle($event: Event): void {
    $event.stopPropagation();
    this.open = !this.open;
  }

  markAll(): void { this.notifSvc.markAllRead(); }

  dismiss(id: number, $event: Event): void {
    $event.stopPropagation();
    this.notifSvc.markRead(id);
  }

  navigate(n: AppNotification): void {
    this.open = false;
    this.notifSvc.markRead(n.id);
    if (n.type === 'reject' || n.type === 'deadline') {
      this.router.navigate(['/agent/declarations'], {
        queryParams: { highlight: n.declarationId, tab: n.type }
      });
    } else if (n.type === 'pending') {
      this.router.navigate(['/manager/dashboard'], {
        queryParams: { highlight: n.declarationId }
      });
    }
  }

  @HostListener('document:click')
  onDocumentClick(): void { this.open = false; }

  stopPropagation($event: Event): void { $event.stopPropagation(); }
}