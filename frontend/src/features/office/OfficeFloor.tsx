import { AnimatePresence } from 'motion/react';
import { Desk } from './Desk';
import { EmptyDesk } from './EmptyDesk';
import type { DeskView } from './officeData';

/**
 * v0.0.4 🍊 Presentational office floor (props only): a 4×2 grid of desks, 2 columns in narrow panes.
 * Swap this component (and Desk / DeskScene) to restyle the office; the data contract is DeskView.
 */
export function OfficeFloor({
  desks,
  onOpenAgent,
  onHire,
}: {
  desks: DeskView[];
  onOpenAgent: (agentId: string) => void;
  onHire: (seat: number) => void;
}) {
  return (
    <div className="grid grid-cols-2 gap-x-2 gap-y-3 p-3 @lg:grid-cols-4">
      <AnimatePresence mode="popLayout" initial={false}>
        {desks.map((desk) =>
          desk.kind === 'agent' ? (
            <Desk key={desk.agentId} {...desk} onOpen={onOpenAgent} />
          ) : (
            <EmptyDesk key={`empty-${desk.seat}`} seat={desk.seat} onHire={onHire} />
          ),
        )}
      </AnimatePresence>
    </div>
  );
}
