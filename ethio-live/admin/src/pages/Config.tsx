import { useState } from 'react';
import { rpc } from '../supabase';
import { formatDate } from '../errors';
import { ECONOMIC_KEYS, type StaffRole } from '../roles';
import { ErrorBox, Loadable, Modal, Page, Table, useAction, useLoad } from '../ui';

interface ConfigRow { key: string; value: unknown; description: string; updated_at: string; updated_by: string | null }

export function Config({ role }: { role: StaffRole }) {
  const state = useLoad(() => rpc<ConfigRow[]>('staff_config'));
  const [edit, setEdit] = useState<ConfigRow | null>(null);
  const [text, setText] = useState('');
  const action = useAction();
  const mayEdit = (key: string) => role === 'superadmin' || (ECONOMIC_KEYS.has(key) ? role === 'finance' : role === 'admin');
  let parsed: unknown = undefined;
  try { parsed = JSON.parse(text); } catch { parsed = undefined; }
  return (
    <Page title="Configuration">
      <p className="muted">Economic settings (revenue share, bean value, fees, payout limits, settlement hold) can only be changed by finance with two-step verification. Every change is audited with the old and new value.</p>
      <Loadable state={state}>{(rows) => (
        <Table rowKey={(r) => r.key} rows={rows} columns={[
          { key: 'key', label: 'Key', render: (r) => <><code>{r.key}</code>{ECONOMIC_KEYS.has(r.key) && <span className="pill gold" style={{ marginLeft: 6 }}>finance</span>}</> },
          { key: 'value', label: 'Value', render: (r) => <code>{JSON.stringify(r.value)}</code> },
          { key: 'description', label: 'Meaning' },
          { key: 'updated_at', label: 'Updated', render: (r) => formatDate(r.updated_at) },
          { key: 'edit', label: '', render: (r) => mayEdit(r.key) ? <button className="secondary" onClick={() => { setEdit(r); setText(JSON.stringify(r.value)); }}>Edit</button> : null },
        ]} />
      )}</Loadable>
      {edit && (
        <Modal title={edit.key} onClose={() => setEdit(null)}>
          <div className="stack">
            <p className="muted">{edit.description}</p>
            <label className="field"><span>Value (JSON)</span><input value={text} onChange={(e) => setText(e.target.value)} /></label>
            {parsed === undefined && <p className="error">Not valid JSON.</p>}
            <ErrorBox message={action.error} />
            <button disabled={action.busy || parsed === undefined} onClick={() => action.run(() => rpc('staff_set_config', { p_key: edit.key, p_value: parsed }),
              () => { setEdit(null); void state.reload(); })}>Save</button>
          </div>
        </Modal>
      )}
    </Page>
  );
}
