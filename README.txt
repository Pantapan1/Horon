====================================================
  VOXELHORIZON v2.1 — ПОЛНЫЙ MODDING ENGINE (APK)
  Плоский архив без папок — готов к сборке в GitHub!
====================================================

КАК СОБРАТЬ APK В GITHUB:
1. Загрузите все файлы из архива в корень репозитория:
   index.html, style.css, textures.js, world.js, entities.js,
   multiplayer_mods.js, game.js, package.json, capacitor.config.json, README.txt
2. Создайте .github/workflows/build-apk.yml из файла build-apk.yml.
3. Во вкладке Actions скачайте готовый app-debug.apk!

ПОЛНЫЙ ДОСТУП ДЛЯ МОДОВ В v2.1 (VH.Mods + VH.Engine + THREE):
Моды получают НЕОГРАНИЧЕННЫЙ доступ ко всей игре:
- VH.Engine: прямой доступ к THREE, scene, camera, renderer, world, player,
  mobMgr, mpMgr, physics (gravity, jumpVelocity, walkSpeed, timeScale),
  ITEMS_DB, SKINS_DB, CRAFT_RECIPES, SMELT_RECIPES, setBlock, explodeAt и др.
- VH.Mods.registerBlock(id, { name, color/draw, onStep, onInteract, onBreak, onPlace })
- VH.Mods.registerItem(id, { name, color/drawIcon, dmg, onUse })
- VH.Mods.registerMob(type, { hp, dmg, hostile, flying, buildMesh, onTick })
- VH.Mods.registerDimension(id, { name, skyColor, fogColor, gravity, generateColumn })
- VH.Mods.registerCommand(name, fn) / registerRecipe / registerSmelt
- VH.Mods.ui.addHudButton / addHudWidget / showModal / closeModal
- VH.Mods.on(event, fn): onInit, onWorldStart, onTick, onBlockBreak, onBlockPlace,
  onBlockInteract, onItemUse, onMobSpawn, onPlayerHurt, onDimensionChange, onChat, onNetPacket
- VH.Mods.storage.get / set (сохранение данных мода) + Импорт .js файлов с телефона!
