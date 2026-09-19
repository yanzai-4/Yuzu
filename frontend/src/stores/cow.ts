/**
 * v0.0.4 🍊 Copy-on-write helpers for the batched event reducer.
 *
 * A batch of events (one animation frame) may touch the same record many times. Each helper clones
 * its base value on the first write only, so a batch costs one copy per touched collection and every
 * untouched collection keeps its identity (memoized selectors stay stable).
 */

/** v0.0.4 🍊 Copy-on-write view over a `Record<string, T>`. */
export class CowRecord<T> {
  private draft: Record<string, T> | null = null;
  private readonly base: Record<string, T>;

  /** v0.0.4 🍊 Wraps the current (frozen) record. */
  constructor(base: Record<string, T>) {
    this.base = base;
  }

  /** v0.0.4 🍊 Reads the latest value of a key. */
  get(key: string): T | undefined {
    return (this.draft ?? this.base)[key];
  }

  /** v0.0.4 🍊 Writes a key (clones the base on the first write). */
  set(key: string, value: T): void {
    if (this.get(key) === value) return;
    this.draft ??= { ...this.base };
    this.draft[key] = value;
  }

  /** v0.0.4 🍊 Removes a key (clones the base on the first write). */
  delete(key: string): void {
    if (!(key in (this.draft ?? this.base))) return;
    this.draft ??= { ...this.base };
    delete this.draft[key];
  }

  /** v0.0.4 🍊 Replaces the whole record. */
  replace(next: Record<string, T>): void {
    this.draft = next;
  }

  /** v0.0.4 🍊 True when at least one write happened. */
  get changed(): boolean {
    return this.draft !== null;
  }

  /** v0.0.4 🍊 The latest record (the base itself when nothing changed). */
  get value(): Record<string, T> {
    return this.draft ?? this.base;
  }
}

/** v0.0.4 🍊 Copy-on-write view over an array. */
export class CowList<T> {
  private draft: T[] | null = null;
  private readonly base: readonly T[];

  /** v0.0.4 🍊 Wraps the current (frozen) array. */
  constructor(base: readonly T[]) {
    this.base = base;
  }

  /** v0.0.4 🍊 The latest items (read-only view). */
  get items(): readonly T[] {
    return this.draft ?? this.base;
  }

  /** v0.0.4 🍊 A mutable copy (cloned on first access). */
  mutable(): T[] {
    this.draft ??= [...this.base];
    return this.draft;
  }

  /** v0.0.4 🍊 True when the array was copied for writing. */
  get changed(): boolean {
    return this.draft !== null;
  }

  /** v0.0.4 🍊 The latest array. */
  get value(): T[] {
    return this.draft ?? (this.base as T[]);
  }
}
