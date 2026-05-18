import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

export type ToastType = 'success' | 'error' | 'warning' | 'info';

export interface Toast {
  id: number;
  type: ToastType;
  message: string;
  duration: number;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private counter = 0;
  private _toasts$ = new Subject<Toast[]>();
  private toasts: Toast[] = [];
  toasts$ = this._toasts$.asObservable();

  show(type: ToastType, message: string, duration = 4000): void {
    const id = ++this.counter;
    const toast: Toast = { id, type, message, duration };
    this.toasts = [...this.toasts, toast];
    this._toasts$.next(this.toasts);
    setTimeout(() => this.dismiss(id), duration);
  }

  success(msg: string): void { this.show('success', msg); }
  error(msg: string): void   { this.show('error', msg, 6000); }
  warning(msg: string): void { this.show('warning', msg, 5000); }
  info(msg: string): void    { this.show('info', msg); }

  dismiss(id: number): void {
    this.toasts = this.toasts.filter(t => t.id !== id);
    this._toasts$.next(this.toasts);
  }
}