const { chromium } = require('playwright');
const path = require('path');

(async () => {
  const browser = await chromium.launch({ args: ['--ignore-certificate-errors'] });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 2,
    ignoreHTTPSErrors: true,
  });
  const page = await context.newPage();
  for (const v of ['a', 'b', 'c']) {
    const file = 'file://' + path.join(__dirname, `variant-${v}.html`);
    await page.goto(file, { waitUntil: 'networkidle' });
    await page.evaluate(async () => {
      await document.fonts.load("24px 'Material Symbols Rounded'");
      await document.fonts.load("700 16px 'Hanken Grotesk'");
      await document.fonts.load("500 13px 'JetBrains Mono'");
      await document.fonts.ready;
    });
    await page.waitForTimeout(1200);
    const out = path.join(__dirname, `variant-${v}.png`);
    await page.screenshot({ path: out });
    console.log('wrote', out);
  }
  await browser.close();
})();
