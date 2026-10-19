import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { api, hasToken, setToken } from './api';
import { useLocalStorage } from './utils';

// ---------------- auth ----------------
const AuthCtx = createContext(null);
export const useAuth = () => useContext(AuthCtx);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(hasToken());

  useEffect(() => {
    if (!hasToken()) return;
    api
      .me()
      .then(setUser)
      .catch(() => setToken(null))
      .finally(() => setLoading(false));
  }, []);

  const value = useMemo(
    () => ({
      user,
      loading,
      async login(username, password) {
        const res = await api.login({ username, password });
        setToken(res.token);
        setUser(res.user);
        return res.user;
      },
      async register(username, email, password) {
        const res = await api.register({ username, email, password });
        setToken(res.token);
        setUser(res.user);
        return res.user;
      },
      logout() {
        setToken(null);
        setUser(null);
      },
    }),
    [user, loading],
  );
  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>;
}

// ---------------- toasts ----------------
const ToastCtx = createContext(null);
export const useToast = () => useContext(ToastCtx);

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const idRef = useRef(0);

  const dismiss = useCallback((id) => setToasts((t) => t.filter((x) => x.id !== id)), []);
  const push = useCallback(
    (type, title, message, ms = 5000) => {
      const id = ++idRef.current;
      setToasts((t) => [...t.slice(-3), { id, type, title, message }]);
      setTimeout(() => dismiss(id), ms);
    },
    [dismiss],
  );
  const api_ = useMemo(
    () => ({
      success: (title, msg) => push('success', title, msg),
      error: (title, msg) => push('error', title, msg, 7000),
      info: (title, msg) => push('info', title, msg),
      warn: (title, msg) => push('warn', title, msg, 8000),
    }),
    [push],
  );

  return (
    <ToastCtx.Provider value={api_}>
      {children}
      <div className="toasts" role="status" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast toast-${t.type}`} onClick={() => dismiss(t.id)}>
            <strong>{t.title}</strong>
            {t.message && <span>{t.message}</span>}
          </div>
        ))}
      </div>
    </ToastCtx.Provider>
  );
}

// ---------------- watchlist ----------------
export function useWatchlist() {
  const [ids, setIds] = useLocalStorage('gavel_watch', []);
  const toggle = useCallback(
    (id) => setIds((cur) => (cur.includes(id) ? cur.filter((x) => x !== id) : [...cur, id])),
    [setIds],
  );
  return { ids, toggle, has: (id) => ids.includes(id) };
}
