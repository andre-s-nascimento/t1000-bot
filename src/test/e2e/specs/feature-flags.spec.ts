import { test, expect } from '@playwright/test';
import { gotoAdmin } from '../helpers/auth';

test.describe('Feature flags — feedback visual', () => {
  test.beforeEach(async ({ page }) => {
    await gotoAdmin(page, 'admin');
    await page.locator('#tab-config').click();
    await page.locator('#sub-tab-features').click();
    // Aguarda o painel de flags carregar
    await page.waitForSelector('#featureFlagsContainer .feature-flag-row', { timeout: 10000 });
  });

  test('renderiza todas as flags com switches', async ({ page }) => {
    const rows = page.locator('#featureFlagsContainer .feature-flag-row');
    await expect(rows.first()).toBeVisible();
    // Mínimo esperado (o backend registra 23, mas o número pode variar)
    expect(await rows.count()).toBeGreaterThanOrEqual(20);
  });

  test('desligar worldcup.enabled acinzenta a sub-pill Copa', async ({ page }) => {
    // Garante que o painel está em Conteúdo & Automação com a sub-pill Copa
    // visível. Como o teste está em Configurações, primeiro precisamos
    // alternar a flag.
    const worldcupSwitch = page.locator('#flag-worldcup\\.enabled');
    const isChecked = await worldcupSwitch.isChecked();

    if (isChecked) {
      await worldcupSwitch.click();
      await page.waitForTimeout(500); // deixa o fetch terminar
    }

    // Vai para Conteúdo & Automação e verifica que a sub-pill está cinza
    await page.locator('#tab-conteudo').click();
    const copaPill = page.locator('#subTabsConteudo .nav-item[data-feature="worldcup.enabled"]');
    await expect(copaPill).toHaveAttribute('data-feature-disabled', 'true');

    // Restaura o estado
    await page.locator('#tab-config').click();
    await page.locator('#sub-tab-features').click();
    await page.waitForSelector('#flag-worldcup\\.enabled');
    await page.locator('#flag-worldcup\\.enabled').click();
  });

  test('flag read-only aparece com badge "read-only"', async ({ page }) => {
    const migrationRow = page.locator('#featureFlagsContainer .feature-flag-row')
      .filter({ hasText: 'migration.enabled' });

    await expect(migrationRow).toBeVisible();
    await expect(migrationRow.locator('.badge')).toContainText('read-only');
    await expect(migrationRow.locator('input[type="checkbox"]')).toBeDisabled();
  });
});