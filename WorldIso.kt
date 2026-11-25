package com.voxelhorizon.nativegame

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.abs
import java.nio.ByteBuffer
import kotlin.random.Random

data class SlotItem(var id: Int, var count: Int)

data class FurnaceState(
    var input: SlotItem? = null,
    var fuel: SlotItem? = null,
    var output: SlotItem? = null,
    var burn: Float = 0f,
    var maxBurn: Float = 1f,
    var cook: Float = 0f
)

data class WaystoneEntry(val name: String, val x: Int, val y: Int, val z: Int, val dim: String)

class WorldIso(var seed: Int, var dimension: String = "overworld") {
    companion object {
        const val CS = 16
        const val MAX_Z = 250
        const val SEA_Z = 138
        const val TW = 32f
        const val TH = 16f
        const val ZH = 28f

        fun dimToInt(dim: String): Int = when (dim) {
            "nether" -> 1
            "aether" -> 2
            else -> 0
        }
    }

    // Track whether a world has been started (replaces old chunks.isNotEmpty() checks)
    var worldActive = false
    val modifications = ConcurrentHashMap<String, Int>()
    val chests = ConcurrentHashMap<String, Array<SlotItem?>>()
    val furnaces = ConcurrentHashMap<String, FurnaceState>()
    val waystones = ConcurrentHashMap<String, WaystoneEntry>()
    val signTexts = ConcurrentHashMap<String, String>()

    init {
        RustEngine.nativeInit(seed, dimToInt(dimension))
    }

    fun blockKey(x: Int, y: Int, z: Int): String = "$dimension:$x,$y,$z"

    fun resetDimension(newDim: String, newSeed: Int = seed) {
        seed = newSeed
        dimension = newDim
        worldActive = true
        RustEngine.nativeResetDim(dimToInt(newDim), newSeed)
        if (newDim == "overworld") {
            setupStarterCampMetadata()
        }
    }

    private fun setupStarterCampMetadata() {
        val sz = 142
        val wsKey = blockKey(4, 3, sz)
        if (!waystones.containsKey(wsKey)) {
            waystones[wsKey] = WaystoneEntry("Стартовая База", 4, 3, sz, "overworld")
        }
        val signKey = blockKey(3, 3, sz)
        if (!signTexts.containsKey(signKey)) {
            signTexts[signKey] = "Лагерь"
        }
        val chKey = blockKey(1, 4, sz)
        if (!chests.containsKey(chKey)) {
            val stChest = arrayOfNulls<SlotItem>(27)
            stChest[0] = SlotItem(101, 12); stChest[1] = SlotItem(148, 1); stChest[2] = SlotItem(136, 3); stChest[3] = SlotItem(137, 2)
            stChest[4] = SlotItem(56, 16); stChest[5] = SlotItem(61, 1); stChest[6] = SlotItem(132, 1)
            chests[chKey] = stChest
        }
    }

    fun syncModsToRust() {
        RustEngine.nativeClearMods()
        for ((k, bId) in modifications) {
            val colon = k.indexOf(':')
            if (colon <= 0) continue
            val dimId = dimToInt(k.substring(0, colon))
            val parts = k.substring(colon + 1).split(",")
            if (parts.size != 3) continue
            RustEngine.nativePutMod(dimId, parts[0].toInt(), parts[1].toInt(), parts[2].toInt(), bId)
        }
    }

    fun getChest(x: Int, y: Int, z: Int): Array<SlotItem?> {
        val key = blockKey(x, y, z)
        return chests.computeIfAbsent(key) {
            val arr = arrayOfNulls<SlotItem>(27)
            // Naturally generated dungeon / abandoned mine chests automatically contain treasure!
            if (!modifications.containsKey(key) && !(x == 1 && y == 4 && z == 142)) {
                val h = abs(x * 73856093 xor y * 19349663 xor z * 83492791)
                if (z < 100) {
                    arr[0] = SlotItem(104, 2 + (h % 2))
                    arr[1] = SlotItem(141, 3)
                    arr[2] = SlotItem(137, 1)
                    arr[3] = SlotItem(56, 8)
                    arr[4] = SlotItem(142, 2)
                } else {
                    arr[0] = SlotItem(104, 2)
                    arr[1] = SlotItem(118, 3)
                    arr[2] = SlotItem(138, 2)
                    arr[3] = SlotItem(131, 1)
                }
            }
            arr
        }
    }

