// Clipping sweep: node cliptest.mjs — every console, from Home and from a page, every 20 ms until the game goes in;
// prints the deepest the cartridge gets inside the console's box, and when
import http from 'node:http'; import fs from 'node:fs'; import path from 'node:path'; import { createRequire } from 'node:module';
const here = path.dirname(new URL(import.meta.url).pathname);
const require = createRequire(import.meta.url); const { chromium } = require('playwright');
const types = { '.html': 'text/html', '.js': 'text/javascript', '.wasm': 'application/wasm', '.json': 'application/json' };
const server = http.createServer((req, res) => { const f = path.join(here, decodeURIComponent(req.url.split('?')[0])); if (!fs.existsSync(f) || fs.statSync(f).isDirectory()) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { 'Content-Type': types[path.extname(f)] ?? 'application/octet-stream' }); fs.createReadStream(f).pipe(res); }).listen(0);
const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
const list = (process.argv[2] ?? 'nes,snes,n64,genesis,gb,gbc,gba,ps1,ds,psp,android').split(',');
for (const sys of list) for (const from of ['home', 'page']) {
  const p = await b.newPage({ viewport: { width: 1334, height: 750 } });
  await p.goto(`http://127.0.0.1:${server.address().port}/rig.html?scene=launch&sys=${sys}&from=${from}`); await p.waitForFunction(() => window.done, null, { timeout: 300000 });
  const r = await p.evaluate(() => { const lead = location.search.includes('from=page') ? .55 : 0; let worst = 0, at = 0, bad = [], hid = [];
    for (let t = 0; t < lead + window.T.slide[0]; t += .02) { window.at(t); if (window.CLIP > worst) { worst = window.CLIP; at = t; } if (window.CLIP > 1) bad.push(+t.toFixed(2)); if (window.HIDDEN > 0) hid.push(+t.toFixed(2)); }
    return { worst: +worst.toFixed(1), at: +at.toFixed(2), frames: bad.length, from: bad[0], to: bad[bad.length - 1], hidden: hid.length ? [hid[0], hid[hid.length - 1], hid.length] : 0 }; });
  console.log(sys.padEnd(8), from.padEnd(5), JSON.stringify(r));
  await p.close();
}
await b.close(); server.close();
