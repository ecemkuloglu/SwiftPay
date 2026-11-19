import { useQuery } from '@tanstack/react-query';
import { Link, NavLink, Outlet, useLocation } from 'react-router';
import { getLimits } from '../api/accounts';
import { queryKeys } from '../api/queryKeys';
import { useAuth } from '../auth/AuthContext';
import { tokenStorage } from '../auth/tokenStorage';
import { formatMoney } from '../utils/money';
import { Brand } from './Brand';

const navClass = ({ isActive }: { isActive: boolean }) => (isActive ? 'active' : undefined);

export function Layout() {
  const { logout } = useAuth();
  const { pathname } = useLocation();
  const limits = useQuery({ queryKey: queryKeys.limits, queryFn: getLimits });
  const username = tokenStorage.username();
  // "Past transfers" stays active on a transfer's detail page, but not on "New transfer".
  const onPastTransfers = pathname.startsWith('/transfers') && pathname !== '/transfers/new';

  return (
    <div className="shell">
      <aside className="side">
        <Brand />
        <div className="who">
          <b>Signed in</b>
          {username ?? 'SwiftPay customer'}
        </div>
        <nav aria-label="Main">
          <NavLink to="/" end className={navClass}>
            Overview
          </NavLink>
          <NavLink to="/transfers/new" className={navClass}>
            New transfer
          </NavLink>
          <Link
            to="/transfers"
            className={onPastTransfers ? 'active' : undefined}
            aria-current={onPastTransfers ? 'page' : undefined}
          >
            Past transfers
          </Link>
          <NavLink to="/statistics" className={navClass}>
            Monthly stats
          </NavLink>
        </nav>
        <div className="side-foot">
          <p>
            Instant TRY transfers inside SwiftPay.
            {limits.data && (
              <>
                <br />
                Daily remaining {formatMoney(limits.data.daily.remaining)}
              </>
            )}
          </p>
          <button type="button" onClick={logout}>
            Sign out
          </button>
        </div>
      </aside>
      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
