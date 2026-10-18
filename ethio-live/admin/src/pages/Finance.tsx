import { useState } from 'react';
import { rpc } from '../supabase';
import { formatDate, formatEtb } from '../errors';
import { ErrorBox, Json, Loadable, Modal, Page, Select, Table, useAction, useLoad } from '../ui';

interface Order {
  id: string; created_at: string; username: string; provider: string; package_id: string; coins: number; amount_minor: number;
  currency: string; status: string; provider_ref: string | null; failure_reason: string | null;
}
const ORDER_STATUSES = ['all', 'created', 'pending', 'paid', 'failed', 'refunded', 'cancelled'] as const;

export function Payments() {
  const [status, setStatus] = useState<(typeof ORDER_STATUSES)[number]>('all');
  const state = useLoad(() => rpc<Order[]>('staff_payment_orders', { p_status: status === 'all' ? null : status, p_limit: 300 }), [status]);
  return (
    <Page title="Payments" actions={<Select label="Status" value={status} options={ORDER_STATUSES} onChange={setStatus} />}>
      <p className="muted">Orders become paid only after the server verifies them with Google Play or Chapa. Refunds and chargebacks reported by the provider reverse the coins automatically.</p>
      <Loadable state={state}>{(rows) => <Table rowKey={(r) => r.id} rows={rows} empty="No orders." columns={[
        { key: 'created_at', label: 'Created', render: (r) => formatDate(r.created_at) },
        { key: 'username', label: 'User', render: (r) => '@' + r.username },
        { key: 'provider', label: 'Provider' },
        { key: 'package_id', label: 'Package', render: (r) => `${r.package_id} (${r.coins} coins)` },
        { key: 'amount_minor', label: 'Amount', render: (r) => r.currency === 'ETB' ? formatEtb(r.amount_minor) : `${(r.amount_minor / 100).toFixed(2)} ${r.currency}` },
        { key: 'status', label: 'Status', render: (r) => <span className={'pill ' + (r.status === 'paid' ? 'green' : r.status === 'failed' || r.status === 'refunded' ? 'red' : '')}>{r.status}</span> },
        { key: 'provider_ref', label: 'Provider ref', render: (r) => r.provider_ref ? <code>{r.provider_ref.slice(0, 24)}</code> : '—' },
        { key: 'failure_reason', label: 'Failure' },
      ]} />}</Loadable>
    </Page>
  );
}

interface Payout {
  id: string; created_at: string; creator_id: string; username: string; beans: number; net_etb_minor: number; fee_etb_minor: number;
  method: { method?: string; account?: string; account_name?: string }; status: string; kyc_status: string; first_approver: string | null;
  needs_second_approval: boolean; account_age_days: number; open_reports: number;
}
const PAYOUT_STATUSES = ['all', 'requested', 'under_review', 'approved', 'paid', 'rejected', 'failed'] as const;

export function Payouts({ userId }: { userId: string }) {
  const [status, setStatus] = useState<(typeof PAYOUT_STATUSES)[number]>('requested');
  const state = useLoad(() => rpc<Payout[]>('staff_payouts', { p_status: status === 'all' ? null : status }), [status]);
  const [open, setOpen] = useState<Payout | null>(null);
  return (
    <Page title="Payouts" actions={<Select label="Status" value={status} options={PAYOUT_STATUSES} onChange={setStatus} />}>
      <p className="muted">Large payouts need two different finance approvers. Approving does not send money: pay through the provider, then record the result here.</p>
      <Loadable state={state}>{(rows) => <Table rowKey={(r) => r.id} rows={rows} empty="No payouts." columns={[
        { key: 'username', label: 'Creator', render: (r) => <button className="link" onClick={() => setOpen(r)}>@{r.username}</button> },
        { key: 'beans', label: 'Beans', render: (r) => r.beans.toLocaleString() },
        { key: 'net_etb_minor', label: 'Pay out', render: (r) => formatEtb(r.net_etb_minor) },
        { key: 'method', label: 'Method', render: (r) => r.method.method ?? '—' },
        { key: 'kyc_status', label: 'KYC', render: (r) => <span className={'pill ' + (r.kyc_status === 'verified' ? 'green' : 'red')}>{r.kyc_status}</span> },
        { key: 'risk', label: 'Risk signals', render: (r) => [r.account_age_days < 30 && `account ${r.account_age_days} days old`, r.open_reports > 0 && `${r.open_reports} open reports`,
          r.needs_second_approval && 'needs 2 approvers'].filter(Boolean).join(' · ') || '—' },
        { key: 'status', label: 'Status' },
        { key: 'created_at', label: 'Requested', render: (r) => formatDate(r.created_at) },
      ]} />}</Loadable>
      {open && <PayoutDetail payout={open} userId={userId} onClose={() => { setOpen(null); void state.reload(); }} />}
    </Page>
  );
}

