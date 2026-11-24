// ============================================================================
// VOXELHORIZON v3.0 — 16x16 ПИКСЕЛЬНЫЙ АТЛАС (96 ТАЙЛОВ), БРОНЯ, ЛУК, ФАКЕЛЫ, КРОВАТЬ
// ============================================================================
window.VH = window.VH || {};

const ITEMS_DB = {
  0:   { name: "Воздух",             solid: false, transparent: true },
  1:   { name: "Дёрн",               solid: true,  hard: 0.6, drop: 2,   tool: "shovel", faces: [0, 1, 2] },
  2:   { name: "Земля",              solid: true,  hard: 0.5, drop: 2,   tool: "shovel", faces: [2, 2, 2] },
  3:   { name: "Камень",             solid: true,  hard: 1.8, drop: 4,   tool: "pick",   faces: [3, 3, 3] },
  4:   { name: "Булыжник",           solid: true,  hard: 2.0, drop: 4,   tool: "pick",   faces: [4, 4, 4] },
  5:   { name: "Дуб",                solid: true,  hard: 1.5, drop: 5,   tool: "axe",    faces: [5, 6, 5] },
  6:   { name: "Листва дуба",        solid: true,  hard: 0.25,drop: 113, transparent: true, faces: [7, 7, 7] },
  7:   { name: "Дубовые доски",      solid: true,  hard: 1.2, drop: 7,   tool: "axe",    faces: [8, 8, 8] },
  8:   { name: "Песок",              solid: true,  hard: 0.5, drop: 8,   tool: "shovel", faces: [9, 9, 9] },
  9:   { name: "Вода",               solid: false, liquid: "water", transparent: true, faces: [10, 10, 10] },
  10:  { name: "Бедрок",             solid: true,  hard: 999, drop: 0,   faces: [11, 11, 11] },
  11:  { name: "Угольная руда",      solid: true,  hard: 2.2, drop: 101, tool: "pick",   faces: [12, 12, 12] },
  12:  { name: "Железная руда",      solid: true,  hard: 2.6, drop: 12,  tool: "pick",   faces: [13, 13, 13] },
  13:  { name: "Золотая руда",       solid: true,  hard: 2.8, drop: 13,  tool: "pick",   faces: [14, 14, 14] },
  14:  { name: "Алмазная руда",      solid: true,  hard: 3.4, drop: 104, tool: "pick",   faces: [15, 15, 15] },
  15:  { name: "Стекло",             solid: true,  hard: 0.35,drop: 15,  transparent: true, faces: [16, 16, 16] },
  16:  { name: "Кирпичи",            solid: true,  hard: 2.0, drop: 16,  tool: "pick",   faces: [17, 17, 17] },
  17:  { name: "Верстак",            solid: true,  hard: 1.4, drop: 17,  tool: "axe",    interactive: "craft", faces: [18, 19, 8] },
  18:  { name: "Печь",               solid: true,  hard: 2.4, drop: 18,  tool: "pick",   interactive: "smelt", faces: [20, 21, 20] },
  19:  { name: "Снег",               solid: true,  hard: 0.4, drop: 19,  tool: "shovel", faces: [22, 23, 2] },
  20:  { name: "Лёд",                solid: true,  hard: 0.6, drop: 20,  transparent: true, faces: [24, 24, 24] },
  21:  { name: "Кактус",             solid: true,  hard: 0.5, drop: 21,  faces: [25, 26, 25] },
  22:  { name: "Обсидиан",           solid: true,  hard: 5.5, drop: 22,  tool: "pick",   faces: [27, 27, 27] },
  23:  { name: "Светокамень",        solid: true,  hard: 0.5, drop: 23,  light: 15,      faces: [28, 28, 28] },
  24:  { name: "Книжная полка",      solid: true,  hard: 1.2, drop: 24,  tool: "axe",    faces: [8, 29, 8] },
  25:  { name: "Гравий",             solid: true,  hard: 0.6, drop: 25,  tool: "shovel", faces: [30, 30, 30] },
  26:  { name: "Динамит (TNT)",      solid: true,  hard: 0.4, drop: 26,  interactive: "tnt", faces: [31, 32, 31] },
  27:  { name: "Берёза",             solid: true,  hard: 1.5, drop: 27,  tool: "axe",    faces: [5, 33, 5] },
  28:  { name: "Песчаник",           solid: true,  hard: 1.4, drop: 28,  tool: "pick",   faces: [34, 34, 34] },
  29:  { name: "Лава",               solid: false, liquid: "lava", transparent: true, light: 15, faces: [35, 35, 35] },
  30:  { name: "Адский камень",      solid: true,  hard: 0.7, drop: 30,  tool: "pick",   faces: [36, 36, 36] },
  31:  { name: "Песок душ",          solid: true,  hard: 0.6, drop: 31,  tool: "shovel", faces: [37, 37, 37] },
  32:  { name: "Адский кирпич",      solid: true,  hard: 2.2, drop: 32,  tool: "pick",   faces: [38, 38, 38] },
  33:  { name: "Кварцевая руда",     solid: true,  hard: 2.0, drop: 117, tool: "pick",   faces: [39, 39, 39] },
  34:  { name: "Портал в Ад",        solid: false, portal: true, transparent: true, light: 12, faces: [40, 40, 40] },
  35:  { name: "Сундук (27 слотов)", solid: true,  hard: 1.4, drop: 35,  tool: "axe",    interactive: "chest", faces: [41, 42, 41] },
  36:  { name: "Изумрудная руда",    solid: true,  hard: 3.2, drop: 118, tool: "pick",   faces: [43, 43, 43] },
  37:  { name: "Факел",              solid: false, torch: true, transparent: true, hard: 0.1, drop: 37, light: 14, faces: [44, 44, 44] },
  38:  { name: "Кровать",            solid: true,  hard: 0.6, drop: 38,  interactive: "bed", faces: [45, 46, 8] },
  39:  { name: "Пшеница",            solid: false, crop: true, transparent: true, hard: 0.1, drop: 122, faces: [47, 47, 47] },

  // Предметы (не ставятся как блоки)
  101: { name: "Уголь",              item: true, color: "#2b2d34", shape: "gem" },
  102: { name: "Железный слиток",    item: true, color: "#dfe6f0", shape: "ingot" },
  103: { name: "Золотой слиток",     item: true, color: "#ffd23f", shape: "ingot" },
  104: { name: "Алмаз",              item: true, color: "#4ceeff", shape: "gem" },
  105: { name: "Палка",              item: true, color: "#9c6d3e", shape: "stick" },
  106: { name: "Деревянная кирка",   item: true, color: "#b8864b", shape: "pick",   toolType: "pick",  power: 2.2, dmg: 3 },
  107: { name: "Каменная кирка",     item: true, color: "#9299a4", shape: "pick",   toolType: "pick",  power: 3.6, dmg: 4 },
  108: { name: "Железная кирка",     item: true, color: "#e4ecf5", shape: "pick",   toolType: "pick",  power: 5.2, dmg: 5 },
  109: { name: "Алмазная кирка",     item: true, color: "#4ceeff", shape: "pick",   toolType: "pick",  power: 8.0, dmg: 6 },
  110: { name: "Каменный топор",     item: true, color: "#9299a4", shape: "axe",    toolType: "axe",   power: 4.0, dmg: 6 },
  111: { name: "Железный меч",       item: true, color: "#e4ecf5", shape: "sword",  toolType: "sword", power: 1.6, dmg: 8 },
  112: { name: "Алмазный меч",       item: true, color: "#4ceeff", shape: "sword",  toolType: "sword", power: 2.0, dmg: 12 },
  113: { name: "Яблоко",             item: true, color: "#e8383f", shape: "food",   healFood: 5, healHp: 2 },
  114: { name: "Сырая говядина",     item: true, color: "#d65d58", shape: "food",   healFood: 3, healHp: 1 },
  115: { name: "Жареный стейк",      item: true, color: "#8c4524", shape: "food",   healFood: 9, healHp: 5 },
  116: { name: "Огниво (Портал)",    item: true, color: "#ff9922", shape: "flint",  ignitePortal: true },
  117: { name: "Адский кварц",       item: true, color: "#f4efe8", shape: "gem" },
  118: { name: "Изумруд",            item: true, color: "#34e86c", shape: "gem" },
  119: { name: "Ведро воды",         item: true, color: "#3898ff", shape: "bucket", placeLiquid: 9 },
  120: { name: "Ведро лавы",         item: true, color: "#ff6618", shape: "bucket", placeLiquid: 29 },
  121: { name: "Семена пшеницы",     item: true, color: "#68c844", shape: "seed",   plantCrop: 39 },
  122: { name: "Хлеб",               item: true, color: "#d89c44", shape: "food",   healFood: 6, healHp: 3 },
  123: { name: "Лук",                item: true, color: "#9c6d3e", shape: "bow",    rangedBow: true, dmg: 9 },
  124: { name: "Стрела",             item: true, color: "#dcdcdc", shape: "stick" },
  125: { name: "Кожаный шлем",       item: true, color: "#9e5c34", shape: "helmet", armorSlot: "head",  armorDef: 1 },
  126: { name: "Железный нагрудник", item: true, color: "#dce4f0", shape: "chest",  armorSlot: "chest", armorDef: 3 },
  127: { name: "Железные поножи",    item: true, color: "#c8d2e0", shape: "legs",   armorSlot: "legs",  armorDef: 2 },
  128: { name: "Алмазные ботинки",   item: true, color: "#4ceeff", shape: "boots",  armorSlot: "feet",  armorDef: 2 },
  129: { name: "Звезда Преисподней", item: true, color: "#fff275", shape: "gem",    healFood: 20, healHp: 20 }
};

