import { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import DataTable from '../components/DataTable';
import { useToast } from '../components/Toast';
import { api } from '../services/api';
const hm = (iso) => iso ? new Date(iso).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' }) : '—';
const label = { PRESENT: 'Present', LATE: 'Late', ON_LEAVE: 'On leave', NOT_MARKED: 'Not marked' };
function Calendar({ rows }) {
  const t = new Date(), y = t.getFullYear(), m = t.getMonth(), td = t.getDate(), ym = t.toLocaleDateString('en-CA').slice(0, 7);
  const marks = Object.fromEntries(rows.filter((r) => r.workDate.startsWith(ym)).map((r) => [+r.workDate.slice(8), r.status.toLowerCase()]));
  const lead = (new Date(y, m, 1).getDay() + 6) % 7, days = new Date(y, m + 1, 0).getDate();
  const cells = [...Array(lead).fill(null), ...Array.from({ length: days }, (_, i) => {
    const d = i + 1, dow = new Date(y, m, d).getDay();
    return { d, s: dow === 0 || dow === 6 ? 'off' : d > td ? 'future' : marks[d] || (d === td ? 'today' : 'absent') };
  })];
  return (
    <div className="card">
      <h2 style={{ marginTop: 0 }}>{t.toLocaleDateString('en-IN', { month: 'long', year: 'numeric' })}</h2>
      <div className="cal">{['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map((d) => <b key={d}>{d}</b>)}
        {cells.map((c, i) => c ? <span key={i} className={'day ' + c.s}>{c.d}</span> : <span key={i} />)}</div>
      <ul className="legend row"><li><i style={{ background: '#2FA878' }} />Present</li><li><i style={{ background: '#F4A62A' }} />Late</li><li><i style={{ background: '#D9534F' }} />No record</li><li><i style={{ background: 'var(--line)' }} />Weekend or upcoming</li></ul>
    </div>
  );
}
export default function Attendance() {
  const { user } = useAuth();
  const toast = useToast();
  const [rows, setRows] = useState([]), [team, setTeam] = useState([]);
  const staff = user.role !== 'employee';
  const load = () => {
    api('/attendance/me').then(setRows).catch((e) => toast(e.message));
    if (staff) api('/attendance/today').then(setTeam).catch((e) => toast(e.message));
  };
  useEffect(() => { load(); }, []);
  const todayRow = rows[0] && rows[0].workDate === new Date().toLocaleDateString('en-CA') ? rows[0] : null;
  const act = (path, msg) => api('/attendance/' + path, { method: 'POST' }).then(() => { toast(msg); load(); }).catch((e) => toast(e.message));
  return (
    <>
      <h1>Attendance</h1>
      <p className="muted">Times are recorded by the server when you press the button.</p>
      <div className="actions">
        <button className="btn" disabled={!!todayRow} onClick={() => act('check-in', 'Checked in')}>Check in</button>
        <button className="btn alt" disabled={!todayRow || !!todayRow.checkOut} onClick={() => act('check-out', 'Checked out')}>Check out</button>
      </div>
      <div className="two" style={{ marginTop: 16 }}>
        <Calendar rows={rows} />
        <div><h2 style={{ marginTop: 0 }}>Recent days</h2>
          <DataTable cols={[{ key: 'workDate', label: 'Date' }, { key: 'in', label: 'In', render: (r) => hm(r.checkIn) }, { key: 'out', label: 'Out', render: (r) => hm(r.checkOut) },
            { key: 'status', label: 'Status', render: (r) => label[r.status] }]} rows={rows.slice(0, 7)} empty="No attendance recorded yet." /></div>
      </div>
      {staff && (<>
        <h2>{user.role === 'admin' ? 'Everyone today' : 'My team today'}</h2>
        <DataTable cols={[{ key: 'fullName', label: 'Employee' }, { key: 'department', label: 'Department' }, { key: 'status', label: 'Status', render: (e) => label[e.status] }]} rows={team} />
      </>)}
    </>
  );
}
