// ============================================================================
// VOXELHORIZON — РЕЕСТР БЛОКОВ/ПРЕДМЕТОВ И ПРОЦЕДУРНЫЙ АТЛАС ТЕКСТУР (100% ОРИГИНАЛ)
// ============================================================================
window.VH = window.VH || {};

// Блоки: 1..28 | Предметы (не ставятся как блоки): 101..115
const ITEMS_DB = {
  0:   { name: "Воздух",          solid: false, transparent: true },
  1:   { name: "Дёрн",            solid: true,  hard: 0.6, drop: 2,   tool: "shovel", faces: [0, 1, 2] }, // top, side, bottom
  2:   { name: "Земля",           solid: true,  hard: 0.5, drop: 2,   tool: "shovel", faces: [2, 2, 2] },
  3:   { name: "Камень",          solid: true,  hard: 1.8, drop: 4,   tool: "pick",   faces: [3, 3, 3] },
  4:   { name: "Булыжник",        solid: true,  hard: 2.0, drop: 4,   tool: "pick",   faces: [4, 4, 4] },
  5:   { name: "Дубовое бревно",  solid: true,  hard: 1.5, drop: 5,   tool: "axe",    faces: [5, 6, 5] },
  6:   { name: "Листва",          solid: true,  hard: 0.3, drop: 0,   transparent: true, faces: [7, 7, 7] },
  7:   { name: "Доски",           solid: true,  hard: 1.2, drop: 7,   tool: "axe",    faces: [8, 8, 8] },
  8:   { name: "Песок",           solid: true,  hard: 0.5, drop: 8,   tool: "shovel", faces: [9, 9, 9] },
  9:   { name: "Вода",            solid: false, liquid: true, transparent: true, faces: [10, 10, 10] },
  10:  { name: "Коренная порода", solid: true,  hard: 999, drop: 0,   faces: [11, 11, 11] },
  11:  { name: "Угольная руда",   solid: true,  hard: 2.2, drop: 101, tool: "pick",   faces: [12, 12, 12] },
  12:  { name: "Железная руда",   solid: true,  hard: 2.6, drop: 12,  tool: "pick",   faces: [13, 13, 13] },
  13:  { name: "Золотая руда",    solid: true,  hard: 2.8, drop: 13,  tool: "pick",   faces: [14, 14, 14] },
  14:  { name: "Алмазная руда",   solid: true,  hard: 3.4, drop: 104, tool: "pick",   faces: [15, 15, 15] },
  15:  { name: "Стекло",          solid: true,  hard: 0.4, drop: 15,  transparent: true, faces: [16, 16, 16] },
  16:  { name: "Кирпичи",         solid: true,  hard: 2.0, drop: 16,  tool: "pick",   faces: [17, 17, 17] },
  17:  { name: "Верстак",         solid: true,  hard: 1.4, drop: 17,  tool: "axe",    interactive: "craft", faces: [18, 19, 8] },
  18:  { name: "Печь",            solid: true,  hard: 2.4, drop: 18,  tool: "pick",   interactive: "smelt", faces: [20, 21, 20] },
  19:  { name: "Снег",            solid: true,  hard: 0.4, drop: 19,  tool: "shovel", faces: [22, 23, 2] },
  20:  { name: "Лёд",             solid: true,  hard: 0.6, drop: 20,  transparent: true, faces: [24, 24, 24] },
  21:  { name: "Кактус",          solid: true,  hard: 0.5, drop: 21,  faces: [25, 26, 25] },
  22:  { name: "Обсидиан",        solid: true,  hard: 6.0, drop: 22,  tool: "pick",   faces: [27, 27, 27] },
  23:  { name: "Лампа Кристалла", solid: true,  hard: 0.6, drop: 23,  light: true,    faces: [28, 28, 28] },
  24:  { name: "Книжная полка",   solid: true,  hard: 1.2, drop: 24,  tool: "axe",    faces: [8, 29, 8] },
  25:  { name: "Гравий",          solid: true,  hard: 0.6, drop: 25,  tool: "shovel", faces: [30, 30, 30] },
  26:  { name: "Динамит (TNT)",   solid: true,  hard: 0.4, drop: 26,  interactive: "tnt", faces: [31, 32, 31] },

  // Предметы (не блоки)
  101: { name: "Уголь",           item: true, color: "#2b2d34", shape: "gem" },
  102: { name: "Железный слиток", item: true, color: "#d8dee8", shape: "ingot" },
  103: { name: "Золотой слиток",  item: true, color: "#f6c945", shape: "ingot" },
  104: { name: "Алмаз",           item: true, color: "#4ce8ff", shape: "gem" },
  105: { name: "Палка",           item: true, color: "#9c6d3e", shape: "stick" },
  106: { name: "Деревянная кирка",item: true, color: "#b8864b", shape: "pick",   toolType: "pick", power: 2.2, dmg: 3 },
  107: { name: "Каменная кирка",  item: true, color: "#9299a4", shape: "pick",   toolType: "pick", power: 3.5, dmg: 4 },
  108: { name: "Железная кирка",  item: true, color: "#dce4f0", shape: "pick",   toolType: "pick", power: 5.0, dmg: 5 },
  109: { name: "Алмазная кирка",  item: true, color: "#4ce8ff", shape: "pick",   toolType: "pick", power: 7.5, dmg: 6 },
  110: { name: "Каменный топор",  item: true, color: "#9299a4", shape: "axe",    toolType: "axe",  power: 3.8, dmg: 6 },
  111: { name: "Железный меч",    item: true, color: "#dce4f0", shape: "sword",  toolType: "sword",power: 1.5, dmg: 8 },
  112: { name: "Алмазный меч",    item: true, color: "#4ce8ff", shape: "sword",  toolType: "sword",power: 1.8, dmg: 12 },
  113: { name: "Яблоко",          item: true, color: "#e84148", shape: "food",   healFood: 5, healHp: 2 },
  114: { name: "Сырое мясо",      item: true, color: "#d66862", shape: "food",   healFood: 3, healHp: 1 },
  115: { name: "Жареный стейк",   item: true, color: "#8c4a2b", shape: "food",   healFood: 9, healHp: 5 }
};

