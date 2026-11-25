package com.voxelhorizon.nativegame

import com.badlogic.gdx.graphics.Color
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class Season(val ruName: String, val grassTint: Color, val particleCol: Int, val ambientTemp: Float) {
    SPRING("Весна", Color(0.95f, 1.08f, 0.96f, 1f), 0xFFB8D9, 16f),
    SUMMER("Лето", Color(1.00f, 1.00f, 1.00f, 1f), 0xD8FF66, 26f),
    AUTUMN("Осень", Color(1.22f, 0.82f, 0.42f, 1f), 0xF58231, 11f),
    WINTER("Зима", Color(0.88f, 0.96f, 1.15f, 1f), 0xE8F6FF, -12f)
}

enum class WeatherType(val ruName: String) {
    CLEAR("Ясно"),
    RAIN("Дождь"),
    STORM("Гроза"),
    SNOW("Метель")
}

data class DashGhost(val x: Float, val y: Float, val z: Float, val facingRight: Boolean, val skinId: String, var life: Float = 0.28f)
data class SlashArc(val x: Float, val y: Float, val z: Float, val angleDeg: Float, val colorHex: Int, var life: Float = 0.22f)

class PlayerState {
    companion object { const val INV_SIZE = 54 }
    var x = 0.5f; var y = 0.5f; var z = 142.0f
    var vx = 0f; var vy = 0f; var vz = 0f
    var facingRight = true
    var aimGx = 1f; var aimGy = 0f
    var creative = false
    var hp = 20f; var maxHp = 20f
    var food = 20f; var maxFood = 20f
    var bodyTemp = 72f
    var warmBuffTimer = 0f
    var minerVisionTimer = 0f
    var speedBuffTimer = 0f
    var strengthBuffTimer = 0f
    var shieldBuffTimer = 0f
    var ridingMode = "" // "", "minecart", "boat", "deer"
    val golemBag = arrayOfNulls<SlotItem>(18)
    var parryTimer = 0f
    var dashCooldown = 0f
    var foodCooldown = 0f
    var weaponUpgradeLvl = 0
    var toolUpgradeLvl = 0

    // Состояние живых анимаций и сна!
    var walkAnim = 0f
    var isMoving = false
    var attackAnim = 0f
    var attackAngleDeg = 0f
    var hurtFlash = 0f
    var hurtSquash = 0f
    var isSleeping = false
    var sleepBedX = 0; var sleepBedY = 0; var sleepBedZ = 142
    var sleepElapsed = 0f
    var zzzTimer = 0f
    var stepDustTimer = 0f

    var selectedSlot = 0
    var skinId = "fox_officer"
    var spawnX = 0.5f; var spawnY = 0.5f; var spawnZ = 142.0f; var spawnDim = "overworld"

    val slots = arrayOfNulls<SlotItem>(INV_SIZE)
    var offhand: SlotItem? = SlotItem(37, 16)
    val armor = IntArray(4) { 0 }

    fun hasWarmArmor(): Boolean = armor.any { BlockRegistry.items[it]?.warmArmor == true }
    fun hasShield(): Boolean = (offhand?.id ?: 0) in intArrayOf(132, 255) || (slots[selectedSlot]?.id ?: 0) in intArrayOf(132, 255)
    fun hasMagnet(): Boolean = countItem(236) > 0
    fun hasMinerHelmet(): Boolean = armor[0] == 237
    fun hasSpeedBoots(): Boolean = armor[3] == 238

    fun getTotalArmor(): Int {
        var d = 0
        for (id in armor) d += BlockRegistry.items[id]?.armorDef ?: 0
        if (offhand?.id == 132) d += 4
        if (offhand?.id == 255) d += 7
        if (shieldBuffTimer > 0f) d += 6
        return d
    }

    fun countItem(id: Int): Int {
        var t = 0
        for (s in slots) if (s != null && s.id == id) t += s.count
        if (offhand?.id == id) t += offhand!!.count
        return t
    }

    fun addItem(id: Int, countIn: Int = 1): Boolean {
        if (id <= 0 || countIn <= 0) return false
        var rem = countIn
        for (i in 0 until INV_SIZE) {
            val s = slots[i]
            if (s != null && s.id == id && s.count < 64) {
                val add = minOf(64 - s.count, rem)
                s.count += add; rem -= add
                if (rem <= 0) return true
            }
        }
        for (i in 0 until INV_SIZE) {
            if (slots[i] == null) {
                val add = minOf(64, rem)
                slots[i] = SlotItem(id, add); rem -= add
                if (rem <= 0) return true
            }
        }
        return rem == 0
    }

    fun takeItem(id: Int, countIn: Int = 1): Boolean {
        if (creative) return true
        if (countItem(id) < countIn) return false
        var rem = countIn
        for (i in 0 until INV_SIZE) {
            val s = slots[i] ?: continue
            if (s.id != id) continue
            val sub = minOf(s.count, rem)
            s.count -= sub; rem -= sub
            if (s.count <= 0) slots[i] = null
            if (rem <= 0) return true
        }
        if (rem > 0 && offhand?.id == id) {
            val sub = minOf(offhand!!.count, rem)
            offhand!!.count -= sub
            if (offhand!!.count <= 0) offhand = null
        }
        return true
    }

    fun autoSortBackpack() {
        val merged = linkedMapOf<Int, Int>()
        for (i in 9 until INV_SIZE) {
            val s = slots[i] ?: continue
            merged[s.id] = (merged[s.id] ?: 0) + s.count
            slots[i] = null
        }
        var idx = 9
        for ((id, total) in merged.entries.sortedBy { it.key }) {
            var rem = total
            while (rem > 0 && idx < INV_SIZE) {
                val take = minOf(64, rem)
                slots[idx++] = SlotItem(id, take)
                rem -= take
            }
        }
    }

    fun swapWithOffhand() {
        val cur = slots[selectedSlot]
        slots[selectedSlot] = offhand
        offhand = cur
    }
}

data class MobEntity(
    val id: Int,
    val type: String,
    val name: String,
    var hostile: Boolean,
    val isBoss: Boolean,
    var x: Float, var y: Float, var z: Float,
    var vx: Float = 0f, var vy: Float = 0f,
    var facingRight: Boolean = true,
    var hp: Float = 20f, var maxHp: Float = 20f,
    var aiTimer: Float = 0f, var aiAngle: Float = 0f,
    var atkCd: Float = 0f, var walkAnim: Float = 0f,
    var attackAnim: Float = 0f,
    var hopPhase: Float = 0f,
    val idlePhase: Float = Random.nextFloat() * 6.28f,
    var hurtFlash: Float = 0f, var hurtSquash: Float = 0f,
    var fuse: Float = 0f,
    var freezeTimer: Float = 0f, var stunTimer: Float = 0f,
    var tamed: Boolean = false,
    var stolenItemId: Int = 0,
    var barterCd: Float = 0f,
    var breedCd: Float = 0f,
    var isDeepMob: Boolean = false,
    var netTargetX: Float = x,
    var netTargetY: Float = y,
    var netTargetZ: Float = z,
    var hasNetTarget: Boolean = false,
    var fleeTimer: Float = 0f
)

