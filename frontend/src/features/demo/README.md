# demo

The guided tour: six numbered steps that jump the workspace to the state that shows off one product
pillar, with a one-line English caption.

The tour is **view-only**. It switches tabs, sets filters and opens the inspector; it never posts a
message, answers a card or mutates the simulated world, so every step is repeatable.

| File | Responsibility |
|---|---|
| `steps.ts` | The six step records and their types. Pure data. |
| `hash.ts` | `#/demo/<id>` parsing and formatting. Pure. |
| `applyDemoStep.ts` | The only code that maps a step onto the stores. |
| `DemoBar.tsx` | The chip row and the caption line. |
| `useDemoKeys.ts` | Number-key shortcuts and hash synchronisation. |

Adding a seventh step means adding one record to `steps.ts`. Nothing else changes.

Removing the tour means deleting this folder, `stores/demo.ts`, the `<Spotlight>` wrappers and the
two lines it adds to `features/shell/Workspace.tsx` (`useDemoKeys()` and `<DemoBar />`).
