import { screen, within } from '@testing-library/react';
import { http } from 'msw';
import { describe, expect, it } from 'vitest';
import { StatisticsTable } from '../components/StatisticsTable';
import { apiError, listPage, ok, statistics, transferDetail } from '../test/handlers';
import { renderApp } from '../test/renderApp';
import { server } from '../test/server';
import { render } from '@testing-library/react';

describe('Overview', () => {
  it('shows accounts with masked IBANs, limits and the latest 4 transfers', async () => {
    let url: URL | undefined;
    server.use(
      http.get('*/api/transfers', ({ request }) => {
        url = new URL(request.url);
        return ok(listPage([]));
      }),
    );
    renderApp('/');

    expect(await screen.findByText('250.000,00 TRY')).toBeInTheDocument();
    expect(screen.getByText('TR62 **** **** **** **** 1001')).toBeInTheDocument();
    expect(screen.queryByText('TR620006100000000000001001')).not.toBeInTheDocument(); // full IBAN not shown
    expect(await screen.findByText('65.000,00 TRY left')).toBeInTheDocument();
    expect(screen.getByText('35.000,00 TRY of 100.000,00 TRY used')).toBeInTheDocument();
    expect(screen.getByText('Per transfer cap 50.000,00 TRY')).toBeInTheDocument();
    expect(await screen.findByText('No transfers yet.')).toBeInTheDocument();
    expect(url?.searchParams.get('size')).toBe('4');
  });
});

describe('Layout', () => {
  it('marks the current section and shows the daily remaining limit', async () => {
    renderApp('/transfers/101');

    const nav = await screen.findByRole('navigation', { name: 'Main' });
    expect(within(nav).getByRole('link', { name: 'Past transfers' })).toHaveAttribute('aria-current', 'page');
    expect(within(nav).getByRole('link', { name: 'New transfer' })).not.toHaveAttribute('aria-current');
    expect(await screen.findByText(/Daily remaining 65\.000,00 TRY/)).toBeInTheDocument();
  });

  it('does not mark "Past transfers" on the new transfer page', async () => {
    renderApp('/transfers/new');

    const nav = await screen.findByRole('navigation', { name: 'Main' });
    expect(within(nav).getByRole('link', { name: 'New transfer' })).toHaveAttribute('aria-current', 'page');
    expect(within(nav).getByRole('link', { name: 'Past transfers' })).not.toHaveAttribute('aria-current');
  });
});

describe('Monthly statistics page', () => {
  it('shows the 6-row statistics table in API order', async () => {
    renderApp('/statistics');

    expect(await screen.findByRole('heading', { name: 'Monthly transfer statistics' })).toBeInTheDocument();
    const rows = within(await screen.findByRole('table'))
      .getAllByRole('row')
      .slice(1);
    expect(rows).toHaveLength(6);
    expect(rows.map((r) => within(r).getAllByRole('cell')[0]?.textContent)).toEqual([
      'May 2026',
      'April 2026',
      'March 2026',
      'February 2026',
      'January 2026',
      'December 2025',
    ]);
    expect(within(rows[0] as HTMLElement).getByText('12.450,00 TRY')).toBeInTheDocument();
  });
});

describe('StatisticsTable', () => {
  it('renders exactly the given 6 rows in order', () => {
    render(<StatisticsTable stats={statistics} />);
    const rows = screen.getAllByRole('row').slice(1);
    expect(rows).toHaveLength(6);
    expect(within(rows[5] as HTMLElement).getByText('0,00 TRY')).toBeInTheDocument();
  });
});

