package com.voxelhorizon.nativegame

import com.badlogic.gdx.graphics.Color
import java.io.File
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

data class CustomBlockMod(
    val id: Int,
    var name: String,
    val pixels: IntArray, // 64 RGB ints (8x8)
    var solid: Boolean = true,
    var lightRadius: Float = 0f,
    var lightColor: Int = 0xFFE688,
    var hardness: Float = 1.0f,
    var stepEffect: Int = 0 // 0=Нет, 1=Батут, 2=Ускорение, 3=Лечение, 4=Шипы/Урон, 5=Лифт +4
)

data class CustomItemMod(
    val id: Int,
    var name: String,
    val pixels: IntArray, // 64 ints (-1 = прозрачный)
    var kind: String = "sword", // "sword", "pick", "staff", "fire_staff", "food", "potion"
    var damage: Int = 14,
    var mineSpeed: Float = 8.0f,
    var durability: Int = 400,
    var healHp: Int = 0,
    var healFood: Int = 0,
    var aoeMine: Boolean = false,
    var specialFx: Int = 0 // 0=Нет, 1=Взрыв, 2=Лёд, 3=Вампиризм, 4=Стан/Молния, 5=Скорость, 6=Щит, 7=ТП домой
)

data class CustomRecipeMod(
    var outId: Int,
    var outCount: Int,
    var req1Id: Int,
    var req1Cnt: Int,
    var req2Id: Int,
    var req2Cnt: Int,
    var station: Int // 0=Карман 2x2, 1=Верстак, 2=Костёр
)

data class CustomMobMod(
    var name: String,
    var baseSkin: String = "zombie",
    var tintHex: Int = 0x58EEFF,
    var behavior: Int = 1, // 0=Мирный, 1=Враг ближний, 2=Стрелок огнём, 3=Стрелок льдом, 4=Босс
    var maxHp: Float = 45f,
    var damage: Float = 6f,
    var speed: Float = 2.8f,
    var dropId: Int = 118,
    var dropCount: Int = 1
)

data class CustomRuleMod(
    var name: String,
    var enabled: Boolean = true,
    var trigger: Int = 0, // 0=Сломан блок, 1=Поставлен блок, 2=Убит моб, 3=Игрок ранен, 4=Утро, 5=Ночь, 6=Каждые 20с
    var action: Int = 0,  // 0=Выдать предмет, 1=Лечить +6 HP, 2=Скорость, 3=Сила, 4=Взрыв, 5=Спавн моба, 6=Тост, 7=Еда, 8=Смена погоды
    var paramId: Int = 113,
    var paramCount: Int = 1,
    var paramText: String = "Событие сработало!"
)

class WorldModRules {
    var playerSpeedMult: Float = 1.0f
    var mobDamageMult: Float = 1.0f
    var mineSpeedMult: Float = 1.0f
    var dropMult: Int = 1
    var timeMode: Int = 0 // 0=Норма, 1=Вечный день, 2=Вечная ночь, 3=Быстрый день
    var infiniteDurability: Boolean = false
    var godMode: Boolean = false
    var megaDash: Boolean = false
}

class NoCodeModStudio(private val storageRoot: File) {
    val customBlocks = mutableListOf<CustomBlockMod>()
    val customItems = mutableListOf<CustomItemMod>()
    val customRecipes = mutableListOf<CustomRecipeMod>()
    val customMobs = mutableListOf<CustomMobMod>()
    val customRules = mutableListOf<CustomRuleMod>()
    val worldRules = WorldModRules()

    // Studio UI Editor state
    var studioTab = 0 // 0=Блоки, 1=Предметы, 2=Крафты, 3=Мобы, 4=События, 5=Мир/Пресеты
    var activeBrushColorIdx = 0
    val palette = intArrayOf(
        0xE83848, 0xFF9828, 0xFFE248, 0x48D858,
        0x38C8FF, 0x4C64F2, 0xB848F8, 0xFF78C2,
        0xF4F6FA, 0x9CA6B8, 0x8C5832, 0x262834
    )

