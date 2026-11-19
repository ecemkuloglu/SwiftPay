import { Link } from 'react-router';
import type { TransferListItem } from '../types/api';
import { formatDateTime } from '../utils/dates';
import { formatMoney } from '../utils/money';
import { StatusBadge } from './StatusBadge';

/** Shared table for the overview's latest transfers and the paged list. */
export function TransferTable({ items, emptyText }: { items: TransferListItem[]; emptyText: string }) {
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th scope="col">When</th>
            <th scope="col">Receiver</th>
            <th scope="col" className="num">
              Amount
            </th>
            <th scope="col">Status</th>
            <th scope="col">
              <span className="visually-hidden">Details</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {items.length === 0 && (
            <tr>
              <td colSpan={5} className="muted">
                {emptyText}
              </td>
            </tr>
          )}
          {items.map((t) => (
            <tr key={t.id}>
              <td>{formatDateTime(t.createdAt)}</td>
              <td className="mono">{t.maskedReceiverIban}</td>
              <td className="num">{formatMoney(t.amount)}</td>
              <td>
                <StatusBadge status={t.status} />
              </td>
              <td>
                <Link to={`/transfers/${t.id}`} aria-label={`Details of transfer ${t.id}`}>
                  Details
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
