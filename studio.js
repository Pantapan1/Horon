// ============================================================================
// VOXELHORIZON v3.0 — ВСТРОЕННАЯ СТУДИЯ МОДОВ (.VHMOD IDE: КОД, 2D, 3D, АНИМАЦИИ)
// ============================================================================
window.VH = window.VH || {};

(function () {
  "use strict";
  const $ = (id) => document.getElementById(id);

  let activeProjId = null;
  let activeFilePath = "scripts/main.js";
  let activeStudioTab = "code";

  // --- 2D PIXEL ART STATE ---
  let pxFrames = [new Array(256).fill(null)];
  let pxCurFrame = 0;
  let pxColor = "#2ed573";
  let pxTool = "brush"; // brush | erase | fill | pick
  let pxMirrorX = false;
  let pxOnion = true;
  let pxPlaying = false;
  let pxTimer = null;

  // --- 3D MODELER & ANIMATION STATE ---
  let stRenderer = null, stScene = null, stCamera = null, stModelGroup = null;
  let stYaw = 0.6, stPitch = 0.35, stZoom = 2.6;
  let stModelData = {
    name: "my_mob",
    bones: [
      { name: "root", pivot: [0, 0, 0], cubes: [{ origin: [-4, 0, -4], size: [8, 8, 8], color: "#2ed573", rotation: [0, 0, 0] }] },
      { name: "head", parent: "root", pivot: [0, 8, 0], cubes: [{ origin: [-3, 8, -3], size: [6, 6, 6], color: "#ffdd33", rotation: [0, 0, 0] }] }
    ]
  };
  let stSelBone = 0;
  let stSelCube = 0;

  let stAnimData = {
    duration: 1.0,
    loop: true,
    tracks: {
      root: [{ time: 0.0, rot: [0, 0, 0] }, { time: 0.5, rot: [0, 15, 0] }, { time: 1.0, rot: [0, 0, 0] }],
      head: [{ time: 0.0, rot: [0, 0, 0] }, { time: 0.5, rot: [-12, 0, 0] }, { time: 1.0, rot: [0, 0, 0] }]
    }
  };
  let stAnimTime = 0.0;
  let stAnimPlaying = false;
  let stAnimRaf = null;

  function getProject() {
    const list = VH.modMgr.projects;
    let p = list.find((x) => x.id === activeProjId) || list[0];
    if (p) activeProjId = p.id;
    return p;
  }

  function sync3DFromProject(proj) {
    if (!proj || !proj.files) return;
    const mKey = Object.keys(proj.files).find((k) => k.endsWith(".vhmodel.json"));
    if (mKey) {
      try { stModelData = JSON.parse(proj.files[mKey]); } catch {}
    }
    const aKey = Object.keys(proj.files).find((k) => k.endsWith(".vhanim.json"));
    if (aKey) {
      try { stAnimData = JSON.parse(proj.files[aKey]); } catch {}
    }
    const tKey = Object.keys(proj.files).find((k) => k.startsWith("textures/") && k.endsWith(".json"));
    if (tKey) {
      try {
        const parsed = JSON.parse(proj.files[tKey]);
        if (Array.isArray(parsed.pixels) && parsed.pixels.length === 256) {
          pxFrames = [parsed.pixels.slice()];
          pxCurFrame = 0;
        }
      } catch {}
    }
  }

  function save3DToProject() {
    const proj = getProject();
    if (!proj) return;
    const mKey = Object.keys(proj.files).find((k) => k.endsWith(".vhmodel.json")) || "models/entity.vhmodel.json";
    const aKey = Object.keys(proj.files).find((k) => k.endsWith(".vhanim.json")) || "animations/entity.vhanim.json";
    proj.files[mKey] = JSON.stringify(stModelData, null, 2);
    proj.files[aKey] = JSON.stringify(stAnimData, null, 2);
    VH.modMgr.saveProjects();
  }

  // ==========================================================================
  // ОТКРЫТИЕ / ЗАКРЫТИЕ СТУДИИ МОДОВ
  // ==========================================================================
  function openStudioModal() {
    if (VH.Engine) VH.Engine.setPaused(true);
    $("mods-modal").classList.remove("hidden");
    const proj = getProject();
    if (proj) sync3DFromProject(proj);
    renderProjectBar();
    switchStudioTab(activeStudioTab);
  }

  function renderProjectBar() {
    const sel = $("studio-proj-select");
    sel.innerHTML = "";
    VH.modMgr.projects.forEach((p) => {
      const opt = document.createElement("option");
      opt.value = p.id;
      opt.textContent = (p.enabled ? "[ВКЛ] " : "[ВЫКЛ] ") + p.name;
      if (p.id === activeProjId) opt.selected = true;
      sel.appendChild(opt);
    });
    const proj = getProject();
    if (proj) {
      $("btn-studio-toggle").textContent = proj.enabled ? "Мод: ВКЛ" : "Мод: ВЫКЛ";
      $("btn-studio-toggle").classList.toggle("green", proj.enabled);
    }
  }

  function switchStudioTab(tab) {
    activeStudioTab = tab;
    ["code", "px2d", "vox3d", "anim"].forEach((t) => {
      $("st-tab-" + t).classList.toggle("active", t === tab);
      $("st-pane-" + t).classList.toggle("hidden", t !== tab);
    });

    if (tab === "code") {
      renderFileTree();
    } else if (tab === "px2d") {
      renderPixelCanvas();
    } else if (tab === "vox3d" || tab === "anim") {
      // Переносим общий 3D-вьюпорт в активную вкладку!
      const mount = $(tab === "vox3d" ? "vox3d-viewport-mount" : "anim-viewport-mount");
      const wrap = $("studio-3d-wrap");
      if (mount && wrap) mount.appendChild(wrap);
      initStudio3D();
      rebuildStudio3DMesh();
      if (tab === "vox3d") renderVox3DControls();
      if (tab === "anim") renderAnimControls();
    }
  }

  // ==========================================================================
  // ВКЛАДКА 1: МНОГОФАЙЛОВЫЙ РЕДАКТОР КОДА + МОБИЛЬНАЯ ПАНЕЛЬ СИМВОЛОВ
  // ==========================================================================
  function renderFileTree() {
    const proj = getProject();
    if (!proj) return;
    const tree = $("studio-file-tree");
    tree.innerHTML = "";
    const paths = Object.keys(proj.files);
    if (!paths.includes(activeFilePath)) activeFilePath = paths[0] || "scripts/main.js";

    paths.forEach((fp) => {
      const item = document.createElement("div");
      item.className = "st-file-item" + (fp === activeFilePath ? " active" : "");
      item.textContent = fp;
      item.onclick = () => {
        saveCurrentFileEditor();
        activeFilePath = fp;
        renderFileTree();
      };
      tree.appendChild(item);
    });

    $("studio-code-editor").value = proj.files[activeFilePath] || "";
    $("studio-cur-file").textContent = activeFilePath;
    $("studio-console").textContent = VH.modMgr.logs.join("\n") || "Готово к работе. Нажмите «Запустить в мире»!";
  }

  function saveCurrentFileEditor() {
    const proj = getProject();
    if (!proj || !activeFilePath) return;
    proj.files[activeFilePath] = $("studio-code-editor").value;
    if (activeFilePath === "manifest.json") {
      try {
        const m = JSON.parse(proj.files[activeFilePath]);
        if (m.name) proj.name = m.name;
      } catch {}
    }
  }

  // ==========================================================================
  // ВКЛАДКА 2: 2D ПИКСЕЛЬ-АРТ РЕДАКТОР (16x16, СИММЕТРИЯ, КАЛЬКА, КАДРЫ)
  // ==========================================================================
  function hexToRgba(hex) {
    const h = hex.replace("#", "");
    return [parseInt(h.slice(0,2),16)||0, parseInt(h.slice(2,4),16)||0, parseInt(h.slice(4,6),16)||0, 255];
  }

  function renderPixelCanvas() {
    const cv = $("px2d-canvas");
    if (!cv) return;
    const ctx = cv.getContext("2d");
    const cell = cv.width / 16;
    ctx.clearRect(0, 0, cv.width, cv.height);

    // Шахматный фон прозрачности
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      ctx.fillStyle = (x + y) % 2 === 0 ? "#2a2e38" : "#20242c";
      ctx.fillRect(x * cell, y * cell, cell, cell);
    }

    // Калька предыдущего кадра (Onion-Skin)
    if (pxOnion && pxCurFrame > 0 && pxFrames[pxCurFrame - 1]) {
      const prev = pxFrames[pxCurFrame - 1];
      for (let i = 0; i < 256; i++) {
        const c = prev[i];
        if (!c || c[3] === 0) continue;
        ctx.fillStyle = `rgba(${c[0]},${c[1]},${c[2]},0.28)`;
        ctx.fillRect((i % 16) * cell, Math.floor(i / 16) * cell, cell, cell);
      }
    }

    // Текущий кадр
    const frame = pxFrames[pxCurFrame] || (pxFrames[pxCurFrame] = new Array(256).fill(null));
    for (let i = 0; i < 256; i++) {
      const c = frame[i];
      if (!c || c[3] === 0) continue;
      ctx.fillStyle = `rgba(${c[0]},${c[1]},${c[2]},${(c[3] ?? 255) / 255})`;
      ctx.fillRect((i % 16) * cell, Math.floor(i / 16) * cell, cell, cell);
    }

    // Сетка
    ctx.strokeStyle = "rgba(255,255,255,0.12)";
    ctx.lineWidth = 1;
    for (let i = 0; i <= 16; i++) {
      ctx.beginPath(); ctx.moveTo(i * cell, 0); ctx.lineTo(i * cell, cv.height); ctx.stroke();
      ctx.beginPath(); ctx.moveTo(0, i * cell); ctx.lineTo(cv.width, i * cell); ctx.stroke();
    }
    if (pxMirrorX) {
      ctx.strokeStyle = "#2ed573";
      ctx.beginPath(); ctx.moveTo(cv.width / 2, 0); ctx.lineTo(cv.width / 2, cv.height); ctx.stroke();
    }
    $("px-frame-lbl").textContent = `Кадр ${pxCurFrame + 1}/${pxFrames.length}`;
  }

  function applyPixelAt(gx, gy) {
    if (gx < 0 || gx >= 16 || gy < 0 || gy >= 16) return;
    const frame = pxFrames[pxCurFrame];
    const idx = gy * 16 + gx;

    if (pxTool === "pick") {
      const c = frame[idx];
      if (c) {
        pxColor = "#" + [c[0], c[1], c[2]].map((v) => v.toString(16).padStart(2, "0")).join("");
        $("px-color-inp").value = pxColor;
      }
      return;
    }

    const paintOne = (x, y) => {
      const i = y * 16 + x;
      frame[i] = pxTool === "erase" ? null : hexToRgba(pxColor);
    };

    if (pxTool === "fill") {
      const target = JSON.stringify(frame[idx]);
      const rep = hexToRgba(pxColor);
      if (target === JSON.stringify(rep)) return;
      const stack = [[gx, gy]];
      const vis = new Set();
      while (stack.length) {
        const [cx, cy] = stack.pop();
        const k = cy * 16 + cx;
        if (cx < 0 || cx >= 16 || cy < 0 || cy >= 16 || vis.has(k)) continue;
        if (JSON.stringify(frame[k]) !== target) continue;
        vis.add(k);
        frame[k] = rep.slice();
        stack.push([cx + 1, cy], [cx - 1, cy], [cx, cy + 1], [cx, cy - 1]);
      }
    } else {
      paintOne(gx, gy);
      if (pxMirrorX) paintOne(15 - gx, gy);
    }
    renderPixelCanvas();
  }

  // ==========================================================================
  // ВКЛАДКА 3 И 4: 3D ВОКСЕЛЬНЫЙ МОДЕЛЛЕР + ТАЙМЛАЙН АНИМАЦИЙ
  // ==========================================================================
  function initStudio3D() {
    if (stRenderer) return;
    const cv = $("studio-3d-canvas");
    if (!cv) return;
    stRenderer = new THREE.WebGLRenderer({ canvas: cv, antialias: true });
    stRenderer.setSize(cv.width, cv.height);
    stScene = new THREE.Scene();
    stScene.background = new THREE.Color(0x161c28);

    stCamera = new THREE.PerspectiveCamera(50, cv.width / cv.height, 0.1, 40);
    stScene.add(new THREE.AmbientLight(0xffffff, 0.72));
    const dl = new THREE.DirectionalLight(0xffffff, 0.75);
    dl.position.set(4, 8, 5);
    stScene.add(dl);

    const grid = new THREE.GridHelper(3, 12, 0x2ed573, 0x334155);
    stScene.add(grid);

    // Вращение камеры пальцем / мышью в 3D-окне
    let dragging = false, lx = 0, ly = 0;
    const onDown = (x, y) => { dragging = true; lx = x; ly = y; };
    const onMove = (x, y) => {
      if (!dragging) return;
      stYaw -= (x - lx) * 0.015;
      stPitch = Math.max(-1.2, Math.min(1.3, stPitch + (y - ly) * 0.015));
      lx = x; ly = y;
      renderStudio3DOnce();
    };
    cv.addEventListener("mousedown", (e) => onDown(e.clientX, e.clientY));
    window.addEventListener("mousemove", (e) => onMove(e.clientX, e.clientY));
    window.addEventListener("mouseup", () => { dragging = false; });
    cv.addEventListener("touchstart", (e) => { e.preventDefault(); onDown(e.touches[0].clientX, e.touches[0].clientY); }, { passive: false });
    cv.addEventListener("touchmove", (e) => { e.preventDefault(); onMove(e.touches[0].clientX, e.touches[0].clientY); }, { passive: false });
    cv.addEventListener("touchend", () => { dragging = false; });
  }

  function rebuildStudio3DMesh() {
    if (!stScene) return;
    if (stModelGroup) stScene.remove(stModelGroup);
    stModelGroup = VH.buildCustomVhModel(stModelData);
    stScene.add(stModelGroup);
    if (activeStudioTab === "anim") {
      VH.applyModelAnimation(stModelGroup, stAnimData, stAnimTime);
    }
    renderStudio3DOnce();
  }

  function renderStudio3DOnce() {
    if (!stRenderer || !stScene || !stCamera) return;
    stCamera.position.set(
      Math.sin(stYaw) * Math.cos(stPitch) * stZoom,
      0.7 + Math.sin(stPitch) * stZoom,
      Math.cos(stYaw) * Math.cos(stPitch) * stZoom
    );
    stCamera.lookAt(0, 0.65, 0);
    stRenderer.render(stScene, stCamera);
  }

  function renderVox3DControls() {
    const bones = stModelData.bones || [];
    if (stSelBone >= bones.length) stSelBone = 0;
    const bone = bones[stSelBone];

    const bSel = $("v3d-bone-sel");
    bSel.innerHTML = "";
    bones.forEach((b, i) => {
      const o = document.createElement("option");
      o.value = i; o.textContent = `Кость: ${b.name}`;
      if (i === stSelBone) o.selected = true;
      bSel.appendChild(o);
    });

    const cubes = bone ? (bone.cubes || []) : [];
    if (stSelCube >= cubes.length) stSelCube = 0;
    const cSel = $("v3d-cube-sel");
    cSel.innerHTML = "";
    cubes.forEach((c, i) => {
      const o = document.createElement("option");
      o.value = i; o.textContent = `Куб #${i + 1} (${c.size.join("×")})`;
      if (i === stSelCube) o.selected = true;
      cSel.appendChild(o);
    });

    const c = cubes[stSelCube];
    if (c) {
      $("v3d-ox").value = c.origin[0]; $("v3d-oy").value = c.origin[1]; $("v3d-oz").value = c.origin[2];
      $("v3d-sx").value = c.size[0];   $("v3d-sy").value = c.size[1];   $("v3d-sz").value = c.size[2];
      $("v3d-rx").value = (c.rotation?.[0]) || 0; $("v3d-ry").value = (c.rotation?.[1]) || 0; $("v3d-rz").value = (c.rotation?.[2]) || 0;
      $("v3d-col").value = c.color || "#2ed573";
    }
  }

  function renderAnimControls() {
    const bones = stModelData.bones || [];
    const bSel = $("anim-bone-sel");
    bSel.innerHTML = "";
    bones.forEach((b, i) => {
      const o = document.createElement("option");
      o.value = b.name; o.textContent = `Кость: ${b.name}`;
      if (i === stSelBone) o.selected = true;
      bSel.appendChild(o);
    });
    $("anim-time-inp").value = stAnimTime;
    $("anim-time-lbl").textContent = stAnimTime.toFixed(2) + "с";
  }

  // ==========================================================================
  // ИНИЦИАЛИЗАЦИЯ ОБРАБОТЧИКОВ СТУДИИ
  // ==========================================================================
  function bindStudioEvents() {
    $("btn-open-mods").onclick = openStudioModal;
    $("btn-pause-mods").onclick = openStudioModal;
    $("btn-close-mods").onclick = () => {
      stAnimPlaying = false;
      clearInterval(pxTimer);
      saveCurrentFileEditor();
      VH.modMgr.saveProjects();
      $("mods-modal").classList.add("hidden");
      if (VH.Engine) VH.Engine.setPaused(false);
    };

    ["code", "px2d", "vox3d", "anim"].forEach((t) => {
      $("st-tab-" + t).onclick = () => switchStudioTab(t);
    });

    // Проект: выбор, создание, вкл/выкл, экспорт .vhmod, импорт .vhmod/.js
    $("studio-proj-select").onchange = (e) => {
      saveCurrentFileEditor();
      activeProjId = e.target.value;
      const proj = getProject();
      sync3DFromProject(proj);
      renderProjectBar();
      switchStudioTab(activeStudioTab);
    };

    $("btn-studio-toggle").onclick = () => {
      const proj = getProject(); if (!proj) return;
      proj.enabled = !proj.enabled;
      VH.modMgr.saveProjects();
      VH.modMgr.reloadAll();
      renderProjectBar();
    };

    $("btn-studio-new-proj").onclick = () => {
      const name = prompt("Название нового мода:", "Мой Супер Мод");
      if (!name) return;
      const id = "proj_" + Date.now();
      VH.modMgr.projects.push({
        id, name, enabled: true,
        files: {
          "manifest.json": JSON.stringify({ id, name, version: "1.0.0", entry: "scripts/main.js" }, null, 2),
          "scripts/main.js": `// Мод: ${name}\nVH.Mods.on("onBlockBreak", (e) => {\n  VH.Mods.toast("Сломан блок ID: " + e.id);\n});`,
          "textures/block.json": JSON.stringify({ size: 16, pixels: [] }),
          "models/mob.vhmodel.json": JSON.stringify(stModelData, null, 2),
          "animations/mob.vhanim.json": JSON.stringify(stAnimData, null, 2)
        }
      });
      activeProjId = id;
      VH.modMgr.saveProjects();
      renderProjectBar();
      switchStudioTab("code");
    };

    $("btn-studio-run").onclick = () => {
      saveCurrentFileEditor();
      save3DToProject();
      VH.modMgr.saveProjects();
      VH.modMgr.reloadAll();
      renderProjectBar();
      $("studio-console").textContent = VH.modMgr.logs.join("\n") || "Моды перезапущены!";
      if (VH.Engine?.showToast) VH.Engine.showToast("Мод сохранён и запущен!");
    };

    $("btn-studio-export").onclick = async () => {
      saveCurrentFileEditor();
      save3DToProject();
      const proj = getProject(); if (!proj) return;
      const msg = await VH.exportJsonFile(proj.name.replace(/[^a-zA-Zа-яА-Я0-9_-]/g, "_") + ".vhmod", proj);
      if (VH.Engine?.showToast) VH.Engine.showToast(msg);
    };

    $("inp-mod-file").onchange = (e) => {
      const f = e.target.files[0]; if (!f) return;
      const r = new FileReader();
      r.onload = () => {
        const raw = String(r.result || "");
        if (f.name.endsWith(".vhmod") || f.name.endsWith(".json")) {
          try {
            const parsed = JSON.parse(raw);
            if (parsed && parsed.files) {
              parsed.id = "proj_" + Date.now();
              parsed.enabled = true;
              VH.modMgr.projects.push(parsed);
              activeProjId = parsed.id;
            }
          } catch {}
        } else {
          const id = "proj_" + Date.now();
          VH.modMgr.projects.push({
            id, name: f.name.replace(/\.js$/i, ""), enabled: true,
            files: { "manifest.json": JSON.stringify({ id, name: f.name, version: "1.0" }), "scripts/main.js": raw }
          });
          activeProjId = id;
        }
        VH.modMgr.saveProjects();
        VH.modMgr.reloadAll();
        renderProjectBar();
        switchStudioTab("code");
      };
      r.readAsText(f);
    };

    // Добавить файл в проект
    $("btn-studio-add-file").onclick = () => {
      const proj = getProject(); if (!proj) return;
      const fp = prompt("Путь файла (напр. scripts/items.js):", "scripts/extra.js");
      if (!fp) return;
      proj.files[fp] = "// Новый файл мода\n";
      activeFilePath = fp;
      VH.modMgr.saveProjects();
      renderFileTree();
    };

    // Быстрые символы на мобильной клавиатуре
    document.querySelectorAll(".sym-key").forEach((btn) => {
      btn.onclick = () => {
        const ta = $("studio-code-editor");
        const ins = btn.getAttribute("data-ins") || btn.textContent;
        const start = ta.selectionStart || 0, end = ta.selectionEnd || 0;
        ta.value = ta.value.slice(0, start) + ins + ta.value.slice(end);
        ta.selectionStart = ta.selectionEnd = start + ins.length;
        ta.focus();
      };
    });

    // --- 2D PIXEL ART EVENTS ---
    const pxCv = $("px2d-canvas");
    let pxDrawing = false;
    const handlePxEvt = (clientX, clientY) => {
      const r = pxCv.getBoundingClientRect();
      const gx = Math.floor(((clientX - r.left) / r.width) * 16);
      const gy = Math.floor(((clientY - r.top) / r.height) * 16);
      applyPixelAt(gx, gy);
    };
    pxCv.addEventListener("mousedown", (e) => { pxDrawing = true; handlePxEvt(e.clientX, e.clientY); });
    window.addEventListener("mousemove", (e) => { if (pxDrawing) handlePxEvt(e.clientX, e.clientY); });
    window.addEventListener("mouseup", () => { pxDrawing = false; });
    pxCv.addEventListener("touchstart", (e) => { e.preventDefault(); pxDrawing = true; handlePxEvt(e.touches[0].clientX, e.touches[0].clientY); }, { passive: false });
    pxCv.addEventListener("touchmove", (e) => { e.preventDefault(); if (pxDrawing) handlePxEvt(e.touches[0].clientX, e.touches[0].clientY); }, { passive: false });
    pxCv.addEventListener("touchend", () => { pxDrawing = false; });

    $("px-color-inp").oninput = (e) => { pxColor = e.target.value; pxTool = "brush"; };
    ["brush", "erase", "fill", "pick"].forEach((tl) => {
      $("px-tool-" + tl).onclick = () => {
        pxTool = tl;
        ["brush", "erase", "fill", "pick"].forEach((x) => $("px-tool-" + x).classList.toggle("active", x === tl));
      };
    });
    $("px-mirror-btn").onclick = () => { pxMirrorX = !pxMirrorX; $("px-mirror-btn").classList.toggle("active", pxMirrorX); renderPixelCanvas(); };
    $("px-onion-btn").onclick  = () => { pxOnion = !pxOnion; $("px-onion-btn").classList.toggle("active", pxOnion); renderPixelCanvas(); };
    $("px-add-frame").onclick  = () => { pxFrames.push(pxFrames[pxCurFrame].slice()); pxCurFrame = pxFrames.length - 1; renderPixelCanvas(); };
    $("px-next-frame").onclick = () => { pxCurFrame = (pxCurFrame + 1) % pxFrames.length; renderPixelCanvas(); };
    $("px-play-anim").onclick  = () => {
      pxPlaying = !pxPlaying;
      $("px-play-anim").textContent = pxPlaying ? "⏹ Стоп" : "▶ Аним.";
      clearInterval(pxTimer);
      if (pxPlaying) pxTimer = setInterval(() => { pxCurFrame = (pxCurFrame + 1) % pxFrames.length; renderPixelCanvas(); }, 180);
    };

    // Привязать нарисованную 2D текстуру к новому Блоку или Предмету в моде!
    $("px-bind-block").onclick = () => {
      const id = parseInt($("px-target-id").value, 10) || 55;
      const proj = getProject();
      const pixels = pxFrames[pxCurFrame].slice();
      if (proj) {
        proj.files[`textures/block_${id}.json`] = JSON.stringify({ size: 16, pixels });
        proj.files["scripts/main.js"] += `\nVH.Mods.registerBlock(${id}, { name: "Арт-Блок #${id}", pixels: ${JSON.stringify(pixels)} });\nVH.Mods.give(${id}, 8);`;
        VH.modMgr.saveProjects();
        VH.modMgr.reloadAll();
      }
      if (VH.Engine?.showToast) VH.Engine.showToast(`Блок #${id} создан из вашего пиксель-арта!`);
    };

    $("px-bind-item").onclick = () => {
      const id = Math.max(131, parseInt($("px-target-id").value, 10) || 135);
      const proj = getProject();
      const pixels = pxFrames[pxCurFrame].slice();
      if (proj) {
        proj.files[`textures/item_${id}.json`] = JSON.stringify({ size: 16, pixels });
        proj.files["scripts/main.js"] += `\nVH.Mods.registerItem(${id}, { name: "Арт-Предмет #${id}", pixels: ${JSON.stringify(pixels)}, dmg: 9 });\nVH.Mods.give(${id}, 1);`;
        VH.modMgr.saveProjects();
        VH.modMgr.reloadAll();
      }
      if (VH.Engine?.showToast) VH.Engine.showToast(`Предмет #${id} создан из вашего пиксель-арта!`);
    };

    // --- 3D VOXEL MODELER EVENTS ---
    $("v3d-bone-sel").onchange = (e) => { stSelBone = +e.target.value; stSelCube = 0; renderVox3DControls(); };
    $("v3d-cube-sel").onchange = (e) => { stSelCube = +e.target.value; renderVox3DControls(); };
    $("v3d-add-bone").onclick = () => {
      const bname = "bone_" + (stModelData.bones.length + 1);
      stModelData.bones.push({ name: bname, parent: "root", pivot: [0, 8, 0], cubes: [{ origin: [-2, 8, -2], size: [4, 4, 4], color: "#4ceeff", rotation: [0, 0, 0] }] });
      stSelBone = stModelData.bones.length - 1; stSelCube = 0;
      rebuildStudio3DMesh(); renderVox3DControls();
    };
    $("v3d-add-cube").onclick = () => {
      const b = stModelData.bones[stSelBone]; if (!b) return;
      b.cubes.push({ origin: [-2, 4, -2], size: [4, 4, 4], color: "#ff6b81", rotation: [0, 0, 0] });
      stSelCube = b.cubes.length - 1;
      rebuildStudio3DMesh(); renderVox3DControls();
    };
    $("v3d-del-cube").onclick = () => {
      const b = stModelData.bones[stSelBone]; if (!b || b.cubes.length <= 1) return;
      b.cubes.splice(stSelCube, 1); stSelCube = 0;
      rebuildStudio3DMesh(); renderVox3DControls();
    };

    const syncCubeInputs = () => {
      const c = stModelData.bones[stSelBone]?.cubes[stSelCube];
      if (!c) return;
      c.origin = [+$("v3d-ox").value || 0, +$("v3d-oy").value || 0, +$("v3d-oz").value || 0];
      c.size   = [Math.max(1, +$("v3d-sx").value || 1), Math.max(1, +$("v3d-sy").value || 1), Math.max(1, +$("v3d-sz").value || 1)];
      c.rotation = [+$("v3d-rx").value || 0, +$("v3d-ry").value || 0, +$("v3d-rz").value || 0];
      c.color  = $("v3d-col").value;
      rebuildStudio3DMesh();
    };
    ["v3d-ox", "v3d-oy", "v3d-oz", "v3d-sx", "v3d-sy", "v3d-sz", "v3d-rx", "v3d-ry", "v3d-rz", "v3d-col"].forEach((id) => {
      $(id).oninput = syncCubeInputs;
    });

    $("v3d-spawn-mob").onclick = () => {
      save3DToProject();
      const proj = getProject();
      const mType = "mod_mob_" + Date.now().toString().slice(-4);
      if (proj) {
        proj.files["scripts/main.js"] += `\nVH.Mods.registerMob("${mType}", { name: "3D Моб Студии", hp: 30, dmg: 4, hostile: false, modelJson: ${JSON.stringify(stModelData)}, animJson: ${JSON.stringify(stAnimData)} });\nVH.Mods.spawnMob("${mType}");`;
        VH.modMgr.saveProjects();
        VH.modMgr.reloadAll();
      }
      if (VH.Engine?.showToast) VH.Engine.showToast("3D-модель зарегистрирована и заспавнена в мире!");
    };

    // --- ANIMATION TIMELINE EVENTS ---
    $("anim-time-inp").oninput = (e) => {
      stAnimTime = +e.target.value;
      $("anim-time-lbl").textContent = stAnimTime.toFixed(2) + "с";
      if (stModelGroup) { VH.applyModelAnimation(stModelGroup, stAnimData, stAnimTime); renderStudio3DOnce(); }
    };
    $("anim-add-key").onclick = () => {
      const bname = $("anim-bone-sel").value || "root";
      const rot = [+$("anim-rx").value || 0, +$("anim-ry").value || 0, +$("anim-rz").value || 0];
      if (!stAnimData.tracks[bname]) stAnimData.tracks[bname] = [];
      stAnimData.tracks[bname] = stAnimData.tracks[bname].filter((k) => Math.abs(k.time - stAnimTime) > 0.04);
      stAnimData.tracks[bname].push({ time: stAnimTime, rot });
      save3DToProject();
      if (stModelGroup) { VH.applyModelAnimation(stModelGroup, stAnimData, stAnimTime); renderStudio3DOnce(); }
      if (VH.Engine?.showToast) VH.Engine.showToast(`Ключ [${bname} @ ${stAnimTime.toFixed(2)}с] добавлен!`);
    };
    $("anim-preset-idle").onclick = () => {
      const bname = $("anim-bone-sel").value || "root";
      stAnimData.tracks[bname] = [{ time: 0, rot: [0, 0, 0] }, { time: 0.5, rot: [8, 0, 4] }, { time: 1.0, rot: [0, 0, 0] }];
      save3DToProject();
    };
    $("anim-preset-spin").onclick = () => {
      const bname = $("anim-bone-sel").value || "root";
      stAnimData.tracks[bname] = [{ time: 0, rot: [0, 0, 0] }, { time: 0.5, rot: [0, 180, 0] }, { time: 1.0, rot: [0, 360, 0] }];
      save3DToProject();
    };
    $("anim-play-btn").onclick = () => {
      stAnimPlaying = !stAnimPlaying;
      $("anim-play-btn").textContent = stAnimPlaying ? "⏹ Стоп" : "▶ Воспроизвести";
      let last = performance.now();
      const tickAnim = (ts) => {
        if (!stAnimPlaying) return;
        const dt = (ts - last) / 1000; last = ts;
        stAnimTime = (stAnimTime + dt) % (stAnimData.duration || 1.0);
        $("anim-time-inp").value = stAnimTime.toFixed(2);
        $("anim-time-lbl").textContent = stAnimTime.toFixed(2) + "с";
        if (stModelGroup) { VH.applyModelAnimation(stModelGroup, stAnimData, stAnimTime); renderStudio3DOnce(); }
        stAnimRaf = requestAnimationFrame(tickAnim);
      };
      if (stAnimPlaying) stAnimRaf = requestAnimationFrame(tickAnim);
      else if (stAnimRaf) cancelAnimationFrame(stAnimRaf);
    };
  }

  VH.bindStudioEvents = bindStudioEvents;
})();
