// ============================================================================
// VOXELHORIZON v2.0 — НАСТОЯЩИЙ 2D/3D ШУМ ПЕРЛИНА, ОБЫЧНЫЙ МИР + АД, ТЕЧЕНИЕ ВОДЫ
// ============================================================================
window.VH = window.VH || {};

const CHUNK_SIZE = 16;
const WORLD_H = 64;
const SEA_LEVEL = 22;

// НАСТОЯЩИЙ ГРАДИЕНТНЫЙ ШУМ КЕНА ПЕРЛИНА (2D и 3D)
class PerlinNoise {
  constructor(seed) {
    this.p = new Uint8Array(512);
    const perm = new Uint8Array(256);
    for (let i = 0; i < 256; i++) perm[i] = i;
    let s = (seed | 0) || 1337;
    for (let i = 255; i > 0; i--) {
      s = (Math.imul(s, 1664525) + 1013904223) | 0;
      const j = (s >>> 0) % (i + 1);
      const tmp = perm[i]; perm[i] = perm[j]; perm[j] = tmp;
    }
    for (let i = 0; i < 512; i++) this.p[i] = perm[i & 255];
  }
  fade(t) { return t * t * t * (t * (t * 6 - 15) + 10); }
  lerp(t, a, b) { return a + t * (b - a); }
  grad2(hash, x, y) {
    const h = hash & 7;
    const u = h < 4 ? x : y;
    const v = h < 4 ? y : x;
    return ((h & 1) ? -u : u) + ((h & 2) ? -2.0 * v : 2.0 * v);
  }
  grad3(hash, x, y, z) {
    const h = hash & 15;
    const u = h < 8 ? x : y;
    const v = h < 4 ? y : (h === 12 || h === 14 ? x : z);
    return ((h & 1) === 0 ? u : -u) + ((h & 2) === 0 ? v : -v);
  }
  noise2(x, y) {
    const X = Math.floor(x) & 255, Y = Math.floor(y) & 255;
    x -= Math.floor(x); y -= Math.floor(y);
    const u = this.fade(x), v = this.fade(y);
    const A = this.p[X] + Y, B = this.p[X + 1] + Y;
    return this.lerp(v,
      this.lerp(u, this.grad2(this.p[A], x, y), this.grad2(this.p[B], x - 1, y)),
      this.lerp(u, this.grad2(this.p[A + 1], x, y - 1), this.grad2(this.p[B + 1], x - 1, y - 1))
    ) * 0.45 + 0.5;
  }
  noise3(x, y, z) {
    const X = Math.floor(x) & 255, Y = Math.floor(y) & 255, Z = Math.floor(z) & 255;
    x -= Math.floor(x); y -= Math.floor(y); z -= Math.floor(z);
    const u = this.fade(x), v = this.fade(y), w = this.fade(z);
    const A = this.p[X] + Y, AA = this.p[A] + Z, AB = this.p[A + 1] + Z;
    const B = this.p[X + 1] + Y, BA = this.p[B] + Z, BB = this.p[B + 1] + Z;
    return this.lerp(w,
      this.lerp(v,
        this.lerp(u, this.grad3(this.p[AA], x, y, z), this.grad3(this.p[BA], x - 1, y, z)),
        this.lerp(u, this.grad3(this.p[AB], x, y - 1, z), this.grad3(this.p[BB], x - 1, y - 1, z))),
      this.lerp(v,
        this.lerp(u, this.grad3(this.p[AA + 1], x, y, z - 1), this.grad3(this.p[BA + 1], x - 1, y, z - 1)),
        this.lerp(u, this.grad3(this.p[AB + 1], x, y - 1, z - 1), this.grad3(this.p[BB + 1], x - 1, y - 1, z - 1)))
    ) * 0.5 + 0.5;
  }
  fbm2(x, y, oct = 4) {
    let val = 0, amp = 0.5, freq = 1, max = 0;
    for (let i = 0; i < oct; i++) {
      val += this.noise2(x * freq, y * freq) * amp;
      max += amp; amp *= 0.5; freq *= 2.03;
    }
    return val / max;
  }
  hash2(x, z) {
    let n = Math.imul(x * 374761393 + z * 668265263, 1274126177);
    return ((n ^ (n >>> 16)) >>> 0) / 4294967295;
  }
}

