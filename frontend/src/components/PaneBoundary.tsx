import { Component, type ErrorInfo, type ReactNode } from 'react';
import { localApiError } from '../api/http';
import { reportError } from '../stores/errors';

interface Props {
  /** Human name of the pane ("Chat", "Office", ...). */
  name: string;
  children: ReactNode;
  /** Render nothing on failure (overlays); the crash is still toasted and logged. */
  quiet?: boolean;
}

interface State {
  error: Error | null;
}

/** v0.0.4 🍊 Error boundary around one pane: shows a friendly fallback instead of blanking the app. */
export class PaneBoundary extends Component<Props, State> {
  override state: State = { error: null };

  /** v0.0.4 🍊 Switches to the fallback after a render error. */
  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  /** v0.0.4 🍊 Reports the crash (toast + Trace error log). */
  override componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error(`[Yuzu] ${this.props.name} pane crashed`, error, info.componentStack);
    reportError(localApiError('INTERNAL', `The ${this.props.name} pane crashed: ${error.message}`, { pane: this.props.name }), 'ui', this.props.name);
  }

  /** v0.0.4 🍊 Renders the pane, or the fallback with a retry button. */
  override render(): ReactNode {
    if (!this.state.error) return this.props.children;
    if (this.props.quiet) return null;
    return (
      <section
        role="alert"
        className="flex min-h-0 flex-1 flex-col items-center justify-center gap-2 rounded-2xl border border-danger/40 bg-surface p-6 text-center"
      >
        <p className="text-sm font-bold">The {this.props.name} pane hit a snag.</p>
        <p className="max-w-xs text-xs text-ink-3">{this.state.error.message}</p>
        <button
          type="button"
          onClick={() => this.setState({ error: null })}
          className="mt-1 rounded-lg border border-line-strong bg-surface px-3 py-1.5 text-xs font-semibold hover:bg-surface-2"
        >
          Reload pane
        </button>
      </section>
    );
  }
}
