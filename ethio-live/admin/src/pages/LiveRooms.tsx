import { useState } from 'react';
import { rpc } from '../supabase';
import { formatDate } from '../errors';
import { ErrorBox, Loadable, Modal, Page, Table, useAction, useLoad } from '../ui';

interface Room {
  id: string; title: string; mode: string; category: string; language: string; viewer_count: number; started_at: string;
  audience: string; in_pk: boolean; total_gift_coins: number; open_reports: number;
  host: { id: string; username: string; display_name: string };
}

export function LiveRooms() {
  const state = useLoad(() => rpc<Room[]>('staff_live_rooms'));
  const [ending, setEnding] = useState<Room | null>(null);
  const [reason, setReason] = useState('');
  const action = useAction();
  return (
    <Page title="Live rooms" actions={<button className="secondary" onClick={state.reload}>Refresh</button>}>
      <Loadable state={state}>{(rows) => (
        <Table rowKey={(r) => r.id} rows={rows} empty="No one is live right now." columns={[
          { key: 'title', label: 'Title' },
          { key: 'host', label: 'Host', render: (r) => `@${r.host.username}` },
          { key: 'mode', label: 'Mode', render: (r) => r.mode + (r.in_pk ? ' · PK' : '') },
          { key: 'category', label: 'Category' },
          { key: 'viewer_count', label: 'Viewers' },
          { key: 'total_gift_coins', label: 'Gift coins' },
          { key: 'open_reports', label: 'Reports', render: (r) => r.open_reports > 0 ? <span className="pill red">{r.open_reports}</span> : '0' },
          { key: 'started_at', label: 'Started', render: (r) => formatDate(r.started_at) },
          { key: 'actions', label: '', render: (r) => <button className="danger" onClick={() => { setEnding(r); setReason(''); }}>End</button> },
        ]} />
      )}</Loadable>
      {ending && (
        <Modal title={`End "${ending.title}"`} onClose={() => setEnding(null)}>
          <div className="stack">
            <p className="muted">The stream ends for everyone, the host is notified, and the action is recorded against @{ending.host.username}.</p>
            <textarea placeholder="Reason (at least 5 characters)" value={reason} onChange={(e) => setReason(e.target.value)} />
            <ErrorBox message={action.error} />
            <button className="danger" disabled={action.busy || reason.trim().length < 5}
              onClick={() => action.run(() => rpc('staff_end_room', { p_room: ending.id, p_reason: reason.trim() }), () => { setEnding(null); void state.reload(); })}>End stream</button>
          </div>
        </Modal>
      )}
    </Page>
  );
}
