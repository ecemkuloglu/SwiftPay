import { Navigate, Outlet, useLocation } from 'react-router';
import type { LoginLocationState } from './AuthContext';
import { useAuth } from './AuthContext';

/** Signed-out users are sent to /login; the original path travels in router state, never in the URL. */
export function ProtectedRoute() {
  const { isAuthenticated } = useAuth();
  const location = useLocation();
  if (!isAuthenticated) {
    const state: LoginLocationState = { from: location.pathname };
    return <Navigate to="/login" replace state={state} />;
  }
  return <Outlet />;
}
