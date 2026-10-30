// Frames of the start animation: node launch.mjs sys "extra=params" t0 t1 step outdir
// Renders rig.html?scene=launch&sys=… once, then poses it at each time and screenshots (tone mapped in the page).
import http from 'node:http'; import fs from 'node:fs'; import path from 'node:path'; import { createRequire } from 'node:module';
const here = path.dirname(new URL(import.meta.url).pathname);
const [sys, extra = '', t0 = '0', t1 = '2.5', step = '.1', out = 'frames'] = process.argv.slice(2);
const require = createRequire(import.meta.url); const { chromium } = require('playwright');
const types = { '.html': 'text/html', '.js': 'text/javascript', '.wasm': 'application/wasm', '.json': 'application/json' };
const server = http.createServer((req, res) => { const f = path.join(here, decodeURIComponent(req.url.split('?')[0])); if (!fs.existsSync(f) || fs.statSync(f).isDirectory()) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { 'Content-Type': types[path.extname(f)] ?? 'application/octet-stream' }); fs.createReadStream(f).pipe(res); }).listen(0);
const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
const p = await b.newPage({ viewport: { width: 1334, height: 750 } }); p.on('pageerror', e => console.error('ERR', e)); p.on('console', m => { if (m.type() === 'error') console.error(m.text()); });
await p.goto(`http://127.0.0.1:${server.address().port}/rig.html?scene=launch&tone=ACES_LEGACY&sys=${sys}&${extra}`); await p.waitForFunction(() => window.done, null, { timeout: 300000 });
fs.mkdirSync(out, { recursive: true });
await p.evaluate(async t => { window.at(t); for (let k = 0; k < 4; k++) { window.frame(); await new Promise(r => requestAnimationFrame(r)); } }, +t0); await p.screenshot();   // the first capture comes out stale: one thrown away
for (let t = +t0, i = 0; t <= +t1 + 1e-6; t += +step, i++) {
  await p.evaluate(async t => { window.at(t); for (let k = 0; k < 4; k++) { window.frame(); await new Promise(r => requestAnimationFrame(r)); } }, t);   // Filament skips frames sent too fast: a few, a display frame apart
  await p.screenshot({ path: path.join(out, `${String(i).padStart(3, '0')}.png`) });
}
await b.close(); server.close();
