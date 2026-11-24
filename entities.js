// ============================================================================
// VOXELHORIZON v3.1 — 3D СКИНЫ, ОБЩИЙ 3D ЛУТ И СИНХРОНИЗИРОВАННЫЕ МОБЫ (HOST-AUTH)
// ============================================================================
window.VH = window.VH || {};

VH.DETAILED_SKINS = [
  { id: "fox_officer",   name: "Офицер Кицунэ",   skin: "#f3c6a5", hair: "#c2410c", eye: "#f59e0b", shirt: "#1e293b", trim: "#fbbf24", pants: "#0f172a", addon: "fox" },
  { id: "estelle_cmd",   name: "Командир Эстель", skin: "#fde2d2", hair: "#2dd4bf", eye: "#e11d48", shirt: "#18181b", trim: "#eab308", pants: "#27272a", addon: "estelle" },
  { id: "luna_cat",      name: "Лунная Кошка",    skin: "#f8e3d6", hair: "#e2e8f0", eye: "#86efac", shirt: "#1c1917", trim: "#dc2626", pants: "#09090b", addon: "luna" },
  { id: "shadow_knight", name: "Теневой Рыцарь",  skin: "#94a3b8", hair: "#0f172a", eye: "#38bdf8", shirt: "#1e1b4b", trim: "#38bdf8", pants: "#0f172a", addon: "knight" },
  { id: "emerald_scout", name: "Следопыт",        skin: "#e0ac69", hair: "#451a03", eye: "#22c55e", shirt: "#14532d", trim: "#a3e635", pants: "#3f2e21", addon: "none" },
  { id: "explorer",      name: "Классика HD",     skin: "#e2b28b", hair: "#4a321d", eye: "#2448a8", shirt: "#2898b8", trim: "#1e6f88", pants: "#38467a", addon: "none" }
];
VH.SKINS_DB = VH.DETAILED_SKINS;

function makeNameTagSprite(text) {
  const cv = document.createElement("canvas");
  cv.width = 256; cv.height = 48;
  const ctx = cv.getContext("2d");
  ctx.fillStyle = "rgba(0, 0, 0, 0.58)";
  ctx.fillRect(14, 6, 228, 36);
  ctx.font = "bold 22px monospace, sans-serif";
  ctx.fillStyle = "#ffffff";
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillText(String(text || "Игрок").slice(0, 16), 128, 25);
  const tex = new THREE.CanvasTexture(cv);
  const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, transparent: true }));
  sp.scale.set(1.6, 0.3, 1);
  sp.position.set(0, 2.25, 0);
  return sp;
}

function buildTexturedPartMesh(w, h, d, img, uvMap, texW, texH) {
  const geo = new THREE.BoxGeometry(w, h, d);
  const uvAttr = geo.attributes.uv;
  const faceOrder = ["east", "west", "up", "down", "south", "north"];
  for (let f = 0; f < 6; f++) {
    const rect = uvMap[faceOrder[f]];
    if (!rect) continue;
    const [u, v, uw, vh] = rect;
    const u0 = u / texW, u1 = (u + uw) / texW;
    const v1 = 1 - v / texH, v0 = 1 - (v + vh) / texH;
    const base = f * 4;
    uvAttr.setXY(base + 0, u0, v1);
    uvAttr.setXY(base + 1, u1, v1);
    uvAttr.setXY(base + 2, u0, v0);
    uvAttr.setXY(base + 3, u1, v0);
  }
  uvAttr.needsUpdate = true;
  const tex = new THREE.Texture(img);
  tex.magFilter = THREE.NearestFilter;
  tex.minFilter = THREE.NearestFilter;
  tex.needsUpdate = true;
  return new THREE.Mesh(geo, new THREE.MeshLambertMaterial({ map: tex }));
}

