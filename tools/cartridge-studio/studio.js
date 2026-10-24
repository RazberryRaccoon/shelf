// The cartridge studio: a turntable view of one cartridge or console, with sliders for the bevels, the plastic and the
// lighting, and export to .glb.
import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { RGBELoader } from 'three/addons/loaders/RGBELoader.js';
import { EXRLoader } from 'three/addons/loaders/EXRLoader.js';
import { GLTFExporter } from 'three/addons/exporters/GLTFExporter.js';
import { STYLES } from './cartridges.js';
import { buildCartridge, sampleLabel, applyVariant } from './cartridge.js';
import { CONSOLES } from './consoles.js';
import { buildConsole, setLid } from './console.js';

const $ = s => document.querySelector(s);
const view = $('#view');

// ---------- renderer: ACES filmic, sRGB output, antialiased ----------
const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, preserveDrawingBuffer: true });
renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.outputColorSpace = THREE.SRGBColorSpace;
view.appendChild(renderer.domElement);

const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(30, 1, 1, 5000);
const controls = new OrbitControls(camera, renderer.domElement);
controls.enableDamping = true; controls.autoRotateSpeed = 1.2;

// one soft key light for a controlled highlight, on top of the environment
const key = new THREE.DirectionalLight(0xffffff, 1);
scene.add(key);

// ---------- environment: a studio HDR, through PMREM ----------
const pmrem = new THREE.PMREMGenerator(renderer);
const ENV_ONLINE = 'https://dl.polyhaven.org/file/ph-assets/HDRIs/hdr/1k/studio_small_09_1k.hdr';   // Poly Haven, CC0
const ENV_BUNDLED = './vendor/studio.exr';   // a Poly Haven studio, CC0, 512 px (from pmndrs/assets), for offline use
function useEnvironment(texture, name) {
  const env = pmrem.fromEquirectangular(texture).texture; texture.dispose();
  if (scene.environment) scene.environment.dispose();
  scene.environment = env; $('#envName').textContent = name;
}
function loadEnvironment() {
  new RGBELoader().load(ENV_ONLINE, t => useEnvironment(t, 'Poly Haven studio (online)'), undefined,
    () => new EXRLoader().load(ENV_BUNDLED, t => useEnvironment(t, 'Poly Haven studio (bundled)')));
}

// ---------- a soft contact shadow under the cartridge ----------
const shadowTex = (() => {
  const c = document.createElement('canvas'); c.width = c.height = 256; const x = c.getContext('2d');
  const g = x.createRadialGradient(128, 128, 0, 128, 128, 128); g.addColorStop(0, 'rgba(0,0,0,.55)'); g.addColorStop(.55, 'rgba(0,0,0,.18)'); g.addColorStop(1, 'rgba(0,0,0,0)');
  x.fillStyle = g; x.fillRect(0, 0, 256, 256); return new THREE.CanvasTexture(c);
})();
const shadow = new THREE.Mesh(new THREE.PlaneGeometry(1, 1), new THREE.MeshBasicMaterial({ map: shadowTex, transparent: true, depthWrite: false }));
shadow.rotation.x = -Math.PI / 2; scene.add(shadow);

// ---------- state, saved per item in this browser ----------
// Items are cartridge styles ('gba') and consoles ('console:gba').
const DEFAULTS = { bevel: .8, round: 1, segments: 6, envIntensity: .85, exposure: .72, keyLight: 1, turntable: true };   // a studio HDR is bright: these keep plastic colors true
let itemId = 'gba', tune = {}, model = null, labelTexture = null, customLabel = null, framed = null;
const isConsole = id => id.startsWith('console:');
const entryFor = id => isConsole(id) ? CONSOLES[id.slice(8)] : STYLES[id];
// Only the values you changed are kept, so new defaults in the data files still reach you.
const STORE = 'cartridge-studio-2';
const saved = () => { try { return JSON.parse(localStorage.getItem(STORE) || '{}'); } catch (e) { return {}; } };
function remember() {
  try {
    const s = saved(), base = defaultsFor(itemId), mine = {};
    for (const k in tune) if (tune[k] !== base[k]) mine[k] = tune[k];
    if (Object.keys(mine).length) s[itemId] = mine; else delete s[itemId];
    s.last = itemId; localStorage.setItem(STORE, JSON.stringify(s));
  } catch (e) { }
}
const CONSOLE_DEFAULTS = { exposure: .65, keyLight: .6 };   // consoles show broad top faces to the light
function defaultsFor(id) {
  const e = entryFor(id);
  return Object.assign({}, DEFAULTS, isConsole(id) ? CONSOLE_DEFAULTS : { variant: e.variants[0].name }, { lid: e.lid ? e.lid.rest : 0 }, e.finish);
}
function tuningFor(id) { return Object.assign(defaultsFor(id), saved()[id] || {}); }
const variantOf = () => { const vs = entryFor(itemId).variants; return vs.find(v => v.name === tune.variant) || vs[0]; };

