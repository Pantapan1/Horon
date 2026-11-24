// ============================================================================
// VOXELHORIZON v2.1 — P2P МУЛЬТИПЛЕЕР + ПОЛНЫЙ БЕЗГРАНИЧНЫЙ MODDING API (VH.Mods / VH.Engine)
// ============================================================================
window.VH = window.VH || {};
VH.CUSTOM_MOBS = {};
VH.CUSTOM_DIMENSIONS = {};
VH.CUSTOM_COMMANDS = {};

// ============================================================================
// 1. P2P МУЛЬТИПЛЕЕР (PEERJS)
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
    this.onCustomPacket = null; // Моды могут слать свои сетевые пакеты!
  }
  _genCode() { return "VH-" + Math.floor(1000 + Math.random() * 8999); }

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
      if (this.onChatRemote) this.onChatRemote("Система", "Комната " + id + " открыта!");
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

  _attachConn(conn, onStatus) {
    conn.on("open", () => {
      this.conns.set(conn.peer, conn);
      if (onStatus) onStatus("Подключено к комнате " + this.roomCode + "!");
      if (this.onChatRemote) this.onChatRemote("Сеть", "Подключён игрок: " + conn.peer);
      conn.send({ t: "hello", nick: this.nick, skinId: VH.activeSkinId || "explorer" });
    });
    conn.on("data", (msg) => {
      if (!msg || !msg.t) return;
      if (msg.t === "hello" || msg.t === "pos") {
        this._updateRemoteAvatar(conn.peer, msg);
        if (this.isHost && msg.t === "pos") this.broadcast(msg, conn.peer);
      } else if (msg.t === "blk") {
        if (this.onBlockRemote) this.onBlockRemote(msg.x, msg.y, msg.z, msg.id);
        if (this.isHost) this.broadcast(msg, conn.peer);
      } else if (msg.t === "chat") {
        if (this.onChatRemote) this.onChatRemote(msg.nick || "Игрок", msg.text);
        if (this.isHost) this.broadcast(msg, conn.peer);
      } else if (msg.t === "mod_pkt") {
        VH.modMgr.emit("onNetPacket", { peer: conn.peer, channel: msg.ch, data: msg.data });
        if (this.isHost) this.broadcast(msg, conn.peer);
      }
    });
    conn.on("close", () => {
      this.conns.delete(conn.peer);
      const rp = this.remotePlayers.get(conn.peer);
      if (rp) { this.scene.remove(rp.mesh); this.remotePlayers.delete(conn.peer); }
    });
  }

  _updateRemoteAvatar(pid, msg) {
    let rp = this.remotePlayers.get(pid);
    if (!rp) {
      const skinObj = VH.SKINS_DB.find((s) => s.id === msg.skinId) || VH.SKINS_DB[1];
      const mesh = VH.buildAvatarMesh(skinObj);
      this.scene.add(mesh);
      rp = { mesh, nick: msg.nick || pid, x: 0, y: 30, z: 0, yaw: 0 };
      this.remotePlayers.set(pid, rp);
    }
    if (typeof msg.x === "number") {
      rp.mesh.position.set(msg.x, msg.y, msg.z);
      rp.mesh.rotation.y = (msg.yaw || 0) + Math.PI;
    }
  }

  broadcast(payload, skipPeerId = null) {
    for (const [pid, conn] of this.conns.entries()) {
      if (pid !== skipPeerId && conn.open) { try { conn.send(payload); } catch {} }
    }
  }
  sendPos(x, y, z, yaw) { if (this.conns.size) this.broadcast({ t: "pos", nick: this.nick, skinId: VH.activeSkinId || "explorer", x, y, z, yaw }); }
  sendBlock(x, y, z, id) { if (this.conns.size) this.broadcast({ t: "blk", x, y, z, id }); }
  sendChat(text) { if (this.conns.size) this.broadcast({ t: "chat", nick: this.nick, text }); }
  sendModPacket(ch, data) { if (this.conns.size) this.broadcast({ t: "mod_pkt", ch, data }); }
  disconnect() {
    for (const c of this.conns.values()) { try { c.close(); } catch {} }
    this.conns.clear();
    for (const rp of this.remotePlayers.values()) this.scene.remove(rp.mesh);
    this.remotePlayers.clear();
    if (this.peer) { try { this.peer.destroy(); } catch {} this.peer = null; }
  }
}

