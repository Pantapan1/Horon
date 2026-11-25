use std::collections::HashMap;
use crate::noise::PerlinNoise;

pub const CS: usize = 16;
pub const CS_I32: i32 = 16;
pub const MAX_Z: usize = 250;
pub const MAX_Z_I32: i32 = 250;
pub const SEA_Z: i32 = 138;
pub const CHUNK_LEN: usize = CS * CS * MAX_Z;

pub const TW: f32 = 32.0;
pub const TH: f32 = 16.0;
pub const ZH: f32 = 28.0;

#[derive(Clone, Copy)]
pub struct BlockMeta {
    pub solid: bool,
    pub transparent: bool,
    pub is_prop: bool,
    pub is_climbable: bool,
    pub is_foliage: bool,
    pub is_liquid: bool,
    pub is_lava: bool,
    pub is_ore: bool,
    pub top_tile: i16,
    pub side_tile: i16,
    pub light_radius: f32,
    pub light_color: i32,
    pub color_hex: i32,
}

impl BlockMeta {
    pub const fn empty() -> Self {
        Self {
            solid: false,
            transparent: true,
            is_prop: false,
            is_climbable: false,
            is_foliage: false,
            is_liquid: false,
            is_lava: false,
            is_ore: false,
            top_tile: -1,
            side_tile: -1,
            light_radius: 0.0,
            light_color: 0xFFB854,
            color_hex: 0xFFFFFF,
        }
    }
}

