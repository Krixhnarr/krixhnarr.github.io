// Collectible-game layer: card levels, battle stats, data shards, daily
// missions and scan streaks. Stats are game values (not biology) derived
// deterministically from rarity + affinity so every player sees the same card.

import { ENTRIES, BY_KEY, SETS } from './dex-data.js';
import { AFFINITY, TYPES } from './affinity.js';

export const SHARD = '◆';
export const DECRYPT_COST = 40;

// ---- card level: sightings 1,2,4,8,16... -> Lv 1,2,3,4,5 ... max 10
export const MAX_LEVEL = 10;
export function levelFor(count) {
  return Math.min(MAX_LEVEL, 1 + Math.floor(Math.log2(Math.max(1, count))));
}
export function nextLevelAt(level) { return level >= MAX_LEVEL ? null : 2 ** level; }

// ---- stats
const BASE = { 1: 34, 2: 44, 3: 54, 4: 68 };
const MOD = {
  terra: { hp: 10, def: 10 },
  aqua: { hp: 10, spd: 4 },
  aero: { spd: 16 },
  feral: { atk: 16 },
  toxin: { atk: 10, def: 4 },
  swarm: { spd: 10, hp: -8 },
  frost: { def: 10, hp: 4 },
  solar: { atk: 6, hp: 6 },
  umbra: { spd: 6, atk: 6 },
  psi: { def: 6, spd: 6 },
  verdant: { hp: 12 },
  volt: { spd: 16, atk: 4 },
  ancient: { def: 16, hp: 8 },
};
export function hash(str) {
  let h = 2166136261;
  for (let i = 0; i < str.length; i++) { h ^= str.charCodeAt(i); h = Math.imul(h, 16777619); }
  return h >>> 0;
}
export const STAT_KEYS = ['hp', 'atk', 'def', 'spd'];
export function statsFor(e, level = 1) {
  const h = hash(e.k);
  const out = {};
  STAT_KEYS.forEach((s, i) => {
    let v = BASE[e.r] + (((h >>> (i * 8)) & 255) % 17) - 8;
    AFFINITY[e.k].forEach((t, j) => { v += Math.round((MOD[t][s] || 0) * (j === 0 ? 1 : 0.6)); });
    v += (level - 1) * 3;
    out[s] = Math.max(8, Math.min(99, v));
  });
  out.pwr = out.hp + out.atk + out.def + out.spd;
  return out;
}

// ---- operator XP: level L starts at 20·(L-1)² XP
export const levelForXP = (xp) => 1 + Math.floor(Math.sqrt(Math.max(0, xp) / 20));
export const xpForLevel = (level) => 20 * (level - 1) ** 2;
// Adds XP and returns the levels crossed; the caller hands out level-up rewards.
export function grantXP(state, amount) {
  const before = levelForXP(state.xp || 0);
  state.xp = (state.xp || 0) + Math.max(0, Math.round(amount));
  return { before, after: levelForXP(state.xp), gained: amount };
}
export const levelUpCredits = (level) => 10 * level;
export const SIGHTING_XP = 5;
export const MISSION_XP = 10;
export const EVENT_XP = 40;

// ---- sync grade: how cleanly the lock-on sweep went (confidence, depth, motion)
export const GRADES = {
  S: { credits: 20, xp: 15, name: 'Perfect sync' },
  A: { credits: 12, xp: 10, name: 'Strong sync' },
  B: { credits: 6, xp: 5, name: 'Good sync' },
  C: { credits: 0, xp: 0, name: 'Weak sync' },
};
export function syncGrade(conf, depth) {
  const c = Math.min(1, (conf || 0) / 0.8);
  const d = depth && depth.tracks ? Math.min(1, (depth.parallax || 0) / depth.tracks / 0.25) : 0.4;
  const range = depth ? depth.range || 0 : 0;
  const m = !depth ? 0.5 : range < 12 ? range / 12 : range > 90 ? Math.max(0.4, 1 - (range - 90) / 90) : 1;
  const q = 0.45 * c + 0.35 * d + 0.2 * m;
  const grade = q >= 0.85 ? 'S' : q >= 0.7 ? 'A' : q >= 0.5 ? 'B' : 'C';
  return { grade, q: +q.toFixed(2), ...GRADES[grade] };
}

// ---- holo (shiny) variants: rare foil pulls, better odds on a perfect sync
export const HOLO_ODDS = 40;
export const holoChance = (grade) => (grade === 'S' ? 2 : 1) / HOLO_ODDS;
export const HOLO_DUPE_CREDITS = 30;

// ---- card stars: half a star per level, five stars at Lv 10
export function starsHTML(level) {
  return Array.from({ length: 5 }, (_, i) => {
    const f = Math.max(0, Math.min(2, level - i * 2));
    return `<i class="star" style="--f:${f * 50}%"></i>`;
  }).join('');
}

