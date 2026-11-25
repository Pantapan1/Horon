package com.voxelhorizon.nativegame

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

data class RemotePlayer(
    val id: String,
    var nick: String,
    var skinId: String,
    var x: Float, var y: Float, var z: Float,
    var yaw: Float,
    var walkPhase: Float = 0f
)

class NetMultiplayer(private val game: VoxelHorizonGame) {
    companion object {
        const val TCP_PORT = 25566
        const val UDP_DISCOVERY_PORT = 25567
    }

    @Volatile var isHost = false
    @Volatile var isConnected = false
    @Volatile var statusText = "Одиночная игра"
    var localNick = "Игрок"

    val remotePlayers = ConcurrentHashMap<String, RemotePlayer>()
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private val clientStreams = CopyOnWriteArrayList<DataOutputStream>()
    private var clientOut: DataOutputStream? = null

    // Пакеты: 1=POS, 2=BLOCK, 3=CHAT, 4=WORLD_INIT, 5=MOB_SYNC
    fun startHost() {
        stop()
        isHost = true
        isConnected = true
        statusText = "Хост активен (Порт $TCP_PORT)"
        thread(isDaemon = true) {
            try {
                val ss = ServerSocket(TCP_PORT)
                serverSocket = ss
                startLanBeacon()
                while (isHost) {
                    val sock = ss.accept()
                    handlePeerSocket(sock, asHost = true)
                }
            } catch (e: Exception) {
                statusText = "Сервер остановлен"
            }
        }
    }

    fun joinDirect(ip: String) {
        stop()
        isHost = false
        statusText = "Подключение к $ip..."
        thread(isDaemon = true) {
            try {
                val sock = Socket(ip.trim(), TCP_PORT)
                clientSocket = sock
                isConnected = true
                statusText = "Подключено к $ip!"
                handlePeerSocket(sock, asHost = false)
            } catch (e: Exception) {
                statusText = "Ошибка сети: ${e.message}"
                isConnected = false
            }
        }
    }

    fun discoverAndJoinLan() {
        statusText = "Поиск комнат в LAN..."
        thread(isDaemon = true) {
            try {
                DatagramSocket(UDP_DISCOVERY_PORT).use { ds ->
                    ds.soTimeout = 3500
                    val buf = ByteArray(128)
                    val pkt = DatagramPacket(buf, buf.size)
                    ds.receive(pkt)
                    val msg = String(pkt.data, 0, pkt.length)
                    if (msg.startsWith("VH_NATIVE_HOST")) {
                        val hostIp = pkt.address.hostAddress ?: return@use
                        joinDirect(hostIp)
                    }
                }
            } catch (_: Exception) {
                statusText = "LAN-комнаты не найдены (введите IP)"
            }
        }
    }

