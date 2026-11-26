package com.voxelhorizon.nativegame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import java.util.concurrent.ConcurrentHashMap

data class ItemDef(
    val id: Int,
    val name: String,
    val solid: Boolean = false,
    val transparent: Boolean = false,
    val isProp: Boolean = false,
    val propIdx: Int = -1,
    val isClimbable: Boolean = false,
    val isFoliage: Boolean = false,
    val isLiquid: Boolean = false,
    val isLava: Boolean = false,
    val isOre: Boolean = false,
    val isWarmSource: Boolean = false,
    val lightRadius: Float = 0f,
    val lightColor: Int = 0xFFB854,
    val hardness: Float = 0.8f,
    val dropId: Int = id,
    val toolType: String = "",
    val topTile: Int = -1,
    val sideTile: Int = -1,
    val isItem: Boolean = false,
    val colorHex: Int = 0xFFFFFF,
    val damage: Int = 3,
    val mineSpeed: Float = 1.0f,
    val aoeMine: Boolean = false,
    val weaponStyle: String = "",
    val healFood: Int = 0,
    val healHp: Int = 0,
    val armorSlot: Int = -1,
    val armorDef: Int = 0,
    val warmArmor: Boolean = false,
    val fuelPower: Float = 0f,
    val interactive: String = "",
    val maxDurability: Int = 0
)

data class RecipeDef(
    val outId: Int,
    val outCount: Int,
    val req: List<Pair<Int, Int>>,
    val desc: String,
    val pocket2x2: Boolean = false,
    val campfireBrew: Boolean = false,
    val category: String = "build" // "tool", "build", "redstone", "food"
)

object BlockRegistry {
    const val ATLAS_COLS = 16
    const val ATLAS_ROWS = 8
    const val TILE_PX = 64

    val items = ConcurrentHashMap<Int, ItemDef>()
    val recipes = mutableListOf<RecipeDef>()
    val smeltMap = mutableMapOf<Int, Int>()

    lateinit var atlasPixmap: Pixmap
    lateinit var atlasTexture: Texture
    private var itemsTexture1: Texture? = null
    private var itemsTexture2: Texture? = null
    private var itemsTexture3: Texture? = null
    private var itemsTexture4: Texture? = null
    private var itemsTexture5: Texture? = null
    val iconRegions = ConcurrentHashMap<Int, TextureRegion>()
    private var nextModTile = 84
    private val customBlockTiles = HashMap<Int, Int>()
    private val customItemTextures = HashMap<Int, Texture>()
    private var baseRecipeCount = 0

