import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { describeError } from './errors';

export function useLoad<T>(load: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try { setData(await load()); } catch (e) { setError(describeError(e)); } finally { setLoading(false); }
  }, deps);
  useEffect(() => { void reload(); }, [reload]);
  return { data, error, loading, reload };
}

/** Runs an action, shows its error, and calls onDone after success. */
export function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const run = async (fn: () => Promise<unknown>, onDone?: () => void) => {
    setBusy(true);
    setError(null);
    try { await fn(); onDone?.(); return true; } catch (e) { setError(describeError(e)); return false; } finally { setBusy(false); }
  };
  return { busy, error, run, setError };
}

export function Page({ title, actions, children }: { title: string; actions?: ReactNode; children: ReactNode }) {
  return (
    <section className="page">
      <header className="page-head"><h1>{title}</h1><div className="row">{actions}</div></header>
      {children}
    </section>
  );
}

export function Loadable<T>({ state, children }: { state: { data: T | null; error: string | null; loading: boolean; reload: () => void }; children: (data: T) => ReactNode }) {
  if (state.error) return <ErrorBox message={state.error} onRetry={state.reload} />;
  if (state.loading && state.data == null) return <p className="muted">Loading…</p>;
  if (state.data == null) return <p className="muted">Nothing to show.</p>;
  return <>{children(state.data)}</>;
}

export function ErrorBox({ message, onRetry }: { message: string | null; onRetry?: () => void }) {
  if (!message) return null;
  return <div className="error">{message}{onRetry && <button className="link" onClick={onRetry}>Retry</button>}</div>;
}

export interface Column<T> { key: string; label: string; render?: (row: T) => ReactNode }

export function Table<T extends object>({ rows, columns, empty = 'Nothing here.', rowKey }:
  { rows: T[]; columns: Column<T>[]; empty?: string; rowKey: (row: T) => string }) {
  if (rows.length === 0) return <p className="muted">{empty}</p>;
  return (
    <div className="table-wrap">
      <table>
        <thead><tr>{columns.map((c) => <th key={c.key}>{c.label}</th>)}</tr></thead>
        <tbody>
          {rows.map((r) => (
            <tr key={rowKey(r)}>
              {columns.map((c) => <td key={c.key}>{c.render ? c.render(r) : String((r as Record<string, unknown>)[c.key] ?? '—')}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function Select<V extends string>({ value, options, onChange, label }:
  { value: V; options: readonly V[]; onChange: (v: V) => void; label?: string }) {
  return (
    <label className="inline">
      {label && <span>{label}</span>}
      <select value={value} onChange={(e) => onChange(e.target.value as V)}>
        {options.map((o) => <option key={o} value={o}>{o.replace(/_/g, ' ')}</option>)}
      </select>
    </label>
  );
}

export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-label={title}>
        <header className="page-head"><h2>{title}</h2><button className="link" onClick={onClose}>Close</button></header>
        {children}
      </div>
    </div>
  );
}

export function Json({ value }: { value: unknown }) {
  return <pre className="json">{JSON.stringify(value, null, 2)}</pre>;
}

export function Stat({ label, value }: { label: string; value: ReactNode }) {
  return <div className="stat"><div className="stat-value">{value}</div><div className="stat-label">{label}</div></div>;
}
