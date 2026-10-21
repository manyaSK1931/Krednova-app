import { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import DataTable from '../components/DataTable';
import { useToast } from '../components/Toast';
import { api } from '../services/api';
const cap = (s) => s[0] + s.slice(1).toLowerCase();
const types = ['CASUAL', 'SICK', 'EARNED', 'UNPAID'];
const blank = { leaveType: 'CASUAL', startDate: '', endDate: '', reason: '' };
export default function LeaveManagement() {
  const { user } = useAuth();
  const toast = useToast();
  const [reqs, setReqs] = useState([]), [bal, setBal] = useState([]), [f, setF] = useState(blank);
  const load = () => {
    api('/leave-requests').then(setReqs).catch((e) => toast(e.message));
    api('/leave-requests/balances').then(setBal).catch(() => {});
  };
  useEffect(() => { load(); }, []);
  const decide = (id, decision) => api('/leave-requests/' + id + '/decision', { method: 'PATCH', body: { decision } }).then(() => { toast(decision === 'APPROVED' ? 'Leave request accepted' : 'Leave request rejected'); load(); }).catch((e) => toast(e.message));
  const apply = (e) => {
    e.preventDefault();
    api('/leave-requests', { method: 'POST', body: f }).then(() => { setF(blank); load(); toast('Leave request submitted'); }).catch((x) => toast(x.message));
  };
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });
  const cols = [
    { key: 'employeeName', label: 'Employee' }, { key: 'leaveType', label: 'Type', render: (r) => cap(r.leaveType) },
    { key: 'd', label: 'Dates', render: (r) => r.startDate + ' to ' + r.endDate + ' (' + r.days + 'd)' }, { key: 'reason', label: 'Reason' },
    { key: 'status', label: 'Status', render: (r) => <span className={'pill ' + cap(r.status)}>{cap(r.status)}</span> },
    { key: 'a', label: 'Action', render: (r) => r.status === 'PENDING' && user.role !== 'employee' && r.employeeId !== user.id ? (
      <span className="actions">
        <button className="btn sm" onClick={() => decide(r.id, 'APPROVED')}>Accept</button>
        <button className="btn alt sm" onClick={() => decide(r.id, 'REJECTED')}>Reject</button>
      </span>) : null },
  ];
  return (
    <>
      <h1>Leave requests</h1>
      <div className="grid">{bal.map((b) => <div className="stat" key={b.leaveType}><span>{cap(b.leaveType)}</span><strong>{b.daysLeft} days left</strong></div>)}</div>
      <form className="card form" onSubmit={apply}>
        <h2>Apply for leave</h2>
        <div className="fields">
          <div><label htmlFor="lt">Leave type</label><select id="lt" value={f.leaveType} onChange={set('leaveType')}>{types.map((t) => <option key={t} value={t}>{cap(t)}</option>)}</select></div>
          <div><label htmlFor="lf">From</label><input id="lf" type="date" required value={f.startDate} onChange={set('startDate')} /></div>
          <div><label htmlFor="lto">To</label><input id="lto" type="date" required min={f.startDate} value={f.endDate} onChange={set('endDate')} /></div>
          <div className="wide"><label htmlFor="lr">Reason</label><input id="lr" required maxLength={200} value={f.reason} onChange={set('reason')} /></div>
        </div>
        <button className="btn" type="submit">Submit request</button>
      </form>
      <h2>{user.role === 'employee' ? 'My requests' : 'Requests'}</h2>
      <DataTable cols={cols} rows={reqs} empty="No leave requests yet." />
    </>
  );
}
