package com.voxelhorizon.nativegame

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

enum class Season(val ruName: String, val rTint: Float, val gTint: Float, val bTint: Float) {
    SPRING("Весна", 0.96f, 1.12f, 0.98f),
    SUMMER("Лето", 1.00f, 1.00f, 1.00f),
    AUTUMN("Осень", 1.24f, 0.84f, 0.44f),
    WINTER("Зима", 0.92f, 1.04f, 1.24f)
}

enum class WeatherType(val ruName: String) {
    CLEAR("Ясно"),
    RAIN("Дождь"),
    STORM("Гроза"),
    SNOW("Снегопад")
}

class WeatherAndSky {
    var worldTime = 0.28f // 0..1
    var dayCount = 1
    var season = Season.SUMMER
    var weather = WeatherType.CLEAR
    var skyColor = Color(0.45f, 0.72f, 0.98f, 1f)
    var dayFactor = 1f
    var lightningFlash = 0f

    private var weatherTimer = 90f
    private var lightningTimer = 6f

    private val mb = ModelBuilder()
    private val sunModel: Model = mb.createBox(10f, 10f, 2f, Material(ColorAttribute.createDiffuse(1f, 0.95f, 0.65f, 1f)), (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong())
    private val moonModel: Model = mb.createBox(8f, 8f, 2f, Material(ColorAttribute.createDiffuse(0.85f, 0.92f, 1f, 1f)), (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong())
    private val cloudModel: Model = mb.createBox(1f, 1f, 1f, Material(ColorAttribute.createDiffuse(0.96f, 0.98f, 1f, 1f)), (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong())
    private val dropModel: Model = mb.createBox(0.08f, 0.65f, 0.08f, Material(ColorAttribute.createDiffuse(0.75f, 0.88f, 1f, 1f)), (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong())
    private val snowModel: Model = mb.createBox(0.18f, 0.18f, 0.18f, Material(ColorAttribute.createDiffuse(1f, 1f, 1f, 1f)), (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong())

    private val clouds = Array(14) { floatArrayOf((Random.nextFloat() - 0.5f) * 120f, 54f + (it % 3) * 2.5f, (Random.nextFloat() - 0.5f) * 120f, 8f + Random.nextFloat() * 8f, 6f + Random.nextFloat() * 6f, 1.4f + Random.nextFloat()) }
    private val precip = Array(90) { floatArrayOf((Random.nextFloat() - 0.5f) * 26f, Random.nextFloat() * 20f, (Random.nextFloat() - 0.5f) * 26f) }

    fun nextSeason(): Season {
        season = Season.values()[(season.ordinal + 1) % Season.values().size]
        if (season == Season.WINTER && weather == WeatherType.RAIN) weather = WeatherType.SNOW
        return season
    }

    fun nextWeather(): WeatherType {
        weather = WeatherType.values()[(weather.ordinal + 1) % WeatherType.values().size]
        if (season == Season.WINTER && weather == WeatherType.RAIN) weather = WeatherType.SNOW
        return weather
    }

    fun update(dt: Float, dimension: String, player: PlayerState) {
        if (dimension == "nether") {
            skyColor.set(0.24f, 0.05f, 0.06f, 1f)
            dayFactor = 0.65f
            return
        }
        if (dimension == "aether") {
            skyColor.set(0.45f, 0.82f, 1.0f, 1f)
            dayFactor = 0.95f
            return
        }

        worldTime += dt / 360f
        if (worldTime >= 1f) { worldTime -= 1f; dayCount++ }

        val sunAngle = worldTime * PI.toFloat() * 2f
        dayFactor = max(0f, sin(sunAngle))
        val sunset = max(0f, 1f - abs(sin(sunAngle) - 0.14f) * 4.5f)

        weatherTimer -= dt
        if (weatherTimer <= 0f) {
            weatherTimer = 85f + Random.nextFloat() * 80f
            val r = Random.nextFloat()
            weather = when {
                season == Season.WINTER -> if (r < 0.5f) WeatherType.SNOW else WeatherType.CLEAR
                season == Season.AUTUMN -> if (r < 0.4f) WeatherType.RAIN else if (r < 0.6f) WeatherType.STORM else WeatherType.CLEAR
                else -> if (r < 0.25f) WeatherType.RAIN else if (r < 0.35f) WeatherType.STORM else WeatherType.CLEAR
            }
        }

        if (weather == WeatherType.STORM) {
            lightningTimer -= dt
            if (lightningTimer <= 0f) {
                lightningTimer = 4f + Random.nextFloat() * 8f
                lightningFlash = 1f
            }
        }
        if (lightningFlash > 0f) lightningFlash = max(0f, lightningFlash - dt * 3.5f)

        val stormDim = when (weather) {
            WeatherType.STORM -> 0.45f
            WeatherType.RAIN -> 0.70f
            WeatherType.SNOW -> 0.86f
            else -> 1.0f
        }
        skyColor.set(
            ((0.06f + 0.44f * dayFactor + 0.36f * sunset) * stormDim + lightningFlash * 0.7f).coerceIn(0f, 1f),
            ((0.08f + 0.64f * dayFactor + 0.14f * sunset) * stormDim + lightningFlash * 0.8f).coerceIn(0f, 1f),
            ((0.16f + 0.82f * dayFactor - 0.10f * sunset) * stormDim + lightningFlash * 0.9f).coerceIn(0f, 1f),
            1f
        )

        val wind = if (weather == WeatherType.STORM) 3.0f else if (weather == WeatherType.RAIN) 1.7f else 1.0f
        for (c in clouds) {
            c[0] += c[5] * wind * dt
            if (c[0] > 65f) c[0] = -65f
        }

        if (weather != WeatherType.CLEAR) {
            val fall = if (weather == WeatherType.SNOW) 3.4f else 19f
            for (p in precip) {
                p[1] -= fall * dt
                if (p[1] < player.y - 3f || abs(p[0] - player.x) > 16f || abs(p[2] - player.z) > 16f) {
                    p[0] = player.x + (Random.nextFloat() - 0.5f) * 26f
                    p[1] = player.y + 10f + Random.nextFloat() * 12f
                    p[2] = player.z + (Random.nextFloat() - 0.5f) * 26f
                }
            }
        }
    }

    fun render(batch: ModelBatch, env: Environment, dimension: String, player: PlayerState) {
        if (dimension != "overworld") return
        val sunAngle = worldTime * PI.toFloat() * 2f
        val sx = cos(sunAngle) * 76f
        val sy = sin(sunAngle) * 76f

        val sunInst = ModelInstance(sunModel)
        sunInst.transform.idt().translate(player.x + sx, max(-40f, player.y + sy), player.z - 18f)
        batch.render(sunInst, env)

        val moonInst = ModelInstance(moonModel)
        moonInst.transform.idt().translate(player.x - sx, max(-40f, player.y - sy), player.z + 18f)
        batch.render(moonInst, env)

        for (c in clouds) {
            val ci = ModelInstance(cloudModel)
            ci.transform.idt().translate(player.x + c[0], c[1], player.z + c[2]).scale(c[3], 1.8f, c[4])
            batch.render(ci, env)
        }

        if (weather != WeatherType.CLEAR) {
            val m = if (weather == WeatherType.SNOW) snowModel else dropModel
            for (p in precip) {
                val pi = ModelInstance(m)
                pi.transform.idt().translate(p[0], p[1], p[2])
                batch.render(pi, env)
            }
        }
    }

    fun dispose() {
        sunModel.dispose(); moonModel.dispose(); cloudModel.dispose(); dropModel.dispose(); snowModel.dispose()
    }
}