    fun init() {
        items.clear(); recipes.clear(); smeltMap.clear(); iconRegions.clear()
        fun reg(d: ItemDef) { items[d.id] = d }

        // 16x8 Atlas (128 tiles):
        //  0..15: anime_tiles.png   | 16..31: anime_tiles_2.png | 32..47: anime_tiles_3.png
        // 48..51: derived           | 52..67: anime_tiles_4.png | 68..83: anime_tiles_5.png
        // 84..126: Lua Mod blocks   | 127: solid white tile for Rust cutaway caps

        reg(ItemDef(0, "Воздух", solid = false, transparent = true))
        reg(ItemDef(1, "Дёрн", solid = true, isFoliage = true, hardness = 0.5f, dropId = 2, toolType = "shovel", topTile = 0, sideTile = 1, colorHex = 0x56B846))
        reg(ItemDef(2, "Земля", solid = true, hardness = 0.45f, dropId = 2, toolType = "shovel", topTile = 2, sideTile = 2, colorHex = 0x865C3A))
        reg(ItemDef(3, "Камень", solid = true, hardness = 1.15f, dropId = 4, toolType = "pick", topTile = 16, sideTile = 16, colorHex = 0x808690))
        reg(ItemDef(4, "Булыжник", solid = true, hardness = 1.25f, dropId = 4, toolType = "pick", topTile = 3, sideTile = 3, colorHex = 0x747A84))
        reg(ItemDef(5, "Дуб", solid = true, hardness = 0.95f, dropId = 5, toolType = "axe", topTile = 4, sideTile = 5, colorHex = 0x6B4A2E, fuelPower = 25f))
        reg(ItemDef(6, "Листва", solid = true, transparent = true, isFoliage = true, hardness = 0.25f, dropId = 113, topTile = 22, sideTile = 22, colorHex = 0x3B983A))
        reg(ItemDef(7, "Доски", solid = true, hardness = 0.75f, dropId = 7, toolType = "axe", topTile = 4, sideTile = 4, colorHex = 0xB88A52, fuelPower = 15f))
        reg(ItemDef(8, "Песок", solid = true, hardness = 0.45f, dropId = 8, toolType = "shovel", topTile = 24, sideTile = 24, colorHex = 0xE6D696))
        reg(ItemDef(9, "Вода", solid = false, transparent = true, isLiquid = true, topTile = 11, sideTile = 11, colorHex = 0x2682E8))
        reg(ItemDef(10, "Бедрок", solid = true, hardness = 999f, dropId = 0, topTile = 7, sideTile = 7, colorHex = 0x28282E))
        reg(ItemDef(11, "Угольная руда", solid = true, isOre = true, hardness = 1.3f, dropId = 101, toolType = "pick", topTile = 17, sideTile = 17, colorHex = 0x3A3D45))
        reg(ItemDef(12, "Железная руда", solid = true, isOre = true, hardness = 1.6f, dropId = 12, toolType = "pick", topTile = 18, sideTile = 18, colorHex = 0xC8A890))
        reg(ItemDef(13, "Золотая руда", solid = true, isOre = true, hardness = 1.8f, dropId = 13, toolType = "pick", topTile = 19, sideTile = 19, colorHex = 0xF5C836))
        reg(ItemDef(14, "Алмазная руда", solid = true, isOre = true, hardness = 2.2f, dropId = 104, toolType = "pick", lightRadius = 130f, lightColor = 0x42EAFA, topTile = 6, sideTile = 6, colorHex = 0x42EAFA))
        reg(ItemDef(15, "Стекло", solid = true, transparent = true, hardness = 0.35f, dropId = 15, topTile = 29, sideTile = 29, colorHex = 0xC8EEFF))
        reg(ItemDef(16, "Кирпичи", solid = true, hardness = 1.3f, dropId = 16, toolType = "pick", topTile = 8, sideTile = 8, colorHex = 0xA44B3A))
        reg(ItemDef(17, "Верстак (3x3)", solid = true, hardness = 0.85f, dropId = 17, toolType = "axe", topTile = 13, sideTile = 4, colorHex = 0xB47844, interactive = "craft"))
        reg(ItemDef(18, "Печь", solid = true, isWarmSource = true, hardness = 1.4f, dropId = 18, toolType = "pick", lightRadius = 210f, lightColor = 0xFF8832, topTile = 51, sideTile = 15, colorHex = 0x6E727A, interactive = "smelt"))
        reg(ItemDef(19, "Снег", solid = true, hardness = 0.4f, dropId = 19, toolType = "shovel", topTile = 26, sideTile = 48, colorHex = 0xF0F6FF))
        reg(ItemDef(20, "Лёд", solid = true, transparent = true, hardness = 0.45f, dropId = 20, topTile = 27, sideTile = 27, colorHex = 0x8ED0FA))
        reg(ItemDef(21, "Кактус", solid = true, hardness = 0.5f, dropId = 21, topTile = 28, sideTile = 28, colorHex = 0x348E38))
        reg(ItemDef(22, "Обсидиан", solid = true, hardness = 3.0f, dropId = 22, toolType = "pick", topTile = 7, sideTile = 7, colorHex = 0x2E1848))
        reg(ItemDef(23, "Светокамень", solid = true, hardness = 0.5f, dropId = 248, lightRadius = 290f, lightColor = 0xFFE27A, topTile = 10, sideTile = 10, colorHex = 0xF8D466))
        reg(ItemDef(24, "Книжная полка", solid = true, hardness = 0.9f, dropId = 24, toolType = "axe", topTile = 4, sideTile = 30, colorHex = 0xA87C4C))
        reg(ItemDef(26, "Динамит (TNT)", solid = true, hardness = 0.4f, dropId = 26, topTile = 31, sideTile = 31, colorHex = 0xCC3430, interactive = "tnt"))
        reg(ItemDef(27, "Берёза", solid = true, hardness = 0.95f, dropId = 27, toolType = "axe", topTile = 4, sideTile = 23, colorHex = 0xDCD8CE, fuelPower = 25f))
        reg(ItemDef(28, "Песчаник", solid = true, hardness = 1.0f, dropId = 28, toolType = "pick", topTile = 25, sideTile = 25, colorHex = 0xDEC88C))
        reg(ItemDef(29, "Лава", solid = false, transparent = true, isLiquid = true, isLava = true, isWarmSource = true, lightRadius = 250f, lightColor = 0xFF6818, topTile = 12, sideTile = 12, colorHex = 0xF86218))
        reg(ItemDef(30, "Адский камень", solid = true, hardness = 0.7f, dropId = 30, toolType = "pick", topTile = 9, sideTile = 9, colorHex = 0x7E2E30))
        reg(ItemDef(31, "Песок душ", solid = true, hardness = 0.6f, dropId = 31, toolType = "shovel", topTile = 50, sideTile = 50, colorHex = 0x544236))
        reg(ItemDef(32, "Адский кирпич", solid = true, hardness = 1.6f, dropId = 32, toolType = "pick", topTile = 49, sideTile = 49, colorHex = 0x3E1E24))
        reg(ItemDef(33, "Кварцевая руда", solid = true, isOre = true, hardness = 1.5f, dropId = 117, toolType = "pick", topTile = 21, sideTile = 21, colorHex = 0xD8C6C0))
        reg(ItemDef(34, "Портал в Ад", solid = false, transparent = true, isProp = true, propIdx = 5, lightRadius = 260f, lightColor = 0xB842FF, hardness = 4.0f, dropId = 22, topTile = 7, sideTile = 7, colorHex = 0x9C28F0, interactive = "portal"))
        reg(ItemDef(35, "Сундук (27 слотов)", solid = true, transparent = true, isProp = true, propIdx = 2, hardness = 1.0f, dropId = 35, toolType = "axe", topTile = 4, sideTile = 14, colorHex = 0xC68232, interactive = "chest"))
        reg(ItemDef(36, "Изумрудная руда", solid = true, isOre = true, hardness = 2.1f, dropId = 118, toolType = "pick", lightRadius = 120f, lightColor = 0x34E86C, topTile = 20, sideTile = 20, colorHex = 0x2EE86A))
        reg(ItemDef(37, "Факел", solid = false, transparent = true, isProp = true, propIdx = 0, isWarmSource = true, lightRadius = 280f, lightColor = 0xFFB844, hardness = 0.1f, dropId = 37, colorHex = 0xFFB834))
        reg(ItemDef(38, "Кровать", solid = false, transparent = true, isProp = true, propIdx = 4, hardness = 0.6f, dropId = 38, colorHex = 0xD82838, interactive = "bed"))
        reg(ItemDef(39, "Спуск в Шахту", solid = false, transparent = true, isProp = true, propIdx = 7, hardness = 999f, dropId = 0, colorHex = 0x5C5042, interactive = "cave"))

        // Blocks 40..74
        reg(ItemDef(40, "Глубинный сланец", solid = true, hardness = 1.9f, dropId = 40, toolType = "pick", topTile = 32, sideTile = 32, colorHex = 0x3A3E52))
        reg(ItemDef(41, "Замшелый булыжник", solid = true, hardness = 1.2f, dropId = 41, toolType = "pick", topTile = 33, sideTile = 33, colorHex = 0x568A4C))
        reg(ItemDef(42, "Весенняя лужа", solid = true, hardness = 0.45f, dropId = 2, toolType = "shovel", topTile = 34, sideTile = 1, colorHex = 0x4EA8F2))
        reg(ItemDef(43, "Сугроб", solid = true, hardness = 0.35f, dropId = 19, toolType = "shovel", topTile = 35, sideTile = 35, colorHex = 0xE8F4FF))
        reg(ItemDef(44, "Листва сакуры", solid = true, transparent = true, hardness = 0.25f, dropId = 113, topTile = 36, sideTile = 36, colorHex = 0xF8A6CC))
        reg(ItemDef(45, "Сакура", solid = true, hardness = 0.95f, dropId = 45, toolType = "axe", topTile = 4, sideTile = 37, colorHex = 0x7A3844, fuelPower = 25f))
        reg(ItemDef(46, "Ель", solid = true, hardness = 0.95f, dropId = 46, toolType = "axe", topTile = 4, sideTile = 38, colorHex = 0x4A3426, fuelPower = 25f))
        reg(ItemDef(47, "Глина", solid = true, hardness = 0.6f, dropId = 47, toolType = "shovel", topTile = 39, sideTile = 39, colorHex = 0x9CA6BC))
        reg(ItemDef(48, "Медная руда", solid = true, isOre = true, hardness = 1.4f, dropId = 48, toolType = "pick", topTile = 40, sideTile = 40, colorHex = 0xD8723E))
        reg(ItemDef(49, "Аметист", solid = true, isOre = true, hardness = 1.8f, dropId = 141, toolType = "pick", lightRadius = 160f, lightColor = 0xB856FF, topTile = 41, sideTile = 41, colorHex = 0xA846F4))
        reg(ItemDef(50, "Рубиновый блок", solid = true, isOre = true, hardness = 2.0f, dropId = 142, toolType = "pick", lightRadius = 160f, lightColor = 0xFF3A5C, topTile = 42, sideTile = 42, colorHex = 0xE82848))
        reg(ItemDef(51, "Каменный кирпич", solid = true, hardness = 1.4f, dropId = 51, toolType = "pick", topTile = 43, sideTile = 43, colorHex = 0x8A909C))
        reg(ItemDef(52, "Бочка", solid = true, hardness = 1.0f, dropId = 52, toolType = "axe", topTile = 4, sideTile = 44, colorHex = 0x9C6838, interactive = "chest"))
        reg(ItemDef(53, "Тыква", solid = true, hardness = 0.55f, dropId = 53, toolType = "axe", topTile = 45, sideTile = 45, colorHex = 0xDE7418, healFood = 6, healHp = 2))
        reg(ItemDef(54, "Пшеница (грядка)", solid = false, transparent = true, hardness = 0.25f, dropId = 143, topTile = 46, sideTile = 46, colorHex = 0xE6C03C))
        reg(ItemDef(55, "Алый ковёр", solid = true, hardness = 0.45f, dropId = 55, topTile = 47, sideTile = 47, colorHex = 0xBA2636))

        reg(ItemDef(56, "Лестница", solid = false, transparent = true, isProp = true, propIdx = 8, isClimbable = true, hardness = 0.35f, dropId = 56, toolType = "axe", colorHex = 0xB47C42))
        reg(ItemDef(57, "Дверь", solid = true, transparent = true, isProp = true, propIdx = 9, hardness = 0.8f, dropId = 57, toolType = "axe", colorHex = 0x945C2E, interactive = "door"))
        reg(ItemDef(58, "Дверь (откр.)", solid = false, transparent = true, isProp = true, propIdx = 10, hardness = 0.8f, dropId = 57, toolType = "axe", colorHex = 0x945C2E, interactive = "door"))
        reg(ItemDef(59, "Костёр", solid = false, transparent = true, isProp = true, propIdx = 11, isWarmSource = true, lightRadius = 340f, lightColor = 0xFF9832, hardness = 0.5f, dropId = 59, colorHex = 0xFF8822, interactive = "campfire"))
        reg(ItemDef(60, "Фонарь", solid = false, transparent = true, isProp = true, propIdx = 13, isWarmSource = true, lightRadius = 400f, lightColor = 0xFFE688, hardness = 0.4f, dropId = 60, colorHex = 0xFFE278))
        reg(ItemDef(61, "Камень Телепорта", solid = true, transparent = true, isProp = true, propIdx = 14, lightRadius = 250f, lightColor = 0x56D8FF, hardness = 2.5f, dropId = 61, toolType = "pick", colorHex = 0x62C8FF, interactive = "waystone"))
        reg(ItemDef(62, "Лесной Алтарь", solid = true, transparent = true, isProp = true, propIdx = 15, lightRadius = 230f, lightColor = 0x38EE7A, hardness = 999f, dropId = 0, colorHex = 0x36E872, interactive = "altar"))
        reg(ItemDef(63, "Наковальня", solid = true, transparent = true, isProp = true, propIdx = 16, hardness = 2.0f, dropId = 63, toolType = "pick", colorHex = 0x525866, interactive = "anvil"))
        reg(ItemDef(64, "Забор", solid = true, transparent = true, isProp = true, propIdx = 17, hardness = 0.7f, dropId = 64, toolType = "axe", colorHex = 0x9E6838))
        reg(ItemDef(65, "Табличка", solid = false, transparent = true, isProp = true, propIdx = 18, hardness = 0.4f, dropId = 65, toolType = "axe", colorHex = 0xB8844A, interactive = "sign"))
        reg(ItemDef(66, "Светящийся гриб", solid = false, transparent = true, isProp = true, propIdx = 19, lightRadius = 150f, lightColor = 0x48DCF8, hardness = 0.15f, dropId = 145, colorHex = 0x44D4F4))
        reg(ItemDef(67, "Ягодный куст", solid = false, transparent = true, isProp = true, propIdx = 20, hardness = 0.25f, dropId = 144, colorHex = 0x348C38))
        reg(ItemDef(68, "Весенние цветы", solid = false, transparent = true, isProp = true, propIdx = 21, hardness = 0.1f, dropId = 68, colorHex = 0xFF78A8))
        reg(ItemDef(69, "Кристалл аметиста", solid = false, transparent = true, isProp = true, propIdx = 22, isOre = true, lightRadius = 190f, lightColor = 0xC662FF, hardness = 0.8f, dropId = 141, toolType = "pick", colorHex = 0xBC54FF))
        reg(ItemDef(70, "Кристалл рубина", solid = false, transparent = true, isProp = true, propIdx = 23, isOre = true, lightRadius = 190f, lightColor = 0xFF385A, hardness = 0.9f, dropId = 142, toolType = "pick", colorHex = 0xEC284A))
        reg(ItemDef(71, "Сундук (Ловушка!)", solid = true, transparent = true, isProp = true, propIdx = 2, hardness = 1.0f, dropId = 103, colorHex = 0xC68232, interactive = "mimic"))
        reg(ItemDef(72, "Шахтный Лифт", solid = false, transparent = true, isProp = true, propIdx = 24, lightRadius = 170f, lightColor = 0x4CEEFF, hardness = 1.5f, dropId = 72, toolType = "pick", colorHex = 0x48D8FF, interactive = "elevator"))
        reg(ItemDef(73, "Шипы-ловушка", solid = false, transparent = true, isProp = true, propIdx = 25, hardness = 1.0f, dropId = 73, toolType = "pick", colorHex = 0xD83848))
        reg(ItemDef(74, "Авто-арбалет", solid = true, transparent = true, isProp = true, propIdx = 26, hardness = 1.4f, dropId = 74, toolType = "axe", colorHex = 0xB87E44))

        // --- НОВЫЕ БЛОКИ v6.5: РЕДСТОУН, МЕХАНИЗМЫ И ФЕРМЕРСТВО (75..98) ---
        reg(ItemDef(75, "Редстоун руда", solid = true, isOre = true, hardness = 1.7f, dropId = 201, toolType = "pick", lightRadius = 110f, lightColor = 0xFF2838, topTile = 52, sideTile = 52, colorHex = 0xD81E2E))
        reg(ItemDef(76, "Блок Редстоуна", solid = true, hardness = 1.5f, dropId = 76, toolType = "pick", lightRadius = 180f, lightColor = 0xFF3444, topTile = 53, sideTile = 53, colorHex = 0xE62232))
        reg(ItemDef(77, "Редстоун Лампа", solid = true, hardness = 0.8f, dropId = 77, topTile = 54, sideTile = 54, colorHex = 0x6E482E))
        reg(ItemDef(78, "Лампа (ВКЛ)", solid = true, hardness = 0.8f, dropId = 77, lightRadius = 420f, lightColor = 0xFFE278, topTile = 55, sideTile = 55, colorHex = 0xFFD864))
        reg(ItemDef(79, "Провод Редстоуна", solid = false, transparent = true, isProp = true, propIdx = 27, hardness = 0.1f, dropId = 201, colorHex = 0x8A1822))
        reg(ItemDef(80, "Провод (Активен)", solid = false, transparent = true, isProp = true, propIdx = 28, lightRadius = 150f, lightColor = 0xFF3642, hardness = 0.1f, dropId = 201, colorHex = 0xFF3844))
        reg(ItemDef(81, "Рычаг", solid = false, transparent = true, isProp = true, propIdx = 30, hardness = 0.3f, dropId = 81, colorHex = 0x848A96, interactive = "lever"))
        reg(ItemDef(82, "Рычаг (ВКЛ)", solid = false, transparent = true, isProp = true, propIdx = 31, lightRadius = 130f, lightColor = 0xFF3A44, hardness = 0.3f, dropId = 81, colorHex = 0xFF3A44, interactive = "lever"))
        reg(ItemDef(83, "Нажимная плита", solid = false, transparent = true, isProp = true, propIdx = 32, hardness = 0.4f, dropId = 83, toolType = "pick", colorHex = 0x9CA2B0))
        reg(ItemDef(84, "Плита (Нажата)", solid = false, transparent = true, isProp = true, propIdx = 33, lightRadius = 120f, lightColor = 0xFF3A44, hardness = 0.4f, dropId = 83, toolType = "pick", colorHex = 0xFF424E))
        reg(ItemDef(85, "Кнопка Редстоуна", solid = false, transparent = true, isProp = true, propIdx = 34, hardness = 0.3f, dropId = 85, colorHex = 0x9298A6, interactive = "button"))
        reg(ItemDef(86, "Редстоун Факел", solid = false, transparent = true, isProp = true, propIdx = 36, lightRadius = 210f, lightColor = 0xFF3844, hardness = 0.1f, dropId = 86, colorHex = 0xFF3844))
        reg(ItemDef(87, "Повторитель", solid = false, transparent = true, isProp = true, propIdx = 38, lightRadius = 120f, lightColor = 0xFF3844, hardness = 0.3f, dropId = 87, colorHex = 0xB8323A))
        reg(ItemDef(88, "Поршень", solid = true, hardness = 1.2f, dropId = 88, toolType = "pick", topTile = 57, sideTile = 56, colorHex = 0x88909E))
        reg(ItemDef(89, "Поршень (Выдвинут)", solid = true, transparent = true, isProp = true, propIdx = 42, lightRadius = 110f, lightColor = 0xFF444E, hardness = 1.2f, dropId = 88, toolType = "pick", colorHex = 0xB88248))
        reg(ItemDef(90, "Раздатчик", solid = true, hardness = 1.4f, dropId = 90, toolType = "pick", topTile = 51, sideTile = 58, colorHex = 0x747A86))
        reg(ItemDef(91, "Нотный блок", solid = true, hardness = 0.8f, dropId = 91, toolType = "axe", topTile = 59, sideTile = 59, colorHex = 0x845234, interactive = "noteblock"))
        reg(ItemDef(92, "Пашня (Грядка)", solid = true, hardness = 0.5f, dropId = 2, toolType = "shovel", topTile = 60, sideTile = 60, colorHex = 0x5A3A22))
        reg(ItemDef(93, "Грядка моркови", solid = false, transparent = true, isProp = true, propIdx = 55, hardness = 0.25f, dropId = 205, colorHex = 0xEE7E18))
        reg(ItemDef(94, "Грядка картошки", solid = false, transparent = true, isProp = true, propIdx = 56, hardness = 0.25f, dropId = 206, colorHex = 0xD4A85C))
        reg(ItemDef(95, "Куст томатов", solid = false, transparent = true, isProp = true, propIdx = 57, hardness = 0.25f, dropId = 208, colorHex = 0xE2242C))
        reg(ItemDef(96, "Кукуруза (грядка)", solid = false, transparent = true, isProp = true, propIdx = 58, hardness = 0.25f, dropId = 209, colorHex = 0xF8CE30))
        reg(ItemDef(97, "Арбуз (блок)", solid = true, hardness = 0.6f, dropId = 211, toolType = "axe", topTile = 65, sideTile = 65, colorHex = 0x36A238))
        reg(ItemDef(98, "Грядка клубники", solid = false, transparent = true, isProp = true, propIdx = 59, hardness = 0.25f, dropId = 212, colorHex = 0xEE2248))

        // --- НОВЫЕ СТРОИТЕЛЬНЫЕ И ДЕКОР-БЛОКИ v6.5 (161..179) ---
        reg(ItemDef(161, "Мрамор", solid = true, hardness = 1.3f, dropId = 161, toolType = "pick", topTile = 68, sideTile = 68, colorHex = 0xE8EAF2))
        reg(ItemDef(162, "Полир. Мрамор", solid = true, hardness = 1.4f, dropId = 162, toolType = "pick", topTile = 69, sideTile = 69, colorHex = 0xF2F2F8))
        reg(ItemDef(163, "Замш. Кам.кирпич", solid = true, hardness = 1.4f, dropId = 163, toolType = "pick", topTile = 70, sideTile = 70, colorHex = 0x5E8C56))
        reg(ItemDef(164, "Медный блок", solid = true, hardness = 1.6f, dropId = 164, toolType = "pick", topTile = 71, sideTile = 71, colorHex = 0xD67242))
        reg(ItemDef(165, "Золотой блок", solid = true, hardness = 1.8f, dropId = 165, toolType = "pick", topTile = 72, sideTile = 72, colorHex = 0xF4C630))
        reg(ItemDef(166, "Алмазный блок", solid = true, hardness = 2.4f, dropId = 166, toolType = "pick", lightRadius = 140f, lightColor = 0x4CEEFF, topTile = 73, sideTile = 73, colorHex = 0x4CEEFF))
        reg(ItemDef(167, "Обсид. кирпич", solid = true, hardness = 2.8f, dropId = 167, toolType = "pick", topTile = 74, sideTile = 74, colorHex = 0x341E54))
        reg(ItemDef(168, "Синее стекло", solid = true, transparent = true, hardness = 0.35f, dropId = 168, topTile = 75, sideTile = 75, colorHex = 0x3892F6))
        reg(ItemDef(169, "Зелёное стекло", solid = true, transparent = true, hardness = 0.35f, dropId = 169, topTile = 76, sideTile = 76, colorHex = 0x30CE62))
        reg(ItemDef(170, "Янтарное стекло", solid = true, transparent = true, hardness = 0.35f, dropId = 170, topTile = 77, sideTile = 77, colorHex = 0xF8BA30))
        reg(ItemDef(171, "Сноп сена", solid = true, hardness = 0.5f, dropId = 171, toolType = "axe", topTile = 67, sideTile = 67, colorHex = 0xDAC038))
        reg(ItemDef(172, "Железная дверь", solid = true, transparent = true, isProp = true, propIdx = 40, hardness = 2.0f, dropId = 172, toolType = "pick", colorHex = 0xBAC4D4, interactive = "iron_door"))
        reg(ItemDef(173, "Жел. дверь (откр.)", solid = false, transparent = true, isProp = true, propIdx = 41, hardness = 2.0f, dropId = 172, toolType = "pick", colorHex = 0xBAC4D4, interactive = "iron_door"))
        reg(ItemDef(174, "Тёмные доски", solid = true, hardness = 0.75f, dropId = 174, toolType = "axe", topTile = 78, sideTile = 78, colorHex = 0x664632, fuelPower = 15f))
        reg(ItemDef(175, "Берёзовые доски", solid = true, hardness = 0.75f, dropId = 175, toolType = "axe", topTile = 79, sideTile = 79, colorHex = 0xE4D8B4, fuelPower = 15f))
        reg(ItemDef(176, "Доски сакуры", solid = true, hardness = 0.75f, dropId = 176, toolType = "axe", topTile = 80, sideTile = 80, colorHex = 0xE89AB2, fuelPower = 15f))
        reg(ItemDef(177, "Адская золотая руда", solid = true, isOre = true, hardness = 1.6f, dropId = 103, toolType = "pick", topTile = 82, sideTile = 82, colorHex = 0xE29828))
        reg(ItemDef(178, "Резной песчаник", solid = true, hardness = 1.1f, dropId = 178, toolType = "pick", topTile = 81, sideTile = 81, colorHex = 0xDEC88C))
        reg(ItemDef(179, "Светящийся кристалл", solid = true, hardness = 1.2f, dropId = 179, toolType = "pick", lightRadius = 320f, lightColor = 0x38E2EE, topTile = 83, sideTile = 83, colorHex = 0x38E2EE))
        reg(ItemDef(180, "Настенный факел (Л)", solid = false, transparent = true, isProp = true, propIdx = 43, isWarmSource = true, lightRadius = 290f, lightColor = 0xFFB844, hardness = 0.1f, dropId = 37, colorHex = 0xFFB834))
        reg(ItemDef(181, "Настенный факел (П)", solid = false, transparent = true, isProp = true, propIdx = 45, isWarmSource = true, lightRadius = 290f, lightColor = 0xFFB844, hardness = 0.1f, dropId = 37, colorHex = 0xFFB834))
        reg(ItemDef(182, "Воронка (Авто-сбор)", solid = true, transparent = true, isProp = true, propIdx = 47, hardness = 1.2f, dropId = 182, toolType = "pick", colorHex = 0x747C8C))
        reg(ItemDef(183, "Датчик света (День)", solid = false, transparent = true, isProp = true, propIdx = 48, hardness = 0.5f, dropId = 183, toolType = "axe", colorHex = 0x567CA8))
        reg(ItemDef(184, "Датчик света (Ночь)", solid = false, transparent = true, isProp = true, propIdx = 49, lightRadius = 130f, lightColor = 0xFFE278, hardness = 0.5f, dropId = 183, toolType = "axe", colorHex = 0xF6C648))
        reg(ItemDef(185, "Рельсы", solid = false, transparent = true, isProp = true, propIdx = 50, hardness = 0.5f, dropId = 185, toolType = "pick", colorHex = 0xB4BCCB))
        reg(ItemDef(186, "Энергорельсы", solid = false, transparent = true, isProp = true, propIdx = 51, lightRadius = 130f, lightColor = 0xFF3A44, hardness = 0.6f, dropId = 186, toolType = "pick", colorHex = 0xF2C230))
        reg(ItemDef(187, "Стол Зачарований", solid = true, transparent = true, isProp = true, propIdx = 53, lightRadius = 210f, lightColor = 0xB856FF, hardness = 2.4f, dropId = 187, toolType = "pick", colorHex = 0x9C38EE, interactive = "enchant"))

        // --- ПРЕДМЕТЫ (101..160 — ВСЕ С УНИКАЛЬНЫМИ ИКОНКАМИ!) ---
        reg(ItemDef(101, "Уголь", isItem = true, colorHex = 0x2B2E38, fuelPower = 80f))
        reg(ItemDef(102, "Железный слиток", isItem = true, colorHex = 0xDFE6F0))
        reg(ItemDef(103, "Золотой слиток", isItem = true, colorHex = 0xFFD23F))
        reg(ItemDef(104, "Алмаз", isItem = true, colorHex = 0x4CEEFF))
        reg(ItemDef(105, "Палка", isItem = true, colorHex = 0x9C6D3E, fuelPower = 8f))
        reg(ItemDef(106, "Деревянная кирка", isItem = true, colorHex = 0xB8864B, toolType = "pick", mineSpeed = 2.4f, damage = 4, maxDurability = 65))
        reg(ItemDef(107, "Каменная кирка", isItem = true, colorHex = 0x9299A4, toolType = "pick", mineSpeed = 3.8f, damage = 5, maxDurability = 135))
        reg(ItemDef(108, "Железная кирка", isItem = true, colorHex = 0xE4ECF5, toolType = "pick", mineSpeed = 5.5f, damage = 6, maxDurability = 280))
        reg(ItemDef(109, "Алмазная кирка", isItem = true, colorHex = 0x4CEEFF, toolType = "pick", mineSpeed = 8.5f, damage = 7, maxDurability = 820))
        reg(ItemDef(110, "Боевой Топор", isItem = true, colorHex = 0xB0B8C4, toolType = "axe", mineSpeed = 4.8f, damage = 7, maxDurability = 260))
        reg(ItemDef(111, "Железный Меч", isItem = true, colorHex = 0xE8F2FF, weaponStyle = "katana", damage = 10, maxDurability = 280))
        reg(ItemDef(112, "Алмазный Меч", isItem = true, colorHex = 0x4CEEFF, weaponStyle = "katana", damage = 16, maxDurability = 820))
        reg(ItemDef(113, "Яблоко", isItem = true, colorHex = 0xE8383F, healFood = 5, healHp = 3))
        reg(ItemDef(114, "Хлеб", isItem = true, colorHex = 0xD69648, healFood = 6, healHp = 2))
        reg(ItemDef(115, "Жареный стейк", isItem = true, colorHex = 0x944824, healFood = 9, healHp = 6))
        reg(ItemDef(116, "Огниво", isItem = true, colorHex = 0xFF9922, maxDurability = 90))
        reg(ItemDef(117, "Адский кварц", isItem = true, colorHex = 0xF4EFE8))
        reg(ItemDef(118, "Изумруд", isItem = true, colorHex = 0x34E86C))
        reg(ItemDef(119, "Ведро воды", isItem = true, colorHex = 0x3898FF))
        reg(ItemDef(120, "Пустое ведро", isItem = true, colorHex = 0x9CA4B2))
        reg(ItemDef(123, "Лук", isItem = true, colorHex = 0x9C6D3E, weaponStyle = "bow", damage = 11, maxDurability = 260))
        reg(ItemDef(124, "Стрела", isItem = true, colorHex = 0xDCDCDC))
        reg(ItemDef(125, "Кожаный шлем", isItem = true, colorHex = 0x9E5C34, armorSlot = 0, armorDef = 2, maxDurability = 130))
        reg(ItemDef(126, "Железный нагрудник", isItem = true, colorHex = 0xDCE4F0, armorSlot = 1, armorDef = 5, maxDurability = 290))
        reg(ItemDef(127, "Железные поножи", isItem = true, colorHex = 0xC8D2E0, armorSlot = 2, armorDef = 4, maxDurability = 270))
        reg(ItemDef(128, "Алмазные ботинки", isItem = true, colorHex = 0x4CEEFF, armorSlot = 3, armorDef = 3, maxDurability = 760))
        reg(ItemDef(129, "Звезда Преисподней", isItem = true, colorHex = 0xFFF275, healFood = 20, healHp = 20))
        reg(ItemDef(130, "Боевой Молот (AoE)", isItem = true, colorHex = 0x8B94A4, weaponStyle = "hammer", damage = 13, maxDurability = 320))
        reg(ItemDef(131, "Крюк-Кошка", isItem = true, colorHex = 0x38BDF8, weaponStyle = "grapple", damage = 4, maxDurability = 180))
        reg(ItemDef(132, "Щит (Парирование)", isItem = true, colorHex = 0xD4A846, armorDef = 4, maxDurability = 260))
        reg(ItemDef(133, "Сердце Леса (+4 MaxHP)", isItem = true, colorHex = 0x2EE870))
        reg(ItemDef(134, "Ледяной Клинок", isItem = true, colorHex = 0x5CE2FF, weaponStyle = "frost", damage = 18, maxDurability = 860))
        reg(ItemDef(135, "Кристальная Кирка (2x2)", isItem = true, colorHex = 0xC852FF, toolType = "pick", mineSpeed = 11.5f, aoeMine = true, damage = 10, maxDurability = 1150))
        reg(ItemDef(136, "Согревающий чай", isItem = true, colorHex = 0xF29438, healFood = 4, healHp = 4))
        reg(ItemDef(137, "Отвар Шахтёра", isItem = true, colorHex = 0xFFD238, healFood = 2, healHp = 2))
        reg(ItemDef(138, "Эликсир Скорости", isItem = true, colorHex = 0x38DAFF, healFood = 2, healHp = 2))
        reg(ItemDef(139, "Рубиновый Клинок", isItem = true, colorHex = 0xFF3458, weaponStyle = "ruby", damage = 20, maxDurability = 1150))
        reg(ItemDef(140, "Медный слиток", isItem = true, colorHex = 0xD8723E))
        reg(ItemDef(141, "Осколок аметиста", isItem = true, colorHex = 0xB846F4))
        reg(ItemDef(142, "Рубин", isItem = true, colorHex = 0xE82648))
        reg(ItemDef(143, "Пшеница", isItem = true, colorHex = 0xE6C03C))
        reg(ItemDef(144, "Лесные ягоды", isItem = true, colorHex = 0xE82448, healFood = 3, healHp = 2))
        reg(ItemDef(145, "Подземный гриб", isItem = true, colorHex = 0x44D4F4, healFood = 2, healHp = 1))
        reg(ItemDef(146, "Жареный гриб", isItem = true, colorHex = 0xA85C2A, healFood = 7, healHp = 4))
        reg(ItemDef(147, "Сгусток слизи", isItem = true, colorHex = 0x56DA5C))
        reg(ItemDef(148, "Тёплая шуба", isItem = true, colorHex = 0x9C6438, armorSlot = 1, armorDef = 4, warmArmor = true, maxDurability = 220))
        reg(ItemDef(149, "Рубиновый Нагрудник", isItem = true, colorHex = 0xE82648, armorSlot = 1, armorDef = 7, warmArmor = true, maxDurability = 1100))
        reg(ItemDef(150, "Удочка (Рыбалка)", isItem = true, colorHex = 0x9C6834, maxDurability = 95))
        reg(ItemDef(151, "Карась", isItem = true, colorHex = 0x76BAE0, healFood = 4, healHp = 2))
        reg(ItemDef(152, "Лосось", isItem = true, colorHex = 0xE86C4C, healFood = 5, healHp = 3))
        reg(ItemDef(153, "Ледяной Окунь", isItem = true, colorHex = 0x56E2FF, healFood = 5, healHp = 4))
        reg(ItemDef(154, "Золотая Рыбка", isItem = true, colorHex = 0xF6C62A, healFood = 10, healHp = 10))
        reg(ItemDef(155, "Уха (Рыбный суп)", isItem = true, colorHex = 0xF2A23A, healFood = 14, healHp = 10))
        reg(ItemDef(156, "Ягодный пирог", isItem = true, colorHex = 0xE23452, healFood = 12, healHp = 8))
        reg(ItemDef(157, "Сырое мясо", isItem = true, colorHex = 0xD83842, healFood = 2, healHp = 1))
        reg(ItemDef(158, "Мех / Шкура", isItem = true, colorHex = 0x9C6838))
        reg(ItemDef(159, "Порох", isItem = true, colorHex = 0x565C68))
        reg(ItemDef(160, "Паутина / Нить", isItem = true, colorHex = 0xE8EEF8))

        // --- 55 НОВЫХ ПРЕДМЕТОВ v6.5 (201..255 — КАЖДЫЙ СО СВОЕЙ МЕХАНИКОЙ И ИКОНКОЙ!) ---
        reg(ItemDef(201, "Пыль Редстоуна", isItem = true, colorHex = 0xEE2A36))
        reg(ItemDef(202, "Гаечный ключ", isItem = true, colorHex = 0xDC3844, damage = 5, maxDurability = 200))
        reg(ItemDef(203, "Железная мотыга", isItem = true, colorHex = 0xDCE6F2, damage = 4, maxDurability = 240))
        reg(ItemDef(204, "Лейка (Рост грядок)", isItem = true, colorHex = 0x4EA2E8, maxDurability = 180))
        reg(ItemDef(205, "Морковь", isItem = true, colorHex = 0xF27E18, healFood = 4, healHp = 2))
        reg(ItemDef(206, "Картофель", isItem = true, colorHex = 0xCE9E56, healFood = 2, healHp = 1))
        reg(ItemDef(207, "Печёный картофель", isItem = true, colorHex = 0xB0682A, healFood = 8, healHp = 4))
        reg(ItemDef(208, "Томат", isItem = true, colorHex = 0xE4242C, healFood = 4, healHp = 2))
        reg(ItemDef(209, "Кукуруза", isItem = true, colorHex = 0xF8CE2E, healFood = 3, healHp = 1))
        reg(ItemDef(210, "Кукуруза гриль", isItem = true, colorHex = 0xEC9E22, healFood = 8, healHp = 5))
        reg(ItemDef(211, "Долька арбуза", isItem = true, colorHex = 0xEE364A, healFood = 5, healHp = 3))
        reg(ItemDef(212, "Клубника", isItem = true, colorHex = 0xEE2248, healFood = 4, healHp = 3))
        reg(ItemDef(213, "Золотая морковь", isItem = true, colorHex = 0xF8C62A, healFood = 10, healHp = 8))
        reg(ItemDef(214, "Золотое яблоко", isItem = true, colorHex = 0xF8C426, healFood = 12, healHp = 14))
        reg(ItemDef(215, "Тыквенный суп", isItem = true, colorHex = 0xEC7C1A, healFood = 12, healHp = 7))
        reg(ItemDef(216, "Овощное рагу", isItem = true, colorHex = 0xC45C2A, healFood = 13, healHp = 8))
        reg(ItemDef(217, "Клубничный торт", isItem = true, colorHex = 0xEE3858, healFood = 16, healHp = 12))
        reg(ItemDef(218, "Сыр", isItem = true, colorHex = 0xF8CC2E, healFood = 7, healHp = 4))
        reg(ItemDef(219, "Сахар", isItem = true, colorHex = 0xF4F6FC, healFood = 2, healHp = 0))
        reg(ItemDef(220, "Баночка мёда", isItem = true, colorHex = 0xF6AE1C, healFood = 8, healHp = 6))
        reg(ItemDef(221, "Грибное ризотто", isItem = true, colorHex = 0xF6E8BA, healFood = 14, healHp = 9))
        reg(ItemDef(222, "Жареная рыба", isItem = true, colorHex = 0xCE7C30, healFood = 9, healHp = 6))
        reg(ItemDef(223, "Ягодный джем", isItem = true, colorHex = 0xD01C44, healFood = 6, healHp = 4))
        reg(ItemDef(224, "Медная кирка", isItem = true, colorHex = 0xDA7440, toolType = "pick", mineSpeed = 4.6f, damage = 5, maxDurability = 200))
        reg(ItemDef(225, "Медный Меч", isItem = true, colorHex = 0xDA7440, weaponStyle = "katana", damage = 8, maxDurability = 200))
        reg(ItemDef(226, "Медный шлем", isItem = true, colorHex = 0xD8723E, armorSlot = 0, armorDef = 3, maxDurability = 190))
        reg(ItemDef(227, "Медный нагрудник", isItem = true, colorHex = 0xD8723E, armorSlot = 1, armorDef = 4, maxDurability = 210))
        reg(ItemDef(228, "Медные поножи", isItem = true, colorHex = 0xD8723E, armorSlot = 2, armorDef = 3, maxDurability = 200))
        reg(ItemDef(229, "Медные ботинки", isItem = true, colorHex = 0xD8723E, armorSlot = 3, armorDef = 2, maxDurability = 180))
        reg(ItemDef(230, "Посох Аметиста", isItem = true, colorHex = 0xB242F8, weaponStyle = "staff_amethyst", damage = 14, maxDurability = 460))
        reg(ItemDef(231, "Огненный Посох", isItem = true, colorHex = 0xFF6C18, weaponStyle = "staff_fire", damage = 16, maxDurability = 480))
        reg(ItemDef(232, "Бумеранг", isItem = true, colorHex = 0xB07234, weaponStyle = "boomerang", damage = 10, maxDurability = 140))
        reg(ItemDef(233, "Сюрикен", isItem = true, colorHex = 0xBAC6D6, weaponStyle = "shuriken", damage = 9))
        reg(ItemDef(234, "Дымовая бомба", isItem = true, colorHex = 0x52586A))
        reg(ItemDef(235, "Жемчуг Телепорта", isItem = true, colorHex = 0x24BAB2, weaponStyle = "pearl", damage = 2))
        reg(ItemDef(236, "Амулет Магнита", isItem = true, colorHex = 0xE22A36))
        reg(ItemDef(237, "Каска Шахтёра (Свет)", isItem = true, colorHex = 0xF6C42A, armorSlot = 0, armorDef = 3, lightRadius = 310f, lightColor = 0xFFF29C, maxDurability = 340))
        reg(ItemDef(238, "Сапоги Скорохода", isItem = true, colorHex = 0x38D6FF, armorSlot = 3, armorDef = 3, maxDurability = 780))
        reg(ItemDef(239, "Аметистовый Меч", isItem = true, colorHex = 0xB648F8, weaponStyle = "katana", damage = 14, maxDurability = 480))
        reg(ItemDef(240, "Рубиновая Кирка", isItem = true, colorHex = 0xEA2648, toolType = "pick", mineSpeed = 10.2f, damage = 8, maxDurability = 1150))
        reg(ItemDef(241, "Бинт (+6 HP сразу)", isItem = true, colorHex = 0xF6F2EE))
        reg(ItemDef(242, "Мега-зелье HP (+14)", isItem = true, colorHex = 0xE82244))
        reg(ItemDef(243, "Эликсир Силы (+50%)", isItem = true, colorHex = 0xF6681C))
        reg(ItemDef(244, "Зелье Щита (+6 Бр.)", isItem = true, colorHex = 0x74C4F6))
        reg(ItemDef(245, "Компас (к Базе)", isItem = true, colorHex = 0xC49A44))
        reg(ItemDef(246, "Подзорная труба", isItem = true, colorHex = 0xD8843E))
        reg(ItemDef(247, "Удобрение (Рост)", isItem = true, colorHex = 0xF6F4EC))
        reg(ItemDef(248, "Светопыль", isItem = true, colorHex = 0xFFDE56))
        reg(ItemDef(249, "Свиток Дома (ТП)", isItem = true, colorHex = 0x9C5CEC))
        reg(ItemDef(250, "Часы Редстоуна", isItem = true, colorHex = 0xF2C230))
        reg(ItemDef(251, "Шоколадка (Скорость)", isItem = true, colorHex = 0x6C3A20, healFood = 5, healHp = 3))
        reg(ItemDef(252, "Яблочный пирог", isItem = true, colorHex = 0xDA9A44, healFood = 13, healHp = 9))
        reg(ItemDef(253, "Шашлычок на шпажке", isItem = true, colorHex = 0xA24826, healFood = 14, healHp = 10))
        reg(ItemDef(254, "Изумрудный Меч (Лут)", isItem = true, colorHex = 0x2EE270, weaponStyle = "emerald", damage = 15, maxDurability = 520))
        reg(ItemDef(255, "Обсидиановый Щит", isItem = true, colorHex = 0x9C42F8, armorDef = 7, maxDurability = 1200))
        reg(ItemDef(256, "Вагонетка (Рельсы)", isItem = true, colorHex = 0x8C94A6))
        reg(ItemDef(257, "Лодка (По воде)", isItem = true, colorHex = 0xA46C36))
        reg(ItemDef(258, "Манок Совы (Разведка)", isItem = true, colorHex = 0xE4D4BC))
        reg(ItemDef(259, "Ядро Голема-сундука", isItem = true, colorHex = 0x8E96A6))
        reg(ItemDef(260, "Крылья Небес (Полёт)", isItem = true, colorHex = 0x8CE2FF))

        // =========================================================================
        // РЕЦЕПТЫ: Карман 2x2 + Верстак 3x3 (4 категории: tool, build, redstone, food) + Костёр!
        // =========================================================================
        // Карманный крафт (2x2)
        recipes += RecipeDef(7, 4, listOf(5 to 1), "Дуб -> 4 Доски", pocket2x2 = true, category = "build")
        recipes += RecipeDef(175, 4, listOf(27 to 1), "Берёза -> 4 Берёз.доски", pocket2x2 = true, category = "build")
        recipes += RecipeDef(176, 4, listOf(45 to 1), "Сакура -> 4 Доски сакуры", pocket2x2 = true, category = "build")
        recipes += RecipeDef(174, 4, listOf(46 to 1), "Ель -> 4 Тёмные доски", pocket2x2 = true, category = "build")
        recipes += RecipeDef(105, 4, listOf(7 to 2), "2 Доски -> 4 Палки", pocket2x2 = true, category = "tool")
        recipes += RecipeDef(17, 1, listOf(7 to 4), "4 Доски -> Верстак", pocket2x2 = true, category = "build")
        recipes += RecipeDef(37, 4, listOf(101 to 1, 105 to 1), "Уголь + Палка -> 4 Факела", pocket2x2 = true, category = "build")
        recipes += RecipeDef(56, 4, listOf(105 to 6), "6 Палок -> 4 Лестницы", pocket2x2 = true, category = "build")
        recipes += RecipeDef(59, 1, listOf(5 to 2, 101 to 1), "2 Бревна + Уголь -> Костёр", pocket2x2 = true, category = "build")

        // ИНСТРУМЕНТЫ, ОРУЖИЕ, БРОНЯ И ГАДЖЕТЫ ("tool")
        recipes += RecipeDef(107, 1, listOf(4 to 3, 105 to 2), "Каменная кирка", category = "tool")
        recipes += RecipeDef(224, 1, listOf(140 to 3, 105 to 2), "Медная кирка", category = "tool")
        recipes += RecipeDef(108, 1, listOf(102 to 3, 105 to 2), "Железная кирка", category = "tool")
        recipes += RecipeDef(109, 1, listOf(104 to 3, 105 to 2), "Алмазная кирка", category = "tool")
        recipes += RecipeDef(240, 1, listOf(142 to 3, 105 to 2), "Рубиновая кирка", category = "tool")
        recipes += RecipeDef(203, 1, listOf(102 to 2, 105 to 2), "Железная мотыга (Пашня)", category = "tool")
        recipes += RecipeDef(204, 1, listOf(140 to 3, 119 to 1), "Лейка (Рост грядок)", category = "tool")
        recipes += RecipeDef(150, 1, listOf(105 to 3, 160 to 2), "Удочка", category = "tool")
        recipes += RecipeDef(116, 1, listOf(102 to 1, 101 to 1), "Огниво", category = "tool")
        recipes += RecipeDef(120, 1, listOf(102 to 3), "Пустое ведро", category = "tool")
        recipes += RecipeDef(225, 1, listOf(140 to 2, 105 to 1), "Медный Меч", category = "tool")
        recipes += RecipeDef(111, 1, listOf(102 to 2, 105 to 1), "Железный Меч", category = "tool")
        recipes += RecipeDef(239, 1, listOf(141 to 2, 105 to 1), "Аметистовый Меч", category = "tool")
        recipes += RecipeDef(254, 1, listOf(118 to 2, 105 to 1), "Изумрудный Меч (Лут)", category = "tool")
        recipes += RecipeDef(112, 1, listOf(104 to 2, 105 to 1), "Алмазный Меч", category = "tool")
        recipes += RecipeDef(139, 1, listOf(142 to 2, 105 to 1), "Рубиновый Клинок", category = "tool")
        recipes += RecipeDef(130, 1, listOf(102 to 5, 105 to 2), "Боевой Молот (AoE)", category = "tool")
        recipes += RecipeDef(123, 1, listOf(105 to 3, 160 to 2), "Лук", category = "tool")
        recipes += RecipeDef(124, 8, listOf(105 to 1, 4 to 1), "8 Стрел", category = "tool")
        recipes += RecipeDef(230, 1, listOf(141 to 3, 105 to 2), "Посох Аметиста", category = "tool")
        recipes += RecipeDef(231, 1, listOf(142 to 2, 101 to 3, 105 to 2), "Огненный Посох", category = "tool")
        recipes += RecipeDef(232, 1, listOf(7 to 3, 140 to 2), "Бумеранг", category = "tool")
        recipes += RecipeDef(233, 6, listOf(102 to 2), "6 Сюрикенов", category = "tool")
        recipes += RecipeDef(234, 3, listOf(159 to 2, 101 to 1), "3 Дымовые бомбы", category = "tool")
        recipes += RecipeDef(235, 2, listOf(141 to 2, 147 to 2), "2 Жемчуга Телепорта", category = "tool")
        recipes += RecipeDef(236, 1, listOf(102 to 3, 201 to 2), "Амулет Магнита", category = "tool")
        recipes += RecipeDef(245, 1, listOf(102 to 2, 201 to 1), "Компас", category = "tool")
        recipes += RecipeDef(246, 1, listOf(140 to 2, 15 to 1), "Подзорная труба", category = "tool")
        recipes += RecipeDef(249, 2, listOf(141 to 1, 201 to 2, 160 to 1), "2 Свитка Дома (ТП)", category = "tool")
        recipes += RecipeDef(132, 1, listOf(7 to 6, 102 to 1), "Щит (Парирование)", category = "tool")
        recipes += RecipeDef(255, 1, listOf(22 to 4, 141 to 2), "Обсидиановый Щит", category = "tool")
        recipes += RecipeDef(125, 1, listOf(158 to 3), "Кожаный шлем", category = "tool")
        recipes += RecipeDef(237, 1, listOf(103 to 2, 60 to 1), "Каска Шахтёра (Свет)", category = "tool")
        recipes += RecipeDef(226, 1, listOf(140 to 4), "Медный шлем", category = "tool")
        recipes += RecipeDef(227, 1, listOf(140 to 6), "Медный нагрудник", category = "tool")
        recipes += RecipeDef(228, 1, listOf(140 to 5), "Медные поножи", category = "tool")
        recipes += RecipeDef(229, 1, listOf(140 to 4), "Медные ботинки", category = "tool")
        recipes += RecipeDef(148, 1, listOf(158 to 4, 7 to 2), "Тёплая шуба", category = "tool")
        recipes += RecipeDef(126, 1, listOf(102 to 6), "Железный нагрудник", category = "tool")
        recipes += RecipeDef(127, 1, listOf(102 to 5), "Железные поножи", category = "tool")
        recipes += RecipeDef(128, 1, listOf(104 to 4), "Алмазные ботинки", category = "tool")
        recipes += RecipeDef(238, 1, listOf(128 to 1, 138 to 1), "Сапоги Скорохода", category = "tool")
        recipes += RecipeDef(149, 1, listOf(142 to 4, 102 to 2), "Рубиновый Нагрудник", category = "tool")

        // РЕДСТОУН И МЕХАНИЗМЫ ("redstone")
        recipes += RecipeDef(76, 1, listOf(201 to 6), "Блок Редстоуна (Источник)", category = "redstone")
        recipes += RecipeDef(86, 2, listOf(201 to 1, 105 to 1), "2 Редстоун Факела", category = "redstone")
        recipes += RecipeDef(81, 2, listOf(4 to 1, 105 to 1), "2 Рычага", category = "redstone")
        recipes += RecipeDef(83, 2, listOf(3 to 2, 201 to 1), "2 Нажимные плиты", category = "redstone")
        recipes += RecipeDef(85, 2, listOf(3 to 1, 201 to 1), "2 Кнопки Редстоуна", category = "redstone")
        recipes += RecipeDef(87, 1, listOf(3 to 3, 201 to 2, 86 to 1), "Повторитель сигнала", category = "redstone")
        recipes += RecipeDef(77, 2, listOf(248 to 2, 201 to 2, 15 to 2), "2 Редстоун Лампы", category = "redstone")
        recipes += RecipeDef(88, 1, listOf(7 to 3, 4 to 4, 102 to 1, 201 to 1), "Поршень", category = "redstone")
        recipes += RecipeDef(90, 1, listOf(4 to 6, 123 to 1, 201 to 1), "Раздатчик стрел", category = "redstone")
        recipes += RecipeDef(91, 1, listOf(7 to 6, 201 to 1), "Нотный блок", category = "redstone")
        recipes += RecipeDef(172, 1, listOf(102 to 6), "Железная дверь (Редстоун)", category = "redstone")
        recipes += RecipeDef(202, 1, listOf(102 to 2, 201 to 1), "Гаечный ключ (Тестер)", category = "redstone")
        recipes += RecipeDef(250, 1, listOf(103 to 2, 201 to 1), "Часы Редстоуна", category = "redstone")
        recipes += RecipeDef(72, 2, listOf(102 to 4, 201 to 2), "2 Шахтных Лифта", category = "redstone")
        recipes += RecipeDef(73, 2, listOf(102 to 3), "2 Шипы-ловушки", category = "redstone")
        recipes += RecipeDef(74, 1, listOf(123 to 1, 102 to 3, 7 to 4), "Авто-арбалет", category = "redstone")
        recipes += RecipeDef(26, 1, listOf(159 to 4, 8 to 2), "Динамит (TNT)", category = "redstone")
        recipes += RecipeDef(182, 1, listOf(102 to 5, 35 to 1), "Воронка (Авто-сбор лута)", category = "redstone")
        recipes += RecipeDef(183, 1, listOf(15 to 3, 117 to 3, 7 to 3), "Датчик света (Ночь->Лампы)", category = "redstone")
        recipes += RecipeDef(185, 8, listOf(102 to 4, 105 to 2), "8 Рельсов для шахты", category = "redstone")
        recipes += RecipeDef(186, 6, listOf(103 to 4, 201 to 2, 105 to 1), "6 Энергорельсов (Ускор.)", category = "redstone")
        recipes += RecipeDef(256, 1, listOf(102 to 5), "Вагонетка (Езда по рельсам)", category = "redstone")

        // СТРОЙКА И ДЕКОР ("build")
        recipes += RecipeDef(18, 1, listOf(4 to 8), "Печь", category = "build")
        recipes += RecipeDef(35, 1, listOf(7 to 8), "Сундук", category = "build")
        recipes += RecipeDef(57, 2, listOf(7 to 6), "2 Двери", category = "build")
        recipes += RecipeDef(60, 2, listOf(37 to 1, 102 to 1), "2 Фонаря", category = "build")
        recipes += RecipeDef(61, 1, listOf(51 to 4, 141 to 2), "Камень Телепорта", category = "build")
        recipes += RecipeDef(63, 1, listOf(102 to 6), "Наковальня", category = "build")
        recipes += RecipeDef(65, 2, listOf(7 to 2, 105 to 1), "2 Таблички", category = "build")
        recipes += RecipeDef(38, 1, listOf(7 to 3, 158 to 2), "Кровать", category = "build")
        recipes += RecipeDef(51, 4, listOf(4 to 4), "4 Каменных кирпича", category = "build")
        recipes += RecipeDef(163, 4, listOf(51 to 4, 6 to 1), "4 Замш. Кам.кирпича", category = "build")
        recipes += RecipeDef(162, 4, listOf(161 to 4), "4 Полир. Мрамора", category = "build")
        recipes += RecipeDef(164, 1, listOf(140 to 4), "Медный блок", category = "build")
        recipes += RecipeDef(165, 1, listOf(103 to 4), "Золотой блок", category = "build")
        recipes += RecipeDef(166, 1, listOf(104 to 4), "Алмазный блок", category = "build")
        recipes += RecipeDef(167, 4, listOf(22 to 4), "4 Обсид. кирпича", category = "build")
        recipes += RecipeDef(168, 4, listOf(15 to 4, 141 to 1), "4 Синего стекла", category = "build")
        recipes += RecipeDef(169, 4, listOf(15 to 4, 118 to 1), "4 Зелёного стекла", category = "build")
        recipes += RecipeDef(170, 4, listOf(15 to 4, 103 to 1), "4 Янтарного стекла", category = "build")
        recipes += RecipeDef(171, 1, listOf(143 to 6), "Сноп сена", category = "build")
        recipes += RecipeDef(178, 4, listOf(28 to 4), "4 Резных песчаника", category = "build")
        recipes += RecipeDef(179, 2, listOf(248 to 4, 15 to 2), "2 Свет. кристалла", category = "build")
        recipes += RecipeDef(52, 1, listOf(7 to 6, 140 to 1), "Бочка", category = "build")
        recipes += RecipeDef(64, 4, listOf(7 to 4, 105 to 2), "4 Забора", category = "build")
        recipes += RecipeDef(34, 1, listOf(22 to 4, 116 to 1), "Портал в Ад", category = "build")
        recipes += RecipeDef(187, 1, listOf(22 to 4, 104 to 2, 141 to 2), "Стол Зачарований", category = "build")
        recipes += RecipeDef(257, 1, listOf(7 to 5), "Лодка (Быстро по воде)", category = "tool")
        recipes += RecipeDef(258, 1, listOf(103 to 2, 141 to 1, 158 to 1), "Манок Совы (Питомец)", category = "tool")
        recipes += RecipeDef(259, 1, listOf(35 to 1, 102 to 4, 201 to 2), "Голем-носильщик (Сундук)", category = "tool")
        recipes += RecipeDef(260, 1, listOf(104 to 2, 141 to 3, 158 to 2), "Крылья Небес (Косметика+)", category = "tool")

        // ЕДА, МЕДИЦИНА И ФЕРМА ("food" + Костёр!)
        recipes += RecipeDef(114, 2, listOf(143 to 3), "2 Хлеба", category = "food")
        recipes += RecipeDef(219, 3, listOf(211 to 1), "3 Сахара из арбуза", category = "food")
        recipes += RecipeDef(218, 2, listOf(136 to 1), "2 Сыра из молока", category = "food")
        recipes += RecipeDef(247, 4, listOf(101 to 1, 2 to 2), "4 Удобрения", category = "food")
        recipes += RecipeDef(241, 2, listOf(160 to 3), "2 Бинта (+6 HP сразу)", category = "food")
        recipes += RecipeDef(213, 1, listOf(205 to 1, 103 to 2), "Золотая морковь", category = "food")
        recipes += RecipeDef(214, 1, listOf(113 to 1, 103 to 3), "Золотое яблоко", category = "food")
        recipes += RecipeDef(251, 2, listOf(219 to 1, 144 to 2), "2 Шоколадки", category = "food")
        recipes += RecipeDef(217, 1, listOf(143 to 2, 219 to 2, 212 to 3), "Клубничный торт", category = "food")
        recipes += RecipeDef(252, 1, listOf(143 to 2, 219 to 1, 113 to 3), "Яблочный пирог", category = "food")
        recipes += RecipeDef(223, 2, listOf(144 to 3, 219 to 1), "2 Ягодных джема", category = "food")

        // Костёр (Готовка и Эликсиры!)
        recipes += RecipeDef(115, 1, listOf(157 to 1), "Жареный стейк", campfireBrew = true, category = "food")
        recipes += RecipeDef(207, 2, listOf(206 to 2), "2 Печёных картофеля", campfireBrew = true, category = "food")
        recipes += RecipeDef(210, 2, listOf(209 to 2), "2 Кукурузы гриль", campfireBrew = true, category = "food")
        recipes += RecipeDef(222, 1, listOf(151 to 1), "Жареная рыба", campfireBrew = true, category = "food")
        recipes += RecipeDef(146, 2, listOf(145 to 2), "2 Жареных гриба", campfireBrew = true, category = "food")
        recipes += RecipeDef(215, 1, listOf(53 to 1, 205 to 1), "Тыквенный суп", campfireBrew = true, category = "food")
        recipes += RecipeDef(216, 1, listOf(205 to 1, 206 to 1, 208 to 1), "Овощное рагу", campfireBrew = true, category = "food")
        recipes += RecipeDef(221, 1, listOf(145 to 2, 218 to 1, 143 to 1), "Грибное ризотто", campfireBrew = true, category = "food")
        recipes += RecipeDef(253, 2, listOf(157 to 1, 208 to 1, 105 to 1), "2 Шашлычка", campfireBrew = true, category = "food")
        recipes += RecipeDef(155, 1, listOf(151 to 1, 145 to 1), "Горячая Уха", campfireBrew = true, category = "food")
        recipes += RecipeDef(156, 1, listOf(143 to 2, 144 to 3), "Ягодный пирог", campfireBrew = true, category = "food")
        recipes += RecipeDef(136, 2, listOf(144 to 2, 113 to 1), "2 Согревающих чая", campfireBrew = true, category = "food")
        recipes += RecipeDef(137, 1, listOf(145 to 2, 141 to 1), "Отвар Шахтёра (Руды)", campfireBrew = true, category = "food")
        recipes += RecipeDef(138, 1, listOf(144 to 2, 147 to 1), "Эликсир Скорости", campfireBrew = true, category = "food")
        recipes += RecipeDef(242, 1, listOf(212 to 2, 142 to 1), "Мега-зелье HP (+14)", campfireBrew = true, category = "food")
        recipes += RecipeDef(243, 1, listOf(201 to 2, 157 to 1), "Эликсир Силы (+50%)", campfireBrew = true, category = "food")
        recipes += RecipeDef(244, 1, listOf(141 to 1, 102 to 1), "Зелье Щита (+6 Бр.)", campfireBrew = true, category = "food")

        // Переплавка в Печи
        smeltMap[12] = 102; smeltMap[13] = 103; smeltMap[48] = 140; smeltMap[75] = 201; smeltMap[177] = 103
        smeltMap[8] = 15; smeltMap[4] = 3; smeltMap[47] = 16
        smeltMap[157] = 115; smeltMap[145] = 146; smeltMap[206] = 207; smeltMap[209] = 210
        smeltMap[151] = 222; smeltMap[152] = 222; smeltMap[153] = 222

        baseRecipeCount = recipes.size
        buildAtlas()
        buildItemIcons()
    }