const ATLAS_COLS = 8;
const ATLAS_ROWS = 12;
const TILE_PX = 64; // HD 64x64 на блок для чётких аниме-текстур!

const SKINS_DB = [
  { id: "fox_officer",   name: "Офицер Кицунэ",   skin: "#f3c6a5", hair: "#c2410c", eye: "#f59e0b", shirt: "#1e293b", trim: "#fbbf24", pants: "#0f172a", addon: "fox" },
  { id: "estelle_cmd",   name: "Командир Эстель", skin: "#fde2d2", hair: "#2dd4bf", eye: "#e11d48", shirt: "#18181b", trim: "#eab308", pants: "#27272a", addon: "estelle" },
  { id: "luna_cat",      name: "Лунная Кошка",    skin: "#f8e3d6", hair: "#e2e8f0", eye: "#86efac", shirt: "#1c1917", trim: "#dc2626", pants: "#09090b", addon: "luna" },
  { id: "shadow_knight", name: "Теневой Рыцарь",  skin: "#94a3b8", hair: "#0f172a", eye: "#38bdf8", shirt: "#1e1b4b", trim: "#38bdf8", pants: "#0f172a", addon: "knight" },
  { id: "emerald_scout", name: "Следопыт",        skin: "#e0ac69", hair: "#451a03", eye: "#22c55e", shirt: "#14532d", trim: "#a3e635", pants: "#3f2e21", addon: "none" },
  { id: "explorer",      name: "Классика HD",     skin: "#e2b28b", hair: "#4a321d", eye: "#2448a8", shirt: "#2898b8", trim: "#1e6f88", pants: "#38467a", addon: "none" }
];

