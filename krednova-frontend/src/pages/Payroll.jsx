import { useEffect, useState } from 'react';
import DataTable from '../components/DataTable';
import { useToast } from '../components/Toast';
import { api } from '../services/api';
import { inr } from '../services/format';
const flow = ['CALCULATED', 'APPROVED', 'FINALIZED'];
const nextStep = { CALCULATED: ['approve', 'Approve payroll'], APPROVED: ['finalize', 'Finalize payroll'] };
export default function Payroll() {
  const toast = useToast();
  const [runs, setRuns] = useState([]), [sel, setSel] = useState(null), [recs, setRecs] = useState([]);
  const [period, setPeriod] = useState(new Date().toLocaleDateString('en-CA').slice(0, 7));
  const loadRuns = () => api('/payroll/runs').then(setRuns).catch((e) => toast(e.message));
  const open = (id) => { setSel(id); api('/payroll/runs/' + id + '/records').then(setRecs).catch((e) => toast(e.message)); };
  useEffect(() => { loadRuns(); }, []);
  const create = () => api('/payroll/runs', { method: 'POST', body: { period } }).then((r) => { toast('Payroll calculated for ' + r.period); loadRuns(); open(r.id); }).catch((e) => toast(e.message));
  const run = runs.find((r) => r.id === sel);
  const move = (action) => api('/payroll/runs/' + sel + '/' + action, { method: 'POST' }).then(() => { toast('Payroll ' + action + 'd'); loadRuns(); }).catch((e) => toast(e.message));
  const total = (k) => recs.reduce((s, r) => s + r[k], 0);
  const cols = [
    { key: 'employeeName', label: 'Employee' }, { key: 'lopDays', label: 'Unpaid days' },
    { key: 'grossPay', label: 'Gross', render: (r) => inr(r.grossPay) }, { key: 'totalDeductions', label: 'Deductions', render: (r) => inr(r.totalDeductions) },
    { key: 'netPay', label: 'Net pay', render: (r) => <b>{inr(r.netPay)}</b> },
  ];
  return (
    <>
      <h1>Payroll processing</h1>
      <div className="actions">
        <div><label htmlFor="pp" className="sr">Month</label><input id="pp" type="month" value={period} onChange={(e) => setPeriod(e.target.value)} /></div>
        <button className="btn" onClick={create} disabled={!period}>Calculate payroll</button>
      </div>
      <div className="chips" role="group" aria-label="Payroll runs">
        {runs.map((r) => <button key={r.id} className={'chip' + (sel === r.id ? ' on' : '')} aria-pressed={sel === r.id} onClick={() => open(r.id)}>{r.period} · {r.status.toLowerCase()}</button>)}
      </div>
      {run ? (<>
        <ol className="flow">{flow.map((s, i) => <li key={s} className={i <= flow.indexOf(run.status) ? 'on' : ''}>{s}</li>)}</ol>
        <DataTable cols={cols} rows={recs} />
        <div className="grid" style={{ marginTop: 16 }}>
          <div className="stat"><span>Total gross</span><strong>{inr(total('grossPay'))}</strong></div>
          <div className="stat"><span>Total deductions</span><strong>{inr(total('totalDeductions'))}</strong></div>
          <div className="stat"><span>Total net payable</span><strong>{inr(total('netPay'))}</strong></div>
        </div>
        {nextStep[run.status] && <button className="btn" onClick={() => move(nextStep[run.status][0])}>{nextStep[run.status][1]}</button>}
      </>) : <p className="muted">Choose a month and calculate, or open an earlier run.</p>}
      <p className="note">Illustrative rules only (PF at 12% of basic, unpaid leave at gross ÷ 26 per day). Replace with your company’s policy and statutory rules before real use. Finalized runs cannot be changed.</p>
    </>
  );
}