pub fn build_block_lut() -> [BlockMeta; 256] {
    let mut lut = [BlockMeta::empty(); 256];
    let mut set = |id: usize,
                   solid: bool,
                   trans: bool,
                   prop: bool,
                   climb: bool,
                   foliage: bool,
                   liq: bool,
                   lava: bool,
                   ore: bool,
                   top: i16,
                   side: i16,
                   lrad: f32,
                   lcol: i32,
                   chex: i32| {
        lut[id] = BlockMeta {
            solid,
            transparent: trans,
            is_prop: prop,
            is_climbable: climb,
            is_foliage: foliage,
            is_liquid: liq,
            is_lava: lava,
            is_ore: ore,
            top_tile: top,
            side_tile: side,
            light_radius: lrad,
            light_color: lcol,
            color_hex: chex,
        };
    };

    // Core blocks (1..39)
    set(1,  true,  false, false, false, true,  false, false, false,  0,  1,   0.0, 0,        0x56B846);
    set(2,  true,  false, false, false, false, false, false, false,  2,  2,   0.0, 0,        0x865C3A);
    set(3,  true,  false, false, false, false, false, false, false, 16, 16,   0.0, 0,        0x808690);
    set(4,  true,  false, false, false, false, false, false, false,  3,  3,   0.0, 0,        0x747A84);
    set(5,  true,  false, false, false, false, false, false, false,  4,  5,   0.0, 0,        0x6B4A2E);
    set(6,  true,  true,  false, false, true,  false, false, false, 22, 22,   0.0, 0,        0x3B983A);
    set(7,  true,  false, false, false, false, false, false, false,  4,  4,   0.0, 0,        0xB88A52);
    set(8,  true,  false, false, false, false, false, false, false, 24, 24,   0.0, 0,        0xE6D696);
    set(9,  false, true,  false, false, false, true,  false, false, 11, 11,   0.0, 0,        0x2682E8);
    set(10, true,  false, false, false, false, false, false, false,  7,  7,   0.0, 0,        0x28282E);
    set(11, true,  false, false, false, false, false, false, true,  17, 17,   0.0, 0,        0x3A3D45);
    set(12, true,  false, false, false, false, false, false, true,  18, 18,   0.0, 0,        0xC8A890);
    set(13, true,  false, false, false, false, false, false, true,  19, 19,   0.0, 0,        0xF5C836);
    set(14, true,  false, false, false, false, false, false, true,   6,  6, 130.0, 0x42EAFA, 0x42EAFA);
    set(15, true,  true,  false, false, false, false, false, false, 29, 29,   0.0, 0,        0xC8EEFF);
    set(16, true,  false, false, false, false, false, false, false,  8,  8,   0.0, 0,        0xA44B3A);
    set(17, true,  false, false, false, false, false, false, false, 13,  4,   0.0, 0,        0xB47844);
    set(18, true,  false, false, false, false, false, false, false, 51, 15, 210.0, 0xFF8832, 0x6E727A);
    set(19, true,  false, false, false, false, false, false, false, 26, 48,   0.0, 0,        0xF0F6FF);
    set(20, true,  true,  false, false, false, false, false, false, 27, 27,   0.0, 0,        0x8ED0FA);
    set(21, true,  false, false, false, false, false, false, false, 28, 28,   0.0, 0,        0x348E38);
    set(22, true,  false, false, false, false, false, false, false,  7,  7,   0.0, 0,        0x2E1848);
    set(23, true,  false, false, false, false, false, false, false, 10, 10, 290.0, 0xFFE27A, 0xF8D466);
    set(24, true,  false, false, false, false, false, false, false,  4, 30,   0.0, 0,        0xA87C4C);
    set(26, true,  false, false, false, false, false, false, false, 31, 31,   0.0, 0,        0xCC3430);
    set(27, true,  false, false, false, false, false, false, false,  4, 23,   0.0, 0,        0xDCD8CE);
    set(28, true,  false, false, false, false, false, false, false, 25, 25,   0.0, 0,        0xDEC88C);
    set(29, false, true,  false, false, false, true,  true,  false, 12, 12, 250.0, 0xFF6818, 0xF86218);
    set(30, true,  false, false, false, false, false, false, false,  9,  9,   0.0, 0,        0x7E2E30);
    set(31, true,  false, false, false, false, false, false, false, 50, 50,   0.0, 0,        0x544236);
    set(32, true,  false, false, false, false, false, false, false, 49, 49,   0.0, 0,        0x3E1E24);
    set(33, true,  false, false, false, false, false, false, true,  21, 21,   0.0, 0,        0xD8C6C0);
    set(34, false, true,  true,  false, false, false, false, false,  7,  7, 260.0, 0xB842FF, 0x9C28F0);
    set(35, true,  true,  true,  false, false, false, false, false,  4, 14,   0.0, 0,        0xC68232);
    set(36, true,  false, false, false, false, false, false, true,  20, 20, 120.0, 0x34E86C, 0x2EE86A);
    set(37, false, true,  true,  false, false, false, false, false, -1, -1, 280.0, 0xFFB844, 0xFFB834);
    set(38, true,  true,  true,  false, false, false, false, false, -1, -1,   0.0, 0,        0xD82838);
    set(39, false, true,  true,  false, false, false, false, false, -1, -1,   0.0, 0,        0x5C5042);

    // Expansion blocks (40..55)
    set(40, true,  false, false, false, false, false, false, false, 32, 32,   0.0, 0,        0x3A3E52);
    set(41, true,  false, false, false, false, false, false, false, 33, 33,   0.0, 0,        0x568A4C);
    set(42, true,  false, false, false, false, false, false, false, 34,  1,   0.0, 0,        0x4EA8F2);
    set(43, true,  false, false, false, false, false, false, false, 35, 35,   0.0, 0,        0xE8F4FF);
    set(44, true,  true,  false, false, false, false, false, false, 36, 36,   0.0, 0,        0xF8A6CC);
    set(45, true,  false, false, false, false, false, false, false,  4, 37,   0.0, 0,        0x7A3844);
    set(46, true,  false, false, false, false, false, false, false,  4, 38,   0.0, 0,        0x4A3426);
    set(47, true,  false, false, false, false, false, false, false, 39, 39,   0.0, 0,        0x9CA6BC);
    set(48, true,  false, false, false, false, false, false, true,  40, 40,   0.0, 0,        0xD8723E);
    set(49, true,  false, false, false, false, false, false, true,  41, 41, 160.0, 0xB856FF, 0xA846F4);
    set(50, true,  false, false, false, false, false, false, true,  42, 42, 160.0, 0xFF3A5C, 0xE82848);
    set(51, true,  false, false, false, false, false, false, false, 43, 43,   0.0, 0,        0x8A909C);
    set(52, true,  false, false, false, false, false, false, false,  4, 44,   0.0, 0,        0x9C6838);
    set(53, true,  false, false, false, false, false, false, false, 45, 45,   0.0, 0,        0xDE7418);
    set(54, false, true,  false, false, false, false, false, false, 46, 46,   0.0, 0,        0xE6C03C);
    set(55, true,  false, false, false, false, false, false, false, 47, 47,   0.0, 0,        0xBA2636);

    // Props (56..71)
    set(56, false, true,  true,  true,  false, false, false, false, -1, -1,   0.0, 0,        0xB47C42);
    set(57, true,  true,  true,  false, false, false, false, false, -1, -1,   0.0, 0,        0x945C2E);
    set(58, false, true,  true,  false, false, false, false, false, -1, -1,   0.0, 0,        0x945C2E);
    set(59, false, true,  true,  false, false, false, false, false, -1, -1, 340.0, 0xFF9832, 0xFF8822);
    set(60, false, true,  true,  false, false, false, false, false, -1, -1, 400.0, 0xFFE688, 0xFFE278);
    set(61, true,  true,  true,  false, false, false, false, false, -1, -1, 250.0, 0x56D8FF, 0x62C8FF);
    set(62, true,  true,  true,  false, false, false, false, false, -1, -1, 230.0, 0x38EE7A, 0x36E872);
    set(63, true,  true,  true,  false, false, false, false, false, -1, -1,   0.0, 0,        0x525866);
    set(64, true,  true,  true,  false, false, false, false, false, -1, -1,   0.0, 0,        0x9E6838);
    set(65, false, true,  true,  false, false, false, false, false, -1, -1,   0.0, 0,        0xB8844A);
    set(66, false, true,  true,  false, false, false, false, false, -1, -1, 150.0, 0x48DCF8, 0x44D4F4);
    set(67, false, true,  true,  false, false, false, false, false, -1, -1,   0.0, 0,        0x348C38);
    set(68, false, true,  true,  false, false, false, false, false, -1, -1,   0.0, 0,        0xFF78A8);
    set(69, false, true,  true,  false, false, false, false, true,  -1, -1, 190.0, 0xC662FF, 0xBC54FF);
    set(70, false, true,  true,  false, false, false, false, true,  -1, -1, 190.0, 0xFF385A, 0xEC284A);
    set(71, true,  true,  true,  false, false, false, false, false, -1, -1,   0.0, 0,        0xC68232);

    lut
}