function drawSkinPreview(canvas, skinObj) {
  const ctx = canvas.getContext("2d");
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const w = canvas.width, h = canvas.height;
  const u = Math.floor(w / 16);
  const ox = Math.floor((w - u * 16) / 2);
  const oy = Math.floor((h - u * 24) / 2);
  if (skinObj && skinObj.customImg) {
    ctx.imageSmoothingEnabled = false;
    ctx.drawImage(skinObj.customImg, 0, 0, w, h);
    return;
  }
  const s = skinObj || SKINS_DB[0];
  const rect = (x, y, rw, rh, c) => { ctx.fillStyle = c; ctx.fillRect(ox + x * u, oy + y * u, rw * u, rh * u); };
  rect(4, 0, 8, 8, s.skin);
  rect(4, 0, 8, 2, s.hair); rect(4, 2, 1, 2, s.hair); rect(11, 2, 1, 2, s.hair);
  rect(5, 4, 1, 1, "#ffffff"); rect(6, 4, 1, 1, s.eye);
  rect(10, 4, 1, 1, "#ffffff"); rect(9, 4, 1, 1, s.eye);
  rect(4, 8, 8, 8, s.shirt);
  rect(1, 8, 3, 8, s.shirt); rect(1, 13, 3, 3, s.skin);
  rect(12, 8, 3, 8, s.shirt); rect(12, 13, 3, 3, s.skin);
  rect(4, 16, 4, 8, s.pants); rect(8, 16, 4, 8, s.pants);
}

