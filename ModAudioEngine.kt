package com.voxelhorizon.nativegame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.AudioDevice
import com.badlogic.gdx.audio.Sound
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

private class ModSynthVoice(
    var freq: Float,
    var waveform: Int, // 0=Saw, 1=Square, 2=Sine, 3=Noise/Drum
    var vol: Float,
    var attack: Float,
    var decay: Float,
    var vibrato: Float,
    var lpCut: Float,
    var duty: Float,
    var channel: Int
) {
    var phase = 0f
    var age = 0f
    var lpState = 0f
}

object ModAudioEngine {
    private const val SAMPLE_RATE = 22050
    private const val BUF_SAMPLES = 512

    @Volatile private var running = false
    private var audioDev: AudioDevice? = null
    private val pendingVoices = ConcurrentLinkedQueue<ModSynthVoice>()
    private val activeVoices = ArrayList<ModSynthVoice>(16)
    private val packSounds = ConcurrentHashMap<String, Sound>()

    @Volatile var isPlaying = false
    @Volatile var playInWorldBg = false
    @Volatile var forceMuteOst = false
    @Volatile var currentStep = 0
    @Volatile var seqBpm = 120
    @Volatile var seqLength = 16
    @Volatile var seqWaves = intArrayOf(1, 0, 2, 3)
    @Volatile var seqNotes = IntArray(4 * 32) { -1 } // channel * 32 + step
    @Volatile var masterVol = 0.72f
    @Volatile var presetAttack = 0.02f
    @Volatile var presetDecay = 0.28f
    @Volatile var presetVibrato = 0.12f
    @Volatile var presetLpCut = 0.85f
    @Volatile var presetDuty = 0.50f

    val vuLevels = FloatArray(4)
    private var stepAccum = 0f

    fun startEngine() {
        if (running) return
        running = true
        thread(isDaemon = true, name = "VoxelModAudioEngine") {
            try {
                audioDev = Gdx.audio.newAudioDevice(SAMPLE_RATE, true)
                val pcm = ShortArray(BUF_SAMPLES)
                val dtBuf = BUF_SAMPLES.toFloat() / SAMPLE_RATE.toFloat()
                val invSr = 1f / SAMPLE_RATE.toFloat()
                var rngState = 0x13579BDF

                while (running) {
                    while (true) {
                        val nv = pendingVoices.poll() ?: break
                        if (activeVoices.size >= 16) activeVoices.removeAt(0)
                        activeVoices.add(nv)
                    }

                    if (isPlaying) {
                        val len = seqLength.coerceIn(4, 32)
                        val stepDur = 15f / max(40, seqBpm).toFloat()
                        stepAccum += dtBuf
                        if (stepAccum >= stepDur) {
                            stepAccum -= stepDur
                            val st = currentStep % len
                            val notesSnap = seqNotes
                            val wavesSnap = seqWaves
                            for (ch in 0 until 4) {
                                val midi = notesSnap[ch * 32 + st]
                                if (midi >= 20) {
                                    val wave = wavesSnap[ch.coerceIn(0, wavesSnap.lastIndex)]
                                    val freq = midiToFreq(midi)
                                    val dec = if (ch == 3) 0.12f else if (ch == 1) 0.32f else presetDecay
                                    val vol = if (ch == 1) 0.58f else if (ch == 3) 0.52f else 0.56f
                                    activeVoices.add(
                                        ModSynthVoice(
                                            freq, wave, vol * masterVol,
                                            presetAttack, dec, presetVibrato, presetLpCut, presetDuty, ch
                                        )
                                    )
                                    vuLevels[ch] = 1.0f
                                }
                            }
                            currentStep = (st + 1) % len
                        }
                    }

                    for (c in 0 until 4) vuLevels[c] = max(0f, vuLevels[c] - dtBuf * 4.5f)

                    if (activeVoices.isEmpty()) {
                        for (i in 0 until BUF_SAMPLES) pcm[i] = 0
                        audioDev?.writeSamples(pcm, 0, BUF_SAMPLES)
                        continue
                    }

                    for (i in 0 until BUF_SAMPLES) {
                        var mix = 0f
                        var vi = activeVoices.size - 1
                        while (vi >= 0) {
                            val v = activeVoices[vi]
                            v.age += invSr
                            val totalDur = v.attack + v.decay
                            if (v.age >= totalDur) {
                                activeVoices.removeAt(vi)
                                vi--
                                continue
                            }
                            val env = if (v.age < v.attack) {
                                v.age / max(0.002f, v.attack)
                            } else {
                                1f - ((v.age - v.attack) / max(0.01f, v.decay))
                            }
                            val vib = if (v.vibrato > 0.01f && v.waveform != 3) {
                                1f + sin(v.age * 34f) * (v.vibrato * 0.035f)
                            } else 1f

                            v.phase += (v.freq * vib) * invSr
                            if (v.phase >= 1f) v.phase -= 1f

                            val raw = when (v.waveform) {
                                0 -> (v.phase * 2f - 1f)
                                1 -> if (v.phase < v.duty) 0.85f else -0.85f
                                2 -> sin(v.phase * 2f * PI.toFloat())
                                else -> {
                                    rngState = rngState xor (rngState shl 13)
                                    rngState = rngState xor (rngState ushr 17)
                                    rngState = rngState xor (rngState shl 5)
                                    val n = ((rngState and 0xFFFF) / 32768f) - 1f
                                    if (v.freq < 180f) sin(v.phase * 2f * PI.toFloat() * (1.2f - v.age * 4f).coerceAtLeast(0.3f)) * 0.85f + n * 0.25f else n * 0.8f
                                }
                            }
                            val alpha = v.lpCut.coerceIn(0.08f, 1.0f)
                            v.lpState += alpha * (raw - v.lpState)
                            mix += v.lpState * env * v.vol
                            vi--
                        }
                        val clamped = (mix * 0.55f).coerceIn(-0.98f, 0.98f)
                        pcm[i] = (clamped * 32767f).toInt().toShort()
                    }
                    audioDev?.writeSamples(pcm, 0, BUF_SAMPLES)
                }
            } catch (_: Exception) {
            } finally {
                try { audioDev?.dispose() } catch (_: Exception) {}
                audioDev = null
            }
        }
    }