class VoxelWorld {
  constructor(scene, seed, dimension = "overworld", savedMods = {}) {
    this.scene = scene;
    this.seed = seed;
    this.dimension = dimension; // "overworld" | "nether"
    this.perlin = new PerlinNoise(dimension === "nether" ? seed + 99991 : seed);
    this.chunks = new Map();
    this.modifications = Object.assign({}, savedMods);
    this.fluidQueue = []; // { x, y, z, id, dist }

    this.solidMat = new THREE.MeshLambertMaterial({ map: VH.atlasTexture, vertexColors: true, alphaTest: 0.45 });
    this.liquidMat = new THREE.MeshLambertMaterial({ map: VH.atlasTexture, vertexColors: true, transparent: true, opacity: 0.76, side: THREE.DoubleSide });
  }

  key(cx, cz) { return cx + "," + cz; }

  getBiome(wx, wz) {
    if (this.dimension === "nether") return "nether";
    const temp = this.perlin.fbm2(wx * 0.005 + 42, wz * 0.005 + 42, 2);
    const moist = this.perlin.fbm2(wx * 0.006 - 71, wz * 0.006 + 19, 2);
    const ridge = this.perlin.fbm2(wx * 0.009 + 120, wz * 0.009 - 88, 2);
    if (ridge > 0.68) return "mountains";
    if (temp > 0.63) return "desert";
    if (temp < 0.36) return "snow";
    if (moist > 0.58) return "birch_forest";
    if (moist > 0.48) return "forest";
    return "plains";
  }

  getTerrainHeight(wx, wz) {
    if (this.dimension === "nether") return 24;
    const cont = this.perlin.fbm2(wx * 0.011, wz * 0.011, 4);
    const detail = this.perlin.fbm2(wx * 0.032 + 15, wz * 0.032 - 22, 2);
    const biome = this.getBiome(wx, wz);
    let h = 15 + cont * 20 + detail * 6;
    if (biome === "mountains") h += Math.pow(Math.max(0, cont - 0.35), 1.6) * 28;
    if (biome === "desert") h = 19 + cont * 11;
    return Math.max(8, Math.min(WORLD_H - 9, Math.floor(h)));
  }

