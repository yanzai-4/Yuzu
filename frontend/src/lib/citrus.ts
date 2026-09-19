/**
 * v0.0.4 🍊 Citrus catalogue: per-fruit body shapes and fallback colors for the SVG characters.
 *
 * `avatarKey` is the lowercase fruit name with dashes ("blood-orange"). Unknown keys fall back to a
 * round orange so a new fruit added by the backend still renders.
 */

/** v0.0.4 🍊 Geometry knobs of one fruit body. */
export interface FruitShape {
  /** Horizontal radius factor. */
  w: number;
  /** Vertical radius factor. */
  h: number;
  /** Overall size factor. */
  size: number;
  /** Peel bumpiness (0 = smooth). */
  bumps: number;
  /** Pointed nub at the bottom (lemon, citron, bergamot). */
  nub: boolean;
  /** Number of leaves on the stem. */
  leaves: 1 | 2;
  /** Optional blush tint painted on the lower body. */
  blush?: string;
}

const DEFAULT_SHAPE: FruitShape = { w: 1, h: 1, size: 1, bumps: 0.3, nub: false, leaves: 1 };

const SHAPES: Record<string, FruitShape> = {
  yuzu: { w: 1.03, h: 0.96, size: 1, bumps: 1.4, nub: false, leaves: 2 },
  lime: { w: 1, h: 1, size: 0.93, bumps: 0.25, nub: false, leaves: 1 },
  kumquat: { w: 0.86, h: 1.08, size: 0.9, bumps: 0.2, nub: false, leaves: 2 },
  pomelo: { w: 1.06, h: 1.02, size: 1.1, bumps: 0.45, nub: false, leaves: 1 },
  lemon: { w: 0.9, h: 1.1, size: 1, bumps: 0.5, nub: true, leaves: 1 },
  mandarin: { w: 1.08, h: 0.9, size: 0.97, bumps: 0.45, nub: false, leaves: 2 },
  grapefruit: { w: 1.02, h: 1, size: 1.08, bumps: 0.3, nub: false, leaves: 1 },
  clementine: { w: 1.06, h: 0.92, size: 0.94, bumps: 0.3, nub: false, leaves: 2 },
  tangerine: { w: 1.07, h: 0.92, size: 0.96, bumps: 0.4, nub: false, leaves: 1 },
  bergamot: { w: 0.98, h: 1.03, size: 1, bumps: 0.8, nub: true, leaves: 1 },
  calamansi: { w: 1, h: 0.98, size: 0.84, bumps: 0.2, nub: false, leaves: 1 },
  sudachi: { w: 1.02, h: 0.98, size: 0.9, bumps: 0.3, nub: false, leaves: 2 },
  citron: { w: 0.9, h: 1.12, size: 1.04, bumps: 1.9, nub: true, leaves: 1 },
  'blood-orange': { w: 1.02, h: 1, size: 1, bumps: 0.35, nub: false, leaves: 1, blush: '#b3122e' },
  satsuma: { w: 1.1, h: 0.88, size: 0.97, bumps: 0.55, nub: false, leaves: 1 },
  kabosu: { w: 1, h: 1, size: 0.98, bumps: 0.3, nub: false, leaves: 2 },
};

/** v0.0.4 🍊 Fallback peel color per fruit (the backend normally sends `agent.color`). */
export const FRUIT_COLORS: Record<string, string> = {
  yuzu: '#f5c518',
  lime: '#7cc242',
  kumquat: '#ff8c1a',
  pomelo: '#c5dc6b',
  lemon: '#ffe14d',
  mandarin: '#ff9a2e',
  grapefruit: '#ff8a65',
  clementine: '#ff9f1c',
  tangerine: '#ff7f11',
  bergamot: '#d4e157',
  calamansi: '#9ccc65',
  sudachi: '#66bb6a',
  citron: '#fdd835',
  'blood-orange': '#e4572e',
  satsuma: '#ffa62b',
  kabosu: '#8bc34a',
};

/** v0.0.4 🍊 Display names of every known citrus, in the order the backend hands them out. */
export const CITRUS_NAMES = [
  'Yuzu',
  'Lime',
  'Kumquat',
  'Pomelo',
  'Lemon',
  'Mandarin',
  'Grapefruit',
  'Clementine',
  'Tangerine',
  'Bergamot',
  'Calamansi',
  'Sudachi',
  'Citron',
  'Blood Orange',
  'Satsuma',
  'Kabosu',
];

/** v0.0.4 🍊 Shape of a fruit by avatar key (falls back to a round orange). */
export function fruitShape(avatarKey: string | null | undefined): FruitShape {
  return (avatarKey && SHAPES[avatarKey]) || DEFAULT_SHAPE;
}

/** v0.0.4 🍊 "Blood Orange" → "blood-orange". */
export function avatarKeyFromName(name: string): string {
  return name.trim().toLowerCase().replace(/\s+/g, '-');
}

/** v0.0.4 🍊 A usable peel color: the agent color when valid, else the fruit default. */
export function fruitColor(color: string | null | undefined, avatarKey?: string | null): string {
  if (color && /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.test(color.trim())) return color.trim();
  return (avatarKey && FRUIT_COLORS[avatarKey]) || '#ff9f1c';
}

const pathCache = new Map<string, string>();

/** v0.0.4 🍊 SVG path of a (slightly bumpy) fruit outline centered on (cx, cy); cached per geometry. */
export function fruitPath(cx: number, cy: number, rx: number, ry: number, shape: FruitShape): string {
  const key = `${cx}|${cy}|${rx}|${ry}|${shape.bumps}|${shape.nub}`;
  const cached = pathCache.get(key);
  if (cached) return cached;
  const steps = 72;
  const parts: string[] = [];
  for (let i = 0; i < steps; i++) {
    const t = (i / steps) * Math.PI * 2;
    let k = 1 + shape.bumps * (0.017 * Math.sin(11 * t) + 0.009 * Math.sin(23 * t + 1.3));
    if (shape.nub) {
      // A soft point at the bottom (t = π/2 because SVG y grows downward).
      const d = Math.atan2(Math.sin(t - Math.PI / 2), Math.cos(t - Math.PI / 2));
      k += 0.14 * Math.exp(-(d * d) / (2 * 0.13 * 0.13));
    }
    const x = cx + rx * k * Math.cos(t);
    const y = cy + ry * k * Math.sin(t);
    parts.push(`${i === 0 ? 'M' : 'L'}${x.toFixed(2)} ${y.toFixed(2)}`);
  }
  const path = `${parts.join(' ')} Z`;
  pathCache.set(key, path);
  return path;
}
