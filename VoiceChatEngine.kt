package com.voxelhorizon.nativegame

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.AudioDevice
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private class PeerVoiceStream {
    val queue = ConcurrentLinkedQueue<ShortArray>()
    @Volatile var lastTalkMs = 0L
    @Volatile var level = 0f
}

object VoiceChatEngine {
    const val SAMPLE_RATE = 8000
    const val FRAME_SAMPLES = 320 // 40ms @ 8000 Hz -> 163 bytes IMA-ADPCM (~4 KB/s!)

    private val INDEX_TABLE = intArrayOf(-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8)
    private val STEP_TABLE = intArrayOf(
        7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
        50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
        337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
        2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
        15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
    )

    @Volatile private var gameRef: VoxelHorizonGame? = null
    @Volatile private var running = false
    @Volatile private var captureRunning = false

    @Volatile var enabled = false
    @Volatile var talking = false          // Push-to-Talk / Toggle Talk
    @Volatile var openMic = false          // Auto Voice Activity Detection (VAD)
    @Volatile var vadThreshold = 0.06f
    @Volatile var deafened = false
    @Volatile var proximityEnabled = true
    @Volatile var proximityRange = 22f
    @Volatile var masterVolume = 1.0f
    @Volatile var micGain = 1.35f
    @Volatile var testLoopback = false     // Solo mic test!

    @Volatile var localMicLevel = 0f
    @Volatile var localTransmitting = false
    @Volatile var lastError = ""

    private val peers = ConcurrentHashMap<String, PeerVoiceStream>()
    val peerMuted = ConcurrentHashMap<String, Boolean>()
    val peerVolumes = ConcurrentHashMap<String, Float>()

    fun attachGame(game: VoxelHorizonGame) {
        gameRef = game
    }

    fun start(game: VoxelHorizonGame): Boolean {
        gameRef = game
        enabled = true
        lastError = ""
        startPlaybackThread()
        if (PlatformBridge.hasMicPermission()) {
            startCaptureThread()
        }
        return true
    }

    fun ensureCaptureIfPermitted() {
        if (enabled && PlatformBridge.hasMicPermission() && !captureRunning) {
            startCaptureThread()
        }
    }

    fun stopAll() {
        enabled = false
        talking = false
        openMic = false
        testLoopback = false
        localTransmitting = false
        localMicLevel = 0f
        running = false
        captureRunning = false
        peers.clear()
    }

    fun isPeerTalking(peerId: String): Boolean {
        val st = peers[peerId] ?: return false
        return (System.currentTimeMillis() - st.lastTalkMs) < 350L
    }

    fun getPeerLevel(peerId: String): Float {
        val st = peers[peerId] ?: return 0f
        return if ((System.currentTimeMillis() - st.lastTalkMs) < 350L) st.level else 0f
    }

    fun onIncomingVoicePacket(peerId: String, data: ByteArray) {
        if (!enabled || deafened) return
        if (peerMuted[peerId] == true) return
        if (data.size < 4) return
        val pcm = decodeAdpcm(data) ?: return
        var sumSq = 0.0
        for (s in pcm) {
            val f = s / 32768.0
            sumSq += f * f
        }
        val rms = sqrt(sumSq / pcm.size).toFloat().coerceIn(0f, 1f)
        val st = peers.computeIfAbsent(peerId) { PeerVoiceStream() }
        while (st.queue.size >= 8) st.queue.poll()
        var off = 0
        while (off < pcm.size) {
            val end = min(pcm.size, off + FRAME_SAMPLES)
            st.queue.add(if (off == 0 && end == pcm.size) pcm else pcm.copyOfRange(off, end))
            off = end
        }
        st.lastTalkMs = System.currentTimeMillis()
        st.level = min(1f, rms * 3.5f)
    }