function rebuild() {
  const entry = entryFor(itemId), kind = isConsole(itemId);
  if (model) { scene.remove(model); model.traverse(o => { if (o.geometry) o.geometry.dispose(); }); }
  if (kind) model = buildConsole(entry, { round: tune.round, segments: tune.segments, finish: tune, lid: tune.lid });
  else {
    if (!customLabel) { if (labelTexture) labelTexture.dispose(); labelTexture = sampleLabel(entry); }
    model = buildCartridge(entry, { bevel: tune.bevel, segments: tune.segments, finish: tune, labelTexture: customLabel || labelTexture });
  }
  // stand it on the floor: a cartridge upright on its bottom edge, a console on its base, facing the camera
  const box = new THREE.Box3().setFromObject(model), size = box.getSize(new THREE.Vector3()), mid = box.getCenter(new THREE.Vector3());
  model.position.set(-mid.x, -box.min.y, -mid.z);
  scene.add(model);
  shadow.scale.set(size.x * 1.25, kind ? size.z * 1.25 : size.z * 3.2 + 6, 1); shadow.position.y = .02;
  const r = Math.max(size.x, size.y, kind ? size.z : 0) * 1.1;
  if (framed !== itemId) {   // frame a newly chosen item; keep the view while tuning
    controls.target.set(0, size.y / 2, 0);
    if (kind) camera.position.set(r * .75, size.y + r * .9, r * 1.9); else camera.position.set(r * .9, size.y * .75 + r * .25, r * 2.6);
    framed = itemId;
  }
  camera.near = r / 20; camera.far = r * 40; camera.updateProjectionMatrix();
  key.position.set(-r * 2, r * 3, r * 2.5);
  syncRows(); applyLook();
}
function applyLook() {   // things that don't need new geometry
  const m = model.userData.materials;
  if (isConsole(itemId)) {
    for (const p of m.plastics) { p.roughness = tune.roughness; p.clearcoat = tune.clearcoat; p.clearcoatRoughness = tune.clearcoatRoughness; }
    m.shell.color.set(tune.color); setLid(model, tune.lid);
  } else applyVariant(model, variantOf(), tune);
  model.traverse(o => { if (o.isMesh) o.material.envMapIntensity = tune.envIntensity; });
  renderer.toneMappingExposure = tune.exposure; key.intensity = tune.keyLight; controls.autoRotate = tune.turntable;
}

