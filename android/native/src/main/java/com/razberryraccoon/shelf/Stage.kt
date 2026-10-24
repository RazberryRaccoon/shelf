package com.razberryraccoon.shelf

import android.content.Context
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
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
 * Shelf's 3D layer: Filament drawing into a transparent TextureView, so the space backdrop shows through behind the
 * objects and the text sits on top. Lit like the Cartridge Studio: a studio HDR as image-based light, one soft key light,
 * ACES filmic tone mapping. The camera is a long lens looking at Shelf's 1334 × 750 layout, so a point at depth 0 lands
 * on exactly that pixel of the layout (y measured down from the top, as in the prototype).
 */
class Stage(private val ctx: Context, private val surface: TextureView) : Choreographer.FrameCallback {
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
        view.blendMode = View.BlendMode.TRANSLUCENT   // keeps the backdrop visible through the empty parts
        renderer.clearOptions = renderer.clearOptions.apply { clear = true; clearColor = doubleArrayOf(0.0, 0.0, 0.0, 0.0) }
        view.multiSampleAntiAliasingOptions = view.multiSampleAntiAliasingOptions.apply { enabled = true; sampleCount = 4 }
        view.colorGrading = ColorGrading.Builder().toneMapper(ToneMapper.ACESLegacy()).build(engine)
        camera.setExposure(.72f)   // the Studio's defaults: exposure .72, environment .85, key light 1
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1f, 1f, 1f).intensity(1f).direction(.48f, -.62f, -.62f).castShadows(false)
            .build(engine, sun)
        scene.addEntity(sun)
        loadEnvironment()

        ui.isOpaque = false
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
        ibl = IndirectLight.Builder().reflections(reflections).intensity(.85f).build(engine)
        scene.indirectLight = ibl
        engine.destroyTexture(equirect); engine.destroyTexture(cube)
        specular.destroy(); toCube.destroy(); context.destroy()
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
        onFrame?.invoke(dt)
        val sc = swapChain ?: return
        if (ui.isReadyToRender && renderer.beginFrame(sc, now)) { renderer.render(view); renderer.endFrame() }
    }

    fun destroy() {
        stop()
        ui.detach()
        resources.destroy(); assets.destroy(); materials.destroyMaterials(); materials.destroy()
        ibl?.let { engine.destroyIndirectLight(it) }
        engine.destroyEntity(sun)
        engine.destroyRenderer(renderer); engine.destroyView(view); engine.destroyScene(scene)
        engine.destroyCameraComponent(camera.entity)
        engine.destroy()
    }
}
