import { rpc } from '../supabase';
import { Loadable, Page, Stat, Table, useLoad } from '../ui';

interface Analytics {
  users_total: number; users_new_7d: number; live_now: number; viewers_now: number; streams_24h: number;
  gift_coins_24h: number; paid_orders_24h: number; open_reports: number; urgent_reports: number; open_appeals: number;
  pending_payouts: number; system_balances: Record<string, number> | null; daily_signups: { day: string; n: number }[];
}

export function Overview() {
  const state = useLoad(() => rpc<Analytics>('staff_analytics'));
  return (
    <Page title="Overview" actions={<button className="secondary" onClick={state.reload}>Refresh</button>}>
      <Loadable state={state}>{(a) => <>
        <div className="stats">
          <Stat label="Users" value={a.users_total.toLocaleString()} />
          <Stat label="New users, 7 days" value={a.users_new_7d.toLocaleString()} />
          <Stat label="Live now" value={a.live_now} />
          <Stat label="Viewers now" value={a.viewers_now.toLocaleString()} />
          <Stat label="Streams, 24 h" value={a.streams_24h} />
          <Stat label="Gift coins, 24 h" value={a.gift_coins_24h.toLocaleString()} />
          <Stat label="Paid orders, 24 h" value={a.paid_orders_24h} />
          <Stat label="Open reports" value={<>{a.open_reports}{a.urgent_reports > 0 && <span className="pill red" style={{ marginLeft: 8 }}>{a.urgent_reports} urgent</span>}</>} />
          <Stat label="Open appeals" value={a.open_appeals} />
          <Stat label="Payouts to review" value={a.pending_payouts} />
        </div>
        {a.system_balances && <>
          <h2>System ledger accounts</h2>
          <p className="muted">Issuance accounts are negative by design: they mirror every coin and bean in circulation. Coin revenue is in coins, not ETB.</p>
          <Table rowKey={(r) => r.kind} rows={Object.entries(a.system_balances).map(([kind, balance]) => ({ kind, balance }))}
            columns={[{ key: 'kind', label: 'Account' }, { key: 'balance', label: 'Balance', render: (r) => r.balance.toLocaleString() }]} />
        </>}
        <h2 style={{ marginTop: 16 }}>Sign-ups, last 30 days</h2>
        <Table rowKey={(r) => r.day} rows={a.daily_signups} empty="No sign-ups yet."
          columns={[{ key: 'day', label: 'Day' }, { key: 'n', label: 'New users' }]} />
      </>}</Loadable>
    </Page>
  );
}