data class DroppedItem(
    val id: Int, val itemId: Int, val count: Int,
    var x: Float, var y: Float, var z: Float,
    var vx: Float, var vy: Float, var vz: Float, var age: Float = 0f
)

data class ProjectileIso(
    var x: Float, var y: Float, var z: Float,
    var vx: Float, var vy: Float,
    val dmg: Float, val fiery: Boolean, val frosty: Boolean, val fromPlayer: Boolean,
    val isGrapple: Boolean = false, val isTeleportPearl: Boolean = false, var life: Float = 2.4f
)

data class ParticleIso(
    var x: Float, var y: Float, var z: Float,
    var vx: Float, var vy: Float, var vz: Float,
    val colorHex: Int, var size: Float, var life: Float,
    val floatUp: Boolean = false
)

data class FloatingText(var x: Float, var y: Float, var z: Float, val text: String, val color: Color, var life: Float = 0.95f)

data class MobSyncSnap(
    val id: Int, val type: String, val name: String, val hostile: Boolean, val isBoss: Boolean,
    val x: Float, val y: Float, val z: Float, val hp: Float, val maxHp: Float,
    val facingRight: Boolean, val walkAnim: Float, val attackAnim: Float,
    val hurtFlash: Float, val freezeTimer: Float, val stunTimer: Float, val tamed: Boolean
)
data class DropSyncSnap(val id: Int, val itemId: Int, val count: Int, val x: Float, val y: Float, val z: Float)

class EntityAndWeatherManager {
    val mobs = CopyOnWriteArrayList<MobEntity>()
    val drops = CopyOnWriteArrayList<DroppedItem>()
    val projectiles = CopyOnWriteArrayList<ProjectileIso>()
    val particles = CopyOnWriteArrayList<ParticleIso>()
    val slashes = CopyOnWriteArrayList<SlashArc>()
    val dashGhosts = CopyOnWriteArrayList<DashGhost>()
    val floatingTexts = CopyOnWriteArrayList<FloatingText>()
    var foxPet: MobEntity? = null
    var owlPet: MobEntity? = null
    var golemPet: MobEntity? = null

    // Автономные сезоны (60 дней) + плавный 12-минутный цикл суток (во сне ускорение x2.6 -> ночь за ~2.3 мин!)
    var worldTime = 0.28f
    var dayCount = 1
    var seasonLengthDays = 60
    var dayFactor = 1f
    var weather = WeatherType.CLEAR
    var lightningFlash = 0f

    private var weatherTimer = 90f
    private var lightningTimer = 6f
    private var spawnTimer = 5.0f
    private var turretTimer = 0f
    private var trapTimer = 0f
    var isBloodMoon = false
    private var lastBloodMoonDay = -1
    private var seasonWorldTimer = 0f
    private var propFxTimer = 0f
    private var nextId = 1

    val season: Season
        get() = Season.values()[((max(1, dayCount) - 1) / max(1, seasonLengthDays)) % 4]

    fun seasonDay(): Int = ((max(1, dayCount) - 1) % max(1, seasonLengthDays)) + 1
    fun seasonProgress(): Float = seasonDay().toFloat() / max(1, seasonLengthDays).toFloat()

    fun spawnMob(type: String, x: Float, y: Float, z: Float): MobEntity {
        val isBoss = type in setOf("inferno_lord", "sky_guardian", "grove_guardian", "frost_titan", "depth_devourer")
        val hostile = type !in setOf("sheep", "cow", "pig", "chicken", "fox_pet", "deer", "boar", "wolf", "bee", "trader", "owl_pet", "golem_pet")
        val maxHp = when (type) {
            "depth_devourer" -> 220f; "frost_titan" -> 190f; "grove_guardian" -> 170f
            "inferno_lord" -> 160f; "sky_guardian" -> 145f
            "crystal_golem" -> 58f; "yeti" -> 48f; "mimic" -> 42f; "enderman" -> 34f
            "piglin", "ghast", "magma_slime" -> 24f; "zombie", "creeper", "blaze", "goblin", "slime" -> 20f
            "skeleton", "boar", "wolf" -> 18f; "spider", "deer" -> 16f; "cow" -> 12f
            "mini_slime", "bat", "bee", "chicken" -> 7f
            else -> 10f
        }
        val title = when (type) {
            "inferno_lord" -> "Повелитель Преисподней"; "sky_guardian" -> "Страж Небес"
            "grove_guardian" -> "Древний Страж Рощи"; "frost_titan" -> "Морозный Титан"; "depth_devourer" -> "Пожиратель Глубин"
            "sheep" -> "Овца"; "cow" -> "Корова"; "pig" -> "Свинья"; "chicken" -> "Курица"
            "deer" -> "Олень"; "boar" -> "Кабан"; "wolf" -> "Волк"; "bee" -> "Пчела"; "trader" -> "Торговец"; "owl_pet" -> "Сова-разведчик"; "golem_pet" -> "Голем-сундук"
            "goblin" -> "Гоблин"; "slime" -> "Слайм"; "mini_slime" -> "Мини-Слайм"; "yeti" -> "Ледяной Йети"
            "bat" -> "Летучая Мышь"; "crystal_golem" -> "Кристальный Голем"; "magma_slime" -> "Магмовый Слайм"; "mimic" -> "Сундук-Мимик"
            "zombie" -> "Зомби"; "skeleton" -> "Скелет"; "creeper" -> "Крипер"; "spider" -> "Паук"
            "enderman" -> "Эндермен"; "piglin" -> "Пиглин"; "blaze" -> "Ифрит"; "ghast" -> "Гаст"
            else -> type
        }
        val deep = hostile && z < 56f
        val finalHp = if (deep) (maxHp * 1.30f) else maxHp
        val m = MobEntity(nextId++, type, title, hostile, isBoss, x, y, z, hp = finalHp, maxHp = finalHp, isDeepMob = deep)
        mobs.add(m)
        return m
    }

    fun toggleFoxPet(player: PlayerState): Boolean {
        if (foxPet != null) { foxPet = null; return false }
        foxPet = MobEntity(-99, "fox_pet", "Лисёнок", false, false, player.x + 1.1f, player.y + 0.5f, player.z, hp = 40f, maxHp = 40f, tamed = true)
        return true
    }

