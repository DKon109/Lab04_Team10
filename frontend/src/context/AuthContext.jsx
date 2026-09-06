import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import * as authApi from '../api/authApi';
import { storeSession, clearSession, getToken, getStoredUser } from '../api/apiClient';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(getStoredUser);
  const [initialising, setInitialising] = useState(() => Boolean(getToken()));

  // Re-validate a restored session against /api/auth/me; a rejected or
  // expired token sends the user back to the auth page.
  useEffect(() => {
    if (!getToken()) return;
    let cancelled = false;
    authApi
      .me()
      .then((current) => {
        if (!cancelled) setUser(current);
      })
      .catch(() => {
        clearSession();
        if (!cancelled) setUser(null);
      })
      .finally(() => {
        if (!cancelled) setInitialising(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    const onUnauthorized = () => setUser(null);
    window.addEventListener('tixora:unauthorized', onUnauthorized);
    return () => window.removeEventListener('tixora:unauthorized', onUnauthorized);
  }, []);

  const login = useCallback(async (email, password) => {
    const response = await authApi.login(email, password);
    const nextUser = { id: response.id, email: response.email, role: response.role };
    storeSession({
      token: response.token,
      expiresInSeconds: response.expiresInSeconds,
      user: nextUser,
    });
    setUser(nextUser);
    return nextUser;
  }, []);

  // Registration returns no token, so log straight in for a smooth demo flow.
  const register = useCallback(
    async (email, password) => {
      await authApi.register(email, password);
      return login(email, password);
    },
    [login]
  );

  const logout = useCallback(() => {
    clearSession();
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, initialising, login, register, logout }),
    [user, initialising, login, register, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
