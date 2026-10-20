/* ================= procedural art ================= */
function pal(g) {
  const r = rng(hash(g.id + 'pal')), h = Math.floor(r() * 360);
  return { h, r,
    sky1: `hsl(${h},55%,16%)`, sky2: `hsl(${(h + 40) % 360},70%,${54 + r() * 10}%)`,
    sun: `hsl(${(h + 180) % 360},90%,72%)`, far: `hsl(${(h + 15) % 360},40%,34%)`, mid: `hsl(${(h + 5) % 360},42%,24%)`,
    near: `hsl(${(h + 350) % 360},45%,12%)`, pop: `hsl(${(h + 200) % 360},85%,66%)` };
}
function sky(ctx, w, h, p) {
  const gr = ctx.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, p.sky1); gr.addColorStop(1, p.sky2);
  ctx.fillStyle = gr; ctx.fillRect(0, 0, w, h);
  const r = rng(hash(p.h + 'stars'));
  ctx.fillStyle = 'rgba(255,255,255,.8)';
  for (let i = 0; i < 70; i++) { const s = r() * 2.2 + .4; ctx.globalAlpha = r() * .8 + .2; ctx.fillRect(r() * w, r() * h * .6, s, s); }
  ctx.globalAlpha = 1;
}
function ridge(ctx, w, h, base, amp, color, r, sharp) {
  ctx.fillStyle = color; ctx.beginPath(); ctx.moveTo(0, h);
  const n = sharp ? 7 : 40;
  for (let i = 0; i <= n; i++) { const x = i / n * w, y = base - (sharp ? (i % 2 ? amp * (.6 + r() * .4) : amp * r() * .25) : (Math.sin(i * .5 + r() * .3) * .5 + .5) * amp); ctx.lineTo(x, y); }
  ctx.lineTo(w, h); ctx.fill();
}
function drawScene(ctx, w, h, g) {
  const p = pal(g), r = rng(hash(g.id + 'scene')), t = theme(g);
  sky(ctx, w, h, p);
  const sunR = Math.min(w, h) * (0.12 + r() * 0.08), sx = w * (0.25 + r() * 0.5), sy = h * (0.25 + r() * 0.15);
  if (t !== 'geo') {
    const sg = ctx.createRadialGradient(sx, sy, 0, sx, sy, sunR * 3); sg.addColorStop(0, 'rgba(255,255,255,.35)'); sg.addColorStop(1, 'rgba(255,255,255,0)');
    ctx.fillStyle = sg; ctx.fillRect(0, 0, w, h);
    ctx.fillStyle = p.sun; ctx.beginPath(); ctx.arc(sx, sy, sunR, 0, 7); ctx.fill();
  }
  if (t === 'peaks') { ridge(ctx, w, h, h * .62, h * .3, p.far, r, true); ridge(ctx, w, h, h * .78, h * .22, p.mid, r, true); ridge(ctx, w, h, h * .92, h * .12, p.near, r, false); }
  else if (t === 'planet') {
    ctx.fillStyle = p.pop; ctx.beginPath(); ctx.arc(w * .7, h * .62, w * .32, 0, 7); ctx.fill();
    ctx.fillStyle = 'rgba(0,0,0,.25)'; ctx.beginPath(); ctx.arc(w * .78, h * .66, w * .3, 0, 7); ctx.fill();
    ctx.strokeStyle = 'rgba(255,255,255,.7)'; ctx.lineWidth = w * .015; ctx.beginPath(); ctx.ellipse(w * .7, h * .62, w * .5, w * .1, -.3, 0, 7); ctx.stroke();
    ridge(ctx, w, h, h * .95, h * .1, p.near, r, false);
  } else if (t === 'city') {
    for (let layer = 0; layer < 2; layer++) {
      ctx.fillStyle = layer ? p.near : p.far;
      let x = -10;
      while (x < w) { const bw = w * (.05 + r() * .08), bh = h * (layer ? .25 + r() * .3 : .35 + r() * .3); ctx.fillRect(x, h - bh, bw, bh);
        if (layer) { ctx.fillStyle = p.sun; for (let k = 0; k < 6; k++) ctx.fillRect(x + r() * bw * .8, h - bh + r() * bh * .8, 3, 4); ctx.fillStyle = p.near; }
        x += bw + w * .01; }
    }
    ctx.fillStyle = p.pop; ctx.fillRect(0, h * .9, w, h * .01);
  } else if (t === 'forest') {
    ridge(ctx, w, h, h * .7, h * .12, p.far, r, false);
    for (let layer = 0; layer < 2; layer++) {
      ctx.fillStyle = layer ? p.near : p.mid;
      for (let i = 0; i < 9; i++) { const x = r() * w, y = h * (layer ? .82 : .72), s = h * (layer ? .14 : .1) * (.7 + r() * .6);
        ctx.beginPath(); ctx.moveTo(x, y - s * 2.2); ctx.lineTo(x + s * .8, y); ctx.lineTo(x - s * .8, y); ctx.fill(); }
    }
    ctx.fillStyle = p.near; ctx.fillRect(0, h * .85, w, h * .15);
  } else if (t === 'space') {
    const ng = ctx.createRadialGradient(w * .3, h * .5, 0, w * .3, h * .5, w * .6); ng.addColorStop(0, p.pop); ng.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.globalAlpha = .45; ctx.fillStyle = ng; ctx.fillRect(0, 0, w, h); ctx.globalAlpha = 1;
    ctx.fillStyle = '#f4f6ff'; const cx = w * .45, cy = h * .6, s = w * .16;
    ctx.beginPath(); ctx.moveTo(cx + s, cy); ctx.lineTo(cx - s * .7, cy - s * .5); ctx.lineTo(cx - s * .4, cy); ctx.lineTo(cx - s * .7, cy + s * .5); ctx.fill();
    ctx.fillStyle = p.sun; ctx.beginPath(); ctx.moveTo(cx - s * .45, cy - s * .15); ctx.lineTo(cx - s * 1.1, cy); ctx.lineTo(cx - s * .45, cy + s * .15); ctx.fill();
  } else if (t === 'sea') {
    for (let k = 0; k < 4; k++) {
      ctx.fillStyle = [p.far, p.mid, p.near, p.near][k]; ctx.globalAlpha = k === 3 ? .8 : 1; ctx.beginPath(); ctx.moveTo(0, h);
      const base = h * (.6 + k * .1);
      for (let x = 0; x <= w; x += w / 40) ctx.lineTo(x, base + Math.sin(x / w * 12 + k * 2 + r()) * h * .015);
      ctx.lineTo(w, h); ctx.fill();
    }
    ctx.globalAlpha = 1;
  } else {   // geo
    for (let i = 0; i < 9; i++) {
      ctx.save(); ctx.translate(r() * w, r() * h); ctx.rotate(r() * 3); ctx.fillStyle = [p.pop, p.sun, p.far][i % 3]; ctx.globalAlpha = .85;
      const s = w * (.08 + r() * .14); if (i % 2) ctx.fillRect(-s / 2, -s / 2, s, s); else { ctx.beginPath(); ctx.arc(0, 0, s / 2, 0, 7); ctx.fill(); }
      ctx.restore();
    }
    ctx.globalAlpha = 1;
  }
}
function wrap(ctx, text, maxW) {
  const words = text.split(' '), lines = []; let cur = '';
  for (const w of words) { const t = cur ? cur + ' ' + w : w; if (ctx.measureText(t).width > maxW && cur) { lines.push(cur); cur = w; } else cur = t; }
  if (cur) lines.push(cur); return lines;
}
function drawTitle(ctx, text, cx, y, maxW, size, align = 'center') {
  let lines;
  for (;;) { ctx.font = `600 ${size}px Unbounded, sans-serif`; lines = wrap(ctx, text, maxW); if (lines.length <= 2 && lines.every(l => ctx.measureText(l).width <= maxW) || size < 12) break; size *= .9; }
  ctx.textAlign = align; ctx.textBaseline = 'top';
  lines.forEach((l, i) => {
    ctx.lineWidth = size * .16; ctx.strokeStyle = 'rgba(10,8,30,.75)'; ctx.lineJoin = 'round'; ctx.strokeText(l, cx, y + i * size * 1.08);
    ctx.fillStyle = '#fff'; ctx.fillText(l, cx, y + i * size * 1.08);
  });
  return y + lines.length * size * 1.08;
}
function canvas(w, h) { const c = document.createElement('canvas'); c.width = Math.round(w); c.height = Math.round(h); return c; }

