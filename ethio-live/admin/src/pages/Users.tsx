import { useState } from 'react';
import { rpc } from '../supabase';
import { formatDate } from '../errors';
import type { StaffRole } from '../roles';
import { ErrorBox, Json, Loadable, Modal, Page, Select, Table, useAction, useLoad } from '../ui';

interface UserRow {
  id: string; username: string; display_name: string; account_status: string; age_band: string; level: number;
  is_creator: boolean; is_verified: boolean; follower_count: number; created_at: string; open_reports: number;
}
const STATUSES = ['any', 'active', 'warned', 'suspended', 'banned', 'deleted'] as const;
export const MODERATION_KINDS = ['warn', 'mute', 'suspend', 'broadcast_ban', 'end_stream', 'remove_content', 'ban', 'restore'] as const;
const KYC = ['none', 'pending', 'verified', 'rejected'] as const;

export function Users({ role }: { role: StaffRole }) {
  const [query, setQuery] = useState('');
  const [submitted, setSubmitted] = useState('');
  const [status, setStatus] = useState<(typeof STATUSES)[number]>('any');
  const [open, setOpen] = useState<UserRow | null>(null);
  const state = useLoad(() => rpc<UserRow[]>('staff_search_users',
    { p_query: submitted, p_status: status === 'any' ? null : status, p_limit: 100 }), [submitted, status]);
  return (
    <Page title="Users">
      <form className="row" style={{ marginBottom: 12 }} onSubmit={(e) => { e.preventDefault(); setSubmitted(query.trim()); }}>
        <input placeholder="Username, name or user ID" value={query} onChange={(e) => setQuery(e.target.value)} style={{ minWidth: 260 }} />
        <Select label="Status" value={status} options={STATUSES} onChange={setStatus} />
        <button>Search</button>
      </form>
      <Loadable state={state}>{(rows) => (
        <Table rowKey={(r) => r.id} rows={rows} columns={[
          { key: 'username', label: 'User', render: (r) => <button className="link" onClick={() => setOpen(r)}>@{r.username}</button> },
          { key: 'display_name', label: 'Name' },
          { key: 'account_status', label: 'Status', render: (r) => <span className={'pill ' + (r.account_status === 'active' ? 'green' : r.account_status === 'warned' ? 'gold' : 'red')}>{r.account_status}</span> },
          { key: 'age_band', label: 'Age' },
          { key: 'level', label: 'Level' },
          { key: 'follower_count', label: 'Followers' },
          { key: 'flags', label: 'Flags', render: (r) => [r.is_creator && 'creator', r.is_verified && 'verified'].filter(Boolean).join(', ') || '—' },
          { key: 'open_reports', label: 'Open reports', render: (r) => r.open_reports > 0 ? <span className="pill red">{r.open_reports}</span> : '0' },
          { key: 'created_at', label: 'Joined', render: (r) => formatDate(r.created_at) },
        ]} />
      )}</Loadable>
      {open && <UserDetail user={open} role={role} onClose={() => { setOpen(null); void state.reload(); }} />}
    </Page>
  );
}

interface Detail {
  profile: Record<string, unknown>; private: Record<string, unknown> | null; balances: Record<string, number> | null;
  moderation_history: { id: string; kind: string; reason: string; created_at: string; expires_at: string | null }[];
  reports_against: number; recent_rooms: { id: string; title: string; status: string; started_at: string | null; end_reason: string | null }[];
}