function buildTextureAtlas() {
  const canvas = document.createElement("canvas");
  canvas.width = ATLAS_COLS * TILE_PX;
  canvas.height = ATLAS_ROWS * TILE_PX;
  const ctx = canvas.getContext("2d");
  ctx.imageSmoothingEnabled = false;

  const tmp16 = document.createElement("canvas");
  tmp16.width = 16; tmp16.height = 16;
  const tctx = tmp16.getContext("2d");

  let animePackActive = true;
  let animeWaterCanvas = null;
  let animeLavaCanvas = null;

  function drawTile16(idx, fn) {
    const tx = (idx % ATLAS_COLS) * TILE_PX;
    const ty = Math.floor(idx / ATLAS_COLS) * TILE_PX;
    const img = tctx.createImageData(16, 16);
    const d = img.data;
    const setPx = (x, y, r, g, b, a = 255) => {
      if (x < 0 || x >= 16 || y < 0 || y >= 16) return;
      const o = (y * 16 + x) * 4;
      d[o] = Math.max(0, Math.min(255, r));
      d[o+1] = Math.max(0, Math.min(255, g));
      d[o+2] = Math.max(0, Math.min(255, b));
      d[o+3] = a;
    };
    const h = (x, y, s) => {
      let n = Math.imul(x * 374761393 + y * 668265263 + s * 1442695041, 1274126177);
      return ((n ^ (n >>> 16)) >>> 0) % 256;
    };
    fn(setPx, h);
    tctx.putImageData(img, 0, 0);
    ctx.imageSmoothingEnabled = false;
    ctx.clearRect(tx, ty, TILE_PX, TILE_PX);
    ctx.drawImage(tmp16, 0, 0, 16, 16, tx, ty, TILE_PX, TILE_PX);
  }

  function paintProceduralBase() {
    drawTile16(0, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=(h(x,y,1)%28)-14; p(x,y, 92+k, 168+k, 58+k); } });
    drawTile16(1, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=(h(x,y,2)%24)-12; const edge=3+(h(x,0,9)%3); if (y<edge) p(x,y, 88+k, 162+k, 54+k); else if (y===edge) p(x,y, 64+k, 118+k, 38+k); else { const peb=h(x,y,5)>235; p(x,y, peb?136:134+k, peb?132:94+k, peb?126:62+k); } } });
    drawTile16(2, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=(h(x,y,3)%26)-13; const peb=h(x,y,4)>238; p(x,y, peb?142:134+k, peb?138:94+k, peb?132:62+k); } });
    drawTile16(3, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const band=Math.floor(h(x>>1,y,7)/16)-8; const k=(h(x,y,8)%16)-8; const v=128+band+k; p(x,y, v, v, v+4); } });
    drawTile16(4, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const gx=(x+(y>>2)*3)%5, gy=y%4; const seam=(gx===0||gy===0); const hi=(gx===1&&gy===1); const v=seam?68:(hi?156:116+((h(x,y,10)%20)-10)); p(x,y,v,v,v+4); } });
    drawTile16(5, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const d=Math.hypot(x-7.5,y-7.5); if (d>6.7) p(x,y,92,66,40); else { const r=(Math.floor(d)%2===0)?16:-6; p(x,y, 174+r, 138+r, 88+r); } } });
    drawTile16(6, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const groove=(x%4===0)?-20:((x%4===2)?10:0); const k=(h(x,y,11)%16)-8; p(x,y, 102+groove+k, 76+groove+k, 46+groove+k); } });
    drawTile16(7, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const r=h(x,y,12); if (r<34) p(x,y,24,62,22,0); else { const k=(r%32)-16; p(x,y, 58+k, 148+k, 52+k, 255); } } });
    drawTile16(8, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const seam=(y%4===0)||((y<4&&x===11)||(y>=4&&y<8&&x===4)||(y>=8&&y<12&&x===13)||(y>=12&&x===6)); const hi=(y%4===1); const k=(h(x,y,13)%14)-7; if (seam) p(x,y,108,82,46); else p(x,y, (hi?188:170)+k, (hi?152:134)+k, (hi?98:84)+k); } });
    drawTile16(9, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=(h(x,y,14)%16)-8; p(x,y, 236+k, 218+k, 152+k); } });
    drawTile16(10, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) p(x,y, 38, 128, 232, 200); });
    drawTile16(11, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const v=28+(h(x,y,15)%58); p(x,y, v, v, v+6); } });

    const ores = [[[28,28,34],[62,62,72]], [[224,184,152],[252,226,198]], [[255,204,42],[255,246,154]], [[58,238,255],[194,254,255]]];
    const spots = [[3,3],[4,3],[3,4],[11,4],[12,4],[11,5],[6,9],[7,9],[7,10],[12,11],[13,11],[2,12],[3,12]];
    ores.forEach(([c1, c2], idx) => {
      drawTile16(12 + idx, (p, h) => {
        for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const v=122+((h(x,y,20+idx)%18)-9); p(x,y,v,v,v+4); }
        spots.forEach(([sx, sy], si) => { const c = si%3===0 ? c2 : c1; p(sx, sy, c[0], c[1], c[2]); });
      });
    });

    drawTile16(16, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const b=(x===0||x===15||y===0||y===15); const gl=(x===3&&y===3)||(x===4&&y===2)||(x===12&&y===12); p(x,y, b?192:225, b?235:248, 255, b?220:(gl?200:35)); } });
    drawTile16(17, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const m=(y%4===0)||((x+(y>>2)*4)%8===0); const k=(h(x,y,25)%16)-8; if (m) p(x,y,192,188,180); else p(x,y,164+k,72+k,56+k); } });
    drawTile16(18, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const b=(x===0||x===15||y===0||y===15); const g=(x===5||x===10||y===5||y===10); p(x,y, b?88:(g?66:174), b?58:(g?42:128), b?34:(g?24:76)); } });
    drawTile16(19, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const b=(x===0||x===15||y===0||y===15||y===4); p(x,y, b?88:162, b?58:122, b?34:72); if (x>=3&&x<=5&&y>=7&&y<=13) p(x,y,192,198,208); if (x>=9&&x<=12&&y>=8&&y<=11) p(x,y,192,198,208); } });
    drawTile16(20, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const b=(x===0||x===15||y===0||y===15); const v=b?74:118+((h(x,y,26)%14)-7); p(x,y,v,v,v+4); } });
    drawTile16(21, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { if (x>=4&&x<=11&&y>=9&&y<=13) p(x,y, 248, 128+((x*19+y*23)%80), 28); else if (x>=3&&x<=12&&y>=3&&y<=6) p(x,y, 32,32,36); else { const v=116+((h(x,y,27)%16)-8); p(x,y,v,v,v+4); } } });
    drawTile16(22, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const v=238+(h(x,y,28)%12); p(x,y,v,v,255); } });
    drawTile16(23, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=(h(x,y,29)%20)-10; if (y<4+(h(x,1,29)%3)) p(x,y,242,248,255); else p(x,y,134+k,94+k,62+k); } });
    drawTile16(24, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=(h(x,y,30)%18)-9; p(x,y, 138+k, 206+k, 255, 210); } });
    drawTile16(25, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) p(x,y, 46, 134, 52); });
    drawTile16(26, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const rib=(x%4===0); p(x,y, rib?32:54, rib?102:152, rib?34:58); } });
    drawTile16(27, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const r=h(x,y,31); p(x,y, r>220?98:24+(r%18), r>220?52:16+(r%12), r>220?158:48+(r%24)); } });
    drawTile16(28, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const r=h(x,y,32); if (r>180) p(x,y,255,244,174); else if (r>90) p(x,y,242,194,82); else p(x,y,164,114,46); } });
    drawTile16(29, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { if (y<2||y>13||y===7||y===8) p(x,y,176,130,78); else { const bc=[[210,48,64],[52,134,224],[62,188,92],[238,186,48]][(x>>1)%4]; p(x,y,bc[0],bc[1],bc[2]); } } });
    drawTile16(30, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const v=116+(h(x,y,33)%42); p(x,y,v,v-2,v-4); } });
    drawTile16(31, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) p(x,y, 204, 48, 46); });
    drawTile16(32, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { if (y>=5&&y<=10) p(x,y, 244, 240, 232); else p(x,y, (x%2===0?218:194), 46, 46); } });
    drawTile16(33, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const dash=(y%4===1&&x>=2&&x<=6)||(y%5===3&&x>=9&&x<=13); const v=232+((h(x,y,34)%18)-9); p(x,y, dash?46:v, dash?44:v, dash?48:v-4); } });
    drawTile16(34, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const band=(y<3)?14:((y===3)?-16:0); const k=(h(x,y,35)%14)-7; p(x,y, 232+band+k, 214+band+k, 158+band+k); } });
    drawTile16(35, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) p(x,y, 248, 98, 22); });
    drawTile16(36, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=(h(x,y,36)%34)-17; p(x,y, 128+k, 42+(k>>1), 46+(k>>1)); } });
    drawTile16(37, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=(h(x,y,37)%24)-12; p(x,y, 86+k, 64+k, 54+k); } });
    drawTile16(38, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const m=(y%4===0)||((x+(y>>2)*4)%8===0); const k=(h(x,y,38)%14)-7; if (m) p(x,y,28,16,22); else p(x,y, 64+k, 28+k, 38+k); } });
    drawTile16(39, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=(h(x,y,39)%28)-14; p(x,y,126+k,42+(k>>1),46+(k>>1)); } spots.forEach(([sx,sy])=>p(sx,sy,246,242,235)); });
    drawTile16(40, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) p(x,y, 158, 42, 242, 200); });
    drawTile16(41, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const b=(x===0||x===15||y===0||y===15); p(x,y, b?58:186, b?36:128, b?18:48); } });
    drawTile16(42, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const b=(x===0||x===15||y===0||y===15||y===5); const lk=(x>=7&&x<=8&&y>=4&&y<=7); p(x,y, lk?235:(b?58:186), lk?215:(b?36:128), lk?120:(b?18:48)); } });
    drawTile16(43, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const v=124+((h(x,y,43)%18)-9); p(x,y,v,v,v+4); } spots.forEach(([sx,sy],si)=>p(sx,sy,si%2?135:42,248,si%2?168:98)); });
    drawTile16(44, (p) => {
      for (let y=0;y<16;y++) for (let x=0;x<16;x++) p(x,y,0,0,0,0);
      for (let y=6;y<16;y++) { p(7,y,154,102,52,255); p(8,y,126,80,38,255); }
      for (let y=3;y<=5;y++) { p(7,y,255,212,52,255); p(8,y,255,134,28,255); }
      p(7,2,255,250,180,255);
    });
    drawTile16(45, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { if (y<4) p(x,y,246,246,255); else p(x,y,(x===0||x===15||y===15)?160:220,42,58); } });
    drawTile16(46, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { if (y<8) p(x,y,220,42,58); else if (y<12) p(x,y,168,122,74); else if (x<3||x>12) p(x,y,128,88,48); else p(x,y,0,0,0,0); } });
    drawTile16(47, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) p(x,y,0,0,0,0); for (const sx of [2,5,8,11,14]) for (let y=3;y<16;y++) p(sx,y,y<7?255:214,y<7?218:172,56,255); });
  }

  paintProceduralBase();

  const tex = new THREE.CanvasTexture(canvas);
  tex.magFilter = THREE.NearestFilter;
  tex.minFilter = THREE.NearestFilter;
  tex.flipY = false;

  // Загрузка HD Аниме-Атласа (anime_tiles.png — 4x4 от Nano Banana) прямо в 64x64 ячейки!
  function applyAnimeTexturePack(enable) {
    animePackActive = Boolean(enable);
    if (!enable) {
      animeWaterCanvas = null;
      animeLavaCanvas = null;
      paintProceduralBase();
      tex.needsUpdate = true;
      if (VH.Engine?.renderHotbar) VH.Engine.renderHotbar();
      return;
    }

    const img = new Image();
    img.onload = () => {
      const sw = img.width / 4, sh = img.height / 4;
      const blit = (cx, cy, tileIdx) => {
        const dx = (tileIdx % ATLAS_COLS) * TILE_PX;
        const dy = Math.floor(tileIdx / ATLAS_COLS) * TILE_PX;
        ctx.imageSmoothingEnabled = true;
        ctx.clearRect(dx, dy, TILE_PX, TILE_PX);
        ctx.drawImage(img, cx * sw, cy * sh, sw, sh, dx, dy, TILE_PX, TILE_PX);
      };

      // Основные 16 тайлов напрямую из Nano Banana 4x4 атласа!
      const map16 = [
        [0,0, 0],  [1,0, 1],  [2,0, 2],  [3,0, 4],   // Дёрн верх, Дёрн бок, Земля, Булыжник
        [0,1, 8],  [1,1, 6],  [2,1, 15], [3,1, 27],  // Доски, Кора дуба, Алмазная руда, Обсидиан
        [0,2, 17], [1,2, 36], [2,2, 28], [3,2, 10],  // Кирпичи, Адский камень, Светокамень, Вода
        [0,3, 35], [1,3, 18], [2,3, 42], [3,3, 21]   // Лава, Верстак, Сундук, Печь
      ];
      for (const [cx, cy, tid] of map16) blit(cx, cy, tid);

      // Производные HD аниме-тайлы для гармоничного вида всех остальных блоков!
      blit(0, 0, 7);  // Листва (пышная зелёная основа + лёгкий аниме-блик)
      blit(0, 1, 19); // Бок верстака на основе аниме-досок
      blit(0, 1, 41); // Крышка сундука на основе аниме-досок
      blit(3, 0, 20); // Верх печи на основе аниме-камня
      blit(1, 2, 39); // Кварцевая руда на основе аниме-адского камня
      // Добавляем яркие кристаллы кварца поверх 39 тайла
      const qx = (39 % ATLAS_COLS) * TILE_PX, qy = Math.floor(39 / ATLAS_COLS) * TILE_PX;
      ctx.fillStyle = "#fff8ee";
      [[10,12,12,8],[38,16,14,10],[18,40,16,10],[44,44,10,8]].forEach(([x,y,w,h]) => ctx.fillRect(qx+x, qy+y, w, h));

      // Снежная сторона (23): аниме-земля + белый снежный верх
      blit(2, 0, 23);
      const sx = (23 % ATLAS_COLS) * TILE_PX, sy = Math.floor(23 / ATLAS_COLS) * TILE_PX;
      ctx.fillStyle = "#f2f8ff";
      ctx.fillRect(sx, sy, TILE_PX, 18);
      ctx.fillStyle = "#cfe4ff";
      ctx.fillRect(sx, sy + 16, TILE_PX, 4);

      // Кэшируем Воду и Лаву из аниме-атласа для плавной прокрутки течения
      animeWaterCanvas = document.createElement("canvas");
      animeWaterCanvas.width = TILE_PX; animeWaterCanvas.height = TILE_PX;
      animeWaterCanvas.getContext("2d").drawImage(img, 3 * sw, 2 * sh, sw, sh, 0, 0, TILE_PX, TILE_PX);

      animeLavaCanvas = document.createElement("canvas");
      animeLavaCanvas.width = TILE_PX; animeLavaCanvas.height = TILE_PX;
      animeLavaCanvas.getContext("2d").drawImage(img, 0 * sw, 3 * sh, sw, sh, 0, 0, TILE_PX, TILE_PX);

      tex.needsUpdate = true;
      if (VH.Engine?.renderHotbar) VH.Engine.renderHotbar();
    };
    img.src = "anime_tiles.png";
  }

  let lastAnimStep = -1;
  function updateAnimatedTextures(timeSec) {
    const step = Math.floor(timeSec * 8);
    if (step === lastAnimStep) return;
    lastAnimStep = step;

    if (animePackActive && animeWaterCanvas && animeLavaCanvas) {
      // Плавное анимированное течение поверх аниме-воды (10) и аниме-лавы (35)!
      const offW = (step * 3) % TILE_PX;
      const wx = (10 % ATLAS_COLS) * TILE_PX, wy = Math.floor(10 / ATLAS_COLS) * TILE_PX;
      ctx.drawImage(animeWaterCanvas, 0, 0, TILE_PX, TILE_PX - offW, wx, wy + offW, TILE_PX, TILE_PX - offW);
      if (offW > 0) ctx.drawImage(animeWaterCanvas, 0, TILE_PX - offW, TILE_PX, offW, wx, wy, TILE_PX, offW);

      const offL = (step * 2) % TILE_PX;
      const lx = (35 % ATLAS_COLS) * TILE_PX, ly = Math.floor(35 / ATLAS_COLS) * TILE_PX;
      ctx.drawImage(animeLavaCanvas, 0, 0, TILE_PX, TILE_PX - offL, lx, ly + offL, TILE_PX, TILE_PX - offL);
      if (offL > 0) ctx.drawImage(animeLavaCanvas, 0, TILE_PX - offL, TILE_PX, offL, lx, ly, TILE_PX, offL);
    } else {
      drawTile16(10, (p) => {
        for (let y=0;y<16;y++) for (let x=0;x<16;x++) {
          const w = Math.sin((x*0.55)+(y*0.35)+step*0.55)*18 + Math.cos((y*0.65)-step*0.4)*12;
          const crest = w > 20 ? 35 : 0;
          p(x, y, 32+crest, 114+w+crest, 224+(w*0.5), 192);
        }
      });
      drawTile16(35, (p) => {
        for (let y=0;y<16;y++) for (let x=0;x<16;x++) {
          const w = Math.sin(x*0.6+step*0.35)*Math.cos(y*0.6-step*0.25);
          const hot = w > 0.35;
          p(x, y, hot?255:216, hot?188:74+Math.floor((w+1)*35), hot?48:16, 255);
        }
      });
    }

    // Вихрь Портала в Ад (тайл 40)
    drawTile16(40, (p) => {
      for (let y=0;y<16;y++) for (let x=0;x<16;x++) {
        const d = Math.hypot(x - 7.5, y - 7.5);
        const swirl = Math.sin(d * 0.9 - step * 0.6 + Math.atan2(y - 7.5, x - 7.5) * 2);
        p(x, y, 148 + swirl * 65, 32 + Math.max(0, swirl) * 60, 232 + swirl * 22, 205);
      }
    });
    tex.needsUpdate = true;
  }

  let nextModTile = 48;
  function registerAtlasTile(drawOrColor, forcedIdx = null) {
    const idx = forcedIdx !== null ? forcedIdx : nextModTile++;
    if (idx >= ATLAS_COLS * ATLAS_ROWS) return 3;
    if (typeof drawOrColor === "function") {
      drawTile16(idx, drawOrColor);
    } else if (Array.isArray(drawOrColor)) {
      drawTile16(idx, (p) => {
        for (let i = 0; i < Math.min(256, drawOrColor.length); i++) {
          const c = drawOrColor[i];
          if (c) p(i % 16, Math.floor(i / 16), c[0], c[1], c[2], c[3] ?? 255);
        }
      });
    } else {
      const hex = String(drawOrColor || "#2ed573").replace("#", "");
      const rr = parseInt(hex.slice(0, 2), 16) || 80;
      const gg = parseInt(hex.slice(2, 4), 16) || 200;
      const bb = parseInt(hex.slice(4, 6), 16) || 120;
      drawTile16(idx, (p, h) => {
        for (let y=0;y<16;y++) for (let x=0;x<16;x++) {
          const b = (x===0||x===15||y===0||y===15);
          const k = (h(x, y, idx + 7) % 24) - 12;
          p(x, y, (b?rr-28:rr)+k, (b?gg-28:gg)+k, (b?bb-28:bb)+k);
        }
      });
    }
    tex.needsUpdate = true;
    return idx;
  }

  VH.registerAtlasTile = registerAtlasTile;
  VH.applyAnimeTexturePack = applyAnimeTexturePack;
  applyAnimeTexturePack(true);
  return { canvas, texture: tex, updateAnimatedTextures, registerAtlasTile, applyAnimeTexturePack };
}

