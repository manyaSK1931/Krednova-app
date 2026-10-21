import { createContext, useCallback, useContext, useState } from 'react';
const T = createContext(() => {});
export const useToast = () => useContext(T);
export function ToastProvider({ children }) {
  const [list, setList] = useState([]);
  const push = useCallback((text) => {
    const id = Date.now() + Math.random();
    setList((l) => [...l, { id, text }]);
    setTimeout(() => setList((l) => l.filter((t) => t.id !== id)), 3200);
  }, []);
  return (
    <T.Provider value={push}>{children}
      <div className="toasts" role="status" aria-live="polite">{list.map((t) => <div key={t.id} className="toast">{t.text}</div>)}</div>
    </T.Provider>
  );
}
