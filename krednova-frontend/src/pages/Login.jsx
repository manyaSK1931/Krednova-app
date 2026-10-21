import { useEffect, useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { api, ensureCsrf } from '../services/api';

export default function Login() {
  const { user, login } = useAuth();
  const nav = useNavigate();
  const notice = useLocation().state?.notice;
  const [setup, setSetup] = useState(null); // null = checking, otherwise { needsSetup, requiresKey }
  const [f, setF] = useState({ name: '', email: '', password: '', confirm: '', key: '' });
  const [err, setErr] = useState(''), [busy, setBusy] = useState(false), [show, setShow] = useState(false);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });

  useEffect(() => {
    api('/auth/setup-status').then(setSetup).catch(() => setSetup({ needsSetup: false, requiresKey: false, offline: true }));
  }, []);

  if (user) return <Navigate to="/" replace />;
  const creating = !!setup?.needsSetup;

  const submit = async (e) => {
    e.preventDefault(); setErr('');
    if (creating) {
      if (f.password.length < 12) return setErr('Password must be at least 12 characters.');
      if (f.password !== f.confirm) return setErr('The two passwords do not match.');
    }
    setBusy(true);
    try {
      if (creating) {
        await ensureCsrf();
        await api('/auth/setup', { method: 'POST', body: { fullName: f.name.trim(), email: f.email.trim(), password: f.password, setupKey: f.key || null } });
      }
      await login(f.email.trim(), f.password);
      nav('/');
    } catch (x) {
      setErr(x.message);
      setF({ ...f, password: '', confirm: '' });
      if (x.status === 409) api('/auth/setup-status').then(setSetup).catch(() => {});
    } finally { setBusy(false); }
  };

  const type = show ? 'text' : 'password';
  const ready = creating ? f.name && f.email && f.password && f.confirm && (!setup.requiresKey || f.key) : f.email && f.password;
  return (
    <div className="login">
      <form className="card" onSubmit={submit} noValidate>
        <div className="logo dark"><i /> KredNova Payroll</div>
        <h1>{creating ? 'Create your admin account' : 'Sign in'}</h1>
        <p className="muted">{creating ? 'First-time setup. This account will manage everyone else.' : 'Use your work email.'}</p>
        {notice && !err && <p role="status" className="muted">{notice}</p>}
        {setup?.offline && <p role="alert" className="alert">Cannot reach the server. Is the backend running?</p>}
        {err && <p role="alert" className="alert">{err}</p>}
        {creating && (<>
          <label htmlFor="nm">Full name</label>
          <input id="nm" autoComplete="name" maxLength={100} required value={f.name} onChange={set('name')} />
        </>)}
        <label htmlFor="em">{creating ? 'Email' : 'Work email'}</label>
        <input id="em" type="email" autoComplete="username" maxLength={190} required value={f.email} onChange={set('email')} />
        <label htmlFor="pw">Password{creating ? ' (at least 12 characters)' : ''}</label>
        <input id="pw" type={type} autoComplete={creating ? 'new-password' : 'current-password'} maxLength={128} required value={f.password} onChange={set('password')} />
        {creating && (<>
          <label htmlFor="pw2">Confirm password</label>
          <input id="pw2" type={type} autoComplete="new-password" maxLength={128} required value={f.confirm} onChange={set('confirm')} />
          {setup.requiresKey && (<>
            <label htmlFor="key">Setup key</label>
            <input id="key" type="password" autoComplete="off" maxLength={128} required value={f.key} onChange={set('key')} />
          </>)}
        </>)}
        <label className="check"><input type="checkbox" checked={show} onChange={(e) => setShow(e.target.checked)} /> Show password</label>
        <button className="btn" type="submit" disabled={busy || setup === null || !ready}>
          {busy ? 'Please wait…' : creating ? 'Create account and sign in' : 'Sign in'}
        </button>
        {!creating && <p className="muted note">New here? Ask your administrator to create your account.</p>}
      </form>
    </div>
  );
}
