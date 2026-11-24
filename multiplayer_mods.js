// ============================================================================
// VOXELHORIZON v3.0 — INDEXEDDB, ЭКСПОРТ ФАЙЛОВ, АВТОПОДКАЧКА МОДОВ В СЕТИ, .VHMOD ПРОЕКТЫ
// ============================================================================
window.VH = window.VH || {};
VH.CUSTOM_MOBS = {};
VH.CUSTOM_DIMENSIONS = {};
VH.CUSTOM_COMMANDS = {};

// ============================================================================
// 1. НАДЁЖНОЕ ХРАНИЛИЩЕ INDEXEDDB (БЕЗ ЛИМИТА 5 МБ!)
// ============================================================================
const VH_DB = {
  _db: null,
  async open() {
    if (this._db) return this._db;
    return new Promise((resolve) => {
      try {
        const req = indexedDB.open("VoxelHorizonDB_v3", 1);
        req.onupgradeneeded = (e) => {
          const db = e.target.result;
          if (!db.objectStoreNames.contains("kv")) db.createObjectStore("kv");
        };
        req.onsuccess = (e) => { this._db = e.target.result; resolve(this._db); };
        req.onerror = () => resolve(null);
      } catch { resolve(null); }
    });
  },
  async get(key, fallback = null) {
    const db = await this.open();
    if (!db) {
      try { const v = localStorage.getItem("vh3_" + key); return v !== null ? JSON.parse(v) : fallback; } catch { return fallback; }
    }
    return new Promise((resolve) => {
      try {
        const tx = db.transaction("kv", "readonly");
        const r = tx.objectStore("kv").get(key);
        r.onsuccess = () => resolve(r.result !== undefined ? r.result : fallback);
        r.onerror = () => resolve(fallback);
      } catch { resolve(fallback); }
    });
  },
  async set(key, val) {
    const db = await this.open();
    if (!db) {
      try { localStorage.setItem("vh3_" + key, JSON.stringify(val)); } catch {}
      return;
    }
    return new Promise((resolve) => {
      try {
        const tx = db.transaction("kv", "readwrite");
        tx.objectStore("kv").put(val, key);
        tx.oncomplete = () => resolve(true);
        tx.onerror = () => resolve(false);
      } catch { resolve(false); }
    });
  },
  async del(key) {
    const db = await this.open();
    if (!db) { try { localStorage.removeItem("vh3_" + key); } catch {} return; }
    return new Promise((resolve) => {
      try {
        const tx = db.transaction("kv", "readwrite");
        tx.objectStore("kv").delete(key);
        tx.oncomplete = () => resolve(true);
        tx.onerror = () => resolve(false);
      } catch { resolve(false); }
    });
  }
};
VH.DB = VH_DB;

