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
const TILE_PX = 16;

const SKINS_DB = [
  { id: "explorer", name: "Искатель",  skin: "#e2b28b", hair: "#4a321d", shirt: "#2898b8", pants: "#38467a", eye: "#2448a8" },
  { id: "ranger",   name: "Рейнджер",  skin: "#ebbfa0", hair: "#b86428", shirt: "#3d8c40", pants: "#4d3e30", eye: "#2d8a42" },
  { id: "knight",   name: "Рыцарь",    skin: "#d8a884", hair: "#5a6270", shirt: "#8c96a6", pants: "#4a5260", eye: "#38c8ff" },
  { id: "shadow",   name: "Тень",      skin: "#d2a892", hair: "#1e1a28", shirt: "#2c223c", pants: "#1c1826", eye: "#d24cff" },
  { id: "crimson",  name: "Огненный",  skin: "#e0ad86", hair: "#321814", shirt: "#b82c2c", pants: "#3a2224", eye: "#ffaa22" },
  { id: "cyber",    name: "Кибер",     skin: "#d8c0b0", hair: "#1e2a38", shirt: "#243448", pants: "#1c2430", eye: "#2eff98" }
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
  rect(7, 6, 2, 1, "#9c6248");
  rect(4, 8, 8, 8, s.shirt);
  rect(1, 8, 3, 8, s.shirt); rect(1, 13, 3, 3, s.skin);
  rect(12, 8, 3, 8, s.shirt); rect(12, 13, 3, 3, s.skin);
  rect(4, 16, 4, 8, s.pants); rect(8, 16, 4, 8, s.pants);
  rect(4, 22, 4, 2, "#2a2a2e"); rect(8, 22, 4, 2, "#2a2a2e");
}

