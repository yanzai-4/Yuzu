import { memo, useId } from 'react';
import { fruitColor, fruitPath, fruitShape } from '../../lib/citrus';
import { shade, tint } from '../../lib/colors';

/** v0.0.4 🍊 Facial expressions of a citrus character. */
export type CitrusExpression = 'happy' | 'focused' | 'thinking' | 'talking' | 'sleeping' | 'worried';

const INK = '#2b1d12';
const LEAF = '#4c9a2a';
const PORES: [number, number][] = [
  [-0.55, -0.35],
  [0.45, -0.5],
  [0.62, 0.1],
  [-0.2, 0.62],
  [0.3, 0.55],
  [-0.66, 0.25],
  [0.05, -0.72],
];

/** v0.0.4 🍊 Props of the citrus body drawing. */
export interface CitrusBodyProps {
  cx: number;
  cy: number;
  /** Base radius before the per-fruit size factor. */
  r: number;
  color: string;
  avatarKey: string;
  expression?: CitrusExpression;
  /** Pupil offset (-1..1) to look at something. */
  lookX?: number;
  lookY?: number;
  /** Adds the CSS hooks used by the office animations (blink, mouth). */
  animated?: boolean;
}

/**
 * v0.0.4 🍊 The citrus character's body: peel (per-fruit shape), leaves, face and cheeks, as an SVG
 * group. Shared by the office desks (animated) and every small avatar (static).
 */
export const CitrusBody = memo(function CitrusBody({
  cx,
  cy,
  r,
  color,
  avatarKey,
  expression = 'happy',
  lookX = 0,
  lookY = 0,
  animated = false,
}: CitrusBodyProps) {
  const clipId = useId();
  const shape = fruitShape(avatarKey);
  const peel = fruitColor(color, avatarKey);
  const R = r * shape.size;
  const rx = R * shape.w;
  const ry = R * shape.h;
  const top = cy - ry * (shape.bumps > 1 ? 1.02 : 1);
  const eyeY = cy - ry * 0.02;
  const eyeDx = rx * 0.3;
  const mouthY = cy + ry * 0.27;
  const px = lookX * R * 0.045;
  const py = lookY * R * 0.045;
  const outline = fruitPath(cx, cy, rx, ry, shape);

  return (
    <g>
      <defs>
        <clipPath id={clipId}>
          <path d={outline} />
        </clipPath>
      </defs>
      {/* Stem and leaves */}
      <path
        d={`M${cx} ${top + R * 0.05} Q${cx + R * 0.02} ${top - R * 0.08} ${cx + R * 0.09} ${top - R * 0.17}`}
        stroke="#6b4423"
        strokeWidth={R * 0.07}
        strokeLinecap="round"
        fill="none"
      />
      <g transform={`translate(${cx + R * 0.08} ${top - R * 0.15}) rotate(-16)`}>
        <path
          d={`M0 0 C${R * 0.22} ${-R * 0.3} ${R * 0.6} ${-R * 0.28} ${R * 0.78} ${-R * 0.08} C${R * 0.56} ${R * 0.1} ${R * 0.22} ${R * 0.1} 0 0 Z`}
          fill={LEAF}
        />
        <path d={`M${R * 0.05} ${-R * 0.02} L${R * 0.62} ${-R * 0.1}`} stroke={shade(LEAF, 0.3)} strokeWidth={R * 0.03} opacity={0.6} />
      </g>
      {shape.leaves === 2 ? (
        <g transform={`translate(${cx + R * 0.06} ${top - R * 0.13}) rotate(200) scale(0.72 -0.72)`}>
          <path
            d={`M0 0 C${R * 0.22} ${-R * 0.3} ${R * 0.6} ${-R * 0.28} ${R * 0.78} ${-R * 0.08} C${R * 0.56} ${R * 0.1} ${R * 0.22} ${R * 0.1} 0 0 Z`}
            fill={tint(LEAF, 0.12)}
          />
        </g>
      ) : null}
      {/* Peel */}
      <path d={outline} fill={peel} stroke={shade(peel, 0.28)} strokeWidth={R * 0.05} strokeLinejoin="round" />
      <g clipPath={`url(#${clipId})`}>
        {shape.blush ? <ellipse cx={cx} cy={cy + ry * 0.55} rx={rx * 0.95} ry={ry * 0.5} fill={shape.blush} opacity={0.4} /> : null}
        <ellipse cx={cx + rx * 0.25} cy={cy + ry * 0.55} rx={rx * 0.9} ry={ry * 0.45} fill={shade(peel, 0.12)} opacity={0.35} />
      </g>
      {PORES.map(([dx, dy], i) => (
        <circle key={i} cx={cx + dx * rx} cy={cy + dy * ry} r={R * 0.028} fill={shade(peel, 0.3)} opacity={0.35} />
      ))}
      <ellipse
        cx={cx - rx * 0.4}
        cy={cy - ry * 0.46}
        rx={R * 0.25}
        ry={R * 0.14}
        transform={`rotate(-35 ${cx - rx * 0.4} ${cy - ry * 0.46})`}
        fill="#fff"
        opacity={0.5}
      />
      {/* Cheeks */}
      <ellipse cx={cx - rx * 0.54} cy={cy + ry * 0.2} rx={R * 0.13} ry={R * 0.08} fill="#ff6b6b" opacity={0.35} />
      <ellipse cx={cx + rx * 0.54} cy={cy + ry * 0.2} rx={R * 0.13} ry={R * 0.08} fill="#ff6b6b" opacity={0.35} />
      {/* Eyes */}
      {expression === 'sleeping' ? (
        <g stroke={INK} strokeWidth={R * 0.05} strokeLinecap="round" fill="none">
          <path d={`M${cx - eyeDx - R * 0.09} ${eyeY} q${R * 0.09} ${R * 0.08} ${R * 0.18} 0`} />
          <path d={`M${cx + eyeDx - R * 0.09} ${eyeY} q${R * 0.09} ${R * 0.08} ${R * 0.18} 0`} />
        </g>
      ) : (
        <g className={animated ? 'citrus-eyes' : undefined}>
          {[-1, 1].map((side) => (
            <g key={side}>
              <ellipse cx={cx + side * eyeDx + px} cy={eyeY + py} rx={R * 0.085} ry={R * 0.115} fill={INK} />
              <circle cx={cx + side * eyeDx + px + R * 0.03} cy={eyeY + py - R * 0.045} r={R * 0.032} fill="#fff" />
            </g>
          ))}
        </g>
      )}
      {expression === 'worried' ? (
        <g stroke={INK} strokeWidth={R * 0.04} strokeLinecap="round">
          <path d={`M${cx - eyeDx - R * 0.1} ${eyeY - R * 0.2} l${R * 0.18} ${R * 0.06}`} />
          <path d={`M${cx + eyeDx + R * 0.1} ${eyeY - R * 0.2} l${-R * 0.18} ${R * 0.06}`} />
        </g>
      ) : null}
      {/* Mouth */}
      <CitrusMouth expression={expression} cx={cx} y={mouthY} R={R} animated={animated} />
    </g>
  );
});