  generateChunkData(cx, cz) {
    const data = new Uint8Array(CHUNK_SIZE * WORLD_H * CHUNK_SIZE);
    const idx = (x, y, z) => (y * CHUNK_SIZE + z) * CHUNK_SIZE + x;
    const baseX = cx * CHUNK_SIZE, baseZ = cz * CHUNK_SIZE;

    const customDim = VH.CUSTOM_DIMENSIONS && VH.CUSTOM_DIMENSIONS[this.dimension];
    if (customDim && typeof customDim.generateChunk === "function") {
      customDim.generateChunk(data, cx, cz, CHUNK_SIZE, WORLD_H, this.perlin, idx);
    } else if (customDim && typeof customDim.generateColumn === "function") {
      for (let lx = 0; lx < CHUNK_SIZE; lx++) {
        for (let lz = 0; lz < CHUNK_SIZE; lz++) {
          const wx = baseX + lx, wz = baseZ + lz;
          customDim.generateColumn(data, lx, lz, wx, wz, WORLD_H, this.perlin, idx);
        }
      }
    } else if (this.dimension === "nether") {
      // === ИЗМЕРЕНИЕ АД (NETHER) НА 3D ШУМЕ ПЕРЛИНА ===
      for (let lx = 0; lx < CHUNK_SIZE; lx++) {
        for (let lz = 0; lz < CHUNK_SIZE; lz++) {
          const wx = baseX + lx, wz = baseZ + lz;
          const floorH = Math.floor(15 + this.perlin.fbm2(wx * 0.035, wz * 0.035, 3) * 14);
          const ceilH  = Math.floor(46 + this.perlin.fbm2(wx * 0.03 + 77, wz * 0.03 - 44, 2) * 12);
          const isSoul = this.perlin.noise2(wx * 0.06, wz * 0.06) > 0.66;

          for (let y = 0; y < WORLD_H; y++) {
            if (y === 0 || y === WORLD_H - 1) {
              data[idx(lx, y, lz)] = 10; // Bedrock
            } else if (y <= floorH || y >= ceilH) {
              const r = this.perlin.hash2(wx * 7 + y * 19, wz * 11 - y * 13);
              if (y === floorH && isSoul && floorH >= 18) data[idx(lx, y, lz)] = 31; // Soul Sand
              else if (r > 0.965) data[idx(lx, y, lz)] = 33; // Quartz Ore
              else data[idx(lx, y, lz)] = 30; // Netherrack
            } else if (y <= 18) {
              data[idx(lx, y, lz)] = 29; // Океан Лавы!
            } else {
              // 3D каменные колонны и висящие гроздья светокамня
              const n3 = this.perlin.noise3(wx * 0.06, y * 0.05, wz * 0.06);
              if (n3 > 0.72) data[idx(lx, y, lz)] = 30;
              else if (y === ceilH - 1 && this.perlin.hash2(wx * 3, wz * 5) > 0.96) data[idx(lx, y, lz)] = 23; // Glowstone!
            }
          }
          // Адские кирпичные башни-колонны
          if (Math.abs(wx % 32) <= 1 && Math.abs(wz % 32) <= 1 && floorH > 18) {
            for (let y = floorH; y < Math.min(ceilH, floorH + 10); y++) data[idx(lx, y, lz)] = 32;
          }
        }
      }
    } else {
      // === ОБЫЧНЫЙ МИР НА 2D/3D ШУМЕ ПЕРЛИНА ===
      for (let lx = 0; lx < CHUNK_SIZE; lx++) {
        for (let lz = 0; lz < CHUNK_SIZE; lz++) {
          const wx = baseX + lx, wz = baseZ + lz;
          const biome = this.getBiome(wx, wz);
          const h = this.getTerrainHeight(wx, wz);

          for (let y = 0; y < WORLD_H; y++) {
            if (y === 0) {
              data[idx(lx, y, lz)] = 10;
            } else if (y < h - 3) {
              // Настоящие 3D Перлин-пещеры!
              const cave = this.perlin.noise3(wx * 0.08, y * 0.11, wz * 0.08);
              if (y > 4 && y < h - 4 && cave > 0.67) {
                data[idx(lx, y, lz)] = y <= 7 ? 29 : 0; // Лава на самом дне глубоких пещер!
              } else {
                const oreR = this.perlin.hash2(wx * 5 + y * 17, wz * 7 - y * 13);
                if (y < 14 && oreR > 0.992) data[idx(lx, y, lz)] = 14;       // Diamond
                else if (y < 16 && biome === "mountains" && oreR > 0.986) data[idx(lx, y, lz)] = 36; // Emerald
                else if (y < 20 && oreR > 0.982) data[idx(lx, y, lz)] = 13;  // Gold
                else if (y < 34 && oreR > 0.960) data[idx(lx, y, lz)] = 12;  // Iron
                else if (oreR > 0.935) data[idx(lx, y, lz)] = 11;            // Coal
                else data[idx(lx, y, lz)] = 3;
              }
            } else if (y < h) {
              data[idx(lx, y, lz)] = biome === "desert" ? 28 : (h <= SEA_LEVEL + 1 ? 8 : 2);
            } else if (y === h) {
              if (biome === "desert" || h <= SEA_LEVEL) data[idx(lx, y, lz)] = 8;
              else if (biome === "snow" || (biome === "mountains" && h > 42)) data[idx(lx, y, lz)] = 19;
              else if (biome === "mountains" && h > 36) data[idx(lx, y, lz)] = 3;
              else data[idx(lx, y, lz)] = 1;
            } else if (y <= SEA_LEVEL) {
              data[idx(lx, y, lz)] = (biome === "snow" && y === SEA_LEVEL) ? 20 : 9;
            }
          }

          // Деревья (Дуб / Берёза) и Кактусы
          if (h > SEA_LEVEL + 1 && lx >= 2 && lx <= 13 && lz >= 2 && lz <= 13) {
            const r = this.perlin.hash2(wx * 19, wz * 23);
            if (biome === "desert" && r > 0.985) {
              for (let cy = 1; cy <= 3; cy++) data[idx(lx, h + cy, lz)] = 21;
            } else if ((biome === "forest" && r > 0.962) || (biome === "birch_forest" && r > 0.962) || (biome === "plains" && r > 0.992) || (biome === "snow" && r > 0.984)) {
              const logId = biome === "birch_forest" ? 27 : 5;
              const th = 4 + ((wx + wz) & 1);
              for (let ty = 1; ty <= th; ty++) data[idx(lx, h + ty, lz)] = logId;
              for (let dy = th - 1; dy <= th + 1; dy++) {
                const rad = dy === th + 1 ? 1 : 2;
                for (let dx = -rad; dx <= rad; dx++) {
                  for (let dz = -rad; dz <= rad; dz++) {
                    if (dx === 0 && dz === 0 && dy <= th) continue;
                    if (Math.abs(dx) === 2 && Math.abs(dz) === 2 && dy === th) continue;
                    if (h + dy < WORLD_H) data[idx(lx + dx, h + dy, lz + dz)] = 6;
                  }
                }
              }
            }
          }
        }
      }
    }

    // Применяем сохранённые изменения
    for (const [mk, bId] of Object.entries(this.modifications)) {
      const p = mk.split(",");
      const mx = +p[0], my = +p[1], mz = +p[2];
      if (Math.floor(mx / CHUNK_SIZE) === cx && Math.floor(mz / CHUNK_SIZE) === cz && my >= 0 && my < WORLD_H) {
        const lx = ((mx % CHUNK_SIZE) + CHUNK_SIZE) % CHUNK_SIZE;
        const lz = ((mz % CHUNK_SIZE) + CHUNK_SIZE) % CHUNK_SIZE;
        data[idx(lx, my, lz)] = bId;
      }
    }
    return data;
  }

