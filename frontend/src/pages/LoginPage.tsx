import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { z } from 'zod';
import { useAuth, type LoginLocationState } from '../auth/AuthContext';
import { Brand } from '../components/Brand';
import { ErrorAlert } from '../components/ErrorAlert';

const schema = z.object({
  username: z.string().trim().min(1, 'Enter your username.'),
  password: z.string().min(1, 'Enter your password.'),
});
type FormValues = z.infer<typeof schema>;

export function LoginPage() {
  const { isAuthenticated, sessionExpired, login } = useAuth();
  const navigate = useNavigate();
  const state = (useLocation().state ?? {}) as LoginLocationState;
  const [error, setError] = useState<unknown>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { username: '', password: '' } });

  if (isAuthenticated) {
    return <Navigate to={state.from ?? '/'} replace />;
  }

  const onSubmit = async (values: FormValues) => {
    setError(null);
    try {
      await login(values.username, values.password);
      navigate(state.from ?? '/', { replace: true });
    } catch (e) {
      setError(e);
    }
  };

  return (
    <main className="login">
      <div className="card login-card">
        <Brand />
        <h1>Sign in to SwiftPay</h1>
        <div aria-live="assertive">
          {sessionExpired && !error && (
            <p className="alert alert-info" role="status">
              Session expired. Please sign in again.
            </p>
          )}
          {error !== null && <ErrorAlert error={error} />}
        </div>
        <form className="form" noValidate onSubmit={handleSubmit(onSubmit)}>
          <div className="field">
            <label htmlFor="username">Username</label>
            <input
              id="username"
              autoComplete="username"
              aria-invalid={errors.username ? true : undefined}
              {...register('username')}
            />
            {errors.username && (
              <span className="field-error" role="alert">
                {errors.username.message}
              </span>
            )}
          </div>
          <div className="field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              aria-invalid={errors.password ? true : undefined}
              {...register('password')}
            />
            {errors.password && (
              <span className="field-error" role="alert">
                {errors.password.message}
              </span>
            )}
          </div>
          <button type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
      </div>
    </main>
  );
}
