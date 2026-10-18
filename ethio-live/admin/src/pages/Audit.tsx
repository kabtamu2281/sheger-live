import { useEffect, useState } from 'react';
import { rpc } from '../supabase';
import { formatDate } from '../errors';
import { ErrorBox, Page, Table, useAction } from '../ui';

interface Entry { id: number; actor_id: string | null; action: string; target_type: string | null; target_id: string | null; details: unknown; created_at: string }

export function Audit() {
  const [rows, setRows] = useState<Entry[]>([]);
  const [done, setDone] = useState(false);
  const [actor, setActor] = useState('');
  const action = useAction();
  const load = (before: number | null, reset: boolean) => action.run(async () => {
    const page = await rpc<Entry[]>('staff_audit_log', { p_limit: 100, p_before: before, p_actor: actor.trim() || null });
    setRows((prev) => reset ? page : [...prev, ...page]);
    setDone(page.length < 100);
  });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { void load(null, true); }, []);
  return (
    <Page title="Audit log">
      <p className="muted">Append-only: the database rejects any update or delete on this table, including from staff.</p>
      <form className="row" style={{ marginBottom: 12 }} onSubmit={(e) => { e.preventDefault(); void load(null, true); }}>
        <input placeholder="Filter by actor user ID" value={actor} onChange={(e) => setActor(e.target.value)} style={{ minWidth: 300 }} />
        <button>Filter</button>
      </form>
      <ErrorBox message={action.error} />
      <Table rowKey={(r) => String(r.id)} rows={rows} empty={action.busy ? 'Loading…' : 'No entries.'} columns={[
        { key: 'created_at', label: 'When', render: (r) => formatDate(r.created_at) },
        { key: 'action', label: 'Action', render: (r) => <code>{r.action}</code> },
        { key: 'actor_id', label: 'Actor', render: (r) => r.actor_id ? <code>{r.actor_id.slice(0, 8)}</code> : 'system' },
        { key: 'target', label: 'Target', render: (r) => r.target_type ? `${r.target_type} ${r.target_id?.slice(0, 12) ?? ''}` : '—' },
        { key: 'details', label: 'Details', render: (r) => <code style={{ fontSize: 12 }}>{JSON.stringify(r.details).slice(0, 160)}</code> },
      ]} />
      {!done && rows.length > 0 && <button className="secondary" style={{ marginTop: 10 }} disabled={action.busy} onClick={() => load(rows[rows.length - 1].id, false)}>Load older</button>}
    </Page>
  );
}
