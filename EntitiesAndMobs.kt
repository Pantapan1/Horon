package com.voxelhorizon.nativegame

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

data class SkinProfile(
    val id: String,
    val name: String,
    val skin: Int,
    val hair: Int,
    val eye: Int,
    val coat: Int,
    val trim: Int,
    val pants: Int,
    val addon: String
)

val SKINS_LIST = listOf(
    SkinProfile("fox_officer", "Офицер Кицунэ", 0xF3C6A5, 0xC2410C, 0xF59E0B, 0x1E293B, 0xFBBF24, 0x0F172A, "fox"),
    SkinProfile("estelle_cmd", "Командир Эстель", 0xFDE2D2, 0x2DD4BF, 0xE11D48, 0x18181B, 0xEAB308, 0x27272A, "estelle"),
    SkinProfile("luna_cat", "Лунная Кошка", 0xF8E3D6, 0xE2E8F0, 0x86EFAC, 0x1C1917, 0xDC2626, 0x09090B, "luna"),
    SkinProfile("shadow_knight", "Теневой Рыцарь", 0x94A3B8, 0x0F172A, 0x38BDF8, 0x1E1B4B, 0x38BDF8, 0x0F172A, "knight"),
    SkinProfile("emerald_scout", "Следопыт", 0xE0AC69, 0x451A03, 0x22C55E, 0x14532D, 0xA3E635, 0x3F2E21, "none"),
    SkinProfile("classic", "Классика HD", 0xE2B28B, 0x4A321D, 0x2448A8, 0x2898B8, 0x1E6F88, 0x38467A, "none")
)

class PlayerState {
    var x = 0.5f; var y = 34f; var z = 0.5f
    var vx = 0f; var vy = 0f; var vz = 0f
    var yaw = 0f; var pitch = -0.15f
    var onGround = false
    var flying = false
    var sneaking = false
    var creative = false
    var thirdPerson = false
    var hp = 20f; var maxHp = 20f
    var food = 20f; var maxFood = 20f
    var dashCooldown = 0f
    var walkBob = 0f
    var armSwing = 0f
    var selectedSlot = 0
    var skinId = "fox_officer"
    var spawnX = 0.5f; var spawnY = 34f; var spawnZ = 0.5f; var spawnDim = "overworld"

    val slots = arrayOfNulls<SlotItem>(36) // 0..8 hotbar, 9..35 backpack
    var offhand: SlotItem? = SlotItem(37, 16) // Вторая рука (по умолчанию Факел!)
    val armor = IntArray(4) { 0 }

