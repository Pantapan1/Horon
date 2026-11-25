package com.voxelhorizon.nativegame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import kotlin.random.Random

object SoundFx {
    private val map = HashMap<String, Sound>()
    var enabled = true

    fun init() {
        dispose()
        val names = listOf(
            "mine", "break", "hit", "hurt", "step", "splash",
            "chest", "craft", "pickup", "levelup", "boss", "fish"
        )
        for (n in names) {
            try {
                val f = Gdx.files.internal("sfx_$n.wav")
                if (f.exists()) map[n] = Gdx.audio.newSound(f)
            } catch (_: Exception) {}
        }
    }

    fun play(name: String, vol: Float = 0.65f, pitchJitter: Float = 0.08f) {
        if (!enabled) return
        val s = map[name] ?: return
        val pitch = (1f + (Random.nextFloat() - 0.5f) * 2f * pitchJitter).coerceIn(0.7f, 1.35f)
        try { s.play(vol, pitch, 0f) } catch (_: Exception) {}
    }

    fun playFixedPitch(name: String, vol: Float = 0.65f, pitch: Float = 1.0f) {
        if (!enabled) return
        val s = map[name] ?: return
        try { s.play(vol, pitch.coerceIn(0.5f, 2.0f), 0f) } catch (_: Exception) {}
    }

    fun dispose() {
        for (s in map.values) try { s.dispose() } catch (_: Exception) {}
        map.clear()
    }
}
