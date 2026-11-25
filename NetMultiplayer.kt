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
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.thread
import kotlin.math.hypot
import kotlin.random.Random

data class RemotePlayer(
    val id: String,
    var nick: String,
    var skinId: String,
    var x: Float, var y: Float, var z: Float,
    var facingRight: Boolean = true,
    var walkAnim: Float = 0f,
    var attackAnim: Float = 0f,
    var isSleeping: Boolean = false,
    var heldId: Int = 0,
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

    // =========================================================================
    // 1. ОНЛАЙН-МУЛЬТИПЛЕЕР ЧЕРЕЗ ИНТЕРНЕТ (КОД КОМНАТЫ — РАБОТАЕТ НА ЛЮБОМ РАССТОЯНИИ!)
    // =========================================================================
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
        mqttTopic = "vh53/room/$code"
        thread(isDaemon = true) {
            var connectedBroker = ""
            var sock: Socket? = null
            for ((host, port) in ONLINE_BROKERS) {
                try {
                    val s = Socket()
                    s.connect(InetSocketAddress(host, port), 4500)
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

                // If guest -> request world sync from Host!
                if (!asHost) {
                    publishOnlinePacket { p -> p.writeByte(5) } // JOIN_REQ
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
    fun startLanHost() {
        stop()
        isHost = true; isConnected = true; isOnlineRelay = false; roomCode = "LAN"
        statusText = "LAN Хост (Порт $TCP_PORT)"
        thread(isDaemon = true) {
            try {
                val ss = ServerSocket(TCP_PORT)
                serverSocket = ss
                startLanBeacon()
                while (isHost && !isOnlineRelay) handleTcpPeer(ss.accept(), asHost = true)
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
            val initBytes = buildCompressedWorldInit()
            synchronized(out) { out.writeByte(4); out.writeInt(initBytes.size); out.write(initBytes); out.flush() }
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
                val fr = inp.readBoolean(); val atk = inp.readFloat(); val slp = inp.readBoolean(); val hId = inp.readInt()
                val rp = remotePlayers.computeIfAbsent(peerKey) { RemotePlayer(peerKey, nick, skin, x, y, z, fr) }
                val d = hypot((x - rp.x).toDouble(), (y - rp.y).toDouble()).toFloat()
                rp.nick = nick; rp.skinId = skin; rp.x = x; rp.y = y; rp.z = z
                rp.facingRight = fr; rp.attackAnim = atk; rp.isSleeping = slp; rp.heldId = hId
                rp.lastSeenMs = System.currentTimeMillis()
                if (d > 0.01f) rp.walkAnim += d * 4.5f
            }
            2 -> { // BLOCK
                val x = inp.readInt(); val y = inp.readInt(); val z = inp.readInt(); val id = inp.readInt()
                game.applyRemoteBlock(x, y, z, id)
                if (asHost && !isOnlineRelay) broadcastBlock(x, y, z, id, skip = skipTcp)
            }
            4 -> { // WORLD_INIT (GZIP)
                val len = inp.readInt()
                val gz = ByteArray(len); inp.readFully(gz)
                if (asHost) return
                DataInputStream(GZIPInputStream(ByteArrayInputStream(gz))).use { zIn ->
                    val seed = zIn.readInt(); val dim = zIn.readUTF()
                    val dayCnt = zIn.readInt(); val seasLen = zIn.readInt(); val wTime = zIn.readFloat()
                    val hx = zIn.readFloat(); val hy = zIn.readFloat(); val hz = zIn.readFloat()
                    val cnt = zIn.readInt(); val mods = HashMap<String, Int>(cnt)
                    for (i in 0 until cnt) mods[zIn.readUTF()] = zIn.readInt()
                    val sCnt = zIn.readInt(); val signs = HashMap<String, String>(sCnt)
                    for (i in 0 until sCnt) signs[zIn.readUTF()] = zIn.readUTF()
                    game.applyHostWorldInit(seed, dim, dayCnt, seasLen, wTime, hx, hy, hz, mods, signs)
                }
            }
            5 -> { // JOIN_REQ (Гость просит мир у Онлайн-Хоста!)
                if (asHost && isOnlineRelay) {
                    val initBytes = buildCompressedWorldInit()
                    publishOnlinePacket { p -> p.writeByte(4); p.writeInt(initBytes.size); p.write(initBytes) }
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
        }
    }

    fun sendPos(x: Float, y: Float, z: Float, facingRight: Boolean, skinId: String, attackAnim: Float, isSleeping: Boolean, heldId: Int) {
        if (!isConnected) return
        val now = System.currentTimeMillis()
        remotePlayers.entries.removeIf { now - it.value.lastSeenMs > 12000L }
        netSendExec.execute {
            if (!isConnected) return@execute
            if (isOnlineRelay) {
                publishOnlinePacket { p ->
                    p.writeByte(1); p.writeUTF(localNick); p.writeUTF(skinId)
                    p.writeFloat(x); p.writeFloat(y); p.writeFloat(z)
                    p.writeBoolean(facingRight); p.writeFloat(attackAnim); p.writeBoolean(isSleeping); p.writeInt(heldId)
                }
            } else {
                val targets = if (isHost) clientStreams else listOfNotNull(clientOut)
                for (o in targets) {
                    try {
                        synchronized(o) {
                            o.writeByte(1); o.writeUTF(localNick); o.writeUTF(skinId)
                            o.writeFloat(x); o.writeFloat(y); o.writeFloat(z)
                            o.writeBoolean(facingRight); o.writeFloat(attackAnim); o.writeBoolean(isSleeping); o.writeInt(heldId)
                            o.flush()
                        }
                    } catch (_: Exception) {}
                }
            }
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
        if (!isConnected || !isHost) return
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
            if (isOnlineRelay) publishOnlinePacket(writer)
            else for (o in clientStreams) { try { synchronized(o) { writer(o); o.flush() } } catch (_: Exception) {} }
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
        if (!isConnected || !isHost) return
        val snap = drops.take(28).map { DropSyncSnap(it.id, it.itemId, it.count, it.x, it.y, it.z) }
        netSendExec.execute {
            if (!isConnected || !isHost) return@execute
            val writer: (DataOutputStream) -> Unit = { p ->
                p.writeByte(10); p.writeInt(snap.size)
                for (s in snap) { p.writeInt(s.id); p.writeInt(s.itemId); p.writeInt(s.count); p.writeFloat(s.x); p.writeFloat(s.y); p.writeFloat(s.z) }
            }
            if (isOnlineRelay) publishOnlinePacket(writer)
            else for (o in clientStreams) { try { synchronized(o) { writer(o); o.flush() } } catch (_: Exception) {} }
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

    fun stop() {
        isHost = false; isConnected = false; isOnlineRelay = false; roomCode = ""
        try { serverSocket?.close() } catch (_: Exception) {}
        try { clientSocket?.close() } catch (_: Exception) {}
        try { mqttSocket?.close() } catch (_: Exception) {}
        serverSocket = null; clientSocket = null; clientOut = null; mqttSocket = null; mqttOut = null
        clientStreams.clear(); remotePlayers.clear()
        statusText = "Одиночная игра"
    }
}