    fun tileUV(tid: Int): FloatArray {
        val col = tid % ATLAS_COLS; val row = tid / ATLAS_COLS
        val su = 1f / ATLAS_COLS; val sv = 1f / ATLAS_ROWS; val eps = 0.0015f
        return floatArrayOf(col * su + eps, row * sv + eps, (col + 1) * su - eps, (row + 1) * sv - eps)
    }

    fun getIconRegion(id: Int): TextureRegion? = iconRegions[id]

    private fun buildAtlas() {
        atlasPixmap = Pixmap(ATLAS_COLS * TILE_PX, ATLAS_ROWS * TILE_PX, Pixmap.Format.RGBA8888)
        atlasPixmap.blending = Pixmap.Blending.None
        atlasPixmap.setColor(0.45f, 0.45f, 0.48f, 1f); atlasPixmap.fill()

        fun blitSheet(filename: String, offsetTile: Int) {
            val f = Gdx.files.internal(filename)
            if (!f.exists()) return
            val src = Pixmap(f)
            val sw = src.width / 4; val sh = src.height / 4
            for (r in 0 until 4) for (c in 0 until 4) {
                val tid = offsetTile + r * 4 + c
                atlasPixmap.drawPixmap(src, c * sw, r * sh, sw, sh, (tid % ATLAS_COLS) * TILE_PX, (tid / ATLAS_COLS) * TILE_PX, TILE_PX, TILE_PX)
            }
            src.dispose()
        }

        blitSheet("anime_tiles.png", 0)
        blitSheet("anime_tiles_2.png", 16)
        blitSheet("anime_tiles_3.png", 32)
        blitSheet("anime_tiles_4.png", 52)
        blitSheet("anime_tiles_5.png", 68)

        // Derived tiles 48..51
        val dx48 = (48 % ATLAS_COLS) * TILE_PX; val dy48 = (48 / ATLAS_COLS) * TILE_PX
        atlasPixmap.drawPixmap(atlasPixmap, (2 % ATLAS_COLS) * TILE_PX, (2 / ATLAS_COLS) * TILE_PX, TILE_PX, TILE_PX, dx48, dy48, TILE_PX, TILE_PX)
        atlasPixmap.drawPixmap(atlasPixmap, (26 % ATLAS_COLS) * TILE_PX, (26 / ATLAS_COLS) * TILE_PX, TILE_PX, 18, dx48, dy48, TILE_PX, 18)

        val dx49 = (49 % ATLAS_COLS) * TILE_PX; val dy49 = (49 / ATLAS_COLS) * TILE_PX
        atlasPixmap.drawPixmap(atlasPixmap, (8 % ATLAS_COLS) * TILE_PX, (8 / ATLAS_COLS) * TILE_PX, TILE_PX, TILE_PX, dx49, dy49, TILE_PX, TILE_PX)
        atlasPixmap.blending = Pixmap.Blending.SourceOver
        atlasPixmap.setColor(0.20f, 0.04f, 0.08f, 0.58f); atlasPixmap.fillRectangle(dx49, dy49, TILE_PX, TILE_PX)

        atlasPixmap.blending = Pixmap.Blending.None
        val dx50 = (50 % ATLAS_COLS) * TILE_PX; val dy50 = (50 / ATLAS_COLS) * TILE_PX
        atlasPixmap.drawPixmap(atlasPixmap, (24 % ATLAS_COLS) * TILE_PX, (24 / ATLAS_COLS) * TILE_PX, TILE_PX, TILE_PX, dx50, dy50, TILE_PX, TILE_PX)
        atlasPixmap.blending = Pixmap.Blending.SourceOver
        atlasPixmap.setColor(0.22f, 0.14f, 0.09f, 0.65f); atlasPixmap.fillRectangle(dx50, dy50, TILE_PX, TILE_PX)

        atlasPixmap.blending = Pixmap.Blending.None
        val dx51 = (51 % ATLAS_COLS) * TILE_PX; val dy51 = (51 / ATLAS_COLS) * TILE_PX
        atlasPixmap.drawPixmap(atlasPixmap, (16 % ATLAS_COLS) * TILE_PX, (16 / ATLAS_COLS) * TILE_PX, TILE_PX, TILE_PX, dx51, dy51, TILE_PX, TILE_PX)
        atlasPixmap.setColor(0.26f, 0.28f, 0.32f, 1f); atlasPixmap.drawRectangle(dx51 + 2, dy51 + 2, TILE_PX - 4, TILE_PX - 4)

        // Slot 127 (col 15, row 7): dedicated white tile for Rust cutaway caps!
        atlasPixmap.blending = Pixmap.Blending.None
        atlasPixmap.setColor(Color.WHITE)
        atlasPixmap.fillRectangle(15 * TILE_PX, 7 * TILE_PX, TILE_PX, TILE_PX)

        atlasTexture = Texture(atlasPixmap)
        atlasTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
    }

