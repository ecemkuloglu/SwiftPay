import { ApiError } from '../api/client';

/** Clear, user-facing text per error code. The server's own message is never shown raw for these. */
const MESSAGES: Record<string, string> = {
  INSUFFICIENT_BALANCE: 'The source account does not have enough balance for this transfer.',
  PER_TRANSFER_LIMIT_EXCEEDED: 'The amount is above the per-transfer limit of 50.000,00 TRY.',
  DAILY_LIMIT_EXCEEDED: 'This transfer would exceed your daily transfer limit.',
  MONTHLY_LIMIT_EXCEEDED: 'This transfer would exceed your monthly transfer limit.',
  SAME_ACCOUNT_TRANSFER: 'The receiver IBAN belongs to the source account. Choose a different receiver.',
  RECEIVER_NOT_FOUND: 'No account with this IBAN exists in SwiftPay.',
  ACCOUNT_NOT_OWNED: 'The selected source account does not belong to you.',
  VALIDATION_ERROR: 'Some fields are not valid. Please check the highlighted fields.',
  IDEMPOTENCY_KEY_CONFLICT: 'This request conflicts with an earlier one. Please submit the transfer again.',
  IDEMPOTENCY_KEY_REQUIRED: 'The request could not be identified. Please submit the transfer again.',
  REQUEST_IN_PROGRESS: 'This transfer is still being processed. Please wait a moment and retry.',
  TOO_MANY_REQUESTS: 'Too many transfers in a short time.',
  TOO_MANY_ATTEMPTS: 'Too many failed sign-in attempts.',
  INVALID_CREDENTIALS: 'Invalid username or password.',
  INVALID_DATE_RANGE: 'The start date must not be after the end date.',
  TRANSFER_NOT_FOUND: 'Transfer not found.',
  NETWORK_ERROR: 'The server could not be reached. Check your connection and try again.',
  UNAUTHORIZED: 'Your session has expired. Please sign in again.',
};

const FALLBACK = 'Something went wrong. Please try again later.';

export interface DescribedError {
  code: string;
  message: string;
  traceId: string | undefined;
  retryAfter: number | undefined;
}

export function describeError(error: unknown): DescribedError {
  if (error instanceof ApiError) {
    let message = MESSAGES[error.code] ?? FALLBACK;
    if (
      error.retryAfter !== undefined &&
      (error.code === 'TOO_MANY_REQUESTS' || error.code === 'TOO_MANY_ATTEMPTS')
    ) {
      message += ` Please try again in ${formatSeconds(error.retryAfter)}.`;
    }
    return { code: error.code, message, traceId: error.traceId, retryAfter: error.retryAfter };
  }
  return { code: 'UNKNOWN_ERROR', message: FALLBACK, traceId: undefined, retryAfter: undefined };
}

function formatSeconds(seconds: number): string {
  if (seconds < 60) {
    return `${seconds} second${seconds === 1 ? '' : 's'}`;
  }
  const minutes = Math.ceil(seconds / 60);
  return `${minutes} minute${minutes === 1 ? '' : 's'}`;
}
