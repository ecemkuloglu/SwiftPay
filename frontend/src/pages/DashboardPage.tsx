import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { getAccounts, getLimits } from '../api/accounts';
import { queryKeys } from '../api/queryKeys';
import { getMonthlyStatistics } from '../api/statistics';
import { AccountCard } from '../components/AccountCard';
import { ErrorAlert } from '../components/ErrorAlert';
import { LimitBar } from '../components/LimitBar';
import { StatisticsTable } from '../components/StatisticsTable';
import { formatMoney } from '../utils/money';

export function DashboardPage() {
  const accounts = useQuery({ queryKey: queryKeys.accounts, queryFn: getAccounts });
  const limits = useQuery({ queryKey: queryKeys.limits, queryFn: getLimits });
  const statistics = useQuery({ queryKey: queryKeys.statistics, queryFn: getMonthlyStatistics });

  return (
    <>
      <div className="page-header">
        <h1>Dashboard</h1>
        <Link className="button" to="/transfers/new">
          New transfer
        </Link>
      </div>

      <section aria-labelledby="accounts-title">
        <h2 id="accounts-title">Accounts</h2>
        {accounts.isPending && <p className="muted">Loading accounts…</p>}
        {accounts.isError && <ErrorAlert error={accounts.error} />}
        {accounts.data && accounts.data.length === 0 && <p className="muted">You have no accounts.</p>}
        <div className="grid">
          {accounts.data?.map((account) => (
            <AccountCard key={account.id} account={account} />
          ))}
        </div>
      </section>

      <section aria-labelledby="limits-title" className="card">
        <h2 id="limits-title">Transfer limits</h2>
        {limits.isPending && <p className="muted">Loading limits…</p>}
        {limits.isError && <ErrorAlert error={limits.error} />}
        {limits.data && (
          <>
            <p className="muted small">Per transfer: {formatMoney(limits.data.perTransferLimit)}</p>
            <LimitBar label="Today" usage={limits.data.daily} />
            <LimitBar label="This month" usage={limits.data.monthly} />
          </>
        )}
      </section>

      <section aria-labelledby="stats-title" className="card">
        <h2 id="stats-title">Monthly statistics</h2>
        {statistics.isPending && <p className="muted">Loading statistics…</p>}
        {statistics.isError && <ErrorAlert error={statistics.error} />}
        {statistics.data && <StatisticsTable stats={statistics.data} />}
      </section>
    </>
  );
}
