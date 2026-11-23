import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import clsx from 'clsx';
import { Alert, Button, Card, Input, Select } from '@/components/ui';
import { Badge, ConfirmDialog, EmptyState, InlineLoader, Modal } from '@/components/ui/overlay';
import { StatTile } from '@/components/finance/Stats';
import { errorCode, fieldErrors, translateError } from '@/lib/errors';
import { formatDate, formatMoney, type Currency } from '@/lib/format';
import { formatMonth } from '@/lib/month';
import { useAuthStore } from '@/stores/authStore';
import { ConversionPreview } from '@/features/payments/MovementFields';
import { ACCOUNT_KINDS, accountKeys, accountsApi, type Account, type AccountKind, type Movement } from './accountsApi';
import { useAccounts, useCards } from './useAccounts';

const COLORS = ['#2a78d6', '#0f766e', '#7c3aed', '#c2410c', '#be185d', '#475569'];

export function AccountsPage() {
  const { t } = useTranslation();
  const q = useAccounts();
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<Account | null>(null);
  const [transferring, setTransferring] = useState(false);
  const [adjusting, setAdjusting] = useState<Account | null>(null);
  const [showArchived, setShowArchived] = useState(false);
  const o = q.data;
  const archived = (o?.accounts ?? []).filter((a) => a.archived);

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold">{t('nav.accounts')}</h1>
          <p className="text-sm text-slate-500">{t('accounts.intro')}</p>
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => setTransferring(true)} disabled={q.active.length < 2}>{t('accounts.transfer')}</Button>
          <Button onClick={() => setCreating(true)}>+ {t('accounts.new')}</Button>
        </div>
      </div>
      {q.isError && <Alert>{translateError(t, q.error)}</Alert>}
      {q.isPending && <InlineLoader label={t('common.loading')} />}
      {o && o.accounts.length === 0 && (
        <EmptyState title={t('accounts.empty')} action={<Button onClick={() => setCreating(true)}>+ {t('accounts.new')}</Button>} />
      )}
      {o && q.active.length > 0 && (
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <StatTile label={t('accounts.netWorth')}
            value={o.netWorth != null ? formatMoney(o.netWorth, o.baseCurrency) : '—'}
            tone={(o.netWorth ?? 0) < 0 ? 'negative' : undefined}
            sub={o.netWorth == null ? t('accounts.noRate')
              : o.valuationRate ? t('accounts.valuedAt', { type: t(`rates.type.${o.valuationType}`), rate: formatMoney(o.valuationRate, 'ARS') })
              : t('accounts.allAccounts')} />
          <StatTile label={t('accounts.savingsTotal')}
            value={o.netWorthSavings != null ? formatMoney(o.netWorthSavings, o.baseCurrency) : '—'}
            sub={t('accounts.savingsHint')} />
          {o.totals.map((tot) => (
            <StatTile key={tot.currency} label={t('accounts.inCurrency', { currency: tot.currency })}
              value={formatMoney(tot.balance, tot.currency)} tone={tot.balance < 0 ? 'negative' : undefined}
              sub={t('accounts.ofWhichSavings', { amount: formatMoney(tot.savings, tot.currency) })} />
          ))}
        </div>
      )}
      {q.active.length > 0 && (
        <div className="grid gap-4 md:grid-cols-2">
          {q.active.map((a) => (
            <AccountCard key={a.id} a={a} onEdit={() => setEditing(a)} onAdjust={() => setAdjusting(a)} />
          ))}
        </div>
      )}
      {archived.length > 0 && (
        <div>
          <button type="button" className="text-sm text-slate-500 hover:underline" onClick={() => setShowArchived(!showArchived)} aria-expanded={showArchived}>
            {t('accounts.archivedCount', { count: archived.length })}
          </button>
          {showArchived && (
            <div className="mt-3 grid gap-4 opacity-80 md:grid-cols-2">
              {archived.map((a) => <AccountCard key={a.id} a={a} onEdit={() => setEditing(a)} onAdjust={() => setAdjusting(a)} />)}
            </div>
          )}
        </div>
      )}
      <div className="grid gap-5 lg:grid-cols-2">
        <CardsPanel />
        <RatesPanel />
      </div>
      {(creating || editing) && <AccountForm account={editing ?? undefined} onClose={() => { setCreating(false); setEditing(null); }} />}
      {transferring && <TransferDialog accounts={q.active} onClose={() => setTransferring(false)} />}
      {adjusting && <AdjustDialog account={adjusting} onClose={() => setAdjusting(null)} />}
    </div>
  );
}

