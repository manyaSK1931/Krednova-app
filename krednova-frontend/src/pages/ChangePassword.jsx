import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function ChangePassword() {
  const { user, changePassword } = useAuth();
  const nav = useNavigate();
  const [f, setF] = useState({ cur: '', next: '', again: '' });
  const [err, setErr] = useState(''), [busy, setBusy] = useState(false), [show, setShow] = useState(false);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });
  const type = show ? 'text' : 'password';
  const submit = async (e) => {
    e.preventDefault(); setErr('');
    if (f.next.length < 12) return setErr('New password must be at least 12 characters.');
    if (f.next !== f.again) return setErr('The two new passwords do not match.');
    setBusy(true);
    try {
      await changePassword(f.cur, f.next);
      nav('/login', { replace: true, state: { notice: 'Password changed. Please sign in with your new password.' } });
    } catch (x) { setErr(x.message); setBusy(false); }
  };
  return (
    <>
      <h1>Change password</h1>
      <p className="muted">{user.mustChange ? 'Your administrator gave you a temporary password. Please choose your own before continuing.' : 'After changing it you will be signed out everywhere and asked to sign in again.'}</p>
      <form className="card fields one" style={{ maxWidth: 460 }} onSubmit={submit} noValidate>
        {err && <p role="alert" className="alert">{err}</p>}
        <div><label htmlFor="cp0">Current password</label><input id="cp0" type={type} autoComplete="current-password" maxLength={128} required value={f.cur} onChange={set('cur')} /></div>
        <div><label htmlFor="cp1">New password (at least 12 characters)</label><input id="cp1" type={type} autoComplete="new-password" maxLength={128} required value={f.next} onChange={set('next')} /></div>
        <div><label htmlFor="cp2">Confirm new password</label><input id="cp2" type={type} autoComplete="new-password" maxLength={128} required value={f.again} onChange={set('again')} /></div>
        <label className="check"><input type="checkbox" checked={show} onChange={(e) => setShow(e.target.checked)} /> Show passwords</label>
        <p className="muted note" style={{ margin: 0 }}>Tip: a few unrelated words make a strong, memorable password. Avoid the word "password" and easy patterns like 123456.</p>
        <button className="btn" type="submit" disabled={busy || !f.cur || !f.next || !f.again}>{busy ? 'Saving…' : 'Change password'}</button>
      </form>
    </>
  );
}
