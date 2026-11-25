package com.voxelhorizon.nativegame

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector3
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class UiScreen {
    MAIN_MENU, PLAYING, PAUSE, INVENTORY, WORKBENCH, FURNACE, CHEST, SETTINGS_MODHUB, WARDROBE, MULTIPLAYER, DEATH
}

data class RayHit(val x: Int, val y: Int, val z: Int, val id: Int, val px: Int, val py: Int, val pz: Int)

class VoxelHorizonGame(private val storageRoot: File) : ApplicationAdapter() {

    lateinit var world: VoxelWorld
    lateinit var entities: EntityRendererAndManager
    lateinit var weatherSky: WeatherAndSky
    lateinit var luaMods: LuaModEngine
    lateinit var netMp: NetMultiplayer
    val player = PlayerState()

    private lateinit var cam3D: PerspectiveCamera
    private lateinit var camUI: OrthographicCamera
    private lateinit var terrainShader: ShaderProgram
    private lateinit var modelBatch: ModelBatch
    private lateinit var env3D: Environment
    private lateinit var spriteBatch: SpriteBatch
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var font: BitmapFont
    private lateinit var fontSmall: BitmapFont
    private var coverTex: Texture? = null

    private var bgMusic: Music? = null
    private var currentOst = ""

    var screen = UiScreen.MAIN_MENU
    var renderDist = 5
    var sensitivity = 1.0f
    var useTwoFinger = true
    var soundEnabled = true
    var gravity = 21f
    var worldFileName = "world_main.vhworld"

    private var elapsed = 0f
    private var physicsTimer = 0f
    private var hungerTimer = 0f
    private var mpPosTimer = 0f
    private var toastText = ""
    private var toastTimer = 0f
    private val chatLog = ArrayDeque<String>()

    // Mining & Touch State
    private var movePtr = -1
    private var moveOx = 0f; private var moveOy = 0f
    private var stickDx = 0f; private var stickDy = 0f
    private var lookPtr = -1
    private var lookSx = 0f; private var lookSy = 0f
    private var lookLx = 0f; private var lookLy = 0f
    private var lookDownTime = 0f
    private var lookMoved = false
    private var holdingMine = false
    private var mineX = 0; private var mineY = 0; private var mineZ = 0
    private var mineProgress = 0f
    private var jumpHeld = false
    private var lastJumpTap = 0f
    private var dpadUp = false; private var dpadDown = false; private var dpadLeft = false; private var dpadRight = false

    private var openFurnacePos: Triple<Int, Int, Int>? = null
    private var openChestPos: Triple<Int, Int, Int>? = null
    private var invSelectedSlot = -1
    private var settingsTab = 0 // 0=Игра, 1=Хаб Модов (Lua)

    private val buttons = mutableListOf<Pair<Rectangle, () -> Unit>>()

