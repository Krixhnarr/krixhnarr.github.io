// LED dot-matrix headings. Each character is a 5×7 grid of dots drawn as
// SVG circles (with faint "off" dots behind, like a real LED board), so
// individual dots can flicker at random.

const G = {
  A: '01110100011000111111100011000110001', B: '11110100011000111110100011000111110',
  C: '01110100011000010000100001000101110', D: '11110100011000110001100011000111110',
  E: '11111100001000011110100001000011111', F: '11111100001000011110100001000010000',
  G: '01110100011000010111100011000101111', H: '10001100011000111111100011000110001',
  I: '01110001000010000100001000010001110', J: '00111000100001000010000101001001100',
  K: '10001100101010011000101001001010001', L: '10000100001000010000100001000011111',
  M: '10001110111010110101100011000110001', N: '10001100011100110101100111000110001',
  O: '01110100011000110001100011000101110', P: '11110100011000111110100001000010000',
  Q: '01110100011000110001101011001001101', R: '11110100011000111110101001001010001',
  S: '01111100001000001110000010000111110', T: '11111001000010000100001000010000100',
  U: '10001100011000110001100011000101110', V: '10001100011000110001100010101000100',
  W: '10001100011000110101101011010101010', X: '10001100010101000100010101000110001',
  Y: '10001100010101000100001000010000100', Z: '11111000010001000100010001000011111',
  0: '01110100011001110101110011000101110', 1: '00100011000010000100001000010001110',
  2: '01110100010000100010001000100011111', 3: '11111000100010000010000011000101110',
  4: '00010001100101010010111110001000010', 5: '11111100001111000001000011000101110',
  6: '00110010001000011110100011000101110', 7: '11111000010001000100010000100001000',
  8: '01110100011000101110100011000101110', 9: '01110100011000101111000010001001100',
  '-': '00000000000000011111000000000000000', '.': '00000000000000000000000000110001100',
  '/': '00001000010001000100010001000010000', '_': '00000000000000000000000000000011111',
};
const COLS = 5;
const ROWS = 7;

export function dotSVG(text, { off = true } = {}) {
  const chars = [...String(text).toUpperCase()];
  let x = 0;
  const dots = [];
  for (const ch of chars) {
    if (ch === ' ') { x += 3; continue; }
    const g = G[ch] || G['-'];
    for (let r = 0; r < ROWS; r++) {
      for (let c = 0; c < COLS; c++) {
        const lit = g[r * COLS + c] === '1';
        if (lit || off) dots.push(`<circle class="${lit ? 'on' : 'off'}" cx="${x + c + 0.5}" cy="${r + 0.5}" r="${lit ? 0.4 : 0.3}"/>`);
      }
    }
    x += COLS + 1;
  }
  const w = Math.max(1, x - 1);
  return `<svg viewBox="0 0 ${w} ${ROWS}" aria-hidden="true" focusable="false">${dots.join('')}</svg>`;
}

// Replace an element's text with dots, keeping the words for screen readers.
export function renderDots(el) {
  const text = el.dataset.dots || el.textContent.trim();
  el.dataset.dots = text;
  el.classList.add('dot-text');
  el.innerHTML = `<span class="sr">${text.replace(/[<&]/g, '')}</span>${dotSVG(text)}`;
}

// Every so often, flicker a few random lit dots and wake a random unlit one.
let timer = null;
export function startTwinkle() {
  if (timer || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  const kick = (dot, cls) => {
    if (!dot || dot.classList.contains(cls)) return;
    dot.style.setProperty('--d', `${(0.25 + Math.random() * 0.9).toFixed(2)}s`);
    dot.classList.add(cls);
    dot.addEventListener('animationend', () => dot.classList.remove(cls), { once: true });
  };
  timer = setInterval(() => {
    if (document.hidden) return;
    document.querySelectorAll('.dot-text svg').forEach((svg) => {
      if (!svg.getClientRects().length) return; // not on screen
      const on = svg.querySelectorAll('.on');
      const offDots = svg.querySelectorAll('.off');
      const n = 1 + Math.floor(Math.random() * 3);
      for (let i = 0; i < n; i++) kick(on[Math.floor(Math.random() * on.length)], 'blink');
      if (Math.random() < 0.35) kick(offDots[Math.floor(Math.random() * offDots.length)], 'ghost');
    });
  }, 420);
}
