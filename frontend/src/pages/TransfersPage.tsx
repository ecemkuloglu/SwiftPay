import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { queryKeys } from '../api/queryKeys';
import { listTransfers, type TransferListQuery } from '../api/transfers';
import { ErrorAlert } from '../components/ErrorAlert';
import { StatusBadge } from '../components/StatusBadge';
import { formatDateTime } from '../utils/dates';
import { formatMoney } from '../utils/money';

const PAGE_SIZE = 10;

interface DateRange {
  startDate: string;
  endDate: string;
}

export function TransfersPage() {
  const [page, setPage] = useState(0);
  const [draft, setDraft] = useState<DateRange>({ startDate: '', endDate: '' });
  const [range, setRange] = useState<DateRange>({ startDate: '', endDate: '' });

  const query: TransferListQuery = {
    page,
    size: PAGE_SIZE,
    ...(range.startDate ? { startDate: range.startDate } : {}),
    ...(range.endDate ? { endDate: range.endDate } : {}),
  };
  const transfers = useQuery({
    queryKey: queryKeys.transferList(query),
    queryFn: () => listTransfers(query),
    placeholderData: keepPreviousData,
  });

  const applyFilter = (event: FormEvent) => {
    event.preventDefault();
    setRange(draft);
    setPage(0);
  };

  const clearFilter = () => {
    const empty = { startDate: '', endDate: '' };
    setDraft(empty);
    setRange(empty);
    setPage(0);
  };

  const data = transfers.data;
  return (
    <>
      <div className="page-header">
        <h1>Transfers</h1>
        <Link className="button" to="/transfers/new">
          New transfer
        </Link>
      </div>

      <form className="card filter" onSubmit={applyFilter} aria-label="Filter by date">
        <div className="field">
          <label htmlFor="startDate">From</label>
          <input
            id="startDate"
            type="date"
            value={draft.startDate}
            onChange={(e) => setDraft({ ...draft, startDate: e.target.value })}
          />
        </div>
        <div className="field">
          <label htmlFor="endDate">To</label>
          <input
            id="endDate"
            type="date"
            value={draft.endDate}
            onChange={(e) => setDraft({ ...draft, endDate: e.target.value })}
          />
        </div>
        <button type="submit">Apply</button>
        <button type="button" className="button-secondary" onClick={clearFilter}>
          Clear
        </button>
      </form>

      <div aria-live="polite">
        {transfers.isPending && <p className="muted">Loading transfers…</p>}
        {transfers.isError && <ErrorAlert error={transfers.error} />}
        {data && data.content.length === 0 && <p className="muted">No transfers found.</p>}
      </div>

      {data && data.content.length > 0 && !transfers.isError && (
        <>
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Date</th>
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
              {data.content.map((t) => (
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
          <nav className="pagination" aria-label="Pagination">
            <button
              type="button"
              className="button-secondary"
              disabled={page === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              Previous
            </button>
            <span>
              Page {data.page + 1} of {Math.max(data.totalPages, 1)} ({data.totalElements} transfers)
            </span>
            <button
              type="button"
              className="button-secondary"
              disabled={page + 1 >= data.totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </button>
          </nav>
        </>
      )}
    </>
  );
}