    private fun startCaptureThread() {
        if (captureRunning) return
        captureRunning = true
        thread(isDaemon = true, name = "VoxelVoiceCapture") {
            var recorder: AudioRecord? = null
            try {
                val minBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufBytes = max(minBuf * 2, FRAME_SAMPLES * 4)
                val sources = intArrayOf(MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.MIC)
                for (src in sources) {
                    try {
                        @Suppress("MissingPermission")
                        val r = AudioRecord(src, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufBytes)
                        if (r.state == AudioRecord.STATE_INITIALIZED) {
                            recorder = r
                            break
                        } else {
                            r.release()
                        }
                    } catch (_: Exception) {}
                }
                val rec = recorder
                if (rec == null) {
                    lastError = "Микрофон занят или недоступен"
                    captureRunning = false
                    return@thread
                }

                rec.startRecording()
                val rawBuf = ShortArray(FRAME_SAMPLES)
                val mqttBatchBuf = ShortArray(FRAME_SAMPLES * 2)
                var mqttBatchHalf = 0
                var hangoverFrames = 0

                while (captureRunning && enabled) {
                    val read = rec.read(rawBuf, 0, FRAME_SAMPLES)
                    if (read <= 0) {
                        Thread.sleep(10)
                        continue
                    }
                    // Apply mic gain & measure RMS
                    var sumSq = 0.0
                    val gain = micGain.coerceIn(0.3f, 3.0f)
                    for (i in 0 until read) {
                        val boosted = (rawBuf[i] * gain).toInt().coerceIn(-32767, 32767)
                        rawBuf[i] = boosted.toShort()
                        val f = boosted / 32768.0
                        sumSq += f * f
                    }
                    val rms = sqrt(sumSq / read).toFloat()
                    localMicLevel = min(1f, rms * 3.2f)

                    val activeByVad = openMic && rms >= vadThreshold
                    if (activeByVad) hangoverFrames = 9 // ~360ms smooth tail
                    else if (hangoverFrames > 0) hangoverFrames--

                    val shouldSend = !deafened && (talking || activeByVad || hangoverFrames > 0 || testLoopback)
                    localTransmitting = shouldSend

                    if (shouldSend && read == FRAME_SAMPLES) {
                        val g = gameRef
                        if (testLoopback) {
                            onIncomingVoicePacket("__loopback__", encodeAdpcm(rawBuf))
                        }
                        if (g != null && g.netMp.isConnected) {
                            if (g.netMp.isOnlineRelay) {
                                System.arraycopy(rawBuf, 0, mqttBatchBuf, mqttBatchHalf * FRAME_SAMPLES, FRAME_SAMPLES)
                                mqttBatchHalf++
                                if (mqttBatchHalf >= 2) {
                                    mqttBatchHalf = 0
                                    g.netMp.broadcastVoice(encodeAdpcm(mqttBatchBuf))
                                }
                            } else {
                                g.netMp.broadcastVoice(encodeAdpcm(rawBuf))
                            }
                        }
                    } else {
                        mqttBatchHalf = 0
                    }
                }
                try { rec.stop() } catch (_: Exception) {}
            } catch (e: Exception) {
                lastError = e.message ?: "Ошибка микрофона"
            } finally {
                localTransmitting = false
                localMicLevel = 0f
                captureRunning = false
                try { recorder?.release() } catch (_: Exception) {}
            }
        }
    }

