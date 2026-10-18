import { useState } from 'react';
import { rpc } from '../supabase';
import { Loadable, Page, Select, Table, useLoad } from '../ui';

const BOARDS = ['creators', 'supporters', 'pk'] as const;
const PERIODS = ['daily', 'weekly', 'monthly'] as const;
interface Row { rank: number; user_id: string; username: string; display_name: string; level: number; score: number }

export function Rankings() {
  const [board, setBoard] = useState<(typeof BOARDS)[number]>('creators');
  const [period, setPeriod] = useState<(typeof PERIODS)[number]>('weekly');
  const state = useLoad(() => rpc<Row[]>('get_rankings', { p_board: board, p_period: period, p_limit: 100 }), [board, period]);
  return (
    <Page title="Rankings" actions={<><Select label="Board" value={board} options={BOARDS} onChange={setBoard} /><Select label="Period" value={period} options={PERIODS} onChange={setPeriod} /></>}>
      <p className="muted">Rankings are computed from ledger-backed gift transactions and finished PK matches; they cannot be edited. To deal with manipulation, act on the accounts involved (Users) and, if needed, reverse coins through a ledger adjustment.</p>
      <Loadable state={state}>{(rows) => <Table rowKey={(r) => r.user_id} rows={rows} empty="No activity in this period."
        columns={[{ key: 'rank', label: '#' }, { key: 'username', label: 'User', render: (r) => `@${r.username}` }, { key: 'display_name', label: 'Name' },
          { key: 'level', label: 'Level' }, { key: 'score', label: board === 'pk' ? 'Wins' : 'Coins' }]} />}</Loadable>
    </Page>
  );
}
