// Card battles: 3 v 3, turn based. Cards fight with the stats and affinity
// types they already have; this module is pure rules (no DOM) — the arena UI
// lives in app.js. Battles never give or take cards.

import { ENTRIES, BY_KEY } from './dex-data.js';
import { AFFINITY } from './affinity.js';
import { statsFor, levelFor, today, hash } from './game.js';

// Each type is strong against two others (×1.5); the reverse is resisted (×0.67).
export const STRONG = {
  terra: ['volt', 'toxin'],
  aqua: ['solar', 'terra'],
  aero: ['swarm', 'verdant'],
  feral: ['aero', 'swarm'],
  toxin: ['verdant', 'aqua'],
  swarm: ['verdant', 'psi'],
  frost: ['aero', 'ancient'],
  solar: ['frost', 'swarm'],
  umbra: ['psi', 'feral'],
  psi: ['feral', 'toxin'],
  verdant: ['aqua', 'terra'],
  volt: ['aqua', 'aero'],
  ancient: ['umbra', 'volt'],
};

export function mult(type, defTypes) {
  let m = 1;
  for (const d of defTypes) {
    if (STRONG[type].includes(d)) m *= 1.5;
    else if (STRONG[d].includes(type)) m *= 0.67;
  }
  return Math.max(0.5, Math.min(2.25, m));
}

export const CHARGE_MAX = 3;
export const STRIKE_POWER = 42;
export const OVERDRIVE_POWER = 78;
export const SQUAD_SIZE = 3;

export function fighter(e, level, holo = false) {
  const st = statsFor(e, level);
  const boost = holo ? 1.1 : 1; // holo cards hit a little harder
  const maxHp = Math.round((st.hp * 2 + 30) * boost);
  return {
    k: e.k, e, lv: level, holo, types: AFFINITY[e.k], maxHp, hp: maxHp,
    atk: Math.round(st.atk * boost), def: Math.round(st.def * boost), spd: st.spd, charge: 0, guard: false,
  };
}

export const active = (side) => side.team[side.i];
const alive = (side) => side.team.some((f) => f.hp > 0);
const bestType = (att, def) => att.types.slice().sort((a, b) => mult(b, def.types) - mult(a, def.types))[0];

export function newBattle(youTeam, foeTeam, meta = {}) {
  return { you: { team: youTeam, i: 0 }, foe: { team: foeTeam, i: 0 }, turn: 0, over: false, winner: null, ...meta };
}

function hit(att, def, move, rnd) {
  const type = move === 'overdrive' ? bestType(att, def) : move.split(':')[1];
  const m = mult(type, def.types);
  const crit = rnd() < 1 / 16;
  const power = move === 'overdrive' ? OVERDRIVE_POWER : STRIKE_POWER;
  let dmg = ((att.atk * power) / (def.def + 30)) * m * (0.88 + rnd() * 0.12) * (crit ? 1.5 : 1);
  if (def.guard) dmg *= 0.4;
  return { dmg: Math.max(1, Math.round(dmg)), mult: m, crit, type };
}

// The rival's choice: overdrive when charged, otherwise usually its best matchup.
export function aiMove(b, rnd = Math.random) {
  const me = active(b.foe);
  const them = active(b.you);
  if (me.charge >= CHARGE_MAX) return 'overdrive';
  if (me.hp < me.maxHp * 0.3 && rnd() < 0.2) return 'guard';
  const opts = me.types.slice().sort((a, c) => mult(c, them.types) - mult(a, them.types));
  return `strike:${rnd() < 0.8 ? opts[0] : opts[opts.length - 1]}`;
}

// Plays one turn and returns the events in order, for the UI to animate.
export function playTurn(b, youMove, rnd = Math.random) {
  if (b.over) return [];
  b.turn++;
  const events = [];
  const foeMove = aiMove(b, rnd);
  const acts = [['you', youMove], ['foe', foeMove]];
  const speed = (side, move) => (move === 'guard' ? 1000 : active(b[side]).spd + rnd() * 0.5);
  acts.sort((x, y) => speed(y[0], y[1]) - speed(x[0], x[1]));
  const actors = acts.map(([side]) => active(b[side]));

  for (let n = 0; n < acts.length; n++) {
    const [side, move] = acts[n];
    const other = side === 'you' ? 'foe' : 'you';
    const me = actors[n];
    if (me.hp <= 0 || active(b[side]) !== me) continue; // fainted before acting
    const target = active(b[other]);
    if (move === 'guard') {
      me.guard = true;
      me.charge = Math.min(CHARGE_MAX, me.charge + 1);
      events.push({ t: 'guard', side, name: me.e.n });
      continue;
    }
    if (move === 'overdrive' && me.charge < CHARGE_MAX) continue;
    const h = hit(me, target, move, rnd);
    if (move === 'overdrive') me.charge = 0;
    else me.charge = Math.min(CHARGE_MAX, me.charge + 1);
    if (!target.guard) target.charge = Math.min(CHARGE_MAX, target.charge + 1);
    target.hp = Math.max(0, target.hp - h.dmg);
    events.push({ t: 'attack', side, name: me.e.n, move, ...h, target: other, hp: target.hp, maxHp: target.maxHp });
    if (target.hp <= 0) {
      events.push({ t: 'faint', side: other, name: target.e.n });
      if (!alive(b[other])) {
        b.over = true;
        b.winner = side;
        events.push({ t: 'end', winner: side });
        break;
      }
    }
  }
  for (const s of ['you', 'foe']) {
    active(b[s]).guard = false;
    if (!b.over && active(b[s]).hp <= 0) {
      b[s].i = b[s].team.findIndex((f) => f.hp > 0);
      events.push({ t: 'enter', side: s, name: active(b[s]).e.n, i: b[s].i });
    }
  }
  return events;
}