    fun toggleOwlPet(player: PlayerState): Boolean {
        if (owlPet != null) { owlPet = null; return false }
        owlPet = MobEntity(-98, "owl_pet", "Сова-разведчик", false, false, player.x - 1.2f, player.y + 1.0f, player.z + 1.4f, hp = 999f, maxHp = 999f, tamed = true)
        return true
    }

    fun toggleGolemPet(player: PlayerState): Boolean {
        if (golemPet != null) { golemPet = null; return false }
        golemPet = MobEntity(-97, "golem_pet", "Голем-сундук", false, false, player.x + 1.4f, player.y - 1.2f, player.z, hp = 999f, maxHp = 999f, tamed = true)
        return true
    }

    fun spawnDrop(itemId: Int, count: Int, x: Float, y: Float, z: Float, vx: Float = 0f, vy: Float = 0f) {
        if (itemId <= 0 || count <= 0) return
        drops.add(DroppedItem(nextId++, itemId, count, x, y, z + 0.35f, vx, vy, 3.6f))
    }

    fun spawnBlockParticles(wx: Int, wy: Int, wz: Int, colorHex: Int, count: Int = 7) {
        for (i in 0 until count) {
            particles.add(
                ParticleIso(
                    wx + 0.5f + (Random.nextFloat() - 0.5f) * 0.4f,
                    wy + 0.5f + (Random.nextFloat() - 0.5f) * 0.4f,
                    wz + 0.6f,
                    (Random.nextFloat() - 0.5f) * 2.8f,
                    (Random.nextFloat() - 0.5f) * 2.8f,
                    3.0f + Random.nextFloat() * 3.0f,
                    colorHex, 6f + Random.nextFloat() * 4f, 0.45f
                )
            )
        }
    }

    fun spawnRisingSpark(x: Float, y: Float, z: Float, colorHex: Int, size: Float = 5f) {
        particles.add(
            ParticleIso(
                x + (Random.nextFloat() - 0.5f) * 0.25f,
                y + (Random.nextFloat() - 0.5f) * 0.25f,
                z,
                (Random.nextFloat() - 0.5f) * 0.45f,
                (Random.nextFloat() - 0.5f) * 0.45f,
                1.2f + Random.nextFloat() * 1.1f,
                colorHex, size, 0.75f, floatUp = true
            )
        )
    }

    fun fireProjectile(x: Float, y: Float, z: Float, dx: Float, dy: Float, dmg: Float, fiery: Boolean, frosty: Boolean, fromPlayer: Boolean, isGrapple: Boolean = false, isTeleportPearl: Boolean = false) {
        val len = max(0.01f, hypot(dx.toDouble(), dy.toDouble()).toFloat())
        val spd = if (isGrapple || isTeleportPearl) 17f else if (fromPlayer) 14f else 8.5f
        projectiles.add(ProjectileIso(x, y, z + 0.5f, dx / len * spd, dy / len * spd, dmg, fiery, frosty, fromPlayer, isGrapple, isTeleportPearl))
    }

    fun getActiveBoss(): MobEntity? = mobs.firstOrNull { it.isBoss }

    fun applySyncedMobs(snaps: List<MobSyncSnap>) {
        val byId = mobs.associateBy { it.id }
        val keep = HashSet<Int>(snaps.size)
        for (s in snaps) {
            keep.add(s.id)
            val ex = byId[s.id]
            if (ex != null) {
                val jump = hypot((s.x - ex.x).toDouble(), (s.y - ex.y).toDouble())
                if (jump > 4.5) { ex.x = s.x; ex.y = s.y; ex.z = s.z }
                ex.netTargetX = s.x; ex.netTargetY = s.y; ex.netTargetZ = s.z; ex.hasNetTarget = true
                ex.hp = s.hp; ex.maxHp = s.maxHp
                ex.facingRight = s.facingRight; ex.walkAnim = s.walkAnim; ex.attackAnim = s.attackAnim
                ex.hurtFlash = s.hurtFlash; ex.freezeTimer = s.freezeTimer; ex.stunTimer = s.stunTimer
                ex.hostile = s.hostile; ex.tamed = s.tamed
            } else {
                mobs.add(MobEntity(s.id, s.type, s.name, s.hostile, s.isBoss, s.x, s.y, s.z,
                    facingRight = s.facingRight, hp = s.hp, maxHp = s.maxHp, walkAnim = s.walkAnim,
                    attackAnim = s.attackAnim, hurtFlash = s.hurtFlash, freezeTimer = s.freezeTimer,
                    stunTimer = s.stunTimer, tamed = s.tamed))
            }
        }
        mobs.removeIf { !keep.contains(it.id) }
    }

    fun applySyncedDrops(snaps: List<DropSyncSnap>) {
        val byId = drops.associateBy { it.id }
        val keep = HashSet<Int>(snaps.size)
        for (s in snaps) {
            keep.add(s.id)
            val ex = byId[s.id]
            if (ex != null) { ex.x = s.x; ex.y = s.y; ex.z = s.z }
            else drops.add(DroppedItem(s.id, s.itemId, s.count, s.x, s.y, s.z, 0f, 0f, 0f, age = 0.6f))
        }
        drops.removeIf { !keep.contains(it.id) }
    }

    fun barterPiglin(m: MobEntity): Int {
        m.barterCd = 25f
        m.hostile = false
        m.attackAnim = 1.0f
        val lootTable = intArrayOf(104, 118, 141, 142, 116, 22, 124, 138)
        val reward = lootTable[Random.nextInt(lootTable.size)]
        val cnt = if (reward == 124) 8 else if (reward == 22) 2 else 1
        spawnDrop(reward, cnt, m.x, m.y, m.z)
        floatingTexts.add(FloatingText(m.x, m.y, m.z + 1.5f, "СДЕЛКА!", Color.GOLD))
        return reward
    }

    fun feedPeacefulMob(m: MobEntity): String {
        m.hp = m.maxHp
        m.breedCd = 35f
        floatingTexts.add(FloatingText(m.x, m.y, m.z + 1.4f, "<3", Color(1f, 0.42f, 0.68f, 1f)))
        return when (m.type) {
            "sheep" -> { spawnDrop(55, 2, m.x, m.y, m.z); "Овца дала 2 Алых ковра!" }
            "cow" -> { spawnDrop(136, 1, m.x, m.y, m.z); "Корова дала тёплое молоко (чай)!" }
            "chicken" -> { spawnDrop(124, 4, m.x, m.y, m.z); "Курица дала перья (4 стрелы)!" }
            else -> {
                if (mobs.size < 16) spawnMob(m.type, m.x + 0.8f, m.y + 0.4f, m.z)
                "Появился новый ${m.name}!"
            }
        }
    }

