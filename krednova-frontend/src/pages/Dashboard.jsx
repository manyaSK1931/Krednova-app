import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import StatCard from '../components/StatCard';
import { Donut, Columns, Bars } from '../components/Charts';
import { api } from '../services/api';
import { inr, isoPlus, isoToday, monthName, fmtDate, clock12, cap } from '../services/format';

const greet = () => { const h = new Date().getHours(); return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening'; };
const load = (path, set) => api(path).then(set).catch(() => {});

export default function Dashboard() {
  const { user } = useAuth();
  const staff = user.role !== 'employee', admin = user.role === 'admin';
  const [now, setNow] = useState(new Date());
  const [cfg, setCfg] = useState(null), [team, setTeam] = useState([]), [week, setWeek] = useState([]), [leaves, setLeaves] = useState([]);
  const [hols, setHols] = useState([]), [sums, setSums] = useState([]), [bal, setBal] = useState([]), [slips, setSlips] = useState([]);
  useEffect(() => { const t = setInterval(() => setNow(new Date()), 1000); return () => clearInterval(t); }, []);
  useEffect(() => {
    load('/settings', setCfg); load('/holidays', setHols); load('/leave-requests', setLeaves);
    if (staff) { load('/attendance/today', setTeam); load('/attendance/week', setWeek); }
    else { load('/leave-requests/balances', setBal); load('/payroll/me', setSlips); }
    if (admin) load('/payroll/summary', setSums);
  }, [staff, admin]);

  const n = (s) => team.filter((e) => e.status === s).length;
  const pending = staff ? leaves.filter((r) => r.status === 'PENDING' && r.employeeId !== user.id).length : 0;
  const byDate = Object.fromEntries(week.map((w) => [w.workDate, w.people]));
  const days = Array.from({ length: 7 }, (_, i) => {
    const d = new Date(); d.setDate(d.getDate() - 6 + i);
    return { label: d.toLocaleDateString('en-IN', { weekday: 'short' }), value: byDate[d.toLocaleDateString('en-CA')] || 0 };
  });
  const t0 = isoToday(), t7 = isoPlus(7);
  const away = leaves.filter((r) => r.status === 'APPROVED' && r.endDate >= t0 && r.startDate <= t7);
  const last = sums[0], lastSlip = slips[0];
  return (
    <>
      <section className="hero">
        <div>
          <p className="hl">{now.toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long' })}</p>
          <h1>{greet()}, {user.name.split(' ')[0]}</h1>
          <p>{cfg ? 'Shift ' + clock12(cfg.shiftStart) + ' to ' + clock12(cfg.shiftEnd) : ''}{pending ? ' · ' + pending + ' leave request' + (pending > 1 ? 's' : '') + ' need a decision' : ''}</p>
          <div className="actions"><Link className="btn sun" to="/attendance">Check in</Link>
            <Link className="btn ghost" to={pending ? '/leave' : '/payslips'}>{pending ? 'Review requests' : 'View payslips'}</Link></div>
        </div>
        <div className="clock" aria-hidden="true">{now.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}</div>
      </section>

      {staff ? (
        <div className="grid">
          <StatCard label="Employees" value={team.length} />
          <StatCard label="Present" value={n('PRESENT')} tone="ok" />
          <StatCard label="Late arrivals" value={n('LATE')} tone="warn" />
          <StatCard label="On leave" value={n('ON_LEAVE')} />
          {admin && last && <StatCard label={'Net payroll, ' + monthName(last.period) + ' (' + last.status.toLowerCase() + ')'} value={inr(last.net)} />}
        </div>
      ) : (
        <div className="grid">{bal.map((b) => <StatCard key={b.leaveType} label={cap(b.leaveType) + ' leave left'} value={b.daysLeft + ' days'} />)}
          <StatCard label={lastSlip ? 'Last net pay, ' + monthName(lastSlip.period) : 'Last net pay'} value={lastSlip ? inr(lastSlip.netPay) : 'No payslip yet'} /></div>
      )}

      {staff && (
        <div className="two">
          <section className="card"><h2>Today’s attendance</h2>
            <Donut center={team.length} data={[
              { label: 'Present', value: n('PRESENT'), color: '#2FA878' }, { label: 'Late', value: n('LATE'), color: '#F4A62A' },
              { label: 'On leave', value: n('ON_LEAVE'), color: '#5B8DEF' }, { label: 'Not marked', value: n('NOT_MARKED'), color: '#D9534F' }]} /></section>
          <section className="card"><h2>Last 7 days, people checked in</h2>
            <Columns max={Math.max(team.length, 1)} data={days} /></section>
        </div>
      )}
      {admin && (
        <section className="card" style={{ marginTop: 16 }}><h2>Net payroll by month</h2>
          {sums.length ? <Bars fmt={inr} data={sums.slice().reverse().map((s) => ({ label: s.period, value: Number(s.net) }))} />
            : <p className="muted">No payroll has been calculated yet. Open Payroll to run the first month.</p>}</section>
      )}
      <div className="two" style={{ marginTop: 16 }}>
        <section className="card"><h2>Upcoming holidays</h2>
          {hols.length ? <ul className="list">{hols.slice(0, 5).map((h) => <li key={h.holidayDate}><b>{fmtDate(h.holidayDate)}</b> {h.name}</li>)}</ul>
            : <p className="muted">No holidays added yet.{admin ? ' Add them in Settings.' : ''}</p>}</section>
        <section className="card"><h2>{staff ? 'Away in the next 7 days' : 'My upcoming leave'}</h2>
          {away.length ? <ul className="list">{away.slice(0, 6).map((r) => <li key={r.id}><b>{staff ? r.employeeName : cap(r.leaveType)}</b> {fmtDate(r.startDate)} to {fmtDate(r.endDate)}</li>)}</ul>
            : <p className="muted">{staff ? 'Nobody has approved leave coming up.' : 'You have no approved leave coming up.'}</p>}</section>
      </div>
    </>
  );
}
