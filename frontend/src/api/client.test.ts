import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiError, ok } from '../test/handlers';
import { server } from '../test/server';
import { login } from './auth';
import { ApiError, configureApiClient, request } from './client';

describe('API client', () => {
  const onUnauthorized = vi.fn();

  afterEach(() => {
    onUnauthorized.mockReset();
    configureApiClient({ getToken: () => null, onUnauthorized: () => undefined });
  });

  it('adds the bearer token and unwraps data', async () => {
    let authorization: string | null = null;
    server.use(
      http.get('*/api/ping', ({ request: req }) => {
        authorization = req.headers.get('Authorization');
        return ok({ pong: true }, { headers: { 'X-Trace-Id': 'abc' } });
      }),
    );
    configureApiClient({ getToken: () => 'secret-token' });

    const result = await request<{ pong: boolean }>('/ping');

    expect(result.data).toEqual({ pong: true });
    expect(authorization).toBe('Bearer secret-token');
    expect(result.headers.get('X-Trace-Id')).toBe('abc');
  });

  it('maps an error body to ApiError with code, message, traceId and fieldErrors', async () => {
    server.use(
      http.post('*/api/transfers', () =>
        apiError(400, 'VALIDATION_ERROR', { fieldErrors: [{ field: 'amount', message: 'must be > 0' }] }),
      ),
    );

    const error = await request('/transfers', { method: 'POST', body: {} }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 400,
      code: 'VALIDATION_ERROR',
      message: 'server message for VALIDATION_ERROR',
      traceId: 'trace-validation_error',
      fieldErrors: [{ field: 'amount', message: 'must be > 0' }],
    });
  });

  it('reads Retry-After on 429', async () => {
    server.use(
      http.post('*/api/transfers', () => apiError(429, 'TOO_MANY_REQUESTS', {}, { 'Retry-After': '42' })),
    );

    await expect(request('/transfers', { method: 'POST', body: {} })).rejects.toMatchObject({
      retryAfter: 42,
    });
  });

  it('turns a failed fetch into NETWORK_ERROR', async () => {
    server.use(http.get('*/api/accounts', () => HttpResponse.error()));

    await expect(request('/accounts')).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' });
  });

  it('a non-JSON 5xx still becomes an ApiError (INTERNAL_ERROR) with the trace header', async () => {
    server.use(
      http.get(
        '*/api/accounts',
        () =>
          new HttpResponse('<html>bad gateway</html>', {
            status: 502,
            headers: { 'X-Trace-Id': 'gw-1' },
          }),
      ),
    );

    await expect(request('/accounts')).rejects.toMatchObject({
      status: 502,
      code: 'INTERNAL_ERROR',
      traceId: 'gw-1',
    });
  });

  it('calls onUnauthorized on a 401 from a protected call', async () => {
    configureApiClient({ getToken: () => 'expired', onUnauthorized });
    server.use(http.get('*/api/accounts', () => apiError(401, 'UNAUTHORIZED')));

    await expect(request('/accounts')).rejects.toMatchObject({ code: 'UNAUTHORIZED' });
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('does NOT treat a 401 from login as an expired session, and sends no token', async () => {
    let authorization: string | null = 'unset';
    configureApiClient({ getToken: () => 'old-token', onUnauthorized });
    server.use(
      http.post('*/api/auth/login', ({ request: req }) => {
        authorization = req.headers.get('Authorization');
        return apiError(401, 'INVALID_CREDENTIALS');
      }),
    );

    await expect(login({ username: 'ecem', password: 'x' })).rejects.toMatchObject({
      code: 'INVALID_CREDENTIALS',
    });
    expect(onUnauthorized).not.toHaveBeenCalled();
    expect(authorization).toBeNull();
  });

  it('never writes to the console', async () => {
    const spies = (['log', 'info', 'warn', 'error', 'debug'] as const).map((m) => vi.spyOn(console, m));
    server.use(http.get('*/api/accounts', () => apiError(500, 'INTERNAL_ERROR')));

    await request('/accounts').catch(() => undefined);

    spies.forEach((spy) => expect(spy).not.toHaveBeenCalled());
  });
});
