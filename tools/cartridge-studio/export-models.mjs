// Exports every cartridge and console from the Studio as .glb files for the Android app (android/native/src/main/
// assets/models/), plus cartridges.json with each cartridge's sizes and color variants.
// Run from this folder: node export-models.mjs (add --consoles for the consoles too). Needs Node and Playwright
// (npm install playwright).
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const out = path.resolve(here, '../../android/native/src/main/assets/models');
const require = createRequire(process.env.PLAYWRIGHT_FROM || import.meta.url);
const { chromium } = require('playwright');

const types = { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.exr': 'application/octet-stream' };
const server = http.createServer((req, res) => {
  const f = path.join(here, decodeURIComponent(req.url.split('?')[0]));
  if (!f.startsWith(here) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { 'Content-Type': types[path.extname(f)] || 'application/octet-stream' }); fs.createReadStream(f).pipe(res);
}).listen(0);
const port = server.address().port;

const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const page = await browser.newPage(); page.on('pageerror', e => console.error(e));
await page.goto(`http://127.0.0.1:${port}/export.html`); await page.waitForFunction(() => window.ready);
const files = await page.evaluate(consoles => window.exportAll({ consoles }), process.argv.includes('--consoles'));
fs.mkdirSync(out, { recursive: true });
for (const [name, b64] of Object.entries(files)) { const buf = Buffer.from(b64, 'base64'); fs.writeFileSync(path.join(out, name), buf); console.log(name.padEnd(28), (buf.length / 1024).toFixed(0) + ' KB'); }
await browser.close(); server.close();
