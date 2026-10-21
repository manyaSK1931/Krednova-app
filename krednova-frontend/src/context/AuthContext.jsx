import { createContext, useContext, useEffect, useState } from 'react';
import { api, ensureCsrf } from '../services/api';

const Ctx = createContext(null);
export const useAuth = () => useContext(Ctx);
const toUser = (u) => ({ name: u.name, email: u.email, role: u.role.toLowerCase(), employeeId: u.employeeId, id: u.id, mustChange: !!u.mustChangePassword });

// The session is an HttpOnly cookie set by Spring Boot. React keeps only the display profile in memory.
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(false);
  const [theme, setTheme] = useState(() => { try { return localStorage.getItem('kn-theme') || 'light'; } catch { return 'light'; } });
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try { localStorage.setItem('kn-theme', theme); } catch { /* theme is not sensitive */ }
  }, [theme]);
  useEffect(() => {
    Promise.all([ensureCsrf(), api('/auth/me')]).then(([, u]) => setUser(toUser(u))).catch(() => {}).finally(() => setReady(true));
  }, []);
  // If the server says the session is over (timeout, deactivated, password changed), drop back to the sign-in page.
  useEffect(() => {
    const out = () => setUser(null);
    window.addEventListener('kn-unauthorized', out);
    return () => window.removeEventListener('kn-unauthorized', out);
  }, []);
  const login = async (email, password) => {
    await ensureCsrf();
    setUser(toUser(await api('/auth/login', { method: 'POST', body: { email, password } })));
  };
  const changePassword = async (currentPassword, newPassword) => {
    await api('/auth/change-password', { method: 'POST', body: { currentPassword, newPassword } });
    setUser(null); // the server ended this session; the person signs in again with the new password
  };
  const logout = async () => { try { await api('/auth/logout', { method: 'POST' }); } finally { setUser(null); } };
  return (
    <Ctx.Provider value={{ user, ready, login, logout, changePassword, theme, setTheme }}>
      {children}
    </Ctx.Provider>
  );
}
