// Visual effects: confetti, type-themed battle attacks, knock-out stars and
// the "collect" flight of a new card into the binder. Plain DOM + CSS/WAAPI.

const reduced = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;
const rand = (a, b) => a + Math.random() * (b - a);

const CONFETTI = ['#FFC83D', '#FF7A59', '#4CC764', '#2F9BEA', '#FF8AD8', '#8AFFD0', '#FFFFFF'];
export function confetti(n = 90) {
  if (reduced()) return;
  const box = document.createElement('div');
  box.className = 'confetti';
  for (let i = 0; i < n; i++) {
    const p = document.createElement('i');
    p.style.left = `${rand(0, 100)}%`;
    p.style.background = CONFETTI[i % CONFETTI.length];
    p.style.setProperty('--dx', `${rand(-80, 80)}px`);
    p.style.setProperty('--rot', `${rand(360, 1080)}deg`);
    p.style.animationDuration = `${rand(1.8, 3.2)}s`;
    p.style.animationDelay = `${rand(0, 0.5)}s`;
    if (i % 3 === 0) p.style.borderRadius = '50%';
    box.appendChild(p);
  }
  document.body.appendChild(box);
  setTimeout(() => box.remove(), 4000);
}

// Each affinity type gets its own attack effect.
const FX = {
  aqua: { shape: 'drop', n: 16, motion: 'splash', colors: ['#4FB3FF', '#9BDCFF', '#FFFFFF'], ring: '#4FB3FF' },
  solar: { shape: 'flame', n: 18, motion: 'rise', colors: ['#FFB01F', '#FF7A59', '#FFE27A'], ring: '#FF9F1C' },
  verdant: { shape: 'leaf', n: 12, motion: 'swirl', colors: ['#4CC764', '#9BE37A', '#2E9B45'] },
  volt: { shape: 'spark', n: 14, motion: 'burst', colors: ['#FFE94D', '#FFFFFF'], bolt: true },
  swarm: { shape: 'dot', n: 18, motion: 'buzz', colors: ['#3A2E12', '#E8C45E'] },
  frost: { shape: 'shard', n: 14, motion: 'burst', colors: ['#3FB2F0', '#8FDBFF', '#FFFFFF'], ring: '#5FC3F5' },
  feral: { shape: 'slash', n: 3, motion: 'slash', colors: ['#FF5A3D'] },
  terra: { shape: 'rock', n: 10, motion: 'fall', colors: ['#A8763E', '#7A5328', '#CDA66F'], dust: true },
  aero: { shape: 'gust', n: 6, motion: 'gust', colors: ['#FFFFFF', '#DDF5FF'] },
  toxin: { shape: 'bubble', n: 14, motion: 'rise', colors: ['#6DB33F', '#9B4DCA', '#B7E36A'] },
  umbra: { shape: 'puff', n: 10, motion: 'burst', colors: ['#3B2E5E', '#5B4A8A', '#1E1733'] },
  psi: { shape: 'ringlet', n: 4, motion: 'rings', colors: ['#FF8AD8', '#D98DDB'] },
  ancient: { shape: 'rune', n: 1, motion: 'rune', colors: ['#E9C46A'] },
};

export function typeFX(type, target, { big = false } = {}) {
  if (reduced() || !target) return;
  const f = FX[type] || FX.feral;
  const box = document.createElement('div');
  box.className = `fx fx-${f.motion}`;
  const n = Math.round(f.n * (big ? 1.6 : 1));
  for (let i = 0; i < n; i++) {
    const p = document.createElement('i');
    p.className = `fx-p ${f.shape}`;
    const a = (i / n) * Math.PI * 2 + rand(-0.3, 0.3);
    const d = rand(40, big ? 110 : 80);
    p.style.setProperty('--x', `${Math.cos(a) * d}px`);
    p.style.setProperty('--y', `${Math.sin(a) * d}px`);
    p.style.setProperty('--c', f.colors[i % f.colors.length]);
    p.style.setProperty('--r', `${rand(-180, 180)}deg`);
    p.style.setProperty('--s', rand(0.7, 1.3).toFixed(2));
    p.style.setProperty('--i', i);
    p.style.animationDelay = `${f.motion === 'slash' ? i * 0.08 : rand(0, 0.12)}s`;
    box.appendChild(p);
  }
  if (f.ring) { const r = document.createElement('b'); r.className = 'fx-ring'; r.style.setProperty('--c', f.ring); box.appendChild(r); }
  if (f.bolt) { const b = document.createElement('b'); b.className = 'fx-bolt'; box.appendChild(b); }
  if (f.dust) { const b = document.createElement('b'); b.className = 'fx-dust'; box.appendChild(b); }
  target.appendChild(box);
  setTimeout(() => box.remove(), 1300);
}

export function koStars(target) {
  if (reduced() || !target) return;
  const s = document.createElement('div');
  s.className = 'ko-stars';
  s.innerHTML = '<i>★</i><i>★</i><i>★</i>';
  target.appendChild(s);
  setTimeout(() => s.remove(), 1400);
}

export function shake(el) {
  if (reduced() || !el) return;
  el.classList.remove('shake');
  void el.offsetWidth;
  el.classList.add('shake');
}

// A freshly revealed card shrinks and flies into the Cards tab.
export function flyToBinder(imgSrc, from, tab) {
  if (!tab) return;
  const to = tab.getBoundingClientRect();
  const bump = () => {
    tab.classList.remove('got');
    void tab.offsetWidth;
    tab.classList.add('got');
    const plus = document.createElement('span');
    plus.className = 'got-plus';
    plus.textContent = '+1';
    tab.appendChild(plus);
    setTimeout(() => plus.remove(), 1000);
  };
  if (reduced() || !from) { bump(); return; }
  const w = Math.min(from.width, 180);
  const h = w * 1.4;
  const x0 = Math.max(10, Math.min(innerWidth - w - 10, from.left + (from.width - w) / 2));
  const y0 = Math.max(10, Math.min(innerHeight - h - 90, from.top));
  const el = document.createElement('div');
  el.className = 'fly-card';
  el.style.width = `${w}px`;
  el.style.height = `${h}px`;
  el.style.left = `${x0}px`;
  el.style.top = `${y0}px`;
  el.innerHTML = `<img src="${imgSrc}" alt="">`;
  document.body.appendChild(el);
  const dx = to.left + to.width / 2 - (x0 + w / 2);
  const dy = to.top + to.height / 2 - (y0 + h / 2);
  const anim = el.animate([
    { transform: 'translate(0, 0) scale(1) rotate(0deg)', opacity: 1 },
    { transform: `translate(${dx * 0.35}px, ${dy * 0.2 - 60}px) scale(.7) rotate(-8deg)`, opacity: 1, offset: 0.4 },
    { transform: `translate(${dx}px, ${dy}px) scale(.12) rotate(12deg)`, opacity: 0.6 },
  ], { duration: 750, easing: 'cubic-bezier(.5, 0, .6, 1)' });
  anim.onfinish = () => { el.remove(); bump(); };
}
