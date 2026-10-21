import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { api } from '../services/api';
import { cap, fmtDate, isoPlus, isoToday, monthName } from '../services/format';

const Ctx = createContext({ items: [], unread: 0, readAll() {}, readOne() {} });
export const useNotifications = () => useContext(Ctx);

// Alerts are worked out from real data (leave requests, holidays, payroll runs, attendance). Nothing is invented.
// Only the ids of alerts you have read are remembered in this browser.
const readKey = (uid) => 'kn-read-alerts:' + uid;
const loadRead = (uid) => { try { return new Set(JSON.parse(localStorage.getItem(readKey(uid)) || '[]')); } catch { return new Set(); } };

function build(user, { leaves, hols, mine, runs }) {
  const out = [], today = isoToday(), soon = isoPlus(14);
  if (user.role !== 'employee')
    leaves.filter((r) => r.status === 'PENDING' && r.employeeId !== user.id).slice(0, 10).forEach((r) =>
      out.push({ id: 'lv-' + r.id, title: 'Leave request waiting', text: r.employeeName + ' asked for ' + cap(r.leaveType) + ' leave from ' + fmtDate(r.startDate) + ' to ' + fmtDate(r.endDate) + '.', to: '/leave' }));
  leaves.filter((r) => r.employeeId === user.id && (r.status === 'APPROVED' || r.status === 'REJECTED') && r.endDate >= today).forEach((r) =>
    out.push({ id: 'own-' + r.id + r.status, title: 'Leave ' + (r.status === 'APPROVED' ? 'approved' : 'rejected'), text: 'Your ' + cap(r.leaveType) + ' leave from ' + fmtDate(r.startDate) + ' to ' + fmtDate(r.endDate) + ' was ' + r.status.toLowerCase() + '.', to: '/leave' }));
  hols.filter((h) => h.holidayDate <= soon).forEach((h) =>
    out.push({ id: 'hol-' + h.holidayDate, title: 'Holiday coming up', text: h.name + ' is on ' + fmtDate(h.holidayDate) + '.' }));
  runs.filter((r) => r.status !== 'FINALIZED').forEach((r) =>
    out.push({ id: 'pr-' + r.id + r.status, title: 'Payroll needs action', text: 'Payroll for ' + monthName(r.period) + ' is ' + (r.status === 'CALCULATED' ? 'calculated and waiting for approval.' : 'approved and waiting to be finalized.'), to: '/payroll' }));
  mine.filter((a) => a.workDate < today && !a.checkOut).slice(0, 5).forEach((a) =>
    out.push({ id: 'co-' + a.workDate, title: 'Missed check-out', text: 'No check-out was recorded on ' + fmtDate(a.workDate) + '.', to: '/attendance' }));
  return out;
}

export function NotificationsProvider({ children }) {
  const { user } = useAuth();
  const { pathname } = useLocation();
  const uid = user ? user.id : null, role = user ? user.role : null;
  const [raw, setRaw] = useState(null), [read, setRead] = useState(new Set());

  useEffect(() => { setRaw(null); setRead(uid ? loadRead(uid) : new Set()); }, [uid]);

  const load = useCallback(() => {
    if (!uid) return;
    const get = (p) => api(p).catch(() => []);
    Promise.all([get('/leave-requests'), get('/holidays'), get('/attendance/me'), role === 'admin' ? get('/payroll/runs') : Promise.resolve([])])
      .then(([leaves, hols, mine, runs]) => setRaw({ uid, leaves, hols, mine, runs }));
  }, [uid, role]);
  useEffect(() => { load(); }, [load, pathname]);
  useEffect(() => { const t = setInterval(load, 60000); return () => clearInterval(t); }, [load]);

  const items = user && raw && raw.uid === uid ? build(user, raw).map((n) => ({ ...n, read: read.has(n.id) })) : [];
  const save = (set) => {
    setRead(set);
    const live = new Set(items.map((n) => n.id)); // forget ids of alerts that no longer exist
    try { localStorage.setItem(readKey(uid), JSON.stringify([...set].filter((id) => live.has(id)))); } catch { /* not sensitive */ }
  };
  const readOne = (id) => save(new Set([...read, id]));
  const readAll = () => save(new Set([...read, ...items.map((n) => n.id)]));
  return <Ctx.Provider value={{ items, unread: items.filter((n) => !n.read).length, readAll, readOne }}>{children}</Ctx.Provider>;
}
