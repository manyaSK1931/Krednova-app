import { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../components/Toast';
import { api } from '../services/api';
import { fmtDate } from '../services/format';

export default function Settings() {
  const { theme, setTheme } = useAuth();
  const toast = useToast();
  const [s, setS] = useState(null), [hols, setHols] = useState([]), [h, setH] = useState({ date: '', name: '' });
  const loadHols = () => api('/holidays').then(setHols).catch((e) => toast(e.message));
  useEffect(() => { api('/settings').then(setS).catch((e) => toast(e.message)); loadHols(); }, []);
  if (!s) return <p className="muted">Loading settings…</p>;
  const set = (k) => (e) => setS({ ...s, [k]: e.target.value });
  const field = (k, l, type = 'text', extra = {}) => <div><label htmlFor={k}>{l}</label><input id={k} type={type} value={s[k]} onChange={set(k)} required {...extra} /></div>;
  const save = (e) => {
    e.preventDefault();
    api('/settings', { method: 'PUT', body: { shiftStart: s.shiftStart, shiftEnd: s.shiftEnd, graceMinutes: Number(s.graceMinutes), casualDays: Number(s.casualDays), sickDays: Number(s.sickDays), earnedDays: Number(s.earnedDays) } })
      .then(setS).then(() => toast('Settings saved')).catch((x) => toast(x.message));
  };
  const addHol = (e) => {
    e.preventDefault();
    api('/holidays', { method: 'POST', body: { date: h.date, name: h.name.trim() } }).then(() => { setH({ date: '', name: '' }); toast('Holiday added'); loadHols(); }).catch((x) => toast(x.message));
  };
  const delHol = (d) => api('/holidays/' + d, { method: 'DELETE' }).then(() => { toast('Holiday removed'); loadHols(); }).catch((x) => toast(x.message));
  return (
    <>
      <h1>Settings</h1>
      <form className="card form" onSubmit={save}>
        <h2>Working hours</h2>
        <div className="fields">{field('shiftStart', 'Shift starts', 'time')}{field('shiftEnd', 'Shift ends', 'time')}{field('graceMinutes', 'Late grace period (minutes)', 'number', { min: 0, max: 120 })}</div>
        <h2>Yearly leave days for new employees</h2>
        <div className="fields">{field('casualDays', 'Casual', 'number', { min: 0, max: 60 })}{field('sickDays', 'Sick', 'number', { min: 0, max: 60 })}{field('earnedDays', 'Earned', 'number', { min: 0, max: 60 })}</div>
        <p className="muted note">Leave days apply to employees you add from now on. Existing balances are not changed. A person is marked late when they check in after the shift start plus the grace period.</p>
        <p><button className="btn" type="submit">Save settings</button></p>
      </form>
      <section className="card form">
        <h2>Holidays</h2>
        {hols.length ? <ul className="list">{hols.map((x) => <li key={x.holidayDate}><b>{fmtDate(x.holidayDate)} {x.holidayDate.slice(0, 4)}</b> {x.name} <button type="button" className="link" onClick={() => delHol(x.holidayDate)} aria-label={'Remove ' + x.name}>Remove</button></li>)}</ul>
          : <p className="muted">No upcoming holidays.</p>}
        <form onSubmit={addHol} className="fields">
          <div><label htmlFor="hd">Date</label><input id="hd" type="date" required value={h.date} onChange={(e) => setH({ ...h, date: e.target.value })} /></div>
          <div><label htmlFor="hn">Holiday name</label><input id="hn" maxLength={100} required value={h.name} onChange={(e) => setH({ ...h, name: e.target.value })} /></div>
          <div style={{ alignSelf: 'end' }}><button className="btn alt" type="submit">Add holiday</button></div>
        </form>
      </section>
      <section className="card form">
        <h2>Appearance</h2>
        <div className="chips">{['light', 'dark'].map((t) => <button type="button" key={t} className={'chip' + (theme === t ? ' on' : '')} aria-pressed={theme === t} onClick={() => setTheme(t)}>{t === 'light' ? 'Light' : 'Dark'}</button>)}</div>
      </section>
    </>
  );
}
