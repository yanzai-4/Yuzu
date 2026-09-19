import { memo, useId } from 'react';

const CODE_LINES: [number, number][] = [
  [68, 18],
  [72, 12],
  [76, 20],
  [80, 9],
  [84, 15],
  [88, 11],
  [92, 17],
];

/** v0.0.4 🍊 Desk, laptop and mug of one workstation (SVG, 160×132 scene coordinates). */
export const DeskFurniture = memo(function DeskFurniture({
  screenOn = true,
  mugColor,
}: {
  screenOn?: boolean;
  mugColor?: string;
}) {
  const clipId = useId();
  return (
    <g>
      {/* Desk */}
      <rect x={8} y={94} width={144} height={7} rx={3} fill="var(--desk-top)" />
      <rect x={14} y={100} width={132} height={5} fill="var(--desk-edge)" />
      <rect x={20} y={104} width={5} height={21} rx={1} fill="var(--desk-leg)" />
      <rect x={135} y={104} width={5} height={21} rx={1} fill="var(--desk-leg)" />
      {/* Laptop */}
      <rect x={104} y={62} width={34} height={27.5} rx={2.5} fill="var(--laptop-body)" />
      <defs>
        <clipPath id={clipId}>
          <rect x={106.5} y={64.5} width={29} height={22} rx={1} />
        </clipPath>
      </defs>
      <g className="laptop-screen">
        <rect x={106.5} y={64.5} width={29} height={22} rx={1} fill={screenOn ? 'var(--screen)' : 'var(--laptop-body)'} />
        {screenOn ? (
          <g clipPath={`url(#${clipId})`}>
            <g className="code-lines">
              {CODE_LINES.map(([y, w]) => (
                <rect key={y} x={109} y={y} width={w} height={1.8} rx={0.9} fill="var(--screen-ink)" opacity={0.75} />
              ))}
            </g>
          </g>
        ) : null}
      </g>
      <path d="M100 94 L142 94 L138 89.5 L104 89.5 Z" fill="var(--laptop-base)" />
      {/* Mug */}
      <path className="steam" d="M22.5 81 q-2 -2 0 -4 q2 -2 0 -4" stroke="var(--ink-3)" strokeWidth={1} fill="none" strokeLinecap="round" />
      <path className="steam" d="M26.5 81 q-2 -2 0 -4 q2 -2 0 -4" stroke="var(--ink-3)" strokeWidth={1} fill="none" strokeLinecap="round" />
      <rect x={19} y={84} width={10} height={10} rx={1.8} fill={mugColor ?? 'var(--surface)'} stroke="var(--line-strong)" strokeWidth={0.8} />
      <path d="M29 86.5 q3.6 0 3.6 3 q0 3 -3.6 3" stroke="var(--line-strong)" strokeWidth={1.4} fill="none" />
    </g>
  );
});
