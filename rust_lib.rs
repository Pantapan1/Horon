mod noise;
mod world;
mod mesher;

use std::slice;
use std::sync::{Mutex, OnceLock};
use jni::objects::{JByteBuffer, JClass};
use jni::sys::{jboolean, jfloat, jint, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;

use crate::mesher::build_iso_frame;
use crate::world::RustWorld;

static WORLD: OnceLock<Mutex<RustWorld>> = OnceLock::new();

fn with_world<R>(f: impl FnOnce(&mut RustWorld) -> R) -> R {
    let lock = WORLD.get_or_init(|| Mutex::new(RustWorld::new(1337, 0)));
    let mut guard = lock.lock().unwrap_or_else(|e| e.into_inner());
    f(&mut guard)
}

unsafe fn buf_as_f32_mut<'a>(env: &JNIEnv<'_>, buf: &JByteBuffer<'_>) -> Option<&'a mut [f32]> {
    let ptr = env.get_direct_buffer_address(buf).ok()?;
    let cap_bytes = env.get_direct_buffer_capacity(buf).ok()?;
    if ptr.is_null() || cap_bytes < 4 {
        return None;
    }
    Some(slice::from_raw_parts_mut(ptr as *mut f32, cap_bytes / 4))
}

unsafe fn buf_as_i32_mut<'a>(env: &JNIEnv<'_>, buf: &JByteBuffer<'_>) -> Option<&'a mut [i32]> {
    let ptr = env.get_direct_buffer_address(buf).ok()?;
    let cap_bytes = env.get_direct_buffer_capacity(buf).ok()?;
    if ptr.is_null() || cap_bytes < 4 {
        return None;
    }
    Some(slice::from_raw_parts_mut(ptr as *mut i32, cap_bytes / 4))
}

#[no_mangle]
pub extern "system" fn Java_com_voxelhorizon_nativegame_RustEngine_nativeInit<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    seed: jint,
    dim: jint,
) {
    with_world(|w| {
        w.mods.clear();
        w.reset_dim(dim, seed);
    });
}

#[no_mangle]
pub extern "system" fn Java_com_voxelhorizon_nativegame_RustEngine_nativeResetDim<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    dim: jint,
    seed: jint,
) {
    with_world(|w| w.reset_dim(dim, seed));
}

#[no_mangle]
pub extern "system" fn Java_com_voxelhorizon_nativegame_RustEngine_nativeClearMods<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
    with_world(|w| w.mods.clear());
}

#[no_mangle]
pub extern "system" fn Java_com_voxelhorizon_nativegame_RustEngine_nativePutMod<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    dim: jint,
    x: jint,
    y: jint,
    z: jint,
    id: jint,
) {
    with_world(|w| {
        w.mods.insert((dim, x, y, z), (id & 0xFF) as u8);
    });
}

#[no_mangle]
pub extern "system" fn Java_com_voxelhorizon_nativegame_RustEngine_nativeRegisterModBlock<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    id: jint,
    tile_idx: jint,
    color_hex: jint,
) {
    with_world(|w| w.register_mod_block(id, tile_idx, color_hex));
}

#[no_mangle]
pub extern "system" fn Java_com_voxelhorizon_nativegame_RustEngine_nativeGetBlock<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    x: jint,
    y: jint,
    z: jint,
) -> jint {
    with_world(|w| w.get_block(x, y, z) as jint)
}

#[no_mangle]
pub extern "system" fn Java_com_voxelhorizon_nativegame_RustEngine_nativeSetBlock<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    x: jint,
    y: jint,
    z: jint,
    id: jint,
) {
    with_world(|w| w.set_block(x, y, z, (id & 0xFF) as u8));
}

#[no_mangle]
pub extern "system" fn Java_com_voxelhorizon_nativegame_RustEngine_nativeGetSurfaceZ<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    x: jint,
    y: jint,
) -> jint {
    with_world(|w| w.get_surface_z(x, y))
}

