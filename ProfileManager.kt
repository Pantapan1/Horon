package com.voxelhorizon.nativegame

import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

data class FriendEntry(
    val friendId: String,
    var nick: String,
    var online: Boolean = false,
    var roomCode: String = "",
    var brokerIdx: Int = 0,
    var lastSeenMs: Long = 0L
)

data class WorldSlotInfo(
    val fileName: String,
    val title: String,
    val seed: Int,
    val creative: Boolean,
    val seasonDays: Int,
    val lastPlayedMs: Long
)

data class ActiveQuest(
    val id: Int,
    val title: String,
    val targetItemId: Int,
    val targetCount: Int,
    val rewardEmeralds: Int,
    val rewardXp: Int
)

data class PlayerProfile(
    val friendId: String,
    var nick: String,
    var skinId: String = "fox_officer",
    var miningXp: Int = 0,
    var combatXp: Int = 0,
    var farmXp: Int = 0,
    val bestiaryKills: HashMap<String, Int> = HashMap(),
    val friends: CopyOnWriteArrayList<FriendEntry> = CopyOnWriteArrayList(),
    var activeQuestIdx: Int = 0
) {
    fun miningLevel(): Int = 1 + (miningXp / 50).coerceAtMost(19)
    fun combatLevel(): Int = 1 + (combatXp / 60).coerceAtMost(19)
    fun farmLevel(): Int = 1 + (farmXp / 40).coerceAtMost(19)
    fun totalLevel(): Int = miningLevel() + combatLevel() + farmLevel() - 2
}

class ProfileManager(private val storageRoot: File) {
    val profiles = mutableListOf<PlayerProfile>()
    var activeIndex = 0
    val worldSlots = mutableListOf<WorldSlotInfo>()

    val quests = listOf(
        ActiveQuest(0, "Добудь 5 Осколков аметиста", 141, 5, 4, 80),
        ActiveQuest(1, "Поймай 3 Карася на удочку", 151, 3, 4, 70),
        ActiveQuest(2, "Собери 6 Пшеницы с фермы", 143, 6, 3, 60),
        ActiveQuest(3, "Принеси 3 Рубина из глубин", 142, 3, 6, 110),
        ActiveQuest(4, "Добудь 1 Сердце Леса (Босс)", 133, 1, 10, 180),
        ActiveQuest(5, "Поймай 1 Золотую Рыбку", 154, 1, 8, 150)
    )

    val active: PlayerProfile
        get() {
            if (profiles.isEmpty()) createDefaultProfile()
            return profiles[activeIndex.coerceIn(0, profiles.lastIndex)]
        }

    fun currentQuest(): ActiveQuest = quests[active.activeQuestIdx % quests.size]

    private fun primaryProfileFile(): File = File(storageRoot, "profiles_v62.dat")
    private fun mirrorProfileFile(): File? = PlatformBridge.backupDir?.let { File(it, "profiles_v62.dat") }
    private fun worldIndexFile(): File = File(File(storageRoot, "worlds"), "worlds_index.dat")

    fun loadAll() {
        profiles.clear()
        val fPrimary = primaryProfileFile()
        val fMirror = mirrorProfileFile()
        val src = when {
            fPrimary.exists() && fPrimary.length() > 10 -> fPrimary
            fMirror != null && fMirror.exists() && fMirror.length() > 10 -> fMirror
            else -> null
        }
        if (src != null) {
            try {
                var cur: PlayerProfile? = null
                for (raw in src.readLines(Charsets.UTF_8)) {
                    val line = raw.trim()
                    if (line.isEmpty()) continue
                    val p = line.split("|")
                    when (p[0]) {
                        "ACTIVE" -> activeIndex = p.getOrNull(1)?.toIntOrNull() ?: 0
                        "PROF" -> if (p.size >= 7) {
                            cur = PlayerProfile(
                                friendId = p[1],
                                nick = p[2],
                                skinId = p[3],
                                miningXp = p[4].toIntOrNull() ?: 0,
                                combatXp = p[5].toIntOrNull() ?: 0,
                                farmXp = p[6].toIntOrNull() ?: 0,
                                activeQuestIdx = p.getOrNull(7)?.toIntOrNull() ?: 0
                            )
                            profiles += cur
                        }
                        "KILL" -> if (cur != null && p.size >= 3) cur.bestiaryKills[p[1]] = p[2].toIntOrNull() ?: 1
                        "FRIEND" -> if (cur != null && p.size >= 3) cur.friends.add(FriendEntry(p[1], p[2]))
                    }
                }
            } catch (_: Exception) {}
        }
        if (profiles.isEmpty()) {
            createDefaultProfile()
            saveProfiles()
        }
        activeIndex = activeIndex.coerceIn(0, profiles.lastIndex)
        loadWorldSlots()
    }

    private fun createDefaultProfile() {
        val fid = "VH-${Random.nextInt(10000, 99999)}"
        profiles += PlayerProfile(fid, "Игрок_${fid.takeLast(3)}", "fox_officer")
        activeIndex = profiles.lastIndex
    }

