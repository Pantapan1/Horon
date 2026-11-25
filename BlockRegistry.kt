package com.voxelhorizon.nativegame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.hypot

data class ItemDef(
    val id: Int,
    val name: String,
    val solid: Boolean = false,
    val transparent: Boolean = false,
    val liquid: Boolean = false,
    val isLava: Boolean = false,
    val isPortal: Boolean = false,
    val isProp3D: Boolean = false,
    val isFoliage: Boolean = false,
    val gravityBlock: Boolean = false,
    val lightLevel: Int = 0,
    val hardness: Float = 1.0f,
    val dropId: Int = id,
    val toolType: String = "",
    val faces: IntArray? = null, // [top, side, bottom]
    val isItem: Boolean = false,
    val colorHex: Int = 0xFFFFFF,
    val damage: Int = 2,
    val mineSpeed: Float = 1.0f,
    val weaponStyle: String = "", // "katana", "hammer", "bow", "grapple"
    val healFood: Int = 0,
    val healHp: Int = 0,
    val armorSlot: Int = -1,      // 0=head, 1=chest, 2=legs, 3=feet
    val armorDef: Int = 0,
    val fuelPower: Float = 0f,
    val interactive: String = ""  // "craft", "smelt", "chest", "bed", "tnt"
)

data class RecipeDef(
    val outId: Int,
    val outCount: Int,
    val req: List<Pair<Int, Int>>,
    val desc: String,
    val pocket2x2: Boolean = false
)

object BlockRegistry {
    const val ATLAS_COLS = 8
    const val ATLAS_ROWS = 12
    const val TILE_PX = 64

    val items = ConcurrentHashMap<Int, ItemDef>()
    val recipes = mutableListOf<RecipeDef>()
    val smeltMap = mutableMapOf<Int, Int>()

    lateinit var atlasPixmap: Pixmap
    lateinit var atlasTexture: Texture
    private var itemIconPixmap: Pixmap? = null
    private var itemIconTexture: Texture? = null
    val iconRegions = ConcurrentHashMap<Int, TextureRegion>()
    private var nextModTile = 48

