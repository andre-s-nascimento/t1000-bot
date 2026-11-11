import { Page } from '@playwright/test';

/**
 * Autentica no painel admin.
 *
 * Estratégia: como o projeto usa Google OAuth2 em produção,
 * nos testes E2E usamos o modo dev (ADMIN_SECURITY_DISABLED=true)
 * que libera /admin-web sem login.
 *
 * Para testar o fluxo de login real, seria necessário mockar o
 * OAuth2 (Playwright route interception ou MSW). Fica fora do escopo
 * deste PR.
 */
export async function gotoAdmin(page: Page, hash?: string) {
  const url = hash ? `/admin-web#${hash}` : '/admin-web';
  await page.goto(url);
  // Aguarda o painel renderizar (status bar é o primeiro elemento visível)
  await page.waitForSelector('.status-bar');
}