import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { getAccounts, getLimits } from '../api/accounts';
import { queryKeys } from '../api/queryKeys';
import { listTransfers, type TransferListQuery } from '../api/transfers';
import { AccountCard } from '../components/AccountCard';
import { ErrorAlert } from '../components/ErrorAlert';
import { LimitBar } from '../components/LimitBar';
import { TransferTable } from '../components/TransferTable';
import { formatMoney } from '../utils/money';

const LATEST: TransferListQuery = { page: 0, size: 4 };

export function DashboardPage() {
  const accounts = useQuery({ queryKey: queryKeys.accounts, queryFn: getAccounts });
  const limits = useQuery({ queryKey: queryKeys.limits, queryFn: getLimits });
  const latest = useQuery({ queryKey: queryKeys.transferList(LATEST), queryFn: () => listTransfers(LATEST) });

  return (
    <>
      <div className="page-top">
        <div>
          <p className="kicker">Overview</p>
          <h1>Careful money, moved in seconds.</h1>
        </div>
        <Link className="button" to="/transfers/new">
          New transfer
        </Link>
      </div>

      <section aria-labelledby="accounts-title">
        <h2 id="accounts-title" className="visually-hidden">
          Accounts
        </h2>
        {accounts.isPending && <p className="muted">Loading accounts…</p>}
        {accounts.isError && <ErrorAlert error={accounts.error} />}
        {accounts.data && accounts.data.length === 0 && <p className="muted">You have no accounts.</p>}
        <div className="overview-grid">
          {accounts.data?.map((account) => (
            <AccountCard key={account.id} account={account} />
          ))}
          <section aria-labelledby="limits-title" className="card">
            <p className="kicker">Transfer limits</p>
            <h3 id="limits-title">What’s left</h3>
            {limits.isPending && <p className="muted">Loading limits…</p>}
            {limits.isError && <ErrorAlert error={limits.error} />}
            {limits.data && (
              <div className="stack">
                <LimitBar label="Today" usage={limits.data.daily} />
                <LimitBar label="This month" usage={limits.data.monthly} />
                <p className="muted small">Per transfer cap {formatMoney(limits.data.perTransferLimit)}</p>
              </div>
            )}
          </section>
        </div>
      </section>

      <section aria-labelledby="latest-title" className="card stack">
        <p className="kicker">Recent</p>
        <h2 id="latest-title">Latest transfers</h2>
        {latest.isPending && <p className="muted">Loading transfers…</p>}
        {latest.isError && <ErrorAlert error={latest.error} />}
        {latest.data && <TransferTable items={latest.data.content} emptyText="No transfers yet." />}
        <p className="small">
          <Link to="/transfers">All transfers</Link>
        </p>
      </section>
    </>
  );
}