    fun init() {
        items.clear()
        recipes.clear()
        smeltMap.clear()

        fun reg(d: ItemDef) { items[d.id] = d }

        // --- БЛОКИ (0..40) ---
        reg(ItemDef(0, "Воздух", solid = false, transparent = true))
        reg(ItemDef(1, "Дёрн", solid = true, isFoliage = false, hardness = 0.6f, dropId = 2, toolType = "shovel", faces = intArrayOf(0, 1, 2), colorHex = 0x56B846))
        reg(ItemDef(2, "Земля", solid = true, hardness = 0.5f, dropId = 2, toolType = "shovel", faces = intArrayOf(2, 2, 2), colorHex = 0x865C3A))
        reg(ItemDef(3, "Камень", solid = true, hardness = 1.6f, dropId = 4, toolType = "pick", faces = intArrayOf(3, 3, 3), colorHex = 0x808690))
        reg(ItemDef(4, "Булыжник", solid = true, hardness = 1.8f, dropId = 4, toolType = "pick", faces = intArrayOf(4, 4, 4), colorHex = 0x747A84))
        reg(ItemDef(5, "Дуб", solid = true, hardness = 1.4f, dropId = 5, toolType = "axe", faces = intArrayOf(5, 6, 5), colorHex = 0x6B4A2E, fuelPower = 25f))
        reg(ItemDef(6, "Листва дуба", solid = true, transparent = true, isFoliage = true, hardness = 0.25f, dropId = 113, faces = intArrayOf(7, 7, 7), colorHex = 0x3B983A))
        reg(ItemDef(7, "Дубовые доски", solid = true, hardness = 1.1f, dropId = 7, toolType = "axe", faces = intArrayOf(8, 8, 8), colorHex = 0xB88A52, fuelPower = 15f))
        reg(ItemDef(8, "Песок", solid = true, gravityBlock = true, hardness = 0.5f, dropId = 8, toolType = "shovel", faces = intArrayOf(9, 9, 9), colorHex = 0xE6D696))
        reg(ItemDef(9, "Вода", solid = false, transparent = true, liquid = true, faces = intArrayOf(10, 10, 10), colorHex = 0x2682E8))
        reg(ItemDef(10, "Бедрок", solid = true, hardness = 999f, dropId = 0, faces = intArrayOf(11, 11, 11), colorHex = 0x28282E))
        reg(ItemDef(11, "Угольная руда", solid = true, hardness = 2.0f, dropId = 101, toolType = "pick", faces = intArrayOf(12, 12, 12), colorHex = 0x3A3D45))
        reg(ItemDef(12, "Железная руда", solid = true, hardness = 2.4f, dropId = 12, toolType = "pick", faces = intArrayOf(13, 13, 13), colorHex = 0xC8A890))
        reg(ItemDef(13, "Золотая руда", solid = true, hardness = 2.6f, dropId = 13, toolType = "pick", faces = intArrayOf(14, 14, 14), colorHex = 0xF5C836))
        reg(ItemDef(14, "Алмазная руда", solid = true, hardness = 3.2f, dropId = 104, toolType = "pick", faces = intArrayOf(15, 15, 15), colorHex = 0x42EAFA))
        reg(ItemDef(15, "Стекло", solid = true, transparent = true, hardness = 0.35f, dropId = 15, faces = intArrayOf(16, 16, 16), colorHex = 0xC8EEFF))
        reg(ItemDef(16, "Кирпичи", solid = true, hardness = 1.9f, dropId = 16, toolType = "pick", faces = intArrayOf(17, 17, 17), colorHex = 0xA44B3A))
        reg(ItemDef(17, "Верстак (3x3)", solid = true, hardness = 1.3f, dropId = 17, toolType = "axe", faces = intArrayOf(18, 19, 8), colorHex = 0xB47844, interactive = "craft"))
        reg(ItemDef(18, "Печь", solid = true, hardness = 2.2f, dropId = 18, toolType = "pick", faces = intArrayOf(20, 21, 20), colorHex = 0x6E727A, interactive = "smelt"))
        reg(ItemDef(19, "Снег", solid = true, hardness = 0.4f, dropId = 19, toolType = "shovel", faces = intArrayOf(22, 23, 2), colorHex = 0xF0F6FF))
        reg(ItemDef(20, "Лёд", solid = true, transparent = true, hardness = 0.5f, dropId = 20, faces = intArrayOf(24, 24, 24), colorHex = 0x8ED0FA))
        reg(ItemDef(21, "Кактус", solid = true, hardness = 0.5f, dropId = 21, faces = intArrayOf(25, 26, 25), colorHex = 0x348E38))
        reg(ItemDef(22, "Обсидиан", solid = true, hardness = 5.0f, dropId = 22, toolType = "pick", faces = intArrayOf(27, 27, 27), colorHex = 0x2E1848))
        reg(ItemDef(23, "Светокамень", solid = true, lightLevel = 15, hardness = 0.5f, dropId = 23, faces = intArrayOf(28, 28, 28), colorHex = 0xF8D466))
        reg(ItemDef(24, "Книжная полка", solid = true, hardness = 1.1f, dropId = 24, toolType = "axe", faces = intArrayOf(8, 29, 8), colorHex = 0xA87C4C))
        reg(ItemDef(25, "Гравий", solid = true, gravityBlock = true, hardness = 0.6f, dropId = 25, toolType = "shovel", faces = intArrayOf(30, 30, 30), colorHex = 0x82807E))
        reg(ItemDef(26, "Динамит (TNT)", solid = true, hardness = 0.4f, dropId = 26, faces = intArrayOf(31, 32, 31), colorHex = 0xCC3430, interactive = "tnt"))
        reg(ItemDef(27, "Берёза", solid = true, hardness = 1.4f, dropId = 27, toolType = "axe", faces = intArrayOf(5, 33, 5), colorHex = 0xDCD8CE, fuelPower = 25f))
        reg(ItemDef(28, "Песчаник", solid = true, hardness = 1.3f, dropId = 28, toolType = "pick", faces = intArrayOf(34, 34, 34), colorHex = 0xDEC88C))
        reg(ItemDef(29, "Лава", solid = false, transparent = true, liquid = true, isLava = true, lightLevel = 15, faces = intArrayOf(35, 35, 35), colorHex = 0xF86218))
        reg(ItemDef(30, "Адский камень", solid = true, hardness = 0.7f, dropId = 30, toolType = "pick", faces = intArrayOf(36, 36, 36), colorHex = 0x7E2E30))
        reg(ItemDef(31, "Песок душ", solid = true, hardness = 0.6f, dropId = 31, toolType = "shovel", faces = intArrayOf(37, 37, 37), colorHex = 0x544236))
        reg(ItemDef(32, "Адский кирпич", solid = true, hardness = 2.0f, dropId = 32, toolType = "pick", faces = intArrayOf(38, 38, 38), colorHex = 0x3E1E24))
        reg(ItemDef(33, "Кварцевая руда", solid = true, hardness = 1.9f, dropId = 117, toolType = "pick", faces = intArrayOf(39, 39, 39), colorHex = 0xD8C6C0))
        reg(ItemDef(34, "Портал в Ад", solid = false, transparent = true, isPortal = true, lightLevel = 13, faces = intArrayOf(40, 40, 40), colorHex = 0x9C28F0))
        reg(ItemDef(35, "Сундук (27 слотов)", solid = true, transparent = true, isProp3D = true, hardness = 1.3f, dropId = 35, toolType = "axe", faces = intArrayOf(41, 42, 41), colorHex = 0xC68232, interactive = "chest"))
        reg(ItemDef(36, "Изумрудная руда", solid = true, hardness = 3.0f, dropId = 118, toolType = "pick", faces = intArrayOf(43, 43, 43), colorHex = 0x2EE86A))
        reg(ItemDef(37, "Факел", solid = false, transparent = true, isProp3D = true, lightLevel = 14, hardness = 0.1f, dropId = 37, faces = intArrayOf(44, 44, 44), colorHex = 0xFFB834))
        reg(ItemDef(38, "Кровать", solid = true, transparent = true, isProp3D = true, hardness = 0.6f, dropId = 38, faces = intArrayOf(45, 46, 8), colorHex = 0xD82838, interactive = "bed"))

        // --- ПРЕДМЕТЫ, ОРУЖИЕ (КАТАНА / МОЛОТ / ЛУК / КРЮК-КОШКА), БРОНЯ И ЕДА ---
        reg(ItemDef(101, "Уголь", isItem = true, colorHex = 0x2B2E38, fuelPower = 80f))
        reg(ItemDef(102, "Железный слиток", isItem = true, colorHex = 0xDFE6F0))
        reg(ItemDef(103, "Золотой слиток", isItem = true, colorHex = 0xFFD23F))
        reg(ItemDef(104, "Алмаз", isItem = true, colorHex = 0x4CEEFF))
        reg(ItemDef(105, "Палка", isItem = true, colorHex = 0x9C6D3E, fuelPower = 8f))
        reg(ItemDef(106, "Деревянная кирка", isItem = true, colorHex = 0xB8864B, toolType = "pick", mineSpeed = 2.4f, damage = 3))
        reg(ItemDef(107, "Каменная кирка", isItem = true, colorHex = 0x9299A4, toolType = "pick", mineSpeed = 3.8f, damage = 4))
        reg(ItemDef(108, "Железная кирка", isItem = true, colorHex = 0xE4ECF5, toolType = "pick", mineSpeed = 5.5f, damage = 5))
        reg(ItemDef(109, "Алмазная кирка", isItem = true, colorHex = 0x4CEEFF, toolType = "pick", mineSpeed = 8.5f, damage = 6))
        reg(ItemDef(110, "Топор дровосека", isItem = true, colorHex = 0xB0B8C4, toolType = "axe", mineSpeed = 4.6f, damage = 6))
        reg(ItemDef(111, "Железная Катана", isItem = true, colorHex = 0xE8F2FF, weaponStyle = "katana", damage = 9))
        reg(ItemDef(112, "Алмазная Катана", isItem = true, colorHex = 0x4CEEFF, weaponStyle = "katana", damage = 14))
        reg(ItemDef(113, "Яблоко", isItem = true, colorHex = 0xE8383F, healFood = 5, healHp = 2))
        reg(ItemDef(114, "Сырое мясо", isItem = true, colorHex = 0xD65D58, healFood = 3, healHp = 1))
        reg(ItemDef(115, "Жареный стейк", isItem = true, colorHex = 0x944824, healFood = 9, healHp = 5))
        reg(ItemDef(116, "Огниво (Портал)", isItem = true, colorHex = 0xFF9922))
        reg(ItemDef(117, "Адский кварц", isItem = true, colorHex = 0xF4EFE8))
        reg(ItemDef(118, "Изумруд", isItem = true, colorHex = 0x34E86C))
        reg(ItemDef(119, "Ведро воды", isItem = true, colorHex = 0x3898FF))
        reg(ItemDef(120, "Ведро лавы", isItem = true, colorHex = 0xFF6618, fuelPower = 120f))
        reg(ItemDef(123, "Лук", isItem = true, colorHex = 0x9C6D3E, weaponStyle = "bow", damage = 10))
        reg(ItemDef(124, "Стрела", isItem = true, colorHex = 0xDCDCDC))
        reg(ItemDef(125, "Кожаный шлем", isItem = true, colorHex = 0x9E5C34, armorSlot = 0, armorDef = 2))
        reg(ItemDef(126, "Железный нагрудник", isItem = true, colorHex = 0xDCE4F0, armorSlot = 1, armorDef = 5))
        reg(ItemDef(127, "Железные поножи", isItem = true, colorHex = 0xC8D2E0, armorSlot = 2, armorDef = 4))
        reg(ItemDef(128, "Алмазные ботинки", isItem = true, colorHex = 0x4CEEFF, armorSlot = 3, armorDef = 3))
        reg(ItemDef(129, "Звезда Преисподней", isItem = true, colorHex = 0xFFF275, healFood = 20, healHp = 20))
        reg(ItemDef(130, "Боевой Молот (AoE)", isItem = true, colorHex = 0x8B94A4, weaponStyle = "hammer", damage = 11))
        reg(ItemDef(131, "Крюк-Кошка", isItem = true, colorHex = 0x38BDF8, weaponStyle = "grapple", damage = 3))
        reg(ItemDef(132, "Щит (Вторая рука)", isItem = true, colorHex = 0xD4A846, armorDef = 3))

        // --- РЕЦЕПТЫ: 2x2 (Карманный крафт) и 3x3 (Верстак) ---
        recipes += RecipeDef(7, 4, listOf(5 to 1), "1 Дуб -> 4 Доски", pocket2x2 = true)
        recipes += RecipeDef(7, 4, listOf(27 to 1), "1 Берёза -> 4 Доски", pocket2x2 = true)
        recipes += RecipeDef(105, 4, listOf(7 to 2), "2 Доски -> 4 Палки", pocket2x2 = true)
        recipes += RecipeDef(17, 1, listOf(7 to 4), "4 Доски -> Верстак", pocket2x2 = true)
        recipes += RecipeDef(37, 4, listOf(101 to 1, 105 to 1), "Уголь + Палка -> 4 Факела", pocket2x2 = true)

        recipes += RecipeDef(18, 1, listOf(4 to 8), "8 Булыжника -> Печь")
        recipes += RecipeDef(35, 1, listOf(7 to 8), "8 Досок -> Сундук")
        recipes += RecipeDef(38, 1, listOf(7 to 3, 2 to 2), "3 Доски + 2 Земли -> Кровать")
        recipes += RecipeDef(106, 1, listOf(7 to 3, 105 to 2), "3 Доски + 2 Палки -> Дерев. кирка")
        recipes += RecipeDef(107, 1, listOf(4 to 3, 105 to 2), "3 Булыжника + 2 Палки -> Кам. кирка")
        recipes += RecipeDef(110, 1, listOf(4 to 3, 105 to 2), "3 Булыжника + 2 Палки -> Топор")
        recipes += RecipeDef(108, 1, listOf(102 to 3, 105 to 2), "3 Железа + 2 Палки -> Жел. кирка")
        recipes += RecipeDef(111, 1, listOf(102 to 2, 105 to 1), "2 Железа + Палка -> Жел. Катана")
        recipes += RecipeDef(109, 1, listOf(104 to 3, 105 to 2), "3 Алмаза + 2 Палки -> Алм. кирка")
        recipes += RecipeDef(112, 1, listOf(104 to 2, 105 to 1), "2 Алмаза + Палка -> Алм. Катана")
        recipes += RecipeDef(130, 1, listOf(102 to 5, 105 to 2), "5 Железа + 2 Палки -> Боевой Молот")
        recipes += RecipeDef(131, 1, listOf(102 to 2, 105 to 2), "2 Железа + 2 Палки -> Крюк-Кошка")
        recipes += RecipeDef(132, 1, listOf(7 to 6, 102 to 1), "6 Досок + Железо -> Щит")
        recipes += RecipeDef(123, 1, listOf(105 to 3, 118 to 1), "3 Палки + Изумруд -> Лук")
        recipes += RecipeDef(124, 8, listOf(105 to 1, 4 to 1), "Палка + Булыжник -> 8 Стрел")
        recipes += RecipeDef(116, 1, listOf(102 to 1, 25 to 1), "Железо + Гравий -> Огниво")
        recipes += RecipeDef(126, 1, listOf(102 to 6), "6 Железа -> Нагрудник")
        recipes += RecipeDef(127, 1, listOf(102 to 5), "5 Железа -> Поножи")
        recipes += RecipeDef(128, 1, listOf(104 to 4), "4 Алмаза -> Алмазные ботинки")
        recipes += RecipeDef(26, 1, listOf(8 to 4, 101 to 2), "4 Песка + 2 Угля -> TNT")

        // --- ПЕРЕПЛАВКА В ПЕЧИ ---
        smeltMap[12] = 102 // Железная руда -> Железный слиток
        smeltMap[13] = 103 // Золотая руда -> Золотой слиток
        smeltMap[8] = 15   // Песок -> Стекло
        smeltMap[114] = 115 // Сырое мясо -> Стейк
        smeltMap[4] = 3    // Булыжник -> Камень

        buildAtlas()
        buildItemIcons()
    }

