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
    val interactive: String = ""
)

data class RecipeDef(
    val outId: Int,
    val outCount: Int,
    val req: List<Pair<Int, Int>>,
    val desc: String,
    val pocket2x2: Boolean = false,
    val campfireBrew: Boolean = false
)

object BlockRegistry {
    const val ATLAS_COLS = 8
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
    val iconRegions = ConcurrentHashMap<Int, TextureRegion>()
    private var nextModTile = 52

    fun init() {
        items.clear(); recipes.clear(); smeltMap.clear(); iconRegions.clear()
        fun reg(d: ItemDef) { items[d.id] = d }

        // Tile map in 8x8 atlas:
        // 0..15:  anime_tiles.png (0 grass_top, 1 grass_side, 2 dirt, 3 cobble, 4 planks, 5 oak, 6 diamond_ore, 7 obsidian,
        //         8 bricks, 9 netherrack, 10 glowstone, 11 water, 12 lava, 13 workbench, 14 chest, 15 furnace)
        // 16..31: anime_tiles_2.png (16 stone, 17 coal_ore, 18 iron_ore, 19 gold_ore, 20 emerald_ore, 21 quartz_ore,
        //         22 leaves, 23 birch, 24 sand, 25 sandstone, 26 snow, 27 ice, 28 cactus, 29 glass, 30 bookshelf, 31 tnt)
        // 32..47: anime_tiles_3.png (32 deepslate, 33 mossy_cobble, 34 puddle, 35 deep_snow, 36 sakura_leaves, 37 sakura_bark,
        //         38 spruce_bark, 39 clay, 40 copper_ore, 41 amethyst_blk, 42 ruby_blk, 43 stone_bricks,
        //         44 barrel, 45 pumpkin, 46 wheat, 47 carpet)
        // 48..51: derived (48 snow_side, 49 nether_brick, 50 soul_sand, 51 furnace_top)

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
        reg(ItemDef(23, "Светокамень", solid = true, hardness = 0.5f, dropId = 23, lightRadius = 290f, lightColor = 0xFFE27A, topTile = 10, sideTile = 10, colorHex = 0xF8D466))
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
        reg(ItemDef(38, "Кровать", solid = true, transparent = true, isProp = true, propIdx = 4, hardness = 0.6f, dropId = 38, colorHex = 0xD82838, interactive = "bed"))
        reg(ItemDef(39, "Спуск в Шахту", solid = false, transparent = true, isProp = true, propIdx = 7, hardness = 999f, dropId = 0, colorHex = 0x5C5042, interactive = "cave"))

        // --- НОВЫЕ БЛОКИ v5.2 (40..71) ---
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
        reg(ItemDef(54, "Пшеница", solid = false, transparent = true, hardness = 0.25f, dropId = 143, topTile = 46, sideTile = 46, colorHex = 0xE6C03C))
        reg(ItemDef(55, "Алый ковёр", solid = true, hardness = 0.45f, dropId = 55, topTile = 47, sideTile = 47, colorHex = 0xBA2636))

        // --- НОВЫЕ ИЗОМЕТРИЧЕСКИЕ ПРОПСЫ v5.2 (56..71) ---
        reg(ItemDef(56, "Лестница", solid = false, transparent = true, isProp = true, propIdx = 8, isClimbable = true, hardness = 0.35f, dropId = 56, toolType = "axe", colorHex = 0xB47C42))
        reg(ItemDef(57, "Дверь", solid = true, transparent = true, isProp = true, propIdx = 9, hardness = 0.8f, dropId = 57, toolType = "axe", colorHex = 0x945C2E, interactive = "door"))
        reg(ItemDef(58, "Дверь (откр.)", solid = false, transparent = true, isProp = true, propIdx = 10, hardness = 0.8f, dropId = 57, toolType = "axe", colorHex = 0x945C2E, interactive = "door"))
        reg(ItemDef(59, "Костёр", solid = false, transparent = true, isProp = true, propIdx = 11, isWarmSource = true, lightRadius = 340f, lightColor = 0xFF9832, hardness = 0.5f, dropId = 59, colorHex = 0xFF8822, interactive = "campfire"))
        reg(ItemDef(60, "Фонарь", solid = false, transparent = true, isProp = true, propIdx = 13, isWarmSource = true, lightRadius = 400f, lightColor = 0xFFE688, hardness = 0.4f, dropId = 60, colorHex = 0xFFE278))
        reg(ItemDef(61, "Камень Телепорта", solid = true, transparent = true, isProp = true, propIdx = 14, lightRadius = 250f, lightColor = 0x56D8FF, hardness = 2.5f, dropId = 61, toolType = "pick", colorHex = 0x62C8FF, interactive = "waystone"))
        reg(ItemDef(62, "Лесной Алтарь", solid = true, transparent = true, isProp = true, propIdx = 15, lightRadius = 230f, lightColor = 0x38EE7A, hardness = 999f, dropId = 0, colorHex = 0x36E872, interactive = "altar"))
        reg(ItemDef(63, "Наковальня (Заточка)", solid = true, transparent = true, isProp = true, propIdx = 16, hardness = 2.0f, dropId = 63, toolType = "pick", colorHex = 0x525866, interactive = "anvil"))
        reg(ItemDef(64, "Забор", solid = true, transparent = true, isProp = true, propIdx = 17, hardness = 0.7f, dropId = 64, toolType = "axe", colorHex = 0x9E6838))
        reg(ItemDef(65, "Указатель (Табличка)", solid = false, transparent = true, isProp = true, propIdx = 18, hardness = 0.4f, dropId = 65, toolType = "axe", colorHex = 0xB8844A, interactive = "sign"))
        reg(ItemDef(66, "Светящийся гриб", solid = false, transparent = true, isProp = true, propIdx = 19, lightRadius = 150f, lightColor = 0x48DCF8, hardness = 0.15f, dropId = 145, colorHex = 0x44D4F4))
        reg(ItemDef(67, "Ягодный куст", solid = false, transparent = true, isProp = true, propIdx = 20, hardness = 0.25f, dropId = 144, colorHex = 0x348C38))
        reg(ItemDef(68, "Весенние цветы", solid = false, transparent = true, isProp = true, propIdx = 21, hardness = 0.1f, dropId = 68, colorHex = 0xFF78A8))
        reg(ItemDef(69, "Кристалл аметиста", solid = false, transparent = true, isProp = true, propIdx = 22, isOre = true, lightRadius = 190f, lightColor = 0xC662FF, hardness = 0.8f, dropId = 141, toolType = "pick", colorHex = 0xBC54FF))
        reg(ItemDef(70, "Кристалл рубина", solid = false, transparent = true, isProp = true, propIdx = 23, isOre = true, lightRadius = 190f, lightColor = 0xFF385A, hardness = 0.9f, dropId = 142, toolType = "pick", colorHex = 0xEC284A))
        reg(ItemDef(71, "Сундук (Ловушка!)", solid = true, transparent = true, isProp = true, propIdx = 2, hardness = 1.0f, dropId = 103, colorHex = 0xC68232, interactive = "mimic"))
        reg(ItemDef(72, "Шахтный Лифт", solid = false, transparent = true, isProp = true, propIdx = 24, lightRadius = 170f, lightColor = 0x4CEEFF, hardness = 1.5f, dropId = 72, toolType = "pick", colorHex = 0x48D8FF, interactive = "elevator"))
        reg(ItemDef(73, "Шипы-ловушка", solid = false, transparent = true, isProp = true, propIdx = 25, hardness = 1.0f, dropId = 73, toolType = "pick", colorHex = 0xD83848))
        reg(ItemDef(74, "Авто-арбалет (Турель)", solid = true, transparent = true, isProp = true, propIdx = 26, hardness = 1.4f, dropId = 74, toolType = "axe", colorHex = 0xB87E44))

        // --- ПРЕДМЕТЫ, ОРУЖИЕ, ОТВАРЫ И НАГРАДЫ БОССОВ (101..148) ---
        reg(ItemDef(101, "Уголь", isItem = true, colorHex = 0x2B2E38, fuelPower = 80f))
        reg(ItemDef(102, "Железный слиток", isItem = true, colorHex = 0xDFE6F0))
        reg(ItemDef(103, "Золотой слиток", isItem = true, colorHex = 0xFFD23F))
        reg(ItemDef(104, "Алмаз", isItem = true, colorHex = 0x4CEEFF))
        reg(ItemDef(105, "Палка", isItem = true, colorHex = 0x9C6D3E, fuelPower = 8f))
        reg(ItemDef(106, "Деревянная кирка", isItem = true, colorHex = 0xB8864B, toolType = "pick", mineSpeed = 2.4f, damage = 4))
        reg(ItemDef(107, "Каменная кирка", isItem = true, colorHex = 0x9299A4, toolType = "pick", mineSpeed = 3.8f, damage = 5))
        reg(ItemDef(108, "Железная кирка", isItem = true, colorHex = 0xE4ECF5, toolType = "pick", mineSpeed = 5.5f, damage = 6))
        reg(ItemDef(109, "Алмазная кирка", isItem = true, colorHex = 0x4CEEFF, toolType = "pick", mineSpeed = 8.5f, damage = 7))
        reg(ItemDef(110, "Боевой Топор", isItem = true, colorHex = 0xB0B8C4, toolType = "axe", mineSpeed = 4.8f, damage = 7))
        reg(ItemDef(111, "Железный Меч", isItem = true, colorHex = 0xE8F2FF, weaponStyle = "katana", damage = 10))
        reg(ItemDef(112, "Алмазный Меч", isItem = true, colorHex = 0x4CEEFF, weaponStyle = "katana", damage = 16))
        reg(ItemDef(113, "Яблоко", isItem = true, colorHex = 0xE8383F, healFood = 5, healHp = 3))
        reg(ItemDef(114, "Хлеб", isItem = true, colorHex = 0xD69648, healFood = 6, healHp = 2))
        reg(ItemDef(115, "Жареный стейк", isItem = true, colorHex = 0x944824, healFood = 9, healHp = 6))
        reg(ItemDef(116, "Огниво", isItem = true, colorHex = 0xFF9922))
        reg(ItemDef(117, "Адский кварц", isItem = true, colorHex = 0xF4EFE8))
        reg(ItemDef(118, "Изумруд", isItem = true, colorHex = 0x34E86C))
        reg(ItemDef(119, "Ведро воды", isItem = true, colorHex = 0x3898FF))
        reg(ItemDef(120, "Пустое ведро", isItem = true, colorHex = 0x9CA4B2))
        reg(ItemDef(123, "Лук", isItem = true, colorHex = 0x9C6D3E, weaponStyle = "bow", damage = 11))
        reg(ItemDef(124, "Стрела", isItem = true, colorHex = 0xDCDCDC))
        reg(ItemDef(125, "Кожаный шлем", isItem = true, colorHex = 0x9E5C34, armorSlot = 0, armorDef = 2))
        reg(ItemDef(126, "Железный нагрудник", isItem = true, colorHex = 0xDCE4F0, armorSlot = 1, armorDef = 5))
        reg(ItemDef(127, "Железные поножи", isItem = true, colorHex = 0xC8D2E0, armorSlot = 2, armorDef = 4))
        reg(ItemDef(128, "Алмазные ботинки", isItem = true, colorHex = 0x4CEEFF, armorSlot = 3, armorDef = 3))
        reg(ItemDef(129, "Звезда Преисподней", isItem = true, colorHex = 0xFFF275, healFood = 20, healHp = 20))
        reg(ItemDef(130, "Боевой Молот (AoE)", isItem = true, colorHex = 0x8B94A4, weaponStyle = "hammer", damage = 13))
        reg(ItemDef(131, "Крюк-Кошка", isItem = true, colorHex = 0x38BDF8, weaponStyle = "grapple", damage = 4))
        reg(ItemDef(132, "Щит (Парирование)", isItem = true, colorHex = 0xD4A846, armorDef = 4))

        // Награды боссов, отвары, новые ресурсы и тёплая броня!
        reg(ItemDef(133, "Сердце Леса (+4 MaxHP)", isItem = true, colorHex = 0x2EE870))
        reg(ItemDef(134, "Ледяной Клинок (Заморозка)", isItem = true, colorHex = 0x5CE2FF, weaponStyle = "frost", damage = 18))
        reg(ItemDef(135, "Кристальная Кирка (2x2)", isItem = true, colorHex = 0xC852FF, toolType = "pick", mineSpeed = 11.5f, aoeMine = true, damage = 10))
        reg(ItemDef(136, "Согревающий чай", isItem = true, colorHex = 0xF29438, healFood = 4, healHp = 4))
        reg(ItemDef(137, "Отвар Шахтёра (Руды)", isItem = true, colorHex = 0xFFD238, healFood = 2, healHp = 2))
        reg(ItemDef(138, "Эликсир Скорости", isItem = true, colorHex = 0x38DAFF, healFood = 2, healHp = 2))
        reg(ItemDef(140, "Медный слиток", isItem = true, colorHex = 0xD8723E))
        reg(ItemDef(141, "Осколок аметиста", isItem = true, colorHex = 0xB846F4))
        reg(ItemDef(142, "Рубин", isItem = true, colorHex = 0xE82648))
        reg(ItemDef(143, "Пшеница", isItem = true, colorHex = 0xE6C03C))
        reg(ItemDef(144, "Лесные ягоды", isItem = true, colorHex = 0xE82448, healFood = 3, healHp = 2))
        reg(ItemDef(145, "Подземный гриб", isItem = true, colorHex = 0x44D4F4, healFood = 2, healHp = 1))
        reg(ItemDef(146, "Жареный гриб", isItem = true, colorHex = 0xA85C2A, healFood = 7, healHp = 4))
        reg(ItemDef(147, "Сгусток слизи", isItem = true, colorHex = 0x56DA5C))
        reg(ItemDef(148, "Тёплая меховая шуба", isItem = true, colorHex = 0x9C6438, armorSlot = 1, armorDef = 4, warmArmor = true))
        reg(ItemDef(139, "Рубиновый Клинок (Вампиризм)", isItem = true, colorHex = 0xFF3458, weaponStyle = "ruby", damage = 20))
        reg(ItemDef(149, "Рубиновый Нагрудник", isItem = true, colorHex = 0xE82648, armorSlot = 1, armorDef = 7, warmArmor = true))
        reg(ItemDef(150, "Удочка (Рыбалка)", isItem = true, colorHex = 0x9C6834))
        reg(ItemDef(151, "Карась", isItem = true, colorHex = 0x76BAE0, healFood = 5, healHp = 3))
        reg(ItemDef(152, "Лосось", isItem = true, colorHex = 0xE86C4C, healFood = 6, healHp = 4))
        reg(ItemDef(153, "Ледяной Окунь", isItem = true, colorHex = 0x56E2FF, healFood = 6, healHp = 5))
        reg(ItemDef(154, "Золотая Рыбка", isItem = true, colorHex = 0xF6C62A, healFood = 10, healHp = 10))
        reg(ItemDef(155, "Уха (Рыбный суп)", isItem = true, colorHex = 0xF2A23A, healFood = 14, healHp = 10))
        reg(ItemDef(156, "Ягодный пирог", isItem = true, colorHex = 0xE23452, healFood = 12, healHp = 8))
        reg(ItemDef(157, "Сырое мясо", isItem = true, colorHex = 0xD83842, healFood = 2, healHp = 1))
        reg(ItemDef(158, "Мех / Шкура", isItem = true, colorHex = 0x9C6838))
        reg(ItemDef(159, "Порох", isItem = true, colorHex = 0x565C68))
        reg(ItemDef(160, "Паутина / Нить", isItem = true, colorHex = 0xE8EEF8))

        // --- РЕЦЕПТЫ: Карман (2x2), Верстак (3x3) и Костёр (Готовка и Отвары!) ---
        recipes += RecipeDef(7, 4, listOf(5 to 1), "1 Дуб -> 4 Доски", pocket2x2 = true)
        recipes += RecipeDef(7, 4, listOf(27 to 1), "1 Берёза -> 4 Доски", pocket2x2 = true)
        recipes += RecipeDef(7, 4, listOf(45 to 1), "1 Сакура -> 4 Доски", pocket2x2 = true)
        recipes += RecipeDef(7, 4, listOf(46 to 1), "1 Ель -> 4 Доски", pocket2x2 = true)
        recipes += RecipeDef(105, 4, listOf(7 to 2), "2 Доски -> 4 Палки", pocket2x2 = true)
        recipes += RecipeDef(17, 1, listOf(7 to 4), "4 Доски -> Верстак", pocket2x2 = true)
        recipes += RecipeDef(37, 4, listOf(101 to 1, 105 to 1), "Уголь + Палка -> 4 Факела", pocket2x2 = true)
        recipes += RecipeDef(56, 4, listOf(105 to 6), "6 Палок -> 4 Лестницы", pocket2x2 = true)
        recipes += RecipeDef(59, 1, listOf(5 to 2, 101 to 1), "2 Бревна + Уголь -> Костёр", pocket2x2 = true)

        // Верстак (3x3)
        recipes += RecipeDef(18, 1, listOf(4 to 8), "8 Булыжника -> Печь")
        recipes += RecipeDef(35, 1, listOf(7 to 8), "8 Досок -> Сундук")
        recipes += RecipeDef(57, 2, listOf(7 to 6), "6 Досок -> 2 Двери")
        recipes += RecipeDef(60, 2, listOf(37 to 1, 102 to 1), "Факел + Железо -> 2 Фонаря")
        recipes += RecipeDef(61, 1, listOf(51 to 4, 141 to 2), "4 Кам.кирпича + 2 Аметиста -> Камень Телепорта")
        recipes += RecipeDef(51, 4, listOf(4 to 4), "4 Булыжника -> 4 Кам.кирпича")
        recipes += RecipeDef(52, 1, listOf(7 to 6, 140 to 1), "6 Досок + Медь -> Бочка")
        recipes += RecipeDef(64, 4, listOf(7 to 4, 105 to 2), "4 Доски + 2 Палки -> 4 Забора")
        recipes += RecipeDef(38, 1, listOf(7 to 3, 158 to 2), "3 Доски + 2 Меха -> Кровать")
        recipes += RecipeDef(148, 1, listOf(158 to 4, 7 to 2), "4 Меха + 2 Доски -> Тёплая шуба (от холода)")
        recipes += RecipeDef(125, 1, listOf(158 to 3), "3 Меха -> Кожаный шлем")
        recipes += RecipeDef(107, 1, listOf(4 to 3, 105 to 2), "3 Булыжника + 2 Палки -> Кам. кирка")
        recipes += RecipeDef(111, 1, listOf(102 to 2, 105 to 1), "2 Железа + Палка -> Железный Меч")
        recipes += RecipeDef(126, 1, listOf(102 to 6), "6 Железа -> Жел. нагрудник")
        recipes += RecipeDef(127, 1, listOf(102 to 5), "5 Железа -> Жел. поножи")
        recipes += RecipeDef(128, 1, listOf(104 to 4), "4 Алмаза -> Алм. ботинки")
        recipes += RecipeDef(26, 1, listOf(159 to 4, 8 to 2), "4 Пороха + 2 Песка -> Динамит (TNT)")
        recipes += RecipeDef(108, 1, listOf(102 to 3, 105 to 2), "3 Железа + 2 Палки -> Жел. кирка")
        recipes += RecipeDef(109, 1, listOf(104 to 3, 105 to 2), "3 Алмаза + 2 Палки -> Алм. кирка")
        recipes += RecipeDef(112, 1, listOf(104 to 2, 105 to 1), "2 Алмаза + Палка -> Алм. Меч")
        recipes += RecipeDef(130, 1, listOf(102 to 5, 105 to 2), "5 Железа + 2 Палки -> Боевой Молот")
        recipes += RecipeDef(132, 1, listOf(7 to 6, 102 to 1), "6 Досок + Железо -> Щит (Парирование)")
        recipes += RecipeDef(123, 1, listOf(105 to 3, 160 to 2), "3 Палки + 2 Нити -> Лук")
        recipes += RecipeDef(124, 8, listOf(105 to 1, 4 to 1), "Палка + Булыжник -> 8 Стрел")
        recipes += RecipeDef(34, 1, listOf(22 to 4, 116 to 1), "4 Обсидиана + Огниво -> Портал в Ад")
        recipes += RecipeDef(114, 2, listOf(143 to 3), "3 Пшеницы -> 2 Хлеба")
        recipes += RecipeDef(139, 1, listOf(142 to 2, 105 to 1), "2 Рубина + Палка -> Рубиновый Клинок")
        recipes += RecipeDef(149, 1, listOf(142 to 4, 102 to 2), "4 Рубина + 2 Железа -> Рубин. Нагрудник")
        recipes += RecipeDef(61, 1, listOf(51 to 4, 142 to 2), "4 Кам.кирпича + 2 Рубина -> Камень Телепорта")
        recipes += RecipeDef(63, 1, listOf(102 to 6), "6 Железа -> Наковальня (Заточка)")
        recipes += RecipeDef(65, 2, listOf(7 to 2, 105 to 1), "2 Доски + Палка -> 2 Таблички")
        recipes += RecipeDef(116, 1, listOf(102 to 1, 101 to 1), "Железо + Уголь -> Огниво")
        recipes += RecipeDef(120, 1, listOf(102 to 3), "3 Железа -> Пустое ведро")
        recipes += RecipeDef(150, 1, listOf(105 to 3, 160 to 2), "3 Палки + 2 Нити -> Удочка")
        recipes += RecipeDef(72, 2, listOf(102 to 4, 141 to 1), "4 Железа + Аметист -> 2 Шахтных Лифта")
        recipes += RecipeDef(73, 2, listOf(102 to 3), "3 Железа -> 2 Шипы-ловушки")
        recipes += RecipeDef(74, 1, listOf(123 to 1, 102 to 3, 7 to 4), "Лук + 3 Железа + 4 Доски -> Авто-арбалет")

        // Рецепты Костра (Готовка и Отвары!)
        recipes += RecipeDef(115, 1, listOf(157 to 1), "1 Сырое мясо -> Жареный стейк", campfireBrew = true)
        recipes += RecipeDef(146, 2, listOf(145 to 2), "2 Гриба -> 2 Жареных гриба", campfireBrew = true)
        recipes += RecipeDef(136, 2, listOf(144 to 2, 113 to 1), "2 Ягоды + Яблоко -> 2 Согревающих чая", campfireBrew = true)
        recipes += RecipeDef(137, 1, listOf(145 to 2, 141 to 1), "2 Гриба + Аметист -> Отвар Шахтёра (видит руды)", campfireBrew = true)
        recipes += RecipeDef(138, 1, listOf(144 to 2, 147 to 1), "2 Ягоды + Слизь -> Эликсир Скорости", campfireBrew = true)
        recipes += RecipeDef(114, 2, listOf(143 to 2), "2 Пшеницы -> 2 Тёплых хлеба", campfireBrew = true)
        recipes += RecipeDef(155, 1, listOf(151 to 1, 145 to 1), "Карась + Гриб -> Горячая Уха", campfireBrew = true)
        recipes += RecipeDef(155, 1, listOf(152 to 1, 145 to 1), "Лосось + Гриб -> Горячая Уха", campfireBrew = true)
        recipes += RecipeDef(156, 1, listOf(143 to 2, 144 to 3), "2 Пшеницы + 3 Ягоды -> Ягодный пирог", campfireBrew = true)

        smeltMap[12] = 102; smeltMap[13] = 103; smeltMap[48] = 140
        smeltMap[8] = 15; smeltMap[145] = 146; smeltMap[4] = 3; smeltMap[47] = 16; smeltMap[157] = 115

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

        // Slot 63 (col 7, row 7): solid white 64x64 tile used by Rust mesher for zero-switch cutaway caps!
        atlasPixmap.blending = Pixmap.Blending.None
        atlasPixmap.setColor(Color.WHITE)
        atlasPixmap.fillRectangle(7 * TILE_PX, 7 * TILE_PX, TILE_PX, TILE_PX)

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

        val f1 = Gdx.files.internal("anime_items.png")
        if (f1.exists()) {
            val tex = Texture(f1).apply { setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest) }
            itemsTexture1 = tex
            val cw = tex.width / 4; val ch = tex.height / 4
            fun c1(c: Int, r: Int) = TextureRegion(tex, c * cw, r * ch, cw, ch)
            iconRegions[112] = c1(0, 0); iconRegions[111] = c1(1, 0)
            iconRegions[109] = c1(2, 0); iconRegions[108] = c1(2, 0); iconRegions[107] = c1(2, 0); iconRegions[106] = c1(2, 0); iconRegions[131] = c1(2, 0)
            iconRegions[110] = c1(3, 0); iconRegions[130] = c1(3, 0)
            iconRegions[123] = c1(0, 1); iconRegions[124] = c1(0, 1)
            iconRegions[104] = c1(1, 1); iconRegions[129] = c1(1, 1); iconRegions[118] = c1(2, 1); iconRegions[103] = c1(3, 1)
            iconRegions[102] = c1(0, 2); iconRegions[117] = c1(0, 2); iconRegions[101] = c1(1, 2); iconRegions[113] = c1(2, 2); iconRegions[115] = c1(3, 2)
            iconRegions[114] = c1(0, 3); iconRegions[37] = c1(1, 3); iconRegions[105] = c1(1, 3); iconRegions[116] = c1(1, 3); iconRegions[119] = c1(2, 3); iconRegions[120] = c1(2, 3)
            iconRegions[126] = c1(3, 3); iconRegions[125] = c1(3, 3); iconRegions[127] = c1(3, 3); iconRegions[128] = c1(3, 3)
        }