pub struct IsoChunk {
    pub blocks: Box<[u8; CHUNK_LEN]>,
    pub surface_h: [i16; CS * CS],
}

#[inline(always)]
pub fn cidx(lx: usize, ly: usize, z: usize) -> usize {
    (z * CS + ly) * CS + lx
}

#[inline(always)]
pub fn chunk_coord(w: i32) -> (i32, usize) {
    let c = w.div_euclid(CS_I32);
    let l = w.rem_euclid(CS_I32) as usize;
    (c, l)
}

pub struct RustWorld {
    pub seed: i32,
    pub dim: i32, // 0=overworld, 1=nether, 2=aether
    pub perlin: PerlinNoise,
    pub chunks: HashMap<(i32, i32), Box<IsoChunk>>,
    pub mods: HashMap<(i32, i32, i32, i32), u8>,
    pub lut: [BlockMeta; 256],
}

impl RustWorld {
    pub fn new(seed: i32, dim: i32) -> Self {
        Self {
            seed,
            dim,
            perlin: PerlinNoise::new(Self::seed_for_dim(dim, seed)),
            chunks: HashMap::with_capacity(64),
            mods: HashMap::new(),
            lut: build_block_lut(),
        }
    }

    fn seed_for_dim(dim: i32, s: i32) -> i32 {
        match dim {
            1 => s.wrapping_add(99991),
            2 => s.wrapping_add(44447),
            _ => s,
        }
    }

    pub fn reset_dim(&mut self, new_dim: i32, new_seed: i32) {
        self.chunks.clear();
        self.dim = new_dim;
        self.seed = new_seed;
        self.perlin = PerlinNoise::new(Self::seed_for_dim(new_dim, new_seed));
    }

    pub fn register_mod_block(&mut self, id: i32, tile_idx: i32, color_hex: i32) {
        if (1..256).contains(&id) {
            self.lut[id as usize] = BlockMeta {
                solid: true,
                transparent: false,
                is_prop: false,
                is_climbable: false,
                is_foliage: false,
                is_liquid: false,
                is_lava: false,
                is_ore: false,
                top_tile: tile_idx as i16,
                side_tile: tile_idx as i16,
                light_radius: 0.0,
                light_color: 0,
                color_hex,
            };
        }
    }

    pub fn get_biome(&self, wx: i32, wy: i32) -> i32 {
        if self.dim != 0 {
            return 0;
        }
        let temp = self.perlin.fbm2(wx as f64 * 0.014 + 42.0, wy as f64 * 0.014 + 42.0, 2);
        let moist = self.perlin.fbm2(wx as f64 * 0.015 - 71.0, wy as f64 * 0.015 + 19.0, 2);
        if temp > 0.66 {
            3
        } else if temp < 0.34 {
            4
        } else if moist > 0.64 {
            5
        } else if moist > 0.54 {
            2
        } else if moist > 0.44 {
            1
        } else {
            0
        }
    }

    fn place_voxel_tree(data: &mut [u8; CHUNK_LEN], lx: usize, ly: usize, base_z: usize, bark: u8, leaf: u8) {
        if lx < 2 || lx > CS - 3 || ly < 2 || ly > CS - 3 || base_z + 6 >= MAX_Z {
            return;
        }
        data[cidx(lx, ly, base_z)] = bark;
        data[cidx(lx, ly, base_z + 1)] = bark;
        data[cidx(lx, ly, base_z + 2)] = bark;
        for dx in -1i32..=1 {
            for dy in -1i32..=1 {
                if dx == 0 && dy == 0 {
                    continue;
                }
                data[cidx((lx as i32 + dx) as usize, (ly as i32 + dy) as usize, base_z + 2)] = leaf;
            }
        }
        for dx in -1i32..=1 {
            for dy in -1i32..=1 {
                if dx.abs() == 1 && dy.abs() == 1 {
                    continue;
                }
                data[cidx((lx as i32 + dx) as usize, (ly as i32 + dy) as usize, base_z + 3)] = leaf;
            }
        }
        data[cidx(lx, ly, base_z + 4)] = leaf;
    }

