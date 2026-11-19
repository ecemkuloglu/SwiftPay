import type { MonthlyStat } from '../types/api';
import { formatMonth } from '../utils/dates';
import { formatMoney, toBarValue } from '../utils/money';

/** Visual companion of StatisticsTable (the table carries the data for screen readers). */
export function StatisticsBars({ stats }: { stats: MonthlyStat[] }) {
  const max = Math.max(1, ...stats.map((row) => toBarValue(row.totalTransferredAmount)));
  return (
    <div className="bars" aria-hidden="true">
      {stats.map((row) => (
        <div className="bar-row" key={row.month}>
          <span>{formatMonth(row.month)}</span>
          <progress value={toBarValue(row.totalTransferredAmount)} max={max} />
          <span className="num">{formatMoney(row.totalTransferredAmount)}</span>
        </div>
      ))}
    </div>
  );
}
