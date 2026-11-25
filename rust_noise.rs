pub struct PerlinNoise {
    p: [u8; 512],
}

impl PerlinNoise {
    pub fn new(seed_in: i32) -> Self {
        let mut perm = [0u8; 256];
        for i in 0..256 {
            perm[i] = i as u8;
        }
        let mut s: u32 = if seed_in == 0 { 1337 } else { seed_in as u32 };
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

    #[inline(always)]
    fn fade(t: f64) -> f64 {
        t * t * t * (t * (t * 6.0 - 15.0) + 10.0)
    }

    #[inline(always)]
    fn lerp(t: f64, a: f64, b: f64) -> f64 {
        a + t * (b - a)
    }

    #[inline(always)]
    fn grad2(hash: u8, x: f64, y: f64) -> f64 {
        let h = hash & 7;
        let u = if h < 4 { x } else { y };
        let v = if h < 4 { y } else { x };
        (if (h & 1) != 0 { -u } else { u }) + (if (h & 2) != 0 { -2.0 * v } else { 2.0 * v })
    }

    #[inline(always)]
    fn grad3(hash: u8, x: f64, y: f64, z: f64) -> f64 {
        let h = hash & 15;
        let u = if h < 8 { x } else { y };
        let v = if h < 4 {
            y
        } else if h == 12 || h == 14 {
            x
        } else {
            z
        };
        (if (h & 1) == 0 { u } else { -u }) + (if (h & 2) == 0 { v } else { -v })
    }

    #[inline]
    pub fn noise2(&self, x_in: f64, y_in: f64) -> f64 {
        let xf = x_in.floor();
        let yf = y_in.floor();
        let xi = (xf as i32 & 255) as usize;
        let yi = (yf as i32 & 255) as usize;
        let x = x_in - xf;
        let y = y_in - yf;
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

    #[inline]
    pub fn noise3(&self, x_in: f64, y_in: f64, z_in: f64) -> f64 {
        let xf = x_in.floor();
        let yf = y_in.floor();
        let zf = z_in.floor();
        let xi = (xf as i32 & 255) as usize;
        let yi = (yf as i32 & 255) as usize;
        let zi = (zf as i32 & 255) as usize;
        let x = x_in - xf;
        let y = y_in - yf;
        let z = z_in - zf;
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

    #[inline]
    pub fn fbm2(&self, x: f64, y: f64, octaves: i32) -> f64 {
        let mut total = 0.0;
        let mut amp = 0.5;
        let mut freq = 1.0;
        let mut norm = 0.0;
        for _ in 0..octaves {
            total += self.noise2(x * freq, y * freq) * amp;
            norm += amp;
            amp *= 0.5;
            freq *= 2.03;
        }
        total / norm
    }

    #[inline(always)]
    pub fn hash2(x: i32, z: i32) -> f64 {
        let mut n = x.wrapping_mul(374761393).wrapping_add(z.wrapping_mul(668265263));
        n = n.wrapping_mul(1274126177);
        let u = (n as u32) ^ ((n as u32) >> 16);
        (u as f64) / 4294967295.0
    }

    #[inline(always)]
    pub fn hash3(x: i32, y: i32, z: i32) -> f64 {
        let mut n = x
            .wrapping_mul(374761393)
            .wrapping_add(y.wrapping_mul(668265263))
            .wrapping_add(z.wrapping_mul(2147483647));
        n = n.wrapping_mul(1274126177);
        let u = (n as u32) ^ ((n as u32) >> 16);
        (u as f64) / 4294967295.0
    }
}