// ============================================================================
// 2. ВСТРОЕННЫЕ ПРИМЕРЫ МОДОВ (ПОКАЗЫВАЮТ ПОЛНЫЙ ДОСТУП КО ВСЕМУ ДВИЖКУ!)
// ============================================================================
const DEFAULT_MODS = [
  {
    id: "mod_fast_portal",
    name: "Быстрый Портал (/portal)",
    enabled: true,
    code:
`VH.Mods.registerCommand("portal", () => {
  VH.Mods.buildPortal();
  VH.Mods.toast("Портал в Ад построен перед вами!");
});`
  },
  {
    id: "mod_gadgets_pack",
    name: "Мод: Батут-Блок + Посох Молнии + HUD Кнопка",
    enabled: false,
    code:
`// 1. Новый блок: Батут (ID 50) с собственной текстурой и физикой прыжка!
VH.Mods.registerBlock(50, {
  name: "Батут-Блок",
  color: "#2ed573",
  hard: 0.6,
  onStep: ({ player }) => {
    player.vy = 14.5;
    VH.Engine.playSfx("craft");
    VH.Mods.toast("Прыжок на Батуте!");
  }
});
// 2. Новый предмет: Посох Молнии (ID 130) — взрывает точку прицела!
VH.Mods.registerItem(130, {
  name: "Посох Молнии",
  color: "#ffdd33",
  shape: "stick",
  dmg: 14,
  onUse: ({ engine }) => {
    const hit = engine.raycastBlock(24);
    if (hit) { engine.explodeAt(hit.x, hit.y, hit.z, 2.6); }
  }
});
// 3. Рецепт крафта и выдача командой /gadgets
VH.Mods.registerRecipe(50, 4, [[7, 2], [118, 1]], "2 Доски + Изумруд -> 4 Батута");
VH.Mods.registerCommand("gadgets", () => {
  VH.Mods.give(50, 8); VH.Mods.give(130, 1);
  VH.Mods.toast("Выданы Батут-Блок и Посох Молнии!");
});
// 4. Своя кнопка прямо поверх игрового HUD!
VH.Mods.ui.addHudButton("btn-mod-jet", "⚡ БУСТ", () => {
  VH.Engine.player.vy = 11;
  VH.Engine.playSfx("craft");
});`
  },
  {
    id: "mod_moon_dim",
    name: "Мод: Измерение ЛУНА (/moon) с низкой гравитацией",
    enabled: false,
    code:
`VH.Mods.registerDimension("moon", {
  name: "ЛУНА",
  skyColor: 0x050712,
  fogColor: 0x0b1024,
  gravity: 6.8,
  generateColumn: (data, lx, lz, wx, wz, WORLD_H, perlin, idx) => {
    const h = Math.floor(20 + perlin.fbm2(wx * 0.02, wz * 0.02, 3) * 10);
    for (let y = 0; y <= h; y++) {
      data[idx(lx, y, lz)] = y === 0 ? 10 : (y === h ? 25 : 3);
    }
  }
});
VH.Mods.registerCommand("moon", () => {
  VH.Mods.switchDimension("moon");
});`
  },
  {
    id: "mod_veinminer",
    name: "Супер-Кирка 3x3 (Алмазная кирка)",
    enabled: false,
    code:
`VH.Mods.on("onBlockBreak", (e) => {
  if (e.heldId === 109) {
    for (let dx = -1; dx <= 1; dx++)
      for (let dy = -1; dy <= 1; dy++)
        if (dx || dy) VH.Mods.setBlock(e.x + dx, e.y + dy, e.z, 0);
  }
});`
  }
];

// ============================================================================
// 3. ЯДРО MODDING ENGINE (VH.modMgr + VH.Mods)
// ============================================================================
const ALL_HOOKS = [
  "onInit", "onWorldStart", "onTick",
  "onBlockBreak", "onBlockPlace", "onBlockInteract", "onItemUse",
  "onMobSpawn", "onMobKill", "onPlayerHurt", "onDimensionChange",
  "onChat", "onKeyDown", "onNetPacket"
];

