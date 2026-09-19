import { useMemo } from 'react';
import type { DeskState } from '../../api/types';
import { useRoomStore } from '../../stores/room';
import { countDeskStates, toDeskViews, type DeskView } from './officeData';

/** v0.0.4 🍊 Everything the office floor renders: eight desk views plus per-state counts. */
export interface OfficeData {
  desks: DeskView[];
  occupied: number;
  stateCounts: [DeskState, number][];
}

/** v0.0.4 🍊 Selector hook of the office floor (seats + agents + statuses → desk views). */
export function useOfficeData(): OfficeData {
  const seats = useRoomStore((s) => s.seats);
  const agents = useRoomStore((s) => s.agents);
  const statuses = useRoomStore((s) => s.statuses);
  return useMemo(() => {
    const desks = toDeskViews(seats, agents, statuses);
    return {
      desks,
      occupied: desks.filter((d) => d.kind === 'agent').length,
      stateCounts: countDeskStates(desks),
    };
  }, [seats, agents, statuses]);
}
