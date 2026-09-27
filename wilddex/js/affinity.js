// Affinity types — WildDex's answer to Pokémon types. Every animal has one
// or two, shown as HUD-style glyphs. Each type has its own muted colour so
// the inventory reads at a glance on the charcoal UI.

// Wedge of a ring, for the trefoil glyphs.
function wedge(cx, cy, r1, r2, a0, a1) {
  const p = (r, a) => `${(cx + r * Math.cos((a * Math.PI) / 180)).toFixed(2)} ${(cy + r * Math.sin((a * Math.PI) / 180)).toFixed(2)}`;
  return `M${p(r1, a0)} L${p(r2, a0)} A${r2} ${r2} 0 0 1 ${p(r2, a1)} L${p(r1, a1)} A${r1} ${r1} 0 0 0 ${p(r1, a0)}Z`;
}
const trefoil = [-90, 30, 150].map((a) => wedge(12, 12, 3.2, 9.5, a - 30, a + 30)).join(' ');

export const TYPES = {
  terra: { name: 'Terra', color: '#CDA66F', desc: 'Ground dwellers & grazers',
    svg: '<path d="M3 19.5 12 4l9 15.5Z"/><path d="m7.8 19.5 4.2-7.3 4.2 7.3"/>' },
  aqua: { name: 'Aqua', color: '#5FB6DA', desc: 'Creatures of water',
    svg: '<path d="M12 3s-6.5 7.4-6.5 11.6a6.5 6.5 0 0 0 13 0C18.5 10.4 12 3 12 3Z"/><path d="M9 15.2a3 3 0 0 0 3 3"/>' },
  aero: { name: 'Aero', color: '#9ED8CF', desc: 'Masters of the sky',
    svg: '<path d="m4 13 8-8 8 8"/><path d="m4 20 8-8 8 8"/>' },
  feral: { name: 'Feral', color: '#E27D6D', desc: 'Apex hunters',
    svg: '<path d="M5 20C7 14 9 8 12 3"/><path d="M10 21c2-5 4-10 7-14"/><path d="M15 21c1.5-3 3-6 5-8.5"/>' },
  toxin: { name: 'Toxin', color: '#A9D46A', desc: 'Venom, poison & stink',
    svg: '<circle cx="12" cy="7.2" r="4.3"/><circle cx="7.8" cy="14.6" r="4.3"/><circle cx="16.2" cy="14.6" r="4.3"/><circle cx="12" cy="12.2" r="1.6" class="fill"/>' },
  swarm: { name: 'Swarm', color: '#E8C45E', desc: 'Insects & arthropods',
    svg: '<path d="m8 3.5 3.5 2v4L8 11.5l-3.5-2v-4Z"/><path d="m16 3.5 3.5 2v4L16 11.5l-3.5-2v-4Z"/><path d="m12 12 3.5 2v4L12 20l-3.5-2v-4Z"/>' },
  frost: { name: 'Frost', color: '#BDD9F4', desc: 'Built for the cold',
    svg: '<path d="M12 2v20M3.3 7l17.4 10M3.3 17 20.7 7"/><path d="m9.5 3.8 2.5 2.4 2.5-2.4M9.5 20.2l2.5-2.4 2.5 2.4"/>' },
  solar: { name: 'Solar', color: '#F0A05B', desc: 'Heat, sun & fire',
    svg: '<circle cx="12" cy="12" r="4.2"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9 7 7M17 17l2.1 2.1M4.9 19.1 7 17M17 7l2.1-2.1"/>' },
  umbra: { name: 'Umbra', color: '#9197C4', desc: 'Night stalkers',
    svg: '<path d="M15.5 3.2A9 9 0 1 0 20.8 16 7.2 7.2 0 0 1 15.5 3.2Z"/><path d="M19 4.5v3M17.5 6h3"/>' },
  psi: { name: 'Psi', color: '#D98DDB', desc: 'Big brains & clever minds',
    svg: '<path d="M2 12c3.8-6.5 16.2-6.5 20 0-3.8 6.5-16.2 6.5-20 0Z"/><circle cx="12" cy="12" r="3.2"/><circle cx="12" cy="12" r="1" class="fill"/>' },
  verdant: { name: 'Verdant', color: '#71C189', desc: 'Forest & leaf dwellers',
    svg: '<path d="M4.5 19.5C4.5 10 10.5 4.5 20 4.5c0 9.5-5.5 15-15.5 15Z"/><path d="M4.5 19.5 14 10"/>' },
  volt: { name: 'Volt', color: '#F1E06E', desc: 'Speed & electric senses',
    svg: '<path d="M13.5 2 5 13.5h6.2L10 22l9-12h-6.2Z"/>' },
  ancient: { name: 'Ancient', color: '#CDBBA2', desc: 'Living fossils & relics',
    svg: `<circle cx="12" cy="12" r="10.5"/><path class="fill" d="${trefoil}"/><circle cx="12" cy="12" r="1.8" class="fill"/>` },
};