function AccountCard({ a, onEdit, onAdjust }: { a: Account; onEdit: () => void; onAdjust: () => void }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [confirm, setConfirm] = useState(false);
  const refresh = () => qc.invalidateQueries({ queryKey: accountKeys.all });
  const archive = useMutation({ mutationFn: () => accountsApi.update(a.id, { archived: !a.archived }), onSuccess: refresh });
  const remove = useMutation({ mutationFn: () => accountsApi.remove(a.id), onSuccess: async () => { setConfirm(false); await refresh(); } });
  const hasMovements = remove.isError && errorCode(remove.error) === 'account.has_movements';

  return (
    <Card className="overflow-hidden">
      <div className="-mx-5 -mt-5 mb-4 h-1.5" style={{ backgroundColor: a.color }} aria-hidden />
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="truncate font-semibold">{a.name}</h2>
          <div className="mt-1 flex flex-wrap gap-1">
            <Badge tone={a.savings ? 'success' : 'neutral'}>{t(`accounts.kind.${a.kind}`)}</Badge>
            <Badge>{a.currency}</Badge>
            {a.archived && <Badge tone="warning">{t('accounts.archived')}</Badge>}
          </div>
        </div>
        <p className={clsx('text-right text-xl font-semibold tabular-nums', a.balance < 0 && 'text-red-700 dark:text-red-400')}>
          {formatMoney(a.balance, a.currency)}
        </p>
      </div>
      <p className="mt-2 text-xs text-slate-500">
        {t('accounts.openingInfo', { amount: formatMoney(a.openingBalance, a.currency), date: formatDate(a.openingDate) })}
      </p>
      <div className="mt-3 flex flex-wrap gap-1">
        <Button variant="ghost" className="px-2 py-1 text-xs" onClick={() => setOpen(!open)} aria-expanded={open}>
          {open ? t('accounts.hideMovements') : t('accounts.movements')}
        </Button>
        {!a.archived && <Button variant="ghost" className="px-2 py-1 text-xs" onClick={onAdjust}>{t('accounts.adjust')}</Button>}
        <Button variant="ghost" className="px-2 py-1 text-xs" onClick={onEdit}>{t('common.edit')}</Button>
        <Button variant="ghost" className="px-2 py-1 text-xs" loading={archive.isPending} onClick={() => archive.mutate()}>
          {a.archived ? t('accounts.unarchive') : t('accounts.archive')}
        </Button>
        <Button variant="ghost" className="px-2 py-1 text-xs text-red-600" onClick={() => setConfirm(true)}>{t('common.delete')}</Button>
      </div>
      {hasMovements && <Alert>{t('accounts.cannotDelete')}</Alert>}
      {open && <Movements account={a} />}
      <ConfirmDialog open={confirm} title={t('accounts.deleteTitle')} message={t('accounts.deleteMessage', { name: a.name })}
        confirmLabel={t('common.delete')} loading={remove.isPending}
        onConfirm={() => remove.mutate()} onClose={() => setConfirm(false)} />
    </Card>
  );
}

