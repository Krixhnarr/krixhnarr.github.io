// Pip — the field drone that rides along with the player. A small SVG
// character with moods (idle, happy, wow, sad) and CSS-driven idle motion:
// hovering, blinking, looking around and a flickering thruster.

export function pipSVG(mood = 'idle', cls = '') {
  return `<svg class="pip ${mood} ${cls}" viewBox="0 0 120 120" aria-hidden="true">
    <ellipse class="pip-shadow" cx="60" cy="113" rx="24" ry="4"/>
    <g class="pip-hover">
      <path class="pip-antenna" d="M60 24V11"/>
      <circle class="pip-tip" cx="60" cy="9" r="4.5"/>
      <rect class="pip-ear" x="11" y="44" width="14" height="24" rx="6"/>
      <rect class="pip-ear" x="95" y="44" width="14" height="24" rx="6"/>
      <rect class="pip-shell" x="20" y="22" width="80" height="68" rx="28"/>
      <path class="pip-shine" d="M34 31c6-3 14-4 22-4"/>
      <rect class="pip-face" x="31" y="36" width="58" height="40" rx="17"/>
      <g class="pip-eyes">
        <g class="pip-look">
          <circle class="pip-iris" cx="60" cy="56" r="11"/>
          <circle class="pip-pupil" cx="60" cy="56" r="5"/>
          <circle class="pip-glint" cx="64.5" cy="51.5" r="2.6"/>
        </g>
        <path class="pip-happy" d="M49 60c3.5-8 18.5-8 22 0"/>
        <path class="pip-sad" d="M49 54c3.5 7 18.5 7 22 0"/>
        <rect class="pip-lid" x="31" y="36" width="58" height="40" rx="17"/>
      </g>
      <path class="pip-cheek" d="M38 69h6M76 69h6"/>
      <ellipse class="pip-jet" cx="60" cy="96" rx="13" ry="4.5"/>
    </g>
  </svg>`;
}
