import type { Account } from '../types/api';
import { formatMoney } from '../utils/money';

export function AccountCard({ account }: { account: Account }) {
  return (
    <article className="card account-card" aria-label={`Account ${account.maskedIban}`}>
      <p className="muted mono">{account.maskedIban}</p>
      <p className="balance">{formatMoney(account.balance, account.currency)}</p>
    </article>
  );
}
