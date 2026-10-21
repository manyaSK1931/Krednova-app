import { useEffect, useState } from 'react';
import { Routes, Route } from 'react-router-dom';
import { onSlow } from './services/api';
import ProtectedRoute from './components/ProtectedRoute';
import AppLayout from './layouts/AppLayout';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Employees from './pages/Employees';
import Attendance from './pages/Attendance';
import LeaveManagement from './pages/LeaveManagement';
import Payroll from './pages/Payroll';
import Payslips from './pages/Payslips';
import Reports from './pages/Reports';
import Notifications from './pages/Notifications';
import Settings from './pages/Settings';
import ChangePassword from './pages/ChangePassword';

const only = (roles, el) => <ProtectedRoute roles={roles}>{el}</ProtectedRoute>;

function SlowBanner() {
  const [slow, setSlow] = useState(false);
  useEffect(() => onSlow(setSlow), []);
  return slow ? <div className="slow-banner" role="status">The server is waking up - this can take up to a minute the first time. Please wait...</div> : null;
}

export default function App() {
  return (
    <>
    <SlowBanner />
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route element={<ProtectedRoute><AppLayout /></ProtectedRoute>}>
        <Route index element={<Dashboard />} />
        <Route path="employees" element={only(['admin', 'manager'], <Employees />)} />
        <Route path="attendance" element={<Attendance />} />
        <Route path="leave" element={<LeaveManagement />} />
        <Route path="payroll" element={only(['admin'], <Payroll />)} />
        <Route path="payslips" element={<Payslips />} />
        <Route path="reports" element={only(['admin', 'manager'], <Reports />)} />
        <Route path="notifications" element={<Notifications />} />
        <Route path="password" element={<ChangePassword />} />
        <Route path="settings" element={only(['admin'], <Settings />)} />
      </Route>
    </Routes>
    </>
  );
}