    // Draft Block
    var draftBlockId = 190
    var draftBlockName = "Неон-Блок"
    val draftBlockPx = IntArray(64) { i ->
        val x = i % 8; val y = i / 8
        if (x == 0 || x == 7 || y == 0 || y == 7) 0x262834 else if ((x + y) % 2 == 0) 0x38C8FF else 0x4C64F2
    }
    var draftBlockSolid = true
    var draftBlockLight = 240f
    var draftBlockHardness = 1.0f
    var draftBlockStepFx = 1

    // Draft Item
    var draftItemId = 261
    var draftItemName = "Клинок Бури"
    val draftItemPx = IntArray(64) { -1 }
    var draftItemKindIdx = 0
    var draftItemDmg = 18
    var draftItemMineSpd = 9.0f
    var draftItemDur = 500
    var draftItemHealHp = 4
    var draftItemHealFood = 6
    var draftItemAoe = false
    var draftItemFx = 4

    // Draft Recipe
    var draftRecOutId = 190
    var draftRecOutCnt = 2
    var draftRecReq1Id = 7
    var draftRecReq1Cnt = 2
    var draftRecReq2Id = 102
    var draftRecReq2Cnt = 1
    var draftRecStation = 1

    // Draft Mob
    var draftMobName = "Кристальный Страж"
    var draftMobSkinIdx = 0
    var draftMobTintIdx = 4
    var draftMobBehavior = 1
    var draftMobHp = 60f
    var draftMobDmg = 7f
    var draftMobSpd = 2.8f
    var draftMobDropIdx = 0

    // Draft Rule
    var draftRuleName = "Награда за моба"
    var draftRuleTrigger = 2
    var draftRuleAction = 1
    var draftRuleParamIdx = 0

    private var periodicTimer = 0f
    private var stepFxCd = 0f
    private var wasDay = true

    val mobSkinPool = arrayOf("zombie", "skeleton", "slime", "yeti", "crystal_golem", "enderman", "blaze", "goblin", "mimic", "sheep", "deer", "wolf", "grove_guardian", "frost_titan", "depth_devourer", "sky_guardian")
    val itemPool = intArrayOf(7, 4, 101, 102, 103, 104, 118, 140, 141, 142, 201, 113, 115, 138, 242, 249, 190, 191, 261, 262)

    init {
        resetItemTemplate(0)
    }

    fun resetItemTemplate(kindIdx: Int) {
        draftItemPx.fill(-1)
        when (kindIdx) {
            0 -> { // Sword template
                for (i in 1..6) { draftItemPx[i * 8 + (7 - i)] = 0x38C8FF; if (i < 6) draftItemPx[i * 8 + (6 - i)] = 0xF4F6FA }
                draftItemPx[5 * 8 + 1] = 0xFFE248; draftItemPx[6 * 8 + 2] = 0xFFE248; draftItemPx[6 * 8 + 1] = 0x8C5832; draftItemPx[7 * 8 + 0] = 0x8C5832
            }
            1 -> { // Pickaxe template
                for (x in 1..6) draftItemPx[1 * 8 + x] = 0xB848F8
                draftItemPx[2 * 8 + 1] = 0xB848F8; draftItemPx[2 * 8 + 6] = 0xB848F8
                for (y in 2..6) draftItemPx[y * 8 + 3] = 0x8C5832
            }
            2, 3 -> { // Staff template
                for (i in 2..6) draftItemPx[i * 8 + (7 - i)] = 0x8C5832
                val gem = if (kindIdx == 3) 0xFF9828 else 0xB848F8
                for (y in 1..2) for (x in 5..6) draftItemPx[y * 8 + x] = gem
            }
            else -> { // Potion / Food template
                for (y in 3..6) for (x in 2..5) draftItemPx[y * 8 + x] = 0xE83848
                draftItemPx[2 * 8 + 3] = 0xF4F6FA; draftItemPx[2 * 8 + 4] = 0xF4F6FA
                draftItemPx[1 * 8 + 3] = 0x8C5832; draftItemPx[1 * 8 + 4] = 0x8C5832
            }
        }
    }

    private fun saveFile(): File = File(File(storageRoot, "mods"), "nocode_mods_v69.dat")