    private fun buildAtlas() {
        atlasPixmap = Pixmap(ATLAS_COLS * TILE_PX, ATLAS_ROWS * TILE_PX, Pixmap.Format.RGBA8888)
        atlasPixmap.blending = Pixmap.Blending.None

        // 1. Заполняем все 48 базовых ячеек процедурными 16x16 -> 64x64 пиксель-арт тайлами
        for (tid in 0 until 48) {
            fillProceduralTile(tid)
        }

        // 2. Накладываем HD аниме-атлас 4x4 (anime_tiles.png от Nano Banana) прямо в 64x64 ячейки!
        val animeFile = Gdx.files.internal("anime_tiles.png")
        if (animeFile.exists()) {
            val src = Pixmap(animeFile)
            val sw = src.width / 4
            val sh = src.height / 4
            fun blit(cx: Int, cy: Int, tileIdx: Int) {
                val dx = (tileIdx % ATLAS_COLS) * TILE_PX
                val dy = (tileIdx / ATLAS_COLS) * TILE_PX
                atlasPixmap.drawPixmap(src, cx * sw, cy * sh, sw, sh, dx, dy, TILE_PX, TILE_PX)
            }
            val map16 = arrayOf(
                intArrayOf(0, 0, 0),  intArrayOf(1, 0, 1),  intArrayOf(2, 0, 2),  intArrayOf(3, 0, 4),
                intArrayOf(0, 1, 8),  intArrayOf(1, 1, 6),  intArrayOf(2, 1, 15), intArrayOf(3, 1, 27),
                intArrayOf(0, 2, 17), intArrayOf(1, 2, 36), intArrayOf(2, 2, 28), intArrayOf(3, 2, 10),
                intArrayOf(0, 3, 35), intArrayOf(1, 3, 18), intArrayOf(2, 3, 42), intArrayOf(3, 3, 21)
            )
            for (m in map16) blit(m[0], m[1], m[2])
            blit(0, 0, 7)   // Листва
            blit(0, 1, 19)  // Бок верстака
            blit(0, 1, 41)  // Крышка сундука
            blit(3, 0, 20)  // Верх печи
            blit(1, 2, 39)  // Кварцевая руда
            src.dispose()
        }

        atlasTexture = Texture(atlasPixmap)
        atlasTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
    }

