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
    MAIN_MENU, WORLD_SLOTS, PROFILES_FRIENDS, PLAYING, PAUSE, INVENTORY, WORKBENCH, FURNACE, CHEST, CAMPFIRE, WAYSTONES, ANVIL, TRADER_QUESTS, BESTIARY_SKILLS, FULL_MAP, ENCHANTING, ACHIEVEMENTS, GOLEM_BAG, SETTINGS_MODHUB, WARDROBE, MULTIPLAYER, DEATH
}

data class IsoPick(val gx: Int, val gy: Int, val gz: Int, val id: Int)
data class UiSlotHit(val zone: String, val index: Int, val rect: Rectangle)
data class LeafDecayEntry(val x: Int, val y: Int, val z: Int, var timer: Float)

class VoxelHorizonGame(private val storageRoot: File) : ApplicationAdapter() {

    lateinit var world: WorldIso
    lateinit var entities: EntityAndWeatherManager
    lateinit var luaMods: LuaModEngine
    lateinit var netMp: NetMultiplayer
    lateinit var profiles: ProfileManager
    val redstone = RedstoneSystem()
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
    var baseWalkSpeed = 3.35f
    var cameraZoom = 0.85f
    var worldFileName = "world_iso_v67.vhworld"

    // Стройка и копка v6.3: режим (0=ПОЛ, 1=СТЕНА, 2=КРЫША), свайп-стройка, быстрый откат!
    private var buildMode = 1 // 0=ПОЛ, 1=СТЕНА, 2=КРЫША, 3=СТОЛБ
    private var buildZOffset = 0 // Сдвиг высоты стройки (-2..+3) кнопками Z+ / Z-
    private var boxFillMode = 0 // 0=ВЫКЛ, 1=ЗАЛИВ (площадка), 2=КОМНАТА (стены 3 блока + дверь!)
    private var fastDemolishMode = false // Режим быстрого сноса блоков при стройке (СНОС)
    private var mirrorBuildMode = false // Зеркальная стройка (симметрично относительно центра)
    private var mirrorCx = 0; private var mirrorCy = 0
    private var pipetteMode = false // Пипетка блока (тап по блоку берёт его в руку)
    private var boxStartGx = Int.MIN_VALUE; private var boxStartGy = Int.MIN_VALUE; private var boxStartGz = 0
    private var digActionMode = 0 // 0=ВНИЗ, 1=ВВЕРХ, 2=ТОННЕЛЬ
    private var isDragMining = false
    private var dragMineCd = 0f
    private var menuPanoScroll = 0f
    private var lastPlacedX = Int.MIN_VALUE; private var lastPlacedY = Int.MIN_VALUE; private var lastPlacedZ = Int.MIN_VALUE
    private var lastPlacedId = 0; private var lastPlacedTime = -10f
    private var dragBuildLastX = Int.MIN_VALUE; private var dragBuildLastY = Int.MIN_VALUE; private var dragBuildLastZ = Int.MIN_VALUE
    private var isDragBuilding = false
    private var ghostTarget: IsoPick? = null

    // Плавный сетевой трекер (15 Гц только при движении или 0.45с heartbeat!)
    private var lastSentX = 0f; private var lastSentY = 0f; private var lastSentZ = 0f
    private var mpHeartbeatTimer = 0f

    // Универсальный Drag-and-Drop инвентаря (54 слота, броня, лев. рука, сундук, печь!)
    private val uiSlots = mutableListOf<UiSlotHit>()
    private var dragPtr = -1
    private var dragStartX = 0f; private var dragStartY = 0f
    private var dragCurX = 0f; private var dragCurY = 0f
    private var dragActive = false
    private var dragFromZone = ""; private var dragFromIdx = -1
    private var dragPreviewItem: SlotItem? = null
    private var activePanelBounds = Rectangle()

