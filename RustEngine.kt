package com.voxelhorizon.nativegame

import java.nio.ByteBuffer

object RustEngine {
    init {
        System.loadLibrary("voxelhorizon_core")
    }

    @JvmStatic external fun nativeInit(seed: Int, dim: Int)
    @JvmStatic external fun nativeResetDim(dim: Int, seed: Int)
    @JvmStatic external fun nativeClearMods()
    @JvmStatic external fun nativePutMod(dim: Int, x: Int, y: Int, z: Int, id: Int)
    @JvmStatic external fun nativeRegisterModBlock(id: Int, tileIdx: Int, colorHex: Int)

    @JvmStatic external fun nativeGetBlock(x: Int, y: Int, z: Int): Int
    @JvmStatic external fun nativeSetBlock(x: Int, y: Int, z: Int, id: Int)
    @JvmStatic external fun nativeGetSurfaceZ(x: Int, y: Int): Int
    @JvmStatic external fun nativeGetTopBlockZ(x: Int, y: Int): Int
    @JvmStatic external fun nativeGetGroundZ(x: Int, y: Int, curZ: Float): Float
    @JvmStatic external fun nativeHasRoofAbove(x: Int, y: Int, pz: Int): Boolean
    @JvmStatic external fun nativeApplySeasonalStep(seasonIdx: Int, seasonProgress: Float, px: Int, py: Int, rngSeed: Int)
    @JvmStatic external fun nativePickIsoBlock(wxS: Float, wyS: Float, pGx: Int, pGy: Int, pz: Int, sliceZ: Int, isCutaway: Boolean, outPick: ByteBuffer)

    @JvmStatic external fun nativeBuildIsoFrame(
        elapsed: Float,
        px: Float, py: Float, pz: Float,
        camX: Float, camY: Float, halfW: Float, halfH: Float,
        grassR: Float, grassG: Float, grassB: Float,
        isCutaway: Boolean, sliceZ: Int, minerVision: Boolean,
        outVerts: ByteBuffer, outDiag: ByteBuffer, outProps: ByteBuffer,
        outLights: ByteBuffer, outOres: ByteBuffer, outCounts: ByteBuffer
    )

    @JvmStatic external fun nativeRevealFog(x: Int, y: Int, rad: Int)
    @JvmStatic external fun nativeFindElevatorZ(x: Int, y: Int, curZ: Int): Int
    @JvmStatic external fun nativeSampleMinimap(cx: Int, cy: Int, step: Int, size: Int, outRgba: ByteBuffer)
}
