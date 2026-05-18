import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

export interface ConfirmDialogData {
  title: string;
  message: string;
  detail?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  type?: 'danger' | 'warning' | 'info';
  resolve: (result: boolean) => void;
}

@Injectable({ providedIn: 'root' })
export class ConfirmDialogService {

  private _dialog$ = new Subject<ConfirmDialogData | null>();
  dialog$ = this._dialog$.asObservable();

  confirm(
    title: string,
    message: string,
    options?: {
      detail?: string;
      confirmLabel?: string;
      cancelLabel?: string;
      type?: 'danger' | 'warning' | 'info';
    }
  ): Promise<boolean> {
    return new Promise(resolve => {
      this._dialog$.next({
        title,
        message,
        detail: options?.detail,
        confirmLabel: options?.confirmLabel ?? 'Confirmer',
        cancelLabel: options?.cancelLabel ?? 'Annuler',
        type: options?.type ?? 'warning',
        resolve
      });
    });
  }

  close(): void {
    this._dialog$.next(null);
  }
}