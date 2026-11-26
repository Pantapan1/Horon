package com.voxelhorizon.nativegame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.ZeroArgFunction
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class LuaModEntry(
    val id: String,
    var name: String,
    var desc: String,
    var enabled: Boolean,
    var code: String,
    var version: String = "1.0",
    var author: String = "",
    var packFile: File? = null,
    var isBuiltin: Boolean = false
)

class LuaModEngine(private val modsDir: File, private val game: VoxelHorizonGame) {
    val mods = mutableListOf<LuaModEntry>()
    private val hooks = HashMap<String, MutableList<LuaValue>>()
    private var globals: Globals? = null
    private val enabledPrefs = HashMap<String, Boolean>()
    private val modTextures = HashMap<Int, Texture>()
    val modTexRegions = HashMap<Int, TextureRegion>()
    private var nextTexHandle = 1
    private var activeLoadingPack: File? = null
    private var activeLoadingModId: String = "global"

    init {
        loadEnabledPrefs()
        mods += LuaModEntry(
            id = "builtin_vein", name = "Супер-Кирка 3x3 (Lua)", desc = "Алмазная кирка копает сразу область 3x3",
            enabled = enabledPrefs["builtin_vein"] ?: false,
            code = """
            VH.on("onBlockBreak", function(x, y, z, id, held)
                if held == 109 then
                    for dx = -1, 1 do
                        for dy = -1, 1 do
                            if dx ~= 0 or dy ~= 0 then
                                VH.setBlock(x + dx, y + dy, z, 0)
                            end
                        end
                    end
                end
            end)
            """.trimIndent(),
            version = "1.0", author = "VoxelHorizon", isBuiltin = true
        )
        mods += LuaModEntry(
            id = "builtin_speed", name = "Режим Спидраннера (Lua)", desc = "Ускоряет бег героя на +40%",
            enabled = enabledPrefs["builtin_speed"] ?: false,
            code = """
            VH.setSpeed(4.5)
            VH.toast("Lua-мод: Спидраннер активен!")
            """.trimIndent(),
            version = "1.0", author = "VoxelHorizon", isBuiltin = true
        )
        mods += LuaModEntry(
            id = "builtin_gift", name = "Стартовый Набор Героя (Lua)", desc = "Выдаёт Алмазный Меч и 4 Изумруда",
            enabled = enabledPrefs["builtin_gift"] ?: false,
            code = """
            VH.give(112, 1)
            VH.give(118, 4)
            """.trimIndent(),
            version = "1.0", author = "VoxelHorizon", isBuiltin = true
        )
        scanExternalMods()
    }

    private fun enabledFile(): File = File(modsDir, "mods_enabled.dat")

    private fun loadEnabledPrefs() {
        enabledPrefs.clear()
        val f = enabledFile()
        if (!f.exists()) return
        try {
            for (raw in f.readLines(Charsets.UTF_8)) {
                val p = raw.trim().split("|")
                if (p.size >= 2) enabledPrefs[p[0]] = (p[1] == "1")
            }
        } catch (_: Exception) {}
    }

