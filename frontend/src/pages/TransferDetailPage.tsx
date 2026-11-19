import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { ApiError } from '../api/client';
import { queryKeys } from '../api/queryKeys';
import { getTransfer } from '../api/transfers';
import { ErrorAlert } from '../components/ErrorAlert';
import { StatusBadge } from '../components/StatusBadge';
import { formatDateTime } from '../utils/dates';
import { groupIban } from '../utils/iban';
import { formatMoney } from '../utils/money';

export function TransferDetailPage() {
  const { id: rawId } = useParams();
  const id = Number(rawId);
  const validId = Number.isSafeInteger(id) && id > 0;
  const transfer = useQuery({
    queryKey: queryKeys.transfer(id),
    queryFn: () => getTransfer(id),
    enabled: validId,
  });

  const notFound =
    !validId || (transfer.error instanceof ApiError && transfer.error.code === 'TRANSFER_NOT_FOUND');
  if (notFound) {
    return (
      <section className="result-card tone-network" aria-labelledby="not-found-title">
        <h1 id="not-found-title">Transfer not found</h1>
        <p>The transfer does not exist or is not yours.</p>
        <div className="actions">
          <Link className="button button-secondary" to="/transfers">
            Back to transfers
          </Link>
        </div>
      </section>
    );
  }
  if (transfer.isPending) {
    return <p className="muted">Loading transfer…</p>;
  }
  if (transfer.isError) {
    return <ErrorAlert error={transfer.error} />;
  }

  const t = transfer.data;
  return (
    <>
      <Link className="button button-secondary back" to="/transfers">
        ← All transfers
      </Link>
      <section className="receipt" aria-labelledby="detail-title">
        <h1 id="detail-title" className="kicker">
          Transfer #{t.id}
        </h1>
        <p className="amount">{formatMoney(t.amount, t.currency)}</p>
        <StatusBadge status={t.status} />
        <dl className="kv">
          <dt>From</dt>
          <dd className="mono">{t.sourceAccountMaskedIban}</dd>
          <dt>To</dt>
          <dd className="mono">{groupIban(t.receiverIban)}</dd>
          <dt>Description</dt>
          <dd>{t.description || '–'}</dd>
          <dt>Created</dt>
          <dd>{formatDateTime(t.createdAt)}</dd>
          <dt>Completed</dt>
          <dd>{formatDateTime(t.completedAt)}</dd>
          {t.failureReason && (
            <>
              <dt>Failure reason</dt>
              <dd>{t.failureReason}</dd>
            </>
          )}
        </dl>
      </section>
    </>
  );
}
