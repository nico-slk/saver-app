import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { categoriesApi, type Category } from './categoriesApi';

/** Todas las categorías (incluidas las borradas, para mostrar el historial) indexadas por id. */
export function useCategories() {
  const { t } = useTranslation();
  const query = useQuery({ queryKey: ['categories', 'all'], queryFn: () => categoriesApi.list(true) });
  const all = query.data ?? [];
  const byId = new Map(all.map((c) => [c.id, c]));
  const label = (c: Category | undefined) =>
    !c ? '—' : c.system ? t(`categories.system.${c.systemKey}`, { defaultValue: c.systemKey ?? '' }) : (c.name ?? '');
  return {
    ...query,
    all,
    active: all.filter((c) => !c.deleted),
    byId,
    label,
    nameOf: (id: string) => label(byId.get(id)),
  };
}
