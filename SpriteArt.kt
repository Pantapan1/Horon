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
data class EmoteDef(val id: String, val name: String)

val SKINS_LIST = listOf(
    SkinProfile("chibi_classic",  "Чиби Белый"),
    SkinProfile("chibi_scarf",    "Уютный Шарф"),
    SkinProfile("chibi_santa",    "Санта-Чиби"),
    SkinProfile("chibi_snowman",  "Снеговичок"),
    SkinProfile("chibi_reindeer", "Оленёнок"),
    SkinProfile("chibi_angel",    "Ангел"),
    SkinProfile("chibi_valkyrie", "Валькирия"),
    SkinProfile("chibi_beanie",   "Уют в Шапке"),
    SkinProfile("chibi_witch",    "Лесная Ведьма"),
    SkinProfile("chibi_music",    "Меломанка"),
    SkinProfile("chibi_ninja",    "Ниндзя"),
    SkinProfile("chibi_king",     "Король Чиби"),
    SkinProfile("chibi_cool",     "Крутой в Очках"),
    SkinProfile("chibi_fox",      "Лисёнок Кицунэ"),
    SkinProfile("chibi_cat",      "Лунная Кошка"),
    SkinProfile("chibi_bunny",    "Зайка"),
    SkinProfile("chibi_frog",     "Лягушка-Худи"),
    SkinProfile("chibi_knight",   "Рыцарь"),
    SkinProfile("chibi_wizard",   "Звёздный Маг"),
    SkinProfile("chibi_samurai",  "Самурай"),
    SkinProfile("chibi_pirate",   "Пират"),
    SkinProfile("chibi_astro",    "Космонавт"),
    SkinProfile("chibi_robot",    "Пиксель-Бот"),
    SkinProfile("chibi_ghost",    "Призрак"),
    SkinProfile("chibi_demon",    "Демонёнок"),
    SkinProfile("chibi_slime",    "Слайм-Чиби"),
    SkinProfile("chibi_mushroom", "Грибочек"),
    SkinProfile("chibi_chef",     "Шеф-Повар"),
    SkinProfile("chibi_sakura",   "Дух Сакуры"),
    SkinProfile("chibi_cyber",    "Кибер-Неон")
)

val EMOTES_LIST = listOf(
    EmoteDef("wave",  "Привет"),
    EmoteDef("peace", "Мир V"),
    EmoteDef("guns",  "Пистолеты"),
    EmoteDef("point", "Указать"),
    EmoteDef("sit",   "Сесть"),
    EmoteDef("dizzy", "X_X"),
    EmoteDef("shy",   "Смущение"),
    EmoteDef("angry", "Злость"),
    EmoteDef("happy", "Смех ^^"),
    EmoteDef("cry",   "Плач T_T"),
    EmoteDef("cool",  "Крутой"),
    EmoteDef("love",  "Сердце")
)

object SpriteArt {
    private val textures = mutableListOf<Texture>()
    val skinSprites = HashMap<String, Array<TextureRegion>>()
    val mobSprites = HashMap<String, Array<TextureRegion>>()

    lateinit var propsTex: Texture
    lateinit var props: Array<TextureRegion>
    lateinit var crackFrames: Array<TextureRegion>
    lateinit var emoteIcons: Array<TextureRegion>
    lateinit var emoteOverlays: Array<TextureRegion>
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

    // v6.9 Concept UI Themes (Backpack, Parchment, Golden Shop, Medieval Warp, Ocean HUD, Mod Studio!)
    lateinit var backpackPanelPatch: NinePatch
    lateinit var parchmentPatch: NinePatch
    lateinit var woodSlotPatch: NinePatch
    lateinit var goldShopPanelPatch: NinePatch
    lateinit var goldSlotPatch: NinePatch
    lateinit var woodBannerPatch: NinePatch
    lateinit var oceanSlotPatch: NinePatch
    lateinit var oceanSlotSelPatch: NinePatch
    lateinit var oceanPanelPatch: NinePatch
    lateinit var oceanBtnBluePatch: NinePatch
    lateinit var oceanBtnMintPatch: NinePatch
    lateinit var goldBtnPatch: NinePatch
    lateinit var crimsonBtnPatch: NinePatch
    lateinit var leatherBtnPatch: NinePatch
    lateinit var blueprintPanelPatch: NinePatch
    lateinit var blueprintSlotPatch: NinePatch

    lateinit var hudIcons: Array<TextureRegion> // 0=heartF, 1=heartH, 2=heartE, 3=armF, 4=armE, 5=foodF, 6=foodE, 7=coin
    lateinit var warpCards: Array<TextureRegion> // 0=overworld, 1=sakura/camp, 2=nether, 3=aether

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