// Экспорт / Сохранение файлов (.vhworld / .vhmod) на устройство Android (Capacitor Filesystem + Share) или в браузер
VH.exportJsonFile = async function (filename, objData) {
  const jsonStr = JSON.stringify(objData, null, 2);
  try {
    const cap = window.Capacitor;
    if (cap && cap.Plugins && cap.Plugins.Filesystem) {
      const { Filesystem, Share } = cap.Plugins;
      await Filesystem.requestPermissions?.();
      const res = await Filesystem.writeFile({
        path: "VoxelHorizon/" + filename,
        data: jsonStr,
        directory: "DOCUMENTS",
        encoding: "utf8",
        recursive: true
      });
      if (Share) {
        await Share.share({ title: filename, url: res.uri });
      }
      return "Сохранено в Documents/VoxelHorizon/" + filename;
    }
  } catch (e) { console.warn("Capacitor Filesystem fallback:", e); }

  const blob = new Blob([jsonStr], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url; a.download = filename;
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  return "Файл " + filename + " загружен!";
};

// ============================================================================
// 2. P2P МУЛЬТИПЛЕЕР С АВТОПОДКАЧКОЙ МОДОВ ХОСТА И СИДА МИРА
// ============================================================================
class MultiplayerManager {
  constructor(scene) {
    this.scene = scene;
    this.peer = null;
    this.conns = new Map();
    this.remotePlayers = new Map();
    this.roomCode = null;
    this.isHost = false;
    this.nick = "Игрок";
    this.onBlockRemote = null;
    this.onChatRemote = null;
    this.onHostModsOffered = null;
  }
  _genCode() { return "VH-" + Math.floor(1000 + Math.random() * 8999); }
  isConnected() { return Boolean(this.peer && (this.isHost || this.conns.size > 0)); }

  hostRoom(nick, onStatus) {
    if (typeof Peer === "undefined") { onStatus("PeerJS не загружен (проверьте интернет)"); return; }
    this.disconnect();
    this.nick = nick || "Хост";
    this.roomCode = this._genCode();
    this.isHost = true;
    onStatus("Создаём комнату " + this.roomCode + "...");
    this.peer = new Peer(this.roomCode);
    this.peer.on("open", (id) => {
      onStatus("Комната открыта! Код для друзей: " + id);
      if (this.onChatRemote) this.onChatRemote("Система", "Комната " + id + " открыта! Все блоки, сундуки, печи, лут, мобы и моды синхронизированы.");
    });
    this.peer.on("connection", (conn) => this._attachConn(conn));
    this.peer.on("error", (err) => onStatus("Ошибка сети: " + err.type));
  }

  joinRoom(code, nick, onStatus) {
    if (typeof Peer === "undefined") { onStatus("PeerJS не загружен (проверьте интернет)"); return; }
    const clean = (code || "").trim().toUpperCase();
    if (!clean) { onStatus("Введите код комнаты!"); return; }
    this.disconnect();
    this.nick = nick || "Гость";
    this.roomCode = clean;
    this.isHost = false;
    onStatus("Подключение к " + clean + "...");
    this.peer = new Peer();
    this.peer.on("open", () => {
      const conn = this.peer.connect(clean, { reliable: true });
      this._attachConn(conn, onStatus);
    });
    this.peer.on("error", (err) => onStatus("Не удалось подключиться: " + err.type));
  }

  sendToHost(payload) {
    if (this.isHost) return;
    for (const c of this.conns.values()) {
      if (c.open) { try { c.send(payload); } catch {} break; }
    }
  }

  _attachConn(conn, onStatus) {
    conn.on("open", () => {
      this.conns.set(conn.peer, conn);
      if (onStatus) onStatus("Подключено к комнате " + this.roomCode + "!");
      if (this.onChatRemote) this.onChatRemote("Сеть", "Подключён игрок: " + conn.peer);

      conn.send({
        t: "hello",
        nick: this.nick,
        skinId: VH.activeSkinId || "fox_officer",
        customPng: VH.activeCustomSkinData || null
      });

      // Хост отправляет полную синхронизацию мира, мобов, сундуков, печей, дропа и модов!
      if (this.isHost && VH.Engine) {
        const eng = VH.Engine;
        if (eng.world) {
          eng.savedDimMods[eng.world.dimension] = Object.assign({}, eng.world.modifications);
          eng.savedDimChests[eng.world.dimension] = Object.assign({}, eng.world.chests);
        }
        conn.send({
          t: "world_init",
          seed: eng.world?.seed || 100100,
          dimension: eng.world?.dimension || "overworld",
          worldTime: eng.worldTime,
          dayCount: eng.dayCount,
          hostPos: { x: eng.player.x, y: eng.player.y, z: eng.player.z },
          dimMods: eng.savedDimMods,
          dimChests: eng.savedDimChests,
          furnaces: eng.furnaces ? Array.from(eng.furnaces.entries()) : [],
          drops: eng.dropMgr ? eng.dropMgr.serializeAll() : [],
          mobs: eng.mobMgr ? eng.mobMgr.serializeAll() : [],
          projects: VH.modMgr.getActiveProjects()
        });
      }
    });

    conn.on("data", (msg) => {
      if (!msg || !msg.t) return;
      const senderId = msg.senderId || conn.peer;
      const relayTypes = ["pos", "skin", "blk", "chest_update", "furnace_update", "drop_spawn", "drop_pickup", "chat", "dim_sync"];
      if (this.isHost && relayTypes.includes(msg.t)) {
        this.broadcast(Object.assign({}, msg, { senderId }), conn.peer);
      }

      if (msg.t === "hello" || msg.t === "pos" || msg.t === "skin") {
        this._updateRemoteAvatar(senderId, msg);
      } else if (msg.t === "world_init" && !this.isHost) {
        if (VH.Engine?.applyWorldSync) VH.Engine.applyWorldSync(msg);
        if (this.onHostModsOffered) this.onHostModsOffered(msg);
      } else if (msg.t === "blk") {
        if (this.onBlockRemote) this.onBlockRemote(msg.x, msg.y, msg.z, msg.id);
      } else if (msg.t === "chest_update") {
        if (VH.Engine?.applyChestSync) VH.Engine.applyChestSync(msg.dim, msg.x, msg.y, msg.z, msg.slots);
      } else if (msg.t === "furnace_update") {
        if (VH.Engine?.applyFurnaceSync) VH.Engine.applyFurnaceSync(msg.key, msg.state);
      } else if (msg.t === "drop_spawn") {
        const d = msg.drop;
        if (VH.Engine?.dropMgr && d) VH.Engine.dropMgr.spawn(d.itemId, d.count, d.x, d.y, d.z, d.vx || 0, d.vy || 1.5, d.vz || 0, d.id, true);
      } else if (msg.t === "drop_pickup") {
        if (VH.Engine?.dropMgr) VH.Engine.dropMgr.remove(msg.id, true);
      } else if (msg.t === "mob_sync" && !this.isHost) {
        if (VH.Engine?.mobMgr) VH.Engine.mobMgr.applyNetworkSnapshot(msg.mobs);
      } else if (msg.t === "mob_hit" && this.isHost) {
        if (VH.Engine?.mobMgr) {
          const mob = VH.Engine.mobMgr.mobs.find((m) => m.id === msg.id);
          if (mob) VH.Engine.mobMgr.damageMob(mob, msg.dmg || 3, msg.dirX || 0, msg.dirZ || 0, (dead) => VH.Engine.handleMobLoot(dead));
        }
      } else if (msg.t === "dim_sync" && !this.isHost) {
        if (VH.Engine?.switchDimension && VH.Engine.world?.dimension !== msg.dim) {
          VH.Engine.switchDimension(msg.dim, true);
        }
      } else if (msg.t === "chat") {
        if (this.onChatRemote) this.onChatRemote(msg.nick || "Игрок", msg.text);
      } else if (msg.t === "mod_pkt") {
        VH.modMgr.emit("onNetPacket", { peer: senderId, channel: msg.ch, data: msg.data });
        if (this.isHost) this.broadcast(Object.assign({}, msg, { senderId }), conn.peer);
      }
    });

    conn.on("close", () => {
      this.conns.delete(conn.peer);
      const rp = this.remotePlayers.get(conn.peer);
      if (rp) { this.scene.remove(rp.mesh); this.remotePlayers.delete(conn.peer); }
    });
  }

  _buildSkinForRemote(skinId, customPng) {
    if (skinId === "custom" && customPng && customPng.startsWith("data:image/")) {
      const img = new Image();
      const obj = { id: "custom", name: "Свой скин", skin: "#e2b28b", hair: "#333", shirt: "#2ed573", pants: "#334", eye: "#fff", customImg: null };
      img.onload = () => { obj.customImg = img; };
      img.src = customPng;
      return { obj, img };
    }
    return { obj: VH.DETAILED_SKINS.find((s) => s.id === skinId) || VH.DETAILED_SKINS[1], img: null };
  }

  _updateRemoteAvatar(pid, msg) {
    let rp = this.remotePlayers.get(pid);
    const skinChanged = rp && msg.skinId && (rp.skinId !== msg.skinId || (msg.customPng && rp.customPng !== msg.customPng));

    if (!rp || skinChanged) {
      const prevPos = rp ? { x: rp.x, y: rp.y, z: rp.z, yaw: rp.yaw } : { x: 0, y: 30, z: 0, yaw: 0 };
      if (rp) this.scene.remove(rp.mesh);

      const nick = msg.nick || (rp && rp.nick) || pid;
      const skinId = msg.skinId || (rp && rp.skinId) || "fox_officer";
      const customPng = msg.customPng || null;
      const { obj, img } = this._buildSkinForRemote(skinId, customPng);
      const mesh = VH.buildAvatarMesh(obj, nick);
      mesh.position.set(prevPos.x, prevPos.y, prevPos.z);
      this.scene.add(mesh);

      rp = { mesh, nick, skinId, customPng, x: prevPos.x, y: prevPos.y, z: prevPos.z, yaw: prevPos.yaw, walkPhase: 0 };
      this.remotePlayers.set(pid, rp);

      if (img) {
        img.onload = () => {
          obj.customImg = img;
          const cur = this.remotePlayers.get(pid);
          if (!cur || cur.customPng !== customPng) return;
          this.scene.remove(cur.mesh);
          const newMesh = VH.buildAvatarMesh(obj, cur.nick);
          newMesh.position.set(cur.x, cur.y, cur.z);
          newMesh.rotation.y = cur.yaw + Math.PI;
          this.scene.add(newMesh);
          cur.mesh = newMesh;
        };
      }
    }

    if (typeof msg.x === "number") {
      const distMoved = Math.hypot(msg.x - rp.x, msg.z - rp.z);
      rp.x = msg.x; rp.y = msg.y; rp.z = msg.z; rp.yaw = msg.yaw || 0;
      rp.mesh.position.set(rp.x, rp.y, rp.z);
      rp.mesh.rotation.y = rp.yaw + Math.PI;

      if (distMoved > 0.01) {
        rp.walkPhase += distMoved * 3.5;
        const sw = Math.sin(rp.walkPhase) * 0.65;
        if (rp.mesh.userData.legL) {
          rp.mesh.userData.legL.rotation.x = sw;
          rp.mesh.userData.legR.rotation.x = -sw;
          rp.mesh.userData.armL.rotation.x = -sw;
          rp.mesh.userData.armR.rotation.x = sw;
        }
      }
      if (rp.mesh.userData.tail) rp.mesh.userData.tail.rotation.y = Math.sin(performance.now() * 0.006) * 0.28;
      if (rp.mesh.userData.braids) rp.mesh.userData.braids.rotation.x = Math.sin(performance.now() * 0.005) * 0.12;
    }
  }

  broadcast(payload, skipPeerId = null) {
    for (const [pid, conn] of this.conns.entries()) {
      if (pid !== skipPeerId && conn.open) { try { conn.send(payload); } catch {} }
    }
  }
  sendPos(x, y, z, yaw) { if (this.conns.size) this.broadcast({ t: "pos", nick: this.nick, skinId: VH.activeSkinId || "fox_officer", x, y, z, yaw }); }
  sendSkin() { if (this.conns.size) this.broadcast({ t: "skin", nick: this.nick, skinId: VH.activeSkinId || "fox_officer", customPng: VH.activeCustomSkinData || null }); }
  sendBlock(x, y, z, id) { if (this.conns.size) this.broadcast({ t: "blk", x, y, z, id }); }
  sendChat(text) { if (this.conns.size) this.broadcast({ t: "chat", nick: this.nick, text }); }
  sendModPacket(ch, data) { if (this.conns.size) this.broadcast({ t: "mod_pkt", ch, data }); }

  disconnect() {
    for (const conn of this.conns.values()) { try { conn.close(); } catch {} }
    this.conns.clear();
    for (const rp of this.remotePlayers.values()) this.scene.remove(rp.mesh);
    this.remotePlayers.clear();
    if (this.peer) { try { this.peer.destroy(); } catch {} this.peer = null; }
    if (VH.modMgr.sessionMods) { VH.modMgr.sessionMods = null; VH.modMgr.reloadAll(); }
  }
}

// ============================================================================
// 3. МНОГОФАЙЛОВЫЕ ПРОЕКТЫ МОДОВ (.VHMOD) + ПОЛНЫЙ MODDING ENGINE
// ============================================================================
function createDefaultModProjects() {
  return [
    {
      id: "proj_anime_pack",
      name: "🎨 Anime Overhaul (Хаб + HD Текстуры + GUI)",
      enabled: true,
      files: {
        "manifest.json": JSON.stringify({ id: "proj_anime_pack", name: "Anime Overhaul", version: "3.2", entry: "scripts/main.js" }, null, 2),
        "scripts/main.js":
`// Главный визуальный мод: Аниме-Хаб, GUI и HD-текстуры блоков!
if (VH.setAnimeTheme) VH.setAnimeTheme(true);`
      }
    },
    {
      id: "proj_portal",
      name: "Быстрый Портал (/portal)",
      enabled: true,
      files: {
        "manifest.json": JSON.stringify({ id: "proj_portal", name: "Быстрый Портал", version: "1.0", entry: "scripts/main.js" }, null, 2),
        "scripts/main.js": `VH.Mods.registerCommand("portal", () => {\n  VH.Mods.buildPortal();\n  VH.Mods.toast("Портал в Ад построен перед вами!");\n});`,
        "textures/icon.json": JSON.stringify({ size: 16, pixels: [] }),
        "models/entity.vhmodel.json": JSON.stringify({ name: "custom_entity", bones: [{ name: "root", pivot: [0,0,0], cubes: [{ origin: [-4,0,-4], size: [8,8,8], color: "#2ed573" }] }] }, null, 2),
        "animations/entity.vhanim.json": JSON.stringify({ duration: 1.0, loop: true, tracks: { root: [{ time: 0, rot: [0,0,0] }, { time: 0.5, rot: [0,180,0] }, { time: 1.0, rot: [0,360,0] }] } }, null, 2)
      }
    },
    {
      id: "proj_gadgets",
      name: "Батут + Посох Молнии + HUD Кнопка (/gadgets)",
      enabled: false,
      files: {
        "manifest.json": JSON.stringify({ id: "proj_gadgets", name: "Гаджеты и Батут", version: "1.0", entry: "scripts/main.js" }, null, 2),
        "scripts/main.js":
`VH.Mods.registerBlock(50, {
  name: "Батут-Блок",
  color: "#2ed573",
  hard: 0.6,
  onStep: ({ player }) => { player.vy = 14.5; VH.Engine.playSfx("craft"); VH.Mods.toast("Прыжок на Батуте!"); }
});
VH.Mods.registerItem(130, {
  name: "Посох Молнии",
  color: "#ffdd33",
  shape: "stick",
  dmg: 14,
  onUse: ({ engine }) => { const h = engine.raycastBlock(24); if (h) engine.explodeAt(h.x, h.y, h.z, 2.6); }
});
VH.Mods.registerRecipe(50, 4, [[7, 2], [118, 1]], "2 Доски + Изумруд -> 4 Батута");
VH.Mods.registerCommand("gadgets", () => { VH.Mods.give(50, 8); VH.Mods.give(130, 1); VH.Mods.toast("Выданы Батут и Посох!"); });
VH.Mods.ui.addHudButton("btn-mod-jet", "⚡ БУСТ", () => { VH.Engine.player.vy = 11; VH.Engine.playSfx("craft"); });`,
        "textures/trampoline.json": JSON.stringify({ size: 16, pixels: [] }),
        "models/gadget.vhmodel.json": JSON.stringify({ name: "gadget", bones: [{ name: "root", pivot: [0,0,0], cubes: [{ origin: [-3,0,-3], size: [6,12,6], color: "#ffdd33" }] }] }, null, 2),
        "animations/gadget.vhanim.json": JSON.stringify({ duration: 1.0, loop: true, tracks: { root: [{ time: 0, rot: [0,0,0] }, { time: 1.0, rot: [0,360,0] }] } }, null, 2)
      }
    },
    {
      id: "proj_moon",
      name: "Измерение ЛУНА (/moon)",
      enabled: false,
      files: {
        "manifest.json": JSON.stringify({ id: "proj_moon", name: "Измерение Луна", version: "1.0", entry: "scripts/main.js" }, null, 2),
        "scripts/main.js":
`VH.Mods.registerDimension("moon", {
  name: "ЛУНА",
  skyColor: 0x050712,
  fogColor: 0x0b1024,
  gravity: 6.8,
  generateColumn: (data, lx, lz, wx, wz, WORLD_H, perlin, idx) => {
    const h = Math.floor(20 + perlin.fbm2(wx * 0.02, wz * 0.02, 3) * 10);
    for (let y = 0; y <= h; y++) data[idx(lx, y, lz)] = y === 0 ? 10 : (y === h ? 25 : 3);
  }
});
VH.Mods.registerCommand("moon", () => VH.Mods.switchDimension("moon"));`
      }
    }
  ];
}

const ALL_HOOKS = [
  "onInit", "onWorldStart", "onTick",
  "onBlockBreak", "onBlockPlace", "onBlockInteract", "onItemUse",
  "onMobSpawn", "onMobKill", "onPlayerHurt", "onDimensionChange",
  "onChat", "onNetPacket"
];

class ModManager {
  constructor() {
    this.hooks = {};
    this._resetHooks();
    this.logs = [];
    this.projects = createDefaultModProjects();
    this.sessionMods = null; // Временные моды хоста при игре по сети!
    this.worldModFilter = null; // Массив ID модов, выбранных для текущего мира (null = все enabled)
  }

  _resetHooks() {
    this.hooks = {};
    ALL_HOOKS.forEach((h) => (this.hooks[h] = []));
  }

  async initFromDB() {
    const saved = await VH.DB.get("mod_projects_v32", null);
    if (Array.isArray(saved) && saved.length > 0) {
      this.projects = saved;
    } else {
      await this.saveProjects();
    }
    this.reloadAll();
  }

  async saveProjects() {
    await VH.DB.set("mod_projects_v32", this.projects);
  }

  getActiveProjects() {
    if (this.sessionMods) return this.sessionMods;
    if (Array.isArray(this.worldModFilter)) {
      return this.projects.filter((p) => this.worldModFilter.includes(p.id));
    }
    return this.projects.filter((p) => p.enabled);
  }

  log(msg, isErr = false) {
    const line = (isErr ? "[ERR] " : "[MOD] ") + String(msg);
    this.logs.unshift(line);
    if (this.logs.length > 40) this.logs.pop();
    const box = document.getElementById("studio-console");
    if (box) box.textContent = this.logs.join("\n");
  }

  reloadAll() {
    this._resetHooks();
    VH.CUSTOM_COMMANDS = {};
    VH.CUSTOM_MOBS = {};
    VH.CUSTOM_DIMENSIONS = {};
    VH.Mods.ui.clearModUI();
    this.logs = [];

    const active = this.getActiveProjects();
    const animeOn = active.some((p) => p.id === "proj_anime_pack");
    if (VH.setAnimeTheme) VH.setAnimeTheme(animeOn);
    for (const proj of active) {
      try {
        // Исполняем все .js файлы проекта (сначала библиотеки, затем scripts/main.js)
        const files = proj.files || {};
        const jsPaths = Object.keys(files).filter((k) => k.endsWith(".js")).sort((a, b) => (a === "scripts/main.js" ? 1 : b === "scripts/main.js" ? -1 : a.localeCompare(b)));
        for (const jp of jsPaths) {
          const fn = new Function("VH", "Engine", "Mods", "THREE", "Project", files[jp]);
          fn(VH, VH.Engine, VH.Mods, window.THREE, proj);
        }
        this.log("Активен мод: " + proj.name);
      } catch (err) {
        this.log(proj.name + ": " + err.message, true);
      }
    }
    this.emit("onInit", { engine: VH.Engine });
  }

  emit(evName, payload = {}) {
    const list = this.hooks[evName];
    if (!list || list.length === 0) return payload;
    for (const cb of list) {
      try { cb(payload); } catch (err) { this.log(evName + ": " + err.message, true); }
    }
    return payload;
  }
}

const modMgr = new ModManager();
VH.MultiplayerManager = MultiplayerManager;
VH.modMgr = modMgr;

VH.Mods = {
  on: (ev, cb) => { if (!modMgr.hooks[ev]) modMgr.hooks[ev] = []; modMgr.hooks[ev].push(cb); },
  log: (msg) => modMgr.log(msg, false),
  registerBlock: (id, def) => {
    const numId = Number(id);
    if (!numId || numId < 1 || numId > 99) return;
    let faces = def.faces;
    if (!faces) {
      const tileIdx = VH.registerAtlasTile ? VH.registerAtlasTile(def.pixels || def.draw || def.color || "#2ed573") : 3;
      faces = [tileIdx, tileIdx, tileIdx];
    }
    VH.ITEMS_DB[numId] = Object.assign({
      name: "Мод-блок #" + numId,
      solid: def.solid !== undefined ? def.solid : true,
      hard: def.hard !== undefined ? def.hard : 1.2,
      drop: def.drop !== undefined ? def.drop : numId,
      transparent: Boolean(def.transparent),
      light: def.light || 0,
      faces
    }, def, { faces });
    if (VH.Engine?.renderHotbar) VH.Engine.renderHotbar();
  },
  registerItem: (id, def) => {
    const numId = Number(id);
    if (!numId || numId < 100) return;
    VH.ITEMS_DB[numId] = Object.assign({
      name: "Мод-предмет #" + numId,
      item: true,
      color: "#2ed573",
      shape: "gem",
      pixelIcon: def.pixels || null
    }, def, { item: true });
    if (VH.Engine?.renderHotbar) VH.Engine.renderHotbar();
  },
  registerMob: (type, def) => { VH.CUSTOM_MOBS[type] = def; },
  spawnMob: (type, x, y, z) => {
    if (!VH.Engine?.mobMgr) return null;
    const p = VH.Engine.player;
    return VH.Engine.mobMgr.spawnMob(type, x ?? p.x + 2, y ?? p.y + 1, z ?? p.z + 2);
  },
  registerDimension: (id, def) => { VH.CUSTOM_DIMENSIONS[id] = def; },
  switchDimension: (id) => { if (VH.Engine?.switchDimension) VH.Engine.switchDimension(id); },
  registerRecipe: (outId, cnt, req, desc) => { if (VH.Engine?.CRAFT_RECIPES) VH.Engine.CRAFT_RECIPES.push({ out: outId, cnt, req, desc }); },
  registerSmelt:  (inIdOrOut, outOrCnt, cntOrReq, desc) => { if (!VH.Engine?.SMELT_RECIPES) return; if (Array.isArray(cntOrReq)) { VH.Engine.SMELT_RECIPES.push({ inId: cntOrReq[0]?.[0], out: inIdOrOut, cnt: outOrCnt || 1, desc: desc || "" }); } else { VH.Engine.SMELT_RECIPES.push({ inId: inIdOrOut, out: outOrCnt, cnt: cntOrReq || 1, desc: desc || "" }); } },
  registerCommand: (name, handler) => { VH.CUSTOM_COMMANDS[(name.startsWith("/") ? name.slice(1) : name).toLowerCase()] = handler; },
  toast: (msg) => { if (VH.Engine?.showToast) VH.Engine.showToast(msg); },
  chat: (sender, msg) => { if (VH.Engine?.addChatLine) VH.Engine.addChatLine(sender, msg); },
  give: (id, cnt = 1) => { if (VH.Engine?.addInv) VH.Engine.addInv(id, cnt); },
  setBlock: (x, y, z, id) => { if (VH.Engine?.setBlock) VH.Engine.setBlock(x, y, z, id); },
  getBlock: (x, y, z) => (VH.Engine?.getBlock ? VH.Engine.getBlock(x, y, z) : 0),
  explode: (x, y, z, r = 2.5) => { if (VH.Engine?.explodeAt) VH.Engine.explodeAt(x, y, z, r); },
  buildPortal: () => { if (VH.Engine?.buildPortalFront) VH.Engine.buildPortalFront(); },
  sendNetPacket: (ch, data) => { if (VH.Engine?.mpMgr) VH.Engine.mpMgr.sendModPacket(ch, data); },
  ui: {
    addHudButton: (id, label, onClick, cssText = "") => {
      const host = document.getElementById("mod-hud-layer");
      if (!host) return null;
      const old = document.getElementById(id); if (old) old.remove();
      const btn = document.createElement("button");
      btn.id = id; btn.className = "mc-hud-btn mod-hud-btn"; btn.textContent = label;
      if (cssText) btn.style.cssText += ";" + cssText;
      btn.addEventListener("touchstart", (e) => { e.preventDefault(); onClick(VH.Engine); }, { passive: false });
      btn.addEventListener("click", () => onClick(VH.Engine));
      host.appendChild(btn);
      return btn;
    },
    addHudWidget: (id, html, cssText = "") => {
      const host = document.getElementById("mod-hud-layer");
      if (!host) return null;
      const old = document.getElementById(id); if (old) old.remove();
      const div = document.createElement("div");
      div.id = id; div.className = "mod-hud-widget"; div.innerHTML = html;
      if (cssText) div.style.cssText += ";" + cssText;
      host.appendChild(div);
      return div;
    },
    showModal: (title, htmlContent) => {
      const m = document.getElementById("mod-custom-modal");
      if (!m) return;
      document.getElementById("mod-custom-title").textContent = title || "Окно Мода";
      const body = document.getElementById("mod-custom-body");
      if (typeof htmlContent === "string") body.innerHTML = htmlContent;
      else { body.innerHTML = ""; body.appendChild(htmlContent); }
      m.classList.remove("hidden");
      if (VH.Engine) VH.Engine.setPaused(true);
    },
    closeModal: () => {
      const m = document.getElementById("mod-custom-modal");
      if (m) m.classList.add("hidden");
      if (VH.Engine) VH.Engine.setPaused(false);
    },
    clearModUI: () => {
      const host = document.getElementById("mod-hud-layer");
      if (host) host.innerHTML = "";
    }
  },
  storage: {
    get: (key, defVal = null) => VH.DB.get("moddata_" + key, defVal),
    set: (key, val) => VH.DB.set("moddata_" + key, val)
  }
};
