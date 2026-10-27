// Renders rig.html in headless Chromium (software WebGL 2): node shot.mjs "rig.html?scene=carts" out.png
// Transparent around the models: tonemap.py adds the tone curve and the backdrop.
import http from 'node:http'; import fs from 'node:fs'; import path from 'node:path'; import { createRequire } from 'node:module';
const here = path.dirname(new URL(import.meta.url).pathname), page0 = process.argv[2], out = process.argv[3];
const require = createRequire(import.meta.url); const { chromium } = require('playwright');
const types = { '.html': 'text/html', '.js': 'text/javascript', '.wasm': 'application/wasm', '.json': 'application/json' };
const server = http.createServer((req, res) => { const f = path.join(here, decodeURIComponent(req.url.split('?')[0])); if (!fs.existsSync(f) || fs.statSync(f).isDirectory()) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { 'Content-Type': types[path.extname(f)] ?? 'application/octet-stream' }); fs.createReadStream(f).pipe(res); }).listen(0);
const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
const p = await b.newPage({ viewport: { width: 1334, height: 750 } }); p.on('pageerror', e => console.error('ERR', e));
await p.goto(`http://127.0.0.1:${server.address().port}/${page0}`); await p.waitForFunction(() => window.done, null, { timeout: 300000 });
await p.screenshot({ path: out, omitBackground: true }); await b.close(); server.close();
