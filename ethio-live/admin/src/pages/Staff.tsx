import { useState } from 'react';
import { rpc } from '../supabase';
import { formatDate } from '../errors';
import type { StaffRole } from '../roles';
import { ErrorBox, Loadable, Page, Select, Table, useAction, useLoad } from '../ui';

interface Member { user_id: string; username: string; role: StaffRole; active: boolean; created_at: string }
const ROLES = ['support', 'moderator', 'finance', 'admin', 'superadmin'] as const;

export function Staff({ role, userId }: { role: StaffRole; userId: string }) {
  const state = useLoad(() => rpc<Member[]>('staff_list_members'));
  const [username, setUsername] = useState('');
  const [newRole, setNewRole] = useState<StaffRole>('support');
  const action = useAction();
  const isSuper = role === 'superadmin';
  const setRole = (user: string, r: StaffRole, active: boolean) => action.run(() => rpc('staff_set_role', { p_user: user, p_role: r, p_active: active }), state.reload);
  const add = () => action.run(async () => {
    const hits = await rpc<{ id: string; username: string }[]>('staff_search_users', { p_query: username.replace(/^@/, ''), p_limit: 5 });
    const exact = hits.find((h) => h.username.toLowerCase() === username.replace(/^@/, '').toLowerCase());
    if (!exact) throw new Error('No user with that exact username.');
    await rpc('staff_set_role', { p_user: exact.id, p_role: newRole, p_active: true });
    setUsername('');
  }, state.reload);
  return (
    <Page title="Staff">
      <p className="muted">Only a superadmin can grant or remove roles, and only with two-step verification. Give each person the lowest role they need.</p>
      <ErrorBox message={action.error} />
      {isSuper && <div className="row" style={{ marginBottom: 12 }}>
        <input placeholder="@username" value={username} onChange={(e) => setUsername(e.target.value)} />
        <Select label="Role" value={newRole} options={ROLES} onChange={setNewRole} />
        <button disabled={action.busy || !username.trim()} onClick={add}>Add staff member</button>
      </div>}
      <Loadable state={state}>{(rows) => <Table rowKey={(r) => r.user_id} rows={rows} columns={[
        { key: 'username', label: 'User', render: (r) => '@' + r.username },
        { key: 'role', label: 'Role', render: (r) => isSuper && r.user_id !== userId
          ? <Select value={r.role} options={ROLES} onChange={(v) => void setRole(r.user_id, v, r.active)} /> : r.role },
        { key: 'active', label: 'Active', render: (r) => isSuper && r.user_id !== userId
          ? <button className="secondary" onClick={() => void setRole(r.user_id, r.role, !r.active)}>{r.active ? 'Deactivate' : 'Activate'}</button> : (r.active ? 'yes' : 'no') },
        { key: 'created_at', label: 'Since', render: (r) => formatDate(r.created_at) },
      ]} />}</Loadable>
    </Page>
  );
}
