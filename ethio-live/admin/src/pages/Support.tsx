import { useState } from 'react';
import { rpc } from '../supabase';
import { formatDate } from '../errors';
import { ErrorBox, Loadable, Modal, Page, Select, Table, useAction, useLoad } from '../ui';

interface Ticket { id: string; created_at: string; username: string | null; topic: string; message: string; status: string; staff_reply: string | null }
const STATUSES = ['open', 'answered', 'closed'] as const;

export function Support() {
  const [status, setStatus] = useState<(typeof STATUSES)[number]>('open');
  const state = useLoad(() => rpc<Ticket[]>('staff_support_queue', { p_status: status }), [status]);
  const [open, setOpen] = useState<Ticket | null>(null);
  const [reply, setReply] = useState('');
  const action = useAction();
  const send = (close: boolean) => open && action.run(
    () => rpc('staff_reply_support', { p_request: open.id, p_reply: reply.trim(), p_close: close }),
    () => { setOpen(null); setReply(''); void state.reload(); });
  return (
    <Page title="Support" actions={<Select label="Status" value={status} options={STATUSES} onChange={setStatus} />}>
      <Loadable state={state}>{(rows) => (
        <Table rowKey={(r) => r.id} rows={rows} empty="No requests." columns={[
          { key: 'topic', label: 'Topic', render: (r) => <button className="link" onClick={() => { setOpen(r); setReply(r.staff_reply ?? ''); }}>{r.topic}</button> },
          { key: 'username', label: 'User', render: (r) => r.username ? '@' + r.username : '—' },
          { key: 'message', label: 'Message', render: (r) => r.message.length > 90 ? r.message.slice(0, 90) + '…' : r.message },
          { key: 'created_at', label: 'Received', render: (r) => formatDate(r.created_at) },
        ]} />
      )}</Loadable>
      {open && (
        <Modal title={`Support: ${open.topic}`} onClose={() => setOpen(null)}>
          <div className="stack">
            <p style={{ whiteSpace: 'pre-wrap' }}>{open.message}</p>
            <p className="muted">Your reply is sent to the user as an in-app notification. Never ask for passwords or verification codes.</p>
            <textarea value={reply} onChange={(e) => setReply(e.target.value)} placeholder="Reply" />
            <ErrorBox message={action.error} />
            <div className="row">
              <button disabled={action.busy || reply.trim().length < 2} onClick={() => send(false)}>Send reply</button>
              <button className="secondary" disabled={action.busy || reply.trim().length < 2} onClick={() => send(true)}>Reply and close</button>
            </div>
          </div>
        </Modal>
      )}
    </Page>
  );
}
