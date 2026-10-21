import { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { Bars, Donut } from '../components/Charts';
import { useToast } from '../components/Toast';
import { api } from '../services/api';
import { inr, downloadCSV, monthName } from '../services/format';

const label = { PRESENT: 'Present', LATE: 'Late', ON_LEAVE: 'On leave', NOT_MARKED: 'Not marked' };
export default function Reports() {
  const { user } = useAuth();
  const toast = useToast();
  const admin = user.role === 'admin';
  const [emps, setEmps] = useState([]), [team, setTeam] = useState([]), [runs, setRuns] = useState([]), [runId, setRunId] = useState(''), [recs, setRecs] = useState([]);
  useEffect(() => {
    api('/employees').then(setEmps).catch((e) => toast(e.message));
    api('/attendance/today').then(setTeam).catch((e) => toast(e.message));
    if (admin) api('/payroll/runs').then((r) => { setRuns(r); if (r.length) setRunId(String(r[0].id)); }).catch((e) => toast(e.message));
  }, [admin]);
  useEffect(() => { if (runId) api('/payroll/runs/' + runId + '/records').then(setRecs).catch((e) => toast(e.message)); else setRecs([]); }, [runId]);

  const active = emps.filter((e) => e.status === 'ACTIVE'), byId = Object.fromEntries(emps.map((e) => [e.id, e]));
  const group = (rows, key, val) => Object.entries(rows.reduce((a, r) => { const k = key(r) || 'Other'; return { ...a, [k]: (a[k] || 0) + val(r) }; }, {})).map(([l, value]) => ({ label: l, value }));
  const n = (s) => team.filter((e) => e.status === s).length;
  const run = runs.find((r) => String(r.id) === runId);
  return (
    <>
      <div className="headrow"><div><h1>Reports</h1><p className="muted">{admin ? 'Company-wide summaries.' : 'Summaries for your team.'}</p></div>
        <div className="actions">
          <button className="btn alt" disabled={!team.length} onClick={() => { downloadCSV('attendance-today.csv', [['ID', 'Name', 'Department', 'Status'], ...team.map((e) => [byId[e.id]?.employeeCode || '', e.fullName, e.department, label[e.status]])]); toast('Attendance report exported'); }}>Export attendance</button>
          {admin && <button className="btn" disabled={!recs.length} onClick={() => { downloadCSV('payroll-' + run.period + '.csv', [['ID', 'Name', 'Gross', 'Deductions', 'Net'], ...recs.map((r) => [byId[r.employeeId]?.employeeCode || '', r.employeeName, r.grossPay, r.totalDeductions, r.netPay])]); toast('Payroll report exported'); }}>Export payroll</button>}</div></div>
      <div className="two">
        <section className="card"><h2>Headcount by department</h2>
          {active.length ? <Bars data={group(active, (e) => e.department, () => 1)} /> : <p className="muted">No employees to show yet.</p>}</section>
        <section className="card"><h2>Attendance today</h2>
          <Donut center={team.length} data={[{ label: 'Present', value: n('PRESENT'), color: '#2FA878' }, { label: 'Late', value: n('LATE'), color: '#F4A62A' }, { label: 'On leave', value: n('ON_LEAVE'), color: '#5B8DEF' }, { label: 'Not marked', value: n('NOT_MARKED'), color: '#D9534F' }]} /></section>
      </div>
      {admin && (
        <section className="card" style={{ marginTop: 16 }}>
          <h2>Net payroll cost by department</h2>
          {runs.length ? (<>
            <label htmlFor="rr" className="sr">Payroll month</label>
            <select id="rr" value={runId} onChange={(e) => setRunId(e.target.value)}>{runs.map((r) => <option key={r.id} value={r.id}>{monthName(r.period)} ({r.status.toLowerCase()})</option>)}</select>
            <Bars fmt={inr} data={group(recs, (r) => byId[r.employeeId]?.department, (r) => Number(r.netPay))} />
          </>) : <p className="muted">No payroll has been calculated yet.</p>}
        </section>
      )}
    </>
  );
}
