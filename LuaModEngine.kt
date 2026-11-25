package com.voxelhorizon.nativegame

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File
import java.util.zip.ZipFile

data class LuaModEntry(
    val id: String,
    val name: String,
    val desc: String,
    var enabled: Boolean,
    val code: String
)

class LuaModEngine(private val modsDir: File, private val game: VoxelHorizonGame) {
    val mods = mutableListOf<LuaModEntry>()
    private val hooks = HashMap<String, MutableList<LuaValue>>()
    private var globals: Globals? = null

    init {
        mods += LuaModEntry(
            "builtin_portal", "Быстрый Портал (/portal)", "Строит обсидиановый портал перед игроком", true,
            """
            VH.on("onChat", function(txt)
                if txt == "/portal" then
                    VH.buildPortal()
                    VH.toast("Портал в Ад построен Lua-модом!")
                end
            end)
            """.trimIndent()
        )
        mods += LuaModEntry(
            "builtin_vein", "Супер-Кирка 3x3 (Lua)", "Алмазная кирка копает сразу 3x3 блока", false,
            """
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
            """.trimIndent()
        )
        mods += LuaModEntry(
            "builtin_moon", "Лунная Гравитация (Lua)", "Ослабляет гравитацию и усиливает прыжок", false,
            """
            VH.setGravity(10.5)
            VH.toast("Lua-мод: Лунная гравитация активна!")
            """.trimIndent()
        )
        scanExternalMods()
    }

    fun scanExternalMods() {
        modsDir.mkdirs()
        modsDir.listFiles()?.forEach { f ->
            if (f.name.endsWith(".lua")) {
                val id = "ext_" + f.nameWithoutExtension
                if (mods.none { it.id == id }) {
                    mods += LuaModEntry(id, f.nameWithoutExtension, "Внешний Lua-мод (${f.name})", true, f.readText())
                }
            } else if (f.name.endsWith(".vhpack") || f.name.endsWith(".zip")) {
                try {
                    ZipFile(f).use { zf ->
                        val entry = zf.getEntry("main.lua") ?: zf.getEntry("scripts/main.lua")
                        if (entry != null) {
                            val code = zf.getInputStream(entry).bufferedReader().readText()
                            val id = "pack_" + f.nameWithoutExtension
                            if (mods.none { it.id == id }) {
                                mods += LuaModEntry(id, f.nameWithoutExtension, "Пакет .vhpack (${f.name})", true, code)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun hotReload() {
        hooks.clear()
        game.gravity = 21f
        scanExternalMods()

        val g = JsePlatform.standardGlobals()
        val vh = LuaTable()

        vh.set("toast", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue { game.showToast(arg.tojstring()); return LuaValue.NIL }
        })
        vh.set("setBlock", object : VarArgFunction() {
            override fun invoke(args: org.luaj.vm2.Varargs): org.luaj.vm2.Varargs {
                game.setWorldBlock(args.checkint(1), args.checkint(2), args.checkint(3), args.checkint(4))
                return LuaValue.NIL
            }
        })
        vh.set("getBlock", object : ThreeArgFunction() {
            override fun call(x: LuaValue, y: LuaValue, z: LuaValue): LuaValue {
                return LuaValue.valueOf(game.world.getBlock(x.checkint(), y.checkint(), z.checkint()))
            }
        })
        vh.set("give", object : TwoArgFunction() {
            override fun call(id: LuaValue, cnt: LuaValue): LuaValue {
                game.player.addItem(id.checkint(), if (cnt.isnil()) 1 else cnt.checkint())
                return LuaValue.NIL
            }
        })
        vh.set("spawnMob", object : OneArgFunction() {
            override fun call(t: LuaValue): LuaValue {
                game.entities.spawnMob(t.tojstring(), game.player.x + 3f, game.player.y + 1f, game.player.z + 3f)
                return LuaValue.NIL
            }
        })
        vh.set("buildPortal", object : OneArgFunction() {
            override fun call(arg: LuaValue): LuaValue {
                game.world.buildNetherPortalAt(game.player.x.toInt() + 2, game.player.y.toInt(), game.player.z.toInt())
                return LuaValue.NIL
            }
        })
        vh.set("setGravity", object : OneArgFunction() {
            override fun call(gv: LuaValue): LuaValue { game.gravity = gv.tofloat(); return LuaValue.NIL }
        })
        vh.set("switchDim", object : OneArgFunction() {
            override fun call(d: LuaValue): LuaValue { game.switchDimension(d.tojstring()); return LuaValue.NIL }
        })
        vh.set("registerBlock", object : VarArgFunction() {
            override fun invoke(args: org.luaj.vm2.Varargs): org.luaj.vm2.Varargs {
                BlockRegistry.registerModBlock(
                    args.checkint(1), args.checkjstring(2),
                    args.optdouble(3, 0.2).toFloat(), args.optdouble(4, 0.85).toFloat(), args.optdouble(5, 0.45).toFloat()
                )
                return LuaValue.NIL
            }
        })
        vh.set("on", object : TwoArgFunction() {
            override fun call(ev: LuaValue, fn: LuaValue): LuaValue {
                if (fn.isfunction()) hooks.computeIfAbsent(ev.tojstring()) { mutableListOf() }.add(fn)
                return LuaValue.NIL
            }
        })

        g.set("VH", vh)
        globals = g

        for (m in mods) {
            if (!m.enabled) continue
            try {
                g.load(m.code, m.name).call()
            } catch (e: Exception) {
                game.showToast("Ошибка Lua (${m.name}): ${e.message}")
            }
        }
    }

    fun emitBlockBreak(x: Int, y: Int, z: Int, id: Int, heldId: Int) {
        val list = hooks["onBlockBreak"] ?: return
        for (fn in list) {
            try {
                fn.invoke(LuaValue.varargsOf(arrayOf(
                    LuaValue.valueOf(x), LuaValue.valueOf(y), LuaValue.valueOf(z),
                    LuaValue.valueOf(id), LuaValue.valueOf(heldId)
                )))
            } catch (_: Exception) {}
        }
    }

    fun emitChat(text: String) {
        val list = hooks["onChat"] ?: return
        for (fn in list) {
            try { fn.call(LuaValue.valueOf(text)) } catch (_: Exception) {}
        }
    }
}
