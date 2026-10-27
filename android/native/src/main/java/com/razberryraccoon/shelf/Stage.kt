package com.razberryraccoon.shelf

import android.content.Context
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.HDRLoader
import com.google.android.filament.utils.IBLPrefilterContext
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.tan

/**
 * Shelf's 3D layer: Filament drawing straight to the screen (a SurfaceView, under the interface's panels), two views a
 * frame: first the space backdrop (Backdrop, flat, no tone mapping), then the 3D objects blended over it. Until 0.20 the
 * 3D drew into a transparent TextureView over a canvas-drawn backdrop, which cost Android an extra copy and a full-screen
 * repaint every frame. Lit like the Cartridge Studio: a studio HDR as image-based light, one soft key light,
 * ACES filmic tone mapping. The camera is a long lens looking at Shelf's 1334 × 750 layout, so a point at depth 0 lands
 * on exactly that pixel of the layout (y measured down from the top, as in the prototype).
 */
class Stage(private val ctx: Context, private val surface: SurfaceView) : Choreographer.FrameCallback {
    companion object {
        init { Filament.init(); Gltfio.init(); Utils.init() }
        const val W = 1334f
        const val H = 750f
        private const val FOV = 14.0   // degrees, vertical: a long lens, so things keep their shapes and line up
        val DIST = (H / 2) / tan(Math.toRadians(FOV / 2)).toFloat()
    }

    val engine: Engine = Engine.create()
    val scene: Scene = engine.createScene()
    private val renderer: Renderer = engine.createRenderer()
    private val view: View = engine.createView()
    private val camera: Camera = engine.createCamera(EntityManager.get().create())
    /** The backdrop's own scene, view and camera: flat, in layout pixels (y up, so layout y is negated), no lights. */
    val backScene: Scene = engine.createScene()
    private val backView: View = engine.createView()
    private val backCamera: Camera = engine.createCamera(EntityManager.get().create())
    private val ui = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val display = DisplayHelper(ctx)
    private var swapChain: SwapChain? = null
    val materials = UbershaderProvider(engine)
    val assets = AssetLoader(engine, materials, EntityManager.get())
    val resources = ResourceLoader(engine)
    private var ibl: IndirectLight? = null
    private val sun = EntityManager.get().create()
    var onFrame: ((Float) -> Unit)? = null   // seconds since the last frame; the screens animate here
    private var last = 0L
    private var running = false

    /** Where the layout sits on the screen: its scale and offset, letterboxed to fit. */
    var scale = 1f; private set
    var ox = 0f; private set
    var oy = 0f; private set

    init {
        view.scene = scene
        view.camera = camera
        view.blendMode = View.BlendMode.TRANSLUCENT   // blended over the backdrop, drawn just before it
        // the backdrop: no tone mapping or other finishing, so its colors reach the screen exactly as its shader works
        // them out (the same values Android's canvas drew), and a plain camera that leaves them alone
        backView.scene = backScene; backView.camera = backCamera
        backView.isPostProcessingEnabled = false
        backView.setShadowingEnabled(false)
        backCamera.setExposure(1f)
        val bg = Theme.bg
        renderer.clearOptions = renderer.clearOptions.apply {
            clear = true; clearColor = doubleArrayOf(android.graphics.Color.red(bg) / 255.0, android.graphics.Color.green(bg) / 255.0, android.graphics.Color.blue(bg) / 255.0, 1.0) }
        view.multiSampleAntiAliasingOptions = view.multiSampleAntiAliasingOptions.apply { enabled = true; sampleCount = 2 }
        // nothing on screen bends light through itself any more (see-through shells are blended), but the disc's clear hub
        // would still switch on a whole extra copy of the frame for refraction whenever it's on screen
        view.setScreenSpaceRefractionEnabled(false)
        // the glow around what's chosen on a game's page draws only around it (Outline): the chosen object marks its pixels
        view.setStencilBufferEnabled(true)
        // PBR Neutral (Khronos): colors and label art come out as they are, and only bright highlights roll off. ACES, before
        // 0.21, darkened everything (a mid grey came out at half its brightness) and crushed dark plastics toward black
        view.colorGrading = ColorGrading.Builder().toneMapper(ToneMapper.PBRNeutralToneMapper()).build(engine)
        camera.setExposure(1f / Look.BRIGHT)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1f, 1f, 1f).intensity(Look.KEY).direction(KEY_DIR[0], KEY_DIR[1], KEY_DIR[2]).castShadows(false)
            .build(engine, sun)
        scene.addEntity(sun)
        loadEnvironment()