  getBlock(wx, wy, wz) {
    const x = Math.floor(wx), y = Math.floor(wy), z = Math.floor(wz);
    if (y < 0 || y >= WORLD_H) return 0;
    const ch = this.chunks.get(this.key(Math.floor(x / CHUNK_SIZE), Math.floor(z / CHUNK_SIZE)));
    if (!ch) return 0;
    const lx = ((x % CHUNK_SIZE) + CHUNK_SIZE) % CHUNK_SIZE;
    const lz = ((z % CHUNK_SIZE) + CHUNK_SIZE) % CHUNK_SIZE;
    return ch.data[(y * CHUNK_SIZE + lz) * CHUNK_SIZE + lx];
  }

  setBlock(wx, wy, wz, bId, triggerFlow = true) {
    const x = Math.floor(wx), y = Math.floor(wy), z = Math.floor(wz);
    if (y <= 0 || y >= WORLD_H - 1) return;
    const cx = Math.floor(x / CHUNK_SIZE), cz = Math.floor(z / CHUNK_SIZE);
    const ch = this.chunks.get(this.key(cx, cz));
    if (!ch) return;
    const lx = ((x % CHUNK_SIZE) + CHUNK_SIZE) % CHUNK_SIZE;
    const lz = ((z % CHUNK_SIZE) + CHUNK_SIZE) % CHUNK_SIZE;
    ch.data[(y * CHUNK_SIZE + lz) * CHUNK_SIZE + lx] = bId;
    this.modifications[x + "," + y + "," + z] = bId;

    this.rebuildChunkMesh(cx, cz);
    if (lx === 0) this.rebuildChunkMesh(cx - 1, cz);
    if (lx === CHUNK_SIZE - 1) this.rebuildChunkMesh(cx + 1, cz);
    if (lz === 0) this.rebuildChunkMesh(cx, cz - 1);
    if (lz === CHUNK_SIZE - 1) this.rebuildChunkMesh(cx, cz + 1);

    // Динамическое течение Воды и Лавы!
    if (triggerFlow) {
      if (bId === 9 || bId === 29) {
        this.fluidQueue.push({ x, y, z, id: bId, dist: 3 });
      } else if (bId === 0) {
        for (const [dx, dy, dz] of [[1,0,0],[-1,0,0],[0,1,0],[0,0,1],[0,0,-1]]) {
          const nb = this.getBlock(x + dx, y + dy, z + dz);
          if (nb === 9 || nb === 29) this.fluidQueue.push({ x: x + dx, y: y + dy, z: z + dz, id: nb, dist: 3 });
        }
      }
    }
  }

