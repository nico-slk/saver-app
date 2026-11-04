import clsx from 'clsx';
import { forwardRef, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from 'react';

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  loading?: boolean;
};

export function Button({ variant = 'primary', loading, className, children, disabled, ...rest }: ButtonProps) {
  return (
    <button
      {...rest}
      disabled={disabled || loading}
      className={clsx(
        'inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition',
        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600',
        'disabled:cursor-not-allowed disabled:opacity-60',
        {
          primary: 'bg-brand-700 text-white hover:bg-brand-800',
          secondary:
            'border border-slate-300 bg-white text-slate-800 hover:bg-slate-100 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:hover:bg-slate-800',
          ghost: 'text-slate-700 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800',
          danger: 'bg-red-600 text-white hover:bg-red-700',
        }[variant],
        className,
      )}
    >
      {loading && <Spinner className="size-4" />}
      {children}
    </button>
  );
}

type FieldProps = { label: string; error?: string; hint?: string };

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & FieldProps>(
  function Input({ label, error, hint, id, className, ...rest }, ref) {
    const inputId = id ?? rest.name;
    return (
      <label htmlFor={inputId} className="block space-y-1">
        <span className="text-sm font-medium text-slate-700 dark:text-slate-300">{label}</span>
        <input
          ref={ref}
          id={inputId}
          aria-invalid={!!error}
          {...rest}
          className={clsx(
            'block w-full rounded-lg border bg-white px-3 py-2 text-sm shadow-sm outline-none transition',
            'focus:border-brand-600 focus:ring-2 focus:ring-brand-600/20 dark:bg-slate-900',
            'disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-500 dark:disabled:bg-slate-800',
            error ? 'border-red-500' : 'border-slate-300 dark:border-slate-700',
            className,
          )}
        />
        {error ? (
          <span className="text-xs text-red-600">{error}</span>
        ) : (
          hint && <span className="text-xs text-slate-500">{hint}</span>
        )}
      </label>
    );
  },
);

export function Select({
  label,
  children,
  id,
  className,
  ...rest
}: SelectHTMLAttributes<HTMLSelectElement> & { label: string; children: ReactNode }) {
  const selectId = id ?? rest.name;
  return (
    <label htmlFor={selectId} className="block space-y-1">
      <span className="text-sm font-medium text-slate-700 dark:text-slate-300">{label}</span>
      <select
        id={selectId}
        {...rest}
        className={clsx(
          'block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm',
          'focus:border-brand-600 focus:ring-2 focus:ring-brand-600/20 dark:border-slate-700 dark:bg-slate-900',
          className,
        )}
      >
        {children}
      </select>
    </label>
  );
}

export function Card({ className, children }: { className?: string; children: ReactNode }) {
  return (
    <div
      className={clsx(
        'rounded-xl border border-slate-200 bg-white p-4 shadow-sm sm:p-6 dark:border-slate-800 dark:bg-slate-900',
        className,
      )}
    >
      {children}
    </div>
  );
}

export function Alert({ kind = 'error', children }: { kind?: 'error' | 'success'; children: ReactNode }) {
  return (
    <div
      role={kind === 'error' ? 'alert' : 'status'}
      className={clsx(
        'rounded-lg px-3 py-2 text-sm',
        kind === 'error'
          ? 'bg-red-50 text-red-700 dark:bg-red-950/50 dark:text-red-300'
          : 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300',
      )}
    >
      {children}
    </div>
  );
}

export function Spinner({ className }: { className?: string }) {
  return (
    <svg className={clsx('animate-spin', className ?? 'size-6')} viewBox="0 0 24 24" fill="none" aria-hidden>
      <circle cx="12" cy="12" r="10" stroke="currentColor" strokeOpacity="0.25" strokeWidth="4" />
      <path d="M22 12a10 10 0 0 0-10-10" stroke="currentColor" strokeWidth="4" strokeLinecap="round" />
    </svg>
  );
}

export function FullScreenLoader({ label }: { label: string }) {
  return (
    <div className="grid min-h-dvh place-items-center text-brand-700" role="status">
      <div className="flex flex-col items-center gap-3">
        <Spinner className="size-8" />
        <span className="text-sm text-slate-500">{label}</span>
      </div>
    </div>
  );
}