function labelArt(g, aspect) {        // aspect = width / height of the cartridge label
  const w = 512, h = Math.round(512 / aspect), c = canvas(w, h), ctx = c.getContext('2d');
  drawScene(ctx, w, h, g);
  const sh = ctx.createLinearGradient(0, h * .55, 0, h); sh.addColorStop(0, 'rgba(8,6,24,0)'); sh.addColorStop(1, 'rgba(8,6,24,.7)');
  ctx.fillStyle = sh; ctx.fillRect(0, 0, w, h);
  const size = Math.min(w, h) * 0.13, lines = (() => { ctx.font = `600 ${size}px Unbounded, sans-serif`; return wrap(ctx, g.title, w * .86).length; })();
  drawTitle(ctx, g.title, w / 2, h - size * (lines > 1 ? 2.6 : 1.6), w * .86, size);
  return c;
}
function coverFront(g, w, h) {
  const c = canvas(w, h), ctx = c.getContext('2d'), s = SYS[g.sys];
  drawScene(ctx, w, h, g);
  const band = Math.round(h * 0.075);
  ctx.fillStyle = 'rgba(8,8,20,.85)'; ctx.fillRect(0, 0, w, band);
  ctx.fillStyle = s.accent; ctx.fillRect(0, band - 4, w, 4);
  ctx.fillStyle = '#fff'; ctx.font = `700 ${band * .45}px Manrope, sans-serif`; ctx.textAlign = 'left'; ctx.textBaseline = 'middle';
  ctx.fillText(s.long, band * .45, band / 2);
  drawTitle(ctx, g.title, w / 2, band + h * .05, w * .84, Math.min(w, h) * .11);
  ctx.fillStyle = 'rgba(255,255,255,.85)'; ctx.font = `600 ${Math.min(w, h) * .035}px Manrope, sans-serif`; ctx.textAlign = 'right'; ctx.textBaseline = 'alphabetic';
  ctx.fillText(g.dev, w - w * .05, h - h * .04);
  return c;
}
function coverBack(g, w, h) {
  const c = canvas(w, h), ctx = c.getContext('2d'), p = pal(g), s = SYS[g.sys];
  ctx.fillStyle = `hsl(${p.h},35%,12%)`; ctx.fillRect(0, 0, w, h);
  const m = w * .07; let y = m;
  ctx.fillStyle = '#fff'; ctx.textAlign = 'left'; ctx.textBaseline = 'top';
  ctx.font = `600 ${w * .055}px Unbounded, sans-serif`; ctx.fillText(g.title, m, y); y += w * .1;
  const sw = (w - m * 2 - m) / 2, shh = sw * s.res[1] / s.res[0];
  for (let i = 0; i < 2; i++) {
    const shot = gameFrame(g, hash(g.id + 'back' + i), i * 37);
    ctx.imageSmoothingEnabled = false; ctx.drawImage(shot, m + i * (sw + m), y, sw, shh);
  }
  y += shh + m * .8;
  ctx.font = `500 ${w * .036}px Manrope, sans-serif`; ctx.fillStyle = 'rgba(255,255,255,.85)';
  wrap(ctx, g.desc, w - m * 2).forEach(l => { ctx.fillText(l, m, y); y += w * .052; });
  y += m * .4; ctx.fillStyle = s.accent; ctx.font = `700 ${w * .034}px Manrope, sans-serif`; ctx.fillText(`${g.genre}, ${g.players}`, m, y);
  const by = h - m - h * .08;
  ctx.fillStyle = '#fff'; ctx.fillRect(w - m - w * .3, by, w * .3, h * .08);
  ctx.fillStyle = '#111'; const br = rng(hash(g.id + 'bar'));
  for (let x = w - m - w * .29; x < w - m - w * .01; x += 3) if (br() > .45) ctx.fillRect(x, by + h * .01, br() > .5 ? 2 : 1, h * .06);
  ctx.fillStyle = 'rgba(255,255,255,.6)'; ctx.font = `500 ${w * .03}px Manrope, sans-serif`; ctx.fillText(`${g.dev}, ${g.year}`, m, by + h * .03);
  return c;
}
function spineArt(g, len, thick) {   // drawn upright: len tall, thick wide
  const c = canvas(thick, len), ctx = c.getContext('2d'), p = pal(g), s = SYS[g.sys];
  ctx.fillStyle = `hsl(${p.h},40%,14%)`; ctx.fillRect(0, 0, thick, len);
  ctx.fillStyle = s.accent; ctx.fillRect(0, 0, thick, len * .1);
  ctx.save(); ctx.translate(thick / 2, len * .14); ctx.rotate(Math.PI / 2);
  ctx.fillStyle = '#fff'; ctx.font = `600 ${thick * .38}px Unbounded, sans-serif`; ctx.textAlign = 'left'; ctx.textBaseline = 'middle';
  let size = thick * .38; while (ctx.measureText(g.title).width > len * .78 && size > 8) { size *= .92; ctx.font = `600 ${size}px Unbounded, sans-serif`; }
  ctx.fillText(g.title, 0, 0); ctx.restore();
  return c;
}