    // Вкладки и страницы новых визуальных экранов
    private var invTab = 0 // 0 = Сумка (54), 1 = Крафт 2x2
    private var chestBagPage = 0 // 0 = слоты 0..26, 1 = слоты 27..53
    private var furnaceBagPage = 0
    private val decayingLeaves = mutableListOf<LeafDecayEntry>()
    private var wbSelectedIdx = 0
    private var wbPage = 0
    private var wbCat = 0 // 0=Все, 1=Инстр., 2=Стройка, 3=Редстоун, 4=Еда
    private var campfirePage = 0
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
                val sBig = (Gdx.graphics.height / 660f).coerceIn(0.95f, 1.75f)
                data.setScale(sBig)
            }
            fontSmall = BitmapFont(pixFnt).apply {
                region.texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
                setUseIntegerPositions(true)
                val sSm = (Gdx.graphics.height / 940f).coerceIn(0.68f, 1.22f)
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
        player.slots[0] = SlotItem(113, 4)  // Яблоки (старт без инструментов!)
        player.slots[1] = SlotItem(37, 6)   // Факелы
        player.slots[2] = SlotItem(7, 6)    // Доски
        player.offhand = null
        player.armor.fill(0)
    }

    private fun isoToScreenX(gx: Float, gy: Float): Float = (gx - gy) * WorldIso.TW
    // Экранный Y для сущностей (центр тайла (gx+0.5, gy+0.5) совпадает 1:1 с центром верхней грани блока!)
    private fun isoToScreenY(gx: Float, gy: Float, gz: Float): Float = -(gx + gy - 1f) * WorldIso.TH + gz * WorldIso.ZH
    // Экранный Y для целочисленных координат блоков (Ghost Preview, выделение блока)
    private fun blockScreenY(gx: Int, gy: Int, gz: Int): Float = -(gx + gy) * WorldIso.TH + gz * WorldIso.ZH

    // Умная диагональ отрисовки сущности: не даёт плоскому полу спереди срезать ноги героя и лисёнка!
    private fun entityDiagIndex(x: Float, y: Float, z: Float, minSum: Int): Int {
        val gx = floor(x).toInt(); val gy = floor(y).toInt(); val gz = floor(z).toInt()
        var di = (gx + gy) - minSum
        val fx = x - gx; val fy = y - gy
        if (fx + fy > 0.96f) {
            val wallA = BlockRegistry.items[world.getBlock(gx + 1, gy, gz)]?.solid == true
            val wallB = BlockRegistry.items[world.getBlock(gx, gy + 1, gz)]?.solid == true
            if (!wallA && !wallB) di += 1
        }
        return di
    }

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
        lastFogGx = Int.MIN_VALUE; lastFogGy = Int.MIN_VALUE; fishingActive = false; decayingLeaves.clear()
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
        val nearTrader = entities.mobs.any { it.type == "trader" && hypot((it.x - player.x).toDouble(), (it.y - player.y).toDouble()) < 18.0 }
        val target = when {
            entities.getActiveBoss() != null -> "ost_boss.wav"
            world.dimension == "nether" -> "ost_nether.wav"
            world.dimension == "aether" -> "ost_aether.wav"
            isDeep || player.z < WorldIso.SEA_Z - 6 -> "ost_cave.wav"
            nearTrader && entities.dayFactor >= 0.35f -> "ost_village.wav"
            entities.dayFactor < 0.28f -> "ost_overworld_night.wav"
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

    fun triggerTntAt(cx: Int, cy: Int, cz: Int) = explodeAt(cx, cy, cz, 2.8f)

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

    private fun wakeUpFromBed(msg: String? = "Вы встали с кровати") {
        player.isSleeping = false
        player.sleepElapsed = 0f
        val gx = floor(player.x).toInt(); val gy = floor(player.y).toInt()
        val safeZ = world.getGroundZ(gx, gy, player.z + 1.0f) ?: (world.getSurfaceZ(gx, gy) + 1).toFloat()
        player.z = safeZ
        if (msg != null) showToast(msg)
    }

    private fun consumeToolDurability(slotIdx: Int = player.selectedSlot, amount: Int = 1, isWeapon: Boolean = false) {
        val upg = if (isWeapon) player.weaponUpgradeLvl else player.toolUpgradeLvl
        val brokenName = player.damageSlotDurability(slotIdx, amount, upg)
        if (brokenName != null) {
            SoundFx.play("break", 0.85f)
            entities.spawnBlockParticles(floor(player.x).toInt(), floor(player.y).toInt(), floor(player.z).toInt(), 0xD8A864, 8)
            showToast("Сломалось: $brokenName!")
        }
    }

    private fun tryUnlockAchievement(id: String) {
        val unlocked = profiles.unlockAchievement(id) ?: return
        player.addItem(118, unlocked.rewardEmeralds)
        SoundFx.play("achieve", 0.8f)
        showToast("ДОСТИЖЕНИЕ: ${unlocked.title}! (+${unlocked.rewardEmeralds} Изумр.)")
    }

    private fun handleMobKill(dead: MobEntity) {
        val dropsToSpawn = mutableListOf<Pair<Int, Int>>()
        when (dead.type) {
            // Боссы: уникальный трофей + умеренная награда (без перекоса экономики!)
            "grove_guardian" -> { dropsToSpawn += 133 to 1; dropsToSpawn += 118 to 2; dropsToSpawn += 141 to 1 }
            "frost_titan"    -> { dropsToSpawn += 134 to 1; dropsToSpawn += 118 to 2; dropsToSpawn += 104 to 1 }
            "depth_devourer" -> { dropsToSpawn += 135 to 1; dropsToSpawn += 142 to 1; dropsToSpawn += 104 to 1 }
            "inferno_lord"   -> { dropsToSpawn += 129 to 1; dropsToSpawn += 142 to 1; dropsToSpawn += 103 to 3 }
            "sky_guardian"   -> { dropsToSpawn += 129 to 1; dropsToSpawn += 118 to 2; dropsToSpawn += 141 to 2 }

            // Мирные и нейтральные: Сырое мясо (нужно жарить!) + Мех/Шкура
            "sheep", "cow", "pig", "deer", "boar" -> {
                dropsToSpawn += 157 to Random.nextInt(1, 3)
                if (Random.nextFloat() < 0.65f) dropsToSpawn += 158 to 1
            }
            "chicken" -> {
                dropsToSpawn += 157 to 1
                if (Random.nextFloat() < 0.45f) dropsToSpawn += 124 to 2
            }
            "wolf" -> dropsToSpawn += 158 to 1

            // Враги: сбалансированный дроп без бесплатных алмазов и рубинов!
            "skeleton" -> {
                dropsToSpawn += 124 to Random.nextInt(2, 5)
                if (Random.nextFloat() < 0.12f) dropsToSpawn += 102 to 1
            }
            "creeper" -> {
                dropsToSpawn += 159 to Random.nextInt(1, 3)
                if (Random.nextFloat() < 0.40f) dropsToSpawn += 101 to 1
            }
            "spider" -> dropsToSpawn += 160 to Random.nextInt(1, 3)
            "enderman" -> {
                dropsToSpawn += 141 to 1
                if (Random.nextFloat() < 0.20f) dropsToSpawn += 235 to 1 // Жемчуг Телепорта
            }
            "yeti" -> {
                dropsToSpawn += 158 to Random.nextInt(2, 4)
                if (Random.nextFloat() < 0.20f) dropsToSpawn += 118 to 1
            }
            "mimic" -> {
                dropsToSpawn += 103 to Random.nextInt(1, 3)
                if (Random.nextFloat() < 0.30f) dropsToSpawn += 138 to 1
            }
            "goblin", "piglin" -> {
                dropsToSpawn += (if (Random.nextBoolean()) 140 else 101) to Random.nextInt(1, 3)
                if (Random.nextFloat() < 0.28f) dropsToSpawn += 103 to 1
            }
            "crystal_golem" -> {
                dropsToSpawn += 141 to Random.nextInt(1, 3)
                if (Random.nextFloat() < 0.25f) dropsToSpawn += 102 to 1
            }
            "slime", "mini_slime", "magma_slime" -> dropsToSpawn += 147 to 1
            "blaze" -> dropsToSpawn += 248 to Random.nextInt(1, 3)
            "ghast" -> { dropsToSpawn += 159 to 2; if (Random.nextFloat() < 0.25f) dropsToSpawn += 117 to 1 }
            "zombie" -> { dropsToSpawn += 101 to 1; if (Random.nextFloat() < 0.12f) dropsToSpawn += 102 to 1 }
            else -> dropsToSpawn += 101 to 1
        }
        if (player.slots[player.selectedSlot]?.id == 254 && Random.nextFloat() < 0.35f) dropsToSpawn += 118 to 1
        for ((dId, dCnt) in dropsToSpawn) {
            entities.spawnDrop(dId, dCnt, dead.x + (Random.nextFloat() - 0.5f) * 0.4f, dead.y + (Random.nextFloat() - 0.5f) * 0.4f, dead.z)
        }
        if (dead.isBoss) tryUnlockAchievement("boss_slayer")
        if (profiles.active.enchFireAspect) {
            for (i in dropsToSpawn.indices) if (dropsToSpawn[i].first == 157) dropsToSpawn[i] = 115 to dropsToSpawn[i].second
        }
        val leveledUp = profiles.recordMobKill(dead.type, dead.isBoss)
        SoundFx.play(if (dead.isBoss || leveledUp) "levelup" else "hit", 0.75f)
        val firstDropName = BlockRegistry.items[dropsToSpawn.firstOrNull()?.first ?: 0]?.name ?: ""
        showToast(when {
            dead.isBoss -> "БОСС ПОВЕРЖЕН! Награда: $firstDropName!"
            leveledUp -> "УРОВЕНЬ БОЯ ${profiles.active.combatLevel()}! (+шанс крита)"
            else -> "Побеждён: ${dead.name}"
        })
    }

    private fun pickIsoBlock(screenX: Float, screenY: Float): IsoPick? {
        tmpVec3.set(screenX, screenY, 0f)
        camWorld.unproject(tmpVec3)
        RustEngine.nativePickIsoBlock(
            tmpVec3.x, tmpVec3.y,
            floor(player.x).toInt(), floor(player.y).toInt(), floor(player.z).toInt(),
            activeSliceZ, isCutawayActive, rustPickBuf
        )
        return IsoPick(rustPickIntView.get(0), rustPickIntView.get(1), rustPickIntView.get(2), rustPickIntView.get(3))
    }

    // Точный расчёт ячейки для стройки по плоскости (ПОЛ / СТЕНА / КРЫША) — никогда не ставит блок на стену спереди!
    private fun pickBuildCellOnPlane(screenX: Float, screenY: Float): IsoPick? {
        tmpVec3.set(screenX, screenY, 0f)
        camWorld.unproject(tmpVec3)
        val pz = floor(player.z).toInt().coerceIn(1, WorldIso.MAX_Z - 4)
        val baseZ = when (buildMode) {
            0 -> max(1, pz - 1)
            2 -> min(WorldIso.MAX_Z - 1, pz + 3)
            else -> pz
        }
        val planeZ = (baseZ + buildZOffset).coerceIn(1, WorldIso.MAX_Z - 2)
        val adj = tmpVec3.y - (planeZ + (if (buildMode == 0) 1f else 0.35f)) * WorldIso.ZH
        val gx = Math.round(0.5f * (tmpVec3.x / WorldIso.TW - adj / WorldIso.TH))
        val gy = Math.round(0.5f * (-adj / WorldIso.TH - tmpVec3.x / WorldIso.TW))

        val targetZ = when (buildMode) {
            0 -> if (world.getBlock(gx, gy, planeZ) == 0 || BlockRegistry.items[world.getBlock(gx, gy, planeZ)]?.isLiquid == true) planeZ else pz
            2, 3 -> planeZ
            else -> {
                // В режиме СТЕНА: если на pz уже стоит блок стены, наращиваем вверх до 3 блоков (pz+1, pz+2)!
                var tz = pz
                while (tz < pz + 3 && tz < WorldIso.MAX_Z - 1 && world.getBlock(gx, gy, tz) > 0 && BlockRegistry.items[world.getBlock(gx, gy, tz)]?.isLiquid == false) {
                    tz++
                }
                tz
            }
        }
        return IsoPick(gx, gy, targetZ.coerceIn(1, WorldIso.MAX_Z - 1), world.getBlock(gx, gy, targetZ.coerceIn(1, WorldIso.MAX_Z - 1)))
    }

    private fun placeWithBuildOptions(gx: Int, gy: Int, tz: Int): Boolean {
        var any = false
        val height = if (buildMode == 3) 3 else 1
        for (dz in 0 until height) {
            if (tryPlaceHeldBlockAt(gx, gy, tz + dz)) any = true
            if (mirrorBuildMode) {
                val mx = mirrorCx * 2 - gx
                val my = mirrorCy * 2 - gy
                if (mx != gx || my != gy) {
                    if (tryPlaceHeldBlockAt(mx, my, tz + dz)) any = true
                }
            }
        }
        return any
    }

    private fun pickBlockIntoHand(targetId: Int): Boolean {
        if (targetId <= 0) return false
        val def = BlockRegistry.items[targetId] ?: return false
        val pickId = if (def.isItem) targetId else (if (def.dropId > 0 && BlockRegistry.items[def.dropId]?.isItem == false) def.dropId else targetId)
        for (i in 0 until 9) {
            if (player.slots[i]?.id == pickId) {
                player.selectedSlot = i
                showToast("Пипетка: ${BlockRegistry.items[pickId]?.name}")
                return true
            }
        }
        for (i in 9 until PlayerState.INV_SIZE) {
            val s = player.slots[i]
            if (s != null && s.id == pickId) {
                val cur = player.slots[player.selectedSlot]
                player.slots[player.selectedSlot] = s
                player.slots[i] = cur
                showToast("Пипетка из сумки: ${BlockRegistry.items[pickId]?.name}")
                return true
            }
        }
        if (player.creative) {
            player.slots[player.selectedSlot] = SlotItem(pickId, 64)
            showToast("Пипетка (Творч.): ${BlockRegistry.items[pickId]?.name}")
            return true
        }
        showToast("В инвентаре нет: ${BlockRegistry.items[pickId]?.name}")
        return false
    }

    private fun tryPlaceHeldBlockAt(gx: Int, gy: Int, tz: Int): Boolean {
        val held = player.slots[player.selectedSlot]
        val hDef = held?.let { BlockRegistry.items[it.id] }
        val placeSlot = if (hDef != null && !hDef.isItem) held
        else if (player.offhand != null && BlockRegistry.items[player.offhand!!.id]?.isItem == false) player.offhand
        else null
        val pSlot = placeSlot ?: return false
        val pDef = BlockRegistry.items[pSlot.id] ?: return false
        if (hypot((gx + 0.5f - player.x).toDouble(), (gy + 0.5f - player.y).toDouble()) > 5.0) return false
        if (tz !in 1 until WorldIso.MAX_Z) return false
        val cur = world.getBlock(gx, gy, tz)
        if (cur != 0 && BlockRegistry.items[cur]?.isLiquid != true) return false
        if (pDef.solid && floor(player.x).toInt() == gx && floor(player.y).toInt() == gy && (floor(player.z).toInt() == tz || floor(player.z).toInt() + 1 == tz)) return false
        if (pDef.solid && netMp.remotePlayers.values.any { rp -> floor(rp.x).toInt() == gx && floor(rp.y).toInt() == gy && (floor(rp.z).toInt() == tz || floor(rp.z).toInt() + 1 == tz) }) return false
        var placeId = pSlot.id
        if (placeId == 37) {
            val wallLeft = BlockRegistry.items[world.getBlock(gx - 1, gy, tz)]?.solid == true
            val wallRight = BlockRegistry.items[world.getBlock(gx, gy - 1, tz)]?.solid == true
            if (wallLeft) { placeId = 180; tryUnlockAchievement("wall_light") }
            else if (wallRight) { placeId = 181; tryUnlockAchievement("wall_light") }
        }
        val oldCount = pSlot.count
        if (player.takeItem(pSlot.id, 1)) {
            setWorldBlock(gx, gy, tz, placeId)
            entities.spawnBlockParticles(gx, gy, tz, pDef.colorHex, 5)
            SoundFx.play(if (placeId == 180 || placeId == 181) "wall_torch" else "mine", 0.45f)
            lastPlacedX = gx; lastPlacedY = gy; lastPlacedZ = tz; lastPlacedId = placeId; lastPlacedTime = elapsed
            if (oldCount <= 1 && player.slots[player.selectedSlot] == null) {
                for (bi in 9 until PlayerState.INV_SIZE) {
                    val bs = player.slots[bi]
                    if (bs != null && bs.id == pSlot.id) {
                        player.slots[player.selectedSlot] = bs
                        player.slots[bi] = null
                        showToast("Авто-пополнение: ${pDef.name}")
                        break
                    }
                }
            }
            return true
        }
        return false
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
        "lever" -> "ПЕРЕКЛЮЧИТЬ РЫЧАГ"
        "button" -> "НАЖАТЬ КНОПКУ"
        "noteblock" -> "ИГРАТЬ НОТУ"
        "iron_door" -> "ЖЕЛЕЗНАЯ ДВЕРЬ"
        "enchant" -> "СТОЛ ЗАЧАРОВАНИЙ"
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
        val strMult = if (player.strengthBuffTimer > 0f) 1.5f else 1f
        val dmg = (((hDef?.damage ?: 4) + (if (isFlint) 5 else player.weaponUpgradeLvl * 2)) * (if (isCrit) 1.5f else 1f) * strMult).toFloat()
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
            if (isRuby || profiles.active.enchLifesteal) {
                player.hp = min(player.maxHp, player.hp + 2f)
                entities.floatingTexts.add(FloatingText(player.x, player.y, player.z + 1.5f, "+2 HP", Color(1f, 0.35f, 0.55f, 1f)))
            }
            if (isFlint) entities.spawnBlockParticles(floor(target.x).toInt(), floor(target.y).toInt(), floor(target.z).toInt(), 0xFF7718, 6)
            autoAttackCd = if (isWeapon) 0.46f else 0.58f
        }
        consumeToolDurability(isWeapon = true)
    }

    private fun digStairUpAt(gx: Int, gy: Int, pz: Int) {
        var broke = false
        for (dz in 1..3) {
            val z = pz + dz
            if (z in 1 until WorldIso.MAX_Z) {
                val b = world.getBlock(gx, gy, z)
                if (b > 0 && (BlockRegistry.items[b]?.hardness ?: 999f) < 900f) {
                    setWorldBlock(gx, gy, z, 0)
                    if (!player.creative) BlockRegistry.items[b]?.dropId?.let { if (it > 0) entities.spawnDrop(it, 1, gx + 0.5f, gy + 0.5f, z.toFloat()) }
                    entities.spawnBlockParticles(gx, gy, z, BlockRegistry.items[b]?.colorHex ?: 0x888888, 4)
                    broke = true
                }
            }
        }
        val stepZ = pz.coerceIn(1, WorldIso.MAX_Z - 2)
        if (world.getBlock(gx, gy, stepZ) == 0 && (player.creative || player.takeItem(4, 1) || player.takeItem(2, 1))) {
            setWorldBlock(gx, gy, stepZ, 4); broke = true
        }
        if (broke) { SoundFx.play("break", 0.55f); showToast("Ступенька ВВЕРХ пробита!") }
    }

    private fun digTunnelForwardAt(gx: Int, gy: Int, pz: Int) {
        var broke = false
        for (step in 1..2) {
            val tx = floor(player.x + player.aimGx * step).toInt()
            val ty = floor(player.y + player.aimGy * step).toInt()
            for (dz in 0..1) {
                val z = (pz + dz).coerceIn(1, WorldIso.MAX_Z - 2)
                val b = world.getBlock(tx, ty, z)
                if (b > 0 && (BlockRegistry.items[b]?.hardness ?: 999f) < 900f) {
                    setWorldBlock(tx, ty, z, 0)
                    if (!player.creative) BlockRegistry.items[b]?.dropId?.let { if (it > 0) entities.spawnDrop(it, 1, tx + 0.5f, ty + 0.5f, z.toFloat()) }
                    entities.spawnBlockParticles(tx, ty, z, BlockRegistry.items[b]?.colorHex ?: 0x888888, 4)
                    broke = true
                }
            }
        }
        if (broke) { SoundFx.play("break", 0.55f); showToast("Тоннель 1x2 пробит!") }
    }

    private fun autoSelectBestToolFor(blockDef: ItemDef) {
        if (blockDef.toolType.isEmpty()) return
        val curDef = player.slots[player.selectedSlot]?.let { BlockRegistry.items[it.id] }
        if (curDef != null && (curDef.toolType == blockDef.toolType || (blockDef.toolType == "shovel" && curDef.toolType == "pick"))) return
        var bestIdx = -1; var bestSpd = 0f
        for (i in 0 until 9) {
            val d = player.slots[i]?.let { BlockRegistry.items[it.id] } ?: continue
            val match = d.toolType == blockDef.toolType || (blockDef.toolType == "shovel" && d.toolType == "pick")
            if (match && d.mineSpeed > bestSpd) { bestSpd = d.mineSpeed; bestIdx = i }
        }
        if (bestIdx >= 0) player.selectedSlot = bestIdx
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
            138 -> if (player.takeItem(138, 1)) { player.speedBuffTimer = 45f; SoundFx.play("pickup", 0.6f); showToast("Эликсир Скорости (+45% бег)!"); return true }
            241 -> if (player.hp < player.maxHp && player.takeItem(241, 1)) {
                player.hp = min(player.maxHp, player.hp + 6f); SoundFx.play("pickup", 0.6f)
                entities.floatingTexts.add(FloatingText(player.x, player.y, player.z + 1.5f, "+6 HP", Color.GREEN))
                showToast("Бинт: +6 HP мгновенно!"); return true
            }
            242 -> if (player.takeItem(242, 1)) {
                player.hp = min(player.maxHp, player.hp + 14f); SoundFx.play("levelup", 0.6f)
                entities.floatingTexts.add(FloatingText(player.x, player.y, player.z + 1.5f, "+14 HP", Color.GREEN))
                showToast("Мега-зелье: +14 HP!"); return true
            }
            243 -> if (player.takeItem(243, 1)) { player.strengthBuffTimer = 60f; SoundFx.play("levelup", 0.6f); showToast("Эликсир Силы: +50% к урону на 60с!"); return true }
            244 -> if (player.takeItem(244, 1)) { player.shieldBuffTimer = 60f; SoundFx.play("levelup", 0.6f); showToast("Зелье Щита: +6 к броне на 60с!"); return true }
            213 -> if (player.takeItem(213, 1)) {
                player.food = min(player.maxFood, player.food + 10f); player.hp = min(player.maxHp, player.hp + 8f)
                player.minerVisionTimer = 60f; SoundFx.play("levelup", 0.6f); showToast("Золотая морковь: +8 HP и Зрение руд (60с)!"); return true
            }
            214 -> if (player.takeItem(214, 1)) {
                player.food = player.maxFood; player.hp = player.maxHp; player.shieldBuffTimer = 60f; player.warmBuffTimer = 60f
                SoundFx.play("levelup", 0.7f); showToast("Золотое яблоко: Полное исцеление + Защита!"); return true
            }
            220 -> if (player.takeItem(220, 1)) {
                player.food = min(player.maxFood, player.food + 8f); player.hp = min(player.maxHp, player.hp + 6f)
                player.bodyTemp = 95f; SoundFx.play("pickup", 0.6f); showToast("Баночка мёда: тепло и +6 HP!"); return true
            }
            223, 251 -> if (player.takeItem(held.id, 1)) {
                player.food = min(player.maxFood, player.food + hDef.healFood); player.hp = min(player.maxHp, player.hp + hDef.healHp)
                player.speedBuffTimer = max(player.speedBuffTimer, 20f); SoundFx.play("pickup", 0.6f); showToast("${hDef.name}: заряд бодрости!"); return true
            }
            234 -> if (player.takeItem(234, 1)) { // Дымовая бомба: оглушает врагов вокруг и даёт рывок!
                val pxI = floor(player.x).toInt(); val pyI = floor(player.y).toInt(); val pzI = floor(player.z).toInt()
                entities.spawnBlockParticles(pxI, pyI, pzI, 0x9098A8, 16)
                for (m in entities.mobs) if (m.hostile && hypot((m.x - player.x).toDouble(), (m.y - player.y).toDouble()) < 5.5) m.stunTimer = 3.5f
                player.speedBuffTimer = max(player.speedBuffTimer, 10f); SoundFx.play("splash", 0.65f); showToast("Дымовая бомба: враги ослеплены!"); return true
            }
            249 -> if (player.takeItem(249, 1)) { // Свиток Дома: телепорт на базу из любой глубины!
                if (world.dimension != player.spawnDim) switchDimension(player.spawnDim)
                player.x = player.spawnX; player.y = player.spawnY; player.z = world.getGroundZ(floor(player.spawnX).toInt(), floor(player.spawnY).toInt(), player.spawnZ + 2f) ?: player.spawnZ
                camWorld.position.set(isoToScreenX(player.x, player.y), isoToScreenY(player.x, player.y, player.z), 0f)
                SoundFx.play("levelup", 0.75f); showToast("Свиток Дома: вы вернулись на базу!"); return true
            }
            245 -> { // Компас: направление к базе / метке!
                val tx = customMarkerX?.toFloat() ?: player.spawnX; val ty = customMarkerY?.toFloat() ?: player.spawnY
                val dist = hypot((tx - player.x).toDouble(), (ty - player.y).toDouble()).toInt()
                showToast("Компас -> Цель (${tx.toInt()}, ${ty.toInt()}): $dist блоков")
                return true
            }
            246 -> { // Подзорная труба: переключает дальний тактический обзор!
                cameraZoom = if (cameraZoom > 1.25f) 0.75f else 1.45f
                showToast(if (cameraZoom > 1.25f) "Подзорная труба: Обзор 1.45x" else "Подзорная труба: Обычный обзор")
                return true
            }
            256 -> {
                player.ridingMode = if (player.ridingMode == "minecart") "" else "minecart"
                SoundFx.play("minecart", 0.7f); tryUnlockAchievement("rider")
                showToast(if (player.ridingMode == "minecart") "Вы сели в Вагонетку! Едьте по рельсам!" else "Вы вышли из Вагонетки")
                return true
            }
            257 -> {
                player.ridingMode = if (player.ridingMode == "boat") "" else "boat"
                SoundFx.play("boat", 0.7f); tryUnlockAchievement("rider")
                showToast(if (player.ridingMode == "boat") "Лодка на воде! Плывите быстро!" else "Вы вышли из Лодки")
                return true
            }
            258 -> {
                val on = entities.toggleOwlPet(player)
                SoundFx.play("levelup", 0.65f); if (on) tryUnlockAchievement("pet_master")
                showToast(if (on) "Сова-разведчик в небе! (Видит сундуки на карте)" else "Сова улетела отдыхать")
                return true
            }
            259 -> {
                val on = entities.toggleGolemPet(player)
                SoundFx.play("piston", 0.65f); if (on) tryUnlockAchievement("pet_master")
                showToast(if (on) "Голем-сундук призван! Тапните по нему (18 слотов)!" else "Голем отдыхает")
                return true
            }
            260 -> {
                profiles.active.cloakId = 5; profiles.saveProfiles()
                SoundFx.play("enchant", 0.7f); showToast("Крылья Небес экипированы! (+15% скорости)")
                return true
            }
            250 -> { // Часы Редстоуна: точное время + импульс редстоуна вокруг!
                val h = ((entities.worldTime * 24f + 6f) % 24f).toInt(); val mn = (((entities.worldTime * 24f + 6f) % 1f) * 60f).toInt()
                redstone.pressButton(floor(player.x).toInt(), floor(player.y).toInt(), floor(player.z).toInt(), world)
                showToast("Часы: %02d:%02d | День ${entities.dayCount} (${entities.season.ruName})".format(h, mn))
                return true
            }
        }
        if (hDef.healFood > 0 && !player.creative && (player.food < player.maxFood || player.hp < player.maxHp)) {
            if (player.foodCooldown > 0f) {
                showToast("Подождите ${"%.1f".format(player.foodCooldown)}с перед следующей едой!")
                return true
            }
            if (player.takeItem(held.id, 1)) {
                player.foodCooldown = 3.5f
                player.food = min(player.maxFood, player.food + hDef.healFood)
                player.hp = min(player.maxHp, player.hp + hDef.healHp)
                SoundFx.play("pickup", 0.55f)
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
        when (hDef?.weaponStyle) {
            "bow" -> {
                if (player.creative || player.takeItem(124, 1)) {
                    entities.fireProjectile(player.x, player.y, player.z, dx, dy, (hDef.damage + player.weaponUpgradeLvl * 2).toFloat(), false, false, true)
                    SoundFx.play("hit", 0.5f)
                } else showToast("Нет стрел!")
                return
            }
            "staff_amethyst" -> {
                entities.fireProjectile(player.x, player.y, player.z, dx, dy, (hDef.damage + player.weaponUpgradeLvl * 2).toFloat(), false, true, true)
                SoundFx.play("levelup", 0.4f); return
            }
            "staff_fire" -> {
                entities.fireProjectile(player.x, player.y, player.z, dx, dy, (hDef.damage + player.weaponUpgradeLvl * 2).toFloat(), true, false, true)
                SoundFx.play("boss", 0.35f); return
            }
            "boomerang" -> {
                entities.fireProjectile(player.x, player.y, player.z, dx, dy, (hDef.damage + player.weaponUpgradeLvl * 2).toFloat(), false, false, true)
                SoundFx.play("hit", 0.5f); return
            }
            "shuriken" -> {
                if (player.creative || player.takeItem(233, 1)) {
                    entities.fireProjectile(player.x, player.y, player.z, dx, dy, (hDef.damage + player.weaponUpgradeLvl * 2).toFloat(), false, false, true)
                    SoundFx.play("hit", 0.45f)
                }
                return
            }
            "pearl" -> {
                if (player.creative || player.takeItem(235, 1)) {
                    entities.fireProjectile(player.x, player.y, player.z, dx, dy, 2f, false, false, true, isTeleportPearl = true)
                    SoundFx.play("splash", 0.5f)
                }
                return
            }
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
        val strMult = if (player.strengthBuffTimer > 0f) 1.5f else 1f
        val dmg = (((hDef?.damage ?: 4) + (if (isFlint) 5 else player.weaponUpgradeLvl * 2)) * (if (isCrit) 1.5f else 1f) * strMult).toFloat()
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
                if (isRuby || profiles.active.enchLifesteal) {
                    player.hp = min(player.maxHp, player.hp + 2f)
                    entities.floatingTexts.add(FloatingText(player.x, player.y, player.z + 1.5f, "+2 HP", Color(1f, 0.35f, 0.55f, 1f)))
                }
                if (isFlint) entities.spawnBlockParticles(floor(m.x).toInt(), floor(m.y).toInt(), floor(m.z).toInt(), 0xFF7718, 8)
                hitMob = true
                if (!isHammer) break
            }
        }
        if (hitMob) { consumeToolDurability(isWeapon = true); if (isHammer) shakeTimer = 0.16f; return }

        // Умная копка: если тапнули с киркой по блоку ПОЛА прямо рядом с ногами -> роем удобную ступеньку вниз!
        val dist = hypot(dx.toDouble(), dy.toDouble())
        val pz = floor(player.z).toInt()
        // Умная копка вниз: только одиночным коротким тапом киркой по полу прямо перед ногами (когда сверху воздух)!
        if (!startContinuousHold && hDef?.toolType == "pick" && pick.gz == pz - 1 && dist <= 2.1 && world.getBlock(pick.gx, pick.gy, pz) == 0) {
            digStairStepAt(pick.gx, pick.gy, pz)
            consumeToolDurability(isWeapon = false)
            return
        }

        // Обычная копка любого блока в радиусе 4.8 (включая блоки, построенные другом!)
        if (dist <= 4.8 && pick.id > 0) {
            if (mineX != pick.gx || mineY != pick.gy || mineZ != pick.gz) {
                mineX = pick.gx; mineY = pick.gy; mineZ = pick.gz
                mineProgress = 0f
            }
            holdingMine = true
            if (!startContinuousHold) updateMining(0.22f)
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
                    val bedFloorZ = world.getGroundZ(pick.gx, pick.gy, pick.gz.toFloat()) ?: pick.gz.toFloat()
                    player.spawnX = pick.gx + 0.5f; player.spawnY = pick.gy + 0.5f; player.spawnZ = bedFloorZ; player.spawnDim = world.dimension
                    player.isSleeping = true
                    player.sleepBedX = pick.gx; player.sleepBedY = pick.gy; player.sleepBedZ = pick.gz
                    player.x = pick.gx + 0.5f; player.y = pick.gy + 0.5f; player.z = bedFloorZ
                    player.vx = 0f; player.vy = 0f; player.sleepElapsed = 0f; player.zzzTimer = 0.2f
                    showToast("Вы легли спать • Время ускорено (x4.5)")
                    return
                }
                "portal" -> { switchDimension(if (world.dimension == "nether") "overworld" else "nether"); return }
                "tnt" -> { explodeAt(pick.gx, pick.gy, pick.gz, 2.8f); return }
                "lever" -> { redstone.toggleLever(pick.gx, pick.gy, pick.gz, this); tryUnlockAchievement("redstone_eng"); return }
                "button" -> { redstone.pressButton(pick.gx, pick.gy, pick.gz, world); showToast("Кнопка: импульс Редстоуна (2.5с)!"); return }
                "noteblock" -> { redstone.playNoteBlock(pick.gx, pick.gy, pick.gz, entities); return }
                "iron_door" -> { showToast("Железную дверь открывает Редстоун (Рычаг / Плита / Кнопка)!"); return }
                "enchant" -> { SoundFx.play("enchant", 0.65f); screen = UiScreen.ENCHANTING; return }
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
                    if (pick.id in intArrayOf(1, 2, 42, 92) && world.getBlock(pick.gx, pick.gy, pick.gz + 1) == 0) {
                        if (player.takeItem(143, 1)) { setWorldBlock(pick.gx, pick.gy, pick.gz + 1, 54); showToast("Пшеница посажена!"); return }
                    }
                }
                144 -> {
                    if (pick.id in intArrayOf(1, 2, 92) && world.getBlock(pick.gx, pick.gy, pick.gz + 1) == 0 && player.food >= 18f) {
                        if (player.takeItem(144, 1)) { setWorldBlock(pick.gx, pick.gy, pick.gz + 1, 67); showToast("Ягодный куст посажен!"); return }
                    }
                }
                201 -> { // Пыль Редстоуна -> кладёт Провод Редстоуна (79)!
                    val tz = if (pick.id == 0) pick.gz else pick.gz + 1
                    if (tz in 1 until WorldIso.MAX_Z && world.getBlock(pick.gx, pick.gy, tz) == 0 && player.takeItem(201, 1)) {
                        setWorldBlock(pick.gx, pick.gy, tz, 79)
                        SoundFx.play("mine", 0.45f)
                        redstone.evaluateAround(pick.gx, pick.gy, tz, world, this, player, entities, null)
                        return
                    }
                }
                202 -> { // Гаечный ключ: проверяет сигнал Редстоуна и активирует механизмы!
                    val pwr = redstone.isPowered(pick.gx, pick.gy, pick.gz)
                    if (pick.id == 81 || pick.id == 82) redstone.toggleLever(pick.gx, pick.gy, pick.gz, this)
                    else showToast("Ключ: блок ${BlockRegistry.items[pick.id]?.name ?: ""} | Сигнал: ${if (pwr) "АКТИВЕН" else "НЕТ"}")
                    return
                }
                203 -> { // Железная мотыга: вспахивает Дёрн/Землю в влажную Пашню (92)!
                    if (pick.id in intArrayOf(1, 2, 42)) {
                        setWorldBlock(pick.gx, pick.gy, pick.gz, 92)
                        entities.spawnBlockParticles(pick.gx, pick.gy, pick.gz, 0x5A3A22, 6)
                        consumeToolDurability(isWeapon = false); SoundFx.play("hoe", 0.55f); tryUnlockAchievement("farmer_pro"); showToast("Вспахана грядка (Пашня)!"); return
                    }
                }
                204, 247 -> { // Лейка или Удобрение: мгновенно ускоряет рост грядок и цветов вокруг!
                    if (held.id == 247 && !player.takeItem(247, 1)) return
                    var grew = 0
                    for (dx in -2..2) for (dy in -2..2) {
                        val gx2 = pick.gx + dx; val gy2 = pick.gy + dy
                        val ground = world.getBlock(gx2, gy2, pick.gz)
                        val abv = world.getBlock(gx2, gy2, pick.gz + 1)
                        if (abv in intArrayOf(54, 67, 93, 94, 95, 96, 98)) {
                            entities.spawnRisingSpark(gx2 + 0.5f, gy2 + 0.5f, pick.gz + 1.2f, 0x48EE78, 6f)
                            val dropId = BlockRegistry.items[abv]?.dropId ?: 0
                            if (dropId > 0) { entities.spawnDrop(dropId, 1, gx2 + 0.5f, gy2 + 0.5f, (pick.gz + 1).toFloat()); grew++ }
                        } else if (ground == 1 && abv == 0 && Random.nextFloat() < 0.25f) {
                            setWorldBlock(gx2, gy2, pick.gz + 1, 68); grew++
                        }
                    }
                    if (held.id == 204) consumeToolDurability(isWeapon = false); SoundFx.play("splash", 0.6f); showToast(if (grew > 0) "Урожай вырос! (+$grew)" else "Полито водой!")
                    return
                }
                205, 206, 208, 209, 211, 212 -> { // Посадка Моркови, Картошки, Томата, Кукурузы, Арбуза и Клубники на Пашню/Землю!
                    if (pick.id in intArrayOf(92, 1, 2) && world.getBlock(pick.gx, pick.gy, pick.gz + 1) == 0) {
                        val cropBlock = when (held.id) { 205 -> 93; 206 -> 94; 208 -> 95; 209 -> 96; 211 -> 97; else -> 98 }
                        if (player.takeItem(held.id, 1)) {
                            setWorldBlock(pick.gx, pick.gy, pick.gz + 1, cropBlock)
                            SoundFx.play("mine", 0.4f); showToast("Посажено: ${BlockRegistry.items[cropBlock]?.name}!"); return
                        }
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

        // Быстрый откат (если случайно поставил блок — повторный тап за 2с сразу вернёт его в рюкзак!)
        if ((elapsed - lastPlacedTime) <= 2.2f && pick.gx == lastPlacedX && pick.gy == lastPlacedY && pick.gz == lastPlacedZ && pick.id == lastPlacedId && pick.id > 0) {
            setWorldBlock(pick.gx, pick.gy, pick.gz, 0)
            if (!player.creative) player.addItem(lastPlacedId, 1)
            lastPlacedTime = -10f
            SoundFx.play("pickup", 0.55f)
            showToast("Блок возвращён в рюкзак!")
            return
        }
        val targetZ = if (pick.id == 0) pick.gz else pick.gz + 1
        placeWithBuildOptions(pick.gx, pick.gy, targetZ)
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
            var finalDropId = d.dropId
            if (d.isOre) {
                tryUnlockAchievement("first_mine")
                if (profiles.active.enchAutoSmelt) {
                    val smelted = BlockRegistry.smeltMap[finalDropId] ?: BlockRegistry.smeltMap[id]
                    if (smelted != null) finalDropId = smelted
                }
                if (profiles.active.enchFortune > 0 && Random.nextFloat() < profiles.active.enchFortune * 0.28f) {
                    dropAmt += profiles.active.enchFortune
                }
                if (Random.nextFloat() < profiles.active.miningLevel() * 0.04f) { dropAmt += 1 }
                if (profiles.addMiningXp(10)) { SoundFx.play("levelup", 0.7f); showToast("Уровень Шахтёра: ${profiles.active.miningLevel()}!") }
            } else if (id == 54 || id == 67) {
                if (profiles.addFarmXp(8)) { SoundFx.play("levelup", 0.7f); showToast("Уровень Фермера: ${profiles.active.farmLevel()}!") }
            }
            if (finalDropId > 0 && !player.creative) entities.spawnDrop(finalDropId, dropAmt, bx + 0.5f, by + 0.5f, bz.toFloat())
            consumeToolDurability(isWeapon = false)
            luaMods.emitBlockBreak(bx, by, bz, id, held?.id ?: 0)
        }

        if (player.creative) { breakSingle(mineX, mineY, mineZ); holdingMine = false; mineProgress = 0f; return }

        val pickOnDirt = hDef?.toolType == "pick" && bDef.toolType == "shovel"
        val hasteMult = if (profiles.active.enchHaste) 1.35f else 1.0f
        val speed = (if (hDef != null && (hDef.toolType == bDef.toolType || pickOnDirt)) hDef.mineSpeed else 1.4f) * (1f + player.toolUpgradeLvl * 0.18f) * hasteMult
        mineProgress += (dt * speed) / max(0.15f, bDef.hardness)
        if (mineProgress >= 1.0f) {
            breakSingle(mineX, mineY, mineZ)
            if (hDef?.aoeMine == true) {
                breakSingle(mineX + 1, mineY, mineZ); breakSingle(mineX, mineY + 1, mineZ); breakSingle(mineX + 1, mineY + 1, mineZ)
            }
            // Рубка ствола дерева целиком + автоматическое осыпание листвы после рубки!
            if (bId in intArrayOf(5, 27, 45, 46)) {
                for (dz in 1..5) if (world.getBlock(mineX, mineY, mineZ + dz) == bId) breakSingle(mineX, mineY, mineZ + dz)
                scheduleUnsupportedLeafDecay(mineX, mineY, mineZ)
            }
            // Авто-тоннель 1x2 в шахте: пробиваешь блок на уровне ног -> блок на уровне головы ломается сам!
            if (hDef?.toolType == "pick" && (isCutawayActive || player.z < WorldIso.SEA_Z - 1) && mineZ == floor(player.z).toInt()) {
                val headB = world.getBlock(mineX, mineY, mineZ + 1)
                if (headB > 0 && (BlockRegistry.items[headB]?.hardness ?: 999f) < 900f) {
                    breakSingle(mineX, mineY, mineZ + 1)
                }
            }
            // Vein Miner для руд: выкапывает сразу 2..4 соседних блока той же руды (растёт с навыком Шахтёра!)
            if (bDef.isOre && hDef?.toolType == "pick") {
                val maxExtra = (1 + profiles.active.miningLevel() / 3).coerceAtMost(4)
                var broken = 0
                val q = ArrayDeque<Triple<Int, Int, Int>>()
                val seen = HashSet<Triple<Int, Int, Int>>()
                q.addLast(Triple(mineX, mineY, mineZ)); seen.add(Triple(mineX, mineY, mineZ))
                val dirs = arrayOf(Triple(1,0,0), Triple(-1,0,0), Triple(0,1,0), Triple(0,-1,0), Triple(0,0,1), Triple(0,0,-1))
                while (q.isNotEmpty() && broken < maxExtra) {
                    val cur = q.removeFirst()
                    for ((dx, dy, dz) in dirs) {
                        val nb = Triple(cur.first + dx, cur.second + dy, cur.third + dz)
                        if (nb.third in 1 until WorldIso.MAX_Z && seen.add(nb) && world.getBlock(nb.first, nb.second, nb.third) == bId) {
                            breakSingle(nb.first, nb.second, nb.third)
                            broken++
                            if (broken >= maxExtra) break
                            q.addLast(nb)
                        }
                    }
                }
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

    private fun hasNearbyTrunk(lx: Int, ly: Int, lz: Int): Boolean {
        for (dx in -2..2) for (dy in -2..2) for (dz in -2..1) {
            val b = world.getBlock(lx + dx, ly + dy, lz + dz)
            if (b in intArrayOf(5, 27, 45, 46)) return true
        }
        return false
    }

    private fun scheduleUnsupportedLeafDecay(cx: Int, cy: Int, cz: Int) {
        for (dz in -2..7) for (dx in -3..3) for (dy in -3..3) {
            val lx = cx + dx; val ly = cy + dy; val lz = cz + dz
            if (lz !in 1 until WorldIso.MAX_Z) continue
            val b = world.getBlock(lx, ly, lz)
            if ((b == 6 || b == 44) && !hasNearbyTrunk(lx, ly, lz)) {
                if (decayingLeaves.none { it.x == lx && it.y == ly && it.z == lz }) {
                    decayingLeaves.add(LeafDecayEntry(lx, ly, lz, 0.04f + Random.nextFloat() * 0.22f))
                }
            }
        }
    }

    private fun updateDecayingLeaves(dt: Float) {
        if (decayingLeaves.isEmpty()) return
        val it = decayingLeaves.iterator()
        val cascade = mutableListOf<Triple<Int, Int, Int>>()
        while (it.hasNext()) {
            val e = it.next()
            e.timer -= dt
            if (e.timer <= 0f) {
                it.remove()
                val b = world.getBlock(e.x, e.y, e.z)
                if ((b == 6 || b == 44) && !hasNearbyTrunk(e.x, e.y, e.z)) {
                    setWorldBlock(e.x, e.y, e.z, 0)
                    entities.spawnBlockParticles(e.x, e.y, e.z, if (b == 44) 0xF8A6CC else 0x4CB848, 3)
                    cascade += Triple(e.x, e.y, e.z)
                    if (!player.creative) {
                        val r = Random.nextFloat()
                        if (r < 0.08f) entities.spawnDrop(113, 1, e.x + 0.5f, e.y + 0.5f, e.z.toFloat())
                        else if (r < 0.16f) entities.spawnDrop(105, 1, e.x + 0.5f, e.y + 0.5f, e.z.toFloat())
                    }
                }
            }
        }
        for ((cx, cy, cz) in cascade) {
            for (dz in -1..1) for (dx in -1..1) for (dy in -1..1) {
                val lx = cx + dx; val ly = cy + dy; val lz = cz + dz
                if (lz in 1 until WorldIso.MAX_Z) {
                    val nb = world.getBlock(lx, ly, lz)
                    if ((nb == 6 || nb == 44) && !hasNearbyTrunk(lx, ly, lz) && decayingLeaves.none { it.x == lx && it.y == ly && it.z == lz }) {
                        decayingLeaves.add(LeafDecayEntry(lx, ly, lz, 0.03f + Random.nextFloat() * 0.12f))
                    }
                }
            }
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
        if (player.hp <= 0f) {
            if (!player.creative) spawnGraveChest()
            screen = UiScreen.DEATH
        }
    }

    private fun spawnGraveChest() {
        val gx = floor(player.x).toInt(); val gy = floor(player.y).toInt()
        val gz = floor(player.z).toInt().coerceIn(1, WorldIso.MAX_Z - 1)
        val grave = world.getChest(gx, gy, gz)
        var putAny = false
        for (i in 0 until PlayerState.INV_SIZE) {
            val s = player.slots[i] ?: continue
            val d = BlockRegistry.items[s.id] ?: continue
            // Оружие, кирки, броню и щит ВСЕГДА сохраняем при игроке! В могилку идёт половина сырья/ресурсов.
            val keepSafe = d.toolType.isNotEmpty() || d.weaponStyle.isNotEmpty() || d.armorSlot >= 0 || s.id == 132 || s.id == 150
            if (keepSafe || s.count <= 1) continue
            val dropHalf = s.count / 2
            val emptyIdx = grave.indexOfFirst { it == null }
            if (dropHalf > 0 && emptyIdx >= 0) {
                grave[emptyIdx] = SlotItem(s.id, dropHalf)
                s.count -= dropHalf
                putAny = true
            }
        }
        if (putAny) {
            setWorldBlock(gx, gy, gz, 35)
            customMarkerX = gx; customMarkerY = gy
            if (netMp.isConnected) netMp.broadcastChest(gx, gy, gz, grave)
            showToast("Сундук-могилка оставлен на X:$gx Y:$gy (отмечен на карте!)")
        }
    }

    private fun updatePlayerTemperatureAndBuffs(dt: Float) {
        if (player.warmBuffTimer > 0f) player.warmBuffTimer = max(0f, player.warmBuffTimer - dt)
        if (player.minerVisionTimer > 0f) player.minerVisionTimer = max(0f, player.minerVisionTimer - dt)
        if (player.speedBuffTimer > 0f) player.speedBuffTimer = max(0f, player.speedBuffTimer - dt)
        if (player.strengthBuffTimer > 0f) player.strengthBuffTimer = max(0f, player.strengthBuffTimer - dt)
        if (player.shieldBuffTimer > 0f) player.shieldBuffTimer = max(0f, player.shieldBuffTimer - dt)
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
                wakeUpFromBed("Вы встали с кровати")
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

        val speedBuff = (if (player.speedBuffTimer > 0f) 1.45f else 1.0f) * (if (player.hasSpeedBoots()) 1.20f else 1.0f)
        val inDeepDark = isCutawayActive && player.z < WorldIso.SEA_Z - 4 && (BlockRegistry.items[player.slots[player.selectedSlot]?.id ?: 0]?.lightRadius ?: 0f) <= 0f && (BlockRegistry.items[player.offhand?.id ?: 0]?.lightRadius ?: 0f) <= 0f
        val darkMult = if (inDeepDark) 0.85f else 1.0f
        val curBlockHere = world.getBlock(floor(player.x).toInt(), floor(player.y).toInt(), floor(player.z).toInt())
        val onRail = curBlockHere == 185 || curBlockHere == 186
        val rideMult = when (player.ridingMode) {
            "minecart" -> if (curBlockHere == 186) 2.65f else if (onRail) 1.95f else 0.70f
            "boat" -> if (inWater) 2.55f else 0.65f
            "deer" -> 1.65f
            else -> if (profiles.active.cloakId == 5) 1.15f else 1.0f
        }
        val waterFactor = if (player.ridingMode == "boat") 1.0f else if (inWater) 0.55f else 1.0f
        val spd = baseWalkSpeed * speedBuff * darkMult * rideMult * min(1f, inLen) * waterFactor
        player.vx += (gxDir * spd - player.vx) * min(1f, dt * 11f)
        player.vy += (gyDir * spd - player.vy) * min(1f, dt * 11f)

        if (player.isMoving) {
            player.aimGx = gxDir; player.aimGy = gyDir
            if (abs(inX) > 0.08f) player.facingRight = inX > 0f
            player.walkAnim += dt * (if (inLen > 0.85f) 11.5f else 8.5f)
        }

        val curGz = world.getGroundZ(pxI, pyI, player.z)
        // Если игрок оказался зажат (например, застроили блоками или после сна), разрешаем выйти на любую свободную соседнюю клетку!
        val isTrapped = curGz == null || BlockRegistry.items[world.getBlock(pxI, pyI, pzI)]?.solid == true

        fun canStepTo(tx: Int, ty: Int, gz: Float?): Boolean {
            if (gz != null) {
                val dz = gz - player.z
                return if (dz >= 0f) dz <= 1.25f else (-dz) <= 2.15f
            }
            if (isTrapped) {
                // Проверяем пол ниже даже при низкой крыше (1 блок высоты), чтобы выбраться из застройки
                for (zChk in (pzI - 1)..(pzI + 1)) {
                    if (zChk !in 1 until WorldIso.MAX_Z) continue
                    val below = world.getBlock(tx, ty, zChk - 1)
                    val atF = world.getBlock(tx, ty, zChk)
                    if ((BlockRegistry.items[below]?.solid == true || BlockRegistry.items[below]?.isLiquid == true) && BlockRegistry.items[atF]?.solid != true) {
                        return true
                    }
                }
            }
            return false
        }

        val nx = player.x + player.vx * dt
        val nGx = floor(nx).toInt()
        val gzX = world.getGroundZ(nGx, pyI, player.z)
        if (canStepTo(nGx, pyI, gzX)) player.x = nx

        val ny = player.y + player.vy * dt
        val nGy = floor(ny).toInt()
        val gzY = world.getGroundZ(floor(player.x).toInt(), nGy, player.z)
        if (canStepTo(floor(player.x).toInt(), nGy, gzY)) player.y = ny

        if (!onLadder) {
            val targetZ = world.getGroundZ(floor(player.x).toInt(), floor(player.y).toInt(), player.z)
            if (targetZ != null) player.z += (targetZ - player.z) * min(1f, dt * 14f)
        }

        if (player.dashCooldown > 0f) player.dashCooldown = max(0f, player.dashCooldown - dt)
        if (player.foodCooldown > 0f) player.foodCooldown = max(0f, player.foodCooldown - dt)
        if (player.attackAnim > 0f) player.attackAnim = max(0f, player.attackAnim - dt * 5f)
    }

    override fun render() {
        val dt = min(0.05f, Gdx.graphics.deltaTime)
        elapsed += dt
        if (toastTimer > 0f) toastTimer = max(0f, toastTimer - dt)
        if (dragMineCd > 0f) dragMineCd = max(0f, dragMineCd - dt)
        if (shakeTimer > 0f) shakeTimer = max(0f, shakeTimer - dt)
        if (chatLog.isNotEmpty()) {
            val updated = chatLog.map { Pair(it.first, it.second - dt) }.filter { it.second > 0f }
            chatLog.clear(); chatLog.addAll(updated)
        }

        updateOstMusic()

        if (world.worldActive && screen !in setOf(UiScreen.MAIN_MENU, UiScreen.WORLD_SLOTS, UiScreen.PAUSE, UiScreen.DEATH)) {
            updateFurnaces(dt)
            updateDecayingLeaves(dt)
            redstone.update(dt, this)
        }

        if (screen == UiScreen.PLAYING) {
            // Долгое удержание пальца на блоке (> 0.20с) запускает непрерывную копку!
            val heldIsBlockNow = player.slots[player.selectedSlot]?.let { BlockRegistry.items[it.id]?.isItem == false } == true
            if (worldTouchPtr != -1 && !worldTouchHeldActed && !isDragBuilding && !heldIsBlockNow && (elapsed - worldTouchDownTime) >= 0.20f) {
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
                if (player.z < 25f) tryUnlockAchievement("deep_miner")
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
            updateMining(dt)
            entities.update(
                dt, world, player,
                isNetClient = netMp.isConnected && !netMp.isHost,
                onHurtPlayer = ::damagePlayer,
                onExplode = ::explodeAt,
                onMobKill = ::handleMobKill,
                onPickup = { dropId, itemId, cnt -> if (player.addItem(itemId, cnt)) SoundFx.play("pickup", 0.45f); if (netMp.isConnected && !netMp.isHost) netMp.sendDropPickup(dropId) },
                onGrapplePull = { gx, gy -> player.vx = (gx - player.x) * 5.5f; player.vy = (gy - player.y) * 5.5f; showToast("Крюк-кошка!") },
                onWakeUpMorning = { wakeUpFromBed("Доброе утро! День ${entities.dayCount} (${entities.season.ruName})") }
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
                netMp.interpolateRemotePlayers(dt)
                mpPosTimer += dt
                mpHeartbeatTimer += dt
                val movedEnough = abs(player.x - lastSentX) + abs(player.y - lastSentY) + abs(player.z - lastSentZ) > 0.03f || player.attackAnim > 0.1f
                if ((mpPosTimer >= 0.066f && movedEnough) || mpHeartbeatTimer >= 0.45f) {
                    mpPosTimer = 0f; mpHeartbeatTimer = 0f
                    lastSentX = player.x; lastSentY = player.y; lastSentZ = player.z
                    netMp.sendPos(player.x, player.y, player.z, player.facingRight, player.skinId, player.attackAnim, player.isSleeping, player.slots[player.selectedSlot]?.id ?: 0, player.hp, player.maxHp)
                }
                if (netMp.isHost && netMp.remotePlayers.isNotEmpty()) {
                    mpTimeSyncTimer += dt
                    if (mpTimeSyncTimer >= 3.0f) { mpTimeSyncTimer = 0f; netMp.broadcastTime(entities.dayCount, entities.worldTime) }
                    mpMobSyncTimer += dt
                    if (mpMobSyncTimer >= 0.16f) { mpMobSyncTimer = 0f; netMp.broadcastMobs(entities.mobs) }
                    mpDropSyncTimer += dt
                    if (mpDropSyncTimer >= 0.45f) { mpDropSyncTimer = 0f; netMp.broadcastDrops(entities.drops) }
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
        val animFrame = (elapsed * 8f).toInt() and 1
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
                val di = entityDiagIndex(m.x, m.y, m.z, minSum)
                if (di in 0 until diagCount) mobsByDiag[di].add(m)
            }
        }
        entities.foxPet?.let { pt -> val di = entityDiagIndex(pt.x, pt.y, pt.z, minSum); if (di in 0 until diagCount) mobsByDiag[di].add(pt) }
        entities.owlPet?.let { pt -> val di = entityDiagIndex(pt.x, pt.y, pt.z, minSum); if (di in 0 until diagCount) mobsByDiag[di].add(pt) }
        entities.golemPet?.let { pt -> val di = entityDiagIndex(pt.x, pt.y, pt.z, minSum); if (di in 0 until diagCount) mobsByDiag[di].add(pt) }
        for (d in entities.drops) {
            if (d.z <= zMax + 1 && d.z >= zMin - 1) {
                val di = entityDiagIndex(d.x, d.y, d.z, minSum)
                if (di in 0 until diagCount) dropsByDiag[di].add(d)
            }
        }
        for (rp in netMp.remotePlayers.values) {
            val di = entityDiagIndex(rp.x, rp.y, rp.z, minSum)
            if (di in 0 until diagCount) remoteByDiag[di].add(rp)
        }
        val playerDiag = entityDiagIndex(player.x, player.y, player.z, minSum).coerceIn(0, max(0, diagCount - 1))

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
                    37 -> animFrame; 34 -> 5 + animFrame; 59 -> 11 + animFrame; 80 -> 28 + animFrame; 86 -> 36 + animFrame; 85 -> if (redstone.isButtonActive(gx, gy, gz)) 35 else 34; 87 -> if (redstone.isPowered(gx, gy, gz)) 39 else 38; 180 -> 43 + animFrame; 181 -> 45 + animFrame; 186 -> if (redstone.isPowered(gx, gy, gz)) 52 else 51; 187 -> 53 + animFrame
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
                    rpx, rpy, rp.skinId, rp.facingRight, rp.walkAnim, moving = rp.isMoving,
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
                val helmLight = if (player.hasMinerHelmet()) 310f else 0f
                val lRad = max(helmLight, max(mDef?.lightRadius ?: 0f, oDef?.lightRadius ?: 0f))
                if (lRad > 0f) {
                    handLightX = hx; handLightY = hy + 24f; handLightRad = lRad * 1.15f
                    handLightCol = if ((mDef?.lightRadius ?: 0f) > 0f) mDef!!.lightColor else if ((oDef?.lightRadius ?: 0f) > 0f) oDef!!.lightColor else 0xFFF29C
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

        // Призрачный контур (Ghost Preview) — сразу видно, куда встанет блок при стройке!
        val heldDefPrev = player.slots[player.selectedSlot]?.let { BlockRegistry.items[it.id] }
        if (screen == UiScreen.PLAYING && !player.isSleeping && heldDefPrev != null && !heldDefPrev.isItem) {
            val gPick = ghostTarget ?: run {
                val fx = floor(player.x + player.aimGx * 1.35f).toInt()
                val fy = floor(player.y + player.aimGy * 1.35f).toInt()
                val pzI = floor(player.z).toInt()
                val gz = when (buildMode) { 0 -> max(1, pzI - 1); 2 -> min(WorldIso.MAX_Z - 1, pzI + 3); else -> pzI }
                IsoPick(fx, fy, gz, world.getBlock(fx, fy, gz))
            }
            val gsX = isoToScreenX(gPick.gx.toFloat(), gPick.gy.toFloat())
            val gsY = blockScreenY(gPick.gx, gPick.gy, gPick.gz)
            val gsTop = gsY + ZH
            val pulseG = 0.28f + sin(elapsed * 6f) * 0.08f
            val canPlaceG = world.getBlock(gPick.gx, gPick.gy, gPick.gz) == 0 || BlockRegistry.items[world.getBlock(gPick.gx, gPick.gy, gPick.gz)]?.isLiquid == true
            val gBits = if (canPlaceG) Color.toFloatBits(0.32f, 0.90f, 1f, pulseG) else Color.toFloatBits(1f, 0.30f, 0.35f, pulseG)
            val gSide = if (canPlaceG) Color.toFloatBits(0.22f, 0.72f, 0.92f, pulseG * 0.75f) else Color.toFloatBits(0.85f, 0.22f, 0.28f, pulseG * 0.75f)
            drawIsoQuad(SpriteArt.whitePx, gsX - TW, gsY, gsX - TW, gsTop, gsX, gsTop - TH, gsX, gsY - TH, gSide, 0f, 0f, 1f, 1f)
            drawIsoQuad(SpriteArt.whitePx, gsX, gsY - TH, gsX, gsTop - TH, gsX + TW, gsTop, gsX + TW, gsY, gSide, 0f, 0f, 1f, 1f)
            drawIsoQuad(SpriteArt.whitePx, gsX - TW, gsTop, gsX, gsTop + TH, gsX + TW, gsTop, gsX, gsTop - TH, gBits, 0f, 0f, 1f, 1f)
            if (mirrorBuildMode) {
                val mx = mirrorCx * 2 - gPick.gx; val my = mirrorCy * 2 - gPick.gy
                val msX = isoToScreenX(mx.toFloat(), my.toFloat()); val msTop = blockScreenY(mx, my, gPick.gz + 1)
                val mBits = Color.toFloatBits(0.95f, 0.55f, 1.0f, pulseG)
                drawIsoQuad(SpriteArt.whitePx, msX - TW, msTop, msX, msTop + TH, msX + TW, msTop, msX, msTop - TH, mBits, 0f, 0f, 1f, 1f)
            }
            if (boxFillMode > 0 && boxStartGx != Int.MIN_VALUE && ghostTarget != null) {
                val x0 = min(boxStartGx, gPick.gx); val x1 = min(x0 + 9, max(boxStartGx, gPick.gx))
                val y0 = min(boxStartGy, gPick.gy); val y1 = min(y0 + 9, max(boxStartGy, gPick.gy))
                val bxBits = Color.toFloatBits(0.28f, 0.96f, 0.56f, 0.34f)
                val topH = if (boxFillMode == 2) 3 else 1
                for (bx in x0..x1) for (by in y0..y1) {
                    if (boxFillMode == 2 && bx != x0 && bx != x1 && by != y0 && by != y1) continue
                    val qx = isoToScreenX(bx.toFloat(), by.toFloat()); val qy = blockScreenY(bx, by, boxStartGz + topH)
                    drawIsoQuad(SpriteArt.whitePx, qx - TW, qy, qx, qy + TH, qx + TW, qy, qx, qy - TH, bxBits, 0f, 0f, 1f, 1f)
                }
            }
        }

        // Подсветка добываемого блока + прогресс бар
        targetPick?.let { sel ->
            if (screen == UiScreen.PLAYING && !player.isSleeping && holdingMine) {
                val sx = isoToScreenX(sel.gx.toFloat(), sel.gy.toFloat())
                val syTop = blockScreenY(sel.gx, sel.gy, sel.gz + 1)
                drawIsoQuad(SpriteArt.whitePx, sx - TW, syTop, sx, syTop + TH, sx + TW, syTop, sx, syTop - TH, Color.toFloatBits(1f, 0.86f, 0.30f, 0.30f), 0f, 0f, 1f, 1f)
                if (mineProgress > 0.02f) {
                    val crIdx = (mineProgress * 7.99f).toInt().coerceIn(0, 7)
            spriteBatch.setColor(1f, 1f, 1f, 0.92f)
            spriteBatch.draw(SpriteArt.crackFrames[crIdx], sx - 32f, syTop - 32f, 64f, 64f)
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
            spriteBatch.draw(frames[7], px - w * 0.5f, py - 10f, w * 0.5f, 16f, w, h, 1f + breath * 0.4f, 1f - breath * 0.5f, 0f)
            return
        }

        // 4-шаговый цикл ходьбы (0 -> 1 -> 0 -> 2) + живое подпрыгивание и наклон!
        val frameIdx = when {
            attackAnim > 0.35f -> 6
            !moving -> ((elapsed * 4f).toInt() and 1)
            else -> 2 + (((walkAnim * 1.45f).toInt() % 4 + 4) % 4)
        }
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
        spriteBatch.draw(SpriteArt.shadowTex, px - 22f * shSc, py - 8f, 44f * shSc, 16f * shSc)

        if (hurtFlash > 0f) spriteBatch.setColor(1f, 0.40f, 0.40f, 1f) else spriteBatch.color = Color.WHITE
        val cId = profiles.active.cloakId
        if (cId > 0) {
            if (cId == 5) {
                val flap = sin(elapsed * 9f) * 8f
                BlockRegistry.getIconRegion(260)?.let { wReg ->
                    spriteBatch.draw(wReg, px - 26f, py + 16f + hopY + flap * 0.25f, 26f, 14f, 52f, 44f, 1f, 1f, flap * 0.4f)
                }
            } else {
                val cloakCol = when (cId) { 1 -> 0xD8283C; 2 -> 0x24C868; 3 -> 0x2C1E48; else -> 0xF4C230 }
                spriteBatch.setColor(((cloakCol shr 16) and 0xFF)/255f, ((cloakCol shr 8) and 0xFF)/255f, (cloakCol and 0xFF)/255f, 0.92f)
                val sway = sin(elapsed * 8f + walkAnim) * 3f
                spriteBatch.draw(SpriteArt.whitePx, px - side * 12f - 7f + sway, py + 6f + hopY, 14f, 28f)
                spriteBatch.color = Color.WHITE
            }
        }
        val rideLift = if (player.ridingMode.isNotEmpty() && hurtSquash == player.hurtSquash) 10f else 0f
        if (rideLift > 0f) {
            val mountReg = when (player.ridingMode) {
                "minecart" -> SpriteArt.props[60]
                "boat" -> SpriteArt.props[61]
                else -> SpriteArt.mobSprites["deer"]?.get(((elapsed * 8f).toInt() and 7))
            }
            mountReg?.let { spriteBatch.draw(it, px - 32f, py - 10f, 32f, 10f, 64f, 64f, side, 1f, 0f) }
        }
        spriteBatch.draw(frames[frameIdx], px - w * 0.5f + side * lunge * 6f, py - 9f + hopY + rideLift, w * 0.5f, 9f, w, h, scaleX, scaleY, rot)
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
        val mFrame = if (m.type == "mimic" || m.type in setOf("bee", "bat", "blaze", "sky_guardian", "owl_pet")) ((elapsed * 10f).toInt() and 7)
                     else if (moving) ((m.walkAnim * 2.2f).toInt() and 7)
                     else ((elapsed * 4f + m.idlePhase).toInt() and 1)
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
        spriteBatch.draw(SpriteArt.shadowTex, mx - mSz * 0.36f * shSc, my - 8f, mSz * 0.72f * shSc, 16f * shSc)

        when {
            m.hurtFlash > 0f -> spriteBatch.setColor(1f, 0.35f, 0.35f, 1f)
            m.freezeTimer > 0f -> spriteBatch.setColor(0.55f, 0.90f, 1f, 1f)
            m.stunTimer > 0f -> spriteBatch.setColor(1f, 0.92f, 0.45f, 1f)
            m.fuse > 0f -> spriteBatch.setColor(1f, 1f, 0.45f, 1f)
            else -> spriteBatch.color = Color.WHITE
        }
        spriteBatch.draw(reg, mx - mSz * 0.5f + side * atkLunge * 7f, my - 6f + hopY, mSz * 0.5f, 6f, mSz, mSz, side * scX, scY, rot)
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

    // Автоподгонка размера шрифта — текст НИКОГДА не вылезет за края кнопки или окна!
    private fun drawFitTextBatch(f: BitmapFont, text: String, x: Float, y: Float, maxW: Float, h: Float, centered: Boolean) {
        if (text.isEmpty() || maxW <= 8f) return
        val oldSx = f.data.scaleX; val oldSy = f.data.scaleY
        glyphLayout.setText(f, text)
        if (glyphLayout.width > maxW) {
            val ratio = (maxW / glyphLayout.width).coerceAtLeast(0.48f)
            f.data.setScale(oldSx * ratio, oldSy * ratio)
            glyphLayout.setText(f, text)
        }
        val tx = if (centered) x + (maxW - glyphLayout.width) * 0.5f else x
        val ty = y + (h + glyphLayout.height) * 0.5f
        f.draw(spriteBatch, glyphLayout, tx, ty)
        if (f.data.scaleX != oldSx) f.data.setScale(oldSx, oldSy)
    }

    private fun drawCenteredText(f: BitmapFont, text: String, x: Float, y: Float, w: Float, h: Float) {
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        drawFitTextBatch(f, text, x + 6f, y, w - 12f, h, centered = true)
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
            drawFitTextBatch(fontSmall, label, x + 12f, y, w - 24f, h, centered = centered)
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
            val maxDur = BlockRegistry.items[item.id]?.maxDurability ?: 0
            if (maxDur > 0 && !player.creative) {
                val curDur = player.getEffectiveDurability(item)
                if (curDur < maxDur) {
                    val frac = (curDur.toFloat() / maxDur.toFloat()).coerceIn(0f, 1f)
                    val bw = sz - 12f; val bx = x + 6f; val by = y + 5f
                    spriteBatch.setColor(0.08f, 0.06f, 0.12f, 0.90f)
                    spriteBatch.draw(SpriteArt.whitePx, bx, by, bw, 6f)
                    if (frac > 0.5f) spriteBatch.setColor(0.24f, 0.92f, 0.44f, 1f)
                    else if (frac > 0.25f) spriteBatch.setColor(0.98f, 0.80f, 0.22f, 1f)
                    else spriteBatch.setColor(0.96f, 0.24f, 0.28f, 1f)
                    spriteBatch.draw(SpriteArt.whitePx, bx + 1f, by + 1f, (bw - 2f) * frac, 4f)
                    spriteBatch.color = Color.WHITE
                }
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
        uiSlots.clear()
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
            UiScreen.ENCHANTING -> renderEnchantingScreen(W, H)
            UiScreen.ACHIEVEMENTS -> renderAchievementsScreen(W, H)
            UiScreen.GOLEM_BAG -> renderGolemBagScreen(W, H)
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
                    player.x = player.spawnX; player.y = player.spawnY; player.z = world.getGroundZ(floor(player.spawnX).toInt(), floor(player.spawnY).toInt(), player.spawnZ + 2f) ?: player.spawnZ
                    screen = UiScreen.PLAYING
                }
            }
        }

        // Плавная иконка перетаскиваемого предмета прямо под пальцем!
        if (dragActive && dragPreviewItem != null) {
            val sz = min(W, H) * 0.11f
            drawSlotBox(dragCurX - sz * 0.5f, dragCurY - sz * 0.5f, sz, dragPreviewItem, selected = true)
        }

        if (toastTimer > 0f && toastText.isNotEmpty()) {
            glyphLayout.setText(fontSmall, toastText)
            val tw = glyphLayout.width + 36f
            val ty = if (screen == UiScreen.MAIN_MENU) H * 0.74f else H - 112f
            drawPanel((W - tw) / 2f, ty, tw, 40f, Color(0.08f, 0.05f, 0.16f, 0.90f), Color.GOLD)
            drawCenteredText(fontSmall, toastText, (W - tw) / 2f, ty, tw, 40f)
        }
    }

    private fun resolveFishingCatch() {
        fishingActive = false
        if (fishingBarPos in 0.36f..0.66f) {
            SoundFx.play("fish", 0.75f); tryUnlockAchievement("golden_fish")
            val roll = Random.nextFloat()
            val catchId = when {
                roll < 0.14f -> 154 // Золотая Рыбка!
                roll < 0.24f -> 104 // Затонувший Алмаз!
                entities.season == Season.WINTER -> 153 // Ледяной Окунь!
                entities.season == Season.AUTUMN -> 152 // Лосось!
                else -> 151 // Карась!
            }
            player.addItem(catchId, 1)
            consumeToolDurability(isWeapon = false)
            val lvlUp = profiles.addFarmXp(14)
            if (lvlUp) SoundFx.play("levelup", 0.7f)
            showToast("Улов: ${BlockRegistry.items[catchId]?.name}!" + if (lvlUp) " (Фермер ур.${profiles.active.farmLevel()}!)" else "")
        } else {
            SoundFx.play("splash", 0.5f)
            showToast("Рыба сорвалась! Попробуйте поймать в зелёной зоне.")
        }
    }

    private fun renderMainMenu(W: Float, H: Float) {
        menuPanoScroll = (menuPanoScroll + Gdx.graphics.deltaTime * 0.018f) % 1f
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        spriteBatch.color = Color.WHITE
        val pano = SpriteArt.menuPanoramaTex
        if (pano != null) {
            val u0 = menuPanoScroll
            val u1 = u0 + 0.55f
            spriteBatch.draw(pano, 0f, 0f, W, H, u0, 1f, u1, 0f)
        } else {
            coverTex?.let { spriteBatch.draw(it, 0f, 0f, W, H) }
        }
        SpriteArt.logoBannerTex?.let { lg ->
            val lw = min(W * 0.52f, H * 0.96f)
            val lh = lw * (lg.height.toFloat() / lg.width.toFloat())
            spriteBatch.draw(lg, W * 0.44f + (W * 0.54f - lw) * 0.5f, H - lh - H * 0.04f, lw, lh)
        }
        val heroW = H * 0.28f; val heroH = heroW * 1.25f
        val hFr = 2 + (((elapsed * 8f).toInt()) and 3)
        SpriteArt.skinSprites[player.skinId]?.get(hFr)?.let { reg ->
            spriteBatch.draw(reg, W * 0.64f, H * 0.14f, heroW, heroH)
        }
        SpriteArt.mobSprites["fox_pet"]?.get(((elapsed * 8f).toInt()) and 7)?.let { foxReg ->
            val fSz = H * 0.18f
            spriteBatch.draw(foxReg, W * 0.78f, H * 0.14f, fSz, fSz)
        }
        spriteBatch.end()

        val updH = max(48f, H * 0.088f)
        if (PlatformBridge.updateDownloading) {
            val uw = W * 0.46f; val ux = W - uw - 18f; val uy = 16f
            drawPanel(ux, uy, uw, updH + 14f)
            spriteBatch.projectionMatrix = camUI.combined
            spriteBatch.begin()
            SpriteArt.barFramePatch.draw(spriteBatch, ux + 14f, uy + 10f, uw - 28f, 18f)
            spriteBatch.setColor(0.24f, 0.90f, 0.46f, 1f)
            spriteBatch.draw(SpriteArt.whitePx, ux + 18f, uy + 14f, (uw - 36f) * (PlatformBridge.updateProgress / 100f).coerceIn(0f, 1f), 10f)
            spriteBatch.color = Color.WHITE
            spriteBatch.end()
            drawCenteredText(fontSmall, PlatformBridge.updateStatusText, ux, uy + 30f, uw, updH - 14f)
        } else if (PlatformBridge.updateAvailable) {
            val ghW = max(116f, W * 0.11f)
            val uw = min(W * 0.38f, 440f)
            val uy = 16f
            drawButton(W - uw - ghW - 26f, uy, uw, updH, "ОБНОВИТЬ ${PlatformBridge.updateTag}", green = true, centered = true) {
                PlatformBridge.downloadAndInstallUpdate { msg -> Gdx.app.postRunnable { showToast(msg) } }
            }
            drawButton(W - ghW - 16f, uy, ghW, updH, "GitHub", blue = true, centered = true) {
                PlatformBridge.openBrowserUrl(PlatformBridge.REPO_RELEASES_URL)
            }
        } else {
            val sw = min(W * 0.34f, 320f); val sh = max(36f, H * 0.058f)
            drawPanel(W - sw - 16f, 16f, sw, sh, badge = true)
            drawCenteredText(fontSmall, PlatformBridge.updateStatusText, W - sw - 16f, 16f, sw, sh)
        }

        val px = W * 0.03f; val py = H * 0.06f; val pw = W * 0.38f; val ph = H * 0.88f
        drawPanel(px, py, pw, ph)

        val pr = profiles.active
        val hdrH = ph * 0.14f
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        drawFitTextBatch(fontSmall, "Rust Engine v6.8 • Стройка Pro + Баланс", px + 18f, py + ph - hdrH * 0.50f, pw - 36f, hdrH * 0.42f, centered = false)
        fontSmall.color = Color(1f, 0.90f, 0.48f, 1f)
        drawFitTextBatch(fontSmall, "${pr.nick} (${pr.friendId}) • Ур.${pr.totalLevel()}", px + 18f, py + ph - hdrH * 0.96f, pw - 36f, hdrH * 0.42f, centered = false)
        fontSmall.color = Color.WHITE
        spriteBatch.end()

        val bx = px + 18f; val bw = pw - 36f
        val areaH = ph - hdrH - 28f
        val gap = 10f
        val bh = (areaH - gap * 5f) / 6f
        var by = py + ph - hdrH - 12f - bh
        drawButton(bx, by, bw, bh, "МОИ МИРЫ (ИГРАТЬ)", green = true, centered = true) { profiles.loadWorldSlots(); screen = UiScreen.WORLD_SLOTS }; by -= bh + gap
        drawButton(bx, by, bw, bh, "ПРОФИЛЬ И ДРУЗЬЯ (${pr.friends.size})", blue = true, centered = true) { screen = UiScreen.PROFILES_FRIENDS }; by -= bh + gap
        drawButton(bx, by, bw, bh, "ДОСТИЖЕНИЯ (${pr.achievements.size}/${profiles.achievementDefs.size})", centered = true) { screen = UiScreen.ACHIEVEMENTS }; by -= bh + gap
        drawButton(bx, by, bw, bh, "ГАРДЕРОБ И ПЛАЩИ", centered = true) { screen = UiScreen.WARDROBE }; by -= bh + gap
        drawButton(bx, by, bw, bh, "МУЛЬТИПЛЕЕР (ОНЛАЙН / LAN)", centered = true) { screen = UiScreen.MULTIPLAYER }; by -= bh + gap
        drawButton(bx, by, bw, bh, "НАСТРОЙКИ И МОДЫ", centered = true) { settingsTab = 0; screen = UiScreen.SETTINGS_MODHUB }
    }

    private fun renderWorldSlotsScreen(W: Float, H: Float) {
        val px = W * 0.06f; val py = H * 0.05f; val pw = W * 0.88f; val ph = H * 0.90f
        drawPanel(px, py, pw, ph)
        val topH = ph * 0.11f; val topY = py + ph - topH - 10f

        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        drawFitTextBatch(font, "МОИ МИРЫ (250 СЛОЁВ)", px + 22f, topY, pw * 0.56f, topH, centered = false)
        spriteBatch.end()
        drawButton(px + pw - topH * 2.2f - 14f, topY, topH * 2.1f, topH, "Назад", red = true, centered = true) { screen = UiScreen.MAIN_MENU }
        drawButton(px + pw - topH * 5.2f - 24f, topY, topH * 2.9f, topH, "Копия в Docs", blue = true, centered = true) {
            showToast(PlatformBridge.backupWorldsToDocuments(storageRoot))
        }

        val actH = ph * 0.12f; val actY = topY - actH - 12f
        val halfW = (pw - 48f) * 0.5f
        drawButton(px + 18f, actY, halfW, actH, "+ НОВЫЙ МИР (ВЫЖИВАНИЕ)", green = true, centered = true) {
            Gdx.input.getTextInput(object : Input.TextInputListener {
                override fun input(text: String) {
                    val nm = text.trim().take(20).ifBlank { "Мир_${profiles.worldSlots.size + 1}" }
                    worldFileName = "world_${System.currentTimeMillis()}.vhworld"
                    startWorld(false, false, null, nm)
                }
                override fun canceled() {}
            }, "Название нового мира", "Мир_${profiles.worldSlots.size + 1}", "")
        }
        drawButton(px + 30f + halfW, actY, halfW, actH, "+ СВОЙ СИД / ТВОРЧЕСТВО", blue = true, centered = true) {
            Gdx.input.getTextInput(object : Input.TextInputListener {
                override fun input(text: String) {
                    val sd = text.trim().toIntOrNull() ?: text.hashCode()
                    worldFileName = "world_${System.currentTimeMillis()}.vhworld"
                    startWorld(false, true, sd, "Сид $sd")
                }
                override fun canceled() {}
            }, "Введите Сид мира (число или слово)", "${Random.nextInt(10000, 99999)}", "")
        }

        val listTop = actY - 14f; val listH = listTop - (py + 16f)
        val rowH = (listH - 40f) / 5f
        if (profiles.worldSlots.isEmpty()) {
            drawButton(px + 18f, listTop - rowH * 1.4f, pw - 36f, rowH * 1.3f, "БЫСТРЫЙ СТАРТ (Создать первый мир)", green = true, centered = true) {
                worldFileName = "world_default.vhworld"
                startWorld(false, false, null, "Основной мир")
            }
        } else {
            val delW = max(124f, pw * 0.15f)
            val mainW = pw - delW - 48f
            profiles.worldSlots.take(5).forEachIndexed { idx, slot ->
                val ry = listTop - (idx + 1) * (rowH + 8f) + 8f
                val modeTxt = if (slot.creative) "Творчество" else "Выживание"
                drawButton(px + 18f, ry, mainW, rowH, "${slot.title}  |  $modeTxt  |  Сид: ${slot.seed}", green = true) {
                    worldFileName = slot.fileName; currentWorldTitle = slot.title
                    startWorld(true, slot.creative, slot.seed, slot.title)
                }
                drawButton(px + 30f + mainW, ry, delW, rowH, "Удалить", red = true, centered = true) {
                    profiles.deleteWorldSlot(slot.fileName); showToast("Мир удалён")
                }
            }
        }
    }

    private fun renderEnchantingScreen(W: Float, H: Float) {
        val px = W * 0.07f; val py = H * 0.06f; val pw = W * 0.86f; val ph = H * 0.88f
        activePanelBounds.set(px, py, pw, ph)
        drawPanel(px, py, pw, ph)
        val topH = ph * 0.11f; val topY = py + ph - topH - 10f
        val pr = profiles.active

        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        drawFitTextBatch(font, "СТОЛ ЗАЧАРОВАНИЙ — РУНЫ", px + 22f, topY, pw * 0.56f, topH, centered = false)
        fontSmall.draw(spriteBatch, "Аметисты: ${player.countItem(141)} | Рубины: ${player.countItem(142)} | Изумруды: ${player.countItem(118)}", px + pw * 0.44f, topY + topH * 0.56f)
        spriteBatch.end()
        drawButton(px + pw - topH * 1.2f - 12f, topY, topH * 1.1f, topH, "X", red = true, centered = true) { screen = UiScreen.PLAYING }

        val listTop = topY - 14f; val listH = listTop - (py + 16f)
        val rowH = (listH - 36f) / 5f
        val bw = pw - 36f
        val bx = px + 18f

        fun drawEnchRow(idx: Int, title: String, active: Boolean, costId: Int, costCnt: Int, onBuy: () -> Unit) {
            val ry = listTop - (idx + 1) * (rowH + 8f) + 8f
            val can = !active && (player.creative || player.countItem(costId) >= costCnt)
            val cName = BlockRegistry.items[costId]?.name ?: ""
            val lbl = if (active) "[АКТИВНО] $title" else "$title — Цена: $costCnt $cName"
            drawButton(bx, ry, bw, rowH, lbl, green = active || can, blue = active) {
                if (active) { showToast("Это зачарование уже активно!"); return@drawButton }
                if (can && (player.creative || player.takeItem(costId, costCnt))) {
                    onBuy(); profiles.saveProfiles(); SoundFx.play("enchant", 0.8f); tryUnlockAchievement("enchanter")
                } else showToast("Не хватает: $costCnt $cName")
            }
        }

        drawEnchRow(0, "АВТО-ПЛАВКА (Руды сразу падают готовыми слитками!)", pr.enchAutoSmelt, 142, 2) { pr.enchAutoSmelt = true; showToast("Наложено: Авто-плавка!") }
        drawEnchRow(1, "УДАЧА Ур.${pr.enchFortune}/3 (До x${pr.enchFortune + 2} алмазов, рубинов и редстоуна!)", pr.enchFortune >= 3, 141, 2 + pr.enchFortune) { pr.enchFortune++; showToast("Наложено: Удача ур.${pr.enchFortune}!") }
        drawEnchRow(2, "ПОДЖОГ (С животных сразу падают жареные стейки!)", pr.enchFireAspect, 142, 2) { pr.enchFireAspect = true; showToast("Наложено: Поджог!") }
        drawEnchRow(3, "ВАМПИРИЗМ (+2 HP за каждый удар любым мечом!)", pr.enchLifesteal, 118, 4) { pr.enchLifesteal = true; showToast("Наложено: Вампиризм!") }
        drawEnchRow(4, "СПЕШКА (+35% скорости копки всеми инструментами!)", pr.enchHaste, 141, 3) { pr.enchHaste = true; showToast("Наложено: Спешка!") }
    }

    private fun renderAchievementsScreen(W: Float, H: Float) {
        val px = W * 0.05f; val py = H * 0.05f; val pw = W * 0.90f; val ph = H * 0.90f
        drawPanel(px, py, pw, ph)
        val topH = ph * 0.11f; val topY = py + ph - topH - 10f
        val pr = profiles.active

        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        drawFitTextBatch(font, "КНИГА ДОСТИЖЕНИЙ (${pr.achievements.size}/${profiles.achievementDefs.size})", px + 22f, topY, pw * 0.72f, topH, centered = false)
        spriteBatch.end()
        drawButton(px + pw - topH * 2.1f - 14f, topY, topH * 2.0f, topH, "Назад", red = true, centered = true) { screen = if (world.worldActive) UiScreen.PLAYING else UiScreen.MAIN_MENU }

        val gridTop = topY - 12f; val gridH = gridTop - (py + 16f)
        val colW = (pw - 48f) * 0.5f
        val rowH = (gridH - 40f) / 6f
        profiles.achievementDefs.forEachIndexed { idx, ach ->
            val unlocked = pr.achievements.contains(ach.id)
            val cx = px + 18f + (idx % 2) * (colW + 12f)
            val cy = gridTop - (idx / 2 + 1) * (rowH + 7f) + 7f
            val status = if (unlocked) "[ГОТОВО] " else ""
            drawButton(cx, cy, colW, rowH, "$status${ach.title}: ${ach.desc} (+${ach.rewardEmeralds} Изумр.)", green = unlocked) {}
        }
    }

    private fun renderGolemBagScreen(W: Float, H: Float) {
        val px = W * 0.05f; val py = H * 0.04f; val pw = W * 0.90f; val ph = H * 0.92f
        activePanelBounds.set(px, py, pw, ph)
        drawPanel(px, py, pw, ph)
        val topH = ph * 0.10f; val topY = py + ph - topH - 8f

        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin(); drawFitTextBatch(font, "ГОЛЕМ-НОСИЛЬЩИК (18 СЛОТОВ)", px + 18f, topY, pw * 0.58f, topH, centered = false); spriteBatch.end()
        drawButton(px + pw - topH * 1.15f - 12f, topY, topH * 1.1f, topH, "X", red = true, centered = true) { screen = UiScreen.PLAYING }
        drawButton(px + pw - topH * 1.15f - pw * 0.22f - 22f, topY, pw * 0.22f, topH, "Сумка: стр.${chestBagPage + 1}/2", blue = true, centered = true) { chestBagPage = 1 - chestBagPage }

        val availH = topY - (py + 22f)
        val cell = min((pw - 36f) / 9f, (availH - 18f) / 5f)
        val gx0 = px + (pw - cell * 9f) * 0.5f
        for (i in 0 until 18) {
            val sx = gx0 + (i % 9) * cell
            val sy = topY - 8f - (i / 9 + 1) * cell
            drawInteractiveSlot("golem", i, sx + 2f, sy + 2f, cell - 4f, player.golemBag[i])
        }
        val bagTop = topY - 8f - cell * 2f - 14f
        val bagOff = chestBagPage * 27
        for (i in 0 until 27) {
            val invIdx = bagOff + i
            val sx = gx0 + (i % 9) * cell
            val sy = bagTop - (i / 9 + 1) * cell
            drawInteractiveSlot("inv", invIdx, sx + 2f, sy + 2f, cell - 4f, player.slots[invIdx], selected = invIdx == player.selectedSlot)
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
                    if (ok) tryUnlockAchievement("online_friend"); showToast(if (ok) "Друг $text добавлен!" else "Неверный или уже добавленный ID")
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

    private fun drawInteractiveSlot(zone: String, idx: Int, x: Float, y: Float, sz: Float, item: SlotItem?, selected: Boolean = false) {
        val isBeingDragged = dragActive && dragFromZone == zone && dragFromIdx == idx
        drawSlotBox(x, y, sz, if (isBeingDragged) null else item, selected = selected)
        uiSlots += UiSlotHit(zone, idx, Rectangle(x, y, sz, sz))
    }

    private fun getSlotRef(zone: String, idx: Int): SlotItem? = when (zone) {
        "inv" -> if (idx in 0 until PlayerState.INV_SIZE) player.slots[idx] else null
        "offhand" -> player.offhand
        "armor" -> if (idx in 0..3 && player.armor[idx] > 0) SlotItem(player.armor[idx], 1) else null
        "chest" -> openChestPos?.let { p -> world.getChest(p.first, p.second, p.third).getOrNull(idx) }
        "golem" -> if (idx in 0 until 18) player.golemBag[idx] else null
        "furnace_in" -> openFurnacePos?.let { p -> world.getFurnace(p.first, p.second, p.third).input }
        "furnace_fuel" -> openFurnacePos?.let { p -> world.getFurnace(p.first, p.second, p.third).fuel }
        "furnace_out" -> openFurnacePos?.let { p -> world.getFurnace(p.first, p.second, p.third).output }
        else -> null
    }

    private fun setSlotRef(zone: String, idx: Int, item: SlotItem?) {
        when (zone) {
            "inv" -> if (idx in 0 until PlayerState.INV_SIZE) player.slots[idx] = item
            "offhand" -> player.offhand = item
            "armor" -> if (idx in 0..3) player.armor[idx] = item?.id ?: 0
            "chest" -> openChestPos?.let { p ->
                val arr = world.getChest(p.first, p.second, p.third)
                if (idx in 0 until 27) { arr[idx] = item; netMp.broadcastChest(p.first, p.second, p.third, arr) }
            }
            "furnace_in" -> openFurnacePos?.let { p -> world.getFurnace(p.first, p.second, p.third).input = item }
            "furnace_fuel" -> openFurnacePos?.let { p -> world.getFurnace(p.first, p.second, p.third).fuel = item }
            "furnace_out" -> openFurnacePos?.let { p -> world.getFurnace(p.first, p.second, p.third).output = item }
            "golem" -> if (idx in 0 until 18) player.golemBag[idx] = item
        }
    }

    private fun handleSlotDrop(fromZ: String, fromI: Int, toZ: String, toI: Int) {
        if (fromZ == toZ && fromI == toI) return
        val src = getSlotRef(fromZ, fromI) ?: return
        val dst = getSlotRef(toZ, toI)

        // Валидация слотов брони и выходного слота печи
        if (toZ == "furnace_out") return
        if (toZ == "armor") {
            val sDef = BlockRegistry.items[src.id]
            if (sDef == null || sDef.armorSlot != toI) { showToast("Сюда подходит только броня этого слота!"); return }
            if (src.count > 1) return
        }
        if (fromZ == "armor" && dst != null) {
            val dDef = BlockRegistry.items[dst.id]
            if (dDef == null || dDef.armorSlot != fromI) return
        }
        if (toZ == "furnace_in" && !BlockRegistry.smeltMap.containsKey(src.id)) {
            showToast("Это нельзя переплавить в печи!"); return
        }
        if (toZ == "furnace_fuel" && (BlockRegistry.items[src.id]?.fuelPower ?: 0f) <= 0f) {
            showToast("Это не топливо (нужен Уголь, Доски или Брёвна)!"); return
        }

        // Объединяем стаки, если одинаковый ID!
        if (dst != null && dst.id == src.id && dst.count < 64 && toZ != "armor") {
            val move = min(64 - dst.count, src.count)
            dst.count += move
            src.count -= move
            if (src.count <= 0) setSlotRef(fromZ, fromI, null) else setSlotRef(fromZ, fromI, src)
            setSlotRef(toZ, toI, dst)
        } else {
            // Меняем местами!
            setSlotRef(toZ, toI, src)
            setSlotRef(fromZ, fromI, dst)
        }
        SoundFx.play("pickup", 0.45f)
    }

    private fun handleSlotQuickTap(zone: String, idx: Int) {
        val item = getSlotRef(zone, idx)
        when (screen) {
            UiScreen.INVENTORY -> {
                if (zone == "inv") {
                    invSelectedSlot = idx
                } else if (zone == "armor" && item != null) {
                    if (player.addItem(item.id, 1)) { setSlotRef("armor", idx, null); SoundFx.play("pickup", 0.45f) }
                } else if (zone == "offhand" && item != null) {
                    if (player.addItem(item.id, item.count)) { setSlotRef("offhand", 0, null); SoundFx.play("pickup", 0.45f) }
                }
            }
            UiScreen.CHEST -> {
                val p = openChestPos ?: return
                val ch = world.getChest(p.first, p.second, p.third)
                if (zone == "chest" && item != null) {
                    if (player.addItem(item.id, item.count)) {
                        ch[idx] = null; netMp.broadcastChest(p.first, p.second, p.third, ch); SoundFx.play("pickup", 0.45f)
                    }
                } else if (zone == "inv" && item != null) {
                    val empty = ch.indexOfFirst { it == null }
                    if (empty >= 0) {
                        ch[empty] = SlotItem(item.id, item.count); player.slots[idx] = null
                        netMp.broadcastChest(p.first, p.second, p.third, ch); SoundFx.play("pickup", 0.45f)
                    }
                }
            }
            UiScreen.FURNACE -> {
                if (zone in setOf("furnace_in", "furnace_fuel", "furnace_out") && item != null) {
                    if (player.addItem(item.id, item.count)) { setSlotRef(zone, idx, null); SoundFx.play("pickup", 0.5f) }
                } else if (zone == "inv" && item != null) {
                    val f = openFurnacePos?.let { world.getFurnace(it.first, it.second, it.third) } ?: return
                    if (BlockRegistry.smeltMap.containsKey(item.id) && (f.input == null || f.input!!.id == item.id)) {
                        f.input = if (f.input == null) SlotItem(item.id, item.count) else SlotItem(item.id, f.input!!.count + item.count)
                        player.slots[idx] = null; SoundFx.play("pickup", 0.45f)
                    } else if ((BlockRegistry.items[item.id]?.fuelPower ?: 0f) > 0f && (f.fuel == null || f.fuel!!.id == item.id)) {
                        f.fuel = if (f.fuel == null) SlotItem(item.id, item.count) else SlotItem(item.id, f.fuel!!.count + item.count)
                        player.slots[idx] = null; SoundFx.play("pickup", 0.45f)
                    }
                }
            }
            UiScreen.GOLEM_BAG -> {
                if (zone == "golem" && item != null) {
                    if (player.addItem(item.id, item.count)) { player.golemBag[idx] = null; SoundFx.play("pickup", 0.45f) }
                } else if (zone == "inv" && item != null) {
                    val emp = player.golemBag.indexOfFirst { it == null }
                    if (emp >= 0) { player.golemBag[emp] = SlotItem(item.id, item.count); player.slots[idx] = null; SoundFx.play("pickup", 0.45f) }
                }
            }
            else -> {}
        }
    }

    private fun drawRecipeCard(x: Float, y: Float, w: Float, h: Float, rec: RecipeDef, canCraft: Boolean, onCraft: () -> Unit) {
        drawPanel(x, y, w, h, badge = true)
        val icoSz = min(h - 14f, 52f)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        BlockRegistry.getIconRegion(rec.outId)?.let { spriteBatch.draw(it, x + 10f, y + (h - icoSz) * 0.5f, icoSz, icoSz) }
        val outName = "${BlockRegistry.items[rec.outId]?.name ?: ""} x${rec.outCount}"
        fontSmall.color = if (canCraft) Color(0.65f, 1f, 0.72f, 1f) else Color(0.86f, 0.84f, 0.90f, 1f)
        fontSmall.draw(spriteBatch, outName, x + icoSz + 18f, y + h - 10f)
        // Мини-иконки ингредиентов с счётчиками!
        var ix = x + icoSz + 18f
        val mini = min(h * 0.40f, 26f)
        for ((reqId, reqCnt) in rec.req) {
            BlockRegistry.getIconRegion(reqId)?.let { spriteBatch.draw(it, ix, y + 8f, mini, mini) }
            val have = player.countItem(reqId)
            fontSmall.color = if (player.creative || have >= reqCnt) Color(0.55f, 0.98f, 0.62f, 1f) else Color(1f, 0.45f, 0.45f, 1f)
            val txt = "$have/$reqCnt"
            glyphLayout.setText(fontSmall, txt)
            fontSmall.draw(spriteBatch, txt, ix + mini + 4f, y + 8f + mini * 0.85f)
            ix += mini + glyphLayout.width + 14f
        }
        fontSmall.color = Color.WHITE
        spriteBatch.end()
        val btnW = min(w * 0.25f, 128f)
        drawButton(x + w - btnW - 8f, y + 8f, btnW, h - 16f, "СОЗДАТЬ", green = canCraft, centered = true) { onCraft() }
    }

    private fun renderTraderQuestsScreen(W: Float, H: Float) {
        val px = W * 0.06f; val py = H * 0.06f; val pw = W * 0.88f; val ph = H * 0.88f
        activePanelBounds.set(px, py, pw, ph)
        drawPanel(px, py, pw, ph)

        val q = profiles.currentQuest()
        val have = player.countItem(q.targetItemId)
        val canTurnIn = have >= q.targetCount
        val topH = ph * 0.12f

        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        font.draw(spriteBatch, "ЛАВКА ТОРГОВЦА", px + 22f, py + ph - topH * 0.26f)
        // Иконки валюты игрока справа от заголовка
        val curIco = min(topH * 0.62f, 34f)
        val curY = py + ph - topH * 0.82f
        val curX = px + pw * 0.44f
        BlockRegistry.getIconRegion(118)?.let { spriteBatch.draw(it, curX, curY, curIco, curIco) }
        fontSmall.draw(spriteBatch, "x${player.countItem(118)}", curX + curIco + 6f, curY + curIco * 0.78f)
        BlockRegistry.getIconRegion(103)?.let { spriteBatch.draw(it, curX + 120f, curY, curIco, curIco) }
        fontSmall.draw(spriteBatch, "x${player.countItem(103)}", curX + 120f + curIco + 6f, curY + curIco * 0.78f)
        spriteBatch.end()
        drawButton(px + pw - topH * 1.15f - 12f, py + ph - topH - 6f, topH * 1.1f, topH * 0.86f, "X", red = true, centered = true) { screen = UiScreen.PLAYING }

        // Визуальная карточка ЗАДАНИЯ с иконкой цели и полосой прогресса!
        val qH = ph * 0.19f; val qY = py + ph - topH - qH - 14f
        drawPanel(px + 18f, qY, pw - 36f, qH, badge = true)
        val qIco = min(qH - 18f, 54f)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        BlockRegistry.getIconRegion(q.targetItemId)?.let { spriteBatch.draw(it, px + 30f, qY + (qH - qIco) * 0.5f, qIco, qIco) }
        fontSmall.draw(spriteBatch, "ЗАДАНИЕ: ${q.title}", px + 42f + qIco, qY + qH - 12f)
        // Полоска прогресса задания
        val barX = px + 42f + qIco; val barW = pw * 0.44f; val barH = qH * 0.24f; val barY = qY + 14f
        SpriteArt.barFramePatch.draw(spriteBatch, barX, barY, barW, barH)
        spriteBatch.setColor(0.24f, 0.86f, 0.44f, 1f)
        spriteBatch.draw(SpriteArt.whitePx, barX + 4f, barY + 4f, (barW - 8f) * (have.toFloat() / max(1, q.targetCount)).coerceIn(0f, 1f), barH - 8f)
        spriteBatch.color = Color.WHITE
        fontSmall.draw(spriteBatch, "$have / ${q.targetCount}   (+${q.rewardEmeralds} Изумр. +${q.rewardXp} XP)", barX + barW + 14f, barY + barH * 0.9f)
        spriteBatch.end()
        drawButton(px + pw - 166f, qY + 10f, 138f, qH - 20f, "СДАТЬ", green = canTurnIn, centered = true) {
            if (canTurnIn && player.takeItem(q.targetItemId, q.targetCount)) {
                player.addItem(118, q.rewardEmeralds)
                profiles.active.miningXp += q.rewardXp / 2; profiles.active.combatXp += q.rewardXp / 2
                profiles.active.activeQuestIdx++; profiles.saveProfiles()
                SoundFx.play("levelup", 0.8f); showToast("Задание выполнено! +${q.rewardEmeralds} Изумрудов!")
            } else showToast("Соберите ещё: ${q.title}")
        }

        // Визуальные карточки товаров (2 колонки x 3 ряда: [Цена Иконка] -> [Товар Иконка] + КУПИТЬ!)
        val trades = listOf(
            Triple(150, 1, Pair(118, 2)),
            Triple(72,  2, Pair(118, 3)),
            Triple(74,  1, Pair(118, 4)),
            Triple(73,  4, Pair(103, 2)),
            Triple(141, 3, Pair(103, 2)),
            Triple(142, 2, Pair(118, 3))
        )
        val gridTop = qY - 12f
        val rowH = (gridTop - (py + 16f)) / 3f - 8f
        val colW = (pw - 52f) * 0.5f
        trades.forEachIndexed { i, (outId, outCnt, cost) ->
            val canBuy = player.creative || player.countItem(cost.first) >= cost.second
            val cx = px + 18f + (i % 2) * (colW + 16f)
            val cy = gridTop - (i / 2 + 1) * (rowH + 8f) + 8f
            drawPanel(cx, cy, colW, rowH, badge = true)
            val ico = min(rowH - 18f, 48f)
            spriteBatch.projectionMatrix = camUI.combined
            spriteBatch.begin()
            // Товар слева
            BlockRegistry.getIconRegion(outId)?.let { spriteBatch.draw(it, cx + 12f, cy + (rowH - ico) * 0.5f, ico, ico) }
            fontSmall.draw(spriteBatch, "${BlockRegistry.items[outId]?.name ?: ""} x$outCnt", cx + ico + 20f, cy + rowH - 12f)
            // Цена иконкой снизу
            val mini = min(rowH * 0.36f, 26f)
            BlockRegistry.getIconRegion(cost.first)?.let { spriteBatch.draw(it, cx + ico + 20f, cy + 10f, mini, mini) }
            fontSmall.color = if (canBuy) Color(0.55f, 0.98f, 0.65f, 1f) else Color(1f, 0.45f, 0.45f, 1f)
            fontSmall.draw(spriteBatch, "Цена: ${cost.second}", cx + ico + 26f + mini, cy + 10f + mini * 0.85f)
            fontSmall.color = Color.WHITE
            spriteBatch.end()
            val buyW = min(colW * 0.28f, 118f)
            drawButton(cx + colW - buyW - 8f, cy + 8f, buyW, rowH - 16f, "КУПИТЬ", green = canBuy, centered = true) {
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
        if (player.speedBuffTimer > 0f) { fontSmall.draw(spriteBatch, "Скорость: ${player.speedBuffTimer.toInt()}с", 16f, bY); bY -= 22f }
        if (player.strengthBuffTimer > 0f) { fontSmall.draw(spriteBatch, "Сила (+50%): ${player.strengthBuffTimer.toInt()}с", 16f, bY); bY -= 22f }
        if (player.shieldBuffTimer > 0f) { fontSmall.draw(spriteBatch, "Щит (+6 Бр.): ${player.shieldBuffTimer.toInt()}с", 16f, bY); bY -= 22f }
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
        if (entities.owlPet != null) {
            for (k in world.chests.keys) {
                val p = k.split(",")
                if (p.size >= 2) drawDot((p[0].toIntOrNull() ?: 0).toFloat(), (p[1].toIntOrNull() ?: 0).toFloat(), 1f, 0.85f, 0.18f)
            }
        }
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
            drawCenteredText(fontSmall, "Сон ($clock • x4.5) — сдвиньте стик или тапните, чтобы встать", sx, sy, sw, 48f)
        }

        // 6. БОЛЬШОЙ УДОБНЫЙ ХОТБАР (в ~1.8-2x крупнее под палец!) + HP / Броня / Еда
        val slotSz = min(H * 0.165f, W * 0.65f / 10.6f).coerceIn(72f, 134f)
        val hbW = slotSz * 10f + 22f; val hbX = (W - hbW) * 0.48f; val hbY = 10f
        val ohX = max(8f, hbX - slotSz - 8f); val ohY = hbY + 6f
        offhandSlotRect.set(ohX, ohY, slotSz - 4f, slotSz - 4f)
        drawSlotBox(ohX, ohY, slotSz - 4f, player.offhand, selected = false)

        val barH = max(22f, H * 0.032f)
        val barsTopY = hbY + slotSz + barH + 34f
        if (!player.creative) {
            val barY = hbY + slotSz + 12f; val halfW = hbW * 0.43f
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
            drawCenteredText(fontSmall, "HP ${player.hp.toInt()}/${player.maxHp.toInt()}", hbX, barY + barH + 2f, halfW, 22f)
            drawCenteredText(fontSmall, "Бр.${player.getTotalArmor()}", hbX + halfW, barY + barH + 2f, hbW - halfW * 2f, 22f)
            drawCenteredText(fontSmall, "Еда ${player.food.toInt()}/20", fdX, barY + barH + 2f, halfW, 22f)
        }

        // 7. УМНАЯ 1-ТАП КНОПКА ВЗАИМОДЕЙСТВИЯ + ПАНЕЛЬ PRO-СТРОЙКИ И КОПКИ СПРАВА!
        if (!player.isSleeping) {
            if (player.ridingMode.isNotEmpty()) {
                val rw = max(170f, W * 0.16f)
                drawButton((W - rw) * 0.5f, barsTopY + 58f, rw, max(44f, H * 0.072f), "СЛЕЗТЬ (${player.ridingMode})", red = true, centered = true) {
                    player.ridingMode = ""; showToast("Вы спешились")
                }
            }
            val nearInter = findNearestInteractive(player.x, player.y, floor(player.z).toInt(), 2.75f)
            if (nearInter != null) {
                val lbl = interactiveLabel(nearInter.id)
                val pw = max(210f, W * 0.22f)
                drawButton((W - pw) * 0.5f, barsTopY + 8f, pw, max(46f, H * 0.078f), lbl, green = true, centered = true) {
                    performInteractOrPlace(nearInter)
                }
            }

            val heldDef = player.slots[player.selectedSlot]?.let { BlockRegistry.items[it.id] }
            val rightX = hbX + hbW + 10f
            val rightW = (W - rightX - 10f).coerceAtLeast(118f)
            val btnHSmall = max(44f, slotSz * 0.54f)

            if ((heldDef != null && !heldDef.isItem) || pipetteMode) {
                val bmLbl = when (buildMode) { 0 -> "ПОЛ"; 2 -> "КРЫША"; 3 -> "СТОЛБ"; else -> "СТЕНА" } + (if (buildZOffset != 0) " ${if (buildZOffset > 0) "+$buildZOffset" else "$buildZOffset"}" else "")
                drawButton(rightX, hbY + 6f, rightW, btnHSmall * 0.88f, bmLbl, blue = true, centered = true) {
                    buildMode = (buildMode + 1) % 4; ghostTarget = null
                    showToast(when (buildMode) { 0 -> "Режим: ПОЛ"; 2 -> "Режим: КРЫША"; 3 -> "Режим: СТОЛБ (колонна 3 блока)"; else -> "Режим: СТЕНА" })
                }
                val halfRw = (rightW - 6f) * 0.5f
                val row2Y = hbY + btnHSmall * 0.94f + 10f
                drawButton(rightX, row2Y, halfRw, btnHSmall * 0.78f, "Z+", centered = true) {
                    buildZOffset = (buildZOffset + 1).coerceIn(-2, 3); showToast("Высота: ${if (buildZOffset >= 0) "+$buildZOffset" else "$buildZOffset"}")
                }
                drawButton(rightX + halfRw + 6f, row2Y, halfRw, btnHSmall * 0.78f, "Z-", centered = true) {
                    buildZOffset = (buildZOffset - 1).coerceIn(-2, 3); showToast("Высота: ${if (buildZOffset >= 0) "+$buildZOffset" else "$buildZOffset"}")
                }
                val row3Y = row2Y + btnHSmall * 0.82f + 4f
                val boxLbl = when (boxFillMode) { 1 -> "ЗАЛИВ"; 2 -> "КОМН."; else -> "ПЛОЩ." }
                drawButton(rightX, row3Y, halfRw, btnHSmall * 0.78f, boxLbl, green = boxFillMode > 0, centered = true) {
                    boxFillMode = (boxFillMode + 1) % 3
                    if (boxFillMode > 0) { fastDemolishMode = false; pipetteMode = false }
                    showToast(when (boxFillMode) { 1 -> "ЗАЛИВКА ПОЛА: тяните прямоугольник!"; 2 -> "КОМНАТА 3D: тяните периметр стен с дверью!"; else -> "Обычная стройка" })
                }
                drawButton(rightX + halfRw + 6f, row3Y, halfRw, btnHSmall * 0.78f, "СНОС", red = fastDemolishMode, centered = true) {
                    fastDemolishMode = !fastDemolishMode
                    if (fastDemolishMode) { boxFillMode = 0; pipetteMode = false }
                    showToast(if (fastDemolishMode) "СНОС ВКЛ: тап мгновенно возвращает блок!" else "Снос выключен")
                }
                val row4Y = row3Y + btnHSmall * 0.82f + 4f
                drawButton(rightX, row4Y, halfRw, btnHSmall * 0.76f, "ЗЕРК.", green = mirrorBuildMode, centered = true) {
                    mirrorBuildMode = !mirrorBuildMode
                    if (mirrorBuildMode) { mirrorCx = floor(player.x).toInt(); mirrorCy = floor(player.y).toInt() }
                    showToast(if (mirrorBuildMode) "ЗЕРКАЛЬНАЯ СТРОЙКА ВКЛ (ось X:$mirrorCx Y:$mirrorCy)" else "Зеркало выключено")
                }
                drawButton(rightX + halfRw + 6f, row4Y, halfRw, btnHSmall * 0.76f, "ПИПЕТ", blue = pipetteMode, centered = true) {
                    pipetteMode = !pipetteMode
                    if (pipetteMode) { boxFillMode = 0; fastDemolishMode = false }
                    showToast(if (pipetteMode) "ПИПЕТКА: тапните по любому блоку в мире!" else "Пипетка выключена")
                }
            } else if (heldDef?.toolType == "pick") {
                val digLbl = when (digActionMode) { 1 -> "ВВЕРХ"; 2 -> "ТОННЕЛЬ"; else -> "ВНИЗ" }
                drawButton(rightX, hbY + 6f, rightW, btnHSmall, digLbl, blue = true, centered = true) {
                    val gx = floor(player.x + player.aimGx * 1.15f).toInt()
                    val gy = floor(player.y + player.aimGy * 1.15f).toInt()
                    val pz = floor(player.z).toInt()
                    when (digActionMode) {
                        1 -> digStairUpAt(gx, gy, pz)
                        2 -> digTunnelForwardAt(gx, gy, pz)
                        else -> digStairStepAt(gx, gy, pz)
                    }
                }
                drawButton(rightX, hbY + btnHSmall + 12f, rightW, btnHSmall * 0.82f, "Режим: $digLbl", centered = true) {
                    digActionMode = (digActionMode + 1) % 3
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
        val px = W * 0.08f; val py = H * 0.07f; val pw = W * 0.84f; val ph = H * 0.86f
        activePanelBounds.set(px, py, pw, ph)
        drawPanel(px, py, pw, ph)
        val topH = ph * 0.12f
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        font.draw(spriteBatch, "КОСТЁР — ГОТОВКА И ОТВАРЫ", px + 22f, py + ph - topH * 0.25f)
        spriteBatch.end()
        drawButton(px + pw - topH * 1.15f - 12f, py + ph - topH - 6f, topH * 1.1f, topH * 0.86f, "X", red = true, centered = true) { screen = UiScreen.PLAYING }

        // Левая декоративная карточка с анимированным костром!
        val leftW = pw * 0.24f
        drawPanel(px + 18f, py + 18f, leftW, ph - topH - 36f, badge = true)
        val cfFrame = 11 + ((elapsed * 5f).toInt() and 1)
        val cfSz = min(leftW * 0.72f, 110f)
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        spriteBatch.draw(SpriteArt.props[cfFrame], px + 18f + (leftW - cfSz) * 0.5f, py + ph * 0.42f, cfSz, cfSz)
        fontSmall.draw(spriteBatch, "Тепло: 100%", px + 30f, py + ph * 0.34f)
        fontSmall.draw(spriteBatch, "HP: ${player.hp.toInt()}/${player.maxHp.toInt()}", px + 30f, py + ph * 0.26f)
        spriteBatch.end()

        // Правая колонка: визуальные карточки блюд и отваров со страницами!
        val listX = px + leftW + 26f; val listW = pw - leftW - 44f
        val cfRecs = BlockRegistry.recipes.filter { it.campfireBrew }
        val perPage = 5
        val totalPages = max(1, (cfRecs.size + perPage - 1) / perPage)
        campfirePage = campfirePage.coerceIn(0, totalPages - 1)
        val pageBarH = 42f
        val listH = ph - topH - 44f - pageBarH
        val cardH = (listH / perPage) - 6f
        val slice = cfRecs.drop(campfirePage * perPage).take(perPage)
        slice.forEachIndexed { idx, rec ->
            val can = player.creative || rec.req.all { (id, cnt) -> player.countItem(id) >= cnt }
            val cy = py + ph - topH - 16f - (idx + 1) * (cardH + 6f) + 6f
            drawRecipeCard(listX, cy, listW, cardH, rec, can) {
                if (can) {
                    rec.req.forEach { (id, cnt) -> player.takeItem(id, cnt) }
                    player.addItem(rec.outId, rec.outCount)
                    SoundFx.play("craft", 0.65f); showToast("Приготовлено: ${BlockRegistry.items[rec.outId]?.name}!")
                }
            }
        }
        drawButton(listX, py + 16f, listW * 0.32f, pageBarH, "< НАЗАД", blue = true, centered = true) { if (campfirePage > 0) campfirePage-- }
        drawCenteredText(fontSmall, "Стр. ${campfirePage + 1}/$totalPages", listX + listW * 0.34f, py + 16f, listW * 0.32f, pageBarH)
        drawButton(listX + listW * 0.68f, py + 16f, listW * 0.32f, pageBarH, "ДАЛЕЕ >", blue = true, centered = true) { if (campfirePage < totalPages - 1) campfirePage++ }
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
        val px = W * 0.08f; val py = H * 0.09f; val pw = W * 0.84f; val ph = H * 0.82f
        activePanelBounds.set(px, py, pw, ph)
        drawPanel(px, py, pw, ph)
        val topH = ph * 0.13f

        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        font.draw(spriteBatch, "НАКОВАЛЬНЯ — КУЗНИЦА", px + 22f, py + ph - topH * 0.28f)
        spriteBatch.end()
        drawButton(px + pw - topH * 1.15f - 12f, py + ph - topH - 6f, topH * 1.1f, topH * 0.86f, "X", red = true, centered = true) { screen = UiScreen.PLAYING }

        val repH = max(46f, ph * 0.11f)
        val canRepair = player.creative || player.countItem(102) >= 1 || player.countItem(4) >= 3
        drawButton(px + 18f, py + 14f, pw - 36f, repH, "ПОЧИНИТЬ ВСЁ СНАРЯЖЕНИЕ (Цена: 1 Железо или 3 Булыжника)", blue = true, centered = true) {
            if (!canRepair) { showToast("Нужен 1 Железный слиток или 3 Булыжника!"); return@drawButton }
            val cnt = player.repairAllEquipment()
            if (cnt <= 0) { showToast("Всё снаряжение уже в идеальном состоянии!"); return@drawButton }
            if (player.takeItem(102, 1) || player.takeItem(4, 3)) {
                SoundFx.play("craft", 0.75f); showToast("Починено предметов: $cnt!")
            }
        }

        // Две большие визуальные карточки ковки (Оружие слева, Инструменты справа)
        val cardW = (pw - 52f) * 0.5f; val cardH = ph - topH - repH - 44f
        val cardY = py + repH + 24f

        fun drawUpgradeCard(cx: Float, title: String, iconId: Int, lvl: Int, bonusTxt: String, cost1: Pair<Int, Int>, cost2: Pair<Int, Int>, canUp: Boolean, onForge: () -> Unit) {
            drawPanel(cx, cardY, cardW, cardH, badge = true)
            val ico = min(cardH * 0.26f, 68f)
            spriteBatch.projectionMatrix = camUI.combined
            spriteBatch.begin()
            BlockRegistry.getIconRegion(iconId)?.let { spriteBatch.draw(it, cx + (cardW - ico) * 0.5f, cardY + cardH - ico - 16f, ico, ico) }
            glyphLayout.setText(font, title)
            font.draw(spriteBatch, title, cx + (cardW - glyphLayout.width) * 0.5f, cardY + cardH - ico - 28f)
            glyphLayout.setText(fontSmall, bonusTxt)
            fontSmall.color = Color(0.65f, 0.98f, 0.72f, 1f)
            fontSmall.draw(spriteBatch, bonusTxt, cx + (cardW - glyphLayout.width) * 0.5f, cardY + cardH - ico - 62f)
            fontSmall.color = Color.WHITE

            // 5 квадратных кристаллов-индикаторов уровня заточки!
            val pipSz = min(cardW * 0.11f, 34f); val pipGap = 8f
            val totalPipW = pipSz * 5f + pipGap * 4f
            val pipX0 = cx + (cardW - totalPipW) * 0.5f
            val pipY = cardY + cardH * 0.36f
            for (i in 0 until 5) {
                val pxI = pipX0 + i * (pipSz + pipGap)
                SpriteArt.slotPatch.draw(spriteBatch, pxI, pipY, pipSz, pipSz)
                if (i < lvl) {
                    spriteBatch.setColor(1f, 0.84f, 0.24f, 1f)
                    spriteBatch.draw(SpriteArt.whitePx, pxI + 6f, pipY + 6f, pipSz - 12f, pipSz - 12f)
                    spriteBatch.color = Color.WHITE
                }
            }

            // Иконки цены заточки
            val mini = 28f
            val costY = cardY + cardH * 0.22f
            val costX = cx + cardW * 0.18f
            BlockRegistry.getIconRegion(cost1.first)?.let { spriteBatch.draw(it, costX, costY, mini, mini) }
            fontSmall.draw(spriteBatch, "${player.countItem(cost1.first)}/${cost1.second}  или", costX + mini + 6f, costY + 22f)
            BlockRegistry.getIconRegion(cost2.first)?.let { spriteBatch.draw(it, cx + cardW * 0.56f, costY, mini, mini) }
            fontSmall.draw(spriteBatch, "${player.countItem(cost2.first)}/${cost2.second}", cx + cardW * 0.56f + mini + 6f, costY + 22f)
            spriteBatch.end()

            val btnH = max(48f, cardH * 0.16f)
            drawButton(cx + 18f, cardY + 14f, cardW - 36f, btnH, if (lvl >= 5) "МАКС. УРОВЕНЬ" else "ЗАТОЧИТЬ (+1)", green = canUp, centered = true) { onForge() }
        }

        val canW = player.weaponUpgradeLvl < 5 && (player.creative || player.countItem(142) >= 1 || player.countItem(102) >= 2)
        drawUpgradeCard(px + 18f, "ОРУЖИЕ +${player.weaponUpgradeLvl}", 112, player.weaponUpgradeLvl, "Бонус урона: +${player.weaponUpgradeLvl * 2}", 142 to 1, 102 to 2, canW) {
            if (!canW) { showToast("Нужен 1 Рубин или 2 Железа!"); return@drawUpgradeCard }
            if (player.takeItem(142, 1) || player.takeItem(102, 2)) {
                player.weaponUpgradeLvl++; SoundFx.play("levelup", 0.75f); showToast("Оружие заточено до +${player.weaponUpgradeLvl}!")
            }
        }

        val canT = player.toolUpgradeLvl < 5 && (player.creative || player.countItem(141) >= 1 || player.countItem(104) >= 1)
        drawUpgradeCard(px + 34f + cardW, "КИРКИ +${player.toolUpgradeLvl}", 109, player.toolUpgradeLvl, "Скорость копки: +${player.toolUpgradeLvl * 18}%", 141 to 1, 104 to 1, canT) {
            if (!canT) { showToast("Нужен 1 Аметист или 1 Алмаз!"); return@drawUpgradeCard }
            if (player.takeItem(141, 1) || player.takeItem(104, 1)) {
                player.toolUpgradeLvl++; SoundFx.play("levelup", 0.75f); showToast("Инструменты усилены до +${player.toolUpgradeLvl}!")
            }
        }
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
        val px = W * 0.02f; val py = H * 0.03f; val pw = W * 0.96f; val ph = H * 0.94f
        activePanelBounds.set(px, py, pw, ph)
        drawPanel(px, py, pw, ph)

        // Верхняя полоса вкладок и кнопок (адаптивная высота — НИКАКИХ наложений!)
        val topH = ph * 0.11f; val topY = py + ph - topH - 10f
        drawButton(px + 16f, topY, pw * 0.24f, topH, "СУМКА (54)", green = invTab == 0, centered = true) { invTab = 0 }
        drawButton(px + 24f + pw * 0.24f, topY, pw * 0.24f, topH, "КРАФТ 2x2", green = invTab == 1, centered = true) { invTab = 1 }
        drawButton(px + pw - topH * 1.15f - 14f, topY, topH * 1.1f, topH, "X", red = true, centered = true) { invSelectedSlot = -1; screen = UiScreen.PLAYING }
        drawButton(px + pw - topH * 1.15f - pw * 0.16f - 24f, topY, pw * 0.16f, topH, "Сорт.", blue = true, centered = true) {
            player.autoSortBackpack(); SoundFx.play("pickup", 0.5f); showToast("Рюкзак отсортирован!")
        }

        val bodyTop = topY - 12f; val bodyH = bodyTop - (py + 14f)

        // ЛЕВАЯ КОЛОНКА: Анимированный Герой + 4 слота Брони + Левая рука + Карточка выбранного предмета!
        val leftW = pw * 0.27f; val leftX = px + 14f
        drawPanel(leftX, py + 14f, leftW, bodyH, badge = true)

        val aSlot = min(bodyH * 0.145f, leftW * 0.22f)
        val armorLabels = arrayOf("Ш", "Б", "П", "Н")
        for (a in 0 until 4) {
            val ax = leftX + 12f
            val ay = bodyTop - 14f - (a + 1) * (aSlot + 6f) + 6f
            val aItem = if (player.armor[a] > 0) SlotItem(player.armor[a], 1) else null
            drawInteractiveSlot("armor", a, ax, ay, aSlot, aItem, selected = false)
            if (aItem == null) drawCenteredText(fontSmall, armorLabels[a], ax, ay, aSlot, aSlot)
        }
        // Слот Левой руки справа от героя
        val ohX = leftX + leftW - aSlot - 12f; val ohY = bodyTop - 14f - aSlot
        drawInteractiveSlot("offhand", 0, ohX, ohY, aSlot, player.offhand, selected = false)
        if (player.offhand == null) drawCenteredText(fontSmall, "Л", ohX, ohY, aSlot, aSlot)

        // Живой Герой в центре левой панели
        val heroW = min(leftW * 0.44f, bodyH * 0.34f); val heroH = heroW * 1.25f
        val heroFr = when (((elapsed * 4f).toInt() % 4 + 4) % 4) { 1 -> 1; 3 -> 2; else -> 0 }
        SpriteArt.skinSprites[player.skinId]?.get(heroFr)?.let { reg ->
            spriteBatch.projectionMatrix = camUI.combined
            spriteBatch.begin()
            spriteBatch.draw(reg, leftX + (leftW - heroW) * 0.5f, bodyTop - heroH - 22f, heroW, heroH)
            fontSmall.draw(spriteBatch, "Броня: ${player.getTotalArmor()}", leftX + 14f, py + bodyH * 0.36f)
            spriteBatch.end()
        }

        // Карточка выбранного предмета (или подсказка про Drag-and-Drop!)
        val inspY = py + 22f; val inspH = bodyH * 0.30f
        val selItem = if (invSelectedSlot in 0 until PlayerState.INV_SIZE) player.slots[invSelectedSlot] else null
        val selDef = selItem?.let { BlockRegistry.items[it.id] }
        if (selItem != null && selDef != null) {
            spriteBatch.projectionMatrix = camUI.combined
            spriteBatch.begin()
            fontSmall.color = Color(1f, 0.90f, 0.42f, 1f)
            fontSmall.draw(spriteBatch, selDef.name, leftX + 12f, inspY + inspH - 4f)
            fontSmall.color = Color.WHITE
            val durSuffix = if (selDef.maxDurability > 0) " | Прочн. ${player.getEffectiveDurability(selItem)}/${selDef.maxDurability}" else ""
            val statTxt = when {
                selDef.armorDef > 0 -> "Защита: +${selDef.armorDef}$durSuffix"
                selDef.weaponStyle.isNotEmpty() || selDef.damage > 4 -> "Урон: ${selDef.damage + player.weaponUpgradeLvl * 2}$durSuffix"
                selDef.maxDurability > 0 -> "Прочность: ${player.getEffectiveDurability(selItem)}/${selDef.maxDurability}"
                selDef.healFood > 0 -> "Еда: +${selDef.healFood}  HP: +${selDef.healHp}"
                else -> "Перетащите в любой слот"
            }
            fontSmall.draw(spriteBatch, statTxt, leftX + 12f, inspY + inspH - 30f)
            spriteBatch.end()
            val btnH = max(36f, inspH * 0.36f)
            val actW = (leftW - 30f) * 0.5f
            if (selDef.armorSlot >= 0) {
                drawButton(leftX + 10f, inspY, actW, btnH, "Надеть", green = true, centered = true) {
                    val prev = player.armor[selDef.armorSlot]
                    player.armor[selDef.armorSlot] = selItem.id
                    player.slots[invSelectedSlot] = if (prev > 0) SlotItem(prev, 1) else null
                    SoundFx.play("pickup", 0.55f); showToast("Надето: ${selDef.name}")
                }
            } else {
                drawButton(leftX + 10f, inspY, actW, btnH, "В руку", blue = true, centered = true) {
                    val tmp = player.slots[player.selectedSlot]; player.slots[player.selectedSlot] = selItem
                    player.slots[invSelectedSlot] = tmp; SoundFx.play("pickup", 0.5f)
                }
            }
            drawButton(leftX + 20f + actW, inspY, actW, btnH, "Выброс", red = true, centered = true) { dropSlotItem(invSelectedSlot) }
        } else {
            spriteBatch.projectionMatrix = camUI.combined
            spriteBatch.begin()
            fontSmall.color = Color(0.75f, 0.85f, 0.96f, 1f)
            fontSmall.draw(spriteBatch, "Зажми и перетащи", leftX + 12f, inspY + inspH * 0.65f)
            fontSmall.draw(spriteBatch, "вещь в любой слот!", leftX + 12f, inspY + inspH * 0.35f)
            fontSmall.color = Color.WHITE
            spriteBatch.end()
        }

        // ПРАВАЯ ЧАСТЬ: 54-СЛОТОВЫЙ БОЛЬШОЙ ИНВЕНТАРЬ (6 рядов x 9 слотов) ИЛИ ВИЗУАЛЬНЫЙ КРАФТ 2x2!
        val rightX = leftX + leftW + 12f; val rightW = pw - leftW - 40f
        if (invTab == 0) {
            val cell = min(rightW / 9f, (bodyH - 18f) / 6f)
            val gridW = cell * 9f
            val gx0 = rightX + (rightW - gridW) * 0.5f
            // 5 рядов сумки сверху (индексы 9..53) + 1 нижний золотой ряд Хотбара (индексы 0..8)!
            for (row in 0 until 5) {
                val sy = bodyTop - (row + 1) * cell
                for (col in 0 until 9) {
                    val idx = 9 + row * 9 + col
                    val sx = gx0 + col * cell
                    drawInteractiveSlot("inv", idx, sx + 2f, sy + 2f, cell - 4f, player.slots[idx], selected = invSelectedSlot == idx)
                }
            }
            // Нижний ряд — Хотбар (0..8), визуально отделён зазором и золотой рамкой
            val hbY = bodyTop - 6f * cell - 10f
            for (col in 0 until 9) {
                val sx = gx0 + col * cell
                drawInteractiveSlot("inv", col, sx + 2f, hbY + 2f, cell - 4f, player.slots[col], selected = invSelectedSlot == col || col == player.selectedSlot)
            }
        } else {
            val pocketRecs = BlockRegistry.recipes.filter { it.pocket2x2 }.take(6)
            val cardH = (bodyH / max(1, pocketRecs.size)) - 6f
            pocketRecs.forEachIndexed { idx, rec ->
                val can = player.creative || rec.req.all { (id, cnt) -> player.countItem(id) >= cnt }
                val cy = bodyTop - (idx + 1) * (cardH + 6f) + 6f
                drawRecipeCard(rightX, cy, rightW, cardH, rec, can) {
                    if (can) {
                        rec.req.forEach { (id, cnt) -> player.takeItem(id, cnt) }
                        player.addItem(rec.outId, rec.outCount)
                        SoundFx.play("craft", 0.65f); showToast("Скрафчено: ${BlockRegistry.items[rec.outId]?.name}!")
                    }
                }
            }
        }
    }

    private fun renderWorkbenchScreen(W: Float, H: Float) {
        val px = W * 0.05f; val py = H * 0.05f; val pw = W * 0.90f; val ph = H * 0.90f
        activePanelBounds.set(px, py, pw, ph)
        drawPanel(px, py, pw, ph)
        val topH = ph * 0.11f; val topY = py + ph - topH - 10f

        val cats = arrayOf("ВСЕ", "ИНСТР.", "СТРОЙКА", "РЕДСТОУН", "ЕДА")
        val catKeys = arrayOf("all", "tool", "build", "redstone", "food")
        val tabsTotalW = pw - topH * 1.3f - 36f
        val tabW = (tabsTotalW - 24f) / 5f
        for (c in 0 until 5) {
            drawButton(px + 14f + c * (tabW + 6f), topY, tabW, topH, cats[c], green = wbCat == c, centered = true) {
                wbCat = c; wbPage = 0; wbSelectedIdx = 0
            }
        }
        drawButton(px + pw - topH * 1.15f - 14f, topY, topH * 1.1f, topH, "X", red = true, centered = true) { screen = UiScreen.PLAYING }

        val allRecs = BlockRegistry.recipes.filter { !it.campfireBrew && (wbCat == 0 || it.category == catKeys[wbCat]) }
        val perPage = 20 // 5 cols x 4 rows of visual icon tiles!
        val totalPages = max(1, (allRecs.size + perPage - 1) / perPage)
        wbPage = wbPage.coerceIn(0, totalPages - 1)
        wbSelectedIdx = wbSelectedIdx.coerceIn(0, max(0, allRecs.size - 1))

        val bodyTop = topY - 12f; val bodyH = bodyTop - (py + 16f)
        val leftW = pw * 0.50f; val leftX = px + 16f
        val pageBarH = 44f
        val gridH = bodyH - pageBarH - 10f
        val cell = min(leftW / 5f, gridH / 4f)
        val gx0 = leftX + (leftW - cell * 5f) * 0.5f

        val startIdx = wbPage * perPage
        for (i in 0 until perPage) {
            val rIdx = startIdx + i
            if (rIdx >= allRecs.size) break
            val rec = allRecs[rIdx]
            val can = player.creative || rec.req.all { (id, cnt) -> player.countItem(id) >= cnt }
            val sx = gx0 + (i % 5) * cell
            val sy = bodyTop - (i / 5 + 1) * cell
            drawSlotBox(sx + 3f, sy + 3f, cell - 6f, SlotItem(rec.outId, rec.outCount), selected = rIdx == wbSelectedIdx)
            if (can) {
                spriteBatch.projectionMatrix = camUI.combined
                spriteBatch.begin()
                spriteBatch.setColor(0.25f, 0.95f, 0.45f, 0.95f)
                spriteBatch.draw(SpriteArt.whitePx, sx + 7f, sy + cell - 13f, 8f, 8f)
                spriteBatch.color = Color.WHITE
                spriteBatch.end()
            }
            buttons += Pair(Rectangle(sx + 3f, sy + 3f, cell - 6f, cell - 6f)) { wbSelectedIdx = rIdx; SoundFx.play("mine", 0.25f) }
        }
        drawButton(leftX, py + 16f, leftW * 0.34f, pageBarH, "< НАЗАД", blue = true, centered = true) { if (wbPage > 0) wbPage-- }
        drawCenteredText(fontSmall, "Стр. ${wbPage + 1}/$totalPages", leftX + leftW * 0.35f, py + 16f, leftW * 0.30f, pageBarH)
        drawButton(leftX + leftW * 0.66f, py + 16f, leftW * 0.34f, pageBarH, "ДАЛЕЕ >", blue = true, centered = true) { if (wbPage < totalPages - 1) wbPage++ }

        // Правая карточка: крупная иконка результата + визуальные слоты ингредиентов + кнопка СКРАФТИТЬ!
        val rightX = leftX + leftW + 18f; val rightW = pw - leftW - 50f
        drawPanel(rightX, py + 16f, rightW, bodyH, badge = true)
        if (allRecs.isNotEmpty()) {
            val rec = allRecs[wbSelectedIdx]
            val can = player.creative || rec.req.all { (id, cnt) -> player.countItem(id) >= cnt }
            val bigIco = min(bodyH * 0.28f, 84f)
            spriteBatch.projectionMatrix = camUI.combined
            spriteBatch.begin()
            SpriteArt.slotSelectedPatch.draw(spriteBatch, rightX + (rightW - bigIco) * 0.5f, bodyTop - bigIco - 18f, bigIco, bigIco)
            BlockRegistry.getIconRegion(rec.outId)?.let {
                spriteBatch.draw(it, rightX + (rightW - bigIco) * 0.5f + 10f, bodyTop - bigIco - 8f, bigIco - 20f, bigIco - 20f)
            }
            val nm = "${BlockRegistry.items[rec.outId]?.name ?: ""} x${rec.outCount}"
            glyphLayout.setText(font, nm)
            font.draw(spriteBatch, nm, rightX + (rightW - glyphLayout.width) * 0.5f, bodyTop - bigIco - 30f)
            fontSmall.draw(spriteBatch, "Ингредиенты:", rightX + 20f, bodyTop - bigIco - 72f)
            var iy = bodyTop - bigIco - 108f
            for ((reqId, reqCnt) in rec.req) {
                val have = player.countItem(reqId)
                BlockRegistry.getIconRegion(reqId)?.let { spriteBatch.draw(it, rightX + 20f, iy - 12f, 34f, 34f) }
                fontSmall.color = if (player.creative || have >= reqCnt) Color(0.55f, 1f, 0.64f, 1f) else Color(1f, 0.45f, 0.45f, 1f)
                fontSmall.draw(spriteBatch, "${BlockRegistry.items[reqId]?.name ?: ""}: $have / $reqCnt", rightX + 62f, iy + 14f)
                iy -= 42f
            }
            fontSmall.color = Color.WHITE
            spriteBatch.end()

            val btnH = max(52f, bodyH * 0.16f)
            drawButton(rightX + 20f, py + 30f, rightW - 40f, btnH, "СКРАФТИТЬ x${rec.outCount}", green = can, centered = true) {
                if (can) {
                    rec.req.forEach { (id, cnt) -> player.takeItem(id, cnt) }
                    player.addItem(rec.outId, rec.outCount)
                    SoundFx.play("craft", 0.7f); showToast("Создано: ${BlockRegistry.items[rec.outId]?.name}!")
                } else showToast("Не хватает ресурсов!")
            }
        }
    }

    private fun renderFurnaceScreen(W: Float, H: Float) {
        val pos = openFurnacePos ?: return
        val f = world.getFurnace(pos.first, pos.second, pos.third)
        val px = W * 0.06f; val py = H * 0.04f; val pw = W * 0.88f; val ph = H * 0.92f
        activePanelBounds.set(px, py, pw, ph)
        drawPanel(px, py, pw, ph)
        val topH = ph * 0.10f; val topY = py + ph - topH - 8f

        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin(); font.draw(spriteBatch, "ПЕЧЬ — ПЕРЕПЛАВКА", px + 20f, topY + topH * 0.72f); spriteBatch.end()
        drawButton(px + pw - topH * 1.15f - 12f, topY, topH * 1.1f, topH, "X", red = true, centered = true) { openFurnacePos = null; screen = UiScreen.PLAYING }
        drawButton(px + pw - topH * 1.15f - pw * 0.22f - 22f, topY, pw * 0.22f, topH, "Сумка: стр.${furnaceBagPage + 1}/2", blue = true, centered = true) {
            furnaceBagPage = 1 - furnaceBagPage
        }

        // Верхняя станция печи: аккуратная горизонтальная компоновка с большими слотами и живыми барами!
        val stH = ph * 0.40f; val stY = topY - stH - 8f
        drawPanel(px + 16f, stY, pw - 32f, stH, badge = true)

        val slSz = min(stH * 0.42f, 82f)
        val inX = px + pw * 0.16f
        val fuelX = inX + slSz + 26f
        val slotsY = stY + stH * 0.42f
        val outX = px + pw * 0.62f

        drawInteractiveSlot("furnace_in", 0, inX, slotsY, slSz, f.input)
        drawInteractiveSlot("furnace_fuel", 0, fuelX, slotsY, slSz, f.fuel)
        drawInteractiveSlot("furnace_out", 0, outX, slotsY, slSz, f.output, selected = f.output != null)

        val burnFrac = (f.burn / max(1f, f.maxBurn)).coerceIn(0f, 1f)
        val cookFrac = (f.cook / 100f).coerceIn(0f, 1f)

        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin()
        // Подписи над слотами
        glyphLayout.setText(fontSmall, "Сырьё")
        fontSmall.draw(spriteBatch, "Сырьё", inX + (slSz - glyphLayout.width) * 0.5f, slotsY + slSz + 22f)
        glyphLayout.setText(fontSmall, "Топливо")
        fontSmall.draw(spriteBatch, "Топливо", fuelX + (slSz - glyphLayout.width) * 0.5f, slotsY + slSz + 22f)
        glyphLayout.setText(fontSmall, "Готово")
        fontSmall.draw(spriteBatch, "Готово", outX + (slSz - glyphLayout.width) * 0.5f, slotsY + slSz + 22f)

        // Компактная полоска ОГНЯ под слотами Сырьё + Топливо (с цифрой %)
        val fireW = (fuelX + slSz) - inX; val fireH = 22f; val fireY = stY + 20f
        SpriteArt.barFramePatch.draw(spriteBatch, inX, fireY, fireW, fireH)
        if (burnFrac > 0.01f) {
            spriteBatch.setColor(1f, 0.56f, 0.14f, 1f)
            spriteBatch.draw(SpriteArt.whitePx, inX + 4f, fireY + 4f, (fireW - 8f) * burnFrac, fireH - 8f)
            spriteBatch.color = Color.WHITE
        }
        fontSmall.draw(spriteBatch, "Огонь: ${(burnFrac * 100).toInt()}%", inX, fireY + fireH + 20f)

        // Главная полоса ПРОГРЕССА ПЛАВКИ в реальном времени между Топливом и Готово!
        val barX = fuelX + slSz + 22f; val barW = outX - barX - 22f; val barH = 26f; val barY = slotsY + (slSz - barH) * 0.5f
        SpriteArt.barFramePatch.draw(spriteBatch, barX, barY, barW, barH)
        if (cookFrac > 0.01f) {
            spriteBatch.setColor(0.28f, 0.94f, 0.50f, 1f)
            spriteBatch.draw(SpriteArt.whitePx, barX + 4f, barY + 4f, (barW - 8f) * cookFrac, barH - 8f)
            spriteBatch.color = Color.WHITE
        }
        val pctTxt = "Плавка: ${f.cook.toInt()}%"
        glyphLayout.setText(fontSmall, pctTxt)
        fontSmall.draw(spriteBatch, pctTxt, barX + (barW - glyphLayout.width) * 0.5f, barY + barH + 22f)
        spriteBatch.end()

        // Просторная кнопка «Авто-загрузка» справа (текст никогда не обрезается!)
        val autoW = max(210f, pw * 0.23f)
        val autoX = px + pw - autoW - 26f
        drawButton(autoX, slotsY + (slSz - 48f) * 0.5f, autoW, 48f, "Авто-загрузка", green = true, centered = true) {
            for (i in 0 until PlayerState.INV_SIZE) {
                val s = player.slots[i] ?: continue
                if (BlockRegistry.smeltMap.containsKey(s.id) && (f.input == null || f.input!!.id == s.id)) {
                    f.input = if (f.input == null) SlotItem(s.id, s.count) else SlotItem(s.id, f.input!!.count + s.count); player.slots[i] = null
                } else if ((BlockRegistry.items[s.id]?.fuelPower ?: 0f) > 0f && (f.fuel == null || f.fuel!!.id == s.id)) {
                    f.fuel = if (f.fuel == null) SlotItem(s.id, s.count) else SlotItem(s.id, f.fuel!!.count + s.count); player.slots[i] = null
                }
            }
            SoundFx.play("pickup", 0.5f)
        }

        // Нижняя часть: 27-слотовая сетка рюкзака (переключается 1/2 для всех 54 слотов!)
        val bagH = stY - (py + 20f)
        val cell = min((pw - 36f) / 9f, bagH / 3f)
        val gx0 = px + (pw - cell * 9f) * 0.5f
        val bagOff = furnaceBagPage * 27
        for (i in 0 until 27) {
            val invIdx = bagOff + i
            val sx = gx0 + (i % 9) * cell
            val sy = stY - 8f - (i / 9 + 1) * cell
            drawInteractiveSlot("inv", invIdx, sx + 2f, sy + 2f, cell - 4f, player.slots[invIdx], selected = invIdx == player.selectedSlot)
        }
    }

    private fun renderChestScreen(W: Float, H: Float) {
        val pos = openChestPos ?: return
        val items = world.getChest(pos.first, pos.second, pos.third)
        val px = W * 0.05f; val py = H * 0.04f; val pw = W * 0.90f; val ph = H * 0.92f
        activePanelBounds.set(px, py, pw, ph)
        drawPanel(px, py, pw, ph)
        val topH = ph * 0.10f; val topY = py + ph - topH - 8f

        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin(); font.draw(spriteBatch, "СУНДУК — ПЕРЕТАСКИВАНИЕ И БЫСТРЫЙ ТАП", px + 18f, topY + topH * 0.72f); spriteBatch.end()
        drawButton(px + pw - topH * 1.15f - 12f, topY, topH * 1.1f, topH, "X", red = true, centered = true) { openChestPos = null; screen = UiScreen.PLAYING }
        drawButton(px + pw - topH * 1.15f - pw * 0.22f - 22f, topY, pw * 0.22f, topH, "Сумка: стр.${chestBagPage + 1}/2", blue = true, centered = true) {
            chestBagPage = 1 - chestBagPage
        }

        // Двойная сетка Drag-and-Drop: сверху 27 слотов Сундука, снизу 27 слотов Инвентаря!
        val availH = topY - (py + 24f)
        val cell = min((pw - 36f) / 9f, (availH - 18f) / 6f)
        val gx0 = px + (pw - cell * 9f) * 0.5f

        for (i in 0 until 27) {
            val sx = gx0 + (i % 9) * cell
            val sy = topY - 8f - (i / 9 + 1) * cell
            drawInteractiveSlot("chest", i, sx + 2f, sy + 2f, cell - 4f, items[i])
        }
        val bagTop = topY - 8f - cell * 3f - 14f
        val bagOffset = chestBagPage * 27
        for (i in 0 until 27) {
            val invIdx = bagOffset + i
            val sx = gx0 + (i % 9) * cell
            val sy = bagTop - (i / 9 + 1) * cell
            drawInteractiveSlot("inv", invIdx, sx + 2f, sy + 2f, cell - 4f, player.slots[invIdx], selected = invIdx == player.selectedSlot)
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
                drawButton(bx, H * 0.16f, bw * 0.48f, max(48f, H * 0.08f), "Навыки и Бестиарий", green = true, centered = true) { screen = UiScreen.BESTIARY_SKILLS }
                drawButton(bx + bw * 0.52f, H * 0.16f, bw * 0.48f, max(48f, H * 0.08f), "Книга Достижений", blue = true, centered = true) { screen = UiScreen.ACHIEVEMENTS }
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
        val px = W * 0.06f; val py = H * 0.05f; val pw = W * 0.88f; val ph = H * 0.90f
        drawPanel(px, py, pw, ph)
        val topH = ph * 0.11f; val topY = py + ph - topH - 10f
        spriteBatch.projectionMatrix = camUI.combined
        spriteBatch.begin(); drawFitTextBatch(font, "ГАРДЕРОБ ГЕРОЕВ И ПЛАЩИ", px + 22f, topY, pw * 0.65f, topH, centered = false); spriteBatch.end()
        drawButton(px + pw - topH * 2.1f - 14f, topY, topH * 2.0f, topH, "Готово", green = true, centered = true) { screen = if (world.worldActive) UiScreen.PLAYING else UiScreen.MAIN_MENU }

        val leftW = pw * 0.32f
        drawPanel(px + 18f, py + 18f, leftW, topY - py - 28f, badge = true)
        val fr = 2 + (((elapsed * 8f).toInt()) and 3)
        SpriteArt.skinSprites[player.skinId]?.get(fr)?.let { reg ->
            val hw = min(leftW * 0.68f, ph * 0.34f); val hh = hw * 1.25f
            spriteBatch.projectionMatrix = camUI.combined
            spriteBatch.begin()
            spriteBatch.draw(reg, px + 18f + (leftW - hw) * 0.5f, py + ph * 0.28f, hw, hh)
            spriteBatch.end()
        }
        val cloakNames = arrayOf("Без плаща", "Алый плащ", "Изумрудный плащ", "Теневой плащ", "Золотой плащ", "Крылья Небес")
        drawButton(px + 28f, py + 30f, leftW - 20f, max(48f, ph * 0.10f), "Плащ: ${cloakNames[profiles.active.cloakId.coerceIn(0, 5)]}", blue = true, centered = true) {
            profiles.active.cloakId = (profiles.active.cloakId + 1) % cloakNames.size
            profiles.saveProfiles()
        }

        val rightX = px + leftW + 30f; val rightW = pw - leftW - 48f
        val colW = (rightW - 14f) * 0.5f
        val rowH = (topY - py - 48f) / 3f - 8f
        SKINS_LIST.forEachIndexed { idx, sk ->
            val cx = rightX + (idx % 2) * (colW + 14f)
            val cy = topY - 14f - (idx / 2 + 1) * (rowH + 10f) + 10f
            drawButton(cx, cy, colW, rowH, sk.name, green = player.skinId == sk.id, centered = true) {
                player.skinId = sk.id; profiles.active.skinId = sk.id; profiles.saveProfiles(); showToast("Герой: ${sk.name}")
            }
        }
    }

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

                // 0. Проверяем UI-кнопки
                for (i in buttons.size - 1 downTo 0) {
                    val (rect, act) = buttons[i]
                    if (rect.contains(ux, uy)) { act(); return true }
                }

                // 0.5 Drag-and-Drop слотов в Инвентаре, Сундуке и Печи!
                if (screen in setOf(UiScreen.INVENTORY, UiScreen.CHEST, UiScreen.FURNACE, UiScreen.GOLEM_BAG)) {
                    for (i in uiSlots.size - 1 downTo 0) {
                        val sl = uiSlots[i]
                        if (sl.rect.contains(ux, uy)) {
                            dragPtr = pointer
                            dragStartX = ux; dragStartY = uy
                            dragCurX = ux; dragCurY = uy
                            dragActive = false
                            dragFromZone = sl.zone; dragFromIdx = sl.index
                            dragPreviewItem = getSlotRef(sl.zone, sl.index)
                            return true
                        }
                    }
                    return false
                }

                if (screen != UiScreen.PLAYING) return false

                // 1. Тап по слоту Левой руки
                if (offhandSlotRect.contains(ux, uy)) {
                    player.swapWithOffhand()
                    showToast("Левая рука: ${BlockRegistry.items[player.offhand?.id ?: 0]?.name ?: "Пусто"}")
                    return true
                }

                // 2. Хотбар
                for (i in 0 until 9) {
                    if (hotbarSlotRects[i].contains(ux, uy)) {
                        player.selectedSlot = i
                        ghostTarget = null
                        player.slots[i]?.let { showToast(BlockRegistry.items[it.id]?.name ?: "") }
                        hotbarSwipePtr = pointer; hotbarSwipeSlot = i; hotbarSwipeStartY = uy
                        return true
                    }
                }

                // 3. Левый плавающий стик
                if (joyPtr == -1 && ux < Gdx.graphics.width * 0.36f) {
                    joyPtr = pointer
                    joyCenterX = ux.coerceIn(joyRadius + 8f, Gdx.graphics.width * 0.32f)
                    joyCenterY = uy.coerceIn(joyRadius + 8f, Gdx.graphics.height * 0.65f)
                    updateJoystick(ux, uy)
                    return true
                }

                // 4. Пинч-зум двумя пальцами
                if (worldTouchPtr != -1 && pinchPtr2 == -1) {
                    pinchPtr2 = pointer
                    val p1 = ptrPos[worldTouchPtr] ?: Pair(worldTouchStartX, Gdx.graphics.height - worldTouchStartY)
                    pinchStartDist = max(20f, hypot((p1.first - ux).toDouble(), (p1.second - uy).toDouble()).toFloat())
                    pinchStartZoom = cameraZoom
                    holdingMine = false; isDragBuilding = false; worldTouchHeldActed = true
                    return true
                }

                // 5. Начало тапа / удержания / свайп-стройки по миру!
                if (worldTouchPtr == -1) {
                    worldTouchPtr = pointer
                    worldTouchDownTime = elapsed
                    worldTouchStartX = screenX.toFloat()
                    worldTouchStartY = screenY.toFloat()
                    worldTouchHeldActed = false
                    isDragBuilding = false
                    dragBuildLastX = Int.MIN_VALUE
                    val heldDef = player.slots[player.selectedSlot]?.let { BlockRegistry.items[it.id] }
                    if (heldDef != null && !heldDef.isItem) {
                        val c0 = pickBuildCellOnPlane(screenX.toFloat(), screenY.toFloat())
                        ghostTarget = c0
                        if (boxFillMode > 0 && c0 != null) { boxStartGx = c0.gx; boxStartGy = c0.gy; boxStartGz = c0.gz }
                    }
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

                // Перетаскивание предмета в инвентаре / сундуке / печи!
                if (pointer == dragPtr && dragPreviewItem != null) {
                    dragCurX = ux; dragCurY = uy
                    if (!dragActive && hypot((ux - dragStartX).toDouble(), (uy - dragStartY).toDouble()) > 16.0) {
                        dragActive = true
                    }
                    return true
                }

                if (screen != UiScreen.PLAYING) return false
                if (pointer == joyPtr) { updateJoystick(ux, uy); return true }
                if (pointer == hotbarSwipePtr && hotbarSwipeSlot >= 0 && (uy - hotbarSwipeStartY) > 54f) {
                    dropSlotItem(hotbarSwipeSlot); hotbarSwipeSlot = -1; return true
                }
                if (pinchPtr2 != -1 && (pointer == worldTouchPtr || pointer == pinchPtr2)) {
                    val p1 = ptrPos[worldTouchPtr]; val p2 = ptrPos[pinchPtr2]
                    if (p1 != null && p2 != null) {
                        val d = max(20f, hypot((p1.first - p2.first).toDouble(), (p1.second - p2.second).toDouble()).toFloat())
                        cameraZoom = (pinchStartZoom * (pinchStartDist / d)).coerceIn(0.28f, 1.45f)
                    }
                    return true
                }

                if (pointer == worldTouchPtr && pinchPtr2 == -1) {
                    val heldDef = player.slots[player.selectedSlot]?.let { BlockRegistry.items[it.id] }
                    val moved = hypot((screenX - worldTouchStartX).toDouble(), (screenY - worldTouchStartY).toDouble())
                    if (heldDef != null && !heldDef.isItem && !fastDemolishMode) {
                        val cell = pickBuildCellOnPlane(screenX.toFloat(), screenY.toFloat())
                        ghostTarget = cell
                        if (boxFillMode > 0 && cell != null) {
                            if (boxStartGx == Int.MIN_VALUE) { boxStartGx = cell.gx; boxStartGy = cell.gy; boxStartGz = cell.gz }
                            if (moved > 22.0) { isDragBuilding = true; worldTouchHeldActed = true; holdingMine = false }
                            return true
                        } else if (moved > 28.0 && cell != null) {
                            isDragBuilding = true; worldTouchHeldActed = true; holdingMine = false
                            if (cell.gx != dragBuildLastX || cell.gy != dragBuildLastY || cell.gz != dragBuildLastZ) {
                                if (placeWithBuildOptions(cell.gx, cell.gy, cell.gz)) {
                                    dragBuildLastX = cell.gx; dragBuildLastY = cell.gy; dragBuildLastZ = cell.gz
                                }
                            }
                            return true
                        }
                    } else if (moved > 26.0 && (holdingMine || isDragMining || heldDef?.toolType in setOf("pick", "axe", "shovel"))) {
                        val p = pickIsoBlock(screenX.toFloat(), screenY.toFloat())
                        if (p != null && p.id > 0 && (p.gx != mineX || p.gy != mineY || p.gz != mineZ) && dragMineCd <= 0f) {
                            val distP = hypot((p.gx + 0.5f - player.x).toDouble(), (p.gy + 0.5f - player.y).toDouble())
                            if (distP <= 4.8) {
                                isDragMining = true; worldTouchHeldActed = true
                                mineX = p.gx; mineY = p.gy; mineZ = p.gz
                                mineProgress = 0f; holdingMine = true; targetPick = p
                                dragMineCd = 0.25f
                            }
                            return true
                        }
                    }
                }
                return false
            }

            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                val ux = screenX.toFloat(); val uy = (Gdx.graphics.height - screenY).toFloat()
                ptrPos.remove(pointer)

                // Завершение Drag-and-Drop или короткого тапа по слоту!
                if (pointer == dragPtr) {
                    val wasDrag = dragActive
                    val fz = dragFromZone; val fi = dragFromIdx; val item = dragPreviewItem
                    dragPtr = -1; dragActive = false; dragPreviewItem = null; dragFromZone = ""; dragFromIdx = -1
                    if (wasDrag && item != null) {
                        val hit = uiSlots.lastOrNull { it.rect.contains(ux, uy) }
                        if (hit != null) {
                            handleSlotDrop(fz, fi, hit.zone, hit.index)
                        } else if (!activePanelBounds.contains(ux, uy) && fz == "inv") {
                            dropSlotItem(fi) // Вытащил за пределы окна -> выбросил 1 шт.!
                        }
                    } else if (fz.isNotEmpty()) {
                        handleSlotQuickTap(fz, fi)
                    }
                    return true
                }

                if (pointer == joyPtr) { joyPtr = -1; stickDx = 0f; stickDy = 0f; return true }
                if (pointer == hotbarSwipePtr) { hotbarSwipePtr = -1; hotbarSwipeSlot = -1; return true }
                if (pointer == pinchPtr2) { pinchPtr2 = -1; return true }

                if (pointer == worldTouchPtr) {
                    if (boxFillMode > 0 && isDragBuilding && boxStartGx != Int.MIN_VALUE && ghostTarget != null) {
                        val gEnd = ghostTarget!!
                        val x0 = min(boxStartGx, gEnd.gx); val x1 = min(x0 + 9, max(boxStartGx, gEnd.gx))
                        val y0 = min(boxStartGy, gEnd.gy); val y1 = min(y0 + 9, max(boxStartGy, gEnd.gy))
                        val midX = (x0 + x1) / 2; val midY = (y0 + y1) / 2
                        var placedCnt = 0
                        if (boxFillMode == 1) {
                            for (bx in x0..x1) for (by in y0..y1) {
                                if (tryPlaceHeldBlockAt(bx, by, boxStartGz)) placedCnt++
                            }
                        } else {
                            // КОМНАТА: стены по периметру высотой 3 блока + проём под дверь на ближней стене!
                            for (bx in x0..x1) for (by in y0..y1) {
                                if (bx != x0 && bx != x1 && by != y0 && by != y1) continue
                                val isDoorWall = (by == y1 && bx == midX) || (bx == x1 && by == midY && x1 - x0 <= 2)
                                for (dz in 0..2) {
                                    if (isDoorWall && dz < 2) continue
                                    if (tryPlaceHeldBlockAt(bx, by, boxStartGz + dz)) placedCnt++
                                }
                            }
                        }
                        if (placedCnt >= 3) {
                            tryUnlockAchievement("builder_box")
                            showToast(if (boxFillMode == 2) "Комната построена ($placedCnt блоков)!" else "Заливка: поставлено блоков $placedCnt!")
                        }
                    }
                    boxStartGx = Int.MIN_VALUE; boxStartGy = Int.MIN_VALUE
                    val wasShortTap = !worldTouchHeldActed && !isDragBuilding && !isDragMining && (elapsed - worldTouchDownTime) < 0.24f
                    worldTouchPtr = -1; holdingMine = false; mineProgress = 0f; isDragBuilding = false; isDragMining = false
                    if (wasShortTap && screen == UiScreen.PLAYING) {
                        handleWorldTap(screenX.toFloat(), screenY.toFloat())
                    }
                    return true
                }
                return false
            }

            private fun handleWorldTap(sx: Float, sy: Float) {
                if (fishingActive) { resolveFishingCatch(); return }
                if (player.isSleeping) {
                    wakeUpFromBed("Вы встали с кровати"); return
                }

                tmpVec3.set(sx, sy, 0f); camWorld.unproject(tmpVec3)
                val hx = isoToScreenX(player.x, player.y); val hy = isoToScreenY(player.x, player.y, player.z) + 22f

                // 1. Тап по герою -> Еда/Отвар или Рывок/Щит
                if (hypot((hx - tmpVec3.x).toDouble(), (hy - tmpVec3.y).toDouble()) < 44.0) {
                    val held = player.slots[player.selectedSlot]
                    if (held != null && useConsumableOrSpecial(held)) return
                    performDashOrParry()
                    return
                }

                // 1.5 PvP по другому игроку
                if (netMp.isConnected && netMp.pvpEnabled) {
                    val tappedPeer = netMp.remotePlayers.values.firstOrNull { rp ->
                        val rx = isoToScreenX(rp.x, rp.y); val ry = isoToScreenY(rp.x, rp.y, rp.z) + 22f
                        hypot((rx - tmpVec3.x).toDouble(), (ry - tmpVec3.y).toDouble()) < 54.0 && hypot((rp.x - player.x).toDouble(), (rp.y - player.y).toDouble()) < 3.2
                    }
                    if (tappedPeer != null) {
                        val hDef = player.slots[player.selectedSlot]?.let { BlockRegistry.items[it.id] }
                        val dmg = ((hDef?.damage ?: 4) + player.weaponUpgradeLvl * 2).toFloat()
                        player.attackAnim = 1.0f; SoundFx.play("hit", 0.6f)
                        netMp.sendPvpHit(tappedPeer.id, dmg)
                        entities.floatingTexts.add(FloatingText(tappedPeer.x, tappedPeer.y, tappedPeer.z + 1.5f, "-${dmg.toInt()}", Color.RED))
                        return
                    }
                }

                val pick = pickIsoBlock(sx, sy) ?: return
                targetPick = pick
                if (pipetteMode && pick.id > 0) { pickBlockIntoHand(pick.id); pipetteMode = false; return }

                entities.golemPet?.let { gl ->
                    val gxS = isoToScreenX(gl.x, gl.y); val gyS = isoToScreenY(gl.x, gl.y, gl.z) + 22f
                    if (hypot((gxS - tmpVec3.x).toDouble(), (gyS - tmpVec3.y).toDouble()) < 68.0) {
                        SoundFx.play("chest", 0.65f); screen = UiScreen.GOLEM_BAG; return
                    }
                }
                // 2. Тап по МОБУ / Торговцу
                val tappedMob = entities.mobs.minByOrNull { m ->
                    val mx = isoToScreenX(m.x, m.y); val my = isoToScreenY(m.x, m.y, m.z) + 22f
                    hypot((mx - tmpVec3.x).toDouble(), (my - tmpVec3.y).toDouble())
                }
                if (tappedMob != null) {
                    val mx = isoToScreenX(tappedMob.x, tappedMob.y); val my = isoToScreenY(tappedMob.x, tappedMob.y, tappedMob.z) + 22f
                    if (hypot((mx - tmpVec3.x).toDouble(), (my - tmpVec3.y).toDouble()) < 76.0) {
                        if (tappedMob.type == "trader") { SoundFx.play("chest", 0.6f); screen = UiScreen.TRADER_QUESTS; return }
                        if (tappedMob.type == "golem_pet") { SoundFx.play("chest", 0.6f); screen = UiScreen.GOLEM_BAG; return }
                        if (tappedMob.type == "deer" && player.ridingMode.isEmpty() && (player.slots[player.selectedSlot]?.id ?: 0) == 113) {
                            player.takeItem(113, 1); player.ridingMode = "deer"; SoundFx.play("levelup", 0.6f); tryUnlockAchievement("rider")
                            showToast("Вы оседлали Оленя! (+65% скорости!)"); return
                        }
                        val held = player.slots[player.selectedSlot]
                        if (tappedMob.type == "wolf" && !tappedMob.tamed && held != null && (held.id == 115 || held.id == 114 || held.id == 157)) {
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
                            if (player.takeItem(held.id, 1)) { showToast(entities.feedPeacefulMob(tappedMob)); return }
                        }
                        performAttackOrMine(IsoPick(floor(tappedMob.x).toInt(), floor(tappedMob.y).toInt(), floor(tappedMob.z).toInt(), 0), startContinuousHold = false)
                        return
                    }
                }

                // 3. Ближайший интерактивный блок (Сундук / Печь / Верстак / Кровать / Лифт)
                val nearTapInter = findNearestInteractive(pick.gx + 0.5f, pick.gy + 0.5f, pick.gz, 1.45f)
                if (nearTapInter != null && hypot((nearTapInter.gx + 0.5f - player.x).toDouble(), (nearTapInter.gy + 0.5f - player.y).toDouble()) <= 4.6) {
                    performInteractOrPlace(nearTapInter)
                    return
                }

                // 4. Стройка по плоскости (ПОЛ / СТЕНА / КРЫША) — работает даже за стеной!
                val held = player.slots[player.selectedSlot]
                val hDef = held?.let { BlockRegistry.items[it.id] }
                if (hDef != null && !hDef.isItem) {
                    if (fastDemolishMode && pick.id > 0 && (BlockRegistry.items[pick.id]?.hardness ?: 999f) < 900f) {
                        val drop = BlockRegistry.items[pick.id]?.dropId ?: pick.id
                        setWorldBlock(pick.gx, pick.gy, pick.gz, 0)
                        if (!player.creative && drop > 0) player.addItem(drop, 1)
                        SoundFx.play("pickup", 0.55f); return
                    }
                    // Быстрый откат последнего поставленного блока
                    if ((elapsed - lastPlacedTime) <= 2.2f && pick.gx == lastPlacedX && pick.gy == lastPlacedY && pick.gz == lastPlacedZ && pick.id == lastPlacedId && pick.id > 0) {
                        setWorldBlock(pick.gx, pick.gy, pick.gz, 0)
                        if (!player.creative) player.addItem(lastPlacedId, 1)
                        lastPlacedTime = -10f; SoundFx.play("pickup", 0.55f); showToast("Блок возвращён!"); return
                    }
                    val bCell = pickBuildCellOnPlane(sx, sy)
                    if (bCell != null) {
                        ghostTarget = bCell
                        placeWithBuildOptions(bCell.gx, bCell.gy, bCell.gz)
                        return
                    }
                }

                val def = BlockRegistry.items[pick.id]
                when {
                    def != null && def.interactive.isNotEmpty() -> performInteractOrPlace(pick)
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