function buildAvatarMesh(skinObj, nickLabel = "") {
  const s = skinObj || VH.DETAILED_SKINS[0];
  const g = new THREE.Group();
  const box = (w, h, d, col, x, y, z, parent = g) => {
    const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), new THREE.MeshLambertMaterial({ color: col }));
    m.position.set(x, y, z);
    parent.add(m);
    return m;
  };

  const head = new THREE.Group(); head.position.set(0, 1.5, 0); g.add(head);
  const armL = new THREE.Group(); armL.position.set(-0.38, 1.44, 0); g.add(armL);
  const armR = new THREE.Group(); armR.position.set( 0.38, 1.44, 0); g.add(armR);
  const legL = new THREE.Group(); legL.position.set(-0.14, 0.76, 0); g.add(legL);
  const legR = new THREE.Group(); legR.position.set( 0.14, 0.76, 0); g.add(legR);

  let tail = null, braids = null;

  if (s.customImg && s.customImg.width >= 32) {
    const img = s.customImg;
    const tw = img.width || 64, th = img.height || 64;
    const is64 = th >= 64;
    const hm = buildTexturedPartMesh(0.5, 0.5, 0.5, img, {
      east: [0, 8, 8, 8], west: [16, 8, 8, 8], up: [8, 0, 8, 8], down: [16, 0, 8, 8], south: [8, 8, 8, 8], north: [24, 8, 8, 8]
    }, tw, th);
    hm.position.set(0, 0.25, 0); head.add(hm);

    const bm = buildTexturedPartMesh(0.52, 0.72, 0.28, img, {
      east: [16, 20, 4, 12], west: [28, 20, 4, 12], up: [20, 16, 8, 4], down: [28, 16, 8, 4], south: [20, 20, 8, 12], north: [32, 20, 8, 12]
    }, tw, th);
    bm.position.set(0, 1.14, 0); g.add(bm);

    const rArmUv = { east: [40, 20, 4, 12], west: [48, 20, 4, 12], up: [44, 16, 4, 4], down: [48, 16, 4, 4], south: [44, 20, 4, 12], north: [52, 20, 4, 12] };
    const lArmUv = is64
      ? { east: [32, 52, 4, 12], west: [40, 52, 4, 12], up: [36, 48, 4, 4], down: [40, 48, 4, 4], south: [36, 52, 4, 12], north: [44, 52, 4, 12] }
      : rArmUv;
    const amL = buildTexturedPartMesh(0.22, 0.72, 0.22, img, rArmUv, tw, th); amL.position.set(0, -0.32, 0); armL.add(amL);
    const amR = buildTexturedPartMesh(0.22, 0.72, 0.22, img, lArmUv, tw, th); amR.position.set(0, -0.32, 0); armR.add(amR);

    const rLegUv = { east: [0, 20, 4, 12], west: [8, 20, 4, 12], up: [4, 16, 4, 4], down: [8, 16, 4, 4], south: [4, 20, 4, 12], north: [12, 20, 4, 12] };
    const lLegUv = is64
      ? { east: [16, 52, 4, 12], west: [24, 52, 4, 12], up: [20, 48, 4, 4], down: [24, 48, 4, 4], south: [20, 52, 4, 12], north: [28, 52, 4, 12] }
      : rLegUv;
    const lmL = buildTexturedPartMesh(0.24, 0.76, 0.24, img, rLegUv, tw, th); lmL.position.set(0, -0.38, 0); legL.add(lmL);
    const lmR = buildTexturedPartMesh(0.24, 0.76, 0.24, img, lLegUv, tw, th); lmR.position.set(0, -0.38, 0); legR.add(lmR);
  } else {
    const trim = s.trim || s.shirt;
    // Голова, чёлка, пряди и выразительные глаза
    box(0.5, 0.5, 0.5, s.skin, 0, 0.25, 0, head);
    box(0.53, 0.16, 0.53, s.hair, 0, 0.44, -0.01, head);
    box(0.53, 0.46, 0.12, s.hair, 0, 0.25, -0.22, head);
    box(0.08, 0.34, 0.12, s.hair, -0.24, 0.22, 0.14, head);
    box(0.08, 0.34, 0.12, s.hair,  0.24, 0.22, 0.14, head);
    box(0.06, 0.08, 0.03, "#ffffff", -0.14, 0.25, 0.255, head);
    box(0.06, 0.08, 0.03, s.eye,     -0.10, 0.25, 0.255, head);
    box(0.06, 0.08, 0.03, "#ffffff",  0.14, 0.25, 0.255, head);
    box(0.06, 0.08, 0.03, s.eye,      0.10, 0.25, 0.255, head);

    // Аксессуары головы по референсам
    if (s.addon === "fox" || s.addon === "estelle") {
      box(0.58, 0.14, 0.58, s.shirt, 0, 0.54, 0.01, head);
      box(0.56, 0.04, 0.20, trim,    0, 0.47, 0.31, head);
      box(0.12, 0.08, 0.04, trim,    0, 0.55, 0.30, head);
    }
    if (s.addon === "fox") {
      const eL = box(0.14, 0.22, 0.10, s.hair, -0.22, 0.62, -0.02, head); eL.rotation.z = 0.28;
      const eR = box(0.14, 0.22, 0.10, s.hair,  0.22, 0.62, -0.02, head); eR.rotation.z = -0.28;
    }
    if (s.addon === "estelle") {
      box(0.44, 0.08, 0.03, trim, 0, 0.24, 0.265, head);
    }
    if (s.addon === "luna") {
      const eL = box(0.16, 0.22, 0.10, s.hair, -0.18, 0.58, 0.02, head); eL.rotation.z = 0.22;
      const eR = box(0.16, 0.22, 0.10, s.hair,  0.18, 0.58, 0.02, head); eR.rotation.z = -0.22;
      box(0.24, 0.10, 0.08, trim, 0, 0.02, 0.24, head);
    }
    if (s.addon === "knight") {
      box(0.56, 0.54, 0.56, s.shirt, 0, 0.26, 0, head);
      box(0.38, 0.10, 0.04, trim, 0, 0.26, 0.28, head);
    }

    // Торс + пояс + расклешённая пола мундира
    box(0.52, 0.72, 0.28, s.shirt, 0, 1.14, 0);
    box(0.54, 0.08, 0.30, trim,    0, 0.84, 0);
    if (s.addon === "fox" || s.addon === "estelle" || s.addon === "luna") {
      box(0.56, 0.26, 0.32, s.shirt, 0, 0.70, 0);
      box(0.57, 0.04, 0.33, trim,    0, 0.58, 0);
    }

    // Руки с наплечниками и манжетами
    box(0.22, 0.72, 0.22, s.shirt, 0, -0.32, 0, armL);
    box(0.24, 0.14, 0.24, trim,    0, -0.06, 0, armL);
    box(0.22, 0.72, 0.22, s.shirt, 0, -0.32, 0, armR);
    box(0.24, 0.14, 0.24, trim,    0, -0.06, 0, armR);

    // Ноги и сапоги
    box(0.24, 0.76, 0.24, s.pants, 0, -0.38, 0, legL);
    box(0.25, 0.22, 0.25, "#1a1c22", 0, -0.65, 0, legL);
    box(0.24, 0.76, 0.24, s.pants, 0, -0.38, 0, legR);
    box(0.25, 0.22, 0.25, "#1a1c22", 0, -0.65, 0, legR);

    // Пушистый 3D-хвост для Офицера Кицунэ
    if (s.addon === "fox") {
      tail = new THREE.Group();
      tail.position.set(0, 0.82, -0.15);
      box(0.22, 0.22, 0.55, s.hair, 0, -0.06, -0.25, tail);
      box(0.18, 0.18, 0.20, "#ffffff", 0, -0.09, -0.55, tail);
      tail.rotation.x = -0.35;
      g.add(tail);
    }
    // Длинные двойные 3D-косы для Лунной Кошки
    if (s.addon === "luna") {
      braids = new THREE.Group();
      braids.position.set(0, 1.48, -0.24);
      box(0.16, 0.78, 0.16, s.hair, -0.17, -0.36, -0.04, braids);
      box(0.16, 0.78, 0.16, s.hair,  0.17, -0.36, -0.04, braids);
      g.add(braids);
    }
  }

  let nameTag = null;
  if (nickLabel) {
    nameTag = makeNameTagSprite(nickLabel);
    g.add(nameTag);
  }

  g.userData = { head, armL, armR, legL, legR, tail, braids, nameTag };
  return g;
}

