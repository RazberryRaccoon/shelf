"""Helpers for building Shelf's console and cartridge models in Blender, from a script.

Coordinates are Shelf's: x right, y up, z toward the viewer, 1 unit = 100 mm. Outlines are drawn as seen from
the front. Blender's own axes differ (front is -Y, up is +Z); these helpers convert.

Run a model script with Blender's Python module (pip install bpy), for example:
    python tools/models/build_gba.py
"""
import base64, json, math, os
import bpy, bmesh
from mathutils import Vector

# ---------- 2D outlines ----------

def rounded(corners, seg=8):
    """corners: [(x, y, r), ...] counter-clockwise; r rounds that corner (0 keeps it sharp)."""
    out, n = [], len(corners)
    for i in range(n):
        ax, ay, _ = corners[i - 1]; bx, by, r = corners[i]; cx, cy, _ = corners[(i + 1) % n]
        ux, uy, vx, vy = ax - bx, ay - by, cx - bx, cy - by
        lu, lv = math.hypot(ux, uy), math.hypot(vx, vy); ux, uy, vx, vy = ux / lu, uy / lu, vx / lv, vy / lv
        th = math.acos(max(-1, min(1, ux * vx + uy * vy)))
        if r <= 0 or th < 1e-3 or th > math.pi - 1e-3:
            out.append((bx, by)); continue
        t = min(r / math.tan(th / 2), lu * .5, lv * .5); rr = t * math.tan(th / 2)
        bisx, bisy = ux + vx, uy + vy; bl = math.hypot(bisx, bisy); dc = rr / math.sin(th / 2)
        ox, oy = bx + bisx / bl * dc, by + bisy / bl * dc
        a0 = math.atan2(by + uy * t - oy, bx + ux * t - ox); a1 = math.atan2(by + vy * t - oy, bx + vx * t - ox)
        da = a1 - a0
        while da > math.pi: da -= 2 * math.pi
        while da < -math.pi: da += 2 * math.pi
        k = max(2, math.ceil(seg * abs(da) / (math.pi / 2)))
        for s in range(k + 1):
            a = a0 + da * s / k; out.append((ox + math.cos(a) * rr, oy + math.sin(a) * rr))
    return dedupe(out)

def dedupe(pts):
    return [p for i, p in enumerate(pts) if math.dist(p, pts[i - 1]) > 1e-6]

def rrect(w, h, r, seg=8, cx=0, cy=0):
    R = r if isinstance(r, (list, tuple)) else [r] * 4   # bottom-left, bottom-right, top-right, top-left
    return [(x + cx, y + cy) for x, y in rounded([(-w / 2, -h / 2, R[0]), (w / 2, -h / 2, R[1]), (w / 2, h / 2, R[2]), (-w / 2, h / 2, R[3])], seg)]

def circle(r, n=48, cx=0, cy=0):
    return [(cx + math.cos(2 * math.pi * i / n) * r, cy + math.sin(2 * math.pi * i / n) * r) for i in range(n)]

def area(p):
    return sum(p[i - 1][0] * p[i][1] - p[i][0] * p[i - 1][1] for i in range(len(p))) / 2

def ccw(p):
    return p if area(p) > 0 else p[::-1]

def offset(p, d):
    """Grow (d > 0) or shrink (d < 0) a counter-clockwise outline by d along its vertex normals."""
    p = ccw(p); n = len(p); out = []
    for i in range(n):
        a, b, c = p[i - 1], p[i], p[(i + 1) % n]
        e1 = (b[1] - a[1], -(b[0] - a[0])); e2 = (c[1] - b[1], -(c[0] - b[0]))
        l1, l2 = math.hypot(*e1) or 1, math.hypot(*e2) or 1
        nx, ny = e1[0] / l1 + e2[0] / l2, e1[1] / l1 + e2[1] / l2; ln = math.hypot(nx, ny) or 1
        k = max(.4, (nx / ln) * (e2[0] / l2) + (ny / ln) * (e2[1] / l2))
        out.append((b[0] + nx / ln * d / k, b[1] + ny / ln * d / k))
    return out

