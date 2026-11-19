/** Title and colour group of the full-page error card after a failed transfer. */
export type ErrorTone = 'validation' | 'balance' | 'limit' | 'duplicate' | 'network';

const PRESENTATION: Record<string, { title: string; tone: ErrorTone }> = {
  INSUFFICIENT_BALANCE: { title: 'Not enough balance', tone: 'balance' },
  PER_TRANSFER_LIMIT_EXCEEDED: { title: 'Transfer limit exceeded', tone: 'limit' },
  DAILY_LIMIT_EXCEEDED: { title: 'Transfer limit exceeded', tone: 'limit' },
  MONTHLY_LIMIT_EXCEEDED: { title: 'Transfer limit exceeded', tone: 'limit' },
  TOO_MANY_REQUESTS: { title: 'Too many transfers', tone: 'limit' },
  VALIDATION_ERROR: { title: 'This transfer cannot be sent', tone: 'validation' },
  SAME_ACCOUNT_TRANSFER: { title: 'This transfer cannot be sent', tone: 'validation' },
  RECEIVER_NOT_FOUND: { title: 'Receiver not found', tone: 'validation' },
  ACCOUNT_NOT_OWNED: { title: 'This transfer cannot be sent', tone: 'validation' },
  IDEMPOTENCY_KEY_CONFLICT: { title: 'Duplicate request blocked', tone: 'duplicate' },
  IDEMPOTENCY_KEY_REQUIRED: { title: 'Duplicate request blocked', tone: 'duplicate' },
  REQUEST_IN_PROGRESS: { title: 'Still processing', tone: 'duplicate' },
  NETWORK_ERROR: { title: 'Network error', tone: 'network' },
};

export function presentTransferError(code: string): { title: string; tone: ErrorTone; label: string } {
  const known = PRESENTATION[code] ?? { title: 'Transfer failed', tone: 'network' as const };
  return { ...known, label: code.replaceAll('_', ' ') };
}
