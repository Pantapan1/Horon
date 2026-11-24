// ============================================================================
// VOXELHORIZON v1.0 — ГЛАВНЫЙ ДВИЖОК (3D СЦЕНА, ФИЗИКА, КРАФТ, ПЕЧЬ, ЗВУК, UI)
// ============================================================================
(function () {
  "use strict";

  const $ = (id) => document.getElementById(id);

  // --- РЕЦЕПТЫ КРАФТА (2x2 и 3x3) И ПЕРЕПЛАВКИ ---
  const CRAFT_RECIPES = [
    { out: 7,   cnt: 4, req: [[5, 1]],                desc: "1 Бревно -> 4 Доски" },
    { out: 105, cnt: 4, req: [[7, 2]],                desc: "2 Доски -> 4 Палки" },
    { out: 17,  cnt: 1, req: [[7, 4]],                desc: "4 Доски -> Верстак" },
    { out: 18,  cnt: 1, req: [[4, 8]],                desc: "8 Булыжника -> Печь" },
    { out: 106, cnt: 1, req: [[7, 3], [105, 2]],      desc: "3 Доски + 2 Палки" },
    { out: 107, cnt: 1, req: [[4, 3], [105, 2]],      desc: "3 Булыжника + 2 Палки" },
    { out: 110, cnt: 1, req: [[4, 3], [105, 2]],      desc: "3 Булыжника + 2 Палки" },
    { out: 108, cnt: 1, req: [[102, 3], [105, 2]],    desc: "3 Железа + 2 Палки" },
    { out: 111, cnt: 1, req: [[102, 2], [105, 1]],    desc: "2 Железа + 1 Палка" },
    { out: 109, cnt: 1, req: [[104, 3], [105, 2]],    desc: "3 Алмаза + 2 Палки" },
    { out: 112, cnt: 1, req: [[104, 2], [105, 1]],    desc: "2 Алмаза + 1 Палка" },
    { out: 23,  cnt: 2, req: [[15, 2], [101, 1]],     desc: "2 Стекла + 1 Уголь -> Лампа" },
    { out: 16,  cnt: 4, req: [[4, 4], [2, 2]],        desc: "4 Булыжника + 2 Земли -> Кирпичи" },
    { out: 24,  cnt: 1, req: [[7, 6]],                desc: "6 Досок -> Книжная полка" },
    { out: 26,  cnt: 1, req: [[8, 4], [101, 2]],      desc: "4 Песка + 2 Угля -> Динамит" }
  ];

  const SMELT_RECIPES = [
    { out: 102, cnt: 1, req: [[12, 1], [101, 1]],     desc: "Железная руда + Уголь -> Железный слиток" },
    { out: 103, cnt: 1, req: [[13, 1], [101, 1]],     desc: "Золотая руда + Уголь -> Золотой слиток" },
    { out: 15,  cnt: 2, req: [[8, 2], [101, 1]],      desc: "2 Песка + Уголь -> 2 Стекла" },
    { out: 115, cnt: 1, req: [[114, 1], [101, 1]],    desc: "Сырое мясо + Уголь -> Жареный стейк" },
    { out: 3,   cnt: 2, req: [[4, 2], [101, 1]],      desc: "2 Булыжника + Уголь -> 2 Камня" }
  ];

  // --- ПРОЦЕДУРНЫЙ WEB AUDIO СИНТЕЗАТОР (0 ВНЕШНИХ ФАЙЛОВ!) ---
  let audioCtx = null;
  const SETTINGS = { renderDist: 3, sens: 1.0, sound: true };

  function playSfx(type) {
    if (!SETTINGS.sound) return;
    try {
      if (!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)();
      if (audioCtx.state === "suspended") audioCtx.resume();
      const t = audioCtx.currentTime;
      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();
      osc.connect(gain); gain.connect(audioCtx.destination);

      if (type === "break") {
        osc.type = "triangle"; osc.frequency.setValueAtTime(190, t); osc.frequency.exponentialRampToValueAtTime(65, t + 0.11);
        gain.gain.setValueAtTime(0.22, t); gain.gain.exponentialRampToValueAtTime(0.01, t + 0.12);
        osc.start(t); osc.stop(t + 0.12);
      } else if (type === "place") {
        osc.type = "sine"; osc.frequency.setValueAtTime(260, t); osc.frequency.exponentialRampToValueAtTime(140, t + 0.08);
        gain.gain.setValueAtTime(0.18, t); gain.gain.exponentialRampToValueAtTime(0.01, t + 0.09);
        osc.start(t); osc.stop(t + 0.09);
      } else if (type === "hit") {
        osc.type = "sawtooth"; osc.frequency.setValueAtTime(150, t); osc.frequency.exponentialRampToValueAtTime(55, t + 0.14);
        gain.gain.setValueAtTime(0.25, t); gain.gain.exponentialRampToValueAtTime(0.01, t + 0.15);
        osc.start(t); osc.stop(t + 0.15);
      } else if (type === "craft") {
        osc.type = "sine"; osc.frequency.setValueAtTime(440, t); osc.frequency.setValueAtTime(660, t + 0.07);
        gain.gain.setValueAtTime(0.18, t); gain.gain.exponentialRampToValueAtTime(0.01, t + 0.18);
        osc.start(t); osc.stop(t + 0.18);
      } else if (type === "boom") {
        osc.type = "sawtooth"; osc.frequency.setValueAtTime(95, t); osc.frequency.exponentialRampToValueAtTime(28, t + 0.45);
        gain.gain.setValueAtTime(0.35, t); gain.gain.exponentialRampToValueAtTime(0.01, t + 0.46);
        osc.start(t); osc.stop(t + 0.46);
      }
    } catch {}
  }

  function showToast(msg) {
    const el = $("toast-msg");
    el.textContent = msg;
    el.classList.remove("hidden");
    clearTimeout(el._t);
    el._t = setTimeout(() => el.classList.add("hidden"), 2200);
  }

  // --- СОСТОЯНИЕ ИГРЫ И ИГРОКА ---
  let renderer, scene, camera, sunLight, ambLight, highlightBox;
  let voxelWorld = null, mobMgr = null;
  let running = false, paused = false;
  let currentWorldKey = "Мой Мир";
  let worldTime = 0.25; // 0..1 (0.25 = утро, 0.5 = полдень, 0.85 = ночь)
  let dayCount = 1;

  const player = {
    x: 0.5, y: 36, z: 0.5,
    vx: 0, vy: 0, vz: 0,
    yaw: 0, pitch: -0.15,
    onGround: false,
    flying: false,
    mode: "survival",
    hp: 20, maxHp: 20,
    food: 20, maxFood: 20,
    hungerTimer: 0,
    selectedSlot: 0,
    inv: {} // itemId -> count
  };
  let hotbar = [106, 1, 4, 7, 17, 18, 23, 113, 26];

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

  // --- СОХРАНЕНИЕ / ЗАГРУЗКА МИРОВ В LOCALSTORAGE ---
  function getSavedWorldsIndex() {
    try { return JSON.parse(localStorage.getItem("vh_worlds_index") || "[]"); } catch { return []; }
  }

  function saveCurrentWorld() {
    if (!voxelWorld) return;
    const payload = {
      name: currentWorldKey,
      seed: voxelWorld.seed,
      mode: player.mode,
      time: worldTime,
      dayCount,
      player: { x: player.x, y: player.y, z: player.z, hp: player.hp, food: player.food, inv: player.inv },
      hotbar,
      mods: voxelWorld.modifications
    };
    try {
      localStorage.setItem("vh_world_" + currentWorldKey, JSON.stringify(payload));
      const idx = getSavedWorldsIndex();
      if (!idx.includes(currentWorldKey)) {
        idx.unshift(currentWorldKey);
        localStorage.setItem("vh_worlds_index", JSON.stringify(idx.slice(0, 8)));
      }
      showToast("Мир «" + currentWorldKey + "» сохранён!");
    } catch { showToast("Недостаточно памяти для сохранения"); }
  }

  function renderSavedWorldsList() {
    const box = $("saved-worlds-list");
    const list = getSavedWorldsIndex();
    box.innerHTML = "";
    list.forEach((wname) => {
      const row = document.createElement("div");
      row.className = "saved-item";
      row.innerHTML = `<span><b>${wname}</b></span><div><button class="load-w">Загрузить</button><button class="del-w">✕</button></div>`;
      row.querySelector(".load-w").onclick = () => startWorld(wname, null, true);
      row.querySelector(".del-w").onclick = () => {
        localStorage.removeItem("vh_world_" + wname);
        const nxt = getSavedWorldsIndex().filter((x) => x !== wname);
        localStorage.setItem("vh_worlds_index", JSON.stringify(nxt));
        renderSavedWorldsList();
      };
      box.appendChild(row);
    });
  }

  // --- ИНИЦИАЛИЗАЦИЯ THREE.JS ---
  function initThree() {
    const canvas = $("game-canvas");
    renderer = new THREE.WebGLRenderer({ canvas, antialias: false, powerPreference: "high-performance" });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5));
    renderer.setSize(window.innerWidth, window.innerHeight);

    scene = new THREE.Scene();
    scene.background = new THREE.Color(0x78b7ff);
    scene.fog = new THREE.Fog(0x78b7ff, 26, 62);

    camera = new THREE.PerspectiveCamera(72, window.innerWidth / window.innerHeight, 0.1, 120);
    ambLight = new THREE.AmbientLight(0xffffff, 0.65);
    scene.add(ambLight);
    sunLight = new THREE.DirectionalLight(0xfff6dd, 0.8);
    sunLight.position.set(30, 60, 25);
    scene.add(sunLight);

    const atlas = VH.buildTextureAtlas();
    VH.atlasCanvas = atlas.canvas;
    VH.atlasTexture = atlas.texture;

    const edges = new THREE.EdgesGeometry(new THREE.BoxGeometry(1.01, 1.01, 1.01));
    highlightBox = new THREE.LineSegments(edges, new THREE.LineBasicMaterial({ color: 0x2ed573 }));
    highlightBox.visible = false;
    scene.add(highlightBox);

    window.addEventListener("resize", () => {
      camera.aspect = window.innerWidth / window.innerHeight;
      camera.updateProjectionMatrix();
      renderer.setSize(window.innerWidth, window.innerHeight);
    });
  }

  // --- ЗАПУСК МИРА ---
  function startWorld(name, seedStr, loadExisting = false) {
    currentWorldKey = (name || "Мой Мир").trim();
    let saved = null;
    if (loadExisting) {
      try { saved = JSON.parse(localStorage.getItem("vh_world_" + currentWorldKey) || "null"); } catch {}
    }

    // Очистка прошлого мира
    if (voxelWorld) {
      for (const ch of voxelWorld.chunks.values()) {
        if (ch.mesh) scene.remove(ch.mesh);
        if (ch.waterMesh) scene.remove(ch.waterMesh);
      }
    }
    if (mobMgr) {
      for (const m of mobMgr.mobs) scene.remove(m.mesh);
    }

    let seed = saved ? saved.seed : (seedStr ? parseInt(seedStr, 10) || hashStr(seedStr) : Math.floor(Math.random() * 999999));
    voxelWorld = new VH.VoxelWorld(scene, seed, saved ? saved.mods : {});
    mobMgr = new VH.MobManager(scene, voxelWorld);

    if (saved) {
      player.mode = saved.mode || "survival";
      worldTime = saved.time || 0.25;
      dayCount = saved.dayCount || 1;
      player.x = saved.player.x; player.y = saved.player.y; player.z = saved.player.z;
      player.hp = saved.player.hp || 20; player.food = saved.player.food || 20;
      player.inv = saved.player.inv || {};
      hotbar = saved.hotbar || [106, 1, 4, 7, 17, 18, 23, 113, 26];
    } else {
      player.mode = $("mode-creative").classList.contains("active") ? "creative" : "survival";
      worldTime = 0.28; dayCount = 1;
      player.x = 0.5; player.z = 0.5;
      player.y = voxelWorld.getTerrainHeight(0, 0) + 3.2;
      player.hp = 20; player.food = 20;
      player.inv = { 106: 1, 113: 5, 7: 8, 17: 1, 23: 4 };
      if (player.mode === "creative") {
        hotbar = [1, 3, 7, 16, 15, 17, 18, 23, 26];
      } else {
        hotbar = [106, 7, 17, 23, 113, 0, 0, 0, 0];
      }
    }

    // Стартовые чанки вокруг игрока
    for (let cx = -1; cx <= 1; cx++) {
      for (let cz = -1; cz <= 1; cz++) voxelWorld.ensureChunk(cx + Math.floor(player.x / 16), cz + Math.floor(player.z / 16));
    }

    player.vx = player.vy = player.vz = 0;
    player.flying = false;
    running = true; paused = false;

    $("main-menu").classList.add("hidden");
    $("pause-modal").classList.add("hidden");
    $("death-modal").classList.add("hidden");
    $("hud").classList.remove("hidden");
    updateModeUI();
    renderHotbar();
    updateStatusBars();
    showToast("Добро пожаловать в VoxelHorizon!");
  }

  function hashStr(s) {
    let h = 2166136261;
    for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); }
    return Math.abs(h) % 999999;
  }

  function updateModeUI() {
    const isCre = player.mode === "creative";
    $("status-bars").classList.toggle("hidden", isCre);
    $("btn-touch-fly").classList.toggle("hidden", !isCre);
  }

  function updateStatusBars() {
    $("hp-fill").style.width = (player.hp / player.maxHp * 100) + "%";
    $("hp-text").textContent = Math.ceil(player.hp);
    $("fd-fill").style.width = (player.food / player.maxFood * 100) + "%";
    $("fd-text").textContent = Math.ceil(player.food);
  }

  function renderHotbar() {
    const hb = $("hotbar");
    hb.innerHTML = "";
    for (let i = 0; i < 9; i++) {
      const id = hotbar[i] || 0;
      const cnt = player.mode === "creative" ? "∞" : (player.inv[id] || 0);
      const slot = document.createElement("div");
      slot.className = "slot" + (i === player.selectedSlot ? " active" : "");
      if (id && (player.mode === "creative" || cnt > 0)) {
        const cv = document.createElement("canvas");
        VH.drawItemIcon(cv, id);
        slot.appendChild(cv);
        const cEl = document.createElement("span");
        cEl.className = "cnt";
        cEl.textContent = cnt;
        slot.appendChild(cEl);
        const nEl = document.createElement("span");
        nEl.className = "sname";
        nEl.textContent = VH.ITEMS_DB[id]?.name || "";
        slot.appendChild(nEl);
      }
      slot.onclick = () => { player.selectedSlot = i; renderHotbar(); };
      hb.appendChild(slot);
    }
  }

  // --- RAYCAST ПО БЛОКАМ ---
  function raycastBlock(maxDist = 5.5) {
    const dir = new THREE.Vector3(0, 0, -1).applyEuler(new THREE.Euler(player.pitch, player.yaw, 0, "YXZ"));
    const ox = player.x, oy = player.y + 1.55, oz = player.z;
    let prevX = Math.floor(ox), prevY = Math.floor(oy), prevZ = Math.floor(oz);
    for (let d = 0.1; d <= maxDist; d += 0.08) {
      const bx = Math.floor(ox + dir.x * d);
      const by = Math.floor(oy + dir.y * d);
      const bz = Math.floor(oz + dir.z * d);
      if (bx === prevX && by === prevY && bz === prevZ) continue;
      const bId = voxelWorld.getBlock(bx, by, bz);
      if (bId && bId !== 9) {
        return { x: bx, y: by, z: bz, id: bId, px: prevX, py: prevY, pz: prevZ, dir };
      }
      prevX = bx; prevY = by; prevZ = bz;
    }
    return null;
  }

  // --- ЛОМАНИЕ / АТАКА И УСТАНОВКА / ИСПОЛЬЗОВАНИЕ ---
  let miningState = { active: false, x: 0, y: 0, z: 0, progress: 0 };

  function tryAttackOrMine(dt, isSingleTap = false) {
    const dir = new THREE.Vector3(0, 0, -1).applyEuler(new THREE.Euler(player.pitch, player.yaw, 0, "YXZ"));
    const heldId = hotbar[player.selectedSlot] || 0;
    const heldInfo = VH.ITEMS_DB[heldId];

    // 1. Проверка удара по мобу
    if (isSingleTap && mobMgr) {
      const dmg = (heldInfo && heldInfo.dmg) ? heldInfo.dmg : 3;
      if (mobMgr.hitMobAtRay({ x: player.x, y: player.y + 1.55, z: player.z }, dir, 4.2, dmg, (dead) => {
        addInv(dead.hostile ? 101 : 114, dead.hostile ? 2 : 2);
        showToast(dead.hostile ? "Побеждён Ночной Сталкер (+2 Угля)" : "Добыто Сырое мясо x2");
      })) {
        playSfx("hit");
        return;
      }
    }

    // 2. Копание блока
    const hit = raycastBlock(5.5);
    if (!hit) { miningState.progress = 0; $("mine-ring").classList.add("hidden"); return; }
    const bInfo = VH.ITEMS_DB[hit.id];
    if (!bInfo || bInfo.hard >= 999) return;

    if (player.mode === "creative") {
      if (isSingleTap) {
        voxelWorld.setBlock(hit.x, hit.y, hit.z, 0);
        playSfx("break");
      }
      return;
    }

    if (hit.x !== miningState.x || hit.y !== miningState.y || hit.z !== miningState.z) {
      miningState = { active: true, x: hit.x, y: hit.y, z: hit.z, progress: 0 };
    }
    let speed = 1.0;
    if (heldInfo && heldInfo.toolType === bInfo.tool) speed = heldInfo.power || 2.5;
    miningState.progress += (dt * speed) / Math.max(0.25, bInfo.hard);

    $("mine-ring").classList.remove("hidden");
    $("mine-fill").style.width = Math.min(100, miningState.progress * 100) + "%";

    if (miningState.progress >= 1.0) {
      voxelWorld.setBlock(hit.x, hit.y, hit.z, 0);
      playSfx("break");
      if (bInfo.drop) addInv(bInfo.drop, 1);
      miningState.progress = 0;
      $("mine-ring").classList.add("hidden");
    }
  }

  function tryPlaceOrUse() {
    const heldId = hotbar[player.selectedSlot] || 0;
    const heldInfo = VH.ITEMS_DB[heldId];

    // 1. Съесть еду, если в руке еда
    if (heldInfo && heldInfo.healFood && player.mode === "survival" && (player.food < 20 || player.hp < 20)) {
      if (takeInv(heldId, 1)) {
        player.food = Math.min(20, player.food + heldInfo.healFood);
        player.hp = Math.min(20, player.hp + (heldInfo.healHp || 0));
        updateStatusBars();
        playSfx("craft");
        showToast("Съедено: " + heldInfo.name);
        return;
      }
    }

    const hit = raycastBlock(5.5);
    if (!hit) return;

    // 2. Интерактивные блоки (Верстак / Печь / TNT)
    const targetInfo = VH.ITEMS_DB[hit.id];
    if (targetInfo && targetInfo.interactive === "craft") { openCraftModal("craft"); return; }
    if (targetInfo && targetInfo.interactive === "smelt") { openCraftModal("smelt"); return; }
    if (targetInfo && targetInfo.interactive === "tnt") {
      explodeTnt(hit.x, hit.y, hit.z);
      return;
    }

    // 3. Установка блока
    if (!heldId || !heldInfo || heldInfo.item) return;
    // Не ставим внутрь игрока
    const inPlayer = Math.abs(hit.px + 0.5 - player.x) < 0.7 && Math.abs(hit.pz + 0.5 - player.z) < 0.7 && (hit.py === Math.floor(player.y) || hit.py === Math.floor(player.y + 1));
    if (inPlayer) return;

    if (takeInv(heldId, 1)) {
      voxelWorld.setBlock(hit.px, hit.py, hit.pz, heldId);
      playSfx("place");
    }
  }

  function explodeTnt(cx, cy, cz) {
    playSfx("boom");
    showToast("Бум!! Динамит взорвался!");
    for (let dx = -2; dx <= 2; dx++) {
      for (let dy = -2; dy <= 2; dy++) {
        for (let dz = -2; dz <= 2; dz++) {
          if (dx*dx + dy*dy + dz*dz <= 6 && cy + dy > 1) {
            voxelWorld.setBlock(cx + dx, cy + dy, cz + dz, 0);
          }
        }
      }
    }
  }

  // --- КРАФТ, ПЕЧЬ И ИНВЕНТАРЬ ---
  let activeModalTab = "craft";
  function openCraftModal(tab = "craft") {
    paused = true;
    activeModalTab = tab;
    if (document.pointerLockElement) document.exitPointerLock();
    $("craft-modal").classList.remove("hidden");
    renderCraftModal();
  }

  function renderCraftModal() {
    $("tab-craft").classList.toggle("active", activeModalTab === "craft");
    $("tab-smelt").classList.toggle("active", activeModalTab === "smelt");
    $("tab-bag").classList.toggle("active", activeModalTab === "bag");
    const box = $("modal-content");
    box.innerHTML = "";

    if (activeModalTab === "bag") {
      const ids = player.mode === "creative"
        ? Object.keys(VH.ITEMS_DB).map(Number).filter((id) => id > 0)
        : Object.keys(player.inv).map(Number).filter((id) => player.inv[id] > 0);
      ids.forEach((id) => {
        const info = VH.ITEMS_DB[id];
        const card = document.createElement("div");
        card.className = "recipe-card";
        const cv = document.createElement("canvas");
        VH.drawItemIcon(cv, id);
        const cnt = player.mode === "creative" ? "∞" : player.inv[id];
        card.innerHTML = `<div class="r-head"></div><div class="r-req">Количество: <b>${cnt}</b></div><button>В активный слот</button>`;
        card.querySelector(".r-head").appendChild(cv);
        card.querySelector(".r-head").append(info.name);
        card.querySelector("button").onclick = () => {
          hotbar[player.selectedSlot] = id;
          renderHotbar();
          showToast("Выбрано: " + info.name);
        };
        box.appendChild(card);
      });
      return;
    }

    const list = activeModalTab === "craft" ? CRAFT_RECIPES : SMELT_RECIPES;
    list.forEach((rec) => {
      const can = player.mode === "creative" || rec.req.every(([rid, rcnt]) => (player.inv[rid] || 0) >= rcnt);
      const info = VH.ITEMS_DB[rec.out];
      const card = document.createElement("div");
      card.className = "recipe-card";
      const cv = document.createElement("canvas");
      VH.drawItemIcon(cv, rec.out);
      card.innerHTML = `<div class="r-head"></div><div class="r-req">${rec.desc}</div><button ${can ? "" : "disabled"}>${activeModalTab === "craft" ? "Скрафтить" : "Переплавить"} (x${rec.cnt})</button>`;
      card.querySelector(".r-head").appendChild(cv);
      card.querySelector(".r-head").append(info.name);
      card.querySelector("button").onclick = () => {
        if (!can) return;
        rec.req.forEach(([rid, rcnt]) => takeInv(rid, rcnt));
        addInv(rec.out, rec.cnt);
        playSfx("craft");
        renderCraftModal();
      };
      box.appendChild(card);
    });
  }

  // --- УПРАВЛЕНИЕ (МОБИЛЬНЫЙ ДЖОЙСТИК + СВАЙП КАМЕРЫ + ПК WASD) ---
  const input = { moveX: 0, moveZ: 0, jump: false, holdingBreak: false };
  const keys = {};

  function setupControls() {
    // 1. Джойстик (левая часть экрана)
    const jZone = $("joy-zone"), jKnob = $("joy-knob");
    let joyId = null, jCx = 0, jCy = 0;

    jZone.addEventListener("touchstart", (e) => {
      e.preventDefault();
      const t = e.changedTouches[0];
      joyId = t.identifier;
      const r = $("joy-base").getBoundingClientRect();
      jCx = r.left + r.width / 2; jCy = r.top + r.height / 2;
    }, { passive: false });

    jZone.addEventListener("touchmove", (e) => {
      e.preventDefault();
      for (const t of e.changedTouches) {
        if (t.identifier !== joyId) continue;
        let dx = t.clientX - jCx, dy = t.clientY - jCy;
        const dist = Math.hypot(dx, dy);
        const maxR = 42;
        if (dist > maxR) { dx = dx / dist * maxR; dy = dy / dist * maxR; }
        jKnob.style.left = (34 + dx) + "px";
        jKnob.style.top = (34 + dy) + "px";
        input.moveX = dx / maxR;
        input.moveZ = -dy / maxR;
      }
    }, { passive: false });

    const resetJoy = (e) => {
      for (const t of e.changedTouches) {
        if (t.identifier === joyId) {
          joyId = null; input.moveX = 0; input.moveZ = 0;
          jKnob.style.left = "34px"; jKnob.style.top = "34px";
        }
      }
    };
    jZone.addEventListener("touchend", resetJoy);
    jZone.addEventListener("touchcancel", resetJoy);

    // 2. Свайп по канвасу для вращения камеры
    const canvas = $("game-canvas");
    let lookId = null, lastLx = 0, lastLy = 0;
    canvas.addEventListener("touchstart", (e) => {
      if (!running || paused) return;
      const t = e.changedTouches[0];
      lookId = t.identifier; lastLx = t.clientX; lastLy = t.clientY;
    });
    canvas.addEventListener("touchmove", (e) => {
      if (!running || paused) return;
      for (const t of e.changedTouches) {
        if (t.identifier !== lookId) continue;
        const dx = t.clientX - lastLx, dy = t.clientY - lastLy;
        lastLx = t.clientX; lastLy = t.clientY;
        player.yaw -= dx * 0.0055 * SETTINGS.sens;
        player.pitch = Math.max(-1.45, Math.min(1.45, player.pitch - dy * 0.0055 * SETTINGS.sens));
      }
    });
    canvas.addEventListener("touchend", (e) => {
      for (const t of e.changedTouches) if (t.identifier === lookId) lookId = null;
    });

    // 3. Правые кнопки действий
    const bBreak = $("btn-touch-break");
    bBreak.addEventListener("touchstart", (e) => { e.preventDefault(); input.holdingBreak = true; tryAttackOrMine(0.05, true); }, { passive: false });
    bBreak.addEventListener("touchend", () => { input.holdingBreak = false; miningState.progress = 0; $("mine-ring").classList.add("hidden"); });
    bBreak.addEventListener("mousedown", () => { input.holdingBreak = true; tryAttackOrMine(0.05, true); });
    bBreak.addEventListener("mouseup", () => { input.holdingBreak = false; miningState.progress = 0; $("mine-ring").classList.add("hidden"); });

    $("btn-touch-place").addEventListener("touchstart", (e) => { e.preventDefault(); tryPlaceOrUse(); }, { passive: false });
    $("btn-touch-place").addEventListener("click", () => tryPlaceOrUse());

    const bJump = $("btn-touch-jump");
    bJump.addEventListener("touchstart", (e) => { e.preventDefault(); input.jump = true; }, { passive: false });
    bJump.addEventListener("touchend", () => { input.jump = false; });
    bJump.addEventListener("mousedown", () => { input.jump = true; });
    bJump.addEventListener("mouseup", () => { input.jump = false; });

    $("btn-touch-fly").onclick = () => {
      if (player.mode === "creative") {
        player.flying = !player.flying; player.vy = 0;
        showToast(player.flying ? "Режим полёта ВКЛ" : "Режим полёта ВЫКЛ");
      }
    };

    // 4. ПК-управление (WASD + PointerLock)
    window.addEventListener("keydown", (e) => {
      keys[e.code] = true;
      if (e.code >= "Digit1" && e.code <= "Digit9") { player.selectedSlot = +e.code[5] - 1; renderHotbar(); }
      if (e.code === "KeyE" && running) { paused ? ($("craft-modal").classList.add("hidden"), paused = false) : openCraftModal("craft"); }
    });
    window.addEventListener("keyup", (e) => { keys[e.code] = false; });
    canvas.addEventListener("mousedown", (e) => {
      if (!running || paused) return;
      if (document.pointerLockElement !== canvas && !("ontouchstart" in window)) canvas.requestPointerLock();
      if (e.button === 0) { input.holdingBreak = true; tryAttackOrMine(0.05, true); }
      if (e.button === 2) tryPlaceOrUse();
    });
    window.addEventListener("mouseup", () => { input.holdingBreak = false; miningState.progress = 0; $("mine-ring").classList.add("hidden"); });
    window.addEventListener("contextmenu", (e) => e.preventDefault());
    window.addEventListener("mousemove", (e) => {
      if (document.pointerLockElement === canvas && running && !paused) {
        player.yaw -= e.movementX * 0.0025 * SETTINGS.sens;
        player.pitch = Math.max(-1.45, Math.min(1.45, player.pitch - e.movementY * 0.0025 * SETTINGS.sens));
      }
    });
  }

  // --- ФИЗИКА ИГРОКА (AABB) ---
  function isSolidAt(x, y, z) {
    const b = voxelWorld.getBlock(x, y, z);
    return Boolean(VH.ITEMS_DB[b] && VH.ITEMS_DB[b].solid);
  }

  function collidesPlayer(x, y, z) {
    const r = 0.28;
    for (const dy of [0.1, 0.9, 1.65]) {
      for (const dx of [-r, r]) {
        for (const dz of [-r, r]) {
          if (isSolidAt(x + dx, y + dy, z + dz)) return true;
        }
      }
    }
    return false;
  }

  function updatePhysics(dt) {
    let mx = input.moveX + (keys["KeyD"] ? 1 : 0) - (keys["KeyA"] ? 1 : 0);
    let mz = input.moveZ + (keys["KeyW"] ? 1 : 0) - (keys["KeyS"] ? 1 : 0);
    const len = Math.hypot(mx, mz);
    if (len > 1) { mx /= len; mz /= len; }

    const sinY = Math.sin(player.yaw), cosY = Math.cos(player.yaw);
    const spd = player.flying ? 9.5 : 5.2;
    const wishX = (-sinY * mz + cosY * mx) * spd;
    const wishZ = (-cosY * mz - sinY * mx) * spd;

    player.vx += (wishX - player.vx) * Math.min(1, dt * 12);
    player.vz += (wishZ - player.vz) * Math.min(1, dt * 12);

    const inWater = voxelWorld.getBlock(player.x, player.y + 0.5, player.z) === 9;
    const jumping = input.jump || keys["Space"];

    if (player.flying) {
      player.vy = jumping ? 6.5 : (keys["ShiftLeft"] ? -6.5 : 0);
    } else if (inWater) {
      player.vy = jumping ? 4.2 : Math.max(-2.5, player.vy - 6 * dt);
    } else {
      player.vy -= 21 * dt;
      if (jumping && player.onGround) { player.vy = 7.6; player.onGround = false; }
    }

    // Движение по X, Z, Y отдельно для чистого скольжения вдоль стен
    if (!collidesPlayer(player.x + player.vx * dt, player.y, player.z)) player.x += player.vx * dt;
    if (!collidesPlayer(player.x, player.y, player.z + player.vz * dt)) player.z += player.vz * dt;

    const prevVy = player.vy;
    const ny = player.y + player.vy * dt;
    if (!collidesPlayer(player.x, ny, player.z)) {
      player.y = ny;
      player.onGround = false;
    } else {
      if (prevVy < -11 && !player.flying && player.mode === "survival") {
        damagePlayer(Math.floor((-prevVy - 10) * 1.4));
      }
      if (player.vy < 0) player.onGround = true;
      player.vy = 0;
    }

    if (player.y < -10) {
      player.y = voxelWorld.getTerrainHeight(player.x, player.z) + 4;
      player.vy = 0;
    }
  }

  function damagePlayer(amt) {
    if (player.mode === "creative" || amt <= 0) return;
    player.hp = Math.max(0, player.hp - amt);
    playSfx("hit");
    updateStatusBars();
    if (player.hp <= 0) {
      paused = true;
      $("death-modal").classList.remove("hidden");
    }
  }

  // --- ГЛАВНЫЙ ЦИКЛ КАДРОВ ---
  let lastTs = performance.now();
  let hudTimer = 0;

  function loop(ts) {
    requestAnimationFrame(loop);
    const dt = Math.min(0.05, (ts - lastTs) / 1000);
    lastTs = ts;

    if (!running || paused) return;

    // 1. Цикл дня и ночи (1 игровые сутки = 6 минут)
    worldTime += dt / 360;
    if (worldTime >= 1) { worldTime -= 1; dayCount++; }
    const sunAngle = worldTime * Math.PI * 2;
    const dayFactor = Math.max(0, Math.sin(sunAngle));
    const isNight = dayFactor < 0.22;

    const skyCol = new THREE.Color().setRGB(
      0.06 + 0.42 * dayFactor,
      0.09 + 0.62 * dayFactor,
      0.16 + 0.80 * dayFactor
    );
    scene.background = skyCol;
    scene.fog.color = skyCol;
    ambLight.intensity = 0.24 + 0.50 * dayFactor;
    sunLight.intensity = 0.12 + 0.78 * dayFactor;
    sunLight.position.set(Math.cos(sunAngle) * 50, Math.sin(sunAngle) * 50, 20);

    // 2. Физика, Чанки, Мобы, Копание
    updatePhysics(dt);
    voxelWorld.updateAround(player.x, player.z, SETTINGS.renderDist);
    mobMgr.update(dt, player, isNight, (dmg) => damagePlayer(dmg));

    if (input.holdingBreak) tryAttackOrMine(dt, false);

    // 3. Голод и регенерация в Выживании
    if (player.mode === "survival") {
      player.hungerTimer += dt;
      if (player.hungerTimer > 22) {
        player.hungerTimer = 0;
        if (player.food > 0) player.food--;
        else damagePlayer(1);
        if (player.food >= 16 && player.hp < 20) player.hp = Math.min(20, player.hp + 1);
        updateStatusBars();
      }
    }

    // 4. Камера и выделение целевого блока
    camera.position.set(player.x, player.y + 1.55, player.z);
    camera.rotation.order = "YXZ";
    camera.rotation.y = player.yaw;
    camera.rotation.x = player.pitch;

    const hit = raycastBlock(5.5);
    if (hit) {
      highlightBox.visible = true;
      highlightBox.position.set(hit.x + 0.5, hit.y + 0.5, hit.z + 0.5);
    } else {
      highlightBox.visible = false;
    }

    hudTimer += dt;
    if (hudTimer > 0.25) {
      hudTimer = 0;
      const tod = isNight ? "Ночь" : "День";
      $("coords-box").textContent = `X:${Math.floor(player.x)} Y:${Math.floor(player.y)} Z:${Math.floor(player.z)} | ${tod} ${dayCount}`;
    }

    renderer.render(scene, camera);
  }

  // --- КНОПКИ UI ---
  function bindUI() {
    $("mode-survival").onclick = () => { $("mode-survival").classList.add("active"); $("mode-creative").classList.remove("active"); };
    $("mode-creative").onclick = () => { $("mode-creative").classList.add("active"); $("mode-survival").classList.remove("active"); };
    $("btn-play").onclick = () => startWorld($("world-name-input").value, $("world-seed-input").value, false);

    $("btn-open-settings").onclick = () => $("settings-modal").classList.remove("hidden");
    $("btn-pause-settings").onclick = () => $("settings-modal").classList.remove("hidden");
    $("btn-close-settings").onclick = () => {
      SETTINGS.renderDist = +$("inp-render-dist").value;
      SETTINGS.sens = +$("inp-sens").value;
      SETTINGS.sound = $("inp-sound").checked;
      $("settings-modal").classList.add("hidden");
    };
    $("inp-render-dist").oninput = (e) => ($("lbl-render-dist").textContent = e.target.value);
    $("inp-sens").oninput = (e) => ($("lbl-sens").textContent = (+e.target.value).toFixed(1));

    $("btn-craft-open").onclick = () => openCraftModal("craft");
    $("btn-inv-open").onclick = () => openCraftModal("bag");
    $("tab-craft").onclick = () => { activeModalTab = "craft"; renderCraftModal(); };
    $("tab-smelt").onclick = () => { activeModalTab = "smelt"; renderCraftModal(); };
    $("tab-bag").onclick = () => { activeModalTab = "bag"; renderCraftModal(); };
    $("btn-close-craft").onclick = () => { $("craft-modal").classList.add("hidden"); paused = false; };

    $("btn-pause").onclick = () => { paused = true; $("pause-modal").classList.remove("hidden"); };
    $("btn-resume").onclick = () => { $("pause-modal").classList.add("hidden"); paused = false; };
    $("btn-save-now").onclick = () => saveCurrentWorld();
    $("btn-toggle-mode").onclick = () => {
      player.mode = player.mode === "survival" ? "creative" : "survival";
      player.flying = false;
      updateModeUI(); renderHotbar();
      showToast("Режим: " + (player.mode === "creative" ? "Творчество" : "Выживание"));
    };
    $("btn-exit-menu").onclick = () => {
      saveCurrentWorld();
      running = false; paused = false;
      $("pause-modal").classList.add("hidden");
      $("hud").classList.add("hidden");
      $("main-menu").classList.remove("hidden");
      renderSavedWorldsList();
    };

    $("btn-respawn").onclick = () => {
      player.hp = 20; player.food = 20;
      player.x = 0.5; player.z = 0.5;
      player.y = voxelWorld.getTerrainHeight(0, 0) + 3.5;
      player.vy = 0;
      $("death-modal").classList.add("hidden");
      paused = false;
      updateStatusBars();
    };
  }

  initThree();
  setupControls();
  bindUI();
  renderSavedWorldsList();
  requestAnimationFrame(loop);
})();
