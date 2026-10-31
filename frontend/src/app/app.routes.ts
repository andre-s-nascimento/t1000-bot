import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { ShellComponent } from './core/layout/shell.component';

export const routes: Routes = [
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'features', pathMatch: 'full' },
      {
        path: 'features',
        loadComponent: () =>
          import('./features/features/features.component').then(m => m.FeaturesComponent)
      },
      {
        path: 'mensagens',
        loadComponent: () =>
          import('./features/mensagens/mensagens.component').then(m => m.MensagensComponent)
      },
      {
        path: 'copa',
        loadComponent: () =>
          import('./features/copa/copa.component').then(m => m.CopaComponent)
      },
      {
        path: 'digest',
        loadComponent: () =>
          import('./features/digest/digest.component').then(m => m.DigestComponent)
      },
      {
        path: 'lembretes',
        loadComponent: () =>
          import('./features/lembretes/lembretes.component').then(m => m.LembretesComponent)
      },
      {
        path: 'auto-response',
        loadComponent: () =>
          import('./features/auto-response/auto-response.component').then(m => m.AutoResponseComponent)
      },
      {
        path: 'aniversarios',
        loadComponent: () =>
          import('./features/aniversarios/aniversarios.component').then(m => m.AniversariosComponent)
      },
      {
        path: 'admin',
        loadComponent: () =>
          import('./features/admin/admin.component').then(m => m.AdminComponent)
      },
      {
        path: 'moderacao',
        loadComponent: () =>
          import('./features/moderacao/moderacao.component').then(m => m.ModeracaoComponent)
      },
      {
        path: 'monitor',
        loadComponent: () =>
          import('./features/monitor/monitor.component').then(m => m.MonitorComponent)
      },
    ]
  },
  { path: 'loading', component: ShellComponent },
  { path: '**', redirectTo: '' }
];