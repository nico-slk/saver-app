import { useQuery, useQueryClient } from '@tanstack/react-query';
import { accountKeys, accountsApi } from './accountsApi';

export function useAccounts() {
  const q = useQuery({ queryKey: accountKeys.all, queryFn: accountsApi.overview });
  const active = (q.data?.accounts ?? []).filter((a) => !a.archived);
  return { ...q, active };
}

export function useCards() {
  const q = useQuery({ queryKey: accountKeys.cards, queryFn: accountsApi.cards });
  const cards = q.data ?? [];
  return { ...q, cards, active: cards.filter((c) => !c.archived), byId: (id?: string | null) => cards.find((c) => c.id === id) };
}

/** Saldos y movimientos dependen de pagos, cobros y transferencias. */
export function useAccountsInvalidation() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: accountKeys.all });
}
