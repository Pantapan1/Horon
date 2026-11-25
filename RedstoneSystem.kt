package com.voxelhorizon.nativegame

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class RedstoneSystem {
    // Active timed pulses: key -> remaining seconds (for Stone Buttons & Clock pulses)
    private val buttonTimers = HashMap<Triple<Int, Int, Int>, Float>()
    private val poweredBlocks = HashSet<Triple<Int, Int, Int>>()
    private val prevPoweredBlocks = HashSet<Triple<Int, Int, Int>>()
    private var evalTimer = 0f
    private var sparkTimer = 0f
    private var hopperTimer = 0f

    fun pressButton(x: Int, y: Int, z: Int, world: WorldIso) {
        buttonTimers[Triple(x, y, z)] = 2.5f
        SoundFx.play("mine", 0.55f, 0.02f)
        evaluateAround(x, y, z, world, null, null, null, null)
    }

    fun isButtonActive(x: Int, y: Int, z: Int): Boolean = (buttonTimers[Triple(x, y, z)] ?: 0f) > 0f

    fun isPowered(x: Int, y: Int, z: Int): Boolean = poweredBlocks.contains(Triple(x, y, z))

    fun toggleLever(x: Int, y: Int, z: Int, game: VoxelHorizonGame) {
        val cur = game.world.getBlock(x, y, z)
        val next = if (cur == 81) 82 else 81
        game.setWorldBlock(x, y, z, next)
        SoundFx.play("mine", 0.6f, 0.03f)
        game.showToast(if (next == 82) "Рычаг: ВКЛ (Сигнал активен!)" else "Рычаг: ВЫКЛ")
        evaluateAround(x, y, z, game.world, game, game.player, game.entities, null)
    }

    fun playNoteBlock(x: Int, y: Int, z: Int, entities: EntityAndWeatherManager) {
        // Musical pentatonic pitch based on block coordinates so players can build real melodies!
        val scale = floatArrayOf(0.75f, 0.84f, 0.94f, 1.0f, 1.12f, 1.26f, 1.41f, 1.50f)
        val idx = ((x * 3 + y * 5 + z) and 7)
        val noteColors = intArrayOf(0xFF4268, 0xFF9E38, 0xFFE248, 0x48EE78, 0x38D4FF, 0x8664FF, 0xFF56E2, 0xFFFFFF)
        SoundFx.playFixedPitch("craft", 0.7f, scale[idx])
        entities.spawnRisingSpark(x + 0.5f, y + 0.5f, z + 1.1f, noteColors[idx], 7f)
        entities.spawnRisingSpark(x + 0.5f, y + 0.5f, z + 1.25f, noteColors[(idx + 2) and 7], 6f)
    }

    fun update(dt: Float, game: VoxelHorizonGame) {
        // Tick button pulse timers
        if (buttonTimers.isNotEmpty()) {
            val it = buttonTimers.entries.iterator()
            var changed = false
            while (it.hasNext()) {
                val e = it.next()
                e.setValue(e.value - dt)
                if (e.value <= 0f) { it.remove(); changed = true }
            }
            if (changed) evalTimer = 0.25f
        }

        // Update Pressure Plates (83 <-> 84) when player or mobs step on them
        val world = game.world
        val px = floor(game.player.x).toInt()
        val py = floor(game.player.y).toInt()
        val pz = floor(game.player.z).toInt()

        evalTimer += dt
        if (evalTimer >= 0.18f) {
            evalTimer = 0f
            for (dz in -2..2) for (dx in -7..7) for (dy in -7..7) {
                val gx = px + dx; val gy = py + dy; val gz = pz + dz
                if (gz !in 1 until WorldIso.MAX_Z) continue
                val b = world.getBlock(gx, gy, gz)
                if (b == 83 || b == 84) {
                    val playerOn = px == gx && py == gy && abs(pz - gz) <= 1
                    val mobOn = game.entities.mobs.any { floor(it.x).toInt() == gx && floor(it.y).toInt() == gy && abs(floor(it.z).toInt() - gz) <= 1 }
                    val want = if (playerOn || mobOn) 84 else 83
                    if (want != b) {
                        game.setWorldBlock(gx, gy, gz, want)
                        if (want == 84) SoundFx.play("mine", 0.45f, 0.02f)
                    }
                }
            }
            val isNightNow = game.entities.dayFactor < 0.48f
            for (dz in -2..2) for (dx in -8..8) for (dy in -8..8) {
                val gx = px + dx; val gy = py + dy; val gz = pz + dz
                if (gz !in 1 until WorldIso.MAX_Z) continue
                val b = world.getBlock(gx, gy, gz)
                if (b == 183 && isNightNow) game.setWorldBlock(gx, gy, gz, 184)
                else if (b == 184 && !isNightNow) game.setWorldBlock(gx, gy, gz, 183)
            }
            evaluateAround(px, py, pz, world, game, game.player, game.entities, null)
        }

        // Воронка (182): авто-сбор лута вокруг и загрузка в соседнюю Печь или Сундук!
        hopperTimer += dt
        if (hopperTimer >= 0.65f) {
            hopperTimer = 0f
            val adjDirs = arrayOf(Triple(1,0,0), Triple(-1,0,0), Triple(0,1,0), Triple(0,-1,0), Triple(0,0,-1), Triple(0,0,1))
            for (dz in -2..2) for (dx in -7..7) for (dy in -7..7) {
                val gx = px + dx; val gy = py + dy; val gz = pz + dz
                if (gz !in 1 until WorldIso.MAX_Z) continue
                if (world.getBlock(gx, gy, gz) != 182) continue
                val drop = game.entities.drops.firstOrNull { hypot((it.x - (gx + 0.5f)).toDouble(), (it.y - (gy + 0.5f)).toDouble()) < 2.6 && abs(it.z - gz) < 2.2f }
                    ?: continue
                var stored = false
                for ((ax, ay, az) in adjDirs) {
                    val nx = gx + ax; val ny = gy + ay; val nz = gz + az
                    if (nz !in 1 until WorldIso.MAX_Z) continue
                    val nb = world.getBlock(nx, ny, nz)
                    if (nb == 18) {
                        val f = world.getFurnace(nx, ny, nz)
                        if (BlockRegistry.smeltMap.containsKey(drop.itemId) && (f.input == null || f.input!!.id == drop.itemId)) {
                            f.input = if (f.input == null) SlotItem(drop.itemId, drop.count) else SlotItem(drop.itemId, min(64, f.input!!.count + drop.count))
                            stored = true; break
                        } else if ((BlockRegistry.items[drop.itemId]?.fuelPower ?: 0f) > 0f && (f.fuel == null || f.fuel!!.id == drop.itemId)) {
                            f.fuel = if (f.fuel == null) SlotItem(drop.itemId, drop.count) else SlotItem(drop.itemId, min(64, f.fuel!!.count + drop.count))
                            stored = true; break
                        }
                    } else if (nb == 35 || nb == 52) {
                        val ch = world.getChest(nx, ny, nz)
                        val ex = ch.indexOfFirst { it != null && it.id == drop.itemId && it.count < 64 }
                        val emp = ch.indexOfFirst { it == null }
                        if (ex >= 0) {
                            ch[ex]!!.count = min(64, ch[ex]!!.count + drop.count)
                            if (game.netMp.isConnected) game.netMp.broadcastChest(nx, ny, nz, ch)
                            stored = true; break
                        } else if (emp >= 0) {
                            ch[emp] = SlotItem(drop.itemId, drop.count)
                            if (game.netMp.isConnected) game.netMp.broadcastChest(nx, ny, nz, ch)
                            stored = true; break
                        }
                    }
                }
                if (stored) {
                    game.entities.drops.remove(drop)
                    game.entities.spawnRisingSpark(gx + 0.5f, gy + 0.5f, gz + 0.9f, 0x56E2FF, 5f)
                }
            }
        }

        // Rising glowing crimson sparks on powered Redstone wires & torches!
        sparkTimer += dt
        if (sparkTimer >= 0.28f) {
            sparkTimer = 0f
            for ((rx, ry, rz) in poweredBlocks) {
                if (abs(rx - px) <= 8 && abs(ry - py) <= 8 && abs(rz - pz) <= 4) {
                    val b = world.getBlock(rx, ry, rz)
                    if ((b == 80 || b == 86 || b == 76) && Random.nextFloat() < 0.30f) {
                        game.entities.spawnRisingSpark(rx + 0.5f, ry + 0.5f, rz + 0.45f, 0xFF3844, 4.5f)
                    }
                }
            }
        }
    }

    fun evaluateAround(
        cx: Int, cy: Int, cz: Int,
        world: WorldIso,
        game: VoxelHorizonGame?,
        player: PlayerState?,
        entities: EntityAndWeatherManager?,
        customSources: List<Triple<Int, Int, Int>>?
    ) {
        val rad = 11
        val zLo = max(1, cz - 4)
        val zHi = min(WorldIso.MAX_Z - 2, cz + 4)

        // 1. Collect all active power sources in local neighborhood
        val queue = ArrayDeque<Pair<Triple<Int, Int, Int>, Int>>()
        val bestPower = HashMap<Triple<Int, Int, Int>, Int>()

        fun addSource(pos: Triple<Int, Int, Int>, strength: Int) {
            val prev = bestPower[pos] ?: 0
            if (strength > prev) {
                bestPower[pos] = strength
                queue.addLast(Pair(pos, strength))
            }
        }

        for (gz in zLo..zHi) for (gx in (cx - rad)..(cx + rad)) for (gy in (cy - rad)..(cy + rad)) {
            val b = world.getBlock(gx, gy, gz)
            val pos = Triple(gx, gy, gz)
            val isSource = b == 76 || b == 82 || b == 84 || b == 86 || b == 184 || (b == 85 && isButtonActive(gx, gy, gz))
            if (isSource) addSource(pos, 15)
        }
        customSources?.forEach { addSource(it, 15) }

        // 2. BFS signal propagation through Redstone Wires (79/80) & Repeaters (87 -> restores to 15!)
        val dirs = arrayOf(
            Triple(1, 0, 0), Triple(-1, 0, 0),
            Triple(0, 1, 0), Triple(0, -1, 0),
            Triple(0, 0, 1), Triple(0, 0, -1),
            // Step-up/down 1 block for wires climbing stairs!
            Triple(1, 0, 1), Triple(-1, 0, 1), Triple(0, 1, 1), Triple(0, -1, 1),
            Triple(1, 0, -1), Triple(-1, 0, -1), Triple(0, 1, -1), Triple(0, -1, -1)
        )
        while (queue.isNotEmpty()) {
            val (pos, pwr) = queue.removeFirst()
            if (pwr <= 1) continue
            for ((dx, dy, dz) in dirs) {
                val nx = pos.first + dx; val ny = pos.second + dy; val nz = pos.third + dz
                if (nz !in zLo..zHi || abs(nx - cx) > rad || abs(ny - cy) > rad) continue
                val nb = world.getBlock(nx, ny, nz)
                val nPos = Triple(nx, ny, nz)
                when (nb) {
                    79, 80 -> addSource(nPos, pwr - 1)
                    87 -> addSource(nPos, 15) // Repeater boosts signal back to 15!
                    // Only directly adjacent (Manhattan 1) triggers consumers!
                    77, 78, 57, 58, 172, 173, 26, 88, 89, 90, 91, 186 -> {
                        if (abs(dx) + abs(dy) + abs(dz) == 1) {
                            if ((bestPower[nPos] ?: 0) < pwr - 1) bestPower[nPos] = pwr - 1
                        }
                    }
                }
            }
        }

        prevPoweredBlocks.clear()
        prevPoweredBlocks.addAll(poweredBlocks)
        poweredBlocks.clear()
        poweredBlocks.addAll(bestPower.keys)

        if (game == null) return

        // 3. Apply state transitions to Wires, Lamps, Doors, Pistons, Dispensers, Note Blocks & TNT!
        for (gz in zLo..zHi) for (gx in (cx - rad)..(cx + rad)) for (gy in (cy - rad)..(cy + rad)) {
            val pos = Triple(gx, gy, gz)
            val on = bestPower.containsKey(pos)
            val risingEdge = on && !prevPoweredBlocks.contains(pos)
            val b = world.getBlock(gx, gy, gz)

            when (b) {
                79 -> if (on) game.setWorldBlock(gx, gy, gz, 80)
                80 -> if (!on) game.setWorldBlock(gx, gy, gz, 79)
                77 -> if (on) game.setWorldBlock(gx, gy, gz, 78)
                78 -> if (!on) game.setWorldBlock(gx, gy, gz, 77)
                172 -> if (on) { game.setWorldBlock(gx, gy, gz, 173); SoundFx.play("door", 0.6f) }
                173 -> if (!on) { game.setWorldBlock(gx, gy, gz, 172); SoundFx.play("door", 0.5f) }
                57 -> if (risingEdge) { game.setWorldBlock(gx, gy, gz, 58); SoundFx.play("door", 0.5f) }
                58 -> if (!on && prevPoweredBlocks.contains(pos)) { game.setWorldBlock(gx, gy, gz, 57) }
                26 -> if (risingEdge) game.triggerTntAt(gx, gy, gz)
                88 -> if (on) {
                    // Piston extends! Pushes block above up by 1 OR launches player/mobs upward!
                    game.setWorldBlock(gx, gy, gz, 89)
                    SoundFx.play("piston", 0.65f, 0.04f)
                    if (gz + 2 < WorldIso.MAX_Z) {
                        val above = world.getBlock(gx, gy, gz + 1)
                        val above2 = world.getBlock(gx, gy, gz + 2)
                        if (above > 0 && above != 10 && above2 == 0 && (BlockRegistry.items[above]?.hardness ?: 999f) < 900f) {
                            game.setWorldBlock(gx, gy, gz + 2, above)
                            game.setWorldBlock(gx, gy, gz + 1, 0)
                        }
                    }
                    if (player != null && floor(player.x).toInt() == gx && floor(player.y).toInt() == gy && abs(player.z - (gz + 1)) <= 1.2f) {
                        player.z = min((WorldIso.MAX_Z - 2).toFloat(), player.z + 2.0f)
                        game.showToast("Поршень подбросил вас вверх!")
                    }
                }
                89 -> if (!on) game.setWorldBlock(gx, gy, gz, 88)
                90 -> if (risingEdge && entities != null) {
                    // Dispenser shoots arrow at nearest hostile mob (or in player aim dir)!
                    val tgt = entities.mobs.filter { it.hostile && !it.tamed && hypot((it.x - gx).toDouble(), (it.y - gy).toDouble()) < 14.0 }
                        .minByOrNull { hypot((it.x - gx).toDouble(), (it.y - gy).toDouble()) }
                    val dx = if (tgt != null) tgt.x - (gx + 0.5f) else (player?.aimGx ?: 1f)
                    val dy = if (tgt != null) tgt.y - (gy + 0.5f) else (player?.aimGy ?: 0f)
                    entities.fireProjectile(gx + 0.5f, gy + 0.5f, gz + 0.6f, dx, dy, 12f, false, false, true)
                    SoundFx.play("hit", 0.55f)
                }
                91 -> if (risingEdge && entities != null) {
                    playNoteBlock(gx, gy, gz, entities)
                }
            }
        }
    }
}
