import { Component, inject } from '@angular/core';
import { ToastService } from './toast.service';

@Component({
  selector: 'app-toast',
  standalone: true,
  template: `
    <div class="toast-container position-fixed top-0 end-0 p-3" style="z-index: 9999;">
      @for (toast of toasts(); track toast.id) {
        <div class="alert alert-{{ toast.variant }} shadow-sm mb-2 d-flex align-items-center" role="alert">
          <span class="flex-grow-1">{{ toast.message }}</span>
          <button type="button" class="btn-close ms-2" (click)="svc.dismiss(toast.id)" aria-label="Fechar"></button>
        </div>
      }
    </div>
  `
})
export class ToastComponent {
  protected svc = inject(ToastService);
  protected toasts = this.svc.toasts;
}