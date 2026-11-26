package com.voxelhorizon.nativegame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.AudioDevice
import com.badlogic.gdx.audio.Sound
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

private class ModSynthVoice(
    var freq: Float,
    var waveform: Int, // 0=Saw, 1=Square, 2=Sine/Tri, 3=Drum/Noise
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
    var releaseGain = 1f
    var dying = false
}

object ModAudioEngine {
    private const val SAMPLE_RATE = 44100
    private const val BUF_SAMPLES = 1024
    private const val TWO_PI = (2.0 * PI).toFloat()

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
    @Volatile var presetAttack = 0.012f
    @Volatile var presetDecay = 0.24f
    @Volatile var presetVibrato = 0.08f
    @Volatile var presetLpCut = 0.72f
    @Volatile var presetDuty = 0.50f

    val vuLevels = FloatArray(4)
    @Volatile private var stepSamplesLeft = 0

    fun startEngine() {
        if (running) return
        running = true
        thread(isDaemon = true, name = "VoxelModAudioEngine") {
            try {
                audioDev = Gdx.audio.newAudioDevice(SAMPLE_RATE, true)
                val pcm = ShortArray(BUF_SAMPLES)
                val invSr = 1f / SAMPLE_RATE.toFloat()
                val dtBuf = BUF_SAMPLES.toFloat() * invSr
                var rngState = 0x13579BDF

                while (running) {
                    while (true) {
                        val nv = pendingVoices.poll() ?: break
                        if (activeVoices.size >= 12) activeVoices.removeAt(0)
                        activeVoices.add(nv)
                    }

                    if (!isPlaying && activeVoices.isEmpty()) {
                        for (c in 0 until 4) vuLevels[c] = max(0f, vuLevels[c] - dtBuf * 4.5f)
                        for (i in 0 until BUF_SAMPLES) pcm[i] = 0
                        audioDev?.writeSamples(pcm, 0, BUF_SAMPLES)
                        continue
                    }

                    for (i in 0 until BUF_SAMPLES) {
                        // Sample-accurate 16th-note sequencer clock (ZERO jitter, ZERO helicopter runaway!)
                        if (isPlaying) {
                            if (stepSamplesLeft <= 0) {
                                val len = seqLength.coerceIn(4, 32)
                                val bpm = seqBpm.coerceIn(40, 240)
                                // 1 beat = 60/bpm sec -> 1 sixteenth note = 15/bpm sec
                                stepSamplesLeft = max(2205, (SAMPLE_RATE.toFloat() * 15f / bpm.toFloat()).toInt())
                                val st = currentStep % len
                                val notesSnap = seqNotes
                                val wavesSnap = seqWaves
                                val stepSec = stepSamplesLeft * invSr

                                for (ch in 0 until 4) {
                                    val midi = notesSnap[ch * 32 + st]
                                    if (midi >= 20) {
                                        // Smoothly fade out any previous voice on the same channel to prevent mud/clipping!
                                        for (k in 0 until activeVoices.size) {
                                            val old = activeVoices[k]
                                            if (old.channel == ch) old.dying = true
                                        }
                                        val wave = wavesSnap[ch.coerceIn(0, wavesSnap.lastIndex)]
                                        val freq = midiToFreq(midi)
                                        val att = if (ch == 3) 0.003f else max(0.006f, presetAttack)
                                        val dec = when (ch) {
                                            3 -> if (midi < 46) 0.14f else 0.08f
                                            1 -> (stepSec * 1.45f).coerceIn(0.14f, 0.36f)
                                            else -> max(0.10f, presetDecay.coerceAtMost(stepSec * 1.85f))
                                        }
                                        val chVol = when (ch) {
                                            0 -> 0.44f
                                            1 -> 0.48f
                                            2 -> 0.34f
                                            else -> 0.42f
                                        }
                                        val lp = when (ch) {
                                            1 -> presetLpCut.coerceAtMost(0.42f)
                                            2 -> presetLpCut.coerceAtMost(0.55f)
                                            else -> presetLpCut.coerceIn(0.18f, 0.85f)
                                        }
                                        if (activeVoices.size >= 12) activeVoices.removeAt(0)
                                        activeVoices.add(
                                            ModSynthVoice(
                                                freq, wave, chVol * masterVol,
                                                att, dec,
                                                if (ch == 0) presetVibrato else 0f,
                                                lp, presetDuty, ch
                                            )
                                        )
                                        vuLevels[ch] = 1.0f
                                    }
                                }
                                currentStep = (st + 1) % len
                            }
                            stepSamplesLeft--
                        }

                        var mix = 0f
                        var vi = activeVoices.size - 1
                        while (vi >= 0) {
                            val v = activeVoices[vi]
                            v.age += invSr
                            if (v.dying) {
                                v.releaseGain -= invSr * 160f // smooth ~6ms anti-click fade out
                                if (v.releaseGain <= 0f) {
                                    activeVoices.removeAt(vi)
                                    vi--
                                    continue
                                }
                            }
                            val totalDur = v.attack + v.decay
                            if (v.age >= totalDur) {
                                activeVoices.removeAt(vi)
                                vi--
                                continue
                            }
                            val env = if (v.age < v.attack) {
                                v.age / max(0.002f, v.attack)
                            } else {
                                val t = (v.age - v.attack) / max(0.01f, v.decay)
                                (1f - t) * (1f - t) // Smooth exponential-like musical decay!
                            }

                            val raw = if (v.waveform == 3) {
                                // Clean Drum / Percussion Synth (continuous phase sweep -> ZERO clicks!)
                                rngState = rngState xor (rngState shl 13)
                                rngState = rngState xor (rngState ushr 17)
                                rngState = rngState xor (rngState shl 5)
                                val noise = ((rngState and 0xFFFF) / 32768f) - 1f
                                if (v.freq < 140f) {
                                    // Punchy 808/retro kick: smooth pitch drop 135Hz -> 42Hz
                                    val kFreq = 42f + 95f * (1f - (v.age * 11f).coerceIn(0f, 1f))
                                    v.phase += kFreq * invSr
                                    if (v.phase >= 1f) v.phase -= 1f
                                    sin(v.phase * TWO_PI) * 0.92f + noise * 0.12f * (1f - (v.age * 25f).coerceIn(0f, 1f))
                                } else if (v.freq < 260f) {
                                    // Crisp Snare: body tone + white noise burst
                                    v.phase += 185f * invSr
                                    if (v.phase >= 1f) v.phase -= 1f
                                    sin(v.phase * TWO_PI) * 0.42f + noise * 0.58f
                                } else {
                                    // Tight Hi-Hat
                                    noise * 0.45f
                                }
                            } else {
                                val vib = if (v.vibrato > 0.01f) {
                                    1f + sin(v.age * 32f) * (v.vibrato * 0.016f)
                                } else 1f
                                v.phase += (v.freq * vib) * invSr
                                if (v.phase >= 1f) v.phase -= 1f
                                when (v.waveform) {
                                    0 -> (v.phase * 2f - 1f) * 0.72f                                   // Warm Saw
                                    1 -> if (v.phase < v.duty) 0.68f else -0.68f                       // Classic 8-bit Pulse
                                    else -> {
                                        // Pure Sine / Soft Chiptune Triangle blend
                                        val tri = 1f - 4f * abs(v.phase - 0.5f)
                                        sin(v.phase * TWO_PI) * 0.65f + tri * 0.35f
                                    }
                                }
                            }

                            // Smooth 1-pole lowpass scaled for 44.1 kHz
                            val alpha = (v.lpCut * 0.42f).coerceIn(0.03f, 0.88f)
                            v.lpState += alpha * (raw - v.lpState)
                            mix += v.lpState * env * v.vol * v.releaseGain
                            vi--
                        }

                        // Soft analog limiter (NEVER harsh digital clipping!)
                        val soft = (mix * 1.15f) / (1f + abs(mix * 0.95f))
                        pcm[i] = (soft.coerceIn(-0.96f, 0.96f) * 32767f).toInt().toShort()
                    }

                    for (c in 0 until 4) vuLevels[c] = max(0f, vuLevels[c] - dtBuf * 4.2f)
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
                midiToFreq(midi), wave.coerceIn(0, 3), vol.coerceIn(0.05f, 1.0f) * 0.55f,
                attack.coerceIn(0.005f, 0.4f), decay.coerceIn(0.06f, 2.0f),
                vibrato.coerceIn(0f, 1f), lpCut.coerceIn(0.12f, 1f), duty.coerceIn(0.15f, 0.85f),
                channel.coerceIn(0, 3)
            )
        )
        vuLevels[channel.coerceIn(0, 3)] = 1.0f
    }

    fun playFreq(freqHz: Float, wave: Int = 1, dur: Float = 0.25f, vol: Float = 0.7f) {
        if (!running) startEngine()
        pendingVoices.add(
            ModSynthVoice(
                freqHz.coerceIn(30f, 6000f), wave.coerceIn(0, 3), vol.coerceIn(0.05f, 1.0f) * 0.55f,
                0.008f, dur.coerceIn(0.05f, 3.0f), 0f, 0.85f, 0.5f, 0
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
        stepSamplesLeft = 0
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
        stepSamplesLeft = 0
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
