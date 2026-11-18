// Hand-written from API_SPEC.md. Field names are exact. Money is ALWAYS a string ("1500.00").

export type Money = string;
/** ISO-8601 UTC timestamp, e.g. "2026-05-14T09:30:00Z". */
export type IsoInstant = string;

export interface ApiEnvelope<T> {
  data: T;
  traceId: string;
  timestamp: IsoInstant;
}

export interface FieldError {
  field: string;
  message: string;
}

export interface ErrorBody {
  code: string;
  message: string;
  traceId: string;
  timestamp: IsoInstant;
  fieldErrors?: FieldError[];
}

export type ErrorCode =
  | 'VALIDATION_ERROR'
  | 'IDEMPOTENCY_KEY_REQUIRED'
  | 'INVALID_DATE_RANGE'
  | 'UNAUTHORIZED'
  | 'INVALID_CREDENTIALS'
  | 'ACCOUNT_NOT_OWNED'
  | 'TRANSFER_NOT_FOUND'
  | 'RECEIVER_NOT_FOUND'
  | 'NOT_FOUND'
  | 'METHOD_NOT_ALLOWED'
  | 'IDEMPOTENCY_KEY_CONFLICT'
  | 'REQUEST_IN_PROGRESS'
  | 'INSUFFICIENT_BALANCE'
  | 'PER_TRANSFER_LIMIT_EXCEEDED'
  | 'DAILY_LIMIT_EXCEEDED'
  | 'MONTHLY_LIMIT_EXCEEDED'
  | 'SAME_ACCOUNT_TRANSFER'
  | 'TOO_MANY_REQUESTS'
  | 'TOO_MANY_ATTEMPTS'
  | 'INTERNAL_ERROR';

// ---- auth -------------------------------------------------------------------------------------

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
}

// ---- accounts ---------------------------------------------------------------------------------

export interface Account {
  id: number;
  iban: string;
  maskedIban: string;
  currency: 'TRY';
  balance: Money;
}

export interface LimitUsage {
  limit: Money;
  used: Money;
  remaining: Money;
}

export interface Limits {
  currency: 'TRY';
  perTransferLimit: Money;
  daily: LimitUsage;
  monthly: LimitUsage;
}

// ---- transfers --------------------------------------------------------------------------------

export type TransferStatus = 'PENDING' | 'COMPLETED' | 'FAILED';

export interface CreateTransferRequest {
  sourceAccountId: number;
  receiverIban: string;
  amount: Money;
  description?: string;
}

/** POST /transfers response. */
export interface Transfer {
  id: number;
  sourceAccountId: number;
  receiverIban: string;
  maskedReceiverIban: string;
  amount: Money;
  currency: 'TRY';
  description: string | null;
  status: TransferStatus;
  createdAt: IsoInstant;
  completedAt: IsoInstant | null;
}

/** GET /transfers/{id} response. */
export interface TransferDetail extends Transfer {
  sourceAccountMaskedIban: string;
  failureReason?: string;
}

export interface TransferListItem {
  id: number;
  maskedReceiverIban: string;
  amount: Money;
  status: TransferStatus;
  createdAt: IsoInstant;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ---- statistics -------------------------------------------------------------------------------

export interface MonthlyStat {
  month: string; // yyyy-MM (Europe/Istanbul)
  totalTransferCount: number;
  totalTransferredAmount: Money;
}
