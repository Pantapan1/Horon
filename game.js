// ============================================================================
// VOXELHORIZON v3.0 — ГЛАВНЫЙ ДВИЖОК (RUST-WASM + CHESTS + ARMOR + BOW + BED + STUDIO)
// ============================================================================
(function () {
  "use strict";
  const $ = (id) => document.getElementById(id);

  const CRAFT_RECIPES = [
    { out: 7,   cnt: 4, req: [[5, 1]],                desc: "1 Дуб -> 4 Доски" },
    { out: 7,   cnt: 4, req: [[27, 1]],               desc: "1 Берёза -> 4 Доски" },
    { out: 105, cnt: 4, req: [[7, 2]],                desc: "2 Доски -> 4 Палки" },
    { out: 37,  cnt: 4, req: [[101, 1], [105, 1]],    desc: "Уголь + Палка -> 4 Факела" },
    { out: 17,  cnt: 1, req: [[7, 4]],                desc: "4 Доски -> Верстак" },
    { out: 35,  cnt: 1, req: [[7, 8]],                desc: "8 Досок -> Сундук (27 слотов)" },
    { out: 38,  cnt: 1, req: [[7, 3], [2, 2]],        desc: "3 Доски + 2 Земли -> Кровать" },
    { out: 18,  cnt: 1, req: [[4, 8]],                desc: "8 Булыжника -> Печь" },
    { out: 106, cnt: 1, req: [[7, 3], [105, 2]],      desc: "3 Доски + 2 Палки" },
    { out: 107, cnt: 1, req: [[4, 3], [105, 2]],      desc: "3 Булыжника + 2 Палки" },
    { out: 110, cnt: 1, req: [[4, 3], [105, 2]],      desc: "3 Булыжника + 2 Палки" },
    { out: 108, cnt: 1, req: [[102, 3], [105, 2]],    desc: "3 Железа + 2 Палки" },
    { out: 111, cnt: 1, req: [[102, 2], [105, 1]],    desc: "2 Железа + 1 Палка" },
    { out: 109, cnt: 1, req: [[104, 3], [105, 2]],    desc: "3 Алмаза + 2 Палки" },
    { out: 112, cnt: 1, req: [[104, 2], [105, 1]],    desc: "2 Алмаза + 1 Палка" },
    { out: 123, cnt: 1, req: [[105, 3], [118, 1]],    desc: "3 Палки + Изумруд -> Лук" },
    { out: 124, cnt: 8, req: [[105, 1], [4, 1]],      desc: "Палка + Булыжник -> 8 Стрел" },
    { out: 122, cnt: 1, req: [[121, 3]],              desc: "3 Семени/Пшеницы -> Хлеб" },
    { out: 125, cnt: 1, req: [[114, 4]],              desc: "4 Кожи/Мяса -> Кожаный шлем" },
    { out: 126, cnt: 1, req: [[102, 6]],              desc: "6 Железа -> Железный нагрудник" },
    { out: 127, cnt: 1, req: [[102, 5]],              desc: "5 Железа -> Железные поножи" },
    { out: 128, cnt: 1, req: [[104, 4]],              desc: "4 Алмаза -> Алмазные ботинки" },
    { out: 116, cnt: 1, req: [[102, 1], [25, 1]],     desc: "Железо + Гравий -> Огниво" },
    { out: 119, cnt: 1, req: [[102, 3]],              desc: "3 Железа -> Ведро воды" },
    { out: 23,  cnt: 2, req: [[15, 2], [101, 1]],     desc: "2 Стекла + Уголь -> Светокамень" },
    { out: 26,  cnt: 1, req: [[8, 4], [101, 2]],      desc: "4 Песка + 2 Угля -> Динамит" }
  ];

  const SMELT_RECIPES = [
    { out: 102, cnt: 1, req: [[12, 1], [101, 1]],     desc: "Железная руда + Уголь" },
    { out: 103, cnt: 1, req: [[13, 1], [101, 1]],     desc: "Золотая руда + Уголь" },
    { out: 15,  cnt: 2, req: [[8, 2], [101, 1]],      desc: "2 Песка + Уголь -> 2 Стекла" },
    { out: 115, cnt: 1, req: [[114, 1], [101, 1]],    desc: "Сырое мясо + Уголь -> Стейк" },
    { out: 3,   cnt: 2, req: [[4, 2], [101, 1]],      desc: "2 Булыжника + Уголь -> Камень" }
  ];

  let audioCtx = null;
  const SETTINGS = { renderDist: 3, sens: 1.0, sound: true, ctrlMode: "dpad" };
  const PHYSICS = { gravity: 21, jumpVelocity: 7.6, walkSpeed: 5.2, flySpeed: 9.8, reach: 5.5, timeScale: 1.0 };

  function playSfx(type) {
    if (!SETTINGS.sound) return;
    try {
      if (!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)();
      if (audioCtx.state === "suspended") audioCtx.resume();
      const t = audioCtx.currentTime;
      const osc = audioCtx.createOscillator(), gain = audioCtx.createGain();
      osc.connect(gain); gain.connect(audioCtx.destination);
      if (type === "break") {
        osc.type = "triangle"; osc.frequency.setValueAtTime(185, t); osc.frequency.exponentialRampToValueAtTime(60, t + 0.11);
        gain.gain.setValueAtTime(0.22, t); gain.gain.exponentialRampToValueAtTime(0.01, t + 0.12);
        osc.start(t); osc.stop(t + 0.12);
      } else if (type === "place") {
        osc.type = "sine"; osc.frequency.setValueAtTime(270, t); osc.frequency.exponentialRampToValueAtTime(140, t + 0.08);
        gain.gain.setValueAtTime(0.18, t); gain.gain.exponentialRampToValueAtTime(0.01, t + 0.09);
        osc.start(t); osc.stop(t + 0.09);
      } else if (type === "hit") {
        osc.type = "sawtooth"; osc.frequency.setValueAtTime(155, t); osc.frequency.exponentialRampToValueAtTime(52, t + 0.14);
        gain.gain.setValueAtTime(0.24, t); gain.gain.exponentialRampToValueAtTime(0.01, t + 0.15);
        osc.start(t); osc.stop(t + 0.15);
      } else if (type === "bow") {
        osc.type = "triangle"; osc.frequency.setValueAtTime(420, t); osc.frequency.exponentialRampToValueAtTime(140, t + 0.14);
        gain.gain.setValueAtTime(0.2, t); gain.gain.exponentialRampToValueAtTime(0.01, t + 0.15);
        osc.start(t); osc.stop(t + 0.15);
      } else if (type === "portal") {
        osc.type = "sine"; osc.frequency.setValueAtTime(220, t); osc.frequency.linearRampToValueAtTime(520, t + 0.45);
        gain.gain.setValueAtTime(0.22, t); gain.gain.exponentialRampToValueAtTime(0.01, t + 0.5);
        osc.start(t); osc.stop(t + 0.5);
      } else if (type === "boom") {
        osc.type = "sawtooth"; osc.frequency.setValueAtTime(95, t); osc.frequency.exponentialRampToValueAtTime(24, t + 0.48);
        gain.gain.setValueAtTime(0.36, t); gain.gain.exponentialRampToValueAtTime(0.01, t + 0.5);
        osc.start(t); osc.stop(t + 0.5);
      } else {
        osc.type = "sine"; osc.frequency.setValueAtTime(480, t); osc.frequency.setValueAtTime(660, t + 0.06);
        gain.gain.setValueAtTime(0.15, t); gain.gain.exponentialRampToValueAtTime(0.01, t + 0.15);
        osc.start(t); osc.stop(t + 0.15);
      }
    } catch {}
  }

  function showToast(msg) {
    const el = $("toast-msg");
    el.textContent = msg;
    el.classList.remove("hidden");
    clearTimeout(el._t);
    el._t = setTimeout(() => el.classList.add("hidden"), 2300);
  }

  function addChatLine(sender, text) {
    const log = $("chat-log");
    const div = document.createElement("div");
    div.className = "chat-line";
    div.textContent = `<${sender}> ${text}`;
    log.appendChild(div);
    while (log.children.length > 6) log.removeChild(log.firstChild);
    setTimeout(() => { if (div.parentNode) div.remove(); }, 10000);
  }

  let renderer, scene, camera, sunLight, ambLight, highlightBox;
  let fpArmGroup = null, playerAvatar = null;
  let atlasObj = null, voxelWorld = null, mobMgr = null, mpMgr = null;
  let particles = [];
  let running = false, paused = false;
  let currentWorldKey = "Новый Мир";
  let worldSeed = 123456;
  let worldTime = 0.28, dayCount = 1;
  let savedDimMods = { overworld: {}, nether: {} };
  let savedDimChests = { overworld: {}, nether: {} };
  let selectedWorldModIds = null;
  let pendingHostSync = null;

  const player = {
    x: 0.5, y: 36, z: 0.5,
    spawnPos: null,
    vx: 0, vy: 0, vz: 0,
    yaw: 0, pitch: -0.15,
    onGround: false, flying: false, sneaking: false,
    camThirdPerson: false,
    mode: "survival",
    hp: 20, maxHp: 20, food: 20, maxFood: 20,
    hungerTimer: 0, lavaTimer: 0, portalTimer: 0,
    walkBob: 0, armSwing: 0,
    selectedSlot: 0,
    skinId: localStorage.getItem("vh_skin_id") || "explorer",
    armor: { head: 0, chest: 0, legs: 0, feet: 0 },
    inv: {}
  };
  VH.activeSkinId = player.skinId;
  let hotbar = [108, 111, 123, 37, 35, 38, 17, 113, 26];

  function getTotalArmorDef() {
    let def = 0;
    for (const slot of ["head", "chest", "legs", "feet"]) {
      const id = player.armor[slot];
      if (id && VH.ITEMS_DB[id]) def += VH.ITEMS_DB[id].armorDef || 0;
    }
    return def;
  }

  function getActiveSkinObj() {
    return VH.SKINS_DB.find((s) => s.id === player.skinId) || VH.SKINS_DB[0];
  }

  function rebuildPlayerVisuals() {
    const s = getActiveSkinObj();
    if (fpArmGroup) camera.remove(fpArmGroup);
    fpArmGroup = new THREE.Group();
    const armMesh = new THREE.Mesh(new THREE.BoxGeometry(0.22, 0.22, 0.68), new THREE.MeshLambertMaterial({ color: s.skin }));
    armMesh.position.set(0.42, -0.34, -0.58);
    armMesh.rotation.set(0.25, -0.25, 0.12);
    fpArmGroup.add(armMesh);
    camera.add(fpArmGroup);

    if (playerAvatar) scene.remove(playerAvatar);
    playerAvatar = VH.buildAvatarMesh(s);
    playerAvatar.visible = player.camThirdPerson;
    scene.add(playerAvatar);
  }

  function spawnBreakParticles(x, y, z, colorHex = 0x8b6b42) {
    for (let i = 0; i < 7; i++) {
      const m = new THREE.Mesh(new THREE.BoxGeometry(0.14, 0.14, 0.14), new THREE.MeshLambertMaterial({ color: colorHex }));
      m.position.set(x + 0.5 + (Math.random() - 0.5) * 0.5, y + 0.5 + (Math.random() - 0.5) * 0.5, z + 0.5 + (Math.random() - 0.5) * 0.5);
      scene.add(m);
      particles.push({ mesh: m, vx: (Math.random() - 0.5) * 4, vy: 2 + Math.random() * 3, vz: (Math.random() - 0.5) * 4, life: 0.45 });
    }
  }

  function initThree() {
    const canvas = $("game-canvas");
    renderer = new THREE.WebGLRenderer({ canvas, antialias: false, powerPreference: "high-performance" });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5));
    renderer.setSize(window.innerWidth, window.innerHeight);

    scene = new THREE.Scene();
    scene.background = new THREE.Color(0x78b7ff);
    scene.fog = new THREE.Fog(0x78b7ff, 26, 62);

    camera = new THREE.PerspectiveCamera(72, window.innerWidth / window.innerHeight, 0.08, 120);
    scene.add(camera);

    ambLight = new THREE.AmbientLight(0xffffff, 0.68);
    scene.add(ambLight);
    sunLight = new THREE.DirectionalLight(0xfff6dd, 0.8);
    sunLight.position.set(30, 60, 25);
    scene.add(sunLight);

    atlasObj = VH.buildTextureAtlas();
    VH.atlasCanvas = atlasObj.canvas;
    VH.atlasTexture = atlasObj.texture;

    const edges = new THREE.EdgesGeometry(new THREE.BoxGeometry(1.008, 1.008, 1.008));
    highlightBox = new THREE.LineSegments(edges, new THREE.LineBasicMaterial({ color: 0x111111 }));
    highlightBox.visible = false;
    scene.add(highlightBox);

    mpMgr = new VH.MultiplayerManager(scene);
    mpMgr.onBlockRemote = (x, y, z, id) => { if (voxelWorld) voxelWorld.setBlock(x, y, z, id, true); };
    mpMgr.onChatRemote = (nick, txt) => addChatLine(nick, txt);
    mpMgr.onHostModsOffered = (syncMsg) => {
      pendingHostSync = syncMsg;
      const cnt = (syncMsg.projects || []).length;
      $("mp-mods-prompt-txt").textContent = `Сервер прислал сид ${syncMsg.seed} и моды (${cnt} шт.). Загрузить на сессию?`;
      $("mp-mods-prompt").classList.remove("hidden");
    };

    rebuildPlayerVisuals();

    window.addEventListener("resize", () => {
      camera.aspect = window.innerWidth / window.innerHeight;
      camera.updateProjectionMatrix();
      renderer.setSize(window.innerWidth, window.innerHeight);
    });
  }

  function addInv(id, cnt) {
    if (!id || cnt <= 0) return;
    player.inv[id] = (player.inv[id] || 0) + cnt;
    if (!hotbar.includes(id)) {
      const empty = hotbar.findIndex((s) => !s || !(player.inv[s] > 0));
      if (empty !== -1) hotbar[empty] = id;
    }
    renderHotbar();
  }

  function takeInv(id, cnt) {
    if (player.mode === "creative") return true;
    if ((player.inv[id] || 0) < cnt) return false;
    player.inv[id] -= cnt;
    if (player.inv[id] <= 0) delete player.inv[id];
    renderHotbar();
    return true;
  }

  function renderClassicStatusBars() {
    const hr = $("hearts-row"), fr = $("food-row"), ar = $("armor-row");
    hr.innerHTML = ""; fr.innerHTML = ""; ar.innerHTML = "";
    const fullH = Math.ceil(player.hp / 2);
    const fullF = Math.ceil(player.food / 2);
    const fullA = getTotalArmorDef();
    for (let i = 0; i < 10; i++) {
      const hs = document.createElement("span"); hs.className = "ico-heart" + (i < fullH ? "" : " empty"); hs.textContent = "♥"; hr.appendChild(hs);
      const fs = document.createElement("span"); fs.className = "ico-food" + (i < fullF ? "" : " empty"); fs.textContent = "🍗"; fr.appendChild(fs);
      if (fullA > 0) {
        const as = document.createElement("span"); as.className = "ico-armor" + (i < fullA ? "" : " empty"); as.textContent = "🛡"; ar.appendChild(as);
      }
    }
  }

  function renderHotbar() {
    const hb = $("hotbar");
    hb.innerHTML = "";
    for (let i = 0; i < 9; i++) {
      const id = hotbar[i] || 0;
      const cnt = player.mode === "creative" ? "∞" : (player.inv[id] || 0);
      const slot = document.createElement("div");
      slot.className = "mc-slot" + (i === player.selectedSlot ? " active" : "");
      if (id && (player.mode === "creative" || cnt > 0)) {
        const cv = document.createElement("canvas"); VH.drawItemIcon(cv, id);
        slot.appendChild(cv);
        const cEl = document.createElement("span"); cEl.className = "cnt"; cEl.textContent = cnt;
        slot.appendChild(cEl);
      }
      slot.onclick = () => {
        player.selectedSlot = i; renderHotbar();
        const nm = VH.ITEMS_DB[hotbar[i]]?.name;
        if (nm) showToast(nm);
      };
      hb.appendChild(slot);
    }
  }

  // --- ПЕРЕКЛЮЧЕНИЕ ИЗМЕРЕНИЙ ---
  function switchDimension(targetDim) {
    if (!voxelWorld || voxelWorld.dimension === targetDim) return;
    savedDimMods[voxelWorld.dimension] = Object.assign({}, voxelWorld.modifications);
    savedDimChests[voxelWorld.dimension] = Object.assign({}, voxelWorld.chests);
    voxelWorld.disposeAll();
    voxelWorld = new VH.VoxelWorld(scene, worldSeed, targetDim, savedDimMods[targetDim] || {}, savedDimChests[targetDim] || {});
    mobMgr.setWorld(voxelWorld);

    for (let cx = -1; cx <= 1; cx++) for (let cz = -1; cz <= 1; cz++) voxelWorld.ensureChunk(cx, cz);
    player.x = 2.5; player.z = 2.5;
    player.y = targetDim === "nether" ? 26.2 : voxelWorld.getTerrainHeight(2, 2) + 2.5;
    player.vx = player.vy = player.vz = 0;

    if (targetDim === "nether") {
      for (let dx = -2; dx <= 4; dx++) for (let dz = -2; dz <= 3; dz++) voxelWorld.setBlock(dx, 24, dz, 32, false);
      for (let dx = -2; dx <= 4; dx++) for (let dy = 25; dy <= 30; dy++) for (let dz = -2; dz <= 3; dz++) voxelWorld.setBlock(dx, dy, dz, 0, false);
      voxelWorld.buildNetherPortalAt(0, 25, 0);
    }

    playSfx("portal");
    const cd = VH.CUSTOM_DIMENSIONS[targetDim];
    VH.modMgr.emit("onDimensionChange", { dimension: targetDim, engine: VH.Engine });
    showToast(cd ? ("Измерение: " + (cd.name || targetDim)) : (targetDim === "nether" ? "Вы вошли в АД (Nether)!" : "Вы вернулись в Обычный мир!"));
  }

  // --- INDEXEDDB СОХРАНЕНИЕ / ЗАГРУЗКА / ЭКСПОРТ МИРОВ ---
  async function getSavedWorldsIndex() {
    return await VH.DB.get("worlds_index", []);
  }

  async function saveCurrentWorld(silent = false) {
    if (!voxelWorld) return;
    savedDimMods[voxelWorld.dimension] = Object.assign({}, voxelWorld.modifications);
    savedDimChests[voxelWorld.dimension] = Object.assign({}, voxelWorld.chests);
    const payload = {
      name: currentWorldKey, seed: worldSeed, mode: player.mode, dim: voxelWorld.dimension,
      time: worldTime, dayCount,
      worldModIds: selectedWorldModIds,
      player: { x: player.x, y: player.y, z: player.z, spawnPos: player.spawnPos, hp: player.hp, food: player.food, armor: player.armor, inv: player.inv },
      hotbar, dimMods: savedDimMods, dimChests: savedDimChests
    };
    await VH.DB.set("world_" + currentWorldKey, payload);
    const idx = await getSavedWorldsIndex();
    if (!idx.includes(currentWorldKey)) {
      idx.unshift(currentWorldKey);
      await VH.DB.set("worlds_index", idx.slice(0, 16));
    }
    if (!silent) showToast("Мир сохранён в IndexedDB!");
  }

  async function renderWorldsModal() {
    // Чекбоксы модов для нового мира
    const wrap = $("world-mods-checklist");
    wrap.innerHTML = "";
    if (!Array.isArray(selectedWorldModIds)) {
      selectedWorldModIds = VH.modMgr.projects.filter((p) => p.enabled).map((p) => p.id);
    }
    VH.modMgr.projects.forEach((p) => {
      const chip = document.createElement("div");
      const active = selectedWorldModIds.includes(p.id);
      chip.className = "mod-chip" + (active ? " active" : "");
      chip.textContent = (active ? "✓ " : "+ ") + p.name;
      chip.onclick = () => {
        if (selectedWorldModIds.includes(p.id)) selectedWorldModIds = selectedWorldModIds.filter((x) => x !== p.id);
        else selectedWorldModIds.push(p.id);
        renderWorldsModal();
      };
      wrap.appendChild(chip);
    });

    const box = $("saved-worlds-list");
    box.innerHTML = "";
    const list = await getSavedWorldsIndex();
    list.forEach((wn) => {
      const row = document.createElement("div"); row.className = "mc-list-row";
      row.innerHTML = `<span>${wn}</span><div class="mc-row"><button class="mc-btn tiny green">Играть</button><button class="mc-btn tiny red">✕</button></div>`;
      row.querySelector(".green").onclick = () => startWorld(wn, null, true);
      row.querySelector(".red").onclick = async () => {
        await VH.DB.del("world_" + wn);
        const nxt = (await getSavedWorldsIndex()).filter((x) => x !== wn);
        await VH.DB.set("worlds_index", nxt);
        renderWorldsModal();
      };
      box.appendChild(row);
    });
  }

  function hashStr(s) {
    let h = 2166136261;
    for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); }
    return Math.abs(h) % 999999;
  }

  async function startWorld(name, seedStr, loadExisting = false, forcedDim = "overworld") {
    currentWorldKey = (name || "Новый Мир").trim();
    let saved = null;
    if (loadExisting) {
      saved = await VH.DB.get("world_" + currentWorldKey, null);
    }

    if (voxelWorld) voxelWorld.disposeAll();
    if (mobMgr) mobMgr.clearAll();

    worldSeed = saved ? saved.seed : (seedStr ? parseInt(seedStr, 10) || hashStr(seedStr) : Math.floor(Math.random() * 999999));
    savedDimMods = saved && saved.dimMods ? saved.dimMods : { overworld: {}, nether: {} };
    savedDimChests = saved && saved.dimChests ? saved.dimChests : { overworld: {}, nether: {} };
    const startDim = saved && saved.dim ? saved.dim : forcedDim;

    if (saved && Array.isArray(saved.worldModIds)) {
      selectedWorldModIds = saved.worldModIds;
    }
    VH.modMgr.worldModFilter = selectedWorldModIds;
    VH.modMgr.reloadAll();

    voxelWorld = new VH.VoxelWorld(scene, worldSeed, startDim, savedDimMods[startDim] || {}, savedDimChests[startDim] || {});
    mobMgr = new VH.MobManager(scene, voxelWorld);

    if (saved) {
      player.mode = saved.mode || "survival";
      worldTime = saved.time || 0.28; dayCount = saved.dayCount || 1;
      player.x = saved.player.x; player.y = saved.player.y; player.z = saved.player.z;
      player.spawnPos = saved.player.spawnPos || null;
      player.hp = saved.player.hp || 20; player.food = saved.player.food || 20;
      player.armor = saved.player.armor || { head: 0, chest: 0, legs: 0, feet: 0 };
      player.inv = saved.player.inv || {};
      hotbar = saved.hotbar || [108, 111, 123, 37, 35, 38, 17, 113, 26];
    } else {
      player.mode = $("mode-creative").classList.contains("active") ? "creative" : "survival";
      worldTime = 0.28; dayCount = 1;
      player.x = 0.5; player.z = 0.5;
      player.y = voxelWorld.getTerrainHeight(0, 0) + 3.2;
      player.spawnPos = null;
      player.hp = 20; player.food = 20;
      player.armor = { head: 125, chest: 0, legs: 0, feet: 0 };
      player.inv = { 108: 1, 111: 1, 123: 1, 124: 16, 37: 12, 35: 2, 38: 1, 17: 1, 22: 14, 116: 1, 121: 6, 113: 6 };
      hotbar = player.mode === "creative"
        ? [1, 3, 7, 37, 35, 38, 22, 116, 26]
        : [108, 111, 123, 37, 35, 38, 116, 113, 26];
    }

    for (let cx = -1; cx <= 1; cx++) for (let cz = -1; cz <= 1; cz++) voxelWorld.ensureChunk(cx + Math.floor(player.x / 16), cz + Math.floor(player.z / 16));

    player.vx = player.vy = player.vz = 0; player.flying = false;
    running = true; paused = false;

    ["main-menu", "worlds-modal", "pause-modal", "death-modal", "mp-modal"].forEach((id) => $(id).classList.add("hidden"));
    $("hud").classList.remove("hidden");
    updateModeUI();
    renderHotbar();
    renderClassicStatusBars();
    VH.modMgr.emit("onWorldStart", { name: currentWorldKey, seed: worldSeed, engine: VH.Engine });
    addChatLine("Мир", `Загружен «${currentWorldKey}» (${VH.WasmCore ? "Rust-WASM Активен!" : "JS Core"})`);
  }

  function updateModeUI() {
    const isCre = player.mode === "creative";
    $("classic-bars").classList.toggle("hidden", isCre);
    $("btn-fly").classList.toggle("hidden", !isCre);
  }

  // --- RAYCAST, КОПАНИЕ, ЛУК, КРОВАТЬ, СУНДУКИ ---
  function raycastBlock(maxDist = 5.5) {
    const dir = new THREE.Vector3(0, 0, -1).applyEuler(new THREE.Euler(player.pitch, player.yaw, 0, "YXZ"));
    const ox = player.x, oy = player.y + 1.55, oz = player.z;
    let prevX = Math.floor(ox), prevY = Math.floor(oy), prevZ = Math.floor(oz);
    for (let d = 0.1; d <= maxDist; d += 0.08) {
      const bx = Math.floor(ox + dir.x * d), by = Math.floor(oy + dir.y * d), bz = Math.floor(oz + dir.z * d);
      if (bx === prevX && by === prevY && bz === prevZ) continue;
      const bId = voxelWorld.getBlock(bx, by, bz);
      if (bId && !VH.ITEMS_DB[bId]?.liquid && !VH.ITEMS_DB[bId]?.portal) {
        return { x: bx, y: by, z: bz, id: bId, px: prevX, py: prevY, pz: prevZ, dir };
      }
      prevX = bx; prevY = by; prevZ = bz;
    }
    return null;
  }

  let miningState = { x: 0, y: 0, z: 0, progress: 0 };

  function explodeAt(cx, cy, cz, rad = 2.4) {
    playSfx("boom");
    const r = Math.ceil(rad);
    for (let dx = -r; dx <= r; dx++) for (let dy = -r; dy <= r; dy++) for (let dz = -r; dz <= r; dz++) {
      if (dx*dx + dy*dy + dz*dz <= rad*rad && cy + dy > 1) {
        voxelWorld.setBlock(cx + dx, cy + dy, cz + dz, 0, true);
        mpMgr.sendBlock(cx + dx, cy + dy, cz + dz, 0);
      }
    }
    spawnBreakParticles(cx, cy, cz, 0xff7722);
  }

  function tryAttackOrMine(dt, isSingleTap = false) {
    player.armSwing = 1.0;
    const dir = new THREE.Vector3(0, 0, -1).applyEuler(new THREE.Euler(player.pitch, player.yaw, 0, "YXZ"));
    const heldId = hotbar[player.selectedSlot] || 0;
    const heldInfo = VH.ITEMS_DB[heldId];

    if (isSingleTap && mobMgr) {
      const dmg = (heldInfo && heldInfo.dmg) ? heldInfo.dmg : 3;
      if (mobMgr.hitMobAtRay({ x: player.x, y: player.y + 1.55, z: player.z }, dir, 4.4, dmg, (dead) => handleMobLoot(dead))) {
        playSfx("hit");
        return;
      }
    }

    const hit = raycastBlock(PHYSICS.reach);
    if (!hit) { miningState.progress = 0; $("mine-bar").classList.add("hidden"); return; }
    const bInfo = VH.ITEMS_DB[hit.id];
    if (!bInfo || bInfo.hard >= 999) return;

    if (player.mode === "creative") {
      if (isSingleTap) {
        voxelWorld.setBlock(hit.x, hit.y, hit.z, 0, true);
        mpMgr.sendBlock(hit.x, hit.y, hit.z, 0);
        spawnBreakParticles(hit.x, hit.y, hit.z);
        playSfx("break");
        VH.modMgr.emit("onBlockBreak", { x: hit.x, y: hit.y, z: hit.z, id: hit.id, heldId });
      }
      return;
    }

    if (hit.x !== miningState.x || hit.y !== miningState.y || hit.z !== miningState.z) {
      miningState = { x: hit.x, y: hit.y, z: hit.z, progress: 0 };
    }
    let speed = 1.0;
    if (heldInfo && heldInfo.toolType === bInfo.tool) speed = heldInfo.power || 2.5;
    miningState.progress += (dt * speed) / Math.max(0.18, bInfo.hard);

    $("mine-bar").classList.remove("hidden");
    $("mine-fill").style.width = Math.min(100, miningState.progress * 100) + "%";

    if (miningState.progress >= 1.0) {
      const brkEv = VH.modMgr.emit("onBlockBreak", { x: hit.x, y: hit.y, z: hit.z, id: hit.id, heldId, cancel: false });
      if (brkEv.cancel) { miningState.progress = 0; $("mine-bar").classList.add("hidden"); return; }
      if (typeof bInfo.onBreak === "function") { try { bInfo.onBreak({ x: hit.x, y: hit.y, z: hit.z, id: hit.id, player, engine: VH.Engine }); } catch {} }
      voxelWorld.setBlock(hit.x, hit.y, hit.z, 0, true);
      mpMgr.sendBlock(hit.x, hit.y, hit.z, 0);
      spawnBreakParticles(hit.x, hit.y, hit.z);
      playSfx("break");
      if (bInfo.drop) addInv(bInfo.drop, 1);
      miningState.progress = 0;
      $("mine-bar").classList.add("hidden");
    }
  }

  function handleMobLoot(dead) {
    const drops = { sheep:114, cow:114, pig:114, chicken:114, zombie:101, skeleton:124, creeper:26, spider:105, enderman:104, piglin:103, blaze:23, ghast:104, inferno_lord:129 };
    addInv(dead.drop || drops[dead.type] || 101, dead.isBoss ? 1 : 1);
    showToast(dead.isBoss ? "БОСС ПОВЕРЖЕН! Получена Звезда Преисподней!" : ("Побеждён: " + dead.name));
  }

  function tryPlaceOrUse() {
    player.armSwing = 1.0;
    const heldId = hotbar[player.selectedSlot] || 0;
    const heldInfo = VH.ITEMS_DB[heldId];

    const useEv = VH.modMgr.emit("onItemUse", { heldId, heldInfo, player, engine: VH.Engine, cancel: false });
    if (useEv.cancel) return;
    if (heldInfo && typeof heldInfo.onUse === "function") {
      try { if (heldInfo.onUse({ heldId, player, engine: VH.Engine }) !== false) return; } catch {}
    }

    // 1. Стрельба из Лука (ID 123)
    if (heldInfo && heldInfo.rangedBow) {
      if (player.mode === "creative" || takeInv(124, 1)) {
        const dir = new THREE.Vector3(0, 0, -1).applyEuler(new THREE.Euler(player.pitch, player.yaw, 0, "YXZ"));
        const from = { x: player.x + dir.x * 0.7, y: player.y + 1.5, z: player.z + dir.z * 0.7 };
        const to   = { x: from.x + dir.x * 15, y: from.y + dir.y * 15 + 1.2, z: from.z + dir.z * 15 };
        mobMgr.fireProjectile(from, to, heldInfo.dmg || 9, false, true);
        playSfx("bow");
      } else {
        showToast("Нет стрел (ID 124)!");
      }
      return;
    }

    // 2. Надеть броню из руки
    if (heldInfo && heldInfo.armorSlot) {
      const prev = player.armor[heldInfo.armorSlot];
      player.armor[heldInfo.armorSlot] = heldId;
      takeInv(heldId, 1);
      if (prev) addInv(prev, 1);
      renderClassicStatusBars();
      playSfx("place");
      showToast("Надето: " + heldInfo.name);
      return;
    }

    // 3. Съесть еду
    if (heldInfo && heldInfo.healFood && player.mode === "survival" && (player.food < 20 || player.hp < 20)) {
      if (takeInv(heldId, 1)) {
        player.food = Math.min(20, player.food + heldInfo.healFood);
        player.hp = Math.min(20, player.hp + (heldInfo.healHp || 0));
        renderClassicStatusBars();
        playSfx("craft");
        return;
      }
    }

    const hit = raycastBlock(PHYSICS.reach);
    if (!hit) return;

    // 4. Огниво по Обсидиану -> Портал в Ад
    if (heldInfo && heldInfo.ignitePortal && hit.id === 22) {
      voxelWorld.buildNetherPortalAt(hit.x - 1, hit.y, hit.z);
      playSfx("portal");
      showToast("Портал в Ад активирован!");
      return;
    }

    // 5. Ведро воды или лавы
    if (heldInfo && heldInfo.placeLiquid) {
      voxelWorld.setBlock(hit.px, hit.py, hit.pz, heldInfo.placeLiquid, true);
      mpMgr.sendBlock(hit.px, hit.py, hit.pz, heldInfo.placeLiquid);
      playSfx("place");
      return;
    }

    // 6. Посадка семян пшеницы на дёрн/землю
    if (heldInfo && heldInfo.plantCrop && (hit.id === 1 || hit.id === 2)) {
      if (takeInv(heldId, 1)) {
        voxelWorld.setBlock(hit.x, hit.y + 1, hit.z, heldInfo.plantCrop, false);
        playSfx("place");
      }
      return;
    }

    // 7. Интерактивные блоки (Сундук 27 слотов, Кровать, Верстак, Печь, TNT)
    const tInfo = VH.ITEMS_DB[hit.id];
    const intEv = VH.modMgr.emit("onBlockInteract", { x: hit.x, y: hit.y, z: hit.z, id: hit.id, heldId, cancel: false });
    if (intEv.cancel) return;
    if (tInfo && typeof tInfo.onInteract === "function") { try { tInfo.onInteract({ x: hit.x, y: hit.y, z: hit.z, id: hit.id, player, engine: VH.Engine }); } catch {} return; }

    if (tInfo && tInfo.interactive === "chest") { openChestModal(hit.x, hit.y, hit.z); return; }
    if (tInfo && tInfo.interactive === "bed") {
      player.spawnPos = { x: hit.x + 0.5, y: hit.y + 1.1, z: hit.z + 0.5, dim: voxelWorld.dimension };
      worldTime = 0.26;
      showToast("Точка возрождения установлена! Наступило утро.");
      return;
    }
    if (tInfo && tInfo.interactive === "craft") { openInvModal("craft"); return; }
    if (tInfo && tInfo.interactive === "smelt") { openInvModal("smelt"); return; }
    if (tInfo && tInfo.interactive === "tnt")   { explodeAt(hit.x, hit.y, hit.z, 2.8); return; }

    // 8. Установка блока
    if (!heldId || !heldInfo || heldInfo.item) return;
    const inPlayer = Math.abs(hit.px + 0.5 - player.x) < 0.7 && Math.abs(hit.pz + 0.5 - player.z) < 0.7 && (hit.py === Math.floor(player.y) || hit.py === Math.floor(player.y + 1));
    if (inPlayer && heldInfo.solid) return;

    const plEv = VH.modMgr.emit("onBlockPlace", { x: hit.px, y: hit.py, z: hit.pz, id: heldId, cancel: false });
    if (plEv.cancel) return;
    if (takeInv(heldId, 1)) {
      voxelWorld.setBlock(hit.px, hit.py, hit.pz, heldId, true);
      mpMgr.sendBlock(hit.px, hit.py, hit.pz, heldId);
      playSfx("place");
      if (typeof heldInfo.onPlace === "function") { try { heldInfo.onPlace({ x: hit.px, y: hit.py, z: hit.pz, id: heldId, player, engine: VH.Engine }); } catch {} }
    }
  }

  // --- 27-СЛОТОВЫЙ СУНДУК ---
  let openChestCoords = null;
  function openChestModal(x, y, z) {
    paused = true;
    openChestCoords = { x, y, z };
    $("chest-modal").classList.remove("hidden");
    renderChestModal();
  }

  function renderChestModal() {
    if (!openChestCoords || !voxelWorld) return;
    const items = voxelWorld.getChestItems(openChestCoords.x, openChestCoords.y, openChestCoords.z);
    const cGrid = $("chest-grid");
    cGrid.innerHTML = "";
    items.forEach((slotObj, idx) => {
      const cell = document.createElement("div"); cell.className = "chest-cell";
      if (slotObj && slotObj.id && slotObj.cnt > 0) {
        const cv = document.createElement("canvas"); VH.drawItemIcon(cv, slotObj.id);
        cell.appendChild(cv);
        const sp = document.createElement("span"); sp.textContent = slotObj.cnt;
        cell.appendChild(sp);
        cell.onclick = () => {
          addInv(slotObj.id, slotObj.cnt);
          items[idx] = null;
          renderChestModal();
        };
      }
      cGrid.appendChild(cell);
    });

    const pGrid = $("chest-player-grid");
    pGrid.innerHTML = "";
    Object.keys(player.inv).map(Number).filter((id) => player.inv[id] > 0).forEach((id) => {
      const cell = document.createElement("div"); cell.className = "chest-cell";
      const cv = document.createElement("canvas"); VH.drawItemIcon(cv, id);
      cell.appendChild(cv);
      const sp = document.createElement("span"); sp.textContent = player.inv[id];
      cell.appendChild(sp);
      cell.onclick = () => {
        const emptyIdx = items.findIndex((s) => !s);
        if (emptyIdx === -1) { showToast("Сундук полон!"); return; }
        const amt = player.inv[id];
        if (takeInv(id, amt)) {
          items[emptyIdx] = { id, cnt: amt };
          renderChestModal();
        }
      };
      pGrid.appendChild(cell);
    });
  }

  // --- ИНВЕНТАРЬ, 4 СЛОТА БРОНИ, КРАФТ И ПЕЧЬ ---
  let invTab = "bag";
  function openInvModal(tab = "bag") {
    paused = true; invTab = tab;
    if (document.pointerLockElement) document.exitPointerLock();
    $("inv-modal").classList.remove("hidden");
    renderInvModal();
  }

  function renderInvModal() {
    // 4 слота брони сверху
    const ab = $("armor-slots-bar");
    ab.innerHTML = "<b style='color:#fff;font-size:11px'>Броня (" + getTotalArmorDef() + "🛡):</b>";
    const labels = { head: "Шлем", chest: "Грудь", legs: "Ноги", feet: "Ботинки" };
    for (const sk of ["head", "chest", "legs", "feet"]) {
      const aid = player.armor[sk];
      const btn = document.createElement("button");
      btn.className = "mc-btn tiny " + (aid ? "green" : "");
      btn.textContent = aid ? `${labels[sk]}: ${VH.ITEMS_DB[aid]?.name}` : `${labels[sk]}: Пусто`;
      btn.onclick = () => {
        if (player.armor[sk]) { addInv(player.armor[sk], 1); player.armor[sk] = 0; renderClassicStatusBars(); renderInvModal(); }
      };
      ab.appendChild(btn);
    }

    ["bag", "craft", "smelt"].forEach((t) => $("tab-" + t).classList.toggle("active", invTab === t));
    const box = $("inv-content");
    box.innerHTML = "";
    if (invTab === "bag") {
      const ids = player.mode === "creative"
        ? Object.keys(VH.ITEMS_DB).map(Number).filter((id) => id > 0)
        : Object.keys(player.inv).map(Number).filter((id) => player.inv[id] > 0);
      ids.forEach((id) => {
        const info = VH.ITEMS_DB[id];
        const card = document.createElement("div"); card.className = "mc-card";
        const cv = document.createElement("canvas"); VH.drawItemIcon(cv, id);
        const cnt = player.mode === "creative" ? "∞" : player.inv[id];
        card.innerHTML = `<div class="mc-card-head"></div><div class="mc-card-sub">В наличии: ${cnt}</div><button class="mc-btn tiny green">${info.armorSlot ? "Надеть" : "В руку"}</button>`;
        card.querySelector(".mc-card-head").appendChild(cv);
        card.querySelector(".mc-card-head").append(info.name);
        card.querySelector("button").onclick = () => {
          if (info.armorSlot) {
            const prev = player.armor[info.armorSlot];
            player.armor[info.armorSlot] = id;
            takeInv(id, 1);
            if (prev) addInv(prev, 1);
            renderClassicStatusBars(); renderInvModal();
          } else {
            hotbar[player.selectedSlot] = id; renderHotbar(); showToast("Выбрано: " + info.name);
          }
        };
        box.appendChild(card);
      });
      return;
    }
    const list = invTab === "craft" ? CRAFT_RECIPES : SMELT_RECIPES;
    list.forEach((rec) => {
      const can = player.mode === "creative" || rec.req.every(([rid, rcnt]) => (player.inv[rid] || 0) >= rcnt);
      const info = VH.ITEMS_DB[rec.out];
      const card = document.createElement("div"); card.className = "mc-card";
      const cv = document.createElement("canvas"); VH.drawItemIcon(cv, rec.out);
      card.innerHTML = `<div class="mc-card-head"></div><div class="mc-card-sub">${rec.desc}</div><button class="mc-btn tiny green" ${can ? "" : "disabled"}>${invTab === "craft" ? "Скрафтить" : "Переплавить"} x${rec.cnt}</button>`;
      card.querySelector(".mc-card-head").appendChild(cv);
      card.querySelector(".mc-card-head").append(info.name);
      card.querySelector("button").onclick = () => {
        if (!can) return;
        rec.req.forEach(([rid, rcnt]) => takeInv(rid, rcnt));
        addInv(rec.out, rec.cnt);
        playSfx("craft");
        renderInvModal();
      };
      box.appendChild(card);
    });
  }

  // --- УПРАВЛЕНИЕ (КРЕСТОВИНА + ТАП/УДЕРЖАНИЕ + WASD) ---
  const input = { moveX: 0, moveZ: 0, jump: false, holdingBreak: false };
  const dpad = { up: false, down: false, left: false, right: false };
  const keys = {};

  function setupControls() {
    const bindDp = (id, k) => {
      const el = $(id);
      const on = (e) => { e.preventDefault(); dpad[k] = true; };
      const off = () => { dpad[k] = false; };
      el.addEventListener("touchstart", on, { passive: false });
      el.addEventListener("touchend", off); el.addEventListener("touchcancel", off);
      el.addEventListener("mousedown", on); el.addEventListener("mouseup", off); el.addEventListener("mouseleave", off);
    };
    bindDp("dp-up", "up"); bindDp("dp-down", "down"); bindDp("dp-left", "left"); bindDp("dp-right", "right");

    const bJump = $("btn-jump");
    bJump.addEventListener("touchstart", (e) => { e.preventDefault(); input.jump = true; }, { passive: false });
    bJump.addEventListener("touchend", () => { input.jump = false; });
    bJump.addEventListener("mousedown", () => { input.jump = true; });
    bJump.addEventListener("mouseup", () => { input.jump = false; });

    $("btn-sneak").onclick = () => { player.sneaking = !player.sneaking; showToast(player.sneaking ? "Присед ВКЛ" : "Присед ВЫКЛ"); };
    $("btn-fly").onclick = () => { if (player.mode === "creative") { player.flying = !player.flying; player.vy = 0; showToast(player.flying ? "Полёт ВКЛ" : "Полёт ВЫКЛ"); } };

    const jZone = $("joy-zone"), jKnob = $("joy-knob");
    let joyId = null, jCx = 0, jCy = 0;
    jZone.addEventListener("touchstart", (e) => { e.preventDefault(); joyId = e.changedTouches[0].identifier; const r = $("joy-base").getBoundingClientRect(); jCx = r.left + r.width/2; jCy = r.top + r.height/2; }, { passive: false });
    jZone.addEventListener("touchmove", (e) => {
      e.preventDefault();
      for (const t of e.changedTouches) {
        if (t.identifier !== joyId) continue;
        let dx = t.clientX - jCx, dy = t.clientY - jCy;
        const d = Math.hypot(dx, dy);
        if (d > 40) { dx = dx / d * 40; dy = dy / d * 40; }
        jKnob.style.left = (36 + dx) + "px"; jKnob.style.top = (36 + dy) + "px";
        input.moveX = dx / 40; input.moveZ = -dy / 40;
      }
    }, { passive: false });
    const endJoy = (e) => { for (const t of e.changedTouches) if (t.identifier === joyId) { joyId = null; input.moveX = 0; input.moveZ = 0; jKnob.style.left = "36px"; jKnob.style.top = "36px"; } };
    jZone.addEventListener("touchend", endJoy); jZone.addEventListener("touchcancel", endJoy);

    const canvas = $("game-canvas");
    let touchId = null, startX = 0, startY = 0, lastX = 0, lastY = 0, startTime = 0, moved = false, holdTimer = null;

    canvas.addEventListener("touchstart", (e) => {
      if (!running || paused) return;
      const t = e.changedTouches[0];
      touchId = t.identifier;
      startX = lastX = t.clientX; startY = lastY = t.clientY;
      startTime = performance.now(); moved = false;
      clearTimeout(holdTimer);
      holdTimer = setTimeout(() => {
        if (!moved && touchId !== null) { input.holdingBreak = true; tryAttackOrMine(0.05, true); }
      }, 220);
    });

    canvas.addEventListener("touchmove", (e) => {
      if (!running || paused) return;
      for (const t of e.changedTouches) {
        if (t.identifier !== touchId) continue;
        const dx = t.clientX - lastX, dy = t.clientY - lastY;
        lastX = t.clientX; lastY = t.clientY;
        if (Math.hypot(t.clientX - startX, t.clientY - startY) > 12) {
          moved = true; clearTimeout(holdTimer); input.holdingBreak = false;
          miningState.progress = 0; $("mine-bar").classList.add("hidden");
        }
        player.yaw -= dx * 0.0055 * SETTINGS.sens;
        player.pitch = Math.max(-1.45, Math.min(1.45, player.pitch - dy * 0.0055 * SETTINGS.sens));
      }
    });

    const endTouch = (e) => {
      for (const t of e.changedTouches) {
        if (t.identifier !== touchId) continue;
        clearTimeout(holdTimer);
        const dur = performance.now() - startTime;
        if (!moved && !input.holdingBreak && dur < 220) {
          const dir = new THREE.Vector3(0, 0, -1).applyEuler(new THREE.Euler(player.pitch, player.yaw, 0, "YXZ"));
          const heldInfo = VH.ITEMS_DB[hotbar[player.selectedSlot] || 0];
          const hitMob = mobMgr && mobMgr.hitMobAtRay({ x: player.x, y: player.y + 1.55, z: player.z }, dir, 4.4, heldInfo?.dmg || 3, (d) => handleMobLoot(d));
          if (hitMob) { player.armSwing = 1.0; playSfx("hit"); }
          else tryPlaceOrUse();
        }
        touchId = null; input.holdingBreak = false; miningState.progress = 0; $("mine-bar").classList.add("hidden");
      }
    };
    canvas.addEventListener("touchend", endTouch); canvas.addEventListener("touchcancel", endTouch);

    window.addEventListener("keydown", (e) => {
      keys[e.code] = true;
      if (e.code >= "Digit1" && e.code <= "Digit9") { player.selectedSlot = +e.code[5] - 1; renderHotbar(); }
      if (e.code === "KeyE" && running) { paused ? ($("inv-modal").classList.add("hidden"), paused = false) : openInvModal("bag"); }
    });
    window.addEventListener("keyup", (e) => { keys[e.code] = false; });
    canvas.addEventListener("mousedown", (e) => {
      if (!running || paused) return;
      if (document.pointerLockElement !== canvas && !("ontouchstart" in window)) canvas.requestPointerLock();
      if (e.button === 0) { input.holdingBreak = true; tryAttackOrMine(0.05, true); }
      if (e.button === 2) tryPlaceOrUse();
    });
    window.addEventListener("mouseup", () => { input.holdingBreak = false; miningState.progress = 0; $("mine-bar").classList.add("hidden"); });
    window.addEventListener("contextmenu", (e) => e.preventDefault());
    window.addEventListener("mousemove", (e) => {
      if (document.pointerLockElement === canvas && running && !paused) {
        player.yaw -= e.movementX * 0.0025 * SETTINGS.sens;
        player.pitch = Math.max(-1.45, Math.min(1.45, player.pitch - e.movementY * 0.0025 * SETTINGS.sens));
      }
    });
  }

  // --- ФИЗИКА ИГРОКА ---
  function isSolidAt(x, y, z) { return Boolean(VH.ITEMS_DB[voxelWorld.getBlock(x, y, z)]?.solid); }
  function collidesPlayer(x, y, z) {
    const r = 0.28;
    for (const dy of [0.1, 0.9, 1.65]) for (const dx of [-r, r]) for (const dz of [-r, r]) {
      if (isSolidAt(x + dx, y + dy, z + dz)) return true;
    }
    return false;
  }

  function damagePlayer(rawAmt) {
    if (player.mode === "creative" || rawAmt <= 0) return;
    const armorDef = getTotalArmorDef();
    const amt = Math.max(1, Math.round(rawAmt * (1 - armorDef * 0.06)));
    VH.modMgr.emit("onPlayerHurt", { amount: amt, rawAmt, player });
    player.hp = Math.max(0, player.hp - amt);
    playSfx("hit");
    renderClassicStatusBars();
    if (player.hp <= 0) { paused = true; $("death-modal").classList.remove("hidden"); }
  }

  function updatePhysics(dt) {
    let mx = input.moveX + (dpad.right || keys["KeyD"] ? 1 : 0) - (dpad.left || keys["KeyA"] ? 1 : 0);
    let mz = input.moveZ + (dpad.up || keys["KeyW"] ? 1 : 0) - (dpad.down || keys["KeyS"] ? 1 : 0);
    const len = Math.hypot(mx, mz);
    if (len > 1) { mx /= len; mz /= len; }

    const sinY = Math.sin(player.yaw), cosY = Math.cos(player.yaw);
    const spd = player.flying ? PHYSICS.flySpeed : (player.sneaking ? PHYSICS.walkSpeed * 0.5 : PHYSICS.walkSpeed);
    player.vx += ((-sinY * mz + cosY * mx) * spd - player.vx) * Math.min(1, dt * 12);
    player.vz += ((-cosY * mz - sinY * mx) * spd - player.vz) * Math.min(1, dt * 12);

    const feetBlk = voxelWorld.getBlock(player.x, player.y + 0.4, player.z);
    const headBlk = voxelWorld.getBlock(player.x, player.y + 1.5, player.z);
    const inWater = feetBlk === 9 || headBlk === 9;
    const inLava  = feetBlk === 29 || headBlk === 29;
    const inPortal = feetBlk === 34 || headBlk === 34;

    $("underwater-tint").classList.toggle("hidden", headBlk !== 9);
    $("portal-tint").classList.toggle("hidden", !inPortal);

    if (inLava) { player.lavaTimer += dt; if (player.lavaTimer > 0.6) { player.lavaTimer = 0; damagePlayer(3); } }
    if (inPortal) {
      player.portalTimer += dt;
      if (player.portalTimer > 1.8) { player.portalTimer = 0; switchDimension(voxelWorld.dimension === "overworld" ? "nether" : "overworld"); return; }
    } else player.portalTimer = 0;

    const jumping = input.jump || keys["Space"];
    if (player.flying) {
      player.vy = jumping ? 6.5 : (player.sneaking || keys["ShiftLeft"] ? -6.5 : 0);
    } else if (inWater || inLava) {
      player.vy = jumping ? 4.4 : Math.max(-2.2, player.vy - 6 * dt);
    } else {
      const cDim = VH.CUSTOM_DIMENSIONS[voxelWorld.dimension];
      const grav = (cDim && typeof cDim.gravity === "number") ? cDim.gravity : PHYSICS.gravity;
      player.vy -= grav * dt;
      if (jumping && player.onGround) { player.vy = PHYSICS.jumpVelocity; player.onGround = false; }
    }

    if (!collidesPlayer(player.x + player.vx * dt, player.y, player.z)) player.x += player.vx * dt;
    if (!collidesPlayer(player.x, player.y, player.z + player.vz * dt)) player.z += player.vz * dt;

    const prevVy = player.vy;
    const ny = player.y + player.vy * dt;
    if (!collidesPlayer(player.x, ny, player.z)) { player.y = ny; player.onGround = false; }
    else {
      if (prevVy < -11 && !player.flying && !inWater) damagePlayer(Math.floor((-prevVy - 10) * 1.4));
      if (player.vy < 0) {
        player.onGround = true;
        const underId = voxelWorld.getBlock(player.x, player.y - 0.15, player.z);
        const uInfo = VH.ITEMS_DB[underId];
        if (uInfo && typeof uInfo.onStep === "function") { try { uInfo.onStep({ x: Math.floor(player.x), y: Math.floor(player.y - 0.15), z: Math.floor(player.z), id: underId, player, engine: VH.Engine }); } catch {} }
      }
      player.vy = 0;
    }

    const moveMag = Math.hypot(player.vx, player.vz);
    if (moveMag > 0.3 && player.onGround) player.walkBob += dt * moveMag * 2.4;
    if (player.armSwing > 0) player.armSwing = Math.max(0, player.armSwing - dt * 5.0);
  }

  // --- ГЛАВНЫЙ ЦИКЛ ---
  let lastTs = performance.now(), fluidTimer = 0, hudTimer = 0, mpTimer = 0;

  function loop(ts) {
    requestAnimationFrame(loop);
    const dt = Math.min(0.05, (ts - lastTs) / 1000);
    lastTs = ts;
    if (!running || paused) return;

    atlasObj.updateAnimatedTextures(ts / 1000);

    const isNether = voxelWorld.dimension === "nether";
    const cDim = VH.CUSTOM_DIMENSIONS[voxelWorld.dimension];
    let isNight = false;
    if (cDim) {
      scene.background.setHex(cDim.skyColor ?? 0x101830);
      scene.fog.color.setHex(cDim.fogColor ?? cDim.skyColor ?? 0x101830);
      ambLight.intensity = cDim.ambient ?? 0.65; sunLight.intensity = cDim.sun ?? 0.7;
    } else if (isNether) {
      scene.background.setHex(0x3a0c0c); scene.fog.color.setHex(0x3a0c0c);
      ambLight.intensity = 0.58; sunLight.intensity = 0.45;
    } else {
      worldTime += (dt * PHYSICS.timeScale) / 360;
      if (worldTime >= 1) { worldTime -= 1; dayCount++; }
      const ang = worldTime * Math.PI * 2;
      const df = Math.max(0, Math.sin(ang));
      isNight = df < 0.22;
      scene.background.setRGB(0.06 + 0.44 * df, 0.08 + 0.64 * df, 0.16 + 0.82 * df);
      scene.fog.color.copy(scene.background);
      ambLight.intensity = 0.25 + 0.52 * df;
      sunLight.intensity = 0.12 + 0.78 * df;
      sunLight.position.set(Math.cos(ang) * 50, Math.sin(ang) * 50, 20);
    }

    updatePhysics(dt);
    voxelWorld.updateAround(player.x, player.z, SETTINGS.renderDist);
    fluidTimer += dt;
    if (fluidTimer > 0.22) { fluidTimer = 0; voxelWorld.tickFluids(); }

    mobMgr.update(dt, player, isNight, (d) => damagePlayer(d), (ex, ey, ez, r) => explodeAt(ex, ey, ez, r), (dead) => handleMobLoot(dead));
    if (input.holdingBreak) tryAttackOrMine(dt, false);

    // Полоса здоровья Босса (если рядом есть Босс)
    const boss = mobMgr.getActiveBoss();
    if (boss) {
      $("boss-bar").classList.remove("hidden");
      $("boss-name").textContent = `${boss.name} (${Math.max(0, Math.ceil(boss.hp))}/${boss.maxHp})`;
      $("boss-fill").style.width = Math.max(0, (boss.hp / boss.maxHp) * 100) + "%";
    } else {
      $("boss-bar").classList.add("hidden");
    }

    for (let i = particles.length - 1; i >= 0; i--) {
      const pt = particles[i];
      pt.life -= dt; pt.vy -= 14 * dt;
      pt.mesh.position.x += pt.vx * dt; pt.mesh.position.y += pt.vy * dt; pt.mesh.position.z += pt.vz * dt;
      if (pt.life <= 0) { scene.remove(pt.mesh); particles.splice(i, 1); }
    }

    VH.modMgr.emit("onTick", { dt, player, engine: VH.Engine });

    if (player.mode === "survival") {
      player.hungerTimer += dt;
      if (player.hungerTimer > 22) {
        player.hungerTimer = 0;
        if (player.food > 0) player.food--; else damagePlayer(1);
        if (player.food >= 16 && player.hp < 20) player.hp = Math.min(20, player.hp + 1);
        renderClassicStatusBars();
      }
    }

    const bobY = Math.sin(player.walkBob) * 0.05;
    camera.rotation.order = "YXZ";
    camera.rotation.y = player.yaw;
    camera.rotation.x = player.pitch;

    if (player.camThirdPerson) {
      const dist = 3.6;
      const sinY = Math.sin(player.yaw), cosY = Math.cos(player.yaw);
      camera.position.set(player.x + sinY * dist, player.y + 2.2 - Math.sin(player.pitch) * 1.5, player.z + cosY * dist);
      fpArmGroup.visible = false; playerAvatar.visible = true;
      playerAvatar.position.set(player.x, player.y, player.z);
      playerAvatar.rotation.y = player.yaw + Math.PI;
      const sw = Math.sin(player.walkBob) * 0.65;
      playerAvatar.userData.legL.rotation.x = sw; playerAvatar.userData.legR.rotation.x = -sw;
      playerAvatar.userData.armL.rotation.x = -sw; playerAvatar.userData.armR.rotation.x = sw - player.armSwing * 1.3;
    } else {
      camera.position.set(player.x, player.y + 1.55 + bobY, player.z);
      fpArmGroup.visible = true; playerAvatar.visible = false;
      fpArmGroup.position.set(Math.cos(player.walkBob * 0.5) * 0.03, bobY * 0.5, -Math.sin(player.armSwing * Math.PI) * 0.25);
      fpArmGroup.rotation.x = -Math.sin(player.armSwing * Math.PI) * 0.75;
    }

    const hit = raycastBlock(PHYSICS.reach);
    highlightBox.visible = Boolean(hit);
    if (hit) highlightBox.position.set(hit.x + 0.5, hit.y + 0.5, hit.z + 0.5);

    mpTimer += dt;
    if (mpTimer > 0.1) { mpTimer = 0; mpMgr.sendPos(player.x, player.y, player.z, player.yaw); }

    hudTimer += dt;
    if (hudTimer > 0.25) {
      hudTimer = 0;
      const dimTxt = cDim ? (cDim.name || voxelWorld.dimension) : (isNether ? "АД (Nether)" : (isNight ? "Ночь " + dayCount : "День " + dayCount));
      $("coords-box").textContent = `Позиция: ${Math.floor(player.x)}, ${Math.floor(player.y)}, ${Math.floor(player.z)} | ${dimTxt}`;
    }

    renderer.render(scene, camera);
  }

  function renderSkinsMenu() {
    const grid = $("skins-grid");
    grid.innerHTML = "";
    VH.SKINS_DB.forEach((sk) => {
      const card = document.createElement("div");
      card.className = "mc-skin-card" + (sk.id === player.skinId ? " active" : "");
      const cv = document.createElement("canvas"); cv.width = 64; cv.height = 96;
      VH.drawSkinPreview(cv, sk);
      card.appendChild(cv);
      const lbl = document.createElement("div"); lbl.textContent = sk.name;
      card.appendChild(lbl);
      card.onclick = () => {
        player.skinId = sk.id; VH.activeSkinId = sk.id;
        localStorage.setItem("vh_skin_id", sk.id);
        rebuildPlayerVisuals();
        VH.drawSkinPreview($("menu-skin-canvas"), sk);
        $("menu-skin-name").textContent = "Скин: " + sk.name;
        renderSkinsMenu();
      };
      grid.appendChild(card);
    });
  }

  function handleChatCommand(raw) {
    const text = raw.trim();
    if (!text) return;
    addChatLine(mpMgr.nick || "Вы", text);
    mpMgr.sendChat(text);
    const chatEv = VH.modMgr.emit("onChat", { text, cancel: false });
    if (chatEv.cancel) return;

    if (text.startsWith("/")) {
      const parts = text.slice(1).split(/\s+/);
      const cmdFn = VH.CUSTOM_COMMANDS[parts[0].toLowerCase()];
      if (typeof cmdFn === "function") { try { cmdFn(parts.slice(1), VH.Engine); } catch (e) { VH.modMgr.log(e.message, true); } return; }
    }

    if (text === "/nether") switchDimension("nether");
    else if (text === "/overworld") switchDimension("overworld");
    else if (text === "/boss") { mobMgr.spawnMob("inferno_lord", player.x + 5, player.y + 1, player.z + 5); showToast("Призван Повелитель Преисподней!"); }
    else if (text === "/time day") { worldTime = 0.28; showToast("Установлен день!"); }
    else if (text === "/time night") { worldTime = 0.85; showToast("Установлена ночь!"); }
    else if (text.startsWith("/give ")) {
      const id = parseInt(text.split(" ")[1], 10);
      if (VH.ITEMS_DB[id]) { addInv(id, 16); showToast("Выдано: " + VH.ITEMS_DB[id].name); }
    }
  }

  function bindUI() {
    $("btn-open-worlds").onclick = () => { renderWorldsModal(); $("worlds-modal").classList.remove("hidden"); };
    $("btn-close-worlds").onclick = () => $("worlds-modal").classList.add("hidden");
    $("mode-survival").onclick = () => { $("mode-survival").classList.add("active"); $("mode-creative").classList.remove("active"); };
    $("mode-creative").onclick = () => { $("mode-creative").classList.add("active"); $("mode-survival").classList.remove("active"); };
    $("btn-play").onclick = () => startWorld($("world-name-input").value, $("world-seed-input").value, false);

    $("inp-world-file").onchange = (e) => {
      const f = e.target.files[0]; if (!f) return;
      const r = new FileReader();
      r.onload = async () => {
        try {
          const data = JSON.parse(String(r.result || "{}"));
          if (data && data.name) {
            await VH.DB.set("world_" + data.name, data);
            const idx = await getSavedWorldsIndex();
            if (!idx.includes(data.name)) { idx.unshift(data.name); await VH.DB.set("worlds_index", idx); }
            renderWorldsModal(); showToast("Импортирован мир: " + data.name);
          }
        } catch { showToast("Ошибка чтения файла мира"); }
      };
      r.readAsText(f);
    };

    $("btn-open-skins").onclick = () => { renderSkinsMenu(); $("skins-modal").classList.remove("hidden"); };
    $("btn-close-skins").onclick = () => $("skins-modal").classList.add("hidden");
    $("inp-custom-skin").onchange = (e) => {
      const file = e.target.files[0]; if (!file) return;
      const img = new Image();
      img.onload = () => {
        const custom = { id: "custom", name: "Свой скин", skin: "#e2b28b", hair: "#333", shirt: "#2ed573", pants: "#334", eye: "#fff", customImg: img };
        const ex = VH.SKINS_DB.findIndex((s) => s.id === "custom");
        if (ex >= 0) VH.SKINS_DB[ex] = custom; else VH.SKINS_DB.push(custom);
        player.skinId = "custom"; VH.drawSkinPreview($("menu-skin-canvas"), custom);
        $("menu-skin-name").textContent = "Скин: Свой PNG"; renderSkinsMenu();
      };
      img.src = URL.createObjectURL(file);
    };

    const openMp = () => { paused = running; $("mp-modal").classList.remove("hidden"); };
    $("btn-open-mp").onclick = openMp;
    $("btn-mp-ingame").onclick = openMp;
    $("btn-close-mp").onclick = () => { $("mp-modal").classList.add("hidden"); if (running) paused = false; };
    $("btn-mp-host").onclick = () => mpMgr.hostRoom($("mp-nick-input").value, (st) => ($("mp-host-status").textContent = st));
    $("btn-mp-join").onclick = () => {
      mpMgr.joinRoom($("mp-join-code").value, $("mp-nick-input").value, (st) => ($("mp-host-status").textContent = st));
      if (!running) startWorld("Сетевой мир", "100100", false);
    };

    $("btn-mp-accept-mods").onclick = () => {
      if (!pendingHostSync) return;
      VH.modMgr.sessionMods = pendingHostSync.projects || [];
      VH.modMgr.reloadAll();
      if (pendingHostSync.seed && voxelWorld) {
        worldSeed = pendingHostSync.seed;
        voxelWorld.disposeAll();
        voxelWorld = new VH.VoxelWorld(scene, worldSeed, pendingHostSync.dim || "overworld", {}, {});
        mobMgr.setWorld(voxelWorld);
      }
      $("mp-mods-prompt").classList.add("hidden");
      showToast("Моды и мир сервера подкачаны на сессию!");
    };
    $("btn-mp-decline-mods").onclick = () => { $("mp-mods-prompt").classList.add("hidden"); };

    $("btn-close-mod-custom").onclick = () => VH.Mods.ui.closeModal();

    const openSet = () => $("settings-modal").classList.remove("hidden");
    $("btn-open-settings").onclick = openSet;
    $("btn-pause-settings").onclick = openSet;
    const closeSet = () => {
      SETTINGS.renderDist = +$("inp-render-dist").value;
      SETTINGS.sens = +$("inp-sens").value;
      SETTINGS.sound = $("inp-sound").checked;
      $("settings-modal").classList.add("hidden");
    };
    $("btn-close-settings").onclick = closeSet;
    $("btn-save-settings").onclick = closeSet;
    $("inp-render-dist").oninput = (e) => ($("lbl-render-dist").textContent = e.target.value);
    $("inp-sens").oninput = (e) => ($("lbl-sens").textContent = (+e.target.value).toFixed(1));
    $("btn-toggle-ctrl").onclick = () => {
      SETTINGS.ctrlMode = SETTINGS.ctrlMode === "dpad" ? "joy" : "dpad";
      $("lbl-ctrl").textContent = SETTINGS.ctrlMode === "dpad" ? "Крестовина (PE)" : "Джойстик";
      $("dpad-zone").classList.toggle("hidden", SETTINGS.ctrlMode !== "dpad");
      $("joy-zone").classList.toggle("hidden", SETTINGS.ctrlMode === "dpad");
    };

    $("btn-inv-dots").onclick = () => openInvModal("bag");
    $("btn-close-inv").onclick = () => { $("inv-modal").classList.add("hidden"); paused = false; };
    $("tab-bag").onclick   = () => { invTab = "bag"; renderInvModal(); };
    $("tab-craft").onclick = () => { invTab = "craft"; renderInvModal(); };
    $("tab-smelt").onclick = () => { invTab = "smelt"; renderInvModal(); };

    $("btn-close-chest").onclick = () => { $("chest-modal").classList.add("hidden"); paused = false; saveCurrentWorld(true); };

    $("btn-cam-view").onclick = () => { player.camThirdPerson = !player.camThirdPerson; showToast(player.camThirdPerson ? "Камера: 3-е лицо" : "Камера: 1-е лицо"); };

    $("btn-chat-open").onclick = () => { paused = true; $("chat-modal").classList.remove("hidden"); $("chat-input").focus(); };
    $("btn-chat-close").onclick = () => { $("chat-modal").classList.add("hidden"); paused = false; };
    $("btn-chat-send").onclick = () => { handleChatCommand($("chat-input").value); $("chat-input").value = ""; $("chat-modal").classList.add("hidden"); paused = false; };

    $("btn-pause").onclick = () => { paused = true; $("pause-modal").classList.remove("hidden"); };
    $("btn-resume").onclick = () => { $("pause-modal").classList.add("hidden"); paused = false; };
    $("btn-save-now").onclick = () => saveCurrentWorld(false);
    $("btn-export-world").onclick = async () => {
      await saveCurrentWorld(true);
      const data = await VH.DB.get("world_" + currentWorldKey, null);
      if (data) { const msg = await VH.exportJsonFile(currentWorldKey.replace(/[^a-zA-Zа-яА-Я0-9_-]/g, "_") + ".vhworld", data); showToast(msg); }
    };
    $("btn-toggle-dim").onclick = () => { $("pause-modal").classList.add("hidden"); paused = false; switchDimension(voxelWorld.dimension === "overworld" ? "nether" : "overworld"); };
    $("btn-toggle-mode").onclick = () => {
      player.mode = player.mode === "survival" ? "creative" : "survival";
      player.flying = false; updateModeUI(); renderHotbar();
      showToast("Режим: " + (player.mode === "creative" ? "Творчество" : "Выживание"));
    };
    $("btn-exit-menu").onclick = async () => {
      await saveCurrentWorld(true);
      running = false; paused = false;
      $("pause-modal").classList.add("hidden"); $("hud").classList.add("hidden");
      $("main-menu").classList.remove("hidden");
    };
    $("btn-respawn").onclick = () => {
      player.hp = 20; player.food = 20;
      if (player.spawnPos) {
        if (voxelWorld.dimension !== player.spawnPos.dim) switchDimension(player.spawnPos.dim);
        player.x = player.spawnPos.x; player.y = player.spawnPos.y; player.z = player.spawnPos.z;
      } else {
        player.x = 0.5; player.z = 0.5;
        player.y = voxelWorld.dimension === "nether" ? 26.2 : voxelWorld.getTerrainHeight(0, 0) + 3.5;
      }
      player.vy = 0; $("death-modal").classList.add("hidden"); paused = false;
      renderClassicStatusBars();
    };

    // Нативная кнопка «Назад» Android и автосохранение при сворачивании приложения
    document.addEventListener("visibilitychange", () => { if (document.hidden && running) saveCurrentWorld(true); });
    try {
      const capApp = window.Capacitor?.Plugins?.App;
      if (capApp) {
        capApp.addListener("backButton", () => {
          if (running && !paused) { paused = true; $("pause-modal").classList.remove("hidden"); }
        });
        capApp.addListener("appStateChange", ({ isActive }) => { if (!isActive && running) saveCurrentWorld(true); });
      }
    } catch {}
  }

  VH.Engine = {
    get THREE() { return window.THREE; },
    get scene() { return scene; },
    get camera() { return camera; },
    get renderer() { return renderer; },
    get world() { return voxelWorld; },
    get mobMgr() { return mobMgr; },
    get mpMgr() { return mpMgr; },
    get worldTime() { return worldTime; },
    set worldTime(v) { worldTime = v; },
    player, hotbar, SETTINGS, physics: PHYSICS, input, keys, dpad,
    ITEMS_DB: VH.ITEMS_DB, SKINS_DB: VH.SKINS_DB, CRAFT_RECIPES, SMELT_RECIPES,
    getBlock: (x, y, z) => voxelWorld ? voxelWorld.getBlock(x, y, z) : 0,
    setBlock: (x, y, z, id) => { if (voxelWorld) { voxelWorld.setBlock(x, y, z, id, true); mpMgr.sendBlock(x, y, z, id); } },
    raycastBlock, explodeAt, switchDimension, spawnBreakParticles, playSfx, damagePlayer,
    addInv, takeInv, renderHotbar, renderClassicStatusBars, saveCurrentWorld, addChatLine, showToast, openInvModal,
    setPaused: (v) => { paused = Boolean(v); },
    buildPortalFront: () => { if (voxelWorld) voxelWorld.buildNetherPortalAt(Math.floor(player.x) + 2, Math.floor(player.y), Math.floor(player.z)); }
  };

  initThree();
  setupControls();
  bindUI();
  if (VH.bindStudioEvents) VH.bindStudioEvents();
  VH.modMgr.initFromDB();
  VH.drawSkinPreview($("menu-skin-canvas"), getActiveSkinObj());
  $("menu-skin-name").textContent = "Скин: " + getActiveSkinObj().name;
  requestAnimationFrame(loop);
})();
