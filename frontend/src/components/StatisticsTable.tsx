import type { MonthlyStat } from '../types/api';
import { formatMonth } from '../utils/dates';
import { formatMoney } from '../utils/money';

/** Rows in the order the API returns them (newest month first). */
export function StatisticsTable({ stats }: { stats: MonthlyStat[] }) {
  return (
    <table className="table">
      <caption className="visually-hidden">Completed transfers per month, newest first</caption>
      <thead>
        <tr>
          <th scope="col">Month</th>
          <th scope="col" className="num">
            Transfers
          </th>
          <th scope="col" className="num">
            Total
          </th>
        </tr>
      </thead>
      <tbody>
        {stats.map((row) => (
          <tr key={row.month}>
            <td>{formatMonth(row.month)}</td>
            <td className="num">{row.totalTransferCount}</td>
            <td className="num">{formatMoney(row.totalTransferredAmount)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
