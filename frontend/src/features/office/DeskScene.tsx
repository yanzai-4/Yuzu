import { memo, type CSSProperties } from 'react';
import type { DeskState } from '../../api/types';
import { CitrusBody, type CitrusExpression } from '../../components/citrus/CitrusBody';
import { fruitColor, fruitPath, fruitShape } from '../../lib/citrus';
import { shade, tint } from '../../lib/colors';
import { DeskFurniture } from './DeskFurniture';
import './office.css';

const CX = 70;
const CY = 70;
const BODY_R = 27;

const EXPRESSION: Record<DeskState, CitrusExpression> = {
  IDLE: 'happy',
  WORKING: 'focused',
  THINKING: 'thinking',
  TALKING: 'talking',
  WAITING: 'happy',
  PAUSED: 'sleeping',
  ERROR: 'worried',
};

const LOOK: Record<DeskState, [number, number]> = {
  IDLE: [0, 0],
  WORKING: [1, 0.5],
  THINKING: [-1, -1],
  TALKING: [0, 0],
  WAITING: [1, -1],
  PAUSED: [0, 0],
  ERROR: [0, 0.3],
};

/**
 * v0.0.4 🍊 Animated workstation: chair, citrus character (per-fruit shape, agent color), desk and
 * laptop, plus state effects (thought dots, clock, zzz, error badge). Animations live in office.css.
 */
export const DeskScene = memo(function DeskScene({
  avatarKey,
  color,
  state,
  blinkDelay,
}: {
  avatarKey: string;
  color: string;
  state: DeskState;
  blinkDelay: number;
}) {
  const shape = fruitShape(avatarKey);
  const peel = fruitColor(color, avatarKey);
  const R = BODY_R * shape.size;
  const rx = R * shape.w;
  const ry = R * shape.h;
  const [lookX, lookY] = LOOK[state];
  const armColor = shade(peel, 0.18);
  const shoulderY = CY + ry * 0.32;
  const leftShoulder = CX - rx * 0.82;
  const rightShoulder = CX + rx * 0.82;

  return (
    <svg
      viewBox="0 0 160 132"
      className="desk-scene block w-full"
      style={{ '--blink-delay': `${blinkDelay}s` } as CSSProperties}
      aria-hidden="true"
    >
      <ellipse cx={80} cy={126} rx={66} ry={3.5} fill="#000" opacity={0.1} />
      {/* Chair back */}
      <rect x={CX - 30} y={CY - 24} width={60} height={56} rx={17} fill="var(--line-strong)" />
      <rect x={CX - 26} y={CY - 20} width={52} height={50} rx={14} fill="var(--surface-3)" opacity={0.6} />
      {/* Body (behind the desk) */}
      <g className="citrus-char">
        <CitrusBody cx={CX} cy={CY} r={BODY_R} color={peel} avatarKey={avatarKey} expression={EXPRESSION[state]} lookX={lookX} lookY={lookY} animated />
        {state === 'ERROR' ? <path d={fruitPath(CX, CY, rx, ry, shape)} fill="#ef4444" opacity={0.32} /> : null}
      </g>
      <DeskFurniture screenOn={state !== 'PAUSED'} mugColor={tint(peel, 0.55)} />
      {/* Arms (in front of the desk, moving with the body) */}
      <g className="citrus-char">
        <g className="citrus-arm citrus-arm-l">
          <path d={`M${leftShoulder} ${shoulderY} Q${leftShoulder - 7} ${shoulderY + 9} 52 91`} stroke={armColor} strokeWidth={5.5} strokeLinecap="round" fill="none" />
          <circle cx={52} cy={91} r={3.6} fill={peel} stroke={shade(peel, 0.3)} strokeWidth={1} />
        </g>
        <g className="citrus-arm citrus-arm-r">
          <path d={`M${rightShoulder} ${shoulderY} Q${rightShoulder + 8} ${shoulderY + 6} 104 90`} stroke={armColor} strokeWidth={5.5} strokeLinecap="round" fill="none" />
          <circle cx={104} cy={90} r={3.6} fill={peel} stroke={shade(peel, 0.3)} strokeWidth={1} />
        </g>
      </g>
      <StateEffects state={state} />
    </svg>
  );
});

function StateEffects({ state }: { state: DeskState }) {
  switch (state) {
    case 'THINKING':
      return (
        <g fill="var(--surface)" stroke="var(--ink-3)" strokeWidth={1}>
          <circle className="thought-dot" cx={42} cy={37} r={2.3} />
          <circle className="thought-dot" cx={34} cy={28} r={3.3} />
          <circle className="thought-dot" cx={24} cy={17} r={4.6} />
        </g>
      );
    case 'WAITING':
      return (
        <g>
          <circle cx={106} cy={40} r={8} fill="var(--surface)" stroke="var(--ink-2)" strokeWidth={1.5} />
          <line x1={106} y1={40} x2={109.5} y2={40} stroke="var(--ink-2)" strokeWidth={1.4} strokeLinecap="round" />
          <line className="clock-hand" x1={106} y1={40} x2={106} y2={34.5} stroke="var(--accent)" strokeWidth={1.4} strokeLinecap="round" />
          <circle cx={106} cy={40} r={1} fill="var(--ink-2)" />
        </g>
      );
    case 'PAUSED':
      return (
        <g fill="var(--ink-3)" fontWeight={800} fontFamily="ui-sans-serif, system-ui">
          <text className="zzz" x={96} y={46} fontSize={9}>
            z
          </text>
          <text className="zzz" x={103} y={38} fontSize={11}>
            z
          </text>
          <text className="zzz" x={111} y={29} fontSize={13}>
            z
          </text>
        </g>
      );
    case 'ERROR':
      return (
        <g>
          <circle cx={97} cy={43} r={7} fill="#dc2626" stroke="#fff" strokeWidth={1.5} />
          <text x={97} y={46.8} textAnchor="middle" fontSize={10} fontWeight={900} fill="#fff" fontFamily="ui-sans-serif, system-ui">
            !
          </text>
        </g>
      );
    default:
      return null;
  }
}
