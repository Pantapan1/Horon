// ============================================================================
// VOXELHORIZON v3.1 — ПОЛНЫЙ ДВИЖОК (2-ПАЛЬЦЕВОЕ УПРАВЛЕНИЕ, СИНХРОНИЗАЦИЯ СЕТИ, ПЕЧЬ, 3D СКИНЫ, OST)
// ============================================================================
(function () {
  "use strict";
  const $ = (id) => document.getElementById(id);

  // 1. КАРМАННЫЙ КРАФТ 2x2 (ТОЛЬКО ОН ДОСТУПЕН В ИНВЕНТАРЕ ИГРОКА!)
  const POCKET_RECIPES = [
    { out: 7,   cnt: 4, req: [[5, 1]],                desc: "1 Дуб -> 4 Доски" },
    { out: 7,   cnt: 4, req: [[27, 1]],               desc: "1 Берёза -> 4 Доски" },
    { out: 105, cnt: 4, req: [[7, 2]],                desc: "2 Доски -> 4 Палки" },
    { out: 17,  cnt: 1, req: [[7, 4]],                desc: "4 Доски -> Верстак" },
    { out: 37,  cnt: 4, req: [[101, 1], [105, 1]],    desc: "Уголь + Палка -> 4 Факела" }
  ];

  // 2. ПОЛНЫЙ КРАФТ 3x3 (ОТКРЫВАЕТСЯ ТОЛЬКО ПРИ ТАПЕ НА ВЕРСТАК ID 17!)
  const CRAFT_RECIPES = [
    ...POCKET_RECIPES,
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
    { out: 122, cnt: 1, req: [[121, 3]],              desc: "3 Семени -> Хлеб" },
    { out: 125, cnt: 1, req: [[114, 4]],              desc: "4 Мяса/Кожи -> Кожаный шлем" },
    { out: 126, cnt: 1, req: [[102, 6]],              desc: "6 Железа -> Железный нагрудник" },
    { out: 127, cnt: 1, req: [[102, 5]],              desc: "5 Железа -> Железные поножи" },
    { out: 128, cnt: 1, req: [[104, 4]],              desc: "4 Алмаза -> Алмазные ботинки" },
    { out: 116, cnt: 1, req: [[102, 1], [25, 1]],     desc: "Железо + Гравий -> Огниво" },
    { out: 119, cnt: 1, req: [[102, 3]],              desc: "3 Железа -> Ведро воды" },
    { out: 23,  cnt: 2, req: [[15, 2], [101, 1]],     desc: "2 Стекла + Уголь -> Светокамень" },
    { out: 24,  cnt: 1, req: [[7, 6]],                desc: "6 Досок -> Книжная полка" },
    { out: 26,  cnt: 1, req: [[8, 4], [101, 2]],      desc: "4 Песка + 2 Угля -> Динамит" }
  ];

  // 3. ПЕРЕПЛАВКА В ПЕЧИ (ОТКРЫВАЕТСЯ ТОЛЬКО ПРИ ТАПЕ НА БЛОК ПЕЧЬ ID 18!)
  const SMELT_RECIPES = [
    { inId: 12,  out: 102, cnt: 1, desc: "Железная руда -> Железный слиток" },
    { inId: 13,  out: 103, cnt: 1, desc: "Золотая руда -> Золотой слиток" },
    { inId: 8,   out: 15,  cnt: 1, desc: "Песок -> Стекло" },
    { inId: 114, out: 115, cnt: 1, desc: "Сырая говядина -> Жареный стейк" },
    { inId: 4,   out: 3,   cnt: 1, desc: "Булыжник -> Камень" }
  ];
  const FUEL_VALUES = { 101: 80, 5: 25, 27: 25, 7: 15, 105: 8, 120: 100 };

  // --- ПРОЦЕДУРНЫЕ SFX + СТЕРЕО WAV САУНДТРЕК ---
  let audioCtx = null;
  let ostAudio = null;
  let currentOstFile = "";
  const SETTINGS = { renderDist: 3, sens: 1.0, sound: true };
  const PHYSICS = { gravity: 21, jumpVelocity: 7.6, walkSpeed: 5.2, flySpeed: 9.8, reach: 5.5, timeScale: 1.0 };

  function updateSoundtrack(file) {
    if (!SETTINGS.sound || !running) {
      if (ostAudio) { ostAudio.pause(); currentOstFile = ""; }
      return;
    }
    if (currentOstFile === file && ostAudio && !ostAudio.paused) return;
    currentOstFile = file;
    try {
      if (ostAudio) ostAudio.pause();
      ostAudio = new Audio(file);
      ostAudio.loop = true;
      ostAudio.volume = 0.28;
      ostAudio.play().catch(() => {});
    } catch {}
  }

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
  let atlasObj = null, voxelWorld = null, mobMgr = null, dropMgr = null, mpMgr = null;
  let particles = [];
  let running = false, paused = false;
  let currentWorldKey = "Новый Мир";
  let worldSeed = 123456;
  let worldTime = 0.28, dayCount = 1;
  let savedDimMods = { overworld: {}, nether: {} };
  let savedDimChests = { overworld: {}, nether: {} };
  let furnaces = new Map(); // "dim:x,y,z" -> { input, fuel, output, burn, maxBurn, cook }
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
    selectedInvSlot: -1,
    skinId: localStorage.getItem("vh_skin_id") || "fox_officer",
    armor: { head: 0, chest: 0, legs: 0, feet: 0 },
    slots: new Array(36).fill(null) // 0..8 = hotbar, 9..35 = backpack
  };
  VH.activeSkinId = player.skinId;

  function getActiveSkinObj() {
    return VH.DETAILED_SKINS.find((s) => s.id === player.skinId) || VH.DETAILED_SKINS[0];
  }

  function getTotalArmorDef() {
    let def = 0;
    for (const sk of ["head", "chest", "legs", "feet"]) {
      const id = player.armor[sk];
      if (id && VH.ITEMS_DB[id]) def += VH.ITEMS_DB[id].armorDef || 0;
    }
    return def;
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
    playerAvatar = VH.buildAvatarMesh(s, "");
    playerAvatar.visible = player.camThirdPerson;
    scene.add(playerAvatar);
  }

  // --- ЖИВОЙ 3D ПРЕДПРОСМОТР СКИНОВ В ГАРДЕРОБЕ И ГЛАВНОМ МЕНЮ ---
  let menuSkinRen = null, menuSkinScene = null, menuSkinCam = null, menuSkinMesh = null;
  let wardSkinRen = null, wardSkinScene = null, wardSkinCam = null, wardSkinMesh = null, wardYaw = 0.45;

  function refreshSkinPreviews() {
    const sk = getActiveSkinObj();
    $("menu-skin-name").textContent = sk.name;

    // 1. Мини-3D окно в Главном меню
    const mCv = $("menu-skin-canvas");
    if (mCv && window.THREE) {
      if (!menuSkinRen) {
        menuSkinRen = new THREE.WebGLRenderer({ canvas: mCv, antialias: true, alpha: true });
        menuSkinRen.setSize(mCv.width, mCv.height);
        menuSkinScene = new THREE.Scene();
        menuSkinScene.add(new THREE.AmbientLight(0xffffff, 0.8));
        const dl = new THREE.DirectionalLight(0xffffff, 0.7); dl.position.set(2, 4, 3); menuSkinScene.add(dl);
        menuSkinCam = new THREE.PerspectiveCamera(38, mCv.width / mCv.height, 0.1, 20);
        menuSkinCam.position.set(0, 1.05, 3.35);
        menuSkinCam.lookAt(0, 0.95, 0);
      }
      if (menuSkinMesh) menuSkinScene.remove(menuSkinMesh);
      menuSkinMesh = VH.buildAvatarMesh(sk, "");
      menuSkinMesh.rotation.y = 0.42;
      menuSkinScene.add(menuSkinMesh);
      menuSkinRen.render(menuSkinScene, menuSkinCam);
    }

    // 2. 3D окно в Гардеробе
    const wCv = $("wardrobe-3d-canvas");
    if (wCv && window.THREE) {
      if (!wardSkinRen) {
        wardSkinRen = new THREE.WebGLRenderer({ canvas: wCv, antialias: true });
        wardSkinRen.setSize(wCv.width, wCv.height);
        wardSkinScene = new THREE.Scene();
        wardSkinScene.background = new THREE.Color(0x2a2f3a);
        wardSkinScene.add(new THREE.AmbientLight(0xffffff, 0.8));
        const dl = new THREE.DirectionalLight(0xffffff, 0.7); dl.position.set(2, 5, 4); wardSkinScene.add(dl);
        wardSkinCam = new THREE.PerspectiveCamera(38, wCv.width / wCv.height, 0.1, 20);
        wardSkinCam.position.set(0, 1.05, 3.3);
        wardSkinCam.lookAt(0, 0.95, 0);

        let wDrag = false, wLx = 0;
        wCv.addEventListener("mousedown", (e) => { wDrag = true; wLx = e.clientX; });
        window.addEventListener("mousemove", (e) => { if (wDrag) { wardYaw += (e.clientX - wLx) * 0.02; wLx = e.clientX; renderWardrobe3D(); } });
        window.addEventListener("mouseup", () => { wDrag = false; });
        wCv.addEventListener("touchstart", (e) => { e.preventDefault(); wDrag = true; wLx = e.touches[0].clientX; }, { passive: false });
        wCv.addEventListener("touchmove", (e) => { e.preventDefault(); if (wDrag) { wardYaw += (e.touches[0].clientX - wLx) * 0.02; wLx = e.touches[0].clientX; renderWardrobe3D(); } }, { passive: false });
        wCv.addEventListener("touchend", () => { wDrag = false; });
      }
      if (wardSkinMesh) wardSkinScene.remove(wardSkinMesh);
      wardSkinMesh = VH.buildAvatarMesh(sk, "");
      wardSkinScene.add(wardSkinMesh);
      renderWardrobe3D();
    }
  }

  function renderWardrobe3D() {
    if (!wardSkinRen || !wardSkinScene || !wardSkinCam) return;
    if (wardSkinMesh) wardSkinMesh.rotation.y = wardYaw;
    wardSkinRen.render(wardSkinScene, wardSkinCam);
  }

  function spawnBreakParticles(x, y, z, colorHex = 0x8b6b42) {
    for (let i = 0; i < 7; i++) {
      const m = new THREE.Mesh(new THREE.BoxGeometry(0.14, 0.14, 0.14), new THREE.MeshLambertMaterial({ color: colorHex }));
      m.position.set(x + 0.5 + (Math.random() - 0.5) * 0.5, y + 0.5 + (Math.random() - 0.5) * 0.5, z + 0.5 + (Math.random() - 0.5) * 0.5);
      scene.add(m);
      particles.push({ mesh: m, vx: (Math.random() - 0.5) * 4, vy: 2 + Math.random() * 3, vz: (Math.random() - 0.5) * 4, life: 0.45 });
    }
  }

  // --- СЛОТЫ ИНВЕНТАРЯ (0..8 ХОТБАР, 9..35 РЮКЗАК) ---
  function countItem(id) {
    let t = 0;
    for (const s of player.slots) if (s && s.id === id) t += s.count;
    return t;
  }

  function addInv(id, cnt = 1) {
    if (!id || cnt <= 0) return false;
    let rem = cnt;
    for (let i = 0; i < 36 && rem > 0; i++) {
      const s = player.slots[i];
      if (s && s.id === id && s.count < 64) {
        const add = Math.min(64 - s.count, rem);
        s.count += add; rem -= add;
      }
    }
    for (let i = 0; i < 36 && rem > 0; i++) {
      if (!player.slots[i]) {
        const add = Math.min(64, rem);
        player.slots[i] = { id, count: add };
        rem -= add;
      }
    }
    renderHotbar();
    return rem === 0;
  }

  function takeInv(id, cnt = 1) {
    if (player.mode === "creative") return true;
    if (countItem(id) < cnt) return false;
    let rem = cnt;
    for (let i = 0; i < 36 && rem > 0; i++) {
      const s = player.slots[i];
      if (!s || s.id !== id) continue;
      const sub = Math.min(s.count, rem);
      s.count -= sub; rem -= sub;
      if (s.count <= 0) player.slots[i] = null;
    }
    renderHotbar();
    return true;
  }

  function dropFromSlot(slotIdx, amount = 1) {
    const s = player.slots[slotIdx];
    if (!s || s.count <= 0) return;
    const dropCnt = Math.min(s.count, amount);
    if (player.mode !== "creative") {
      s.count -= dropCnt;
      if (s.count <= 0) player.slots[slotIdx] = null;
    }
    renderHotbar();
    const fx = -Math.sin(player.yaw), fz = -Math.cos(player.yaw);
    if (dropMgr) {
      dropMgr.spawn(s.id, dropCnt, player.x + fx * 1.1, player.y + 1.25, player.z + fz * 1.1, fx * 4.4, 3.2, fz * 4.4);
    }
    playSfx("place");
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

  function renderSlotCell(el, slotObj, isSelected = false) {
    el.innerHTML = "";
    el.className = "mc-slot" + (isSelected ? " active" : "");
    if (!slotObj || !slotObj.id || slotObj.count <= 0) return;
    const cv = document.createElement("canvas");
    VH.drawItemIcon(cv, slotObj.id);
    el.appendChild(cv);
    const cEl = document.createElement("span");
    cEl.className = "cnt";
    cEl.textContent = player.mode === "creative" ? "∞" : slotObj.count;
    el.appendChild(cEl);
  }

  function renderHotbar() {
    const hb = $("hotbar");
    hb.innerHTML = "";
    for (let i = 0; i < 9; i++) {
      const slot = document.createElement("div");
      renderSlotCell(slot, player.slots[i], i === player.selectedSlot);
      slot.onclick = () => {
        player.selectedSlot = i;
        renderHotbar();
        const nm = player.slots[i] ? VH.ITEMS_DB[player.slots[i].id]?.name : null;
        if (nm) showToast(nm);
      };
      hb.appendChild(slot);
    }
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
      if (!syncMsg.projects || syncMsg.projects.length === 0) return;
      pendingHostSync = syncMsg;
      $("mp-mods-prompt-txt").textContent = `Сервер использует моды (${syncMsg.projects.length} шт.). Загрузить на сессию?`;
      $("mp-mods-prompt").classList.remove("hidden");
    };

    rebuildPlayerVisuals();

    window.addEventListener("resize", () => {
      camera.aspect = window.innerWidth / window.innerHeight;
      camera.updateProjectionMatrix();
      renderer.setSize(window.innerWidth, window.innerHeight);
    });
  }

  // --- СИНХРОНИЗАЦИЯ МИРА, СУНДУКОВ И ПЕЧЕЙ В МУЛЬТИПЛЕЕРЕ ---
  function applyWorldSync(msg) {
    worldSeed = msg.seed || 100100;
    const dim = msg.dimension || "overworld";
    savedDimMods = msg.dimMods || { overworld: {}, nether: {} };
    savedDimChests = msg.dimChests || { overworld: {}, nether: {} };
    furnaces = new Map(msg.furnaces || []);
    if (typeof msg.worldTime === "number") worldTime = msg.worldTime;
    if (typeof msg.dayCount === "number") dayCount = msg.dayCount;

    if (!running) {
      startWorld("Сетевой мир", String(worldSeed), false, dim).then(() => {
        if (msg.hostPos) {
          player.x = msg.hostPos.x + 1.5;
          player.y = msg.hostPos.y + 0.5;
          player.z = msg.hostPos.z + 1.5;
        }
        if (dropMgr && msg.drops) dropMgr.applySnapshot(msg.drops);
        if (mobMgr && msg.mobs) mobMgr.applyNetworkSnapshot(msg.mobs);
      });
      return;
    }

    if (voxelWorld) voxelWorld.disposeAll();
    voxelWorld = new VH.VoxelWorld(scene, worldSeed, dim, savedDimMods[dim] || {}, savedDimChests[dim] || {});
    mobMgr.setWorld(voxelWorld);
    dropMgr.setWorld(voxelWorld);

    if (msg.hostPos) {
      player.x = msg.hostPos.x + 1.5;
      player.y = msg.hostPos.y + 0.5;
      player.z = msg.hostPos.z + 1.5;
    }
    for (let cx = -1; cx <= 1; cx++) for (let cz = -1; cz <= 1; cz++) voxelWorld.ensureChunk(cx + Math.floor(player.x / 16), cz + Math.floor(player.z / 16));
    if (msg.drops) dropMgr.applySnapshot(msg.drops);
    if (msg.mobs) mobMgr.applyNetworkSnapshot(msg.mobs);
    showToast("Мир, сундуки, печи, мобы и лут синхронизированы!");
  }

  function applyChestSync(dim, x, y, z, slots) {
    const k = `${Math.floor(x)},${Math.floor(y)},${Math.floor(z)}`;
    if (!savedDimChests[dim]) savedDimChests[dim] = {};
    savedDimChests[dim][k] = slots;
    if (voxelWorld && voxelWorld.dimension === dim) {
      voxelWorld.chests[k] = slots;
    }
    if (openChestCoords && openChestCoords.dim === dim && openChestCoords.x === x && openChestCoords.y === y && openChestCoords.z === z) {
      renderChestModal();
    }
  }

  function applyFurnaceSync(key, fState) {
    furnaces.set(key, fState);
    if (openFurnaceKey === key) renderFurnaceModal();
  }

  function switchDimension(targetDim, fromNet = false) {
    if (!voxelWorld || voxelWorld.dimension === targetDim) return;
    savedDimMods[voxelWorld.dimension] = Object.assign({}, voxelWorld.modifications);
    savedDimChests[voxelWorld.dimension] = Object.assign({}, voxelWorld.chests);
    voxelWorld.disposeAll();
    voxelWorld = new VH.VoxelWorld(scene, worldSeed, targetDim, savedDimMods[targetDim] || {}, savedDimChests[targetDim] || {});
    mobMgr.setWorld(voxelWorld);
    dropMgr.setWorld(voxelWorld);

    for (let cx = -1; cx <= 1; cx++) for (let cz = -1; cz <= 1; cz++) voxelWorld.ensureChunk(cx, cz);
    player.x = 2.5; player.z = 2.5;
    player.y = targetDim === "nether" ? 26.2 : voxelWorld.getTerrainHeight(2, 2) + 2.5;
    player.vx = player.vy = player.vz = 0;

    if (targetDim === "nether") {
      for (let dx = -2; dx <= 4; dx++) for (let dz = -2; dz <= 3; dz++) voxelWorld.setBlock(dx, 24, dz, 32, false);
      for (let dx = -2; dx <= 4; dx++) for (let dy = 25; dy <= 30; dy++) for (let dz = -2; dz <= 3; dz++) voxelWorld.setBlock(dx, dy, dz, 0, false);
      voxelWorld.buildNetherPortalAt(0, 25, 0);
    }

    if (!fromNet && mpMgr?.isConnected() && mpMgr.isHost) {
      mpMgr.broadcast({ t: "dim_sync", dim: targetDim });
    }
    playSfx("portal");
    const cd = VH.CUSTOM_DIMENSIONS[targetDim];
    VH.modMgr.emit("onDimensionChange", { dimension: targetDim, engine: VH.Engine });
    showToast(cd ? ("Измерение: " + (cd.name || targetDim)) : (targetDim === "nether" ? "Вы вошли в АД (Nether)!" : "Вы вернулись в Обычный мир!"));
  }

  // --- INDEXEDDB СОХРАНЕНИЕ / ЗАГРУЗКА / ЭКСПОРТ ---
  async function getSavedWorldsIndex() { return await VH.DB.get("worlds_index", []); }

  async function saveCurrentWorld(silent = false) {
    if (!voxelWorld) return;
    savedDimMods[voxelWorld.dimension] = Object.assign({}, voxelWorld.modifications);
    savedDimChests[voxelWorld.dimension] = Object.assign({}, voxelWorld.chests);
    const payload = {
      name: currentWorldKey, seed: worldSeed, mode: player.mode, dim: voxelWorld.dimension,
      time: worldTime, dayCount,
      worldModIds: selectedWorldModIds,
      player: { x: player.x, y: player.y, z: player.z, spawnPos: player.spawnPos, hp: player.hp, food: player.food, armor: player.armor, slots: player.slots },
      dimMods: savedDimMods, dimChests: savedDimChests,
      furnaces: Array.from(furnaces.entries())
    };
    await VH.DB.set("world_" + currentWorldKey, payload);
    const idx = await getSavedWorldsIndex();
    if (!idx.includes(currentWorldKey)) { idx.unshift(currentWorldKey); await VH.DB.set("worlds_index", idx.slice(0, 16)); }
    if (!silent) showToast("Мир сохранён в IndexedDB!");
  }

  async function renderWorldsModal() {
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
    if (loadExisting) saved = await VH.DB.get("world_" + currentWorldKey, null);

    if (voxelWorld) voxelWorld.disposeAll();
    if (mobMgr) mobMgr.clearAll();
    if (dropMgr) dropMgr.clearAll();

    worldSeed = saved ? saved.seed : (seedStr ? parseInt(seedStr, 10) || hashStr(seedStr) : Math.floor(Math.random() * 999999));
    savedDimMods = saved && saved.dimMods ? saved.dimMods : (savedDimMods || { overworld: {}, nether: {} });
    savedDimChests = saved && saved.dimChests ? saved.dimChests : (savedDimChests || { overworld: {}, nether: {} });
    if (saved && saved.furnaces) furnaces = new Map(saved.furnaces);
    const startDim = saved && saved.dim ? saved.dim : forcedDim;

    if (saved && Array.isArray(saved.worldModIds)) selectedWorldModIds = saved.worldModIds;
    VH.modMgr.worldModFilter = selectedWorldModIds;
    VH.modMgr.reloadAll();

    voxelWorld = new VH.VoxelWorld(scene, worldSeed, startDim, savedDimMods[startDim] || {}, savedDimChests[startDim] || {});
    mobMgr = new VH.MobManager(scene, voxelWorld);
    dropMgr = new VH.DroppedItemManager(scene, voxelWorld);

    if (saved) {
      player.mode = saved.mode || "survival";
      worldTime = saved.time || 0.28; dayCount = saved.dayCount || 1;
      player.x = saved.player.x; player.y = saved.player.y; player.z = saved.player.z;
      player.spawnPos = saved.player.spawnPos || null;
      player.hp = saved.player.hp || 20; player.food = saved.player.food || 20;
      player.armor = saved.player.armor || { head: 0, chest: 0, legs: 0, feet: 0 };
      player.slots = Array.isArray(saved.player.slots) ? saved.player.slots : new Array(36).fill(null);
    } else {
      player.mode = $("mode-creative").classList.contains("active") ? "creative" : "survival";
      worldTime = 0.28; dayCount = 1;
      player.x = 0.5; player.z = 0.5;
      player.y = voxelWorld.getTerrainHeight(0, 0) + 3.2;
      player.spawnPos = null;
      player.hp = 20; player.food = 20;
      player.armor = { head: 125, chest: 0, legs: 0, feet: 0 };
      player.slots = new Array(36).fill(null);
      player.slots[0] = { id: 108, count: 1 };
      player.slots[1] = { id: 111, count: 1 };
      player.slots[2] = { id: 7,   count: 16 };
      player.slots[3] = { id: 17,  count: 1 };
      player.slots[4] = { id: 18,  count: 1 };
      player.slots[5] = { id: 35,  count: 2 };
      player.slots[6] = { id: 101, count: 8 };
      player.slots[7] = { id: 12,  count: 6 };
      player.slots[8] = { id: 113, count: 6 };
      player.slots[9]  = { id: 22, count: 14 };
      player.slots[10] = { id: 116, count: 1 };
      player.slots[11] = { id: 37, count: 12 };
      player.slots[12] = { id: 38, count: 1 };
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
    addChatLine("Мир", `Загружен «${currentWorldKey}» (${VH.WasmCore ? "Rust-WASM" : "JS Core"})`);
  }

  function updateModeUI() {
    const isCre = player.mode === "creative";
    $("classic-bars").classList.toggle("hidden", isCre);
    $("btn-fly").classList.toggle("hidden", !isCre);
  }

  // --- ЛОГИКА РАБОТЫ ПЕЧИ В РЕАЛЬНОМ ВРЕМЕНИ ---
  function updateFurnaces(dt) {
    for (const [key, f] of furnaces.entries()) {
      let dirty = false;
      if (f.burn > 0) {
        f.burn = Math.max(0, f.burn - dt * 10);
        dirty = true;
      }
      const rec = f.input ? SMELT_RECIPES.find((r) => r.inId === f.input.id) : null;
      const canCook = rec && (!f.output || (f.output.id === rec.out && f.output.count + rec.cnt <= 64));

      if (canCook && f.burn <= 0 && f.fuel && FUEL_VALUES[f.fuel.id]) {
        f.maxBurn = FUEL_VALUES[f.fuel.id];
        f.burn = f.maxBurn;
        f.fuel.count--;
        if (f.fuel.count <= 0) f.fuel = null;
        dirty = true;
      }

      if (canCook && f.burn > 0) {
        f.cook += dt * 20;
        dirty = true;
        if (f.cook >= 100) {
          f.cook = 0;
          f.input.count--;
          if (f.input.count <= 0) f.input = null;
          if (!f.output) f.output = { id: rec.out, count: rec.cnt };
          else f.output.count += rec.cnt;
          if (mpMgr?.isConnected()) mpMgr.broadcast({ t: "furnace_update", key, state: f });
        }
      } else if (!canCook && f.cook > 0) {
        f.cook = 0;
        dirty = true;
      }

      if (dirty && openFurnaceKey === key) renderFurnaceModal();
    }
  }

  // --- RAYCAST, КОПАНИЕ, УСТАНОВКА, ВЗАИМОДЕЙСТВИЕ ---
  function raycastBlock(maxDist = 5.5) {
    const dir = new THREE.Vector3(0, 0, -1).applyEuler(new THREE.Euler(player.pitch, player.yaw, 0, "YXZ"));
    const ox = player.x, oy = player.y + (player.sneaking ? 1.32 : 1.55), oz = player.z;
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

  function handleMobLoot(dead) {
    const drops = { sheep:114, cow:114, pig:114, chicken:114, zombie:101, skeleton:124, creeper:26, spider:105, enderman:104, piglin:103, blaze:23, ghast:104, inferno_lord:129 };
    const dropId = dead.drop || drops[dead.type] || 101;
    if (dropMgr) dropMgr.spawn(dropId, 1, dead.x, dead.y + 0.5, dead.z, 0, 3.2, 0);
    showToast(dead.isBoss ? "БОСС ПОВЕРЖЕН! Выпала Звезда Преисподней!" : ("Побеждён: " + dead.name));
  }

  function tryAttackOrMine(dt, isSingleTap = false) {
    player.armSwing = 1.0;
    const dir = new THREE.Vector3(0, 0, -1).applyEuler(new THREE.Euler(player.pitch, player.yaw, 0, "YXZ"));
    const heldSlot = player.slots[player.selectedSlot];
    const heldId = heldSlot ? heldSlot.id : 0;
    const heldInfo = VH.ITEMS_DB[heldId];

    if (isSingleTap && mobMgr) {
      const dmg = (heldInfo && heldInfo.dmg) ? heldInfo.dmg : 3;
      if (mobMgr.hitMobAtRay({ x: player.x, y: player.y + 1.55, z: player.z }, dir, 4.4, dmg, (dead) => handleMobLoot(dead))) {
        playSfx("hit");
        return;
      }
    }

    const hit = raycastBlock(PHYSICS.reach);
    if (!hit) { miningState.progress = 0; $("mine-ring").classList.add("hidden"); return; }
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

    $("mine-ring").classList.remove("hidden");
    $("mine-fill").style.width = Math.min(100, miningState.progress * 100) + "%";

    if (miningState.progress >= 1.0) {
      const brkEv = VH.modMgr.emit("onBlockBreak", { x: hit.x, y: hit.y, z: hit.z, id: hit.id, heldId, cancel: false });
      if (brkEv.cancel) { miningState.progress = 0; $("mine-ring").classList.add("hidden"); return; }
      if (typeof bInfo.onBreak === "function") { try { bInfo.onBreak({ x: hit.x, y: hit.y, z: hit.z, id: hit.id, player, engine: VH.Engine }); } catch {} }
      voxelWorld.setBlock(hit.x, hit.y, hit.z, 0, true);
      mpMgr.sendBlock(hit.x, hit.y, hit.z, 0);
      spawnBreakParticles(hit.x, hit.y, hit.z);
      playSfx("break");
      if (bInfo.drop && dropMgr) {
        dropMgr.spawn(bInfo.drop, 1, hit.x + 0.5, hit.y + 0.5, hit.z + 0.5, (Math.random()-0.5)*1.5, 2.8, (Math.random()-0.5)*1.5);
      }
      miningState.progress = 0;
      $("mine-ring").classList.add("hidden");
    }
  }

  function tryPlaceOrUse() {
    player.armSwing = 1.0;
    const heldSlot = player.slots[player.selectedSlot];
    const heldId = heldSlot ? heldSlot.id : 0;
    const heldInfo = VH.ITEMS_DB[heldId];

    const useEv = VH.modMgr.emit("onItemUse", { heldId, heldInfo, player, engine: VH.Engine, cancel: false });
    if (useEv.cancel) return;
    if (heldInfo && typeof heldInfo.onUse === "function") {
      try { if (heldInfo.onUse({ heldId, player, engine: VH.Engine }) !== false) return; } catch {}
    }

    // 1. Лук
    if (heldInfo && heldInfo.rangedBow) {
      if (player.mode === "creative" || takeInv(124, 1)) {
        const dir = new THREE.Vector3(0, 0, -1).applyEuler(new THREE.Euler(player.pitch, player.yaw, 0, "YXZ"));
        const from = { x: player.x + dir.x * 0.7, y: player.y + 1.5, z: player.z + dir.z * 0.7 };
        const to   = { x: from.x + dir.x * 15, y: from.y + dir.y * 15 + 1.2, z: from.z + dir.z * 15 };
        mobMgr.fireProjectile(from, to, heldInfo.dmg || 9, false, true);
        playSfx("bow");
      } else showToast("Нет стрел (ID 124)!");
      return;
    }

    // 2. Броня из руки
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

    // 3. Еда
    const foodVal = heldInfo ? (heldInfo.healFood || heldInfo.food || 0) : 0;
    if (foodVal > 0 && player.mode === "survival" && (player.food < 20 || player.hp < 20)) {
      if (takeInv(heldId, 1)) {
        player.food = Math.min(20, player.food + foodVal);
        player.hp = Math.min(20, player.hp + (heldInfo.healHp || 2));
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

    // 6. Посадка семян
    if (heldInfo && heldInfo.plantCrop && (hit.id === 1 || hit.id === 2)) {
      if (takeInv(heldId, 1)) { voxelWorld.setBlock(hit.x, hit.y + 1, hit.z, heldInfo.plantCrop, false); playSfx("place"); }
      return;
    }

    // 7. Интерактивные блоки: Верстак (17), Печь (18), Сундук (35), Кровать (38), TNT (26)
    const tInfo = VH.ITEMS_DB[hit.id];
    const intEv = VH.modMgr.emit("onBlockInteract", { x: hit.x, y: hit.y, z: hit.z, id: hit.id, heldId, cancel: false });
    if (intEv.cancel) return;
    if (tInfo && typeof tInfo.onInteract === "function") { try { tInfo.onInteract({ x: hit.x, y: hit.y, z: hit.z, id: hit.id, player, engine: VH.Engine }); } catch {} return; }

    if (tInfo && tInfo.interactive === "craft") { openCraftingTableModal(); return; }
    if (tInfo && tInfo.interactive === "smelt") { openFurnaceModal(hit.x, hit.y, hit.z); return; }
    if (tInfo && tInfo.interactive === "chest") { openChestModal(hit.x, hit.y, hit.z); return; }
    if (tInfo && tInfo.interactive === "bed") {
      player.spawnPos = { x: hit.x + 0.5, y: hit.y + 1.1, z: hit.z + 0.5, dim: voxelWorld.dimension };
      worldTime = 0.26;
      showToast("Точка возрождения установлена! Наступило утро.");
      return;
    }
    if (tInfo && tInfo.interactive === "tnt") { explodeAt(hit.x, hit.y, hit.z, 2.8); return; }

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

  // --- 1. ОКНО ИНВЕНТАРЯ (БРОНЯ + 2x2 КАРМАННЫЙ КРАФТ + СЛОТЫ, БЕЗ ПЛАВКИ!) ---
  function openInventoryModal() {
    paused = true;
    player.selectedInvSlot = -1;
    $("inventory-modal").classList.remove("hidden");
    renderInventoryModal();
  }

  function renderInventoryModal() {
    // 4 слота брони
    const ab = $("armor-slots-col");
    ab.innerHTML = "";
    const labels = { head: "Шлем", chest: "Грудь", legs: "Ноги", feet: "Ботинки" };
    for (const sk of ["head", "chest", "legs", "feet"]) {
      const aid = player.armor[sk];
      const btn = document.createElement("button");
      btn.className = "mc-btn tiny " + (aid ? "green" : "");
      btn.textContent = aid ? `${VH.ITEMS_DB[aid]?.name}` : `${labels[sk]}`;
      btn.onclick = () => {
        if (player.armor[sk]) { addInv(player.armor[sk], 1); player.armor[sk] = 0; renderClassicStatusBars(); renderInventoryModal(); }
      };
      ab.appendChild(btn);
    }

    // Мини-превью скина в инвентаре
    const icv = $("inv-skin-canvas");
    if (icv) {
      const ictx = icv.getContext("2d");
      ictx.clearRect(0, 0, icv.width, icv.height);
      const sk = getActiveSkinObj();
      ictx.fillStyle = sk.shirt; ictx.fillRect(28, 42, 28, 36);
      ictx.fillStyle = sk.skin;  ictx.fillRect(30, 14, 24, 24);
      ictx.fillStyle = sk.hair;  ictx.fillRect(30, 12, 24, 8);
      ictx.fillStyle = sk.pants; ictx.fillRect(30, 78, 11, 32); ictx.fillRect(43, 78, 11, 32);
    }

    // Карманный крафт 2x2 (ТОЛЬКО 5 базовых рецептов!)
    const pList = $("pocket-craft-list");
    pList.innerHTML = "";
    POCKET_RECIPES.forEach((rec) => {
      const can = player.mode === "creative" || rec.req.every(([rid, rcnt]) => countItem(rid) >= rcnt);
      const info = VH.ITEMS_DB[rec.out];
      const row = document.createElement("div"); row.className = "mc-list-row";
      row.innerHTML = `<span>${info.name} ×${rec.cnt}<br><small>${rec.desc}</small></span><button class="mc-btn tiny green" ${can ? "" : "disabled"}>+</button>`;
      row.querySelector("button").onclick = () => {
        if (!can) return;
        rec.req.forEach(([rid, rcnt]) => takeInv(rid, rcnt));
        addInv(rec.out, rec.cnt);
        playSfx("craft");
        renderInventoryModal();
      };
      pList.appendChild(row);
    });

    // Сетка Рюкзака (9..35) и Хотбара (0..8)
    const bagGrid = $("inv-bag-grid"), hbGrid = $("inv-hotbar-grid");
    bagGrid.innerHTML = ""; hbGrid.innerHTML = "";

    for (let i = 0; i < 36; i++) {
      const cell = document.createElement("div");
      renderSlotCell(cell, player.slots[i], i === player.selectedInvSlot);
      cell.onclick = () => {
        const cur = player.slots[i];
        const info = cur ? VH.ITEMS_DB[cur.id] : null;
        if (info && info.armorSlot && !player.armor[info.armorSlot] && player.selectedInvSlot === -1) {
          player.armor[info.armorSlot] = cur.id;
          takeInv(cur.id, 1);
          renderClassicStatusBars(); renderInventoryModal();
          return;
        }
        if (player.selectedInvSlot === -1) {
          player.selectedInvSlot = i;
        } else if (player.selectedInvSlot === i) {
          player.selectedInvSlot = -1;
        } else {
          const a = player.slots[player.selectedInvSlot], b = player.slots[i];
          if (a && b && a.id === b.id && b.count < 64) {
            const mv = Math.min(64 - b.count, a.count);
            b.count += mv; a.count -= mv;
            if (a.count <= 0) player.slots[player.selectedInvSlot] = null;
          } else {
            player.slots[player.selectedInvSlot] = b;
            player.slots[i] = a;
          }
          player.selectedInvSlot = -1;
          renderHotbar();
        }
        renderInventoryModal();
      };
      (i < 9 ? hbGrid : bagGrid).appendChild(cell);
    }
  }

  // --- 2. ОКНО ВЕРСТАКА (ПОЛНЫЙ 3x3 КРАФТ) ---
  function openCraftingTableModal() {
    paused = true;
    $("crafting-modal").classList.remove("hidden");
    const box = $("table-craft-grid");
    box.innerHTML = "";
    CRAFT_RECIPES.forEach((rec) => {
      const can = player.mode === "creative" || rec.req.every(([rid, rcnt]) => countItem(rid) >= rcnt);
      const info = VH.ITEMS_DB[rec.out];
      if (!info) return;
      const card = document.createElement("div"); card.className = "mc-card";
      const cv = document.createElement("canvas"); VH.drawItemIcon(cv, rec.out);
      card.innerHTML = `<div class="mc-card-head"></div><div class="mc-card-sub">${rec.desc}</div><button class="mc-btn tiny green" ${can ? "" : "disabled"}>Скрафтить ×${rec.cnt}</button>`;
      card.querySelector(".mc-card-head").appendChild(cv);
      card.querySelector(".mc-card-head").append(info.name);
      card.querySelector("button").onclick = () => {
        if (!can) return;
        rec.req.forEach(([rid, rcnt]) => takeInv(rid, rcnt));
        addInv(rec.out, rec.cnt);
        playSfx("craft");
        openCraftingTableModal();
      };
      box.appendChild(card);
    });
  }

  // --- 3. ОКНО ПЕЧИ (ОТДЕЛЬНЫЙ БЛОК С ТОПЛИВОМ И СИНХРОНИЗАЦИЕЙ) ---
  let openFurnaceKey = null;
  function openFurnaceModal(x, y, z) {
    paused = true;
    openFurnaceKey = `${voxelWorld.dimension}:${Math.floor(x)},${Math.floor(y)},${Math.floor(z)}`;
    if (!furnaces.has(openFurnaceKey)) {
      furnaces.set(openFurnaceKey, { input: null, fuel: null, output: null, burn: 0, maxBurn: 1, cook: 0 });
    }
    $("furnace-modal").classList.remove("hidden");
    renderFurnaceModal();
  }

  function renderFurnaceModal() {
    if (!openFurnaceKey) return;
    const f = furnaces.get(openFurnaceKey);
    if (!f) return;

    renderSlotCell($("furnace-in-slot"), f.input);
    renderSlotCell($("furnace-fuel-slot"), f.fuel);
    renderSlotCell($("furnace-out-slot"), f.output);
    $("furnace-flame-lbl").textContent = `🔥 ${Math.round((f.burn / Math.max(1, f.maxBurn)) * 100)}%`;
    $("furnace-prog-fill").style.width = `${Math.min(100, Math.round(f.cook))}%`;

    const syncF = () => {
      if (mpMgr?.isConnected()) mpMgr.broadcast({ t: "furnace_update", key: openFurnaceKey, state: f });
    };

    $("furnace-in-slot").onclick = () => { if (f.input && addInv(f.input.id, f.input.count)) { f.input = null; syncF(); renderFurnaceModal(); } };
    $("furnace-fuel-slot").onclick = () => { if (f.fuel && addInv(f.fuel.id, f.fuel.count)) { f.fuel = null; syncF(); renderFurnaceModal(); } };
    $("furnace-out-slot").onclick = () => { if (f.output && addInv(f.output.id, f.output.count)) { f.output = null; syncF(); renderFurnaceModal(); } };

    const pGrid = $("furnace-player-grid");
    pGrid.innerHTML = "";
    for (let i = 0; i < 36; i++) {
      const s = player.slots[i];
      if (!s) continue;
      const cell = document.createElement("div");
      renderSlotCell(cell, s);
      cell.onclick = () => {
        const isSmeltable = SMELT_RECIPES.some((r) => r.inId === s.id);
        const isFuel = Boolean(FUEL_VALUES[s.id]);
        if (isSmeltable && (!f.input || f.input.id === s.id)) {
          f.input = f.input ? { id: s.id, count: f.input.count + s.count } : { id: s.id, count: s.count };
          player.slots[i] = null;
        } else if (isFuel && (!f.fuel || f.fuel.id === s.id)) {
          f.fuel = f.fuel ? { id: s.id, count: f.fuel.count + s.count } : { id: s.id, count: s.count };
          player.slots[i] = null;
        } else {
          showToast("Положите руду/песок/мясо (Сырьё) или уголь/дерево (Топливо)!");
          return;
        }
        renderHotbar(); syncF(); renderFurnaceModal();
      };
      pGrid.appendChild(cell);
    }
  }

  // --- 4. ОКНО СУНДУКА (27 СЛОТОВ, СИНХРОНИЗИРОВАНО В СЕТИ) ---
  let openChestCoords = null;
  function openChestModal(x, y, z) {
    paused = true;
    openChestCoords = { dim: voxelWorld.dimension, x: Math.floor(x), y: Math.floor(y), z: Math.floor(z) };
    $("chest-modal").classList.remove("hidden");
    renderChestModal();
  }

  function renderChestModal() {
    if (!openChestCoords || !voxelWorld) return;
    const { dim, x, y, z } = openChestCoords;
    const items = voxelWorld.getChestItems(x, y, z);
    const syncC = () => {
      if (mpMgr?.isConnected()) mpMgr.broadcast({ t: "chest_update", dim, x, y, z, slots: items });
    };

    const cGrid = $("chest-grid");
    cGrid.innerHTML = "";
    items.forEach((slotObj, idx) => {
      const cell = document.createElement("div");
      renderSlotCell(cell, slotObj);
      cell.onclick = () => {
        if (slotObj && slotObj.id && slotObj.count > 0 && addInv(slotObj.id, slotObj.count)) {
          items[idx] = null;
          syncC(); renderChestModal();
        }
      };
      cGrid.appendChild(cell);
    });

    const pGrid = $("chest-player-grid");
    pGrid.innerHTML = "";
    for (let i = 0; i < 36; i++) {
      const s = player.slots[i];
      if (!s) continue;
      const cell = document.createElement("div");
      renderSlotCell(cell, s);
      cell.onclick = () => {
        const emptyIdx = items.findIndex((x) => !x);
        if (emptyIdx === -1) { showToast("Сундук полон!"); return; }
        items[emptyIdx] = { id: s.id, count: s.count };
        player.slots[i] = null;
        renderHotbar(); syncC(); renderChestModal();
      };
      pGrid.appendChild(cell);
    }
  }

  // --- ДВУХПАЛЬЦЕВОЕ СЕНСОРНОЕ УПРАВЛЕНИЕ БЕЗ КРЕСТИКА ---
  const input = { moveX: 0, moveZ: 0, jump: false, holdingBreak: false };
  const keys = {};

  function setupControls() {
    // 1. Левая половина экрана: плавающий виртуальный стик движения
    const mZone = $("touch-move-zone"), fStick = $("floating-stick"), fKnob = $("floating-stick-knob");
    let moveTid = null, origX = 0, origY = 0;

    mZone.addEventListener("touchstart", (e) => {
      e.preventDefault();
      if (moveTid !== null) return;
      const t = e.changedTouches[0];
      moveTid = t.identifier;
      origX = t.clientX; origY = t.clientY;
      fStick.style.left = origX + "px";
      fStick.style.top = origY + "px";
      fStick.classList.remove("hidden");
    }, { passive: false });

    mZone.addEventListener("touchmove", (e) => {
      e.preventDefault();
      for (const t of e.changedTouches) {
        if (t.identifier !== moveTid) continue;
        let dx = t.clientX - origX, dy = t.clientY - origY;
        const dist = Math.hypot(dx, dy);
        const maxR = 40;
        if (dist > maxR) { dx = dx / dist * maxR; dy = dy / dist * maxR; }
        fKnob.style.transform = `translate(${dx}px, ${dy}px)`;
        input.moveX = dx / maxR;
        input.moveZ = -dy / maxR;
      }
    }, { passive: false });

    const endMove = (e) => {
      for (const t of e.changedTouches) {
        if (t.identifier === moveTid) {
          moveTid = null; input.moveX = 0; input.moveZ = 0;
          fKnob.style.transform = "translate(0px, 0px)";
          fStick.classList.add("hidden");
        }
      }
    };
    mZone.addEventListener("touchend", endMove);
    mZone.addEventListener("touchcancel", endMove);

    // 2. Правая половина экрана: свободный обзор пальцем + короткий тап (действие) + удержание (ломать)
    const lZone = $("touch-look-zone");
    let lookTid = null, startX = 0, startY = 0, lastX = 0, lastY = 0, startTime = 0, moved = false, holdTimer = null;

    lZone.addEventListener("touchstart", (e) => {
      e.preventDefault();
      if (!running || paused || lookTid !== null) return;
      const t = e.changedTouches[0];
      lookTid = t.identifier;
      startX = lastX = t.clientX; startY = lastY = t.clientY;
      startTime = performance.now(); moved = false;
      clearTimeout(holdTimer);
      holdTimer = setTimeout(() => {
        if (!moved && lookTid !== null) {
          input.holdingBreak = true;
          tryAttackOrMine(0.05, true);
        }
      }, 220);
    }, { passive: false });

    lZone.addEventListener("touchmove", (e) => {
      e.preventDefault();
      if (!running || paused) return;
      for (const t of e.changedTouches) {
        if (t.identifier !== lookTid) continue;
        const dx = t.clientX - lastX, dy = t.clientY - lastY;
        lastX = t.clientX; lastY = t.clientY;
        if (Math.hypot(t.clientX - startX, t.clientY - startY) > 14) {
          moved = true; clearTimeout(holdTimer); input.holdingBreak = false;
          miningState.progress = 0; $("mine-ring").classList.add("hidden");
        }
        player.yaw -= dx * 0.0055 * SETTINGS.sens;
        player.pitch = Math.max(-1.45, Math.min(1.45, player.pitch - dy * 0.0055 * SETTINGS.sens));
      }
    }, { passive: false });

    const endLook = (e) => {
      for (const t of e.changedTouches) {
        if (t.identifier !== lookTid) continue;
        clearTimeout(holdTimer);
        const dur = performance.now() - startTime;
        if (!moved && !input.holdingBreak && dur < 220) {
          const dir = new THREE.Vector3(0, 0, -1).applyEuler(new THREE.Euler(player.pitch, player.yaw, 0, "YXZ"));
          const heldInfo = VH.ITEMS_DB[player.slots[player.selectedSlot]?.id || 0];
          const hitMob = mobMgr && mobMgr.hitMobAtRay({ x: player.x, y: player.y + 1.55, z: player.z }, dir, 4.4, heldInfo?.dmg || 3, (d) => handleMobLoot(d));
          if (hitMob) { player.armSwing = 1.0; playSfx("hit"); }
          else tryPlaceOrUse();
        }
        lookTid = null; input.holdingBreak = false; miningState.progress = 0; $("mine-ring").classList.add("hidden");
      }
    };
    lZone.addEventListener("touchend", endLook);
    lZone.addEventListener("touchcancel", endLook);

    // 3. Правые кнопки: Прыжок, Присед, Выбросить, Полёт
    const bJump = $("btn-jump");
    bJump.addEventListener("touchstart", (e) => { e.preventDefault(); input.jump = true; }, { passive: false });
    bJump.addEventListener("touchend", () => { input.jump = false; });
    bJump.addEventListener("mousedown", () => { input.jump = true; });
    bJump.addEventListener("mouseup", () => { input.jump = false; });

    $("btn-sneak").onclick = () => { player.sneaking = !player.sneaking; showToast(player.sneaking ? "Присед ВКЛ" : "Присед ВЫКЛ"); };
    $("btn-drop").onclick  = () => dropFromSlot(player.selectedSlot, 1);
    $("btn-fly").onclick   = () => { if (player.mode === "creative") { player.flying = !player.flying; player.vy = 0; showToast(player.flying ? "Полёт ВКЛ" : "Полёт ВЫКЛ"); } };

    // 4. ПК-управление (WASD + Мышь + Q выброс + E инвентарь)
    window.addEventListener("keydown", (e) => {
      if (document.activeElement && (document.activeElement.tagName === "INPUT" || document.activeElement.tagName === "TEXTAREA")) return;
      keys[e.code] = true;
      if (e.code >= "Digit1" && e.code <= "Digit9") { player.selectedSlot = +e.code[5] - 1; renderHotbar(); }
      if (e.code === "KeyQ" && running && !paused) dropFromSlot(player.selectedSlot, 1);
      if (e.code === "KeyE" && running) { paused ? ($("inventory-modal").classList.add("hidden"), paused = false) : openInventoryModal(); }
    });
    window.addEventListener("keyup", (e) => { keys[e.code] = false; });
    const canvas = $("game-canvas");
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
    let mx = input.moveX + (keys["KeyD"] ? 1 : 0) - (keys["KeyA"] ? 1 : 0);
    let mz = input.moveZ + (keys["KeyW"] ? 1 : 0) - (keys["KeyS"] ? 1 : 0);
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
    updateFurnaces(dt);

    mobMgr.update(dt, player, isNight, (d) => damagePlayer(d), (ex, ey, ez, r) => explodeAt(ex, ey, ez, r), (dead) => handleMobLoot(dead));
    if (dropMgr) dropMgr.update(dt, player, (id, cnt) => { addInv(id, cnt); playSfx("craft"); });
    if (input.holdingBreak) tryAttackOrMine(dt, false);

    const boss = mobMgr.getActiveBoss();
    if (boss) {
      $("boss-bar").classList.remove("hidden");
      $("boss-name").textContent = `${boss.name} (${Math.max(0, Math.ceil(boss.hp))}/${boss.maxHp})`;
      $("boss-fill").style.width = Math.max(0, (boss.hp / boss.maxHp) * 100) + "%";
      updateSoundtrack("ost_boss.wav");
    } else {
      $("boss-bar").classList.add("hidden");
      updateSoundtrack(isNether ? "ost_nether.wav" : (isNight ? "ost_overworld_night.wav" : "ost_overworld_day.wav"));
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

    const eyeH = player.sneaking ? 1.32 : 1.55;
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
      if (playerAvatar.userData.tail) playerAvatar.userData.tail.rotation.y = Math.sin(ts * 0.006) * 0.28;
      if (playerAvatar.userData.braids) playerAvatar.userData.braids.rotation.x = Math.sin(ts * 0.005) * 0.12;
    } else {
      camera.position.set(player.x, player.y + eyeH + bobY, player.z);
      fpArmGroup.visible = true; playerAvatar.visible = false;
      fpArmGroup.position.set(Math.cos(player.walkBob * 0.5) * 0.03, bobY * 0.5, -Math.sin(player.armSwing * Math.PI) * 0.25);
      fpArmGroup.rotation.x = -Math.sin(player.armSwing * Math.PI) * 0.75;
    }

    const hit = raycastBlock(PHYSICS.reach);
    highlightBox.visible = Boolean(hit);
    if (hit) highlightBox.position.set(hit.x + 0.5, hit.y + 0.5, hit.z + 0.5);

    mpTimer += dt;
    if (mpTimer > 0.09) { mpTimer = 0; mpMgr.sendPos(player.x, player.y, player.z, player.yaw); }

    hudTimer += dt;
    if (hudTimer > 0.25) {
      hudTimer = 0;
      const dimTxt = cDim ? (cDim.name || voxelWorld.dimension) : (isNether ? "АД (Nether)" : (isNight ? "Ночь " + dayCount : "День " + dayCount));
      $("coords-box").textContent = `Позиция: ${Math.floor(player.x)}, ${Math.floor(player.y)}, ${Math.floor(player.z)} | ${dimTxt}`;
    }

    renderer.render(scene, camera);
  }

  // --- ГАРДЕРОБ ---
  function renderSkinsMenu() {
    const grid = $("skins-grid");
    grid.innerHTML = "";
    VH.DETAILED_SKINS.forEach((sk) => {
      const card = document.createElement("div");
      card.className = "mc-skin-card" + (sk.id === player.skinId ? " active" : "");
      card.style.padding = "8px";
      card.textContent = sk.name;
      card.onclick = () => {
        player.skinId = sk.id; VH.activeSkinId = sk.id;
        localStorage.setItem("vh_skin_id", sk.id);
        rebuildPlayerVisuals();
        if (mpMgr) mpMgr.sendSkin();
        refreshSkinPreviews();
        renderSkinsMenu();
      };
      grid.appendChild(card);
    });
    refreshSkinPreviews();
  }

  // --- ЧАТ И КОМАНДЫ ---
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

    if (text === "/tp_host") {
      const firstPeer = mpMgr.remotePlayers.values().next().value;
      if (firstPeer) { player.x = firstPeer.x + 1.2; player.y = firstPeer.y + 0.5; player.z = firstPeer.z + 1.2; showToast("Телепорт к игроку!"); }
      else showToast("Нет подключённых игроков!");
    } else if (text === "/spawn") {
      player.x = 0.5; player.z = 0.5; player.y = voxelWorld.dimension === "nether" ? 26.2 : voxelWorld.getTerrainHeight(0, 0) + 3.5;
    } else if (text === "/nether") switchDimension("nether");
    else if (text === "/overworld") switchDimension("overworld");
    else if (text === "/boss") { mobMgr.spawnMob("inferno_lord", player.x + 5, player.y + 1, player.z + 5); showToast("Призван Повелитель Преисподней!"); }
    else if (text === "/time day") { worldTime = 0.28; showToast("Установлен день!"); }
    else if (text === "/time night") { worldTime = 0.85; showToast("Установлена ночь!"); }
    else if (text.startsWith("/give ")) {
      const parts = text.split(/\s+/);
      const id = parseInt(parts[1], 10);
      const cnt = parseInt(parts[2], 10) || 16;
      if (VH.ITEMS_DB[id]) { addInv(id, cnt); showToast("Выдано: " + VH.ITEMS_DB[id].name + " ×" + cnt); }
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
      const reader = new FileReader();
      reader.onload = () => {
        const rawUrl = String(reader.result || "");
        const img = new Image();
        img.onload = async () => {
          const normCv = document.createElement("canvas");
          normCv.width = 64; normCv.height = img.height <= 32 ? 32 : 64;
          const nctx = normCv.getContext("2d");
          nctx.imageSmoothingEnabled = false;
          nctx.drawImage(img, 0, 0, normCv.width, normCv.height);
          const dataUrl = normCv.toDataURL("image/png");

          const normImg = new Image();
          normImg.onload = async () => {
            const custom = { id: "custom", name: "Свой PNG", skin: "#e2b28b", hair: "#333", shirt: "#2ed573", pants: "#334", eye: "#fff", customImg: normImg };
            const ex = VH.DETAILED_SKINS.findIndex((s) => s.id === "custom");
            if (ex >= 0) VH.DETAILED_SKINS[ex] = custom; else VH.DETAILED_SKINS.push(custom);
            player.skinId = "custom"; VH.activeSkinId = "custom"; VH.activeCustomSkinData = dataUrl;
            localStorage.setItem("vh_skin_id", "custom");
            await VH.DB.set("custom_skin_png", dataUrl);
            rebuildPlayerVisuals();
            if (mpMgr) mpMgr.sendSkin();
            renderSkinsMenu();
          };
          normImg.src = dataUrl;
        };
        img.src = rawUrl;
      };
      reader.readAsDataURL(file);
    };

    const openMp = () => { paused = running; $("mp-modal").classList.remove("hidden"); };
    $("btn-open-mp").onclick = openMp;
    $("btn-mp-ingame").onclick = openMp;
    $("btn-close-mp").onclick = () => { $("mp-modal").classList.add("hidden"); if (running) paused = false; };
    $("btn-mp-host").onclick = () => mpMgr.hostRoom($("mp-nick-input").value, (st) => ($("mp-host-status").textContent = st));
    $("btn-mp-join").onclick = () => {
      mpMgr.joinRoom($("mp-join-code").value, $("mp-nick-input").value, (st) => ($("mp-host-status").textContent = st));
    };

    $("btn-mp-accept-mods").onclick = () => {
      if (!pendingHostSync) return;
      VH.modMgr.sessionMods = pendingHostSync.projects || [];
      VH.modMgr.reloadAll();
      $("mp-mods-prompt").classList.add("hidden");
      showToast("Моды сервера подкачаны на сессию!");
    };
    $("btn-mp-decline-mods").onclick = () => { $("mp-mods-prompt").classList.add("hidden"); };

    $("btn-close-mod-custom").onclick = () => VH.Mods.ui.closeModal();


  VH.setAnimeTheme = function (enable) {
    const on = Boolean(enable);
    document.body.classList.toggle("anime-ui", on);
    const chk = $("inp-anime-ui");
    if (chk) chk.checked = on;
    if (VH.applyAnimeTexturePack) VH.applyAnimeTexturePack(on);
  };

  function switchSettingsTab(tab) {
    $("set-tab-general").classList.toggle("active", tab === "general");
    $("set-tab-modhub").classList.toggle("active", tab === "modhub");
    $("set-pane-general").classList.toggle("hidden", tab !== "general");
    $("set-pane-modhub").classList.toggle("hidden", tab !== "modhub");
    if (tab === "modhub") renderSettingsModHub();
  }

  function renderSettingsModHub() {
    const box = $("settings-modhub-list");
    if (!box) return;
    box.innerHTML = "";
    VH.modMgr.projects.forEach((proj) => {
      const card = document.createElement("div");
      card.className = "modhub-card" + (proj.enabled ? " active" : "");
      const filesCnt = Object.keys(proj.files || {}).length;
      card.innerHTML =
        `<div><div class="modhub-title">${proj.name}</div><div class="modhub-sub">Файлов: ${filesCnt} • ${proj.enabled ? "Активен" : "Отключён"}</div></div>` +
        `<label class="vh-switch"><input type="checkbox" ${proj.enabled ? "checked" : ""} /><span class="vh-slider"></span></label>`;
      card.querySelector("input").onchange = async (e) => {
        proj.enabled = e.target.checked;
        if (Array.isArray(selectedWorldModIds)) {
          if (proj.enabled && !selectedWorldModIds.includes(proj.id)) selectedWorldModIds.push(proj.id);
          if (!proj.enabled) selectedWorldModIds = selectedWorldModIds.filter((x) => x !== proj.id);
        }
        await VH.modMgr.saveProjects();
        VH.modMgr.reloadAll();
        renderSettingsModHub();
        showToast((proj.enabled ? "Включён: " : "Отключён: ") + proj.name);
      };
      box.appendChild(card);
    });
  }

    const openSet = () => {
      renderSettingsModHub();
      $("settings-modal").classList.remove("hidden");
    };
    $("btn-open-settings").onclick = openSet;
    $("btn-pause-settings").onclick = openSet;
    $("set-tab-general").onclick = () => switchSettingsTab("general");
    $("set-tab-modhub").onclick  = () => switchSettingsTab("modhub");
    $("inp-anime-ui").onchange = async (e) => {
      const on = e.target.checked;
      const ap = VH.modMgr.projects.find((p) => p.id === "proj_anime_pack");
      if (ap) { ap.enabled = on; await VH.modMgr.saveProjects(); }
      VH.setAnimeTheme(on);
      renderSettingsModHub();
      showToast(on ? "Аниме-тема и HD-текстуры ВКЛ!" : "Классическая тема ВКЛ!");
    };
    $("btn-modhub-open-ide").onclick = () => {
      $("settings-modal").classList.add("hidden");
      $("btn-open-mods").click();
    };
    $("inp-modhub-file").onchange = (e) => {
      const f = e.target.files[0]; if (!f) return;
      const r = new FileReader();
      r.onload = async () => {
        const raw = String(r.result || "");
        if (f.name.endsWith(".vhmod") || f.name.endsWith(".json")) {
          try {
            const parsed = JSON.parse(raw);
            if (parsed && parsed.files) { parsed.id = "proj_" + Date.now(); parsed.enabled = true; VH.modMgr.projects.push(parsed); }
          } catch {}
        } else {
          const id = "proj_" + Date.now();
          VH.modMgr.projects.push({ id, name: f.name.replace(/\.js$/i, ""), enabled: true, files: { "manifest.json": JSON.stringify({ id, name: f.name, version: "1.0" }), "scripts/main.js": raw } });
        }
        await VH.modMgr.saveProjects();
        VH.modMgr.reloadAll();
        renderSettingsModHub();
        showToast("Мод добавлен в Хаб: " + f.name);
      };
      r.readAsText(f);
    };
    const closeSet = () => {
      SETTINGS.renderDist = +$("inp-render-dist").value;
      SETTINGS.sens = +$("inp-sens").value;
      SETTINGS.sound = $("inp-sound").checked;
      if (!SETTINGS.sound && ostAudio) { ostAudio.pause(); currentOstFile = ""; }
      $("settings-modal").classList.add("hidden");
    };
    $("btn-close-settings").onclick = closeSet;
    $("btn-save-settings").onclick = closeSet;
    $("inp-render-dist").oninput = (e) => ($("lbl-render-dist").textContent = e.target.value);
    $("inp-sens").oninput = (e) => ($("lbl-sens").textContent = (+e.target.value).toFixed(1));

    // Раздельные модалки: Инвентарь (2x2), Верстак (3x3), Печь, Сундук
    $("btn-inv-dots").onclick = () => openInventoryModal();
    $("btn-close-inv").onclick = () => { $("inventory-modal").classList.add("hidden"); paused = false; };
    $("btn-inv-drop").onclick = () => {
      if (player.selectedInvSlot !== -1) { dropFromSlot(player.selectedInvSlot, 64); renderInventoryModal(); }
    };
    $("btn-close-crafting").onclick = () => { $("crafting-modal").classList.add("hidden"); paused = false; };
    $("btn-close-furnace").onclick  = () => { $("furnace-modal").classList.add("hidden"); openFurnaceKey = null; paused = false; };
    $("btn-close-chest").onclick    = () => { $("chest-modal").classList.add("hidden"); openChestCoords = null; paused = false; saveCurrentWorld(true); };

    $("btn-cam-view").onclick = () => { player.camThirdPerson = !player.camThirdPerson; showToast(player.camThirdPerson ? "Камера: 3-е лицо" : "Камера: 1-е лицо"); };

    // Удобный чат вверху экрана + быстрые чипы команд
    $("btn-chat-open").onclick = () => { paused = true; $("chat-modal").classList.remove("hidden"); setTimeout(() => $("chat-input").focus(), 40); };
    $("btn-chat-close").onclick = () => { $("chat-modal").classList.add("hidden"); paused = false; };
    $("btn-chat-send").onclick = () => { handleChatCommand($("chat-input").value); $("chat-input").value = ""; $("chat-modal").classList.add("hidden"); paused = false; };
    $("chat-input").addEventListener("keydown", (e) => {
      if (e.key === "Enter") { handleChatCommand($("chat-input").value); $("chat-input").value = ""; $("chat-modal").classList.add("hidden"); paused = false; }
    });
    document.querySelectorAll(".cmd-chip").forEach((chip) => {
      chip.onclick = () => {
        const cmd = chip.getAttribute("data-cmd") || "";
        handleChatCommand(cmd);
        $("chat-modal").classList.add("hidden");
        paused = false;
      };
    });

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
      if (ostAudio) { ostAudio.pause(); currentOstFile = ""; }
      $("pause-modal").classList.add("hidden"); $("hud").classList.add("hidden");
      $("main-menu").classList.remove("hidden");
      refreshSkinPreviews();
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
    get dropMgr() { return dropMgr; },
    get mpMgr() { return mpMgr; },
    get savedDimMods() { return savedDimMods; },
    get savedDimChests() { return savedDimChests; },
    get furnaces() { return furnaces; },
    get worldTime() { return worldTime; },
    set worldTime(v) { worldTime = v; },
    get dayCount() { return dayCount; },
    player, SETTINGS, physics: PHYSICS, input, keys,
    ITEMS_DB: VH.ITEMS_DB, SKINS_DB: VH.DETAILED_SKINS, CRAFT_RECIPES, SMELT_RECIPES,
    getBlock: (x, y, z) => voxelWorld ? voxelWorld.getBlock(x, y, z) : 0,
    setBlock: (x, y, z, id) => { if (voxelWorld) { voxelWorld.setBlock(x, y, z, id, true); mpMgr.sendBlock(x, y, z, id); } },
    applyWorldSync, applyChestSync, applyFurnaceSync, handleMobLoot,
    raycastBlock, explodeAt, switchDimension, spawnBreakParticles, playSfx, damagePlayer,
    addInv, takeInv, renderHotbar, renderClassicStatusBars, saveCurrentWorld, addChatLine, showToast,
    openInvModal: openInventoryModal,
    setPaused: (v) => { paused = Boolean(v); },
    buildPortalFront: () => { if (voxelWorld) voxelWorld.buildNetherPortalAt(Math.floor(player.x) + 2, Math.floor(player.y), Math.floor(player.z)); }
  };

  initThree();
  setupControls();
  bindUI();
  if (VH.bindStudioEvents) VH.bindStudioEvents();
  VH.modMgr.initFromDB();
  VH.DB.get("custom_skin_png", null).then((dataUrl) => {
    if (dataUrl && typeof dataUrl === "string") {
      VH.activeCustomSkinData = dataUrl;
      const img = new Image();
      img.onload = () => {
        const custom = { id: "custom", name: "Свой PNG", skin: "#e2b28b", hair: "#333", shirt: "#2ed573", pants: "#334", eye: "#fff", customImg: img };
        const ex = VH.DETAILED_SKINS.findIndex((s) => s.id === "custom");
        if (ex >= 0) VH.DETAILED_SKINS[ex] = custom; else VH.DETAILED_SKINS.push(custom);
        if (player.skinId === "custom") rebuildPlayerVisuals();
        refreshSkinPreviews();
      };
      img.src = dataUrl;
    }
  });
  refreshSkinPreviews();
  requestAnimationFrame(loop);
})();
