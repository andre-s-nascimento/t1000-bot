import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, map, of, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AdminUser {
  email: string;
  name: string;
  sub: string;
  authenticated: boolean;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  readonly user = signal<AdminUser | null>(null);
  readonly checked = signal(false);

  me() {
    if (this.checked()) return of(this.user());

    return this.http
      .get<AdminUser>(`${environment.apiBase}/admin/me`, { withCredentials: true })
      .pipe(
        tap(user => {
          this.user.set(user);
          this.checked.set(true);
        }),
        catchError(() => {
          this.user.set(null);
          this.checked.set(true);
          return of(null);
        })
      );
  }

  isAuthenticated(): boolean {
    return this.user()?.authenticated === true;
  }

  login(): void {
    window.location.href = environment.loginUrl;
  }

  logout(): void {
    window.location.href = '/logout';
  }

  guard() {
    return this.me().pipe(
      map(user => {
        if (!user || !user.authenticated) {
          this.login();
          return false;
        }
        return true;
      })
    );
  }
}