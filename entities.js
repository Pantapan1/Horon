// ============================================================================
// VOXELHORIZON v3.0 — 12 МОБОВ + БОСС АДА (ПОВЕЛИТЕЛЬ ПРЕИСПОДНЕЙ) + 3D АНИМАЦИИ МОДОВ
// ============================================================================
window.VH = window.VH || {};

function buildAvatarMesh(skinObj) {
  const s = skinObj || VH.SKINS_DB[0];
  const g = new THREE.Group();
  const box = (w, h, d, col, x, y, z, parent = g) => {
    const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), new THREE.MeshLambertMaterial({ color: col }));
    m.position.set(x, y, z);
    parent.add(m);
    return m;
  };
  const head = new THREE.Group(); head.position.set(0, 1.5, 0); g.add(head);
  box(0.5, 0.5, 0.5, s.skin, 0, 0.25, 0, head);
  box(0.52, 0.16, 0.52, s.hair, 0, 0.44, 0, head);
  box(0.09, 0.08, 0.04, s.eye, -0.11, 0.26, 0.26, head);
  box(0.09, 0.08, 0.04, s.eye,  0.11, 0.26, 0.26, head);

  box(0.52, 0.72, 0.28, s.shirt, 0, 1.14, 0);
  const armL = new THREE.Group(); armL.position.set(-0.38, 1.44, 0); g.add(armL);
  box(0.22, 0.72, 0.22, s.shirt, 0, -0.32, 0, armL);
  const armR = new THREE.Group(); armR.position.set( 0.38, 1.44, 0); g.add(armR);
  box(0.22, 0.72, 0.22, s.shirt, 0, -0.32, 0, armR);
  const legL = new THREE.Group(); legL.position.set(-0.14, 0.76, 0); g.add(legL);
  box(0.24, 0.76, 0.24, s.pants, 0, -0.38, 0, legL);
  const legR = new THREE.Group(); legR.position.set( 0.14, 0.76, 0); g.add(legR);
  box(0.24, 0.76, 0.24, s.pants, 0, -0.38, 0, legR);

  g.userData = { head, armL, armR, legL, legR };
  return g;
}

// Сборка 3D-модели из формата Студии Модов (.vhmodel.json)
function buildCustomVhModel(modelJson) {
  const root = new THREE.Group();
  const boneMap = {};
  const bones = (modelJson && modelJson.bones) || [];
  const scale = 1 / 16; // 16 воксельных единиц = 1 блок

  for (const b of bones) {
    const grp = new THREE.Group();
    const piv = b.pivot || [0, 0, 0];
    grp.position.set(piv[0] * scale, piv[1] * scale, piv[2] * scale);
    if (b.rotation) {
      grp.rotation.set(
        (b.rotation[0] || 0) * Math.PI / 180,
        (b.rotation[1] || 0) * Math.PI / 180,
        (b.rotation[2] || 0) * Math.PI / 180
      );
    }
    boneMap[b.name] = grp;
  }

  for (const b of bones) {
    const grp = boneMap[b.name];
    const piv = b.pivot || [0, 0, 0];
    if (b.parent && boneMap[b.parent]) {
      const ppiv = (bones.find((x) => x.name === b.parent)?.pivot) || [0, 0, 0];
      grp.position.set((piv[0] - ppiv[0]) * scale, (piv[1] - ppiv[1]) * scale, (piv[2] - ppiv[2]) * scale);
      boneMap[b.parent].add(grp);
    } else {
      root.add(grp);
    }

    for (const c of (b.cubes || [])) {
      const sz = c.size || [4, 4, 4];
      const org = c.origin || [-2, 0, -2];
      const mat = new THREE.MeshLambertMaterial({ color: c.color || "#2ed573" });
      const mesh = new THREE.Mesh(new THREE.BoxGeometry(sz[0] * scale, sz[1] * scale, sz[2] * scale), mat);
      mesh.position.set(
        (org[0] + sz[0] * 0.5 - piv[0]) * scale,
        (org[1] + sz[1] * 0.5 - piv[1]) * scale,
        (org[2] + sz[2] * 0.5 - piv[2]) * scale
      );
      if (c.rotation) {
        mesh.rotation.set(
          (c.rotation[0] || 0) * Math.PI / 180,
          (c.rotation[1] || 0) * Math.PI / 180,
          (c.rotation[2] || 0) * Math.PI / 180
        );
      }
      grp.add(mesh);
    }
  }

  root.userData = { legs: [], arms: [], boneMap, basePivots: Object.fromEntries(bones.map((b) => [b.name, b.pivot || [0,0,0]])) };
  return root;
}