    fun createProfile(nickRaw: String): PlayerProfile {
        val nick = nickRaw.trim().replace("|", "").take(18).ifBlank { "Герой_${Random.nextInt(10, 99)}" }
        val fid = "VH-${Random.nextInt(10000, 99999)}"
        val pr = PlayerProfile(fid, nick, "fox_officer")
        profiles += pr
        activeIndex = profiles.lastIndex
        saveProfiles()
        return pr
    }

    fun deleteProfile(idx: Int): Boolean {
        if (profiles.size <= 1 || idx !in profiles.indices) return false
        profiles.removeAt(idx)
        activeIndex = activeIndex.coerceIn(0, profiles.lastIndex)
        saveProfiles()
        return true
    }

    fun addFriend(friendIdRaw: String, nickRaw: String): Boolean {
        val fid = friendIdRaw.trim().uppercase().replace("|", "").take(16)
        if (fid.length < 4 || fid == active.friendId) return false
        val nick = nickRaw.trim().replace("|", "").take(18).ifBlank { fid }
        if (active.friends.any { it.friendId == fid }) return false
        active.friends.add(FriendEntry(fid, nick))
        saveProfiles()
        return true
    }

    fun removeFriend(fid: String) {
        active.friends.removeIf { it.friendId == fid }
        saveProfiles()
    }

    fun recordMobKill(mobType: String, isBoss: Boolean): Boolean {
        val prevLvl = active.combatLevel()
        active.bestiaryKills[mobType] = (active.bestiaryKills[mobType] ?: 0) + 1
        active.combatXp += if (isBoss) 65 else 14
        saveProfiles()
        return active.combatLevel() > prevLvl
    }

    fun addMiningXp(amount: Int): Boolean {
        val prev = active.miningLevel()
        active.miningXp += amount
        if (active.miningLevel() > prev) { saveProfiles(); return true }
        return false
    }

    fun addFarmXp(amount: Int): Boolean {
        val prev = active.farmLevel()
        active.farmXp += amount
        if (active.farmLevel() > prev) { saveProfiles(); return true }
        return false
    }

    fun saveProfiles() {
        val sb = StringBuilder()
        sb.append("ACTIVE|").append(activeIndex).append('\n')
        for (pr in profiles) {
            sb.append("PROF|").append(pr.friendId).append('|').append(pr.nick).append('|')
                .append(pr.skinId).append('|').append(pr.miningXp).append('|')
                .append(pr.combatXp).append('|').append(pr.farmXp).append('|').append(pr.activeQuestIdx).append('\n')
            for ((k, v) in pr.bestiaryKills) sb.append("KILL|").append(k).append('|').append(v).append('\n')
            for (fr in pr.friends) sb.append("FRIEND|").append(fr.friendId).append('|').append(fr.nick).append('\n')
        }
        val text = sb.toString()
        try {
            primaryProfileFile().parentFile?.mkdirs()
            primaryProfileFile().writeText(text, Charsets.UTF_8)
        } catch (_: Exception) {}
        try {
            mirrorProfileFile()?.let { mf -> mf.parentFile?.mkdirs(); mf.writeText(text, Charsets.UTF_8) }
        } catch (_: Exception) {}
    }

    // === MULTI-WORLD SLOTS ===
    fun loadWorldSlots() {
        worldSlots.clear()
        val f = worldIndexFile()
        if (f.exists()) {
            try {
                for (raw in f.readLines(Charsets.UTF_8)) {
                    val p = raw.trim().split("|")
                    if (p.size >= 6) {
                        worldSlots += WorldSlotInfo(
                            fileName = p[0], title = p[1], seed = p[2].toIntOrNull() ?: 1337,
                            creative = p[3] == "1", seasonDays = p[4].toIntOrNull() ?: 60,
                            lastPlayedMs = p[5].toLongOrNull() ?: 0L
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        // Discover any existing .vhworld files not yet listed
        val dir = File(storageRoot, "worlds")
        dir.listFiles()?.filter { it.name.endsWith(".vhworld") }?.forEach { wf ->
            if (worldSlots.none { it.fileName == wf.name }) {
                worldSlots += WorldSlotInfo(wf.name, wf.name.removeSuffix(".vhworld"), Random.nextInt(100000, 999999), false, 60, wf.lastModified())
            }
        }
        worldSlots.sortByDescending { it.lastPlayedMs }
    }

    fun registerWorldSlot(slot: WorldSlotInfo) {
        worldSlots.removeIf { it.fileName == slot.fileName }
        worldSlots.add(0, slot)
        saveWorldSlots()
    }

    fun deleteWorldSlot(fileName: String) {
        worldSlots.removeIf { it.fileName == fileName }
        try { File(File(storageRoot, "worlds"), fileName).delete() } catch (_: Exception) {}
        saveWorldSlots()
    }

    fun saveWorldSlots() {
        val sb = StringBuilder()
        for (w in worldSlots) {
            sb.append(w.fileName).append('|').append(w.title.replace("|", "")).append('|')
                .append(w.seed).append('|').append(if (w.creative) "1" else "0").append('|')
                .append(w.seasonDays).append('|').append(w.lastPlayedMs).append('\n')
        }
        try {
            worldIndexFile().parentFile?.mkdirs()
            worldIndexFile().writeText(sb.toString(), Charsets.UTF_8)
        } catch (_: Exception) {}
    }
}