    fn generate_chunk(&self, cx: i32, cy: i32) -> Box<IsoChunk> {
        let mut data: Box<[u8; CHUNK_LEN]> = vec![0u8; CHUNK_LEN].into_boxed_slice().try_into().ok().unwrap();
        let mut surf_h = [140i16; CS * CS];
        let base_x = cx * CS_I32;
        let base_y = cy * CS_I32;

        if self.dim == 1 {
            // NETHER
            for ly in 0..CS {
                for lx in 0..CS {
                    let wx = base_x + lx as i32;
                    let wy = base_y + ly as i32;
                    let n = self.perlin.fbm2(wx as f64 * 0.048, wy as f64 * 0.048, 3);
                    let dist0 = ((wx * wx + wy * wy) as f64).sqrt();
                    let h = if dist0 < 5.0 { 140 } else { (136 + (n * 10.0) as i32).clamp(135, 148) };
                    surf_h[ly * CS + lx] = h as i16;
                    data[cidx(lx, ly, 0)] = 10;
                    for z in 1..=(h as usize) {
                        let r = PerlinNoise::hash3(wx, wy, z as i32);
                        data[cidx(lx, ly, z)] = if z == h as usize && n > 0.64 {
                            31
                        } else if r > 0.96 {
                            23
                        } else if r > 0.90 {
                            33
                        } else {
                            30
                        };
                    }
                    if h < SEA_Z {
                        for z in (h + 1)..=SEA_Z {
                            data[cidx(lx, ly, z as usize)] = 29;
                        }
                    }
                }
            }
            if cx == 0 && cy == 0 {
                data[cidx(2, 2, 141)] = 34;
            }
        } else if self.dim == 2 {
            // AETHER
            for ly in 0..CS {
                for lx in 0..CS {
                    let wx = base_x + lx as i32;
                    let wy = base_y + ly as i32;
                    let mask = self.perlin.fbm2(wx as f64 * 0.045, wy as f64 * 0.045, 3);
                    let dist0 = ((wx * wx + wy * wy) as f64).sqrt();
                    if dist0 < 7.0 || mask > 0.45 {
                        let top_z = if dist0 < 7.0 {
                            142
                        } else {
                            (140 + ((mask - 0.45) * 14.0) as i32).clamp(140, 152)
                        };
                        surf_h[ly * CS + lx] = top_z as i16;
                        for z in (top_z - 4)..=(top_z - 2) {
                            data[cidx(lx, ly, z as usize)] = 3;
                        }
                        data[cidx(lx, ly, (top_z - 1) as usize)] = 2;
                        data[cidx(lx, ly, top_z as usize)] = 1;
                        let r = PerlinNoise::hash2(wx.wrapping_mul(19), wy.wrapping_mul(23));
                        if dist0 > 3.5 && r > 0.962 {
                            Self::place_voxel_tree(&mut data, lx, ly, (top_z + 1) as usize, 27, 6);
                        } else if dist0 > 3.5 && r > 0.945 {
                            data[cidx(lx, ly, (top_z + 1) as usize)] = 23;
                        }
                    } else {
                        surf_h[ly * CS + lx] = 135;
                    }
                }
            }
        } else {
            // OVERWORLD: 250 LAYERS + 3D CAVES + DEEPSLATE + MINES + CAMP + DUNGEONS
            const GZ_STEPS: usize = 18;
            let mut cave_grid = [0.0f32; 5 * 5 * GZ_STEPS];
            for gz in 0..GZ_STEPS {
                let wz = (gz * 8) as f64;
                for gy in 0..5 {
                    for gx in 0..5 {
                        let wx = (base_x + (gx as i32) * 4) as f64;
                        let wy = (base_y + (gy as i32) * 4) as f64;
                        cave_grid[(gz * 5 + gy) * 5 + gx] =
                            self.perlin.noise3(wx * 0.055, wy * 0.055, wz * 0.065) as f32;
                    }
                }
            }
            let sample_cave = |lx: usize, ly: usize, z: usize| -> f32 {
                let fx = lx as f32 * 0.25;
                let fy = ly as f32 * 0.25;
                let fz = (z as f32 * 0.125).clamp(0.0, (GZ_STEPS as f32) - 1.001);
                let x0 = fx as usize;
                let y0 = fy as usize;
                let z0 = fz as usize;
                let tx = fx - x0 as f32;
                let ty = fy - y0 as f32;
                let tz = fz - z0 as f32;
                let g = |x: usize, y: usize, zz: usize| cave_grid[(zz * 5 + y) * 5 + x];
                let c00 = g(x0, y0, z0) + (g(x0 + 1, y0, z0) - g(x0, y0, z0)) * tx;
                let c10 = g(x0, y0 + 1, z0) + (g(x0 + 1, y0 + 1, z0) - g(x0, y0 + 1, z0)) * tx;
                let c01 = g(x0, y0, z0 + 1) + (g(x0 + 1, y0, z0 + 1) - g(x0, y0, z0 + 1)) * tx;
                let c11 = g(x0, y0 + 1, z0 + 1) + (g(x0 + 1, y0 + 1, z0 + 1) - g(x0, y0 + 1, z0 + 1)) * tx;
                let c0 = c00 + (c10 - c00) * ty;
                let c1 = c01 + (c11 - c01) * ty;
                c0 + (c1 - c0) * tz
            };

            let u_biome = (cx.wrapping_mul(31337) ^ cy.wrapping_mul(7919)).rem_euclid(3);

            for ly in 0..CS {
                for lx in 0..CS {
                    let wx = base_x + lx as i32;
                    let wy = base_y + ly as i32;
                    let biome = self.get_biome(wx, wy);
                    let cont = self.perlin.fbm2(wx as f64 * 0.028, wy as f64 * 0.028, 3);
                    let ridge = self.perlin.fbm2(wx as f64 * 0.016 + 110.0, wy as f64 * 0.016 - 90.0, 2);
                    let dist0 = ((wx * wx + wy * wy) as f64).sqrt();

                    let h = if dist0 < 6.5 {
                        141
                    } else {
                        let mut hh = 136.0 + cont * 12.0;
                        if ridge > 0.62 {
                            hh += (ridge - 0.62) * 42.0;
                        }
                        (hh as i32).clamp(135, 165)
                    };
                    surf_h[ly * CS + lx] = h as i16;
                    data[cidx(lx, ly, 0)] = 10;

                    let h_u = h as usize;
                    for z in 1..(h_u - 2) {
                        let is_deep = z < 56;
                        let cave_val = if (4..=134).contains(&z) { sample_cave(lx, ly, z) } else { 0.0 };
                        if cave_val > 0.665 {
                            data[cidx(lx, ly, z)] = if z <= 8 { 29 } else { 0 };
                        } else {
                            let r = PerlinNoise::hash3(wx, wy, z as i32);
                            data[cidx(lx, ly, z)] = if is_deep && z < 26 && r > 0.985 {
                                14
                            } else if is_deep && z < 34 && r > 0.976 {
                                36
                            } else if is_deep && u_biome == 2 && r > 0.955 {
                                if ((wx + z as i32) & 1) == 0 { 49 } else { 50 }
                            } else if is_deep && r > 0.960 {
                                13
                            } else if is_deep && r > 0.935 {
                                12
                            } else if is_deep {
                                40
                            } else if z < 95 && r > 0.978 {
                                13
                            } else if r > 0.952 {
                                12
                            } else if r > 0.928 {
                                48
                            } else if r > 0.900 {
                                11
                            } else if u_biome == 1 && r > 0.885 {
                                41
                            } else {
                                3
                            };
                        }
                    }

                    data[cidx(lx, ly, h_u - 2)] = if biome == 3 { 28 } else if h <= SEA_Z { 47 } else { 2 };
                    data[cidx(lx, ly, h_u - 1)] = if biome == 3 { 28 } else { 2 };
                    data[cidx(lx, ly, h_u)] = if h <= SEA_Z || biome == 3 {
                        8
                    } else if biome == 4 || h >= 154 {
                        19
                    } else {
                        1
                    };
                    if h < SEA_Z {
                        let fluid = if biome == 4 { 20 } else { 9 };
                        for z in (h + 1)..=SEA_Z {
                            data[cidx(lx, ly, z as usize)] = fluid;
                        }
                    }
                }
            }

            // Underground glowing mushrooms & crystal clusters
            for ly in 1..(CS - 1) {
                for lx in 1..(CS - 1) {
                    let r = PerlinNoise::hash3(base_x + lx as i32, base_y + ly as i32, 777);
                    if r < 0.92 {
                        continue;
                    }
                    let mut z = 12usize;
                    while z <= 126 {
                        if data[cidx(lx, ly, z)] == 0 && data[cidx(lx, ly, z - 1)] > 0 && data[cidx(lx, ly, z - 1)] != 29 {
                            data[cidx(lx, ly, z)] = if z < 56 { if r > 0.96 { 70 } else { 69 } } else { 66 };
                            break;
                        }
                        z += 4;
                    }
                }
            }

            // Abandoned mineshaft at Z=82
            let m_hash = (cx.wrapping_mul(49999) ^ cy.wrapping_mul(27773)).rem_euclid(100);
            if m_hash < 25 {
                let mz = 82usize;
                for lx in 2..=13 {
                    for ly in 7..=9 {
                        data[cidx(lx, ly, mz - 1)] = 7;
                        data[cidx(lx, ly, mz)] = 0;
                        data[cidx(lx, ly, mz + 1)] = 0;
                        data[cidx(lx, ly, mz + 2)] = 0;
                    }
                }
                for &px in &[4usize, 10usize] {
                    data[cidx(px, 7, mz)] = 5;
                    data[cidx(px, 7, mz + 1)] = 5;
                    data[cidx(px, 9, mz)] = 5;
                    data[cidx(px, 9, mz + 1)] = 5;
                    data[cidx(px, 8, mz + 2)] = 7;
                }
                data[cidx(7, 7, mz)] = 37;
                data[cidx(7, 9, mz)] = if m_hash % 3 == 0 { 71 } else { 35 };
            }

            // Trees, bushes, flowers, pumpkins
            for ly in 2..(CS - 2) {
                for lx in 2..(CS - 2) {
                    let wx = base_x + lx as i32;
                    let wy = base_y + ly as i32;
                    if ((wx * wx + wy * wy) as f64).sqrt() < 5.5 {
                        continue;
                    }
                    let top_z = surf_h[ly * CS + lx] as usize;
                    let top_id = data[cidx(lx, ly, top_z)];
                    let biome = self.get_biome(wx, wy);
                    let r = PerlinNoise::hash2(wx.wrapping_mul(19), wy.wrapping_mul(23));
                    if top_id == 8 && biome == 3 && r > 0.96 && top_z + 2 < MAX_Z {
                        data[cidx(lx, ly, top_z + 1)] = 21;
                        data[cidx(lx, ly, top_z + 2)] = 21;
                    } else if top_id == 1 || top_id == 19 {
                        if r > 0.958 {
                            match biome {
                                5 => Self::place_voxel_tree(&mut data, lx, ly, top_z + 1, 45, 44),
                                4 => Self::place_voxel_tree(&mut data, lx, ly, top_z + 1, 46, 6),
                                2 => Self::place_voxel_tree(&mut data, lx, ly, top_z + 1, 27, 6),
                                _ => Self::place_voxel_tree(&mut data, lx, ly, top_z + 1, 5, 6),
                            }
                        } else if (0.940..=0.950).contains(&r) {
                            data[cidx(lx, ly, top_z + 1)] = 67;
                        } else if (0.925..=0.935).contains(&r) {
                            data[cidx(lx, ly, top_z + 1)] = 68;
                        } else if (0.918..=0.922).contains(&r) {
                            data[cidx(lx, ly, top_z + 1)] = 53;
                        }
                    }
                }
            }

            // Starter camp at (0, 0)
            if cx == 0 && cy == 0 {
                let sz = 142usize;
                data[cidx(3, 1, sz)] = 17;
                data[cidx(1, 3, sz)] = 18;
                data[cidx(1, 4, sz)] = 35;
                data[cidx(2, 2, sz)] = 59;
                data[cidx(4, 3, sz)] = 61;
                data[cidx(5, 1, sz)] = 62;
                data[cidx(2, 4, sz)] = 63;
                data[cidx(3, 3, sz)] = 65;

                for dz in 0..=8usize {
                    data[cidx(4, 4, 141 - dz)] = 56;
                    data[cidx(3, 4, 141 - dz)] = 0;
                }
                for dx in 2..=5usize {
                    for dy in 3..=6usize {
                        data[cidx(dx, dy, 133)] = 0;
                        data[cidx(dx, dy, 134)] = 0;
                    }
                }
                data[cidx(2, 4, 133)] = 37;
                data[cidx(2, 3, 133)] = 12;
                data[cidx(2, 5, 133)] = 48;
            }

            // Surface ruin dungeons
            let d_hash = ((cx.wrapping_mul(73856093) ^ cy.wrapping_mul(19349663)) as i64)
                .wrapping_mul(2654435761)
                .rem_euclid(100);
            if (cx != 0 || cy != 0) && d_hash < 11 {
                let bh = (surf_h[7 * CS + 7] as usize).clamp(139, 158);
                for dx in -2i32..=2 {
                    for dy in -2i32..=2 {
                        let lx = (7 + dx) as usize;
                        let ly = (7 + dy) as usize;
                        data[cidx(lx, ly, bh)] = 41;
                        let is_wall = dx.abs() == 2 || dy.abs() == 2;
                        let is_door = dy == 2 && dx == 0;
                        for z in (bh + 1)..=(bh + 3) {
                            data[cidx(lx, ly, z)] = if is_wall && !is_door {
                                if ((dx + dy + z as i32) & 1) == 0 { 51 } else { 41 }
                            } else {
                                0
                            };
                        }
                    }
                }
                data[cidx(7, 6, bh + 1)] = if d_hash % 2 == 0 { 71 } else { 35 };
                data[cidx(6, 6, bh + 1)] = 60;
            }
        }

        // Apply saved modifications belonging to this chunk
        for (&(mdim, mx, my, mz), &b_id) in &self.mods {
            if mdim == self.dim && mz >= 0 && (mz as usize) < MAX_Z {
                let (mcx, mlx) = chunk_coord(mx);
                let (mcy, mly) = chunk_coord(my);
                if mcx == cx && mcy == cy {
                    data[cidx(mlx, mly, mz as usize)] = b_id;
                }
            }
        }

        Box::new(IsoChunk {
            blocks: data,
            surface_h: surf_h,
        })
    }