export const TYPE_IDS = Object.keys(TYPES);

// Affinities per entry key: primary first.
const A = `
dog terra psi|cat feral umbra|rabbit terra|hamster terra umbra|guinea-pig terra|goldfish aqua|ferret feral|african-grey aero psi|cockroach swarm umbra|fly swarm aero
cow terra|horse terra volt|pig terra psi|sheep terra|chicken terra|mallard aqua aero|goose aero aqua|water-buffalo terra aqua|llama terra|hare terra volt|quail terra aero|partridge terra aero
squirrel terra verdant|robin aero|house-finch aero|goldfinch aero|junco aero frost|chickadee aero psi|jay aero psi|magpie aero psi|bulbul aero|brambling aero frost|indigo-bunting aero umbra|hummingbird aero volt|snail terra aqua|slug terra aqua|skunk terra toxin|porcupine terra umbra
ladybug swarm toxin|bee swarm toxin|ant swarm|grasshopper swarm verdant|cricket swarm umbra|mantis swarm feral|cicada swarm terra|leafhopper swarm verdant|lacewing swarm aero|stick-insect swarm verdant|dragonfly swarm aero|damselfly swarm aqua|monarch swarm toxin|red-admiral swarm aero|ringlet swarm verdant|cabbage-white swarm verdant|sulphur swarm solar|blue-butterfly swarm psi
orb-weaver swarm|black-widow swarm toxin|tarantula swarm toxin|wolf-spider swarm umbra|harvestman swarm umbra|tick swarm toxin|centipede swarm toxin|tiger-beetle swarm volt|ground-beetle swarm umbra|longhorn-beetle swarm verdant|leaf-beetle swarm verdant|dung-beetle swarm terra|rhino-beetle swarm terra|weevil swarm verdant|roundworm terra|isopod swarm aqua
flamingo aero aqua|pelican aero aqua|white-stork aero aqua|black-stork aero verdant|spoonbill aero aqua|little-blue-heron aero aqua|great-egret aero aqua|bittern aero verdant|crane aero aqua|limpkin aero aqua|sandpiper aero aqua|oystercatcher aero aqua
tench aqua|coho-salmon aqua|sturgeon aqua ancient|gar aqua ancient|crayfish aqua|beaver aqua terra|otter aqua feral|mink aqua feral|merganser aqua aero|coot aqua aero|swamphen aqua aero
bullfrog aqua feral|tree-frog aqua verdant|tailed-frog aqua frost|fire-salamander aqua toxin|newt aqua toxin|spotted-salamander aqua solar|axolotl aqua|pond-turtle aqua terra|box-turtle terra|gecko terra umbra|iguana terra verdant|anole terra verdant|whiptail terra solar|agama terra solar|alligator-lizard terra|green-lizard terra verdant|chameleon verdant psi|komodo feral toxin|nile-crocodile aqua feral|alligator aqua feral
worm-snake terra|ringneck-snake terra toxin|hognose-snake terra psi|green-snake terra verdant|kingsnake terra feral|garter-snake terra|water-snake aqua terra|vine-snake verdant toxin|night-snake umbra toxin|boa terra feral|rock-python terra feral|indian-cobra toxin terra|green-mamba toxin verdant|diamondback toxin terra
great-white aqua feral|tiger-shark aqua feral|hammerhead aqua volt|electric-ray aqua volt|stingray aqua toxin|loggerhead aqua|leatherback aqua ancient|sea-snake aqua toxin|orca aqua psi|grey-whale aqua|dugong aqua verdant|sea-lion aqua terra|albatross aero aqua|barracouta aqua feral|eel aqua umbra
jellyfish aqua toxin|anemone aqua toxin|brain-coral aqua ancient|flatworm aqua toxin|conch aqua|sea-slug aqua toxin|chiton aqua|nautilus aqua ancient|crab aqua terra|fiddler-crab aqua terra|hermit-crab aqua terra|lobster aqua|starfish aqua|sea-urchin aqua ancient|sea-cucumber aqua|clownfish aqua|lionfish aqua toxin|pufferfish aqua toxin|rock-beauty aqua
bald-eagle aero feral|kite aero solar|vulture aero toxin|great-grey-owl aero umbra|peacock aero verdant|grouse aero terra|dipper aero aqua|bee-eater aero feral|coucal aero terra
lion feral solar|cheetah feral volt|leopard feral umbra|zebra terra|african-elephant terra psi|hippo aqua terra|hyena feral umbra|wild-dog feral psi|warthog terra|gazelle terra volt|impala terra volt|hartebeest terra|meerkat terra solar|mongoose feral toxin|ostrich terra volt|bustard aero terra
brown-bear feral terra|black-bear terra verdant|grey-wolf feral umbra|red-wolf feral|coyote feral psi|red-fox feral psi|grey-fox feral verdant|lynx feral frost|cougar feral terra|wild-boar terra|badger terra umbra|weasel feral|marmot terra frost|bighorn terra|ibex terra|bison terra|snow-leopard feral frost|red-panda verdant terra|giant-panda verdant terra
orangutan psi verdant|gorilla psi terra|chimpanzee psi|gibbon psi aero|siamang psi aero|macaque psi frost|langur psi verdant|colobus psi verdant|proboscis-monkey psi aqua|marmoset psi verdant|capuchin psi|howler-monkey psi volt|titi-monkey psi|spider-monkey psi verdant|squirrel-monkey psi|baboon psi terra|patas-monkey psi volt|guenon psi verdant|ring-tailed-lemur psi toxin|indri psi verdant
tiger feral verdant|jaguar feral aqua|sloth verdant|asian-elephant terra psi|sloth-bear terra swarm|dhole feral|armadillo terra|toucan aero verdant|macaw aero verdant|hornbill aero verdant|jacamar aero swarm
koala verdant|wallaby terra|wombat terra umbra|platypus aqua toxin|echidna terra ancient|dingo feral solar|black-swan aero aqua|cockatoo aero psi|lorikeet aero verdant|frilled-lizard terra solar
polar-bear frost feral|arctic-fox frost|arctic-wolf frost feral|king-penguin frost aqua|ptarmigan frost aero
camel solar terra|kit-fox solar umbra|gila-monster solar toxin|sidewinder solar toxin|horned-viper solar toxin|scorpion solar toxin
triceratops ancient terra|trilobite ancient aqua`;

export const AFFINITY = Object.fromEntries(
  A.trim().split(/[|\n]/).map((row) => {
    const [key, ...types] = row.trim().split(/\s+/);
    return [key, types];
  }),
);

export function glyph(id, cls = 'glyph') {
  const t = TYPES[id];
  return `<svg class="${cls}" viewBox="0 0 24 24" style="--tc:${t.color}" aria-hidden="true">${t.svg}</svg>`;
}