    private fun fillProceduralTile(tid: Int) {
        val baseCol = when (tid) {
            0 -> Color(0.36f, 0.72f, 0.28f, 1f)
            1 -> Color(0.50f, 0.36f, 0.24f, 1f)
            2 -> Color(0.52f, 0.37f, 0.25f, 1f)
            3 -> Color(0.52f, 0.54f, 0.58f, 1f)
            4 -> Color(0.46f, 0.48f, 0.52f, 1f)
            5 -> Color(0.66f, 0.52f, 0.34f, 1f)
            6 -> Color(0.40f, 0.29f, 0.18f, 1f)
            7 -> Color(0.24f, 0.58f, 0.22f, 1f)
            8 -> Color(0.72f, 0.55f, 0.34f, 1f)
            9 -> Color(0.90f, 0.84f, 0.60f, 1f)
            10 -> Color(0.16f, 0.52f, 0.92f, 0.80f)
            11 -> Color(0.18f, 0.18f, 0.22f, 1f)
            12 -> Color(0.46f, 0.48f, 0.52f, 1f)
            13 -> Color(0.52f, 0.52f, 0.55f, 1f)
            14 -> Color(0.54f, 0.54f, 0.52f, 1f)
            15 -> Color(0.46f, 0.54f, 0.58f, 1f)
            16 -> Color(0.78f, 0.93f, 1.00f, 0.45f)
            17 -> Color(0.64f, 0.29f, 0.22f, 1f)
            22, 23 -> Color(0.94f, 0.96f, 1.00f, 1f)
            24 -> Color(0.55f, 0.80f, 0.98f, 0.82f)
            25, 26 -> Color(0.20f, 0.56f, 0.22f, 1f)
            27 -> Color(0.16f, 0.10f, 0.26f, 1f)
            28 -> Color(0.98f, 0.82f, 0.36f, 1f)
            29 -> Color(0.66f, 0.48f, 0.30f, 1f)
            30 -> Color(0.50f, 0.49f, 0.48f, 1f)
            31, 32 -> Color(0.80f, 0.20f, 0.18f, 1f)
            33 -> Color(0.88f, 0.87f, 0.84f, 1f)
            34 -> Color(0.88f, 0.80f, 0.56f, 1f)
            35 -> Color(0.96f, 0.38f, 0.10f, 1f)
            36, 39 -> Color(0.48f, 0.18f, 0.19f, 1f)
            37 -> Color(0.32f, 0.25f, 0.20f, 1f)
            38 -> Color(0.24f, 0.11f, 0.14f, 1f)
            40 -> Color(0.62f, 0.16f, 0.94f, 0.78f)
            43 -> Color(0.48f, 0.52f, 0.52f, 1f)
            else -> Color(0.65f, 0.52f, 0.36f, 1f)
        }
        val tx = (tid % ATLAS_COLS) * TILE_PX
        val ty = (tid / ATLAS_COLS) * TILE_PX
        val step = TILE_PX / 16
        val tmp = Color()
        for (py in 0 until 16) {
            for (px in 0 until 16) {
                val h = (((px * 37 + py * 17 + tid * 29) xor (px * 13)) and 15) / 15f - 0.5f
                tmp.set(baseCol)
                // Акценты для руд (12,13,14,15,39,43), берёзы (33), стекла (16), TNT (32)
                val isSpot = (px in 3..4 && py in 3..4) || (px in 11..12 && py in 5..6) || (px in 6..7 && py in 10..11)
                if (isSpot && tid in intArrayOf(12, 13, 14, 15, 39, 43)) {
                    when (tid) {
                        12 -> tmp.set(0.12f, 0.12f, 0.15f, 1f)
                        13 -> tmp.set(0.86f, 0.72f, 0.58f, 1f)
                        14 -> tmp.set(1.00f, 0.82f, 0.18f, 1f)
                        15 -> tmp.set(0.24f, 0.92f, 1.00f, 1f)
                        39 -> tmp.set(0.95f, 0.93f, 0.90f, 1f)
                        43 -> tmp.set(0.18f, 0.94f, 0.42f, 1f)
                    }
                } else if (tid == 33 && ((py % 4 == 1 && px in 2..6) || (py % 5 == 3 && px in 9..13))) {
                    tmp.set(0.18f, 0.17f, 0.16f, 1f)
                } else if (tid == 32 && py in 5..10) {
                    tmp.set(0.94f, 0.92f, 0.88f, 1f)
                } else if (tid == 16 && (px == 0 || px == 15 || py == 0 || py == 15)) {
                    tmp.set(0.80f, 0.95f, 1.00f, 0.90f)
                } else {
                    tmp.r = (tmp.r + h * 0.12f).coerceIn(0f, 1f)
                    tmp.g = (tmp.g + h * 0.12f).coerceIn(0f, 1f)
                    tmp.b = (tmp.b + h * 0.12f).coerceIn(0f, 1f)
                }
                atlasPixmap.setColor(tmp)
                atlasPixmap.fillRectangle(tx + px * step, ty + py * step, step, step)
            }
        }
    }