    private fun buildItemIcons() {
        for ((id, def) in items) {
            if (!def.isItem && !def.isProp && def.sideTile >= 0 && id > 0) {
                iconRegions[id] = TextureRegion(atlasTexture, (def.sideTile % ATLAS_COLS) * TILE_PX, (def.sideTile / ATLAS_COLS) * TILE_PX, TILE_PX, TILE_PX)
            }
        }
        iconRegions[35] = TextureRegion(atlasTexture, (14 % ATLAS_COLS) * TILE_PX, (14 / ATLAS_COLS) * TILE_PX, TILE_PX, TILE_PX)
        iconRegions[17] = TextureRegion(atlasTexture, (13 % ATLAS_COLS) * TILE_PX, (13 / ATLAS_COLS) * TILE_PX, TILE_PX, TILE_PX)

        // Sheet 1: 16 unique base icons (NO duplicates!)
        val f1 = Gdx.files.internal("anime_items.png")
        if (f1.exists()) {
            val tex = Texture(f1).apply { setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest) }
            itemsTexture1 = tex
            val cw = tex.width / 4; val ch = tex.height / 4
            fun c1(c: Int, r: Int) = TextureRegion(tex, c * cw, r * ch, cw, ch)
            iconRegions[112] = c1(0, 0) // Алмазный Меч
            iconRegions[111] = c1(1, 0) // Железный Меч
            iconRegions[109] = c1(2, 0) // Алмазная Кирка
            iconRegions[110] = c1(3, 0) // Боевой Топор
            iconRegions[123] = c1(0, 1) // Лук
            iconRegions[104] = c1(1, 1) // Алмаз
            iconRegions[118] = c1(2, 1) // Изумруд
            iconRegions[103] = c1(3, 1) // Золотой слиток
            iconRegions[102] = c1(0, 2) // Железный слиток
            iconRegions[101] = c1(1, 2) // Уголь
            iconRegions[113] = c1(2, 2) // Яблоко
            iconRegions[115] = c1(3, 2) // Жареный стейк
            iconRegions[114] = c1(0, 3) // Хлеб
            iconRegions[37]  = c1(1, 3) // Факел
            iconRegions[119] = c1(2, 3) // Ведро воды
            iconRegions[126] = c1(3, 3) // Железный нагрудник
        }