/* A pretend game screen: a small pixel-art level that scrolls. Used for save thumbnails,
   the back of the box and the "playing" screen. Game Boy games get the four greens. */
const DMG = ['#e0f8d0', '#88c070', '#346856', '#081820'];
function gameFrame(g, seed, scroll, out) {
  const s = SYS[g.sys], [w, h] = s.res, c = out || canvas(w, h), p = pal(g), r = rng(seed);
  if (c.width !== w || c.height !== h) { c.width = w; c.height = h; }
  const ctx = c.getContext('2d');
  const green = g.sys === 'gb';
  const col = (css, k) => green ? DMG[k] : css;
  ctx.fillStyle = col(`hsl(${p.h},60%,${30 + r() * 30}%)`, 0); ctx.fillRect(0, 0, w, h);
  ctx.fillStyle = col(`hsl(${(p.h + 30) % 360},55%,70%)`, 0);
  for (let i = 0; i < 5; i++) { const cx = ((i * 97 + seed % 50) - scroll * .2) % (w + 60); ctx.fillRect((cx + w + 60) % (w + 60) - 30, 14 + (i * 23) % 40, 26, 8); }
  ctx.fillStyle = col(`hsl(${(p.h + 10) % 360},40%,38%)`, 1);
  for (let x = 0; x < w; x += 4) { const wx = x + scroll * .5; const hh = 30 + Math.sin(wx * .03 + seed) * 12 + Math.sin(wx * .011) * 10; ctx.fillRect(x, h - 40 - hh, 4, hh + 40); }
  const tile = 16, gy = h - 32;
  for (let x = -(scroll % tile); x < w; x += tile) {
    ctx.fillStyle = col(`hsl(${(p.h + 200) % 360},45%,35%)`, 2); ctx.fillRect(x, gy, tile, 32);
    ctx.fillStyle = col(`hsl(${(p.h + 200) % 360},50%,50%)`, 1); ctx.fillRect(x, gy, tile - 1, 4);
    ctx.fillStyle = col('rgba(0,0,0,.25)', 3); ctx.fillRect(x + tile - 1, gy, 1, 32); ctx.fillRect(x, gy + 15, tile, 1);
  }
  const pr = rng(seed + 7);
  for (let i = 0; i < 6; i++) {
    const px = ((i * 120 + pr() * 60) - scroll) % (w + 200); const X = (px + w + 200) % (w + 200) - 100, Y = gy - 30 - (i % 3) * 24;
    ctx.fillStyle = col(`hsl(${(p.h + 200) % 360},45%,40%)`, 2); ctx.fillRect(X, Y, 48, 8);
    ctx.fillStyle = col('#ffd84a', 1); ctx.fillRect(X + 18, Y - 14, 6, 8);
  }
  const bob = Math.abs(Math.sin(scroll * .08)) * 10, px0 = Math.round(w * .3), py0 = gy - 16 - bob;
  ctx.fillStyle = col('#ffffff', 0); ctx.fillRect(px0, py0, 10, 16);
  ctx.fillStyle = col(`hsl(${(p.h + 180) % 360},80%,55%)`, 3); ctx.fillRect(px0, py0 + 6, 10, 10); ctx.fillRect(px0 + 6, py0 + 2, 2, 2);
  return c;
}

