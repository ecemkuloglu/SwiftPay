import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router';
import { z } from 'zod';
import { getAccounts, getLimits } from '../api/accounts';
import { ApiError } from '../api/client';
import { queryKeys } from '../api/queryKeys';
import { createTransfer, type CreateTransferResult } from '../api/transfers';
import { ErrorAlert } from '../components/ErrorAlert';
import type { CreateTransferRequest } from '../types/api';
import { formatDateTime } from '../utils/dates';
import { describeError } from '../utils/errorMessages';
import { presentTransferError } from '../utils/errorPresentation';
import { isValidTrIban, normalizeIban } from '../utils/iban';
import { formatMoney, parseAmountInput } from '../utils/money';

const MAX_DESCRIPTION = 140;

const schema = z.object({
  sourceAccountId: z.string().min(1, 'Choose a source account.'),
  receiverIban: z
    .string()
    .trim()
    .min(1, 'Enter the receiver IBAN.')
    .refine(isValidTrIban, 'Enter a valid TR IBAN (TR + 24 digits).'),
  amount: z.string().superRefine((value, ctx) => {
    const parsed = parseAmountInput(value);
    if (!parsed.ok) {
      ctx.addIssue({ code: 'custom', message: parsed.error });
    }
  }),
  description: z.string().max(MAX_DESCRIPTION, `At most ${MAX_DESCRIPTION} characters.`),
});

type FormValues = z.infer<typeof schema>;
type FieldName = keyof FormValues;
const FIELD_NAMES: FieldName[] = ['sourceAccountId', 'receiverIban', 'amount', 'description'];

const EMPTY: FormValues = { sourceAccountId: '', receiverIban: '', amount: '', description: '' };

type Outcome = { kind: 'success'; result: CreateTransferResult } | { kind: 'error'; error: unknown } | null;

function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

/**
 * Errors after which the request may not have been processed (or is still running): retrying the SAME payload
 * must reuse the SAME Idempotency-Key, so the server replays instead of creating a second transfer.
 */
function keepsIdempotencyKey(error: unknown): boolean {
  if (!(error instanceof ApiError)) {
    return false;
  }
  return (
    error.code === 'NETWORK_ERROR' ||
    error.status >= 500 ||
    error.code === 'REQUEST_IN_PROGRESS' ||
    error.code === 'TOO_MANY_REQUESTS' ||
    error.code === 'VALIDATION_ERROR'
  );
}

function toRequest(values: FormValues): CreateTransferRequest {
  const amount = parseAmountInput(values.amount);
  const description = values.description.trim();
  return {
    sourceAccountId: Number(values.sourceAccountId),
    receiverIban: normalizeIban(values.receiverIban),
    amount: amount.ok ? amount.value : values.amount,
    ...(description ? { description } : {}),
  };
}