    override fun create() {
        BlockRegistry.init()
        terrainShader = VoxelShaders.createTerrainShader()
        modelBatch = ModelBatch()
        env3D = Environment().apply {
            set(ColorAttribute(ColorAttribute.AmbientLight, 0.72f, 0.72f, 0.76f, 1f))
            add(DirectionalLight().set(0.85f, 0.82f, 0.78f, -0.5f, -0.8f, -0.4f))
        }
        spriteBatch = SpriteBatch()
        shapeRenderer = ShapeRenderer()

        cam3D = PerspectiveCamera(72f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat()).apply {
            near = 0.08f; far = 160f
        }
        camUI = OrthographicCamera(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        camUI.setToOrtho(false, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())

        loadFonts()
        val cov = Gdx.files.internal("cover_anime.jpg")
        if (cov.exists()) coverTex = Texture(cov)

        world = VoxelWorld(Random.nextInt(100000, 999999), "overworld")
        entities = EntityRendererAndManager()
        weatherSky = WeatherAndSky()
        netMp = NetMultiplayer(this)
        luaMods = LuaModEngine(File(storageRoot, "mods"), this)
        luaMods.hotReload()

        setupStarterInventory()
        setupInputProcessor()
    }

    private fun loadFonts() {
        val cyrillic = FreeTypeFontGenerator.DEFAULT_CHARS +
            "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя•—«»№"
        val fFile = Gdx.files.internal("font.ttf")
        if (fFile.exists()) {
            val gen = FreeTypeFontGenerator(fFile)
            val p1 = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                size = max(20, Gdx.graphics.height / 32)
                characters = cyrillic
                color = Color.WHITE
                shadowOffsetX = 2; shadowOffsetY = 2
                shadowColor = Color(0f, 0f, 0f, 0.75f)
            }
            val p2 = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                size = max(15, Gdx.graphics.height / 44)
                characters = cyrillic
                color = Color.WHITE
                shadowOffsetX = 1; shadowOffsetY = 1
                shadowColor = Color(0f, 0f, 0f, 0.75f)
            }
            font = gen.generateFont(p1)
            fontSmall = gen.generateFont(p2)
            gen.dispose()
        } else {
            font = BitmapFont()
            fontSmall = BitmapFont()
        }
    }

    private fun setupStarterInventory() {
        player.slots.fill(null)
        player.slots[0] = SlotItem(108, 1)  // Железная кирка
        player.slots[1] = SlotItem(111, 1)  // Железная Катана
        player.slots[2] = SlotItem(110, 1)  // Топор дровосека
        player.slots[3] = SlotItem(131, 1)  // Крюк-кошка!
        player.slots[4] = SlotItem(7, 24)   // Доски
        player.slots[5] = SlotItem(17, 1)   // Верстак
        player.slots[6] = SlotItem(18, 1)   // Печь
        player.slots[7] = SlotItem(35, 2)   // 3D Сундук
        player.slots[8] = SlotItem(38, 1)   // 2-блочная 3D Кровать
        player.slots[9] = SlotItem(101, 12) // Уголь
        player.slots[10] = SlotItem(12, 8)  // Железная руда
        player.slots[11] = SlotItem(22, 14) // Обсидиан
        player.slots[12] = SlotItem(116, 1) // Огниво
        player.slots[13] = SlotItem(123, 1) // Лук
        player.slots[14] = SlotItem(124, 16)// Стрелы
        player.slots[15] = SlotItem(113, 8) // Яблоки
        player.slots[16] = SlotItem(130, 1) // Боевой Молот AoE
        player.slots[17] = SlotItem(132, 1) // Щит для второй руки
        player.offhand = SlotItem(37, 16)   // Факел во второй руке!
        player.armor[0] = 125
    }

    fun startNewOrLoadedWorld(loadSaved: Boolean, creativeMode: Boolean) {
        val f = File(File(storageRoot, "worlds"), worldFileName)
        if (loadSaved && world.loadFromFile(f, player)) {
            showToast("Мир загружен с устройства!")
        } else {
            world.resetDimension("overworld", Random.nextInt(100000, 999999))
            player.creative = creativeMode
            player.x = 0.5f; player.z = 0.5f
            player.y = world.getTerrainHeight(0, 0) + 3.5f
            player.spawnX = player.x; player.spawnY = player.y; player.spawnZ = player.z; player.spawnDim = "overworld"
            player.hp = 20f; player.food = 20f
            setupStarterInventory()
            showToast("Создан новый мир (Сид ${world.seed})")
        }
        entities.mobs.clear(); entities.drops.clear(); entities.projectiles.clear()
        screen = UiScreen.PLAYING
    }

    fun showToast(msg: String) {
        toastText = msg
        toastTimer = 2.6f
    }

    fun addChat(line: String) {
        Gdx.app.postRunnable {
            chatLog.addLast(line)
            while (chatLog.size > 6) chatLog.removeFirst()
        }
    }

    fun setWorldBlock(x: Int, y: Int, z: Int, id: Int) {
        Gdx.app.postRunnable {
            world.setBlock(x, y, z, id, true)
            if (netMp.isConnected) netMp.broadcastBlock(x, y, z, id)
        }
    }

    fun applyRemoteBlock(x: Int, y: Int, z: Int, id: Int) {
        Gdx.app.postRunnable { world.setBlock(x, y, z, id, true) }
    }

    fun applyHostWorldInit(seed: Int, dim: String, hx: Float, hy: Float, hz: Float, mods: Map<String, Int>) {
        Gdx.app.postRunnable {
            world.modifications.clear()
            world.modifications.putAll(mods)
            world.resetDimension(dim, seed)
            player.x = hx + 1.5f; player.y = hy + 0.5f; player.z = hz + 1.5f
            screen = UiScreen.PLAYING
            showToast("Мир Хоста синхронизирован!")
        }
    }

    fun switchDimension(targetDim: String) {
        if (world.dimension == targetDim) return
        world.resetDimension(targetDim, world.seed)
        entities.mobs.clear(); entities.drops.clear(); entities.projectiles.clear()
        player.x = 2.5f; player.z = 2.5f
        player.y = when (targetDim) {
            "nether" -> 26.5f
            "aether" -> 35.0f
            else -> world.getTerrainHeight(2, 2) + 3.0f
        }
        player.vx = 0f; player.vy = 0f; player.vz = 0f
        showToast(when (targetDim) {
            "nether" -> "Вы вошли в АД (Nether)!"
            "aether" -> "Вы вошли в Небесные Острова (Эфир)!"
            else -> "Возвращение в Обычный мир!"
        })
    }

    private fun updateOstMusic() {
        if (!soundEnabled || screen == UiScreen.MAIN_MENU) {
            bgMusic?.stop(); currentOst = ""; return
        }
        val target = when {
            entities.getActiveBoss() != null -> "ost_boss.wav"
            world.dimension == "nether" -> "ost_nether.wav"
            weatherSky.dayFactor < 0.22f -> "ost_overworld_night.wav"
            else -> "ost_overworld_day.wav"
        }
        if (target == currentOst && bgMusic?.isPlaying == true) return
        currentOst = target
        try {
            bgMusic?.stop(); bgMusic?.dispose()
            val f = Gdx.files.internal(target)
            if (f.exists()) {
                bgMusic = Gdx.audio.newMusic(f).apply {
                    isLooping = true
                    volume = 0.28f
                    play()
                }
            }
        } catch (_: Exception) {}
    }

    // --- RAYCAST ПО БЛОКАМ ---
    private fun raycastBlock(maxDist: Float = 5.5f): RayHit? {
        val dx = -sin(player.yaw) * cos(player.pitch)
        val dy = sin(player.pitch)
        val dz = -cos(player.yaw) * cos(player.pitch)
        val ox = player.x; val oy = player.y + (if (player.sneaking) 1.32f else 1.56f); val oz = player.z
        var px = floor(ox).toInt(); var py = floor(oy).toInt(); var pz = floor(oz).toInt()
        var d = 0.15f
        while (d <= maxDist) {
            val bx = floor(ox + dx * d).toInt()
            val by = floor(oy + dy * d).toInt()
            val bz = floor(oz + dz * d).toInt()
            if (bx != px || by != py || bz != pz) {
                val id = world.getBlock(bx, by, bz)
                val info = BlockRegistry.items[id]
                if (id > 0 && info != null && !info.liquid && !info.isPortal) {
                    return RayHit(bx, by, bz, id, px, py, pz)
                }
                px = bx; py = by; pz = bz
            }
            d += 0.08f
        }
        return null
    }

    private fun explodeAt(cx: Int, cy: Int, cz: Int, rad: Float) {
        val r = rad.toInt() + 1
        for (dx in -r..r) for (dy in -r..r) for (dz in -r..r) {
            if (dx * dx + dy * dy + dz * dz <= rad * rad && cy + dy > 1) {
                world.setBlock(cx + dx, cy + dy, cz + dz, 0, true)
                if (netMp.isConnected) netMp.broadcastBlock(cx + dx, cy + dy, cz + dz, 0)
            }
        }
    }

    private fun handleMobKill(dead: MobEntity) {
        val dropId = when (dead.type) {
            "sheep", "cow", "pig", "chicken" -> 114
            "zombie" -> 101; "skeleton" -> 124; "creeper" -> 26; "enderman" -> 104
            "piglin" -> 103; "blaze" -> 23; "ghast" -> 104
            "inferno_lord", "sky_guardian" -> 129
            else -> 101
        }
        entities.spawnDrop(dropId, 1, dead.x, dead.y + 0.5f, dead.z)
        showToast(if (dead.isBoss) "БОСС ПОВЕРЖЕН! Выпала Звезда Преисподней!" else "Побеждён: ${dead.name}")
    }

    private fun performTapAction() {
        player.armSwing = 1.0f
        val held = player.slots[player.selectedSlot]
        val heldDef = held?.let { BlockRegistry.items[it.id] }
        val dx = -sin(player.yaw) * cos(player.pitch)
        val dy = sin(player.pitch)
        val dz = -cos(player.yaw) * cos(player.pitch)

        // 1. Крюк-кошка (притягивает игрока к любой стене/горе!)
        if (heldDef?.weaponStyle == "grapple") {
            entities.fireProjectile(player.x + dx * 0.7f, player.y + 1.5f, player.z + dz * 0.7f, dx, dy, dz, 3f, false, true, isGrapple = true)
            return
        }

        // 2. Лук (стрельба стрелами)
        if (heldDef?.weaponStyle == "bow") {
            if (player.creative || player.takeItem(124, 1)) {
                entities.fireProjectile(player.x + dx * 0.7f, player.y + 1.5f, player.z + dz * 0.7f, dx, dy + 0.08f, dz, heldDef.damage.toFloat(), false, true)
            } else showToast("Нет стрел!")
            return
        }

        // 3. Атака моба (с учётом крита в прыжке + AoE Молота!)
        val isCrit = !player.onGround && player.vy < -0.5f
        val baseDmg = (heldDef?.damage ?: 3).toFloat() * (if (isCrit) 1.5f else 1.0f)
        if (entities.hitMobInRay(player.x, player.y + 1.56f, player.z, dx, dy, dz, 4.6f, baseDmg, heldDef?.weaponStyle == "hammer", ::handleMobKill)) {
            if (isCrit) showToast("КРИТ! ${baseDmg.toInt()} урона")
            return
        }

        // 4. Еда
        if (heldDef != null && heldDef.healFood > 0 && !player.creative && (player.food < 20f || player.hp < 20f)) {
            if (player.takeItem(held.id, 1)) {
                player.food = min(20f, player.food + heldDef.healFood)
                player.hp = min(20f, player.hp + heldDef.healHp)
                showToast("Съедено: ${heldDef.name}")
                return
            }
        }

        // 5. Взаимодействие с блоками или установка блока
        val hit = raycastBlock(5.5f) ?: return
        val targetDef = BlockRegistry.items[hit.id]

        if (held?.id == 116 && hit.id == 22) {
            world.buildNetherPortalAt(hit.x - 1, hit.y, hit.z)
            showToast("Портал в Ад активирован!")
            return
        }
        if (held?.id == 119) { setWorldBlock(hit.px, hit.py, hit.pz, 9); return }
        if (held?.id == 120) { setWorldBlock(hit.px, hit.py, hit.pz, 29); return }

        when (targetDef?.interactive) {
            "craft" -> { screen = UiScreen.WORKBENCH; return }
            "smelt" -> { openFurnacePos = Triple(hit.x, hit.y, hit.z); screen = UiScreen.FURNACE; return }
            "chest" -> {
                openChestPos = Triple(hit.x, hit.y, hit.z)
                entities.openChestKey = world.blockKey(hit.x, hit.y, hit.z)
                screen = UiScreen.CHEST
                return
            }
            "bed" -> {
                player.spawnX = hit.x + 0.5f; player.spawnY = hit.y + 1.1f; player.spawnZ = hit.z + 0.5f; player.spawnDim = world.dimension
                weatherSky.worldTime = 0.26f
                showToast("Точка возрождения установлена! Доброе утро.")
                return
            }
            "tnt" -> { explodeAt(hit.x, hit.y, hit.z, 2.8f); return }
        }

        // Установка блока из главной руки (или из второй руки Off-hand, если в главной инструмент!)
        val placeSlot = if (heldDef != null && !heldDef.isItem) held
        else if (player.offhand != null && BlockRegistry.items[player.offhand!!.id]?.isItem == false) player.offhand
        else null
        if (placeSlot != null) {
            val pDef = BlockRegistry.items[placeSlot.id] ?: return
            val inPlayer = abs(hit.px + 0.5f - player.x) < 0.7f && abs(hit.pz + 0.5f - player.z) < 0.7f && (hit.py == floor(player.y).toInt() || hit.py == floor(player.y + 1f).toInt())
            if (inPlayer && pDef.solid) return
            if (player.takeItem(placeSlot.id, 1)) {
                setWorldBlock(hit.px, hit.py, hit.pz, placeSlot.id)
            }
        }
    }

    private fun updateMining(dt: Float) {
        if (!holdingMine) { mineProgress = 0f; return }
        player.armSwing = 1.0f
        val hit = raycastBlock(5.5f)
        if (hit == null) { mineProgress = 0f; return }
        val bDef = BlockRegistry.items[hit.id] ?: return
        if (bDef.hardness >= 999f) return

        val held = player.slots[player.selectedSlot]
        val hDef = held?.let { BlockRegistry.items[it.id] }
        if (player.creative) {
            setWorldBlock(hit.x, hit.y, hit.z, 0)
            holdingMine = false
            return
        }
        if (hit.x != mineX || hit.y != mineY || hit.z != mineZ) {
            mineX = hit.x; mineY = hit.y; mineZ = hit.z; mineProgress = 0f
        }
        val speed = if (hDef != null && hDef.toolType == bDef.toolType) hDef.mineSpeed else 1.0f
        mineProgress += (dt * speed) / max(0.18f, bDef.hardness)
        if (mineProgress >= 1.0f) {
            setWorldBlock(hit.x, hit.y, hit.z, 0)
            if (bDef.dropId > 0) entities.spawnDrop(bDef.dropId, 1, hit.x + 0.5f, hit.y + 0.5f, hit.z + 0.5f)
            // Реалистичная рубка деревьев топором!
            if ((hit.id == 5 || hit.id == 27) && hDef?.toolType == "axe") {
                val extra = world.fellTreeFrom(hit.x, hit.y, hit.z, hit.id)
                if (extra > 0) {
                    entities.spawnDrop(hit.id, extra, hit.x + 0.5f, hit.y + 1.2f, hit.z + 0.5f)
                    showToast("Дерево срублено (+${extra + 1} брёвен)!")
                }
            }
            luaMods.emitBlockBreak(hit.x, hit.y, hit.z, hit.id, held?.id ?: 0)
            mineProgress = 0f
        }
    }

    private fun performDash() {
        if (player.dashCooldown > 0f) return
        player.dashCooldown = 1.8f
        val fx = -sin(player.yaw); val fz = -cos(player.yaw)
        player.vx = fx * 15f; player.vz = fz * 15f
        if (player.onGround) player.vy = 3.2f
        showToast("Рывок!")
    }

    private fun dropHeldItem() {
        val s = player.slots[player.selectedSlot] ?: return
        if (player.takeItem(s.id, 1)) {
            val fx = -sin(player.yaw); val fz = -cos(player.yaw)
            entities.spawnDrop(s.id, 1, player.x + fx * 1.1f, player.y + 1.25f, player.z + fz * 1.1f, fx * 4.5f, 3.0f, fz * 4.5f)
        }
    }

    private fun updateFurnaces(dt: Float) {
        for (f in world.furnaces.values) {
            if (f.burn > 0f) f.burn = max(0f, f.burn - dt * 10f)
            val outId = f.input?.let { BlockRegistry.smeltMap[it.id] }
            val canCook = outId != null && (f.output == null || (f.output!!.id == outId && f.output!!.count < 64))
            if (canCook && f.burn <= 0f && f.fuel != null) {
                val fp = BlockRegistry.items[f.fuel!!.id]?.fuelPower ?: 0f
                if (fp > 0f) {
                    f.maxBurn = fp; f.burn = fp
                    f.fuel!!.count--
                    if (f.fuel!!.count <= 0) f.fuel = null
                }
            }
            if (canCook && f.burn > 0f) {
                f.cook += dt * 20f
                if (f.cook >= 100f) {
                    f.cook = 0f
                    f.input!!.count--
                    if (f.input!!.count <= 0) f.input = null
                    if (f.output == null) f.output = SlotItem(outId!!, 1)
                    else f.output!!.count++
                }
            } else if (!canCook) f.cook = 0f
        }
    }

    private fun collides(x: Float, y: Float, z: Float): Boolean {
        val r = 0.28f
        for (dy in floatArrayOf(0.1f, 0.9f, 1.65f))
            for (dx in floatArrayOf(-r, r))
                for (dz in floatArrayOf(-r, r))
                    if (world.isSolid(floor(x + dx).toInt(), floor(y + dy).toInt(), floor(z + dz).toInt())) return true
        return false
    }

    private fun damagePlayer(raw: Float) {
        if (player.creative || raw <= 0f) return
        val dmg = max(1f, raw * (1f - player.getTotalArmor() * 0.045f))
        player.hp = max(0f, player.hp - dmg)
        if (player.hp <= 0f) screen = UiScreen.DEATH
    }

    private fun updatePlayerPhysics(dt: Float) {
        // Ввод с экрана + Геймпада + Клавиатуры
        if (GamepadBridge.dashTriggered) { GamepadBridge.dashTriggered = false; performDash() }
        if (GamepadBridge.swapOffhandTriggered) { GamepadBridge.swapOffhandTriggered = false; player.swapWithOffhand(); showToast("Смена второй руки") }
        if (GamepadBridge.hotbarDelta != 0) {
            player.selectedSlot = (player.selectedSlot + GamepadBridge.hotbarDelta + 9) % 9
            GamepadBridge.hotbarDelta = 0
        }
        if (GamepadBridge.lookX != 0f || GamepadBridge.lookY != 0f) {
            player.yaw -= GamepadBridge.lookX * dt * 2.6f * sensitivity
            player.pitch = (player.pitch + GamepadBridge.lookY * dt * 2.2f * sensitivity).coerceIn(-1.46f, 1.46f)
        }

        var mx = stickDx + GamepadBridge.moveX + (if (dpadRight || Gdx.input.isKeyPressed(Input.Keys.D)) 1f else 0f) - (if (dpadLeft || Gdx.input.isKeyPressed(Input.Keys.A)) 1f else 0f)
        var mz = stickDy + GamepadBridge.moveY + (if (dpadUp || Gdx.input.isKeyPressed(Input.Keys.W)) 1f else 0f) - (if (dpadDown || Gdx.input.isKeyPressed(Input.Keys.S)) 1f else 0f)
        val len = hypot(mx.toDouble(), mz.toDouble()).toFloat()
        if (len > 1f) { mx /= len; mz /= len }

        val sprinting = mz > 0.82f && !player.sneaking
        val spd = if (player.flying) 10.5f else if (player.sneaking) 2.6f else if (sprinting) 7.4f else 5.2f
        val sinY = sin(player.yaw); val cosY = cos(player.yaw)
        val wishX = (-sinY * mz + cosY * mx) * spd
        val wishZ = (-cosY * mz - sinY * mx) * spd
        player.vx += (wishX - player.vx) * min(1f, dt * 11f)
        player.vz += (wishZ - player.vz) * min(1f, dt * 11f)

        val feetB = world.getBlock(floor(player.x).toInt(), floor(player.y + 0.4f).toInt(), floor(player.z).toInt())
        val inWater = feetB == 9
        val inLava = feetB == 29
        val inPortal = feetB == 34

        if (inLava) damagePlayer(3f * dt * 2f)
        if (inPortal) switchDimension(if (world.dimension == "overworld") "nether" else "overworld")

        val jumping = jumpHeld || GamepadBridge.jumpPressed || Gdx.input.isKeyPressed(Input.Keys.SPACE)
        val dimGrav = if (world.dimension == "aether") 11.2f else gravity

        if (player.flying) {
            player.vy = if (jumping) 7.0f else if (player.sneaking) -7.0f else 0f
        } else if (inWater || inLava) {
            player.vy = if (jumping) 4.4f else max(-2.2f, player.vy - 6f * dt)
        } else {
            player.vy -= dimGrav * dt
            // Планер Элитры при удержании прыжка в воздухе!
            if (jumping && !player.onGround && player.vy < -1.5f) {
                player.vy = max(-2.2f, player.vy)
                player.vx += (-sinY * 9.5f - player.vx) * min(1f, dt * 3f)
                player.vz += (-cosY * 9.5f - player.vz) * min(1f, dt * 3f)
            }
            if (jumping && player.onGround) { player.vy = 7.7f; player.onGround = false }
        }

        // Автопрыжок MCPE на 1 блок при движении вперёд
        if (player.onGround && !player.flying && !player.sneaking && mz > 0.35f) {
            val cx = floor(player.x - sinY * 0.6f).toInt(); val cz = floor(player.z - cosY * 0.6f).toInt()
            if (world.isSolid(cx, floor(player.y + 0.15f).toInt(), cz) && !world.isSolid(cx, floor(player.y + 1.15f).toInt(), cz)) {
                player.vy = 7.7f; player.onGround = false
            }
        }

        if (!collides(player.x + player.vx * dt, player.y, player.z)) player.x += player.vx * dt
        if (!collides(player.x, player.y, player.z + player.vz * dt)) player.z += player.vz * dt

        val prevVy = player.vy
        val ny = player.y + player.vy * dt
        if (!collides(player.x, ny, player.z)) {
            player.y = ny; player.onGround = false
        } else {
            if (prevVy < -11.5f && !player.flying && !inWater) damagePlayer((-prevVy - 10.5f) * 1.3f)
            if (player.vy < 0f) player.onGround = true
            player.vy = 0f
        }
        if (player.y < -12f) {
            if (world.dimension == "aether") switchDimension("overworld")
            else { player.y = world.getTerrainHeight(player.x.toInt(), player.z.toInt()) + 4f; player.vy = 0f }
        }

        if (player.dashCooldown > 0f) player.dashCooldown = max(0f, player.dashCooldown - dt)
        if (hypot(player.vx.toDouble(), player.vz.toDouble()) > 0.3 && player.onGround) player.walkBob += dt * 10f
        if (player.armSwing > 0f) player.armSwing = max(0f, player.armSwing - dt * 5f)
    }

    override fun render() {
        val dt = min(0.05f, Gdx.graphics.deltaTime)
        elapsed += dt
        if (toastTimer > 0f) toastTimer = max(0f, toastTimer - dt)

        updateOstMusic()

        if (screen == UiScreen.PLAYING) {
            weatherSky.update(dt, world.dimension, player)
            updatePlayerPhysics(dt)
            world.updateAround(player.x, player.z, renderDist)
            physicsTimer += dt
            if (physicsTimer >= 0.2f) { physicsTimer = 0f; world.tickPhysics() }
            updateFurnaces(dt)
            updateMining(dt)

            if (lookPtr != -1 && !lookMoved && !holdingMine && (elapsed - lookDownTime) > 0.22f) {
                holdingMine = true
            }

            entities.update(
                dt, world, player,
                isNight = weatherSky.dayFactor < 0.22f,
                isNetClient = netMp.isConnected && !netMp.isHost,
                onHurtPlayer = ::damagePlayer,
                onExplode = ::explodeAt,
                onMobKill = ::handleMobKill,
                onPickup = { id, cnt -> player.addItem(id, cnt) },
                onGrapplePull = { gx, gy, gz ->
                    player.vx = (gx - player.x) * 2.8f
                    player.vy = (gy - player.y) * 2.4f + 4.5f
                    player.vz = (gz - player.z) * 2.8f
                    showToast("Крюк-кошка!")
                }
            )

            if (!player.creative) {
                hungerTimer += dt
                if (hungerTimer > 24f) {
                    hungerTimer = 0f
                    if (player.food > 0f) player.food = max(0f, player.food - 1f) else damagePlayer(1f)
                    if (player.food >= 16f && player.hp < 20f) player.hp = min(20f, player.hp + 1f)
                }
            }

            if (netMp.isConnected) {
                mpPosTimer += dt
                if (mpPosTimer >= 0.08f) {
                    mpPosTimer = 0f
                    netMp.sendPos(player.x, player.y, player.z, player.yaw, player.skinId)
                }
            }
        }

        // --- OPENGL ES 3D RENDER ---
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glClearColor(weatherSky.skyColor.r, weatherSky.skyColor.g, weatherSky.skyColor.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        if (screen != UiScreen.MAIN_MENU) {
            render3DScene()
        }

        // --- ANIME 2D HUD & MENUS ---
        render2DUI()
    }

    private fun render3DScene() {
        val eyeH = if (player.sneaking) 1.32f else 1.56f
        val bob = sin(player.walkBob) * 0.045f
        val dx = -sin(player.yaw) * cos(player.pitch)
        val dy = sin(player.pitch)
        val dz = -cos(player.yaw) * cos(player.pitch)

        if (player.thirdPerson) {
            cam3D.position.set(player.x - dx * 3.8f, player.y + eyeH + 0.6f - dy * 2.2f, player.z - dz * 3.8f)
            cam3D.lookAt(player.x, player.y + eyeH, player.z)
        } else {
            cam3D.position.set(player.x, player.y + eyeH + bob, player.z)
            cam3D.direction.set(dx, dy, dz).nor()
        }
        cam3D.up.set(0f, 1f, 0f)
        cam3D.update()

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glCullFace(GL20.GL_BACK)

        // Динамический цветной свет от главной или второй руки!
        val mainId = player.slots[player.selectedSlot]?.id ?: 0
        val offId = player.offhand?.id ?: 0
        val lightId = if ((BlockRegistry.items[mainId]?.lightLevel ?: 0) > 0 || mainId == 120) mainId
        else if ((BlockRegistry.items[offId]?.lightLevel ?: 0) > 0 || offId == 120) offId else 0
        val handInt = if (lightId > 0) 0.95f + sin(elapsed * 12f) * 0.08f else 0f

        BlockRegistry.atlasTexture.bind(0)
        terrainShader.bind()
        terrainShader.setUniformMatrix("u_projTrans", cam3D.combined)
        terrainShader.setUniformi("u_texture", 0)
        terrainShader.setUniformf("u_time", elapsed)
        terrainShader.setUniformf("u_camPos", cam3D.position)
        terrainShader.setUniformf("u_dayFactor", weatherSky.dayFactor)
        terrainShader.setUniformf("u_fogColor", weatherSky.skyColor.r, weatherSky.skyColor.g, weatherSky.skyColor.b)
        terrainShader.setUniformf("u_fogStart", renderDist * 11f)
        terrainShader.setUniformf("u_fogEnd", renderDist * 16.5f)
        terrainShader.setUniformf("u_seasonTint", weatherSky.season.rTint, weatherSky.season.gTint, weatherSky.season.bTint)
        terrainShader.setUniformf("u_handLightPos", player.x, player.y + 1.4f, player.z)
        terrainShader.setUniformf("u_handLightColor", 1.0f, 0.78f, 0.42f)
        terrainShader.setUniformf("u_handLightIntensity", handInt)

        // 1. Непрозрачные блоки
        Gdx.gl.glDisable(GL20.GL_BLEND)
        terrainShader.setUniformf("u_alpha", 1.0f)
        world.render(terrainShader, transparentPass = false)

        // 2. 3D-пропсы (Сундуки, Кровати, Факелы), Мобы, Аватары, Солнце/Луна/Облака
        modelBatch.begin(cam3D)
        weatherSky.render(modelBatch, env3D, world.dimension, player)
        entities.renderAll(modelBatch, env3D, world, player, netMp.remotePlayers.values, elapsed)
        modelBatch.end()

        // 3. Полупрозрачная живая Вода, Лава и Портал
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        terrainShader.bind()
        terrainShader.setUniformf("u_alpha", 0.78f)
        world.render(terrainShader, transparentPass = true)
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    // --- АНИМЕ-ИНТЕРФЕЙС (ЗОЛОТО + ИНДИГО) НА OPENGL ---
    private fun drawPanel(x: Float, y: Float, w: Float, h: Float, bg: Color = Color(0.11f, 0.07f, 0.18f, 0.93f), border: Color = Color(1f, 0.82f, 0.30f, 1f)) {
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapeRenderer.projectionMatrix = camUI.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = bg
        shapeRenderer.rect(x, y, w, h)
        shapeRenderer.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = border
        shapeRenderer.rect(x, y, w, h)
        shapeRenderer.end()
    }

    private fun drawButton(x: Float, y: Float, w: Float, h: Float, label: String, green: Boolean = false, onClick: () -> Unit) {
        val bg = if (green) Color(0.10f, 0.56f, 0.34f, 0.94f) else Color(0.42f, 0.14f, 0.30f, 0.92f)
        val br = if (green) Color(0.48f, 0.93f, 0.62f, 1f) else Color(1f, 0.82f, 0.30f, 1f)
        drawPanel(x, y, w, h, bg, br)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        fontSmall.draw(spriteBatch, label, x + 10f, y + h * 0.66f)
        spriteBatch.end()
        buttons += Pair(Rectangle(x, y, w, h), onClick)
    }

    private fun render2DUI() {
        buttons.clear()
        val W = Gdx.graphics.width.toFloat()
        val H = Gdx.graphics.height.toFloat()
        camUI.setToOrtho(false, W, H)
        camUI.update()

        when (screen) {
            UiScreen.MAIN_MENU -> renderMainMenu(W, H)
            UiScreen.PLAYING -> renderGameplayHud(W, H)
            UiScreen.PAUSE -> { renderGameplayHud(W, H); renderPauseMenu(W, H) }
            UiScreen.INVENTORY -> renderInventoryScreen(W, H)
            UiScreen.WORKBENCH -> renderWorkbenchScreen(W, H)
            UiScreen.FURNACE -> renderFurnaceScreen(W, H)
            UiScreen.CHEST -> renderChestScreen(W, H)
            UiScreen.SETTINGS_MODHUB -> renderSettingsAndModHub(W, H)
            UiScreen.WARDROBE -> renderWardrobeScreen(W, H)
            UiScreen.MULTIPLAYER -> renderMultiplayerScreen(W, H)
            UiScreen.DEATH -> {
                drawPanel(W * 0.3f, H * 0.35f, W * 0.4f, H * 0.3f, Color(0.32f, 0.05f, 0.08f, 0.94f), Color.RED)
                spriteBatch.projectionMatrix = camUI.combined
                spriteBatch.begin()
                font.draw(spriteBatch, "ВЫ ПОГИБЛИ!", W * 0.40f, H * 0.56f)
                spriteBatch.end()
                drawButton(W * 0.38f, H * 0.40f, W * 0.24f, 54f, "Возродиться", green = true) {
                    player.hp = 20f; player.food = 20f
                    if (world.dimension != player.spawnDim) switchDimension(player.spawnDim)
                    player.x = player.spawnX; player.y = player.spawnY; player.z = player.spawnZ
                    screen = UiScreen.PLAYING
                }
            }
        }

        if (toastTimer > 0f && toastText.isNotEmpty()) {
            val tw = min(W * 0.7f, toastText.length * 13f + 36f)
            drawPanel((W - tw) / 2f, H - 96f, tw, 36f, Color(0.08f, 0.05f, 0.14f, 0.88f), Color.GOLD)
            spriteBatch.projectionMatrix = camUI.combined
            spriteBatch.begin()
            fontSmall.draw(spriteBatch, toastText, (W - tw) / 2f + 14f, H - 72f)
            spriteBatch.end()
        }
    }

    private fun renderMainMenu(W: Float, H: Float) {
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        coverTex?.let { spriteBatch.draw(it, 0f, 0f, W, H) }
        spriteBatch.end()

        drawPanel(W * 0.28f, H * 0.10f, W * 0.44f, H * 0.78f)
        spriteBatch.begin()
        font.draw(spriteBatch, "VOXELHORIZON NATIVE v4.0", W * 0.31f, H * 0.82f)
        fontSmall.draw(spriteBatch, "Kotlin + OpenGL ES + LuaJ • Скин: ${SKINS_LIST.firstOrNull { it.id == player.skinId }?.name}", W * 0.31f, H * 0.75f)
        spriteBatch.end()

        val bx = W * 0.32f; val bw = W * 0.36f; val bh = H * 0.088f; val gap = bh + 10f
        var by = H * 0.60f
        drawButton(bx, by, bw, bh, "▶ Выживание (Загрузить / Новый)", green = true) { startNewOrLoadedWorld(true, false) }
        by -= gap
        drawButton(bx, by, bw, bh, "✦ Творчество (Новый мир)", green = true) { startNewOrLoadedWorld(false, true) }
        by -= gap
        drawButton(bx, by, bw, bh, "👤 Гардероб 3D-Скинов") { screen = UiScreen.WARDROBE }
        by -= gap
        drawButton(bx, by, bw, bh, "🌐 Мультиплеер (LAN / IP)") { screen = UiScreen.MULTIPLAYER }
        by -= gap
        drawButton(bx, by, bw, bh, "⚙ Настройки и Хаб Модов (Lua)") { settingsTab = 1; screen = UiScreen.SETTINGS_MODHUB }
    }

    private fun renderGameplayHud(W: Float, H: Float) {
        // 1. Верхняя панель координат + время/сезон/погода
        val dimRu = when (world.dimension) { "nether" -> "АД"; "aether" -> "ЭФИР"; else -> "Мир" }
        drawPanel(10f, H - 44f, 410f, 34f)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        fontSmall.draw(spriteBatch, "X:${player.x.toInt()} Y:${player.y.toInt()} Z:${player.z.toInt()} | $dimRu | ${weatherSky.season.ruName} • ${weatherSky.weather.ruName}", 18f, H - 21f)
        // Чат слева
        var cy = H - 62f
        for (line in chatLog) { fontSmall.draw(spriteBatch, line, 14f, cy); cy -= 22f }
        spriteBatch.end()

        // 2. Полоса Босса
        entities.getActiveBoss()?.let { boss ->
            val bw = W * 0.36f; val bx = (W - bw) / 2f
            drawPanel(bx, H - 52f, bw, 32f, Color(0.18f, 0.03f, 0.22f, 0.9f), Color.MAGENTA)
            shapeRenderer.projectionMatrix = camUI.combined
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.color = Color(0.85f, 0.20f, 0.95f, 1f)
            shapeRenderer.rect(bx + 4f, H - 48f, (bw - 8f) * (boss.hp / boss.maxHp).coerceIn(0f, 1f), 10f)
            shapeRenderer.end()
            spriteBatch.begin()
            fontSmall.draw(spriteBatch, "${boss.name} (${boss.hp.toInt()}/${boss.maxHp.toInt()})", bx + 8f, H - 26f)
            spriteBatch.end()
        }

        // 3. Верхние правые кнопки: Камера, Сезон, Погода, Питомец, Пауза
        val tb = 42f
        drawButton(W - 52f, H - 48f, tb, tb, "❚❚") { screen = UiScreen.PAUSE }
        drawButton(W - 102f, H - 48f, tb, tb, "👁") { player.thirdPerson = !player.thirdPerson; showToast(if (player.thirdPerson) "3-е лицо" else "1-е лицо") }
        drawButton(W - 152f, H - 48f, tb, tb, "🦊") { val on = entities.toggleFoxPet(player); showToast(if (on) "Лисёнок призван!" else "Лисёнок отдыхает") }
        drawButton(W - 202f, H - 48f, tb, tb, "🌦") { showToast("Погода: ${weatherSky.nextWeather().ruName}") }
        drawButton(W - 252f, H - 48f, tb, tb, "🍂") { showToast("Сезон: ${weatherSky.nextSeason().ruName}") }

        // 4. Прицел и полоска копания
        shapeRenderer.projectionMatrix = camUI.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(1f, 1f, 1f, 0.85f)
        shapeRenderer.rect(W / 2f - 10f, H / 2f - 1.5f, 20f, 3f)
        shapeRenderer.rect(W / 2f - 1.5f, H / 2f - 10f, 3f, 20f)
        if (mineProgress > 0.02f) {
            shapeRenderer.color = Color(0.1f, 0.1f, 0.1f, 0.8f)
            shapeRenderer.rect(W / 2f - 40f, H / 2f - 32f, 80f, 9f)
            shapeRenderer.color = Color(0.25f, 0.92f, 0.48f, 1f)
            shapeRenderer.rect(W / 2f - 39f, H / 2f - 31f, 78f * mineProgress.coerceIn(0f, 1f), 7f)
        }
        // Плавающий стик слева
        if (movePtr != -1 && useTwoFinger) {
            shapeRenderer.color = Color(1f, 1f, 1f, 0.22f)
            shapeRenderer.circle(moveOx, moveOy, 54f)
            shapeRenderer.color = Color(0.30f, 0.92f, 0.55f, 0.78f)
            shapeRenderer.circle(moveOx + stickDx * 42f, moveOy + stickDy * 42f, 22f)
        }
        shapeRenderer.end()

        // 5. Классическая Крестовина (если выбрана в Настройках вместо 2 пальцев)
        if (!useTwoFinger) {
            drawButton(72f, 132f, 52f, 52f, "▲") { dpadUp = !dpadUp }
            drawButton(16f, 76f, 52f, 52f, "◀") { dpadLeft = !dpadLeft }
            drawButton(128f, 76f, 52f, 52f, "▶") { dpadRight = !dpadRight }
            drawButton(72f, 20f, 52f, 52f, "▼") { dpadDown = !dpadDown }
        }

        // 6. Правые кнопки: Прыжок, Присед, Рывок, Вторая рука, Выбросить
        drawButton(W - 82f, 24f, 66f, 66f, "▲", green = true) {
            val now = elapsed
            if (player.creative && now - lastJumpTap < 0.35f) {
                player.flying = !player.flying; player.vy = 0f; showToast(if (player.flying) "Полёт ВКЛ" else "Полёт ВЫКЛ")
            } else if (player.onGround || player.flying) {
                player.vy = 7.7f; player.onGround = false
            }
            lastJumpTap = now
        }
        drawButton(W - 76f, 100f, 54f, 48f, "▼") { player.sneaking = !player.sneaking; showToast(if (player.sneaking) "Присед ВКЛ" else "Присед ВЫКЛ") }
        drawButton(W - 76f, 156f, 54f, 48f, "⚡") { performDash() }
        drawButton(W - 140f, 100f, 54f, 48f, "⇄") { player.swapWithOffhand(); showToast("Вторая рука: ${BlockRegistry.items[player.offhand?.id ?: 0]?.name ?: "Пусто"}") }
        drawButton(W - 140f, 156f, 54f, 48f, "⬇") { dropHeldItem() }

        // 7. ХП, Броня, Голод и 9-слотовый Хотбар + Off-hand + Кнопка «•••»
        val slotSz = min(56f, W / 14f)
        val hbW = slotSz * 10f + 18f
        val hbX = (W - hbW) / 2f
        val hbY = 12f

        if (!player.creative) {
            drawPanel(hbX, hbY + slotSz + 6f, hbW, 24f, Color(0.08f, 0.05f, 0.14f, 0.80f), Color(1f, 0.82f, 0.3f, 0.5f))
            spriteBatch.begin()
            fontSmall.draw(spriteBatch, "HP: ${player.hp.toInt()}/20   Броня: ${player.getTotalArmor()}   Еда: ${player.food.toInt()}/20   Лев.рука: ${BlockRegistry.items[player.offhand?.id ?: 0]?.name ?: "-"}", hbX + 10f, hbY + slotSz + 24f)
            spriteBatch.end()
        }

        drawPanel(hbX, hbY, hbW, slotSz + 8f)
        for (i in 0 until 9) {
            val sx = hbX + 6f + i * slotSz
            val sy = hbY + 4f
            val sel = i == player.selectedSlot
            val s = player.slots[i]
            val col = if (s != null) Color(((BlockRegistry.items[s.id]?.colorHex ?: 0xFFFFFF) shl 8) or 0xFF) else Color(0.18f, 0.12f, 0.28f, 0.9f)
            drawPanel(sx, sy, slotSz - 4f, slotSz - 4f, if (s != null) col else Color(0.16f, 0.10f, 0.24f, 0.9f), if (sel) Color.GREEN else Color.GOLD)
            if (s != null) {
                spriteBatch.begin()
                fontSmall.draw(spriteBatch, "${s.count}", sx + 4f, sy + 18f)
                spriteBatch.end()
            }
            buttons += Pair(Rectangle(sx, sy, slotSz - 4f, slotSz - 4f)) {
                player.selectedSlot = i
                s?.let { showToast(BlockRegistry.items[it.id]?.name ?: "") }
            }
        }
        drawButton(hbX + 6f + 9 * slotSz, hbY + 4f, slotSz, slotSz - 4f, "•••") { screen = UiScreen.INVENTORY }
    }

    private fun renderPauseMenu(W: Float, H: Float) {
        drawPanel(W * 0.30f, H * 0.10f, W * 0.40f, H * 0.80f)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        font.draw(spriteBatch, "ПАУЗА", W * 0.46f, H * 0.85f)
        spriteBatch.end()

        val bx = W * 0.34f; val bw = W * 0.32f; val bh = H * 0.082f; val gap = bh + 8f
        var by = H * 0.69f
        drawButton(bx, by, bw, bh, "▶ Продолжить", green = true) { screen = UiScreen.PLAYING }
        by -= gap
        drawButton(bx, by, bw, bh, "💾 Сохранить мир (.vhworld)") {
            world.saveToFile(File(File(storageRoot, "worlds"), worldFileName), player)
            showToast("Мир сохранён (GZIP)!")
        }
        by -= gap
        drawButton(bx, by, bw, bh, "🌀 Измерение: Мир / Ад / Эфир") {
            val next = when (world.dimension) { "overworld" -> "nether"; "nether" -> "aether"; else -> "overworld" }
            switchDimension(next); screen = UiScreen.PLAYING
        }
        by -= gap
        drawButton(bx, by, bw, bh, "🔄 Режим: ${if (player.creative) "Творчество" else "Выживание"}") {
            player.creative = !player.creative; player.flying = false
            showToast(if (player.creative) "Творчество" else "Выживание")
        }
        by -= gap
        drawButton(bx, by, bw, bh, "⚙ Настройки и Хаб Модов (Lua)") { settingsTab = 1; screen = UiScreen.SETTINGS_MODHUB }
        by -= gap
        drawButton(bx, by, bw, bh, "🌐 Мультиплеер (LAN / IP)") { screen = UiScreen.MULTIPLAYER }
        by -= gap
        drawButton(bx, by, bw, bh, "🚪 Сохранить и в меню") {
            world.saveToFile(File(File(storageRoot, "worlds"), worldFileName), player)
            screen = UiScreen.MAIN_MENU
        }
    }

    private fun renderInventoryScreen(W: Float, H: Float) {
        drawPanel(W * 0.08f, H * 0.08f, W * 0.84f, H * 0.84f)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        font.draw(spriteBatch, "ИНВЕНТАРЬ • БРОНЯ • КАРМАННЫЙ КРАФТ (2x2)", W * 0.10f, H * 0.88f)
        spriteBatch.end()

        drawButton(W * 0.80f, H * 0.83f, 90f, 38f, "Закрыть") { screen = UiScreen.PLAYING }
        drawButton(W * 0.65f, H * 0.83f, 150f, 38f, "⇅ Сортировать", green = true) { player.autoSortBackpack(); showToast("Рюкзак отсортирован!") }

        // Слева: Карманный крафт 2x2
        var ry = H * 0.72f
        for (rec in BlockRegistry.recipes.filter { it.pocket2x2 }) {
            val can = player.creative || rec.req.all { (id, cnt) -> player.countItem(id) >= cnt }
            drawButton(W * 0.10f, ry, W * 0.34f, 38f, "${BlockRegistry.items[rec.outId]?.name} x${rec.outCount} (${rec.desc})", green = can) {
                if (can) {
                    rec.req.forEach { (id, cnt) -> player.takeItem(id, cnt) }
                    player.addItem(rec.outId, rec.outCount)
                    showToast("Скрафчено: ${BlockRegistry.items[rec.outId]?.name}")
                }
            }
            ry -= 44f
        }

        // Справа: Сетка 36 слотов (0..8 хотбар, 9..35 рюкзак)
        val cell = min(54f, W * 0.42f / 9f)
        val gx = W * 0.48f
        for (i in 0 until 36) {
            val col = i % 9; val row = i / 9
            val sx = gx + col * cell
            val sy = H * 0.68f - row * cell
            val s = player.slots[i]
            val cHex = s?.let { BlockRegistry.items[it.id]?.colorHex } ?: 0x281C3E
            drawPanel(sx, sy, cell - 4f, cell - 4f, Color((cHex shl 8) or 0xEE), if (invSelectedSlot == i) Color.GREEN else Color.GOLD)
            if (s != null) {
                spriteBatch.begin()
                fontSmall.draw(spriteBatch, "${s.count}", sx + 3f, sy + 18f)
                spriteBatch.end()
            }
            buttons += Pair(Rectangle(sx, sy, cell - 4f, cell - 4f)) {
                val cur = player.slots[i]
                val def = cur?.let { BlockRegistry.items[it.id] }
                if (def != null && def.armorSlot >= 0 && invSelectedSlot == -1) {
                    val prev = player.armor[def.armorSlot]
                    player.armor[def.armorSlot] = cur.id
                    player.slots[i] = if (prev > 0) SlotItem(prev, 1) else null
                    showToast("Надето: ${def.name}")
                } else if (invSelectedSlot == -1) {
                    invSelectedSlot = i
                } else {
                    val tmp = player.slots[invSelectedSlot]
                    player.slots[invSelectedSlot] = player.slots[i]
                    player.slots[i] = tmp
                    invSelectedSlot = -1
                }
            }
        }
    }

    private fun renderWorkbenchScreen(W: Float, H: Float) {
        drawPanel(W * 0.08f, H * 0.08f, W * 0.84f, H * 0.84f)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        font.draw(spriteBatch, "ВЕРСТАК — ПОЛНЫЙ КРАФТ (3x3)", W * 0.10f, H * 0.88f)
        spriteBatch.end()
        drawButton(W * 0.80f, H * 0.83f, 90f, 38f, "Закрыть") { screen = UiScreen.PLAYING }

        val cols = 2
        val bw = W * 0.39f
        var idx = 0
        for (rec in BlockRegistry.recipes.take(16)) {
            val c = idx % cols; val r = idx / cols
            val can = player.creative || rec.req.all { (id, cnt) -> player.countItem(id) >= cnt }
            drawButton(W * 0.10f + c * (bw + 12f), H * 0.74f - r * 44f, bw, 38f, "${BlockRegistry.items[rec.outId]?.name} x${rec.outCount} — ${rec.desc}", green = can) {
                if (can) {
                    rec.req.forEach { (id, cnt) -> player.takeItem(id, cnt) }
                    player.addItem(rec.outId, rec.outCount)
                    showToast("Создано: ${BlockRegistry.items[rec.outId]?.name}")
                }
            }
            idx++
        }
    }

    private fun renderFurnaceScreen(W: Float, H: Float) {
        val pos = openFurnacePos ?: return
        val f = world.getFurnace(pos.first, pos.second, pos.third)
        drawPanel(W * 0.14f, H * 0.14f, W * 0.72f, H * 0.72f)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        font.draw(spriteBatch, "ПЕЧЬ — ПЕРЕПЛАВКА С ТОПЛИВОМ", W * 0.17f, H * 0.81f)
        fontSmall.draw(spriteBatch, "Огонь: ${((f.burn / max(1f, f.maxBurn)) * 100).toInt()}%   Прогресс: ${f.cook.toInt()}%", W * 0.17f, H * 0.73f)
        spriteBatch.end()
        drawButton(W * 0.74f, H * 0.77f, 95f, 38f, "Закрыть") { openFurnacePos = null; screen = UiScreen.PLAYING }

        drawButton(W * 0.18f, H * 0.56f, W * 0.28f, 44f, "Сырьё: ${f.input?.let { "${BlockRegistry.items[it.id]?.name} x${it.count}" } ?: "Пусто"}") {
            f.input?.let { player.addItem(it.id, it.count); f.input = null }
        }
        drawButton(W * 0.18f, H * 0.45f, W * 0.28f, 44f, "Топливо: ${f.fuel?.let { "${BlockRegistry.items[it.id]?.name} x${it.count}" } ?: "Пусто"}") {
            f.fuel?.let { player.addItem(it.id, it.count); f.fuel = null }
        }
        drawButton(W * 0.52f, H * 0.50f, W * 0.28f, 52f, "Готово: ${f.output?.let { "${BlockRegistry.items[it.id]?.name} x${it.count}" } ?: "Пусто"}", green = f.output != null) {
            f.output?.let { player.addItem(it.id, it.count); f.output = null }
        }
        drawButton(W * 0.18f, H * 0.24f, W * 0.62f, 46f, "Загрузить руду/мясо и уголь из рюкзака в Печь", green = true) {
            for (i in 0 until 36) {
                val s = player.slots[i] ?: continue
                if (BlockRegistry.smeltMap.containsKey(s.id) && (f.input == null || f.input!!.id == s.id)) {
                    f.input = if (f.input == null) SlotItem(s.id, s.count) else SlotItem(s.id, f.input!!.count + s.count)
                    player.slots[i] = null
                } else if ((BlockRegistry.items[s.id]?.fuelPower ?: 0f) > 0f && (f.fuel == null || f.fuel!!.id == s.id)) {
                    f.fuel = if (f.fuel == null) SlotItem(s.id, s.count) else SlotItem(s.id, f.fuel!!.count + s.count)
                    player.slots[i] = null
                }
            }
        }
    }

    private fun renderChestScreen(W: Float, H: Float) {
        val pos = openChestPos ?: return
        val items = world.getChest(pos.first, pos.second, pos.third)
        drawPanel(W * 0.10f, H * 0.10f, W * 0.80f, H * 0.80f)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        font.draw(spriteBatch, "СУНДУК (27 СЛОТОВ)", W * 0.13f, H * 0.85f)
        spriteBatch.end()
        drawButton(W * 0.78f, H * 0.81f, 95f, 38f, "Закрыть") {
            openChestPos = null; entities.openChestKey = null; screen = UiScreen.PLAYING
        }

        val cell = min(56f, W * 0.74f / 9f)
        val gx = W * 0.13f
        for (i in 0 until 27) {
            val sx = gx + (i % 9) * cell; val sy = H * 0.68f - (i / 9) * cell
            val s = items[i]
            val cHex = s?.let { BlockRegistry.items[it.id]?.colorHex } ?: 0x2A1C3A
            drawPanel(sx, sy, cell - 4f, cell - 4f, Color((cHex shl 8) or 0xEE), Color.GOLD)
            if (s != null) { spriteBatch.begin(); fontSmall.draw(spriteBatch, "${s.count}", sx + 3f, sy + 18f); spriteBatch.end() }
            buttons += Pair(Rectangle(sx, sy, cell - 4f, cell - 4f)) {
                if (s != null && player.addItem(s.id, s.count)) items[i] = null
            }
        }
        drawButton(W * 0.13f, H * 0.16f, W * 0.74f, 44f, "Положить текущий предмет из руки в сундук", green = true) {
            val held = player.slots[player.selectedSlot]
            val empty = items.indexOfFirst { it == null }
            if (held != null && empty >= 0) { items[empty] = SlotItem(held.id, held.count); player.slots[player.selectedSlot] = null }
        }
    }

    private fun renderSettingsAndModHub(W: Float, H: Float) {
        drawPanel(W * 0.10f, H * 0.08f, W * 0.80f, H * 0.84f)
        drawButton(W * 0.13f, H * 0.81f, W * 0.30f, 42f, "🎮 Настройки и Управление", green = settingsTab == 0) { settingsTab = 0 }
        drawButton(W * 0.45f, H * 0.81f, W * 0.30f, 42f, "✨ Хаб Модов (LuaJ)", green = settingsTab == 1) { settingsTab = 1 }
        drawButton(W * 0.78f, H * 0.81f, 95f, 42f, "Готово") { screen = if (world.chunks.isNotEmpty()) UiScreen.PLAYING else UiScreen.MAIN_MENU }

        if (settingsTab == 0) {
            drawButton(W * 0.14f, H * 0.64f, W * 0.72f, 48f, "Дальность прорисовки: $renderDist чанков (Нажмите для смены)") {
                renderDist = if (renderDist >= 8) 3 else renderDist + 1
            }
            drawButton(W * 0.14f, H * 0.52f, W * 0.72f, 48f, "Профиль управления: ${if (useTwoFinger) "2 Пальца MCPE" else "Классическая Крестовина"}") {
                useTwoFinger = !useTwoFinger
            }
            drawButton(W * 0.14f, H * 0.40f, W * 0.72f, 48f, "Чувствительность камеры: ${"%.1f".format(sensitivity)}") {
                sensitivity = if (sensitivity >= 2.0f) 0.6f else sensitivity + 0.2f
            }
            drawButton(W * 0.14f, H * 0.28f, W * 0.72f, 48f, "Музыка и звуки: ${if (soundEnabled) "ВКЛ" else "ВЫКЛ"}", green = soundEnabled) {
                soundEnabled = !soundEnabled
            }
        } else {
            var my = H * 0.66f
            for (m in luaMods.mods.take(6)) {
                drawButton(W * 0.14f, my, W * 0.72f, 46f, "${if (m.enabled) "[ВКЛ]" else "[ВЫКЛ]"} ${m.name} — ${m.desc}", green = m.enabled) {
                    m.enabled = !m.enabled
                    luaMods.hotReload()
                    showToast("${if (m.enabled) "Включён" else "Отключён"}: ${m.name}")
                }
                my -= 52f
            }
            drawButton(W * 0.14f, H * 0.14f, W * 0.72f, 46f, "🔄 Горячая перезагрузка модов (Hot Reload из VoxelHorizon/mods/)", green = true) {
                luaMods.hotReload()
                showToast("Lua-моды перезагружены (${luaMods.mods.count { it.enabled }} актив.)!")
            }
        }
    }

    private fun renderWardrobeScreen(W: Float, H: Float) {
        drawPanel(W * 0.12f, H * 0.10f, W * 0.76f, H * 0.80f)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        font.draw(spriteBatch, "ГАРДЕРОБ 3D-СКИНОВ", W * 0.15f, H * 0.85f)
        spriteBatch.end()
        drawButton(W * 0.76f, H * 0.81f, 95f, 38f, "Назад") { screen = UiScreen.MAIN_MENU }

        var y = H * 0.68f
        for (sk in SKINS_LIST) {
            drawButton(W * 0.16f, y, W * 0.68f, 48f, "${sk.name} ${if (player.skinId == sk.id) "(Выбран ✓)" else ""}", green = player.skinId == sk.id) {
                player.skinId = sk.id
                showToast("Выбран 3D-скин: ${sk.name}")
            }
            y -= 56f
        }
    }

    private fun renderMultiplayerScreen(W: Float, H: Float) {
        drawPanel(W * 0.14f, H * 0.12f, W * 0.72f, H * 0.76f)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        font.draw(spriteBatch, "МУЛЬТИПЛЕЕР (TCP + LAN)", W * 0.18f, H * 0.82f)
        fontSmall.draw(spriteBatch, "Статус: ${netMp.statusText} | Игроков рядом: ${netMp.remotePlayers.size}", W * 0.18f, H * 0.74f)
        spriteBatch.end()
        drawButton(W * 0.74f, H * 0.79f, 95f, 38f, "Назад") { screen = if (world.chunks.isNotEmpty()) UiScreen.PLAYING else UiScreen.MAIN_MENU }

        drawButton(W * 0.18f, H * 0.56f, W * 0.64f, 52f, "🌐 Создать комнату (Хост, Порт 25566)", green = true) {
            if (world.chunks.isEmpty()) startNewOrLoadedWorld(true, false)
            netMp.startHost()
            showToast("Комната открыта! Друзья в Wi-Fi найдут её автоматически.")
        }
        drawButton(W * 0.18f, H * 0.44f, W * 0.64f, 52f, "🔍 Найти и войти в комнату по LAN (Wi-Fi)", green = true) {
            netMp.discoverAndJoinLan()
        }
        drawButton(W * 0.18f, H * 0.32f, W * 0.64f, 52f, "⌨ Подключиться по IP адресу") {
            Gdx.input.getTextInput(object : Input.TextInputListener {
                override fun input(text: String) { if (text.isNotBlank()) netMp.joinDirect(text) }
                override fun canceled() {}
            }, "Введите IP Хоста", "192.168.1.", "")
        }
        drawButton(W * 0.18f, H * 0.20f, W * 0.64f, 46f, "Отключиться от сети") { netMp.stop(); showToast("Сеть отключена") }
    }

    // --- НАТИВНЫЙ МУЛЬТИТАЧ (2 ПАЛЬЦА КАК В MCPE) ---
    private fun setupInputProcessor() {
        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                val ux = screenX.toFloat()
                val uy = (Gdx.graphics.height - screenY).toFloat()

                // 1. Проверяем нажатие по UI-кнопкам (с конца, чтобы верхние перекрывали нижние)
                for (i in buttons.size - 1 downTo 0) {
                    val (rect, action) = buttons[i]
                    if (rect.contains(ux, uy)) {
                        action()
                        return true
                    }
                }

                if (screen != UiScreen.PLAYING) return false

                // 2. Игровой мультитач: левая часть = плавающий стик, правая = обзор + тап/удержание
                val halfW = Gdx.graphics.width * 0.45f
                if (useTwoFinger && movePtr == -1 && ux < halfW) {
                    movePtr = pointer
                    moveOx = ux; moveOy = uy
                    stickDx = 0f; stickDy = 0f
                } else if (lookPtr == -1) {
                    lookPtr = pointer
                    lookSx = ux; lookSy = uy; lookLx = ux; lookLy = uy
                    lookDownTime = elapsed; lookMoved = false
                }
                return true
            }

            override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
                if (screen != UiScreen.PLAYING) return false
                val ux = screenX.toFloat()
                val uy = (Gdx.graphics.height - screenY).toFloat()

                if (pointer == movePtr) {
                    var dx = ux - moveOx; var dy = uy - moveOy
                    val maxR = 48f
                    val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    if (dist > maxR) { dx = dx / dist * maxR; dy = dy / dist * maxR }
                    stickDx = dx / maxR; stickDy = dy / maxR
                    return true
                } else if (pointer == lookPtr) {
                    val dx = ux - lookLx; val dy = uy - lookLy
                    lookLx = ux; lookLy = uy
                    if (hypot((ux - lookSx).toDouble(), (uy - lookSy).toDouble()) > 14.0) {
                        lookMoved = true; holdingMine = false
                    }
                    player.yaw -= dx * 0.0045f * sensitivity
                    player.pitch = (player.pitch + dy * 0.0045f * sensitivity).coerceIn(-1.46f, 1.46f)
                    return true
                }
                return false
            }

            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                if (pointer == movePtr) {
                    movePtr = -1; stickDx = 0f; stickDy = 0f
                } else if (pointer == lookPtr) {
                    if (!lookMoved && !holdingMine && (elapsed - lookDownTime) < 0.22f && screen == UiScreen.PLAYING) {
                        performTapAction()
                    }
                    lookPtr = -1; holdingMine = false; mineProgress = 0f
                }
                return true
            }
        }
    }

    override fun resize(width: Int, height: Int) {
        cam3D.viewportWidth = width.toFloat()
        cam3D.viewportHeight = height.toFloat()
        cam3D.update()
        camUI.setToOrtho(false, width.toFloat(), height.toFloat())
        camUI.update()
    }

    override fun pause() {
        if (screen == UiScreen.PLAYING) {
            world.saveToFile(File(File(storageRoot, "worlds"), worldFileName), player)
        }
    }

    override fun dispose() {
        netMp.stop()
        bgMusic?.dispose()
        world.dispose()
        entities.dispose()
        weatherSky.dispose()
        BlockRegistry.dispose()
        terrainShader.dispose()
        modelBatch.dispose()
        spriteBatch.dispose()
        shapeRenderer.dispose()
        font.dispose()
        fontSmall.dispose()
        coverTex?.dispose()
    }
}