// ============================================================================
// ОБЩИЕ ВЫБРОШЕННЫЕ ПРЕДМЕТЫ (3D ЛУТ НА ЗЕМЛЕ С СИНХРОНИЗАЦИЕЙ В СЕТИ)
// ============================================================================
class DroppedItemManager {
  constructor(scene, world) {
    this.scene = scene;
    this.world = world;
    this.items = new Map();
    this.nextId = 1;
  }

  setWorld(world) {
    this.clearAll();
    this.world = world;
  }

  clearAll() {
    for (const it of this.items.values()) this.scene.remove(it.mesh);
    this.items.clear();
  }

  _makeItemMesh(itemId) {
    const cv = document.createElement("canvas");
    VH.drawItemIcon(cv, itemId);
    const tex = new THREE.CanvasTexture(cv);
    tex.magFilter = THREE.NearestFilter;
    tex.minFilter = THREE.NearestFilter;
    const mat = new THREE.MeshLambertMaterial({ map: tex, transparent: true, alphaTest: 0.2 });
    return new THREE.Mesh(new THREE.BoxGeometry(0.32, 0.32, 0.32), mat);
  }

  spawn(itemId, count, x, y, z, vx = 0, vy = 3.6, vz = 0, forcedId = null, skipNet = false) {
    if (!itemId || count <= 0) return null;
    const id = forcedId || ("d_" + Date.now() + "_" + (this.nextId++));
    if (this.items.has(id)) return this.items.get(id);
    const mesh = this._makeItemMesh(itemId);
    mesh.position.set(x, y, z);
    this.scene.add(mesh);
    const obj = { id, itemId, count, x, y, z, vx, vy, vz, age: 0, mesh };
    this.items.set(id, obj);

    if (!skipNet && VH.Engine?.mpMgr?.isConnected()) {
      VH.Engine.mpMgr.broadcast({ t: "drop_spawn", drop: { id, itemId, count, x, y, z, vx, vy, vz } });
    }
    return obj;
  }