    private fun parseModLines(lines: List<String>, mergeMode: Boolean): Int {
        var added = 0
        try {
            for (raw in lines) {
                val p = raw.trim().split("|")
                if (p.isEmpty()) continue
                when (p[0]) {
                    "BLK" -> if (p.size >= 9) {
                        val px = IntArray(64)
                        val hexes = p[8].split(",")
                        for (i in 0 until min(64, hexes.size)) px[i] = hexes[i].toIntOrNull(16) ?: 0x38C8FF
                        val bid = p[1].toIntOrNull() ?: 190
                        if (mergeMode) customBlocks.removeIf { it.id == bid }
                        customBlocks += CustomBlockMod(
                            id = bid, name = p[2], pixels = px,
                            solid = p[3] == "1", lightRadius = p[4].toFloatOrNull() ?: 0f,
                            lightColor = p[5].toIntOrNull() ?: 0xFFE688,
                            hardness = p[6].toFloatOrNull() ?: 1f, stepEffect = p[7].toIntOrNull() ?: 0
                        )
                        added++
                    }
                    "ITM" -> if (p.size >= 12) {
                        val px = IntArray(64) { -1 }
                        val hexes = p[11].split(",")
                        for (i in 0 until min(64, hexes.size)) px[i] = if (hexes[i] == "-") -1 else (hexes[i].toIntOrNull(16) ?: -1)
                        val iid = p[1].toIntOrNull() ?: 261
                        if (mergeMode) customItems.removeIf { it.id == iid }
                        customItems += CustomItemMod(
                            id = iid, name = p[2], pixels = px, kind = p[3],
                            damage = p[4].toIntOrNull() ?: 12, mineSpeed = p[5].toFloatOrNull() ?: 6f,
                            durability = p[6].toIntOrNull() ?: 300, healHp = p[7].toIntOrNull() ?: 0,
                            healFood = p[8].toIntOrNull() ?: 0, aoeMine = p[9] == "1", specialFx = p[10].toIntOrNull() ?: 0
                        )
                        added++
                    }
                    "REC" -> if (p.size >= 8) {
                        customRecipes += CustomRecipeMod(
                            p[1].toIntOrNull() ?: 190, p[2].toIntOrNull() ?: 1,
                            p[3].toIntOrNull() ?: 7, p[4].toIntOrNull() ?: 2,
                            p[5].toIntOrNull() ?: 0, p[6].toIntOrNull() ?: 0,
                            p[7].toIntOrNull() ?: 1
                        )
                        added++
                    }
                    "MOB" -> if (p.size >= 10) {
                        if (mergeMode) customMobs.removeIf { it.name == p[1] }
                        customMobs += CustomMobMod(
                            p[1], p[2], p[3].toIntOrNull() ?: 0x58EEFF, p[4].toIntOrNull() ?: 1,
                            p[5].toFloatOrNull() ?: 45f, p[6].toFloatOrNull() ?: 6f,
                            p[7].toFloatOrNull() ?: 2.8f, p[8].toIntOrNull() ?: 118, p[9].toIntOrNull() ?: 1
                        )
                        added++
                    }
                    "RULE" -> if (p.size >= 8) {
                        customRules += CustomRuleMod(
                            p[1], p[2] == "1", p[3].toIntOrNull() ?: 0, p[4].toIntOrNull() ?: 0,
                            p[5].toIntOrNull() ?: 113, p[6].toIntOrNull() ?: 1, p[7]
                        )
                        added++
                    }
                    "WORLD" -> if (p.size >= 9) {
                        worldRules.playerSpeedMult = p[1].toFloatOrNull() ?: 1f
                        worldRules.mobDamageMult = p[2].toFloatOrNull() ?: 1f
                        worldRules.mineSpeedMult = p[3].toFloatOrNull() ?: 1f
                        worldRules.dropMult = p[4].toIntOrNull() ?: 1
                        worldRules.timeMode = p[5].toIntOrNull() ?: 0
                        worldRules.infiniteDurability = p[6] == "1"
                        worldRules.godMode = p[7] == "1"
                        worldRules.megaDash = p[8] == "1"
                    }
                }
            }
        } catch (_: Exception) {}
        return added
    }