  tickFluids() {
    if (this.fluidQueue.length === 0) return;
    const batch = this.fluidQueue.splice(0, 6);
    for (const f of batch) {
      if (this.getBlock(f.x, f.y, f.z) !== f.id) continue;
      // Сначала течёт вниз
      if (f.y > 2 && this.getBlock(f.x, f.y - 1, f.z) === 0) {
        this.setBlock(f.x, f.y - 1, f.z, f.id, false);
        this.fluidQueue.push({ x: f.x, y: f.y - 1, z: f.z, id: f.id, dist: f.dist });
      } else if (f.dist > 0) {
        for (const [dx, dz] of [[1,0],[-1,0],[0,1],[0,-1]]) {
          const nx = f.x + dx, nz = f.z + dz;
          const nb = this.getBlock(nx, f.y, nz);
          if (nb === 0) {
            this.setBlock(nx, f.y, nz, f.id, false);
            this.fluidQueue.push({ x: nx, y: f.y, z: nz, id: f.id, dist: f.dist - 1 });
          } else if ((f.id === 9 && nb === 29) || (f.id === 29 && nb === 9)) {
            this.setBlock(nx, f.y, nz, 22, false); // Вода + Лава = Обсидиан!
          }
        }
      }
    }
  }

  buildNetherPortalAt(bx, by, bz) {
    // Строит классическую обсидиановую рамку 4x5 с фиолетовым порталом внутри!
    for (let dx = 0; dx <= 3; dx++) {
      for (let dy = 0; dy <= 4; dy++) {
        const isFrame = dx === 0 || dx === 3 || dy === 0 || dy === 4;
        this.setBlock(bx + dx, by + dy, bz, isFrame ? 22 : 34, false);
      }
    }
  }

  ensureChunk(cx, cz) {
    const k = this.key(cx, cz);
    if (this.chunks.has(k)) return;
    const data = this.generateChunkData(cx, cz);
    this.chunks.set(k, { data, mesh: null, waterMesh: null });
    this.rebuildChunkMesh(cx, cz);
  }

  updateAround(px, pz, radius) {
    const pcx = Math.floor(px / CHUNK_SIZE), pcz = Math.floor(pz / CHUNK_SIZE);
    const needed = new Set();
    let built = 0;
    for (let r = 0; r <= radius; r++) {
      for (let dx = -r; dx <= r; dx++) {
        for (let dz = -r; dz <= r; dz++) {
          const cx = pcx + dx, cz = pcz + dz;
          const k = this.key(cx, cz);
          needed.add(k);
          if (!this.chunks.has(k) && built < 2) {
            this.ensureChunk(cx, cz);
            built++;
          }
        }
      }
    }
    for (const [k, ch] of this.chunks.entries()) {
      if (!needed.has(k)) {
        if (ch.mesh) { this.scene.remove(ch.mesh); ch.mesh.geometry.dispose(); }
        if (ch.waterMesh) { this.scene.remove(ch.waterMesh); ch.waterMesh.geometry.dispose(); }
        this.chunks.delete(k);
      }
    }
  }

  disposeAll() {
    for (const ch of this.chunks.values()) {
      if (ch.mesh) { this.scene.remove(ch.mesh); ch.mesh.geometry.dispose(); }
      if (ch.waterMesh) { this.scene.remove(ch.waterMesh); ch.waterMesh.geometry.dispose(); }
    }
    this.chunks.clear();
  }