// ---- dates
export const today = (d = new Date()) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const yesterday = () => { const d = new Date(); d.setDate(d.getDate() - 1); return today(d); };

// ---- streak: returns bonus shards when today's first sighting extends it
export function touchStreak(state) {
  const s = state.streak || (state.streak = { last: null, days: 0, best: 0 });
  const t = today();
  if (s.last === t) return { bonus: 0, days: s.days, extended: false };
  s.days = s.last === yesterday() ? s.days + 1 : 1;
  s.last = t;
  s.best = Math.max(s.best || 0, s.days);
  return { bonus: Math.min(50, 5 * s.days), days: s.days, extended: true };
}
export function liveStreak(state) {
  const s = state.streak;
  if (!s || !s.last) return 0;
  return s.last === today() || s.last === yesterday() ? s.days : 0;
}

// ---- daily missions (same three for everyone on a given day)
const EASY_TYPES = ['terra', 'aero', 'swarm', 'aqua', 'verdant', 'feral'];
const EASY_SETS = ['home', 'farm', 'backyard', 'bugs'];
function makeMissions(dateStr) {
  const h = hash(dateStr);
  const type = EASY_TYPES[h % EASY_TYPES.length];
  const set = SETS.find((s) => s.id === EASY_SETS[(h >>> 5) % EASY_SETS.length]);
  const pool = [
    { id: 'log3', text: 'Log 3 sightings', goal: 3, reward: 20 },
    { id: 'new1', text: 'Register a new species', goal: 1, reward: 40 },
    { id: `type:${type}`, text: `Scan a ${TYPES[type].name}-type animal`, goal: 1, reward: 25, type },
    { id: 'rare', text: 'Scan an Uncommon or rarer animal', goal: 1, reward: 30 },
    { id: 'repeat', text: 'Re-scan a card you already own', goal: 1, reward: 15 },
    { id: `set:${set.id}`, text: `Scan something from ${set.name}`, goal: 1, reward: 20, set: set.id },
  ];
  // Always one "new species" order, plus two others picked by the date.
  const others = pool.filter((m) => m.id !== 'new1');
  const a = others[(h >>> 9) % others.length];
  const rest = others.filter((m) => m !== a);
  const b = rest[(h >>> 13) % rest.length];
  return [pool[1], a, b];
}
export const ALL_CLEAR_BONUS = 30;

export function dailyState(state) {
  const t = today();
  if (!state.daily || state.daily.date !== t) state.daily = { date: t, progress: {}, claimed: {}, bonus: false };
  return { ...state.daily, missions: makeMissions(t) };
}

// Called after each successful registration; returns missions that just completed.
export function progressMissions(state, { entry, isNew }) {
  const d = dailyState(state);
  const done = [];
  for (const m of d.missions) {
    let hit = false;
    if (m.id === 'log3') hit = true;
    else if (m.id === 'new1') hit = isNew;
    else if (m.id === 'rare') hit = entry.r >= 2;
    else if (m.id === 'repeat') hit = !isNew;
    else if (m.type) hit = AFFINITY[entry.k].includes(m.type);
    else if (m.set) hit = entry.set === m.set;
    if (!hit) continue;
    const before = state.daily.progress[m.id] || 0;
    if (before >= m.goal) continue;
    state.daily.progress[m.id] = before + 1;
    if (before + 1 >= m.goal) done.push(m);
  }
  return done;
}

export function claimMission(state, id) {
  const d = dailyState(state);
  const m = d.missions.find((x) => x.id === id);
  if (!m || state.daily.claimed[id] || (state.daily.progress[id] || 0) < m.goal) return 0;
  state.daily.claimed[id] = true;
  let gained = m.reward;
  if (!state.daily.bonus && d.missions.every((x) => state.daily.claimed[x.id])) {
    state.daily.bonus = true;
    gained += ALL_CLEAR_BONUS;
  }
  state.shards = (state.shards || 0) + gained;
  return gained;
}

// ---- weekly events: a themed challenge that rotates every ISO week
const EVENTS = [
  { id: 'monsoon', name: 'Monsoon Week', type: 'aqua', text: 'Log 5 Aqua-type sightings' },
  { id: 'bugs', name: 'Bug Week', type: 'swarm', text: 'Log 5 Swarm-type sightings' },
  { id: 'sky', name: 'Sky Watch', type: 'aero', text: 'Log 5 Aero-type sightings' },
  { id: 'green', name: 'Green Week', type: 'verdant', text: 'Log 4 Verdant-type sightings', goal: 4 },
  { id: 'night', name: 'Night Watch', type: 'umbra', text: 'Log 3 Umbra-type sightings', goal: 3 },
  { id: 'hunt', name: 'Predator Week', type: 'feral', text: 'Log 4 Feral-type sightings', goal: 4 },
  { id: 'farm', name: 'Farm Fair', set: 'farm', text: 'Log 5 sightings from Farm & Country' },
  { id: 'yard', name: 'Backyard Blitz', set: 'backyard', text: 'Log 5 sightings from Backyard & Park' },
  { id: 'pets', name: 'Pet Parade', set: 'home', text: 'Log 5 sightings from Home & Pets' },
];
export const EVENT_REWARD = 80;

