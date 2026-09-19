import { useCallback, useEffect, useRef, useState } from 'react';

interface AsyncResult<T> {
  key: string;
  nonce: number;
  data: T | null;
  failed: boolean;
}

/** v0.0.4 🍊 State of a keyed async load. */
export interface AsyncState<T> {
  data: T | null;
  loading: boolean;
  failed: boolean;
  reload: () => void;
}

/**
 * v0.0.4 🍊 Runs `load` whenever `key` changes (or reload() is called) and exposes its result.
 * Late results of an outdated key are ignored; data of the current key is kept while reloading.
 */
export function useAsync<T>(load: () => Promise<T>, key: string): AsyncState<T> {
  const loadRef = useRef(load);
  const [nonce, setNonce] = useState(0);
  const [result, setResult] = useState<AsyncResult<T> | null>(null);

  useEffect(() => {
    loadRef.current = load;
  });

  useEffect(() => {
    let cancelled = false;
    loadRef.current().then(
      (data) => {
        if (!cancelled) setResult({ key, nonce, data, failed: false });
      },
      () => {
        if (!cancelled) setResult({ key, nonce, data: null, failed: true });
      },
    );
    return () => {
      cancelled = true;
    };
  }, [key, nonce]);

  const reload = useCallback(() => setNonce((n) => n + 1), []);
  const current = result?.key === key ? result : null;
  return {
    data: current?.data ?? null,
    loading: !current || current.nonce !== nonce,
    failed: current?.failed ?? false,
    reload,
  };
}
