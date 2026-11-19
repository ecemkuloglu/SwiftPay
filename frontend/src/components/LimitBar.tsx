import type { LimitUsage } from '../types/api';
import { formatMoney, toBarValue } from '../utils/money';

/** Remaining on top, used / limit below. Numbers are used only for the bar's visual fill (native <progress>). */
export function LimitBar({ label, usage }: { label: string; usage: LimitUsage }) {
  const id = `limit-${label.toLowerCase().replace(/\s+/g, '-')}`;
  return (
    <div className="limit">
      <div className="limit-row">
        <span id={id}>{label}</span>
        <b>{formatMoney(usage.remaining)} left</b>
      </div>
      <progress aria-labelledby={id} value={toBarValue(usage.used)} max={toBarValue(usage.limit) || 1} />
      <p className="muted small">
        {formatMoney(usage.used)} of {formatMoney(usage.limit)} used
      </p>
    </div>
  );
}