    private fun startPlaybackThread() {
        if (running) return
        running = true
        thread(isDaemon = true, name = "VoxelVoicePlayback") {
            var dev: AudioDevice? = null
            try {
                dev = Gdx.audio.newAudioDevice(SAMPLE_RATE, true)
                val mixF = FloatArray(FRAME_SAMPLES)
                val outPcm = ShortArray(FRAME_SAMPLES)

                while (running && enabled) {
                    for (i in 0 until FRAME_SAMPLES) mixF[i] = 0f
                    var anyAudio = false
                    val g = gameRef

                    if (!deafened) {
                        val now = System.currentTimeMillis()
                        val it = peers.entries.iterator()
                        while (it.hasNext()) {
                            val entry = it.next()
                            val pid = entry.key
                            val stream = entry.value
                            val frame = stream.queue.poll()
                            if (frame == null) {
                                if (now - stream.lastTalkMs > 8000L) it.remove()
                                continue
                            }
                            if (peerMuted[pid] == true) continue

                            var atten = 1.0f
                            if (pid != "__loopback__" && proximityEnabled && g != null) {
                                val rp = g.netMp.remotePlayers[pid]
                                if (rp != null) {
                                    val dist = hypot((rp.x - g.player.x).toDouble(), (rp.y - g.player.y).toDouble()).toFloat()
                                    val maxR = max(6f, proximityRange)
                                    atten = when {
                                        dist <= 4.0f -> 1.0f
                                        dist >= maxR -> 0.0f
                                        else -> 1.0f - ((dist - 4.0f) / (maxR - 4.0f))
                                    }
                                }
                            }
                            val pVol = (peerVolumes[pid] ?: 1.0f).coerceIn(0f, 2.0f) * masterVolume.coerceIn(0f, 1.8f) * atten
                            if (pVol <= 0.01f) continue

                            anyAudio = true
                            val n = min(FRAME_SAMPLES, frame.size)
                            for (i in 0 until n) {
                                mixF[i] += (frame[i] / 32768f) * pVol
                            }
                        }
                    }

                    if (!anyAudio) {
                        for (i in 0 until FRAME_SAMPLES) outPcm[i] = 0
                    } else {
                        for (i in 0 until FRAME_SAMPLES) {
                            val x = mixF[i]
                            val soft = x / (1f + abs(x) * 0.45f)
                            outPcm[i] = (soft.coerceIn(-0.98f, 0.98f) * 32767f).toInt().toShort()
                        }
                    }
                    dev.writeSamples(outPcm, 0, FRAME_SAMPLES)
                }
            } catch (_: Exception) {
            } finally {
                running = false
                try { dev?.dispose() } catch (_: Exception) {}
            }
        }
    }

    // === IMA-ADPCM 4-BIT CODEC (320 samples -> 163 bytes, crisp speech, 4 KB/s!) ===
    fun encodeAdpcm(pcm: ShortArray): ByteArray {
        val n = pcm.size
        val out = ByteArray(3 + (n + 1) / 2)
        var pred = pcm[0].toInt()
        var idx = 0
        out[0] = (pred and 0xFF).toByte()
        out[1] = ((pred shr 8) and 0xFF).toByte()
        out[2] = idx.toByte()

        var outPos = 3
        var i = 0
        while (i < n) {
            var packed = 0
            for (nib in 0 until 2) {
                val sample = if (i < n) pcm[i].toInt() else pred
                i++
                var diff = sample - pred
                var code = 0
                if (diff < 0) {
                    code = 8
                    diff = -diff
                }
                val step = STEP_TABLE[idx]
                var delta = step shr 3
                if (diff >= step) { code = code or 4; diff -= step; delta += step }
                if (diff >= (step shr 1)) { code = code or 2; diff -= (step shr 1); delta += (step shr 1) }
                if (diff >= (step shr 2)) { code = code or 1; delta += (step shr 2) }

                pred = if ((code and 8) != 0) pred - delta else pred + delta
                pred = pred.coerceIn(-32768, 32767)
                idx = (idx + INDEX_TABLE[code]).coerceIn(0, 88)

                if (nib == 0) packed = code and 0x0F else packed = packed or ((code and 0x0F) shl 4)
            }
            out[outPos++] = packed.toByte()
        }
        return out
    }

    fun decodeAdpcm(data: ByteArray): ShortArray? {
        if (data.size < 4) return null
        val payloadBytes = data.size - 3
        val out = ShortArray(payloadBytes * 2)
        var pred = ((data[1].toInt() shl 8) or (data[0].toInt() and 0xFF)).toShort().toInt()
        var idx = (data[2].toInt() and 0xFF).coerceIn(0, 88)

        var outPos = 0
        for (p in 3 until data.size) {
            val b = data[p].toInt() and 0xFF
            for (nib in 0 until 2) {
                val code = if (nib == 0) (b and 0x0F) else ((b shr 4) and 0x0F)
                val step = STEP_TABLE[idx]
                var delta = step shr 3
                if ((code and 4) != 0) delta += step
                if ((code and 2) != 0) delta += (step shr 1)
                if ((code and 1) != 0) delta += (step shr 2)
                pred = if ((code and 8) != 0) pred - delta else pred + delta
                pred = pred.coerceIn(-32768, 32767)
                idx = (idx + INDEX_TABLE[code]).coerceIn(0, 88)
                out[outPos++] = pred.toShort()
            }
        }
        return out
    }

    fun dispose() {
        stopAll()
        gameRef = null
    }
}
