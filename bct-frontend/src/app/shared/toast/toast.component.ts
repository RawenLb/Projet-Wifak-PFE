import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { Toast, ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-toast',
  templateUrl: './toast.component.html',
  styleUrls: ['./toast.component.scss']
})
export class ToastComponent implements OnInit, OnDestroy {
  toasts: Toast[] = [];
  private sub!: Subscription;

  constructor(private svc: ToastService) {}

  ngOnInit(): void {
    this.sub = this.svc.toasts$.subscribe((t: Toast[]) => { this.toasts = t; });
  }

  ngOnDestroy(): void { this.sub?.unsubscribe(); }

  dismiss(id: number): void { this.svc.dismiss(id); }
}