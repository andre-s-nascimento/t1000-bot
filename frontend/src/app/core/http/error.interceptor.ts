import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ToastService } from '../../shared/ui/toast.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401) {
        // deixa o authGuard cuidar (redirect pro Google)
        return throwError(() => err);
      }
      const msg =
        err.error?.erro || err.error?.message || err.message || 'Erro inesperado';
      toast.error(msg);
      return throwError(() => err);
    })
  );
};