  remove(id, skipNet = false) {
    const it = this.items.get(id);
    if (!it) return;
    this.scene.remove(it.mesh);
    this.items.delete(id);
    if (!skipNet && VH.Engine?.mpMgr?.isConnected()) {
      VH.Engine.mpMgr.broadcast({ t: "drop_pickup", id });
    }
  }

  serializeAll() {
    return Array.from(this.items.values()).map((d) => ({
      id: d.id, itemId: d.itemId, count: d.count, x: +d.x.toFixed(2), y: +d.y.toFixed(2), z: +d.z.toFixed(2)
    }));
  }

  applySnapshot(list) {
    this.clearAll();
    if (!Array.isArray(list)) return;
    for (const d of list) {
      this.spawn(d.itemId, d.count, d.x, d.y, d.z, 0, 0, 0, d.id, true);
    }
  }

  update(dt, player, onPickup) {
    for (const [id, it] of this.items.entries()) {
      it.age += dt;
      if (it.age > 180) { this.remove(id, true); continue; }
      it.vy -= 15 * dt;
      it.x += it.vx * dt;
      it.y += it.vy * dt;
      it.z += it.vz * dt;
      it.vx *= 0.88; it.vz *= 0.88;

      const below = this.world.getBlock(Math.floor(it.x), Math.floor(it.y - 0.16), Math.floor(it.z));
      if (VH.ITEMS_DB[below]?.solid && it.vy < 0) {
        it.y = Math.floor(it.y - 0.16) + 1.16;
        it.vy = 0;
      }

      it.mesh.position.set(it.x, it.y + Math.sin(it.age * 4.2) * 0.08, it.z);
      it.mesh.rotation.y += dt * 2.2;

      if (it.age > 0.65 && player) {
        const dist = Math.hypot(player.x - it.x, (player.y + 0.8) - it.y, player.z - it.z);
        if (dist < 1.65) {
          onPickup(it.itemId, it.count);
          this.remove(id, false);
        }
      }
    }
  }
}

