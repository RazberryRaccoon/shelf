// The cartridge studio: a turntable view of one cartridge style, with sliders for the bevels, the plastic and the
// lighting, and export to .glb.
import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { RGBELoader } from 'three/addons/loaders/RGBELoader.js';
import { EXRLoader } from 'three/addons/loaders/EXRLoader.js';
import { GLTFExporter } from 'three/addons/exporters/GLTFExporter.js';
import { STYLES } from './cartridges.js';
import { buildCartridge, sampleLabel } from './cartridge.js';

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

// ---------- state, saved per style in this browser ----------
const DEFAULTS = { bevel: .8, segments: 6, envIntensity: .85, exposure: .72, keyLight: 1, turntable: true };   // a studio HDR is bright: these keep plastic colors true
let styleId = 'gba', tune = {}, cart = null, labelTexture = null, customLabel = null, framed = null;
const saved = () => { try { return JSON.parse(localStorage.getItem('cartridge-studio') || '{}'); } catch (e) { return {}; } };
function remember() { try { const s = saved(); s[styleId] = tune; s.last = styleId; localStorage.setItem('cartridge-studio', JSON.stringify(s)); } catch (e) { } }
function tuningFor(id) { return Object.assign({}, DEFAULTS, STYLES[id].finish, saved()[id] || {}); }

function rebuild() {
  const style = STYLES[styleId];
  if (cart) { scene.remove(cart); cart.traverse(o => { if (o.geometry) o.geometry.dispose(); }); }
  if (!customLabel) { if (labelTexture) labelTexture.dispose(); labelTexture = sampleLabel(style); }
  cart = buildCartridge(style, { bevel: tune.bevel, segments: tune.segments, finish: tune, labelTexture: customLabel || labelTexture });
  // stand it upright on its bottom edge, facing the camera
  const box = new THREE.Box3().setFromObject(cart), size = box.getSize(new THREE.Vector3());
  cart.position.y = -box.min.y;
  scene.add(cart);
  shadow.scale.set(size.x * 1.25, size.z * 3.2 + 6, 1); shadow.position.y = .02;
  const r = Math.max(size.x, size.y) * 1.1;
  if (framed !== styleId) {   // frame a newly chosen style; keep the view while tuning
    controls.target.set(0, size.y / 2, 0); camera.position.set(r * .9, size.y * .75 + r * .25, r * 2.6); framed = styleId;
  }
  camera.near = r / 20; camera.far = r * 40; camera.updateProjectionMatrix();
  key.position.set(-r * 2, r * 3, r * 2.5);
  applyLook();
}
function applyLook() {   // things that don't need new geometry
  const m = cart.userData.materials;
  m.plastic.color.set(tune.color); m.plastic.roughness = tune.roughness; m.plastic.clearcoat = tune.clearcoat; m.plastic.clearcoatRoughness = tune.clearcoatRoughness;
  m.plastic.envMapIntensity = m.label.envMapIntensity = tune.envIntensity;
  renderer.toneMappingExposure = tune.exposure; key.intensity = tune.keyLight; controls.autoRotate = tune.turntable;
}

// ---------- controls ----------
const SLIDERS = [
  ['bevel', 'Bevel size', 0, 2.5, .05, 'mm', true], ['segments', 'Bevel segments', 1, 10, 1, '', true],
  ['roughness', 'Roughness', 0, 1, .01], ['clearcoat', 'Clearcoat', 0, 1, .01], ['clearcoatRoughness', 'Clearcoat roughness', 0, 1, .01],
  ['envIntensity', 'Environment intensity', 0, 3, .05], ['exposure', 'Exposure', .3, 2.5, .05], ['keyLight', 'Key light', 0, 4, .05],
];
function buildPanel() {
  $('#style').innerHTML = Object.entries(STYLES).map(([id, s]) => `<option value="${id}">${s.name}</option>`).join('');
  $('#sliders').innerHTML = SLIDERS.map(([k, label, min, max, step, unit]) =>
    `<label class="row"><span>${label}</span><input type="range" id="s-${k}" min="${min}" max="${max}" step="${step}"><output id="o-${k}"></output></label>`).join('');
  for (const [k, , , , step, unit = '', geometry] of SLIDERS) {
    const el = $('#s-' + k);
    el.addEventListener('input', () => { tune[k] = +el.value; $('#o-' + k).textContent = fmt(+el.value, step) + unit; geometry ? rebuild() : applyLook(); remember(); });
  }
  $('#style').addEventListener('change', e => selectStyle(e.target.value));
  $('#color').addEventListener('input', e => { tune.color = e.target.value; applyLook(); remember(); });
  $('#turntable').addEventListener('change', e => { tune.turntable = e.target.checked; applyLook(); remember(); });
  $('#reset').addEventListener('click', () => { try { const s = saved(); delete s[styleId]; localStorage.setItem('cartridge-studio', JSON.stringify(s)); } catch (e) { } selectStyle(styleId); });
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
function syncPanel() {
  $('#style').value = styleId; $('#color').value = tune.color; $('#turntable').checked = tune.turntable;
  for (const [k, , , , step, unit = ''] of SLIDERS) { $('#s-' + k).value = tune[k]; $('#o-' + k).textContent = fmt(tune[k], step) + unit; }
}
function selectStyle(id) { styleId = id; tune = tuningFor(id); syncPanel(); rebuild(); remember(); }

// ---------- export ----------
function exportGlb() {
  const copy = cart.clone(); copy.position.set(0, 0, 0);
  new GLTFExporter().parse(copy, buf => {
    const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([buf], { type: 'model/gltf-binary' }));
    a.download = `cartridge-${styleId}.glb`; document.body.append(a); a.click(); a.remove();
    $('#note').textContent = `Saved cartridge-${styleId}.glb (millimetres, label included).`;
  }, err => { $('#note').textContent = 'Export failed: ' + err.message; }, { binary: true });
}
function copySettings() {   // the tuned values, ready to paste into this style's "finish" in cartridges.js
  const t = tune, text = `finish: { color: '${t.color}', roughness: ${t.roughness}, clearcoat: ${t.clearcoat}, clearcoatRoughness: ${t.clearcoatRoughness} },   // bevel ${t.bevel} mm, ${t.segments} segments`;
  $('#settingsText').value = text; $('#settingsText').hidden = false; $('#settingsText').select();
  navigator.clipboard && navigator.clipboard.writeText(text).then(() => { $('#note').textContent = 'Copied.'; }, () => { $('#note').textContent = 'Select the text and copy it.'; });
}

// ---------- run ----------
function resize() { const r = view.getBoundingClientRect(); renderer.setSize(r.width, r.height, false); camera.aspect = r.width / r.height; camera.updateProjectionMatrix(); }
new ResizeObserver(resize).observe(view);
buildPanel(); loadEnvironment();
selectStyle(STYLES[saved().last] ? saved().last : 'gba');
renderer.setAnimationLoop(() => { controls.update(); renderer.render(scene, camera); });
window.studio = { renderer, scene, camera, controls, selectStyle, get cart() { return cart; }, get tune() { return tune; } };   // for testing
