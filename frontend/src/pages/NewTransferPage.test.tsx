import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import type { UserEvent } from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { ALI_IBAN, apiError, ok, transferFor } from '../test/handlers';
import { renderApp } from '../test/renderApp';
import { server } from '../test/server';

interface Captured {
  key: string | null;
  body: Record<string, unknown>;
}

/** Records every POST /transfers (Idempotency-Key + body) and answers with the given responses in order. */
function capturePosts(...responses: Array<() => Response | Promise<Response>>): Captured[] {
  const calls: Captured[] = [];
  server.use(
    http.post('*/api/transfers', async ({ request }) => {
      calls.push({
        key: request.headers.get('Idempotency-Key'),
        body: (await request.json()) as Record<string, unknown>,
      });
      const respond = responses[Math.min(calls.length - 1, responses.length - 1)];
      return respond ? respond() : ok(transferFor('1500.00'), { status: 201 });
    }),
  );
  return calls;
}

async function fillForm(user: UserEvent, { iban = ALI_IBAN, amount = '1500', description = 'Kira' } = {}) {
  await screen.findByRole('option', { name: /TR62 \*{4}/ });
  await user.selectOptions(screen.getByLabelText('Source account'), '1');
  await user.clear(screen.getByLabelText('Receiver IBAN'));
  await user.type(screen.getByLabelText('Receiver IBAN'), iban);
  await user.clear(screen.getByLabelText('Amount (TRY)'));
  await user.type(screen.getByLabelText('Amount (TRY)'), amount);
  if (description) {
    await user.clear(screen.getByLabelText('Description (optional)'));
    await user.type(screen.getByLabelText('Description (optional)'), description);
  }
}

const submit = (user: UserEvent) => user.click(screen.getByRole('button', { name: /send now|retry/i }));