    fun damageMob(m: MobEntity, dmg: Float, kbX: Float, kbY: Float, frostHit: Boolean = false, onKill: (MobEntity) -> Unit) {
        if (m.tamed || m.type in setOf("trader", "owl_pet", "golem_pet")) return
        m.hp -= dmg
        m.hurtFlash = 0.22f
        m.hurtSquash = 1.0f
        if (!m.hostile) m.fleeTimer = 4.2f
        m.vx += kbX * 6f; m.vy += kbY * 6f
        if (m.type == "boar" || m.type == "wolf" || m.type == "bee") m.hostile = true
        if (m.type == "zombie") {
            // Зомби сзывает ближайших зомби на помощь!
            for (other in mobs) {
                if (other.type == "zombie" && hypot((other.x - m.x).toDouble(), (other.y - m.y).toDouble()) < 8.0) {
                    other.hostile = true
                }
            }
        }
        if (frostHit) m.freezeTimer = 3.5f
        floatingTexts.add(FloatingText(m.x, m.y, m.z + 1.4f, "-${dmg.toInt()}", if (frostHit) Color(0.4f, 0.9f, 1f, 1f) else Color(1f, 0.88f, 0.35f, 1f)))
        if (m.type == "enderman" && m.hp > 0f && Random.nextFloat() < 0.45f) {
            m.x += (Random.nextFloat() - 0.5f) * 6f; m.y += (Random.nextFloat() - 0.5f) * 6f
        }
        if (m.hp <= 0f) {
            mobs.remove(m)
            if (m.type == "slime") {
                spawnMob("mini_slime", m.x + 0.5f, m.y, m.z)
                spawnMob("mini_slime", m.x - 0.5f, m.y, m.z)
            }
            if (m.stolenItemId > 0) spawnDrop(m.stolenItemId, 1, m.x, m.y, m.z)
            onKill(m)
        }
    }

