import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Input } from './index';
import type { InputHTMLAttributes } from 'react';

type PasswordInputProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  error?: string;
  hint?: string;
};

export function PasswordInput({ className, ...props }: PasswordInputProps) {
  const { t } = useTranslation();
  const [visible, setVisible] = useState(false);
  return (
    <div className="relative">
      <Input {...props} type={visible ? 'text' : 'password'} className={`pr-16 ${className ?? ''}`} />
      <button
        type="button"
        onClick={() => setVisible((current) => !current)}
        aria-label={visible ? t('auth.hidePassword') : t('auth.showPassword')}
        aria-pressed={visible}
        className="absolute right-2 top-[2rem] rounded-md px-2 py-1 text-xs font-medium text-brand-700 hover:bg-slate-100 focus-visible:outline-2 focus-visible:outline-brand-600 dark:text-brand-400 dark:hover:bg-slate-800"
      >
        {visible ? t('auth.hidePassword') : t('auth.showPassword')}
      </button>
    </div>
  );
}
