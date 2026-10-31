import { Component, inject, signal, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <nav class="navbar navbar-expand-lg navbar-dark bg-dark fixed-top">
      <div class="container">
        <a class="navbar-brand" routerLink="/">
          <i class="bi bi-robot"></i> T-Mill Admin
        </a>
        <div class="ms-auto d-flex align-items-center gap-2">
          <button class="theme-toggle" type="button" (click)="toggleTheme()">
            <i class="bi" [class]="theme() === 'dark' ? 'bi-sun' : 'bi-moon-stars'"></i>
          </button>
          @if (user(); as u) {
            <span class="text-light small d-none d-lg-inline">{{ u.email }}</span>
          }
          <button class="btn btn-outline-light btn-sm" (click)="logout()">Sair</button>
        </div>
      </div>
    </nav>

    <div class="container" style="padding-top: 70px;">
      <ul class="nav nav-tabs" role="tablist">
        @for (tab of tabs; track tab.path) {
          <li class="nav-item">
            <a
              class="nav-link"
              [routerLink]="tab.path"
              routerLinkActive="active"
              [routerLinkActiveOptions]="{ exact: tab.path === '/' }">
              {{ tab.label }}
            </a>
          </li>
        }
      </ul>

      <div class="tab-content mt-3">
        <router-outlet />
      </div>
    </div>
  `
})
export class ShellComponent implements OnInit {
  private auth = inject(AuthService);

  readonly user = this.auth.user;
  readonly theme = signal<'light' | 'dark'>('light');

  readonly tabs = [
    { path: '/mensagens',     label: '📨 Mensagens' },
    { path: '/copa',          label: '⚽ Copa' },
    { path: '/digest',        label: '📰 Digest' },
    { path: '/lembretes',     label: '📅 Lembretes' },
    { path: '/auto-response', label: '🤖 Auto-Response' },
    { path: '/aniversarios',  label: '🎂 Aniversários' },
    { path: '/features',      label: '🎛️ Features' },
    { path: '/admin',         label: '⚙️ Admin' },
    { path: '/moderacao',     label: '🔨 Moderação' },
    { path: '/monitor',       label: '📊 Monitor' },
  ];

  ngOnInit() {
    const saved = localStorage.getItem('tmill-theme') as 'light' | 'dark' | null;
    const prefersDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
    this.setTheme(saved ?? (prefersDark ? 'dark' : 'light'));
  }

  toggleTheme() {
    this.setTheme(this.theme() === 'dark' ? 'light' : 'dark');
  }

  private setTheme(t: 'light' | 'dark') {
    this.theme.set(t);
    document.documentElement.setAttribute('data-bs-theme', t);
    localStorage.setItem('tmill-theme', t);
  }

  logout() {
    this.auth.logout();
  }
}