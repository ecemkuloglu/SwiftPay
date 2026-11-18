import type { TransferStatus } from '../types/api';

const LABELS: Record<TransferStatus, string> = {
  COMPLETED: 'Completed',
  FAILED: 'Failed',
  PENDING: 'Pending',
};

export function StatusBadge({ status }: { status: TransferStatus }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{LABELS[status] ?? status}</span>;
}
