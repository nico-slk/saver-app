import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Input, Select } from '@/components/ui';
import { formatMoney, type Currency } from '@/lib/format';
import { accountsApi, type Account, type Purpose } from '@/features/accounts/accountsApi';

/** Debounce simple para no consultar la conversión en cada tecla. */
function useDebounced<T>(value: T, ms = 350): T {
  const [v, setV] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setV(value), ms);
    return () => clearTimeout(id);
  }, [value, ms]);
  return v;
}

export function AccountSelect({ accounts, value, onChange, label, error }: {
  accounts: Account[]; value: string; onChange: (id: string) => void; label: string; error?: string;
}) {
  const { t } = useTranslation();
  if (accounts.length === 0) {
    return (
      <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900 dark:bg-amber-900/30 dark:text-amber-100">
        {t('accounts.noneYet')}{' '}
        <Link to="/accounts" className="font-medium underline">{t('accounts.create')}</Link>
      </p>
    );
  }
  return (
    <div>
      <Select label={label} name="accountId" value={value} onChange={(e) => onChange(e.target.value)}>
        {accounts.map((a) => (
          <option key={a.id} value={a.id}>{a.name} · {a.currency} · {formatMoney(a.balance, a.currency)}</option>
        ))}
      </Select>
      {error && <span className="text-xs text-red-600">{error}</span>}
    </div>
  );
}

/**
 * Si la cuenta es de otra moneda muestra cuánto sale/entra de la cuenta y con qué dólar, y permite
 * cambiar la cotización. {@code rate} vacío = la que corresponde según el uso.
 */
export function ConversionPreview({ amount, from, to, purpose, rate, onRate }: {
  amount: string; from: Currency; to?: Currency; purpose: Purpose; rate: string; onRate: (v: string) => void;
}) {
  const { t } = useTranslation();
  const params = useDebounced({ amount, from, to, purpose, rate });
  const needed = !!to && to !== from && Number(amount) > 0;
  const q = useQuery({
    queryKey: ['exchange-rates', 'convert', params],
    queryFn: () => accountsApi.convert({ ...params, to: params.to! }),
    enabled: needed && !!params.to && params.to !== params.from && Number(params.amount) > 0,
    retry: false,
  });
  if (!needed) return null;
  const missing = q.isError;
  return (
    <div className="space-y-2 rounded-lg bg-slate-50 p-3 text-sm dark:bg-slate-800/50">
      {q.data && (
        <p>
          {t(purpose === 'RECEIPT' || purpose === 'TRANSFER' ? 'payments.conversionIn' : 'payments.conversionOut', {
            amount: formatMoney(q.data.amount, to!),
          })}{' '}
          <span className="text-slate-500">
            {t('payments.conversionRate', { type: t(`rates.type.${q.data.type ?? 'MANUAL'}`), rate: formatMoney(q.data.rate ?? 0, 'ARS') })}
          </span>
        </p>
      )}
      {missing && <p className="text-amber-700 dark:text-amber-300">{t('payments.rateMissing')}</p>}
      <div className="w-48">
        <Input label={t('payments.manualRate')} name="rate" type="number" inputMode="decimal" step="0.01" min="0"
          placeholder={q.data?.rate ? String(q.data.rate) : ''} value={rate} onChange={(e) => onRate(e.target.value)} />
      </div>
    </div>
  );
}