// 32x32 АНИМЕ-ИКОНКИ ПРЕДМЕТОВ И БЛОКОВ (С ЧЁТКИМ КОНТУРОМ И БЛИКАМИ!)
function drawItemIcon(canvas, id) {
  const ctx = canvas.getContext("2d");
  canvas.width = 32; canvas.height = 32;
  ctx.clearRect(0, 0, 32, 32);
  const info = ITEMS_DB[id];
  if (!info || !VH.atlasCanvas) return;

  if (typeof info.drawIcon === "function") { try { info.drawIcon(ctx); return; } catch {} }
  if (Array.isArray(info.pixelIcon)) {
    for (let i = 0; i < Math.min(256, info.pixelIcon.length); i++) {
      const c = info.pixelIcon[i];
      if (!c || c[3] === 0) continue;
      ctx.fillStyle = `rgba(${c[0]},${c[1]},${c[2]},${(c[3] ?? 255)/255})`;
      ctx.fillRect((i % 16) * 2, Math.floor(i / 16) * 2, 2, 2);
    }
    return;
  }

  if (!info.item && info.faces) {
    const f = info.faces[1];
    ctx.imageSmoothingEnabled = true;
    ctx.drawImage(VH.atlasCanvas, (f % ATLAS_COLS) * TILE_PX, Math.floor(f / ATLAS_COLS) * TILE_PX, TILE_PX, TILE_PX, 2, 2, 28, 28);
    if (!info.torch && !info.crop) {
      ctx.strokeStyle = "rgba(255, 215, 0, 0.55)";
      ctx.lineWidth = 1.5;
      ctx.strokeRect(2, 2, 28, 28);
    }
    return;
  }

  // Аниме RPG-иконки для предметов
  const col = info.color || "#ffffff";
  const drawOutlined = (fn) => {
    ctx.save();
    ctx.lineJoin = "round";
    ctx.lineCap = "round";
    fn();
    ctx.restore();
  };

  drawOutlined(() => {
    if (info.shape === "sword") {
      // Клинок с золотой гардой и аниме-бликом
      ctx.strokeStyle = "#120c1c"; ctx.lineWidth = 6;
      ctx.beginPath(); ctx.moveTo(8, 24); ctx.lineTo(26, 6); ctx.stroke();
      ctx.strokeStyle = col; ctx.lineWidth = 4;
      ctx.beginPath(); ctx.moveTo(10, 22); ctx.lineTo(26, 6); ctx.stroke();
      ctx.strokeStyle = "#ffffff"; ctx.lineWidth = 1.5;
      ctx.beginPath(); ctx.moveTo(13, 19); ctx.lineTo(25, 7); ctx.stroke();
      ctx.strokeStyle = "#ffd700"; ctx.lineWidth = 4;
      ctx.beginPath(); ctx.moveTo(8, 16); ctx.lineTo(16, 24); ctx.stroke();
      ctx.strokeStyle = "#7c4a22"; ctx.lineWidth = 3.5;
      ctx.beginPath(); ctx.moveTo(6, 26); ctx.lineTo(11, 21); ctx.stroke();
    } else if (info.shape === "pick") {
      ctx.strokeStyle = "#120c1c"; ctx.lineWidth = 6;
      ctx.beginPath(); ctx.moveTo(7, 25); ctx.lineTo(22, 10); ctx.stroke();
      ctx.strokeStyle = "#9c6230"; ctx.lineWidth = 3.5;
      ctx.beginPath(); ctx.moveTo(7, 25); ctx.lineTo(22, 10); ctx.stroke();
      ctx.strokeStyle = "#120c1c"; ctx.lineWidth = 7;
      ctx.beginPath(); ctx.arc(17, 15, 11, -Math.PI * 0.72, -Math.PI * 0.02); ctx.stroke();
      ctx.strokeStyle = col; ctx.lineWidth = 4.5;
      ctx.beginPath(); ctx.arc(17, 15, 11, -Math.PI * 0.72, -Math.PI * 0.02); ctx.stroke();
    } else if (info.shape === "axe") {
      ctx.strokeStyle = "#120c1c"; ctx.lineWidth = 6;
      ctx.beginPath(); ctx.moveTo(7, 25); ctx.lineTo(22, 10); ctx.stroke();
      ctx.strokeStyle = "#9c6230"; ctx.lineWidth = 3.5;
      ctx.beginPath(); ctx.moveTo(7, 25); ctx.lineTo(22, 10); ctx.stroke();
      ctx.fillStyle = col; ctx.strokeStyle = "#120c1c"; ctx.lineWidth = 2;
      ctx.beginPath(); ctx.moveTo(15, 7); ctx.lineTo(26, 8); ctx.lineTo(25, 19); ctx.lineTo(16, 15); ctx.closePath();
      ctx.fill(); ctx.stroke();
    } else if (info.shape === "bow") {
      ctx.strokeStyle = "#e6eef8"; ctx.lineWidth = 1.5;
      ctx.beginPath(); ctx.moveTo(9, 6); ctx.lineTo(9, 26); ctx.stroke();
      ctx.strokeStyle = "#120c1c"; ctx.lineWidth = 6;
      ctx.beginPath(); ctx.arc(10, 16, 12, -Math.PI * 0.45, Math.PI * 0.45); ctx.stroke();
      ctx.strokeStyle = "#b87438"; ctx.lineWidth = 3.5;
      ctx.beginPath(); ctx.arc(10, 16, 12, -Math.PI * 0.45, Math.PI * 0.45); ctx.stroke();
      ctx.strokeStyle = "#ffd700"; ctx.lineWidth = 2;
      ctx.beginPath(); ctx.moveTo(7, 16); ctx.lineTo(26, 16); ctx.stroke();
    } else if (info.shape === "gem") {
      ctx.fillStyle = col; ctx.strokeStyle = "#120c1c"; ctx.lineWidth = 2.5;
      ctx.beginPath(); ctx.moveTo(16, 4); ctx.lineTo(26, 14); ctx.lineTo(16, 28); ctx.lineTo(6, 14); ctx.closePath();
      ctx.fill(); ctx.stroke();
      ctx.fillStyle = "rgba(255,255,255,0.6)";
      ctx.beginPath(); ctx.moveTo(16, 7); ctx.lineTo(21, 13); ctx.lineTo(14, 15); ctx.closePath(); ctx.fill();
    } else if (info.shape === "ingot") {
      ctx.fillStyle = col; ctx.strokeStyle = "#120c1c"; ctx.lineWidth = 2.5;
      ctx.beginPath(); ctx.moveTo(8, 12); ctx.lineTo(24, 12); ctx.lineTo(28, 22); ctx.lineTo(4, 22); ctx.closePath();
      ctx.fill(); ctx.stroke();
      ctx.fillStyle = "rgba(255,255,255,0.55)"; ctx.fillRect(10, 14, 12, 3);
    } else if (info.shape === "food") {
      ctx.fillStyle = col; ctx.strokeStyle = "#120c1c"; ctx.lineWidth = 2.5;
      ctx.beginPath(); ctx.arc(16, 17, 10, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
      ctx.fillStyle = "rgba(255,255,255,0.55)"; ctx.beginPath(); ctx.arc(12, 13, 3, 0, Math.PI * 2); ctx.fill();
      if (id === 113) { ctx.fillStyle = "#4caf50"; ctx.fillRect(15, 4, 5, 4); }
    } else if (info.shape === "bucket") {
      ctx.fillStyle = "#c8d4e4"; ctx.strokeStyle = "#120c1c"; ctx.lineWidth = 2.5;
      ctx.beginPath(); ctx.moveTo(6, 9); ctx.lineTo(26, 9); ctx.lineTo(23, 27); ctx.lineTo(9, 27); ctx.closePath();
      ctx.fill(); ctx.stroke();
      ctx.fillStyle = col; ctx.fillRect(8, 11, 16, 7);
    } else if (info.shape === "flint") {
      ctx.fillStyle = "#8896a8"; ctx.strokeStyle = "#120c1c"; ctx.lineWidth = 2;
      ctx.fillRect(6, 14, 10, 12); ctx.strokeRect(6, 14, 10, 12);
      ctx.fillStyle = "#ff9922";
      ctx.beginPath(); ctx.moveTo(20, 5); ctx.lineTo(26, 15); ctx.lineTo(17, 19); ctx.closePath(); ctx.fill(); ctx.stroke();
    } else if (info.shape === "helmet" || info.shape === "chest" || info.shape === "legs" || info.shape === "boots") {
      ctx.fillStyle = col; ctx.strokeStyle = "#120c1c"; ctx.lineWidth = 2.5;
      if (info.shape === "helmet") { ctx.fillRect(6, 8, 20, 14); ctx.strokeRect(6, 8, 20, 14); ctx.fillStyle = "#120c1c"; ctx.fillRect(10, 16, 12, 6); }
      else if (info.shape === "chest") { ctx.fillRect(6, 6, 20, 20); ctx.strokeRect(6, 6, 20, 20); ctx.fillStyle = "#ffd700"; ctx.fillRect(13, 12, 6, 6); }
      else if (info.shape === "legs") { ctx.fillRect(8, 6, 16, 8); ctx.fillRect(8, 14, 6, 13); ctx.fillRect(18, 14, 6, 13); ctx.strokeRect(8, 6, 16, 8); }
      else { ctx.fillRect(5, 14, 9, 12); ctx.fillRect(18, 14, 9, 12); ctx.strokeRect(5, 14, 9, 12); ctx.strokeRect(18, 14, 9, 12); }
    } else {
      ctx.strokeStyle = "#120c1c"; ctx.lineWidth = 6;
      ctx.beginPath(); ctx.moveTo(8, 24); ctx.lineTo(24, 8); ctx.stroke();
      ctx.strokeStyle = col; ctx.lineWidth = 3.5;
      ctx.beginPath(); ctx.moveTo(8, 24); ctx.lineTo(24, 8); ctx.stroke();
    }
  });
}

VH.ITEMS_DB = ITEMS_DB;
VH.SKINS_DB = SKINS_DB;
VH.ATLAS_COLS = ATLAS_COLS;
VH.ATLAS_ROWS = ATLAS_ROWS;
VH.drawSkinPreview = drawSkinPreview;
VH.buildTextureAtlas = buildTextureAtlas;
VH.drawItemIcon = drawItemIcon;