// ---------- controls ----------
// [key, label, min, max, step, unit, needs new geometry, shown for: 'cart' | 'console' | 'lid' (consoles with a hinged part)]
const SLIDERS = [
  ['bevel', 'Bevel size', 0, 2.5, .05, 'mm', true, 'cart'], ['round', 'Edge rounding', 0, 2.5, .05, '×', true, 'console'],
  ['lid', 'Lid', 0, 1, .01, '', false, 'lid'], ['segments', 'Bevel segments', 1, 10, 1, '', true],
  ['roughness', 'Roughness', 0, 1, .01], ['clearcoat', 'Clearcoat', 0, 1, .01], ['clearcoatRoughness', 'Clearcoat roughness', 0, 1, .01],
  ['envIntensity', 'Environment intensity', 0, 3, .05], ['exposure', 'Exposure', .3, 2.5, .05], ['keyLight', 'Key light', 0, 4, .05],
];
function buildPanel() {
  $('#style').innerHTML = `<optgroup label="Cartridges">${Object.entries(STYLES).map(([id, s]) => `<option value="${id}">${s.name}</option>`).join('')}</optgroup>`
    + `<optgroup label="Consoles">${Object.entries(CONSOLES).map(([id, s]) => `<option value="console:${id}">${s.name}</option>`).join('')}</optgroup>`;
  $('#sliders').innerHTML = SLIDERS.map(([k, label, min, max, step, unit]) =>
    `<label class="row" id="r-${k}"><span>${label}</span><input type="range" id="s-${k}" min="${min}" max="${max}" step="${step}"><output id="o-${k}"></output></label>`).join('');
  for (const [k, , , , step, unit = '', geometry] of SLIDERS) {
    const el = $('#s-' + k);
    el.addEventListener('input', () => { tune[k] = +el.value; $('#o-' + k).textContent = fmt(+el.value, step) + unit; geometry ? rebuild() : applyLook(); remember(); });
  }
  $('#style').addEventListener('change', e => selectStyle(e.target.value));
  $('#color').addEventListener('input', e => { tune.color = e.target.value; applyLook(); remember(); });
  $('#variant').addEventListener('change', e => { tune.variant = e.target.value; syncVariant(); applyLook(); remember(); });
  $('#turntable').addEventListener('change', e => { tune.turntable = e.target.checked; applyLook(); remember(); });
  $('#reset').addEventListener('click', () => { try { const s = saved(); delete s[itemId]; localStorage.setItem(STORE, JSON.stringify(s)); } catch (e) { } selectStyle(itemId); });
  $('#labelFile').addEventListener('change', e => {
    const f = e.target.files[0]; if (!f) return;
    new THREE.TextureLoader().load(URL.createObjectURL(f), t => { t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 8; customLabel = t; rebuild(); });
  });
  $('#envFile').addEventListener('change', e => {
    const f = e.target.files[0]; if (!f) return;
    const loader = /\.exr$/i.test(f.name) ? new EXRLoader() : new RGBELoader();
    loader.load(URL.createObjectURL(f), t => useEnvironment(t, f.name));
  });
  $('#export').addEventListener('click', exportGlb);
  $('#copy').addEventListener('click', copySettings);
}
const fmt = (v, step) => step >= 1 ? String(v) : v.toFixed(2);
function syncRows() {   // show only the controls that apply to this item
  const kind = isConsole(itemId) ? 'console' : 'cart', lid = kind === 'console' && !!entryFor(itemId).lid;
  for (const [k, , , , , , , only] of SLIDERS) $('#r-' + k).hidden = !!only && !(only === kind || (only === 'lid' && lid));
  $('#labelLine').hidden = $('#variantLine').hidden = kind !== 'cart'; $('#colorLine').hidden = kind === 'cart';
}
function syncVariant() {   // the cartridge's color variants, with the games each one was used for
  if (isConsole(itemId)) return;
  const vs = entryFor(itemId).variants;
  $('#variant').innerHTML = vs.map(v => `<option value="${v.name}">${v.name}</option>`).join('');
  $('#variant').value = variantOf().name; $('#variantGames').textContent = variantOf().games;
}
function syncPanel() {
  $('#style').value = itemId; $('#color').value = tune.color; $('#turntable').checked = tune.turntable;
  for (const [k, , , , step, unit = ''] of SLIDERS) { $('#s-' + k).value = tune[k]; $('#o-' + k).textContent = fmt(tune[k], step) + unit; }
}
function selectStyle(id) { itemId = id; tune = tuningFor(id); syncPanel(); syncVariant(); rebuild(); remember(); }
const fileName = () => isConsole(itemId) ? `console-${itemId.slice(8)}.glb` : `cartridge-${itemId}.glb`;

// ---------- export ----------
function exportGlb() {
  const copy = model.clone(); copy.position.set(0, 0, 0);
  new GLTFExporter().parse(copy, buf => {
    const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([buf], { type: 'model/gltf-binary' }));
    a.download = fileName(); document.body.append(a); a.click(); a.remove();
    $('#note').textContent = `Saved ${fileName()} (millimetres${isConsole(itemId) ? ', the lid as its own part' : ', label included'}).`;
  }, err => { $('#note').textContent = 'Export failed: ' + err.message; }, { binary: true });
}
function copySettings() {   // the tuned values, ready to paste into this item's "finish" in cartridges.js or consoles.js
  const t = tune, con = isConsole(itemId), shape = con ? `edge rounding ${t.round}×` : `bevel ${t.bevel} mm, ${t.variant}`;
  const text = `finish: { color: '${con ? t.color : variantOf().color}', roughness: ${t.roughness}, clearcoat: ${t.clearcoat}, clearcoatRoughness: ${t.clearcoatRoughness} },   // ${shape}, ${t.segments} segments`;
  $('#settingsText').value = text; $('#settingsText').hidden = false; $('#settingsText').select();
  navigator.clipboard && navigator.clipboard.writeText(text).then(() => { $('#note').textContent = 'Copied.'; }, () => { $('#note').textContent = 'Select the text and copy it.'; });
}

// ---------- run ----------
function resize() { const r = view.getBoundingClientRect(); renderer.setSize(r.width, r.height, false); camera.aspect = r.width / r.height; camera.updateProjectionMatrix(); }
new ResizeObserver(resize).observe(view);
buildPanel(); loadEnvironment();
{ const last = saved().last; selectStyle(last && entryFor(last) ? last : 'gba'); }
renderer.setAnimationLoop(() => { controls.update(); renderer.render(scene, camera); });
window.studio = { renderer, scene, camera, controls, selectStyle, get model() { return model; }, get tune() { return tune; } };   // for testing