export function UserDetail({ user, role, onClose, reportId, roomId }:
  { user: { id: string; username: string }; role: StaffRole; onClose: () => void; reportId?: string; roomId?: string }) {
  const state = useLoad(() => rpc<Detail>('staff_user_detail', { p_user: user.id }), [user.id]);
  const [kind, setKind] = useState<(typeof MODERATION_KINDS)[number]>('warn');
  const [reason, setReason] = useState('');
  const [hours, setHours] = useState('24');
  const [kyc, setKyc] = useState<(typeof KYC)[number]>('verified');
  const [kycRef, setKycRef] = useState('');
  const action = useAction();
  const [done, setDone] = useState<string | null>(null);
  const canModerate = ['moderator', 'admin', 'superadmin'].includes(role);
  const canKyc = ['finance', 'superadmin'].includes(role);
  const timed = kind === 'suspend' || kind === 'broadcast_ban' || kind === 'mute';
  return (
    <Modal title={`@${user.username}`} onClose={onClose}>
      <Loadable state={state}>{(d) => (
        <div className="stack">
          <div className="grid2">
            <div><span className="muted">Status</span><br />{String(d.profile.account_status)}</div>
            <div><span className="muted">Reports against</span><br />{d.reports_against}</div>
            {d.private && <div><span className="muted">Age / KYC</span><br />{String(d.private.age)} · {String(d.private.kyc_status)}</div>}
            {d.balances && <div><span className="muted">Coins / beans</span><br />{d.balances.coins} coins · {d.balances.beans} beans ({d.balances.beans_pending} pending)</div>}
          </div>
          <h2>Moderation history</h2>
          <Table rowKey={(r) => r.id} rows={d.moderation_history} empty="No actions."
            columns={[{ key: 'kind', label: 'Action' }, { key: 'reason', label: 'Reason' },
              { key: 'created_at', label: 'When', render: (r) => formatDate(r.created_at) },
              { key: 'expires_at', label: 'Until', render: (r) => formatDate(r.expires_at) }]} />
          <h2>Recent lives</h2>
          <Table rowKey={(r) => r.id} rows={d.recent_rooms} empty="No lives."
            columns={[{ key: 'title', label: 'Title' }, { key: 'status', label: 'Status' },
              { key: 'started_at', label: 'Started', render: (r) => formatDate(r.started_at) }, { key: 'end_reason', label: 'End reason' }]} />
          {canModerate && (
            <div className="stack">
              <h2>Take action</h2>
              <div className="row">
                <Select label="Action" value={kind} options={MODERATION_KINDS} onChange={setKind} />
                {timed && <label className="inline"><span>Hours (0 = indefinite)</span><input type="number" min={0} value={hours} onChange={(e) => setHours(e.target.value)} style={{ width: 90 }} /></label>}
              </div>
              <textarea placeholder="Reason shown to the user and kept in the audit log (at least 5 characters)" value={reason} onChange={(e) => setReason(e.target.value)} />
              {kind === 'ban' && role !== 'admin' && role !== 'superadmin' && <p className="notice">Only admins can ban permanently.</p>}
              <ErrorBox message={action.error} />
              {done && <p className="ok">{done}</p>}
              <div className="row"><button className={kind === 'ban' ? 'danger' : ''} disabled={action.busy || reason.trim().length < 5}
                onClick={() => action.run(() => rpc('staff_moderate', { p_user: user.id, p_kind: kind, p_reason: reason.trim(),
                  p_hours: timed ? Number(hours) || null : null, p_report: reportId ?? null, p_room: roomId ?? null }),
                () => { setDone(`Applied: ${kind}`); setReason(''); void state.reload(); })}>Apply</button></div>
            </div>
          )}
          {canKyc && (
            <div className="stack">
              <h2>Identity verification (KYC)</h2>
              <p className="muted">Record the outcome of an identity check done outside this dashboard. Keep the reference to the check, not the documents themselves.</p>
              <div className="row">
                <Select label="Status" value={kyc} options={KYC} onChange={setKyc} />
                <input placeholder="Reference (case number)" value={kycRef} onChange={(e) => setKycRef(e.target.value)} />
                <button disabled={action.busy} onClick={() => action.run(() => rpc('staff_set_kyc', { p_user: user.id, p_status: kyc, p_reference: kycRef || null }),
                  () => { setDone(`KYC set to ${kyc}`); void state.reload(); })}>Save KYC</button>
              </div>
            </div>
          )}
          <details><summary className="muted">Raw profile</summary><Json value={d.profile} /></details>
        </div>
      )}</Loadable>
    </Modal>
  );
}
