import type { ReactNode } from 'react';

/** Tooltip neutro (superficie + tinta de texto), con muestra de color por serie. */
export function TooltipBox({ title, rows }: { title: ReactNode; rows: { color: string; label: string; value: string }[] }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs shadow-md dark:border-slate-700 dark:bg-slate-900">
      <p className="mb-1 font-semibold text-slate-900 dark:text-slate-100">{title}</p>
      {rows.map((r) => (
        <p key={r.label} className="flex items-center gap-2 text-slate-600 dark:text-slate-300">
          <span className="size-2.5 rounded-sm" style={{ backgroundColor: r.color }} aria-hidden />
          <span className="flex-1">{r.label}</span>
          <span className="font-medium text-slate-900 tabular-nums dark:text-slate-100">{r.value}</span>
        </p>
      ))}
    </div>
  );
}
