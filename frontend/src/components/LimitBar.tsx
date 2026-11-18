import type { LimitUsage } from '../types/api';
import { formatMoney, toBarValue } from '../utils/money';

/** Used / limit / remaining. Numbers are used only for the bar's visual fill (native <progress>, no inline style). */
export function LimitBar({ label, usage }: { label: string; usage: LimitUsage }) {
  const id = `limit-${label.toLowerCase().replace(/\s+/g, '-')}`;
  return (
    <div className="limit">
      <div className="limit-header">
        <span id={id}>{label}</span>
        <span className="muted">
          {formatMoney(usage.used)} / {formatMoney(usage.limit)}
        </span>
      </div>
      <progress aria-labelledby={id} value={toBarValue(usage.used)} max={toBarValue(usage.limit) || 1} />
      <p className="muted small">Remaining: {formatMoney(usage.remaining)}</p>
    </div>
  );
}