function buildTextureAtlas() {
  const canvas = document.createElement("canvas");
  canvas.width = ATLAS_COLS * TILE_PX;
  canvas.height = ATLAS_ROWS * TILE_PX;
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
    const h = (x, y, s) => {
      let n = Math.imul(x * 374761393 + y * 668265263 + s * 1442695041, 1274126177);
      return ((n ^ (n >>> 16)) >>> 0) % 256;
    };
    fn(setPx, h);
    ctx.putImageData(img, tx, ty);
  }

  // 0..43 базовые тайлы
  drawTile(0, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=(h(x,y,1)%28)-14; p(x,y, 92+k, 168+k, 58+k); } });
  drawTile(1, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=(h(x,y,2)%24)-12; const edge=3+(h(x,0,9)%3); if (y<edge) p(x,y, 88+k, 162+k, 54+k); else if (y===edge) p(x,y, 64+k, 118+k, 38+k); else { const peb=h(x,y,5)>235; p(x,y, peb?136:134+k, peb?132:94+k, peb?126:62+k); } } });
  drawTile(2, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=(h(x,y,3)%26)-13; const peb=h(x,y,4)>238; p(x,y, peb?142:134+k, peb?138:94+k, peb?132:62+k); } });
  drawTile(3, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const band=Math.floor(h(x>>1,y,7)/16)-8; const k=(h(x,y,8)%16)-8; const v=128+band+k; p(x,y, v, v, v+2); } });
  drawTile(4, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const gx=(x+(y>>2)*3)%5, gy=y%4; const seam=(gx===0||gy===0); const hi=(gx===1&&gy===1); const v=seam?68:(hi?156:116+((h(x,y,10)%20)-10)); p(x,y,v,v,v+2); } });
  drawTile(5, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const d=Math.hypot(x-7.5,y-7.5); if (d>6.7) p(x,y,92,66,40); else { const r=(Math.floor(d)%2===0)?16:-6; p(x,y, 174+r, 138+r, 88+r); } } });
  drawTile(6, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const groove=(x%4===0)?-20:((x%4===2)?10:0); const k=(h(x,y,11)%16)-8; p(x,y, 102+groove+k, 76+groove+k, 46+groove+k); } });
  drawTile(7, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const r=h(x,y,12); if (r<38) p(x,y,24,62,22,0); else { const k=(r%32)-16; p(x,y, 58+k, 138+k, 46+k, 255); } } });
  drawTile(8, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const seam=(y%4===0)||((y<4&&x===11)||(y>=4&&y<8&&x===4)||(y>=8&&y<12&&x===13)||(y>=12&&x===6)); const hi=(y%4===1); const k=(h(x,y,13)%14)-7; if (seam) p(x,y,108,82,46); else p(x,y, (hi?188:170)+k, (hi?152:134)+k, (hi?98:84)+k); } });
  drawTile(9, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=(h(x,y,14)%16)-8; p(x,y, 224+k, 212+k, 158+k); } });
  drawTile(10, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) p(x,y, 38, 118, 224, 195); });
  drawTile(11, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const v=30+(h(x,y,15)%65); p(x,y, v, v, v); } });

  const ores = [[[28,28,32],[54,54,60]], [[216,178,148],[242,216,192]], [[248,202,42],[255,244,140]], [[64,232,248],[180,252,255]]];
  const spots = [[3,3],[4,3],[3,4],[11,4],[12,4],[11,5],[6,9],[7,9],[7,10],[12,11],[13,11],[2,12],[3,12]];
  ores.forEach(([c1, c2], idx) => {
    drawTile(12 + idx, (p, h) => {
      for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const v=124+((h(x,y,20+idx)%18)-9); p(x,y,v,v,v+2); }
      spots.forEach(([sx, sy], si) => { const c = si%3===0 ? c2 : c1; p(sx, sy, c[0], c[1], c[2]); });
    });
  });

  drawTile(16, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const b=(x===0||x===15||y===0||y===15); const gl=(x===3&&y===3)||(x===4&&y===2)||(x===12&&y===12); p(x,y, b?192:225, b?235:248, 255, b?220:(gl?200:35)); } });
  drawTile(17, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const m=(y%4===0)||((x+(y>>2)*4)%8===0); const k=(h(x,y,25)%16)-8; if (m) p(x,y,192,188,180); else p(x,y,154+k,72+k,56+k); } });
  drawTile(18, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const b=(x===0||x===15||y===0||y===15); const g=(x===5||x===10||y===5||y===10); p(x,y, b?88:(g?66:174), b?58:(g?42:128), b?34:(g?24:76)); } });
  drawTile(19, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const b=(x===0||x===15||y===0||y===15||y===4); p(x,y, b?88:162, b?58:122, b?34:72); if (x>=3&&x<=5&&y>=7&&y<=13) p(x,y,192,198,208); if (x>=9&&x<=12&&y>=8&&y<=11) p(x,y,192,198,208); } });
  drawTile(20, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const b=(x===0||x===15||y===0||y===15); const v=b?74:118+((h(x,y,26)%14)-7); p(x,y,v,v,v); } });
  drawTile(21, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { if (x>=4&&x<=11&&y>=9&&y<=13) p(x,y, 248, 128+((x*19+y*23)%80), 28); else if (x>=3&&x<=12&&y>=3&&y<=6) p(x,y, 32,32,36); else { const v=116+((h(x,y,27)%16)-8); p(x,y,v,v,v); } } });
  drawTile(22, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const v=238+(h(x,y,28)%12); p(x,y,v,v,255); } });
  drawTile(23, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=(h(x,y,29)%20)-10; if (y<4+(h(x,1,29)%3)) p(x,y,242,248,255); else p(x,y,134+k,94+k,62+k); } });
  drawTile(24, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=(h(x,y,30)%18)-9; p(x,y, 138+k, 198+k, 250, 210); } });
  drawTile(25, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) p(x,y, 46, 124, 48); });
  drawTile(26, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const rib=(x%4===0); p(x,y, rib?32:54, rib?96:142, rib?34:54); } });
  drawTile(27, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const r=h(x,y,31); p(x,y, r>220?82:24+(r%18), r>220?48:16+(r%12), r>220?128:42+(r%24)); } });
  drawTile(28, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const r=h(x,y,32); if (r>180) p(x,y,255,244,174); else if (r>90) p(x,y,232,188,88); else p(x,y,154,108,46); } });
  drawTile(29, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { if (y<2||y>13||y===7||y===8) p(x,y,168,130,82); else { const bc=[[178,42,48],[46,114,192],[58,158,76],[218,174,48]][(x>>1)%4]; p(x,y,bc[0],bc[1],bc[2]); } } });
  drawTile(30, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const v=112+(h(x,y,33)%42); p(x,y,v,v-2,v-4); } });
  drawTile(31, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) p(x,y, 186, 48, 42); });
  drawTile(32, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { if (y>=5&&y<=10) p(x,y, 238, 234, 224); else p(x,y, (x%2===0?204:184), 46, 42); } });
  drawTile(33, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const dash=(y%4===1&&x>=2&&x<=6)||(y%5===3&&x>=9&&x<=13); const v=224+((h(x,y,34)%18)-9); p(x,y, dash?46:v, dash?44:v, dash?42:v-4); } });
  drawTile(34, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const band=(y<3)?14:((y===3)?-16:0); const k=(h(x,y,35)%14)-7; p(x,y, 224+band+k, 210+band+k, 162+band+k); } });
  drawTile(35, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) p(x,y, 238, 92, 20); });
  drawTile(36, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=(h(x,y,36)%34)-17; p(x,y, 118+k, 46+(k>>1), 46+(k>>1)); } });
  drawTile(37, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=(h(x,y,37)%24)-12; p(x,y, 82+k, 64+k, 52+k); } });
  drawTile(38, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const m=(y%4===0)||((x+(y>>2)*4)%8===0); const k=(h(x,y,38)%14)-7; if (m) p(x,y,28,16,20); else p(x,y, 58+k, 28+k, 34+k); } });
  drawTile(39, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const k=(h(x,y,39)%28)-14; p(x,y,118+k,46+(k>>1),46+(k>>1)); } spots.forEach(([sx,sy])=>p(sx,sy,242,238,230)); });
  drawTile(40, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) p(x,y, 148, 36, 232, 195); });
  drawTile(41, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const b=(x===0||x===15||y===0||y===15); p(x,y, b?52:168, b?34:118, b?16:44); } });
  drawTile(42, (p) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const b=(x===0||x===15||y===0||y===15||y===5); const lk=(x>=7&&x<=8&&y>=4&&y<=7); p(x,y, lk?210:(b?52:168), lk?214:(b?34:118), lk?220:(b?16:44)); } });
  drawTile(43, (p, h) => { for (let y=0;y<16;y++) for (let x=0;x<16;x++) { const v=124+((h(x,y,43)%18)-9); p(x,y,v,v,v+2); } spots.forEach(([sx,sy],si)=>p(sx,sy,si%2?120:42,238,si%2?152:92)); });

  // 44: Torch (прозрачный фон + палка + горящий уголёк!)
  drawTile(44, (p) => {
    for (let y=0;y<16;y++) for (let x=0;x<16;x++) p(x,y,0,0,0,0);
    for (let y=6;y<16;y++) { p(7,y,142,98,52,255); p(8,y,118,78,38,255); }
    for (let y=3;y<=5;y++) { p(7,y,255,198,42,255); p(8,y,255,134,24,255); }
    p(7,2,255,244,160,255);
  });
  // 45: Bed Top (белая подушка + красное одеяло)
  drawTile(45, (p) => {
    for (let y=0;y<16;y++) for (let x=0;x<16;x++) {
      if (y < 4) p(x,y, 240, 242, 248);
      else p(x,y, (x===0||x===15||y===15)?148:198, 38, 42);
    }
  });
  // 46: Bed Side
  drawTile(46, (p) => {
    for (let y=0;y<16;y++) for (let x=0;x<16;x++) {
      if (y < 8) p(x,y, 198, 42, 46);
      else if (y < 12) p(x,y, 158, 118, 72);
      else if (x < 3 || x > 12) p(x,y, 128, 92, 54);
      else p(x,y,0,0,0,0);
    }
  });
  // 47: Wheat Crop
  drawTile(47, (p) => {
    for (let y=0;y<16;y++) for (let x=0;x<16;x++) p(x,y,0,0,0,0);
    for (const sx of [2, 5, 8, 11, 14]) {
      for (let y=3;y<16;y++) p(sx, y, y<7?242:198, y<7?204:162, 48, 255);
    }
  });

  const tex = new THREE.CanvasTexture(canvas);
  tex.magFilter = THREE.NearestFilter;
  tex.minFilter = THREE.NearestFilter;
  tex.flipY = false;

  let lastAnimStep = -1;
  function updateAnimatedTextures(timeSec) {
    const step = Math.floor(timeSec * 8);
    if (step === lastAnimStep) return;
    lastAnimStep = step;

    drawTile(10, (p) => {
      for (let y=0;y<16;y++) for (let x=0;x<16;x++) {
        const w = Math.sin((x * 0.55) + (y * 0.35) + step * 0.55) * 18 + Math.cos((y * 0.65) - step * 0.4) * 12;
        const crest = w > 20 ? 35 : 0;
        p(x, y, 32 + crest, 114 + w + crest, 224 + (w * 0.5), 192);
      }
    });
    drawTile(35, (p) => {
      for (let y=0;y<16;y++) for (let x=0;x<16;x++) {
        const w = Math.sin(x * 0.6 + step * 0.35) * Math.cos(y * 0.6 - step * 0.25);
        const hot = w > 0.35;
        p(x, y, hot ? 255 : 216, hot ? 188 : 74 + Math.floor((w + 1) * 35), hot ? 48 : 16, 255);
      }
    });
    drawTile(40, (p) => {
      for (let y=0;y<16;y++) for (let x=0;x<16;x++) {
        const d = Math.hypot(x - 7.5, y - 7.5);
        const swirl = Math.sin(d * 0.9 - step * 0.6 + Math.atan2(y - 7.5, x - 7.5) * 2);
        p(x, y, 138 + swirl * 65, 28 + Math.max(0, swirl) * 55, 224 + swirl * 30, 200);
      }
    });
    tex.needsUpdate = true;
  }

  let nextModTile = 48;
  function registerAtlasTile(drawOrColor, forcedIdx = null) {
    const idx = forcedIdx !== null ? forcedIdx : nextModTile++;
    if (idx >= ATLAS_COLS * ATLAS_ROWS) return 3;
    if (typeof drawOrColor === "function") {
      drawTile(idx, drawOrColor);
    } else if (Array.isArray(drawOrColor)) {
      // Массив 256 RGBA-цветов из 2D Пиксель-редактора Студии!
      drawTile(idx, (p) => {
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
      drawTile(idx, (p, h) => {
        for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
          const b = (x === 0 || x === 15 || y === 0 || y === 15);
          const k = (h(x, y, idx + 7) % 24) - 12;
          p(x, y, (b ? rr - 28 : rr) + k, (b ? gg - 28 : gg) + k, (b ? bb - 28 : bb) + k);
        }
      });
    }
    tex.needsUpdate = true;
    return idx;
  }

  VH.registerAtlasTile = registerAtlasTile;
  return { canvas, texture: tex, updateAnimatedTextures, registerAtlasTile };
}