    fun loadAndApply() {
        customBlocks.clear(); customItems.clear(); customRecipes.clear(); customMobs.clear(); customRules.clear()
        val f = saveFile()
        val text = if (f.exists() && f.length() > 5) {
            try { f.readText(Charsets.UTF_8) } catch (_: Exception) { null }
        } else {
            PlatformBridge.loadNoCodeModsVault()
        }
        if (!text.isNullOrBlank()) {
            parseModLines(text.lines(), mergeMode = false)
            if (!f.exists()) saveAll()
        }
        applyAllToEngine()
    }

    fun importNoCodeModText(rawText: String, sourceName: String): String {
        val added = parseModLines(rawText.lines(), mergeMode = true)
        applyAllToEngine()
        saveAll()
        return if (added > 0) "Мод «$sourceName» импортирован (+$added элем.)!" else "Настройки из мода «$sourceName» применены!"
    }

    fun serializeAll(): String {
        val sb = StringBuilder()
        sb.append("WORLD|").append(worldRules.playerSpeedMult).append('|').append(worldRules.mobDamageMult).append('|')
            .append(worldRules.mineSpeedMult).append('|').append(worldRules.dropMult).append('|')
            .append(worldRules.timeMode).append('|').append(if (worldRules.infiniteDurability) "1" else "0").append('|')
            .append(if (worldRules.godMode) "1" else "0").append('|').append(if (worldRules.megaDash) "1" else "0").append('\n')
        for (b in customBlocks) {
            val pxStr = b.pixels.joinToString(",") { "%06X".format(it and 0xFFFFFF) }
            sb.append("BLK|").append(b.id).append('|').append(b.name.replace("|", "")).append('|')
                .append(if (b.solid) "1" else "0").append('|').append(b.lightRadius).append('|')
                .append(b.lightColor).append('|').append(b.hardness).append('|').append(b.stepEffect).append('|').append(pxStr).append('\n')
        }
        for (it in customItems) {
            val pxStr = it.pixels.joinToString(",") { if (it < 0) "-" else "%06X".format(it and 0xFFFFFF) }
            sb.append("ITM|").append(it.id).append('|').append(it.name.replace("|", "")).append('|')
                .append(it.kind).append('|').append(it.damage).append('|').append(it.mineSpeed).append('|')
                .append(it.durability).append('|').append(it.healHp).append('|').append(it.healFood).append('|')
                .append(if (it.aoeMine) "1" else "0").append('|').append(it.specialFx).append('|').append(pxStr).append('\n')
        }
        for (r in customRecipes) {
            sb.append("REC|").append(r.outId).append('|').append(r.outCount).append('|')
                .append(r.req1Id).append('|').append(r.req1Cnt).append('|').append(r.req2Id).append('|')
                .append(r.req2Cnt).append('|').append(r.station).append('\n')
        }
        for (m in customMobs) {
            sb.append("MOB|").append(m.name.replace("|", "")).append('|').append(m.baseSkin).append('|')
                .append(m.tintHex).append('|').append(m.behavior).append('|').append(m.maxHp).append('|')
                .append(m.damage).append('|').append(m.speed).append('|').append(m.dropId).append('|').append(m.dropCount).append('\n')
        }
        for (ru in customRules) {
            sb.append("RULE|").append(ru.name.replace("|", "")).append('|').append(if (ru.enabled) "1" else "0").append('|')
                .append(ru.trigger).append('|').append(ru.action).append('|').append(ru.paramId).append('|')
                .append(ru.paramCount).append('|').append(ru.paramText.replace("|", "")).append('\n')
        }
        return sb.toString()
    }

    fun exportAllToFile(destFile: File): File {
        destFile.parentFile?.mkdirs()
        destFile.writeText(serializeAll(), Charsets.UTF_8)
        return destFile
    }

    fun saveAll() {
        val text = serializeAll()
        try {
            saveFile().parentFile?.mkdirs()
            saveFile().writeText(text, Charsets.UTF_8)
        } catch (_: Exception) {}
        PlatformBridge.saveNoCodeModsVault(text)
    }