    pub fn ensure_chunk(&mut self, cx: i32, cy: i32) {
        if !self.chunks.contains_key(&(cx, cy)) {
            let ch = self.generate_chunk(cx, cy);
            self.chunks.insert((cx, cy), ch);
        }
    }

    #[inline]
    pub fn get_block(&mut self, wx: i32, wy: i32, wz: i32) -> u8 {
        if wz < 0 || wz >= MAX_Z_I32 {
            return 0;
        }
        let (cx, lx) = chunk_coord(wx);
        let (cy, ly) = chunk_coord(wy);
        self.ensure_chunk(cx, cy);
        self.chunks[&(cx, cy)].blocks[cidx(lx, ly, wz as usize)]
    }

    pub fn set_block(&mut self, wx: i32, wy: i32, wz: i32, id: u8) {
        if wz < 1 || wz >= MAX_Z_I32 {
            return;
        }
        let (cx, lx) = chunk_coord(wx);
        let (cy, ly) = chunk_coord(wy);
        self.ensure_chunk(cx, cy);
        self.chunks.get_mut(&(cx, cy)).unwrap().blocks[cidx(lx, ly, wz as usize)] = id;
        self.mods.insert((self.dim, wx, wy, wz), id);
    }

    pub fn get_surface_z(&mut self, wx: i32, wy: i32) -> i32 {
        let (cx, lx) = chunk_coord(wx);
        let (cy, ly) = chunk_coord(wy);
        self.ensure_chunk(cx, cy);
        self.chunks[&(cx, cy)].surface_h[ly * CS + lx] as i32
    }

