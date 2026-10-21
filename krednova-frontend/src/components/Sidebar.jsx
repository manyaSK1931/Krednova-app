import { NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useNotifications } from '../context/NotificationsContext';
const all = ['admin', 'manager', 'employee'];
const items = [
  ['Dashboard', '/', all], ['Employees', '/employees', ['admin', 'manager']],
  ['Attendance', '/attendance', all], ['Leave requests', '/leave', all],
  ['Payroll', '/payroll', ['admin']], ['Payslips', '/payslips', all],
  ['Reports', '/reports', ['admin', 'manager']], ['Notifications', '/notifications', all],
  ['Settings', '/settings', ['admin']],
];
export default function Sidebar({ open, close }) {
  const { user } = useAuth();
  const { unread } = useNotifications();
  return (
    <aside className={'side' + (open ? ' open' : '')}>
      <div className="logo"><i /> KredNova</div>
      <nav>{items.filter((i) => i[2].includes(user.role)).map(([l, to]) => (
        <NavLink key={to} to={to} end={to === '/'} onClick={close}>{l}{to === '/notifications' && unread > 0 && <em className="badge">{unread}</em>}</NavLink>
      ))}</nav>
      <p className="sidefoot">Payroll, people and time in one place.</p>
    </aside>
  );
}