/* achievement badges: a small glyph on a colored tile; locked ones are gray with a lock */
const badgeCache = new Map();
function badge(g, a, locked) {
  const key = a.id + (locked ? 'L' : ''); if (badgeCache.has(key)) return badgeCache.get(key);
  const c = canvas(96, 96), x = c.getContext('2d'), p = pal(g), r = rng(hash(a.id));
  const h = (p.h + Math.floor(r() * 120) - 60 + 360) % 360;
  const gr = x.createLinearGradient(0, 0, 96, 96);
  gr.addColorStop(0, locked ? '#3a3f58' : `hsl(${h},70%,62%)`); gr.addColorStop(1, locked ? '#1d2138' : `hsl(${(h + 40) % 360},65%,32%)`);
  x.fillStyle = gr; x.beginPath(); x.roundRect(0, 0, 96, 96, 20); x.fill();
  x.fillStyle = locked ? 'rgba(238,241,255,.25)' : '#fff'; x.strokeStyle = x.fillStyle; x.lineWidth = 7; x.lineJoin = 'round'; x.lineCap = 'round';
  x.save(); x.translate(48, 46);
  const star = (n, R, r2) => { x.beginPath(); for (let i = 0; i < n * 2; i++) { const a2 = i / (n * 2) * Math.PI * 2 - Math.PI / 2, rad = i % 2 ? r2 : R; x.lineTo(Math.cos(a2) * rad, Math.sin(a2) * rad); } x.closePath(); x.fill(); };
  switch (a.icon) {
    case 'star': star(5, 28, 12); break;
    case 'diamond': x.beginPath(); x.moveTo(0, -28); x.lineTo(22, 0); x.lineTo(0, 28); x.lineTo(-22, 0); x.closePath(); x.fill(); break;
    case 'ring': x.beginPath(); x.arc(0, 0, 20, 0, 7); x.stroke(); break;
    case 'bolt': x.beginPath(); x.moveTo(6, -28); x.lineTo(-14, 4); x.lineTo(0, 4); x.lineTo(-6, 28); x.lineTo(14, -4); x.lineTo(0, -4); x.closePath(); x.fill(); break;
    case 'crown': x.beginPath(); x.moveTo(-24, 16); x.lineTo(-24, -12); x.lineTo(-10, 2); x.lineTo(0, -20); x.lineTo(10, 2); x.lineTo(24, -12); x.lineTo(24, 16); x.closePath(); x.fill(); break;
    case 'flag': x.fillRect(-18, -26, 5, 54); x.beginPath(); x.moveTo(-13, -26); x.lineTo(22, -15); x.lineTo(-13, -2); x.closePath(); x.fill(); break;
    case 'heart': x.beginPath(); x.moveTo(0, 22); x.bezierCurveTo(-34, 0, -18, -30, 0, -12); x.bezierCurveTo(18, -30, 34, 0, 0, 22); x.fill(); break;
    default: x.beginPath(); x.arc(0, 0, 24, 0, 7); x.fill(); x.fillStyle = gr; x.beginPath(); x.arc(10, -8, 20, 0, 7); x.fill();
  }
  x.restore();
  if (locked) { x.fillStyle = 'rgba(7,11,30,.85)'; x.beginPath(); x.roundRect(58, 58, 32, 32, 9); x.fill(); x.strokeStyle = '#9aa2cc'; x.lineWidth = 3;
    x.beginPath(); x.arc(74, 70, 6, Math.PI, 0); x.stroke(); x.fillStyle = '#9aa2cc'; x.fillRect(66, 70, 16, 12); }
  const url = c.toDataURL(); badgeCache.set(key, url); return url;
}
// a soft rounded panel used as a GL texture: a fill, a bright border and a glow, all in the alpha channel
function plateArt() {
  const c = canvas(320, 384), x = c.getContext('2d'); x.clearRect(0, 0, 320, 384);
  x.shadowColor = 'rgba(255,255,255,.9)'; x.shadowBlur = 26;
  x.strokeStyle = 'rgba(255,255,255,1)'; x.lineWidth = 6; x.beginPath(); x.roundRect(30, 30, 260, 324, 34); x.stroke();
  x.shadowBlur = 0; x.fillStyle = 'rgba(255,255,255,.16)'; x.beginPath(); x.roundRect(33, 33, 254, 318, 31); x.fill();
  return c;
}
function tileArt() {
  const c = canvas(256, 256), x = c.getContext('2d'); x.clearRect(0, 0, 256, 256);
  x.fillStyle = 'rgba(255,255,255,.1)'; x.beginPath(); x.roundRect(8, 8, 240, 240, 36); x.fill();
  x.setLineDash([16, 12]); x.strokeStyle = 'rgba(255,255,255,.55)'; x.lineWidth = 4; x.beginPath(); x.roundRect(8, 8, 240, 240, 36); x.stroke();
  return c;
}