function Movements({ account }: { account: Account }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: [...accountKeys.all, 'movements', account.id], queryFn: () => accountsApi.movements(account.id) });
  const undo = useMutation({
    mutationFn: (m: Movement) => m.kind === 'ADJUSTMENT' ? accountsApi.deleteAdjustment(m.refId) : accountsApi.deleteTransfer(m.refId),
    onSuccess: () => qc.invalidateQueries({ queryKey: accountKeys.all }),
  });
  if (q.isPending) return <InlineLoader label={t('common.loading')} />;
  if (!q.data?.length) return <p className="mt-3 text-sm text-slate-500">{t('accounts.noMovements')}</p>;
  return (
    <ul className="mt-3 divide-y divide-slate-100 border-t border-slate-100 text-sm dark:divide-slate-800 dark:border-slate-800">
      {q.data.map((m) => (
        <li key={m.kind + m.refId} className={clsx('flex items-center justify-between gap-3 py-2', !m.counted && 'opacity-60')}>
          <div className="min-w-0">
            <p className="truncate">
              {t(`accounts.movement.${m.kind}`, { name: m.description ?? '' })}
              {m.month && <span className="text-slate-500"> · {formatMonth(m.month, true)}</span>}
            </p>
            <p className="text-xs text-slate-500">
              {formatDate(m.date)}{!m.counted && ` · ${t('accounts.beforeOpening')}`}
            </p>
          </div>
          <div className="flex items-center gap-1">
            <span className={clsx('tabular-nums', m.amount < 0 ? 'text-red-700 dark:text-red-400' : 'text-emerald-700 dark:text-emerald-400')}>
              {m.amount > 0 ? '+' : ''}{formatMoney(m.amount, account.currency)}
            </span>
            {(m.kind === 'ADJUSTMENT' || m.kind.startsWith('TRANSFER')) && (
              <button type="button" aria-label={t('accounts.undo')} title={t('accounts.undo')}
                className="rounded p-1 text-slate-400 hover:text-red-600" onClick={() => undo.mutate(m)}>×</button>
            )}
          </div>
        </li>
      ))}
    </ul>
  );
}

function AccountForm({ account, onClose }: { account?: Account; onClose: () => void }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const [f, setF] = useState({
    name: account?.name ?? '',
    kind: (account?.kind ?? 'CHECKING') as AccountKind,
    currency: (account?.currency ?? user?.baseCurrency ?? 'ARS') as Currency,
    openingBalance: account ? String(account.openingBalance) : '',
    openingDate: account?.openingDate ?? new Date().toISOString().slice(0, 10),
    color: account?.color ?? COLORS[0]!,
  });
  const set = <K extends keyof typeof f>(k: K, v: (typeof f)[K]) => setF((s) => ({ ...s, [k]: v }));
  const save = useMutation({
    mutationFn: () => {
      const body = { ...f, openingBalance: f.openingBalance || '0' };
      if (account) {
        const { currency: _c, ...rest } = body;
        return accountsApi.update(account.id, rest);
      }
      return accountsApi.create(body);
    },
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: accountKeys.all });
      onClose();
    },
  });
  const errors = save.isError ? fieldErrors(save.error) : {};
  const err = (k: string) => (errors[k] ? translateError(t, errors[k]) : undefined);
  const submit = (e: FormEvent) => { e.preventDefault(); save.mutate(); };
  return (
    <Modal open onClose={onClose} title={account ? t('accounts.editTitle') : t('accounts.newTitle')}>
      <form onSubmit={submit} className="space-y-4" noValidate>
        {save.isError && <Alert>{translateError(t, save.error)}</Alert>}
        <Input label={t('accounts.fields.name')} name="name" required maxLength={60} placeholder={t('accounts.namePlaceholder')}
          value={f.name} error={err('name')} onChange={(e) => set('name', e.target.value)} />
        <div className="grid grid-cols-2 gap-3">
          <Select label={t('accounts.fields.kind')} name="kind" value={f.kind} onChange={(e) => set('kind', e.target.value as AccountKind)}>
            {ACCOUNT_KINDS.map((k) => <option key={k} value={k}>{t(`accounts.kind.${k}`)}</option>)}
          </Select>
          <Select label={t('finance.currency')} name="currency" value={f.currency} disabled={!!account}
            onChange={(e) => set('currency', e.target.value as Currency)}>
            <option value="ARS">ARS</option>
            <option value="USD">USD</option>
          </Select>
        </div>
        <p className="text-xs text-slate-500">{t(`accounts.kindHint.${f.kind}`)}</p>
        <div className="grid grid-cols-2 gap-3">
          <Input label={t('accounts.fields.openingBalance')} name="openingBalance" type="number" inputMode="decimal" step="0.01"
            placeholder="0" value={f.openingBalance} error={err('openingBalance')} onChange={(e) => set('openingBalance', e.target.value)} />
          <Input label={t('accounts.fields.openingDate')} name="openingDate" type="date" value={f.openingDate}
            error={err('openingDate')} onChange={(e) => set('openingDate', e.target.value)} />
        </div>
        <p className="text-xs text-slate-500">{t('accounts.openingHint')}</p>
        <fieldset>
          <legend className="mb-1 text-sm font-medium text-slate-700 dark:text-slate-300">{t('accounts.fields.color')}</legend>
          <div className="flex gap-2">
            {COLORS.map((c) => (
              <button key={c} type="button" aria-label={c} aria-pressed={f.color === c} onClick={() => set('color', c)}
                className={clsx('size-7 rounded-full ring-offset-2 dark:ring-offset-slate-900', f.color === c && 'ring-2 ring-slate-900 dark:ring-white')}
                style={{ backgroundColor: c }} />
            ))}
          </div>
        </fieldset>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="submit" loading={save.isPending}>{t('common.save')}</Button>
        </div>
      </form>
    </Modal>
  );
}