function CitrusMouth({ expression, cx, y, R, animated }: { expression: CitrusExpression; cx: number; y: number; R: number; animated: boolean }) {
  const stroke = { stroke: INK, strokeWidth: R * 0.055, strokeLinecap: 'round' as const, fill: 'none' };
  switch (expression) {
    case 'talking':
      return (
        <ellipse
          className={animated ? 'citrus-mouth-talk' : undefined}
          cx={cx}
          cy={y + R * 0.03}
          rx={R * 0.1}
          ry={R * 0.08}
          fill="#7a2e1c"
        />
      );
    case 'thinking':
      return <path d={`M${cx - R * 0.06} ${y + R * 0.03} L${cx + R * 0.1} ${y}`} {...stroke} />;
    case 'focused':
      return <path d={`M${cx - R * 0.09} ${y + R * 0.02} L${cx + R * 0.09} ${y + R * 0.02}`} {...stroke} />;
    case 'sleeping':
      return <circle cx={cx} cy={y + R * 0.03} r={R * 0.045} {...stroke} strokeWidth={R * 0.04} />;
    case 'worried':
      return <path d={`M${cx - R * 0.13} ${y + R * 0.08} Q${cx} ${y - R * 0.05} ${cx + R * 0.13} ${y + R * 0.08}`} {...stroke} />;
    default:
      return <path d={`M${cx - R * 0.14} ${y} Q${cx} ${y + R * 0.13} ${cx + R * 0.14} ${y}`} {...stroke} />;
  }
}
