import { useState } from 'react';
import { rpc } from '../supabase';
import { formatDate } from '../errors';
import type { StaffRole } from '../roles';
import { ErrorBox, Loadable, Modal, Page, Select, Table, useAction, useLoad } from '../ui';

interface EventRow {
  id: string; title: string; description: string; rules: string; reward_description: string; metric: string;
  banner_key: string; starts_at: string; ends_at: string; published: boolean;
}
const METRICS = ['gift_coins_received', 'gift_coins_sent', 'pk_wins'] as const;
const toLocal = (iso: string) => iso ? new Date(iso).toISOString().slice(0, 16) : '';

export function Events({ role }: { role: StaffRole }) {
  const state = useLoad(() => rpc<EventRow[]>('staff_events'));
  const [edit, setEdit] = useState<EventRow | null>(null);
  const [board, setBoard] = useState<EventRow | null>(null);
  const canEdit = role === 'admin' || role === 'superadmin';
  const now = new Date();
  return (
    <Page title="Events" actions={canEdit && <button onClick={() => setEdit({ id: '', title: '', description: '', rules: '', reward_description: '', metric: 'gift_coins_received',
      banner_key: 'event_default', starts_at: now.toISOString(), ends_at: new Date(now.getTime() + 7 * 864e5).toISOString(), published: false })}>New event</button>}>
      <Loadable state={state}>{(rows) => (
        <Table rowKey={(r) => r.id} rows={rows} empty="No events yet." columns={[
          { key: 'title', label: 'Title', render: (r) => canEdit ? <button className="link" onClick={() => setEdit(r)}>{r.title}</button> : r.title },
          { key: 'metric', label: 'Scored by' },
          { key: 'starts_at', label: 'Starts', render: (r) => formatDate(r.starts_at) },
          { key: 'ends_at', label: 'Ends', render: (r) => formatDate(r.ends_at) },
          { key: 'published', label: 'Published', render: (r) => r.published ? <span className="pill green">yes</span> : <span className="pill">draft</span> },
          { key: 'board', label: '', render: (r) => <button className="secondary" onClick={() => setBoard(r)}>Leaderboard</button> },
        ]} />
      )}</Loadable>
      {edit && <EventEditor event={edit} onClose={() => { setEdit(null); void state.reload(); }} />}
      {board && <EventBoard event={board} onClose={() => setBoard(null)} />}
    </Page>
  );
}

function EventEditor({ event, onClose }: { event: EventRow; onClose: () => void }) {
  const [e, setE] = useState(event);
  const action = useAction();
  const set = <K extends keyof EventRow>(k: K, v: EventRow[K]) => setE({ ...e, [k]: v });
  const valid = e.title && e.description && e.rules && e.reward_description && new Date(e.ends_at) > new Date(e.starts_at);
  return (
    <Modal title={event.id ? 'Edit event' : 'New event'} onClose={onClose}>
      <div className="stack">
        <label className="field"><span>Title</span><input value={e.title} onChange={(x) => set('title', x.target.value)} /></label>
        <label className="field"><span>Description</span><textarea value={e.description} onChange={(x) => set('description', x.target.value)} /></label>
        <label className="field"><span>Rules (shown to users)</span><textarea value={e.rules} onChange={(x) => set('rules', x.target.value)} /></label>
        <label className="field"><span>Reward (describe exactly what winners get)</span><input value={e.reward_description} onChange={(x) => set('reward_description', x.target.value)} /></label>
        <div className="row">
          <Select label="Scored by" value={e.metric as (typeof METRICS)[number]} options={METRICS} onChange={(v) => set('metric', v)} />
          <label className="inline"><span>Starts (UTC)</span><input type="datetime-local" value={toLocal(e.starts_at)} onChange={(x) => set('starts_at', new Date(x.target.value + 'Z').toISOString())} /></label>
          <label className="inline"><span>Ends (UTC)</span><input type="datetime-local" value={toLocal(e.ends_at)} onChange={(x) => set('ends_at', new Date(x.target.value + 'Z').toISOString())} /></label>
        </div>
        <label className="inline"><input type="checkbox" checked={e.published} onChange={(x) => set('published', x.target.checked)} /> Published (visible in the app)</label>
        <ErrorBox message={action.error} />
        <button disabled={action.busy || !valid} onClick={() => action.run(() => rpc('staff_upsert_event', { p: e }), onClose)}>Save event</button>
      </div>
    </Modal>
  );
}

function EventBoard({ event, onClose }: { event: EventRow; onClose: () => void }) {
  const state = useLoad(() => rpc<{ rank: number; user_id: string; username: string; score: number }[]>('event_leaderboard', { p_event: event.id }), [event.id]);
  return (
    <Modal title={`${event.title}: leaderboard`} onClose={onClose}>
      <p className="muted">Computed by the server from ledger-backed gift and PK records inside the event window.</p>
      <Loadable state={state}>{(rows) => <Table rowKey={(r) => r.user_id} rows={rows} empty="No entries yet."
        columns={[{ key: 'rank', label: '#' }, { key: 'username', label: 'User', render: (r) => '@' + r.username }, { key: 'score', label: 'Score' }]} />}</Loadable>
    </Modal>
  );
}
