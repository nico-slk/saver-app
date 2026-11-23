import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Card } from '@/components/ui';
import { translateError } from '@/lib/errors';
import { notificationsApi, type NotificationPreference } from './notificationsApi';

export function NotificationSettings() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: ['notifications', 'preferences'], queryFn: notificationsApi.getPreferences });
  const [items, setItems] = useState<NotificationPreference[]>([]);
  useEffect(() => {
    if (query.data) setItems(query.data.items);
  }, [query.data]);

  const save = useMutation({
    mutationFn: () => notificationsApi.savePreferences(items),
    onSuccess: (data) => {
      setItems(data.items);
      void queryClient.setQueryData(['notifications', 'preferences'], data);
    },
  });

  const patchItem = (kind: NotificationPreference['kind'], patch: Partial<NotificationPreference>) => {
    setItems((current) => current.map((item) => item.kind === kind ? { ...item, ...patch } : item));
  };

  return (
    <Card>
      <div className="mb-4">
        <h2 className="font-semibold">{t('settings.notifications.title')}</h2>
        <p className="mt-1 text-sm text-slate-500">{t('settings.notifications.intro')}</p>
      </div>
      {query.isError && <Alert>{translateError(t, query.error)}</Alert>}
      {save.isError && <Alert>{translateError(t, save.error)}</Alert>}
      {save.isSuccess && <Alert kind="success">{t('settings.notifications.saved')}</Alert>}
      {query.isLoading ? (
        <p className="text-sm text-slate-500">{t('common.loading')}</p>
      ) : (
        <div className="space-y-3">
          {items.map((item) => (
            <section key={item.kind} className="rounded-lg border border-slate-200 p-3 dark:border-slate-700">
              <label className="flex cursor-pointer items-start gap-3">
                <input
                  type="checkbox"
                  checked={item.enabled}
                  onChange={(e) => patchItem(item.kind, { enabled: e.target.checked })}
                  className="mt-1 size-4 accent-brand-700"
                />
                <span className="min-w-0 flex-1">
                  <span className="block font-medium">{t(`settings.notifications.kinds.${item.kind}.title`)}</span>
                  <span className="block text-sm text-slate-500">{t(`settings.notifications.kinds.${item.kind}.description`)}</span>
                  <span className="mt-1 block text-xs font-medium text-slate-600 dark:text-slate-400">
                    {item.enabled ? t('settings.notifications.active') : t('settings.notifications.inactive')}
                  </span>
                </span>
              </label>
              <div className="mt-3 flex flex-wrap gap-x-5 gap-y-2 pl-7">
                {(['emailEnabled', 'pushEnabled'] as const).map((channel) => (
                  <label key={channel} className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-300">
                    <input
                      type="checkbox"
                      checked={item[channel]}
                      disabled={!item.enabled}
                      onChange={(e) => patchItem(item.kind, { [channel]: e.target.checked })}
                      className="size-4 accent-brand-700"
                    />
                    {t(`settings.notifications.channels.${channel}`)}
                  </label>
                ))}
              </div>
            </section>
          ))}
        </div>
      )}
      <div className="mt-4 space-y-3">
        <p className="text-xs text-slate-500">{t('settings.notifications.deliveryNote')}</p>
        <Button type="button" loading={save.isPending} disabled={!query.data} onClick={() => save.mutate()}>
          {t('settings.save')}
        </Button>
      </div>
    </Card>
  );
}