    fun registerModBlock(id: Int, name: String, r: Float, g: Float, b: Float): Int {
        val tid = if (nextModTile < ATLAS_COLS * ATLAS_ROWS) nextModTile++ else 3
        val tx = (tid % ATLAS_COLS) * TILE_PX
        val ty = (tid / ATLAS_COLS) * TILE_PX
        val col = Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), 1f)
        atlasPixmap.setColor(col)
        atlasPixmap.fillRectangle(tx, ty, TILE_PX, TILE_PX)
        atlasPixmap.setColor(col.cpy().mul(0.75f))
        atlasPixmap.drawRectangle(tx, ty, TILE_PX, TILE_PX)
        Gdx.app.postRunnable { atlasTexture.draw(atlasPixmap, 0, 0) }

        val hex = ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
        items[id] = ItemDef(id, name, solid = true, hardness = 1.2f, dropId = id, faces = intArrayOf(tid, tid, tid), colorHex = hex)
        iconRegions[id] = TextureRegion(atlasTexture, tx, ty, TILE_PX, TILE_PX)
        return tid
    }


    fun getIconRegion(id: Int): TextureRegion? = iconRegions[id]

    private fun buildItemIcons() {
        iconRegions.clear()
        // 1. Для всех блоков берём реальный 64x64 тайл прямо из атласа блоков!
        for ((id, def) in items) {
            if (!def.isItem && def.faces != null && id > 0) {
                val tileIdx = def.faces[1]
                val tx = (tileIdx % ATLAS_COLS) * TILE_PX
                val ty = (tileIdx / ATLAS_COLS) * TILE_PX
                iconRegions[id] = TextureRegion(atlasTexture, tx, ty, TILE_PX, TILE_PX)
            }
        }

        // 2. Для всех предметов (101..132) и 3D-пропсов (35, 37, 38) рисуем чёткие 32x32 RPG-иконки!
        val itemIds = items.keys.filter { it >= 100 || it == 37 || it == 38 }.sorted()
        val cols = 8
        val rows = maxOf(4, (itemIds.size + cols - 1) / cols)
        val sz = 32
        val pm = Pixmap(cols * sz, rows * sz, Pixmap.Format.RGBA8888)
        pm.blending = Pixmap.Blending.SourceOver
        pm.setColor(0f, 0f, 0f, 0f)
        pm.fill()

        fun px(ox: Int, oy: Int, x: Int, y: Int, w: Int, h: Int, hex: Int, a: Float = 1f) {
            val r = ((hex shr 16) and 0xFF) / 255f
            val g = ((hex shr 8) and 0xFF) / 255f
            val b = (hex and 0xFF) / 255f
            pm.setColor(r, g, b, a)
            pm.fillRectangle(ox + x * 2, oy + y * 2, w * 2, h * 2)
        }

        itemIds.forEachIndexed { index, id ->
            val def = items[id] ?: return@forEachIndexed
            val ox = (index % cols) * sz
            val oy = (index / cols) * sz
            val c = def.colorHex
            val wood = 0x8A5A2E
            val dark = 0x161220

            when {
                id == 37 -> { // Факел
                    px(ox, oy, 7, 6, 2, 9, wood)
                    px(ox, oy, 6, 4, 4, 3, 0xFF7718)
                    px(ox, oy, 7, 2, 2, 3, 0xFFF488)
                }
                id == 38 -> { // Кровать
                    px(ox, oy, 2, 7, 12, 5, 0x8F5B30)
                    px(ox, oy, 2, 11, 2, 3, 0x6E4220)
                    px(ox, oy, 12, 11, 2, 3, 0x6E4220)
                    px(ox, oy, 3, 5, 3, 3, 0xFFFFFF)
                    px(ox, oy, 6, 5, 8, 4, 0xD62839)
                }
                def.toolType == "pick" -> { // Кирка
                    px(ox, oy, 3, 2, 10, 2, c)
                    px(ox, oy, 2, 3, 2, 3, c)
                    px(ox, oy, 12, 3, 2, 3, c)
                    px(ox, oy, 7, 4, 2, 11, wood)
                }
                def.toolType == "axe" -> { // Топор
                    px(ox, oy, 4, 2, 7, 5, c)
                    px(ox, oy, 3, 4, 3, 4, c)
                    px(ox, oy, 7, 5, 2, 10, wood)
                }
                def.weaponStyle == "katana" -> { // Катана
                    for (i in 0 until 9) {
                        px(ox, oy, 13 - i, 2 + i, 2, 2, c)
                        px(ox, oy, 13 - i, 2 + i, 1, 1, 0xFFFFFF)
                    }
                    px(ox, oy, 4, 9, 4, 2, 0xFFD24C)
                    px(ox, oy, 2, 11, 3, 3, 0x3A2438)
                }
                def.weaponStyle == "hammer" -> { // Боевой Молот
                    px(ox, oy, 3, 2, 10, 5, c)
                    px(ox, oy, 4, 3, 8, 2, 0xFFFFFF, 0.35f)
                    px(ox, oy, 7, 7, 2, 8, wood)
                }
                def.weaponStyle == "grapple" -> { // Крюк-кошка
                    px(ox, oy, 7, 2, 2, 12, 0x94A3B8)
                    px(ox, oy, 3, 3, 10, 2, c)
                    px(ox, oy, 3, 3, 2, 4, c)
                    px(ox, oy, 11, 3, 2, 4, c)
                    px(ox, oy, 6, 12, 4, 3, 0xFFD24C)
                }
                def.weaponStyle == "bow" -> { // Лук
                    px(ox, oy, 4, 2, 1, 12, 0xE8E8E8)
                    px(ox, oy, 5, 2, 2, 2, wood)
                    px(ox, oy, 7, 3, 2, 2, wood)
                    px(ox, oy, 9, 5, 2, 6, wood)
                    px(ox, oy, 7, 11, 2, 2, wood)
                    px(ox, oy, 5, 12, 2, 2, wood)
                    px(ox, oy, 3, 7, 10, 2, 0xFFD24C)
                }
                id == 132 -> { // Щит
                    px(ox, oy, 3, 2, 10, 11, 0x8A5A2E)
                    px(ox, oy, 4, 3, 8, 9, c)
                    px(ox, oy, 7, 4, 2, 7, 0xFFE888)
                    px(ox, oy, 5, 7, 6, 2, 0xFFE888)
                }
                def.armorSlot >= 0 -> { // Броня
                    when (def.armorSlot) {
                        0 -> { px(ox, oy, 3, 4, 10, 8, c); px(ox, oy, 5, 9, 6, 3, dark) }
                        1 -> { px(ox, oy, 3, 3, 10, 10, c); px(ox, oy, 6, 3, 4, 3, dark) }
                        2 -> { px(ox, oy, 4, 3, 8, 4, c); px(ox, oy, 4, 7, 3, 7, c); px(ox, oy, 9, 7, 3, 7, c) }
                        else -> { px(ox, oy, 3, 7, 4, 6, c); px(ox, oy, 9, 7, 4, 6, c) }
                    }
                }
                def.healFood > 0 -> { // Еда (Яблоко, Мясо, Стейк, Звезда)
                    px(ox, oy, 4, 5, 8, 8, c)
                    px(ox, oy, 3, 6, 10, 6, c)
                    px(ox, oy, 5, 6, 2, 2, 0xFFFFFF, 0.55f)
                    if (id == 113) px(ox, oy, 8, 3, 3, 2, 0x4ADE80)
                }
                id == 119 || id == 120 -> { // Ведро воды/лавы
                    px(ox, oy, 3, 4, 10, 10, 0xBCC6D4)
                    px(ox, oy, 4, 5, 8, 4, c)
                }
                id == 116 -> { // Огниво
                    px(ox, oy, 3, 8, 5, 6, 0x8A94A4)
                    px(ox, oy, 8, 3, 4, 7, 0xFF9922)
                    px(ox, oy, 9, 4, 2, 4, 0xFFF275)
                }
                id == 105 || id == 124 -> { // Палка / Стрела
                    for (i in 0 until 10) px(ox, oy, 3 + i, 12 - i, 2, 2, c)
                    if (id == 124) px(ox, oy, 11, 2, 3, 3, 0xFFFFFF)
                }
                id == 102 || id == 103 -> { // Слитки
                    px(ox, oy, 3, 6, 10, 5, c)
                    px(ox, oy, 4, 6, 8, 2, 0xFFFFFF, 0.45f)
                }
                else -> { // Кристаллы / Алмаз / Изумруд / Уголь / Кварц
                    px(ox, oy, 5, 3, 6, 10, c)
                    px(ox, oy, 3, 5, 10, 6, c)
                    px(ox, oy, 5, 4, 3, 3, 0xFFFFFF, 0.45f)
                }
            }
        }

        val tex = Texture(pm)
        tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
        itemIconPixmap = pm
        itemIconTexture = tex

        itemIds.forEachIndexed { index, id ->
            val ox = (index % cols) * sz
            val oy = (index / cols) * sz
            iconRegions[id] = TextureRegion(tex, ox, oy, sz, sz)
        }
    }

    fun dispose() {
        itemIconTexture?.dispose()
        itemIconPixmap?.dispose()
        if (::atlasTexture.isInitialized) atlasTexture.dispose()
        if (::atlasPixmap.isInitialized) atlasPixmap.dispose()
    }
}
