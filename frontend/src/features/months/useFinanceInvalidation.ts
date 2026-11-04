import { useQueryClient } from '@tanstack/react-query';

/** Ingresos, gastos, meses, historial y proyecciones dependen entre sí: tras cualquier cambio se refrescan todos. */
export function useFinanceInvalidation() {
  const qc = useQueryClient();
  return () =>
    Promise.all([
      qc.invalidateQueries({ queryKey: ['months'] }),
      qc.invalidateQueries({ queryKey: ['expenses'] }),
      qc.invalidateQueries({ queryKey: ['incomes'] }),
      qc.invalidateQueries({ queryKey: ['categories'] }),
      qc.invalidateQueries({ queryKey: ['history'] }),
      qc.invalidateQueries({ queryKey: ['projections'] }),
      qc.invalidateQueries({ queryKey: ['timeline'] }),
    ]);
}
