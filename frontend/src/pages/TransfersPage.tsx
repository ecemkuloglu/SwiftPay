import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { queryKeys } from '../api/queryKeys';
import { listTransfers, type TransferListQuery } from '../api/transfers';
import { ErrorAlert } from '../components/ErrorAlert';
import { TransferTable } from '../components/TransferTable';

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
      <div className="page-top">
        <div>
          <p className="kicker">Customer only</p>
          <h1>Past transfers</h1>
        </div>
        <Link className="button" to="/transfers/new">
          New transfer
        </Link>
      </div>

      <form className="filters" onSubmit={applyFilter} aria-label="Filter by date">
        <div className="field">
          <label htmlFor="startDate">Start date</label>
          <input
            id="startDate"
            type="date"
            value={draft.startDate}
            onChange={(e) => setDraft({ ...draft, startDate: e.target.value })}
          />
        </div>
        <div className="field">
          <label htmlFor="endDate">End date</label>
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

      <section className="card" aria-label="Transfers">
        <div aria-live="polite">
          {transfers.isPending && <p className="muted">Loading transfers…</p>}
          {transfers.isError && <ErrorAlert error={transfers.error} />}
        </div>

        {data && !transfers.isError && (
          <>
            <TransferTable items={data.content} emptyText="No transfers found." />
            {data.content.length > 0 && (
              <nav className="pagination" aria-label="Pagination">
                <span>
                  {data.totalElements} transfers · page {data.page + 1} of {Math.max(data.totalPages, 1)}
                </span>
                <div>
                  <button
                    type="button"
                    className="button-secondary"
                    disabled={page === 0}
                    onClick={() => setPage((p) => p - 1)}
                  >
                    Previous
                  </button>
                  <button
                    type="button"
                    className="button-secondary"
                    disabled={page + 1 >= data.totalPages}
                    onClick={() => setPage((p) => p + 1)}
                  >
                    Next
                  </button>
                </div>
              </nav>
            )}
          </>
        )}
      </section>
    </>
  );
}