const ATLAS_COLS = 8;
const TILE_PX = 16;

function buildTextureAtlas() {
  const canvas = document.createElement("canvas");
  canvas.width = ATLAS_COLS * TILE_PX;
  canvas.height = ATLAS_COLS * TILE_PX;
  const ctx = canvas.getContext("2d");

  function drawTile(idx, fn) {
    const tx = (idx % ATLAS_COLS) * TILE_PX;
    const ty = Math.floor(idx / ATLAS_COLS) * TILE_PX;
    const img = ctx.createImageData(TILE_PX, TILE_PX);
    const d = img.data;
    const setPx = (x, y, r, g, b, a = 255) => {
      if (x < 0 || x >= 16 || y < 0 || y >= 16) return;
      const o = (y * 16 + x) * 4;
      d[o] = Math.max(0, Math.min(255, r));
      d[o+1] = Math.max(0, Math.min(255, g));
      d[o+2] = Math.max(0, Math.min(255, b));
      d[o+3] = a;
    };
    const noise = (x, y, s) => (((x * 37 + y * 17 + s * 29) ^ (x * 13)) % 11) - 5;
    fn(setPx, noise);
    ctx.putImageData(img, tx, ty);
  }

  // 0: Grass Top
  drawTile(0, (p, n) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=n(x,y,1)*4; p(x,y, 76+k, 166+k, 68+k); } });
  // 1: Grass Side
  drawTile(1, (p, n) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=n(x,y,2)*4; if (y < 4 + ((x*7)%3)) p(x,y, 74+k, 162+k, 66+k); else p(x,y, 126+k, 88+k, 58+k); } });
  // 2: Dirt
  drawTile(2, (p, n) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=n(x,y,3)*5; p(x,y, 128+k, 90+k, 60+k); } });
  // 3: Stone
  drawTile(3, (p, n) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=n(x,y,4)*4; p(x,y, 124+k, 128+k, 134+k); } });
  // 4: Cobblestone
  drawTile(4, (p, n) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const seam=(y%4===0)||((x+(y>>2)*3)%6===0); const k=n(x,y,5)*4; const v=seam?74:116+k; p(x,y, v, v+2, v+5); } });
  // 5: Log Top (rings)
  drawTile(5, (p, n) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const d=Math.hypot(x-7.5,y-7.5); if (d>6.8) p(x,y,88,62,40); else { const ring=Math.floor(d)%2===0?14:0; p(x,y,168-ring,132-ring,84-ring); } } });
  // 6: Log Side (bark)
  drawTile(6, (p, n) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=(x%3===0?-14:n(x,y,6)*3); p(x,y, 98+k, 70+k, 44+k); } });
  // 7: Leaves
  drawTile(7, (p, n) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=n(x,y,7)*5; if ((x*11+y*7)%9===0) p(x,y, 32,74,34, 180); else p(x,y, 48+k, 132+k, 52+k, 245); } });
  // 8: Planks
  drawTile(8, (p, n) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const seam=(y%4===0)||((x===0&&y<8)||(x===8&&y>=8)); const k=n(x,y,8)*3; if (seam) p(x,y,118,86,52); else p(x,y,176+k,138+k,88+k); } });
  // 9: Sand
  drawTile(9, (p, n) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=n(x,y,9)*3; p(x,y, 224+k, 210+k, 152+k); } });
  // 10: Water
  drawTile(10, (p, n) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const w=Math.sin((x+y)*0.6)*10; p(x,y, 36, 128+w, 218+w, 185); } });
  // 11: Bedrock
  drawTile(11, (p, n) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const v=38+n(x,y,11)*6; p(x,y, v, v, v+4); } });

  // 12..15: Ores (Coal, Iron, Gold, Diamond)
  const oreCols = [[32,34,40], [214,186,158], [248,204,54], [68,236,248]];
  oreCols.forEach((oc, idx) => {
    drawTile(12 + idx, (p, n) => {
      for (let y=0;y<16;y++) for (let x=0;x<16;x++) {
        const k=n(x,y,12+idx)*4;
        const vein = ((x-3)*(x-3)+(y-4)*(y-4)<3) || ((x-11)*(x-11)+(y-5)*(y-5)<3) || ((x-6)*(x-6)+(y-11)*(y-11)<3) || ((x-12)*(x-12)+(y-12)*(y-12)<2.5);
        if (vein) p(x,y, oc[0], oc[1], oc[2]);
        else p(x,y, 122+k, 126+k, 132+k);
      }
    });
  });

  // 16: Glass
  drawTile(16, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const b=(x===0||x===15||y===0||y===15); const glint=(x+y===6||x+y===7)&&x>2&&x<6; p(x,y, b?190:210, b?235:245, 255, b?215:(glint?180:65)); } });
  // 17: Bricks
  drawTile(17, (p, n) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const mortar=(y%4===0)||((x+(y>>2)*4)%8===0); const k=n(x,y,17)*4; if (mortar) p(x,y,182,178,170); else p(x,y,162+k,74+k,58+k); } });
  // 18: Workbench Top (3x3 grid!)
  drawTile(18, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const b=(x===0||x===15||y===0||y===15); const grid=(x===5||x===10||y===5||y===10); p(x,y, b?94:(grid?72:168), b?66:(grid?48:124), b?38:(grid?28:74)); } });
  // 19: Workbench Side
  drawTile(19, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const b=(x===0||x===15||y===0||y===15||y===5); p(x,y, b?92:156, b?64:118, b?36:72); if (x>=4&&x<=6&&y>=8&&y<=13) p(x,y,180,188,198); } });
  // 20: Furnace Top
  drawTile(20, (p, n) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const b=(x===0||x===15||y===0||y===15); const v=b?68:106+n(x,y,20)*3; p(x,y, v, v, v+4); } });
  // 21: Furnace Front (glowing hearth!)
  drawTile(21, (p, n) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { if (x>=4&&x<=11&&y>=8&&y<=13) p(x,y, 244, 138+(y*12)%60, 32); else if (x>=4&&x<=11&&y>=3&&y<=5) p(x,y, 34,36,42); else { const v=104+n(x,y,21)*3; p(x,y,v,v,v+4); } } });
  // 22: Snow Top
  drawTile(22, (p, n) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const v=238+n(x,y,22)*2; p(x,y, v, v+4, 255); } });
  // 23: Snow Side
  drawTile(23, (p, n) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=n(x,y,23)*3; if (y<5) p(x,y,238,244,255); else p(x,y,126+k,88+k,58+k); } });
  // 24: Ice
  drawTile(24, (p, n) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=n(x,y,24)*3; p(x,y, 136+k, 204+k, 248, 200); } });
  // 25: Cactus Top
  drawTile(25, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) p(x,y, 48, 128, 52); });
  // 26: Cactus Side
  drawTile(26, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const rib=(x%4===0); p(x,y, rib?34:54, rib?104:146, rib?38:56); } });
  // 27: Obsidian
  drawTile(27, (p, n) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=n(x,y,27)*4; p(x,y, 34+k, 22+k, 52+k); } });
  // 28: Crystal Lamp
  drawTile(28, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const b=(x===0||x===15||y===0||y===15); p(x,y, b?58:215, b?82:252, b?94:235); } });
  // 29: Bookshelf
  drawTile(29, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { if (y<2||y>13||y===7||y===8) p(x,y,164,124,78); else { const bc=[[186,48,54],[52,126,196],[64,168,88],[212,172,54]][(x>>1)%4]; p(x,y,bc[0],bc[1],bc[2]); } } });
  // 30: Gravel
  drawTile(30, (p, n) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const v=128+n(x,y,30)*8; p(x,y,v,v-2,v-4); } });
  // 31: TNT Top
  drawTile(31, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) p(x,y, 178, 48, 42); });
  // 32: TNT Side
  drawTile(32, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { if (y>=5&&y<=10) p(x,y, 236, 234, 224); else p(x,y, 198, 46, 42); } });

  const tex = new THREE.CanvasTexture(canvas);
  tex.magFilter = THREE.NearestFilter;
  tex.minFilter = THREE.NearestFilter;
  tex.flipY = false;
  return { canvas, texture: tex };
}