    fun saveEnabledPrefs() {
        for (m in mods) enabledPrefs[m.id] = m.enabled
        val sb = StringBuilder()
        for ((k, v) in enabledPrefs) sb.append(k).append('|').append(if (v) "1" else "0").append('\n')
        try {
            modsDir.mkdirs()
            enabledFile().writeText(sb.toString(), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    private fun readPackEntryBytes(pack: File?, relPath: String): ByteArray? {
        if (pack == null || !pack.exists()) return null
        val norm = relPath.trimStart('/')
        return try {
            ZipFile(pack).use { zf ->
                val e = zf.getEntry(norm) ?: zf.getEntry("assets/$norm") ?: return null
                zf.getInputStream(e).readBytes()
            }
        } catch (_: Exception) { null }
    }

    fun scanExternalMods() {
        modsDir.mkdirs()
        val seenIds = HashSet<String>()
        for (m in mods) if (m.isBuiltin) seenIds.add(m.id)

        modsDir.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { f ->
            if (!f.isFile) return@forEach
            if (f.name.endsWith(".lua")) {
                val id = "ext_" + f.nameWithoutExtension.lowercase().replace(Regex("[^a-z0-9_\\-]"), "_")
                val code = try { f.readText(Charsets.UTF_8) } catch (_: Exception) { "" }
                val ver = Regex("--\\s*@version\\s+([^\\r\\n]+)").find(code)?.groupValues?.getOrNull(1)?.trim() ?: "1.0"
                val title = Regex("--\\s*@name\\s+([^\\r\\n]+)").find(code)?.groupValues?.getOrNull(1)?.trim() ?: f.nameWithoutExtension
                seenIds.add(id)
                val existing = mods.firstOrNull { it.id == id }
                if (existing != null) {
                    existing.name = title; existing.version = ver; existing.code = code; existing.packFile = f
                } else {
                    mods += LuaModEntry(id, title, "Lua-мод (${f.name})", enabledPrefs[id] ?: true, code, version = ver, packFile = f)
                }
            } else if (f.name.endsWith(".vhpack") || f.name.endsWith(".zip")) {
                try {
                    ZipFile(f).use { zf ->
                        val mEntry = zf.getEntry("manifest.json")
                        val mJson = if (mEntry != null) zf.getInputStream(mEntry).bufferedReader(Charsets.UTF_8).readText() else ""
                        val rawId = PlatformBridge.extractJsonString(mJson, "id").ifBlank { f.nameWithoutExtension }
                        val id = "pack_" + rawId.lowercase().replace(Regex("[^a-z0-9_\\-]"), "_")
                        val title = PlatformBridge.extractJsonString(mJson, "name").ifBlank { f.nameWithoutExtension }
                        val ver = PlatformBridge.extractJsonString(mJson, "version").ifBlank { "1.0" }
                        val author = PlatformBridge.extractJsonString(mJson, "author")
                        val desc = PlatformBridge.extractJsonString(mJson, "description").ifBlank { ".vhpack (${f.name})" }
                        val luaEntry = zf.getEntry("main.lua") ?: zf.getEntry("scripts/main.lua")
                        val code = if (luaEntry != null) zf.getInputStream(luaEntry).bufferedReader(Charsets.UTF_8).readText() else ""
                        var hasVhmod = false
                        val en = zf.entries()
                        while (en.hasMoreElements()) {
                            if (en.nextElement().name.lowercase().endsWith(".vhmod")) { hasVhmod = true; break }
                        }
                        if (luaEntry != null || hasVhmod || mEntry != null) {
                            seenIds.add(id)
                            val existing = mods.firstOrNull { it.id == id }
                            if (existing != null) {
                                existing.name = title; existing.version = ver; existing.author = author
                                existing.desc = desc; existing.code = code; existing.packFile = f
                            } else {
                                mods += LuaModEntry(id, title, desc, enabledPrefs[id] ?: true, code, version = ver, author = author, packFile = f)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        // Remove deleted external mods cleanly!
        mods.removeIf { !it.isBuiltin && !seenIds.contains(it.id) }
    }

    fun deleteExternalMod(modId: String): Boolean {
        val m = mods.firstOrNull { it.id == modId && !it.isBuiltin } ?: return false
        try { m.packFile?.delete() } catch (_: Exception) {}
        mods.remove(m)
        enabledPrefs.remove(modId)
        saveEnabledPrefs()
        hotReload()
        return true
    }

    fun hotReload() {
        hooks.clear()
        game.baseWalkSpeed = 3.35f
        if (game.screen == UiScreen.MOD_CUSTOM_UI) game.screen = UiScreen.PLAYING
        ModAudioEngine.stopSeq()

        // Dispose previously loaded mod textures safely on GL thread
        val oldTextures = modTextures.values.toList()
        modTextures.clear(); modTexRegions.clear(); nextTexHandle = 1
        if (oldTextures.isNotEmpty()) {
            Gdx.app.postRunnable { for (t in oldTextures) { try { t.dispose() } catch (_: Exception) {} } }
        }

        scanExternalMods()
        saveEnabledPrefs()

        // Reset pack-contributed NoCode content & rebuild clean recipes without duplicates!
        game.modStudio.clearExternalPackContent()
        for (m in mods) {
            if (!m.enabled || m.packFile == null) continue
            val pf = m.packFile!!
            if (pf.name.endsWith(".vhpack") || pf.name.endsWith(".zip")) {
                try {
                    ZipFile(pf).use { zf ->
                        val en = zf.entries()
                        while (en.hasMoreElements()) {
                            val e = en.nextElement()
                            if (!e.isDirectory && e.name.lowercase().endsWith(".vhmod")) {
                                val txt = zf.getInputStream(e).bufferedReader(Charsets.UTF_8).readText()
                                game.modStudio.loadPackVhmod(m.id, txt)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        game.modStudio.applyAllToEngine()

        // === BUILD UNRESTRICTED LUA ENVIRONMENT (Full VH API + VH.ui + VH.audio + VH.engine + luajava!) ===
        val g = JsePlatform.standardGlobals()
        val vh = LuaTable()

        // --- Core Messaging, World & Blocks ---
        vh.set("toast", object : OneArgFunction() { override fun call(a: LuaValue): LuaValue { game.showToast(a.tojstring()); return LuaValue.NIL } })
        vh.set("chat", object : OneArgFunction() {
            override fun call(a: LuaValue): LuaValue {
                val msg = a.tojstring()
                game.addChatMessage(msg)
                if (game.netMp.isConnected) game.netMp.broadcastChat("MOD", msg)
                return LuaValue.NIL
            }
        })
        vh.set("setBlock", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                game.setWorldBlock(args.checkint(1), args.checkint(2), args.checkint(3), args.checkint(4))
                return LuaValue.NIL
            }
        })
        vh.set("getBlock", object : ThreeArgFunction() {
            override fun call(x: LuaValue, y: LuaValue, z: LuaValue): LuaValue =
                LuaValue.valueOf(game.world.getBlock(x.checkint(), y.checkint(), z.checkint()))
        })
        vh.set("getSurfaceZ", object : TwoArgFunction() {
            override fun call(x: LuaValue, y: LuaValue): LuaValue = LuaValue.valueOf(game.world.getSurfaceZ(x.checkint(), y.checkint()))
        })
        vh.set("getGroundZ", object : ThreeArgFunction() {
            override fun call(x: LuaValue, y: LuaValue, z: LuaValue): LuaValue {
                val gz = game.world.getGroundZ(x.checkint(), y.checkint(), z.tofloat())
                return if (gz != null) LuaValue.valueOf(gz.toDouble()) else LuaValue.NIL
            }
        })

        // --- Player, Inventory & Stats ---
        vh.set("give", object : TwoArgFunction() {
            override fun call(id: LuaValue, cnt: LuaValue): LuaValue =
                LuaValue.valueOf(game.player.addItem(id.checkint(), if (cnt.isnil()) 1 else cnt.checkint()))
        })
        vh.set("take", object : TwoArgFunction() {
            override fun call(id: LuaValue, cnt: LuaValue): LuaValue =
                LuaValue.valueOf(game.player.takeItem(id.checkint(), if (cnt.isnil()) 1 else cnt.checkint()))
        })
        vh.set("countItem", object : OneArgFunction() {
            override fun call(id: LuaValue): LuaValue = LuaValue.valueOf(game.player.countItem(id.checkint()))
        })
        vh.set("getHeldItem", object : ZeroArgFunction() {
            override fun call(): LuaValue = LuaValue.valueOf(game.player.slots[game.player.selectedSlot]?.id ?: 0)
        })
        vh.set("setHeldSlot", object : OneArgFunction() {
            override fun call(s: LuaValue): LuaValue { game.player.selectedSlot = s.checkint().coerceIn(0, 8); return LuaValue.NIL }
        })
        vh.set("getItemName", object : OneArgFunction() {
            override fun call(id: LuaValue): LuaValue = LuaValue.valueOf(BlockRegistry.items[id.checkint()]?.name ?: "")
        })
        vh.set("getPlayerPos", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs = LuaValue.varargsOf(
                arrayOf(LuaValue.valueOf(game.player.x.toDouble()), LuaValue.valueOf(game.player.y.toDouble()), LuaValue.valueOf(game.player.z.toDouble()))
            )
        })
        vh.set("setPlayerPos", object : ThreeArgFunction() {
            override fun call(x: LuaValue, y: LuaValue, z: LuaValue): LuaValue {
                game.player.x = x.tofloat(); game.player.y = y.tofloat(); game.player.z = z.tofloat()
                return LuaValue.NIL
            }
        })
        vh.set("setPlayerVel", object : ThreeArgFunction() {
            override fun call(vx: LuaValue, vy: LuaValue, vz: LuaValue): LuaValue {
                game.player.vx = vx.tofloat(); game.player.vy = vy.tofloat(); game.player.vz = vz.tofloat()
                return LuaValue.NIL
            }
        })
        vh.set("getHp", object : ZeroArgFunction() { override fun call(): LuaValue = LuaValue.valueOf(game.player.hp.toDouble()) })
        vh.set("setHp", object : TwoArgFunction() {
            override fun call(hp: LuaValue, maxHp: LuaValue): LuaValue {
                if (!maxHp.isnil()) game.player.maxHp = max(2f, maxHp.tofloat())
                game.player.hp = hp.tofloat().coerceIn(0f, game.player.maxHp)
                return LuaValue.NIL
            }
        })
        vh.set("getFood", object : ZeroArgFunction() { override fun call(): LuaValue = LuaValue.valueOf(game.player.food.toDouble()) })
        vh.set("setFood", object : OneArgFunction() {
            override fun call(f: LuaValue): LuaValue { game.player.food = f.tofloat().coerceIn(0f, game.player.maxFood); return LuaValue.NIL }
        })
        vh.set("setSpeed", object : OneArgFunction() { override fun call(s: LuaValue): LuaValue { game.baseWalkSpeed = s.tofloat(); return LuaValue.NIL } })
        vh.set("setCreative", object : OneArgFunction() { override fun call(b: LuaValue): LuaValue { game.player.creative = b.toboolean(); return LuaValue.NIL } })
        vh.set("isCreative", object : ZeroArgFunction() { override fun call(): LuaValue = LuaValue.valueOf(game.player.creative) })
        vh.set("setZoom", object : OneArgFunction() { override fun call(z: LuaValue): LuaValue { game.cameraZoom = z.tofloat().coerceIn(0.25f, 2.0f); return LuaValue.NIL } })
        vh.set("getZoom", object : ZeroArgFunction() { override fun call(): LuaValue = LuaValue.valueOf(game.cameraZoom.toDouble()) })
        vh.set("shake", object : OneArgFunction() { override fun call(s: LuaValue): LuaValue { game.triggerScreenShake(s.optdouble(0.2).toFloat()); return LuaValue.NIL } })

        // --- Time, Weather, Dimensions & World FX ---
        vh.set("getTime", object : ZeroArgFunction() { override fun call(): LuaValue = LuaValue.valueOf(game.entities.worldTime.toDouble()) })
        vh.set("setTime", object : OneArgFunction() { override fun call(t: LuaValue): LuaValue { game.entities.worldTime = t.tofloat().coerceIn(0f, 1f); return LuaValue.NIL } })
        vh.set("getDay", object : ZeroArgFunction() { override fun call(): LuaValue = LuaValue.valueOf(game.entities.dayCount) })
        vh.set("setDay", object : OneArgFunction() { override fun call(d: LuaValue): LuaValue { game.entities.dayCount = max(1, d.checkint()); return LuaValue.NIL } })
        vh.set("getSeason", object : ZeroArgFunction() { override fun call(): LuaValue = LuaValue.valueOf(game.entities.season.ruName) })
        vh.set("getWeather", object : ZeroArgFunction() { override fun call(): LuaValue = LuaValue.valueOf(game.entities.weather.name) })
        vh.set("setWeather", object : OneArgFunction() {
            override fun call(w: LuaValue): LuaValue {
                val nm = w.tojstring().uppercase()
                WeatherType.values().firstOrNull { it.name == nm }?.let { game.entities.weather = it }
                return LuaValue.NIL
            }
        })
        vh.set("getRealClock", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                val s = try { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) } catch (_: Exception) { "12:00" }
                return LuaValue.valueOf(s)
            }
        })
        vh.set("switchDim", object : OneArgFunction() { override fun call(d: LuaValue): LuaValue { game.switchDimension(d.tojstring()); return LuaValue.NIL } })
        vh.set("getDim", object : ZeroArgFunction() { override fun call(): LuaValue = LuaValue.valueOf(game.world.dimension) })
        vh.set("explode", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                game.triggerTntAt(args.checkint(1), args.checkint(2), args.checkint(3))
                return LuaValue.NIL
            }
        })
        vh.set("particles", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                game.entities.spawnBlockParticles(args.checkint(1), args.checkint(2), args.checkint(3), args.optint(4, 0xFFE248), args.optint(5, 6))
                return LuaValue.NIL
            }
        })
        vh.set("spark", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                game.entities.spawnRisingSpark(args.checkdouble(1).toFloat(), args.checkdouble(2).toFloat(), args.checkdouble(3).toFloat(), args.optint(4, 0xFFE248), args.optdouble(5, 6.0).toFloat())
                return LuaValue.NIL
            }
        })
        vh.set("floatingText", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val hex = args.optint(5, 0xFFE248)
                val c = Color(((hex shr 16) and 0xFF) / 255f, ((hex shr 8) and 0xFF) / 255f, (hex and 0xFF) / 255f, 1f)
                game.entities.floatingTexts.add(FloatingText(args.checkdouble(1).toFloat(), args.checkdouble(2).toFloat(), args.checkdouble(3).toFloat(), args.checkjstring(4), c))
                return LuaValue.NIL
            }
        })

        // --- Entities, Mobs, Drops & Projectiles ---
        vh.set("spawnMob", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val t = args.checkjstring(1)
                val x = if (args.narg() >= 2 && !args.arg(2).isnil()) args.checkdouble(2).toFloat() else game.player.x + 2f
                val y = if (args.narg() >= 3 && !args.arg(3).isnil()) args.checkdouble(3).toFloat() else game.player.y + 1f
                val z = if (args.narg() >= 4 && !args.arg(4).isnil()) args.checkdouble(4).toFloat() else game.player.z
                val m = game.entities.spawnMob(t, x, y, z)
                if (args.narg() >= 5 && args.arg(5).istable()) {
                    val o = args.arg(5).checktable()
                    if (!o.get("hp").isnil()) { m.hp = o.get("hp").tofloat(); m.maxHp = m.hp }
                    if (!o.get("damage").isnil()) m.customDamage = o.get("damage").tofloat()
                    if (!o.get("speed").isnil()) m.customSpeed = o.get("speed").tofloat()
                    if (!o.get("tint").isnil()) m.customTintHex = o.get("tint").toint()
                    if (!o.get("dropId").isnil()) m.customDropId = o.get("dropId").toint()
                    if (!o.get("dropCount").isnil()) m.customDropCount = o.get("dropCount").toint()
                    if (!o.get("hostile").isnil()) m.hostile = o.get("hostile").toboolean()
                }
                return CoerceJavaToLua.coerce(m)
            }
        })
        vh.set("spawnDrop", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                game.entities.spawnDrop(args.checkint(1), args.optint(2, 1), args.checkdouble(3).toFloat(), args.checkdouble(4).toFloat(), args.checkdouble(5).toFloat())
                return LuaValue.NIL
            }
        })
        vh.set("fireProjectile", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                game.entities.fireProjectile(
                    args.checkdouble(1).toFloat(), args.checkdouble(2).toFloat(), args.checkdouble(3).toFloat(),
                    args.checkdouble(4).toFloat(), args.checkdouble(5).toFloat(),
                    args.optdouble(6, 10.0).toFloat(), args.optboolean(7, false), args.optboolean(8, false), args.optboolean(9, true)
                )
                return LuaValue.NIL
            }
        })

        // --- Dynamic Content Registration (PNG Blocks, 3D Props, Items, Recipes, Textures!) ---
        vh.set("registerBlock", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                BlockRegistry.registerModBlock(args.checkint(1), args.checkjstring(2), args.optdouble(3, 0.2).toFloat(), args.optdouble(4, 0.85).toFloat(), args.optdouble(5, 0.45).toFloat())
                return LuaValue.NIL
            }
        })
        vh.set("registerBlockEx", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val id = args.checkint(1)
                val name = args.checkjstring(2)
                val o = if (args.narg() >= 3 && args.arg(3).istable()) args.arg(3).checktable() else LuaTable()
                val texPath = o.get("texture").optjstring("")
                val png = if (texPath.isNotEmpty()) readPackEntryBytes(activeLoadingPack, texPath) else null
                val solid = o.get("solid").optboolean(true)
                val trans = o.get("transparent").optboolean(false)
                val isProp = o.get("isProp").optboolean(false)
                val lRad = o.get("lightRadius").optdouble(0.0).toFloat()
                val lCol = o.get("lightColor").optint(0xFFE688)
                val hard = o.get("hardness").optdouble(1.0).toFloat()
                val dropId = o.get("dropId").optint(id)
                val inter = o.get("interactive").optjstring("")
                val cHex = o.get("colorHex").optint(0x4CE4FF)
                BlockRegistry.registerModBlockEx(id, name, png, solid, trans, isProp, lRad, lCol, hard, dropId, inter, cHex)
                return LuaValue.NIL
            }
        })
        vh.set("registerItem", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val id = args.checkint(1)
                val name = args.checkjstring(2)
                val o = if (args.narg() >= 3 && args.arg(3).istable()) args.arg(3).checktable() else LuaTable()
                val texPath = o.get("texture").optjstring("")
                val png = if (texPath.isNotEmpty()) readPackEntryBytes(activeLoadingPack, texPath) else null
                BlockRegistry.registerModItemEx(
                    id = id, name = name, pngBytes = png,
                    kind = o.get("kind").optjstring(""),
                    damage = o.get("damage").optint(3),
                    mineSpeed = o.get("mineSpeed").optdouble(1.0).toFloat(),
                    durability = o.get("durability").optint(0),
                    healHp = o.get("healHp").optint(0),
                    healFood = o.get("healFood").optint(0),
                    aoeMine = o.get("aoeMine").optboolean(false),
                    armorSlot = o.get("armorSlot").optint(-1),
                    armorDef = o.get("armorDef").optint(0),
                    colorHex = o.get("colorHex").optint(0xFFFFFF)
                )
                return LuaValue.NIL
            }
        })
        vh.set("registerRecipe", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val outId = args.checkint(1)
                val outCnt = args.optint(2, 1)
                val reqTbl = args.checktable(3)
                val desc = args.optjstring(4, BlockRegistry.items[outId]?.name ?: "Мод-рецепт")
                val station = args.optint(5, 1) // 0=pocket, 1=workbench, 2=campfire
                val cat = args.optjstring(6, "redstone")
                val reqs = mutableListOf<Pair<Int, Int>>()
                var k = LuaValue.NIL
                while (true) {
                    val nxt = reqTbl.next(k)
                    k = nxt.arg1()
                    if (k.isnil()) break
                    val v = nxt.arg(2)
                    if (v.istable()) {
                        val pairT = v.checktable()
                        reqs += pairT.get(1).toint() to max(1, pairT.get(2).optint(1))
                    } else if (k.isint()) {
                        reqs += k.toint() to max(1, v.optint(1))
                    }
                }
                if (reqs.isNotEmpty()) {
                    BlockRegistry.registerModRecipe(outId, outCnt, reqs, desc, pocket2x2 = (station == 0), campfireBrew = (station == 2), category = cat)
                }
                return LuaValue.NIL
            }
        })
        vh.set("loadTexture", object : OneArgFunction() {
            override fun call(pathVal: LuaValue): LuaValue {
                val bytes = readPackEntryBytes(activeLoadingPack, pathVal.checkjstring()) ?: return LuaValue.valueOf(0)
                val handle = nextTexHandle++
                Gdx.app.postRunnable {
                    try {
                        val pm = Pixmap(bytes, 0, bytes.size)
                        val t = Texture(pm).apply { setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest) }
                        pm.dispose()
                        modTextures[handle] = t
                        modTexRegions[handle] = TextureRegion(t)
                    } catch (_: Exception) {}
                }
                return LuaValue.valueOf(handle)
            }
        })

        // --- Persistent Per-Mod File Storage, Clipboard & Android Share ---
        vh.set("saveData", object : ThreeArgFunction() {
            override fun call(modKey: LuaValue, fileKey: LuaValue, content: LuaValue): LuaValue {
                val mClean = modKey.tojstring().replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "shared" }
                val fClean = fileKey.tojstring().replace(Regex("[^a-zA-Z0-9._\\-]"), "_").ifBlank { "data.txt" }
                try {
                    val dir = File(File(modsDir, "data"), mClean).also { it.mkdirs() }
                    File(dir, fClean).writeText(content.tojstring(), Charsets.UTF_8)
                    return LuaValue.TRUE
                } catch (_: Exception) { return LuaValue.FALSE }
            }
        })
        vh.set("loadData", object : TwoArgFunction() {
            override fun call(modKey: LuaValue, fileKey: LuaValue): LuaValue {
                val mClean = modKey.tojstring().replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "shared" }
                val fClean = fileKey.tojstring().replace(Regex("[^a-zA-Z0-9._\\-]"), "_").ifBlank { "data.txt" }
                val f = File(File(File(modsDir, "data"), mClean), fClean)
                return if (f.exists()) try { LuaValue.valueOf(f.readText(Charsets.UTF_8)) } catch (_: Exception) { LuaValue.NIL } else LuaValue.NIL
            }
        })
        vh.set("shareData", object : ThreeArgFunction() {
            override fun call(filename: LuaValue, content: LuaValue, title: LuaValue): LuaValue {
                val fn = filename.tojstring().replace(Regex("[^a-zA-Z0-9._\\-]"), "_").ifBlank { "mod_export.dat" }
                val f = File(File(modsDir, "export").also { it.mkdirs() }, fn)
                return try {
                    f.writeText(content.tojstring(), Charsets.UTF_8)
                    LuaValue.valueOf(PlatformBridge.shareFile(f, title.optjstring("Поделиться файлом")))
                } catch (_: Exception) { LuaValue.FALSE }
            }
        })
        vh.set("copyText", object : TwoArgFunction() {
            override fun call(lbl: LuaValue, txt: LuaValue): LuaValue = LuaValue.valueOf(PlatformBridge.copyToClipboard(lbl.tojstring(), txt.tojstring()))
        })
        vh.set("pickImport", object : ZeroArgFunction() {
            override fun call(): LuaValue { PlatformBridge.onPickFileRequest?.invoke(); return LuaValue.NIL }
        })

        // --- Event Registration ---
        vh.set("on", object : TwoArgFunction() {
            override fun call(ev: LuaValue, fn: LuaValue): LuaValue {
                if (fn.isfunction()) hooks.computeIfAbsent(ev.tojstring()) { mutableListOf() }.add(fn)
                return LuaValue.NIL
            }
        })

        // === VH.ui — FULL CUSTOM GUI BUILDER FOR ANY MOD ===
        val ui = LuaTable()
        ui.set("open", object : OneArgFunction() {
            override fun call(id: LuaValue): LuaValue {
                game.activeModScreenId = id.checkjstring()
                game.screen = UiScreen.MOD_CUSTOM_UI
                return LuaValue.NIL
            }
        })
        ui.set("close", object : ZeroArgFunction() {
            override fun call(): LuaValue {
                game.activeModScreenId = ""
                game.screen = UiScreen.PLAYING
                return LuaValue.NIL
            }
        })
        ui.set("isOpen", object : OneArgFunction() {
            override fun call(id: LuaValue): LuaValue =
                LuaValue.valueOf(game.screen == UiScreen.MOD_CUSTOM_UI && (id.isnil() || game.activeModScreenId == id.tojstring()))
        })
        ui.set("panel", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                game.drawModPanel(args.checkjstring(1), args.checkdouble(2).toFloat(), args.checkdouble(3).toFloat(), args.checkdouble(4).toFloat(), args.checkdouble(5).toFloat())
                return LuaValue.NIL
            }
        })
        ui.set("rect", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                game.drawModRect(args.checkdouble(1).toFloat(), args.checkdouble(2).toFloat(), args.checkdouble(3).toFloat(), args.checkdouble(4).toFloat(), args.checkint(5), args.optdouble(6, 1.0).toFloat())
                return LuaValue.NIL
            }
        })
        ui.set("text", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                game.drawModText(
                    args.checkjstring(1),
                    args.checkdouble(2).toFloat(), args.checkdouble(3).toFloat(),
                    args.checkdouble(4).toFloat(), args.checkdouble(5).toFloat(),
                    args.optint(6, 0xFFFFFF), args.optboolean(7, false), args.optboolean(8, false)
                )
                return LuaValue.NIL
            }
        })
        ui.set("button", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val style = args.checkjstring(1)
                val x = args.checkdouble(2).toFloat(); val y = args.checkdouble(3).toFloat()
                val w = args.checkdouble(4).toFloat(); val h = args.checkdouble(5).toFloat()
                val lbl = args.checkjstring(6)
                val fn = args.arg(7)
                game.drawModButton(style, x, y, w, h, lbl) {
                    if (fn.isfunction()) {
                        try { fn.call() } catch (e: Exception) { game.showToast("Lua UI: ${e.message}") }
                    }
                }
                return LuaValue.NIL
            }
        })
        ui.set("hitArea", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val fn = args.arg(5)
                game.drawModHitArea(args.checkdouble(1).toFloat(), args.checkdouble(2).toFloat(), args.checkdouble(3).toFloat(), args.checkdouble(4).toFloat()) {
                    if (fn.isfunction()) {
                        try { fn.call() } catch (e: Exception) { game.showToast("Lua Hit: ${e.message}") }
                    }
                }
                return LuaValue.NIL
            }
        })
        ui.set("icon", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                game.drawModIcon(args.checkint(1), args.checkdouble(2).toFloat(), args.checkdouble(3).toFloat(), args.checkdouble(4).toFloat(), args.checkdouble(5).toFloat())
                return LuaValue.NIL
            }
        })
        ui.set("texture", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val reg = modTexRegions[args.checkint(1)]
                if (reg != null) game.drawModRegion(reg, args.checkdouble(2).toFloat(), args.checkdouble(3).toFloat(), args.checkdouble(4).toFloat(), args.checkdouble(5).toFloat())
                return LuaValue.NIL
            }
        })
        ui.set("progressBar", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                game.drawModProgressBar(
                    args.checkdouble(1).toFloat(), args.checkdouble(2).toFloat(),
                    args.checkdouble(3).toFloat(), args.checkdouble(4).toFloat(),
                    args.checkdouble(5).toFloat(), args.optint(6, 0x48F088), args.optjstring(7, "")
                )
                return LuaValue.NIL
            }
        })
        ui.set("prompt", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val fn = args.arg(4)
                game.openModPrompt(args.checkjstring(1), args.optjstring(2, ""), args.optjstring(3, "")) { res ->
                    Gdx.app.postRunnable {
                        if (fn.isfunction()) {
                            try { fn.call(LuaValue.valueOf(res)) } catch (e: Exception) { game.showToast("Lua Prompt: ${e.message}") }
                        }
                    }
                }
                return LuaValue.NIL
            }
        })
        vh.set("ui", ui)

        // === VH.audio — REAL-TIME PCM SYNTHESIZER, SEQUENCER & PACK SFX ===
        val audio = LuaTable()
        audio.set("playNote", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                ModAudioEngine.playNote(
                    midi = args.checkint(1),
                    wave = args.optint(2, 1),
                    vol = args.optdouble(3, ModAudioEngine.masterVol.toDouble()).toFloat(),
                    attack = args.optdouble(4, ModAudioEngine.presetAttack.toDouble()).toFloat(),
                    decay = args.optdouble(5, ModAudioEngine.presetDecay.toDouble()).toFloat(),
                    vibrato = args.optdouble(6, ModAudioEngine.presetVibrato.toDouble()).toFloat(),
                    lpCut = args.optdouble(7, ModAudioEngine.presetLpCut.toDouble()).toFloat(),
                    duty = args.optdouble(8, ModAudioEngine.presetDuty.toDouble()).toFloat(),
                    channel = args.optint(9, 0)
                )
                return LuaValue.NIL
            }
        })
        audio.set("playFreq", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                ModAudioEngine.playFreq(args.checkdouble(1).toFloat(), args.optint(2, 1), args.optdouble(3, 0.25).toFloat(), args.optdouble(4, 0.7).toFloat())
                return LuaValue.NIL
            }
        })
        audio.set("setPreset", object : OneArgFunction() {
            override fun call(tVal: LuaValue): LuaValue {
                if (tVal.istable()) {
                    val t = tVal.checktable()
                    if (!t.get("vol").isnil()) ModAudioEngine.masterVol = t.get("vol").tofloat().coerceIn(0.05f, 1f)
                    if (!t.get("attack").isnil()) ModAudioEngine.presetAttack = t.get("attack").tofloat().coerceIn(0.002f, 0.4f)
                    if (!t.get("decay").isnil()) ModAudioEngine.presetDecay = t.get("decay").tofloat().coerceIn(0.05f, 2f)
                    if (!t.get("vibrato").isnil()) ModAudioEngine.presetVibrato = t.get("vibrato").tofloat().coerceIn(0f, 1f)
                    if (!t.get("lp").isnil()) ModAudioEngine.presetLpCut = t.get("lp").tofloat().coerceIn(0.08f, 1f)
                    if (!t.get("duty").isnil()) ModAudioEngine.presetDuty = t.get("duty").tofloat().coerceIn(0.1f, 0.9f)
                }
                return LuaValue.NIL
            }
        })
        audio.set("startSeq", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val bpm = args.checkint(1)
                val len = args.optint(2, 16).coerceIn(4, 32)
                val flat = IntArray(4 * 32) { -1 }
                val nTbl = args.checktable(3)
                for (ch in 0 until 4) {
                    val row = nTbl.get(ch + 1)
                    if (row.istable()) {
                        val rt = row.checktable()
                        for (st in 0 until min(32, len)) flat[ch * 32 + st] = rt.get(st + 1).optint(-1)
                    }
                }
                val waves = intArrayOf(1, 0, 2, 3)
                if (args.narg() >= 4 && args.arg(4).istable()) {
                    val wt = args.arg(4).checktable()
                    for (c in 0 until 4) waves[c] = wt.get(c + 1).optint(waves[c])
                }
                val bg = args.optboolean(5, false)
                ModAudioEngine.startSeq(bpm, len, flat, waves, bg)
                return LuaValue.NIL
            }
        })
        audio.set("updateSeq", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val bpm = args.checkint(1)
                val len = args.optint(2, 16).coerceIn(4, 32)
                val flat = IntArray(4 * 32) { -1 }
                val nTbl = args.checktable(3)
                for (ch in 0 until 4) {
                    val row = nTbl.get(ch + 1)
                    if (row.istable()) {
                        val rt = row.checktable()
                        for (st in 0 until min(32, len)) flat[ch * 32 + st] = rt.get(st + 1).optint(-1)
                    }
                }
                val waves = intArrayOf(1, 0, 2, 3)
                if (args.narg() >= 4 && args.arg(4).istable()) {
                    val wt = args.arg(4).checktable()
                    for (c in 0 until 4) waves[c] = wt.get(c + 1).optint(waves[c])
                }
                ModAudioEngine.updateSeq(bpm, len, flat, waves)
                return LuaValue.NIL
            }
        })
        audio.set("stopSeq", object : ZeroArgFunction() { override fun call(): LuaValue { ModAudioEngine.stopSeq(); return LuaValue.NIL } })
        audio.set("isPlaying", object : ZeroArgFunction() { override fun call(): LuaValue = LuaValue.valueOf(ModAudioEngine.isPlaying) })
        audio.set("isBgPlaying", object : ZeroArgFunction() { override fun call(): LuaValue = LuaValue.valueOf(ModAudioEngine.playInWorldBg) })
        audio.set("getStep", object : ZeroArgFunction() { override fun call(): LuaValue = LuaValue.valueOf(ModAudioEngine.currentStep) })
        audio.set("getVu", object : OneArgFunction() { override fun call(ch: LuaValue): LuaValue = LuaValue.valueOf(ModAudioEngine.vuLevels[ch.checkint().coerceIn(0, 3)].toDouble()) })
        audio.set("muteOst", object : OneArgFunction() { override fun call(b: LuaValue): LuaValue { ModAudioEngine.forceMuteOst = b.toboolean(); return LuaValue.NIL } })
        audio.set("playSfx", object : TwoArgFunction() {
            override fun call(nm: LuaValue, vol: LuaValue): LuaValue {
                SoundFx.play(nm.checkjstring(), vol.optdouble(0.6).toFloat()); return LuaValue.NIL
            }
        })
        vh.set("audio", audio)

        // === VH.engine — UNRESTRICTED DIRECT LIVE ACCESS TO THE ENTIRE GAME ENGINE! ===
        val eng = LuaTable()
        eng.set("game", CoerceJavaToLua.coerce(game))
        eng.set("world", CoerceJavaToLua.coerce(game.world))
        eng.set("player", CoerceJavaToLua.coerce(game.player))
        eng.set("entities", CoerceJavaToLua.coerce(game.entities))
        eng.set("registry", CoerceJavaToLua.coerce(BlockRegistry))
        eng.set("sprites", CoerceJavaToLua.coerce(SpriteArt))
        eng.set("net", CoerceJavaToLua.coerce(game.netMp))
        eng.set("profiles", CoerceJavaToLua.coerce(game.profiles))
        eng.set("redstone", CoerceJavaToLua.coerce(game.redstone))
        eng.set("platform", CoerceJavaToLua.coerce(PlatformBridge))
        eng.set("studio", CoerceJavaToLua.coerce(game.modStudio))
        vh.set("engine", eng)

        g.set("VH", vh)
        globals = g

        for (m in mods) {
            if (!m.enabled || m.code.isBlank()) continue
            activeLoadingPack = m.packFile
            activeLoadingModId = m.id
            try {
                g.load(m.code, "${m.name} v${m.version}").call()
            } catch (e: Exception) {
                game.showToast("Ошибка Lua (${m.name}): ${e.message?.take(60)}")
            }
        }
        activeLoadingPack = null
        activeLoadingModId = "global"
        emitSimple("onLoad")
    }

    private fun emitSimple(ev: String) {
        val list = hooks[ev] ?: return
        for (fn in list) { try { fn.call() } catch (_: Exception) {} }
    }

    fun emitTick(dt: Float) {
        val list = hooks["onTick"] ?: return
        val v = LuaValue.valueOf(dt.toDouble())
        for (fn in list) { try { fn.call(v) } catch (_: Exception) {} }
    }

    fun emitRenderHud(w: Float, h: Float) {
        val list = hooks["onRenderHud"] ?: return
        val vw = LuaValue.valueOf(w.toDouble()); val vh = LuaValue.valueOf(h.toDouble())
        for (fn in list) { try { fn.call(vw, vh) } catch (_: Exception) {} }
    }

    fun emitRenderScreen(screenId: String, w: Float, h: Float) {
        val list = hooks["onRenderScreen"] ?: return
        val vs = LuaValue.valueOf(screenId); val vw = LuaValue.valueOf(w.toDouble()); val vh = LuaValue.valueOf(h.toDouble())
        for (fn in list) { try { fn.call(vs, vw, vh) } catch (_: Exception) {} }
    }

    fun emitBlockBreak(x: Int, y: Int, z: Int, id: Int, heldId: Int) {
        val list = hooks["onBlockBreak"] ?: return
        val args = LuaValue.varargsOf(arrayOf(LuaValue.valueOf(x), LuaValue.valueOf(y), LuaValue.valueOf(z), LuaValue.valueOf(id), LuaValue.valueOf(heldId)))
        for (fn in list) { try { fn.invoke(args) } catch (_: Exception) {} }
    }

    fun emitBlockPlace(x: Int, y: Int, z: Int, id: Int) {
        val list = hooks["onBlockPlace"] ?: return
        val args = LuaValue.varargsOf(arrayOf(LuaValue.valueOf(x), LuaValue.valueOf(y), LuaValue.valueOf(z), LuaValue.valueOf(id)))
        for (fn in list) { try { fn.invoke(args) } catch (_: Exception) {} }
    }

    fun emitBlockInteract(x: Int, y: Int, z: Int, id: Int, heldId: Int): Boolean {
        val list = hooks["onBlockInteract"] ?: return false
        val args = LuaValue.varargsOf(arrayOf(LuaValue.valueOf(x), LuaValue.valueOf(y), LuaValue.valueOf(z), LuaValue.valueOf(id), LuaValue.valueOf(heldId)))
        for (fn in list) {
            try {
                if (fn.invoke(args).arg1().toboolean()) return true
            } catch (_: Exception) {}
        }
        return false
    }

    fun emitItemUse(itemId: Int, x: Float, y: Float, z: Float): Boolean {
        val list = hooks["onItemUse"] ?: return false
        val args = LuaValue.varargsOf(arrayOf(LuaValue.valueOf(itemId), LuaValue.valueOf(x.toDouble()), LuaValue.valueOf(y.toDouble()), LuaValue.valueOf(z.toDouble())))
        for (fn in list) {
            try {
                if (fn.invoke(args).arg1().toboolean()) return true
            } catch (_: Exception) {}
        }
        return false
    }

    fun emitMobKill(type: String, name: String, x: Float, y: Float, z: Float, isBoss: Boolean) {
        val list = hooks["onMobKill"] ?: return
        val args = LuaValue.varargsOf(arrayOf(LuaValue.valueOf(type), LuaValue.valueOf(name), LuaValue.valueOf(x.toDouble()), LuaValue.valueOf(y.toDouble()), LuaValue.valueOf(z.toDouble()), LuaValue.valueOf(isBoss)))
        for (fn in list) { try { fn.invoke(args) } catch (_: Exception) {} }
    }

    fun emitPlayerDamage(rawDmg: Float): Boolean {
        val list = hooks["onPlayerDamage"] ?: return true
        val v = LuaValue.valueOf(rawDmg.toDouble())
        for (fn in list) {
            try {
                val res = fn.call(v)
                if (res.isboolean() && !res.toboolean()) return false
            } catch (_: Exception) {}
        }
        return true
    }

    fun emitChat(nick: String, msg: String) {
        val list = hooks["onChat"] ?: return
        val vn = LuaValue.valueOf(nick); val vm = LuaValue.valueOf(msg)
        for (fn in list) { try { fn.call(vn, vm) } catch (_: Exception) {} }
    }

    fun emitFileImport(name: String, content: String) {
        val list = hooks["onFileImport"] ?: return
        val vn = LuaValue.valueOf(name); val vc = LuaValue.valueOf(content)
        for (fn in list) { try { fn.call(vn, vc) } catch (_: Exception) {} }
    }

    fun dispose() {
        for (t in modTextures.values) { try { t.dispose() } catch (_: Exception) {} }
        modTextures.clear(); modTexRegions.clear()
    }
}