function TransferDialog({ accounts, onClose }: { accounts: Account[]; onClose: () => void }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [from, setFrom] = useState(accounts[0]?.id ?? '');
  const [to, setTo] = useState(accounts[1]?.id ?? '');
  const [amount, setAmount] = useState('');
  const [rate, setRate] = useState('');
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [notes, setNotes] = useState('');
  const src = accounts.find((a) => a.id === from);
  const dst = accounts.find((a) => a.id === to);
  const save = useMutation({
    mutationFn: () => accountsApi.transfer({ fromAccountId: from, toAccountId: to, amount, date, notes, rate: rate || undefined }),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: accountKeys.all });
      onClose();
    },
  });
  const submit = (e: FormEvent) => { e.preventDefault(); save.mutate(); };
  return (
    <Modal open onClose={onClose} title={t('accounts.transferTitle')}>
      <form onSubmit={submit} className="space-y-4" noValidate>
        {save.isError && <Alert>{translateError(t, save.error)}</Alert>}
        <p className="text-sm text-slate-500">{t('accounts.transferHint')}</p>
        <div className="grid grid-cols-2 gap-3">
          <Select label={t('accounts.from')} name="from" value={from} onChange={(e) => setFrom(e.target.value)}>
            {accounts.map((a) => <option key={a.id} value={a.id}>{a.name} ({a.currency})</option>)}
          </Select>
          <Select label={t('accounts.to')} name="to" value={to} onChange={(e) => setTo(e.target.value)}>
            {accounts.filter((a) => a.id !== from).map((a) => <option key={a.id} value={a.id}>{a.name} ({a.currency})</option>)}
          </Select>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <Input label={t('accounts.amountIn', { currency: src?.currency ?? '' })} name="amount" type="number" inputMode="decimal"
            step="0.01" min="0.01" required value={amount} onChange={(e) => setAmount(e.target.value)} />
          <Input label={t('payments.date')} name="date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </div>
        {src && dst && <ConversionPreview amount={amount} from={src.currency} to={dst.currency} purpose="TRANSFER" rate={rate} onRate={setRate} />}
        <Input label={t('payments.notes')} name="notes" maxLength={500} value={notes} onChange={(e) => setNotes(e.target.value)} />
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="submit" loading={save.isPending} disabled={!from || !to || from === to}>{t('accounts.transfer')}</Button>
        </div>
      </form>
    </Modal>
  );
}

function AdjustDialog({ account, onClose }: { account: Account; onClose: () => void }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [target, setTarget] = useState(String(account.balance));
  const [notes, setNotes] = useState('');
  const save = useMutation({
    mutationFn: () => accountsApi.adjust(account.id, { targetBalance: target, notes }),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: accountKeys.all });
      onClose();
    },
  });
  const diff = Number(target) - account.balance;
  const submit = (e: FormEvent) => { e.preventDefault(); save.mutate(); };
  return (
    <Modal open onClose={onClose} title={t('accounts.adjustTitle', { name: account.name })}>
      <form onSubmit={submit} className="space-y-4" noValidate>
        {save.isError && <Alert>{translateError(t, save.error)}</Alert>}
        <p className="text-sm text-slate-500">{t('accounts.adjustHint', { amount: formatMoney(account.balance, account.currency) })}</p>
        <Input label={t('accounts.realBalance', { currency: account.currency })} name="targetBalance" type="number" inputMode="decimal"
          step="0.01" required value={target} onChange={(e) => setTarget(e.target.value)}
          hint={Number.isFinite(diff) && diff !== 0 ? t('accounts.adjustDiff', { amount: (diff > 0 ? '+' : '') + formatMoney(diff, account.currency) }) : undefined} />
        <Input label={t('payments.notes')} name="notes" maxLength={500} placeholder={t('accounts.adjustPlaceholder')}
          value={notes} onChange={(e) => setNotes(e.target.value)} />
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="submit" loading={save.isPending} disabled={!Number.isFinite(diff) || diff === 0}>{t('common.save')}</Button>
        </div>
      </form>
    </Modal>
  );
}