    fun getTotalArmor(): Int {
        var d = 0
        for (id in armor) d += BlockRegistry.items[id]?.armorDef ?: 0
        if (offhand?.id == 132) d += 3 // Щит во второй руке!
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
        for (i in 0 until 36) {
            val s = slots[i]
            if (s != null && s.id == id && s.count < 64) {
                val add = minOf(64 - s.count, rem)
                s.count += add; rem -= add
                if (rem <= 0) return true
            }
        }
        for (i in 0 until 36) {
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
        for (i in 0 until 36) {
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

    // Умная автосортировка рюкзака в 1 тап!
    fun autoSortBackpack() {
        val merged = linkedMapOf<Int, Int>()
        for (i in 9 until 36) {
            val s = slots[i] ?: continue
            merged[s.id] = (merged[s.id] ?: 0) + s.count
            slots[i] = null
        }
        var idx = 9
        for ((id, total) in merged.entries.sortedBy { it.key }) {
            var rem = total
            while (rem > 0 && idx < 36) {
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
    val hostile: Boolean,
    val flying: Boolean,
    val isBoss: Boolean,
    var x: Float, var y: Float, var z: Float,
    var vx: Float = 0f, var vy: Float = 0f, var vz: Float = 0f,
    var yaw: Float = 0f,
    var hp: Float = 20f, var maxHp: Float = 20f,
    var aiTimer: Float = 0f, var atkCd: Float = 0f, var walkPhase: Float = 0f,
    var hurtFlash: Float = 0f, var fuse: Float = 0f
)

data class DroppedItem(
    val id: Int,
    val itemId: Int,
    val count: Int,
    var x: Float, var y: Float, var z: Float,
    var vx: Float, var vy: Float, var vz: Float,
    var age: Float = 0f
)

data class Projectile(
    var x: Float, var y: Float, var z: Float,
    var vx: Float, var vy: Float, var vz: Float,
    val dmg: Float,
    val fiery: Boolean,
    val fromPlayer: Boolean,
    val isGrapple: Boolean = false,
    var life: Float = 3.0f
)

class EntityRendererAndManager {
    private val boxModels = HashMap<Int, Model>()
    private val modelBuilder = ModelBuilder()

    val mobs = CopyOnWriteArrayList<MobEntity>()
    val drops = CopyOnWriteArrayList<DroppedItem>()
    val projectiles = CopyOnWriteArrayList<Projectile>()
    var foxPet: MobEntity? = null

    private var nextMobId = 1
    private var nextDropId = 1
    private var spawnTimer = 2.0f

    var openChestKey: String? = null
    private val chestLidAngles = HashMap<String, Float>()

    private fun getBoxModel(hex: Int): Model {
        return boxModels.computeIfAbsent(hex) {
            val r = ((hex shr 16) and 0xFF) / 255f
            val g = ((hex shr 8) and 0xFF) / 255f
            val b = (hex and 0xFF) / 255f
            modelBuilder.createBox(
                1f, 1f, 1f,
                Material(ColorAttribute.createDiffuse(r, g, b, 1f)),
                (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
            )
        }
    }

    private fun drawBox(batch: ModelBatch, env: Environment, hex: Int, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, rotYDeg: Float = 0f, rotXDeg: Float = 0f) {
        val inst = ModelInstance(getBoxModel(hex))
        inst.transform.idt()
            .translate(x, y, z)
            .rotate(Vector3.Y, rotYDeg)
            .rotate(Vector3.X, rotXDeg)
            .scale(sx, sy, sz)
        batch.render(inst, env)
    }

    fun spawnMob(type: String, x: Float, y: Float, z: Float, forcedId: Int? = null): MobEntity {
        val id = forcedId ?: nextMobId++
        val isBoss = type == "inferno_lord" || type == "sky_guardian"
        val hostile = type !in setOf("sheep", "cow", "pig", "chicken", "fox_pet")
        val flying = type in setOf("blaze", "ghast", "sky_guardian")
        val maxHp = when (type) {
            "inferno_lord" -> 160f
            "sky_guardian" -> 140f
            "enderman" -> 34f
            "piglin", "ghast" -> 24f
            "zombie", "creeper", "blaze" -> 20f
            "skeleton" -> 18f
            "spider" -> 16f
            "cow" -> 12f
            "chicken" -> 6f
            else -> 10f
        }
        val title = when (type) {
            "inferno_lord" -> "Повелитель Преисподней"
            "sky_guardian" -> "Страж Небес"
            else -> type
        }
        val mob = MobEntity(id, type, title, hostile, flying, isBoss, x, y, z, hp = maxHp, maxHp = maxHp)
        mobs.add(mob)
        return mob
    }

    fun toggleFoxPet(player: PlayerState): Boolean {
        if (foxPet != null) { foxPet = null; return false }
        foxPet = MobEntity(-99, "fox_pet", "Лисёнок", hostile = false, flying = false, isBoss = false, x = player.x + 1.5f, y = player.y + 0.5f, z = player.z + 1.5f, hp = 40f, maxHp = 40f)
        return true
    }

    fun spawnDrop(itemId: Int, count: Int, x: Float, y: Float, z: Float, vx: Float = 0f, vy: Float = 3.2f, vz: Float = 0f) {
        if (itemId <= 0 || count <= 0) return
        drops.add(DroppedItem(nextDropId++, itemId, count, x, y, z, vx, vy, vz))
    }

    fun fireProjectile(fromX: Float, fromY: Float, fromZ: Float, dirX: Float, dirY: Float, dirZ: Float, dmg: Float, fiery: Boolean, fromPlayer: Boolean, isGrapple: Boolean = false) {
        val spd = if (isGrapple) 28f else if (fromPlayer) 24f else if (fiery) 12f else 15f
        projectiles.add(Projectile(fromX, fromY, fromZ, dirX * spd, dirY * spd, dirZ * spd, dmg, fiery, fromPlayer, isGrapple))
    }

    fun hitMobInRay(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, maxDist: Float, dmg: Float, aoeHammer: Boolean, onKill: (MobEntity) -> Unit): Boolean {
        var hitAny = false
        for (m in mobs) {
            val cy = m.y + if (m.isBoss) 1.5f else 0.9f
            val rSq = if (aoeHammer) 3.2f else if (m.isBoss) 2.6f else 0.95f
            val tx = m.x - ox; val ty = cy - oy; val tz = m.z - oz
            val proj = tx * dx + ty * dy + tz * dz
            if (proj < 0f || proj > maxDist) continue
            val px = ox + dx * proj - m.x
            val py = oy + dy * proj - cy
            val pz = oz + dz * proj - m.z
            if (px * px + py * py + pz * pz <= rSq) {
                damageMob(m, dmg, dx, dz, onKill)
                hitAny = true
                if (!aoeHammer) break
            }
        }
        return hitAny
    }

    fun damageMob(m: MobEntity, dmg: Float, dirX: Float, dirZ: Float, onKill: (MobEntity) -> Unit) {
        m.hp -= dmg
        m.hurtFlash = 0.22f
        m.vx += dirX * 5.5f; m.vz += dirZ * 5.5f
        if (!m.flying) m.vy = 4.6f
        if (m.type == "enderman" && m.hp > 0f && Random.nextFloat() < 0.5f) {
            m.x += (Random.nextFloat() - 0.5f) * 10f
            m.z += (Random.nextFloat() - 0.5f) * 10f
        }
        if (m.hp <= 0f) {
            mobs.remove(m)
            onKill(m)
        }
    }

    fun getActiveBoss(): MobEntity? = mobs.firstOrNull { it.isBoss }

    fun update(
        dt: Float,
        world: VoxelWorld,
        player: PlayerState,
        isNight: Boolean,
        isNetClient: Boolean,
        onHurtPlayer: (Float) -> Unit,
        onExplode: (Int, Int, Int, Float) -> Unit,
        onMobKill: (MobEntity) -> Unit,
        onPickup: (Int, Int) -> Unit,
        onGrapplePull: (Float, Float, Float) -> Unit
    ) {
        // 1. Обновление 3D дропа
        for (d in drops) {
            d.age += dt
            if (d.age > 180f) { drops.remove(d); continue }
            d.vy -= 15f * dt
            d.x += d.vx * dt; d.y += d.vy * dt; d.z += d.vz * dt
            d.vx *= 0.88f; d.vz *= 0.88f
            if (world.isSolid(floor(d.x).toInt(), floor(d.y - 0.18f).toInt(), floor(d.z).toInt()) && d.vy < 0f) {
                d.y = floor(d.y - 0.18f) + 1.18f
                d.vy = 0f
            }
            if (d.age > 0.6f && hypot((player.x - d.x).toDouble(), (player.z - d.z).toDouble()) < 1.65 && abs(player.y + 0.8f - d.y) < 1.6f) {
                onPickup(d.itemId, d.count)
                drops.remove(d)
            }
        }

        // 2. Снаряды (Стрелы, Фаерболы, Крюк-кошка!)
        for (pr in projectiles) {
            pr.life -= dt
            if (pr.fromPlayer && !pr.isGrapple) pr.vy -= 9.5f * dt
            pr.x += pr.vx * dt; pr.y += pr.vy * dt; pr.z += pr.vz * dt
            var hit = world.isSolid(floor(pr.x).toInt(), floor(pr.y).toInt(), floor(pr.z).toInt())
            if (hit && pr.isGrapple) {
                onGrapplePull(pr.x, pr.y, pr.z)
                projectiles.remove(pr); continue
            }
            if (pr.fromPlayer && !pr.isGrapple) {
                for (m in mobs) {
                    if (hypot((pr.x - m.x).toDouble(), (pr.z - m.z).toDouble()) < (if (m.isBoss) 1.4 else 0.8) && abs(pr.y - (m.y + 1f)) < 1.3f) {
                        damageMob(m, pr.dmg, pr.vx * 0.05f, pr.vz * 0.05f, onMobKill)
                        hit = true; break
                    }
                }
            } else if (!pr.fromPlayer) {
                if (hypot((pr.x - player.x).toDouble(), (pr.z - player.z).toDouble()) < 0.75 && abs(pr.y - (player.y + 1f)) < 1.0f) {
                    onHurtPlayer(pr.dmg)
                    hit = true
                }
            }
            if (hit || pr.life <= 0f) {
                if (pr.fiery && hit && !isNetClient) onExplode(floor(pr.x).toInt(), floor(pr.y).toInt(), floor(pr.z).toInt(), 1.8f)
                projectiles.remove(pr)
            }
        }

        // 3. Питомец-Лис (помогает в бою!)
        foxPet?.let { pt ->
            pt.atkCd = maxOf(0f, pt.atkCd - dt)
            val target = mobs.filter { it.hostile }.minByOrNull { hypot((it.x - pt.x).toDouble(), (it.z - pt.z).toDouble()) }
            val gx = if (target != null && hypot((target.x - pt.x).toDouble(), (target.z - pt.z).toDouble()) < 12.0) target.x else player.x - cos(player.yaw) * 1.4f
            val gz = if (target != null && hypot((target.x - pt.x).toDouble(), (target.z - pt.z).toDouble()) < 12.0) target.z else player.z + sin(player.yaw) * 1.4f
            val dP = hypot((player.x - pt.x).toDouble(), (player.z - pt.z).toDouble())
            val dG = hypot((gx - pt.x).toDouble(), (gz - pt.z).toDouble())
            if (dP > 22.0) {
                pt.x = player.x + 1.2f; pt.y = player.y + 0.5f; pt.z = player.z + 1.2f
            } else if (dG > 1.4) {
                pt.yaw = atan2((gx - pt.x).toDouble(), (gz - pt.z).toDouble()).toFloat()
                val nx = pt.x + sin(pt.yaw) * 4.5f * dt
                val nz = pt.z + cos(pt.yaw) * 4.5f * dt
                if (world.isSolid(floor(nx).toInt(), floor(pt.y + 0.2f).toInt(), floor(nz).toInt()) && pt.vy <= 0.1f) pt.vy = 6.2f
                else { pt.x = nx; pt.z = nz }
                pt.walkPhase += dt * 11f
            }
            if (target != null && dG < 1.6 && pt.atkCd <= 0f) {
                pt.atkCd = 1.0f
                damageMob(target, 5f, sin(pt.yaw), cos(pt.yaw), onMobKill)
            }
            pt.vy -= 18f * dt
            val ny = pt.y + pt.vy * dt
            if (pt.vy <= 0f && world.isSolid(floor(pt.x).toInt(), floor(ny).toInt(), floor(pt.z).toInt())) {
                pt.y = floor(ny) + 1.0f; pt.vy = 0f
            } else pt.y = ny
        }

        if (isNetClient) return

        // 4. Спавн и ИИ мобов (Хост / Одиночная игра)
        spawnTimer -= dt
        if (spawnTimer <= 0f && mobs.size < 12) {
            spawnTimer = 4.0f
            val ang = Random.nextFloat() * (PI.toFloat() * 2f)
            val dist = 14f + Random.nextFloat() * 14f
            val sx = floor(player.x + cos(ang) * dist).toInt()
            val sz = floor(player.z + sin(ang) * dist).toInt()
            when (world.dimension) {
                "nether" -> {
                    val t = if (getActiveBoss() == null && Random.nextFloat() < 0.18f) "inferno_lord" else listOf("piglin", "blaze", "ghast").random()
                    spawnMob(t, sx + 0.5f, if (t == "ghast") 34f else 26f, sz + 0.5f)
                }
                "aether" -> {
                    val t = if (getActiveBoss() == null && Random.nextFloat() < 0.22f) "sky_guardian" else "sheep"
                    spawnMob(t, sx + 0.5f, 35f, sz + 0.5f)
                }
                else -> {
                    for (sy in VoxelWorld.WH - 3 downTo VoxelWorld.SEA_LEVEL) {
                        if (world.isSolid(sx, sy, sz)) {
                            val t = if (isNight && Random.nextFloat() < 0.7f) listOf("zombie", "skeleton", "creeper", "spider", "enderman").random()
                            else listOf("sheep", "cow", "pig", "chicken").random()
                            spawnMob(t, sx + 0.5f, sy + 1.1f, sz + 0.5f)
                            break
                        }
                    }
                }
            }
        }

        for (m in mobs) {
            val distP = hypot((player.x - m.x).toDouble(), (player.z - m.z).toDouble()).toFloat()
            if (distP > 52f || m.y < -4f) { mobs.remove(m); continue }
            m.aiTimer -= dt
            m.atkCd = maxOf(0f, m.atkCd - dt)
            val aggro = m.hostile && !player.creative && distP < (if (m.isBoss) 26f else 18f)
            if (aggro) {
                m.yaw = atan2((player.x - m.x).toDouble(), (player.z - m.z).toDouble()).toFloat()
                val ranged = m.type in setOf("skeleton", "blaze", "ghast", "inferno_lord", "sky_guardian")
                val spd = if (m.isBoss) 3.0f else if (m.type == "spider" || m.type == "enderman") 3.4f else 2.4f
                if (ranged && distP < 15f && m.atkCd <= 0f) {
                    m.atkCd = if (m.isBoss) 2.0f else 2.2f
                    val dx = player.x - m.x; val dy = (player.y + 1f) - (m.y + 1.4f); val dz = player.z - m.z
                    val l = maxOf(0.1f, hypot(hypot(dx.toDouble(), dy.toDouble()), dz.toDouble()).toFloat())
                    fireProjectile(m.x, m.y + 1.4f, m.z, dx / l, dy / l, dz / l, if (m.isBoss) 5f else 3f, m.type != "skeleton", false)
                } else if (m.type == "creeper" && distP < 2.6f) {
                    m.fuse += dt
                    if (m.fuse >= 1.35f) {
                        onExplode(floor(m.x).toInt(), floor(m.y).toInt(), floor(m.z).toInt(), 2.5f)
                        if (distP < 4.5f) onHurtPlayer(8f)
                        mobs.remove(m); continue
                    }
                } else {
                    m.fuse = maxOf(0f, m.fuse - dt)
                    if (!ranged || distP > 8f) {
                        m.vx += (sin(m.yaw) * spd - m.vx) * minOf(1f, dt * 6f)
                        m.vz += (cos(m.yaw) * spd - m.vz) * minOf(1f, dt * 6f)
                    }
                    if (!ranged && distP < 1.65f && abs(player.y - m.y) < 2.0f && m.atkCd <= 0f) {
                        m.atkCd = 1.1f
                        onHurtPlayer(if (m.type == "enderman") 5f else 3f)
                    }
                }
            } else {
                if (m.aiTimer <= 0f) { m.aiTimer = 2f + Random.nextFloat() * 3f; m.yaw = Random.nextFloat() * (PI.toFloat() * 2f) }
                m.vx += (sin(m.yaw) * 1.1f - m.vx) * minOf(1f, dt * 4f)
                m.vz += (cos(m.yaw) * 1.1f - m.vz) * minOf(1f, dt * 4f)
            }

            if (m.flying) {
                m.vy = (maxOf(22f, player.y + 3.5f) - m.y) * 0.8f
                m.x += m.vx * dt; m.y += m.vy * dt; m.z += m.vz * dt
            } else {
                m.vy -= 18f * dt
                val nx = m.x + m.vx * dt; val nz = m.z + m.vz * dt
                if (world.isSolid(floor(nx).toInt(), floor(m.y + 0.2f).toInt(), floor(nz).toInt())) {
                    if (!world.isSolid(floor(nx).toInt(), floor(m.y + 1.2f).toInt(), floor(nz).toInt()) && m.vy <= 0.1f) m.vy = 6.2f
                    else m.yaw += 2.1f
                } else { m.x = nx; m.z = nz }
                val ny = m.y + m.vy * dt
                if (m.vy <= 0f && world.isSolid(floor(m.x).toInt(), floor(ny).toInt(), floor(m.z).toInt())) {
                    m.y = floor(ny) + 1.0f; m.vy = 0f
                } else m.y = ny
            }
            m.walkPhase += dt * hypot(m.vx.toDouble(), m.vz.toDouble()).toFloat() * 4.5f
            if (m.hurtFlash > 0f) m.hurtFlash -= dt
        }
    }

    // Рендер всех 3D-пропсов (Сундуки с анимацией крышки, 2-блочные Кровати, 3D Факелы), Мобов, Дропа и Аватаров!
    fun renderAll(batch: ModelBatch, env: Environment, world: VoxelWorld, player: PlayerState, remotePlayers: Collection<RemotePlayer>, timeSec: Float) {
        // 1. 3D Пропсы вокруг игрока (радиус 18 блоков)
        val px = floor(player.x).toInt(); val py = floor(player.y).toInt(); val pz = floor(player.z).toInt()
        for (wx in px - 18..px + 18) for (wz in pz - 18..pz + 18) {
            for (wy in maxOf(1, py - 10)..minOf(VoxelWorld.WH - 2, py + 10)) {
                val id = world.getBlock(wx, wy, wz)
                when (id) {
                    35 -> { // 3D Сундук с анимированной крышкой и золотым замком!
                        val key = world.blockKey(wx, wy, wz)
                        val target = if (openChestKey == key) -72f else 0f
                        val cur = (chestLidAngles[key] ?: 0f)
                        val next = cur + (target - cur) * 0.2f
                        chestLidAngles[key] = next
                        drawBox(batch, env, 0xB57632, wx + 0.5f, wy + 0.28f, wz + 0.5f, 0.86f, 0.56f, 0.86f)
                        drawBox(batch, env, 0x422A16, wx + 0.5f, wy + 0.54f, wz + 0.5f, 0.88f, 0.05f, 0.88f)
                        drawBox(batch, env, 0xB57632, wx + 0.5f, wy + 0.70f, wz + 0.5f, 0.86f, 0.28f, 0.86f, 0f, next)
                        drawBox(batch, env, 0xFFE066, wx + 0.5f, wy + 0.56f, wz + 0.94f, 0.14f, 0.22f, 0.08f)
                    }
                    38 -> { // 2-блочная 3D Кровать (ножки, изголовье, подушка, алое одеяло!)
                        drawBox(batch, env, 0x8F5B30, wx + 0.5f, wy + 0.22f, wz + 0.92f, 0.94f, 0.16f, 1.82f)
                        drawBox(batch, env, 0x8F5B30, wx + 0.5f, wy + 0.44f, wz + 0.04f, 0.96f, 0.54f, 0.10f)
                        drawBox(batch, env, 0xF4F6FA, wx + 0.5f, wy + 0.38f, wz + 0.92f, 0.90f, 0.18f, 1.70f)
                        drawBox(batch, env, 0xFFFFFF, wx + 0.5f, wy + 0.51f, wz + 0.32f, 0.70f, 0.14f, 0.36f)
                        drawBox(batch, env, 0xD62839, wx + 0.5f, wy + 0.40f, wz + 1.18f, 0.92f, 0.19f, 1.20f)
                        drawBox(batch, env, 0xFFD24C, wx + 0.5f, wy + 0.50f, wz + 0.58f, 0.94f, 0.05f, 0.10f)
                    }
                    37 -> { // 3D Факел с живым мерцающим пламенем!
                        val fl = 0.85f + sin(timeSec * 15f + wx * 7 + wz * 13) * 0.18f
                        drawBox(batch, env, 0x8F5B30, wx + 0.5f, wy + 0.29f, wz + 0.5f, 0.11f, 0.58f, 0.11f)
                        drawBox(batch, env, 0xFF7718, wx + 0.5f, wy + 0.68f, wz + 0.5f, 0.16f * fl, 0.22f * fl, 0.16f * fl)
                        drawBox(batch, env, 0xFFF6A8, wx + 0.5f, wy + 0.68f, wz + 0.5f, 0.09f * fl, 0.14f * fl, 0.09f * fl)
                    }
                }
            }
        }

        // 2. Мобы и Лисёнок-питомец
        for (m in mobs) renderMob(batch, env, m, timeSec)
        foxPet?.let { renderMob(batch, env, it, timeSec) }

        // 3. 3D-аватар игрока (от 3-го лица) и удалённые игроки в сети
        if (player.thirdPerson) {
            renderAvatar(batch, env, player.skinId, player.x, player.y, player.z, (player.yaw * 180f / PI.toFloat()) + 180f, player.walkBob, timeSec)
        }
        for (rp in remotePlayers) {
            renderAvatar(batch, env, rp.skinId, rp.x, rp.y, rp.z, (rp.yaw * 180f / PI.toFloat()) + 180f, rp.walkPhase, timeSec)
        }

        // 4. 3D Выброшенный лут и Снаряды
        for (d in drops) {
            val col = BlockRegistry.items[d.itemId]?.colorHex ?: 0x4ADE80
            val bob = sin(d.age * 4f) * 0.08f
            drawBox(batch, env, col, d.x, d.y + bob, d.z, 0.28f, 0.28f, 0.28f, d.age * 110f)
        }
        for (pr in projectiles) {
            val col = if (pr.isGrapple) 0x38BDF8 else if (pr.fiery) 0xFF7718 else 0xFFE888
            val sz = if (pr.fiery) 0.34f else 0.14f
            drawBox(batch, env, col, pr.x, pr.y, pr.z, sz, sz, sz)
        }
    }

    private fun renderAvatar(batch: ModelBatch, env: Environment, skinId: String, x: Float, y: Float, z: Float, yawDeg: Float, walk: Float, t: Float) {
        val sk = SKINS_LIST.firstOrNull { it.id == skinId } ?: SKINS_LIST[0]
        val sw = sin(walk) * 32f
        // Голова + Волосы + Глаза
        drawBox(batch, env, sk.skin, x, y + 1.65f, z, 0.48f, 0.48f, 0.48f, yawDeg)
        drawBox(batch, env, sk.hair, x, y + 1.88f, z, 0.52f, 0.16f, 0.52f, yawDeg)
        // Фуражка / Ушки
        if (sk.addon == "fox" || sk.addon == "estelle") {
            drawBox(batch, env, sk.coat, x, y + 1.98f, z, 0.56f, 0.12f, 0.56f, yawDeg)
            drawBox(batch, env, sk.trim, x, y + 1.94f, z, 0.58f, 0.04f, 0.58f, yawDeg)
        }
        if (sk.addon == "fox" || sk.addon == "luna") {
            drawBox(batch, env, sk.hair, x, y + 2.08f, z, 0.46f, 0.18f, 0.12f, yawDeg)
        }
        // Мундир + Пояс + Ноги + Руки
        drawBox(batch, env, sk.coat, x, y + 1.12f, z, 0.50f, 0.70f, 0.28f, yawDeg)
        drawBox(batch, env, sk.trim, x, y + 0.82f, z, 0.53f, 0.08f, 0.30f, yawDeg)
        drawBox(batch, env, sk.pants, x, y + 0.40f, z, 0.44f, 0.72f, 0.24f, yawDeg, sw * 0.25f)
        if (sk.addon == "fox") {
            drawBox(batch, env, sk.hair, x - sin(yawDeg * PI.toFloat() / 180f) * 0.34f, y + 0.78f, z - cos(yawDeg * PI.toFloat() / 180f) * 0.34f, 0.20f, 0.20f, 0.50f, yawDeg + sin(t * 6f) * 16f)
        }
    }

    private fun renderMob(batch: ModelBatch, env: Environment, m: MobEntity, t: Float) {
        val yawDeg = m.yaw * 180f / PI.toFloat()
        val hurt = m.hurtFlash > 0f
        val sc = if (m.fuse > 0f) 1f + sin(m.fuse * 26f) * 0.15f else 1f
        val col = if (hurt) 0xFF4444 else when (m.type) {
            "sheep" -> 0xF2F4F8; "cow" -> 0x5C3C28; "pig" -> 0xF49DB2; "chicken" -> 0xFFFFFF
            "zombie" -> 0x4E8248; "skeleton" -> 0xE0E0D8; "creeper" -> 0x3EB844; "spider" -> 0x26222A
            "enderman" -> 0x16161C; "piglin" -> 0xE49C88; "blaze" -> 0xFFB824; "ghast" -> 0xF4F4F6
            "inferno_lord" -> 0x48181E; "sky_guardian" -> 0xBAE6FD; "fox_pet" -> 0xE86A24
            else -> 0x44DD88
        }
        val h = when (m.type) {
            "inferno_lord", "sky_guardian" -> 2.4f
            "enderman" -> 2.5f
            "ghast" -> 2.0f
            "sheep", "cow", "pig" -> 0.85f
            "fox_pet", "chicken", "spider" -> 0.55f
            else -> 1.75f
        }
        val w = if (m.type == "ghast") 2.0f else if (m.isBoss) 1.1f else 0.65f
        drawBox(batch, env, col, m.x, m.y + h * 0.5f, m.z, w * sc, h * sc, w * sc, yawDeg)
        if (m.isBoss) {
            drawBox(batch, env, 0xFFD24C, m.x, m.y + h + 0.18f, m.z, w * 0.9f, 0.22f, w * 0.9f, yawDeg + t * 30f)
        }
    }

    fun dispose() {
        for (m in boxModels.values) m.dispose()
        boxModels.clear()
    }
}