function PayoutDetail({ payout, userId, onClose }: { payout: Payout; userId: string; onClose: () => void }) {
  const [reason, setReason] = useState('');
  const [ref, setRef] = useState('');
  const action = useAction();
  const reviewable = payout.status === 'requested' || payout.status === 'under_review';
  const iApprovedFirst = payout.first_approver === userId;
  return (
    <Modal title={`Payout for @${payout.username}`} onClose={onClose}>
      <div className="stack">
        <div className="grid2">
          <div><span className="muted">Beans</span><br />{payout.beans.toLocaleString()}</div>
          <div><span className="muted">Fee / net</span><br />{formatEtb(payout.fee_etb_minor)} / <b>{formatEtb(payout.net_etb_minor)}</b></div>
          <div><span className="muted">Status</span><br />{payout.status}</div>
          <div><span className="muted">KYC</span><br />{payout.kyc_status}</div>
        </div>
        <h2>Pay to</h2>
        <Json value={payout.method} />
        {reviewable && <>
          {iApprovedFirst && <p className="notice">You gave the first approval. A different finance approver must give the second.</p>}
          <textarea placeholder="Reason (required to reject, recorded in the audit log)" value={reason} onChange={(e) => setReason(e.target.value)} />
          <ErrorBox message={action.error} />
          <div className="row">
            <button disabled={action.busy || iApprovedFirst} onClick={() => action.run(() => rpc('staff_review_payout', { p_payout: payout.id, p_approve: true, p_reason: reason || null }), onClose)}>
              {payout.needs_second_approval && !payout.first_approver ? 'Give first approval' : 'Approve'}</button>
            <button className="danger" disabled={action.busy || reason.trim().length < 5}
              onClick={() => action.run(() => rpc('staff_review_payout', { p_payout: payout.id, p_approve: false, p_reason: reason.trim() }), onClose)}>Reject and return beans</button>
          </div>
        </>}
        {payout.status === 'approved' && <>
          <p className="muted">After sending the money, record the provider's transaction reference. If the transfer failed, record it as failed and the beans go back to the creator.</p>
          <input placeholder="Provider transaction reference" value={ref} onChange={(e) => setRef(e.target.value)} />
          <ErrorBox message={action.error} />
          <div className="row">
            <button disabled={action.busy || ref.trim().length < 3} onClick={() => action.run(() => rpc('staff_mark_payout_paid', { p_payout: payout.id, p_provider_ref: ref.trim(), p_success: true }), onClose)}>Mark paid</button>
            <button className="danger" disabled={action.busy} onClick={() => action.run(() => rpc('staff_mark_payout_paid', { p_payout: payout.id, p_provider_ref: ref.trim() || null, p_success: false }), onClose)}>Mark failed</button>
          </div>
        </>}
      </div>
    </Modal>
  );
}

interface Adjustment {
  id: string; created_at: string; account_id: string; owner_username: string | null; account_kind: string; amount: number; unit: string;
  reason: string; requested_by: string; requested_by_username: string; approved_by: string | null; status: string;
}
interface UserHit { id: string; username: string }
interface Account { id: string; kind: string; balance: number }
const ADJ_STATUSES = ['pending', 'applied', 'rejected'] as const;