function panelArt() { const c = canvas(677, 369), x = c.getContext('2d'); x.clearRect(0, 0, 677, 369); x.fillStyle = '#fff'; x.beginPath(); x.roundRect(0, 0, 677, 369, 14); x.fill(); return c; }

function shadowArt() { const c = canvas(256, 96), x = c.getContext('2d'), g = x.createRadialGradient(128, 128, 0, 128, 128, 128);
  g.addColorStop(0, 'rgba(255,255,255,1)'); g.addColorStop(.5, 'rgba(255,255,255,.5)'); g.addColorStop(1, 'rgba(255,255,255,0)');
  x.setTransform(1, 0, 0, 96 / 256, 0, 0); x.fillStyle = g; x.fillRect(0, 0, 256, 256); return c; }

/* ================= media: title screens, thumbnails and manual pages (all generated here; scraped on the Pi) ================= */
function titleFrame(g, out) {   // a pretend title screen at the console's resolution
  const s = SYS[g.sys], [w, h] = s.res, c = out || canvas(w, h); if (c.width !== w) { c.width = w; c.height = h; }
  const x = c.getContext('2d'), p = pal(g), green = g.sys === 'gb', col = (css, k) => green ? DMG[k] : css;
  x.fillStyle = col(`hsl(${p.h},55%,14%)`, 3); x.fillRect(0, 0, w, h);
  const r = rng(hash(g.id + 'title')); x.fillStyle = col('#ffffff', 0);
  for (let i = 0; i < 40; i++) x.fillRect(Math.floor(r() * w), Math.floor(r() * h * .7), 1, 1);
  x.fillStyle = col(`hsl(${(p.h + 20) % 360},45%,30%)`, 2); for (let i = 0; i < w; i += 4) { const hh = 18 + Math.sin(i * .05 + p.h) * 8; x.fillRect(i, h - hh, 4, hh); }
  // the title, drawn small and scaled up so it looks like pixel art
  const t = canvas(w, 40), tx = t.getContext('2d'); tx.font = `bold ${Math.round(w / 14)}px Manrope, sans-serif`; tx.textAlign = 'center'; tx.textBaseline = 'middle';
  tx.fillStyle = col(`hsl(${(p.h + 180) % 360},90%,70%)`, 0); tx.fillText(g.title.toUpperCase(), w / 2, 20);
  x.imageSmoothingEnabled = false; x.drawImage(t, 0, 0, w, 40, 0, h * .22, w, 40 * 1.6);
  x.font = `bold ${Math.round(w / 26)}px Manrope, sans-serif`; x.textAlign = 'center'; x.fillStyle = col('#ffffff', 0); x.fillText('PRESS START', w / 2, h * .72);
  x.font = `${Math.round(w / 34)}px Manrope, sans-serif`; x.fillStyle = col('rgba(255,255,255,.7)', 1); x.fillText(`© ${g.year} ${g.dev.toUpperCase()}`, w / 2, h * .86);
  return c;
}
const mediaCache = new Map();
function mediaCanvas(g, m) {   // the full-size picture for one media item
  const key = g.id + m.kind + (m.seed || ''); if (mediaCache.has(key)) return mediaCache.get(key);
  const [bw, bh] = BOX[g.sys], k = 900 / Math.max(bw, bh);
  const c = m.kind === 'title' ? titleFrame(g) : m.kind === 'box' ? coverFront(g, bw * k, bh * k) : m.kind === 'back' ? coverBack(g, bw * k, bh * k) : gameFrame(g, m.seed, m.scroll);
  mediaCache.set(key, c); return c;
}
function mediaThumb(g, m) {   // a small picture for the strips, cached as an image
  const key = 'thumb' + g.id + m.kind + (m.seed || ''); if (mediaCache.has(key)) return mediaCache.get(key);
  const src = mediaCanvas(g, m), t = canvas(240, 176), x = t.getContext('2d'), pixel = m.kind !== 'box' && m.kind !== 'back';
  x.fillStyle = '#0a0d22'; x.fillRect(0, 0, 240, 176); x.imageSmoothingEnabled = !pixel;
  const sc = pixel ? Math.max(240 / src.width, 176 / src.height) : Math.min(240 / src.width, 176 / src.height);   // screens fill, boxes fit
  x.drawImage(src, (240 - src.width * sc) / 2, (176 - src.height * sc) / 2, src.width * sc, src.height * sc);
  const url = t.toDataURL(); mediaCache.set(key, url); return url;
}