function CardsPanel() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const { active } = useCards();
  const [name, setName] = useState('');
  const refresh = () => qc.invalidateQueries({ queryKey: accountKeys.cards });
  const add = useMutation({
    mutationFn: () => accountsApi.createCard({ name, color: COLORS[(active.length + 2) % COLORS.length] }),
    onSuccess: async () => { setName(''); await refresh(); },
  });
  const archive = useMutation({ mutationFn: (id: string) => accountsApi.archiveCard(id), onSuccess: refresh });
  return (
    <Card>
      <h2 className="font-semibold">{t('cards.title')}</h2>
      <p className="mb-3 text-xs text-slate-500">{t('cards.intro')}</p>
      {active.length > 0 && (
        <ul className="mb-3 divide-y divide-slate-100 text-sm dark:divide-slate-800">
          {active.map((c) => (
            <li key={c.id} className="flex items-center justify-between py-2">
              <span className="flex items-center gap-2"><span className="size-3 rounded-sm" style={{ backgroundColor: c.color }} aria-hidden />{c.name}</span>
              <Button variant="ghost" className="px-2 py-1 text-xs" onClick={() => archive.mutate(c.id)}>{t('accounts.archive')}</Button>
            </li>
          ))}
        </ul>
      )}
      <form className="flex items-end gap-2" onSubmit={(e) => { e.preventDefault(); if (name.trim()) add.mutate(); }} noValidate>
        <div className="flex-1">
          <Input label={t('cards.new')} name="cardName" maxLength={60} placeholder={t('cards.placeholder')} value={name}
            onChange={(e) => setName(e.target.value)} />
        </div>
        <Button type="submit" variant="secondary" loading={add.isPending} disabled={!name.trim()}>{t('common.add')}</Button>
      </form>
      {add.isError && <Alert>{translateError(t, add.error)}</Alert>}
    </Card>
  );
}

function RatesPanel() {
  const { t } = useTranslation();
  const q = useQuery({ queryKey: accountKeys.rates, queryFn: accountsApi.rates });
  const order = ['OFICIAL', 'MEP', 'TARJETA', 'BLUE', 'CCL'] as const;
  const rows = order.map((ty) => q.data?.find((r) => r.type === ty)).filter((r) => r != null);
  return (
    <Card>
      <h2 className="font-semibold">{t('rates.title')}</h2>
      <p className="mb-3 text-xs text-slate-500">{t('rates.intro')}</p>
      {q.isPending ? <InlineLoader label={t('common.loading')} /> : rows.length === 0 ? (
        <p className="text-sm text-slate-500">{t('rates.none')}</p>
      ) : (
        <table className="w-full text-sm tabular-nums">
          <thead className="text-left text-xs text-slate-500 uppercase">
            <tr><th className="py-1">{t('rates.type.label')}</th><th className="py-1 text-right">{t('rates.buy')}</th><th className="py-1 text-right">{t('rates.sell')}</th></tr>
          </thead>
          <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
            {rows.map((r) => (
              <tr key={r.type}>
                <td className="py-1.5">{t(`rates.type.${r.type}`)}<span className="block text-xs text-slate-500">{t(`rates.use.${r.type}`)}</span></td>
                <td className="py-1.5 text-right">{r.buy != null ? formatMoney(r.buy, 'ARS') : '—'}</td>
                <td className="py-1.5 text-right">{formatMoney(r.sell, 'ARS')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {rows[0] && <p className="mt-2 text-xs text-slate-500">{t('rates.updated', { date: formatDate(rows[0].day) })}</p>}
    </Card>
  );
}
