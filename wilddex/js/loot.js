// Supply crates: bought with credits (or earned by levelling up) and full of
// cosmetics only — card frames and operator titles. Animals can never come
// out of a crate; those still have to be scanned for real.

export const TIERS = {
  1: { name: 'Common', color: '#B4B4BC' },
  2: { name: 'Rare', color: '#B9A0F0' },
  3: { name: 'Epic', color: '#5FB6DA' },
  4: { name: 'Legendary', color: '#E9C46A' },
};

export const FRAMES = {
  standard: { name: 'Standard', r: 0, desc: 'Factory issue' },
  circuit: { name: 'Circuit', r: 1, desc: 'Printed traces' },
  topo: { name: 'Topo', r: 1, desc: 'Contour lines' },
  neon: { name: 'Neon', r: 2, desc: 'Lavender glow tube' },
  sakura: { name: 'Sakura', r: 2, desc: 'Butterfly pink' },
  aurora: { name: 'Aurora', r: 3, desc: 'Shifting sky light' },
  glitch: { name: 'Glitch', r: 3, desc: 'RGB split signal' },
  obsidian: { name: 'Obsidian', r: 4, desc: 'Black glass, gold edge' },
};

export const TITLES = {
  rookie: { name: 'Field Rookie', r: 0 },
  puddle: { name: 'Puddle Scout', r: 1 },
  bugmag: { name: 'Bug Magnet', r: 1 },
  moth: { name: 'Moth Mechanic', r: 1 },
  fox: { name: 'Fox Friend', r: 1 },
  signal: { name: 'Signal Chaser', r: 1 },
  owl: { name: 'Owl Whisperer', r: 2 },
  night: { name: 'Night Stalker', r: 2 },
  beetle: { name: 'Beetle Baron', r: 2 },
  tide: { name: 'Tide Reader', r: 2 },
  storm: { name: 'Storm Caller', r: 3 },
  apex: { name: 'Apex Operator', r: 3 },
  wild: { name: 'Wild Card', r: 3 },
  relic: { name: 'Relic Keeper', r: 4 },
};

export const CRATE_COST = 60;
export const DUPE_REFUND = 20;
const WEIGHTS = { 1: 60, 2: 28, 3: 10, 4: 2 };

export function locker(state) {
  if (!state.locker) state.locker = { frames: ['standard'], titles: ['rookie'], frame: 'standard', title: 'rookie', crates: 0, opened: 0 };
  return state.locker;
}

export const frameId = (state) => (state.locker && FRAMES[state.locker.frame] ? state.locker.frame : 'standard');
export const titleName = (state) => TITLES[state.locker?.title]?.name || TITLES.rookie.name;

// Opens one crate. `free` uses an earned crate, otherwise it costs credits.
export function openCrate(state, { free = false, rnd = Math.random } = {}) {
  const l = locker(state);
  if (free) { if (l.crates < 1) return null; l.crates--; } else {
    if ((state.shards || 0) < CRATE_COST) return null;
    state.shards -= CRATE_COST;
  }
  l.opened++;
  let roll = rnd() * 100;
  let tier = 1;
  for (const [t, w] of Object.entries(WEIGHTS)) { if (roll < w) { tier = Number(t); break; } roll -= w; }
  const pool = [
    ...Object.entries(FRAMES).filter(([, v]) => v.r === tier).map(([id, v]) => ({ kind: 'frame', id, ...v })),
    ...Object.entries(TITLES).filter(([, v]) => v.r === tier).map(([id, v]) => ({ kind: 'title', id, ...v })),
  ];
  const item = pool[Math.floor(rnd() * pool.length)];
  const list = item.kind === 'frame' ? l.frames : l.titles;
  const dupe = list.includes(item.id);
  if (dupe) state.shards = (state.shards || 0) + DUPE_REFUND;
  else list.push(item.id);
  return { ...item, dupe };
}