function isoWeek(d = new Date()) {
  const t = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
  const day = t.getUTCDay() || 7;
  t.setUTCDate(t.getUTCDate() + 4 - day);
  const y0 = new Date(Date.UTC(t.getUTCFullYear(), 0, 1));
  return { year: t.getUTCFullYear(), week: Math.ceil(((t - y0) / 86400000 + 1) / 7), daysLeft: 8 - (d.getDay() || 7) };
}

export function weeklyEvent(state) {
  const { year, week, daysLeft } = isoWeek();
  const key = `${year}-W${week}`;
  const ev = EVENTS[(year * 53 + week) % EVENTS.length];
  if (!state.event || state.event.key !== key) state.event = { key, progress: 0, claimed: false };
  return { ...ev, goal: ev.goal || 5, reward: EVENT_REWARD, key, daysLeft, progress: state.event.progress, claimed: state.event.claimed };
}

// Returns true when this sighting completes the week's event.
export function progressEvent(state, entry) {
  const ev = weeklyEvent(state);
  if (ev.claimed || ev.progress >= ev.goal) return false;
  const hit = ev.type ? AFFINITY[entry.k].includes(ev.type) : entry.set === ev.set;
  if (!hit) return false;
  state.event.progress++;
  return state.event.progress >= ev.goal;
}

export function claimEvent(state) {
  const ev = weeklyEvent(state);
  if (ev.claimed || ev.progress < ev.goal) return 0;
  state.event.claimed = true;
  state.eventsWon = (state.eventsWon || 0) + 1;
  state.shards = (state.shards || 0) + EVENT_REWARD;
  return EVENT_REWARD;
}

// ---- achievements (computed, never stored)
export function achievements(state) {
  const caught = Object.keys(state.caught).filter((k) => BY_KEY[k]);
  const byType = {};
  for (const k of caught) for (const t of AFFINITY[k]) byType[t] = (byType[t] || 0) + 1;
  const maxLevel = caught.reduce((m, k) => Math.max(m, levelFor(state.caught[k].count)), 0);
  const list = [
    { id: 'first', name: 'First Contact', desc: 'Register your first animal', done: caught.length >= 1 },
    { id: 'ten', name: 'Field Agent', desc: 'Register 10 species', done: caught.length >= 10 },
    { id: 'fifty', name: 'Archivist', desc: 'Register 50 species', done: caught.length >= 50 },
    { id: 'rare', name: 'Rare Signal', desc: 'Register a Rare animal', done: caught.some((k) => BY_KEY[k].r >= 3) },
    { id: 'legend', name: 'Relic Hunter', desc: 'Register a Legendary', done: caught.some((k) => BY_KEY[k].r === 4) },
    { id: 'level5', name: 'Bonded', desc: 'Raise a card to Lv 5', done: maxLevel >= 5 },
    { id: 'types', name: 'Spectrum', desc: 'Own every affinity type', done: Object.keys(TYPES).every((t) => byType[t]) },
    { id: 'streak7', name: 'Dedicated', desc: 'Reach a 7-day streak', done: (state.streak?.best || 0) >= 7 },
    { id: 'sector', name: 'Sector Secured', desc: 'Complete any sector', done: SETS.some((s) => s.entries.every((e) => state.caught[e.k])) },
    { id: 'event', name: 'Event Champion', desc: 'Complete a weekly event', done: (state.eventsWon || 0) >= 1 },
    { id: 'social', name: 'Field Partner', desc: 'Compare with a friend', done: (state.compared || 0) >= 1 },
    { id: 'mapper', name: 'Cartographer', desc: 'Tag 10 capture locations', done: caught.filter((k) => state.caught[k].loc).length >= 10 },
    { id: 'holo', name: 'Holo Hunter', desc: 'Pull a holo card', done: caught.some((k) => state.caught[k].holo) },
    { id: 'victory', name: 'First Victory', desc: 'Win a battle', done: (state.battle?.wins || 0) >= 1 },
    { id: 'rivals', name: 'Rival Slayer', desc: 'Defeat 5 daily rivals', done: (state.battle?.rivals || 0) >= 5 },
    { id: 'perfect', name: 'Perfect Sync', desc: 'Get an S grade on a capture', done: (state.bestGrade || '') === 'S' },
    { id: 'stylist', name: 'Stylist', desc: 'Unlock a new card frame', done: (state.locker?.frames?.length || 0) > 1 },
  ];
  return { list, byType };
}

export const TOTAL = ENTRIES.length;