export function NewTransferPage() {
  const queryClient = useQueryClient();
  const accounts = useQuery({ queryKey: queryKeys.accounts, queryFn: getAccounts });
  const limits = useQuery({ queryKey: queryKeys.limits, queryFn: getLimits });
  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: EMPTY,
    mode: 'onTouched',
  });
  const { register, handleSubmit, formState, setError, reset, watch } = form;

  // Idempotency: one key per logical transfer (see keepsIdempotencyKey and onSubmit).
  const keyRef = useRef<string>(newIdempotencyKey());
  const lastAttemptRef = useRef<string | null>(null);
  const inFlightRef = useRef(false);
  const [submitting, setSubmitting] = useState(false);
  const [outcome, setOutcome] = useState<Outcome>(null);

  const onSubmit = async (values: FormValues) => {
    if (inFlightRef.current) {
      return; // double-submit guard (the button is disabled too)
    }
    inFlightRef.current = true;
    setSubmitting(true);

    const payload = toRequest(values);
    const fingerprint = JSON.stringify(payload);
    if (lastAttemptRef.current !== null && lastAttemptRef.current !== fingerprint) {
      keyRef.current = newIdempotencyKey(); // the user changed something after a failed attempt
    }
    lastAttemptRef.current = fingerprint;

    try {
      const result = await createTransfer(payload, keyRef.current);
      keyRef.current = newIdempotencyKey(); // next transfer = new key
      lastAttemptRef.current = null;
      setOutcome({ kind: 'success', result });
      await Promise.all(
        [queryKeys.accounts, queryKeys.limits, queryKeys.transfers, queryKeys.statistics].map((queryKey) =>
          queryClient.invalidateQueries({ queryKey }),
        ),
      );
    } catch (error) {
      if (!keepsIdempotencyKey(error)) {
        keyRef.current = newIdempotencyKey(); // definitive outcome: a new attempt is a new transfer
        lastAttemptRef.current = null;
      }
      if (error instanceof ApiError) {
        for (const fieldError of error.fieldErrors) {
          if ((FIELD_NAMES as string[]).includes(fieldError.field)) {
            setError(fieldError.field as FieldName, { type: 'server', message: fieldError.message });
          }
        }
      }
      setOutcome({ kind: 'error', error });
    } finally {
      inFlightRef.current = false;
      setSubmitting(false);
    }
  };

  const startNew = () => {
    reset(EMPTY);
    setOutcome(null);
  };

  /** Back to the filled-in form; the idempotency rules above still decide whether the key is reused. */
  const backToForm = () => setOutcome(null);

  const description = watch('description');
  const sourceId = watch('sourceAccountId');
  const errors = formState.errors;

  if (outcome?.kind === 'success') {
    const { transfer, replayed } = outcome.result;
    const source = accounts.data?.find((a) => a.id === transfer.sourceAccountId);
    return (
      <section className="receipt" aria-labelledby="result-title" role="status" aria-live="polite">
        <p className="kicker">{replayed ? 'Already processed' : 'Completed'}</p>
        <h1 id="result-title">Transfer sent</h1>
        {replayed ? (
          <p className="alert alert-info">This transfer was already processed. No new transfer was made.</p>
        ) : (
          <p className="muted">The amount left your account once, and only once.</p>
        )}
        <dl className="kv">
          <dt>Amount</dt>
          <dd>{formatMoney(transfer.amount, transfer.currency)}</dd>
          <dt>To</dt>
          <dd className="mono">{transfer.maskedReceiverIban}</dd>
          {source && (
            <>
              <dt>From</dt>
              <dd className="mono">{source.maskedIban}</dd>
            </>
          )}
          <dt>When</dt>
          <dd>{formatDateTime(transfer.createdAt)}</dd>
          <dt>Description</dt>
          <dd>{transfer.description || '–'}</dd>
          <dt>Transfer ID</dt>
          <dd>{transfer.id}</dd>
        </dl>
        <div className="actions">
          <Link className="button" to={`/transfers/${transfer.id}`}>
            Open details
          </Link>
          <Link className="button button-secondary" to="/transfers">
            All transfers
          </Link>
          <button type="button" className="button-secondary" onClick={startNew}>
            Send another
          </button>
        </div>
      </section>
    );
  }

  if (outcome?.kind === 'error') {
    const { code, message, traceId } = describeError(outcome.error);
    const { title, tone, label } = presentTransferError(code);
    // Retryable outcomes can be sent again as they are (same payload, same key); the rest go back to the form.
    const canRetry = keepsIdempotencyKey(outcome.error) && code !== 'VALIDATION_ERROR';
    return (
      <section className={`result-card tone-${tone}`} role="alert" aria-labelledby="error-title">
        <p className="kicker">{label}</p>
        <h1 id="error-title">{title}</h1>
        <p>{message}</p>
        {traceId && <p className="support-code">Support code: {traceId}</p>}
        <div className="actions">
          {canRetry ? (
            <>
              <button type="button" disabled={submitting} onClick={() => void handleSubmit(onSubmit)()}>
                {submitting ? 'Sending…' : 'Retry'}
              </button>
              <button type="button" className="button-secondary" onClick={backToForm}>
                Edit transfer
              </button>
            </>
          ) : (
            <>
              <button type="button" onClick={backToForm}>
                Try again
              </button>
              <Link className="button button-secondary" to="/transfers">
                View past transfers
              </Link>
            </>
          )}
        </div>
      </section>
    );
  }

  const source = accounts.data?.find((a) => String(a.id) === sourceId);

  return (
    <>
      <h1 id="new-transfer-title">New money transfer</h1>

      {accounts.isError && <ErrorAlert error={accounts.error} title="Accounts could not be loaded" />}

      <div className="grid-2">
        <form
          className="card form"
          noValidate
          aria-labelledby="new-transfer-title"
          onSubmit={handleSubmit(onSubmit)}
        >
          <div className="field">
            <label htmlFor="sourceAccountId">Source account</label>
            <select
              id="sourceAccountId"
              aria-invalid={errors.sourceAccountId ? true : undefined}
              aria-describedby={errors.sourceAccountId ? 'sourceAccountId-error' : undefined}
              disabled={accounts.isPending}
              {...register('sourceAccountId')}
            >
              <option value="">{accounts.isPending ? 'Loading accounts…' : 'Choose an account'}</option>
              {accounts.data?.map((account) => (
                <option key={account.id} value={String(account.id)}>
                  {account.maskedIban} — {formatMoney(account.balance, account.currency)}
                </option>
              ))}
            </select>
            <FieldErrorText id="sourceAccountId-error" message={errors.sourceAccountId?.message} />
          </div>

          <div className="field">
            <label htmlFor="receiverIban">Receiver IBAN</label>
            <input
              id="receiverIban"
              className="mono"
              autoComplete="off"
              spellCheck={false}
              placeholder="TR00 0000 0000 0000 0000 0000 00"
              aria-invalid={errors.receiverIban ? true : undefined}
              aria-describedby={errors.receiverIban ? 'receiverIban-error' : undefined}
              {...register('receiverIban')}
            />
            <FieldErrorText id="receiverIban-error" message={errors.receiverIban?.message} />
          </div>

          <div className="field">
            <label htmlFor="amount">Amount (TRY)</label>
            <input
              id="amount"
              inputMode="decimal"
              autoComplete="off"
              placeholder="1500,00"
              aria-invalid={errors.amount ? true : undefined}
              aria-describedby={errors.amount ? 'amount-error' : undefined}
              {...register('amount')}
            />
            <FieldErrorText id="amount-error" message={errors.amount?.message} />
          </div>

          <div className="field">
            <label htmlFor="description">Description (optional)</label>
            <textarea
              id="description"
              rows={2}
              maxLength={MAX_DESCRIPTION}
              placeholder="What is this for?"
              aria-invalid={errors.description ? true : undefined}
              aria-describedby="description-count"
              {...register('description')}
            />
            <span id="description-count" className="muted small">
              {description.length}/{MAX_DESCRIPTION}
            </span>
            <FieldErrorText id="description-error" message={errors.description?.message} />
          </div>

          <button type="submit" disabled={submitting}>
            {submitting ? 'Sending…' : 'Send now'}
          </button>
        </form>

        <aside className="card" aria-label="Source account and limits">
          {source ? (
            <>
              <p className="kicker">From {source.currency} account</p>
              <h2>{formatMoney(source.balance, source.currency)} available</h2>
              <p className="muted mono small">{source.maskedIban}</p>
            </>
          ) : (
            <>
              <p className="kicker">Source account</p>
              <p className="muted">Choose an account to see its balance.</p>
            </>
          )}
          {limits.data && (
            <div className="stack">
              <LimitLine label="Per transfer" value={formatMoney(limits.data.perTransferLimit)} />
              <LimitLine label="Daily remaining" value={formatMoney(limits.data.daily.remaining)} />
              <LimitLine label="Monthly remaining" value={formatMoney(limits.data.monthly.remaining)} />
            </div>
          )}
        </aside>
      </div>
    </>
  );
}

function LimitLine({ label, value }: { label: string; value: string }) {
  return (
    <div className="limit-row">
      <span>{label}</span>
      <b>{value}</b>
    </div>
  );
}

function FieldErrorText({ id, message }: { id: string; message: string | undefined }) {
  return message ? (
    <span id={id} className="field-error" role="alert">
      {message}
    </span>
  ) : null;
}
