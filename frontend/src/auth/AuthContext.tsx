import { useQueryClient } from '@tanstack/react-query';
import {
  createContext,
  useCallback,
  useContext,
  useLayoutEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { useNavigate } from 'react-router';
import { login as loginRequest } from '../api/auth';
import { configureApiClient } from '../api/client';
import { tokenStorage } from './tokenStorage';

interface AuthContextValue {
  isAuthenticated: boolean;
  /** true after a 401 ended the session; the login page shows "Session expired". */
  sessionExpired: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export interface LoginLocationState {
  from?: string;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => tokenStorage.get());
  const [sessionExpired, setSessionExpired] = useState(false);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const endSession = useCallback(() => {
    tokenStorage.clear();
    setToken(null);
    queryClient.clear(); // never show the previous user's data
  }, [queryClient]);

  // Layout effect: the client must know the token source before any child query runs.
  useLayoutEffect(() => {
    configureApiClient({
      getToken: () => tokenStorage.get(),
      // The flag lives in context (not router state): ProtectedRoute then redirects to /login and keeps the
      // current page in `from`, so the user returns to it after signing in again.
      onUnauthorized: () => {
        setSessionExpired(true);
        endSession();
      },
    });
  }, [endSession]);

  const login = useCallback(async (username: string, password: string) => {
    const response = await loginRequest({ username, password });
    tokenStorage.set(response.accessToken, response.expiresIn, username);
    setSessionExpired(false);
    setToken(response.accessToken);
  }, []);

  const logout = useCallback(() => {
    setSessionExpired(false);
    endSession();
    navigate('/login', { replace: true });
  }, [endSession, navigate]);

  const value = useMemo(
    () => ({ isAuthenticated: token !== null, sessionExpired, login, logout }),
    [token, sessionExpired, login, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return value;
}