describe('Transfers list', () => {
  it('requests page size 10 newest first and shows masked rows with status', async () => {
    let url: URL | undefined;
    server.use(
      http.get('*/api/transfers', ({ request }) => {
        url = new URL(request.url);
        return ok(
          listPage(
            [
              {
                id: 7,
                maskedReceiverIban: 'TR69 **** **** **** **** 2001',
                amount: '1500.00',
                status: 'COMPLETED',
                createdAt: '2026-05-14T09:30:00Z',
              },
              {
                id: 6,
                maskedReceiverIban: 'TR35 **** **** **** **** 3001',
                amount: '20000.00',
                status: 'FAILED',
                createdAt: '2026-05-13T21:00:00Z',
              },
            ],
            0,
            12,
          ),
        );
      }),
    );
    renderApp('/transfers');

    expect(await screen.findByText('14.05.2026 12:30')).toBeInTheDocument(); // Istanbul time
    expect(screen.getByText('1.500,00 TRY')).toBeInTheDocument();
    expect(screen.getByText('Failed')).toBeInTheDocument();
    expect(screen.getByText('12 transfers · page 1 of 2')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Details of transfer 7' })).toHaveAttribute(
      'href',
      '/transfers/7',
    );
    expect(url?.searchParams.get('size')).toBe('10');
    expect(url?.searchParams.get('page')).toBe('0');
    expect(url?.searchParams.get('sort')).toBe('createdAt,desc');
  });

  it('shows the empty state', async () => {
    renderApp('/transfers');
    expect(await screen.findByText('No transfers found.')).toBeInTheDocument();
  });

  it('sends the date filter and shows INVALID_DATE_RANGE from the server', async () => {
    let params: URLSearchParams | undefined;
    server.use(
      http.get('*/api/transfers', ({ request }) => {
        params = new URL(request.url).searchParams;
        return params.get('startDate') ? apiError(400, 'INVALID_DATE_RANGE') : ok(listPage([]));
      }),
    );
    const { user } = renderApp('/transfers');
    await screen.findByText('No transfers found.');

    await user.type(screen.getByLabelText('Start date'), '2026-05-31');
    await user.type(screen.getByLabelText('End date'), '2026-05-01');
    await user.click(screen.getByRole('button', { name: 'Apply' }));

    expect(await screen.findByText('The start date must not be after the end date.')).toBeInTheDocument();
    expect(params?.get('startDate')).toBe('2026-05-31');
    expect(params?.get('endDate')).toBe('2026-05-01');
  });

  it('shows an error state with the support code', async () => {
    server.use(http.get('*/api/transfers', () => apiError(500, 'INTERNAL_ERROR')));
    renderApp('/transfers');
    expect(await screen.findByText('Support code: trace-internal_error')).toBeInTheDocument();
  });
});

describe('Transfer detail', () => {
  it('shows the full receiver IBAN as returned and the masked source', async () => {
    renderApp('/transfers/101');

    expect(await screen.findByRole('heading', { name: 'Transfer #101' })).toBeInTheDocument();
    expect(screen.getByText('TR69 0006 2000 0000 0000 0020 01')).toBeInTheDocument();
    expect(screen.getByText(transferDetail.sourceAccountMaskedIban)).toBeInTheDocument();
  });

  it('shows the failure reason of a FAILED transfer', async () => {
    server.use(
      http.get('*/api/transfers/:id', () =>
        ok({
          ...transferDetail,
          status: 'FAILED',
          completedAt: null,
          failureReason: 'Insufficient balance.',
        }),
      ),
    );
    renderApp('/transfers/101');
    expect(await screen.findByText('Insufficient balance.')).toBeInTheDocument();
  });

  it('404 shows "Transfer not found"', async () => {
    server.use(http.get('*/api/transfers/:id', () => apiError(404, 'TRANSFER_NOT_FOUND')));
    renderApp('/transfers/999');
    expect(await screen.findByRole('heading', { name: 'Transfer not found' })).toBeInTheDocument();
  });

  it('a non-numeric id shows "Transfer not found" without calling the API', async () => {
    renderApp('/transfers/abc');
    expect(await screen.findByRole('heading', { name: 'Transfer not found' })).toBeInTheDocument();
  });
});