// Проигрывание ключевых кадров (.vhanim.json) по времени tSec
function applyModelAnimation(modelGroup, animJson, tSec) {
  if (!modelGroup || !modelGroup.userData?.boneMap || !animJson || !animJson.tracks) return;
  const dur = Math.max(0.1, animJson.duration || 1.0);
  const t = animJson.loop !== false ? (tSec % dur) : Math.min(dur, tSec);

  for (const [boneName, frames] of Object.entries(animJson.tracks)) {
    const bone = modelGroup.userData.boneMap[boneName];
    if (!bone || !Array.isArray(frames) || frames.length === 0) continue;
    const sorted = frames.slice().sort((a, b) => a.time - b.time);
    let f0 = sorted[0], f1 = sorted[sorted.length - 1];
    for (let i = 0; i < sorted.length - 1; i++) {
      if (t >= sorted[i].time && t <= sorted[i + 1].time) {
        f0 = sorted[i]; f1 = sorted[i + 1]; break;
      }
    }
    const span = Math.max(0.0001, f1.time - f0.time);
    const alpha = Math.max(0, Math.min(1, (t - f0.time) / span));
    const lerp = (a, b) => a + (b - a) * alpha;

    const r0 = f0.rot || [0, 0, 0], r1 = f1.rot || r0;
    bone.rotation.set(
      lerp(r0[0], r1[0]) * Math.PI / 180,
      lerp(r0[1], r1[1]) * Math.PI / 180,
      lerp(r0[2], r1[2]) * Math.PI / 180
    );
    if (f0.scale || f1.scale) {
      const s0 = f0.scale || [1, 1, 1], s1 = f1.scale || s0;
      bone.scale.set(lerp(s0[0], s1[0]), lerp(s0[1], s1[1]), lerp(s0[2], s1[2]));
    }
  }
}

class MobManager {
  constructor(scene, world) {
    this.scene = scene;
    this.world = world;
    this.mobs = [];
    this.projectiles = []; // { mesh, x,y,z, vx,vy,vz, dmg, life, fiery, fromPlayer }
    this.spawnTimer = 1.5;
  }

  setWorld(world) { this.clearAll(); this.world = world; }

  clearAll() {
    for (const m of this.mobs) this.scene.remove(m.mesh);
    for (const p of this.projectiles) this.scene.remove(p.mesh);
    this.mobs = []; this.projectiles = [];
  }

  getActiveBoss() {
    return this.mobs.find((m) => m.isBoss) || null;
  }

