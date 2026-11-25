package com.voxelhorizon.nativegame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import kotlin.math.PI
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
    // 4 frames per hero: [0=idle, 1=stepL, 2=stepR, 3=sleeping_on_bed]
    val skinSprites = HashMap<String, Array<TextureRegion>>()
    val mobSprites = HashMap<String, Array<TextureRegion>>()

    lateinit var propsTex: Texture
    lateinit var props: Array<TextureRegion>
    lateinit var shadowTex: Texture
    lateinit var glowTex: Texture
    lateinit var slashTex: Texture
    lateinit var whitePx: Texture
    lateinit var circlePurpleBtn: Texture
    lateinit var circleBlueBtn: Texture
    lateinit var joyRingTex: Texture
    lateinit var joyKnobTex: Texture

    fun init() {
        textures.clear(); skinSprites.clear(); mobSprites.clear()

        val wp = Pixmap(2, 2, Pixmap.Format.RGBA8888)
        wp.setColor(Color.WHITE); wp.fill()
        whitePx = makeTex(wp)

        shadowTex = buildShadowTexture()
        glowTex = buildRadialGlowTexture()
        slashTex = buildSlashArcTexture()

        circlePurpleBtn = buildCircleButton(Color(0.32f, 0.14f, 0.42f, 0.90f), Color(1f, 0.80f, 0.28f, 1f))
        circleBlueBtn = buildCircleButton(Color(0.14f, 0.42f, 0.72f, 0.92f), Color(0.56f, 0.90f, 1f, 1f))
        joyRingTex = buildJoystickRing()
        joyKnobTex = buildCircleButton(Color(0.95f, 0.95f, 0.98f, 0.76f), Color(1f, 1f, 1f, 0.95f))

        // 6 heroes x 4 frames (384x320): idle, left stride, right stride, sleeping!
        val hTex = Texture(Gdx.files.internal("sprites_heroes.png")).apply {
            setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest); textures += this
        }
        SKINS_LIST.forEachIndexed { idx, sk ->
            skinSprites[sk.id] = Array(4) { fr -> TextureRegion(hTex, idx * 64, fr * 80, 64, 80) }
        }

        val mTex = Texture(Gdx.files.internal("sprites_mobs.png")).apply {
            setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest); textures += this
        }
        val mobOrder = listOf(
            "sheep", "cow", "pig", "chicken", "fox_pet", "zombie", "skeleton", "creeper",
            "spider", "enderman", "piglin", "blaze", "ghast", "inferno_lord", "sky_guardian",
            "deer", "boar", "wolf", "bee", "goblin", "slime", "mini_slime", "yeti",
            "bat", "crystal_golem", "magma_slime", "mimic", "grove_guardian", "frost_titan", "depth_devourer"
        )
        mobOrder.forEachIndexed { idx, mId ->
            val col = idx % 8; val rowBase = (idx / 8) * 2
            mobSprites[mId] = arrayOf(
                TextureRegion(mTex, col * 64, rowBase * 64, 64, 64),
                TextureRegion(mTex, col * 64, (rowBase + 1) * 64, 64, 64)
            )
        }

        propsTex = Texture(Gdx.files.internal("sprites_props.png")).apply {
            setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest); textures += this
        }
        props = Array(24) { i -> TextureRegion(propsTex, (i % 8) * 64, (i / 8) * 64, 64, 64) }
        BlockRegistry.attachPropIcons(propsTex)
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
            // Crescent arc on the right/front side (-1.2..+1.2 rad)
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

    private fun buildCircleButton(fill: Color, rim: Color): Texture {
        val sz = 128; val pm = Pixmap(sz, sz, Pixmap.Format.RGBA8888)
        pm.blending = Pixmap.Blending.None; val c = sz / 2f
        for (y in 0 until sz) for (x in 0 until sz) {
            val d = hypot(x + 0.5f - c, y + 0.5f - c)
            when {
                d > c - 1.5f -> pm.setColor(0f, 0f, 0f, 0f)
                d > c - 7.5f -> pm.setColor(0.10f, 0.06f, 0.14f, 0.95f)
                d > c - 13.5f -> pm.setColor(rim.r, rim.g, rim.b, 1f)
                else -> {
                    val hi = (1f - (y / sz.toFloat())) * 0.14f
                    pm.setColor((fill.r + hi).coerceAtMost(1f), (fill.g + hi).coerceAtMost(1f), (fill.b + hi).coerceAtMost(1f), fill.a)
                }
            }
            pm.drawPixel(x, y)
        }
        return makeTex(pm, linear = true)
    }

    private fun buildJoystickRing(): Texture {
        val sz = 192; val pm = Pixmap(sz, sz, Pixmap.Format.RGBA8888)
        pm.blending = Pixmap.Blending.None; val c = sz / 2f
        for (y in 0 until sz) for (x in 0 until sz) {
            val d = hypot(x + 0.5f - c, y + 0.5f - c)
            when {
                d > c - 2f -> pm.setColor(0f, 0f, 0f, 0f)
                d > c - 7f -> pm.setColor(1f, 1f, 1f, 0.52f)
                else -> pm.setColor(0.08f, 0.08f, 0.14f, 0.30f)
            }
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