function drawItemIcon(canvas, id) {
  const ctx = canvas.getContext("2d");
  canvas.width = 16; canvas.height = 16;
  ctx.clearRect(0, 0, 16, 16);
  const info = ITEMS_DB[id];
  if (!info || !VH.atlasCanvas) return;
  if (typeof info.drawIcon === "function") { try { info.drawIcon(ctx); return; } catch {} }
  if (Array.isArray(info.pixelIcon)) {
    for (let i = 0; i < Math.min(256, info.pixelIcon.length); i++) {
      const c = info.pixelIcon[i];
      if (!c || c[3] === 0) continue;
      ctx.fillStyle = `rgba(${c[0]},${c[1]},${c[2]},${(c[3] ?? 255)/255})`;
      ctx.fillRect(i % 16, Math.floor(i / 16), 1, 1);
    }
    return;
  }
  if (!info.item && info.faces) {
    const f = info.faces[1];
    ctx.drawImage(VH.atlasCanvas, (f % ATLAS_COLS) * 16, Math.floor(f / ATLAS_COLS) * 16, 16, 16, 1, 1, 14, 14);
    if (!info.torch && !info.crop) {
      ctx.strokeStyle = "rgba(0,0,0,0.45)";
      ctx.strokeRect(1, 1, 14, 14);
    }
    return;
  }
  ctx.fillStyle = info.color || "#fff";
  if (info.shape === "pick") {
    ctx.fillRect(3, 2, 10, 3); ctx.fillRect(2, 4, 2, 2); ctx.fillRect(12, 4, 2, 2);
    ctx.fillStyle = "#8a5d32"; ctx.fillRect(7, 5, 2, 10);
  } else if (info.shape === "axe") {
    ctx.fillRect(4, 2, 7, 5); ctx.fillRect(3, 4, 3, 4);
    ctx.fillStyle = "#8a5d32"; ctx.fillRect(7, 5, 2, 10);
  } else if (info.shape === "sword") {
    for (let i=0;i<9;i++) ctx.fillRect(12-i, 2+i, 2, 2);
    ctx.fillStyle = "#8a5d32"; ctx.fillRect(2, 11, 4, 3);
    ctx.fillStyle = "#e8c454"; ctx.fillRect(4, 9, 4, 2);
  } else if (info.shape === "stick") {
    for (let i=0;i<10;i++) ctx.fillRect(3+i, 12-i, 2, 2);
  } else if (info.shape === "ingot") {
    ctx.fillRect(3, 6, 10, 5); ctx.fillStyle = "rgba(255,255,255,0.45)"; ctx.fillRect(4, 6, 8, 2);
  } else if (info.shape === "gem") {
    ctx.fillRect(5, 3, 6, 10); ctx.fillRect(3, 5, 10, 6);
  } else if (info.shape === "food") {
    ctx.fillRect(4, 4, 8, 8); ctx.fillRect(3, 6, 10, 4);
  } else if (info.shape === "flint") {
    ctx.fillStyle = "#8892a0"; ctx.fillRect(3, 7, 5, 6);
    ctx.fillStyle = "#ff9922"; ctx.fillRect(8, 3, 4, 6);
  } else if (info.shape === "bucket") {
    ctx.fillStyle = "#bcc4d0"; ctx.fillRect(3, 4, 10, 9);
    ctx.fillStyle = info.color; ctx.fillRect(4, 5, 8, 4);
  } else if (info.shape === "seed") {
    ctx.fillRect(4, 6, 3, 3); ctx.fillRect(9, 4, 3, 3); ctx.fillRect(7, 9, 3, 3);
  } else if (info.shape === "bow") {
    ctx.fillRect(4, 2, 2, 12); ctx.fillRect(6, 3, 2, 2); ctx.fillRect(8, 5, 2, 6); ctx.fillRect(6, 11, 2, 2);
    ctx.fillStyle = "#e8e8e8"; ctx.fillRect(3, 2, 1, 12);
  } else if (info.shape === "helmet") {
    ctx.fillRect(3, 4, 10, 7); ctx.fillRect(5, 8, 6, 3);
  } else if (info.shape === "chest") {
    ctx.fillRect(3, 3, 10, 10); ctx.fillStyle = "rgba(0,0,0,0.3)"; ctx.fillRect(6, 3, 4, 3);
  } else if (info.shape === "legs") {
    ctx.fillRect(4, 3, 8, 4); ctx.fillRect(4, 7, 3, 7); ctx.fillRect(9, 7, 3, 7);
  } else if (info.shape === "boots") {
    ctx.fillRect(3, 7, 4, 6); ctx.fillRect(9, 7, 4, 6);
  }
}

VH.ITEMS_DB = ITEMS_DB;
VH.SKINS_DB = SKINS_DB;
VH.ATLAS_COLS = ATLAS_COLS;
VH.ATLAS_ROWS = ATLAS_ROWS;
VH.drawSkinPreview = drawSkinPreview;
VH.buildTextureAtlas = buildTextureAtlas;
VH.drawItemIcon = drawItemIcon;
