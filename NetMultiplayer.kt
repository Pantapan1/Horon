package com.voxelhorizon.nativegame

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.thread
import kotlin.math.hypot
import kotlin.random.Random

enum class ModSyncState { IDLE, CHECKING, PROMPT, DOWNLOADING, DONE }

data class HostModManifestItem(
    val id: String,
    val name: String,
    val version: String,
    val fileName: String,
    val sizeBytes: Int,
    val crcHex: String
)

data class RemotePlayer(
    val id: String,
    var nick: String,
    var skinId: String,
    var x: Float, var y: Float, var z: Float,
    var targetX: Float = x, var targetY: Float = y, var targetZ: Float = z,
    var facingRight: Boolean = true,
    var walkAnim: Float = 0f,
    var isMoving: Boolean = false,
    var attackAnim: Float = 0f,
    var isSleeping: Boolean = false,
    var heldId: Int = 0,
    var hp: Float = 20f,
    var maxHp: Float = 20f,
    var emoteIdx: Int = -1,
    var emoteTimer: Float = 0f,
    var lastSeenMs: Long = System.currentTimeMillis()
)

class NetMultiplayer(private val game: VoxelHorizonGame) {
    companion object {
        const val TCP_PORT = 25566
        const val UDP_DISCOVERY_PORT = 25567
        // Публичные облачные MQTT-релеи (работают через 4G/LTE и разные Wi-Fi без проброса портов!)
        val ONLINE_BROKERS = listOf(
            "broker.emqx.io" to 1883,
            "broker.hivemq.com" to 1883,
            "test.mosquitto.org" to 1883
        )
    }

    @Volatile var isHost = false
    @Volatile var isConnected = false
    @Volatile var isOnlineRelay = false
    @Volatile var roomCode = ""
    @Volatile var statusText = "Одиночная игра"
    var localNick = "Игрок"
    @Volatile var pvpEnabled = false
    @Volatile var selectedBrokerIdx = 0
    @Volatile var roomPassword = ""
    private var presenceRunning = false
    private val localPeerId = Random.nextInt(100000, 999999999)

    val remotePlayers = ConcurrentHashMap<String, RemotePlayer>()

    // LAN / Direct TCP state
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private val clientStreams = CopyOnWriteArrayList<DataOutputStream>()
    private var clientOut: DataOutputStream? = null

    // Online MQTT Relay state
    private var mqttSocket: Socket? = null
    private var mqttOut: DataOutputStream? = null
    private var mqttTopic = ""
    private val netSendExec = Executors.newSingleThreadExecutor { r -> Thread(r, "vh-net-send").apply { isDaemon = true } }
    private val voiceSendExec = Executors.newSingleThreadExecutor { r -> Thread(r, "vh-voice-send").apply { isDaemon = true } }
    private val voiceInFlight = AtomicBoolean(false)
    private val modTransferExec = Executors.newSingleThreadExecutor { r -> Thread(r, "vh-mod-xfer").apply { isDaemon = true } }

    // === Автосинхронизация модов от Хоста (Bedrock-style v6.13) ===
    @Volatile var modSyncState = ModSyncState.IDLE
    @Volatile var hostGameVersion = ""
    @Volatile var hostManifestMods: List<HostModManifestItem> = emptyList()
    @Volatile var missingHostMods: List<HostModManifestItem> = emptyList()
    @Volatile var modSyncStatusText = ""
    @Volatile var modSyncCurrentName = ""
    @Volatile var modSyncDoneFiles = 0
    @Volatile var modSyncTotalFiles = 0
    @Volatile var modSyncDoneBytes = 0
    @Volatile var modSyncTotalBytes = 0
    @Volatile private var worldInitReceived = false
    @Volatile private var activeDlModId = ""
    @Volatile private var activeDlChunks: Array<ByteArray?>? = null
    @Volatile private var activeDlReceivedCount = 0
    @Volatile private var activeDlLastChunkMs = 0L
    private val posInFlight = AtomicBoolean(false)
    private val mobsInFlight = AtomicBoolean(false)
    private val dropsInFlight = AtomicBoolean(false)

    // =========================================================================
    // 1. ОНЛАЙН-МУЛЬТИПЛЕЕР ЧЕРЕЗ ИНТЕРНЕТ (КОД КОМНАТЫ — РАБОТАЕТ НА ЛЮБОМ РАССТОЯНИИ!)
    // =========================================================================
    fun brokerHostName(): String = ONLINE_BROKERS[selectedBrokerIdx.coerceIn(0, ONLINE_BROKERS.lastIndex)].first

    fun cycleBroker() {
        selectedBrokerIdx = (selectedBrokerIdx + 1) % ONLINE_BROKERS.size
    }

    private fun buildRoomTopic(code: String): String {
        val cleanPass = roomPassword.trim()
        return if (cleanPass.isEmpty()) "vh62/room/$code" else "vh62/room/${code}_${cleanPass.hashCode().and(0xFFFF)}"
    }

    fun startOnlineHost() {
        stop()
        val code = Random.nextInt(1000, 9999).toString()
        roomCode = code
        isHost = true
        isOnlineRelay = true
        statusText = "Подключение к облаку..."
        connectMqttRelay(code, asHost = true)
    }

    fun joinOnlineRoom(codeRaw: String) {
        val code = codeRaw.trim().filter { it.isLetterOrDigit() }
        if (code.isEmpty()) return
        stop()
        roomCode = code
        isHost = false
        isOnlineRelay = true
        statusText = "Вход в комнату #$code..."
        connectMqttRelay(code, asHost = false)
    }