// ---------------------------------------------------------------- squads & opponents
export const owned = (state) => Object.keys(state.caught).filter((k) => BY_KEY[k]);
const lvOf = (state, k) => levelFor(state.caught[k].count);
export const pwrOf = (state, k) => statsFor(BY_KEY[k], lvOf(state, k)).pwr * (state.caught[k].holo ? 1.1 : 1);

export function battleState(state) {
  if (!state.battle) state.battle = { squad: [], rival: null, sims: null, wins: 0, losses: 0, rivals: 0 };
  return state.battle;
}

// The saved squad (cards still owned), topped up with the strongest cards.
export function squad(state) {
  const b = battleState(state);
  const mine = owned(state);
  const keys = b.squad.filter((k) => mine.includes(k)).slice(0, SQUAD_SIZE);
  const rest = mine.filter((k) => !keys.includes(k)).sort((x, y) => pwrOf(state, y) - pwrOf(state, x));
  while (keys.length < Math.min(SQUAD_SIZE, mine.length)) keys.push(rest.shift());
  return keys;
}
export const bestSquad = (state) => owned(state).sort((x, y) => pwrOf(state, y) - pwrOf(state, x)).slice(0, SQUAD_SIZE);
export const squadTeam = (state, keys = squad(state)) => keys.map((k) => fighter(BY_KEY[k], lvOf(state, k), !!state.caught[k].holo));

function squadLevel(state) {
  const keys = squad(state);
  if (!keys.length) return 1;
  return Math.max(1, Math.round(keys.reduce((a, k) => a + lvOf(state, k), 0) / keys.length));
}
const rarityCap = (n) => (n < 6 ? 1 : n < 15 ? 2 : n < 40 ? 3 : 4);

// Picks `count` distinct cards, preferring different primary types.
function pickTeam(count, cap, rnd) {
  const pool = ENTRIES.filter((e) => e.r <= cap);
  const out = [];
  let guard = 0;
  while (out.length < count && guard++ < 200) {
    const e = pool[Math.floor(rnd() * pool.length)];
    if (out.includes(e)) continue;
    if (guard < 100 && out.some((o) => AFFINITY[o.k][0] === AFFINITY[e.k][0])) continue;
    out.push(e);
  }
  return out;
}
function seeded(seed) {
  let x = seed >>> 0 || 1;
  return () => { x ^= x << 13; x >>>= 0; x ^= x >>> 17; x ^= x << 5; x >>>= 0; return x / 4294967296; };
}

const CALLSIGNS = ['VEX', 'NOVA', 'KODA', 'RAZE', 'ONYX', 'LYRA', 'JUNO', 'HEX', 'MIRA', 'ZED', 'ASH', 'RUNE', 'SABLE', 'ORIN'];
export const RIVAL_REWARD = { credits: 60, xp: 50 };
export const SIM_REWARD = { credits: 12, xp: 15 };
export const SIM_PAID = 5;
export const LOSS_XP = 5;

// Today's rival: fixed for the day once generated (so levelling up mid-day doesn't reshuffle it).
export function dailyRival(state) {
  const b = battleState(state);
  const t = today();
  if (!b.rival || b.rival.date !== t) {
    const rnd = seeded(hash(`rival:${t}:${state.opId || ''}`));
    const lv = squadLevel(state);
    const team = pickTeam(SQUAD_SIZE, rarityCap(owned(state).length), rnd);
    const name = `${CALLSIGNS[Math.floor(rnd() * CALLSIGNS.length)]}-${10 + Math.floor(rnd() * 90)}`;
    b.rival = { date: t, name, keys: team.map((e) => e.k), lvs: team.map((_, i) => Math.min(10, lv + (i === 2 ? 1 : 0))), won: false, tries: 0 };
  }
  return b.rival;
}
export const rivalTeam = (r) => r.keys.map((k, i) => fighter(BY_KEY[k], r.lvs[i]));

export function simsToday(state) {
  const b = battleState(state);
  if (!b.sims || b.sims.date !== today()) b.sims = { date: today(), paid: 0 };
  return b.sims;
}
export function wildTeam(state, size) {
  const lv = squadLevel(state);
  const team = pickTeam(size, rarityCap(owned(state).length), Math.random);
  return team.map((e) => fighter(e, Math.max(1, Math.min(10, lv + Math.floor(Math.random() * 3) - 1))));
}

// Rewards for a finished battle; mutates battle stats and returns what was earned.
export function settle(state, kind, won) {
  const b = battleState(state);
  if (won) b.wins++; else b.losses++;
  if (kind === 'rival') {
    const r = dailyRival(state);
    r.tries++;
    if (won && !r.won) { r.won = true; b.rivals++; return { ...RIVAL_REWARD }; }
    return { credits: 0, xp: won ? 10 : LOSS_XP };
  }
  const s = simsToday(state);
  if (won && s.paid < SIM_PAID) { s.paid++; return { ...SIM_REWARD }; }
  return { credits: 0, xp: won ? 5 : LOSS_XP };
}
