import { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import DataTable from '../components/DataTable';
import { Avatar, Modal } from '../components/UI';
import { useToast } from '../components/Toast';
import { api } from '../services/api';
import { inr, downloadCSV } from '../services/format';
const depts = ['Design', 'Engineering', 'Sales', 'Finance', 'HR'];
const cap = (s) => s[0] + s.slice(1).toLowerCase();
const blank = { fullName: '', email: '', department: 'Design', jobTitle: '', basicSalary: '', role: 'EMPLOYEE', managerId: '', temporaryPassword: '' };
export default function Employees() {
  const { user } = useAuth();
  const toast = useToast();
  const [list, setList] = useState([]), [loading, setLoading] = useState(true);
  const [q, setQ] = useState(''), [dept, setDept] = useState('All');
  const [sel, setSel] = useState(null), [rp, setRp] = useState(''), [adding, setAdding] = useState(false), [f, setF] = useState(blank);
  const admin = user.role === 'admin';
  const load = () => api('/employees').then(setList).catch((e) => toast(e.message)).finally(() => setLoading(false));
  useEffect(() => { load(); }, []);
  const toggle = (e) => api('/employees/' + e.id + '/status', { method: 'PATCH', body: { status: e.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE' } }).then(load).catch((x) => toast(x.message));
  const add = (ev) => {
    ev.preventDefault();
    api('/employees', { method: 'POST', body: { ...f, basicSalary: Number(f.basicSalary), managerId: f.managerId ? Number(f.managerId) : null } })
      .then((c) => { setAdding(false); setF(blank); load(); toast(c.fullName + ' added as ' + c.employeeCode); })
      .catch((x) => toast(x.message));
  };
  const rows = list.filter((e) => (dept === 'All' || e.department === dept) && (e.fullName + e.department + e.employeeCode).toLowerCase().includes(q.toLowerCase()));
  const cols = [
    { key: 'employeeCode', label: 'ID' },
    { key: 'fullName', label: 'Name', render: (e) => <button className="who-btn" onClick={() => setSel(e)}><Avatar name={e.fullName} i={e.id} size={30} />{e.fullName}</button> },
    { key: 'department', label: 'Department' }, { key: 'jobTitle', label: 'Role' },
    { key: 'status', label: 'Status', render: (e) => <span className={'pill ' + cap(e.status)}>{cap(e.status)}</span> },
    ...(admin ? [{ key: 'sal', label: 'Gross salary', render: (e) => inr(e.basicSalary + e.hra + e.otherAllowances) },
      { key: 'act', label: '', render: (e) => <button className="link" onClick={() => toggle(e)}>{e.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}</button> }] : []),
  ];
  const reset = (ev) => {
    ev.preventDefault();
    api('/employees/' + sel.id + '/password', { method: 'PATCH', body: { temporaryPassword: rp } })
      .then(() => { setRp(''); toast('Temporary password set. ' + sel.fullName + ' must choose a new one at next sign-in.'); })
      .catch((x) => toast(x.message));
  };
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });
  return (
    <>
      <div className="headrow"><div><h1>Employees</h1>
        <p className="muted">{admin ? 'Everyone in the company.' : 'Your team. Salary details are hidden for managers.'}</p></div>
        <div className="actions">
          <button className="btn alt" onClick={() => { downloadCSV('employees.csv', [['ID', 'Name', 'Department', 'Role', 'Status'], ...rows.map((e) => [e.employeeCode, e.fullName, e.department, e.jobTitle, e.status])]); toast('Employee list exported'); }}>Export CSV</button>
          {admin && <button className="btn" onClick={() => setAdding(true)}>Add employee</button>}</div></div>
      <div className="chips" role="group" aria-label="Filter by department">
        {['All', ...depts].map((d) => <button key={d} className={'chip' + (dept === d ? ' on' : '')} aria-pressed={dept === d} onClick={() => setDept(d)}>{d}</button>)}
      </div>
      <input className="search" placeholder="Search by name, department, or ID" aria-label="Search employees" value={q} onChange={(e) => setQ(e.target.value)} />
      {loading ? <p className="muted">Loading employees…</p> : <DataTable cols={cols} rows={rows} empty="No employees match your filters." />}
      {sel && (
        <Modal title={sel.fullName} onClose={() => { setSel(null); setRp(''); }}>
          <div className="profile"><Avatar name={sel.fullName} i={sel.id} size={64} />
            <div><b>{sel.jobTitle}</b><br /><span className="muted">{sel.department} · {sel.employeeCode}</span><br /><span className="muted">{sel.email}</span></div></div>
          {admin ? <ul className="list"><li>Basic <b>{inr(sel.basicSalary)}</b></li><li>House rent allowance <b>{inr(sel.hra)}</b></li><li>Other allowances <b>{inr(sel.otherAllowances)}</b></li></ul>
            : <p className="muted">Salary details are visible to Admin / HR only.</p>}
          {admin && sel.employeeCode !== user.employeeId && (
            <form onSubmit={reset} className="fields one">
              <div><label htmlFor="rp">Reset password (temporary, 12+ characters)</label>
                <input id="rp" type="password" autoComplete="new-password" minLength={12} maxLength={128} required value={rp} onChange={(e) => setRp(e.target.value)} /></div>
              <button className="btn alt" type="submit">Set temporary password</button>
            </form>
          )}
        </Modal>
      )}
      {adding && (
        <Modal title="Add employee" onClose={() => setAdding(false)}>
          <form onSubmit={add} className="fields one">
            <div><label htmlFor="an">Full name</label><input id="an" required maxLength={100} value={f.fullName} onChange={set('fullName')} /></div>
            <div><label htmlFor="ae">Work email</label><input id="ae" type="email" required maxLength={190} value={f.email} onChange={set('email')} /></div>
            <div><label htmlFor="ad">Department</label><select id="ad" value={f.department} onChange={set('department')}>{depts.map((d) => <option key={d}>{d}</option>)}</select></div>
            <div><label htmlFor="at">Job role</label><input id="at" required maxLength={80} value={f.jobTitle} onChange={set('jobTitle')} /></div>
            <div><label htmlFor="ab">Basic salary per month (₹)</label><input id="ab" type="number" min="0" required value={f.basicSalary} onChange={set('basicSalary')} /></div>
            <div><label htmlFor="ar">Access level</label><select id="ar" value={f.role} onChange={set('role')}><option value="EMPLOYEE">Employee</option><option value="MANAGER">Manager</option><option value="ADMIN">Admin / HR</option></select></div>
            <div><label htmlFor="am">Reports to (approves their leave)</label><select id="am" value={f.managerId} onChange={set('managerId')}><option value="">No one (Admin / HR approves)</option>{list.filter((e) => e.status === 'ACTIVE').map((e) => <option key={e.id} value={e.id}>{e.fullName} ({e.jobTitle})</option>)}</select></div>
            <div><label htmlFor="ap">Temporary password (12+ characters)</label><input id="ap" type="password" autoComplete="new-password" minLength={12} maxLength={128} required value={f.temporaryPassword} onChange={set('temporaryPassword')} /></div>
            <button className="btn" type="submit">Save employee</button>
          </form>
        </Modal>
      )}
    </>
  );
}