    fun midiToFreq(midi: Int): Float = (440.0 * 2.0.pow((midi - 69) / 12.0)).toFloat()

    fun playNote(
        midi: Int,
        wave: Int = 1,
        vol: Float = masterVol,
        attack: Float = presetAttack,
        decay: Float = presetDecay,
        vibrato: Float = presetVibrato,
        lpCut: Float = presetLpCut,
        duty: Float = presetDuty,
        channel: Int = 0
    ) {
        if (!running) startEngine()
        pendingVoices.add(
            ModSynthVoice(
                midiToFreq(midi), wave.coerceIn(0, 3), vol.coerceIn(0.05f, 1.0f),
                attack.coerceIn(0.002f, 0.4f), decay.coerceIn(0.05f, 2.0f),
                vibrato.coerceIn(0f, 1f), lpCut.coerceIn(0.08f, 1f), duty.coerceIn(0.1f, 0.9f),
                channel.coerceIn(0, 3)
            )
        )
        vuLevels[channel.coerceIn(0, 3)] = 1.0f
    }

    fun playFreq(freqHz: Float, wave: Int = 1, dur: Float = 0.25f, vol: Float = 0.7f) {
        if (!running) startEngine()
        pendingVoices.add(
            ModSynthVoice(
                freqHz.coerceIn(30f, 6000f), wave.coerceIn(0, 3), vol.coerceIn(0.05f, 1f),
                0.01f, dur.coerceIn(0.04f, 3.0f), 0f, 0.9f, 0.5f, 0
            )
        )
        vuLevels[0] = 1.0f
    }

    fun startSeq(bpm: Int, length: Int, notesFlat: IntArray, waves4: IntArray, bgInWorld: Boolean) {
        if (!running) startEngine()
        seqBpm = bpm.coerceIn(40, 240)
        seqLength = length.coerceIn(4, 32)
        seqNotes = notesFlat.copyOf(4 * 32)
        seqWaves = waves4.copyOf(4)
        currentStep = 0
        stepAccum = 10f
        isPlaying = true
        playInWorldBg = bgInWorld
    }

    fun updateSeq(bpm: Int, length: Int, notesFlat: IntArray, waves4: IntArray) {
        seqBpm = bpm.coerceIn(40, 240)
        seqLength = length.coerceIn(4, 32)
        seqNotes = notesFlat.copyOf(4 * 32)
        seqWaves = waves4.copyOf(4)
    }

    fun stopSeq() {
        isPlaying = false
        playInWorldBg = false
        currentStep = 0
    }

    fun playPackSound(file: File, vol: Float = 0.8f) {
        if (!file.exists()) return
        Gdx.app.postRunnable {
            try {
                val s = packSounds.computeIfAbsent(file.absolutePath) {
                    Gdx.audio.newSound(Gdx.files.absolute(file.absolutePath))
                }
                s.play(vol.coerceIn(0.05f, 1.0f))
            } catch (_: Exception) {}
        }
    }

    fun dispose() {
        running = false
        isPlaying = false
        playInWorldBg = false
        forceMuteOst = false
        for (s in packSounds.values) {
            try { s.dispose() } catch (_: Exception) {}
        }
        packSounds.clear()
    }
}
