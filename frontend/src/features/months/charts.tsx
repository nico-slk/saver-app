import { useTranslation } from 'react-i18next';
import { Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useChartTheme } from '@/components/charts/chartTheme';
import { TooltipBox } from '@/components/charts/ChartTooltip';
import { INTL_LOCALE, currentLanguage } from '@/i18n';
import { formatMoney, type Currency } from '@/lib/format';
import { formatMonth } from '@/lib/month';

const compact = (v: number) =>
  new Intl.NumberFormat(INTL_LOCALE[currentLanguage()], { notation: 'compact', maximumFractionDigits: 1 }).format(v);

/** Gastos del mes por categoría: magnitud → barras horizontales de un solo color, ordenadas. */
export function CategoryBars({ data, currency }: { data: { name: string; amount: number }[]; currency: Currency }) {
  const c = useChartTheme();
  const height = Math.max(120, data.length * 36 + 30);
  return (
    <div style={{ height }} role="img" aria-label={data.map((d) => `${d.name}: ${formatMoney(d.amount, currency)}`).join(', ')}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} layout="vertical" margin={{ top: 4, right: 16, bottom: 4, left: 4 }} barCategoryGap={8}>
          <CartesianGrid horizontal={false} stroke={c.grid} />
          <XAxis type="number" tickFormatter={compact} stroke={c.axis} tick={{ fill: c.axis, fontSize: 11 }} tickLine={false} axisLine={false} />
          <YAxis type="category" dataKey="name" width={110} stroke={c.axis} tick={{ fill: c.text, fontSize: 12 }} tickLine={false} axisLine={false} />
          <Tooltip
            cursor={{ fill: c.grid, opacity: 0.5 }}
            content={({ active, payload }) =>
              active && payload?.[0] ? (
                <TooltipBox title={String(payload[0].payload.name)}
                  rows={[{ color: c.series1, label: '', value: formatMoney(Number(payload[0].value), currency) }]} />
              ) : null
            }
          />
          <Bar dataKey="amount" fill={c.series1} radius={[0, 4, 4, 0]} maxBarSize={22} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

/** Próximos meses: ingresos vs gastos previstos (un solo eje, barras agrupadas). */
export function OutlookBars({ data, currency, label }: { data: { month: string; income: number; expenses: number }[]; currency: Currency; label?: string }) {
  const { t } = useTranslation();
  const c = useChartTheme();
  const labels = { income: t('month.income'), expenses: t('month.expenses') };
  return (
    <div className="h-64" role="img" aria-label={label ?? t('projections.monthlyTitle')}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} barGap={2} barCategoryGap="22%">
          <CartesianGrid vertical={false} stroke={c.grid} />
          <XAxis dataKey="month" tickFormatter={(m: string) => formatMonth(m, true)} stroke={c.axis}
            tick={{ fill: c.axis, fontSize: 11 }} tickLine={false} axisLine={{ stroke: c.grid }} />
          <YAxis tickFormatter={compact} width={48} stroke={c.axis} tick={{ fill: c.axis, fontSize: 11 }} tickLine={false} axisLine={false} />
          <Tooltip
            cursor={{ fill: c.grid, opacity: 0.5 }}
            content={({ active, payload, label }) =>
              active && payload?.length ? (
                <TooltipBox title={formatMonth(String(label))}
                  rows={payload.map((p) => ({
                    color: String(p.color),
                    label: labels[p.dataKey as 'income' | 'expenses'],
                    value: formatMoney(Number(p.value), currency),
                  }))} />
              ) : null
            }
          />
          <Legend itemSorter={null} iconType="square" iconSize={10} formatter={(v: string) => (
            <span style={{ color: c.text, fontSize: 12 }}>{labels[v as 'income' | 'expenses']}</span>
          )} />
          <Bar dataKey="income" fill={c.series1} radius={[4, 4, 0, 0]} maxBarSize={28} />
          <Bar dataKey="expenses" fill={c.series2} radius={[4, 4, 0, 0]} maxBarSize={28} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

/** Ahorro por mes: una sola serie, con línea de referencia en 0 (los meses negativos quedan por debajo). */
export function SavingsBars({ data, currency, label }: { data: { month: string; value: number }[]; currency: Currency; label: string }) {
  const c = useChartTheme();
  return (
    <div className="h-56" role="img" aria-label={label}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} barCategoryGap="22%">
          <CartesianGrid vertical={false} stroke={c.grid} />
          <XAxis dataKey="month" tickFormatter={(m: string) => formatMonth(m, true)} stroke={c.axis}
            tick={{ fill: c.axis, fontSize: 11 }} tickLine={false} axisLine={{ stroke: c.grid }} />
          <YAxis tickFormatter={compact} width={48} stroke={c.axis} tick={{ fill: c.axis, fontSize: 11 }} tickLine={false} axisLine={false} />
          <ReferenceLine y={0} stroke={c.axis} />
          <Tooltip
            cursor={{ fill: c.grid, opacity: 0.5 }}
            content={({ active, payload, label: l }) =>
              active && payload?.[0] ? (
                <TooltipBox title={formatMonth(String(l))} rows={[{ color: c.series1, label, value: formatMoney(Number(payload[0].value), currency) }]} />
              ) : null
            }
          />
          <Bar dataKey="value" fill={c.series1} radius={[4, 4, 0, 0]} maxBarSize={28} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

/** Saldo acumulado: nominal y en valores de hoy (dos líneas, un solo eje). */
export function CumulativeLines({
  data,
  currency,
  labels,
}: {
  data: { month: string; nominal: number; real?: number }[];
  currency: Currency;
  labels: { nominal: string; real: string };
}) {
  const c = useChartTheme();
  const showReal = data.some((d) => d.real !== undefined && d.real !== d.nominal);
  return (
    <div className="h-64" role="img" aria-label={labels.nominal}>
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
          <CartesianGrid vertical={false} stroke={c.grid} />
          <XAxis dataKey="month" tickFormatter={(m: string) => formatMonth(m, true)} stroke={c.axis}
            tick={{ fill: c.axis, fontSize: 11 }} tickLine={false} axisLine={{ stroke: c.grid }} minTickGap={16} />
          <YAxis tickFormatter={compact} width={52} stroke={c.axis} tick={{ fill: c.axis, fontSize: 11 }} tickLine={false} axisLine={false} />
          <ReferenceLine y={0} stroke={c.axis} />
          <Tooltip
            cursor={{ stroke: c.axis, strokeDasharray: '3 3' }}
            content={({ active, payload, label }) =>
              active && payload?.length ? (
                <TooltipBox title={formatMonth(String(label))}
                  rows={payload.map((p) => ({
                    color: String(p.color),
                    label: p.dataKey === 'real' ? labels.real : labels.nominal,
                    value: formatMoney(Number(p.value), currency),
                  }))} />
              ) : null
            }
          />
          {showReal && (
            <Legend iconType="plainline" iconSize={14} formatter={(v: string) => (
              <span style={{ color: c.text, fontSize: 12 }}>{v === 'real' ? labels.real : labels.nominal}</span>
            )} />
          )}
          <Line type="monotone" dataKey="nominal" stroke={c.series1} strokeWidth={2} dot={false} activeDot={{ r: 5 }} />
          {showReal && <Line type="monotone" dataKey="real" stroke={c.series2} strokeWidth={2} dot={false} activeDot={{ r: 5 }} />}
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
