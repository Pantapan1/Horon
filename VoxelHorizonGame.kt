package com.voxelhorizon.nativegame

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector3
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

enum class UiScreen {
    MAIN_MENU, WORLD_SLOTS, PROFILES_FRIENDS, PLAYING, PAUSE, INVENTORY, WORKBENCH, FURNACE, CHEST, CAMPFIRE, WAYSTONES, ANVIL, TRADER_QUESTS, BESTIARY_SKILLS, FULL_MAP, SETTINGS_MODHUB, WARDROBE, MULTIPLAYER, DEATH
}

data class IsoPick(val gx: Int, val gy: Int, val gz: Int, val id: Int)

class VoxelHorizonGame(private val storageRoot: File) : ApplicationAdapter() {

    lateinit var world: WorldIso
    lateinit var entities: EntityAndWeatherManager
    lateinit var luaMods: LuaModEngine
    lateinit var netMp: NetMultiplayer
    lateinit var profiles: ProfileManager
    val player = PlayerState()

    private lateinit var camWorld: OrthographicCamera
    private lateinit var camUI: OrthographicCamera
    private lateinit var spriteBatch: SpriteBatch
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var font: BitmapFont
    private lateinit var fontSmall: BitmapFont
    private val glyphLayout = GlyphLayout()
    private val tmpVec3 = Vector3()
    private val quadVerts = FloatArray(20)
    private var coverTex: Texture? = null

    // Zero-allocation shared native buffers between Rust (libvoxelhorizon_core.so) and OpenGL ES!
    private val MAX_TERRAIN_FLOATS = 200000
    private val rustVertsBuf = ByteBuffer.allocateDirect(MAX_TERRAIN_FLOATS * 4).order(ByteOrder.nativeOrder())
    private val rustVertsFloatView = rustVertsBuf.asFloatBuffer()
    private val terrainVertsArr = FloatArray(MAX_TERRAIN_FLOATS)

    private val rustDiagBuf = ByteBuffer.allocateDirect(64 * 2 * 4).order(ByteOrder.nativeOrder())
    private val rustDiagIntView = rustDiagBuf.asIntBuffer()
    private val diagArr = IntArray(64 * 2)

    private val rustPropsBuf = ByteBuffer.allocateDirect(1024 * 8 * 4).order(ByteOrder.nativeOrder())
    private val rustPropsFloatView = rustPropsBuf.asFloatBuffer()
    private val propsArr = FloatArray(1024 * 8)

    private val rustLightsBuf = ByteBuffer.allocateDirect(512 * 4 * 4).order(ByteOrder.nativeOrder())
    private val rustLightsFloatView = rustLightsBuf.asFloatBuffer()
    private val lightsArr = FloatArray(512 * 4)

    private val rustOresBuf = ByteBuffer.allocateDirect(512 * 3 * 4).order(ByteOrder.nativeOrder())
    private val rustOresFloatView = rustOresBuf.asFloatBuffer()
    private val oresArr = FloatArray(512 * 3)