    fun getFurnace(x: Int, y: Int, z: Int): FurnaceState = furnaces.computeIfAbsent(blockKey(x, y, z)) { FurnaceState() }

    fun registerWaystone(x: Int, y: Int, z: Int, label: String? = null) {
        val k = blockKey(x, y, z)
        val nm = label ?: (if (z < SEA_Z - 10) "Шахта Z:$z ($x,$y)" else "База Z:$z ($x,$y)")
        waystones[k] = WaystoneEntry(nm, x, y, z, dimension)
    }

    fun removeWaystone(x: Int, y: Int, z: Int) {
        waystones.remove(blockKey(x, y, z))
    }

    fun getSurfaceZ(wx: Int, wy: Int): Int = RustEngine.nativeGetSurfaceZ(wx, wy)
    fun getTopBlockZ(wx: Int, wy: Int): Int = RustEngine.nativeGetTopBlockZ(wx, wy)
    fun getBlock(wx: Int, wy: Int, wz: Int): Int = RustEngine.nativeGetBlock(wx, wy, wz)

    fun setBlock(wx: Int, wy: Int, wz: Int, id: Int) {
        if (wz !in 1 until MAX_Z) return
        val old = RustEngine.nativeGetBlock(wx, wy, wz)
        if (old == 61 && id != 61) removeWaystone(wx, wy, wz)
        if (id == 61) registerWaystone(wx, wy, wz)
        if (old == 65 && id != 65) signTexts.remove(blockKey(wx, wy, wz))
        RustEngine.nativeSetBlock(wx, wy, wz, id)
        if (id in intArrayOf(37, 59, 60, 72)) RustEngine.nativeRevealFog(wx, wy, 7)
        modifications[blockKey(wx, wy, wz)] = id
    }

    fun hasRoofAbove(wx: Int, wy: Int, pz: Int): Boolean = RustEngine.nativeHasRoofAbove(wx, wy, pz)
    fun revealFog(wx: Int, wy: Int, rad: Int) = RustEngine.nativeRevealFog(wx, wy, rad)
    fun findElevatorZ(wx: Int, wy: Int, curZ: Int): Int = RustEngine.nativeFindElevatorZ(wx, wy, curZ)
    fun sampleMinimap(cx: Int, cy: Int, step: Int, size: Int, outRgba: ByteBuffer) = RustEngine.nativeSampleMinimap(cx, cy, step, size, outRgba)

    fun applySeasonalEnvironmentStep(season: Season, seasonProgress: Float, px: Int, py: Int) {
        RustEngine.nativeApplySeasonalStep(season.ordinal, seasonProgress, px, py, Random.nextInt())
    }

    fun getGroundZ(wx: Int, wy: Int, currentZ: Float): Float? {
        val res = RustEngine.nativeGetGroundZ(wx, wy, currentZ)
        return if (res < 0f) null else res
    }

