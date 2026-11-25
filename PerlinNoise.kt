package com.voxelhorizon.nativegame

import kotlin.math.floor

class PerlinNoise(seed: Int) {
    private val p = IntArray(512)

    init {
        val perm = IntArray(256) { it }
        var s = if (seed == 0) 1337 else seed
        for (i in 255 downTo 1) {
            s = s * 1664525 + 1013904223
            val j = ((s.toLong() and 0xFFFFFFFFL) % (i + 1)).toInt()
            val tmp = perm[i]; perm[i] = perm[j]; perm[j] = tmp
        }
        for (i in 0 until 512) p[i] = perm[i and 255]
    }

    private fun fade(t: Double) = t * t * t * (t * (t * 6.0 - 15.0) + 10.0)
    private fun lerp(t: Double, a: Double, b: Double) = a + t * (b - a)

    private fun grad2(hash: Int, x: Double, y: Double): Double {
        val h = hash and 7
        val u = if (h < 4) x else y
        val v = if (h < 4) y else x
        return (if ((h and 1) != 0) -u else u) + (if ((h and 2) != 0) -2.0 * v else 2.0 * v)
    }

    private fun grad3(hash: Int, x: Double, y: Double, z: Double): Double {
        val h = hash and 15
        val u = if (h < 8) x else y
        val v = if (h < 4) y else if (h == 12 || h == 14) x else z
        return (if ((h and 1) == 0) u else -u) + (if ((h and 2) == 0) v else -v)
    }

    fun noise2(xIn: Double, yIn: Double): Double {
        val xi = floor(xIn).toInt() and 255
        val yi = floor(yIn).toInt() and 255
        val x = xIn - floor(xIn)
        val y = yIn - floor(yIn)
        val u = fade(x)
        val v = fade(y)
        val a = p[xi] + yi
        val b = p[xi + 1] + yi
        return lerp(
            v,
            lerp(u, grad2(p[a], x, y), grad2(p[b], x - 1.0, y)),
            lerp(u, grad2(p[a + 1], x, y - 1.0), grad2(p[b + 1], x - 1.0, y - 1.0))
        ) * 0.45 + 0.5
    }

    fun noise3(xIn: Double, yIn: Double, zIn: Double): Double {
        val xi = floor(xIn).toInt() and 255
        val yi = floor(yIn).toInt() and 255
        val zi = floor(zIn).toInt() and 255
        val x = xIn - floor(xIn)
        val y = yIn - floor(yIn)
        val z = zIn - floor(zIn)
        val u = fade(x); val v = fade(y); val w = fade(z)
        val a = p[xi] + yi; val aa = p[a] + zi; val ab = p[a + 1] + zi
        val b = p[xi + 1] + yi; val ba = p[b] + zi; val bb = p[b + 1] + zi
        return lerp(
            w,
            lerp(
                v,
                lerp(u, grad3(p[aa], x, y, z), grad3(p[ba], x - 1.0, y, z)),
                lerp(u, grad3(p[ab], x, y - 1.0, z), grad3(p[bb], x - 1.0, y - 1.0, z))
            ),
            lerp(
                v,
                lerp(u, grad3(p[aa + 1], x, y, z - 1.0), grad3(p[ba + 1], x - 1.0, y, z - 1.0)),
                lerp(u, grad3(p[ab + 1], x, y - 1.0, z - 1.0), grad3(p[bb + 1], x - 1.0, y - 1.0, z - 1.0))
            )
        ) * 0.5 + 0.5
    }

    fun fbm2(x: Double, y: Double, octaves: Int = 4): Double {
        var total = 0.0
        var amp = 0.5
        var freq = 1.0
        var norm = 0.0
        for (i in 0 until octaves) {
            total += noise2(x * freq, y * freq) * amp
            norm += amp
            amp *= 0.5
            freq *= 2.03
        }
        return total / norm
    }

    fun hash2(x: Int, z: Int): Double {
        var n = (x * 374761393 + z * 668265263) * 1274126177
        n = n xor (n ushr 16)
        return (n.toLong() and 0xFFFFFFFFL).toDouble() / 4294967295.0
    }
}