        // Sheet 2: 16 unique RPG icons (NO duplicates!)
        val f2 = Gdx.files.internal("anime_items_2.png")
        if (f2.exists()) {
            val tex = Texture(f2).apply { setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest) }
            itemsTexture2 = tex
            val cw = tex.width / 4; val ch = tex.height / 4
            fun c2(c: Int, r: Int) = TextureRegion(tex, c * cw, r * ch, cw, ch)
            iconRegions[133] = c2(0, 0); iconRegions[134] = c2(1, 0); iconRegions[135] = c2(2, 0); iconRegions[136] = c2(3, 0)
            iconRegions[137] = c2(0, 1); iconRegions[138] = c2(1, 1); iconRegions[140] = c2(2, 1); iconRegions[141] = c2(3, 1)
            iconRegions[142] = c2(0, 2); iconRegions[143] = c2(1, 2); iconRegions[144] = c2(2, 2); iconRegions[145] = c2(3, 2)
            iconRegions[146] = c2(0, 3); iconRegions[147] = c2(1, 3); iconRegions[148] = c2(2, 3); iconRegions[132] = c2(3, 3)
        }

        // Sheet 3: 11 fishing & balanced mob drop icons
        val f3 = Gdx.files.internal("anime_items_3.png")
        if (f3.exists()) {
            val tex = Texture(f3).apply { setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest) }
            itemsTexture3 = tex
            val cw = tex.width / 4; val ch = tex.height / 4
            fun c3(c: Int, r: Int) = TextureRegion(tex, c * cw, r * ch, cw, ch)
            iconRegions[150] = c3(0, 0); iconRegions[151] = c3(1, 0); iconRegions[152] = c3(2, 0); iconRegions[153] = c3(3, 0)
            iconRegions[154] = c3(0, 1); iconRegions[155] = c3(1, 1); iconRegions[156] = c3(2, 1)
            iconRegions[157] = c3(0, 2); iconRegions[158] = c3(1, 2); iconRegions[159] = c3(2, 2); iconRegions[160] = c3(3, 2)
        }

        // Sheet 4 (512x512 = 8x8): 16 deduplicated icons + 48 brand-new unique icons!
        val f4 = Gdx.files.internal("anime_items_4.png")
        if (f4.exists()) {
            val tex = Texture(f4).apply { setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest) }
            itemsTexture4 = tex
            val cw = tex.width / 8; val ch = tex.height / 8
            fun c4(idx: Int) = TextureRegion(tex, (idx % 8) * cw, (idx / 8) * ch, cw, ch)
            val dedupIds = intArrayOf(106, 107, 108, 131, 130, 124, 129, 117, 105, 116, 120, 125, 127, 128, 139, 149)
            dedupIds.forEachIndexed { i, id -> iconRegions[id] = c4(i) }
            for (k in 0 until 48) iconRegions[201 + k] = c4(16 + k)
        }

        // Sheet 5 (256x128 = 4x2): 7 extra unique icons (249..255)!
        val f5 = Gdx.files.internal("anime_items_5.png")
        if (f5.exists()) {
            val tex = Texture(f5).apply { setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest) }
            itemsTexture5 = tex
            val cw = tex.width / 4; val ch = tex.height / 4
            fun c5(idx: Int) = TextureRegion(tex, (idx % 4) * cw, (idx / 4) * ch, cw, ch)
            for (k in 0 until 12) iconRegions[249 + k] = c5(k)
        }
    }

    fun attachPropIcons(propsTex: Texture) {
        fun pIcon(idx: Int) = TextureRegion(propsTex, (idx % 8) * 64, (idx / 8) * 64, 64, 64)
        iconRegions[38]  = pIcon(4);  iconRegions[34]  = pIcon(5);  iconRegions[56]  = pIcon(8)
        iconRegions[57]  = pIcon(9);  iconRegions[59]  = pIcon(11); iconRegions[60]  = pIcon(13)
        iconRegions[61]  = pIcon(14); iconRegions[62]  = pIcon(15); iconRegions[63]  = pIcon(16)
        iconRegions[64]  = pIcon(17); iconRegions[65]  = pIcon(18); iconRegions[66]  = pIcon(19)
        iconRegions[67]  = pIcon(20); iconRegions[68]  = pIcon(21); iconRegions[69]  = pIcon(22)
        iconRegions[70]  = pIcon(23); iconRegions[72]  = pIcon(24); iconRegions[73]  = pIcon(25)
        iconRegions[74]  = pIcon(26); iconRegions[79]  = pIcon(27); iconRegions[80]  = pIcon(28)
        iconRegions[81]  = pIcon(30); iconRegions[82]  = pIcon(31); iconRegions[83]  = pIcon(32)
        iconRegions[84]  = pIcon(33); iconRegions[85]  = pIcon(34); iconRegions[86]  = pIcon(36)
        iconRegions[87]  = pIcon(38); iconRegions[172] = pIcon(40); iconRegions[173] = pIcon(41)
        iconRegions[89]  = pIcon(42); iconRegions[180] = pIcon(43); iconRegions[181] = pIcon(45)
        iconRegions[182] = pIcon(47); iconRegions[183] = pIcon(48); iconRegions[184] = pIcon(49)
        iconRegions[185] = pIcon(50); iconRegions[186] = pIcon(52); iconRegions[187] = pIcon(53)
        iconRegions[93]  = pIcon(55); iconRegions[94]  = pIcon(56); iconRegions[95]  = pIcon(57)
        iconRegions[96]  = pIcon(58); iconRegions[98]  = pIcon(59)
    }

    fun clearCustomRecipes() {
        while (recipes.size > baseRecipeCount) recipes.removeAt(recipes.lastIndex)
    }

    fun registerCustomBlock8x8(
        id: Int, name: String, pixels8x8: IntArray,
        solid: Boolean = true, lightRadius: Float = 0f, lightColor: Int = 0xFFE688,
        hardness: Float = 1.0f, dropId: Int = id
    ): Int {
        val tid = customBlockTiles.getOrPut(id) {
            if (nextModTile < ATLAS_COLS * ATLAS_ROWS - 1) nextModTile++ else 84 + (id % 36)
        }
        val tx = (tid % ATLAS_COLS) * TILE_PX
        val ty = (tid / ATLAS_COLS) * TILE_PX
        val cell = TILE_PX / 8
        atlasPixmap.blending = Pixmap.Blending.None
        var avgR = 0L; var avgG = 0L; var avgB = 0L
        for (py in 0 until 8) for (px in 0 until 8) {
            val hex = pixels8x8[py * 8 + px]
            val r = (hex shr 16) and 0xFF; val g = (hex shr 8) and 0xFF; val b = hex and 0xFF
            avgR += r; avgG += g; avgB += b
            atlasPixmap.setColor(r / 255f, g / 255f, b / 255f, 1f)
            atlasPixmap.fillRectangle(tx + px * cell, ty + py * cell, cell, cell)
        }
        val avgHex = (((avgR / 64).toInt() and 0xFF) shl 16) or (((avgG / 64).toInt() and 0xFF) shl 8) or ((avgB / 64).toInt() and 0xFF)
        Gdx.app.postRunnable { if (::atlasTexture.isInitialized) atlasTexture.draw(atlasPixmap, 0, 0) }
        items[id] = ItemDef(
            id = id, name = name, solid = solid, transparent = !solid,
            lightRadius = lightRadius, lightColor = lightColor,
            hardness = hardness, dropId = if (dropId > 0) dropId else id,
            toolType = "pick", topTile = tid, sideTile = tid, colorHex = avgHex
        )
        iconRegions[id] = TextureRegion(atlasTexture, tx, ty, TILE_PX, TILE_PX)
        RustEngine.nativeRegisterModBlock(id, tid, avgHex)
        return tid
    }

    fun registerCustomItem8x8(
        id: Int, name: String, pixels8x8: IntArray,
        kind: String, damage: Int, mineSpeed: Float, durability: Int,
        healHp: Int, healFood: Int, aoeMine: Boolean
    ) {
        val pm = Pixmap(64, 64, Pixmap.Format.RGBA8888)
        pm.blending = Pixmap.Blending.None
        pm.setColor(0f, 0f, 0f, 0f); pm.fill()
        var avgR = 0L; var avgG = 0L; var avgB = 0L; var cnt = 0
        for (py in 0 until 8) for (px in 0 until 8) {
            val hex = pixels8x8[py * 8 + px]
            if (hex < 0) continue // transparent pixel!
            val r = (hex shr 16) and 0xFF; val g = (hex shr 8) and 0xFF; val b = hex and 0xFF
            avgR += r; avgG += g; avgB += b; cnt++
            pm.setColor(r / 255f, g / 255f, b / 255f, 1f)
            pm.fillRectangle(px * 8, py * 8, 8, 8)
        }
        val avgHex = if (cnt > 0) ((((avgR / cnt).toInt() and 0xFF) shl 16) or (((avgG / cnt).toInt() and 0xFF) shl 8) or ((avgB / cnt).toInt() and 0xFF)) else 0xFFFFFF
        val toolType = if (kind == "pick") "pick" else ""
        val weaponStyle = when (kind) {
            "sword" -> "katana"
            "staff" -> "staff_amethyst"
            "fire_staff" -> "staff_fire"
            else -> ""
        }
        items[id] = ItemDef(
            id = id, name = name, isItem = true, colorHex = avgHex,
            toolType = toolType, weaponStyle = weaponStyle,
            damage = damage, mineSpeed = mineSpeed, aoeMine = aoeMine,
            healFood = healFood, healHp = healHp, maxDurability = durability
        )
        Gdx.app.postRunnable {
            customItemTextures.remove(id)?.dispose()
            val t = Texture(pm).apply { setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest) }
            pm.dispose()
            customItemTextures[id] = t
            iconRegions[id] = TextureRegion(t)
        }
    }

    fun registerModBlock(id: Int, name: String, r: Float, g: Float, b: Float): Int {
        val tid = if (nextModTile < ATLAS_COLS * ATLAS_ROWS - 1) nextModTile++ else 16
        val tx = (tid % ATLAS_COLS) * TILE_PX; val ty = (tid / ATLAS_COLS) * TILE_PX
        val col = Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), 1f)
        atlasPixmap.setColor(col); atlasPixmap.fillRectangle(tx, ty, TILE_PX, TILE_PX)
        atlasPixmap.setColor(col.cpy().mul(0.72f)); atlasPixmap.drawRectangle(tx, ty, TILE_PX, TILE_PX)
        Gdx.app.postRunnable { atlasTexture.draw(atlasPixmap, 0, 0) }
        val hex = ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
        items[id] = ItemDef(id, name, solid = true, hardness = 1.0f, dropId = id, topTile = tid, sideTile = tid, colorHex = hex)
        iconRegions[id] = TextureRegion(atlasTexture, tx, ty, TILE_PX, TILE_PX)
        RustEngine.nativeRegisterModBlock(id, tid, hex)
        return tid
    }

    fun dispose() {
        if (::atlasTexture.isInitialized) atlasTexture.dispose()
        if (::atlasPixmap.isInitialized) atlasPixmap.dispose()
        itemsTexture1?.dispose(); itemsTexture2?.dispose(); itemsTexture3?.dispose()
        itemsTexture4?.dispose(); itemsTexture5?.dispose()
        for (t in customItemTextures.values) t.dispose(); customItemTextures.clear()
    }
}
