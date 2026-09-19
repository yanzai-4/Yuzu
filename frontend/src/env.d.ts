/** v0.0.4 🍊 Typed Vite environment variables used by the frontend. */
interface ImportMetaEnv {
  /** "1" swaps the HTTP client for the in-memory mock backend (see src/api/mock). */
  readonly VITE_MOCK?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