#[no_mangle]
pub extern "system" fn Java_com_voxelhorizon_nativegame_RustEngine_nativeGetTopBlockZ<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    x: jint,
    y: jint,
) -> jint {
    with_world(|w| w.get_top_block_z(x, y))
}

#[no_mangle]
pub extern "system" fn Java_com_voxelhorizon_nativegame_RustEngine_nativeGetGroundZ<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    x: jint,
    y: jint,
    cur_z: jfloat,
) -> jfloat {
    with_world(|w| w.get_ground_z(x, y, cur_z))
}

#[no_mangle]
pub extern "system" fn Java_com_voxelhorizon_nativegame_RustEngine_nativeHasRoofAbove<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    x: jint,
    y: jint,
    pz: jint,
) -> jboolean {
    if with_world(|w| w.has_roof_above(x, y, pz)) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_voxelhorizon_nativegame_RustEngine_nativeApplySeasonalStep<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    season_idx: jint,
    season_progress: jfloat,
    px: jint,
    py: jint,
    rng_seed: jint,
) {
    with_world(|w| w.apply_seasonal_step(season_idx, season_progress, px, py, rng_seed));
}

#[no_mangle]
pub extern "system" fn Java_com_voxelhorizon_nativegame_RustEngine_nativePickIsoBlock<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    wx_s: jfloat,
    wy_s: jfloat,
    pz: jint,
    slice_z: jint,
    out_pick: JByteBuffer<'local>,
) {
    let (gx, gy, gz, id) = with_world(|w| w.pick_iso_block(wx_s, wy_s, pz, slice_z));
    unsafe {
        if let Some(out) = buf_as_i32_mut(&env, &out_pick) {
            if out.len() >= 4 {
                out[0] = gx;
                out[1] = gy;
                out[2] = gz;
                out[3] = id as i32;
            }
        }
    }
}

#[no_mangle]
#[allow(clippy::too_many_arguments)]
pub extern "system" fn Java_com_voxelhorizon_nativegame_RustEngine_nativeBuildIsoFrame<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    elapsed: jfloat,
    px: jfloat,
    py: jfloat,
    pz: jfloat,
    cam_x: jfloat,
    cam_y: jfloat,
    half_w: jfloat,
    half_h: jfloat,
    grass_r: jfloat,
    grass_g: jfloat,
    grass_b: jfloat,
    is_cutaway: jboolean,
    slice_z: jint,
    miner_vision: jboolean,
    out_verts: JByteBuffer<'local>,
    out_diag: JByteBuffer<'local>,
    out_props: JByteBuffer<'local>,
    out_lights: JByteBuffer<'local>,
    out_ores: JByteBuffer<'local>,
    out_counts: JByteBuffer<'local>,
) {
    unsafe {
        let Some(verts) = buf_as_f32_mut(&env, &out_verts) else { return; };
        let Some(diag) = buf_as_i32_mut(&env, &out_diag) else { return; };
        let Some(props) = buf_as_f32_mut(&env, &out_props) else { return; };
        let Some(lights) = buf_as_f32_mut(&env, &out_lights) else { return; };
        let Some(ores) = buf_as_f32_mut(&env, &out_ores) else { return; };
        let Some(counts) = buf_as_i32_mut(&env, &out_counts) else { return; };

        let res = with_world(|w| {
            build_iso_frame(
                w,
                elapsed,
                px, py, pz,
                cam_x, cam_y, half_w, half_h,
                grass_r, grass_g, grass_b,
                is_cutaway != 0,
                slice_z,
                miner_vision != 0,
                verts, diag, props, lights, ores,
            )
        });

        if counts.len() >= 6 {
            counts[0] = res.vert_floats;
            counts[1] = res.diag_count;
            counts[2] = res.min_sum;
            counts[3] = res.prop_count;
            counts[4] = res.light_count;
            counts[5] = res.ore_count;
        }
    }
}
