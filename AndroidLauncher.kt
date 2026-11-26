package com.voxelhorizon.nativegame

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import java.io.File
import kotlin.math.abs

object GamepadBridge {
    @Volatile var moveX = 0f
    @Volatile var moveY = 0f
    @Volatile var lookX = 0f
    @Volatile var lookY = 0f
    @Volatile var jumpPressed = false
    @Volatile var sneakPressed = false
    @Volatile var attackPressed = false
    @Volatile var placePressed = false
    @Volatile var dashTriggered = false
    @Volatile var swapOffhandTriggered = false
    @Volatile var hotbarDelta = 0
}

class AndroidLauncher : AndroidApplication() {
    companion object {
        private const val REQ_PICK_IMPORT_FILE = 902
    }

    private var gameInstance: VoxelHorizonGame? = null
    private lateinit var activeStorageRoot: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Запрос разрешений на память и уведомления при первом запуске!
        requestFirstLaunchPermissions()

        val publicMirror = try {
            val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            File(docs, "VoxelHorizon").also { it.mkdirs(); File(it, "worlds").mkdirs(); File(it, "mods").mkdirs() }
        } catch (_: Exception) { null }

        val canWriteDocs = try {
            if (publicMirror != null) {
                val probe = File(publicMirror, ".probe")
                probe.writeText("ok"); probe.delete(); true
            } else false
        } catch (_: Exception) { false }

        val appRoot = File(getExternalFilesDir(null) ?: filesDir, "VoxelHorizon").also {
            it.mkdirs(); File(it, "worlds").mkdirs(); File(it, "mods").mkdirs()
        }
        val storageRoot = if (canWriteDocs && publicMirror != null) {
            migrateDirIfMissing(File(appRoot, "worlds"), File(publicMirror, "worlds"))
            migrateDirIfMissing(File(appRoot, "mods"), File(publicMirror, "mods"))
            try {
                val srcP = File(appRoot, "profiles_v62.dat")
                val dstP = File(publicMirror, "profiles_v62.dat")
                if (srcP.isFile && (!dstP.exists() || srcP.lastModified() > dstP.lastModified())) {
                    srcP.copyTo(dstP, overwrite = true)
                }
            } catch (_: Exception) {}
            publicMirror
        } else appRoot
        activeStorageRoot = storageRoot

        PlatformBridge.init(this, publicMirror, storageRoot)
        PlatformBridge.onPickFileRequest = {
            runOnUiThread {
                try {
                    val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    startActivityForResult(i, REQ_PICK_IMPORT_FILE)
                } catch (_: Exception) {
                    gameInstance?.showToast("Не удалось открыть проводник")
                }
            }
        }

        // Если игру открыли тапом по .vhworld / .vhmod / .lua / .vhpack в мессенджере или проводнике!
        intent?.let { PlatformBridge.handleIncomingIntent(this, it, activeStorageRoot) }

        val config = AndroidApplicationConfiguration().apply {
            useAccelerometer = false
            useCompass = false
            useImmersiveMode = true
            useWakelock = true
            numSamples = 0
            r = 8; g = 8; b = 8; a = 8
            depth = 24
        }
        val game = VoxelHorizonGame(storageRoot)
        gameInstance = game
        initialize(game, config)
        applyImmersiveFullscreen()
    }

    private fun migrateDirIfMissing(srcDir: File, dstDir: File) {
        try {
            dstDir.mkdirs()
            srcDir.listFiles()?.forEach { f ->
                val dst = File(dstDir, f.name)
                if (f.isFile && !dst.exists()) f.copyTo(dst)
            }
        } catch (_: Exception) {}
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent != null && ::activeStorageRoot.isInitialized) {
            PlatformBridge.handleIncomingIntent(this, intent, activeStorageRoot)
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_IMPORT_FILE && resultCode == RESULT_OK && data?.data != null && ::activeStorageRoot.isInitialized) {
            PlatformBridge.handleIncomingUri(this, data.data!!, activeStorageRoot)
        }
    }

    private fun requestFirstLaunchPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                needed += Manifest.permission.POST_NOTIFICATIONS
            }
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                needed += Manifest.permission.READ_MEDIA_IMAGES
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                needed += Manifest.permission.READ_EXTERNAL_STORAGE
            }
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                needed += Manifest.permission.WRITE_EXTERNAL_STORAGE
            }
        }
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), 901)
        }
    }

    override fun onStop() {
        super.onStop()
        PlatformBridge.onBeforeUpdateCallback?.invoke()
        PlatformBridge.scheduleComeBackReminder(20)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveFullscreen()
    }

    private fun applyImmersiveFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { ctrl ->
                ctrl.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                ctrl.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK &&
            event.action == MotionEvent.ACTION_MOVE
        ) {
            fun dz(v: Float) = if (abs(v) < 0.15f) 0f else v
            GamepadBridge.moveX = dz(event.getAxisValue(MotionEvent.AXIS_X))
            GamepadBridge.moveY = -dz(event.getAxisValue(MotionEvent.AXIS_Y))
            GamepadBridge.lookX = dz(event.getAxisValue(MotionEvent.AXIS_Z))
            GamepadBridge.lookY = -dz(event.getAxisValue(MotionEvent.AXIS_RZ))
            GamepadBridge.attackPressed = event.getAxisValue(MotionEvent.AXIS_RTRIGGER) > 0.4f
            GamepadBridge.placePressed = event.getAxisValue(MotionEvent.AXIS_LTRIGGER) > 0.4f
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
            when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> { GamepadBridge.jumpPressed = true; return true }
                KeyEvent.KEYCODE_BUTTON_B -> { GamepadBridge.sneakPressed = !GamepadBridge.sneakPressed; return true }
                KeyEvent.KEYCODE_BUTTON_X -> { GamepadBridge.dashTriggered = true; return true }
                KeyEvent.KEYCODE_BUTTON_Y -> { GamepadBridge.swapOffhandTriggered = true; return true }
                KeyEvent.KEYCODE_BUTTON_L1 -> { GamepadBridge.hotbarDelta = -1; return true }
                KeyEvent.KEYCODE_BUTTON_R1 -> { GamepadBridge.hotbarDelta = 1; return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
            if (keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                GamepadBridge.jumpPressed = false
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }
}
