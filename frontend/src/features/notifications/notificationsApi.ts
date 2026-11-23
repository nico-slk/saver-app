import { api } from '@/lib/api';

export type NotificationKind = 'DUE_SOON' | 'DUE_TODAY' | 'OVERDUE';
export type NotificationPreference = {
  kind: NotificationKind;
  enabled: boolean;
  emailEnabled: boolean;
  pushEnabled: boolean;
};
export type NotificationPreferences = { items: NotificationPreference[] };

export const notificationsApi = {
  getPreferences: () => api.get<NotificationPreferences>('/api/notifications/preferences').then((r) => r.data),
  savePreferences: (items: NotificationPreference[]) =>
    api.put<NotificationPreferences>('/api/notifications/preferences', { items }).then((r) => r.data),
};
