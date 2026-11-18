import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { AppRoutes } from '../App';
import { AuthProvider } from '../auth/AuthContext';
import { tokenStorage } from '../auth/tokenStorage';

/** Renders the real app routes at `route`, optionally already signed in. */
export function renderApp(route = '/', { signedIn = true } = {}) {
  if (signedIn) {
    tokenStorage.set('jwt-test-token', 900);
  }
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const user = userEvent.setup();
  const view = render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        <AuthProvider>
          <AppRoutes />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { user, queryClient, ...view };
}