    pub fn get_top_block_z(&mut self, wx: i32, wy: i32) -> i32 {
        let approx = self.get_surface_z(wx, wy);
        let start = (approx + 24).min(MAX_Z_I32 - 1);
        for z in (0..=start).rev() {
            if self.get_block(wx, wy, z) > 0 {
                return z;
            }
        }
        0
    }

    pub fn has_roof_above(&mut self, wx: i32, wy: i32, pz: i32) -> bool {
        if self.dim == 0 && pz < self.get_surface_z(wx, wy) - 1 {
            return true;
        }
        let top_check = (MAX_Z_I32 - 1).min(pz + 14);
        for z in (pz + 2)..=top_check {
            let id = self.get_block(wx, wy, z);
            if id > 0 && id != 6 && id != 44 && self.lut[id as usize].solid {
                return true;
            }
        }
        false
    }

    pub fn get_ground_z(&mut self, wx: i32, wy: i32, current_z: f32) -> f32 {
        let cz = (current_z + 0.1).floor() as i32;
        let b_cur = self.get_block(wx, wy, cz);
        if self.lut[b_cur as usize].is_climbable {
            return cz as f32;
        }
        let b_up = self.get_block(wx, wy, cz + 1);
        if self.lut[b_up as usize].is_climbable {
            return (cz + 1) as f32;
        }
        let z_hi = (MAX_Z_I32 - 2).min(cz + 1);
        let z_lo = 0.max(cz - 2);
        for z in (z_lo..=z_hi).rev() {
            let b = self.get_block(wx, wy, z);
            let m = self.lut[b as usize];
            if b > 0 && (m.solid || m.is_liquid) {
                let a1 = self.get_block(wx, wy, z + 1);
                let a2 = self.get_block(wx, wy, z + 2);
                if (a1 == 0 || !self.lut[a1 as usize].solid) && (a2 == 0 || !self.lut[a2 as usize].solid) {
                    return (z + 1) as f32;
                }
                return -1.0;
            }
        }
        -1.0
    }

