import { Injectable, signal } from '@angular/core';

export interface Toast {
  id: number;
  message: string;
  variant: 'success' | 'danger' | 'info' | 'warning';
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private counter = 0;
  readonly toasts = signal<Toast[]>([]);

  success(message: string) { this.push(message, 'success'); }
  error(message: string)   { this.push(message, 'danger'); }
  info(message: string)    { this.push(message, 'info'); }
  warning(message: string) { this.push(message, 'warning'); }

  private push(message: string, variant: Toast['variant']) {
    const id = ++this.counter;
    this.toasts.update(t => [...t, { id, message, variant }]);
    setTimeout(() => this.dismiss(id), 5000);
  }

  dismiss(id: number) {
    this.toasts.update(t => t.filter(x => x.id !== id));
  }
}