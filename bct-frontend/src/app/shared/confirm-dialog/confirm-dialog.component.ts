import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { ConfirmDialogData, ConfirmDialogService } from '../../services/confirm-dialog.service';

@Component({
  selector: 'app-confirm-dialog',
  templateUrl: './confirm-dialog.component.html',
  styleUrls: ['./confirm-dialog.component.scss']
})
export class ConfirmDialogComponent implements OnInit, OnDestroy {
  data: ConfirmDialogData | null = null;
  private sub!: Subscription;

  constructor(private svc: ConfirmDialogService) {}

  ngOnInit(): void {
    this.sub = this.svc.dialog$.subscribe((d: ConfirmDialogData | null) => { this.data = d; });
  }

  ngOnDestroy(): void { this.sub?.unsubscribe(); }

  confirm(): void { this.data?.resolve(true); this.data = null; }
  cancel(): void  { this.data?.resolve(false); this.data = null; }

  icon(): string {
    const icons: Record<string, string> = { danger: 'X', warning: '!', info: 'i' };
    return icons[this.data?.type ?? 'warning'] ?? '!';
  }
}