    fun applyAllToEngine() {
        for (b in customBlocks) {
            BlockRegistry.registerCustomBlock8x8(b.id, b.name, b.pixels, b.solid, b.lightRadius, b.lightColor, b.hardness, b.id)
        }
        for (it in customItems) {
            BlockRegistry.registerCustomItem8x8(it.id, it.name, it.pixels, it.kind, it.damage, it.mineSpeed, it.durability, it.healHp, it.healFood, it.aoeMine)
        }
        BlockRegistry.clearCustomRecipes()
        for (r in customRecipes) {
            val req = mutableListOf(r.req1Id to max(1, r.req1Cnt))
            if (r.req2Id > 0 && r.req2Cnt > 0) req += r.req2Id to r.req2Cnt
            val outName = BlockRegistry.items[r.outId]?.name ?: "Мод-предмет"
            BlockRegistry.recipes += RecipeDef(
                outId = r.outId, outCount = max(1, r.outCount), req = req,
                desc = "Мод: $outName",
                pocket2x2 = (r.station == 0), campfireBrew = (r.station == 2), category = "build"
            )
        }
    }

    fun saveDraftBlock(game: VoxelHorizonGame) {
        val id = draftBlockId.coerceIn(190, 199)
        customBlocks.removeIf { it.id == id }
        val mod = CustomBlockMod(id, draftBlockName.ifBlank { "Блок #$id" }, draftBlockPx.copyOf(), draftBlockSolid, draftBlockLight, 0xFFE688, draftBlockHardness, draftBlockStepFx)
        customBlocks += mod
        applyAllToEngine(); saveAll()
        game.player.addItem(id, 16)
        SoundFx.play("craft", 0.75f)
        game.showToast("Создан блок «${mod.name}» (ID:$id) и выдан x16!")
    }

    fun saveDraftItem(game: VoxelHorizonGame) {
        val id = draftItemId.coerceIn(261, 275)
        val kinds = arrayOf("sword", "pick", "staff", "fire_staff", "food", "potion")
        val kStr = kinds[draftItemKindIdx.coerceIn(0, kinds.lastIndex)]
        customItems.removeIf { it.id == id }
        val mod = CustomItemMod(
            id, draftItemName.ifBlank { "Предмет #$id" }, draftItemPx.copyOf(), kStr,
            draftItemDmg, draftItemMineSpd, draftItemDur, draftItemHealHp, draftItemHealFood, draftItemAoe, draftItemFx
        )
        customItems += mod
        applyAllToEngine(); saveAll()
        game.player.addItem(id, 1)
        SoundFx.play("enchant", 0.75f)
        game.showToast("Создан предмет «${mod.name}» (ID:$id) и выдан в сумку!")
    }

    fun saveDraftRecipe(game: VoxelHorizonGame) {
        customRecipes += CustomRecipeMod(draftRecOutId, draftRecOutCnt, draftRecReq1Id, draftRecReq1Cnt, draftRecReq2Id, draftRecReq2Cnt, draftRecStation)
        applyAllToEngine(); saveAll()
        SoundFx.play("craft", 0.7f)
        game.showToast("Рецепт добавлен в крафт! (${customRecipes.size} мод-рецептов)")
    }

    fun saveDraftMob(game: VoxelHorizonGame, spawnNow: Boolean) {
        val skin = mobSkinPool[draftMobSkinIdx.coerceIn(0, mobSkinPool.lastIndex)]
        val dropId = itemPool[draftMobDropIdx.coerceIn(0, itemPool.lastIndex)]
        val tint = palette[draftMobTintIdx.coerceIn(0, palette.lastIndex)]
        val mod = CustomMobMod(draftMobName.ifBlank { "Мод-Моб" }, skin, tint, draftMobBehavior, draftMobHp, draftMobDmg, draftMobSpd, dropId, 1)
        customMobs.removeIf { it.name == mod.name }
        customMobs += mod
        saveAll()
        if (spawnNow && game.world.worldActive) spawnCustomMob(mod, game)
        else game.showToast("Моб «${mod.name}» сохранён!")
    }