        val thFile = Gdx.files.internal("ui_theme_v69.png")
        if (thFile.exists()) {
            val thTex = Texture(thFile).apply { setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest); textures += this }
            fun tp(c: Int, r: Int, b: Int = 10) = NinePatch(TextureRegion(thTex, c * 64, r * 64, 64, 64), b, b, b, b)
            backpackPanelPatch  = tp(0, 0, 12)
            parchmentPatch      = tp(1, 0, 10)
            woodSlotPatch       = tp(2, 0, 8)
            goldShopPanelPatch  = tp(3, 0, 12)
            goldSlotPatch       = tp(4, 0, 8)
            woodBannerPatch     = tp(5, 0, 10)
            oceanSlotPatch      = tp(6, 0, 8)
            oceanSlotSelPatch   = tp(7, 0, 8)
            oceanPanelPatch     = tp(0, 1, 12)
            oceanBtnBluePatch   = tp(1, 1, 10)
            oceanBtnMintPatch   = tp(2, 1, 10)
            goldBtnPatch        = tp(3, 1, 10)
            crimsonBtnPatch     = tp(4, 1, 10)
            leatherBtnPatch     = tp(5, 1, 10)
            blueprintPanelPatch = tp(6, 1, 12)
            blueprintSlotPatch  = tp(7, 1, 8)
        } else {
            backpackPanelPatch = panelPatch; parchmentPatch = panelPatch; woodSlotPatch = slotPatch
            goldShopPanelPatch = panelPatch; goldSlotPatch = slotPatch; woodBannerPatch = panelPatch
            oceanSlotPatch = slotPatch; oceanSlotSelPatch = slotSelectedPatch; oceanPanelPatch = panelPatch
            oceanBtnBluePatch = btnBluePatch; oceanBtnMintPatch = btnGreenPatch; goldBtnPatch = btnPurplePatch
            crimsonBtnPatch = btnRedPatch; leatherBtnPatch = btnPurplePatch; blueprintPanelPatch = panelPatch; blueprintSlotPatch = slotPatch
        }

        val hiFile = Gdx.files.internal("hud_icons_v69.png")
        hudIcons = if (hiFile.exists()) {
            val hiTex = Texture(hiFile).apply { setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest); textures += this }
            Array(8) { i -> TextureRegion(hiTex, i * 32, 0, 32, 32) }
        } else Array(8) { TextureRegion(whitePx) }

        val wcFile = Gdx.files.internal("warp_cards_v69.png")
        warpCards = if (wcFile.exists()) {
            val wcTex = Texture(wcFile).apply { setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest); textures += this }
            Array(4) { i -> TextureRegion(wcTex, i * 128, 0, 128, 192) }
        } else Array(4) { TextureRegion(whitePx) }

        val hTex = Texture(Gdx.files.internal("sprites_heroes.png")).apply {
            setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest); textures += this
        }
        val hCols = (hTex.width / 64).coerceAtLeast(1)
        val hFrames = (hTex.height / 80).coerceIn(4, 8)
        SKINS_LIST.forEachIndexed { idx, sk ->
            val col = idx % hCols
            skinSprites[sk.id] = Array(8) { fr ->
                val srcRow = if (hFrames >= 8) fr else when (fr) { 0, 1 -> 0; 2, 3 -> 1; 4, 5 -> 2; 6 -> 1; else -> 3 }
                TextureRegion(hTex, col * 64, srcRow * 80, 64, 80)
            }
        }
        // Обратная совместимость со старыми сохранениями: плавно переводим на новых чиби-героев!
        val legacyMap = mapOf(
            "fox_officer" to "chibi_fox",
            "estelle_cmd" to "chibi_music",
            "luna_cat" to "chibi_cat",
            "shadow_knight" to "chibi_knight",
            "emerald_scout" to "chibi_witch",
            "classic" to "chibi_classic"
        )
        for ((oldId, newId) in legacyMap) {
            skinSprites[newId]?.let { skinSprites[oldId] = it }
        }

        val emFile = Gdx.files.internal("emotes_v611.png")
        emoteIcons = if (emFile.exists()) {
            val eTex = Texture(emFile).apply { setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest); textures += this }
            Array(EMOTES_LIST.size) { i -> TextureRegion(eTex, i * 64, 0, 64, 64) }
        } else Array(EMOTES_LIST.size) { TextureRegion(whitePx) }

        val ovFile = Gdx.files.internal("emote_overlays_v611.png")
        emoteOverlays = if (ovFile.exists()) {
            val oTex = Texture(ovFile).apply { setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest); textures += this }
            Array(EMOTES_LIST.size) { i -> TextureRegion(oTex, i * 64, 0, 64, 80) }
        } else Array(EMOTES_LIST.size) { TextureRegion(whitePx) }

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

    fun getSkinFrames(id: String): Array<TextureRegion> =
        skinSprites[id] ?: skinSprites["chibi_scarf"] ?: skinSprites.values.first()

    fun dispose() { for (t in textures) t.dispose(); textures.clear() }
}