const SENTENCES = [
  'Press Start at the title screen to begin. Choose Continue to pick up from your last save.',
  'Every world is split into short stages. Reach the lantern at the end of each one to light the way forward.',
  'Hidden paths are marked by cracked stone and loose bricks. Try every wall.',
  'Your health is shown at the top of the screen. Collect glowing seeds to restore it.',
  'Some doors only open after you find the matching key somewhere else in the world.',
  'When you are stuck, return to the village. The people there always have something new to say.',
  'Treasure chests can hold coins, keys or rare items. Some only appear after a boss is defeated.',
  'Hold the button longer to jump higher. Tap it for a short hop over small gaps.',
  'The map screen shows every area you have visited. Areas with secrets left to find are marked with a star.',
  'Bosses have patterns. Watch carefully, wait for an opening, and strike when they pause.',
  'Two players can take turns. The second player starts after the first player loses a life.',
  'Rare items are often guarded. Look for them high up, far down, or behind a waterfall.',
  'Your progress is saved at each lantern you light, so take your time and explore.',
  'Coins can be spent at the traveling shop, which appears in a different place in every world.',
  'If you lose all your lives, you will return to the start of the current world with your items intact.',
  'Listen closely. Many secrets are announced by a short chime when you are near them.',
];
const SECTIONS = ['Getting started', 'Controls', 'The story', 'On screen', 'Items', 'Tips', 'The story', 'On screen', 'Items', 'Tips', 'Worlds', 'Getting started'];
function manualPage(g, n, total) {
  const key = 'man' + g.id + ':' + n; if (mediaCache.has(key)) return mediaCache.get(key);
  const W0 = 600, H0 = 840, c = canvas(W0, H0), x = c.getContext('2d'), p = pal(g), r = rng(hash(g.id + 'man' + n)), m = 52;
  if (n === 0) { const [bw, bh] = BOX[g.sys], f = coverFront(g, 600, 840 * Math.min(1, bh / bw)); x.fillStyle = `hsl(${p.h},35%,14%)`; x.fillRect(0, 0, W0, H0); x.drawImage(f, 0, (H0 - f.height) / 2); mediaCache.set(key, c); return c; }
  x.fillStyle = '#f3eee3'; x.fillRect(0, 0, W0, H0);
  const kind = n === 1 ? 'Contents' : n === total - 1 ? 'Notes' : SECTIONS[(n - 2) % SECTIONS.length];
  x.fillStyle = `hsl(${p.h},45%,32%)`; x.fillRect(0, 0, W0, 76);
  x.fillStyle = '#fff'; x.font = '600 30px Unbounded, sans-serif'; x.textBaseline = 'middle'; x.fillText(kind, m, 40);
  x.fillStyle = '#8a8478'; x.font = '500 18px Manrope, sans-serif'; x.textAlign = 'center'; x.fillText(String(n), W0 / 2, H0 - 30); x.textAlign = 'left';
  let y = 120; x.textBaseline = 'top';
  const para = (t, size = 21, color = '#3b3833') => { x.font = `500 ${size}px Manrope, sans-serif`; x.fillStyle = color; wrap(x, t, W0 - m * 2).forEach(l => { x.fillText(l, m, y); y += size * 1.5; }); y += 16; };
  const sentences = k => Array.from({ length: k }, () => SENTENCES[Math.floor(r() * SENTENCES.length)]).join(' ');
  if (kind === 'Contents') {
    x.font = '500 22px Manrope, sans-serif';
    const first = [];   // each section's real first page
    for (let q = 2; q < total - 1; q++) { const t = SECTIONS[(q - 2) % SECTIONS.length]; if (!first.some(f => f[0] === t)) first.push([t, q]); }
    first.push(['Notes', total - 1]);
    first.forEach(([t, pgn]) => {
      x.fillStyle = '#3b3833'; x.fillText(t, m, y); const pg = String(pgn); x.textAlign = 'right'; x.fillText(pg, W0 - m, y); x.textAlign = 'left';
      x.fillStyle = '#b7b0a2'; for (let d = m + x.measureText(t).width + 12; d < W0 - m - 40; d += 10) x.fillRect(d, y + 16, 3, 3); y += 52; });
  } else if (kind === 'Controls') {
    para(`${g.title} is played with the standard controller.`);
    const cx = W0 / 2, cy = 330; x.fillStyle = '#d9d2c3'; x.beginPath(); x.roundRect(cx - 200, cy - 80, 400, 160, 70); x.fill();
    x.fillStyle = '#3b3833'; x.fillRect(cx - 150, cy - 12, 70, 24); x.fillRect(cx - 127, cy - 35, 24, 70);
    x.fillStyle = `hsl(${p.h},55%,45%)`; [[110, -18], [150, 12]].forEach(([dx, dy]) => { x.beginPath(); x.arc(cx + dx, cy + dy, 18, 0, 7); x.fill(); });
    x.fillStyle = '#6d675c'; x.fillRect(cx - 40, cy + 20, 30, 10); x.fillRect(cx + 5, cy + 20, 30, 10);
    y = 460; [['Control pad', 'Move, and choose menu items'], ['A button', 'Jump. Hold to jump higher'], ['B button', 'Use the item you are holding'], ['Start', 'Pause and open the map'], ['Select', 'Change the item you are holding']].forEach(([a, b]) => {
      x.font = '700 21px Manrope, sans-serif'; x.fillStyle = '#3b3833'; x.fillText(a, m, y); x.font = '500 21px Manrope, sans-serif'; x.fillText(b, m + 170, y); y += 44; });
  } else if (kind === 'The story') {
    const ill = canvas(W0 - m * 2, 240); drawScene(ill.getContext('2d'), ill.width, ill.height, g); x.drawImage(ill, m, y); y += 266;
    para(g.desc, 22); para(sentences(2));
  } else if (kind === 'On screen') {
    [0, 1].forEach(i => { const f = gameFrame(g, hash(g.id + 'manshot' + n + i), 40 + i * 90), sw = 230, sh = sw * f.height / f.width;
      x.imageSmoothingEnabled = false; x.drawImage(f, m + i * (sw + 36), y, sw, sh); x.imageSmoothingEnabled = true;
      x.font = '500 17px Manrope, sans-serif'; x.fillStyle = '#6d675c'; x.fillText(['Health and coins', 'The map screen'][i], m + i * (sw + 36), y + sh + 10); });
    y += 260; para(sentences(3));
  } else if (kind === 'Items') {
    const names = ['Lantern', 'Seed', 'Key', 'Map', 'Boots', 'Charm', 'Shell', 'Gear', 'Crown'];
    names.forEach((t, i) => { const col = i % 3, row = Math.floor(i / 3), ix = m + col * 170, iy = y + row * 150;
      x.fillStyle = `hsl(${(p.h + i * 40) % 360},55%,55%)`; x.beginPath(); x.roundRect(ix + 30, iy, 72, 72, 18); x.fill();
      x.font = '600 19px Manrope, sans-serif'; x.fillStyle = '#3b3833'; x.textAlign = 'center'; x.fillText(t, ix + 66, iy + 84); x.textAlign = 'left'; });
    y += 470; para(sentences(1));
  } else if (kind === 'Notes') {
    x.strokeStyle = '#cfc7b6'; x.lineWidth = 1.5; for (let ly = y + 30; ly < H0 - 80; ly += 44) { x.beginPath(); x.moveTo(m, ly); x.lineTo(W0 - m, ly); x.stroke(); }
  } else { para(sentences(3)); para(sentences(3)); if (r() > .4) para(sentences(2)); }
  mediaCache.set(key, c); return c;
}