        ui.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(s: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(s, ui.swapChainFlags)
                display.attach(renderer, surface.display)
            }
            override fun onDetachedFromSurface() {
                display.detach()
                swapChain?.let { engine.destroySwapChain(it); engine.flushAndWait() }
                swapChain = null
            }
            override fun onResized(width: Int, height: Int) = layout(width, height)
        }
        ui.attachTo(surface)
    }

    /** The studio HDR (Poly Haven, CC0), turned into image-based light on the device. */
    private fun loadEnvironment() {
        val equirect = HDRLoader.createTexture(engine, readAsset("env/studio.hdr")) ?: return
        val context = IBLPrefilterContext(engine)
        val toCube = IBLPrefilterContext.EquirectangularToCubemap(context)
        val specular = IBLPrefilterContext.SpecularFilter(context)
        val cube = toCube.run(equirect)
        val reflections = specular.run(cube)
        ibl = IndirectLight.Builder().reflections(reflections).intensity(Look.ENV).build(engine)
        scene.indirectLight = ibl
        turnKey(0f)
        engine.destroyTexture(equirect); engine.destroyTexture(cube)
        specular.destroy(); toCube.destroy(); context.destroy()
    }

    /**
     * The lighting, live: the studio environment's strength, the brightness, and the key light (Settings › Lighting).
     * Filament's direct exposure brightens by its inverse (setExposure(e) multiplies the light by 1/e), so the
     * brightness goes in as 1 / brightness; before 0.21 the Exposure setting worked backwards because of it.
     */
    fun setLook(env: Float, bright: Float, key: Float) {
        ibl?.setIntensity(env)
        camera.setExposure(1f / bright.coerceAtLeast(.01f))
        engine.lightManager.let { it.setIntensity(it.getInstance(sun), key) }
    }

    /**
     * Turns all the lighting (the studio's reflections and the key light together) by `a` radians more around the
     * vertical axis, on top of the resting turn (RIG_YAW, RIG_PITCH), so a cartridge you turn catches a sweep of highlight.
     */
    fun turnKey(a: Float) {
        val yaw = RIG_YAW + a; val cy = kotlin.math.cos(yaw); val sy = kotlin.math.sin(yaw)
        val cp = kotlin.math.cos(RIG_PITCH); val sp = kotlin.math.sin(RIG_PITCH)
        // the turn, as a 3 × 3 matrix: around the vertical axis by yaw, after tipping around the horizontal axis by pitch
        val m = floatArrayOf(cy, 0f, -sy,   sy * sp, cp, cy * sp,   sy * cp, -sp, cy * cp)   // columns, as Filament takes them
        ibl?.setRotation(m)
        val (x, y, z) = KEY_DIR
        val dx = m[0] * x + m[3] * y + m[6] * z; val dy = m[1] * x + m[4] * y + m[7] * z; val dz = m[2] * x + m[5] * y + m[8] * z
        engine.lightManager.let { it.setDirection(it.getInstance(sun), dx, dy, dz) }
    }

    fun readAsset(path: String): ByteBuffer {
        val bytes = ctx.assets.open(path).use { it.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.flip() }
    }

    private fun layout(width: Int, height: Int) {
        scale = minOf(width / W, height / H)
        ox = (width - W * scale) / 2; oy = (height - H * scale) / 2
        // Filament's viewport counts up from the bottom
        view.viewport = Viewport(ox.toInt(), (height - oy - H * scale).toInt(), (W * scale).toInt(), (H * scale).toInt())
        // the backdrop fills the whole screen, the layout's part of it lined up with the 3D
        backView.viewport = Viewport(0, 0, width, height)
        backCamera.setProjection(Camera.Projection.ORTHO, (-ox / scale).toDouble(), ((width - ox) / scale).toDouble(),
            (-(height - oy) / scale).toDouble(), (oy / scale).toDouble(), 0.0, 10.0)
        backCamera.lookAt(0.0, 0.0, 5.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        camera.setProjection(FOV, (W / H).toDouble(), 50.0, (DIST * 3).toDouble(), Camera.Fov.VERTICAL)
        camera.lookAt(W / 2.0, H / 2.0, DIST.toDouble(), W / 2.0, H / 2.0, 0.0, 0.0, 1.0, 0.0)
    }

    fun start() { if (!running) { running = true; last = 0; Choreographer.getInstance().postFrameCallback(this) } }
    fun stop() { running = false; Choreographer.getInstance().removeFrameCallback(this) }

    override fun doFrame(now: Long) {
        if (!running) return
        Choreographer.getInstance().postFrameCallback(this)
        val dt = if (last == 0L) 1 / 60f else ((now - last) / 1e9f).coerceIn(0f, .1f)
        last = now
        val t0 = System.nanoTime()
        onFrame?.invoke(dt)
        val t1 = System.nanoTime()
        val sc = swapChain ?: return
        // the GPU still busy with an earlier frame: Filament skips this one (it shows as a skipped frame in the meter)
        if (ui.isReadyToRender && renderer.beginFrame(sc, now)) { renderer.render(backView); renderer.render(view); renderer.endFrame(); drawn++ } else skipped++
        // the meter (Settings › Show frame rate): each half second, how many frames came and how long each part took
        ticks++; uiNs += t1 - t0; drawNs += System.nanoTime() - t1; worst = maxOf(worst, dt)
        if (statFrom == 0L) statFrom = now
        if (now - statFrom >= 500_000_000L) {
            val s = (now - statFrom) / 1e9f
            stats = "%.0f fps  ·  %.0f drawn  ·  %d skipped\nscreens %.1f ms  ·  3D %.1f ms  ·  slowest %.0f ms".format(ticks / s, drawn / s, skipped, uiNs / 1e6f / ticks, drawNs / 1e6f / ticks, worst * 1000)
            ticks = 0; drawn = 0; skipped = 0; uiNs = 0; drawNs = 0; worst = 0f; statFrom = now
        }
    }
    private var ticks = 0; private var drawn = 0; private var skipped = 0; private var uiNs = 0L; private var drawNs = 0L; private var worst = 0f; private var statFrom = 0L
    /** The last half second's frame rate and timings, for the meter. */
    var stats = ""; private set

    /**
     * How much smoothing the 3D gets (Settings › Smoothness): 0 best looks (4× multisampling), 1 balanced (2×),
     * 2 smoothest (no multisampling; FXAA, a cheap edge blur, instead). Balanced is the default since 0.20.
     */
    fun quality(q: Int) {
        view.multiSampleAntiAliasingOptions = view.multiSampleAntiAliasingOptions.apply { enabled = q < 2; sampleCount = if (q == 0) 4 else 2 }
        view.antiAliasing = if (q == 2) View.AntiAliasing.FXAA else View.AntiAliasing.NONE
    }

    fun destroy() {
        stop()
        ui.detach()
        resources.destroy(); assets.destroy(); materials.destroyMaterials(); materials.destroy()
        ibl?.let { engine.destroyIndirectLight(it) }
        engine.destroyEntity(sun)
        engine.destroyRenderer(renderer); engine.destroyView(view); engine.destroyScene(scene)
        engine.destroyView(backView); engine.destroyScene(backScene)
        engine.destroyCameraComponent(camera.entity); engine.destroyCameraComponent(backCamera.entity)
        engine.destroy()
    }
}

