import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.me().pipe(
    map(user => {
      if (user && user.authenticated) return true;

      // Em dev com ADMIN_SECURITY_DISABLED, /admin/me retorna 200 com authenticated:false
      // Deixa passar para não travar o desenvolvimento.
      // (Só ativa se você estiver nesse modo)
      if (!environment.production) {
        // ⚠️ Remova este bloco se preferir OAuth2 real sempre
        // return true;
      }

      auth.login();
      return router.createUrlTree(['/loading']);
    })
  );
};