    fun spawnCustomMob(mod: CustomMobMod, game: VoxelHorizonGame) {
        val px = game.player.x + 2.4f; val py = game.player.y + 1.2f
        val pz = game.world.getGroundZ(floor(px).toInt(), floor(py).toInt(), game.player.z) ?: game.player.z
        val m = game.entities.spawnMob(mod.baseSkin, px, py, pz)
        val isHostile = mod.behavior != 0
        val rStyle = when (mod.behavior) { 2 -> 1; 3 -> 2; else -> 0 }
        // Override spawned mob stats with user's custom mod parameters!
        val cm = MobEntity(
            id = m.id, type = mod.baseSkin, name = mod.name,
            hostile = isHostile, isBoss = mod.behavior == 4,
            x = px, y = py, z = pz, hp = mod.maxHp, maxHp = mod.maxHp,
            customDamage = mod.damage, customSpeed = mod.speed, customTintHex = mod.tintHex,
            customDropId = mod.dropId, customDropCount = mod.dropCount, customRangedStyle = rStyle
        )
        game.entities.mobs.remove(m)
        game.entities.mobs.add(cm)
        SoundFx.play(if (mod.behavior == 4) "boss" else "levelup", 0.7f)
        game.showToast("Заспавнен: ${mod.name} (HP ${mod.maxHp.toInt()})!")
    }

    fun saveDraftRule(game: VoxelHorizonGame) {
        val pId = itemPool[draftRuleParamIdx.coerceIn(0, itemPool.lastIndex)]
        customRules += CustomRuleMod(draftRuleName.ifBlank { "Правило #${customRules.size + 1}" }, true, draftRuleTrigger, draftRuleAction, pId, 1, "Мод-событие активно!")
        saveAll()
        SoundFx.play("craft", 0.7f)
        game.showToast("Правило добавлено! (Всего: ${customRules.size})")
    }

    fun getCustomItem(id: Int): CustomItemMod? = customItems.firstOrNull { it.id == id }

    fun applyPreset(presetIdx: Int, game: VoxelHorizonGame) {
        when (presetIdx) {
            0 -> { // Хардкор+
                worldRules.playerSpeedMult = 0.92f; worldRules.mobDamageMult = 2.0f; worldRules.mineSpeedMult = 0.85f
                worldRules.dropMult = 1; worldRules.infiniteDurability = false; worldRules.godMode = false; worldRules.timeMode = 0
                game.showToast("Пресет: ХАРДКОР+ (враги 2x урон!)")
            }
            1 -> { // Магический мир
                worldRules.playerSpeedMult = 1.35f; worldRules.mobDamageMult = 1.0f; worldRules.mineSpeedMult = 1.8f
                worldRules.dropMult = 2; worldRules.megaDash = true; worldRules.timeMode = 0
                game.showToast("Пресет: МАГИЧЕСКИЙ МИР (дроп x2, быстрый бег!)")
            }
            2 -> { // Бог стройки
                worldRules.playerSpeedMult = 1.5f; worldRules.mineSpeedMult = 3.5f; worldRules.dropMult = 3
                worldRules.infiniteDurability = true; worldRules.timeMode = 1; worldRules.godMode = true
                game.showToast("Пресет: БОГ СТРОЙКИ (вечный день, x3.5 копка, бессмертие!)")
            }
            else -> { // Стандарт
                worldRules.playerSpeedMult = 1.0f; worldRules.mobDamageMult = 1.0f; worldRules.mineSpeedMult = 1.0f
                worldRules.dropMult = 1; worldRules.timeMode = 0; worldRules.infiniteDurability = false; worldRules.godMode = false; worldRules.megaDash = false
                game.showToast("Все правила мира сброшены к стандарту")
            }
        }
        saveAll()
    }