    private val rustCountsBuf = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder())
    private val rustCountsIntView = rustCountsBuf.asIntBuffer()

    private val rustPickBuf = ByteBuffer.allocateDirect(4 * 4).order(ByteOrder.nativeOrder())
    private val rustPickIntView = rustPickBuf.asIntBuffer()

    // Reusable diagonal buckets for entities (ZERO per-frame HashMap/ArrayList allocations!)
    private val mobsByDiag = Array(64) { ArrayList<MobEntity>(8) }
    private val dropsByDiag = Array(64) { ArrayList<DroppedItem>(8) }
    private val remoteByDiag = Array(64) { ArrayList<RemotePlayer>(4) }

    private var bgMusic: Music? = null
    private var currentOst = ""

    var screen = UiScreen.MAIN_MENU
    var soundEnabled = true
    var autoAttackEnabled = true
    private var autoAttackCd = 0f
    var baseWalkSpeed = 5.0f
    var cameraZoom = 0.85f
    var worldFileName = "world_iso_v62.vhworld"
    var currentWorldTitle = "Мой мир"

    // Minimap (32x32 native Rust radar, updated every 0.45s -> 0 FPS impact!)
    private val MINIMAP_SZ = 32
    private val rustMinimapBuf = ByteBuffer.allocateDirect(32 * 32 * 4).order(ByteOrder.nativeOrder())
    private val rustMinimapIntView = rustMinimapBuf.asIntBuffer()
    private val minimapColors = IntArray(32 * 32)
    private lateinit var minimapPixmap: Pixmap
    private lateinit var minimapTex: Texture
    private var minimapTimer = 0f
    private var minimapRect = Rectangle()
    private var customMarkerX: Int? = null
    private var customMarkerY: Int? = null
    private var lastFogGx = Int.MIN_VALUE
    private var lastFogGy = Int.MIN_VALUE

    // Fishing Mini-Game state
    private var fishingActive = false
    private var fishingBarPos = 0.5f
    private var fishingBarDir = 1f
    private var fishingBarRect = Rectangle()

    private var isCutawayActive = false
    private var manualLayerOffset = 0
    private var activeSliceZ = 145

    private var elapsed = 0f
    private var hungerTimer = 0f
    private var freezeDmgTimer = 0f
    private var mpPosTimer = 0f
    private var mpTimeSyncTimer = 0f
    private var mpMobSyncTimer = 0f
    private var mpDropSyncTimer = 0f
    private val chatLog = ArrayDeque<Pair<String, Float>>()
    private var shakeTimer = 0f
    private var toastText = ""
    private var toastTimer = 0f

    // Pure Stick + Tap / Hold / Pinch Input State
    private var joyPtr = -1
    private var joyBaseX = 110f; private var joyBaseY = 120f
    private var joyCenterX = 110f; private var joyCenterY = 120f; private var joyRadius = 74f
    private var stickDx = 0f; private var stickDy = 0f

    private var worldTouchPtr = -1
    private var worldTouchDownTime = 0f
    private var worldTouchStartX = 0f; private var worldTouchStartY = 0f
    private var worldTouchHeldActed = false
    private var pinchPtr2 = -1
    private var pinchStartDist = 0f
    private var pinchStartZoom = 0.85f
    private val ptrPos = HashMap<Int, Pair<Float, Float>>()

    private var hotbarSwipePtr = -1
    private var hotbarSwipeSlot = -1
    private var hotbarSwipeStartY = 0f
    private val hotbarSlotRects = Array(9) { Rectangle() }
    private var offhandSlotRect = Rectangle()

    private var targetPick: IsoPick? = null
    private var holdingMine = false
    private var mineProgress = 0f
    private var mineX = 0; private var mineY = 0; private var mineZ = 0

    private var openFurnacePos: Triple<Int, Int, Int>? = null
    private var openChestPos: Triple<Int, Int, Int>? = null
    private var invSelectedSlot = -1
    private var settingsTab = 0

    private val weatherDrops = Array(64) { floatArrayOf(Random.nextFloat(), Random.nextFloat(), 0.7f + Random.nextFloat() * 0.6f) }
    private val buttons = mutableListOf<Pair<Rectangle, () -> Unit>>()

    override fun create() {
        BlockRegistry.init()
        SpriteArt.init()
        SoundFx.init()
        minimapPixmap = Pixmap(MINIMAP_SZ, MINIMAP_SZ, Pixmap.Format.RGBA8888)
        minimapTex = Texture(minimapPixmap).apply { setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest) }
        spriteBatch = SpriteBatch(4096)
        shapeRenderer = ShapeRenderer()

        val w = Gdx.graphics.width.toFloat(); val h = Gdx.graphics.height.toFloat()
        camWorld = OrthographicCamera(w, h)
        camUI = OrthographicCamera(w, h)
        camUI.setToOrtho(false, w, h)

        loadFonts()
        val cov = Gdx.files.internal("cover_anime.jpg")
        if (cov.exists()) coverTex = Texture(cov)

        world = WorldIso(Random.nextInt(100000, 999999), "overworld")
        entities = EntityAndWeatherManager()
        profiles = ProfileManager(storageRoot)
        profiles.loadAll()
        player.skinId = profiles.active.skinId
        netMp = NetMultiplayer(this)
        netMp.localNick = profiles.active.nick
        netMp.startFriendsPresence(profiles)
        luaMods = LuaModEngine(File(storageRoot, "mods"), this)
        luaMods.hotReload()

        setupStarterInventory()
        setupInputProcessor()
    }

    private fun loadFonts() {
        val pixFnt = Gdx.files.internal("pixel_font.fnt")
        if (pixFnt.exists()) {
            font = BitmapFont(pixFnt).apply {
                region.texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
                setUseIntegerPositions(true)
                val sBig = (Gdx.graphics.height / 540f).coerceIn(1.1f, 2.2f)
                data.setScale(sBig)
            }
            fontSmall = BitmapFont(pixFnt).apply {
                region.texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
                setUseIntegerPositions(true)
                val sSm = (Gdx.graphics.height / 820f).coerceIn(0.75f, 1.45f)
                data.setScale(sSm)
            }
            return
        }
        val cyrillic = FreeTypeFontGenerator.DEFAULT_CHARS +
            "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя•—«»№∞°"
        val fFile = Gdx.files.internal("font.ttf")
        if (fFile.exists()) {
            val gen = FreeTypeFontGenerator(fFile)
            val p1 = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                size = (Gdx.graphics.height / 28).coerceIn(22, 38)
                characters = cyrillic; color = Color.WHITE
                shadowOffsetX = 2; shadowOffsetY = 2; shadowColor = Color(0f, 0f, 0f, 0.85f)
            }
            val p2 = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                size = (Gdx.graphics.height / 44).coerceIn(15, 25)
                characters = cyrillic; color = Color.WHITE
                shadowOffsetX = 1; shadowOffsetY = 1; shadowColor = Color(0f, 0f, 0f, 0.85f)
            }
            font = gen.generateFont(p1)
            fontSmall = gen.generateFont(p2)
            gen.dispose()
        } else {
            font = BitmapFont(); fontSmall = BitmapFont()
        }
    }

    private fun setupStarterInventory() {
        player.slots.fill(null)
        player.slots[0] = SlotItem(109, 1)  // Алмазная кирка
        player.slots[1] = SlotItem(111, 1)  // Железный меч
        player.slots[2] = SlotItem(110, 1)  // Боевой топор
        player.slots[3] = SlotItem(56, 16)  // Приставные Лестницы
        player.slots[4] = SlotItem(7, 24)   // Доски
        player.slots[5] = SlotItem(37, 20)  // Факелы
        player.slots[6] = SlotItem(38, 1)   // Кровать (попробуй лечь спать!)
        player.slots[7] = SlotItem(61, 1)   // Камень Телепорта
        player.slots[8] = SlotItem(115, 6)  // Жареный стейк
        player.slots[9] = SlotItem(59, 1)   // Костёр
        player.slots[10] = SlotItem(60, 2)  // Фонарь
        player.slots[11] = SlotItem(57, 2)  // Дверь
        player.slots[12] = SlotItem(132, 1) // Щит (Парирование!)
        player.slots[13] = SlotItem(148, 1) // Тёплая меховая шуба
        player.slots[14] = SlotItem(136, 2) // Согревающий чай
        player.slots[15] = SlotItem(137, 2) // Отвар Шахтёра
        player.slots[16] = SlotItem(138, 2) // Эликсир Скорости
        player.slots[17] = SlotItem(35, 2)  // Сундук
        player.slots[18] = SlotItem(119, 1) // Ведро воды
        player.slots[19] = SlotItem(116, 1) // Огниво
        player.slots[20] = SlotItem(143, 6) // Пшеница (ферма/корм)
        player.slots[21] = SlotItem(103, 4) // Золото (обмен с Пиглином)
        player.slots[22] = SlotItem(142, 4) // Рубины (крафт/наковальня)
        player.slots[23] = SlotItem(150, 1) // Удочка (Рыбалка!)
        player.slots[24] = SlotItem(72, 2)  // Шахтные Лифты (2 шт)
        player.slots[25] = SlotItem(73, 2)  // Шипы-ловушки
        player.slots[26] = SlotItem(74, 1)  // Авто-арбалет
        player.offhand = SlotItem(37, 16)
        player.armor[0] = 125
    }

    private fun isoToScreenX(gx: Float, gy: Float): Float = (gx - gy) * WorldIso.TW
    private fun isoToScreenY(gx: Float, gy: Float, gz: Float): Float = -(gx + gy) * WorldIso.TH + gz * WorldIso.ZH

    fun startWorld(loadSaved: Boolean, creativeMode: Boolean, customSeed: Int? = null, titleIn: String? = null) {
        val f = File(File(storageRoot, "worlds"), worldFileName)
        if (loadSaved && world.loadFromFile(f, player, entities)) {
            showToast("Мир загружен! День ${entities.dayCount} (${entities.season.ruName})")
        } else {
            world.modifications.clear(); world.chests.clear(); world.furnaces.clear(); world.signTexts.clear(); RustEngine.nativeClearMods()
            val useSeed = customSeed ?: Random.nextInt(100000, 999999)
            world.resetDimension("overworld", useSeed)
            world.waystones.clear()
            player.creative = creativeMode
            player.x = 0.5f; player.y = 0.5f
            player.z = (world.getSurfaceZ(0, 0) + 1).toFloat()
            player.spawnX = player.x; player.spawnY = player.y; player.spawnZ = player.z; player.spawnDim = "overworld"
            player.hp = 20f; player.maxHp = 20f; player.food = 20f; player.bodyTemp = 72f; player.isSleeping = false
            entities.dayCount = 1; entities.worldTime = 0.26f
            setupStarterInventory()
            entities.mobs.clear(); entities.drops.clear(); entities.projectiles.clear()
            entities.spawnMob("sheep", 4.5f, 2.5f, (world.getSurfaceZ(4, 2) + 1).toFloat())
            entities.spawnMob("deer", -3.5f, 3.5f, (world.getSurfaceZ(-3, 3) + 1).toFloat())
            entities.spawnMob("wolf", -2.5f, -3.5f, (world.getSurfaceZ(-2, -3) + 1).toFloat())
            entities.spawnMob("trader", 3.5f, -2.5f, (world.getSurfaceZ(3, -2) + 1).toFloat())
            if (entities.foxPet == null) entities.toggleFoxPet(player)
            showToast("Стик — движение, Тап/Удержание — всё остальное!")
        }
        if (titleIn != null) currentWorldTitle = titleIn
        profiles.registerWorldSlot(WorldSlotInfo(worldFileName, currentWorldTitle, world.seed, player.creative, entities.seasonLengthDays, System.currentTimeMillis()))
        lastFogGx = Int.MIN_VALUE; lastFogGy = Int.MIN_VALUE; fishingActive = false
        manualLayerOffset = 0
        camWorld.position.set(isoToScreenX(player.x, player.y), isoToScreenY(player.x, player.y, player.z), 0f)
        screen = UiScreen.PLAYING
    }

    fun showToast(msg: String) { toastText = msg; toastTimer = 2.8f }

    fun setWorldBlock(x: Int, y: Int, z: Int, id: Int) {
        Gdx.app.postRunnable {
            world.setBlock(x, y, z, id)
            if (netMp.isConnected) netMp.broadcastBlock(x, y, z, id)
        }
    }

    fun applyRemoteBlock(x: Int, y: Int, z: Int, id: Int) {
        Gdx.app.postRunnable { world.setBlock(x, y, z, id) }
    }

    fun applyHostWorldInit(seed: Int, dim: String, dayCnt: Int, seasLen: Int, wTime: Float, hx: Float, hy: Float, hz: Float, mods: Map<String, Int>, signs: Map<String, String>) {
        Gdx.app.postRunnable {
            world.modifications.clear(); world.modifications.putAll(mods)
            world.signTexts.clear(); world.signTexts.putAll(signs)
            world.syncModsToRust()
            world.resetDimension(dim, seed)
            entities.dayCount = dayCnt; entities.seasonLengthDays = seasLen; entities.worldTime = wTime
            player.x = hx + 1.1f; player.y = hy; player.z = hz; player.isSleeping = false
            camWorld.position.set(isoToScreenX(player.x, player.y), isoToScreenY(player.x, player.y, player.z), 0f)
            screen = UiScreen.PLAYING
            showToast("Мир Хоста синхронизирован! (День $dayCnt)")
        }
    }

    fun applyRemoteMobs(list: List<MobSyncSnap>) {
        Gdx.app.postRunnable { entities.applySyncedMobs(list) }
    }

    fun applyGuestMobHit(mobId: Int, dmg: Float, kbX: Float, kbY: Float, frost: Boolean) {
        Gdx.app.postRunnable {
            val m = entities.mobs.firstOrNull { it.id == mobId } ?: return@postRunnable
            entities.damageMob(m, dmg, kbX, kbY, frostHit = frost, onKill = ::handleMobKill)
        }
    }

    fun applyRemoteChest(x: Int, y: Int, z: Int, arr: Array<SlotItem?>) {
        Gdx.app.postRunnable {
            val cur = world.getChest(x, y, z)
            for (i in 0 until 27) cur[i] = arr[i]
        }
    }

    fun applyRemoteDrops(list: List<DropSyncSnap>) {
        Gdx.app.postRunnable { entities.applySyncedDrops(list) }
    }

    fun applyGuestDropPickup(dropId: Int) {
        Gdx.app.postRunnable { entities.drops.removeIf { it.id == dropId } }
    }

    fun addChatMessage(line: String) {
        Gdx.app.postRunnable {
            chatLog.addLast(Pair(line, 12f))
            while (chatLog.size > 5) chatLog.removeFirst()
        }
    }

    fun applyRemotePvpHit(dmg: Float, fromNick: String) {
        Gdx.app.postRunnable {
            damagePlayer(dmg, null)
            showToast("PvP: $fromNick нанёс ${dmg.toInt()} урона!")
        }
    }

    fun applyRemoteSign(x: Int, y: Int, z: Int, txt: String) {
        Gdx.app.postRunnable { world.signTexts[world.blockKey(x, y, z)] = txt }
    }

    fun switchDimension(targetDim: String) {
        if (world.dimension == targetDim) return
        player.isSleeping = false
        world.resetDimension(targetDim, world.seed)
        entities.mobs.clear(); entities.drops.clear(); entities.projectiles.clear()
        player.x = 0.5f; player.y = 0.5f
        player.z = (world.getSurfaceZ(0, 0) + 1).toFloat()
        player.vx = 0f; player.vy = 0f; manualLayerOffset = 0
        camWorld.position.set(isoToScreenX(player.x, player.y), isoToScreenY(player.x, player.y, player.z), 0f)
        showToast(when (targetDim) {
            "nether" -> "Вы вошли в АД (Nether)!"
            "aether" -> "Небесные Острова (Эфир)!"
            else -> "Возвращение в Обычный мир!"
        })
    }

    private fun updateOstMusic() {
        if (!soundEnabled || screen == UiScreen.MAIN_MENU) { bgMusic?.stop(); currentOst = ""; return }
        val isDeep = world.dimension == "overworld" && player.z < WorldIso.SEA_Z - 5
        val target = when {
            entities.getActiveBoss() != null -> "ost_boss.wav"
            world.dimension == "nether" -> "ost_nether.wav"
            entities.dayFactor < 0.25f || isDeep -> "ost_overworld_night.wav"
            else -> "ost_overworld_day.wav"
        }
        if (target == currentOst && bgMusic?.isPlaying == true) return
        currentOst = target
        try {
            bgMusic?.stop(); bgMusic?.dispose()
            val f = Gdx.files.internal(target)
            if (f.exists()) bgMusic = Gdx.audio.newMusic(f).apply { isLooping = true; volume = 0.26f; play() }
        } catch (_: Exception) {}
    }

    private fun explodeAt(cx: Int, cy: Int, cz: Int, rad: Float) {
        shakeTimer = 0.35f
        val r = rad.toInt() + 1
        for (dx in -r..r) for (dy in -r..r) for (dz in -1..2) {
            if (dx * dx + dy * dy <= rad * rad && cz + dz in 1 until WorldIso.MAX_Z) {
                val b = world.getBlock(cx + dx, cy + dy, cz + dz)
                if (b > 0 && (BlockRegistry.items[b]?.hardness ?: 0f) < 900f) {
                    setWorldBlock(cx + dx, cy + dy, cz + dz, 0)
                    entities.spawnBlockParticles(cx + dx, cy + dy, cz + dz, 0xFF7722, 3)
                }
            }
        }
    }

    private fun handleMobKill(dead: MobEntity) {
        val dropId = when (dead.type) {
            "grove_guardian" -> 133; "frost_titan" -> 134; "depth_devourer" -> 135
            "inferno_lord", "sky_guardian" -> 129
            "sheep", "cow", "pig", "chicken", "deer", "boar" -> 115
            "slime", "mini_slime", "magma_slime" -> 147
            "crystal_golem" -> 141; "yeti" -> 148; "mimic" -> 104; "goblin" -> 103
            "zombie" -> 101; "skeleton" -> 124; "creeper" -> 26; "enderman" -> 104
            "piglin" -> 103; "blaze" -> 23; "ghast" -> 104
            else -> 101
        }
        entities.spawnDrop(dropId, 1, dead.x, dead.y, dead.z)
        val leveledUp = profiles.recordMobKill(dead.type, dead.isBoss)
        SoundFx.play(if (dead.isBoss || leveledUp) "levelup" else "hit", 0.75f)
        val dropName = BlockRegistry.items[dropId]?.name ?: ""
        showToast(when {
            dead.isBoss -> "БОСС ПОВЕРЖЕН! Награда: $dropName!"
            leveledUp -> "УРОВЕНЬ БОЯ ${profiles.active.combatLevel()}! (+шанс крита)"
            else -> "Побеждён: ${dead.name}"
        })
    }

    private fun pickIsoBlock(screenX: Float, screenY: Float): IsoPick? {
        tmpVec3.set(screenX, screenY, 0f)
        camWorld.unproject(tmpVec3)
        RustEngine.nativePickIsoBlock(tmpVec3.x, tmpVec3.y, floor(player.z).toInt(), activeSliceZ, rustPickBuf)
        return IsoPick(rustPickIntView.get(0), rustPickIntView.get(1), rustPickIntView.get(2), rustPickIntView.get(3))
    }

    private fun findNearestInteractive(cx: Float, cy: Float, cz: Int, maxRad: Float): IsoPick? {
        val r = maxRad.toInt() + 1
        var best: IsoPick? = null
        var bestDist = maxRad + 0.01f
        val gx0 = floor(cx).toInt(); val gy0 = floor(cy).toInt()
        for (dz in -1..2) {
            val gz = cz + dz
            if (gz !in 1 until WorldIso.MAX_Z) continue
            for (dx in -r..r) for (dy in -r..r) {
                val gx = gx0 + dx; val gy = gy0 + dy
                val id = world.getBlock(gx, gy, gz)
                if (id <= 0) continue
                val inter = BlockRegistry.items[id]?.interactive ?: ""
                if (inter.isEmpty()) continue
                val d = hypot((gx + 0.5f - cx).toDouble(), (gy + 0.5f - cy).toDouble()).toFloat()
                if (d < bestDist) { bestDist = d; best = IsoPick(gx, gy, gz, id) }
            }
        }
        return best
    }

    private fun interactiveLabel(id: Int): String = when (BlockRegistry.items[id]?.interactive) {
        "chest" -> "ОТКРЫТЬ СУНДУК"
        "craft" -> "ВЕРСТАК"
        "smelt" -> "ПЕЧЬ"
        "campfire" -> "КОСТЁР"
        "bed" -> "ЛЕЧЬ СПАТЬ"
        "anvil" -> "НАКОВАЛЬНЯ"
        "waystone" -> "ТЕЛЕПОРТ"
        "elevator" -> "ЛИФТ ВВЕРХ/ВНИЗ"
        "door" -> if (id == 57) "ОТКРЫТЬ ДВЕРЬ" else "ЗАКРЫТЬ ДВЕРЬ"
        "sign" -> "ТАБЛИЧКА"
        "altar" -> "АЛТАРЬ БОССА"
        "portal" -> "ПОРТАЛ В АД"
        "mimic" -> "ОТКРЫТЬ СУНДУК"
        else -> "ДЕЙСТВИЕ"
    }

    private fun updateAutoAttack(dt: Float) {
        if (autoAttackCd > 0f) autoAttackCd = max(0f, autoAttackCd - dt)
        if (!autoAttackEnabled || player.isSleeping || autoAttackCd > 0f) return

        val held = player.slots[player.selectedSlot]
        val hDef = held?.let { BlockRegistry.items[it.id] }
        val isBow = hDef?.weaponStyle == "bow"
        val isHammer = hDef?.weaponStyle == "hammer"
        val isFrost = hDef?.weaponStyle == "frost"
        val isRuby = hDef?.weaponStyle == "ruby"
        val isFlint = held?.id == 116
        val isWeapon = hDef != null && (hDef.weaponStyle.isNotEmpty() || hDef.toolType == "axe" || hDef.damage >= 6)

        val range = when {
            isBow -> if (player.creative || player.countItem(124) > 0) 8.5f else 0f
            isHammer -> 3.4f
            isWeapon -> 3.0f
            else -> 1.95f // Самозащита вплотную даже с киркой/блоком!
        }
        if (range <= 0f) return

        val target = entities.mobs
            .filter { it.hostile && !it.tamed && abs(it.z - player.z) < 2.8f }
            .minByOrNull { hypot((it.x - player.x).toDouble(), (it.y - player.y).toDouble()) }
            ?: return
        val dist = hypot((target.x - player.x).toDouble(), (target.y - player.y).toDouble()).toFloat()
        if (dist > range) return

        val dx = target.x - player.x; val dy = target.y - player.y
        val len = max(0.01f, dist)
        player.aimGx = dx / len; player.aimGy = dy / len
        val sdx = player.aimGx - player.aimGy; val sdy = -(player.aimGx + player.aimGy) * 0.5f
        player.attackAngleDeg = atan2(sdy.toDouble(), sdx.toDouble()).toFloat() * 180f / PI.toFloat()
        player.facingRight = sdx >= 0f
        player.attackAnim = 1.0f

        if (isBow) {
            if (player.creative || player.takeItem(124, 1)) {
                entities.fireProjectile(player.x, player.y, player.z, dx, dy, ((hDef?.damage ?: 11) + player.weaponUpgradeLvl * 2).toFloat(), false, false, true)
                autoAttackCd = 0.72f
            }
            return
        }

        entities.slashes.add(SlashArc(player.x + player.aimGx * 0.65f, player.y + player.aimGy * 0.65f, player.z + 0.5f, player.attackAngleDeg, hDef?.colorHex ?: 0xFFFFFF))
        val critChance = profiles.active.combatLevel() * 0.03f
        val isCrit = Random.nextFloat() < critChance
        val dmg = (((hDef?.damage ?: 4) + (if (isFlint) 5 else player.weaponUpgradeLvl * 2)) * (if (isCrit) 1.5f else 1f)).toFloat()
        SoundFx.play("hit", 0.55f)
        val isGuest = netMp.isConnected && !netMp.isHost

        if (isHammer) {
            for (m in entities.mobs) {
                if (!m.tamed && abs(m.z - player.z) < 2.8f && hypot((m.x - player.x).toDouble(), (m.y - player.y).toDouble()) <= range) {
                    if (isGuest) { netMp.sendMobHit(m.id, dmg, player.aimGx, player.aimGy, false); m.hurtFlash = 0.22f }
                    else entities.damageMob(m, dmg, player.aimGx, player.aimGy, frostHit = false, onKill = ::handleMobKill)
                }
            }
            shakeTimer = 0.14f
            autoAttackCd = 0.65f
        } else {
            if (isGuest) { netMp.sendMobHit(target.id, dmg, player.aimGx, player.aimGy, isFrost); target.hurtFlash = 0.22f }
            else entities.damageMob(target, dmg, player.aimGx, player.aimGy, frostHit = isFrost, onKill = ::handleMobKill)
            if (isRuby) {
                player.hp = min(player.maxHp, player.hp + 2f)
                entities.floatingTexts.add(FloatingText(player.x, player.y, player.z + 1.5f, "+2 HP", Color(1f, 0.35f, 0.55f, 1f)))
            }
            if (isFlint) entities.spawnBlockParticles(floor(target.x).toInt(), floor(target.y).toInt(), floor(target.z).toInt(), 0xFF7718, 6)
            autoAttackCd = if (isWeapon) 0.46f else 0.58f
        }
    }

    private fun digStairStepAt(gx: Int, gy: Int, pz: Int) {
        player.attackAnim = 1.0f
        val targetZ = max(1, pz - 1)
        var dugAny = false
        for (dz in intArrayOf(pz + 1, pz, targetZ)) {
            if (dz !in 1 until WorldIso.MAX_Z) continue
            val bId = world.getBlock(gx, gy, dz)
            val bDef = BlockRegistry.items[bId]
            if (bId > 0 && bDef != null && bDef.hardness < 900f) {
                setWorldBlock(gx, gy, dz, 0)
                entities.spawnBlockParticles(gx, gy, dz, bDef.colorHex, 5)
                if (bDef.dropId > 0 && !player.creative) entities.spawnDrop(bDef.dropId, 1, gx + 0.5f, gy + 0.5f, dz.toFloat())
                dugAny = true
            }
        }
        if (dugAny) showToast("Ступенька вниз (Z:$targetZ)")
    }

    private fun useConsumableOrSpecial(held: SlotItem): Boolean {
        val hDef = BlockRegistry.items[held.id] ?: return false
        when (held.id) {
            133 -> if (player.takeItem(133, 1)) { player.maxHp += 4f; player.hp = player.maxHp; showToast("Сердце Леса: Макс. HP ${player.maxHp.toInt()}!"); return true }
            136 -> if (player.takeItem(136, 1)) { player.bodyTemp = 100f; player.warmBuffTimer = 90f; player.food = min(player.maxFood, player.food + 4f); player.hp = min(player.maxHp, player.hp + 4f); showToast("Согревающий чай (90с тепла)!"); return true }
            137 -> if (player.takeItem(137, 1)) { player.minerVisionTimer = 45f; showToast("Отвар Шахтёра: руды подсвечены на 45с!"); return true }
            138 -> if (player.takeItem(138, 1)) { player.speedBuffTimer = 45f; showToast("Эликсир Скорости (+45% бег)!"); return true }
        }
        if (hDef.healFood > 0 && !player.creative && (player.food < player.maxFood || player.hp < player.maxHp)) {
            if (player.takeItem(held.id, 1)) {
                player.food = min(player.maxFood, player.food + hDef.healFood)
                player.hp = min(player.maxHp, player.hp + hDef.healHp)
                entities.floatingTexts.add(FloatingText(player.x, player.y, player.z + 1.5f, "+${hDef.healHp} HP", Color.GREEN))
                showToast("Съедено: ${hDef.name}")
                return true
            }
        }
        return false
    }

    private fun performAttackOrMine(pick: IsoPick, startContinuousHold: Boolean) {
        player.attackAnim = 1.0f
        val held = player.slots[player.selectedSlot]
        val hDef = held?.let { BlockRegistry.items[it.id] }
        val dx = (pick.gx + 0.5f) - player.x; val dy = (pick.gy + 0.5f) - player.y
        val len = max(0.01f, hypot(dx.toDouble(), dy.toDouble()).toFloat())
        player.aimGx = dx / len; player.aimGy = dy / len
        val sdx = player.aimGx - player.aimGy; val sdy = -(player.aimGx + player.aimGy) * 0.5f
        player.attackAngleDeg = atan2(sdy.toDouble(), sdx.toDouble()).toFloat() * 180f / PI.toFloat()
        player.facingRight = sdx >= 0f

        if (hDef?.weaponStyle == "grapple") {
            entities.fireProjectile(player.x, player.y, player.z, dx, dy, 4f, false, false, true, isGrapple = true)
            return
        }
        if (hDef?.weaponStyle == "bow") {
            if (player.creative || player.takeItem(124, 1)) {
                entities.fireProjectile(player.x, player.y, player.z, dx, dy, hDef.damage.toFloat(), false, false, true)
            } else showToast("Нет стрел!")
            return
        }

        // Дуга взмаха оружием/инструментом!
        entities.slashes.add(SlashArc(player.x + player.aimGx * 0.65f, player.y + player.aimGy * 0.65f, player.z + 0.5f, player.attackAngleDeg, hDef?.colorHex ?: 0xFFFFFF))

        // Удар по мобам (с учётом Заточки на Наковальне, Вампиризма Рубинового Клинка, Огнива и сетевого MOB_HIT!)
        val isHammer = hDef?.weaponStyle == "hammer"
        val isFrost = hDef?.weaponStyle == "frost"
        val isRuby = hDef?.weaponStyle == "ruby"
        val isFlint = held?.id == 116
        val reach = if (isHammer) 2.9f else 2.5f
        val critChance = profiles.active.combatLevel() * 0.03f
        val isCrit = Random.nextFloat() < critChance
        val dmg = (((hDef?.damage ?: 4) + (if (isFlint) 5 else player.weaponUpgradeLvl * 2)) * (if (isCrit) 1.5f else 1f)).toFloat()
        SoundFx.play("hit", 0.55f)
        var hitMob = false
        val isGuest = netMp.isConnected && !netMp.isHost
        for (m in entities.mobs) {
            if (!m.tamed && abs(m.z - player.z) < 2.8f && hypot((m.x - player.x).toDouble(), (m.y - player.y).toDouble()) <= reach) {
                if (isGuest) {
                    netMp.sendMobHit(m.id, dmg, player.aimGx, player.aimGy, isFrost)
                    m.hurtFlash = 0.22f
                } else {
                    entities.damageMob(m, dmg, player.aimGx, player.aimGy, frostHit = isFrost, onKill = ::handleMobKill)
                }
                if (isRuby) {
                    player.hp = min(player.maxHp, player.hp + 2f)
                    entities.floatingTexts.add(FloatingText(player.x, player.y, player.z + 1.5f, "+2 HP", Color(1f, 0.35f, 0.55f, 1f)))
                }
                if (isFlint) entities.spawnBlockParticles(floor(m.x).toInt(), floor(m.y).toInt(), floor(m.z).toInt(), 0xFF7718, 8)
                hitMob = true
                if (!isHammer) break
            }
        }
        if (hitMob) { if (isHammer) shakeTimer = 0.16f; return }

        // Умная копка: если тапнули с киркой по блоку ПОЛА прямо рядом с ногами -> роем удобную ступеньку вниз!
        val dist = hypot(dx.toDouble(), dy.toDouble())
        val pz = floor(player.z).toInt()
        // Умная копка вниз: киркой по полу под собой или перед ногами (тапом или удержанием!) -> ступенька вглубь!
        if (hDef?.toolType == "pick" && pick.gz <= pz - 1 && dist <= 2.3) {
            digStairStepAt(pick.gx, pick.gy, pz)
            return
        }

        // Иначе копаем выбранный блок
        if (dist <= 4.6 && pick.id > 0) {
            mineX = pick.gx; mineY = pick.gy; mineZ = pick.gz; holdingMine = true
            updateMining(if (startContinuousHold) 0.10f else 0.30f)
        }
    }

    private fun performInteractOrPlace(pick: IsoPick) {
        player.attackAnim = 0.6f
        val held = player.slots[player.selectedSlot]
        val hDef = held?.let { BlockRegistry.items[it.id] }
        val dist = hypot((pick.gx + 0.5f - player.x).toDouble(), (pick.gy + 0.5f - player.y).toDouble())

        if (dist <= 4.0 && pick.id > 0) {
            when (BlockRegistry.items[pick.id]?.interactive) {
                "craft" -> { screen = UiScreen.WORKBENCH; return }
                "smelt" -> { openFurnacePos = Triple(pick.gx, pick.gy, pick.gz); screen = UiScreen.FURNACE; return }
                "chest" -> { SoundFx.play("chest", 0.65f); openChestPos = Triple(pick.gx, pick.gy, pick.gz); screen = UiScreen.CHEST; return }
                "campfire" -> { player.bodyTemp = 100f; player.hp = min(player.maxHp, player.hp + 4f); screen = UiScreen.CAMPFIRE; return }
                "waystone" -> { SoundFx.play("chest", 0.6f); world.registerWaystone(pick.gx, pick.gy, pick.gz); screen = UiScreen.WAYSTONES; return }
                "elevator" -> {
                    val destZ = world.findElevatorZ(pick.gx, pick.gy, pick.gz)
                    if (destZ > 0) {
                        player.x = pick.gx + 0.5f; player.y = pick.gy + 0.5f; player.z = destZ.toFloat()
                        camWorld.position.set(isoToScreenX(player.x, player.y), isoToScreenY(player.x, player.y, player.z), 0f)
                        world.revealFog(pick.gx, pick.gy, 7)
                        SoundFx.play("levelup", 0.55f)
                        showToast("Шахтный лифт -> Z:$destZ")
                    } else {
                        showToast("Поставьте 2-й Шахтный Лифт строго выше или ниже по колонне!")
                    }
                    return
                }
                "anvil" -> { screen = UiScreen.ANVIL; return }
                "sign" -> {
                    val key = world.blockKey(pick.gx, pick.gy, pick.gz)
                    val curTxt = world.signTexts[key] ?: ""
                    Gdx.input.getTextInput(object : Input.TextInputListener {
                        override fun input(text: String) {
                            val clean = text.trim().take(24)
                            world.signTexts[key] = clean
                            if (netMp.isConnected) netMp.broadcastSign(pick.gx, pick.gy, pick.gz, clean)
                            showToast("Табличка: $clean")
                        }
                        override fun canceled() {}
                    }, "Надпись на табличке", curTxt, "Например: Дом или Шахта")
                    return
                }
                "door" -> {
                    setWorldBlock(pick.gx, pick.gy, pick.gz, if (pick.id == 57) 58 else 57)
                    showToast(if (pick.id == 57) "Дверь открыта" else "Дверь закрыта")
                    return
                }
                "mimic" -> {
                    setWorldBlock(pick.gx, pick.gy, pick.gz, 0)
                    entities.spawnMob("mimic", pick.gx + 0.5f, pick.gy + 0.5f, pick.gz.toFloat())
                    shakeTimer = 0.25f; showToast("ОСТОРОЖНО! Это был Сундук-Мимик!")
                    return
                }
                "altar" -> {
                    SoundFx.play("boss", 0.8f)
                    if (entities.getActiveBoss() != null) { showToast("Босс уже пробуждён!"); return }
                    val bossType = if (entities.season == Season.WINTER) "frost_titan" else "grove_guardian"
                    val b = entities.spawnMob(bossType, pick.gx + 2.5f, pick.gy + 2.5f, pick.gz.toFloat())
                    shakeTimer = 0.4f; showToast("ПРОБУДИЛСЯ БОСС: ${b.name}!")
                    return
                }
                "bed" -> {
                    // РЕАЛИСТИЧНАЯ АНИМАЦИЯ СНА НА КРОВАТИ (БЕЗ МГНОВЕННОГО ПРОПУСКА НОЧИ!)
                    player.spawnX = pick.gx + 0.5f; player.spawnY = pick.gy + 0.5f; player.spawnZ = pick.gz.toFloat(); player.spawnDim = world.dimension
                    player.isSleeping = true
                    player.sleepBedX = pick.gx; player.sleepBedY = pick.gy; player.sleepBedZ = pick.gz
                    player.x = pick.gx + 0.5f; player.y = pick.gy + 0.5f; player.z = pick.gz.toFloat()
                    player.vx = 0f; player.vy = 0f; player.sleepElapsed = 0f; player.zzzTimer = 0.2f
                    showToast("Вы легли спать • Время ускорено (x2.6, ночь ~2.3 мин)")
                    return
                }
                "portal" -> { switchDimension(if (world.dimension == "nether") "overworld" else "nether"); return }
                "tnt" -> { explodeAt(pick.gx, pick.gy, pick.gz, 2.8f); return }
            }
        }

        // Специальные предметы по тапу на блок: Ведро воды (119), Пустое ведро (120), Огниво (116), Посадка Пшеницы (143) и Ягод (144)!
        if (held != null && dist <= 4.6) {
            when (held.id) {
                119 -> { // Ведро воды: разливает воду и превращает соседнюю Лаву в Обсидиан!
                    val tz = if (pick.id == 0) pick.gz else pick.gz + 1
                    if (tz in 1 until WorldIso.MAX_Z) {
                        var madeObs = false
                        for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
                            if (world.getBlock(pick.gx + dx, pick.gy + dy, tz + dz) == 29) {
                                setWorldBlock(pick.gx + dx, pick.gy + dy, tz + dz, 22); madeObs = true
                            }
                        }
                        if (!madeObs && world.getBlock(pick.gx, pick.gy, tz) == 0) setWorldBlock(pick.gx, pick.gy, tz, 9)
                        if (!player.creative) player.slots[player.selectedSlot] = SlotItem(120, 1)
                        entities.spawnBlockParticles(pick.gx, pick.gy, tz, 0x3898FF, 8)
                        showToast(if (madeObs) "Вода + Лава = Обсидиан!" else "Вода разлита!")
                        return
                    }
                }
                150 -> { // Удочка: рыбалка в воде (9), луже (42) или проруби во льду (20)!
                    var nearWater = false
                    for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
                        if (world.getBlock(pick.gx + dx, pick.gy + dy, pick.gz + dz) in intArrayOf(9, 20, 42)) { nearWater = true; break }
                    }
                    if (nearWater) {
                        fishingActive = true; fishingBarPos = 0.1f; fishingBarDir = 1f
                        SoundFx.play("splash", 0.65f)
                        showToast("Клюёт! Тапните, когда поплавок в ЗЕЛЁНОЙ зоне!")
                        return
                    } else {
                        showToast("Подойдите к воде или льду для рыбалки!")
                        return
                    }
                }
                120 -> { // Пустое ведро: набирает воду обратно!
                    var foundWater = false
                    for (dz in 0..1) {
                        if (world.getBlock(pick.gx, pick.gy, pick.gz + dz) == 9) {
                            setWorldBlock(pick.gx, pick.gy, pick.gz + dz, 0); foundWater = true; break
                        }
                    }
                    if (foundWater) {
                        player.slots[player.selectedSlot] = SlotItem(119, 1)
                        showToast("Набрано Ведро воды!")
                        return
                    }
                }
                116 -> { // Огниво: поджигает Обсидиан->Портал, TNT->Взрыв, Дерево->Костёр!
                    when (pick.id) {
                        22 -> { setWorldBlock(pick.gx, pick.gy, min(WorldIso.MAX_Z - 1, pick.gz + 1), 34); showToast("Портал в Ад зажжён!"); return }
                        26 -> { explodeAt(pick.gx, pick.gy, pick.gz, 3.0f); return }
                        5, 7, 27, 45, 46 -> {
                            val tz = min(WorldIso.MAX_Z - 1, pick.gz + 1)
                            if (world.getBlock(pick.gx, pick.gy, tz) == 0) { setWorldBlock(pick.gx, pick.gy, tz, 59); showToast("Костёр разожжён!"); return }
                        }
                    }
                }
                143 -> { // Посадка Пшеницы на Дёрн/Землю!
                    if (pick.id in intArrayOf(1, 2, 42) && world.getBlock(pick.gx, pick.gy, pick.gz + 1) == 0) {
                        if (player.takeItem(143, 1)) { setWorldBlock(pick.gx, pick.gy, pick.gz + 1, 54); showToast("Пшеница посажена!"); return }
                    }
                }
                144 -> { // Посадка Ягодного куста на Дёрн/Землю (если не едим)!
                    if (pick.id in intArrayOf(1, 2) && world.getBlock(pick.gx, pick.gy, pick.gz + 1) == 0 && player.food >= 18f) {
                        if (player.takeItem(144, 1)) { setWorldBlock(pick.gx, pick.gy, pick.gz + 1, 67); showToast("Ягодный куст посажен!"); return }
                    }
                }
            }
        }
        if (held != null && useConsumableOrSpecial(held)) return

        if (dist > 4.6) return
        val placeSlot = if (hDef != null && !hDef.isItem) held
        else if (player.offhand != null && BlockRegistry.items[player.offhand!!.id]?.isItem == false) player.offhand
        else null
        val pSlot = placeSlot ?: return
        val pDef = BlockRegistry.items[pSlot.id] ?: return

        val targetZ = if (pick.id == 0) pick.gz else pick.gz + 1
        if (targetZ in 1 until WorldIso.MAX_Z && world.getBlock(pick.gx, pick.gy, targetZ) == 0) {
            if (pDef.solid && floor(player.x).toInt() == pick.gx && floor(player.y).toInt() == pick.gy && floor(player.z).toInt() == targetZ) return
            if (player.takeItem(pSlot.id, 1)) {
                setWorldBlock(pick.gx, pick.gy, targetZ, pSlot.id)
                entities.spawnBlockParticles(pick.gx, pick.gy, targetZ, pDef.colorHex, 5)
            }
        }
    }

    private fun updateMining(dt: Float) {
        if (!holdingMine) { mineProgress = 0f; return }
        val bId = world.getBlock(mineX, mineY, mineZ)
        val bDef = BlockRegistry.items[bId]
        if (bId == 0 || bDef == null || bDef.hardness >= 900f) { holdingMine = false; mineProgress = 0f; return }

        val held = player.slots[player.selectedSlot]
        val hDef = held?.let { BlockRegistry.items[it.id] }
        if (player.attackAnim <= 0.15f) {
            player.attackAnim = 0.85f
            entities.spawnBlockParticles(mineX, mineY, mineZ, bDef.colorHex, 2); SoundFx.play("mine", 0.35f)
        }

        fun breakSingle(bx: Int, by: Int, bz: Int) {
            val id = world.getBlock(bx, by, bz)
            val d = BlockRegistry.items[id] ?: return
            if (id == 0 || d.hardness >= 900f) return
            setWorldBlock(bx, by, bz, 0)
            entities.spawnBlockParticles(bx, by, bz, d.colorHex, 7)
            SoundFx.play("break", 0.55f)
            var dropAmt = if ((id == 54 || id == 67) && entities.season in setOf(Season.SPRING, Season.SUMMER)) 2 else 1
            if (d.isOre) {
                if (Random.nextFloat() < profiles.active.miningLevel() * 0.04f) { dropAmt += 1; showToast("Навык Шахтёра: Двойная руда!") }
                if (profiles.addMiningXp(10)) { SoundFx.play("levelup", 0.7f); showToast("Уровень Шахтёра: ${profiles.active.miningLevel()}!") }
            } else if (id == 54 || id == 67) {
                if (profiles.addFarmXp(8)) { SoundFx.play("levelup", 0.7f); showToast("Уровень Фермера: ${profiles.active.farmLevel()}!") }
            }
            if (d.dropId > 0 && !player.creative) entities.spawnDrop(d.dropId, dropAmt, bx + 0.5f, by + 0.5f, bz.toFloat())
            luaMods.emitBlockBreak(bx, by, bz, id, held?.id ?: 0)
        }

        if (player.creative) { breakSingle(mineX, mineY, mineZ); holdingMine = false; mineProgress = 0f; return }

        val pickOnDirt = hDef?.toolType == "pick" && bDef.toolType == "shovel"
        val speed = (if (hDef != null && (hDef.toolType == bDef.toolType || pickOnDirt)) hDef.mineSpeed else 1.4f) * (1f + player.toolUpgradeLvl * 0.18f)
        mineProgress += (dt * speed) / max(0.15f, bDef.hardness)
        if (mineProgress >= 1.0f) {
            breakSingle(mineX, mineY, mineZ)
            if (hDef?.aoeMine == true) {
                breakSingle(mineX + 1, mineY, mineZ); breakSingle(mineX, mineY + 1, mineZ); breakSingle(mineX + 1, mineY + 1, mineZ)
            }
            if (bId in intArrayOf(5, 27, 45, 46) && hDef?.toolType == "axe") {
                for (dz in 1..4) if (world.getBlock(mineX, mineY, mineZ + dz) == bId) breakSingle(mineX, mineY, mineZ + dz)
            }
            mineProgress = 0f; holdingMine = false
        }
    }

    private fun performDashOrParry() {
        if (player.hasShield()) {
            player.parryTimer = 0.48f
            entities.spawnBlockParticles(floor(player.x).toInt(), floor(player.y).toInt(), floor(player.z).toInt(), 0xFFD24C, 6)
            showToast("Щит: Парирование!")
        }
        if (player.dashCooldown > 0f) return
        player.dashCooldown = 1.3f
        // Призрачный шлейф рывка!
        for (i in 0..2) {
            entities.dashGhosts.add(DashGhost(player.x + player.aimGx * i * 0.25f, player.y + player.aimGy * i * 0.25f, player.z, player.facingRight, player.skinId, 0.26f - i * 0.05f))
        }
        player.vx = player.aimGx * 14f; player.vy = player.aimGy * 14f
        entities.spawnBlockParticles(floor(player.x).toInt(), floor(player.y).toInt(), floor(player.z).toInt(), 0x38BDF8, 5)
    }

    private fun dropSlotItem(slotIdx: Int) {
        val s = player.slots[slotIdx] ?: return
        if (player.takeItem(s.id, 1)) {
            entities.spawnDrop(s.id, 1, player.x + player.aimGx * 0.8f, player.y + player.aimGy * 0.8f, player.z, player.aimGx * 2.5f, player.aimGy * 2.5f)
            showToast("Выброшено: ${BlockRegistry.items[s.id]?.name}")
        }
    }

    private fun updateFurnaces(dt: Float) {
        for (f in world.furnaces.values) {
            if (f.burn > 0f) f.burn = max(0f, f.burn - dt * 10f)
            val outId = f.input?.let { BlockRegistry.smeltMap[it.id] }
            val canCook = outId != null && (f.output == null || (f.output!!.id == outId && f.output!!.count < 64))
            if (canCook && f.burn <= 0f && f.fuel != null) {
                val fp = BlockRegistry.items[f.fuel!!.id]?.fuelPower ?: 0f
                if (fp > 0f) { f.maxBurn = fp; f.burn = fp; f.fuel!!.count--; if (f.fuel!!.count <= 0) f.fuel = null }
            }
            if (canCook && f.burn > 0f) {
                f.cook += dt * 20f
                if (f.cook >= 100f) {
                    f.cook = 0f; f.input!!.count--; if (f.input!!.count <= 0) f.input = null
                    if (f.output == null) f.output = SlotItem(outId!!, 1) else f.output!!.count++
                }
            } else if (!canCook) f.cook = 0f
        }
    }

    private fun damagePlayer(raw: Float, attacker: MobEntity?) {
        if (player.creative || raw <= 0f) return
        if (player.isSleeping) { player.isSleeping = false; showToast("Вас разбудил враг!") }
        if (player.parryTimer > 0f) {
            player.parryTimer = 0f
            attacker?.let {
                it.stunTimer = 2.2f
                it.vx = (it.x - player.x) * 8f; it.vy = (it.y - player.y) * 8f
                entities.floatingTexts.add(FloatingText(it.x, it.y, it.z + 1.5f, "ПАРИРОВАНО!", Color.GOLD))
            }
            showToast("Идеальное парирование щитом!")
            return
        }
        val dmg = max(1f, raw * (1f - player.getTotalArmor() * 0.045f))
        player.hp = max(0f, player.hp - dmg)
        player.hurtFlash = 0.24f; player.hurtSquash = 1.0f; shakeTimer = 0.2f; SoundFx.play("hurt", 0.7f)
        entities.floatingTexts.add(FloatingText(player.x, player.y, player.z + 1.5f, "-${dmg.toInt()}", Color(1f, 0.25f, 0.3f, 1f)))
        if (player.hp <= 0f) screen = UiScreen.DEATH
    }

    private fun updatePlayerTemperatureAndBuffs(dt: Float) {
        if (player.warmBuffTimer > 0f) player.warmBuffTimer = max(0f, player.warmBuffTimer - dt)
        if (player.minerVisionTimer > 0f) player.minerVisionTimer = max(0f, player.minerVisionTimer - dt)
        if (player.speedBuffTimer > 0f) player.speedBuffTimer = max(0f, player.speedBuffTimer - dt)
        if (player.parryTimer > 0f) player.parryTimer = max(0f, player.parryTimer - dt)
        if (player.hurtFlash > 0f) player.hurtFlash = max(0f, player.hurtFlash - dt)
        if (player.hurtSquash > 0f) player.hurtSquash = max(0f, player.hurtSquash - dt * 5f)

        val px = floor(player.x).toInt(); val py = floor(player.y).toInt(); val pz = floor(player.z).toInt()
        var nearWarmth = false
        for (dx in -3..3) for (dy in -3..3) for (dz in -1..1) {
            if (BlockRegistry.items[world.getBlock(px + dx, py + dy, pz + dz)]?.isWarmSource == true) { nearWarmth = true; break }
        }
        val holdingWarm = BlockRegistry.items[player.slots[player.selectedSlot]?.id ?: 0]?.isWarmSource == true ||
            BlockRegistry.items[player.offhand?.id ?: 0]?.isWarmSource == true

        val isWinterOutdoors = world.dimension == "overworld" && entities.season == Season.WINTER && !isCutawayActive && !player.isSleeping
        val isCold = isWinterOutdoors && !nearWarmth && !player.hasWarmArmor() && player.warmBuffTimer <= 0f
        val targetTemp = when {
            nearWarmth || player.isSleeping -> 95f
            player.warmBuffTimer > 0f || player.hasWarmArmor() -> 80f
            isCold -> if (entities.weather == WeatherType.SNOW) 8f else 18f
            holdingWarm -> 76f
            else -> 72f
        }
        player.bodyTemp += (targetTemp - player.bodyTemp) * min(1f, dt * 0.25f)

        if (player.bodyTemp < 24f && !player.creative) {
            freezeDmgTimer += dt
            if (freezeDmgTimer >= 3.5f) {
                freezeDmgTimer = 0f; damagePlayer(1.5f, null)
                showToast("Вы замерзаете! Погрейтесь у Костра или наденьте Шубу.")
            }
        }
    }

    private fun updatePlayer(dt: Float) {
        if (GamepadBridge.dashTriggered) { GamepadBridge.dashTriggered = false; performDashOrParry() }
        if (GamepadBridge.swapOffhandTriggered) { GamepadBridge.swapOffhandTriggered = false; player.swapWithOffhand() }
        if (GamepadBridge.hotbarDelta != 0) { player.selectedSlot = (player.selectedSlot + GamepadBridge.hotbarDelta + 9) % 9; GamepadBridge.hotbarDelta = 0 }

        var inX = stickDx + GamepadBridge.moveX + (if (Gdx.input.isKeyPressed(Input.Keys.D)) 1f else 0f) - (if (Gdx.input.isKeyPressed(Input.Keys.A)) 1f else 0f)
        var inY = stickDy + GamepadBridge.moveY + (if (Gdx.input.isKeyPressed(Input.Keys.W)) 1f else 0f) - (if (Gdx.input.isKeyPressed(Input.Keys.S)) 1f else 0f)
        val inLen = hypot(inX.toDouble(), inY.toDouble()).toFloat()
        if (inLen > 1f) { inX /= inLen; inY /= inLen }

        // Движение стиком будит игрока и поднимает с кровати!
        if (player.isSleeping) {
            if (inLen > 0.25f) {
                player.isSleeping = false; player.sleepElapsed = 0f
                showToast("Вы встали с кровати")
            } else {
                player.vx = 0f; player.vy = 0f; player.isMoving = false
                return
            }
        }

        val dGx = inX - inY; val dGy = -inX - inY
        val dLen = hypot(dGx.toDouble(), dGy.toDouble()).toFloat()
        val gxDir = if (dLen > 0.01f) dGx / dLen else 0f
        val gyDir = if (dLen > 0.01f) dGy / dLen else 0f

        val pxI = floor(player.x).toInt(); val pyI = floor(player.y).toInt(); val pzI = floor(player.z).toInt()
        val underB = world.getBlock(pxI, pyI, max(0, pzI - 1))
        val atFeetB = world.getBlock(pxI, pyI, pzI)
        val onLadder = BlockRegistry.items[atFeetB]?.isClimbable == true || BlockRegistry.items[underB]?.isClimbable == true
        val inWater = underB == 9
        val inLava = underB == 29
        if (inLava) damagePlayer(3f * dt * 2f, null)

        // Пыль под ногами при беге и брызги весенних луж!
        player.isMoving = inLen > 0.14f
        if (player.isMoving) {
            player.stepDustTimer += dt
            if (underB == 42 && player.stepDustTimer >= 0.12f) {
                player.stepDustTimer = 0f; entities.spawnBlockParticles(pxI, pyI, pzI, 0x74C8FF, 2); SoundFx.play("splash", 0.3f)
            } else if (inLen > 0.78f && player.stepDustTimer >= 0.22f) {
                SoundFx.play("step", 0.22f)
                player.stepDustTimer = 0f
                val dustCol = BlockRegistry.items[underB]?.colorHex ?: 0xD8D0C0
                entities.spawnRisingSpark(player.x, player.y, player.z, dustCol, 4.5f)
            }
        }

        if (onLadder && abs(inY) > 0.35f) {
            val nz = (player.z + inY * dt * 4.5f).coerceIn(1f, (WorldIso.MAX_Z - 2).toFloat())
            if (BlockRegistry.items[world.getBlock(pxI, pyI, floor(nz).toInt())]?.solid != true) player.z = nz
        }

        val speedBuff = if (player.speedBuffTimer > 0f) 1.45f else 1.0f
        val spd = baseWalkSpeed * speedBuff * min(1f, inLen) * (if (inWater) 0.58f else if (inLen > 0.85f) 1.32f else 1.0f)
        player.vx += (gxDir * spd - player.vx) * min(1f, dt * 11f)
        player.vy += (gyDir * spd - player.vy) * min(1f, dt * 11f)

        if (player.isMoving) {
            player.aimGx = gxDir; player.aimGy = gyDir
            if (abs(inX) > 0.08f) player.facingRight = inX > 0f
            player.walkAnim += dt * (if (inLen > 0.85f) 11.5f else 8.5f)
        }

        val nx = player.x + player.vx * dt
        val gzX = world.getGroundZ(floor(nx).toInt(), floor(player.y).toInt(), player.z)
        if (gzX != null && abs(gzX - player.z) <= 1.25f) player.x = nx

        val ny = player.y + player.vy * dt
        val gzY = world.getGroundZ(floor(player.x).toInt(), floor(ny).toInt(), player.z)
        if (gzY != null && abs(gzY - player.z) <= 1.25f) player.y = ny

        if (!onLadder) {
            val targetZ = world.getGroundZ(floor(player.x).toInt(), floor(player.y).toInt(), player.z)
            if (targetZ != null) player.z += (targetZ - player.z) * min(1f, dt * 14f)
        }

        if (player.dashCooldown > 0f) player.dashCooldown = max(0f, player.dashCooldown - dt)
        if (player.attackAnim > 0f) player.attackAnim = max(0f, player.attackAnim - dt * 5f)
    }

    override fun render() {
        val dt = min(0.05f, Gdx.graphics.deltaTime)
        elapsed += dt
        if (toastTimer > 0f) toastTimer = max(0f, toastTimer - dt)
        if (shakeTimer > 0f) shakeTimer = max(0f, shakeTimer - dt)
        if (chatLog.isNotEmpty()) {
            val updated = chatLog.map { Pair(it.first, it.second - dt) }.filter { it.second > 0f }
            chatLog.clear(); chatLog.addAll(updated)
        }

        updateOstMusic()

        if (screen == UiScreen.PLAYING) {
            // Долгое удержание пальца на блоке (> 0.20с) запускает непрерывную копку!
            if (worldTouchPtr != -1 && !worldTouchHeldActed && (elapsed - worldTouchDownTime) >= 0.20f) {
                worldTouchHeldActed = true
                pickIsoBlock(worldTouchStartX, worldTouchStartY)?.let { pk ->
                    targetPick = pk
                    if (pk.id > 0) performAttackOrMine(pk, startContinuousHold = true)
                }
            }

            updatePlayer(dt)
            updateAutoAttack(dt)
            // Открываем Туман Войны в шахте вокруг героя!
            val fGx = floor(player.x).toInt(); val fGy = floor(player.y).toInt()
            if (fGx != lastFogGx || fGy != lastFogGy) {
                lastFogGx = fGx; lastFogGy = fGy
                if (player.z < WorldIso.SEA_Z - 2) world.revealFog(fGx, fGy, 6)
            }
            // Обновляем мини-карту через нативный Rust 2 раза в секунду
            minimapTimer += dt
            if (minimapTimer >= 0.45f) {
                minimapTimer = 0f
                world.sampleMinimap(fGx, fGy, 2, MINIMAP_SZ, rustMinimapBuf)
                rustMinimapIntView.position(0); rustMinimapIntView.get(minimapColors, 0, MINIMAP_SZ * MINIMAP_SZ)
                minimapPixmap.blending = Pixmap.Blending.None
                for (my in 0 until MINIMAP_SZ) for (mx in 0 until MINIMAP_SZ) {
                    minimapPixmap.drawPixel(mx, my, minimapColors[my * MINIMAP_SZ + mx])
                }
                minimapTex.draw(minimapPixmap, 0, 0)
            }
            // Анимация поплавка при рыбалке!
            if (fishingActive) {
                fishingBarPos += fishingBarDir * dt * 1.18f
                if (fishingBarPos >= 1f) { fishingBarPos = 1f; fishingBarDir = -1f }
                if (fishingBarPos <= 0f) { fishingBarPos = 0f; fishingBarDir = 1f }
            }
            updatePlayerTemperatureAndBuffs(dt)
            updateFurnaces(dt)
            updateMining(dt)
            entities.update(
                dt, world, player,
                isNetClient = netMp.isConnected && !netMp.isHost,
                onHurtPlayer = ::damagePlayer,
                onExplode = ::explodeAt,
                onMobKill = ::handleMobKill,
                onPickup = { dropId, itemId, cnt -> if (player.addItem(itemId, cnt)) SoundFx.play("pickup", 0.45f); if (netMp.isConnected && !netMp.isHost) netMp.sendDropPickup(dropId) },
                onGrapplePull = { gx, gy -> player.vx = (gx - player.x) * 5.5f; player.vy = (gy - player.y) * 5.5f; showToast("Крюк-кошка!") },
                onWakeUpMorning = { showToast("Доброе утро! День ${entities.dayCount} (${entities.season.ruName})") }
            )
            if (!player.creative) {
                hungerTimer += dt
                if (hungerTimer > 26f) {
                    hungerTimer = 0f
                    if (player.food > 0f) player.food = max(0f, player.food - 1f) else damagePlayer(1f, null)
                    if (player.food >= 16f && player.hp < player.maxHp) player.hp = min(player.maxHp, player.hp + 1f)
                }
            }
            if (netMp.isConnected) {
                mpPosTimer += dt
                if (mpPosTimer >= 0.10f) {
                    mpPosTimer = 0f
                    netMp.sendPos(player.x, player.y, player.z, player.facingRight, player.skinId, player.attackAnim, player.isSleeping, player.slots[player.selectedSlot]?.id ?: 0, player.hp, player.maxHp)
                }
                if (netMp.isHost) {
                    mpTimeSyncTimer += dt
                    if (mpTimeSyncTimer >= 2.5f) { mpTimeSyncTimer = 0f; netMp.broadcastTime(entities.dayCount, entities.worldTime) }
                    mpMobSyncTimer += dt
                    if (mpMobSyncTimer >= 0.14f) { mpMobSyncTimer = 0f; netMp.broadcastMobs(entities.mobs) }
                    mpDropSyncTimer += dt
                    if (mpDropSyncTimer >= 0.35f) { mpDropSyncTimer = 0f; netMp.broadcastDrops(entities.drops) }
                }
            }
        }

        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        val pGx = floor(player.x).toInt(); val pGy = floor(player.y).toInt(); val pZ = floor(player.z).toInt()
        isCutawayActive = (screen != UiScreen.MAIN_MENU) && (world.hasRoofAbove(pGx, pGy, pZ) || manualLayerOffset != 0)
        activeSliceZ = if (isCutawayActive) (pZ + 1 + manualLayerOffset).coerceIn(1, WorldIso.MAX_Z - 1) else min(WorldIso.MAX_Z - 1, pZ + 12)

        when {
            isCutawayActive -> Gdx.gl.glClearColor(0.04f, 0.03f, 0.07f, 1f)
            world.dimension == "nether" -> Gdx.gl.glClearColor(0.22f, 0.05f, 0.06f, 1f)
            world.dimension == "aether" -> Gdx.gl.glClearColor(0.45f, 0.80f, 0.98f, 1f)
            else -> Gdx.gl.glClearColor(0.14f, 0.34f, 0.56f, 1f)
        }
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        if (screen != UiScreen.MAIN_MENU) renderIsometricWorld(dt)
        render2DUI()
    }

    // =========================================================================
    // LIVING ANIMATED 250-LAYER ISOMETRIC RENDERER!
    // =========================================================================
    private fun drawIsoQuad(tex: Texture, x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, colBits: Float, u0: Float, v0: Float, u1: Float, v1: Float) {
        val q = quadVerts
        q[0] = x0; q[1] = y0; q[2] = colBits; q[3] = u0; q[4] = v1
        q[5] = x1; q[6] = y1; q[7] = colBits; q[8] = u0; q[9] = v0
        q[10] = x2; q[11] = y2; q[12] = colBits; q[13] = u1; q[14] = v0
        q[15] = x3; q[16] = y3; q[17] = colBits; q[18] = u1; q[19] = v1
        spriteBatch.draw(tex, q, 0, 20)
    }

    private fun renderIsometricWorld(dt: Float) {
        val TW = WorldIso.TW; val TH = WorldIso.TH; val ZH = WorldIso.ZH
        val tCamX = isoToScreenX(player.x, player.y)
        val tCamY = isoToScreenY(player.x, player.y, player.z) + 18f
        camWorld.position.x += (tCamX - camWorld.position.x) * min(1f, dt * 8f)
        camWorld.position.y += (tCamY - camWorld.position.y) * min(1f, dt * 8f)
        if (shakeTimer > 0f) {
            camWorld.position.x += (Random.nextFloat() - 0.5f) * 9f
            camWorld.position.y += (Random.nextFloat() - 0.5f) * 9f
        }
        camWorld.zoom = cameraZoom
        camWorld.update()

        val halfW = (camWorld.viewportWidth * camWorld.zoom) * 0.5f + TW * 4f
        val halfH = (camWorld.viewportHeight * camWorld.zoom) * 0.5f + ZH * 6f
        val pGx = floor(player.x).toInt(); val pGy = floor(player.y).toInt(); val pZ = floor(player.z).toInt()
        val zMin = max(0, pZ - 6)
        val zMax = activeSliceZ
        val animFrame = (elapsed * 5f).toInt() and 1
        val grassTint = if (world.dimension == "overworld") entities.season.grassTint else Color.WHITE

        // === 1 SINGLE NATIVE RUST CALL BUILDS ALL VISIBLE 250-LAYER QUADS, PROPS, LIGHTS & ORES! ===
        RustEngine.nativeBuildIsoFrame(
            elapsed, player.x, player.y, player.z,
            camWorld.position.x, camWorld.position.y, halfW, halfH,
            grassTint.r, grassTint.g, grassTint.b,
            isCutawayActive, zMax, player.minerVisionTimer > 0f,
            rustVertsBuf, rustDiagBuf, rustPropsBuf, rustLightsBuf, rustOresBuf, rustCountsBuf
        )

        val vertFloats = rustCountsIntView.get(0).coerceIn(0, MAX_TERRAIN_FLOATS)
        val diagCount = rustCountsIntView.get(1).coerceIn(0, 64)
        val minSum = rustCountsIntView.get(2)
        val propCount = rustCountsIntView.get(3).coerceIn(0, 1024)
        val lightCount = rustCountsIntView.get(4).coerceIn(0, 512)
        val oreCount = rustCountsIntView.get(5).coerceIn(0, 512)

        if (vertFloats > 0) { rustVertsFloatView.position(0); rustVertsFloatView.get(terrainVertsArr, 0, vertFloats) }
        if (diagCount > 0) { rustDiagIntView.position(0); rustDiagIntView.get(diagArr, 0, diagCount * 2) }
        if (propCount > 0) { rustPropsFloatView.position(0); rustPropsFloatView.get(propsArr, 0, propCount * 8) }
        if (lightCount > 0) { rustLightsFloatView.position(0); rustLightsFloatView.get(lightsArr, 0, lightCount * 4) }
        if (oreCount > 0) { rustOresFloatView.position(0); rustOresFloatView.get(oresArr, 0, oreCount * 3) }

        // Group dynamic entities into pre-allocated diagonal buckets (0 allocations!)
        for (i in 0 until diagCount) {
            mobsByDiag[i].clear()
            dropsByDiag[i].clear()
            remoteByDiag[i].clear()
        }
        for (m in entities.mobs) {
            if (m.z <= zMax + 1 && m.z >= zMin - 1) {
                val di = (floor(m.x).toInt() + floor(m.y).toInt()) - minSum
                if (di in 0 until diagCount) mobsByDiag[di].add(m)
            }
        }
        entities.foxPet?.let { pt ->
            val di = (floor(pt.x).toInt() + floor(pt.y).toInt()) - minSum
            if (di in 0 until diagCount) mobsByDiag[di].add(pt)
        }
        for (d in entities.drops) {
            if (d.z <= zMax + 1 && d.z >= zMin - 1) {
                val di = (floor(d.x).toInt() + floor(d.y).toInt()) - minSum
                if (di in 0 until diagCount) dropsByDiag[di].add(d)
            }
        }
        for (rp in netMp.remotePlayers.values) {
            val di = (floor(rp.x).toInt() + floor(rp.y).toInt()) - minSum
            if (di in 0 until diagCount) remoteByDiag[di].add(rp)
        }
        val playerDiag = (pGx + pGy) - minSum

        val atlas = BlockRegistry.atlasTexture
        spriteBatch.projectionMatrix = camWorld.combined
        spriteBatch.begin()

        // Шлейфы рывка
        for (dg in entities.dashGhosts) {
            val gx = isoToScreenX(dg.x, dg.y); val gy = isoToScreenY(dg.x, dg.y, dg.z)
            val reg = SpriteArt.skinSprites[dg.skinId]?.get(1) ?: continue
            spriteBatch.setColor(0.45f, 0.88f, 1f, (dg.life / 0.28f) * 0.55f)
            spriteBatch.draw(reg, gx - 32f, gy - 12f, 32f, 12f, 64f, 80f, if (dg.facingRight) 1f else -1f, 1f, 0f)
        }
        spriteBatch.color = Color.WHITE

        // === STRICT PAINTER'S ALGORITHM: 53 BIG RUST CONTIGUOUS BATCHES INSTEAD OF 15,000 TINY DRAWS! ===
        var propCursor = 0
        var handLightX = 0f; var handLightY = 0f; var handLightRad = 0f; var handLightCol = 0

        for (d in 0 until diagCount) {
            val startF = diagArr[d * 2]
            val countF = diagArr[d * 2 + 1]
            if (countF > 0) {
                spriteBatch.draw(atlas, terrainVertsArr, startF, countF)
            }

            // Props on diagonal d (emitted in sorted diagonal order by Rust!)
            while (propCursor < propCount && propsArr[propCursor * 8].toInt() == d) {
                val pi = propCursor * 8
                val sx = propsArr[pi + 1]
                val sy = propsArr[pi + 2]
                val bId = propsArr[pi + 3].toInt()
                val gx = propsArr[pi + 4].toInt()
                val gy = propsArr[pi + 5].toInt()
                val gz = propsArr[pi + 6].toInt()
                val a = propsArr[pi + 7]
                val def = BlockRegistry.items[bId]
                val pIdx = when (bId) {
                    35 -> if (openChestPos?.first == gx && openChestPos?.second == gy && openChestPos?.third == gz) 3 else 2
                    37 -> animFrame; 34 -> 5 + animFrame; 59 -> 11 + animFrame
                    else -> def?.propIdx ?: -1
                }
                if (pIdx in SpriteArt.props.indices) {
                    spriteBatch.setColor(1f, 1f, 1f, 0.78f * a)
                    spriteBatch.draw(SpriteArt.shadowTex, sx - 24f, sy - 12f, 48f, 22f)
                    spriteBatch.setColor(1f, 1f, 1f, a)
                    val sway = if (bId == 68 || bId == 66) sin(elapsed * 2.8f + gx + gy) * 3.5f else 0f
                    spriteBatch.draw(SpriteArt.props[pIdx], sx - 32f, sy - 14f, 32f, 14f, 64f, 64f, 1f, 1f, sway)
                    spriteBatch.color = Color.WHITE
                    if (bId == 65) {
                        world.signTexts[world.blockKey(gx, gy, gz)]?.takeIf { it.isNotEmpty() }?.let { sTxt ->
                            glyphLayout.setText(fontSmall, sTxt)
                            fontSmall.color = Color(1f, 0.94f, 0.72f, 1f)
                            fontSmall.draw(spriteBatch, glyphLayout, sx - glyphLayout.width * 0.5f, sy + 56f)
                            fontSmall.color = Color.WHITE
                        }
                    }
                }
                propCursor++
            }

            // Drops on diagonal d
            val dList = dropsByDiag[d]
            for (i in 0 until dList.size) {
                val dr = dList[i]
                val dx = isoToScreenX(dr.x, dr.y); val dy = isoToScreenY(dr.x, dr.y, dr.z)
                val bob = sin(dr.age * 4.5f) * 5f
                spriteBatch.setColor(1f, 1f, 1f, 0.58f); spriteBatch.draw(SpriteArt.shadowTex, dx - 14f, dy - 6f, 28f, 12f)
                spriteBatch.color = Color.WHITE
                BlockRegistry.getIconRegion(dr.itemId)?.let {
                    spriteBatch.draw(it, dx - 15f, dy + bob, 15f, 15f, 30f, 30f, 1f, 1f, sin(dr.age * 3f) * 10f)
                }
            }

            // Mobs on diagonal d
            val mList = mobsByDiag[d]
            for (i in 0 until mList.size) drawAnimatedMob(mList[i])

            // Remote players on diagonal d
            val rList = remoteByDiag[d]
            for (i in 0 until rList.size) {
                val rp = rList[i]
                val rpx = isoToScreenX(rp.x, rp.y); val rpy = isoToScreenY(rp.x, rp.y, rp.z)
                drawAnimatedHero(
                    rpx, rpy, rp.skinId, rp.facingRight, rp.walkAnim, moving = true,
                    heldId = rp.heldId, attackAnim = rp.attackAnim, attackAngle = 0f,
                    isSleeping = rp.isSleeping, hurtFlash = 0f, hurtSquash = 0f,
                    armorHelmet = 0, armorChest = 0, hasShieldOffhand = false
                )
                // Ник и полоска HP над головой друга в мультиплеере!
                glyphLayout.setText(fontSmall, rp.nick)
                fontSmall.color = Color(0.56f, 0.90f, 1f, 1f)
                fontSmall.draw(spriteBatch, glyphLayout, rpx - glyphLayout.width * 0.5f, rpy + 86f)
                fontSmall.color = Color.WHITE
                spriteBatch.setColor(0.1f, 0.05f, 0.1f, 0.85f); spriteBatch.draw(SpriteArt.whitePx, rpx - 22f, rpy + 68f, 44f, 6f)
                spriteBatch.setColor(0.26f, 0.90f, 0.44f, 1f); spriteBatch.draw(SpriteArt.whitePx, rpx - 21f, rpy + 69f, 42f * (rp.hp / max(1f, rp.maxHp)).coerceIn(0f, 1f), 4f)
                spriteBatch.color = Color.WHITE
            }

            // Main Hero on playerDiag
            if (d == playerDiag) {
                val hx = isoToScreenX(player.x, player.y); val hy = isoToScreenY(player.x, player.y, player.z)
                val held = player.slots[player.selectedSlot]
                drawAnimatedHero(
                    hx, hy, player.skinId, player.facingRight, player.walkAnim, player.isMoving,
                    held?.id ?: 0, player.attackAnim, player.attackAngleDeg,
                    player.isSleeping, player.hurtFlash, player.hurtSquash,
                    armorHelmet = player.armor[0], armorChest = player.armor[1], hasShieldOffhand = player.offhand?.id == 132
                )
                if (player.parryTimer > 0f) {
                    spriteBatch.setColor(1f, 0.88f, 0.35f, 0.58f)
                    spriteBatch.draw(SpriteArt.glowTex, hx - 48f, hy - 14f, 96f, 96f)
                    spriteBatch.color = Color.WHITE
                }
                val mDef = held?.let { BlockRegistry.items[it.id] }
                val oDef = player.offhand?.let { BlockRegistry.items[it.id] }
                val lRad = max(mDef?.lightRadius ?: 0f, oDef?.lightRadius ?: 0f)
                if (lRad > 0f) {
                    handLightX = hx; handLightY = hy + 24f; handLightRad = lRad * 1.15f
                    handLightCol = if ((mDef?.lightRadius ?: 0f) > 0f) mDef!!.lightColor else oDef!!.lightColor
                }
            }
        }

        // Дуги ударов оружием, Снаряды и Частицы
        for (sl in entities.slashes) {
            val sx = isoToScreenX(sl.x, sl.y); val sy = isoToScreenY(sl.x, sl.y, sl.z)
            val a = (sl.life / 0.22f).coerceIn(0f, 1f)
            spriteBatch.setColor(((sl.colorHex shr 16) and 0xFF) / 255f, ((sl.colorHex shr 8) and 0xFF) / 255f, (sl.colorHex and 0xFF) / 255f, a * 0.9f)
            val sc = 0.85f + (1f - a) * 0.45f
            spriteBatch.draw(SpriteArt.slashTex, sx - 40f, sy - 40f, 40f, 40f, 80f, 80f, sc, sc, sl.angleDeg)
        }
        for (pr in entities.projectiles) {
            val px = isoToScreenX(pr.x, pr.y); val py = isoToScreenY(pr.x, pr.y, pr.z)
            val c = if (pr.isGrapple) 0x38BDF8 else if (pr.frosty) 0x76ECFF else if (pr.fiery) 0xFF7718 else 0xFFE888
            spriteBatch.setColor(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f, 1f)
            val sz = if (pr.fiery || pr.frosty) 16f else 10f
            spriteBatch.draw(SpriteArt.whitePx, px - sz * 0.5f, py - sz * 0.5f, sz, sz)
        }
        for (pt in entities.particles) {
            val px = isoToScreenX(pt.x, pt.y); val py = isoToScreenY(pt.x, pt.y, pt.z)
            spriteBatch.setColor(((pt.colorHex shr 16) and 0xFF) / 255f, ((pt.colorHex shr 8) and 0xFF) / 255f, (pt.colorHex and 0xFF) / 255f, min(1f, pt.life * 2.2f))
            spriteBatch.draw(SpriteArt.whitePx, px, py, pt.size, pt.size)
        }
        spriteBatch.color = Color.WHITE

        // Подсветка добываемого блока + прогресс бар
        targetPick?.let { sel ->
            if (screen == UiScreen.PLAYING && !player.isSleeping && holdingMine) {
                val sx = isoToScreenX(sel.gx.toFloat(), sel.gy.toFloat())
                val syTop = isoToScreenY(sel.gx.toFloat(), sel.gy.toFloat(), (sel.gz + 1).toFloat())
                drawIsoQuad(SpriteArt.whitePx, sx - TW, syTop, sx, syTop + TH, sx + TW, syTop, sx, syTop - TH, Color.toFloatBits(1f, 0.86f, 0.30f, 0.30f), 0f, 0f, 1f, 1f)
                if (mineProgress > 0.02f) {
                    spriteBatch.setColor(0.08f, 0.06f, 0.12f, 0.88f); spriteBatch.draw(SpriteArt.whitePx, sx - 26f, syTop + 18f, 52f, 9f)
                    spriteBatch.setColor(0.28f, 0.94f, 0.52f, 1f); spriteBatch.draw(SpriteArt.whitePx, sx - 24f, syTop + 20f, 48f * mineProgress.coerceIn(0f, 1f), 5f)
                    spriteBatch.color = Color.WHITE
                }
            }
        }

        // Динамическое освещение
        val darkness = when {
            isCutawayActive && player.z < WorldIso.SEA_Z - 2 -> 0.62f
            isCutawayActive -> 0.36f
            world.dimension == "nether" -> 0.25f
            world.dimension == "aether" -> 0.04f
            else -> ((1f - entities.dayFactor) * 0.64f + (if (entities.weather == WeatherType.STORM) 0.24f else if (entities.weather == WeatherType.RAIN || entities.weather == WeatherType.SNOW) 0.14f else 0f) - entities.lightningFlash * 0.7f).coerceIn(0f, 0.74f)
        }
        if (darkness > 0.02f) {
            val tr = if (world.dimension == "nether") 0.24f else if (entities.isBloodMoon) 0.34f else 0.04f
            val tg = if (world.dimension == "nether") 0.04f else 0.05f
            val tb = if (world.dimension == "nether") 0.06f else 0.16f
            spriteBatch.setColor(tr, tg, tb, darkness)
            spriteBatch.draw(SpriteArt.whitePx, camWorld.position.x - halfW, camWorld.position.y - halfH, halfW * 2f, halfH * 2f)
        }
        if (lightCount > 0 || handLightRad > 0f || oreCount > 0) {
            spriteBatch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
            val pulse = 0.94f + sin(elapsed * 9f) * 0.06f
            val alpha = if (darkness > 0.14f) 0.58f else 0.22f
            for (i in 0 until lightCount) {
                val li = i * 4
                val lx = lightsArr[li]; val ly = lightsArr[li + 1]; val rad = lightsArr[li + 2] * pulse; val hex = lightsArr[li + 3].toInt()
                spriteBatch.setColor(((hex shr 16) and 0xFF) / 255f, ((hex shr 8) and 0xFF) / 255f, (hex and 0xFF) / 255f, alpha)
                spriteBatch.draw(SpriteArt.glowTex, lx - rad * 0.5f, ly - rad * 0.5f, rad, rad)
            }
            if (handLightRad > 0f) {
                val rad = handLightRad * pulse
                spriteBatch.setColor(((handLightCol shr 16) and 0xFF) / 255f, ((handLightCol shr 8) and 0xFF) / 255f, (handLightCol and 0xFF) / 255f, alpha)
                spriteBatch.draw(SpriteArt.glowTex, handLightX - rad * 0.5f, handLightY - rad * 0.5f, rad, rad)
            }
            for (i in 0 until oreCount) {
                val oi = i * 3
                val ox = oresArr[oi]; val oy = oresArr[oi + 1]; val oHex = oresArr[oi + 2].toInt()
                val r = 46f * pulse
                spriteBatch.setColor(((oHex shr 16) and 0xFF) / 255f, ((oHex shr 8) and 0xFF) / 255f, (oHex and 0xFF) / 255f, 0.75f)
                spriteBatch.draw(SpriteArt.glowTex, ox - r * 0.5f, oy - r * 0.5f, r, r)
            }
            spriteBatch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        }

        for (ft in entities.floatingTexts) {
            val fx = isoToScreenX(ft.x, ft.y); val fy = isoToScreenY(ft.x, ft.y, ft.z)
            fontSmall.setColor(ft.color.r, ft.color.g, ft.color.b, min(1f, ft.life * 1.4f))
            fontSmall.draw(spriteBatch, ft.text, fx - 14f, fy + 28f)
        }
        fontSmall.color = Color.WHITE
        spriteBatch.color = Color.WHITE
        spriteBatch.end()
    }

    private fun drawAnimatedHero(
        px: Float, py: Float, skinId: String, facingRight: Boolean, walkAnim: Float, moving: Boolean,
        heldId: Int, attackAnim: Float, attackAngle: Float, isSleeping: Boolean, hurtFlash: Float, hurtSquash: Float,
        armorHelmet: Int = 0, armorChest: Int = 0, hasShieldOffhand: Boolean = false
    ) {
        val frames = SpriteArt.skinSprites[skinId] ?: SpriteArt.skinSprites["fox_officer"]!!
        val w = 64f; val h = 80f

        if (isSleeping) {
            // СПЯЩИЙ ГЕРОЙ ПОД ОДЕЯЛОМ С ПЛАВНЫМ ДЫХАНИЕМ!
            val breath = sin(elapsed * 2.2f) * 0.04f
            spriteBatch.color = Color.WHITE
            spriteBatch.draw(frames[3], px - w * 0.5f, py - 10f, w * 0.5f, 16f, w, h, 1f + breath * 0.4f, 1f - breath * 0.5f, 0f)
            return
        }

        // 4-шаговый цикл ходьбы (0 -> 1 -> 0 -> 2) + живое подпрыгивание и наклон!
        val frameIdx = if (!moving) 0 else when ((walkAnim.toInt() % 4 + 4) % 4) { 1 -> 1; 3 -> 2; else -> 0 }
        val hopY = if (moving) abs(sin(walkAnim * 1.5f)) * 7.5f else 0f
        val strideTilt = if (moving) sin(walkAnim * 1.5f) * 6.5f else 0f
        val breathY = sin(elapsed * 3.4f) * 0.035f
        val lunge = sin(attackAnim * PI.toFloat())
        val side = if (facingRight) 1f else -1f

        val scaleX = side * (1f - breathY * 0.6f + hurtSquash * 0.24f)
        val scaleY = 1f + breathY - hurtSquash * 0.20f + (if (moving) abs(sin(walkAnim * 1.5f)) * 0.05f else 0f)
        val rot = strideTilt - side * lunge * 11f

        // Тень слегка уменьшается в прыжке шага
        val shSc = 1f - (hopY / 42f)
        spriteBatch.setColor(1f, 1f, 1f, 0.74f)
        spriteBatch.draw(SpriteArt.shadowTex, px - 22f * shSc, py - 10f, 44f * shSc, 18f * shSc)

        if (hurtFlash > 0f) spriteBatch.setColor(1f, 0.40f, 0.40f, 1f) else spriteBatch.color = Color.WHITE
        spriteBatch.draw(frames[frameIdx], px - w * 0.5f + side * lunge * 6f, py - 12f + hopY, w * 0.5f, 12f, w, h, scaleX, scaleY, rot)
        spriteBatch.color = Color.WHITE

        // Видимая Броня и Щит прямо на герое!
        if (armorChest > 0) {
            val cCol = when (armorChest) { 149 -> 0xE82648; 148 -> 0xA86E3E; else -> 0xDCE4F0 }
            spriteBatch.setColor(((cCol shr 16) and 0xFF) / 255f, ((cCol shr 8) and 0xFF) / 255f, (cCol and 0xFF) / 255f, 0.90f)
            spriteBatch.draw(SpriteArt.whitePx, px - 10f, py + 14f + hopY, 20f, 14f)
            if (armorChest == 148) { // Белая меховая оторочка на шубе!
                spriteBatch.setColor(0.97f, 0.96f, 0.94f, 0.95f)
                spriteBatch.draw(SpriteArt.whitePx, px - 10f, py + 24f + hopY, 20f, 4f)
            }
            spriteBatch.color = Color.WHITE
        }
        if (armorHelmet > 0) {
            spriteBatch.setColor(0.68f, 0.42f, 0.24f, 0.88f)
            spriteBatch.draw(SpriteArt.whitePx, px - 12f, py + 47f + hopY, 24f, 6f)
            spriteBatch.color = Color.WHITE
        }
        if (hasShieldOffhand) {
            BlockRegistry.getIconRegion(132)?.let { shReg ->
                spriteBatch.draw(shReg, px - side * 16f - 11f, py + 12f + hopY, 22f, 22f)
            }
        }
        // Вращающееся оружие / инструмент в руке с настоящим замахом!
        if (heldId > 0) {
            BlockRegistry.getIconRegion(heldId)?.let { itemReg ->
                val swingRot = if (attackAnim > 0.01f) side * (-45f + (1f - attackAnim) * 130f) else strideTilt * 1.6f
                val offX = side * (17f + lunge * 10f)
                val offY = 16f + hopY + lunge * 6f
                spriteBatch.draw(itemReg, px + offX - 15f, py + offY - 8f, 15f, 8f, 30f, 30f, side, 1f, -swingRot)
            }
        }
    }

    private fun drawAnimatedMob(m: MobEntity) {
        val mx = isoToScreenX(m.x, m.y); val my = isoToScreenY(m.x, m.y, m.z)
        val mSz = if (m.isBoss) 96f else 64f
        val moveSpd = hypot(m.vx.toDouble(), m.vy.toDouble()).toFloat()
        val moving = moveSpd > 0.15f
        val mFrame = if (m.type == "mimic" || m.type in setOf("bee", "bat", "blaze", "sky_guardian")) ((elapsed * 7f).toInt() and 1)
                     else if (moving) (m.walkAnim.toInt() and 1) else 0
        val reg = SpriteArt.mobSprites[m.type]?.get(mFrame) ?: SpriteArt.mobSprites["sheep"]!![0]

        val isSlime = m.type in setOf("slime", "mini_slime", "magma_slime")
        val isFlyer = m.type in setOf("bat", "bee", "ghast", "blaze", "sky_guardian")
        val breath = sin(elapsed * 3.0f + m.idlePhase) * 0.045f
        val side = if (m.facingRight) 1f else -1f

        var hopY = 0f
        var scX = 1f - breath * 0.5f
        var scY = 1f + breath
        var rot = 0f

        when {
            isSlime -> {
                val h = max(0f, sin(m.hopPhase))
                hopY = h * 18f
                scY = 0.82f + h * 0.36f
                scX = 1.18f - h * 0.28f
            }
            isFlyer -> {
                hopY = 14f + sin(elapsed * 4.8f + m.idlePhase) * 8f
                rot = cos(elapsed * 3.4f + m.idlePhase) * 7f
            }
            m.type == "mimic" -> {
                hopY = abs(sin(elapsed * 7f)) * 9f
                rot = sin(elapsed * 7f) * 8f
            }
            moving -> {
                hopY = abs(sin(m.walkAnim * 1.4f)) * 6.5f
                rot = sin(m.walkAnim * 1.4f) * 6f
            }
        }

        // Выпад при атаке и сжатие при получении урона!
        val atkLunge = sin(m.attackAnim * PI.toFloat())
        scX += m.hurtSquash * 0.25f + (if (m.fuse > 0f) sin(m.fuse * 26f) * 0.16f else 0f)
        scY -= m.hurtSquash * 0.22f
        rot -= side * atkLunge * 14f

        val shSc = (1f - hopY / 50f).coerceIn(0.45f, 1.1f)
        spriteBatch.setColor(1f, 1f, 1f, 0.70f)
        spriteBatch.draw(SpriteArt.shadowTex, mx - mSz * 0.36f * shSc, my - 10f, mSz * 0.72f * shSc, 20f * shSc)

        when {
            m.hurtFlash > 0f -> spriteBatch.setColor(1f, 0.35f, 0.35f, 1f)
            m.freezeTimer > 0f -> spriteBatch.setColor(0.55f, 0.90f, 1f, 1f)
            m.stunTimer > 0f -> spriteBatch.setColor(1f, 0.92f, 0.45f, 1f)
            m.fuse > 0f -> spriteBatch.setColor(1f, 1f, 0.45f, 1f)
            else -> spriteBatch.color = Color.WHITE
        }
        spriteBatch.draw(reg, mx - mSz * 0.5f + side * atkLunge * 7f, my - 12f + hopY, mSz * 0.5f, 12f, mSz, mSz, side * scX, scY, rot)
        spriteBatch.color = Color.WHITE

        if (m.hp < m.maxHp) {
            val bw = 42f; val bx = mx - bw * 0.5f; val by = my + mSz - 8f + hopY
            spriteBatch.setColor(0.1f, 0.05f, 0.1f, 0.85f); spriteBatch.draw(SpriteArt.whitePx, bx, by, bw, 7f)
            spriteBatch.setColor(if (m.tamed) 0.3f else 0.95f, if (m.tamed) 0.9f else 0.22f, 0.32f, 1f)
            spriteBatch.draw(SpriteArt.whitePx, bx + 1f, by + 1f, (bw - 2f) * (m.hp / m.maxHp).coerceIn(0f, 1f), 5f)
            spriteBatch.color = Color.WHITE
        }
    }

    // =========================================================================
    // ULTRA-CLEAN STICK + TAP HUD (0 КНОПОК СПРАВА СНИЗУ!)
    // =========================================================================
    private fun drawPanel(x: Float, y: Float, w: Float, h: Float, bg: Color = Color.WHITE, border: Color = Color.WHITE, badge: Boolean = false) {
        val patch = if (badge) SpriteArt.hudBadgePatch else SpriteArt.panelPatch
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        spriteBatch.color = Color.WHITE
        patch.draw(spriteBatch, x, y, w, h)
        spriteBatch.end()
    }

    private fun drawCenteredText(f: BitmapFont, text: String, x: Float, y: Float, w: Float, h: Float) {
        glyphLayout.setText(f, text)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        f.draw(spriteBatch, glyphLayout, x + (w - glyphLayout.width) / 2f, y + (h + glyphLayout.height) / 2f)
        spriteBatch.end()
    }

    private fun drawButton(x: Float, y: Float, w: Float, h: Float, label: String, green: Boolean = false, centered: Boolean = false, blue: Boolean = false, red: Boolean = false, onClick: () -> Unit) {
        val patch = when {
            green -> SpriteArt.btnGreenPatch
            blue -> SpriteArt.btnBluePatch
            red -> SpriteArt.btnRedPatch
            else -> SpriteArt.btnPurplePatch
        }
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        spriteBatch.color = Color.WHITE
        patch.draw(spriteBatch, x, y, w, h)
        if (label.isNotEmpty()) {
            glyphLayout.setText(fontSmall, label)
            val tx = if (centered) x + (w - glyphLayout.width) / 2f else x + 16f
            fontSmall.draw(spriteBatch, glyphLayout, tx, y + (h + glyphLayout.height) / 2f)
        }
        spriteBatch.end()
        buttons += Pair(Rectangle(x, y, w, h), onClick)
    }

    private fun drawCircleBtn(cx: Float, cy: Float, diameter: Float, label: String, blue: Boolean = false, onClick: () -> Unit) {
        val patch = if (blue) SpriteArt.btnBluePatch else SpriteArt.pauseBtnPatch
        val x = cx - diameter * 0.5f; val y = cy - diameter * 0.5f
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        spriteBatch.color = Color.WHITE
        patch.draw(spriteBatch, x, y, diameter, diameter)
        if (label.isNotEmpty()) {
            glyphLayout.setText(fontSmall, label)
            fontSmall.draw(spriteBatch, glyphLayout, x + (diameter - glyphLayout.width) / 2f, y + (diameter + glyphLayout.height) / 2f)
        }
        spriteBatch.end()
        buttons += Pair(Rectangle(x, y, diameter, diameter), onClick)
    }

    private fun drawSlotBox(x: Float, y: Float, sz: Float, item: SlotItem?, selected: Boolean = false) {
        val patch = if (selected) SpriteArt.slotSelectedPatch else SpriteArt.slotPatch
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        spriteBatch.color = Color.WHITE
        patch.draw(spriteBatch, x, y, sz, sz)
        if (item != null && item.id > 0) {
            val pad = sz * 0.14f
            BlockRegistry.getIconRegion(item.id)?.let { reg ->
                spriteBatch.draw(reg, x + pad, y + pad, sz - pad * 2f, sz - pad * 2f)
            }
            if (item.count > 1 || player.creative) {
                val txt = if (player.creative) "∞" else "${item.count}"
                glyphLayout.setText(fontSmall, txt)
                fontSmall.draw(spriteBatch, txt, x + sz - glyphLayout.width - 5f, y + glyphLayout.height + 4f)
            }
        }
        spriteBatch.end()
    }

    private fun renderWeatherOverlay(W: Float, H: Float) {
        if (world.dimension != "overworld" || screen != UiScreen.PLAYING || isCutawayActive) return
        val dt = min(0.05f, Gdx.graphics.deltaTime)
        val isStorm = entities.weather == WeatherType.STORM
        val isRain = entities.weather == WeatherType.RAIN || isStorm
        val isSnow = entities.weather == WeatherType.SNOW
        val isAmb = entities.weather == WeatherType.CLEAR && (entities.season == Season.SPRING || entities.season == Season.AUTUMN || (entities.season == Season.SUMMER && entities.dayFactor < 0.3f))
        if (!isRain && !isSnow && !isAmb) return

        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        for (p in weatherDrops) {
            val spd = if (isRain) (if (isStorm) 1.5f else 1.1f) else if (isSnow) 0.28f else 0.14f
            p[1] -= spd * p[2] * dt
            p[0] += (if (isRain) -0.18f else if (isSnow) -0.14f + sin(elapsed * 2.2f + p[2] * 9f) * 0.08f else sin(elapsed * 2f + p[2] * 10f) * 0.08f) * dt
            if (p[1] < 0f) { p[1] = 1.02f; p[0] = Random.nextFloat() }
            if (p[0] < 0f) p[0] = 1f; if (p[0] > 1f) p[0] = 0f
            val x = p[0] * W; val y = p[1] * H
            if (isRain) { spriteBatch.setColor(0.75f, 0.88f, 1f, 0.65f); spriteBatch.draw(SpriteArt.whitePx, x, y, 2.5f, 24f) }
            else if (isSnow) { spriteBatch.setColor(1f, 1f, 1f, 0.88f); spriteBatch.draw(SpriteArt.whitePx, x, y, 7f, 7f) }
            else {
                val c = entities.season.particleCol
                spriteBatch.setColor(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f, 0.85f)
                spriteBatch.draw(SpriteArt.whitePx, x, y, 8f, 6f)
            }
        }
        spriteBatch.color = Color.WHITE
        spriteBatch.end()
    }

    private fun render2DUI() {
        buttons.clear()
        val W = Gdx.graphics.width.toFloat(); val H = Gdx.graphics.height.toFloat()
        camUI.setToOrtho(false, W, H); camUI.update()

        if (screen == UiScreen.PLAYING) renderWeatherOverlay(W, H)

        when (screen) {
            UiScreen.MAIN_MENU -> renderMainMenu(W, H)
            UiScreen.WORLD_SLOTS -> renderWorldSlotsScreen(W, H)
            UiScreen.PROFILES_FRIENDS -> renderProfilesFriendsScreen(W, H)
            UiScreen.TRADER_QUESTS -> renderTraderQuestsScreen(W, H)
            UiScreen.BESTIARY_SKILLS -> renderBestiarySkillsScreen(W, H)
            UiScreen.FULL_MAP -> renderFullMapScreen(W, H)
            UiScreen.PLAYING -> renderGameplayHud(W, H)
            UiScreen.PAUSE -> { renderGameplayHud(W, H); renderPauseMenu(W, H) }
            UiScreen.INVENTORY -> renderInventoryScreen(W, H)
            UiScreen.WORKBENCH -> renderWorkbenchScreen(W, H)
            UiScreen.FURNACE -> renderFurnaceScreen(W, H)
            UiScreen.CHEST -> renderChestScreen(W, H)
            UiScreen.CAMPFIRE -> renderCampfireScreen(W, H)
            UiScreen.WAYSTONES -> renderWaystonesScreen(W, H)
            UiScreen.ANVIL -> renderAnvilScreen(W, H)
            UiScreen.SETTINGS_MODHUB -> renderSettingsAndModHub(W, H)
            UiScreen.WARDROBE -> renderWardrobeScreen(W, H)
            UiScreen.MULTIPLAYER -> renderMultiplayerScreen(W, H)
            UiScreen.DEATH -> {
                drawPanel(W * 0.30f, H * 0.34f, W * 0.40f, H * 0.32f, Color(0.32f, 0.05f, 0.08f, 0.94f), Color.RED)
                drawCenteredText(font, "ВЫ ПОГИБЛИ!", W * 0.30f, H * 0.50f, W * 0.40f, 44f)
                drawButton(W * 0.35f, H * 0.38f, W * 0.30f, 54f, "ВОЗРОДИТЬСЯ", green = true, centered = true) {
                    player.hp = player.maxHp; player.food = player.maxFood; player.bodyTemp = 75f; player.isSleeping = false
                    if (world.dimension != player.spawnDim) switchDimension(player.spawnDim)
                    player.x = player.spawnX; player.y = player.spawnY; player.z = player.spawnZ
                    screen = UiScreen.PLAYING
                }
            }
        }

        if (toastTimer > 0f && toastText.isNotEmpty()) {
            glyphLayout.setText(fontSmall, toastText)
            val tw = glyphLayout.width + 36f
            drawPanel((W - tw) / 2f, H - 106f, tw, 38f, Color(0.08f, 0.05f, 0.16f, 0.90f), Color.GOLD)
            drawCenteredText(fontSmall, toastText, (W - tw) / 2f, H - 106f, tw, 38f)
        }
    }

    private fun resolveFishingCatch() {
        fishingActive = false
        if (fishingBarPos in 0.36f..0.66f) {
            SoundFx.play("fish", 0.75f)
            val roll = Random.nextFloat()
            val catchId = when {
                roll < 0.14f -> 154 // Золотая Рыбка!
                roll < 0.24f -> 104 // Затонувший Алмаз!
                entities.season == Season.WINTER -> 153 // Ледяной Окунь!
                entities.season == Season.AUTUMN -> 152 // Лосось!
                else -> 151 // Карась!
            }
            player.addItem(catchId, 1)
            val lvlUp = profiles.addFarmXp(14)
            if (lvlUp) SoundFx.play("levelup", 0.7f)
            showToast("Улов: ${BlockRegistry.items[catchId]?.name}!" + if (lvlUp) " (Фермер ур.${profiles.active.farmLevel()}!)" else "")
        } else {
            SoundFx.play("splash", 0.5f)
            showToast("Рыба сорвалась! Попробуйте поймать в зелёной зоне.")
        }
    }

    private fun renderMainMenu(W: Float, H: Float) {
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin(); coverTex?.let { spriteBatch.draw(it, 0f, 0f, W, H) }; spriteBatch.end()

        // БАННЕР АВТООБНОВЛЕНИЯ С GITHUB (https://github.com/Pantapan1/Horon)
        if (PlatformBridge.updateAvailable) {
            val uw = min(W * 0.52f, 520f)
            val ux = W - uw - 18f; val uy = H - 78f
            drawButton(ux, uy, uw, 58f, "НОВАЯ ВЕРСИЯ ${PlatformBridge.updateTag}! НАЖМИТЕ ДЛЯ ОБНОВЛЕНИЯ", green = true, centered = true) {
                PlatformBridge.openBrowserUrl(PlatformBridge.updateUrl)
            }
        } else {
            glyphLayout.setText(fontSmall, PlatformBridge.updateStatusText)
            val sw = glyphLayout.width + 26f
            drawPanel(W - sw - 14f, H - 46f, sw, 34f, badge = true)
            drawCenteredText(fontSmall, PlatformBridge.updateStatusText, W - sw - 14f, H - 46f, sw, 34f)
        }

        val px = W * 0.03f; val py = H * 0.06f; val pw = W * 0.37f; val ph = H * 0.74f
        drawPanel(px, py, pw, ph)

        val pr = profiles.active
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        fontSmall.draw(spriteBatch, "Rust Engine v6.2 • 250 Слоёв", px + 16f, py + ph - 16f)
        fontSmall.draw(spriteBatch, "${pr.nick} (${pr.friendId}) • Ур.${pr.totalLevel()}", px + 16f, py + ph - 42f)
        spriteBatch.end()

        val bx = px + 16f; val bw = pw - 32f; val bh = (ph - 102f) / 5f - 8f
        var by = py + ph - 72f - bh
        drawButton(bx, by, bw, bh, "МОИ МИРЫ (ИГРАТЬ)", green = true, centered = true) { profiles.loadWorldSlots(); screen = UiScreen.WORLD_SLOTS }; by -= bh + 8f
        drawButton(bx, by, bw, bh, "ПРОФИЛЬ И ДРУЗЬЯ (${pr.friends.size})", blue = true, centered = true) { screen = UiScreen.PROFILES_FRIENDS }; by -= bh + 8f
        drawButton(bx, by, bw, bh, "ГАРДЕРОБ ГЕРОЕВ", centered = true) { screen = UiScreen.WARDROBE }; by -= bh + 8f
        drawButton(bx, by, bw, bh, "МУЛЬТИПЛЕЕР (ОНЛАЙН / LAN)", centered = true) { screen = UiScreen.MULTIPLAYER }; by -= bh + 8f
        drawButton(bx, by, bw, bh, "НАСТРОЙКИ И МОДЫ", centered = true) { settingsTab = 0; screen = UiScreen.SETTINGS_MODHUB }
    }

    private fun renderWorldSlotsScreen(W: Float, H: Float) {
        drawPanel(W * 0.08f, H * 0.07f, W * 0.84f, H * 0.86f)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin(); font.draw(spriteBatch, "МОИ МИРЫ (250 СЛОЁВ)", W * 0.11f, H * 0.88f); spriteBatch.end()
        drawButton(W * 0.78f, H * 0.82f, 118f, 44f, "Назад", centered = true) { screen = UiScreen.MAIN_MENU }

        drawButton(W * 0.11f, H * 0.72f, W * 0.37f, 48f, "+ НОВЫЙ МИР (ВЫЖИВАНИЕ)", green = true, centered = true) {
            Gdx.input.getTextInput(object : Input.TextInputListener {
                override fun input(text: String) {
                    val nm = text.trim().take(20).ifBlank { "Мир_${profiles.worldSlots.size + 1}" }
                    worldFileName = "world_${System.currentTimeMillis()}.vhworld"
                    startWorld(false, false, null, nm)
                }
                override fun canceled() {}
            }, "Название нового мира", "Мир_${profiles.worldSlots.size + 1}", "")
        }
        drawButton(W * 0.50f, H * 0.72f, W * 0.39f, 48f, "+ НОВЫЙ МИР (СВОЙ СИД / ТВОРЧЕСТВО)", blue = true, centered = true) {
            Gdx.input.getTextInput(object : Input.TextInputListener {
                override fun input(text: String) {
                    val sd = text.trim().toIntOrNull() ?: text.hashCode()
                    worldFileName = "world_${System.currentTimeMillis()}.vhworld"
                    startWorld(false, true, sd, "Сид $sd")
                }
                override fun canceled() {}
            }, "Введите Сид мира (число или слово)", "${Random.nextInt(10000, 99999)}", "")
        }

        var y = H * 0.58f
        if (profiles.worldSlots.isEmpty()) {
            drawButton(W * 0.11f, y, W * 0.78f, 54f, "БЫСТРЫЙ СТАРТ (Создать первый мир)", green = true, centered = true) {
                worldFileName = "world_default.vhworld"
                startWorld(false, false, null, "Основной мир")
            }
        } else {
            for (slot in profiles.worldSlots.take(5)) {
                val modeTxt = if (slot.creative) "Творч." else "Выжив."
                drawButton(W * 0.11f, y, W * 0.64f, 48f, "${slot.title} | $modeTxt | Сид:${slot.seed} | Сезон ${slot.seasonDays}д", green = true) {
                    worldFileName = slot.fileName; currentWorldTitle = slot.title
                    startWorld(true, slot.creative, slot.seed, slot.title)
                }
                drawButton(W * 0.76f, y, W * 0.13f, 48f, "Удалить", red = true, centered = true) {
                    profiles.deleteWorldSlot(slot.fileName); showToast("Мир удалён")
                }
                y -= 56f
            }
        }
    }

    private fun renderProfilesFriendsScreen(W: Float, H: Float) {
        drawPanel(W * 0.06f, H * 0.06f, W * 0.88f, H * 0.88f)
        val pr = profiles.active
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        font.draw(spriteBatch, "ПРОФИЛЬ И ДРУЗЬЯ ОНЛАЙН", W * 0.09f, H * 0.89f)
        fontSmall.draw(spriteBatch, "Ваш ID для друзей: ${pr.friendId} | Ник: ${pr.nick} | Уровень: ${pr.totalLevel()}", W * 0.09f, H * 0.82f)
        spriteBatch.end()
        drawButton(W * 0.80f, H * 0.84f, 118f, 44f, "Назад", centered = true) { screen = if (world.worldActive) UiScreen.PLAYING else UiScreen.MAIN_MENU }

        // Левая колонка: Профили на устройстве (хранятся постоянно!)
        drawButton(W * 0.09f, H * 0.70f, W * 0.36f, 44f, "+ Создать новый профиль", green = true, centered = true) {
            Gdx.input.getTextInput(object : Input.TextInputListener {
                override fun input(text: String) {
                    val np = profiles.createProfile(text)
                    player.skinId = np.skinId; netMp.localNick = np.nick
                    showToast("Создан профиль: ${np.nick} (${np.friendId})")
                }
                override fun canceled() {}
            }, "Имя нового профиля", "Герой", "")
        }
        drawButton(W * 0.09f, H * 0.62f, W * 0.36f, 42f, "Сменить ник профиля", blue = true, centered = true) {
            Gdx.input.getTextInput(object : Input.TextInputListener {
                override fun input(text: String) {
                    val nn = text.trim().replace("|", "").take(18)
                    if (nn.isNotEmpty()) { pr.nick = nn; netMp.localNick = nn; profiles.saveProfiles(); showToast("Ник изменён: $nn") }
                }
                override fun canceled() {}
            }, "Новый ник", pr.nick, "")
        }
        var py = H * 0.51f
        profiles.profiles.take(4).forEachIndexed { idx, p ->
            drawButton(W * 0.09f, py, W * 0.36f, 42f, "${if (idx == profiles.activeIndex) "[АКТ] " else ""}${p.nick} (${p.friendId})", green = idx == profiles.activeIndex) {
                profiles.activeIndex = idx; player.skinId = p.skinId; netMp.localNick = p.nick; profiles.saveProfiles()
                showToast("Активный профиль: ${p.nick}")
            }
            py -= 48f
        }

        // Правая колонка: Друзья онлайн (по Friend ID!)
        drawButton(W * 0.48f, H * 0.70f, W * 0.42f, 44f, "+ Добавить друга по ID (например VH-12345)", green = true, centered = true) {
            Gdx.input.getTextInput(object : Input.TextInputListener {
                override fun input(text: String) {
                    val ok = profiles.addFriend(text, text)
                    showToast(if (ok) "Друг $text добавлен!" else "Неверный или уже добавленный ID")
                }
                override fun canceled() {}
            }, "Введите Friend ID друга (VH-XXXXX)", "VH-", "")
        }
        var fy = H * 0.59f
        for (fr in pr.friends.take(5)) {
            val st = if (fr.online && fr.roomCode.isNotEmpty()) "ОНЛАЙН • Код ${fr.roomCode}" else if (fr.online) "ОНЛАЙН" else "Офлайн"
            drawButton(W * 0.48f, fy, W * 0.30f, 44f, "${fr.nick} ($st)", green = fr.online) {
                if (fr.roomCode.isNotEmpty()) {
                    netMp.selectedBrokerIdx = fr.brokerIdx
                    netMp.joinOnlineRoom(fr.roomCode)
                    showToast("Подключаемся к другу ${fr.nick}...")
                } else {
                    showToast("Друг ${fr.nick} сейчас не в комнате")
                }
            }
            drawButton(W * 0.79f, fy, W * 0.11f, 44f, "Удал.", red = true, centered = true) { profiles.removeFriend(fr.friendId) }
            fy -= 50f
        }
    }

    private fun renderTraderQuestsScreen(W: Float, H: Float) {
        drawPanel(W * 0.08f, H * 0.07f, W * 0.84f, H * 0.86f)
        val q = profiles.currentQuest()
        val have = player.countItem(q.targetItemId)
        val canTurnIn = have >= q.targetCount

        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        font.draw(spriteBatch, "ДЕРЕВЕНСКИЙ ТОРГОВЕЦ И ЗАДАНИЯ", W * 0.11f, H * 0.88f)
        fontSmall.draw(spriteBatch, "Ваши Изумруды: ${player.countItem(118)} | Золото: ${player.countItem(103)}", W * 0.11f, H * 0.81f)
        spriteBatch.end()
        drawButton(W * 0.78f, H * 0.83f, 118f, 44f, "Закрыть", centered = true) { screen = UiScreen.PLAYING }

        // Карточка активного задания
        drawButton(W * 0.11f, H * 0.69f, W * 0.78f, 52f, "ЗАДАНИЕ: ${q.title} ($have/${q.targetCount}) -> Награда: ${q.rewardEmeralds} Изумр. + ${q.rewardXp} XP", green = canTurnIn, centered = true) {
            if (canTurnIn && player.takeItem(q.targetItemId, q.targetCount)) {
                player.addItem(118, q.rewardEmeralds)
                profiles.active.miningXp += q.rewardXp / 2
                profiles.active.combatXp += q.rewardXp / 2
                profiles.active.activeQuestIdx++
                profiles.saveProfiles()
                SoundFx.play("levelup", 0.8f)
                showToast("Задание выполнено! +${q.rewardEmeralds} Изумрудов!")
            } else showToast("Соберите ещё: ${q.title}")
        }

        // Товары Торговца
        val trades = listOf(
            Triple(150, 1, Pair(118, 2)), // Удочка за 2 Изумруда
            Triple(72,  2, Pair(118, 3)), // 2 Шахтных Лифта за 3 Изумруда
            Triple(74,  1, Pair(118, 4)), // Авто-арбалет за 4 Изумруда
            Triple(73,  4, Pair(103, 2)), // 4 Шипы-ловушки за 2 Золота
            Triple(141, 3, Pair(103, 2)), // 3 Аметиста за 2 Золота
            Triple(142, 2, Pair(118, 3))  // 2 Рубина за 3 Изумруда
        )
        val colW = W * 0.38f
        trades.forEachIndexed { i, (outId, outCnt, cost) ->
            val canBuy = player.creative || player.countItem(cost.first) >= cost.second
            val bx = W * 0.11f + (i % 2) * (colW + 16f)
            val by = H * 0.54f - (i / 2) * 58f
            val costName = BlockRegistry.items[cost.first]?.name ?: ""
            drawButton(bx, by, colW, 48f, "${BlockRegistry.items[outId]?.name} x$outCnt — ${cost.second} $costName", green = canBuy) {
                if (canBuy && player.takeItem(cost.first, cost.second)) {
                    player.addItem(outId, outCnt); SoundFx.play("craft", 0.65f); showToast("Куплено!")
                }
            }
        }
    }

    private fun renderBestiarySkillsScreen(W: Float, H: Float) {
        drawPanel(W * 0.08f, H * 0.07f, W * 0.84f, H * 0.86f)
        val pr = profiles.active
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        font.draw(spriteBatch, "НАВЫКИ И БЕСТИАРИЙ", W * 0.11f, H * 0.88f)
        fontSmall.draw(spriteBatch, "Шахтёр ур.${pr.miningLevel()} (Шанс x2 руды: ${pr.miningLevel()*4}%)  |  Бой ур.${pr.combatLevel()} (Крит: ${pr.combatLevel()*3}%)  |  Фермер ур.${pr.farmLevel()}", W * 0.11f, H * 0.79f)
        fontSmall.draw(spriteBatch, "Открытые и побеждённые существа (${pr.bestiaryKills.size}):", W * 0.11f, H * 0.71f)
        var y = H * 0.63f
        val entries = pr.bestiaryKills.entries.sortedByDescending { it.value }.take(12)
        if (entries.isEmpty()) {
            fontSmall.draw(spriteBatch, "Пока пусто — сразитесь с мобами или боссами!", W * 0.11f, y)
        } else {
            entries.forEachIndexed { idx, (k, v) ->
                val xCol = W * 0.11f + (idx % 2) * (W * 0.39f)
                val yRow = y - (idx / 2) * 36f
                fontSmall.draw(spriteBatch, "• $k — побеждено: $v", xCol, yRow)
            }
        }
        spriteBatch.end()
        drawButton(W * 0.78f, H * 0.83f, 118f, 44f, "Закрыть", centered = true) { screen = UiScreen.PLAYING }
    }

    private fun renderFullMapScreen(W: Float, H: Float) {
        drawPanel(W * 0.12f, H * 0.06f, W * 0.76f, H * 0.88f)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin(); font.draw(spriteBatch, "КАРТА МИРА (X:${player.x.toInt()} Y:${player.y.toInt()})", W * 0.15f, H * 0.89f); spriteBatch.end()
        drawButton(W * 0.75f, H * 0.84f, 118f, 44f, "Закрыть", centered = true) { screen = UiScreen.PLAYING }

        val mapSz = min(W * 0.44f, H * 0.66f)
        val mx = W * 0.16f; val my = H * 0.14f
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        spriteBatch.color = Color.WHITE
        spriteBatch.draw(minimapTex, mx, my, mapSz, mapSz)
        spriteBatch.setColor(1f, 0.92f, 0.25f, 1f)
        spriteBatch.draw(SpriteArt.whitePx, mx + mapSz * 0.5f - 5f, my + mapSz * 0.5f - 5f, 10f, 10f)
        spriteBatch.color = Color.WHITE
        spriteBatch.end()

        val rx = mx + mapSz + 22f; val rw = W * 0.85f - rx
        drawButton(rx, H * 0.64f, rw, 50f, "Поставить метку здесь", green = true, centered = true) {
            customMarkerX = floor(player.x).toInt(); customMarkerY = floor(player.y).toInt()
            showToast("Метка поставлена: X:$customMarkerX Y:$customMarkerY")
        }
        drawButton(rx, H * 0.50f, rw, 50f, "Убрать метку", red = true, centered = true) {
            customMarkerX = null; customMarkerY = null; showToast("Метка убрана")
        }
        drawButton(rx, H * 0.36f, rw, 50f, "Бестиарий и Навыки", blue = true, centered = true) { screen = UiScreen.BESTIARY_SKILLS }
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        fontSmall.draw(spriteBatch, "Жёлтый — Вы", rx, H * 0.28f)
        fontSmall.draw(spriteBatch, "Синий — Друзья", rx, H * 0.24f)
        fontSmall.draw(spriteBatch, "Зелёный — Торговец", rx, H * 0.20f)
        fontSmall.draw(spriteBatch, "Красный — Босс", rx, H * 0.16f)
        spriteBatch.end()
    }

    private fun renderGameplayHud(W: Float, H: Float) {
        // 1. Верхняя левая пиксельная плашка статуса
        val placeTag = when {
            world.dimension == "nether" -> "АД"
            world.dimension == "aether" -> "ЭФИР"
            isCutawayActive && player.z < WorldIso.SEA_Z - 2 -> "Шахта"
            isCutawayActive -> "Под крышей"
            else -> "Мир"
        }
        val tempC = (entities.season.ambientTemp + (player.bodyTemp - 65f) * 0.35f).toInt()
        val hours = ((entities.worldTime * 24f + 6f) % 24f).toInt()
        val mins = (((entities.worldTime * 24f + 6f) % 1f) * 60f).toInt()
        val clock = "%02d:%02d".format(hours, mins)
        val roomTag = if (netMp.isConnected && netMp.roomCode.isNotEmpty()) " | Код:${netMp.roomCode}" else ""
        val fps = Gdx.graphics.framesPerSecond
        val infoTxt = "${fps} FPS (Rust) | Z:${player.z.toInt()}/250 | $placeTag | Дн.${entities.seasonDay()}/${entities.seasonLengthDays} ${entities.season.ruName} $clock ${tempC}°C$roomTag"
        glyphLayout.setText(fontSmall, infoTxt)
        val topH = max(38f, H * 0.058f)
        drawPanel(12f, H - topH - 10f, glyphLayout.width + 28f, topH, badge = true)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        fontSmall.draw(spriteBatch, glyphLayout, 26f, H - 10f - (topH - glyphLayout.height) / 2f)
        var bY = H - topH - 28f
        if (player.warmBuffTimer > 0f) { fontSmall.draw(spriteBatch, "Чай (Тепло): ${player.warmBuffTimer.toInt()}с", 16f, bY); bY -= 22f }
        if (player.minerVisionTimer > 0f) { fontSmall.draw(spriteBatch, "Отвар Шахтёра: ${player.minerVisionTimer.toInt()}с", 16f, bY); bY -= 22f }
        if (player.speedBuffTimer > 0f) { fontSmall.draw(spriteBatch, "Эликсир Скорости: ${player.speedBuffTimer.toInt()}с", 16f, bY); bY -= 22f }
        for ((cLine, _) in chatLog) { fontSmall.draw(spriteBatch, cLine, 16f, bY); bY -= 22f }
        spriteBatch.end()

        // 2. Полоса Босса
        entities.getActiveBoss()?.let { boss ->
            val bw = W * 0.36f; val bx = (W - bw) / 2f
            drawPanel(bx, H - 72f, bw, 48f)
            spriteBatch.projectionMatrix = camUI.combined
            spriteBatch.begin()
            spriteBatch.setColor(0.85f, 0.20f, 0.95f, 1f)
            spriteBatch.draw(SpriteArt.whitePx, bx + 10f, H - 62f, (bw - 20f) * (boss.hp / boss.maxHp).coerceIn(0f, 1f), 10f)
            spriteBatch.color = Color.WHITE
            spriteBatch.end()
            drawCenteredText(fontSmall, "${boss.name} (${boss.hp.toInt()}/${boss.maxHp.toInt()})", bx, H - 50f, bw, 24f)
        }

        // 3. Пиксельная кнопка Паузы (и СЛОЙ / ЧАТ только когда нужны)
        val cD = max(52f, H * 0.090f); val cY = H - cD * 0.5f - 10f
        drawCircleBtn(W - cD * 0.5f - 10f, cY, cD, "||") { screen = UiScreen.PAUSE }

        // Пиксельная МИНИ-КАРТА (тап открывает большую карту с метками!)
        val mmSz = max(84f, H * 0.165f)
        val mmX = W - cD - mmSz - 20f; val mmY = H - mmSz - 10f
        minimapRect.set(mmX, mmY, mmSz, mmSz)
        drawPanel(mmX - 4f, mmY - 4f, mmSz + 8f, mmSz + 8f, badge = true)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        spriteBatch.color = Color.WHITE
        spriteBatch.draw(minimapTex, mmX, mmY, mmSz, mmSz)
        // Игрок в центре мини-карты (жёлтая точка)
        spriteBatch.setColor(1f, 0.92f, 0.25f, 1f)
        spriteBatch.draw(SpriteArt.whitePx, mmX + mmSz * 0.5f - 3f, mmY + mmSz * 0.5f - 3f, 6f, 6f)
        // Друзья (синие), Боссы (красные), Торговец (зелёные), Камни Телепорта (голубые)
        val pCx = floor(player.x).toInt(); val pCy = floor(player.y).toInt()
        fun drawDot(wx: Float, wy: Float, r: Float, g: Float, b: Float) {
            val nx = ((wx - pCx) / (MINIMAP_SZ * 2f) + 0.5f)
            val ny = ((wy - pCy) / (MINIMAP_SZ * 2f) + 0.5f)
            if (nx in 0.05f..0.95f && ny in 0.05f..0.95f) {
                spriteBatch.setColor(r, g, b, 1f)
                spriteBatch.draw(SpriteArt.whitePx, mmX + nx * mmSz - 2.5f, mmY + (1f - ny) * mmSz - 2.5f, 5f, 5f)
            }
        }
        for (rp in netMp.remotePlayers.values) drawDot(rp.x, rp.y, 0.35f, 0.85f, 1f)
        for (m in entities.mobs) {
            if (m.isBoss) drawDot(m.x, m.y, 1f, 0.18f, 0.25f)
            else if (m.type == "trader") drawDot(m.x, m.y, 0.28f, 0.96f, 0.45f)
        }
        for (ws in world.waystones.values) if (ws.dim == world.dimension) drawDot(ws.x.toFloat(), ws.y.toFloat(), 0.4f, 0.95f, 1f)
        if (customMarkerX != null && customMarkerY != null) drawDot(customMarkerX!!.toFloat(), customMarkerY!!.toFloat(), 1f, 0.5f, 0.1f)
        spriteBatch.color = Color.WHITE
        spriteBatch.end()
        buttons += Pair(Rectangle(mmX, mmY, mmSz, mmSz)) { screen = UiScreen.FULL_MAP }

        // Мини-игра РЫБАЛКА (когда закинута удочка!)
        if (fishingActive) {
            val fw = min(W * 0.46f, 360f); val fh = 42f
            val fx = (W - fw) * 0.5f; val fy = H * 0.30f
            fishingBarRect.set(fx, fy, fw, fh)
            drawPanel(fx - 6f, fy - 6f, fw + 12f, fh + 12f)
            spriteBatch.projectionMatrix = camUI.combined
            spriteBatch.begin()
            spriteBatch.setColor(0.16f, 0.30f, 0.48f, 1f); spriteBatch.draw(SpriteArt.whitePx, fx, fy, fw, fh)
            // Зелёная зона поклёвки (0.38..0.64)
            spriteBatch.setColor(0.22f, 0.82f, 0.40f, 1f); spriteBatch.draw(SpriteArt.whitePx, fx + fw * 0.38f, fy + 4f, fw * 0.26f, fh - 8f)
            // Бегающий поплавок
            spriteBatch.setColor(1f, 0.92f, 0.24f, 1f); spriteBatch.draw(SpriteArt.whitePx, fx + fw * fishingBarPos - 6f, fy - 2f, 12f, fh + 4f)
            spriteBatch.color = Color.WHITE
            spriteBatch.end()
            drawButton(fx, fy - 52f, fw, 42f, "ПОДСЕЧЬ! (Тап в зелёной зоне)", green = true, centered = true) { resolveFishingCatch() }
        }
        if (netMp.isConnected) {
            drawCircleBtn(W - cD * 0.5f - 10f, cY - cD - 10f, cD * 0.88f, "ЧАТ", blue = true) {
                Gdx.input.getTextInput(object : Input.TextInputListener {
                    override fun input(text: String) {
                        val m = text.trim().take(60)
                        if (m.isNotEmpty()) { addChatMessage("${netMp.localNick}: $m"); netMp.broadcastChat(netMp.localNick, m) }
                    }
                    override fun canceled() {}
                }, "Сообщение в чат", "", "")
            }
        }
        if (isCutawayActive || manualLayerOffset != 0) {
            val sliceLbl = if (manualLayerOffset == 0) "СЛОЙ" else (if (manualLayerOffset > 0) "+$manualLayerOffset" else "$manualLayerOffset")
            drawCircleBtn(mmX - cD * 0.5f - 14f, cY, cD, sliceLbl, blue = true) {
                manualLayerOffset = when (manualLayerOffset) { 0 -> 1; 1 -> 2; 2 -> -1; else -> 0 }
                showToast(if (manualLayerOffset == 0) "Срез: Авто" else "Сдвиг среза: $manualLayerOffset")
            }
        }

        // 4. Пиксельный плавающий джойстик слева
        joyRadius = max(64f, H * 0.12f)
        joyBaseX = joyRadius + 24f; joyBaseY = joyRadius + 24f
        if (joyPtr == -1) { joyCenterX = joyBaseX; joyCenterY = joyBaseY }
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        spriteBatch.setColor(1f, 1f, 1f, if (joyPtr != -1) 0.95f else 0.58f)
        spriteBatch.draw(SpriteArt.joyRingReg, joyCenterX - joyRadius, joyCenterY - joyRadius, joyRadius * 2f, joyRadius * 2f)
        val knobR = joyRadius * 0.36f
        spriteBatch.draw(SpriteArt.joyKnobReg, joyCenterX + stickDx * (joyRadius * 0.54f) - knobR, joyCenterY + stickDy * (joyRadius * 0.54f) - knobR, knobR * 2f, knobR * 2f)
        spriteBatch.color = Color.WHITE
        spriteBatch.end()

        // 5. Плашка ускоренного сна
        if (player.isSleeping) {
            val sw = min(W * 0.72f, 640f)
            val sx = (W - sw) / 2f; val sy = H * 0.24f
            drawPanel(sx, sy, sw, 48f, badge = true)
            drawCenteredText(fontSmall, "Сон ($clock • x2.6) — сдвиньте стик или тапните, чтобы встать", sx, sy, sw, 48f)
        }

        // 6. Хотбар с пиксельной рамкой + HP / Броня / Еда
        val slotSz = min(H * 0.095f, W * 0.54f / 10.5f).coerceIn(46f, 68f)
        val hbW = slotSz * 10f + 20f; val hbX = (W - hbW) / 2f; val hbY = 12f
        val ohX = hbX - slotSz - 10f; val ohY = hbY + 6f
        offhandSlotRect.set(ohX, ohY, slotSz - 4f, slotSz - 4f)
        drawSlotBox(ohX, ohY, slotSz - 4f, player.offhand, selected = false)

        val barsTopY = hbY + slotSz + 36f
        if (!player.creative) {
            val barY = hbY + slotSz + 12f; val barH = 18f; val halfW = hbW * 0.44f
            spriteBatch.projectionMatrix = camUI.combined
            spriteBatch.begin()
            SpriteArt.barFramePatch.draw(spriteBatch, hbX, barY, halfW, barH)
            spriteBatch.setColor(0.92f, 0.22f, 0.26f, 1f)
            spriteBatch.draw(SpriteArt.whitePx, hbX + 4f, barY + 4f, (halfW - 8f) * (player.hp / player.maxHp).coerceIn(0f, 1f), barH - 8f)
            spriteBatch.color = Color.WHITE
            val fdX = hbX + hbW - halfW
            SpriteArt.barFramePatch.draw(spriteBatch, fdX, barY, halfW, barH)
            spriteBatch.setColor(0.95f, 0.60f, 0.14f, 1f)
            spriteBatch.draw(SpriteArt.whitePx, fdX + 4f, barY + 4f, (halfW - 8f) * (player.food / player.maxFood).coerceIn(0f, 1f), barH - 8f)
            spriteBatch.color = Color.WHITE
            spriteBatch.end()
            drawCenteredText(fontSmall, "HP ${player.hp.toInt()}/${player.maxHp.toInt()}", hbX, barY + barH + 2f, halfW, 18f)
            drawCenteredText(fontSmall, "Бр.${player.getTotalArmor()}", hbX + halfW, barY + barH + 2f, hbW - halfW * 2f, 18f)
            drawCenteredText(fontSmall, "Еда ${player.food.toInt()}/20", fdX, barY + barH + 2f, halfW, 18f)
        }

        // 7. УМНАЯ 1-ТАП КНОПКА ВЗАИМОДЕЙСТВИЯ (появляется ТОЛЬКО когда рядом Сундук / Печь / Кровать / Верстак!)
        if (!player.isSleeping) {
            val nearInter = findNearestInteractive(player.x, player.y, floor(player.z).toInt(), 2.75f)
            if (nearInter != null) {
                val lbl = interactiveLabel(nearInter.id)
                glyphLayout.setText(fontSmall, lbl)
                val pw = max(168f, glyphLayout.width + 38f)
                drawButton((W - pw) * 0.5f, barsTopY + 12f, pw, 42f, lbl, green = true, centered = true) {
                    performInteractOrPlace(nearInter)
                }
            }
            // Компактная кнопка «КОПАТЬ ВНИЗ» появляется ТОЛЬКО когда в руках Кирка — 1 тап роет ступеньку вглубь!
            val heldDef = player.slots[player.selectedSlot]?.let { BlockRegistry.items[it.id] }
            if (heldDef?.toolType == "pick") {
                val digW = max(138f, slotSz * 2.3f)
                val digX = min(W - digW - 14f, hbX + hbW + 12f)
                drawButton(digX, hbY + 6f, digW, slotSz - 4f, "КОПАТЬ ВНИЗ", blue = true, centered = true) {
                    val gx = floor(player.x + player.aimGx * 1.15f).toInt()
                    val gy = floor(player.y + player.aimGy * 1.15f).toInt()
                    digStairStepAt(gx, gy, floor(player.z).toInt())
                }
            }
        }

        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        spriteBatch.color = Color.WHITE
        SpriteArt.hotbarPatch.draw(spriteBatch, hbX, hbY, hbW, slotSz + 10f)
        spriteBatch.end()
        for (i in 0 until 9) {
            val sx = hbX + 7f + i * slotSz; val sy = hbY + 7f; val s = player.slots[i]
            hotbarSlotRects[i].set(sx, sy, slotSz - 4f, slotSz - 4f)
            drawSlotBox(sx, sy, slotSz - 4f, s, selected = i == player.selectedSlot)
        }
        drawButton(hbX + 7f + 9 * slotSz, hbY + 7f, slotSz + 2f, slotSz - 4f, "...", centered = true) { screen = UiScreen.INVENTORY }
    }

    private fun renderCampfireScreen(W: Float, H: Float) {
        drawPanel(W * 0.14f, H * 0.12f, W * 0.72f, H * 0.76f)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        font.draw(spriteBatch, "КОСТЁР — ГОТОВКА И ОТВАРЫ", W * 0.17f, H * 0.83f)
        fontSmall.draw(spriteBatch, "Тепло восстановлено! Выберите отвар или блюдо:", W * 0.17f, H * 0.75f)
        spriteBatch.end()
        drawButton(W * 0.73f, H * 0.79f, 110f, 42f, "Закрыть", centered = true) { screen = UiScreen.PLAYING }
        var y = H * 0.62f
        for (rec in BlockRegistry.recipes.filter { it.campfireBrew }) {
            val can = player.creative || rec.req.all { (id, cnt) -> player.countItem(id) >= cnt }
            drawButton(W * 0.17f, y, W * 0.66f, 48f, "${BlockRegistry.items[rec.outId]?.name} x${rec.outCount} — ${rec.desc}", green = can) {
                if (can) { rec.req.forEach { (id, cnt) -> player.takeItem(id, cnt) }; player.addItem(rec.outId, rec.outCount); SoundFx.play("craft", 0.65f); showToast("Сварено: ${BlockRegistry.items[rec.outId]?.name}!") }
            }
            y -= 56f
        }
    }

    private fun renderWaystonesScreen(W: Float, H: Float) {
        drawPanel(W * 0.14f, H * 0.12f, W * 0.72f, H * 0.76f)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        font.draw(spriteBatch, "КАМНИ ТЕЛЕПОРТА (БАЗА И ШАХТЫ)", W * 0.17f, H * 0.83f)
        fontSmall.draw(spriteBatch, "Мгновенное перемещение между установленными Камнями Телепорта:", W * 0.17f, H * 0.75f)
        spriteBatch.end()
        drawButton(W * 0.73f, H * 0.79f, 110f, 42f, "Закрыть", centered = true) { screen = UiScreen.PLAYING }
        var y = H * 0.62f
        for (ws in world.waystones.values.take(7)) {
            drawButton(W * 0.17f, y, W * 0.66f, 48f, "${ws.name} (X:${ws.x} Y:${ws.y} Z:${ws.z} • ${ws.dim})", green = true, centered = true) {
                if (world.dimension != ws.dim) switchDimension(ws.dim)
                player.x = ws.x + 0.5f; player.y = ws.y + 1.2f; player.z = ws.z.toFloat(); player.isSleeping = false
                camWorld.position.set(isoToScreenX(player.x, player.y), isoToScreenY(player.x, player.y, player.z), 0f)
                screen = UiScreen.PLAYING; showToast("Телепорт: ${ws.name}")
            }
            y -= 56f
        }
    }

    private fun renderAnvilScreen(W: Float, H: Float) {
        drawPanel(W * 0.14f, H * 0.14f, W * 0.72f, H * 0.72f)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        font.draw(spriteBatch, "НАКОВАЛЬНЯ — ЗАТОЧКА ОРУЖИЯ И КИРОК", W * 0.17f, H * 0.81f)
        fontSmall.draw(spriteBatch, "Уровень оружия: +${player.weaponUpgradeLvl}/5 (+${player.weaponUpgradeLvl * 2} урона) | Уровень кирок: +${player.toolUpgradeLvl}/5 (+${player.toolUpgradeLvl * 18}% скорости)", W * 0.17f, H * 0.73f)
        spriteBatch.end()
        drawButton(W * 0.73f, H * 0.77f, 110f, 42f, "Закрыть", centered = true) { screen = UiScreen.PLAYING }

        val canW = player.weaponUpgradeLvl < 5 && (player.creative || player.countItem(142) >= 1 || player.countItem(102) >= 2)
        drawButton(W * 0.17f, H * 0.54f, W * 0.66f, 54f, "Заточить Оружие (+2 к урону навсегда • Цена: 1 Рубин или 2 Железа)", green = canW, centered = true) {
            if (!canW) { showToast("Нужен 1 Рубин или 2 Железа (макс. ур. 5)"); return@drawButton }
            if (player.takeItem(142, 1) || player.takeItem(102, 2)) {
                player.weaponUpgradeLvl++; showToast("Оружие заточено до +${player.weaponUpgradeLvl}!")
            }
        }
        val canT = player.toolUpgradeLvl < 5 && (player.creative || player.countItem(141) >= 1 || player.countItem(104) >= 1)
        drawButton(W * 0.17f, H * 0.40f, W * 0.66f, 54f, "Усилить Инструменты (+18% к скорости копки • Цена: 1 Аметист или 1 Алмаз)", green = canT, centered = true) {
            if (!canT) { showToast("Нужен 1 Аметист или 1 Алмаз (макс. ур. 5)"); return@drawButton }
            if (player.takeItem(141, 1) || player.takeItem(104, 1)) {
                player.toolUpgradeLvl++; showToast("Инструменты усилены до +${player.toolUpgradeLvl}!")
            }
        }
        drawButton(W * 0.17f, H * 0.25f, W * 0.66f, 48f, "Открыть полный крафт (3x3)", centered = true) { screen = UiScreen.WORKBENCH }
    }

    private fun renderPauseMenu(W: Float, H: Float) {
        val pw = min(W * 0.44f, 460f); val ph = min(H * 0.66f, 410f)
        val px = (W - pw) * 0.5f; val py = (H - ph) * 0.5f
        drawPanel(px, py, pw, ph)
        drawCenteredText(font, "ПАУЗА", px, py + ph - 58f, pw, 40f)

        val bx = px + 26f; val bw = pw - 52f
        val bh = (ph - 92f) / 4f - 10f
        var by = py + ph - 74f - bh
        drawButton(bx, by, bw, bh, "ПРОДОЛЖИТЬ", green = true, centered = true) { screen = UiScreen.PLAYING }; by -= bh + 10f
        drawButton(bx, by, bw, bh, "МУЛЬТИПЛЕЕР", centered = true) { screen = UiScreen.MULTIPLAYER }; by -= bh + 10f
        drawButton(bx, by, bw, bh, "НАСТРОЙКИ И МИР", blue = true, centered = true) { settingsTab = 0; screen = UiScreen.SETTINGS_MODHUB }; by -= bh + 10f
        drawButton(bx, by, bw, bh, "ВЫЙТИ В МЕНЮ", red = true, centered = true) {
            world.saveToFile(File(File(storageRoot, "worlds"), worldFileName), player, entities)
            showToast("Мир сохранён!")
            screen = UiScreen.MAIN_MENU
        }
    }

    private fun renderInventoryScreen(W: Float, H: Float) {
        drawPanel(W * 0.06f, H * 0.06f, W * 0.88f, H * 0.88f)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin(); font.draw(spriteBatch, "ИНВЕНТАРЬ И КАРМАННЫЙ КРАФТ (2x2)", W * 0.08f, H * 0.90f); spriteBatch.end()
        drawButton(W * 0.81f, H * 0.84f, 110f, 42f, "Закрыть", centered = true) { screen = UiScreen.PLAYING }
        drawButton(W * 0.65f, H * 0.84f, 165f, 42f, "Сортировать", green = true, centered = true) { player.autoSortBackpack(); showToast("Рюкзак отсортирован!") }
        if (invSelectedSlot >= 0 && player.slots[invSelectedSlot] != null) {
            drawButton(W * 0.49f, H * 0.84f, 155f, 42f, "Выбросить 1", centered = true) { dropSlotItem(invSelectedSlot) }
        }

        var ry = H * 0.74f
        for (rec in BlockRegistry.recipes.filter { it.pocket2x2 }.take(9)) {
            val can = player.creative || rec.req.all { (id, cnt) -> player.countItem(id) >= cnt }
            drawButton(W * 0.08f, ry, W * 0.36f, 38f, "${BlockRegistry.items[rec.outId]?.name} x${rec.outCount} (${rec.desc})", green = can) {
                if (can) { rec.req.forEach { (id, cnt) -> player.takeItem(id, cnt) }; player.addItem(rec.outId, rec.outCount); SoundFx.play("craft", 0.65f); showToast("Скрафчено!") }
            }
            ry -= 44f
        }

        val cell = min(H * 0.115f, W * 0.44f / 9f); val gx = W * 0.47f
        for (i in 0 until 36) {
            val sx = gx + (i % 9) * cell; val sy = H * 0.67f - (i / 9) * cell
            drawSlotBox(sx, sy, cell - 5f, player.slots[i], selected = invSelectedSlot == i)
            buttons += Pair(Rectangle(sx, sy, cell - 5f, cell - 5f)) {
                val cur = player.slots[i]; val def = cur?.let { BlockRegistry.items[it.id] }
                if (def != null && def.armorSlot >= 0 && invSelectedSlot == -1) {
                    val prev = player.armor[def.armorSlot]; player.armor[def.armorSlot] = cur.id
                    player.slots[i] = if (prev > 0) SlotItem(prev, 1) else null; showToast("Надето: ${def.name}")
                } else if (invSelectedSlot == -1) invSelectedSlot = i
                else { val tmp = player.slots[invSelectedSlot]; player.slots[invSelectedSlot] = player.slots[i]; player.slots[i] = tmp; invSelectedSlot = -1 }
            }
        }
    }

    private fun renderWorkbenchScreen(W: Float, H: Float) {
        drawPanel(W * 0.06f, H * 0.06f, W * 0.88f, H * 0.88f)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin(); font.draw(spriteBatch, "ВЕРСТАК — ПОЛНЫЙ КРАФТ (3x3)", W * 0.08f, H * 0.90f); spriteBatch.end()
        drawButton(W * 0.81f, H * 0.84f, 110f, 42f, "Закрыть", centered = true) { screen = UiScreen.PLAYING }
        val bw = W * 0.41f
        BlockRegistry.recipes.filter { !it.campfireBrew }.take(18).forEachIndexed { idx, rec ->
            val can = player.creative || rec.req.all { (id, cnt) -> player.countItem(id) >= cnt }
            drawButton(W * 0.08f + (idx % 2) * (bw + 14f), H * 0.75f - (idx / 2) * 43f, bw, 38f, "${BlockRegistry.items[rec.outId]?.name} x${rec.outCount} — ${rec.desc}", green = can) {
                if (can) { rec.req.forEach { (id, cnt) -> player.takeItem(id, cnt) }; player.addItem(rec.outId, rec.outCount); SoundFx.play("craft", 0.65f); showToast("Создано!") }
            }
        }
    }

    private fun renderFurnaceScreen(W: Float, H: Float) {
        val pos = openFurnacePos ?: return
        val f = world.getFurnace(pos.first, pos.second, pos.third)
        drawPanel(W * 0.12f, H * 0.12f, W * 0.76f, H * 0.76f)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        font.draw(spriteBatch, "ПЕЧЬ — ПЕРЕПЛАВКА С ТОПЛИВОМ", W * 0.15f, H * 0.83f)
        fontSmall.draw(spriteBatch, "Огонь: ${((f.burn / max(1f, f.maxBurn)) * 100).toInt()}%   Прогресс: ${f.cook.toInt()}%", W * 0.15f, H * 0.74f)
        spriteBatch.end()
        drawButton(W * 0.75f, H * 0.78f, 110f, 42f, "Закрыть", centered = true) { openFurnacePos = null; screen = UiScreen.PLAYING }
        drawButton(W * 0.16f, H * 0.56f, W * 0.30f, 50f, "Сырьё: ${f.input?.let { "${BlockRegistry.items[it.id]?.name} x${it.count}" } ?: "Пусто"}") { f.input?.let { player.addItem(it.id, it.count); f.input = null } }
        drawButton(W * 0.16f, H * 0.44f, W * 0.30f, 50f, "Топливо: ${f.fuel?.let { "${BlockRegistry.items[it.id]?.name} x${it.count}" } ?: "Пусто"}") { f.fuel?.let { player.addItem(it.id, it.count); f.fuel = null } }
        drawButton(W * 0.52f, H * 0.49f, W * 0.30f, 58f, "Готово: ${f.output?.let { "${BlockRegistry.items[it.id]?.name} x${it.count}" } ?: "Пусто"}", green = f.output != null) { f.output?.let { player.addItem(it.id, it.count); f.output = null } }
        drawButton(W * 0.16f, H * 0.22f, W * 0.66f, 50f, "Загрузить руду и уголь из рюкзака", green = true, centered = true) {
            for (i in 0 until 36) {
                val s = player.slots[i] ?: continue
                if (BlockRegistry.smeltMap.containsKey(s.id) && (f.input == null || f.input!!.id == s.id)) {
                    f.input = if (f.input == null) SlotItem(s.id, s.count) else SlotItem(s.id, f.input!!.count + s.count); player.slots[i] = null
                } else if ((BlockRegistry.items[s.id]?.fuelPower ?: 0f) > 0f && (f.fuel == null || f.fuel!!.id == s.id)) {
                    f.fuel = if (f.fuel == null) SlotItem(s.id, s.count) else SlotItem(s.id, f.fuel!!.count + s.count); player.slots[i] = null
                }
            }
        }
    }

    private fun renderChestScreen(W: Float, H: Float) {
        val pos = openChestPos ?: return
        val items = world.getChest(pos.first, pos.second, pos.third)
        drawPanel(W * 0.08f, H * 0.08f, W * 0.84f, H * 0.84f)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin(); font.draw(spriteBatch, "СУНДУК / БОЧКА (27 СЛОТОВ)", W * 0.11f, H * 0.87f); spriteBatch.end()
        drawButton(W * 0.79f, H * 0.82f, 110f, 42f, "Закрыть", centered = true) { openChestPos = null; screen = UiScreen.PLAYING }
        val cell = min(H * 0.13f, W * 0.76f / 9f); val gx = (W - cell * 9f) / 2f
        for (i in 0 until 27) {
            val sx = gx + (i % 9) * cell; val sy = H * 0.65f - (i / 9) * cell; val s = items[i]
            drawSlotBox(sx, sy, cell - 5f, s, selected = false)
            buttons += Pair(Rectangle(sx, sy, cell - 5f, cell - 5f)) { if (s != null && player.addItem(s.id, s.count)) { items[i] = null; netMp.broadcastChest(pos.first, pos.second, pos.third, items) } }
        }
        drawButton(gx, H * 0.14f, cell * 9f, 48f, "Положить текущий предмет из руки в сундук", green = true, centered = true) {
            val held = player.slots[player.selectedSlot]; val empty = items.indexOfFirst { it == null }
            if (held != null && empty >= 0) { items[empty] = SlotItem(held.id, held.count); player.slots[player.selectedSlot] = null; netMp.broadcastChest(pos.first, pos.second, pos.third, items) }
        }
    }

    private fun renderSettingsAndModHub(W: Float, H: Float) {
        drawPanel(W * 0.08f, H * 0.08f, W * 0.84f, H * 0.84f)
        val tabW = W * 0.21f
        drawButton(W * 0.11f, H * 0.80f, tabW, 48f, "Настройки", green = settingsTab == 0, centered = true) { settingsTab = 0 }
        drawButton(W * 0.12f + tabW, H * 0.80f, tabW, 48f, "Мир и Герой", green = settingsTab == 1, centered = true) { settingsTab = 1 }
        drawButton(W * 0.13f + tabW * 2f, H * 0.80f, tabW, 48f, "Моды (Lua)", green = settingsTab == 2, centered = true) { settingsTab = 2 }
        drawButton(W * 0.78f, H * 0.80f, 118f, 48f, "Готово", blue = true, centered = true) { screen = if (world.worldActive) UiScreen.PLAYING else UiScreen.MAIN_MENU }

        val bx = W * 0.12f; val bw = W * 0.76f
        when (settingsTab) {
            0 -> {
                drawButton(bx, H * 0.64f, bw, 50f, "Автоатака по мобам: ${if (autoAttackEnabled) "ВКЛ" else "ВЫКЛ"}", green = autoAttackEnabled) {
                    autoAttackEnabled = !autoAttackEnabled
                }
                drawButton(bx, H * 0.52f, bw, 50f, "Длительность сезона: ${entities.seasonLengthDays} дней (30 / 60 / 90)") {
                    entities.seasonLengthDays = when (entities.seasonLengthDays) { 30 -> 60; 60 -> 90; else -> 30 }
                }
                drawButton(bx, H * 0.40f, bw, 50f, "Масштаб камеры (или щипок): ${"%.2f".format(cameraZoom)}") {
                    cameraZoom = if (cameraZoom <= 0.35f) 1.30f else (cameraZoom - 0.18f).coerceAtLeast(0.30f)
                }
                drawButton(bx, H * 0.28f, bw, 50f, "Музыка и звуки: ${if (soundEnabled) "ВКЛ" else "ВЫКЛ"}", green = soundEnabled) {
                    soundEnabled = !soundEnabled; SoundFx.enabled = soundEnabled
                }
                drawButton(bx, H * 0.16f, bw, 46f, "Проверить обновления GitHub (${PlatformBridge.CURRENT_VERSION})", blue = true, centered = true) {
                    PlatformBridge.checkGitHubUpdate(); showToast("Проверяем GitHub Horon...")
                }
            }
            1 -> {
                drawButton(bx, H * 0.64f, bw, 50f, "Сохранить мир (.vhworld)", green = true, centered = true) {
                    world.saveToFile(File(File(storageRoot, "worlds"), worldFileName), player, entities)
                    showToast("Мир сохранён!")
                }
                drawButton(bx, H * 0.52f, bw, 50f, if (entities.foxPet != null) "Лисёнок-питомец: ПРИЗВАН (Нажмите чтобы отозвать)" else "Лисёнок-питомец: ОТДЫХАЕТ (Нажмите чтобы призвать)", green = entities.foxPet != null, centered = true) {
                    val on = entities.toggleFoxPet(player); showToast(if (on) "Лисёнок призван!" else "Лисёнок отдыхает")
                }
                val dimRu = when (world.dimension) { "nether" -> "АД"; "aether" -> "ЭФИР"; else -> "Обычный мир" }
                drawButton(bx, H * 0.40f, bw, 50f, "Измерение: $dimRu (Нажмите для перехода)", blue = true, centered = true) {
                    val next = when (world.dimension) { "overworld" -> "nether"; "nether" -> "aether"; else -> "overworld" }
                    switchDimension(next); screen = UiScreen.PLAYING
                }
                drawButton(bx, H * 0.28f, bw, 50f, "Режим игры: ${if (player.creative) "Творчество" else "Выживание"}", centered = true) {
                    player.creative = !player.creative
                }
                drawButton(bx, H * 0.16f, bw, 46f, "Навыки, Опыт и Бестиарий", green = true, centered = true) {
                    screen = UiScreen.BESTIARY_SKILLS
                }
            }
            else -> {
                var my = H * 0.66f
                for (m in luaMods.mods.take(6)) {
                    drawButton(bx, my, bw, 48f, "${if (m.enabled) "[ВКЛ]" else "[ВЫКЛ]"} ${m.name} — ${m.desc}", green = m.enabled) {
                        m.enabled = !m.enabled; luaMods.hotReload(); showToast("${if (m.enabled) "Включён" else "Отключён"}: ${m.name}")
                    }
                    my -= 56f
                }
                drawButton(bx, H * 0.14f, bw, 48f, "Горячая перезагрузка модов (VoxelHorizon/mods/)", green = true, centered = true) {
                    luaMods.hotReload(); showToast("Lua-моды перезагружены!")
                }
            }
        }
    }

    private fun renderWardrobeScreen(W: Float, H: Float) {
        drawPanel(W * 0.08f, H * 0.08f, W * 0.84f, H * 0.84f)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin(); font.draw(spriteBatch, "ГАРДЕРОБ ИЗОМЕТРИЧЕСКИХ ГЕРОЕВ", W * 0.11f, H * 0.87f); spriteBatch.end()
        drawButton(W * 0.78f, H * 0.82f, 110f, 42f, "Назад", centered = true) { screen = UiScreen.MAIN_MENU }

        drawPanel(W * 0.11f, H * 0.18f, W * 0.24f, H * 0.58f, Color(0.16f, 0.10f, 0.28f, 0.95f), Color.GOLD)
        val previewFrame = when (((elapsed * 5f).toInt() % 4 + 4) % 4) { 1 -> 1; 3 -> 2; else -> 0 }
        SpriteArt.skinSprites[player.skinId]?.get(previewFrame)?.let { reg ->
            spriteBatch.projectionMatrix = camUI.combined
            spriteBatch.begin(); spriteBatch.draw(reg, W * 0.15f, H * 0.25f, W * 0.16f, W * 0.20f); spriteBatch.end()
        }

        var y = H * 0.67f
        for (sk in SKINS_LIST) {
            drawButton(W * 0.38f, y, W * 0.50f, 50f, "${sk.name} ${if (player.skinId == sk.id) "(Выбран)" else ""}", green = player.skinId == sk.id, centered = true) {
                player.skinId = sk.id; profiles.active.skinId = sk.id; profiles.saveProfiles(); showToast("Герой: ${sk.name}")
            }
            y -= 58f
        }
    }

    // ОНЛАЙН-МУЛЬТИПЛЕЕР (ИНТЕРНЕТ ПО КОДУ КОМНАТЫ ИЗ ЛЮБОГО ГОРОДА!) + LAN + IP!
    private fun renderMultiplayerScreen(W: Float, H: Float) {
        drawPanel(W * 0.08f, H * 0.06f, W * 0.84f, H * 0.88f)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        font.draw(spriteBatch, "МУЛЬТИПЛЕЕР (ОНЛАЙН + LAN)", W * 0.11f, H * 0.89f)
        fontSmall.draw(spriteBatch, "Статус: ${netMp.statusText} | Игроков: ${netMp.remotePlayers.size + (if (netMp.isConnected) 1 else 0)}", W * 0.11f, H * 0.82f)
        spriteBatch.end()
        drawButton(W * 0.78f, H * 0.84f, 118f, 44f, "Назад", centered = true) { screen = if (world.worldActive) UiScreen.PLAYING else UiScreen.MAIN_MENU }

        // Сервер-брокер и Пароль комнаты (чтобы друзья 100% попадали на один сервер без чужих!)
        drawButton(W * 0.11f, H * 0.70f, W * 0.42f, 44f, "Сервер: ${netMp.brokerHostName()}", blue = true, centered = true) {
            netMp.cycleBroker(); showToast("Выбран сервер: ${netMp.brokerHostName()}")
        }
        val passLbl = if (netMp.roomPassword.isEmpty()) "Пароль: НЕТ (Открыто)" else "Пароль: **** (Установлен)"
        drawButton(W * 0.54f, H * 0.70f, W * 0.35f, 44f, passLbl, centered = true) {
            Gdx.input.getTextInput(object : Input.TextInputListener {
                override fun input(text: String) { netMp.roomPassword = text.trim(); showToast(if (netMp.roomPassword.isEmpty()) "Пароль убран" else "Пароль комнаты установлен!") }
                override fun canceled() {}
            }, "Пароль комнаты (пусто = без пароля)", netMp.roomPassword, "")
        }

        drawButton(W * 0.11f, H * 0.57f, W * 0.38f, 50f, "Создать ОНЛАЙН (Код)", green = true, centered = true) {
            if (!world.worldActive) startWorld(true, false)
            netMp.startOnlineHost()
        }
        drawButton(W * 0.51f, H * 0.57f, W * 0.38f, 50f, "Войти по Коду", green = true, centered = true) {
            Gdx.input.getTextInput(object : Input.TextInputListener {
                override fun input(text: String) { if (text.isNotBlank()) netMp.joinOnlineRoom(text) }
                override fun canceled() {}
            }, "Введите 4-значный Код комнаты", "", "Например: 4829")
        }

        drawButton(W * 0.11f, H * 0.43f, W * 0.38f, 46f, "Друзья Онлайн (${profiles.active.friends.size})", blue = true, centered = true) { screen = UiScreen.PROFILES_FRIENDS }
        drawButton(W * 0.51f, H * 0.43f, W * 0.38f, 46f, "PvP: ${if (netMp.pvpEnabled) "ВКЛ" else "ВЫКЛ"}", green = netMp.pvpEnabled, centered = true) { netMp.pvpEnabled = !netMp.pvpEnabled }

        drawButton(W * 0.11f, H * 0.30f, W * 0.25f, 44f, "Создать LAN", centered = true) {
            if (!world.worldActive) startWorld(true, false); netMp.startLanHost(); showToast("LAN открыт!")
        }
        drawButton(W * 0.37f, H * 0.30f, W * 0.25f, 44f, "Найти LAN", centered = true) { netMp.discoverAndJoinLan() }
        drawButton(W * 0.64f, H * 0.30f, W * 0.25f, 44f, "Вход по IP", centered = true) {
            Gdx.input.getTextInput(object : Input.TextInputListener {
                override fun input(text: String) { if (text.isNotBlank()) netMp.joinDirectIp(text) }
                override fun canceled() {}
            }, "IP Хоста", "192.168.1.", "")
        }
        drawButton(W * 0.11f, H * 0.16f, W * 0.78f, 44f, "Отключиться от сети", red = true, centered = true) { netMp.stop(); showToast("Сеть отключена") }
    }

    // =========================================================================
    // 100% PURE STICK + TAP / HOLD / SWIPE / PINCH INPUT!
    // =========================================================================
    private fun setupInputProcessor() {
        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                val ux = screenX.toFloat(); val uy = (Gdx.graphics.height - screenY).toFloat()
                ptrPos[pointer] = Pair(ux, uy)

                // 0. Проверяем UI-кнопки (меню, пауза, «...», СЛОЙ)
                for (i in buttons.size - 1 downTo 0) {
                    val (rect, act) = buttons[i]
                    if (rect.contains(ux, uy)) { act(); return true }
                }
                if (screen != UiScreen.PLAYING) return false

                // 1. Тап по слоту Левой руки (Off-hand) -> мгновенный обмен рук!
                if (offhandSlotRect.contains(ux, uy)) {
                    player.swapWithOffhand()
                    showToast("Левая рука: ${BlockRegistry.items[player.offhand?.id ?: 0]?.name ?: "Пусто"}")
                    return true
                }

                // 2. Тап или свайп вверх по слоту Хотбара (выбор слота / выброс свайпом вверх!)
                for (i in 0 until 9) {
                    if (hotbarSlotRects[i].contains(ux, uy)) {
                        player.selectedSlot = i
                        player.slots[i]?.let { showToast(BlockRegistry.items[it.id]?.name ?: "") }
                        hotbarSwipePtr = pointer; hotbarSwipeSlot = i; hotbarSwipeStartY = uy
                        return true
                    }
                }

                // 3. Левый плавающий стик (левые 36% экрана)
                if (joyPtr == -1 && ux < Gdx.graphics.width * 0.36f) {
                    joyPtr = pointer
                    joyCenterX = ux.coerceIn(joyRadius + 8f, Gdx.graphics.width * 0.32f)
                    joyCenterY = uy.coerceIn(joyRadius + 8f, Gdx.graphics.height * 0.65f)
                    updateJoystick(ux, uy)
                    return true
                }

                // 4. Второй палец на мире -> Пинч-зум двумя пальцами!
                if (worldTouchPtr != -1 && pinchPtr2 == -1) {
                    pinchPtr2 = pointer
                    val p1 = ptrPos[worldTouchPtr] ?: Pair(worldTouchStartX, Gdx.graphics.height - worldTouchStartY)
                    pinchStartDist = max(20f, hypot((p1.first - ux).toDouble(), (p1.second - uy).toDouble()).toFloat())
                    pinchStartZoom = cameraZoom
                    holdingMine = false; worldTouchHeldActed = true
                    return true
                }

                // 5. Начало тапа / удержания по миру!
                if (worldTouchPtr == -1) {
                    worldTouchPtr = pointer
                    worldTouchDownTime = elapsed
                    worldTouchStartX = screenX.toFloat()
                    worldTouchStartY = screenY.toFloat()
                    worldTouchHeldActed = false
                    return true
                }
                return true
            }

            private fun updateJoystick(ux: Float, uy: Float) {
                var dx = ux - joyCenterX; var dy = uy - joyCenterY
                val maxR = joyRadius * 0.72f
                val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (dist > maxR) { dx = dx / dist * maxR; dy = dy / dist * maxR }
                stickDx = dx / maxR; stickDy = dy / maxR
            }

            override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
                val ux = screenX.toFloat(); val uy = (Gdx.graphics.height - screenY).toFloat()
                ptrPos[pointer] = Pair(ux, uy)
                if (screen != UiScreen.PLAYING) return false

                if (pointer == joyPtr) { updateJoystick(ux, uy); return true }
                if (pointer == hotbarSwipePtr && hotbarSwipeSlot >= 0 && (uy - hotbarSwipeStartY) > 54f) {
                    dropSlotItem(hotbarSwipeSlot)
                    hotbarSwipeSlot = -1
                    return true
                }
                if (pinchPtr2 != -1 && (pointer == worldTouchPtr || pointer == pinchPtr2)) {
                    val p1 = ptrPos[worldTouchPtr]; val p2 = ptrPos[pinchPtr2]
                    if (p1 != null && p2 != null) {
                        val d = max(20f, hypot((p1.first - p2.first).toDouble(), (p1.second - p2.second).toDouble()).toFloat())
                        cameraZoom = (pinchStartZoom * (pinchStartDist / d)).coerceIn(0.28f, 1.45f)
                    }
                    return true
                }
                return false
            }

            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                ptrPos.remove(pointer)
                if (pointer == joyPtr) { joyPtr = -1; stickDx = 0f; stickDy = 0f; return true }
                if (pointer == hotbarSwipePtr) { hotbarSwipePtr = -1; hotbarSwipeSlot = -1; return true }
                if (pointer == pinchPtr2) { pinchPtr2 = -1; return true }

                if (pointer == worldTouchPtr) {
                    val wasShortTap = !worldTouchHeldActed && (elapsed - worldTouchDownTime) < 0.22f
                    worldTouchPtr = -1; holdingMine = false; mineProgress = 0f
                    if (wasShortTap && screen == UiScreen.PLAYING) {
                        handleWorldTap(screenX.toFloat(), screenY.toFloat())
                    }
                    return true
                }
                return false
            }

            private fun handleWorldTap(sx: Float, sy: Float) {
                // Если спим на кровати -> тап поднимает игрока!
                if (fishingActive) { resolveFishingCatch(); return }
                if (player.isSleeping) {
                    player.isSleeping = false; player.sleepElapsed = 0f
                    showToast("Вы встали с кровати")
                    return
                }

                tmpVec3.set(sx, sy, 0f); camWorld.unproject(tmpVec3)
                val hx = isoToScreenX(player.x, player.y); val hy = isoToScreenY(player.x, player.y, player.z) + 22f

                // 1. Тап по самому ГЕРОЮ -> Еда/Отвар/Сердце Леса ИЛИ Рывок/Парирование Щитом!
                if (hypot((hx - tmpVec3.x).toDouble(), (hy - tmpVec3.y).toDouble()) < 44.0) {
                    val held = player.slots[player.selectedSlot]
                    if (held != null && useConsumableOrSpecial(held)) return
                    performDashOrParry()
                    return
                }

                // PvP удар по другому игроку (если PvP включено)
                if (netMp.isConnected && netMp.pvpEnabled) {
                    val tappedPeer = netMp.remotePlayers.values.firstOrNull { rp ->
                        val rx = isoToScreenX(rp.x, rp.y); val ry = isoToScreenY(rp.x, rp.y, rp.z) + 22f
                        hypot((rx - tmpVec3.x).toDouble(), (ry - tmpVec3.y).toDouble()) < 50.0 && hypot((rp.x - player.x).toDouble(), (rp.y - player.y).toDouble()) < 3.0
                    }
                    if (tappedPeer != null) {
                        val hDef = player.slots[player.selectedSlot]?.let { BlockRegistry.items[it.id] }
                        val dmg = ((hDef?.damage ?: 4) + player.weaponUpgradeLvl * 2).toFloat()
                        player.attackAnim = 1.0f
                        netMp.sendPvpHit(tappedPeer.id, dmg)
                        entities.floatingTexts.add(FloatingText(tappedPeer.x, tappedPeer.y, tappedPeer.z + 1.5f, "-${dmg.toInt()}", Color.RED))
                        return
                    }
                }
                val pick = pickIsoBlock(sx, sy) ?: return
                targetPick = pick

                // 2. Тап по МОБУ (увеличенный радиус 76px — не промахнёшься!)
                val tappedMob = entities.mobs.minByOrNull { m ->
                    val mx = isoToScreenX(m.x, m.y); val my = isoToScreenY(m.x, m.y, m.z) + 22f
                    hypot((mx - tmpVec3.x).toDouble(), (my - tmpVec3.y).toDouble())
                }
                if (tappedMob != null) {
                    val mx = isoToScreenX(tappedMob.x, tappedMob.y); val my = isoToScreenY(tappedMob.x, tappedMob.y, tappedMob.z) + 22f
                    if (hypot((mx - tmpVec3.x).toDouble(), (my - tmpVec3.y).toDouble()) < 76.0) {
                        if (tappedMob.type == "trader") { SoundFx.play("chest", 0.6f); screen = UiScreen.TRADER_QUESTS; return }
                        val held = player.slots[player.selectedSlot]
                        if (tappedMob.type == "wolf" && !tappedMob.tamed && held != null && (held.id == 115 || held.id == 114)) {
                            if (player.takeItem(held.id, 1)) {
                                tappedMob.tamed = true; tappedMob.hostile = false; tappedMob.hp = 45f; tappedMob.maxHp = 45f
                                showToast("Волк приручён! Теперь он защищает вас!"); return
                            }
                        }
                        if (tappedMob.type == "piglin" && held?.id == 103) {
                            if (player.takeItem(103, 1)) {
                                val rew = entities.barterPiglin(tappedMob)
                                showToast("Пиглин обменял золото на: ${BlockRegistry.items[rew]?.name}!"); return
                            }
                        }
                        if (tappedMob.type in setOf("sheep", "cow", "pig", "chicken", "deer") && held != null && (held.id == 143 || held.id == 113) && tappedMob.breedCd <= 0f) {
                            if (player.takeItem(held.id, 1)) {
                                showToast(entities.feedPeacefulMob(tappedMob)); return
                            }
                        }
                        performAttackOrMine(IsoPick(floor(tappedMob.x).toInt(), floor(tappedMob.y).toInt(), floor(tappedMob.z).toInt(), 0), startContinuousHold = false)
                        return
                    }
                }

                // 2.5 Умный поиск ближайшего Сундука / Кровати / Печи / Верстака рядом с точкой тапа!
                val nearTapInter = findNearestInteractive(pick.gx + 0.5f, pick.gy + 0.5f, pick.gz, 1.45f)
                if (nearTapInter != null && hypot((nearTapInter.gx + 0.5f - player.x).toDouble(), (nearTapInter.gy + 0.5f - player.y).toDouble()) <= 4.6) {
                    performInteractOrPlace(nearTapInter)
                    return
                }

                val def = BlockRegistry.items[pick.id]
                val held = player.slots[player.selectedSlot]
                val hDef = held?.let { BlockRegistry.items[it.id] }
                when {
                    def != null && def.interactive.isNotEmpty() -> performInteractOrPlace(pick)
                    hDef != null && !hDef.isItem -> performInteractOrPlace(pick)
                    else -> performAttackOrMine(pick, startContinuousHold = false)
                }
            }
        }
    }

    override fun resize(width: Int, height: Int) {
        camWorld.viewportWidth = width.toFloat(); camWorld.viewportHeight = height.toFloat(); camWorld.update()
        camUI.setToOrtho(false, width.toFloat(), height.toFloat()); camUI.update()
    }

    override fun pause() {
        if (screen == UiScreen.PLAYING) world.saveToFile(File(File(storageRoot, "worlds"), worldFileName), player, entities)
    }

    override fun dispose() {
        netMp.stop(); bgMusic?.dispose()
        SoundFx.dispose(); BlockRegistry.dispose(); SpriteArt.dispose()
        if (::minimapTex.isInitialized) minimapTex.dispose()
        if (::minimapPixmap.isInitialized) minimapPixmap.dispose()
        spriteBatch.dispose(); shapeRenderer.dispose()
        font.dispose(); fontSmall.dispose(); coverTex?.dispose()
    }
}