// ============================================================================
// 3D МОДЕЛИ СТУДИИ МОДОВ (.VHMOD)
// ============================================================================
function buildCustomVhModel(modelJson) {
  const root = new THREE.Group();
  const boneMap = {};
  const bones = (modelJson && modelJson.bones) || [];
  const scale = 1 / 16;

  for (const b of bones) {
    const grp = new THREE.Group();
    const piv = b.pivot || [0, 0, 0];
    grp.position.set(piv[0] * scale, piv[1] * scale, piv[2] * scale);
    if (b.rotation) {
      grp.rotation.set((b.rotation[0]||0)*Math.PI/180, (b.rotation[1]||0)*Math.PI/180, (b.rotation[2]||0)*Math.PI/180);
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
      const sz = c.size || [4, 4, 4], org = c.origin || [-2, 0, -2];
      const mesh = new THREE.Mesh(new THREE.BoxGeometry(sz[0]*scale, sz[1]*scale, sz[2]*scale), new THREE.MeshLambertMaterial({ color: c.color || "#2ed573" }));
      mesh.position.set((org[0]+sz[0]*0.5-piv[0])*scale, (org[1]+sz[1]*0.5-piv[1])*scale, (org[2]+sz[2]*0.5-piv[2])*scale);
      if (c.rotation) mesh.rotation.set((c.rotation[0]||0)*Math.PI/180, (c.rotation[1]||0)*Math.PI/180, (c.rotation[2]||0)*Math.PI/180);
      grp.add(mesh);
    }
  }
  root.userData = { legs: [], arms: [], boneMap };
  return root;
}

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
      if (t >= sorted[i].time && t <= sorted[i + 1].time) { f0 = sorted[i]; f1 = sorted[i + 1]; break; }
    }
    const alpha = Math.max(0, Math.min(1, (t - f0.time) / Math.max(0.0001, f1.time - f0.time)));
    const lerp = (a, b) => a + (b - a) * alpha;
    const r0 = f0.rot || [0, 0, 0], r1 = f1.rot || r0;
    bone.rotation.set(lerp(r0[0], r1[0])*Math.PI/180, lerp(r0[1], r1[1])*Math.PI/180, lerp(r0[2], r1[2])*Math.PI/180);
  }
}

// ============================================================================
// СИНХРОНИЗИРОВАННЫЕ МОБЫ (HOST-AUTHORITATIVE В МУЛЬТИПЛЕЕРЕ)
// ============================================================================
class MobManager {
  constructor(scene, world) {
    this.scene = scene;
    this.world = world;
    this.mobs = [];
    this.projectiles = [];
    this.spawnTimer = 1.5;
    this.nextMobId = 1;
    this.netSyncTimer = 0;
  }

  setWorld(world) { this.clearAll(); this.world = world; }

  clearAll() {
    for (const m of this.mobs) this.scene.remove(m.mesh);
    for (const p of this.projectiles) this.scene.remove(p.mesh);
    this.mobs = []; this.projectiles = [];
  }

  getActiveBoss() { return this.mobs.find((m) => m.isBoss) || null; }

