import { test, expect } from '@playwright/test';
import { gotoAdmin } from '../helpers/auth';

test.describe('Deep links (aliases e legacy)', () => {
  test('#copa abre Conteúdo & Automação > Copa', async ({ page }) => {
    await gotoAdmin(page, 'copa');

    await expect(page.locator('#tab-conteudo')).toHaveClass(/active/);
    await expect(page.locator('#sub-tab-copa')).toHaveClass(/active/);
    await expect(page.locator('#sub-copa')).toBeVisible();
  });

  test('#features abre Configurações > Features', async ({ page }) => {
    await gotoAdmin(page, 'features');

    await expect(page.locator('#tab-config')).toHaveClass(/active/);
    await expect(page.locator('#sub-tab-features')).toHaveClass(/active/);
  });

  test('#aniversarios abre Configurações > Aniversários', async ({ page }) => {
    await gotoAdmin(page, 'aniversarios');

    await expect(page.locator('#tab-config')).toHaveClass(/active/);
    await expect(page.locator('#sub-tab-aniversarios')).toHaveClass(/active/);
  });

  test('#monitoramento (alias) abre Monitoramento', async ({ page }) => {
    await gotoAdmin(page, 'monitoramento');

    await expect(page.locator('#tab-monitor')).toHaveClass(/active/);
  });

  test('#anuncios (alias) abre Anúncios', async ({ page }) => {
    await gotoAdmin(page, 'anuncios');

    await expect(page.locator('#tab-reminders')).toHaveClass(/active/);
  });

  test('#mensagens (legacy) continua funcionando', async ({ page }) => {
    await gotoAdmin(page, 'mensagens');

    await expect(page.locator('#tab-mensagens')).toHaveClass(/active/);
  });
});