/**
 * Where the key light shines from: the upper left, about 55° off straight on (after the rig's turn, below). Before 0.21
 * it came from almost edge on to the cartridges' fronts and gave them less than a fifth of its light, so the lighting was
 * nearly all the studio environment, flat, with dark plastic close to black. Now it lights a resting front at about half
 * strength and shapes the sides, while its mirror angle stays about 44° away from the fronts, so they keep their real
 * color (the problem the 0.4 move fixed: gold read tan when the highlight sat on the whole front).
 */
private val KEY_DIR = floatArrayOf(.4f, -.52f, -.75f)

/**
 * The whole lighting is turned to match the cartridges' resting angle. The user (after 0.4): gold looks tan at the
 * resting angle and "properly gold" only when turned to face you head on, so keep the angle and move the light. A
 * mirror's reflection turns twice as far as the mirror, so the lights turn by twice the resting turn (Home's -.2 and
 * the game page's -.28 sideways, -.1 up): a cartridge at rest now sees the lighting it saw facing you head on.
 */
private const val RIG_YAW = -.48f
private const val RIG_PITCH = -.2f

/**
 * Starting values for the lighting (0.21), tuned by rendering every model with this same Filament (its web build) and
 * measuring: with PBR Neutral, a flat grey label at rest comes out within a few shades of its own value (128 → 124,
 * white 255 → 237; ACES had them at 67 and 167), shells within a couple, about half the light from the studio and half
 * from the key. Settings › Lighting still tunes them on the device.
 */
object Look { const val ENV = .5f; const val BRIGHT = 1f; const val KEY = 3.4f }