        val f2 = Gdx.files.internal("anime_items_2.png")
        if (f2.exists()) {
            val tex = Texture(f2).apply { setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest) }
            itemsTexture2 = tex
            val cw = tex.width / 4; val ch = tex.height / 4
            fun c2(c: Int, r: Int) = TextureRegion(tex, c * cw, r * ch, cw, ch)
            iconRegions[133] = c2(0, 0) // Сердце Леса
            iconRegions[134] = c2(1, 0) // Ледяной Клинок
            iconRegions[135] = c2(2, 0) // Кристальная Кирка 2x2
            iconRegions[136] = c2(3, 0) // Согревающий чай
            iconRegions[137] = c2(0, 1) // Отвар Шахтёра
            iconRegions[138] = c2(1, 1) // Эликсир Скорости
            iconRegions[140] = c2(2, 1) // Медный слиток
            iconRegions[141] = c2(3, 1) // Осколок аметиста
            iconRegions[142] = c2(0, 2) // Рубин
            iconRegions[143] = c2(1, 2) // Пшеница
            iconRegions[144] = c2(2, 2) // Лесные ягоды
            iconRegions[145] = c2(3, 2) // Подземный гриб
            iconRegions[146] = c2(0, 3) // Жареный гриб
            iconRegions[147] = c2(1, 3) // Сгусток слизи
            iconRegions[148] = c2(2, 3) // Тёплая шуба
            iconRegions[132] = c2(3, 3) // Щит (Парирование)
            iconRegions[139] = c2(1, 0) // Рубиновый Клинок
            iconRegions[149] = c2(2, 3) // Рубиновый Нагрудник
        }

        val f3 = Gdx.files.internal("anime_items_3.png")
        if (f3.exists()) {
            val tex = Texture(f3).apply { setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest) }
            itemsTexture3 = tex
            val cw = tex.width / 4; val ch = tex.height / 4
            fun c3(c: Int, r: Int) = TextureRegion(tex, c * cw, r * ch, cw, ch)
            iconRegions[150] = c3(0, 0) // Удочка
            iconRegions[151] = c3(1, 0) // Карась
            iconRegions[152] = c3(2, 0) // Лосось
            iconRegions[153] = c3(3, 0) // Ледяной Окунь
            iconRegions[154] = c3(0, 1) // Золотая Рыбка
            iconRegions[155] = c3(1, 1) // Уха
            iconRegions[156] = c3(2, 1) // Ягодный пирог
            iconRegions[157] = c3(0, 2) // Сырое мясо
            iconRegions[158] = c3(1, 2) // Мех / Шкура
            iconRegions[159] = c3(2, 2) // Порох
            iconRegions[160] = c3(3, 2) // Паутина / Нить
        }
    }

    fun attachPropIcons(propsTex: Texture) {
        fun pIcon(idx: Int) = TextureRegion(propsTex, (idx % 8) * 64, (idx / 8) * 64, 64, 64)
        iconRegions[38] = pIcon(4)  // Кровать
        iconRegions[34] = pIcon(5)  // Портал
        iconRegions[56] = pIcon(8)  // Лестница
        iconRegions[57] = pIcon(9)  // Дверь
        iconRegions[59] = pIcon(11) // Костёр
        iconRegions[60] = pIcon(13) // Фонарь
        iconRegions[61] = pIcon(14) // Камень Телепорта
        iconRegions[62] = pIcon(15) // Лесной Алтарь
        iconRegions[63] = pIcon(16) // Наковальня
        iconRegions[64] = pIcon(17) // Забор
        iconRegions[65] = pIcon(18) // Указатель
        iconRegions[66] = pIcon(19) // Гриб
        iconRegions[67] = pIcon(20) // Куст
        iconRegions[68] = pIcon(21) // Цветы
        iconRegions[69] = pIcon(22) // Кристалл аметиста
        iconRegions[70] = pIcon(23) // Кристалл рубина
        iconRegions[72] = pIcon(24) // Шахтный Лифт
        iconRegions[73] = pIcon(25) // Шипы-ловушка
        iconRegions[74] = pIcon(26) // Авто-арбалет
    }

    fun registerModBlock(id: Int, name: String, r: Float, g: Float, b: Float): Int {
        val tid = if (nextModTile < ATLAS_COLS * ATLAS_ROWS) nextModTile++ else 16
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
    }
}
