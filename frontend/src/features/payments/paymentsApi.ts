import { api } from '@/lib/api';
import type { Currency } from '@/lib/format';

export type Attachment = { id: string; fileName: string; contentType: string; sizeBytes: number };

export type Payment = {
  id: string;
  occurrenceId: string;
  accountId?: string;
  accountName?: string;
  accountCurrency?: Currency;
  paidOn: string;
  amount: number;
  currency: Currency;
  accountAmount?: number;
  rate?: number;
  rateType?: string;
  notes?: string;
  assumed: boolean;
  attachments: Attachment[];
};

export type OccurrencePayments = {
  occurrenceId: string;
  status: 'PENDING' | 'PARTIAL' | 'PAID';
  expectedAmount: number;
  paidAmount: number;
  remaining: number;
  currency: Currency;
  cardId?: string;
  payments: Payment[];
};

export const ACCEPTED_FILES = 'application/pdf,image/png,image/jpeg,image/webp';
export const MAX_FILES = 10;

export const paymentsApi = {
  list: (occurrenceId: string) => api.get<OccurrencePayments>(`/api/occurrences/${occurrenceId}/payments`).then((r) => r.data),
  pay: (occurrenceId: string, input: { accountId: string; amount: string; date?: string; rate?: string; notes?: string }) =>
    api.post<OccurrencePayments>(`/api/occurrences/${occurrenceId}/payments`, { ...input, rate: input.rate || undefined }).then((r) => r.data),
  remove: (paymentId: string) => api.delete<OccurrencePayments>(`/api/payments/${paymentId}`).then((r) => r.data),
  upload: (paymentId: string, files: File[]) => {
    const form = new FormData();
    files.forEach((f) => form.append('files', f));
    // El cliente usa JSON por defecto: con multipart axios deja el FormData tal cual y el navegador pone el boundary.
    return api.post<Attachment[]>(`/api/payments/${paymentId}/attachments`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }).then((r) => r.data);
  },
  removeAttachment: (id: string) => api.delete<void>(`/api/attachments/${id}`),
  /** Abre el comprobante en otra pestaña (se descarga con la sesión, no hay links públicos). */
  open: async (a: Attachment) => {
    const win = window.open('', '_blank');
    const blob = await api.get<Blob>(`/api/attachments/${a.id}`, { responseType: 'blob' }).then((r) => r.data);
    const url = URL.createObjectURL(blob);
    if (win) win.location.href = url;
    else window.location.href = url;
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  },
};