    pub fn apply_seasonal_step(&mut self, season_idx: i32, season_progress: f32, px: i32, py: i32, rng_seed: i32) {
        if self.dim != 0 {
            return;
        }
        #[inline(always)]
        fn lcg(s: &mut u32) -> u32 {
            *s = s.wrapping_mul(1664525).wrapping_add(1013904223);
            *s
        }
        #[inline(always)]
        fn lcg_f(s: &mut u32) -> f32 {
            (lcg(s) as f32) / 4294967295.0
        }
        let mut s = rng_seed as u32;

        let wx = px + ((lcg(&mut s) % 27) as i32) - 13;
        let wy = py + ((lcg(&mut s) % 27) as i32) - 13;
        let sz = self.get_surface_z(wx, wy);
        let top_id = self.get_block(wx, wy, sz);
        let above_id = self.get_block(wx, wy, sz + 1);

        match season_idx {
            3 => {
                // WINTER
                if above_id == 54 && season_progress > 0.25 && lcg_f(&mut s) < 0.15 {
                    self.set_block(wx, wy, sz + 1, 0);
                }
                if top_id == 9 && self.get_block(wx, wy, SEA_Z) == 9 {
                    self.set_block(wx, wy, SEA_Z, 20);
                } else if top_id == 1 || top_id == 42 {
                    self.set_block(wx, wy, sz, 19);
                } else if top_id == 19 && above_id == 0 && season_progress > 0.35 && lcg_f(&mut s) < 0.25 {
                    self.set_block(wx, wy, sz + 1, 43);
                }
            }
            0 => {
                // SPRING
                let biome = self.get_biome(wx, wy);
                if above_id == 43 {
                    self.set_block(wx, wy, sz + 1, 0);
                } else if top_id == 19 && biome != 4 && sz < 154 {
                    self.set_block(wx, wy, sz, if lcg_f(&mut s) < 0.24 { 42 } else { 1 });
                } else if top_id == 1 && above_id == 0 && lcg_f(&mut s) < 0.09 {
                    self.set_block(wx, wy, sz, 42);
                } else if top_id == 1 && above_id == 0 && lcg_f(&mut s) < 0.06 {
                    self.set_block(wx, wy, sz + 1, 68);
                } else if self.get_block(wx, wy, SEA_Z) == 20 && biome != 4 {
                    self.set_block(wx, wy, SEA_Z, 9);
                }
            }
            1 => {
                // SUMMER
                if top_id == 42 {
                    self.set_block(wx, wy, sz, 1);
                }
                if (above_id == 54 || above_id == 67) && lcg_f(&mut s) < 0.08 {
                    let nx = wx + ((lcg(&mut s) % 3) as i32) - 1;
                    let ny = wy + ((lcg(&mut s) % 3) as i32) - 1;
                    let nsz = self.get_surface_z(nx, ny);
                    let nb = self.get_block(nx, ny, nsz);
                    if (nb == 1 || nb == 2) && self.get_block(nx, ny, nsz + 1) == 0 {
                        self.set_block(nx, ny, nsz + 1, above_id);
                    }
                }
                if above_id == 43 && self.get_biome(wx, wy) != 4 {
                    self.set_block(wx, wy, sz + 1, 0);
                }
            }
            _ => {
                // AUTUMN
                if top_id == 42 {
                    self.set_block(wx, wy, sz, 1);
                }
                if top_id == 1 && above_id == 0 && lcg_f(&mut s) < 0.025 {
                    self.set_block(wx, wy, sz + 1, 53);
                }
            }
        }
    }