def clip(p, a, b, c):
    """Keep the part of outline p where a*x + b*y >= c (one half-plane)."""
    out = []
    for i in range(len(p)):
        P, Q = p[i - 1], p[i]; fp, fq = a * P[0] + b * P[1] - c, a * Q[0] + b * Q[1] - c
        if fq >= 0:
            if fp < 0: t = fp / (fp - fq); out.append((P[0] + (Q[0] - P[0]) * t, P[1] + (Q[1] - P[1]) * t))
            out.append(Q)
        elif fp >= 0:
            t = fp / (fp - fq); out.append((P[0] + (Q[0] - P[0]) * t, P[1] + (Q[1] - P[1]) * t))
    return dedupe(out)

def soften(p, r, seg=6):
    """Round every sharp corner of an outline (after clipping) with radius r."""
    return rounded([(x, y, r) for x, y in ccw(p)], seg)

# ---------- scene ----------

MATS = {}

def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True); MATS.clear()

def mat(name, col, spec=.3, gloss=0, extra=None):
    """A material. col is '#rrggbb'. spec, gloss and extra flags travel to Shelf with the model."""
    if name in MATS: return MATS[name]
    m = bpy.data.materials.new(name); m.use_nodes = True
    rgb = [int(col[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    bsdf = m.node_tree.nodes.get('Principled BSDF'); bsdf.inputs['Base Color'].default_value = (*rgb, 1)
    bsdf.inputs['Roughness'].default_value = .55 - .4 * gloss
    m['shelf'] = json.dumps(dict(col=col, spec=spec, gloss=gloss, **(extra or {})))
    MATS[name] = m; return m

def _place(ob, z):
    ob.rotation_euler = (math.pi / 2, 0, 0)   # the outline's own z (depth) points toward the viewer
    ob.location = (0, -z, 0)

def _to_mesh(ob):
    bpy.context.view_layer.objects.active = ob
    for o in bpy.context.selected_objects: o.select_set(False)
    ob.select_set(True)
    bpy.ops.object.convert(target='MESH')
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    return bpy.context.view_layer.objects.active

def solid(name, outline, z0, z1, bevel=.01, material=None, res=4):
    """An outline (front view) pushed from depth z0 (back) to z1 (front), with rounded edges of radius `bevel`.
    bevel can be [front, back] to round the two faces differently; the less rounded face is then cut crisp."""
    if isinstance(bevel, (list, tuple)):
        bf, bb = bevel
        if abs(bf - bb) > 1e-6:
            big = max(bf, bb); ext = big * 1.5
            ob = solid(name, outline, z0 - (ext if bb < bf else 0), z1 + (ext if bf < bb else 0), big, material, res)
            xs = [p[0] for p in outline]; ys = [p[1] for p in outline]; pad = .5
            cz0, cz1 = (z0 - 1, z0) if bb < bf else (z1, z1 + 1)
            cutter = solid(name + '_trim', [(min(xs) - pad, min(ys) - pad), (max(xs) + pad, min(ys) - pad), (max(xs) + pad, max(ys) + pad), (min(xs) - pad, max(ys) + pad)], cz0, cz1, 0, None, 1)
            return cut(ob, cutter, keep_material=False)
        bevel = bf
    cu = bpy.data.curves.new(name, 'CURVE'); cu.dimensions = '2D'; cu.fill_mode = 'BOTH'
    sp = cu.splines.new('POLY'); pts = ccw(outline); sp.points.add(len(pts) - 1)
    for i, (x, y) in enumerate(pts): sp.points[i].co = (x, y, 0, 1)
    sp.use_cyclic_u = True
    b = min(bevel, (z1 - z0) / 2 * .98)
    cu.extrude = max(0, (z1 - z0) / 2 - b); cu.bevel_depth = b; cu.bevel_resolution = res; cu.offset = -b
    ob = bpy.data.objects.new(name, cu); bpy.context.collection.objects.link(ob); _place(ob, (z0 + z1) / 2)
    ob = _to_mesh(ob)
    if material: ob.data.materials.append(material)
    return ob

def flat(name, outline, z, material, facing=1):
    """A flat face (front view outline) at depth z, facing the viewer (1) or away (-1). Gets 0..1 UVs across its bounds."""
    me = bpy.data.meshes.new(name); bm = bmesh.new()
    pts = ccw(outline) if facing > 0 else ccw(outline)[::-1]
    vs = [bm.verts.new((x, -z, y)) for x, y in pts]; f = bm.faces.new(vs)
    uv = bm.loops.layers.uv.new('UVMap')
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]; x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    for l in f.loops:
        co = l.vert.co; u = (co.x - x0) / (x1 - x0)
        l[uv].uv = ((u if facing > 0 else 1 - u), (co.z - y0) / (y1 - y0))
    bmesh.ops.triangulate(bm, faces=bm.faces); bm.to_mesh(me); bm.free()
    ob = bpy.data.objects.new(name, me); bpy.context.collection.objects.link(ob); me.materials.append(material)
    return ob

def cylinder(name, r, z0, z1, x=0, y=0, material=None, axis='z', bevel=.004, n=40):
    """A round button, screw or hole: a disc of radius r from depth z0 to z1 (axis 'z'), or standing on the top face (axis 'y')."""
    ob = solid(name, circle(r, n), z0, z1, bevel, material, 3)
    if axis == 'y':   # turn so the disc's face points up: rotate about x
        ob.rotation_euler = (-math.pi / 2, 0, 0); bpy.context.view_layer.objects.active = ob
        bpy.ops.object.transform_apply(rotation=True)
    ob.location = (x, 0, y) if axis == 'z' else (x, y, 0)
    bpy.context.view_layer.objects.active = ob; bpy.ops.object.transform_apply(location=True)
    return ob

def box(name, w, h, z0, z1, x=0, y=0, material=None, r=.004, bevel=.003):
    return solid(name, rrect(w, h, r, 4, x, y), z0, z1, bevel, material, 2)

def cut(target, cutter, keep_material=True):
    """Subtract cutter from target. The new inner walls take the cutter's material (so holes can be dark)."""
    mod = target.modifiers.new('cut', 'BOOLEAN'); mod.operation = 'DIFFERENCE'; mod.object = cutter; mod.solver = 'EXACT'
    mod.material_mode = 'TRANSFER' if keep_material else 'INDEX'
    bpy.context.view_layer.objects.active = target; bpy.ops.object.modifier_apply(modifier=mod.name)
    bpy.data.objects.remove(cutter, do_unlink=True)
    return target

def rotate(ob, rx=0, ry=0, rz=0, pivot=(0, 0, 0)):
    """Rotate about a pivot, in Shelf's axes (degrees)."""
    px, py, pz = pivot; bp = Vector((px, -pz, py))
    ob.location -= bp; bpy.context.view_layer.objects.active = ob; bpy.ops.object.transform_apply(location=True)
    ob.rotation_euler = (math.radians(rx), math.radians(-rz), math.radians(ry))   # Shelf x, z, y map to Blender x, -y, z
    bpy.ops.object.transform_apply(rotation=True); ob.location = bp; bpy.ops.object.transform_apply(location=True)
    return ob

# ---------- bake and export ----------

def finish(name, out_dir, ao_size=1024, samples=48, ao_dist=.08):
    """Join everything, smooth it, bake soft shadows (ambient occlusion) into a texture, and write the model file."""
    obs = [o for o in bpy.context.scene.objects if o.type == 'MESH']
    for o in obs:   # an empty material slot can come along from a cut; give it its neighbour's material
        real = [m for m in o.data.materials if m]
        for i, m in enumerate(o.data.materials):
            if m is None and real: o.data.materials[i] = real[0]
    for o in bpy.context.selected_objects: o.select_set(False)
    for o in obs:
        o.select_set(True)
        if 'UVMap' not in o.data.uv_layers: o.data.uv_layers.new(name='UVMap')
    bpy.context.view_layer.objects.active = obs[0]; bpy.ops.object.join(); ob = bpy.context.view_layer.objects.active
    ob.name = name
    bpy.ops.object.shade_auto_smooth(angle=math.radians(35))
    # keep big flat faces truly flat next to their rounded edges (otherwise long thin triangles shade in streaks)
    wn = ob.modifiers.new('flat faces', 'WEIGHTED_NORMAL'); wn.mode = 'FACE_AREA'; wn.weight = 100; wn.keep_sharp = True
    for m in list(ob.modifiers): bpy.ops.object.modifier_apply(modifier=m.name)
    # a second set of UVs, unwrapped without overlaps, for the baked shadows
    me = ob.data; ao_uv = me.uv_layers.new(name='AO'); me.uv_layers.active = ao_uv
    bpy.ops.object.mode_set(mode='EDIT'); bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.uv.smart_project(angle_limit=math.radians(50), island_margin=.006); bpy.ops.object.mode_set(mode='OBJECT')
    img = bpy.data.images.new(name + '_ao', ao_size, ao_size)
    for m in me.materials:
        n = m.node_tree.nodes.new('ShaderNodeTexImage'); n.image = img; m.node_tree.nodes.active = n
    sc = bpy.context.scene; sc.render.engine = 'CYCLES'; sc.cycles.device = 'CPU'; sc.cycles.samples = samples
    sc.render.bake.margin = 6; sc.world = sc.world or bpy.data.worlds.new('w')
    sc.world.light_settings.distance = ao_dist   # how far soft shadows reach: a few millimetres, at this scale
    bpy.ops.object.bake(type='AO')
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, name + '_ao.png'); s = sc.render.image_settings
    s.file_format = 'PNG'; s.color_mode = 'BW'; s.color_depth = '8'; s.compression = 100
    img.save_render(path, scene=sc)   # grayscale is all a shadow map needs
    export(ob, os.path.join(out_dir, name + '.json'), path)
    return ob

def export(ob, path, ao_png):
    """Shelf's model format: one part per material, each with positions, normals, UVs (label/screen), AO UVs and indices."""
    import array
    me = ob.data; me.calc_loop_triangles()
    uv0, uv1 = me.uv_layers['UVMap'].data, me.uv_layers['AO'].data
    normals = [n.vector for n in me.corner_normals]
    parts = {}
    for tri in me.loop_triangles:
        P = parts.setdefault(tri.material_index, {'map': {}, 'v': [], 'i': []})
        poly = me.polygons[tri.polygon_index]; big = poly.area > .0015   # large faces are flat: use their own direction
        for li in tri.loops:
            co = me.vertices[me.loops[li].vertex_index].co; nn = poly.normal if big else normals[li]
            v = (co.x, co.z, -co.y, nn.x, nn.z, -nn.y, uv0[li].uv.x, uv0[li].uv.y, uv1[li].uv.x, uv1[li].uv.y)
            key = tuple(round(x, 5) for x in v)
            if key not in P['map']: P['map'][key] = len(P['v']); P['v'].append(v)
            P['i'].append(P['map'][key])
    b64 = lambda typ, xs: base64.b64encode(array.array(typ, xs).tobytes()).decode()
    out = {'parts': [], 'ao': base64.b64encode(open(ao_png, 'rb').read()).decode()}
    lo, hi = [1e9] * 3, [-1e9] * 3
    for mi, P in sorted(parts.items()):
        assert len(P['v']) < 65536, 'too many vertices in one part'
        V = P['v']
        for v in V:
            for a in range(3): lo[a] = min(lo[a], v[a]); hi[a] = max(hi[a], v[a])
        q = lambda x, k: max(-k, min(k, round(x * k)))   # normals as bytes, UVs as 16-bit fractions: smaller files, same look
        out['parts'].append({'mat': json.loads(me.materials[mi]['shelf']) | {'name': me.materials[mi].name},
            'pos': b64('f', [x for v in V for x in v[0:3]]), 'nor': b64('b', [q(x, 127) for v in V for x in v[3:6]]),
            'uv': b64('H', [q(min(1, max(0, x)), 65535) for v in V for x in v[6:8]]), 'uv2': b64('H', [q(min(1, max(0, x)), 65535) for v in V for x in v[8:10]]), 'idx': b64('H', P['i'])})
    out['lo'], out['hi'] = lo, hi
    json.dump(out, open(path, 'w'))
    print(f'wrote {path}: {sum(len(P["i"]) // 3 for P in parts.values())} triangles, {len(out["parts"])} parts')

# ---------- shaping helpers ----------

def move(ob, dx=0, dy=0, dz=0):
    """Move an object in Shelf's axes and bake the move in."""
    ob.location = (ob.location.x + dx, ob.location.y - dz, ob.location.z + dy)
    bpy.context.view_layer.objects.active = ob; bpy.ops.object.transform_apply(location=True)
    return ob

def band(outline, d_out, d_in, keep):
    """A strip that hugs part of an outline: between the outline grown by d_out and shrunk by d_in, for the points
    where keep(x, y) is true (they must be one unbroken run)."""
    O = ccw(outline); n = len(O); A, B = offset(O, d_out), offset(O, -d_in)
    flags = [keep(*p) for p in O]
    start = next(i for i in range(n) if flags[i] and not flags[i - 1])
    run = []; i = start
    while flags[i % n]: run.append(i % n); i += 1
    return [A[i] for i in run] + [B[i] for i in reversed(run)]

def rotated(pts, deg, cx=0, cy=0):
    a = math.radians(deg); c, s = math.cos(a), math.sin(a)
    return [(cx + (x - cx) * c - (y - cy) * s, cy + (x - cx) * s + (y - cy) * c) for x, y in pts]

# ---------- previews: render the model the way the reference photos see it ----------

def preview(ob, path, view='front', size=900, ortho=1.6, samples=24):
    """Render a quick studio image of the finished model. view: front, back, three-quarter, top, bottom, left."""
    sc = bpy.context.scene; sc.render.engine = 'CYCLES'; sc.cycles.samples = samples; sc.cycles.device = 'CPU'
    sc.render.resolution_x = sc.render.resolution_y = size; sc.render.film_transparent = False
    w = sc.world; w.use_nodes = True; bg = w.node_tree.nodes.get('Background'); bg.inputs[0].default_value = (.85, .86, .9, 1); bg.inputs[1].default_value = .35
    if 'key' not in bpy.data.objects:
        L = bpy.data.lights.new('key', 'AREA'); L.energy = 25; L.size = 3; lo = bpy.data.objects.new('key', L); bpy.context.collection.objects.link(lo)
        lo.location = (-2, -3, 3); lo.rotation_euler = (math.radians(50), 0, math.radians(-35))
    cam = bpy.data.objects.get('cam')
    if not cam:
        cd = bpy.data.cameras.new('cam'); cam = bpy.data.objects.new('cam', cd); bpy.context.collection.objects.link(cam)
    sc.camera = cam; cam.data.type = 'ORTHO'; cam.data.ortho_scale = ortho
    dirs = {'front': (0, -1, 0), 'back': (0, 1, 0), 'top': (0, 0, 1), 'bottom': (0, 0, -1), 'left': (-1, 0, 0),
            'three-quarter': (-.45, -1, .55)}
    d = Vector(dirs[view]).normalized(); cam.location = d * 5
    cam.rotation_euler = (-d).to_track_quat('-Z', 'Z' if view not in ('top', 'bottom') else 'Y').to_euler()
    if view == 'back': cam.rotation_euler = (math.pi / 2, 0, math.pi)   # looking at the back, upright
    if view == 'three-quarter': cam.data.type = 'PERSP'; cam.data.lens = 120
    sc.view_settings.view_transform = 'Standard'; sc.render.image_settings.color_mode = 'RGB'
    sc.render.filepath = path; bpy.ops.render.render(write_still=True)
