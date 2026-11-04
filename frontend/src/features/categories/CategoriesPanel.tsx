import { useState, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Card, Input } from '@/components/ui';
import { Badge, ConfirmDialog } from '@/components/ui/overlay';
import { translateError } from '@/lib/errors';
import { useFinanceInvalidation } from '@/features/months/useFinanceInvalidation';
import { categoriesApi, type Category } from './categoriesApi';
import { useCategories } from './useCategories';

const PALETTE = ['#22c55e', '#ef4444', '#a855f7', '#ec4899', '#14b8a6', '#f97316', '#84cc16', '#3b82f6'];

/** Alta, edición y borrado (lógico) de categorías propias. Las de sistema son fijas. */
export function CategoriesPanel() {
  const { t } = useTranslation();
  const { active, label } = useCategories();
  const refresh = useFinanceInvalidation();
  const [form, setForm] = useState({ name: '', color: PALETTE[0]! });
  const [editing, setEditing] = useState<Category | null>(null);
  const [deleting, setDeleting] = useState<Category | null>(null);

  const create = useMutation({
    mutationFn: () => categoriesApi.create(form),
    onSuccess: async () => {
      setForm({ name: '', color: PALETTE[(active.length + 1) % PALETTE.length]! });
      await refresh();
    },
  });
  const update = useMutation({
    mutationFn: (c: Category) => categoriesApi.update(c.id, { name: c.name ?? '', color: c.color }),
    onSuccess: async () => {
      setEditing(null);
      await refresh();
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => categoriesApi.remove(id),
    onSuccess: async () => {
      setDeleting(null);
      await refresh();
    },
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    create.mutate();
  };

  return (
    <div className="grid gap-4 lg:grid-cols-[1fr_320px]">
      <Card>
        {(update.isError || remove.isError) && <Alert>{translateError(t, update.error ?? remove.error)}</Alert>}
        <ul className="divide-y divide-slate-100 dark:divide-slate-800">
          {active.map((c) => (
            <li key={c.id} className="flex items-center gap-3 py-2.5">
              {editing?.id === c.id ? (
                <form
                  className="flex flex-1 flex-wrap items-center gap-2"
                  onSubmit={(e) => {
                    e.preventDefault();
                    update.mutate(editing);
                  }}
                >
                  <input type="color" aria-label={t('categories.color')} value={editing.color}
                    onChange={(e) => setEditing({ ...editing, color: e.target.value })} className="h-8 w-10 rounded" />
                  <input aria-label={t('categories.name')} value={editing.name ?? ''} maxLength={60} required
                    onChange={(e) => setEditing({ ...editing, name: e.target.value })}
                    className="min-w-0 flex-1 rounded-lg border border-slate-300 bg-white px-2 py-1.5 text-sm dark:border-slate-700 dark:bg-slate-900" />
                  <Button type="submit" loading={update.isPending}>{t('common.save')}</Button>
                  <Button type="button" variant="ghost" onClick={() => setEditing(null)}>{t('common.cancel')}</Button>
                </form>
              ) : (
                <>
                  <span className="size-3.5 shrink-0 rounded-full" style={{ backgroundColor: c.color }} aria-hidden />
                  <span className="flex-1 text-sm">{label(c)}</span>
                  {c.system ? (
                    <Badge>{t('categories.systemBadge')}</Badge>
                  ) : (
                    <div className="flex gap-1">
                      <Button variant="ghost" className="px-2 py-1 text-xs" onClick={() => setEditing(c)}>{t('common.edit')}</Button>
                      <Button variant="ghost" className="px-2 py-1 text-xs text-red-600" onClick={() => setDeleting(c)}>{t('common.delete')}</Button>
                    </div>
                  )}
                </>
              )}
            </li>
          ))}
        </ul>
      </Card>
      <Card>
        <h3 className="mb-3 font-semibold">{t('categories.new')}</h3>
        <form onSubmit={submit} className="space-y-3">
          {create.isError && <Alert>{translateError(t, create.error)}</Alert>}
          <Input label={t('categories.name')} name="name" required maxLength={60} placeholder={t('categories.placeholder')}
            value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          <div>
            <span className="text-sm font-medium text-slate-700 dark:text-slate-300">{t('categories.color')}</span>
            <div className="mt-1 flex flex-wrap gap-2">
              {PALETTE.map((col) => (
                <button key={col} type="button" aria-label={col} aria-pressed={form.color === col}
                  onClick={() => setForm({ ...form, color: col })}
                  className="size-7 rounded-full ring-offset-2 aria-pressed:ring-2 aria-pressed:ring-slate-500 dark:ring-offset-slate-900"
                  style={{ backgroundColor: col }} />
              ))}
            </div>
          </div>
          <Button type="submit" className="w-full" loading={create.isPending}>{t('categories.create')}</Button>
        </form>
        <p className="mt-3 text-xs text-slate-500">{t('categories.deleteHint')}</p>
      </Card>
      <ConfirmDialog
        open={!!deleting}
        title={t('categories.deleteTitle')}
        message={t('categories.deleteMessage', { name: deleting?.name ?? '' })}
        confirmLabel={t('common.delete')}
        loading={remove.isPending}
        onConfirm={() => deleting && remove.mutate(deleting.id)}
        onClose={() => setDeleting(null)}
      />
    </div>
  );
}