    private fun connectMqttRelay(code: String, asHost: Boolean) {
        mqttTopic = buildRoomTopic(code)
        thread(isDaemon = true) {
            var connectedBroker = ""
            var sock: Socket? = null
            // Try user-selected broker FIRST so both friends reliably hit the exact same server!
            val ordered = listOf(ONLINE_BROKERS[selectedBrokerIdx.coerceIn(0, ONLINE_BROKERS.lastIndex)]) +
                ONLINE_BROKERS.filterIndexed { idx, _ -> idx != selectedBrokerIdx }
            for ((host, port) in ordered) {
                try {
                    val s = Socket()
                    s.connect(InetSocketAddress(host, port), 4500)
                    s.tcpNoDelay = true
                    s.soTimeout = 0
                    sock = s
                    connectedBroker = host
                    break
                } catch (_: Exception) {}
            }
            if (sock == null) {
                statusText = "Нет связи с облаком (проверьте интернет)"
                isConnected = false; isOnlineRelay = false
                return@thread
            }

            try {
                mqttSocket = sock
                val out = DataOutputStream(sock.getOutputStream())
                val inp = DataInputStream(sock.getInputStream())
                mqttOut = out

                // 1. MQTT v3.1.1 CONNECT
                sendMqttConnect(out, "vh53_${localPeerId}")
                val ackType = inp.readUnsignedByte()
                val ackLen = readMqttVarInt(inp)
                val ackBytes = ByteArray(ackLen); inp.readFully(ackBytes)
                if ((ackType and 0xF0) != 0x20 || ackBytes.size < 2 || ackBytes[1].toInt() != 0) {
                    throw RuntimeException("MQTT CONNACK rejected")
                }

                // 2. MQTT SUBSCRIBE to room topic
                sendMqttSubscribe(out, 1, mqttTopic)
                isConnected = true
                statusText = if (asHost) "Онлайн Хост • Код: $code ($connectedBroker)" else "Онлайн в комнате #$code!"
                game.showToast(if (asHost) "Онлайн-комната #$code открыта! Сообщите код другу." else "Подключено к комнате #$code!")

                // Keepalive ping thread (25s)
                thread(isDaemon = true) {
                    while (isConnected && isOnlineRelay && !sock.isClosed) {
                        try {
                            Thread.sleep(25000)
                            if (isConnected && isOnlineRelay) {
                                synchronized(out) { out.writeByte(0xC0); out.writeByte(0x00); out.flush() }
                            }
                        } catch (_: Exception) { break }
                    }
                }

                // If guest -> ask Host for mod manifest first (v6.13), with automatic fallback to old hosts!
                if (!asHost) {
                    modSyncState = ModSyncState.CHECKING
                    modSyncStatusText = "Проверка модов хоста..."
                    publishOnlinePacket { p -> p.writeByte(18); p.writeUTF(PlatformBridge.CURRENT_VERSION) } // MOD_HELLO_REQ
                    thread(isDaemon = true) {
                        try { Thread.sleep(2600) } catch (_: Exception) {}
                        if (isConnected && !isHost && modSyncState == ModSyncState.CHECKING && !worldInitReceived) {
                            modSyncState = ModSyncState.DONE
                            sendJoinWorldRequest()
                        }
                    }
                }

                // 3. MQTT Receive Loop
                while (isConnected && isOnlineRelay && !sock.isClosed) {
                    val header = inp.readUnsignedByte()
                    val remLen = readMqttVarInt(inp)
                    val body = ByteArray(remLen)
                    if (remLen > 0) inp.readFully(body)

                    val pktType = header and 0xF0
                    if (pktType == 0x30) { // PUBLISH
                        val bis = DataInputStream(ByteArrayInputStream(body))
                        val tLen = bis.readUnsignedShort()
                        bis.skipBytes(tLen)
                        val qos = (header ushr 1) and 0x03
                        if (qos > 0) bis.skipBytes(2)
                        val senderId = bis.readInt()
                        if (senderId == localPeerId) continue // Ignore own echo!
                        handleIncomingPayload(senderId.toString(), bis, asHost)
                    }
                }
            } catch (e: Exception) {
                if (isOnlineRelay) statusText = "Онлайн отключён: ${e.message ?: "разрыв"}"
            } finally {
                if (isOnlineRelay) {
                    isConnected = false
                    try { sock.close() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun writeMqttVarInt(out: ByteArrayOutputStream, valueIn: Int) {
        var v = valueIn
        do {
            var encoded = v % 128
            v /= 128
            if (v > 0) encoded = encoded or 0x80
            out.write(encoded)
        } while (v > 0)
    }

    private fun readMqttVarInt(inp: DataInputStream): Int {
        var mult = 1; var value = 0
        while (true) {
            val b = inp.readUnsignedByte()
            value += (b and 0x7F) * mult
            if ((b and 0x80) == 0) break
            mult *= 128
        }
        return value
    }

    private fun sendMqttConnect(out: DataOutputStream, clientId: String) {
        val payload = ByteArrayOutputStream()
        val pOut = DataOutputStream(payload)
        pOut.writeUTF("MQTT")
        pOut.writeByte(4)    // Protocol level 3.1.1
        pOut.writeByte(0x02) // Clean session
        pOut.writeShort(60)  // KeepAlive 60s
        pOut.writeUTF(clientId)
        val body = payload.toByteArray()

        val pkt = ByteArrayOutputStream()
        pkt.write(0x10)
        writeMqttVarInt(pkt, body.size)
        pkt.write(body)
        synchronized(out) { out.write(pkt.toByteArray()); out.flush() }
    }

    private fun sendMqttSubscribe(out: DataOutputStream, packetId: Int, topic: String) {
        val payload = ByteArrayOutputStream()
        val pOut = DataOutputStream(payload)
        pOut.writeShort(packetId)
        pOut.writeUTF(topic)
        pOut.writeByte(0) // QoS 0
        val body = payload.toByteArray()

        val pkt = ByteArrayOutputStream()
        pkt.write(0x82)
        writeMqttVarInt(pkt, body.size)
        pkt.write(body)
        synchronized(out) { out.write(pkt.toByteArray()); out.flush() }
    }

    private fun publishOnlinePacket(writer: (DataOutputStream) -> Unit) {
        val out = mqttOut ?: return
        if (!isConnected || !isOnlineRelay) return
        try {
            val payload = ByteArrayOutputStream()
            val pOut = DataOutputStream(payload)
            pOut.writeUTF(mqttTopic)
            pOut.writeInt(localPeerId)
            writer(pOut)
            val body = payload.toByteArray()

            val pkt = ByteArrayOutputStream()
            pkt.write(0x30) // PUBLISH QoS 0
            writeMqttVarInt(pkt, body.size)
            pkt.write(body)
            synchronized(out) { out.write(pkt.toByteArray()); out.flush() }
        } catch (_: Exception) {}
    }

    // =========================================================================
    // 2. ЛОКАЛЬНАЯ СЕТЬ (LAN Wi-Fi) И ПРЯМОЙ IP (TCP)
    // =========================================================================

    fun startFriendsPresence(profiles: ProfileManager) {
        if (presenceRunning) return
        presenceRunning = true
        thread(isDaemon = true) {
            while (presenceRunning) {
                var pSock: Socket? = null
                try {
                    val (host, port) = ONLINE_BROKERS[selectedBrokerIdx.coerceIn(0, ONLINE_BROKERS.lastIndex)]
                    val s = Socket()
                    s.connect(InetSocketAddress(host, port), 5000)
                    s.soTimeout = 0
                    pSock = s
                    val out = DataOutputStream(s.getOutputStream())
                    val inp = DataInputStream(s.getInputStream())
                    sendMqttConnect(out, "vh62_p_${localPeerId}_${Random.nextInt(100, 999)}")
                    val ackType = inp.readUnsignedByte()
                    val ackLen = readMqttVarInt(inp)
                    val ackB = ByteArray(ackLen); inp.readFully(ackB)
                    if ((ackType and 0xF0) != 0x20) throw RuntimeException("Presence CONNACK fail")
                    var subId = 10
                    for (fr in profiles.active.friends) {
                        sendMqttSubscribe(out, subId++, "vh62/presence/${fr.friendId}")
                    }

                    // Publish our presence every 15s
                    thread(isDaemon = true) {
                        while (presenceRunning && !s.isClosed) {
                            try {
                                val pr = profiles.active
                                val topic = "vh62/presence/${pr.friendId}"
                                val payloadStr = "${pr.friendId}|${pr.nick}|${if (isConnected) roomCode else ""}|$selectedBrokerIdx"
                                val bodyBos = ByteArrayOutputStream()
                                val bp = DataOutputStream(bodyBos)
                                bp.writeUTF(topic)
                                bp.write(payloadStr.toByteArray(Charsets.UTF_8))
                                val body = bodyBos.toByteArray()
                                val pkt = ByteArrayOutputStream()
                                pkt.write(0x30)
                                writeMqttVarInt(pkt, body.size)
                                pkt.write(body)
                                synchronized(out) { out.write(pkt.toByteArray()); out.flush() }

                                // Mark stale friends offline (>40s)
                                val now = System.currentTimeMillis()
                                for (fr in pr.friends) if (fr.online && now - fr.lastSeenMs > 40000L) fr.online = false
                                Thread.sleep(15000)
                            } catch (_: Exception) { break }
                        }
                    }

                    while (presenceRunning && !s.isClosed) {
                        val hdr = inp.readUnsignedByte()
                        val rLen = readMqttVarInt(inp)
                        val body = ByteArray(rLen)
                        if (rLen > 0) inp.readFully(body)
                        if ((hdr and 0xF0) == 0x30) {
                            val bis = DataInputStream(ByteArrayInputStream(body))
                            val tLen = bis.readUnsignedShort(); bis.skipBytes(tLen)
                            val qos = (hdr ushr 1) and 0x03; if (qos > 0) bis.skipBytes(2)
                            val rem = bis.available()
                            if (rem > 0) {
                                val raw = ByteArray(rem); bis.readFully(raw)
                                val parts = String(raw, Charsets.UTF_8).split("|")
                                if (parts.size >= 4) {
                                    val fid = parts[0]; val fnick = parts[1]; val froom = parts[2]; val fbrk = parts[3].toIntOrNull() ?: 0
                                    for (fr in profiles.active.friends) {
                                        if (fr.friendId.equals(fid, ignoreCase = true)) {
                                            fr.nick = fnick.ifBlank { fr.nick }
                                            fr.roomCode = froom
                                            fr.brokerIdx = fbrk
                                            fr.online = true
                                            fr.lastSeenMs = System.currentTimeMillis()
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    try { pSock?.close() } catch (_: Exception) {}
                    try { Thread.sleep(10000) } catch (_: Exception) {}
                }
            }
        }
    }

    fun startLanHost() {
        stop()
        isHost = true; isConnected = true; isOnlineRelay = false; roomCode = "LAN"
        statusText = "LAN Хост (Порт $TCP_PORT)"
        thread(isDaemon = true) {
            try {
                val ss = ServerSocket(TCP_PORT)
                serverSocket = ss
                startLanBeacon()
                while (isHost && !isOnlineRelay) { val c = ss.accept(); try { c.tcpNoDelay = true } catch (_: Exception) {}; handleTcpPeer(c, asHost = true) }
            } catch (_: Exception) { statusText = "LAN Сервер остановлен" }
        }
    }

    fun joinDirectIp(ip: String) {
        stop()
        isHost = false; isOnlineRelay = false; statusText = "Подключение к $ip..."
        thread(isDaemon = true) {
            try {
                val sock = Socket()
                sock.connect(InetSocketAddress(ip.trim(), TCP_PORT), 4500)
                sock.tcpNoDelay = true
                clientSocket = sock; isConnected = true
                statusText = "Подключено к $ip!"
                handleTcpPeer(sock, asHost = false)
            } catch (e: Exception) { statusText = "Ошибка сети: ${e.message}"; isConnected = false }
        }
    }

    fun discoverAndJoinLan() {
        statusText = "Поиск комнат в Wi-Fi (LAN)..."
        thread(isDaemon = true) {
            try {
                DatagramSocket(UDP_DISCOVERY_PORT).use { ds ->
                    ds.soTimeout = 3500
                    val buf = ByteArray(128)
                    val pkt = DatagramPacket(buf, buf.size)
                    ds.receive(pkt)
                    val msg = String(pkt.data, 0, pkt.length)
                    if (msg.startsWith("VHISO_HOST")) pkt.address.hostAddress?.let { joinDirectIp(it) }
                }
            } catch (_: Exception) { statusText = "LAN не найден — используйте Онлайн Код!" }
        }
    }

    private fun startLanBeacon() {
        thread(isDaemon = true) {
            try {
                DatagramSocket().use { ds ->
                    ds.broadcast = true
                    val data = "VHISO_HOST:$localNick".toByteArray()
                    val pkt = DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), UDP_DISCOVERY_PORT)
                    while (isHost && !isOnlineRelay) { try { ds.send(pkt) } catch (_: Exception) {}; Thread.sleep(1500) }
                }
            } catch (_: Exception) {}
        }
    }

    private fun handleTcpPeer(sock: Socket, asHost: Boolean) {
        val peerKey = sock.remoteSocketAddress.toString()
        val out = DataOutputStream(sock.getOutputStream())
        val inp = DataInputStream(sock.getInputStream())
        if (asHost) clientStreams.add(out) else clientOut = out

        if (asHost) {
            sendModManifestToHost(targetTcp = out)
        } else {
            modSyncState = ModSyncState.CHECKING
            modSyncStatusText = "Проверка модов хоста (LAN)..."
            try { synchronized(out) { out.writeByte(18); out.writeUTF(PlatformBridge.CURRENT_VERSION); out.flush() } } catch (_: Exception) {}
        }

        thread(isDaemon = true) {
            try {
                while (isConnected && !isOnlineRelay && !sock.isClosed) {
                    handleIncomingPayload(peerKey, inp, asHost, skipTcp = out)
                }
            } catch (_: Exception) {
            } finally {
                remotePlayers.remove(peerKey); clientStreams.remove(out)
                try { sock.close() } catch (_: Exception) {}
            }
        }
    }

    // =========================================================================
    // 3. ЕДИНЫЙ ПРОТОКОЛ ПАКЕТОВ (ДЛЯ ОНЛАЙН-ОБЛАКА И LAN!)
    //    1=POS+ANIM, 2=BLOCK, 4=WORLD_INIT(GZIP), 5=JOIN_REQ, 6=TIME_SYNC
    // =========================================================================
    private fun buildCompressedWorldInit(): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(GZIPOutputStream(bos)).use { out ->
            out.writeInt(game.world.seed)
            out.writeUTF(game.world.dimension)
            out.writeInt(game.entities.dayCount)
            out.writeInt(game.entities.seasonLengthDays)
            out.writeFloat(game.entities.worldTime)
            out.writeFloat(game.player.x); out.writeFloat(game.player.y); out.writeFloat(game.player.z)
            val mods = game.world.modifications.entries.take(3500)
            out.writeInt(mods.size)
            for ((k, v) in mods) { out.writeUTF(k); out.writeInt(v) }
            val signs = game.world.signTexts.entries.take(200)
            out.writeInt(signs.size)
            for ((k, txt) in signs) { out.writeUTF(k); out.writeUTF(txt) }
        }
        return bos.toByteArray()
    }

    private fun handleIncomingPayload(peerKey: String, inp: DataInputStream, asHost: Boolean, skipTcp: DataOutputStream? = null) {
        when (inp.readUnsignedByte()) {
            1 -> { // POS + ANIM + SLEEP
                val nick = inp.readUTF(); val skin = inp.readUTF()
                val x = inp.readFloat(); val y = inp.readFloat(); val z = inp.readFloat()
                val fr = inp.readBoolean(); val atk = inp.readFloat(); val slp = inp.readBoolean(); val hId = inp.readInt(); val rHp = inp.readFloat(); val rMaxHp = inp.readFloat()
                val rp = remotePlayers.computeIfAbsent(peerKey) { RemotePlayer(peerKey, nick, skin, x, y, z, x, y, z, fr) }
                val jump = hypot((x - rp.x).toDouble(), (y - rp.y).toDouble()).toFloat()
                if (jump > 6.5f) { rp.x = x; rp.y = y; rp.z = z }
                rp.nick = nick; rp.skinId = skin
                rp.targetX = x; rp.targetY = y; rp.targetZ = z
                rp.facingRight = fr
                if (atk > rp.attackAnim) rp.attackAnim = atk
                rp.isSleeping = slp; rp.heldId = hId; rp.hp = rHp; rp.maxHp = rMaxHp
                rp.lastSeenMs = System.currentTimeMillis()
            }
            2 -> { // BLOCK
                val x = inp.readInt(); val y = inp.readInt(); val z = inp.readInt(); val id = inp.readInt()
                game.applyRemoteBlock(x, y, z, id)
                if (asHost && !isOnlineRelay) broadcastBlock(x, y, z, id, skip = skipTcp)
            }
            4 -> { // WORLD_INIT (GZIP)
                val len = inp.readInt()
                if (len !in 1..(4 * 1024 * 1024)) throw RuntimeException("Повреждённый пакет мира ($len)")
                val gz = ByteArray(len); inp.readFully(gz)
                if (asHost || worldInitReceived) return
                worldInitReceived = true
                modSyncState = ModSyncState.DONE
                DataInputStream(GZIPInputStream(ByteArrayInputStream(gz))).use { zIn ->
                    val seed = zIn.readInt(); val dim = zIn.readUTF()
                    val dayCnt = zIn.readInt(); val seasLen = zIn.readInt(); val wTime = zIn.readFloat()
                    val hx = zIn.readFloat(); val hy = zIn.readFloat(); val hz = zIn.readFloat()
                    val cnt = zIn.readInt().coerceIn(0, 10000); val mods = HashMap<String, Int>(cnt)
                    for (i in 0 until cnt) mods[zIn.readUTF()] = zIn.readInt()
                    val sCnt = zIn.readInt().coerceIn(0, 1000); val signs = HashMap<String, String>(sCnt)
                    for (i in 0 until sCnt) signs[zIn.readUTF()] = zIn.readUTF()
                    game.applyHostWorldInit(seed, dim, dayCnt, seasLen, wTime, hx, hy, hz, mods, signs)
                }
            }
            5 -> { // JOIN_REQ (Гость готов и просит мир у Хоста — работает и Онлайн, и LAN!)
                if (asHost) {
                    val initBytes = buildCompressedWorldInit()
                    if (isOnlineRelay) {
                        publishOnlinePacket { p -> p.writeByte(4); p.writeInt(initBytes.size); p.write(initBytes) }
                    } else if (skipTcp != null) {
                        try { synchronized(skipTcp) { skipTcp.writeByte(4); skipTcp.writeInt(initBytes.size); skipTcp.write(initBytes); skipTcp.flush() } } catch (_: Exception) {}
                    }
                }
            }
            6 -> { // TIME_SYNC
                val dayCnt = inp.readInt(); val wTime = inp.readFloat()
                if (!asHost) { game.entities.dayCount = dayCnt; game.entities.worldTime = wTime }
            }
            7 -> { // MOB_SYNC (Хост -> Гости: все мобы и боссы в реальном времени!)
                val cnt = inp.readInt()
                val list = ArrayList<MobSyncSnap>(cnt)
                for (i in 0 until cnt) {
                    list.add(MobSyncSnap(
                        inp.readInt(), inp.readUTF(), inp.readUTF(), inp.readBoolean(), inp.readBoolean(),
                        inp.readFloat(), inp.readFloat(), inp.readFloat(), inp.readFloat(), inp.readFloat(),
                        inp.readBoolean(), inp.readFloat(), inp.readFloat(),
                        inp.readFloat(), inp.readFloat(), inp.readFloat(), inp.readBoolean()
                    ))
                }
                if (!asHost) game.applyRemoteMobs(list)
            }
            8 -> { // MOB_HIT (Гость -> Хост: удар гостя по мобу/боссу!)
                val mobId = inp.readInt(); val dmg = inp.readFloat()
                val kbX = inp.readFloat(); val kbY = inp.readFloat(); val frost = inp.readBoolean()
                if (asHost) game.applyGuestMobHit(mobId, dmg, kbX, kbY, frost)
            }
            9 -> { // CHEST_SYNC (Общие сундуки и бочки!)
                val x = inp.readInt(); val y = inp.readInt(); val z = inp.readInt()
                val arr = arrayOfNulls<SlotItem>(27)
                for (i in 0 until 27) {
                    val id = inp.readInt(); val cnt = inp.readInt()
                    if (id > 0 && cnt > 0) arr[i] = SlotItem(id, cnt)
                }
                game.applyRemoteChest(x, y, z, arr)
                if (asHost && !isOnlineRelay) broadcastChest(x, y, z, arr, skip = skipTcp)
            }
            10 -> { // DROP_SYNC (Хост -> Гости: общий лут на земле!)
                val cnt = inp.readInt()
                val list = ArrayList<DropSyncSnap>(cnt)
                for (i in 0 until cnt) {
                    list.add(DropSyncSnap(inp.readInt(), inp.readInt(), inp.readInt(), inp.readFloat(), inp.readFloat(), inp.readFloat()))
                }
                if (!asHost) game.applyRemoteDrops(list)
            }
            11 -> { // DROP_PICKUP (Гость подобрал предмет -> Хост удаляет с земли)
                val dropId = inp.readInt()
                if (asHost) game.applyGuestDropPickup(dropId)
            }
            12 -> { // CHAT_MSG (Общий чат комнаты!)
                val nick = inp.readUTF(); val msg = inp.readUTF()
                game.addChatMessage("$nick: $msg")
                if (asHost && !isOnlineRelay) broadcastChat(nick, msg, skip = skipTcp)
            }
            13 -> { // PVP_HIT (PvP между игроками, если включено!)
                val targetId = inp.readUTF(); val dmg = inp.readFloat(); val fromNick = inp.readUTF()
                if (pvpEnabled && (targetId == localPeerId.toString() || targetId == "ALL")) {
                    game.applyRemotePvpHit(dmg, fromNick)
                }
            }
            14 -> { // SIGN_SYNC (Синхронизация надписей на табличках!)
                val x = inp.readInt(); val y = inp.readInt(); val z = inp.readInt(); val txt = inp.readUTF()
                game.applyRemoteSign(x, y, z, txt)
                if (asHost && !isOnlineRelay) broadcastSign(x, y, z, txt, skip = skipTcp)
            }
            16 -> { // EMOTE_SYNC (Синхронизация эмоций персонажей!)
                val emIdx = inp.readInt()
                remotePlayers[peerKey]?.let { rp ->
                    rp.emoteIdx = emIdx
                    rp.emoteTimer = if (emIdx == 4) 999f else if (emIdx >= 0) 4.2f else 0f
                }
                if (asHost && !isOnlineRelay) broadcastEmote(emIdx, skip = skipTcp)
            }
            17 -> { // VOICE_FRAME (4-bit IMA-ADPCM голосовой пакет для модов!)
                val vLen = inp.readUnsignedShort()
                if (vLen in 1..2048) {
                    val vData = ByteArray(vLen)
                    inp.readFully(vData)
                    VoiceChatEngine.onIncomingVoicePacket(peerKey, vData)
                    if (asHost && !isOnlineRelay) broadcastVoice(vData, skip = skipTcp)
                }
            }
            18 -> { // MOD_HELLO_REQ (Гость запрашивает список активных модов хоста!)
                inp.readUTF() // guestVersion
                if (asHost) sendModManifestToHost(targetTcp = if (isOnlineRelay) null else skipTcp)
            }
            19 -> { // MOD_MANIFEST (Хост прислал список своих модов -> сверяем у Гостя!)
                val hVer = inp.readUTF()
                val cnt = inp.readInt().coerceIn(0, 64)
                val list = ArrayList<HostModManifestItem>(cnt)
                for (i in 0 until cnt) {
                    list.add(
                        HostModManifestItem(
                            id = inp.readUTF(),
                            name = inp.readUTF(),
                            version = inp.readUTF(),
                            fileName = inp.readUTF(),
                            sizeBytes = inp.readInt().coerceIn(0, 4 * 1024 * 1024),
                            crcHex = inp.readUTF()
                        )
                    )
                }
                if (!asHost && !worldInitReceived && (modSyncState == ModSyncState.IDLE || modSyncState == ModSyncState.CHECKING)) {
                    handleHostModManifest(hVer, list)
                }
            }
            20 -> { // MOD_FILE_REQ (Гость просит скачать конкретный мод или пропавший чанк!)
                val reqModId = inp.readUTF()
                val reqChunk = inp.readInt()
                if (asHost) {
                    sendModFileChunksToGuest(reqModId, reqChunk, targetTcp = if (isOnlineRelay) null else skipTcp)
                }
            }
            21 -> { // MOD_FILE_CHUNK (Чанк .vhpack / .lua от Хоста к Гостю!)
                val mId = inp.readUTF()
                inp.readUTF() // fileName
                val cIdx = inp.readInt()
                val totalC = inp.readInt()
                val dLen = inp.readInt()
                if (dLen !in 0..32768) throw RuntimeException("Неверный чанк мода ($dLen)")
                val chunkBytes = ByteArray(dLen)
                if (dLen > 0) inp.readFully(chunkBytes)
                if (!asHost && modSyncState == ModSyncState.DOWNLOADING && mId == activeDlModId) {
                    val arr = activeDlChunks
                    if (arr != null && totalC == arr.size && cIdx in arr.indices) {
                        if (arr[cIdx] == null) {
                            arr[cIdx] = chunkBytes
                            activeDlReceivedCount++
                            modSyncDoneBytes += dLen
                        }
                        activeDlLastChunkMs = System.currentTimeMillis()
                    }
                }
            }
            else -> {
                if (!isOnlineRelay) throw RuntimeException("Несовместимая версия игры у хоста!")
            }
        }
    }

    // === ЛОГИКА АВТОСИНХРОНИЗАЦИИ МОДОВ (BEDROCK-STYLE) ===
    private fun sendModManifestToHost(targetTcp: DataOutputStream?) {
        val list = game.luaMods.getHostShareableMods()
        val writer: (DataOutputStream) -> Unit = { p ->
            p.writeByte(19)
            p.writeUTF(PlatformBridge.CURRENT_VERSION)
            p.writeInt(list.size)
            for (m in list) {
                p.writeUTF(m.id); p.writeUTF(m.name); p.writeUTF(m.version)
                p.writeUTF(m.fileName); p.writeInt(m.sizeBytes); p.writeUTF(m.crcHex)
            }
        }
        modTransferExec.execute {
            if (isOnlineRelay) publishOnlinePacket(writer)
            else if (targetTcp != null) {
                try { synchronized(targetTcp) { writer(targetTcp); targetTcp.flush() } } catch (_: Exception) {}
            }
        }
    }

    private fun sendModFileChunksToGuest(modId: String, onlyChunk: Int, targetTcp: DataOutputStream?) {
        val bytes = game.luaMods.getLocalModFileBytes(modId) ?: return
        val entry = game.luaMods.mods.firstOrNull { it.id == modId }
        val fileName = entry?.packFile?.name ?: "$modId.vhpack"
        val chunkSize = 8192
        val totalChunks = kotlin.math.max(1, (bytes.size + chunkSize - 1) / chunkSize)

        modTransferExec.execute {
            val range = if (onlyChunk in 0 until totalChunks) onlyChunk..onlyChunk else 0 until totalChunks
            for (ci in range) {
                if (!isConnected) break
                val start = ci * chunkSize
                val end = kotlin.math.min(bytes.size, start + chunkSize)
                val len = kotlin.math.max(0, end - start)
                val slice = bytes.copyOfRange(start, start + len)
                val writer: (DataOutputStream) -> Unit = { p ->
                    p.writeByte(21)
                    p.writeUTF(modId); p.writeUTF(fileName)
                    p.writeInt(ci); p.writeInt(totalChunks)
                    p.writeInt(len); if (len > 0) p.write(slice)
                }
                if (isOnlineRelay) publishOnlinePacket(writer)
                else if (targetTcp != null) {
                    try { synchronized(targetTcp) { writer(targetTcp); targetTcp.flush() } } catch (_: Exception) { break }
                }
                try { Thread.sleep(if (isOnlineRelay) 35L else 4L) } catch (_: Exception) {}
            }
        }
    }

    private fun handleHostModManifest(hVer: String, hostMods: List<HostModManifestItem>) {
        hostGameVersion = hVer
        hostManifestMods = hostMods
        if (hostMods.isEmpty()) {
            modSyncState = ModSyncState.DONE
            sendJoinWorldRequest()
            return
        }
        val missing = game.luaMods.findMissingHostMods(hostMods)
        if (missing.isEmpty()) {
            // Все моды хоста уже есть у гостя — включаем их если выключены и сразу входим в мир!
            modSyncState = ModSyncState.DONE
            com.badlogic.gdx.Gdx.app.postRunnable {
                game.luaMods.activateHostModSet(hostMods.map { it.id }.toSet())
                sendJoinWorldRequest()
            }
            return
        }
        // Есть недостающие или более новые моды -> показываем окно подтверждения загрузки!
        missingHostMods = missing
        modSyncTotalFiles = missing.size
        modSyncDoneFiles = 0
        modSyncTotalBytes = missing.sumOf { it.sizeBytes }
        modSyncDoneBytes = 0
        modSyncCurrentName = ""
        modSyncStatusText = "Нужно загрузить модов: ${missing.size}"
        modSyncState = ModSyncState.PROMPT
        com.badlogic.gdx.Gdx.app.postRunnable {
            game.screen = UiScreen.MOD_SYNC_PROMPT
        }
    }

    fun sendJoinWorldRequest() {
        if (!isConnected || isHost) return
        netSendExec.execute {
            if (!isConnected || isHost) return@execute
            if (isOnlineRelay) {
                publishOnlinePacket { p -> p.writeByte(5) }
            } else {
                clientOut?.let { o -> try { synchronized(o) { o.writeByte(5); o.flush() } } catch (_: Exception) {} }
            }
        }
    }

    fun skipModDownloadAndJoin() {
        modSyncState = ModSyncState.DONE
        modSyncStatusText = "Вход без загрузки модов..."
        sendJoinWorldRequest()
    }

    fun acceptModDownload() {
        if (modSyncState != ModSyncState.PROMPT) return
        val toDownload = missingHostMods.toList()
        if (toDownload.isEmpty()) { skipModDownloadAndJoin(); return }
        modSyncState = ModSyncState.DOWNLOADING
        modSyncDoneFiles = 0
        modSyncDoneBytes = 0
        modSyncTotalFiles = toDownload.size
        modSyncTotalBytes = toDownload.sumOf { it.sizeBytes }.coerceAtLeast(1)

        thread(isDaemon = true, name = "vh-mod-downloader") {
            try {
                for ((idx, hm) in toDownload.withIndex()) {
                    if (!isConnected || modSyncState != ModSyncState.DOWNLOADING) return@thread
                    modSyncCurrentName = "${hm.name} v${hm.version}"
                    modSyncStatusText = "Загрузка (${idx + 1}/${toDownload.size}): ${hm.name}..."
                    val chunkSize = 8192
                    val totalC = kotlin.math.max(1, (hm.sizeBytes + chunkSize - 1) / chunkSize)
                    val chunks = arrayOfNulls<ByteArray>(totalC)
                    activeDlModId = hm.id
                    activeDlChunks = chunks
                    activeDlReceivedCount = 0
                    activeDlLastChunkMs = System.currentTimeMillis()

                    requestModChunkFromHost(hm.id, -1)

                    var retries = 0
                    while (isConnected && modSyncState == ModSyncState.DOWNLOADING && activeDlReceivedCount < totalC) {
                        try { Thread.sleep(75) } catch (_: Exception) {}
                        val idleMs = System.currentTimeMillis() - activeDlLastChunkMs
                        if (idleMs > 1400L) {
                            retries++
                            if (retries > 6) {
                                modSyncStatusText = "Таймаут загрузки: ${hm.name}"
                                modSyncState = ModSyncState.PROMPT
                                game.showToast("Не удалось скачать «${hm.name}» — нажмите Повторить!")
                                return@thread
                            }
                            activeDlLastChunkMs = System.currentTimeMillis()
                            var sentReqs = 0
                            for (ci in 0 until totalC) {
                                if (chunks[ci] == null) {
                                    requestModChunkFromHost(hm.id, ci)
                                    sentReqs++
                                    if (sentReqs >= 4) break
                                }
                            }
                        }
                    }

                    if (!isConnected || modSyncState != ModSyncState.DOWNLOADING) return@thread
                    val bos = ByteArrayOutputStream(hm.sizeBytes)
                    for (ci in 0 until totalC) chunks[ci]?.let { bos.write(it) }
                    game.luaMods.installDownloadedMod(hm.id, hm.fileName, bos.toByteArray())
                    modSyncDoneFiles = idx + 1
                }

                // Все моды скачаны! Применяем на GL-потоке и входим в мир хоста!
                val allIds = hostManifestMods.map { it.id }.toSet()
                modSyncStatusText = "Применение модов и вход в мир..."
                com.badlogic.gdx.Gdx.app.postRunnable {
                    game.luaMods.activateHostModSet(allIds)
                    modSyncState = ModSyncState.DONE
                    game.showToast("Моды хоста загружены (${toDownload.size})! Входим в мир...")
                    sendJoinWorldRequest()
                }
            } catch (e: Exception) {
                modSyncStatusText = "Ошибка загрузки: ${e.message ?: "сеть"}"
                modSyncState = ModSyncState.PROMPT
            } finally {
                activeDlModId = ""
                activeDlChunks = null
            }
        }
    }

    private fun requestModChunkFromHost(modId: String, chunkIdx: Int) {
        val writer: (DataOutputStream) -> Unit = { p -> p.writeByte(20); p.writeUTF(modId); p.writeInt(chunkIdx) }
        if (isOnlineRelay) publishOnlinePacket(writer)
        else clientOut?.let { o -> try { synchronized(o) { writer(o); o.flush() } } catch (_: Exception) {} }
    }

    fun interpolateRemotePlayers(dt: Float) {
        val k = kotlin.math.min(1f, dt * 14f)
        for (rp in remotePlayers.values) {
            val dx = rp.targetX - rp.x
            val dy = rp.targetY - rp.y
            val dz = rp.targetZ - rp.z
            val d = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            rp.x += dx * k
            rp.y += dy * k
            rp.z += dz * k
            rp.isMoving = d > 0.035f
            if (rp.isMoving) rp.walkAnim += kotlin.math.min(0.6f, d) * 8.5f
            if (rp.attackAnim > 0f) rp.attackAnim = kotlin.math.max(0f, rp.attackAnim - dt * 5f)
            if (rp.isMoving && rp.emoteIdx == 4) { rp.emoteIdx = -1; rp.emoteTimer = 0f }
            if (rp.emoteTimer > 0f && rp.emoteIdx != 4) { rp.emoteTimer = kotlin.math.max(0f, rp.emoteTimer - dt); if (rp.emoteTimer <= 0f) rp.emoteIdx = -1 }
        }
    }

    fun sendPos(x: Float, y: Float, z: Float, facingRight: Boolean, skinId: String, attackAnim: Float, isSleeping: Boolean, heldId: Int, hp: Float, maxHp: Float) {
        if (!isConnected) return
        val now = System.currentTimeMillis()
        remotePlayers.entries.removeIf { now - it.value.lastSeenMs > 25000L }
        if (!posInFlight.compareAndSet(false, true)) return
        netSendExec.execute {
            try {
            if (!isConnected) return@execute
            if (isOnlineRelay) {
                publishOnlinePacket { p ->
                    p.writeByte(1); p.writeUTF(localNick); p.writeUTF(skinId)
                    p.writeFloat(x); p.writeFloat(y); p.writeFloat(z)
                    p.writeBoolean(facingRight); p.writeFloat(attackAnim); p.writeBoolean(isSleeping); p.writeInt(heldId); p.writeFloat(hp); p.writeFloat(maxHp)
                }
            } else {
                val targets = if (isHost) clientStreams else listOfNotNull(clientOut)
                for (o in targets) {
                    try {
                        synchronized(o) {
                            o.writeByte(1); o.writeUTF(localNick); o.writeUTF(skinId)
                            o.writeFloat(x); o.writeFloat(y); o.writeFloat(z)
                            o.writeBoolean(facingRight); o.writeFloat(attackAnim); o.writeBoolean(isSleeping); o.writeInt(heldId); o.writeFloat(hp); o.writeFloat(maxHp)
                            o.flush()
                        }
                    } catch (_: Exception) {}
                }
            }
            } finally { posInFlight.set(false) }
        }
    }

    fun broadcastBlock(x: Int, y: Int, z: Int, id: Int, skip: DataOutputStream? = null) {
        if (!isConnected) return
        netSendExec.execute {
            if (!isConnected) return@execute
            if (isOnlineRelay) {
                publishOnlinePacket { p -> p.writeByte(2); p.writeInt(x); p.writeInt(y); p.writeInt(z); p.writeInt(id) }
            } else {
                val targets = if (isHost) clientStreams else listOfNotNull(clientOut)
                for (o in targets) {
                    if (o === skip) continue
                    try { synchronized(o) { o.writeByte(2); o.writeInt(x); o.writeInt(y); o.writeInt(z); o.writeInt(id); o.flush() } } catch (_: Exception) {}
                }
            }
        }
    }

    fun broadcastTime(dayCount: Int, worldTime: Float) {
        if (!isConnected || !isHost) return
        netSendExec.execute {
            if (!isConnected || !isHost) return@execute
            if (isOnlineRelay) {
                publishOnlinePacket { p -> p.writeByte(6); p.writeInt(dayCount); p.writeFloat(worldTime) }
            } else {
                for (o in clientStreams) {
                    try { synchronized(o) { o.writeByte(6); o.writeInt(dayCount); o.writeFloat(worldTime); o.flush() } } catch (_: Exception) {}
                }
            }
        }
    }

    fun broadcastMobs(mobs: Collection<MobEntity>) {
        if (!isConnected || !isHost || remotePlayers.isEmpty()) return
        if (!mobsInFlight.compareAndSet(false, true)) return
        val snap = mobs.take(24).map {
            MobSyncSnap(it.id, it.type, it.name, it.hostile, it.isBoss, it.x, it.y, it.z, it.hp, it.maxHp,
                it.facingRight, it.walkAnim, it.attackAnim, it.hurtFlash, it.freezeTimer, it.stunTimer, it.tamed)
        }
        netSendExec.execute {
            if (!isConnected || !isHost) return@execute
            val writer: (DataOutputStream) -> Unit = { p ->
                p.writeByte(7); p.writeInt(snap.size)
                for (s in snap) {
                    p.writeInt(s.id); p.writeUTF(s.type); p.writeUTF(s.name); p.writeBoolean(s.hostile); p.writeBoolean(s.isBoss)
                    p.writeFloat(s.x); p.writeFloat(s.y); p.writeFloat(s.z); p.writeFloat(s.hp); p.writeFloat(s.maxHp)
                    p.writeBoolean(s.facingRight); p.writeFloat(s.walkAnim); p.writeFloat(s.attackAnim)
                    p.writeFloat(s.hurtFlash); p.writeFloat(s.freezeTimer); p.writeFloat(s.stunTimer); p.writeBoolean(s.tamed)
                }
            }
            try {
            if (isOnlineRelay) publishOnlinePacket(writer)
            else for (o in clientStreams) { try { synchronized(o) { writer(o); o.flush() } } catch (_: Exception) {} }
            } finally { mobsInFlight.set(false) }
        }
    }

    fun sendMobHit(mobId: Int, dmg: Float, kbX: Float, kbY: Float, frost: Boolean) {
        if (!isConnected || isHost) return
        netSendExec.execute {
            val writer: (DataOutputStream) -> Unit = { p ->
                p.writeByte(8); p.writeInt(mobId); p.writeFloat(dmg); p.writeFloat(kbX); p.writeFloat(kbY); p.writeBoolean(frost)
            }
            if (isOnlineRelay) publishOnlinePacket(writer)
            else clientOut?.let { o -> try { synchronized(o) { writer(o); o.flush() } } catch (_: Exception) {} }
        }
    }

    fun broadcastChest(x: Int, y: Int, z: Int, slots: Array<SlotItem?>, skip: DataOutputStream? = null) {
        if (!isConnected) return
        val copy = Array(27) { i -> slots[i]?.let { SlotItem(it.id, it.count) } }
        netSendExec.execute {
            val writer: (DataOutputStream) -> Unit = { p ->
                p.writeByte(9); p.writeInt(x); p.writeInt(y); p.writeInt(z)
                for (i in 0 until 27) { p.writeInt(copy[i]?.id ?: 0); p.writeInt(copy[i]?.count ?: 0) }
            }
            if (isOnlineRelay) publishOnlinePacket(writer)
            else {
                val targets = if (isHost) clientStreams else listOfNotNull(clientOut)
                for (o in targets) if (o !== skip) try { synchronized(o) { writer(o); o.flush() } } catch (_: Exception) {}
            }
        }
    }

    fun broadcastDrops(drops: Collection<DroppedItem>) {
        if (!isConnected || !isHost || remotePlayers.isEmpty()) return
        if (!dropsInFlight.compareAndSet(false, true)) return
        val snap = drops.take(28).map { DropSyncSnap(it.id, it.itemId, it.count, it.x, it.y, it.z) }
        netSendExec.execute {
            if (!isConnected || !isHost) return@execute
            val writer: (DataOutputStream) -> Unit = { p ->
                p.writeByte(10); p.writeInt(snap.size)
                for (s in snap) { p.writeInt(s.id); p.writeInt(s.itemId); p.writeInt(s.count); p.writeFloat(s.x); p.writeFloat(s.y); p.writeFloat(s.z) }
            }
            try {
            if (isOnlineRelay) publishOnlinePacket(writer)
            else for (o in clientStreams) { try { synchronized(o) { writer(o); o.flush() } } catch (_: Exception) {} }
            } finally { dropsInFlight.set(false) }
        }
    }

    fun sendDropPickup(dropId: Int) {
        if (!isConnected || isHost) return
        netSendExec.execute {
            val writer: (DataOutputStream) -> Unit = { p -> p.writeByte(11); p.writeInt(dropId) }
            if (isOnlineRelay) publishOnlinePacket(writer)
            else clientOut?.let { o -> try { synchronized(o) { writer(o); o.flush() } } catch (_: Exception) {} }
        }
    }

    fun broadcastChat(nick: String, msg: String, skip: DataOutputStream? = null) {
        if (!isConnected) return
        netSendExec.execute {
            val writer: (DataOutputStream) -> Unit = { p -> p.writeByte(12); p.writeUTF(nick); p.writeUTF(msg) }
            if (isOnlineRelay) publishOnlinePacket(writer)
            else {
                val targets = if (isHost) clientStreams else listOfNotNull(clientOut)
                for (o in targets) if (o !== skip) try { synchronized(o) { writer(o); o.flush() } } catch (_: Exception) {}
            }
        }
    }

    fun sendPvpHit(targetPeerId: String, dmg: Float) {
        if (!isConnected || !pvpEnabled) return
        netSendExec.execute {
            val writer: (DataOutputStream) -> Unit = { p -> p.writeByte(13); p.writeUTF(targetPeerId); p.writeFloat(dmg); p.writeUTF(localNick) }
            if (isOnlineRelay) publishOnlinePacket(writer)
            else {
                val targets = if (isHost) clientStreams else listOfNotNull(clientOut)
                for (o in targets) try { synchronized(o) { writer(o); o.flush() } } catch (_: Exception) {}
            }
        }
    }

    fun broadcastSign(x: Int, y: Int, z: Int, txt: String, skip: DataOutputStream? = null) {
        if (!isConnected) return
        netSendExec.execute {
            val writer: (DataOutputStream) -> Unit = { p -> p.writeByte(14); p.writeInt(x); p.writeInt(y); p.writeInt(z); p.writeUTF(txt) }
            if (isOnlineRelay) publishOnlinePacket(writer)
            else {
                val targets = if (isHost) clientStreams else listOfNotNull(clientOut)
                for (o in targets) if (o !== skip) try { synchronized(o) { writer(o); o.flush() } } catch (_: Exception) {}
            }
        }
    }

    fun broadcastVoice(frame: ByteArray, skip: DataOutputStream? = null) {
        if (!isConnected || frame.isEmpty() || frame.size > 2048) return
        if (skip == null && !voiceInFlight.compareAndSet(false, true)) return
        voiceSendExec.execute {
            try {
                if (!isConnected) return@execute
                val writer: (DataOutputStream) -> Unit = { p ->
                    p.writeByte(17)
                    p.writeShort(frame.size)
                    p.write(frame)
                }
                if (isOnlineRelay) {
                    publishOnlinePacket(writer)
                } else {
                    val targets = if (isHost) clientStreams else listOfNotNull(clientOut)
                    for (o in targets) if (o !== skip) {
                        try { synchronized(o) { writer(o); o.flush() } } catch (_: Exception) {}
                    }
                }
            } finally {
                if (skip == null) voiceInFlight.set(false)
            }
        }
    }

    fun broadcastEmote(emoteIdx: Int, skip: DataOutputStream? = null) {
        if (!isConnected) return
        netSendExec.execute {
            val writer: (DataOutputStream) -> Unit = { p -> p.writeByte(16); p.writeInt(emoteIdx) }
            if (isOnlineRelay) publishOnlinePacket(writer)
            else {
                val targets = if (isHost) clientStreams else listOfNotNull(clientOut)
                for (o in targets) if (o !== skip) try { synchronized(o) { writer(o); o.flush() } } catch (_: Exception) {}
            }
        }
    }

    fun stop() {
        isHost = false; isConnected = false; isOnlineRelay = false; roomCode = ""
        worldInitReceived = false; modSyncState = ModSyncState.IDLE
        missingHostMods = emptyList(); hostManifestMods = emptyList()
        activeDlModId = ""; activeDlChunks = null
        try { serverSocket?.close() } catch (_: Exception) {}
        try { clientSocket?.close() } catch (_: Exception) {}
        try { mqttSocket?.close() } catch (_: Exception) {}
        serverSocket = null; clientSocket = null; clientOut = null; mqttSocket = null; mqttOut = null
        clientStreams.clear(); remotePlayers.clear()
        statusText = "Одиночная игра"
    }
}
