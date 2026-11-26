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

data class AchievementDef(
    val id: String,
    val title: String,
    val desc: String,
    val rewardEmeralds: Int,
    val rewardXp: Int
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
    var skinId: String = "chibi_scarf",
    var miningXp: Int = 0,
    var combatXp: Int = 0,
    var farmXp: Int = 0,
    val bestiaryKills: HashMap<String, Int> = HashMap(),
    val friends: CopyOnWriteArrayList<FriendEntry> = CopyOnWriteArrayList(),
    var activeQuestIdx: Int = 0,
    var cloakId: Int = 0,
    var enchAutoSmelt: Boolean = false,
    var enchFortune: Int = 0,
    var enchFireAspect: Boolean = false,
    var enchLifesteal: Boolean = false,
    var enchHaste: Boolean = false,
    val achievements: HashSet<String> = HashSet()
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

    val achievementDefs = listOf(
        AchievementDef("first_mine",    "Первая жила",         "Добудьте первую руду в шахте",                2, 40),
        AchievementDef("deep_miner",    "На дне мира",         "Спуститесь ниже Z = 25",                      4, 90),
        AchievementDef("redstone_eng",  "Инженер Редстоуна",   "Активируйте механизм на Редстоуне",           3, 70),
        AchievementDef("wall_light",    "Уютный дом",          "Повесьте факел на стену",                     2, 40),
        AchievementDef("farmer_pro",    "Мастер урожая",       "Вспашите грядку или вырастите урожай",        2, 50),
        AchievementDef("golden_fish",   "Золотой улов",        "Поймайте рыбу на удочку",                     3, 60),
        AchievementDef("enchanter",     "Чародей",             "Наложите зачарование на Столе Зачарований",   4, 80),
        AchievementDef("boss_slayer",   "Покоритель Боссов",   "Победите любого из 5 Боссов",                 6, 150),
        AchievementDef("pet_master",    "Повелитель питомцев", "Призовите Сову или Голема-носильщика",        3, 70),
        AchievementDef("rider",         "Ветерок",             "Прокатитесь на Вагонетке, Лодке или Олене",   3, 60),
        AchievementDef("builder_box",   "Архитектор",          "Постройте участок заливкой (Box Fill)",       2, 50),
        AchievementDef("online_friend", "Вместе веселее",      "Добавьте друга или зайдите в мультиплеер",    3, 60)
    )

    fun unlockAchievement(id: String): AchievementDef? {
        if (active.achievements.contains(id)) return null
        val def = achievementDefs.firstOrNull { it.id == id } ?: return null
        active.achievements.add(id)
        active.miningXp += def.rewardXp / 2
        active.combatXp += def.rewardXp / 2
        saveProfiles()
        return def
    }

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

    private fun parseProfileSnapshot(text: String): Pair<Int, MutableList<PlayerProfile>> {
        var actIdx = 0
        val list = mutableListOf<PlayerProfile>()
        try {
            var cur: PlayerProfile? = null
            for (raw in text.lines()) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                val p = line.split("|")
                when (p[0]) {
                    "ACTIVE" -> actIdx = p.getOrNull(1)?.toIntOrNull() ?: 0
                    "PROF" -> if (p.size >= 7) {
                        cur = PlayerProfile(
                            friendId = p[1],
                            nick = p[2],
                            skinId = when (p[3]) { "fox_officer" -> "chibi_fox"; "estelle_cmd" -> "chibi_music"; "luna_cat" -> "chibi_cat"; "shadow_knight" -> "chibi_knight"; "emerald_scout" -> "chibi_witch"; "classic" -> "chibi_classic"; else -> p[3] },
                            miningXp = p[4].toIntOrNull() ?: 0,
                            combatXp = p[5].toIntOrNull() ?: 0,
                            farmXp = p[6].toIntOrNull() ?: 0,
                            activeQuestIdx = p.getOrNull(7)?.toIntOrNull() ?: 0,
                            cloakId = p.getOrNull(8)?.toIntOrNull() ?: 0,
                            enchAutoSmelt = p.getOrNull(9) == "1",
                            enchFortune = p.getOrNull(10)?.toIntOrNull() ?: 0,
                            enchFireAspect = p.getOrNull(11) == "1",
                            enchLifesteal = p.getOrNull(12) == "1",
                            enchHaste = p.getOrNull(13) == "1"
                        )
                        list += cur
                    }
                    "KILL" -> if (cur != null && p.size >= 3) cur.bestiaryKills[p[1]] = p[2].toIntOrNull() ?: 1
                    "FRIEND" -> if (cur != null && p.size >= 3) cur.friends.add(FriendEntry(p[1], p[2]))
                    "ACH" -> if (cur != null && p.size >= 2) cur.achievements.add(p[1])
                }
            }
        } catch (_: Exception) {}
        return Pair(actIdx, list)
    }

    private fun progressScore(list: List<PlayerProfile>): Long {
        if (list.isEmpty()) return -1L
        var score = list.size.toLong() * 50L
        for (pr in list) {
            score += (pr.miningXp + pr.combatXp + pr.farmXp).toLong()
            score += pr.friends.size.toLong() * 80L
            score += pr.achievements.size.toLong() * 120L
            score += pr.bestiaryKills.values.sum().toLong() * 10L
            if (!pr.nick.startsWith("Игрок_")) score += 200L
        }
        return score
    }

    fun loadAll() {
        profiles.clear()
        // Проверяем ВСЕ 4 резервных хранилища (SharedPreferences, filesDir, storageRoot, Documents) и берём самое полное/свежее!
        val candidates = mutableListOf<Pair<String, Long>>()
        val fPrimary = primaryProfileFile()
        if (fPrimary.exists() && fPrimary.length() > 10) {
            try { candidates += Pair(fPrimary.readText(Charsets.UTF_8), fPrimary.lastModified()) } catch (_: Exception) {}
        }
        val fMirror = mirrorProfileFile()
        if (fMirror != null && fMirror.exists() && fMirror.length() > 10) {
            try { candidates += Pair(fMirror.readText(Charsets.UTF_8), fMirror.lastModified()) } catch (_: Exception) {}
        }
        candidates.addAll(PlatformBridge.loadProfileVaultCandidates())

        var bestAct = 0
        var bestList: MutableList<PlayerProfile>? = null
        var bestScore = -1L
        var bestTs = -1L
        for ((txt, ts) in candidates) {
            val (aIdx, parsed) = parseProfileSnapshot(txt)
            if (parsed.isEmpty()) continue
            val sc = progressScore(parsed)
            if (sc > bestScore || (sc == bestScore && ts > bestTs)) {
                bestScore = sc; bestTs = ts; bestAct = aIdx; bestList = parsed
            }
        }
        if (bestList != null) {
            profiles.addAll(bestList)
            activeIndex = bestAct.coerceIn(0, profiles.lastIndex)
            // Синхронизируем восстановленный профиль во все хранилища, чтобы не потерялся!
            saveProfiles()
        } else {
            createDefaultProfile()
            saveProfiles()
        }
        loadWorldSlots()
    }

    private fun createDefaultProfile() {
        val fid = "VH-${Random.nextInt(10000, 99999)}"
        profiles += PlayerProfile(fid, "Игрок_${fid.takeLast(3)}", "chibi_scarf")
        activeIndex = profiles.lastIndex
    }

    fun createProfile(nickRaw: String): PlayerProfile {
        val nick = nickRaw.trim().replace("|", "").take(18).ifBlank { "Герой_${Random.nextInt(10, 99)}" }
        val fid = "VH-${Random.nextInt(10000, 99999)}"
        val pr = PlayerProfile(fid, nick, "chibi_scarf")
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
                .append(pr.combatXp).append('|').append(pr.farmXp).append('|').append(pr.activeQuestIdx).append('|').append(pr.cloakId).append('|').append(if (pr.enchAutoSmelt) "1" else "0").append('|').append(pr.enchFortune).append('|').append(if (pr.enchFireAspect) "1" else "0").append('|').append(if (pr.enchLifesteal) "1" else "0").append('|').append(if (pr.enchHaste) "1" else "0").append('\n')
            for ((k, v) in pr.bestiaryKills) sb.append("KILL|").append(k).append('|').append(v).append('\n')
            for (fr in pr.friends) sb.append("FRIEND|").append(fr.friendId).append('|').append(fr.nick).append('\n')
            for (ac in pr.achievements) sb.append("ACH|").append(ac).append('\n')
        }
        val text = sb.toString()
        try {
            primaryProfileFile().parentFile?.mkdirs()
            primaryProfileFile().writeText(text, Charsets.UTF_8)
        } catch (_: Exception) {}
        try {
            mirrorProfileFile()?.let { mf -> mf.parentFile?.mkdirs(); mf.writeText(text, Charsets.UTF_8) }
        } catch (_: Exception) {}
        PlatformBridge.saveProfileVault(text)
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
