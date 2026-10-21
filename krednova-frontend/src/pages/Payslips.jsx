import { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { api } from '../services/api';
import { inr, monthName } from '../services/format';

const Row = ({ l, v, neg }) => <div className={'prow' + (neg ? ' neg' : '')}><span>{l}</span><span>{neg ? '−' : ''}{inr(v)}</span></div>;

export default function Payslips() {
  const { user } = useAuth();
  const [slips, setSlips] = useState(null), [i, setI] = useState(0), [err, setErr] = useState('');
  useEffect(() => { api('/payroll/me').then(setSlips).catch((e) => setErr(e.message)); }, []);
  if (err) return <p role="alert" className="alert">{err}</p>;
  if (!slips) return <p className="muted">Loading payslips…</p>;
  if (!slips.length) return (<><h1>Payslips</h1><p className="muted">No payslips yet. Each month’s payslip appears here once payroll for that month is finalized.</p></>);
  const p = slips[Math.min(i, slips.length - 1)];
  return (
    <>
      <h1>Payslips</h1>
      {user.role === 'admin' && <p className="muted">This page shows your own payslips. Everyone’s payroll is under Payroll.</p>}
      <div className="actions">
        <label htmlFor="pm" className="sr">Month</label>
        <select id="pm" value={i} onChange={(e) => setI(+e.target.value)}>{slips.map((s, k) => <option key={s.period} value={k}>{monthName(s.period)}</option>)}</select>
        <button className="btn alt" onClick={() => window.print()}>Print or save as PDF</button>
      </div>
      <section className="card slip">
        <div className="prow head"><b>Payslip, {monthName(p.period)}</b><span>{user.name} · {user.employeeId}</span></div>
        <Row l="Basic" v={p.basicSalary} /><Row l="House rent allowance" v={p.hra} /><Row l="Other allowances" v={p.otherAllowances} />
        <Row l="Provident fund" v={p.pf} neg />{Number(p.lopAmount) > 0 && <Row l={'Unpaid leave (' + p.lopDays + ' days)'} v={p.lopAmount} neg />}
        <div className="prow net"><span>Net pay</span><strong>{inr(p.netPay)}</strong></div>
      </section>
    </>
  );
}