class ModManager {
  constructor() {
    this.hooks = {};
    this._resetHooks();
    this.logs = [];
    this.mods = this._loadMods();
    this.runtime = null;
  }

  _resetHooks() {
    this.hooks = {};
    ALL_HOOKS.forEach((h) => (this.hooks[h] = []));
  }

  _loadMods() {
    try {
      const raw = localStorage.getItem("vh_user_mods_v21");
      if (raw) return JSON.parse(raw);
    } catch {}
    return DEFAULT_MODS.slice();
  }

  saveMods() {
    try { localStorage.setItem("vh_user_mods_v21", JSON.stringify(this.mods)); } catch {}
  }

  log(msg, isErr = false) {
    const line = (isErr ? "[ERR] " : "[MOD] ") + String(msg);
    this.logs.unshift(line);
    if (this.logs.length > 30) this.logs.pop();
    const box = document.getElementById("mod-console-log");
    if (box) box.textContent = this.logs.join("\n");
  }

  addMod(name, code) {
    const mod = { id: "mod_" + Date.now(), name: name || "Новый мод", enabled: true, code };
    this.mods.push(mod);
    this.saveMods();
    this.reloadAll();
    return mod;
  }

  updateMod(id, name, code) {
    const m = this.mods.find((x) => x.id === id);
    if (!m) return;
    m.name = name || m.name;
    m.code = code;
    this.saveMods();
    this.reloadAll();
  }

  toggleMod(id) {
    const m = this.mods.find((x) => x.id === id);
    if (!m) return;
    m.enabled = !m.enabled;
    this.saveMods();
    this.reloadAll();
  }

  removeMod(id) {
    this.mods = this.mods.filter((x) => x.id !== id);
    this.saveMods();
    this.reloadAll();
  }

  reloadAll() {
    this._resetHooks();
    VH.CUSTOM_COMMANDS = {};
    VH.Mods.ui.clearModUI();
    this.logs = [];

    for (const m of this.mods) {
      if (!m.enabled) continue;
      try {
        const fn = new Function("VH", "Engine", "Mods", "THREE", m.code);
        fn(VH, VH.Engine, VH.Mods, window.THREE);
        this.log("Загружен: " + m.name);
      } catch (err) {
        this.log(m.name + ": " + err.message, true);
      }
    }
    this.emit("onInit", { engine: VH.Engine });
  }

  emit(evName, payload = {}) {
    const list = this.hooks[evName];
    if (!list || list.length === 0) return payload;
    for (const cb of list) {
      try { cb(payload); } catch (err) { this.log(evName + " error: " + err.message, true); }
    }
    return payload;
  }
}

const modMgr = new ModManager();

VH.MultiplayerManager = MultiplayerManager;
VH.modMgr = modMgr;

