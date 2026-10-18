import { useEffect, useState } from 'react';
import type { Session } from '@supabase/supabase-js';
import { configured, db, rpc } from './supabase';
import { describeError } from './errors';
import { SECTION_LABELS, sectionsFor, type Section, type StaffRole } from './roles';
import { ErrorBox } from './ui';
import { Overview } from './pages/Overview';
import { Users } from './pages/Users';
import { LiveRooms } from './pages/LiveRooms';
import { Reports, Appeals } from './pages/Moderation';
import { Support } from './pages/Support';
import { Catalog } from './pages/Catalog';
import { Config } from './pages/Config';
import { Events } from './pages/Events';
import { Rankings } from './pages/Rankings';
import { Payments, Payouts, Adjustments } from './pages/Finance';
import { Staff } from './pages/Staff';
import { Audit } from './pages/Audit';

interface WhoAmI { user_id: string; role: StaffRole | null; aal: string }

export function App() {
  const [session, setSession] = useState<Session | null | undefined>(undefined);
  useEffect(() => {
    if (!configured) return;
    void db().auth.getSession().then(({ data }) => setSession(data.session));
    const { data } = db().auth.onAuthStateChange((_e, s) => setSession(s));
    return () => data.subscription.unsubscribe();
  }, []);

  if (!configured) return <Centered><h1>Not configured</h1><p className="muted">Copy admin/.env.example to admin/.env.local and set the Supabase URL and anon key, then restart.</p></Centered>;
  if (session === undefined) return <Centered><p className="muted">Loading…</p></Centered>;
  if (!session) return <SignIn />;
  return <StaffGate key={session.access_token} />;
}

function Centered({ children }: { children: React.ReactNode }) {
  return <div className="center"><div className="card stack">{children}</div></div>;
}

function SignIn() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true); setError(null);
    const { error } = await db().auth.signInWithPassword({ email, password });
    if (error) setError(error.message);
    setBusy(false);
  };
  return (
    <Centered>
      <h1 style={{ color: 'var(--gold)' }}>Ethio Live Admin</h1>
      <p className="muted">Staff sign-in. Accounts are created in the app and given a role by a superadmin.</p>
      <form className="stack" onSubmit={submit}>
        <label className="field"><span>Email</span><input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="username" required /></label>
        <label className="field"><span>Password</span><input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required /></label>
        <ErrorBox message={error} />
        <button disabled={busy}>Sign in</button>
      </form>
    </Centered>
  );
}

/** Every staff member must use two-step verification (TOTP) before the dashboard opens. The database also
 *  enforces aal2 on financial and role changes, so skipping this screen does not bypass anything. */
function StaffGate() {
  const [who, setWho] = useState<WhoAmI | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [mfa, setMfa] = useState<'checking' | 'enroll' | 'verify' | 'ok'>('checking');
  const [factorId, setFactorId] = useState<string | null>(null);
  const [qr, setQr] = useState<string | null>(null);
  const [secret, setSecret] = useState<string | null>(null);
  const [code, setCode] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const w = await rpc<WhoAmI>('staff_whoami');
        setWho(w);
        if (!w.role) return;
        const { data: aal } = await db().auth.mfa.getAuthenticatorAssuranceLevel();
        if (aal?.currentLevel === 'aal2') { setMfa('ok'); return; }
        const { data: factors } = await db().auth.mfa.listFactors();
        const totp = factors?.totp.find((f) => f.status === 'verified');
        if (totp) { setFactorId(totp.id); setMfa('verify'); return; }
        const { data: enrolled, error: enrollError } = await db().auth.mfa.enroll({ factorType: 'totp', friendlyName: 'Ethio Live Admin' });
        if (enrollError) throw enrollError;
        setFactorId(enrolled.id); setQr(enrolled.totp.qr_code); setSecret(enrolled.totp.secret); setMfa('enroll');
      } catch (e) { setError(describeError(e)); }
    })();
  }, []);

  const verify = async () => {
    if (!factorId) return;
    setError(null);
    const { error } = await db().auth.mfa.challengeAndVerify({ factorId, code: code.trim() });
    if (error) { setError(error.message); return; }
    setWho(await rpc<WhoAmI>('staff_whoami'));
    setMfa('ok');
  };
  const signOut = () => void db().auth.signOut();

  if (error && !who) return <Centered><ErrorBox message={error} /><button onClick={signOut}>Sign out</button></Centered>;
  if (!who) return <Centered><p className="muted">Checking access…</p></Centered>;
  if (!who.role) return <Centered><h2>No staff access</h2><p className="muted">This account does not have a staff role. Ask a superadmin to add you.</p><button onClick={signOut}>Sign out</button></Centered>;
  if (mfa === 'checking') return <Centered><p className="muted">Checking two-step verification…</p></Centered>;
  if (mfa !== 'ok') {
    return (
      <Centered>
        <h2>Two-step verification</h2>
        {mfa === 'enroll' && <>
          <p className="muted">Scan this code with an authenticator app (Google Authenticator, Microsoft Authenticator, 1Password…), then enter the 6-digit code.</p>
          {qr && <img src={qr} alt="Authenticator QR code" style={{ background: '#fff', padding: 8, borderRadius: 8, width: 200 }} />}
          {secret && <p className="muted">Or enter this key: <code>{secret}</code></p>}
        </>}
        {mfa === 'verify' && <p className="muted">Enter the 6-digit code from your authenticator app.</p>}
        <input inputMode="numeric" maxLength={6} value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} placeholder="123456" />
        <ErrorBox message={error} />
        <button disabled={code.length !== 6} onClick={verify}>Verify</button>
        <button className="secondary" onClick={signOut}>Sign out</button>
      </Centered>
    );
  }
  return <Shell role={who.role} userId={who.user_id} onSignOut={signOut} />;
}

function Shell({ role, userId, onSignOut }: { role: StaffRole; userId: string; onSignOut: () => void }) {
  const sections = sectionsFor(role);
  const [section, setSection] = useState<Section>(() => {
    const fromHash = window.location.hash.slice(1) as Section;
    return sections.includes(fromHash) ? fromHash : sections[0];
  });
  useEffect(() => { window.location.hash = section; }, [section]);
  return (
    <div className="shell">
      <nav className="side">
        <div className="brand">Ethio Live Admin</div>
        {sections.map((s) => <button key={s} className={s === section ? 'active' : ''} onClick={() => setSection(s)}>{SECTION_LABELS[s]}</button>)}
        <div className="who">Signed in as <b>{role}</b><br /><button className="link" style={{ padding: 0 }} onClick={onSignOut}>Sign out</button></div>
      </nav>
      <main>
        {section === 'overview' && <Overview />}
        {section === 'users' && <Users role={role} />}
        {section === 'live' && <LiveRooms />}
        {section === 'reports' && <Reports role={role} />}
        {section === 'appeals' && <Appeals />}
        {section === 'support' && <Support />}
        {section === 'catalog' && <Catalog role={role} />}
        {section === 'config' && <Config role={role} />}
        {section === 'events' && <Events role={role} />}
        {section === 'rankings' && <Rankings />}
        {section === 'payments' && <Payments />}
        {section === 'payouts' && <Payouts userId={userId} />}
        {section === 'adjustments' && <Adjustments userId={userId} />}
        {section === 'staff' && <Staff role={role} userId={userId} />}
        {section === 'audit' && <Audit />}
      </main>
    </div>
  );
}
