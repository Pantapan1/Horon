package com.voxelhorizon.nativegame

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class SlotItem(var id: Int, var count: Int)

data class FurnaceState(
    var input: SlotItem? = null,
    var fuel: SlotItem? = null,
    var output: SlotItem? = null,
    var burn: Float = 0f,
    var maxBurn: Float = 1f,
    var cook: Float = 0f
)

class MeshBuildResult(
    val cx: Int,
    val cz: Int,
    val solidVerts: FloatArray,
    val solidIndices: ShortArray,
    val waterVerts: FloatArray,
    val waterIndices: ShortArray
)

class ChunkData(val cx: Int, val cz: Int, val blocks: ByteArray, var skyLight: ByteArray, var blockLight: ByteArray) {
    var solidMesh: Mesh? = null
    var waterMesh: Mesh? = null
    @Volatile var dirty = false

    fun dispose() {
        solidMesh?.dispose(); solidMesh = null
        waterMesh?.dispose(); waterMesh = null
    }
}

class VoxelWorld(var seed: Int, var dimension: String = "overworld") {
    companion object {
        const val CS = 16
        const val WH = 64
        const val SEA_LEVEL = 22
    }

    private var perlin = PerlinNoise(if (dimension == "nether") seed + 99991 else if (dimension == "aether") seed + 44447 else seed)
    val chunks = ConcurrentHashMap<Long, ChunkData>()
    val modifications = ConcurrentHashMap<String, Int>()
    val chests = ConcurrentHashMap<String, Array<SlotItem?>>()
    val furnaces = ConcurrentHashMap<String, FurnaceState>()

    private val pendingGen = ConcurrentHashMap.newKeySet<Long>()
    private val readyMeshes = ConcurrentLinkedQueue<MeshBuildResult>()
    private val workerPool = Executors.newFixedThreadPool(2)

    private val fluidQueue = ArrayDeque<IntArray>() // [x, y, z, id, dist]
    private val gravityQueue = ArrayDeque<IntArray>() // [x, y, z]