// ПОЛНЫЙ API ДЛЯ МОДОВ (МОЖНО ДЕЛАТЬ ВСЁ ЧТО УГОДНО!)
VH.Mods = {
  // 1. Хуки событий (можно отменять через e.cancel = true!)
  on: (ev, cb) => {
    if (!modMgr.hooks[ev]) modMgr.hooks[ev] = [];
    modMgr.hooks[ev].push(cb);
  },
  log: (msg) => modMgr.log(msg, false),

  // 2. Регистрация новых Блоков (с текстурой, физикой и событиями!)
  registerBlock: (id, def) => {
    const numId = Number(id);
    if (!numId || numId < 1 || numId > 99) return;
    let faces = def.faces;
    if (!faces) {
      const tileIdx = VH.registerAtlasTile ? VH.registerAtlasTile(def.draw || def.color || "#2ed573") : 3;
      faces = [tileIdx, tileIdx, tileIdx];
    }
    VH.ITEMS_DB[numId] = Object.assign({
      name: "Мод-блок #" + numId,
      solid: def.solid !== undefined ? def.solid : true,
      hard: def.hard !== undefined ? def.hard : 1.2,
      drop: def.drop !== undefined ? def.drop : numId,
      transparent: Boolean(def.transparent),
      light: Boolean(def.light),
      faces
    }, def, { faces });
    if (VH.Engine && VH.Engine.renderHotbar) VH.Engine.renderHotbar();
  },

  // 3. Регистрация новых Предметов / Оружия / Инструментов / Гаджетов!
  registerItem: (id, def) => {
    const numId = Number(id);
    if (!numId || numId < 100) return;
    VH.ITEMS_DB[numId] = Object.assign({
      name: "Мод-предмет #" + numId,
      item: true,
      color: "#2ed573",
      shape: "gem"
    }, def, { item: true });
    if (VH.Engine && VH.Engine.renderHotbar) VH.Engine.renderHotbar();
  },

  // 4. Регистрация новых 3D Мобов и их спавн!
  registerMob: (type, def) => { VH.CUSTOM_MOBS[type] = def; },
  spawnMob: (type, x, y, z) => {
    if (!VH.Engine || !VH.Engine.mobMgr) return null;
    const p = VH.Engine.player;
    return VH.Engine.mobMgr.spawnMob(type, x ?? p.x + 2, y ?? p.y + 1, z ?? p.z + 2);
  },

  // 5. Регистрация новых Измерений (со своим небом, гравитацией и генератором!)
  registerDimension: (id, def) => { VH.CUSTOM_DIMENSIONS[id] = def; },
  switchDimension: (id) => { if (VH.Engine?.switchDimension) VH.Engine.switchDimension(id); },

  // 6. Рецепты крафта, переплавки и команды чата
  registerRecipe: (outId, cnt, req, desc) => { if (VH.Engine?.CRAFT_RECIPES) VH.Engine.CRAFT_RECIPES.push({ out: outId, cnt, req, desc }); },
  registerSmelt:  (outId, cnt, req, desc) => { if (VH.Engine?.SMELT_RECIPES) VH.Engine.SMELT_RECIPES.push({ out: outId, cnt, req, desc }); },
  registerCommand: (name, handler) => { VH.CUSTOM_COMMANDS[(name.startsWith("/") ? name.slice(1) : name).toLowerCase()] = handler; },

  // 7. Мир, Игрок, Сеть, Звук
  toast: (msg) => { if (VH.Engine?.showToast) VH.Engine.showToast(msg); },
  chat: (sender, msg) => { if (VH.Engine?.addChatLine) VH.Engine.addChatLine(sender, msg); },
  give: (id, cnt = 1) => { if (VH.Engine?.addInv) VH.Engine.addInv(id, cnt); },
  setBlock: (x, y, z, id) => { if (VH.Engine?.setBlock) VH.Engine.setBlock(x, y, z, id); },
  getBlock: (x, y, z) => (VH.Engine?.getBlock ? VH.Engine.getBlock(x, y, z) : 0),
  explode: (x, y, z, r = 2.5) => { if (VH.Engine?.explodeAt) VH.Engine.explodeAt(x, y, z, r); },
  buildPortal: () => { if (VH.Engine?.buildPortalFront) VH.Engine.buildPortalFront(); },
  sendNetPacket: (ch, data) => { if (VH.Engine?.mpMgr) VH.Engine.mpMgr.sendModPacket(ch, data); },

  // 8. ПОЛНЫЙ ДОСТУП К UI И HUD (СВОИ КНОПКИ, ВИДЖЕТЫ И ОКНА!)
  ui: {
    addHudButton: (id, label, onClick, cssText = "") => {
      const host = document.getElementById("mod-hud-layer");
      if (!host) return null;
      const old = document.getElementById(id); if (old) old.remove();
      const btn = document.createElement("button");
      btn.id = id;
      btn.className = "mc-hud-btn mod-hud-btn";
      btn.textContent = label;
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
      div.id = id;
      div.className = "mod-hud-widget";
      div.innerHTML = html;
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

  // 9. Постоянное хранилище данных для модов
  storage: {
    get: (key, defVal = null) => {
      try { const v = localStorage.getItem("vh_moddata_" + key); return v !== null ? JSON.parse(v) : defVal; } catch { return defVal; }
    },
    set: (key, val) => {
      try { localStorage.setItem("vh_moddata_" + key, JSON.stringify(val)); } catch {}
    }
  }
};
