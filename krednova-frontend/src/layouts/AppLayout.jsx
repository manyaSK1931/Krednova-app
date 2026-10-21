import { useState } from 'react';
import { Link, Outlet, useNavigate } from 'react-router-dom';
import Sidebar from '../components/Sidebar';
import { Avatar } from '../components/UI';
import { useAuth } from '../context/AuthContext';
import { useNotifications } from '../context/NotificationsContext';
const label = { admin: 'Admin / HR', manager: 'Manager', employee: 'Employee' };
export default function AppLayout() {
  const { user, logout, theme, setTheme } = useAuth();
  const { unread } = useNotifications();
  const [open, setOpen] = useState(false);
  const nav = useNavigate();
  return (
    <div className="app">
      <Sidebar open={open} close={() => setOpen(false)} />
      <div className="main">
        <header className="top">
          <button className="menu" aria-label="Open menu" onClick={() => setOpen(!open)}>Menu</button>
          <span className="who"><Avatar name={user.name} i={+user.employeeId.slice(3)} /><span><b>{user.name}</b><small>{label[user.role]}</small></span></span>
          <span className="tools">
            <Link className="btn alt" to="/password">Change password</Link>
            <Link className="btn alt" to="/notifications">Alerts{unread > 0 && <em className="badge">{unread}</em>}</Link>
            <button className="btn alt" onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')} aria-pressed={theme === 'dark'}>{theme === 'dark' ? 'Light mode' : 'Dark mode'}</button>
            <button className="btn alt" onClick={async () => { await logout(); nav('/login'); }}>Sign out</button>
          </span>
        </header>
        <main className="page"><Outlet /></main>
      </div>
    </div>
  );
}
