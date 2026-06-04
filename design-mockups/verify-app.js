const { chromium } = require('playwright');
const path = require('path');

const INDEX = 'http://127.0.0.1:8077/index.html';

(async () => {
  const browser = await chromium.launch({ args: ['--ignore-certificate-errors'] });
  for (const theme of ['c', 'b']) {
    const context = await browser.newContext({
      viewport: { width: 1440, height: 900 },
      deviceScaleFactor: 2,
      ignoreHTTPSErrors: true,
    });
    await context.addInitScript((t) => {
      try { localStorage.setItem('console-theme', t); } catch (e) {}
    }, theme);
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', e => errors.push(String(e)));
    await page.goto(INDEX, { waitUntil: 'networkidle' });
    // Wait for React + Babel to mount the app.
    await page.waitForSelector('.appbar', { timeout: 15000 });
    await page.evaluate(async () => {
      await document.fonts.load("24px 'Material Symbols Rounded'");
      await document.fonts.ready;
    });
    // Trigger the structured-output analyze → backend fetch fails → error console renders.
    const btn = page.locator('.panel .btn.filled').first();
    await btn.click();
    await page.waitForSelector('.console', { timeout: 8000 }).catch(() => {});
    await page.waitForTimeout(800);
    const out = path.join(__dirname, `check-${theme}.png`);
    await page.screenshot({ path: out });
    console.log('wrote', out, '| theme=' + theme, '| pageerrors:', errors.length ? errors : 'none',
      '| data-theme=', await page.evaluate(() => document.documentElement.dataset.theme));
    await context.close();
  }
  await browser.close();
})();
