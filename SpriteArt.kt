package com.voxelhorizon.nativegame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.pow

data class SkinProfile(val id: String, val name: String)

val SKINS_LIST = listOf(
    SkinProfile("fox_officer", "Офицер Кицунэ"),
    SkinProfile("estelle_cmd", "Командир Эстель"),
    SkinProfile("luna_cat", "Лунная Кошка"),
    SkinProfile("shadow_knight", "Теневой Рыцарь"),
    SkinProfile("emerald_scout", "Следопыт"),
    SkinProfile("classic", "Классика HD")
)

object SpriteArt {
    private val textures = mutableListOf<Texture>()
    val skinSprites = HashMap<String, Array<TextureRegion>>()
    val mobSprites = HashMap<String, Array<TextureRegion>>()

    lateinit var propsTex: Texture
    lateinit var props: Array<TextureRegion>
    lateinit var crackFrames: Array<TextureRegion>
    lateinit var shadowTex: Texture
    lateinit var glowTex: Texture
    lateinit var slashTex: TextureRegion
    lateinit var whitePx: Texture
    var menuPanoramaTex: Texture? = null
    var logoBannerTex: Texture? = null

    lateinit var panelPatch: NinePatch
    lateinit var btnGreenPatch: NinePatch
    lateinit var btnPurplePatch: NinePatch
    lateinit var btnBluePatch: NinePatch
    lateinit var btnRedPatch: NinePatch
    lateinit var hotbarPatch: NinePatch
    lateinit var slotPatch: NinePatch
    lateinit var slotSelectedPatch: NinePatch
    lateinit var hudBadgePatch: NinePatch
    lateinit var pauseBtnPatch: NinePatch
    lateinit var barFramePatch: NinePatch
    lateinit var joyRingReg: TextureRegion
    lateinit var joyKnobReg: TextureRegion

    fun init() {
        textures.clear(); skinSprites.clear(); mobSprites.clear()

        val wp = Pixmap(2, 2, Pixmap.Format.RGBA8888)
        wp.setColor(Color.WHITE); wp.fill()
        whitePx = makeTex(wp)

        shadowTex = buildShadowTexture()
        glowTex = buildRadialGlowTexture()
        slashTex = TextureRegion(buildSlashArcTexture())

        val pFile = Gdx.files.internal("menu_panorama.png")
        if (pFile.exists()) {
            menuPanoramaTex = Texture(pFile).apply {
                setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
                setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.ClampToEdge)
                textures += this
            }
        }
        val lFile = Gdx.files.internal("logo_banner.png")
        if (lFile.exists()) {
            logoBannerTex = Texture(lFile).apply {
                setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
                textures += this
            }
        }

