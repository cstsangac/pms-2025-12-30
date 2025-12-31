import { test, expect } from '@playwright/test';

test.describe('Portfolio Dashboard', () => {
  test('should load the dashboard', async ({ page }) => {
    await page.goto('/');
    
    await expect(page.locator('h1')).toContainText('Portfolio Management System');
  });

  test('should display portfolio summary cards', async ({ page }) => {
    await page.goto('/');
    
    await expect(page.locator('.card').first()).toBeVisible();
    await expect(page.getByText('Total Portfolio Value')).toBeVisible();
    await expect(page.getByText('Total Cash Balance')).toBeVisible();
    await expect(page.getByText('Total Holdings')).toBeVisible();
  });

  test('should display portfolios table when data is available', async ({ page }) => {
    await page.goto('/');
    
    // Wait for potential loading state
    await page.waitForTimeout(1000);
    
    const table = page.locator('.table').first();
    if (await table.isVisible()) {
      await expect(table.locator('th')).toContainText(['Client Name', 'Account Number', 'Total Value']);
    }
  });
});
