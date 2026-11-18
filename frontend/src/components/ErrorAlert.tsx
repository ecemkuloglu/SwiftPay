import { describeError } from '../utils/errorMessages';

interface Props {
  error: unknown;
  title?: string;
}

/** Error box read out by screen readers; always shows the trace id as a small "Support code". */
export function ErrorAlert({ error, title }: Props) {
  const { message, traceId, code } = describeError(error);
  return (
    <div className="alert alert-error" role="alert" data-code={code}>
      {title && <strong className="alert-title">{title}</strong>}
      <p>{message}</p>
      {traceId && <p className="support-code">Support code: {traceId}</p>}
    </div>
  );
}