export function Adjustments({ userId }: { userId: string }) {
  const [status, setStatus] = useState<(typeof ADJ_STATUSES)[number]>('pending');
  const state = useLoad(() => rpc<Adjustment[]>('staff_adjustments', { p_status: status }), [status]);
  const [creating, setCreating] = useState(false);
  const action = useAction();
  return (
    <Page title="Ledger adjustments" actions={<><Select label="Status" value={status} options={ADJ_STATUSES} onChange={setStatus} /><button onClick={() => setCreating(true)}>Request adjustment</button></>}>
      <p className="muted">The ledger is append-only. A correction is a new balanced transaction: one finance user requests it and a different one approves it.</p>
      <ErrorBox message={action.error} />
      <Loadable state={state}>{(rows) => <Table rowKey={(r) => r.id} rows={rows} empty="Nothing here." columns={[
        { key: 'owner_username', label: 'Account', render: (r) => `@${r.owner_username ?? '?'} · ${r.account_kind}` },
        { key: 'amount', label: 'Amount', render: (r) => `${r.amount > 0 ? '+' : ''}${r.amount} ${r.unit}` },
        { key: 'reason', label: 'Reason' },
        { key: 'requested_by_username', label: 'Requested by', render: (r) => '@' + r.requested_by_username },
        { key: 'created_at', label: 'When', render: (r) => formatDate(r.created_at) },
        { key: 'decide', label: '', render: (r) => r.status === 'pending' && r.requested_by !== userId ? <div className="row">
          <button disabled={action.busy} onClick={() => action.run(() => rpc('staff_decide_adjustment', { p_request: r.id, p_approve: true }), state.reload)}>Approve</button>
          <button className="secondary" disabled={action.busy} onClick={() => action.run(() => rpc('staff_decide_adjustment', { p_request: r.id, p_approve: false }), state.reload)}>Reject</button>
        </div> : r.status === 'pending' ? <span className="muted">awaiting another approver</span> : null },
      ]} />}</Loadable>
      {creating && <NewAdjustment onClose={() => { setCreating(false); void state.reload(); }} />}
    </Page>
  );
}

function NewAdjustment({ onClose }: { onClose: () => void }) {
  const [query, setQuery] = useState('');
  const [hits, setHits] = useState<UserHit[]>([]);
  const [accounts, setAccounts] = useState<Account[] | null>(null);
  const [account, setAccount] = useState('');
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');
  const action = useAction();
  const selected = accounts?.find((a) => a.id === account);
  const unit = selected?.kind.includes('bean') || selected?.kind === 'payout_hold' ? 'bean' : 'coin';
  return (
    <Modal title="Request a ledger adjustment" onClose={onClose}>
      <div className="stack">
        <form className="row" onSubmit={(e) => { e.preventDefault(); void action.run(async () => setHits(await rpc<UserHit[]>('staff_search_users', { p_query: query, p_limit: 10 }))); }}>
          <input placeholder="Find user" value={query} onChange={(e) => setQuery(e.target.value)} /><button>Search</button>
        </form>
        {hits.length > 0 && <div className="row">{hits.map((h) => <button key={h.id} className="secondary"
          onClick={() => action.run(async () => { setAccounts(await rpc<Account[]>('staff_wallet_accounts', { p_user: h.id })); setAccount(''); })}>@{h.username}</button>)}</div>}
        {accounts && <label className="field"><span>Account</span>
          <select value={account} onChange={(e) => setAccount(e.target.value)}>
            <option value="">Choose…</option>
            {accounts.map((a) => <option key={a.id} value={a.id}>{a.kind} (balance {a.balance})</option>)}
          </select></label>}
        {selected && <>
          <label className="field"><span>Amount in {unit}s (negative to remove)</span><input type="number" value={amount} onChange={(e) => setAmount(e.target.value)} /></label>
          <textarea placeholder="Reason (at least 10 characters, e.g. ticket number and what happened)" value={reason} onChange={(e) => setReason(e.target.value)} />
        </>}
        <ErrorBox message={action.error} />
        <button disabled={action.busy || !selected || !Number(amount) || reason.trim().length < 10}
          onClick={() => action.run(() => rpc('staff_request_adjustment', { p_account: account, p_amount: Number(amount), p_unit: unit, p_reason: reason.trim() }), onClose)}>Submit for approval</button>
      </div>
    </Modal>
  );
}
