// ============================================================================
// VOXELHORIZON — 3D МОБЫ (МИРНЫЕ И НОЧНЫЕ ВРАЖДЕБНЫЕ) + ИИ И БОЁВКА
// ============================================================================
window.VH = window.VH || {};

class MobManager {
  constructor(scene, world) {
    this.scene = scene;
    this.world = world;
    this.mobs = [];
    this.spawnTimer = 0;
  }

  createMobMesh(type) {
    const g = new THREE.Group();
    const mkBox = (w, h, d, col, x, y, z) => {
      const m = new THREE.Mesh(
        new THREE.BoxGeometry(w, h, d),
        new THREE.MeshLambertMaterial({ color: col })
      );
      m.position.set(x, y, z);
      g.add(m);
      return m;
    };

    if (type === "sheep") {
      mkBox(0.9, 0.7, 1.2, 0xf2f4f8, 0, 0.85, 0);
      mkBox(0.55, 0.55, 0.65, 0x3b3532, 0, 1.05, 0.75);
      mkBox(0.22, 0.5, 0.22, 0x3b3532, -0.28, 0.25, -0.35);
      mkBox(0.22, 0.5, 0.22, 0x3b3532,  0.28, 0.25, -0.35);
      mkBox(0.22, 0.5, 0.22, 0x3b3532, -0.28, 0.25,  0.35);
      mkBox(0.22, 0.5, 0.22, 0x3b3532,  0.28, 0.25,  0.35);
    } else if (type === "pig") {
      mkBox(0.85, 0.65, 1.15, 0xf29cb0, 0, 0.75, 0);
      mkBox(0.55, 0.55, 0.55, 0xf5a8ba, 0, 0.85, 0.72);
      mkBox(0.28, 0.22, 0.15, 0xd8748a, 0, 0.76, 1.02);
      mkBox(0.22, 0.42, 0.22, 0xe4869c, -0.25, 0.21, -0.35);
      mkBox(0.22, 0.42, 0.22, 0xe4869c,  0.25, 0.21, -0.35);
      mkBox(0.22, 0.42, 0.22, 0xe4869c, -0.25, 0.21,  0.35);
      mkBox(0.22, 0.42, 0.22, 0xe4869c,  0.25, 0.21,  0.35);
    } else if (type === "stalker") {
      // Ночной враждебный моб (зелёно-тёмный гуманоид с жёлтыми глазами)
      mkBox(0.55, 0.75, 0.35, 0x2f5a44, 0, 1.1, 0);
      mkBox(0.5, 0.5, 0.5, 0x4b7d54, 0, 1.72, 0);
      mkBox(0.1, 0.08, 0.05, 0xffee44, -0.12, 1.76, 0.26);
      mkBox(0.1, 0.08, 0.05, 0xffee44,  0.12, 1.76, 0.26);
      mkBox(0.22, 0.72, 0.22, 0x4b7d54, -0.4, 1.15, 0.25);
      mkBox(0.22, 0.72, 0.22, 0x4b7d54,  0.4, 1.15, 0.25);
      mkBox(0.24, 0.72, 0.24, 0x26344a, -0.15, 0.36, 0);
      mkBox(0.24, 0.72, 0.24, 0x26344a,  0.15, 0.36, 0);
    }
    return g;
  }

  spawnMob(type, x, y, z) {
    const mesh = this.createMobMesh(type);
    mesh.position.set(x, y, z);
    this.scene.add(mesh);
    const hostile = type === "stalker";
    this.mobs.push({
      type,
      hostile,
      mesh,
      x, y, z,
      vx: 0, vy: 0, vz: 0,
      hp: hostile ? 20 : 10,
      maxHp: hostile ? 20 : 10,
      dirAngle: Math.random() * Math.PI * 2,
      aiTimer: 0,
      atkCd: 0,
      hurtFlash: 0
    });
  }

  hitMobAtRay(origin, dir, maxDist, dmg, onKill) {
    let best = null, bestD = maxDist;
    for (const m of this.mobs) {
      const cx = m.x, cy = m.y + 0.9, cz = m.z;
      const toX = cx - origin.x, toY = cy - origin.y, toZ = cz - origin.z;
      const proj = toX * dir.x + toY * dir.y + toZ * dir.z;
      if (proj < 0 || proj > bestD) continue;
      const px = origin.x + dir.x * proj - cx;
      const py = origin.y + dir.y * proj - cy;
      const pz = origin.z + dir.z * proj - cz;
      if (px * px + py * py + pz * pz <= 0.72) {
        bestD = proj;
        best = m;
      }
    }
    if (!best) return false;
    best.hp -= dmg;
    best.hurtFlash = 0.2;
    best.vx += dir.x * 6;
    best.vz += dir.z * 6;
    best.vy = 4.5;
    if (best.hp <= 0) {
      this.scene.remove(best.mesh);
      this.mobs = this.mobs.filter((x) => x !== best);
      if (onKill) onKill(best);
    }
    return true;
  }