        val uiTex = Texture(Gdx.files.internal("ui_pixel.png")).apply {
            setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest); textures += this
        }
        fun np(col: Int, row: Int, b: Int = 10) = NinePatch(TextureRegion(uiTex, col * 64, row * 64, 64, 64), b, b, b, b)
        panelPatch        = np(0, 0, 12)
        btnGreenPatch     = np(1, 0, 10)
        btnPurplePatch    = np(2, 0, 10)
        btnBluePatch      = np(3, 0, 10)
        btnRedPatch       = np(4, 0, 10)
        hotbarPatch       = np(5, 0, 10)
        slotPatch         = np(6, 0, 8)
        slotSelectedPatch = np(7, 0, 8)
        hudBadgePatch     = np(0, 1, 8)
        pauseBtnPatch     = np(1, 1, 10)
        barFramePatch     = np(2, 1, 6)
        joyRingReg        = TextureRegion(uiTex, 3 * 64, 64, 64, 64)
        joyKnobReg        = TextureRegion(uiTex, 4 * 64, 64, 64, 64)

        val hTex = Texture(Gdx.files.internal("sprites_heroes.png")).apply {
            setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest); textures += this
        }
        val hFrames = (hTex.height / 80).coerceIn(4, 8)
        SKINS_LIST.forEachIndexed { idx, sk ->
            skinSprites[sk.id] = Array(8) { fr ->
                val srcRow = if (hFrames >= 8) fr else when (fr) { 0, 1 -> 0; 2, 3 -> 1; 4, 5 -> 2; 6 -> 1; else -> 3 }
                TextureRegion(hTex, idx * 64, srcRow * 80, 64, 80)
            }
        }

        val mTex = Texture(Gdx.files.internal("sprites_mobs.png")).apply {
            setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest); textures += this
        }
        val mobOrder = listOf(
            "sheep", "cow", "pig", "chicken", "fox_pet", "zombie", "skeleton", "creeper",
            "spider", "enderman", "piglin", "blaze", "ghast", "inferno_lord", "sky_guardian",
            "deer", "boar", "wolf", "bee", "goblin", "slime", "mini_slime", "yeti",
            "bat", "crystal_golem", "magma_slime", "mimic", "grove_guardian", "frost_titan",
            "depth_devourer", "trader", "owl_pet", "golem_pet"
        )
        if (mTex.width >= 2048) {
            mobOrder.forEachIndexed { idx, mId ->
                val x0 = (idx % 4) * 512
                val y0 = (idx / 4) * 64
                mobSprites[mId] = Array(8) { fr -> TextureRegion(mTex, x0 + fr * 64, y0, 64, 64) }
            }
        } else {
            mobOrder.forEachIndexed { idx, mId ->
                val col = idx % 8; val rowBase = (idx / 8) * 2
                val f0 = TextureRegion(mTex, col * 64, rowBase * 64, 64, 64)
                val f1 = TextureRegion(mTex, col * 64, (rowBase + 1) * 64, 64, 64)
                mobSprites[mId] = Array(8) { fr -> if ((fr and 1) == 0) f0 else f1 }
            }
        }

        propsTex = Texture(Gdx.files.internal("sprites_props.png")).apply {
            setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest); textures += this
        }
        props = Array(62) { i -> TextureRegion(propsTex, (i % 8) * 64, (i / 8) * 64, 64, 64) }
        BlockRegistry.attachPropIcons(propsTex)

        val cFile = Gdx.files.internal("block_cracks.png")
        crackFrames = if (cFile.exists()) {
            val cTex = Texture(cFile).apply { setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest); textures += this }
            Array(8) { fr -> TextureRegion(cTex, fr * 64, 0, 64, 64) }
        } else {
            Array(8) { TextureRegion(whitePx) }
        }
    }

    private fun makeTex(pm: Pixmap, linear: Boolean = false): Texture {
        val t = Texture(pm)
        val f = if (linear) Texture.TextureFilter.Linear else Texture.TextureFilter.Nearest
        t.setFilter(f, f); pm.dispose(); textures += t
        return t
    }

    private fun buildSlashArcTexture(): Texture {
        val sz = 96; val pm = Pixmap(sz, sz, Pixmap.Format.RGBA8888)
        pm.blending = Pixmap.Blending.None
        val c = sz / 2f
        for (y in 0 until sz) for (x in 0 until sz) {
            val dx = x - c; val dy = y - c
            val d = hypot(dx, dy) / (c * 0.92f)
            val ang = atan2(dy.toDouble(), dx.toDouble())
            if (d in 0.52f..0.98f && ang in -1.25..1.25) {
                val edge = 1f - kotlin.math.abs((d - 0.75f) / 0.24f)
                val tip = 1f - (kotlin.math.abs(ang) / 1.28).toFloat().pow(1.6f)
                val a = (edge * tip).coerceIn(0f, 1f)
                pm.setColor(1f, 1f, 1f, a)
            } else pm.setColor(0f, 0f, 0f, 0f)
            pm.drawPixel(x, y)
        }
        return makeTex(pm, linear = true)
    }

    private fun buildShadowTexture(): Texture {
        val pm = Pixmap(64, 32, Pixmap.Format.RGBA8888)
        pm.blending = Pixmap.Blending.None
        for (y in 0 until 32) for (x in 0 until 64) {
            val d = hypot((x - 32f) / 30f, (y - 16f) / 14f)
            pm.setColor(0f, 0f, 0f, ((1f - d).coerceIn(0f, 1f)).pow(1.4f) * 0.44f); pm.drawPixel(x, y)
        }
        return makeTex(pm, linear = true)
    }

    private fun buildRadialGlowTexture(): Texture {
        val pm = Pixmap(128, 128, Pixmap.Format.RGBA8888)
        pm.blending = Pixmap.Blending.None
        for (y in 0 until 128) for (x in 0 until 128) {
            val d = hypot((x - 64f) / 62f, (y - 64f) / 62f)
            pm.setColor(1f, 1f, 1f, ((1f - d).coerceIn(0f, 1f)).pow(2.0f)); pm.drawPixel(x, y)
        }
        return makeTex(pm, linear = true)
    }

    fun dispose() { for (t in textures) t.dispose(); textures.clear() }
}
