import { screen } from '@testing-library/react';
import { delay, http } from 'msw';
import { describe, expect, it } from 'vitest';
import { apiError, listPage, ok } from '../test/handlers';
import { renderApp } from '../test/renderApp';
import { server } from '../test/server';
import { tokenStorage } from './tokenStorage';

describe('authentication', () => {
  it('ProtectedRoute sends signed-out users to /login', async () => {
    renderApp('/transfers', { signedIn: false });

    expect(await screen.findByRole('heading', { name: 'Sign in to SwiftPay' })).toBeInTheDocument();
  });

  it('logs in, stores the token in sessionStorage (never localStorage) and returns to the requested page', async () => {
    let body: unknown;
    server.use(
      http.post('*/api/auth/login', async ({ request }) => {
        body = await request.json();
        await delay(50);
        return ok({ accessToken: 'jwt-from-login', tokenType: 'Bearer', expiresIn: 900 });
      }),
    );
    const { user } = renderApp('/transfers', { signedIn: false });

    await user.type(await screen.findByLabelText('Username'), 'ecem');
    await user.type(screen.getByLabelText('Password'), 'Test1234!');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(screen.getByRole('button', { name: 'Signing in…' })).toBeDisabled();

    expect(await screen.findByRole('heading', { name: 'Transfers' })).toBeInTheDocument();
    expect(body).toEqual({ username: 'ecem', password: 'Test1234!' });
    expect(tokenStorage.get()).toBe('jwt-from-login');
    expect(sessionStorage.getItem('swiftpay.session')).toContain('jwt-from-login');
    // eslint-disable-next-line no-restricted-properties -- asserting that nothing was written there
    expect(window.localStorage.length).toBe(0);
  });

  it('shows one generic message for bad credentials', async () => {
    server.use(http.post('*/api/auth/login', () => apiError(401, 'INVALID_CREDENTIALS')));
    const { user } = renderApp('/login', { signedIn: false });

    await user.type(screen.getByLabelText('Username'), 'ecem');
    await user.type(screen.getByLabelText('Password'), 'wrong');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Invalid username or password.')).toBeInTheDocument();
    expect(screen.queryByText(/Session expired/)).not.toBeInTheDocument();
    expect(screen.getByText('Support code: trace-invalid_credentials')).toBeInTheDocument();
  });

  it('requires both fields', async () => {
    const { user } = renderApp('/login', { signedIn: false });

    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Enter your username.')).toBeInTheDocument();
    expect(screen.getByText('Enter your password.')).toBeInTheDocument();
  });

  it('a signed-in user visiting /login is redirected to the dashboard', async () => {
    renderApp('/login');

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
  });

  it('a 401 on a protected call clears the session and shows "Session expired"', async () => {
    server.use(http.get('*/api/accounts', () => apiError(401, 'UNAUTHORIZED')));
    renderApp('/');

    expect(await screen.findByText('Session expired. Please sign in again.')).toBeInTheDocument();
    expect(tokenStorage.get()).toBeNull();
    expect(sessionStorage.getItem('swiftpay.session')).toBeNull();
  });

  it('after a session expiry, signing in again returns to the page the user was on', async () => {
    let expired = true;
    server.use(
      http.get('*/api/transfers', () => (expired ? apiError(401, 'UNAUTHORIZED') : ok(listPage([])))),
    );
    const { user } = renderApp('/transfers');

    await screen.findByText('Session expired. Please sign in again.');
    expired = false;
    await user.type(screen.getByLabelText('Username'), 'ecem');
    await user.type(screen.getByLabelText('Password'), 'Test1234!');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { name: 'Transfers' })).toBeInTheDocument();
    expect(screen.queryByText(/Session expired/)).not.toBeInTheDocument();
  });

  it('sign out clears the session', async () => {
    const { user } = renderApp('/');

    await user.click(await screen.findByRole('button', { name: 'Sign out' }));

    expect(await screen.findByRole('heading', { name: 'Sign in to SwiftPay' })).toBeInTheDocument();
    expect(tokenStorage.get()).toBeNull();
  });
});