  update(dt, player, isNight, onPlayerHurt) {
    this.spawnTimer -= dt;
    if (this.spawnTimer <= 0 && this.mobs.length < 10) {
      this.spawnTimer = 4.5;
      const ang = Math.random() * Math.PI * 2;
      const dist = 14 + Math.random() * 14;
      const sx = Math.floor(player.x + Math.cos(ang) * dist);
      const sz = Math.floor(player.z + Math.sin(ang) * dist);
      // Находим поверхность
      for (let sy = VH.WORLD_H - 3; sy > VH.SEA_LEVEL; sy--) {
        const b = this.world.getBlock(sx, sy, sz);
        const info = VH.ITEMS_DB[b];
        if (info && info.solid) {
          const type = isNight && Math.random() < 0.65 ? "stalker" : (Math.random() < 0.5 ? "sheep" : "pig");
          this.spawnMob(type, sx + 0.5, sy + 1.1, sz + 0.5);
          break;
        }
      }
    }

    for (let i = this.mobs.length - 1; i >= 0; i--) {
      const m = this.mobs[i];
      const distP = Math.hypot(player.x - m.x, player.z - m.z);
      if (distP > 48 || m.y < -5) {
        this.scene.remove(m.mesh);
        this.mobs.splice(i, 1);
        continue;
      }

      m.aiTimer -= dt;
      m.atkCd = Math.max(0, m.atkCd - dt);

      if (m.hostile && distP < 16 && player.mode === "survival") {
        const dx = player.x - m.x, dz = player.z - m.z;
        m.dirAngle = Math.atan2(dx, dz);
        const spd = 2.6;
        m.vx += (Math.sin(m.dirAngle) * spd - m.vx) * Math.min(1, dt * 6);
        m.vz += (Math.cos(m.dirAngle) * spd - m.vz) * Math.min(1, dt * 6);
        if (distP < 1.6 && Math.abs(player.y - m.y) < 1.8 && m.atkCd <= 0) {
          m.atkCd = 1.1;
          if (onPlayerHurt) onPlayerHurt(3);
        }
      } else {
        if (m.aiTimer <= 0) {
          m.aiTimer = 2 + Math.random() * 3;
          m.dirAngle = Math.random() * Math.PI * 2;
        }
        const spd = m.hostile ? 1.4 : 1.1;
        m.vx += (Math.sin(m.dirAngle) * spd - m.vx) * Math.min(1, dt * 4);
        m.vz += (Math.cos(m.dirAngle) * spd - m.vz) * Math.min(1, dt * 4);
      }

      // Гравитация и простое преодоление ступенек
      m.vy -= 18 * dt;
      const nx = m.x + m.vx * dt;
      const nz = m.z + m.vz * dt;
      const blockAhead = this.world.getBlock(nx, m.y + 0.2, nz);
      if (VH.ITEMS_DB[blockAhead]?.solid) {
        if (!VH.ITEMS_DB[this.world.getBlock(nx, m.y + 1.2, nz)]?.solid && m.vy <= 0.1) {
          m.vy = 6.2; // Прыжок на блок!
        } else {
          m.dirAngle += Math.PI * 0.7;
        }
      } else {
        m.x = nx; m.z = nz;
      }

      const ny = m.y + m.vy * dt;
      if (m.vy <= 0 && VH.ITEMS_DB[this.world.getBlock(m.x, ny, m.z)]?.solid) {
        m.y = Math.floor(ny) + 1.0;
        m.vy = 0;
      } else {
        m.y = ny;
      }

      m.mesh.position.set(m.x, m.y, m.z);
      m.mesh.rotation.y = m.dirAngle;

      if (m.hurtFlash > 0) {
        m.hurtFlash -= dt;
        m.mesh.scale.set(1.08, 0.92, 1.08);
      } else {
        m.mesh.scale.set(1, 1, 1);
      }
    }
  }
}

VH.MobManager = MobManager;