describe('New transfer form', () => {
  it('shows client-side validation messages and sends nothing', async () => {
    const calls = capturePosts();
    const { user } = renderApp('/transfers/new');
    await screen.findByRole('option', { name: /TR62/ });

    await submit(user);
    expect(await screen.findByText('Choose a source account.')).toBeInTheDocument();
    expect(screen.getByText('Enter the receiver IBAN.')).toBeInTheDocument();
    expect(screen.getByText('Enter an amount.')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Receiver IBAN'), 'TR640006200000000000000002'); // bad checksum
    await user.type(screen.getByLabelText('Amount (TRY)'), '10,001');
    await submit(user);
    expect(await screen.findByText('Enter a valid TR IBAN (TR + 24 digits).')).toBeInTheDocument();
    expect(screen.getByText('At most 2 decimal places.')).toBeInTheDocument();
    expect(screen.getByLabelText('Amount (TRY)')).toHaveAttribute('aria-invalid', 'true');
    expect(calls).toHaveLength(0);
  });

  it('sends the normalized payload as strings and shows the result', async () => {
    const calls = capturePosts();
    const { user, queryClient } = renderApp('/transfers/new');
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');

    await fillForm(user, {
      iban: 'tr69 0006 2000 0000 0000 0020 01',
      amount: '1500',
      description: '  Kira  ',
    });
    await submit(user);

    expect(await screen.findByRole('heading', { name: 'Transfer sent' })).toBeInTheDocument();
    expect(calls).toHaveLength(1);
    expect(calls[0]?.body).toEqual({
      sourceAccountId: 1,
      receiverIban: ALI_IBAN,
      amount: '1500.00',
      description: 'Kira',
    });
    expect(calls[0]?.key).toMatch(/^[0-9a-f-]{36}$/);
    expect(screen.getByText('1.500,00 TRY')).toBeInTheDocument();
    expect(screen.getByText('TR69 **** **** **** **** 2001')).toBeInTheDocument();
    expect(screen.getByText('TR62 **** **** **** **** 1001')).toBeInTheDocument(); // "From"
    expect(screen.getByRole('link', { name: 'Open details' })).toHaveAttribute('href', '/transfers/101');
    expect(screen.queryByText(/already processed/)).not.toBeInTheDocument();
    for (const key of [['accounts'], ['limits'], ['transfers'], ['statistics']]) {
      expect(invalidate).toHaveBeenCalledWith({ queryKey: key });
    }
  });

  it('shows the replay notice when Idempotent-Replayed is true', async () => {
    capturePosts(() =>
      ok(transferFor('1500.00'), { status: 200, headers: { 'Idempotent-Replayed': 'true' } }),
    );
    const { user } = renderApp('/transfers/new');

    await fillForm(user);
    await submit(user);

    expect(await screen.findByText(/This transfer was already processed/)).toBeInTheDocument();
  });

  it('uses a new key for the next transfer after a success', async () => {
    const calls = capturePosts();
    const { user } = renderApp('/transfers/new');

    await fillForm(user);
    await submit(user);
    await user.click(await screen.findByRole('button', { name: 'Send another' }));
    expect(screen.getByLabelText('Receiver IBAN')).toHaveValue('');
    await fillForm(user);
    await submit(user);
    await screen.findByRole('heading', { name: 'Transfer sent' });

    expect(calls).toHaveLength(2);
    expect(calls[1]?.key).not.toBe(calls[0]?.key);
  });

  it('reuses the SAME key when retrying the same payload after a 5xx, and a NEW key after an edit', async () => {
    const calls = capturePosts(
      () => apiError(500, 'INTERNAL_ERROR'),
      () => apiError(503, 'INTERNAL_ERROR'),
    );
    const { user } = renderApp('/transfers/new');

    await fillForm(user);
    await submit(user);
    await screen.findByText('Something went wrong. Please try again later.');
    await submit(user); // "Retry" on the error card resends as it is
    await waitFor(() => expect(calls).toHaveLength(2));
    expect(calls[1]?.key).toBe(calls[0]?.key);

    await user.click(await screen.findByRole('button', { name: 'Edit transfer' }));
    expect(screen.getByLabelText('Receiver IBAN')).toHaveValue(ALI_IBAN); // the form keeps what was typed
    await user.clear(screen.getByLabelText('Amount (TRY)'));
    await user.type(screen.getByLabelText('Amount (TRY)'), '1600');
    await submit(user);
    await waitFor(() => expect(calls).toHaveLength(3));
    expect(calls[2]?.key).not.toBe(calls[1]?.key);
  });

  it('reuses the SAME key after a network error', async () => {
    const calls = capturePosts(
      () => HttpResponse.error(),
      () => ok(transferFor('1500.00'), { status: 200, headers: { 'Idempotent-Replayed': 'true' } }),
    );
    const { user } = renderApp('/transfers/new');

    await fillForm(user);
    await submit(user);
    expect(await screen.findByText(/The server could not be reached/)).toBeInTheDocument();
    await submit(user);

    expect(await screen.findByText(/already processed/)).toBeInTheDocument();
    expect(calls).toHaveLength(2);
    expect(calls[1]?.key).toBe(calls[0]?.key);
  });

  it('REQUEST_IN_PROGRESS shows "still processing" and retries with the same key', async () => {
    const calls = capturePosts(() => apiError(409, 'REQUEST_IN_PROGRESS'));
    const { user } = renderApp('/transfers/new');

    await fillForm(user);
    await submit(user);
    expect(await screen.findByText('Still processing')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Retry' }));

    await waitFor(() => expect(calls).toHaveLength(2));
    expect(calls[1]?.key).toBe(calls[0]?.key);
  });

  it('uses a NEW key after a definitive business error even with an unchanged payload', async () => {
    const calls = capturePosts(() => apiError(422, 'INSUFFICIENT_BALANCE'));
    const { user } = renderApp('/transfers/new');

    await fillForm(user);
    await submit(user);
    await screen.findByText(/does not have enough balance/);
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    await submit(user);

    await waitFor(() => expect(calls).toHaveLength(2));
    expect(calls[1]?.key).not.toBe(calls[0]?.key);
  });

  it('a double click sends exactly one request', async () => {
    const calls = capturePosts(async () => {
      await delay(150);
      return ok(transferFor('1500.00'), { status: 201 });
    });
    const { user } = renderApp('/transfers/new');

    await fillForm(user);
    const button = screen.getByRole('button', { name: 'Send now' });
    await user.dblClick(button);
    fireEvent.submit(button.closest('form') as HTMLFormElement); // a third, programmatic submit

    expect(await screen.findByRole('heading', { name: 'Transfer sent' })).toBeInTheDocument();
    expect(calls).toHaveLength(1);
  });

  it('disables the button while the request is in flight', async () => {
    capturePosts(async () => {
      await delay(100);
      return ok(transferFor('1500.00'), { status: 201 });
    });
    const { user } = renderApp('/transfers/new');

    await fillForm(user);
    await submit(user);

    expect(screen.getByRole('button', { name: 'Sending…' })).toBeDisabled();
    await screen.findByRole('heading', { name: 'Transfer sent' });
  });

  it.each([
    [422, 'INSUFFICIENT_BALANCE', /does not have enough balance/],
    [422, 'PER_TRANSFER_LIMIT_EXCEEDED', /above the per-transfer limit/],
    [422, 'DAILY_LIMIT_EXCEEDED', /exceed your daily transfer limit/],
    [422, 'MONTHLY_LIMIT_EXCEEDED', /exceed your monthly transfer limit/],
    [422, 'SAME_ACCOUNT_TRANSFER', /belongs to the source account/],
    [404, 'RECEIVER_NOT_FOUND', /No account with this IBAN exists/],
    [403, 'ACCOUNT_NOT_OWNED', /does not belong to you/],
    [400, 'VALIDATION_ERROR', /Some fields are not valid/],
    [409, 'IDEMPOTENCY_KEY_CONFLICT', /conflicts with an earlier one/],
    [429, 'TOO_MANY_REQUESTS', /Too many transfers in a short time/],
    [418, 'SOMETHING_NEW', /Something went wrong/],
  ])('%i %s shows its message and the support code', async (status, code, message) => {
    capturePosts(() => apiError(status, code));
    const { user } = renderApp('/transfers/new');

    await fillForm(user);
    await submit(user);

    const box = (await screen.findByText(message)).closest<HTMLElement>('[role="alert"]');
    expect(box).not.toBeNull();
    expect(
      within(box as HTMLElement).getByText(`Support code: trace-${code.toLowerCase()}`),
    ).toBeInTheDocument();
    expect(screen.queryByText(`server message for ${code}`)).not.toBeInTheDocument();
  });

  it('NETWORK_ERROR shows its message (no support code: there was no response)', async () => {
    capturePosts(() => HttpResponse.error());
    const { user } = renderApp('/transfers/new');

    await fillForm(user);
    await submit(user);

    expect(await screen.findByText(/The server could not be reached/)).toBeInTheDocument();
    expect(screen.queryByText(/Support code/)).not.toBeInTheDocument();
  });

  it('TOO_MANY_REQUESTS shows Retry-After', async () => {
    capturePosts(() => apiError(429, 'TOO_MANY_REQUESTS', {}, { 'Retry-After': '30' }));
    const { user } = renderApp('/transfers/new');

    await fillForm(user);
    await submit(user);

    expect(await screen.findByText(/Please try again in 30 seconds\./)).toBeInTheDocument();
  });

  it('maps VALIDATION_ERROR fieldErrors onto the form fields', async () => {
    capturePosts(() =>
      apiError(400, 'VALIDATION_ERROR', {
        fieldErrors: [
          { field: 'amount', message: 'must be greater than 0' },
          { field: 'Idempotency-Key', message: 'bad key' },
        ],
      }),
    );
    const { user } = renderApp('/transfers/new');

    await fillForm(user);
    await submit(user);
    await screen.findByRole('heading', { name: 'This transfer cannot be sent' });
    await user.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByText('must be greater than 0')).toBeInTheDocument();
    expect(screen.getByLabelText('Amount (TRY)')).toHaveAttribute('aria-invalid', 'true');
  });

  it('renders user text as text, never as HTML', async () => {
    capturePosts(() =>
      ok({ ...transferFor('1.00'), maskedReceiverIban: '<img src=x onerror=alert(1)>' }, { status: 201 }),
    );
    const { user, container } = renderApp('/transfers/new');

    await fillForm(user, { amount: '1' });
    await submit(user);

    expect(await screen.findByText('<img src=x onerror=alert(1)>')).toBeInTheDocument();
    expect(container.querySelector('img')).toBeNull();
  });

  it.each([
    [422, 'DAILY_LIMIT_EXCEEDED', 'Transfer limit exceeded', 'tone-limit'],
    [422, 'INSUFFICIENT_BALANCE', 'Not enough balance', 'tone-balance'],
    [404, 'RECEIVER_NOT_FOUND', 'Receiver not found', 'tone-validation'],
    [409, 'REQUEST_IN_PROGRESS', 'Still processing', 'tone-duplicate'],
    [503, 'INTERNAL_ERROR', 'Transfer failed', 'tone-network'],
  ])('%i %s replaces the form with a titled error card', async (status, code, title, tone) => {
    capturePosts(() => apiError(status, code));
    const { user } = renderApp('/transfers/new');

    await fillForm(user);
    await submit(user);

    const heading = await screen.findByRole('heading', { name: title });
    expect(heading.closest('[role="alert"]')).toHaveClass('result-card', tone);
    expect(screen.getByText(code.replaceAll('_', ' '))).toBeInTheDocument();
    expect(screen.queryByLabelText('Receiver IBAN')).not.toBeInTheDocument();
  });

  it('shows the selected account and the remaining limits next to the form', async () => {
    const { user } = renderApp('/transfers/new');

    expect(await screen.findByText('Choose an account to see its balance.')).toBeInTheDocument();
    expect(await screen.findByText('65.000,00 TRY')).toBeInTheDocument(); // daily remaining
    await screen.findByRole('option', { name: /TR62 \*{4}/ });
    await user.selectOptions(screen.getByLabelText('Source account'), '1');

    expect(screen.getByRole('heading', { name: '250.000,00 TRY available' })).toBeInTheDocument();
  });
});