  rebuildChunkMesh(cx, cz) {
    const ch = this.chunks.get(this.key(cx, cz));
    if (!ch) return;
    if (ch.mesh) { this.scene.remove(ch.mesh); ch.mesh.geometry.dispose(); ch.mesh = null; }
    if (ch.waterMesh) { this.scene.remove(ch.waterMesh); ch.waterMesh.geometry.dispose(); ch.waterMesh = null; }

    const sPos=[], sNorm=[], sUv=[], sCol=[], sIdx=[];
    const wPos=[], wNorm=[], wUv=[], wCol=[], wIdx=[];
    const baseX = cx * CHUNK_SIZE, baseZ = cz * CHUNK_SIZE;
    const stepU = 1 / VH.ATLAS_COLS, stepV = 1 / (VH.ATLAS_ROWS || VH.ATLAS_COLS), eps = 0.002;

    const FACES = [
      { dir:[ 0, 1, 0], fIdx:0, shade:1.00, verts:[[0,1,1],[1,1,1],[1,1,0],[0,1,0]] },
      { dir:[ 0,-1, 0], fIdx:2, shade:0.55, verts:[[0,0,0],[1,0,0],[1,0,1],[0,0,1]] },
      { dir:[ 1, 0, 0], fIdx:1, shade:0.84, verts:[[1,0,1],[1,0,0],[1,1,0],[1,1,1]] },
      { dir:[-1, 0, 0], fIdx:1, shade:0.78, verts:[[0,0,0],[0,0,1],[0,1,1],[0,1,0]] },
      { dir:[ 0, 0, 1], fIdx:1, shade:0.90, verts:[[0,0,1],[1,0,1],[1,1,1],[0,1,1]] },
      { dir:[ 0, 0,-1], fIdx:1, shade:0.72, verts:[[1,0,0],[0,0,0],[0,1,0],[1,1,0]] }
    ];

    for (let y = 0; y < WORLD_H; y++) {
      for (let lz = 0; lz < CHUNK_SIZE; lz++) {
        for (let lx = 0; lx < CHUNK_SIZE; lx++) {
          const bId = ch.data[(y * CHUNK_SIZE + lz) * CHUNK_SIZE + lx];
          if (bId === 0) continue;
          const info = VH.ITEMS_DB[bId];
          if (!info || !info.faces) continue;

          const wx = baseX + lx, wz = baseZ + lz;
          const isFluidOrPortal = Boolean(info.liquid || info.portal);

          for (const face of FACES) {
            const nx = wx + face.dir[0], ny = y + face.dir[1], nz = wz + face.dir[2];
            const nbId = this.getBlock(nx, ny, nz);
            const nbInfo = VH.ITEMS_DB[nbId];

            if (isFluidOrPortal) {
              if (nbId === bId || (nbInfo && nbInfo.solid && !nbInfo.transparent)) continue;
            } else {
              if (nbId === bId && info.transparent) continue;
              if (nbInfo && nbInfo.solid && !nbInfo.transparent) continue;
            }

            const tile = info.faces[face.fIdx];
            const u0 = (tile % VH.ATLAS_COLS) * stepU + eps;
            const v0 = Math.floor(tile / VH.ATLAS_COLS) * stepV + eps;
            const u1 = u0 + stepU - eps * 2;
            const v1 = v0 + stepV - eps * 2;

            const posA = isFluidOrPortal ? wPos : sPos;
            const nrmA = isFluidOrPortal ? wNorm : sNorm;
            const uvA  = isFluidOrPortal ? wUv : sUv;
            const colA = isFluidOrPortal ? wCol : sCol;
            const idxA = isFluidOrPortal ? wIdx : sIdx;
            const vStart = posA.length / 3;
            const sh = info.light ? 1.18 : face.shade;

            for (let vi = 0; vi < 4; vi++) {
              const v = face.verts[vi];
              const vy = info.liquid && v[1] === 1 ? 0.88 : v[1];
              posA.push(wx + v[0], y + vy, wz + v[2]);
              nrmA.push(face.dir[0], face.dir[1], face.dir[2]);
              colA.push(sh, sh, sh);
            }
            uvA.push(u0, v1, u1, v1, u1, v0, u0, v0);
            idxA.push(vStart, vStart + 1, vStart + 2, vStart, vStart + 2, vStart + 3);
          }
        }
      }
    }

    const mk = (pos, norm, uv, col, idx, mat) => {
      if (pos.length === 0) return null;
      const geo = new THREE.BufferGeometry();
      geo.setAttribute("position", new THREE.Float32BufferAttribute(pos, 3));
      geo.setAttribute("normal", new THREE.Float32BufferAttribute(norm, 3));
      geo.setAttribute("uv", new THREE.Float32BufferAttribute(uv, 2));
      geo.setAttribute("color", new THREE.Float32BufferAttribute(col, 3));
      geo.setIndex(idx);
      const mesh = new THREE.Mesh(geo, mat);
      this.scene.add(mesh);
      return mesh;
    };

    ch.mesh = mk(sPos, sNorm, sUv, sCol, sIdx, this.solidMat);
    ch.waterMesh = mk(wPos, wNorm, wUv, wCol, wIdx, this.liquidMat);
  }
}

VH.CHUNK_SIZE = CHUNK_SIZE;
VH.WORLD_H = WORLD_H;
VH.SEA_LEVEL = SEA_LEVEL;
VH.PerlinNoise = PerlinNoise;
VH.VoxelWorld = VoxelWorld;