  createMobMesh(type) {
    const cdef = VH.CUSTOM_MOBS && VH.CUSTOM_MOBS[type];
    if (cdef && cdef.modelJson) {
      return buildCustomVhModel(cdef.modelJson);
    }

    const g = new THREE.Group();
    const legs = [], arms = [];
    const box = (w, h, d, col, x, y, z, parent = g) => {
      const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), new THREE.MeshLambertMaterial({ color: col }));
      m.position.set(x, y, z); parent.add(m); return m;
    };
    const mkLimb = (w, h, d, col, px, py, pz) => {
      const p = new THREE.Group(); p.position.set(px, py, pz); g.add(p);
      box(w, h, d, col, 0, -h / 2, 0, p); return p;
    };

    if (type === "sheep" || type === "cow" || type === "pig") {
      const bodyCol = type === "sheep" ? 0xf2f4f8 : (type === "cow" ? 0x5c3c28 : 0xf49db2);
      const headCol = type === "sheep" ? 0x3b3532 : (type === "cow" ? 0x4a3020 : 0xf8abc0);
      box(0.85, 0.68, 1.18, bodyCol, 0, 0.82, 0);
      box(0.54, 0.54, 0.58, headCol, 0, 1.02, 0.72);
      if (type === "cow") { box(0.1, 0.22, 0.1, 0xdadada, -0.26, 1.32, 0.68); box(0.1, 0.22, 0.1, 0xdadada, 0.26, 1.32, 0.68); }
      legs.push(mkLimb(0.22, 0.5, 0.22, headCol, -0.26, 0.5, -0.36), mkLimb(0.22, 0.5, 0.22, headCol, 0.26, 0.5, -0.36));
      legs.push(mkLimb(0.22, 0.5, 0.22, headCol, -0.26, 0.5,  0.36), mkLimb(0.22, 0.5, 0.22, headCol, 0.26, 0.5,  0.36));
    } else if (type === "chicken") {
      box(0.42, 0.36, 0.52, 0xffffff, 0, 0.45, 0);
      box(0.26, 0.32, 0.26, 0xffffff, 0, 0.74, 0.26);
      box(0.12, 0.08, 0.16, 0xf2a624, 0, 0.72, 0.42);
      legs.push(mkLimb(0.08, 0.28, 0.08, 0xf2a624, -0.1, 0.28, 0), mkLimb(0.08, 0.28, 0.08, 0xf2a624, 0.1, 0.28, 0));
    } else if (type === "zombie" || type === "skeleton" || type === "piglin") {
      const skin = type === "zombie" ? 0x52824c : (type === "skeleton" ? 0xe0e0d8 : 0xe49c88);
      const shirt = type === "zombie" ? 0x2a8898 : (type === "skeleton" ? 0xd0d0c8 : 0x5c3428);
      const pants = type === "zombie" ? 0x3a3278 : (type === "skeleton" ? 0xd0d0c8 : 0x3a241c);
      const eyeC = type === "skeleton" ? 0x1a1a1a : (type === "piglin" ? 0xffffff : 0x182618);
      box(0.5, 0.5, 0.5, skin, 0, 1.72, 0);
      box(0.1, 0.07, 0.04, eyeC, -0.11, 1.76, 0.26); box(0.1, 0.07, 0.04, eyeC, 0.11, 1.76, 0.26);
      box(0.52, 0.72, 0.28, shirt, 0, 1.12, 0);
      const aL = mkLimb(0.2, 0.7, 0.2, skin, -0.38, 1.42, 0), aR = mkLimb(0.2, 0.7, 0.2, skin, 0.38, 1.42, 0);
      if (type === "zombie") { aL.rotation.x = -1.35; aR.rotation.x = -1.35; }
      arms.push(aL, aR);
      legs.push(mkLimb(0.22, 0.76, 0.22, pants, -0.14, 0.76, 0), mkLimb(0.22, 0.76, 0.22, pants, 0.14, 0.76, 0));
    } else if (type === "creeper") {
      box(0.48, 0.48, 0.48, 0x3eb844, 0, 1.56, 0);
      box(0.1, 0.1, 0.04, 0x111111, -0.12, 1.62, 0.25); box(0.1, 0.1, 0.04, 0x111111, 0.12, 1.62, 0.25);
      box(0.16, 0.14, 0.04, 0x111111, 0, 1.46, 0.25);
      box(0.46, 0.82, 0.3, 0x35a43a, 0, 0.92, 0);
      legs.push(mkLimb(0.22, 0.5, 0.22, 0x2d8e32, -0.14, 0.5, -0.18), mkLimb(0.22, 0.5, 0.22, 0x2d8e32, 0.14, 0.5, -0.18));
      legs.push(mkLimb(0.22, 0.5, 0.22, 0x2d8e32, -0.14, 0.5,  0.18), mkLimb(0.22, 0.5, 0.22, 0x2d8e32, 0.14, 0.5,  0.18));
    } else if (type === "spider") {
      box(0.7, 0.42, 0.95, 0x2c282e, 0, 0.42, -0.2);
      box(0.48, 0.36, 0.48, 0x221e24, 0, 0.42, 0.48);
      box(0.08, 0.08, 0.04, 0xff2222, -0.12, 0.48, 0.73); box(0.08, 0.08, 0.04, 0xff2222, 0.12, 0.48, 0.73);
      for (const sx of [-0.5, 0.5]) for (const sz of [-0.3, 0.0, 0.3]) legs.push(mkLimb(0.12, 0.5, 0.12, 0x1c1820, sx, 0.42, sz));
    } else if (type === "enderman") {
      box(0.48, 0.48, 0.48, 0x16161a, 0, 2.55, 0);
      box(0.12, 0.06, 0.04, 0xd84cff, -0.12, 2.58, 0.25); box(0.12, 0.06, 0.04, 0xd84cff, 0.12, 2.58, 0.25);
      box(0.48, 0.85, 0.26, 0x141418, 0, 1.88, 0);
      arms.push(mkLimb(0.14, 1.3, 0.14, 0x141418, -0.34, 2.25, 0), mkLimb(0.14, 1.3, 0.14, 0x141418, 0.34, 2.25, 0));
      legs.push(mkLimb(0.14, 1.45, 0.14, 0x141418, -0.14, 1.45, 0), mkLimb(0.14, 1.45, 0.14, 0x141418, 0.14, 1.45, 0));
    } else if (type === "blaze") {
      box(0.5, 0.5, 0.5, 0xffb824, 0, 1.65, 0);
      box(0.1, 0.08, 0.04, 0x1a1a1a, -0.11, 1.68, 0.26); box(0.1, 0.08, 0.04, 0x1a1a1a, 0.11, 1.68, 0.26);
      for (let i = 0; i < 4; i++) {
        const a = (i / 4) * Math.PI * 2;
        arms.push(mkLimb(0.14, 0.8, 0.14, 0xffd842, Math.cos(a) * 0.48, 1.3, Math.sin(a) * 0.48));
      }
    } else if (type === "ghast") {
      box(2.1, 2.1, 2.1, 0xf4f4f6, 0, 1.5, 0);
      box(0.32, 0.2, 0.05, 0x321414, -0.48, 1.7, 1.06); box(0.32, 0.2, 0.05, 0x321414, 0.48, 1.7, 1.06);
      box(0.4, 0.52, 0.05, 0x521818, 0, 1.1, 1.06);
      for (let i = -1; i <= 1; i++) legs.push(mkLimb(0.22, 1.2, 0.22, 0xdadce2, i * 0.55, 0.5, 0));
    } else if (type === "inferno_lord") {
      // БОСС АДА: Повелитель Преисподней!
      box(0.85, 0.85, 0.85, 0x2c1418, 0, 2.55, 0);
      box(0.92, 0.22, 0.92, 0xffc824, 0, 3.05, 0); // Огненная корона
      box(0.18, 0.12, 0.06, 0xff4418, -0.2, 2.62, 0.44); box(0.18, 0.12, 0.06, 0xff4418, 0.2, 2.62, 0.44);
      box(1.1, 1.3, 0.6, 0x48181e, 0, 1.55, 0);
      box(0.4, 0.4, 0.1, 0xff9918, 0, 1.65, 0.32); // Ядро в груди
      const aL = mkLimb(0.34, 1.25, 0.34, 0x381216, -0.78, 2.1, 0);
      const aR = mkLimb(0.34, 1.25, 0.34, 0x381216,  0.78, 2.1, 0);
      box(0.16, 0.16, 1.4, 0xff7718, 0, -1.1, 0.5, aR); // Пылающий меч!
      arms.push(aL, aR);
      legs.push(mkLimb(0.36, 0.95, 0.36, 0x240c10, -0.28, 0.95, 0), mkLimb(0.36, 0.95, 0.36, 0x240c10, 0.28, 0.95, 0));
    } else if (cdef && typeof cdef.buildMesh === "function") {
      try { const cm = cdef.buildMesh(THREE, g, { box, mkLimb, legs, arms }); if (cm) { cm.userData = cm.userData || { legs, arms }; return cm; } } catch {}
    } else {
      const col = (cdef && cdef.color) || 0x44dd88;
      box(0.55, 0.55, 0.55, col, 0, 1.7, 0);
      box(0.55, 0.75, 0.32, col, 0, 1.1, 0);
      arms.push(mkLimb(0.22, 0.7, 0.22, col, -0.4, 1.4, 0), mkLimb(0.22, 0.7, 0.22, col, 0.4, 1.4, 0));
      legs.push(mkLimb(0.24, 0.75, 0.24, col, -0.15, 0.75, 0), mkLimb(0.24, 0.75, 0.24, col, 0.15, 0.75, 0));
    }

    g.userData = { legs, arms };
    return g;
  }

  spawnMob(type, x, y, z) {
    const mesh = this.createMobMesh(type);
    mesh.position.set(x, y, z);
    this.scene.add(mesh);
    const cdef = VH.CUSTOM_MOBS && VH.CUSTOM_MOBS[type];
    const isBoss = type === "inferno_lord" || Boolean(cdef && cdef.isBoss);
    const hostile = cdef ? Boolean(cdef.hostile) : !["sheep", "cow", "pig", "chicken"].includes(type);
    const flying  = cdef ? Boolean(cdef.flying)  : (type === "blaze" || type === "ghast");
    const hpMap = { sheep:10, cow:12, pig:10, chicken:6, zombie:20, skeleton:18, creeper:20, spider:16, enderman:34, piglin:24, blaze:22, ghast:24, inferno_lord:160 };
    const maxHp = cdef && cdef.hp ? cdef.hp : (hpMap[type] || 16);
    const mob = {
      type, name: (cdef && cdef.name) || (type === "inferno_lord" ? "Повелитель Преисподней" : type),
      isBoss, hostile, flying, mesh, x, y, z, vx:0, vy:0, vz:0,
      hp: maxHp, maxHp, dmg: (cdef && cdef.dmg) || 3,
      customDef: cdef || null,
      dirAngle: Math.random() * Math.PI * 2,
      aiTimer: 0, atkCd: 0, fuse: 0, walkPhase: 0, hurtFlash: 0
    };
    this.mobs.push(mob);
    if (VH.modMgr) VH.modMgr.emit("onMobSpawn", { mob });
    return mob;
  }

  fireProjectile(from, to, dmg, fiery = false, fromPlayer = false) {
    const dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
    const len = Math.max(0.1, Math.hypot(dx, dy, dz));
    const spd = fromPlayer ? 24 : (fiery ? 11 : 15);
    const mesh = new THREE.Mesh(
      new THREE.BoxGeometry(fiery ? 0.36 : 0.1, fiery ? 0.36 : 0.1, fiery ? 0.36 : 0.65),
      new THREE.MeshBasicMaterial({ color: fiery ? 0xff7718 : (fromPlayer ? 0xffe484 : 0xded8c8) })
    );
    mesh.position.set(from.x, from.y, from.z);
    this.scene.add(mesh);
    this.projectiles.push({ mesh, x: from.x, y: from.y, z: from.z, vx: dx/len*spd, vy: dy/len*spd, vz: dz/len*spd, dmg, life: 3.0, fiery, fromPlayer });
  }

  damageMob(mob, dmg, dirX = 0, dirZ = 0, onKill = null) {
    mob.hp -= dmg;
    mob.hurtFlash = 0.22;
    mob.vx += dirX * 5.5; mob.vz += dirZ * 5.5;
    if (!mob.flying) mob.vy = 4.5;
    if (mob.type === "enderman" && mob.hp > 0 && Math.random() < 0.5) {
      mob.x += (Math.random() - 0.5) * 10; mob.z += (Math.random() - 0.5) * 10;
    }
    if (mob.hp <= 0) {
      this.scene.remove(mob.mesh);
      this.mobs = this.mobs.filter((x) => x !== mob);
      if (VH.modMgr) VH.modMgr.emit("onMobKill", { mob });
      if (onKill) onKill(mob);
    }
  }

  hitMobAtRay(origin, dir, maxDist, dmg, onKill) {
    let best = null, bestD = maxDist;
    for (const m of this.mobs) {
      const rSq = m.isBoss ? 2.6 : (m.type === "ghast" ? 2.2 : 0.85);
      const cy = m.y + (m.isBoss ? 1.6 : (m.type === "enderman" ? 1.4 : 0.9));
      const toX = m.x - origin.x, toY = cy - origin.y, toZ = m.z - origin.z;
      const proj = toX * dir.x + toY * dir.y + toZ * dir.z;
      if (proj < 0 || proj > bestD) continue;
      const px = origin.x + dir.x * proj - m.x, py = origin.y + dir.y * proj - cy, pz = origin.z + dir.z * proj - m.z;
      if (px * px + py * py + pz * pz <= rSq) { bestD = proj; best = m; }
    }
    if (!best) return false;
    this.damageMob(best, dmg, dir.x, dir.z, onKill);
    return true;
  }

  update(dt, player, isNight, onPlayerHurt, onExplosion, onMobKilledByArrow) {
    this.spawnTimer -= dt;
    if (this.spawnTimer <= 0 && this.mobs.length < 12) {
      this.spawnTimer = 3.8;
      const ang = Math.random() * Math.PI * 2;
      const dist = 13 + Math.random() * 14;
      const sx = Math.floor(player.x + Math.cos(ang) * dist);
      const sz = Math.floor(player.z + Math.sin(ang) * dist);

      if (this.world.dimension === "nether") {
        if (!this.getActiveBoss() && Math.random() < 0.18) {
          this.spawnMob("inferno_lord", sx + 0.5, 27, sz + 0.5);
        } else {
          const pool = ["piglin", "blaze", "ghast"];
          const type = pool[Math.floor(Math.random() * pool.length)];
          this.spawnMob(type, sx + 0.5, type === "ghast" ? 34 : 26, sz + 0.5);
        }
      } else {
        for (let sy = VH.WORLD_H - 3; sy > VH.SEA_LEVEL; sy--) {
          if (VH.ITEMS_DB[this.world.getBlock(sx, sy, sz)]?.solid) {
            const peaceful = ["sheep", "cow", "pig", "chicken"];
            const hostile = ["zombie", "skeleton", "creeper", "spider", "enderman"];
            const type = (isNight && Math.random() < 0.72)
              ? hostile[Math.floor(Math.random() * hostile.length)]
              : peaceful[Math.floor(Math.random() * peaceful.length)];
            this.spawnMob(type, sx + 0.5, sy + 1.1, sz + 0.5);
            break;
          }
        }
      }
    }

    // Снаряды (стрелы игрока, стрелы скелета, фаерболы)
    for (let i = this.projectiles.length - 1; i >= 0; i--) {
      const pr = this.projectiles[i];
      pr.life -= dt;
      if (pr.fromPlayer) pr.vy -= 9.5 * dt; // Баллистика стрелы лука!
      pr.x += pr.vx * dt; pr.y += pr.vy * dt; pr.z += pr.vz * dt;
      pr.mesh.position.set(pr.x, pr.y, pr.z);

      let hitTarget = false;
      if (pr.fromPlayer) {
        for (const m of this.mobs) {
          if (Math.hypot(pr.x - m.x, pr.z - m.z) < (m.isBoss ? 1.3 : 0.75) && Math.abs(pr.y - (m.y + 1.0)) < 1.3) {
            this.damageMob(m, pr.dmg, pr.vx * 0.05, pr.vz * 0.05, onMobKilledByArrow);
            hitTarget = true; break;
          }
        }
      } else {
        hitTarget = Math.hypot(pr.x - player.x, pr.z - player.z) < 0.75 && Math.abs(pr.y - (player.y + 1.0)) < 1.0;
        if (hitTarget && onPlayerHurt) onPlayerHurt(pr.dmg);
      }

      const hitB = VH.ITEMS_DB[this.world.getBlock(pr.x, pr.y, pr.z)]?.solid;
      if (hitTarget || hitB || pr.life <= 0) {
        if (pr.fiery && (hitTarget || hitB) && onExplosion) onExplosion(Math.floor(pr.x), Math.floor(pr.y), Math.floor(pr.z), 1.6);
        this.scene.remove(pr.mesh);
        this.projectiles.splice(i, 1);
      }
    }

    for (let i = this.mobs.length - 1; i >= 0; i--) {
      const m = this.mobs[i];
      const distP = Math.hypot(player.x - m.x, player.z - m.z);
      if (distP > 52 || m.y < -4) { this.scene.remove(m.mesh); this.mobs.splice(i, 1); continue; }

      m.aiTimer -= dt;
      m.atkCd = Math.max(0, m.atkCd - dt);
      const aggro = m.hostile && distP < (m.isBoss ? 26 : 18) && player.mode === "survival";

      if (aggro) {
        m.dirAngle = Math.atan2(player.x - m.x, player.z - m.z);
        const ranged = m.type === "skeleton" || m.type === "blaze" || m.type === "ghast";
        const spd = m.isBoss ? 3.1 : (m.type === "spider" || m.type === "enderman" ? 3.4 : (ranged ? 1.8 : 2.5));

        if (m.isBoss && distP < 18 && m.atkCd <= 0) {
          m.atkCd = 2.2;
          // Веер из 3 огненных шаров Босса!
          for (const off of [-1.5, 0, 1.5]) {
            this.fireProjectile({ x: m.x, y: m.y + 2.0, z: m.z }, { x: player.x + off, y: player.y + 1.0, z: player.z - off }, 5, true, false);
          }
        } else if (ranged && distP < 14 && m.atkCd <= 0) {
          m.atkCd = m.type === "ghast" ? 2.6 : 1.9;
          this.fireProjectile({ x: m.x, y: m.y + 1.4, z: m.z }, { x: player.x, y: player.y + 1.0, z: player.z }, m.type === "ghast" ? 5 : 3, m.type !== "skeleton", false);
        } else if (m.type === "creeper" && distP < 2.6) {
          m.fuse += dt;
          const sc = 1 + Math.sin(m.fuse * 26) * 0.14;
          m.mesh.scale.set(sc, sc, sc);
          if (m.fuse >= 1.35) {
            if (onExplosion) onExplosion(Math.floor(m.x), Math.floor(m.y), Math.floor(m.z), 2.5);
            if (distP < 4.5 && onPlayerHurt) onPlayerHurt(8);
            this.scene.remove(m.mesh); this.mobs.splice(i, 1); continue;
          }
        } else {
          m.fuse = Math.max(0, m.fuse - dt);
          if (!ranged || distP > 8) {
            m.vx += (Math.sin(m.dirAngle) * spd - m.vx) * Math.min(1, dt * 6);
            m.vz += (Math.cos(m.dirAngle) * spd - m.vz) * Math.min(1, dt * 6);
          }
          if (!ranged && distP < (m.isBoss ? 2.2 : 1.65) && Math.abs(player.y - m.y) < 2.2 && m.atkCd <= 0) {
            m.atkCd = 1.1;
            if (onPlayerHurt) onPlayerHurt(m.isBoss ? 7 : (m.type === "enderman" ? 5 : 3));
          }
        }
      } else {
        if (m.aiTimer <= 0) { m.aiTimer = 2 + Math.random() * 3; m.dirAngle = Math.random() * Math.PI * 2; }
        const spd = m.hostile ? 1.4 : 1.1;
        m.vx += (Math.sin(m.dirAngle) * spd - m.vx) * Math.min(1, dt * 4);
        m.vz += (Math.cos(m.dirAngle) * spd - m.vz) * Math.min(1, dt * 4);
      }

      if (m.flying) {
        const targetY = Math.max(22, player.y + (m.type === "ghast" ? 6 : 2.5));
        m.vy = (targetY - m.y) * 0.9;
        m.x += m.vx * dt; m.y += m.vy * dt; m.z += m.vz * dt;
      } else {
        m.vy -= 18 * dt;
        const nx = m.x + m.vx * dt, nz = m.z + m.vz * dt;
        if (VH.ITEMS_DB[this.world.getBlock(nx, m.y + 0.2, nz)]?.solid) {
          if (!VH.ITEMS_DB[this.world.getBlock(nx, m.y + 1.2, nz)]?.solid && m.vy <= 0.1) m.vy = 6.2;
          else m.dirAngle += Math.PI * 0.7;
        } else { m.x = nx; m.z = nz; }
        const ny = m.y + m.vy * dt;
        if (m.vy <= 0 && VH.ITEMS_DB[this.world.getBlock(m.x, ny, m.z)]?.solid) { m.y = Math.floor(ny) + 1.0; m.vy = 0; }
        else m.y = ny;
      }

      const moveSpd = Math.hypot(m.vx, m.vz);
      m.walkPhase += dt * moveSpd * 4.5;
      const swing = Math.sin(m.walkPhase) * 0.65;
      if (m.mesh.userData.legs) m.mesh.userData.legs.forEach((leg, li) => { leg.rotation.x = li % 2 === 0 ? swing : -swing; });
      if (m.type === "blaze" && m.mesh.userData.arms) {
        m.mesh.userData.arms.forEach((rod, ri) => { rod.position.y = 1.2 + Math.sin(m.walkPhase + ri) * 0.2; });
      } else if (m.type !== "zombie" && m.mesh.userData.arms) {
        m.mesh.userData.arms.forEach((arm, ai) => { arm.rotation.x = ai % 2 === 0 ? -swing : swing; });
      }

      if (m.customDef && m.customDef.animJson) {
        applyModelAnimation(m.mesh, m.customDef.animJson, m.walkPhase * 0.25);
      }
      if (m.customDef && typeof m.customDef.onTick === "function") {
        try { m.customDef.onTick(m, dt, VH.Engine); } catch {}
      }

      m.mesh.position.set(m.x, m.y, m.z);
      m.mesh.rotation.y = m.dirAngle;
      if (m.hurtFlash > 0) { m.hurtFlash -= dt; m.mesh.scale.set(1.08, 0.92, 1.08); }
      else if (m.fuse <= 0) m.mesh.scale.set(1, 1, 1);
    }
  }
}

VH.buildAvatarMesh = buildAvatarMesh;
VH.buildCustomVhModel = buildCustomVhModel;
VH.applyModelAnimation = applyModelAnimation;
VH.MobManager = MobManager;
