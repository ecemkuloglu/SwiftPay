import { useQuery } from '@tanstack/react-query';
import { queryKeys } from '../api/queryKeys';
import { getMonthlyStatistics } from '../api/statistics';
import { ErrorAlert } from '../components/ErrorAlert';
import { StatisticsBars } from '../components/StatisticsBars';
import { StatisticsTable } from '../components/StatisticsTable';

export function StatisticsPage() {
  const statistics = useQuery({ queryKey: queryKeys.statistics, queryFn: getMonthlyStatistics });

  return (
    <>
      <p className="kicker">Completed transfers only</p>
      <h1>Monthly transfer statistics</h1>
      <section className="card" aria-label="Monthly statistics">
        {statistics.isPending && <p className="muted">Loading statistics…</p>}
        {statistics.isError && <ErrorAlert error={statistics.error} />}
        {statistics.data && (
          <>
            <StatisticsTable stats={statistics.data} />
            <StatisticsBars stats={statistics.data} />
          </>
        )}
      </section>
    </>
  );
}