    private fun startLanBeacon() {
        thread(isDaemon = true) {
            try {
                DatagramSocket().use { ds ->
                    ds.broadcast = true
                    val data = "VH_NATIVE_HOST:$localNick".toByteArray()
                    val pkt = DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), UDP_DISCOVERY_PORT)
                    while (isHost) {
                        try { ds.send(pkt) } catch (_: Exception) {}
                        Thread.sleep(1500)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun handlePeerSocket(sock: Socket, asHost: Boolean) {
        val peerKey = sock.remoteSocketAddress.toString()
        val out = DataOutputStream(sock.getOutputStream())
        val inp = DataInputStream(sock.getInputStream())
        if (asHost) clientStreams.add(out) else clientOut = out

        // Если мы Хост — шлём полную синхронизацию мира гостю!
        if (asHost) {
            synchronized(out) {
                out.writeByte(4) // WORLD_INIT
                out.writeInt(game.world.seed)
                out.writeUTF(game.world.dimension)
                out.writeFloat(game.player.x); out.writeFloat(game.player.y); out.writeFloat(game.player.z)
                val mods = game.world.modifications.entries.toList()
                out.writeInt(mods.size)
                for ((k, v) in mods) { out.writeUTF(k); out.writeInt(v) }
                out.flush()
            }
        }

        thread(isDaemon = true) {
            try {
                while (isConnected && !sock.isClosed) {
                    val type = inp.readUnsignedByte()
                    when (type) {
                        1 -> { // POS + SKIN
                            val nick = inp.readUTF(); val skin = inp.readUTF()
                            val x = inp.readFloat(); val y = inp.readFloat(); val z = inp.readFloat(); val yaw = inp.readFloat()
                            val rp = remotePlayers.computeIfAbsent(peerKey) { RemotePlayer(peerKey, nick, skin, x, y, z, yaw) }
                            val moved = kotlin.math.hypot((x - rp.x).toDouble(), (z - rp.z).toDouble()).toFloat()
                            rp.nick = nick; rp.skinId = skin; rp.x = x; rp.y = y; rp.z = z; rp.yaw = yaw
                            if (moved > 0.01f) rp.walkPhase += moved * 3.5f
                        }
                        2 -> { // BLOCK
                            val x = inp.readInt(); val y = inp.readInt(); val z = inp.readInt(); val id = inp.readInt()
                            game.applyRemoteBlock(x, y, z, id)
                            if (asHost) broadcastBlock(x, y, z, id, skip = out)
                        }
                        3 -> { // CHAT
                            val nick = inp.readUTF(); val msg = inp.readUTF()
                            game.addChat("$nick: $msg")
                            if (asHost) broadcastChat(nick, msg, skip = out)
                        }
                        4 -> { // WORLD_INIT (клиент принимает мир хоста!)
                            val seed = inp.readInt()
                            val dim = inp.readUTF()
                            val hx = inp.readFloat(); val hy = inp.readFloat(); val hz = inp.readFloat()
                            val cnt = inp.readInt()
                            val mods = HashMap<String, Int>(cnt)
                            for (i in 0 until cnt) mods[inp.readUTF()] = inp.readInt()
                            game.applyHostWorldInit(seed, dim, hx, hy, hz, mods)
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                remotePlayers.remove(peerKey)
                clientStreams.remove(out)
                try { sock.close() } catch (_: Exception) {}
            }
        }
    }

    fun sendPos(x: Float, y: Float, z: Float, yaw: Float, skinId: String) {
        if (!isConnected) return
        val targets = if (isHost) clientStreams else listOfNotNull(clientOut)
        for (o in targets) {
            try {
                synchronized(o) {
                    o.writeByte(1); o.writeUTF(localNick); o.writeUTF(skinId)
                    o.writeFloat(x); o.writeFloat(y); o.writeFloat(z); o.writeFloat(yaw)
                    o.flush()
                }
            } catch (_: Exception) {}
        }
    }

    fun broadcastBlock(x: Int, y: Int, z: Int, id: Int, skip: DataOutputStream? = null) {
        if (!isConnected) return
        val targets = if (isHost) clientStreams else listOfNotNull(clientOut)
        for (o in targets) {
            if (o === skip) continue
            try {
                synchronized(o) { o.writeByte(2); o.writeInt(x); o.writeInt(y); o.writeInt(z); o.writeInt(id); o.flush() }
            } catch (_: Exception) {}
        }
    }

    fun broadcastChat(nick: String, msg: String, skip: DataOutputStream? = null) {
        if (!isConnected) return
        val targets = if (isHost) clientStreams else listOfNotNull(clientOut)
        for (o in targets) {
            if (o === skip) continue
            try {
                synchronized(o) { o.writeByte(3); o.writeUTF(nick); o.writeUTF(msg); o.flush() }
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        isHost = false; isConnected = false
        try { serverSocket?.close() } catch (_: Exception) {}
        try { clientSocket?.close() } catch (_: Exception) {}
        serverSocket = null; clientSocket = null; clientOut = null
        clientStreams.clear()
        remotePlayers.clear()
    }
}
