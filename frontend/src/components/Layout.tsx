import { NavLink, Outlet } from 'react-router';
import { useAuth } from '../auth/AuthContext';

export function Layout() {
  const { logout } = useAuth();
  return (
    <>
      <header className="topbar">
        <span className="brand">SwiftPay</span>
        <nav aria-label="Main">
          <NavLink to="/" end>
            Dashboard
          </NavLink>
          <NavLink to="/transfers/new">New transfer</NavLink>
          <NavLink to="/transfers" end>
            Transfers
          </NavLink>
        </nav>
        <button type="button" className="button-secondary" onClick={logout}>
          Sign out
        </button>
      </header>
      <main className="container">
        <Outlet />
      </main>
    </>
  );
}
