use wasm_bindgen::prelude::*;

const CHUNK_SIZE: usize = 16;
const WORLD_H: usize = 64;
const SEA_LEVEL: usize = 22;

struct Perlin {
    p: [u8; 512],
}

impl Perlin {
    fn new(seed: i32) -> Self {
        let mut perm = [0u8; 256];
        for i in 0..256 {
            perm[i] = i as u8;
        }
        let mut s: u32 = if seed == 0 { 1337 } else { seed as u32 };
        for i in (1..256).rev() {
            s = s.wrapping_mul(1664525).wrapping_add(1013904223);
            let j = (s as usize) % (i + 1);
            perm.swap(i, j);
        }
        let mut p = [0u8; 512];
        for i in 0..512 {
            p[i] = perm[i & 255];
        }
        Self { p }
    }

    #[inline]
    fn fade(t: f64) -> f64 {
        t * t * t * (t * (t * 6.0 - 15.0) + 10.0)
    }

    #[inline]
    fn lerp(t: f64, a: f64, b: f64) -> f64 {
        a + t * (b - a)
    }

    #[inline]
    fn grad2(hash: u8, x: f64, y: f64) -> f64 {
        let h = hash & 7;
        let u = if h < 4 { x } else { y };
        let v = if h < 4 { y } else { x };
        (if (h & 1) != 0 { -u } else { u }) + (if (h & 2) != 0 { -2.0 * v } else { 2.0 * v })
    }

    #[inline]
    fn grad3(hash: u8, x: f64, y: f64, z: f64) -> f64 {
        let h = hash & 15;
        let u = if h < 8 { x } else { y };
        let v = if h < 4 { y } else if h == 12 || h == 14 { x } else { z };
        (if (h & 1) == 0 { u } else { -u }) + (if (h & 2) == 0 { v } else { -v })
    }

    fn noise2(&self, mut x: f64, mut y: f64) -> f64 {
        let xi = (x.floor() as i32 & 255) as usize;
        let yi = (y.floor() as i32 & 255) as usize;
        x -= x.floor();
        y -= y.floor();
        let u = Self::fade(x);
        let v = Self::fade(y);
        let a = self.p[xi] as usize + yi;
        let b = self.p[xi + 1] as usize + yi;
        Self::lerp(
            v,
            Self::lerp(u, Self::grad2(self.p[a], x, y), Self::grad2(self.p[b], x - 1.0, y)),
            Self::lerp(
                u,
                Self::grad2(self.p[a + 1], x, y - 1.0),
                Self::grad2(self.p[b + 1], x - 1.0, y - 1.0),
            ),
        ) * 0.45
            + 0.5
    }

    fn noise3(&self, mut x: f64, mut y: f64, mut z: f64) -> f64 {
        let xi = (x.floor() as i32 & 255) as usize;
        let yi = (y.floor() as i32 & 255) as usize;
        let zi = (z.floor() as i32 & 255) as usize;
        x -= x.floor();
        y -= y.floor();
        z -= z.floor();
        let u = Self::fade(x);
        let v = Self::fade(y);
        let w = Self::fade(z);
        let a = self.p[xi] as usize + yi;
        let aa = self.p[a] as usize + zi;
        let ab = self.p[a + 1] as usize + zi;
        let b = self.p[xi + 1] as usize + yi;
        let ba = self.p[b] as usize + zi;
        let bb = self.p[b + 1] as usize + zi;
        Self::lerp(
            w,
            Self::lerp(
                v,
                Self::lerp(u, Self::grad3(self.p[aa], x, y, z), Self::grad3(self.p[ba], x - 1.0, y, z)),
                Self::lerp(
                    u,
                    Self::grad3(self.p[ab], x, y - 1.0, z),
                    Self::grad3(self.p[bb], x - 1.0, y - 1.0, z),
                ),
            ),
            Self::lerp(
                v,
                Self::lerp(
                    u,
                    Self::grad3(self.p[aa + 1], x, y, z - 1.0),
                    Self::grad3(self.p[ba + 1], x - 1.0, y, z - 1.0),
                ),
                Self::lerp(
                    u,
                    Self::grad3(self.p[ab + 1], x, y - 1.0, z - 1.0),
                    Self::grad3(self.p[bb + 1], x - 1.0, y - 1.0, z - 1.0),
                ),
            ),
        ) * 0.5
            + 0.5
    }

