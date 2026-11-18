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
      <section>
        <h1>Transfer not found</h1>
        <p>The transfer does not exist or is not yours.</p>
        <Link to="/transfers">Back to transfers</Link>
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
    <section aria-labelledby="detail-title">
      <div className="page-header">
        <h1 id="detail-title">Transfer #{t.id}</h1>
        <StatusBadge status={t.status} />
      </div>
      <dl className="card details">
        <dt>Amount</dt>
        <dd>{formatMoney(t.amount, t.currency)}</dd>
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
      <Link to="/transfers">Back to transfers</Link>
    </section>
  );
}