    fun chunkKey(cx: Int, cz: Int): Long = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)
    fun blockKey(x: Int, y: Int, z: Int): String = "$dimension:$x,$y,$z"

    fun resetDimension(newDim: String, newSeed: Int = seed) {
        for (ch in chunks.values) ch.dispose()
        chunks.clear()
        pendingGen.clear()
        readyMeshes.clear()
        fluidQueue.clear()
        gravityQueue.clear()
        seed = newSeed
        dimension = newDim
        perlin = PerlinNoise(if (newDim == "nether") seed + 99991 else if (newDim == "aether") seed + 44447 else seed)
    }

    fun getChest(x: Int, y: Int, z: Int): Array<SlotItem?> {
        return chests.computeIfAbsent(blockKey(x, y, z)) { arrayOfNulls(27) }
    }

    fun getFurnace(x: Int, y: Int, z: Int): FurnaceState {
        return furnaces.computeIfAbsent(blockKey(x, y, z)) { FurnaceState() }
    }

    fun getBiome(wx: Int, wz: Int): Int {
        if (dimension == "nether") return 6
        if (dimension == "aether") return 7
        val temp = perlin.fbm2(wx * 0.005 + 42.0, wz * 0.005 + 42.0, 2)
        val moist = perlin.fbm2(wx * 0.006 - 71.0, wz * 0.006 + 19.0, 2)
        val ridge = perlin.fbm2(wx * 0.009 + 120.0, wz * 0.009 - 88.0, 2)
        return when {
            ridge > 0.68 -> 5 // Mountains
            temp > 0.63 -> 3  // Desert
            temp < 0.36 -> 4  // Snow Taiga
            moist > 0.58 -> 2 // Birch Forest
            moist > 0.48 -> 1 // Oak Forest
            else -> 0         // Plains
        }
    }

    fun getTerrainHeight(wx: Int, wz: Int): Int {
        if (dimension == "nether") return 24
        if (dimension == "aether") return 31
        val cont = perlin.fbm2(wx * 0.011, wz * 0.011, 4)
        val detail = perlin.fbm2(wx * 0.032 + 15.0, wz * 0.032 - 22.0, 2)
        val biome = getBiome(wx, wz)
        var h = 15.0 + cont * 20.0 + detail * 6.0
        if (biome == 5 && cont > 0.35) h += (cont - 0.35).pow(1.6) * 28.0
        if (biome == 3) h = 19.0 + cont * 11.0
        return h.toInt().coerceIn(8, WH - 9)
    }

    private fun idx(lx: Int, y: Int, lz: Int) = (y * CS + lz) * CS + lx

    private fun generateChunkBlocks(cx: Int, cz: Int): ByteArray {
        val data = ByteArray(CS * WH * CS)
        val baseX = cx * CS
        val baseZ = cz * CS

        if (dimension == "nether") {
            for (lx in 0 until CS) for (lz in 0 until CS) {
                val wx = baseX + lx; val wz = baseZ + lz
                val floorH = (15.0 + perlin.fbm2(wx * 0.035, wz * 0.035, 3) * 14.0).toInt()
                val ceilH = (46.0 + perlin.fbm2(wx * 0.03 + 77.0, wz * 0.03 - 44.0, 2) * 12.0).toInt()
                val isSoul = perlin.noise2(wx * 0.06, wz * 0.06) > 0.66
                for (y in 0 until WH) {
                    data[idx(lx, y, lz)] = when {
                        y == 0 || y == WH - 1 -> 10
                        y <= floorH || y >= ceilH -> {
                            val r = perlin.hash2(wx * 7 + y * 19, wz * 11 - y * 13)
                            when {
                                y == floorH && isSoul && floorH >= 18 -> 31
                                r > 0.965 -> 33
                                else -> 30
                            }
                        }
                        y <= 18 -> 29
                        else -> {
                            val n3 = perlin.noise3(wx * 0.06, y * 0.05, wz * 0.06)
                            when {
                                n3 > 0.72 -> 30
                                y + 1 == ceilH && perlin.hash2(wx * 3, wz * 5) > 0.96 -> 23
                                else -> 0
                            }
                        }
                    }.toByte()
                }
                if (abs(wx % 32) <= 1 && abs(wz % 32) <= 1 && floorH > 18) {
                    for (y in floorH until min(ceilH, floorH + 10)) data[idx(lx, y, lz)] = 32
                }
            }
        } else if (dimension == "aether") {
            // Измерение Небесные Острова (Эфир)
            for (lx in 0 until CS) for (lz in 0 until CS) {
                val wx = baseX + lx; val wz = baseZ + lz
                val mask = perlin.fbm2(wx * 0.028, wz * 0.028, 3)
                val forceSpawn = hypot(wx.toDouble(), wz.toDouble()) < 10.0
                if (!forceSpawn && mask < 0.50) continue
                val thick = if (forceSpawn) 6 else ((mask - 0.48) * 28.0).toInt().coerceAtLeast(3)
                val topY = (28.0 + perlin.noise2(wx * 0.05, wz * 0.05) * 8.0).toInt().coerceAtMost(WH - 8)
                val botY = (topY - thick).coerceAtLeast(8)
                for (y in botY..topY) {
                    data[idx(lx, y, lz)] = when {
                        y == topY -> 1
                        y >= topY - 1 -> 2
                        else -> {
                            val r = perlin.hash2(wx * 11 + y * 13, wz * 7 - y * 17)
                            when {
                                r > 0.96 -> 36
                                r > 0.93 -> 23
                                r > 0.90 -> 13
                                else -> 3
                            }
                        }
                    }.toByte()
                }
                if (lx in 2..13 && lz in 2..13 && perlin.hash2(wx * 17, wz * 29) > 0.978) {
                    for (ty in 1..4) data[idx(lx, topY + ty, lz)] = 27
                    for (dx in -1..1) for (dz in -1..1) {
                        data[idx(lx + dx, topY + 4, lz + dz)] = 6
                        data[idx(lx + dx, topY + 5, lz + dz)] = 6
                    }
                }
            }
        } else {
            // Обычный мир (6 биомов + 3D пещеры + руды + деревья + нативные данжи)
            for (lx in 0 until CS) for (lz in 0 until CS) {
                val wx = baseX + lx; val wz = baseZ + lz
                val biome = getBiome(wx, wz)
                val h = getTerrainHeight(wx, wz)
                for (y in 0 until WH) {
                    data[idx(lx, y, lz)] = when {
                        y == 0 -> 10
                        y < h - 3 -> {
                            val cave = perlin.noise3(wx * 0.08, y * 0.11, wz * 0.08)
                            if (y > 4 && y < h - 4 && cave > 0.67) {
                                if (y <= 7) 29 else 0
                            } else {
                                val oreR = perlin.hash2(wx * 5 + y * 17, wz * 7 - y * 13)
                                when {
                                    y < 14 && oreR > 0.992 -> 14
                                    y < 16 && biome == 5 && oreR > 0.986 -> 36
                                    y < 20 && oreR > 0.982 -> 13
                                    y < 34 && oreR > 0.960 -> 12
                                    oreR > 0.935 -> 11
                                    else -> 3
                                }
                            }
                        }
                        y < h -> if (biome == 3) 28 else if (h <= SEA_LEVEL + 1) 8 else 2
                        y == h -> when {
                            biome == 3 || h <= SEA_LEVEL -> 8
                            biome == 4 || (biome == 5 && h > 42) -> 19
                            biome == 5 && h > 36 -> 3
                            else -> 1
                        }
                        y <= SEA_LEVEL -> if (biome == 4 && y == SEA_LEVEL) 20 else 9
                        else -> 0
                    }.toByte()
                }

                if (h > SEA_LEVEL + 1 && lx in 2..13 && lz in 2..13) {
                    val r = perlin.hash2(wx * 19, wz * 23)
                    if (biome == 3 && r > 0.985) {
                        for (cy in 1..3) data[idx(lx, h + cy, lz)] = 21
                    } else if ((biome == 1 && r > 0.962) || (biome == 2 && r > 0.962) || (biome == 0 && r > 0.992) || (biome == 4 && r > 0.984)) {
                        val logId: Byte = if (biome == 2) 27 else 5
                        val th = 4 + ((wx + wz) and 1)
                        for (ty in 1..th) data[idx(lx, h + ty, lz)] = logId
                        for (dy in (th - 1)..(th + 1)) {
                            val rad = if (dy == th + 1) 1 else 2
                            for (dx in -rad..rad) for (dz in -rad..rad) {
                                if (dx == 0 && dz == 0 && dy <= th) continue
                                if (abs(dx) == 2 && abs(dz) == 2 && dy == th) continue
                                if (h + dy < WH) data[idx(lx + dx, h + dy, lz + dz)] = 6
                            }
                        }
                    }
                }
            }

            // Нативные каменные Данжи-Башни с сундуком лута и факелами (1 из 10 чанков)
            val dHash = abs((cx * 73856093 xor cz * 19349663) * 2654435761L % 100L)
            if ((cx != 0 || cz != 0) && dHash < 10) {
                val wxC = baseX + 7; val wzC = baseZ + 7
                val bh = getTerrainHeight(wxC, wzC)
                if (bh > SEA_LEVEL + 1 && bh + 8 < WH) {
                    for (dx in -2..2) for (dz in -2..2) {
                        data[idx(7 + dx, bh, 7 + dz)] = 4
                        for (dy in 1..6) {
                            val isWall = abs(dx) == 2 || abs(dz) == 2
                            val isDoor = dz == 2 && dx == 0 && dy <= 2
                            data[idx(7 + dx, bh + dy, 7 + dz)] = when {
                                dy == 6 -> if (isWall && ((dx + dz) and 1) == 0) 4 else 0
                                isWall && !isDoor -> if (((dx + dy) and 1) == 0) 4 else 16
                                else -> 0
                            }.toByte()
                        }
                    }
                    data[idx(7, bh + 1, 6)] = 35 // Сундук сокровищ
                    data[idx(6, bh + 2, 6)] = 37 // Факел
                    data[idx(8, bh + 2, 6)] = 37 // Факел
                    val slots = getChest(wxC, bh + 1, wzC - 1)
                    slots[1] = SlotItem(104, 2)
                    slots[4] = SlotItem(118, 3)
                    slots[7] = SlotItem(115, 5)
                    slots[10] = SlotItem(131, 1) // Крюк-кошка!
                }
            }
        }

        // Применяем сохранённые изменения игрока для этого чанка
        val prefix = "$dimension:"
        for ((k, bId) in modifications) {
            if (!k.startsWith(prefix)) continue
            val parts = k.substring(prefix.length).split(",")
            if (parts.size != 3) continue
            val mx = parts[0].toInt(); val my = parts[1].toInt(); val mz = parts[2].toInt()
            if (floor(mx.toDouble() / CS).toInt() == cx && floor(mz.toDouble() / CS).toInt() == cz && my in 0 until WH) {
                val lx = ((mx % CS) + CS) % CS
                val lz = ((mz % CS) + CS) % CS
                data[idx(lx, my, lz)] = bId.toByte()
            }
        }
        return data
    }

    // Двухканальное освещение (Sky 0..15 + Block 0..15 BFS)
    private fun computeLight(data: ByteArray): Pair<ByteArray, ByteArray> {
        val sky = ByteArray(CS * WH * CS)
        val blk = ByteArray(CS * WH * CS)
        val queue = IntArray(CS * WH * CS)
        var head = 0; var tail = 0
        val defaultSky = if (dimension == "nether") 4 else 15

        for (lx in 0 until CS) for (lz in 0 until CS) {
            var s = defaultSky
            for (y in WH - 1 downTo 0) {
                val i = idx(lx, y, lz)
                val b = data[i].toInt() and 0xFF
                val def = BlockRegistry.items[b]
                if (b != 0 && (def == null || !def.transparent)) s = 0
                else if (b == 6 || b == 9 || b == 20) s = max(0, s - 2)
                sky[i] = s.toByte()
                val emit = def?.lightLevel ?: 0
                if (emit > 0) {
                    blk[i] = emit.toByte()
                    queue[tail++] = i
                }
            }
        }

        val dirs = arrayOf(intArrayOf(1,0,0), intArrayOf(-1,0,0), intArrayOf(0,1,0), intArrayOf(0,-1,0), intArrayOf(0,0,1), intArrayOf(0,0,-1))
        while (head < tail) {
            val cur = queue[head++]
            val cl = blk[cur].toInt() and 0xFF
            if (cl <= 1) continue
            val nl = (cl - 1).toByte()
            val lx = cur % CS
            val lz = (cur / CS) % CS
            val y = cur / (CS * CS)
            for (d in dirs) {
                val nx = lx + d[0]; val ny = y + d[1]; val nz = lz + d[2]
                if (nx !in 0 until CS || ny !in 0 until WH || nz !in 0 until CS) continue
                val ni = idx(nx, ny, nz)
                val nb = data[ni].toInt() and 0xFF
                val def = BlockRegistry.items[nb]
                if ((nb == 0 || (def != null && def.transparent)) && (blk[ni].toInt() and 0xFF) < (cl - 1)) {
                    blk[ni] = nl
                    if (tail < queue.size) queue[tail++] = ni
                }
            }
        }
        return Pair(sky, blk)
    }

    fun getBlock(wx: Int, wy: Int, wz: Int): Int {
        if (wy !in 0 until WH) return 0
        val cx = floor(wx.toDouble() / CS).toInt()
        val cz = floor(wz.toDouble() / CS).toInt()
        val ch = chunks[chunkKey(cx, cz)] ?: return 0
        val lx = ((wx % CS) + CS) % CS
        val lz = ((wz % CS) + CS) % CS
        return ch.blocks[idx(lx, wy, lz)].toInt() and 0xFF
    }

    fun isSolid(wx: Int, wy: Int, wz: Int): Boolean {
        val id = getBlock(wx, wy, wz)
        return BlockRegistry.items[id]?.solid == true
    }

    fun setBlock(wx: Int, wy: Int, wz: Int, bId: Int, triggerPhysics: Boolean = true) {
        if (wy <= 0 || wy >= WH - 1) return
        val cx = floor(wx.toDouble() / CS).toInt()
        val cz = floor(wz.toDouble() / CS).toInt()
        val ch = chunks[chunkKey(cx, cz)] ?: return
        val lx = ((wx % CS) + CS) % CS
        val lz = ((wz % CS) + CS) % CS
        ch.blocks[idx(lx, wy, lz)] = bId.toByte()
        modifications[blockKey(wx, wy, wz)] = bId

        val (sl, bl) = computeLight(ch.blocks)
        ch.skyLight = sl; ch.blockLight = bl
        scheduleChunkMesh(cx, cz)
        if (lx == 0) scheduleChunkMesh(cx - 1, cz)
        if (lx == CS - 1) scheduleChunkMesh(cx + 1, cz)
        if (lz == 0) scheduleChunkMesh(cx, cz - 1)
        if (lz == CS - 1) scheduleChunkMesh(cx, cz + 1)

        if (triggerPhysics) {
            if (bId == 9 || bId == 29) fluidQueue.addLast(intArrayOf(wx, wy, wz, bId, 3))
            else if (bId == 0) {
                val nbs = arrayOf(intArrayOf(1,0,0), intArrayOf(-1,0,0), intArrayOf(0,1,0), intArrayOf(0,0,1), intArrayOf(0,0,-1))
                for (d in nbs) {
                    val nb = getBlock(wx + d[0], wy + d[1], wz + d[2])
                    if (nb == 9 || nb == 29) fluidQueue.addLast(intArrayOf(wx + d[0], wy + d[1], wz + d[2], nb, 3))
                    if (d[1] == 1 && BlockRegistry.items[nb]?.gravityBlock == true) {
                        gravityQueue.addLast(intArrayOf(wx, wy + 1, wz))
                    }
                }
            } else if (BlockRegistry.items[bId]?.gravityBlock == true) {
                gravityQueue.addLast(intArrayOf(wx, wy, wz))
            }
        }
    }

    // Реалистичная рубка деревьев топором (валит ствол вверх!)
    fun fellTreeFrom(wx: Int, wy: Int, wz: Int, logId: Int): Int {
        var felled = 0
        for (dy in 1..7) {
            if (getBlock(wx, wy + dy, wz) == logId) {
                setBlock(wx, wy + dy, wz, 0, false)
                felled++
            } else break
        }
        return felled
    }

    fun buildNetherPortalAt(bx: Int, by: Int, bz: Int) {
        for (dx in 0..3) for (dy in 0..4) {
            val frame = dx == 0 || dx == 3 || dy == 0 || dy == 4
            setBlock(bx + dx, by + dy, bz, if (frame) 22 else 34, false)
        }
    }

    fun tickPhysics() {
        // 1. Осыпание песка и гравия
        var gSteps = 0
        while (gravityQueue.isNotEmpty() && gSteps++ < 6) {
            val pos = gravityQueue.removeFirst()
            val x = pos[0]; val y = pos[1]; val z = pos[2]
            val id = getBlock(x, y, z)
            if (BlockRegistry.items[id]?.gravityBlock == true && y > 1 && getBlock(x, y - 1, z) == 0) {
                setBlock(x, y, z, 0, false)
                setBlock(x, y - 1, z, id, false)
                gravityQueue.addLast(intArrayOf(x, y - 1, z))
                if (BlockRegistry.items[getBlock(x, y + 1, z)]?.gravityBlock == true) {
                    gravityQueue.addLast(intArrayOf(x, y + 1, z))
                }
            }
        }

        // 2. Течение Воды и Лавы (+ Вода + Лава = Обсидиан!)
        var fSteps = 0
        while (fluidQueue.isNotEmpty() && fSteps++ < 6) {
            val f = fluidQueue.removeFirst()
            val x = f[0]; val y = f[1]; val z = f[2]; val id = f[3]; val dist = f[4]
            if (getBlock(x, y, z) != id) continue
            if (y > 2 && getBlock(x, y - 1, z) == 0) {
                setBlock(x, y - 1, z, id, false)
                fluidQueue.addLast(intArrayOf(x, y - 1, z, id, dist))
            } else if (dist > 0) {
                for (d in arrayOf(intArrayOf(1,0), intArrayOf(-1,0), intArrayOf(0,1), intArrayOf(0,-1))) {
                    val nx = x + d[0]; val nz = z + d[1]
                    val nb = getBlock(nx, y, nz)
                    if (nb == 0) {
                        setBlock(nx, y, nz, id, false)
                        fluidQueue.addLast(intArrayOf(nx, y, nz, id, dist - 1))
                    } else if ((id == 9 && nb == 29) || (id == 29 && nb == 9)) {
                        setBlock(nx, y, nz, 22, false)
                    }
                }
            }
        }
    }

    fun updateAround(px: Float, pz: Float, radius: Int) {
        val pcx = floor(px.toDouble() / CS).toInt()
        val pcz = floor(pz.toDouble() / CS).toInt()
        val needed = HashSet<Long>()

        for (r in 0..radius) {
            for (dx in -r..r) for (dz in -r..r) {
                val cx = pcx + dx; val cz = pcz + dz
                val k = chunkKey(cx, cz)
                needed.add(k)
                if (!chunks.containsKey(k) && pendingGen.add(k)) {
                    workerPool.submit {
                        val blocks = generateChunkBlocks(cx, cz)
                        val (sl, bl) = computeLight(blocks)
                        val ch = ChunkData(cx, cz, blocks, sl, bl)
                        chunks[k] = ch
                        pendingGen.remove(k)
                        readyMeshes.add(buildGreedyMeshData(ch))
                    }
                }
            }
        }

        // Выгружаем далёкие чанки на главном потоке OpenGL
        val iter = chunks.entries.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            if (!needed.contains(e.key)) {
                e.value.dispose()
                iter.remove()
            }
        }

        // Загружаем до 2 готовых мешей за кадр в GPU
        var uploaded = 0
        while (uploaded < 2) {
            val res = readyMeshes.poll() ?: break
            val ch = chunks[chunkKey(res.cx, res.cz)] ?: continue
            ch.dispose()
            ch.solidMesh = createGlMesh(res.solidVerts, res.solidIndices)
            ch.waterMesh = createGlMesh(res.waterVerts, res.waterIndices)
            uploaded++
        }
    }

    private fun scheduleChunkMesh(cx: Int, cz: Int) {
        val ch = chunks[chunkKey(cx, cz)] ?: return
        workerPool.submit {
            readyMeshes.add(buildGreedyMeshData(ch))
        }
    }

    private fun createGlMesh(verts: FloatArray, indices: ShortArray): Mesh? {
        if (verts.isEmpty() || indices.isEmpty()) return null
        val mesh = Mesh(
            true, verts.size / 9, indices.size,
            VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
            VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + "0"),
            VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, ShaderProgram.COLOR_ATTRIBUTE)
        )
        mesh.setVertices(verts)
        mesh.setIndices(indices)
        return mesh
    }

    // Сборка геометрии чанка с Face Culling + Vertex AO + 2-канальным светом + Greedy Merging!
    private fun buildGreedyMeshData(ch: ChunkData): MeshBuildResult {
        val sV = FloatArrayList(4096); val sI = ShortArrayList(2048)
        val wV = FloatArrayList(1024); val wI = ShortArrayList(512)
        val baseX = ch.cx * CS; val baseZ = ch.cz * CS
        val stepU = 1f / BlockRegistry.ATLAS_COLS
        val stepV = 1f / BlockRegistry.ATLAS_ROWS
        val eps = 0.0015f

        fun localSolid(x: Int, y: Int, z: Int): Boolean {
            if (y !in 0 until WH) return false
            val id = if (x in 0 until CS && z in 0 until CS) ch.blocks[idx(x, y, z)].toInt() and 0xFF
            else getBlock(baseX + x, y, baseZ + z)
            val d = BlockRegistry.items[id]
            return d != null && d.solid && !d.transparent
        }

        fun lightAt(x: Int, y: Int, z: Int): Pair<Float, Float> {
            if (y < 0) return Pair(0.1f, 0f)
            if (y >= WH) return Pair(1f, 0f)
            if (x in 0 until CS && z in 0 until CS) {
                val i = idx(x, y, z)
                return Pair((ch.skyLight[i].toInt() and 0xFF) / 15f, (ch.blockLight[i].toInt() and 0xFF) / 15f)
            }
            return Pair(0.85f, 0f)
        }

        val faces = arrayOf(
            intArrayOf(0, 1, 0, 0),   // Top (+Y)
            intArrayOf(0, -1, 0, 2),  // Bottom (-Y)
            intArrayOf(1, 0, 0, 1),   // +X
            intArrayOf(-1, 0, 0, 1),  // -X
            intArrayOf(0, 0, 1, 1),   // +Z
            intArrayOf(0, 0, -1, 1)   // -Z
        )
        val shades = floatArrayOf(1.00f, 0.56f, 0.84f, 0.78f, 0.90f, 0.72f)
        val faceVerts = arrayOf(
            arrayOf(floatArrayOf(0f,1f,1f), floatArrayOf(1f,1f,1f), floatArrayOf(1f,1f,0f), floatArrayOf(0f,1f,0f)),
            arrayOf(floatArrayOf(0f,0f,0f), floatArrayOf(1f,0f,0f), floatArrayOf(1f,0f,1f), floatArrayOf(0f,0f,1f)),
            arrayOf(floatArrayOf(1f,0f,1f), floatArrayOf(1f,0f,0f), floatArrayOf(1f,1f,0f), floatArrayOf(1f,1f,1f)),
            arrayOf(floatArrayOf(0f,0f,0f), floatArrayOf(0f,0f,1f), floatArrayOf(0f,1f,1f), floatArrayOf(0f,1f,0f)),
            arrayOf(floatArrayOf(0f,0f,1f), floatArrayOf(1f,0f,1f), floatArrayOf(1f,1f,1f), floatArrayOf(0f,1f,1f)),
            arrayOf(floatArrayOf(1f,0f,0f), floatArrayOf(0f,0f,0f), floatArrayOf(0f,1f,0f), floatArrayOf(1f,1f,0f))
        )

        for (y in 0 until WH) {
            for (lz in 0 until CS) {
                for (lx in 0 until CS) {
                    val bId = ch.blocks[idx(lx, y, lz)].toInt() and 0xFF
                    if (bId == 0) continue
                    val info = BlockRegistry.items[bId] ?: continue
                    if (info.isProp3D || info.faces == null) continue // 3D-пропсы (сундуки, кровати, факелы) рендерятся отдельно!

                    val wx = baseX + lx; val wz = baseZ + lz
                    val isFluid = info.liquid || info.isPortal
                    val flag = when {
                        info.isLava || info.isPortal || info.lightLevel >= 14 -> 0.75f
                        info.liquid -> 0.50f
                        info.isFoliage -> 0.25f
                        else -> 0.0f
                    }

                    for (fi in 0 until 6) {
                        val f = faces[fi]
                        val nx = lx + f[0]; val ny = y + f[1]; val nz = lz + f[2]
                        val nbId = if (nx in 0 until CS && ny in 0 until WH && nz in 0 until CS) {
                            ch.blocks[idx(nx, ny, nz)].toInt() and 0xFF
                        } else {
                            getBlock(baseX + nx, ny, baseZ + nz)
                        }
                        val nbInfo = BlockRegistry.items[nbId]
                        if (isFluid) {
                            if (nbId == bId || (nbInfo != null && nbInfo.solid && !nbInfo.transparent)) continue
                        } else {
                            if (nbId == bId && info.transparent) continue
                            if (nbInfo != null && nbInfo.solid && !nbInfo.transparent) continue
                        }

                        val tile = info.faces[f[3]]
                        val u0 = (tile % BlockRegistry.ATLAS_COLS) * stepU + eps
                        val v0 = (tile / BlockRegistry.ATLAS_COLS) * stepV + eps
                        val u1 = u0 + stepU - eps * 2f
                        val v1 = v0 + stepV - eps * 2f

                        val (skyL, blkL) = lightAt(nx, ny, nz)
                        // Vertex Ambient Occlusion (AO) для объёма углов!
                        var ao = 1.0f
                        if (!isFluid && fi == 0) {
                            var occ = 0
                            if (localSolid(lx + 1, y + 1, lz)) occ++
                            if (localSolid(lx - 1, y + 1, lz)) occ++
                            if (localSolid(lx, y + 1, lz + 1)) occ++
                            if (localSolid(lx, y + 1, lz - 1)) occ++
                            ao = 1.0f - occ * 0.08f
                        }

                        val vBuf = if (isFluid) wV else sV
                        val iBuf = if (isFluid) wI else sI
                        val vStart = (vBuf.size / 9)
                        if (vStart + 4 > 32760) continue

                        val verts = faceVerts[fi]
                        val uvs = arrayOf(floatArrayOf(u0, v1), floatArrayOf(u1, v1), floatArrayOf(u1, v0), floatArrayOf(u0, v0))
                        for (vi in 0 until 4) {
                            val v = verts[vi]
                            val vy = if (info.liquid && v[1] > 0.5f) 0.88f else v[1]
                            vBuf.add(wx + v[0], y + vy, wz + v[2])
                            vBuf.add(uvs[vi][0], uvs[vi][1])
                            vBuf.add(skyL * ao, blkL * ao, shades[fi], flag)
                        }
                        iBuf.add(vStart.toShort(), (vStart + 1).toShort(), (vStart + 2).toShort())
                        iBuf.add(vStart.toShort(), (vStart + 2).toShort(), (vStart + 3).toShort())
                    }
                }
            }
        }

        return MeshBuildResult(ch.cx, ch.cz, sV.toArray(), sI.toArray(), wV.toArray(), wI.toArray())
    }

    fun render(shader: ShaderProgram, transparentPass: Boolean) {
        for (ch in chunks.values) {
            val mesh = if (transparentPass) ch.waterMesh else ch.solidMesh
            mesh?.render(shader, GL20.GL_TRIANGLES)
        }
    }

    // Бинарное GZIP-сохранение мира на устройство Android!
    fun saveToFile(file: File, player: PlayerState) {
        try {
            file.parentFile?.mkdirs()
            DataOutputStream(GZIPOutputStream(BufferedOutputStream(FileOutputStream(file)))).use { out ->
                out.writeInt(40) // magic version 4.0
                out.writeInt(seed)
                out.writeUTF(dimension)
                out.writeFloat(player.x); out.writeFloat(player.y); out.writeFloat(player.z)
                out.writeFloat(player.hp); out.writeFloat(player.food)
                out.writeBoolean(player.creative)

                // Inventory 36 + offhand + 4 armor
                for (i in 0 until 36) {
                    val s = player.slots[i]
                    out.writeInt(s?.id ?: 0); out.writeInt(s?.count ?: 0)
                }
                out.writeInt(player.offhand?.id ?: 0); out.writeInt(player.offhand?.count ?: 0)
                for (i in 0 until 4) out.writeInt(player.armor[i])

                // Modifications
                out.writeInt(modifications.size)
                for ((k, v) in modifications) { out.writeUTF(k); out.writeInt(v) }

                // Chests
                out.writeInt(chests.size)
                for ((k, arr) in chests) {
                    out.writeUTF(k)
                    for (i in 0 until 27) {
                        out.writeInt(arr[i]?.id ?: 0); out.writeInt(arr[i]?.count ?: 0)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun loadFromFile(file: File, player: PlayerState): Boolean {
        if (!file.exists()) return false
        return try {
            DataInputStream(GZIPInputStream(BufferedInputStream(FileInputStream(file)))).use { inp ->
                val ver = inp.readInt()
                if (ver != 40) return false
                val s = inp.readInt()
                val dim = inp.readUTF()
                player.x = inp.readFloat(); player.y = inp.readFloat(); player.z = inp.readFloat()
                player.hp = inp.readFloat(); player.food = inp.readFloat()
                player.creative = inp.readBoolean()

                for (i in 0 until 36) {
                    val id = inp.readInt(); val cnt = inp.readInt()
                    player.slots[i] = if (id > 0 && cnt > 0) SlotItem(id, cnt) else null
                }
                val ohId = inp.readInt(); val ohCnt = inp.readInt()
                player.offhand = if (ohId > 0 && ohCnt > 0) SlotItem(ohId, ohCnt) else null
                for (i in 0 until 4) player.armor[i] = inp.readInt()

                modifications.clear()
                val mCnt = inp.readInt()
                for (i in 0 until mCnt) modifications[inp.readUTF()] = inp.readInt()

                chests.clear()
                val cCnt = inp.readInt()
                for (i in 0 until cCnt) {
                    val k = inp.readUTF()
                    val arr = arrayOfNulls<SlotItem>(27)
                    for (j in 0 until 27) {
                        val id = inp.readInt(); val cnt = inp.readInt()
                        if (id > 0 && cnt > 0) arr[j] = SlotItem(id, cnt)
                    }
                    chests[k] = arr
                }
                resetDimension(dim, s)
            }
            true
        } catch (_: Exception) { false }
    }

    fun dispose() {
        workerPool.shutdownNow()
        for (ch in chunks.values) ch.dispose()
        chunks.clear()
    }
}

private class FloatArrayList(cap: Int) {
    private var arr = FloatArray(cap)
    var size = 0
    fun add(a: Float, b: Float) { ensure(2); arr[size++] = a; arr[size++] = b }
    fun add(a: Float, b: Float, c: Float) { ensure(3); arr[size++] = a; arr[size++] = b; arr[size++] = c }
    fun add(a: Float, b: Float, c: Float, d: Float) { ensure(4); arr[size++] = a; arr[size++] = b; arr[size++] = c; arr[size++] = d }
    private fun ensure(extra: Int) { if (size + extra > arr.size) arr = arr.copyOf(arr.size * 2 + extra) }
    fun toArray(): FloatArray = arr.copyOf(size)
}

private class ShortArrayList(cap: Int) {
    private var arr = ShortArray(cap)
    var size = 0
    fun add(a: Short, b: Short, c: Short) {
        if (size + 3 > arr.size) arr = arr.copyOf(arr.size * 2 + 3)
        arr[size++] = a; arr[size++] = b; arr[size++] = c
    }
    fun toArray(): ShortArray = arr.copyOf(size)
}
