use crate::world::{chunk_coord, cidx, IsoChunk, RustWorld, MAX_Z_I32, TH, TW, ZH};

const ATLAS_COLS: f32 = 8.0;
const ATLAS_ROWS: f32 = 8.0;
const UV_EPS: f32 = 0.0015;
const WHITE_TILE_IDX: i16 = 63; // Dedicated solid white 64x64 tile in atlas slot 63 -> ZERO texture switches!

#[inline(always)]
pub fn pack_color(r: f32, g: f32, b: f32, a: f32) -> f32 {
    let ri = (r.clamp(0.0, 1.0) * 255.0) as u32;
    let gi = (g.clamp(0.0, 1.0) * 255.0) as u32;
    let bi = (b.clamp(0.0, 1.0) * 255.0) as u32;
    let ai = (a.clamp(0.0, 1.0) * 255.0) as u32;
    let bits = ((ai << 24) | (bi << 16) | (gi << 8) | ri) & 0xfeff_ffff;
    f32::from_bits(bits)
}

#[inline(always)]
fn tile_uv(tid: i16) -> (f32, f32, f32, f32) {
    let col = (tid % 8) as f32;
    let row = (tid / 8) as f32;
    let su = 1.0 / ATLAS_COLS;
    let sv = 1.0 / ATLAS_ROWS;
    (
        col * su + UV_EPS,
        row * sv + UV_EPS,
        (col + 1.0) * su - UV_EPS,
        (row + 1.0) * sv - UV_EPS,
    )
}

#[inline(always)]
#[allow(clippy::too_many_arguments)]
fn push_quad(
    out: &mut [f32],
    cursor: &mut usize,
    x0: f32, y0: f32,
    x1: f32, y1: f32,
    x2: f32, y2: f32,
    x3: f32, y3: f32,
    col_bits: f32,
    u0: f32, v0: f32, u1: f32, v1: f32,
) {
    let c = *cursor;
    if c + 20 > out.len() {
        return;
    }
    out[c]      = x0; out[c + 1]  = y0; out[c + 2]  = col_bits; out[c + 3]  = u0; out[c + 4]  = v1;
    out[c + 5]  = x1; out[c + 6]  = y1; out[c + 7]  = col_bits; out[c + 8]  = u0; out[c + 9]  = v0;
    out[c + 10] = x2; out[c + 11] = y2; out[c + 12] = col_bits; out[c + 13] = u1; out[c + 14] = v0;
    out[c + 15] = x3; out[c + 16] = y3; out[c + 17] = col_bits; out[c + 18] = u1; out[c + 19] = v1;
    *cursor = c + 20;
}

pub struct FrameBuildCounts {
    pub vert_floats: i32,
    pub diag_count: i32,
    pub min_sum: i32,
    pub prop_count: i32,
    pub light_count: i32,
    pub ore_count: i32,
}

