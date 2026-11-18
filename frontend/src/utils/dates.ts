const TIME_ZONE = 'Europe/Istanbul';

const dateTime = new Intl.DateTimeFormat('tr-TR', {
  timeZone: TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

const monthLabel = new Intl.DateTimeFormat('en-GB', { timeZone: 'UTC', month: 'long', year: 'numeric' });

/** ISO instant -> "14.05.2026 12:30" in Europe/Istanbul. */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) {
    return '–';
  }
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : dateTime.format(date);
}

/** "2026-05" -> "May 2026". */
export function formatMonth(yearMonth: string): string {
  const date = new Date(`${yearMonth}-01T00:00:00Z`);
  return Number.isNaN(date.getTime()) ? yearMonth : monthLabel.format(date);
}