    fun tick(dt: Float, game: VoxelHorizonGame) {
        // 1. World Time Mode override
        when (worldRules.timeMode) {
            1 -> { game.entities.worldTime = 0.28f; game.entities.dayFactor = 1f }
            2 -> { game.entities.worldTime = 0.78f; game.entities.dayFactor = 0.05f }
            3 -> { game.entities.worldTime = (game.entities.worldTime + dt / 180f) % 1f }
        }
        val isDayNow = game.entities.dayFactor >= 0.35f
        if (isDayNow && !wasDay) fireTrigger(4, game) // Утро
        if (!isDayNow && wasDay) fireTrigger(5, game) // Ночь
        wasDay = isDayNow

        // 2. Periodic 20s trigger
        periodicTimer += dt
        if (periodicTimer >= 20f) {
            periodicTimer = 0f
            fireTrigger(6, game)
        }

        // 3. Custom Block Step Effects (Bounce / Speed / Heal / Spikes / Lift!)
        if (stepFxCd > 0f) stepFxCd = max(0f, stepFxCd - dt)
        if (stepFxCd <= 0f && customBlocks.isNotEmpty()) {
            val px = floor(game.player.x).toInt(); val py = floor(game.player.y).toInt(); val pz = floor(game.player.z).toInt()
            val under = game.world.getBlock(px, py, max(0, pz - 1))
            val atFeet = game.world.getBlock(px, py, pz)
            val blk = customBlocks.firstOrNull { it.id == under || it.id == atFeet }
            if (blk != null && blk.stepEffect > 0) {
                stepFxCd = 0.75f
                when (blk.stepEffect) {
                    1 -> { game.player.z = min((WorldIso.MAX_Z - 2).toFloat(), game.player.z + 2.5f); SoundFx.play("piston", 0.6f); game.showToast("Батут-блок!") }
                    2 -> { game.player.speedBuffTimer = max(game.player.speedBuffTimer, 5f) }
                    3 -> { game.player.hp = min(game.player.maxHp, game.player.hp + 2f); game.entities.floatingTexts.add(FloatingText(game.player.x, game.player.y, game.player.z + 1.4f, "+2 HP", Color.GREEN)) }
                    4 -> { if (!worldRules.godMode) game.player.hp = max(1f, game.player.hp - 2f); SoundFx.play("hurt", 0.5f) }
                    5 -> { game.player.z = min((WorldIso.MAX_Z - 2).toFloat(), game.player.z + 4.0f); SoundFx.play("levelup", 0.5f) }
                }
            }
        }
    }

    fun fireTrigger(triggerId: Int, game: VoxelHorizonGame) {
        for (rule in customRules) {
            if (!rule.enabled || rule.trigger != triggerId) continue
            when (rule.action) {
                0 -> { game.player.addItem(rule.paramId, max(1, rule.paramCount)); SoundFx.play("pickup", 0.45f) }
                1 -> { game.player.hp = min(game.player.maxHp, game.player.hp + 6f); game.entities.floatingTexts.add(FloatingText(game.player.x, game.player.y, game.player.z + 1.5f, "+6 HP", Color.GREEN)) }
                2 -> { game.player.speedBuffTimer = max(game.player.speedBuffTimer, 20f) }
                3 -> { game.player.strengthBuffTimer = max(game.player.strengthBuffTimer, 20f) }
                4 -> { game.triggerTntAt(floor(game.player.x + game.player.aimGx * 2.5f).toInt(), floor(game.player.y + game.player.aimGy * 2.5f).toInt(), floor(game.player.z).toInt()) }
                5 -> { if (customMobs.isNotEmpty()) spawnCustomMob(customMobs.first(), game) else game.entities.spawnMob("sheep", game.player.x + 2f, game.player.y + 1f, game.player.z) }
                6 -> { game.showToast(rule.paramText) }
                7 -> { game.player.food = game.player.maxFood }
                8 -> { game.entities.weather = WeatherType.values()[(game.entities.weather.ordinal + 1) % WeatherType.values().size] }
            }
        }
    }

    fun applyCustomWeaponEffect(fxId: Int, target: MobEntity?, game: VoxelHorizonGame) {
        when (fxId) {
            1 -> if (target != null) game.triggerTntAt(floor(target.x).toInt(), floor(target.y).toInt(), floor(target.z).toInt())
            2 -> if (target != null) target.freezeTimer = 4.5f
            3 -> { game.player.hp = min(game.player.maxHp, game.player.hp + 3f); game.entities.floatingTexts.add(FloatingText(game.player.x, game.player.y, game.player.z + 1.5f, "+3 HP", Color.PINK)) }
            4 -> if (target != null) { target.stunTimer = 3.0f; game.entities.spawnBlockParticles(floor(target.x).toInt(), floor(target.y).toInt(), floor(target.z).toInt(), 0x56EEFF, 10) }
            5 -> game.player.speedBuffTimer = max(game.player.speedBuffTimer, 15f)
            6 -> game.player.shieldBuffTimer = max(game.player.shieldBuffTimer, 15f)
            7 -> { game.player.x = game.player.spawnX; game.player.y = game.player.spawnY; game.player.z = game.player.spawnZ; game.showToast("Мод-телепорт на базу!") }
        }
    }
}