#[allow(clippy::too_many_arguments)]
pub fn build_iso_frame(
    world: &mut RustWorld,
    elapsed: f32,
    px: f32,
    py: f32,
    pz: f32,
    cam_x: f32,
    cam_y: f32,
    half_w: f32,
    half_h: f32,
    grass_r: f32,
    grass_g: f32,
    grass_b: f32,
    is_cutaway: bool,
    slice_z: i32,
    miner_vision: bool,
    out_verts: &mut [f32],
    out_diag: &mut [i32],
    out_props: &mut [f32],
    out_lights: &mut [f32],
    out_ores: &mut [f32],
) -> FrameBuildCounts {
    let p_gx = px.floor() as i32;
    let p_gy = py.floor() as i32;
    let p_z = pz.floor() as i32;
    let view_r: i32 = 13;

    // 1. Pre-ensure 5x5 local chunk grid around player -> ZERO HashMap lookups inside inner loop!
    let (base_cx, _) = chunk_coord(p_gx);
    let (base_cy, _) = chunk_coord(p_gy);
    for dy in -2i32..=2 {
        for dx in -2i32..=2 {
            world.ensure_chunk(base_cx + dx, base_cy + dy);
        }
    }

    // Build flat 5x5 raw pointer cache into already-ensured chunks (world is NOT mutated below!)
    let mut grid: [Option<*const IsoChunk>; 25] = [None; 25];
    for dy in -2i32..=2 {
        for dx in -2i32..=2 {
            let idx = ((dy + 2) * 5 + (dx + 2)) as usize;
            if let Some(ch) = world.chunks.get(&(base_cx + dx, base_cy + dy)) {
                grid[idx] = Some(&**ch as *const IsoChunk);
            }
        }
    }

    let get_fast = |wx: i32, wy: i32, wz: i32| -> u8 {
        if wz < 0 || wz >= MAX_Z_I32 {
            return 0;
        }
        let (cx, lx) = chunk_coord(wx);
        let (cy, ly) = chunk_coord(wy);
        let ddx = cx - base_cx + 2;
        let ddy = cy - base_cy + 2;
        if !(0..5).contains(&ddx) || !(0..5).contains(&ddy) {
            return 0;
        }
        if let Some(ptr) = grid[(ddy * 5 + ddx) as usize] {
            // SAFETY: chunks HashMap is not modified during this function
            unsafe { (*ptr).blocks[cidx(lx, ly, wz as usize)] }
        } else {
            0
        }
    };

    let z_min = 0.max(p_z - 6);
    let z_max = slice_z.clamp(1, MAX_Z_I32 - 1);
    let min_sum = (p_gx - view_r) + (p_gy - view_r);
    let max_sum = (p_gx + view_r) + (p_gy + view_r);
    let diag_count = (max_sum - min_sum + 1).clamp(0, (out_diag.len() / 2) as i32);

    let cut_void_bits = pack_color(0.055, 0.045, 0.09, 1.0);
    let (wu0, wv0, wu1, wv1) = tile_uv(WHITE_TILE_IDX);
    let lut = &world.lut;

    let mut vert_cur: usize = 0;
    let mut prop_count: usize = 0;
    let mut light_count: usize = 0;
    let mut ore_count: usize = 0;

    let max_props = out_props.len() / 8;
    let max_lights = out_lights.len() / 4;
    let max_ores = out_ores.len() / 3;

    // 2. Strict Painter's Algorithm diagonal pass (back-to-front)
    for d in 0..(diag_count as usize) {
        let sum = min_sum + d as i32;
        let diag_start = vert_cur;

        let gx_lo = (p_gx - view_r).max(sum - (p_gy + view_r));
        let gx_hi = (p_gx + view_r).min(sum - (p_gy - view_r));

        for gx in gx_lo..=gx_hi {
            let gy = sum - gx;
            let sx = (gx - gy) as f32 * TW;
            let base_sy = -((gx + gy) as f32) * TH;
            if (sx - cam_x).abs() > half_w || (base_sy + p_z as f32 * ZH - cam_y).abs() > half_h {
                continue;
            }

            let occludes_hero = !is_cutaway
                && (gx + gy > p_gx + p_gy)
                && (gx + gy <= p_gx + p_gy + 4)
                && ((gx - gy) - (p_gx - p_gy)).abs() <= 2;

            for gz in z_min..=z_max {
                let b_id = get_fast(gx, gy, gz);
                if b_id == 0 {
                    continue;
                }
                let def = &lut[b_id as usize];
                let sy = base_sy + gz as f32 * ZH;

                if miner_vision && def.is_ore && (gx - p_gx).abs() <= 8 && (gy - p_gy).abs() <= 8 {
                    if ore_count < max_ores {
                        let oi = ore_count * 3;
                        out_ores[oi] = sx;
                        out_ores[oi + 1] = sy + ZH * 0.5;
                        out_ores[oi + 2] = def.color_hex as f32;
                        ore_count += 1;
                    }
                }

                if def.is_prop {
                    if prop_count < max_props {
                        let pi = prop_count * 8;
                        let a = if occludes_hero && gz >= p_z { 0.38 } else { 1.0 };
                        out_props[pi] = d as f32;
                        out_props[pi + 1] = sx;
                        out_props[pi + 2] = sy;
                        out_props[pi + 3] = b_id as f32;
                        out_props[pi + 4] = gx as f32;
                        out_props[pi + 5] = gy as f32;
                        out_props[pi + 6] = gz as f32;
                        out_props[pi + 7] = a;
                        prop_count += 1;
                    }
                    if def.light_radius > 0.0 && light_count < max_lights {
                        let li = light_count * 4;
                        out_lights[li] = sx;
                        out_lights[li + 1] = sy + 24.0;
                        out_lights[li + 2] = def.light_radius;
                        out_lights[li + 3] = def.light_color as f32;
                        light_count += 1;
                    }
                    continue;
                }

                let up_id = get_fast(gx, gy, gz + 1);
                let left_id = get_fast(gx, gy + 1, gz);
                let right_id = get_fast(gx + 1, gy, gz);

                let is_cut_cap = is_cutaway && gz == z_max && up_id > 0 && lut[up_id as usize].solid;
                let draw_top = is_cut_cap || up_id == 0 || (lut[up_id as usize].transparent && up_id != b_id);
                let draw_left = left_id == 0 || (lut[left_id as usize].transparent && left_id != b_id);
                let draw_right = right_id == 0 || (lut[right_id as usize].transparent && right_id != b_id);
                if !draw_top && !draw_left && !draw_right {
                    continue;
                }

                let h_block = if def.is_liquid { ZH * 0.78 } else { ZH };
                let sy_top = sy + h_block;
                let alpha = if occludes_hero && gz >= p_z {
                    0.34
                } else if def.is_liquid && !def.is_lava {
                    0.88
                } else {
                    1.0
                };

                let liq_wave = if def.is_liquid {
                    0.90 + (elapsed * 3.2 + gx as f32 * 0.7 + gy as f32 * 0.7).sin() * 0.10
                } else {
                    1.0
                };

                let top_col = if is_cut_cap {
                    cut_void_bits
                } else if def.is_foliage {
                    pack_color(grass_r, grass_g, grass_b, alpha)
                } else if def.is_liquid {
                    pack_color(liq_wave, liq_wave, 1.0, alpha)
                } else {
                    pack_color(1.0, 1.0, 1.0, alpha)
                };

                let left_col = if def.is_foliage && b_id == 6 {
                    pack_color(grass_r * 0.86, grass_g * 0.86, grass_b * 0.90, alpha)
                } else if def.is_liquid {
                    pack_color(0.75, 0.80, 0.95, alpha)
                } else {
                    pack_color(0.84, 0.85, 0.90, alpha)
                };

                let right_col = if def.is_foliage && b_id == 6 {
                    pack_color(grass_r * 0.66, grass_g * 0.66, grass_b * 0.74, alpha)
                } else if def.is_liquid {
                    pack_color(0.58, 0.65, 0.85, alpha)
                } else {
                    pack_color(0.64, 0.65, 0.72, alpha)
                };

                if draw_left && def.side_tile >= 0 {
                    let (u0, v0, u1, v1) = tile_uv(def.side_tile);
                    push_quad(
                        out_verts, &mut vert_cur,
                        sx - TW, sy, sx - TW, sy_top, sx, sy_top - TH, sx, sy - TH,
                        left_col, u0, v0, u1, v1,
                    );
                }
                if draw_right && def.side_tile >= 0 {
                    let (u0, v0, u1, v1) = tile_uv(def.side_tile);
                    push_quad(
                        out_verts, &mut vert_cur,
                        sx, sy - TH, sx, sy_top - TH, sx + TW, sy_top, sx + TW, sy,
                        right_col, u0, v0, u1, v1,
                    );
                }
                if draw_top {
                    if is_cut_cap {
                        push_quad(
                            out_verts, &mut vert_cur,
                            sx - TW, sy_top, sx, sy_top + TH, sx + TW, sy_top, sx, sy_top - TH,
                            cut_void_bits, wu0, wv0, wu1, wv1,
                        );
                    } else if def.top_tile >= 0 {
                        let (u0, v0, u1, v1) = tile_uv(def.top_tile);
                        push_quad(
                            out_verts, &mut vert_cur,
                            sx - TW, sy_top, sx, sy_top + TH, sx + TW, sy_top, sx, sy_top - TH,
                            top_col, u0, v0, u1, v1,
                        );
                    }
                }

                if def.light_radius > 0.0 && light_count < max_lights {
                    let li = light_count * 4;
                    out_lights[li] = sx;
                    out_lights[li + 1] = sy_top;
                    out_lights[li + 2] = def.light_radius;
                    out_lights[li + 3] = def.light_color as f32;
                    light_count += 1;
                }
            }
        }

        out_diag[d * 2] = diag_start as i32;
        out_diag[d * 2 + 1] = (vert_cur - diag_start) as i32;
    }

    FrameBuildCounts {
        vert_floats: vert_cur as i32,
        diag_count,
        min_sum,
        prop_count: prop_count as i32,
        light_count: light_count as i32,
        ore_count: ore_count as i32,
    }
}
