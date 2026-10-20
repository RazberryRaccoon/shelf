/* ---------- models built in Blender (tools/models): parts, baked soft shadows, and where labels and screens go ---------- */
// MODEL_DATA comes from prototype-source/models, joined in by build.sh.
const blenderModels = {};
function b64buf(s) { const b = atob(s), u = new Uint8Array(b.length); for (let i = 0; i < b.length; i++) u[i] = b.charCodeAt(i); return u.buffer; }
function blenderModel(name, info = {}) {
  if (name in blenderModels) return blenderModels[name];
  const D = typeof MODEL_DATA !== 'undefined' && MODEL_DATA[name]; if (!D) return (blenderModels[name] = null);
  let aoTex = null; const img = new Image();   // the shadow map loads in the background; until then parts draw without it
  img.onload = () => { const t = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t); gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.LUMINANCE, gl.LUMINANCE, gl.UNSIGNED_BYTE, img);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE); aoTex = t; };
  img.src = 'data:image/png;base64,' + D.ao;
  const parts = D.parts.map(P => {
    const pos = new Float32Array(b64buf(P.pos)), nor = Float32Array.from(new Int8Array(b64buf(P.nor)), v => v / 127);
    const uv = Float32Array.from(new Uint16Array(b64buf(P.uv)), v => v / 65535), uv2 = Float32Array.from(new Uint16Array(b64buf(P.uv2)), v => v / 65535);
    const M = P.mat;
    return { mesh: mesh(pos, nor, uv, new Uint16Array(b64buf(P.idx)), uv2), m: IDENT, col: hexRgb(M.col), spec: M.spec, gloss: M.gloss, grain: M.grain || 0,
      label: M.label ? true : undefined, screen: M.screen, led: M.led, glass: M.glass, ao: () => aoTex };
  });
  parts.sort((a, b) => (a.glass ? 1 : 0) - (b.glass ? 1 : 0));
  return (blenderModels[name] = Object.assign({ parts, lo: D.lo, hi: D.hi, blender: true }, info));
}