function drawItemIcon(canvas, id) {
  const ctx = canvas.getContext("2d");
  canvas.width = 16; canvas.height = 16;
  ctx.clearRect(0, 0, 16, 16);
  const info = ITEMS_DB[id];
  if (!info || !VH.atlasCanvas) return;

  if (!info.item && info.faces) {
    const f = info.faces[1]; // Side texture
    const sx = (f % ATLAS_COLS) * TILE_PX;
    const sy = Math.floor(f / ATLAS_COLS) * TILE_PX;
    ctx.drawImage(VH.atlasCanvas, sx, sy, 16, 16, 1, 1, 14, 14);
    ctx.strokeStyle = "rgba(0,0,0,0.45)";
    ctx.strokeRect(1, 1, 14, 14);
    return;
  }

  ctx.fillStyle = info.color || "#ffffff";
  if (info.shape === "pick") {
    ctx.fillRect(3, 2, 10, 3);
    ctx.fillRect(2, 4, 2, 2); ctx.fillRect(12, 4, 2, 2);
    ctx.fillStyle = "#8a5d32";
    ctx.fillRect(7, 5, 2, 10);
  } else if (info.shape === "axe") {
    ctx.fillRect(4, 2, 7, 5); ctx.fillRect(3, 4, 3, 4);
    ctx.fillStyle = "#8a5d32";
    ctx.fillRect(7, 5, 2, 10);
  } else if (info.shape === "sword") {
    for (let i = 0; i < 9; i++) ctx.fillRect(12 - i, 2 + i, 2, 2);
    ctx.fillStyle = "#8a5d32";
    ctx.fillRect(2, 11, 4, 3);
    ctx.fillStyle = "#e8c454";
    ctx.fillRect(4, 9, 4, 2);
  } else if (info.shape === "stick") {
    for (let i = 0; i < 10; i++) ctx.fillRect(3 + i, 12 - i, 2, 2);
  } else if (info.shape === "ingot") {
    ctx.fillRect(3, 6, 10, 5);
    ctx.fillStyle = "rgba(255,255,255,0.45)";
    ctx.fillRect(4, 6, 8, 2);
  } else if (info.shape === "gem") {
    ctx.fillRect(5, 3, 6, 10); ctx.fillRect(3, 5, 10, 6);
    ctx.fillStyle = "rgba(255,255,255,0.4)";
    ctx.fillRect(5, 4, 3, 3);
  } else if (info.shape === "food") {
    ctx.fillRect(4, 4, 8, 8); ctx.fillRect(3, 6, 10, 4);
  }
}

VH.ITEMS_DB = ITEMS_DB;
VH.ATLAS_COLS = ATLAS_COLS;
VH.buildTextureAtlas = buildTextureAtlas;
VH.drawItemIcon = drawItemIcon;