    fun saveToFile(file: File, player: PlayerState, entities: EntityAndWeatherManager) {
        try {
            file.parentFile?.mkdirs()
            DataOutputStream(GZIPOutputStream(BufferedOutputStream(FileOutputStream(file)))).use { out ->
                out.writeInt(54)
                out.writeInt(seed); out.writeUTF(dimension)
                out.writeFloat(player.x); out.writeFloat(player.y); out.writeFloat(player.z)
                out.writeFloat(player.hp); out.writeFloat(player.maxHp); out.writeFloat(player.food)
                out.writeInt(player.weaponUpgradeLvl); out.writeInt(player.toolUpgradeLvl)
                out.writeBoolean(player.creative)
                out.writeInt(entities.dayCount); out.writeInt(entities.seasonLengthDays); out.writeFloat(entities.worldTime)
                for (i in 0 until 36) { out.writeInt(player.slots[i]?.id ?: 0); out.writeInt(player.slots[i]?.count ?: 0) }
                out.writeInt(player.offhand?.id ?: 0); out.writeInt(player.offhand?.count ?: 0)
                for (i in 0 until 4) out.writeInt(player.armor[i])
                out.writeInt(modifications.size); for ((k, v) in modifications) { out.writeUTF(k); out.writeInt(v) }
                out.writeInt(chests.size)
                for ((k, arr) in chests) {
                    out.writeUTF(k)
                    for (j in 0 until 27) { out.writeInt(arr[j]?.id ?: 0); out.writeInt(arr[j]?.count ?: 0) }
                }
                out.writeInt(waystones.size)
                for ((k, w) in waystones) { out.writeUTF(k); out.writeUTF(w.name); out.writeInt(w.x); out.writeInt(w.y); out.writeInt(w.z); out.writeUTF(w.dim) }
                out.writeInt(signTexts.size)
                for ((k, txt) in signTexts) { out.writeUTF(k); out.writeUTF(txt) }
            }
        } catch (_: Exception) {}
    }

    fun loadFromFile(file: File, player: PlayerState, entities: EntityAndWeatherManager): Boolean {
        if (!file.exists()) return false
        return try {
            DataInputStream(GZIPInputStream(BufferedInputStream(FileInputStream(file)))).use { inp ->
                val ver = inp.readInt()
                if (ver != 52 && ver != 54) return false
                val s = inp.readInt(); val dim = inp.readUTF()
                player.x = inp.readFloat(); player.y = inp.readFloat(); player.z = inp.readFloat()
                player.hp = inp.readFloat(); player.maxHp = inp.readFloat(); player.food = inp.readFloat()
                if (ver >= 54) { player.weaponUpgradeLvl = inp.readInt(); player.toolUpgradeLvl = inp.readInt() }
                player.creative = inp.readBoolean()
                entities.dayCount = inp.readInt(); entities.seasonLengthDays = inp.readInt(); entities.worldTime = inp.readFloat()
                for (i in 0 until 36) {
                    val id = inp.readInt(); val cnt = inp.readInt()
                    player.slots[i] = if (id > 0 && cnt > 0) SlotItem(id, cnt) else null
                }
                val ohId = inp.readInt(); val ohCnt = inp.readInt()
                player.offhand = if (ohId > 0 && ohCnt > 0) SlotItem(ohId, ohCnt) else null
                for (i in 0 until 4) player.armor[i] = inp.readInt()
                modifications.clear(); val mc = inp.readInt(); for (i in 0 until mc) modifications[inp.readUTF()] = inp.readInt()
                chests.clear(); val cc = inp.readInt()
                for (i in 0 until cc) {
                    val k = inp.readUTF(); val arr = arrayOfNulls<SlotItem>(27)
                    for (j in 0 until 27) { val id = inp.readInt(); val cnt = inp.readInt(); if (id > 0 && cnt > 0) arr[j] = SlotItem(id, cnt) }
                    chests[k] = arr
                }
                waystones.clear(); val wc = inp.readInt()
                for (i in 0 until wc) {
                    val k = inp.readUTF(); val nm = inp.readUTF()
                    waystones[k] = WaystoneEntry(nm, inp.readInt(), inp.readInt(), inp.readInt(), inp.readUTF())
                }
                signTexts.clear()
                if (ver >= 54) { val sc = inp.readInt(); for (i in 0 until sc) signTexts[inp.readUTF()] = inp.readUTF() }
                syncModsToRust()
                resetDimension(dim, s)
            }
            true
        } catch (_: Exception) { false }
    }
}
