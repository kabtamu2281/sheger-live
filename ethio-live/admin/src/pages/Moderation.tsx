import { useState } from 'react';
import { rpc } from '../supabase';
import { formatDate } from '../errors';
import type { StaffRole } from '../roles';
import { ErrorBox, Json, Loadable, Modal, Page, Select, Table, useAction, useLoad } from '../ui';
import { UserDetail } from './Users';

interface Report {
  id: string; created_at: string; reason: string; priority: number; target_type: string; target_id: string;
  target_user_id: string | null; target_username: string | null; reporter_username: string; details: string | null;
  evidence: Record<string, unknown>; status: string; prior_actions: number;
}
const REPORT_STATUSES = ['open', 'escalated', 'reviewing', 'actioned', 'dismissed'] as const;

export function Reports({ role }: { role: StaffRole }) {
  const [status, setStatus] = useState<(typeof REPORT_STATUSES)[number]>('open');
  const state = useLoad(() => rpc<Report[]>('staff_report_queue', { p_status: status, p_limit: 200 }), [status]);
  const [open, setOpen] = useState<Report | null>(null);
  return (
    <Page title="Reports" actions={<><Select label="Status" value={status} options={REPORT_STATUSES} onChange={setStatus} /><button className="secondary" onClick={state.reload}>Refresh</button></>}>
      <p className="muted">Sorted by priority: reports about minors and illegal content come first.</p>
      <Loadable state={state}>{(rows) => (
        <Table rowKey={(r) => r.id} rows={rows} empty="The queue is empty." columns={[
          { key: 'priority', label: 'P', render: (r) => <span className={'pill ' + (r.priority === 1 ? 'red' : r.priority === 2 ? 'gold' : '')}>{r.priority}</span> },
          { key: 'reason', label: 'Reason', render: (r) => <button className="link" onClick={() => setOpen(r)}>{r.reason.replace(/_/g, ' ')}</button> },
          { key: 'target_type', label: 'Target', render: (r) => `${r.target_type.replace(/_/g, ' ')}${r.target_username ? ' · @' + r.target_username : ''}` },
          { key: 'prior_actions', label: 'Prior actions' },
          { key: 'reporter_username', label: 'Reporter', render: (r) => '@' + r.reporter_username },
          { key: 'created_at', label: 'Reported', render: (r) => formatDate(r.created_at) },
        ]} />
      )}</Loadable>
      {open && <ReportDetail report={open} role={role} onClose={() => { setOpen(null); void state.reload(); }} />}
    </Page>
  );
}

function ReportDetail({ report, role, onClose }: { report: Report; role: StaffRole; onClose: () => void }) {
  const [note, setNote] = useState('');
  const [acting, setActing] = useState(false);
  const action = useAction();
  const canAct = ['moderator', 'admin', 'superadmin'].includes(role);
  if (acting && report.target_user_id) {
    return <UserDetail user={{ id: report.target_user_id, username: report.target_username ?? 'user' }} role={role} onClose={onClose}
      reportId={report.id} roomId={report.target_type === 'room' ? report.target_id : undefined} />;
  }
  const resolve = (status: 'dismissed' | 'escalated' | 'reviewing') =>
    action.run(() => rpc('staff_resolve_report', { p_report: report.id, p_status: status, p_resolution: note.trim() || status }), onClose);
  return (
    <Modal title={`Report: ${report.reason.replace(/_/g, ' ')}`} onClose={onClose}>
      <div className="stack">
        <div className="grid2">
          <div><span className="muted">Target</span><br />{report.target_type} {report.target_username && `· @${report.target_username}`}</div>
          <div><span className="muted">Reporter</span><br />@{report.reporter_username}</div>
          <div><span className="muted">Status</span><br />{report.status}</div>
          <div><span className="muted">Prior actions on target</span><br />{report.prior_actions}</div>
        </div>
        {report.details && <p>{report.details}</p>}
        <h2>Evidence</h2>
        <p className="muted">Captured by the server when the report was filed. Access is limited to moderators and is logged.</p>
        <Json value={report.evidence} />
        {canAct && <>
          <textarea placeholder="Resolution note" value={note} onChange={(e) => setNote(e.target.value)} />
          <ErrorBox message={action.error} />
          <div className="row">
            {report.target_user_id && <button onClick={() => setActing(true)}>Take action on @{report.target_username}</button>}
            <button className="secondary" disabled={action.busy} onClick={() => resolve('reviewing')}>Mark reviewing</button>
            <button className="secondary" disabled={action.busy} onClick={() => resolve('escalated')}>Escalate</button>
            <button className="secondary" disabled={action.busy} onClick={() => resolve('dismissed')}>Dismiss</button>
          </div>
        </>}
      </div>
    </Modal>
  );
}

interface Appeal {
  id: string; created_at: string; user_id: string; username: string; message: string; action_kind: string;
  action_reason: string; action_at: string; status: string;
}
const APPEAL_STATUSES = ['open', 'upheld', 'overturned'] as const;

export function Appeals() {
  const [status, setStatus] = useState<(typeof APPEAL_STATUSES)[number]>('open');
  const state = useLoad(() => rpc<Appeal[]>('staff_appeals', { p_status: status }), [status]);
  const [open, setOpen] = useState<Appeal | null>(null);
  const [note, setNote] = useState('');
  const action = useAction();
  const decide = (overturn: boolean) => open && action.run(
    () => rpc('staff_decide_appeal', { p_appeal: open.id, p_overturn: overturn, p_note: note.trim() }),
    () => { setOpen(null); setNote(''); void state.reload(); });
  return (
    <Page title="Appeals" actions={<Select label="Status" value={status} options={APPEAL_STATUSES} onChange={setStatus} />}>
      <p className="muted">The moderator who took an action cannot decide its appeal; the server enforces this.</p>
      <Loadable state={state}>{(rows) => (
        <Table rowKey={(r) => r.id} rows={rows} empty="No appeals." columns={[
          { key: 'username', label: 'User', render: (r) => <button className="link" onClick={() => setOpen(r)}>@{r.username}</button> },
          { key: 'action_kind', label: 'Action' },
          { key: 'action_reason', label: 'Original reason' },
          { key: 'created_at', label: 'Appealed', render: (r) => formatDate(r.created_at) },
          { key: 'status', label: 'Status' },
        ]} />
      )}</Loadable>
      {open && (
        <Modal title={`Appeal from @${open.username}`} onClose={() => setOpen(null)}>
          <div className="stack">
            <p><span className="muted">Action:</span> {open.action_kind} on {formatDate(open.action_at)} — {open.action_reason}</p>
            <p><span className="muted">User says:</span> {open.message}</p>
            {open.status === 'open' && <>
              <textarea placeholder="Decision note (sent to the user)" value={note} onChange={(e) => setNote(e.target.value)} />
              <ErrorBox message={action.error} />
              <div className="row">
                <button disabled={action.busy || note.trim().length < 3} onClick={() => decide(true)}>Overturn and restore</button>
                <button className="secondary" disabled={action.busy || note.trim().length < 3} onClick={() => decide(false)}>Uphold</button>
              </div>
            </>}
          </div>
        </Modal>
      )}
    </Page>
  );
}