    fun update(
        dt: Float,
        world: WorldIso,
        player: PlayerState,
        isNetClient: Boolean,
        onHurtPlayer: (Float, MobEntity?) -> Unit,
        onExplode: (Int, Int, Int, Float) -> Unit,
        onMobKill: (MobEntity) -> Unit,
        onPickup: (Int, Int, Int) -> Unit,
        onGrapplePull: (Float, Float) -> Unit,
        onWakeUpMorning: () -> Unit
    ) {
        val curSeason = season
        if (world.dimension == "overworld") {
            // Полный цикл суток = 720 сек (12 мин: день 6 мин, ночь 6 мин).
            // Когда герой ЛЕЖИТ НА КРОВАТИ (isSleeping), время ускоряется в 2.6 раза -> ночь проходит за ~2.3 минуты!
            val timeMult = if (player.isSleeping) 4.5f else 1.0f
            val prevTime = worldTime
            worldTime += (dt * timeMult) / 1440f
            if (worldTime >= 1f) { worldTime -= 1f; dayCount++ }
            dayFactor = max(0f, sin(worldTime * PI.toFloat() * 2f))

            if (player.isSleeping) {
                player.sleepElapsed += dt
                player.zzzTimer += dt
                if (player.zzzTimer >= 1.15f) {
                    player.zzzTimer = 0f
                    floatingTexts.add(FloatingText(player.x, player.y, player.z + 0.8f, "Zzz", Color(0.75f, 0.88f, 1f, 1f), life = 1.4f))
                }
                // Мягкое лечение и согревание во сне!
                player.hp = min(player.maxHp, player.hp + dt * 0.45f)
                player.bodyTemp = min(90f, player.bodyTemp + dt * 4f)
                // Автопробуждение при наступлении рассвета (после хотя бы 6 сек сна)
                if (player.sleepElapsed > 6f && prevTime < 0.24f && worldTime >= 0.24f) {
                    player.isSleeping = false
                    player.sleepElapsed = 0f
                    onWakeUpMorning()
                }
            }

            weatherTimer -= dt
            if (weatherTimer <= 0f) {
                weatherTimer = 75f + Random.nextFloat() * 80f
                val r = Random.nextFloat()
                weather = when (curSeason) {
                    Season.WINTER -> if (r < 0.58f) WeatherType.SNOW else WeatherType.CLEAR
                    Season.SPRING -> if (r < 0.48f) WeatherType.RAIN else if (r < 0.62f) WeatherType.STORM else WeatherType.CLEAR
                    Season.AUTUMN -> if (r < 0.46f) WeatherType.RAIN else if (r < 0.68f) WeatherType.STORM else WeatherType.CLEAR
                    Season.SUMMER -> if (r < 0.22f) WeatherType.STORM else if (r < 0.34f) WeatherType.RAIN else WeatherType.CLEAR
                }
            }
            if (curSeason == Season.WINTER && weather == WeatherType.RAIN) weather = WeatherType.SNOW
            if (curSeason != Season.WINTER && weather == WeatherType.SNOW) weather = WeatherType.RAIN

            if (weather == WeatherType.STORM) {
                lightningTimer -= dt
                if (lightningTimer <= 0f) { lightningTimer = 4f + Random.nextFloat() * 7f; lightningFlash = 1f }
            }
            if (lightningFlash > 0f) lightningFlash = max(0f, lightningFlash - dt * 3.5f)

            seasonWorldTimer += dt
            if (seasonWorldTimer >= 0.45f) {
                seasonWorldTimer = 0f
                world.applySeasonalEnvironmentStep(curSeason, seasonProgress(), floor(player.x).toInt(), floor(player.y).toInt())
            }
        } else {
            dayFactor = if (world.dimension == "aether") 1f else 0.55f
        }

        // Живые частицы от Костров, Факелов, Порталов и Камней Телепорта рядом с игроком!
        propFxTimer += dt
        if (propFxTimer >= 0.22f) {
            propFxTimer = 0f
            val px = floor(player.x).toInt(); val py = floor(player.y).toInt(); val pz = floor(player.z).toInt()
            for (dx in -6..6) for (dy in -6..6) for (dz in -1..2) {
                val b = world.getBlock(px + dx, py + dy, pz + dz)
                when (b) {
                    59 -> if (Random.nextFloat() < 0.55f) spawnRisingSpark(px + dx + 0.5f, py + dy + 0.5f, pz + dz + 0.6f, if (Random.nextBoolean()) 0xFF9828 else 0xFFE268, 6f)
                    37 -> if (Random.nextFloat() < 0.30f) spawnRisingSpark(px + dx + 0.5f, py + dy + 0.5f, pz + dz + 0.8f, 0xFFBC42, 4.5f)
                    34 -> if (Random.nextFloat() < 0.50f) spawnRisingSpark(px + dx + 0.5f, py + dy + 0.5f, pz + dz + 0.5f, 0xC858FF, 5.5f)
                    61 -> if (Random.nextFloat() < 0.35f) spawnRisingSpark(px + dx + 0.5f, py + dy + 0.5f, pz + dz + 0.7f, 0x56E2FF, 5f)
                }
            }
        }

        // Дроп
        for (d in drops) {
            d.age += dt
            if (d.age > 180f) { drops.remove(d); continue }
            d.vz -= 12f * dt; d.z += d.vz * dt
            val gz = world.getGroundZ(floor(d.x).toInt(), floor(d.y).toInt(), d.z) ?: d.z
            if (d.z < gz) { d.z = gz; d.vz = 0f; d.vx *= 0.8f; d.vy *= 0.8f }
            d.x += d.vx * dt; d.y += d.vy * dt
            val dist = hypot((player.x - d.x).toDouble(), (player.y - d.y).toDouble()).toFloat()
            val pickupRad = if (player.hasMagnet()) 5.8f else 2.2f
            if (d.age > 0.45f && dist < pickupRad && abs(player.z - d.z) < 2.8f) {
                d.x += (player.x - d.x) * min(1f, dt * 8f)
                d.y += (player.y - d.y) * min(1f, dt * 8f)
                if (dist < 0.75f) { onPickup(d.id, d.itemId, d.count); drops.remove(d) }
            }
        }

        // Снаряды
        for (pr in projectiles) {
            pr.life -= dt
            pr.x += pr.vx * dt; pr.y += pr.vy * dt
            val bx = floor(pr.x).toInt(); val by = floor(pr.y).toInt(); val bz = floor(pr.z).toInt()
            var hit = BlockRegistry.items[world.getBlock(bx, by, bz)]?.solid == true
            if (hit && pr.isGrapple) { onGrapplePull(pr.x, pr.y); projectiles.remove(pr); continue }
            if (pr.isTeleportPearl && (hit || pr.life <= 0.1f)) {
                val gz = world.getGroundZ(bx, by, pr.z)
                if (gz != null) { player.x = bx + 0.5f; player.y = by + 0.5f; player.z = gz; spawnBlockParticles(bx, by, gz.toInt(), 0x24BAB2, 10); SoundFx.play("levelup", 0.55f) }
                projectiles.remove(pr); continue
            }
            if (pr.fromPlayer && !pr.isGrapple && !pr.isTeleportPearl) {
                for (m in mobs) {
                    if (abs(pr.z - m.z) < 2.0f && hypot((pr.x - m.x).toDouble(), (pr.y - m.y).toDouble()) < (if (m.isBoss) 1.4 else 0.8)) {
                        damageMob(m, pr.dmg, pr.vx * 0.08f, pr.vy * 0.08f, pr.frosty, onMobKill); hit = true; break
                    }
                }
            } else if (!pr.fromPlayer) {
                if (abs(pr.z - player.z) < 2.0f && hypot((pr.x - player.x).toDouble(), (pr.y - player.y).toDouble()) < 0.75) {
                    if (pr.frosty) player.bodyTemp = max(0f, player.bodyTemp - 18f)
                    onHurtPlayer(pr.dmg, null); hit = true
                }
            }
            if (hit || pr.life <= 0f) {
                if (pr.fiery && hit && !isNetClient) onExplode(bx, by, max(1, bz), 2.0f)
                projectiles.remove(pr)
            }
        }

        // Частицы, дуги атак, шлейфы рывка и всплывающий текст (Zzz / Урон)
        for (p in particles) {
            p.life -= dt
            if (!p.floatUp) p.vz -= 12f * dt
            p.x += p.vx * dt; p.y += p.vy * dt; p.z = max(0f, p.z + p.vz * dt)
            if (p.life <= 0f) particles.remove(p)
        }
        for (s in slashes) { s.life -= dt; if (s.life <= 0f) slashes.remove(s) }
        for (dg in dashGhosts) { dg.life -= dt; if (dg.life <= 0f) dashGhosts.remove(dg) }
        for (ft in floatingTexts) { ft.life -= dt; ft.z += dt * 1.1f; if (ft.life <= 0f) floatingTexts.remove(ft) }

        // Союзники (Питомец-Лис + Прирученные Волки)
        fun tickAlly(pt: MobEntity, offX: Float, offY: Float) {
            pt.atkCd = max(0f, pt.atkCd - dt)
            if (pt.attackAnim > 0f) pt.attackAnim = max(0f, pt.attackAnim - dt * 4f)
            val target = mobs.filter { it.hostile && !it.tamed && abs(it.z - pt.z) < 3f }
                .minByOrNull { hypot((it.x - pt.x).toDouble(), (it.y - pt.y).toDouble()) }
            val gx = if (target != null && hypot((target.x - pt.x).toDouble(), (target.y - pt.y).toDouble()) < 10.0) target.x else player.x + offX
            val gy = if (target != null && hypot((target.x - pt.x).toDouble(), (target.y - pt.y).toDouble()) < 10.0) target.y else player.y + offY
            val distP = hypot((player.x - pt.x).toDouble(), (player.y - pt.y).toDouble())
            val distG = hypot((gx - pt.x).toDouble(), (gy - pt.y).toDouble()).toFloat()
            if (distP > 16.0 || abs(player.z - pt.z) > 6f) { pt.x = player.x + offX; pt.y = player.y + offY; pt.z = player.z }
            else if (distG > 0.9f) {
                val ang = atan2((gy - pt.y).toDouble(), (gx - pt.x).toDouble()).toFloat()
                val nx = pt.x + cos(ang) * 4.7f * dt; val ny = pt.y + sin(ang) * 4.7f * dt
                val gz = world.getGroundZ(floor(nx).toInt(), floor(ny).toInt(), pt.z)
                if (gz != null) { pt.x = nx; pt.y = ny; pt.z += (gz - pt.z) * min(1f, dt * 12f) }
                pt.facingRight = (cos(ang) - sin(ang)) >= 0f
                pt.walkAnim += dt * 11f
            }
            if (target != null && distG < 1.35f && pt.atkCd <= 0f) {
                pt.atkCd = 0.9f; pt.attackAnim = 1.0f
                damageMob(target, if (pt.type == "wolf") 8f else 6f, target.x - pt.x, target.y - pt.y, false, onMobKill)
            }
        }
        foxPet?.let { tickAlly(it, 1.1f, 0.5f) }
        owlPet?.let { ow ->
            val gx = player.x - 1.1f + cos(ow.idlePhase * 0.7f) * 0.6f
            val gy = player.y + 1.1f + sin(ow.idlePhase * 0.7f) * 0.6f
            val gz = player.z + 1.45f
            val d = hypot((gx - ow.x).toDouble(), (gy - ow.y).toDouble())
            if (d > 14.0 || abs(gz - ow.z) > 8f) { ow.x = gx; ow.y = gy; ow.z = gz }
            else {
                ow.x += (gx - ow.x) * min(1f, dt * 4.5f)
                ow.y += (gy - ow.y) * min(1f, dt * 4.5f)
                ow.z += (gz - ow.z) * min(1f, dt * 4.5f)
            }
            ow.facingRight = ((gx - ow.x) - (gy - ow.y)) >= 0f
            ow.walkAnim += dt * 10f
        }
        golemPet?.let { gl ->
            val gx = player.x + 1.35f; val gy = player.y - 1.15f
            val d = hypot((gx - gl.x).toDouble(), (gy - gl.y).toDouble()).toFloat()
            if (d > 12f || abs(player.z - gl.z) > 4.5f) { gl.x = gx; gl.y = gy; gl.z = player.z }
            else if (d > 1.1f) {
                val ang = atan2((gy - gl.y).toDouble(), (gx - gl.x).toDouble()).toFloat()
                val nx = gl.x + cos(ang) * 4.4f * dt; val ny = gl.y + sin(ang) * 4.4f * dt
                val gz = world.getGroundZ(floor(nx).toInt(), floor(ny).toInt(), gl.z)
                if (gz != null && abs(gz - gl.z) <= 1.5f) { gl.x = nx; gl.y = ny; gl.z += (gz - gl.z) * min(1f, dt * 10f) }
                else { gl.x += (gx - gl.x) * dt * 2.5f; gl.y += (gy - gl.y) * dt * 2.5f; gl.z = player.z }
                gl.facingRight = (cos(ang) - sin(ang)) >= 0f
                gl.walkAnim += dt * 9f
            }
        }

        if (isNetClient) {
            val k = min(1f, dt * 12f)
            for (m in mobs) {
                if (m.hasNetTarget) {
                    val dx = m.netTargetX - m.x; val dy = m.netTargetY - m.y
                    m.vx = dx * 6f; m.vy = dy * 6f
                    m.x += dx * k; m.y += dy * k; m.z += (m.netTargetZ - m.z) * k
                }
                if (m.hurtFlash > 0f) m.hurtFlash = max(0f, m.hurtFlash - dt)
                if (m.attackAnim > 0f) m.attackAnim = max(0f, m.attackAnim - dt * 4f)
            }
            return
        }

        // Кровавая Луна (раз в 7 ночей — контролируемый набег на лагерь!)
        val bloodNow = world.dimension == "overworld" && dayCount >= 7 && (dayCount % 7 == 0) && dayFactor < 0.25f
        isBloodMoon = bloodNow
        if (bloodNow && lastBloodMoonDay != dayCount) {
            lastBloodMoonDay = dayCount
            floatingTexts.add(FloatingText(player.x, player.y, player.z + 2.0f, "КРОВАВАЯ ЛУНА!", Color(1f, 0.2f, 0.25f, 1f), 2.5f))
            SoundFx.play("boss", 0.7f)
        }

        // Авто-арбалеты (Турели 74) защищают базу и стреляют по ближайшим врагам!
        turretTimer += dt
        if (turretTimer >= 1.25f) {
            turretTimer = 0f
            val pxI = floor(player.x).toInt(); val pyI = floor(player.y).toInt(); val pzI = floor(player.z).toInt()
            for (dx in -7..7) for (dy in -7..7) for (dz in -1..2) {
                if (world.getBlock(pxI + dx, pyI + dy, pzI + dz) == 74) {
                    val tx = pxI + dx + 0.5f; val ty = pyI + dy + 0.5f; val tz = (pzI + dz).toFloat()
                    val tgt = mobs.filter { it.hostile && !it.tamed && abs(it.z - tz) < 3f && hypot((it.x - tx).toDouble(), (it.y - ty).toDouble()) < 10.0 }
                        .minByOrNull { hypot((it.x - tx).toDouble(), (it.y - ty).toDouble()) }
                    if (tgt != null) {
                        fireProjectile(tx, ty, tz, tgt.x - tx, tgt.y - ty, 9f, false, false, true)
                        SoundFx.play("hit", 0.35f)
                    }
                }
            }
        }

        // Шипы-ловушки (73) наносят урон врагам, наступившим на них!
        trapTimer += dt
        if (trapTimer >= 0.55f) {
            trapTimer = 0f
            for (m in mobs) {
                if (m.hostile && !m.tamed && world.getBlock(floor(m.x).toInt(), floor(m.y).toInt(), floor(m.z).toInt()) == 73) {
                    damageMob(m, 5f, 0f, 0f, false, onMobKill)
                    spawnBlockParticles(floor(m.x).toInt(), floor(m.y).toInt(), floor(m.z).toInt(), 0xE82848, 4)
                }
            }
        }

        // Спокойный, не перегруженный спавн мобов (макс 7 мобов в мире, макс 3 врага обычно / 5 в Кровавую Луну!)
        spawnTimer -= dt
        val maxTotalMobs = if (bloodNow) 9 else 7
        val maxHostileMobs = if (bloodNow) 5 else 3
        val curHostiles = mobs.count { it.hostile && !it.tamed }
        if (spawnTimer <= 0f && mobs.size < maxTotalMobs) {
            spawnTimer = if (bloodNow) 4.0f else 6.8f
            val ang = Random.nextFloat() * PI.toFloat() * 2f
            val dist = 9.5f + Random.nextFloat() * 6.5f
            val sx = floor(player.x + cos(ang) * dist).toInt()
            val sy = floor(player.y + sin(ang) * dist).toInt()
            val pz = floor(player.z).toInt()
            val isUnderground = world.dimension == "overworld" && pz < world.getSurfaceZ(sx, sy) - 3
            val spawnZ = if (isUnderground) world.getGroundZ(sx, sy, player.z) else (world.getTopBlockZ(sx, sy) + 1).toFloat()

            if (spawnZ != null) {
                val sz = spawnZ.toInt()
                val underBlock = world.getBlock(sx, sy, max(0, sz - 1))
                if (BlockRegistry.items[underBlock]?.solid == true) {
                    val type = when {
                        world.dimension == "nether" -> if (getActiveBoss() == null && Random.nextFloat() < 0.14f) "inferno_lord" else listOf("piglin", "blaze", "ghast", "magma_slime").random()
                        world.dimension == "aether" -> if (getActiveBoss() == null && Random.nextFloat() < 0.16f) "sky_guardian" else listOf("sheep", "bee").random()
                        isUnderground -> when {
                            sz < 26 -> if (getActiveBoss() == null && Random.nextFloat() < 0.14f) "depth_devourer" else listOf("crystal_golem", "magma_slime", "enderman", "bat").random()
                            sz < 60 -> listOf("crystal_golem", "bat", "skeleton", "spider", "slime").random()
                            else -> listOf("bat", "zombie", "skeleton", "creeper", "goblin", "slime").random()
                        }
                        curSeason == Season.WINTER && weather == WeatherType.SNOW -> {
                            if (getActiveBoss() == null && Random.nextFloat() < 0.10f) "frost_titan"
                            else listOf("yeti", "wolf", "deer", "skeleton").random()
                        }
                        dayFactor < 0.25f -> listOf("zombie", "skeleton", "creeper", "spider", "goblin", "slime", "enderman").random()
                        curSeason == Season.SUMMER -> listOf("sheep", "cow", "pig", "chicken", "deer", "boar", "wolf", "bee").random()
                        else -> listOf("sheep", "cow", "pig", "chicken", "deer", "boar", "wolf").random()
                    }
                    val wouldBeHostile = type !in setOf("sheep", "cow", "pig", "chicken", "deer", "boar", "wolf", "bee", "trader", "owl_pet", "golem_pet")
                    if (!wouldBeHostile || curHostiles < maxHostileMobs) {
                        spawnMob(type, sx + 0.5f, sy + 0.5f, spawnZ)
                    }
                }
            }
        }

        // ИИ всех мобов и 5 Боссов + живые фазы прыжков и замахов!
        for (m in mobs) {
            if (m.tamed) { tickAlly(m, -1.0f, 0.8f); continue }
            val distP = hypot((player.x - m.x).toDouble(), (player.y - m.y).toDouble()).toFloat()
            if ((m.type != "trader" && distP > 32f) || distP > 48f || abs(player.z - m.z) > 24f) { mobs.remove(m); continue }
            m.aiTimer -= dt; m.atkCd = max(0f, m.atkCd - dt)
            if (m.hurtFlash > 0f) m.hurtFlash -= dt
            if (m.hurtSquash > 0f) m.hurtSquash = max(0f, m.hurtSquash - dt * 5f)
            if (m.attackAnim > 0f) m.attackAnim = max(0f, m.attackAnim - dt * 4f)
            if (m.freezeTimer > 0f) m.freezeTimer = max(0f, m.freezeTimer - dt)
            if (m.barterCd > 0f) { m.barterCd = max(0f, m.barterCd - dt); if (m.barterCd <= 0f && m.type == "piglin") m.hostile = true }
            if (m.breedCd > 0f) m.breedCd = max(0f, m.breedCd - dt)
            if (m.stunTimer > 0f) { m.stunTimer = max(0f, m.stunTimer - dt); continue }

            // Летучая Мышь боится яркого факела/фонаря в руках игрока и улетает!
            if (m.type == "bat" && distP < 5.5f) {
                val heldLight = (BlockRegistry.items[player.slots[player.selectedSlot]?.id ?: 0]?.lightRadius ?: 0f) > 0f ||
                    (BlockRegistry.items[player.offhand?.id ?: 0]?.lightRadius ?: 0f) > 0f
                if (heldLight) {
                    val flee = atan2((m.y - player.y).toDouble(), (m.x - player.x).toDouble()).toFloat()
                    m.vx = cos(flee) * 4.6f; m.vy = sin(flee) * 4.6f
                    m.x += m.vx * dt; m.y += m.vy * dt; continue
                }
            }
            // Пчела собирает пыльцу у цветов и изредка оставляет ягоды/мёд!
            if (m.type == "bee" && !m.hostile && m.aiTimer <= 0.05f && Random.nextFloat() < 0.08f) {
                if (world.getBlock(floor(m.x).toInt(), floor(m.y).toInt(), floor(m.z).toInt()) == 68) {
                    spawnDrop(144, 1, m.x, m.y, m.z)
                }
            }

            val slowMult = if (m.freezeTimer > 0f) 0.42f else 1.0f

            if (m.type == "goblin" && m.stolenItemId == 0) {
                val nearDrop = drops.minByOrNull { hypot((it.x - m.x).toDouble(), (it.y - m.y).toDouble()) }
                if (nearDrop != null && hypot((nearDrop.x - m.x).toDouble(), (nearDrop.y - m.y).toDouble()) < 7.0) {
                    val ang = atan2((nearDrop.y - m.y).toDouble(), (nearDrop.x - m.x).toDouble()).toFloat()
                    m.vx = cos(ang) * 3.8f * slowMult; m.vy = sin(ang) * 3.8f * slowMult
                    if (hypot((nearDrop.x - m.x).toDouble(), (nearDrop.y - m.y).toDouble()) < 0.85) {
                        m.stolenItemId = nearDrop.itemId; drops.remove(nearDrop)
                        floatingTexts.add(FloatingText(m.x, m.y, m.z + 1.3f, "УКРАЛ!", Color.YELLOW))
                    }
                }
            }

            val aggro = m.hostile && !player.creative && distP < (if (m.isBoss) 18f else 11f) && abs(player.z - m.z) < 4.5f
            if (aggro) {
                val ang = atan2((player.y - m.y).toDouble(), (player.x - m.x).toDouble()).toFloat()
                val ranged = m.type in setOf("skeleton", "blaze", "ghast", "inferno_lord", "sky_guardian", "frost_titan")
                val zombieNightBoost = if (m.type == "zombie" && dayFactor < 0.25f) 1.32f else 1.0f
                val spd = (if (m.isBoss) 3.0f else if (m.type in setOf("spider", "enderman", "goblin", "bat")) 3.4f else 2.3f) * slowMult * zombieNightBoost

                if (m.type == "grove_guardian" && m.atkCd <= 0f && distP < 12f) {
                    m.atkCd = 3.2f; m.attackAnim = 1.0f
                    if (distP < 3.6f) onHurtPlayer(6f, m)
                    if (mobs.size < 16) spawnMob("mini_slime", m.x + 1.2f, m.y, m.z)
                    spawnBlockParticles(floor(m.x).toInt(), floor(m.y).toInt(), floor(m.z).toInt(), 0x38EE7A, 10)
                } else if (m.type == "frost_titan" && m.atkCd <= 0f && distP < 12f) {
                    m.atkCd = 2.0f; m.attackAnim = 1.0f
                    fireProjectile(m.x, m.y, m.z, player.x - m.x, player.y - m.y, 5f, false, true, false)
                    world.setBlock(floor(m.x).toInt(), floor(m.y).toInt(), max(1, floor(m.z - 0.5f).toInt()), 20)
                } else if (m.type == "depth_devourer" && distP < 10f && m.atkCd <= 0f) {
                    val fx = floor(m.x + cos(ang) * 1.2f).toInt(); val fy = floor(m.y + sin(ang) * 1.2f).toInt(); val fz = floor(m.z).toInt()
                    val frontB = world.getBlock(fx, fy, fz)
                    if (frontB > 0 && frontB != 10 && BlockRegistry.items[frontB]?.solid == true) {
                        world.setBlock(fx, fy, fz, 0); spawnBlockParticles(fx, fy, fz, 0xE82848, 8)
                    }
                    if (distP < 1.8f) { m.atkCd = 1.2f; m.attackAnim = 1.0f; onHurtPlayer(7f, m) }
                } else if (m.type == "yeti" && distP in 3.2f..9.5f && m.atkCd <= 0f) {
                    // Ледяной Йети швыряет глыбу льда издалека!
                    m.atkCd = 2.6f; m.attackAnim = 1.0f
                    fireProjectile(m.x, m.y, m.z, player.x - m.x, player.y - m.y, 4.5f, false, true, false)
                } else if (m.type == "crystal_golem" && distP < 3.4f && m.atkCd <= 0f) {
                    // Кристальный Голем бьёт сейсмическим ударом по площади!
                    m.atkCd = 3.2f; m.attackAnim = 1.0f
                    onHurtPlayer(6.5f, m)
                    spawnBlockParticles(floor(m.x).toInt(), floor(m.y).toInt(), floor(m.z).toInt(), 0xC662FF, 12)
                } else if (m.type == "spider" && distP in 2.2f..5.2f && m.atkCd <= 0f) {
                    // Паук делает резкий прыжок-бросок на игрока!
                    m.atkCd = 2.4f; m.attackAnim = 1.0f
                    m.vx = cos(ang) * 9.5f; m.vy = sin(ang) * 9.5f
                } else if (m.type == "magma_slime" && distP < 1.5f && m.atkCd <= 0f) {
                    // Магмовый слайм обжигает искрами лавы!
                    m.atkCd = 1.2f; m.attackAnim = 1.0f
                    onHurtPlayer(4.5f, m)
                    spawnBlockParticles(floor(m.x).toInt(), floor(m.y).toInt(), floor(m.z).toInt(), 0xFF6818, 8)
                } else if (ranged && distP < 9.5f && m.atkCd <= 0f) {
                    m.atkCd = if (m.isBoss) 1.8f else 2.2f; m.attackAnim = 1.0f
                    fireProjectile(m.x, m.y, m.z, player.x - m.x, player.y - m.y, if (m.isBoss) 5f else 3f, m.type != "skeleton", false, false)
                } else if (m.type == "creeper" && distP < 1.8f) {
                    m.fuse += dt
                    if (m.fuse >= 1.25f) {
                        onExplode(floor(m.x).toInt(), floor(m.y).toInt(), floor(m.z).toInt(), 2.3f)
                        if (distP < 3.4f) onHurtPlayer(8f, m)
                        mobs.remove(m); continue
                    }
                } else {
                    m.fuse = max(0f, m.fuse - dt)
                    if (!ranged || distP > 5.0f) {
                        m.vx += (cos(ang) * spd - m.vx) * min(1f, dt * 6f)
                        m.vy += (sin(ang) * spd - m.vy) * min(1f, dt * 6f)
                    }
                    if (!ranged && distP < 1.25f && m.atkCd <= 0f) {
                        m.atkCd = 1.05f; m.attackAnim = 1.0f
                        val hitDmg = (when (m.type) { "yeti", "crystal_golem", "mimic" -> 6f; "enderman" -> 5f; else -> 3f }) * (if (m.isDeepMob) 1.25f else 1f)
                        onHurtPlayer(hitDmg, m)
                    }
                }
            } else if (m.fleeTimer > 0f) {
                m.fleeTimer = max(0f, m.fleeTimer - dt)
                val fleeAng = atan2((m.y - player.y).toDouble(), (m.x - player.x).toDouble()).toFloat()
                m.vx += (cos(fleeAng) * 3.6f * slowMult - m.vx) * min(1f, dt * 6f)
                m.vy += (sin(fleeAng) * 3.6f * slowMult - m.vy) * min(1f, dt * 6f)
            } else {
                if (m.aiTimer <= 0f) { m.aiTimer = 2.4f + Random.nextFloat() * 3.5f; m.aiAngle = Random.nextFloat() * PI.toFloat() * 2f }
                val grazeSpd = if (m.aiTimer < 1.1f) 0f else 1.15f
                m.vx += (cos(m.aiAngle) * grazeSpd * slowMult - m.vx) * min(1f, dt * 4f)
                m.vy += (sin(m.aiAngle) * grazeSpd * slowMult - m.vy) * min(1f, dt * 4f)
            }

            val nx = m.x + m.vx * dt; val ny = m.y + m.vy * dt
            if (m.type in setOf("bat", "bee", "ghast", "blaze", "sky_guardian")) {
                m.x = nx; m.y = ny
            } else {
                val gz = world.getGroundZ(floor(nx).toInt(), floor(ny).toInt(), m.z)
                val maxStep = if (m.type == "spider") 2.25f else 1.2f
                if (gz != null && abs(gz - m.z) <= maxStep) {
                    m.x = nx; m.y = ny; m.z += (gz - m.z) * min(1f, dt * 10f)
                } else {
                    val gzx = world.getGroundZ(floor(nx).toInt(), floor(m.y).toInt(), m.z)
                    val gzy = world.getGroundZ(floor(m.x).toInt(), floor(ny).toInt(), m.z)
                    when {
                        gzx != null && abs(gzx - m.z) <= maxStep -> { m.x = nx; m.z += (gzx - m.z) * min(1f, dt * 10f) }
                        gzy != null && abs(gzy - m.z) <= maxStep -> { m.y = ny; m.z += (gzy - m.z) * min(1f, dt * 10f) }
                        else -> m.aiAngle += 1.57f
                    }
                }
            }
            val screenDx = m.vx - m.vy
            if (abs(screenDx) > 0.1f) m.facingRight = screenDx > 0f
            val moveSpd = hypot(m.vx.toDouble(), m.vy.toDouble()).toFloat()
            m.walkAnim += dt * moveSpd * 3.8f
            if (m.type in setOf("slime", "mini_slime", "magma_slime", "mimic")) {
                m.hopPhase += dt * (4.5f + moveSpd * 1.2f)
            }
        }
    }
}