    pub fn pick_iso_block(&mut self, wx_s: f32, wy_s: f32, pz: i32, slice_z: i32) -> (i32, i32, i32, u8) {
        let z_top = (MAX_Z_I32 - 1).min(slice_z);
        let z_bot = 1.max(pz - 6);
        for gz in (z_bot..=z_top).rev() {
            let adj_top = wy_s - (gz + 1) as f32 * ZH;
            let gx_t = (0.5 * (wx_s / TW - adj_top / TH)).floor() as i32;
            let gy_t = (0.5 * (-adj_top / TH - wx_s / TW)).floor() as i32;
            let id_t = self.get_block(gx_t, gy_t, gz);
            if id_t > 0 && !self.lut[id_t as usize].is_liquid {
                return (gx_t, gy_t, gz, id_t);
            }
            let adj_mid = wy_s - (gz as f32 + 0.5) * ZH;
            let gx_m = (0.5 * (wx_s / TW - adj_mid / TH)).floor() as i32;
            let gy_m = (0.5 * (-adj_mid / TH - wx_s / TW)).floor() as i32;
            let id_m = self.get_block(gx_m, gy_m, gz);
            if id_m > 0 && !self.lut[id_m as usize].is_liquid {
                return (gx_m, gy_m, gz, id_m);
            }
        }
        let adj_p = wy_s - pz as f32 * ZH;
        let gx = (0.5 * (wx_s / TW - adj_p / TH)).floor() as i32;
        let gy = (0.5 * (-adj_p / TH - wx_s / TW)).floor() as i32;
        let pzc = pz.clamp(1, MAX_Z_I32 - 1);
        let id = self.get_block(gx, gy, pzc);
        if id > 0 {
            (gx, gy, pzc, id)
        } else {
            let bz = 1.max(pzc - 1);
            (gx, gy, bz, self.get_block(gx, gy, bz))
        }
    }
}
