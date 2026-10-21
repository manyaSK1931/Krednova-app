import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
// UI guard only. The API repeats every permission check.
export default function ProtectedRoute({ roles, children }) {
  const { user, ready } = useAuth();
  const { pathname } = useLocation();
  if (!ready) return null;
  if (!user) return <Navigate to="/login" replace />;
  if (user.mustChange && pathname !== '/password') return <Navigate to="/password" replace />;
  if (roles && !roles.includes(user.role)) return <Navigate to="/" replace />;
  return children;
}