  createMobMesh(type) {
    const cdef = VH.CUSTOM_MOBS && VH.CUSTOM_MOBS[type];
    if (cdef && cdef.modelJson) return buildCustomVhModel(cdef.modelJson);

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
      box(0.85, 0.85, 0.85, 0x2c1418, 0, 2.55, 0);
      box(0.92, 0.22, 0.92, 0xffc824, 0, 3.05, 0);
      box(0.18, 0.12, 0.06, 0xff4418, -0.2, 2.62, 0.44); box(0.18, 0.12, 0.06, 0xff4418, 0.2, 2.62, 0.44);
      box(1.1, 1.3, 0.6, 0x48181e, 0, 1.55, 0);
      box(0.4, 0.4, 0.1, 0xff9918, 0, 1.65, 0.32);
      const aL = mkLimb(0.34, 1.25, 0.34, 0x381216, -0.78, 2.1, 0);
      const aR = mkLimb(0.34, 1.25, 0.34, 0x381216,  0.78, 2.1, 0);
      box(0.16, 0.16, 1.4, 0xff7718, 0, -1.1, 0.5, aR);
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

  spawnMob(type, x, y, z, forcedId = null) {
    const id = forcedId || ("m_" + (this.nextMobId++));
    const existing = this.mobs.find((m) => m.id === id);
    if (existing) return existing;

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
      id, type, name: (cdef && cdef.name) || (type === "inferno_lord" ? "Повелитель Преисподней" : type),
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

  serializeAll() {
    return this.mobs.map((m) => ({
      id: m.id, type: m.type,
      x: +m.x.toFixed(2), y: +m.y.toFixed(2), z: +m.z.toFixed(2),
      yaw: +m.dirAngle.toFixed(2), hp: m.hp, maxHp: m.maxHp
    }));
  }

  applyNetworkSnapshot(list) {
    if (!Array.isArray(list)) return;
    const seen = new Set();
    for (const s of list) {
      seen.add(s.id);
      let m = this.mobs.find((x) => x.id === s.id);
      if (!m) m = this.spawnMob(s.type, s.x, s.y, s.z, s.id);
      m.tx = s.x; m.ty = s.y; m.tz = s.z;
      m.dirAngle = s.yaw; m.hp = s.hp; m.maxHp = s.maxHp;
    }
    for (let i = this.mobs.length - 1; i >= 0; i--) {
      if (!seen.has(this.mobs[i].id)) {
        this.scene.remove(this.mobs[i].mesh);
        this.mobs.splice(i, 1);
      }
    }
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
    if (!mob) return;
    // Если мы Клиент в мультиплеере — отправляем удар Хосту, чтобы мобы были общими!
    if (VH.Engine?.mpMgr?.isConnected() && !VH.Engine?.mpMgr?.isHost) {
      mob.hurtFlash = 0.22;
      VH.Engine.mpMgr.sendToHost({ t: "mob_hit", id: mob.id, dmg, dirX, dirZ });
      return;
    }

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
    const mp = VH.Engine?.mpMgr;
    const isClient = Boolean(mp && mp.isConnected() && !mp.isHost);

    // Снаряды обновляются у всех визуально
    for (let i = this.projectiles.length - 1; i >= 0; i--) {
      const pr = this.projectiles[i];
      pr.life -= dt;
      if (pr.fromPlayer) pr.vy -= 9.5 * dt;
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
        if (pr.fiery && (hitTarget || hitB) && onExplosion && !isClient) onExplosion(Math.floor(pr.x), Math.floor(pr.y), Math.floor(pr.z), 1.6);
        this.scene.remove(pr.mesh);
        this.projectiles.splice(i, 1);
      }
    }

    // Если мы Клиент в мультиплеере — плавно двигаем мобов по данным Хоста!
    if (isClient) {
      for (const m of this.mobs) {
        if (typeof m.tx === "number") {
          const dMove = Math.hypot(m.tx - m.x, m.tz - m.z);
          m.x += (m.tx - m.x) * Math.min(1, dt * 10);
          m.y += (m.ty - m.y) * Math.min(1, dt * 10);
          m.z += (m.tz - m.z) * Math.min(1, dt * 10);
          m.walkPhase += dMove * 4.5;
          const swing = Math.sin(m.walkPhase) * 0.65;
          if (m.mesh.userData.legs) m.mesh.userData.legs.forEach((leg, li) => { leg.rotation.x = li % 2 === 0 ? swing : -swing; });
        }
        m.mesh.position.set(m.x, m.y, m.z);
        m.mesh.rotation.y = m.dirAngle;
        if (m.hurtFlash > 0) { m.hurtFlash -= dt; m.mesh.scale.set(1.08, 0.92, 1.08); }
        else m.mesh.scale.set(1, 1, 1);
      }
      return;
    }

    // Хост или Одиночная игра управляют спавном и ИИ мобов
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

      if (m.customDef && m.customDef.animJson) applyModelAnimation(m.mesh, m.customDef.animJson, m.walkPhase * 0.25);
      if (m.customDef && typeof m.customDef.onTick === "function") { try { m.customDef.onTick(m, dt, VH.Engine); } catch {} }

      m.mesh.position.set(m.x, m.y, m.z);
      m.mesh.rotation.y = m.dirAngle;
      if (m.hurtFlash > 0) { m.hurtFlash -= dt; m.mesh.scale.set(1.08, 0.92, 1.08); }
      else if (m.fuse <= 0) m.mesh.scale.set(1, 1, 1);
    }

    // Хост рассылает состояние всех мобов клиентам 10 раз/сек
    if (mp && mp.isConnected() && mp.isHost) {
      this.netSyncTimer += dt;
      if (this.netSyncTimer >= 0.1) {
        this.netSyncTimer = 0;
        mp.broadcast({ t: "mob_sync", mobs: this.serializeAll() });
      }
    }
  }
}

VH.buildAvatarMesh = buildAvatarMesh;
VH.DroppedItemManager = DroppedItemManager;
VH.buildCustomVhModel = buildCustomVhModel;
VH.applyModelAnimation = applyModelAnimation;
VH.MobManager = MobManager;