    fn fbm2(&self, x: f64, y: f64, oct: usize) -> f64 {
        let mut val = 0.0;
        let mut amp = 0.5;
        let mut freq = 1.0;
        let mut max = 0.0;
        for _ in 0..oct {
            val += self.noise2(x * freq, y * freq) * amp;
            max += amp;
            amp *= 0.5;
            freq *= 2.03;
        }
        val / max
    }

    #[inline]
    fn hash2(x: i32, z: i32) -> f64 {
        let n = (x.wrapping_mul(374761393).wrapping_add(z.wrapping_mul(668265263)))
            .wrapping_mul(1274126177);
        (((n ^ (n >> 16)) as u32) as f64) / 4294967295.0
    }
}

#[inline]
fn idx(x: usize, y: usize, z: usize) -> usize {
    (y * CHUNK_SIZE + z) * CHUNK_SIZE + x
}

#[wasm_bindgen]
pub fn generate_chunk_wasm(seed: i32, dim_code: i32, cx: i32, cz: i32) -> Vec<u8> {
    let mut data = vec![0u8; CHUNK_SIZE * WORLD_H * CHUNK_SIZE];
    let perlin = Perlin::new(if dim_code == 1 { seed.wrapping_add(99991) } else { seed });
    let base_x = cx * (CHUNK_SIZE as i32);
    let base_z = cz * (CHUNK_SIZE as i32);

    if dim_code == 1 {
        // Nether dimension
        for lx in 0..CHUNK_SIZE {
            for lz in 0..CHUNK_SIZE {
                let wx = base_x + lx as i32;
                let wz = base_z + lz as i32;
                let floor_h = (15.0 + perlin.fbm2(wx as f64 * 0.035, wz as f64 * 0.035, 3) * 14.0) as usize;
                let ceil_h = (46.0 + perlin.fbm2(wx as f64 * 0.03 + 77.0, wz as f64 * 0.03 - 44.0, 2) * 12.0) as usize;
                let is_soul = perlin.noise2(wx as f64 * 0.06, wz as f64 * 0.06) > 0.66;

                for y in 0..WORLD_H {
                    if y == 0 || y == WORLD_H - 1 {
                        data[idx(lx, y, lz)] = 10;
                    } else if y <= floor_h || y >= ceil_h {
                        let r = Perlin::hash2(wx * 7 + (y as i32) * 19, wz * 11 - (y as i32) * 13);
                        if y == floor_h && is_soul && floor_h >= 18 {
                            data[idx(lx, y, lz)] = 31;
                        } else if r > 0.965 {
                            data[idx(lx, y, lz)] = 33;
                        } else {
                            data[idx(lx, y, lz)] = 30;
                        }
                    } else if y <= 18 {
                        data[idx(lx, y, lz)] = 29;
                    } else {
                        let n3 = perlin.noise3(wx as f64 * 0.06, y as f64 * 0.05, wz as f64 * 0.06);
                        if n3 > 0.72 {
                            data[idx(lx, y, lz)] = 30;
                        } else if y + 1 == ceil_h && Perlin::hash2(wx * 3, wz * 5) > 0.96 {
                            data[idx(lx, y, lz)] = 23;
                        }
                    }
                }
                if (wx.rem_euclid(32) <= 1 || wx.rem_euclid(32) == 31)
                    && (wz.rem_euclid(32) <= 1 || wz.rem_euclid(32) == 31)
                    && floor_h > 18
                {
                    let top = ceil_h.min(floor_h + 10);
                    for y in floor_h..top {
                        data[idx(lx, y, lz)] = 32;
                    }
                }
            }
        }
        return data;
    }

    // Overworld dimension
    for lx in 0..CHUNK_SIZE {
        for lz in 0..CHUNK_SIZE {
            let wx = base_x + lx as i32;
            let wz = base_z + lz as i32;
            let temp = perlin.fbm2(wx as f64 * 0.005 + 42.0, wz as f64 * 0.005 + 42.0, 2);
            let moist = perlin.fbm2(wx as f64 * 0.006 - 71.0, wz as f64 * 0.006 + 19.0, 2);
            let ridge = perlin.fbm2(wx as f64 * 0.009 + 120.0, wz as f64 * 0.009 - 88.0, 2);

            // 0=plains, 1=forest, 2=birch, 3=desert, 4=snow, 5=mountains
            let biome = if ridge > 0.68 {
                5
            } else if temp > 0.63 {
                3
            } else if temp < 0.36 {
                4
            } else if moist > 0.58 {
                2
            } else if moist > 0.48 {
                1
            } else {
                0
            };

            let cont = perlin.fbm2(wx as f64 * 0.011, wz as f64 * 0.011, 4);
            let detail = perlin.fbm2(wx as f64 * 0.032 + 15.0, wz as f64 * 0.032 - 22.0, 2);
            let mut hf = 15.0 + cont * 20.0 + detail * 6.0;
            if biome == 5 && cont > 0.35 {
                hf += (cont - 0.35).powf(1.6) * 28.0;
            }
            if biome == 3 {
                hf = 19.0 + cont * 11.0;
            }
            let h = (hf.floor() as usize).clamp(8, WORLD_H - 9);

            for y in 0..WORLD_H {
                if y == 0 {
                    data[idx(lx, y, lz)] = 10;
                } else if y + 3 < h {
                    let cave = perlin.noise3(wx as f64 * 0.08, y as f64 * 0.11, wz as f64 * 0.08);
                    if y > 4 && y + 4 < h && cave > 0.67 {
                        data[idx(lx, y, lz)] = if y <= 7 { 29 } else { 0 };
                    } else {
                        let ore_r = Perlin::hash2(wx * 5 + (y as i32) * 17, wz * 7 - (y as i32) * 13);
                        if y < 14 && ore_r > 0.992 {
                            data[idx(lx, y, lz)] = 14;
                        } else if y < 16 && biome == 5 && ore_r > 0.986 {
                            data[idx(lx, y, lz)] = 36;
                        } else if y < 20 && ore_r > 0.982 {
                            data[idx(lx, y, lz)] = 13;
                        } else if y < 34 && ore_r > 0.960 {
                            data[idx(lx, y, lz)] = 12;
                        } else if ore_r > 0.935 {
                            data[idx(lx, y, lz)] = 11;
                        } else {
                            data[idx(lx, y, lz)] = 3;
                        }
                    }
                } else if y < h {
                    data[idx(lx, y, lz)] = if biome == 3 {
                        28
                    } else if h <= SEA_LEVEL + 1 {
                        8
                    } else {
                        2
                    };
                } else if y == h {
                    if biome == 3 || h <= SEA_LEVEL {
                        data[idx(lx, y, lz)] = 8;
                    } else if biome == 4 || (biome == 5 && h > 42) {
                        data[idx(lx, y, lz)] = 19;
                    } else if biome == 5 && h > 36 {
                        data[idx(lx, y, lz)] = 3;
                    } else {
                        data[idx(lx, y, lz)] = 1;
                    }
                } else if y <= SEA_LEVEL {
                    data[idx(lx, y, lz)] = if biome == 4 && y == SEA_LEVEL { 20 } else { 9 };
                }
            }

            if h > SEA_LEVEL + 1 && (2..=13).contains(&lx) && (2..=13).contains(&lz) {
                let r = Perlin::hash2(wx * 19, wz * 23);
                if biome == 3 && r > 0.985 {
                    for cy in 1..=3 {
                        data[idx(lx, h + cy, lz)] = 21;
                    }
                } else if (biome == 1 && r > 0.962)
                    || (biome == 2 && r > 0.962)
                    || (biome == 0 && r > 0.992)
                    || (biome == 4 && r > 0.984)
                {
                    let log_id = if biome == 2 { 27 } else { 5 };
                    let th = 4 + (((wx + wz) & 1) as usize);
                    for ty in 1..=th {
                        data[idx(lx, h + ty, lz)] = log_id;
                    }
                    for dy in (th - 1)..=(th + 1) {
                        let rad: isize = if dy == th + 1 { 1 } else { 2 };
                        for dx in -rad..=rad {
                            for dz in -rad..=rad {
                                if dx == 0 && dz == 0 && dy <= th {
                                    continue;
                                }
                                if dx.abs() == 2 && dz.abs() == 2 && dy == th {
                                    continue;
                                }
                                if h + dy < WORLD_H {
                                    let tx = (lx as isize + dx) as usize;
                                    let tz = (lz as isize + dz) as usize;
                                    data[idx(tx, h + dy, tz)] = 6;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    data
}

#[wasm_bindgen]
pub fn compute_block_light_wasm(data: &[u8], is_nether: bool) -> Vec<u8> {
    let total = CHUNK_SIZE * WORLD_H * CHUNK_SIZE;
    let mut light = vec![0u8; total];
    let mut queue: Vec<usize> = Vec::with_capacity(512);

    // 1. Skylight (top-down sun/sky propagation) + Block light emitters (Torches, Glowstone, Lava, Portal)
    for lx in 0..CHUNK_SIZE {
        for lz in 0..CHUNK_SIZE {
            let mut sky: u8 = if is_nether { 5 } else { 15 };
            for y in (0..WORLD_H).rev() {
                let i = idx(lx, y, lz);
                let b = data[i];
                let emit: u8 = match b {
                    23 | 29 => 15, // Glowstone / Lava
                    37 => 14,      // Torch
                    34 => 12,      // Nether Portal
                    _ => 0,
                };
                if b != 0 && b != 6 && b != 9 && b != 15 && b != 20 && b != 34 && b != 37 && b != 39 {
                    sky = 0;
                } else if b == 6 || b == 9 || b == 20 {
                    sky = sky.saturating_sub(2);
                }
                let lv = sky.max(emit);
                light[i] = lv;
                if emit > 1 {
                    queue.push(i);
                }
            }
        }
    }

    // 2. Flood-fill block light propagation
    let mut head = 0;
    while head < queue.len() {
        let cur = queue[head];
        head += 1;
        let cl = light[cur];
        if cl <= 1 {
            continue;
        }
        let nl = cl - 1;
        let lx = cur % CHUNK_SIZE;
        let lz = (cur / CHUNK_SIZE) % CHUNK_SIZE;
        let y = cur / (CHUNK_SIZE * CHUNK_SIZE);

        let neighbors: [(isize, isize, isize); 6] = [
            (1, 0, 0),
            (-1, 0, 0),
            (0, 1, 0),
            (0, -1, 0),
            (0, 0, 1),
            (0, 0, -1),
        ];
        for (dx, dy, dz) in neighbors {
            let nx = lx as isize + dx;
            let ny = y as isize + dy;
            let nz = lz as isize + dz;
            if nx < 0
                || nx >= CHUNK_SIZE as isize
                || ny < 0
                || ny >= WORLD_H as isize
                || nz < 0
                || nz >= CHUNK_SIZE as isize
            {
                continue;
            }
            let ni = idx(nx as usize, ny as usize, nz as usize);
            let nb = data[ni];
            let passable = nb == 0 || nb == 6 || nb == 9 || nb == 15 || nb == 20 || nb == 34 || nb == 37 || nb == 39;
            if passable && light[ni] < nl {
                light[ni] = nl;
                queue.push(ni);
            }
        }
    }
    light
}
