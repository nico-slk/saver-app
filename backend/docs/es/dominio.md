# Dominio y reglas de negocio

[English](../en/domain.md) · [← Volver](../../README.md)

Este documento describe **qué** hace Saver. Es la referencia funcional para el backend y el frontend.
Las secciones marcadas con ✅ están implementadas; las marcadas con 🛠️ todavía no. Las parciales lo aclaran.

## Monedas y cotización 🛠️

- Monedas soportadas: **ARS** y **USD**. Cada usuario elige su moneda principal.
- Cotización: **dólar oficial, valor venta**, obtenida de una API pública (dolarapi.com). Si falla, se carga a mano.
- Cada cobro o pago en USD guarda la cotización usada. Cada cierre de mes guarda la cotización del día del cierre.
- Las proyecciones se muestran en valores **nominales** y **reales** (descontando la inflación de la moneda, ver [Inflación](#inflación-)).

## Ingresos ✅

| Tipo | Comportamiento | ¿Entra en proyecciones? |
|---|---|---|
| **Fijo** (sueldo) | Monto con vigencia; los cambios se versionan desde una fecha | Sí, indefinidamente |
| **Extra puntual** | Un monto en un mes (imprevisto, cobro, préstamo) | No |
| **Extra recurrente** | Mismo monto durante N meses o sin fecha de fin | Sí, solo en esos meses |

Si los montos de un trabajo pagado en partes son distintos cada mes, se cargan a mano como extras puntuales.

**Aumentos** (ingresos que se repiten): igual que los gastos fijos, cada N meses un % fijo o **según la inflación** de la moneda. Solo se usan para estimar meses futuros; el aumento real se registra con «Registrar aumento».

## Categorías ✅

- Categorías del sistema: Alquiler, Servicios, Cuotas, Otros. El usuario puede crear las suyas (ej. “Comida”).
- Borrar una categoría es un **borrado lógico**: deja de estar disponible para gastos nuevos, pero el historial conserva su nombre y color.

## Gastos ✅

Un gasto se define una vez como **plantilla** y genera **ocurrencias** mensuales.

| Tipo | Ejemplo | Ocurrencias |
|---|---|---|
| **Único** | Arreglo del auto | Una, en el mes indicado |
| **En cuotas** | 15.000 × 3 desde noviembre | Nov, Dic, Ene — cada una con “cuota n/N” |
| **Fijo indefinido** | Alquiler | Todos los meses hasta que se dé de baja |

- **Vencimiento**: por defecto el último día del mes; se puede fijar otra fecha al crear el gasto y editarla en cada ocurrencia.
- **Aumentos** (gastos fijos): periodicidad (ej. cada 4 meses) y un % fijo **o la inflación**: el aumento del mes A es la inflación acumulada de los N meses anteriores (A−N … A−1), oficial donde existe y proyectada para el resto. Cuando llega el aumento real, se carga el nuevo monto con su fecha de vigencia y queda en el historial.
- **Monto variable** (luz, gas): la plantilla tiene un monto estimado y cada mes se puede ajustar el monto de esa ocurrencia. La proyección usará el **promedio de los últimos 3 pagos reales** cuando existan los pagos (etapa 3); hoy usa el monto estimado.
- **Ocurrencias**: se generan automáticamente desde las plantillas hasta el mes actual (al consultar un mes). Los meses futuros se calculan sin guardarse. Las ocurrencias impagas de meses anteriores aparecen como **vencidas** en el mes actual.
- **Edición**: tipo, moneda, mes de inicio y cantidad de cuotas no se editan (se borra y se crea de nuevo). El monto cambia con «Registrar aumento» para conservar el historial; las ocurrencias sin pagos se recalculan.

## Pagos y comprobantes 🛠️

- Estados de una ocurrencia: **PENDIENTE → PARCIAL → PAGADO**, o **VENCIDO** si pasó el vencimiento sin completarse.
- Una ocurrencia admite **varios pagos** (ej. pago mínimo de la tarjeta). Cada pago tiene monto, fecha, moneda, cotización, observaciones y **varios adjuntos** (PDF, PNG, JPEG…).
- La interfaz lista los gastos ordenados por vencimiento, con los días restantes, el número de cuota y un botón **Pagar** que abre el formulario de pago.

## Cierre de mes 🛠️

- Se ejecuta **automáticamente al terminar cada mes**, en la zona horaria del usuario.
- Cada cierre tiene un **UUID interno** y un **identificador legible** por usuario (`CIE-2026-10`).
- Guarda una **foto inmutable**: ingresos, gastos por categoría (pagados e impagos), ahorro, saldo y cotización del día.
- Los gastos impagos quedan registrados en el cierre y **pasan al mes siguiente como VENCIDOS**, primeros en la lista.

## Ahorro 🛠️

- El usuario puede declarar cuánto ahorró en un mes.
- Si no declara nada, se considera ahorro **todo el sobrante** del mes (ingresos − gastos).

## Inflación ✅

- **Fuentes oficiales**, descargadas una vez por día (y al arrancar) y guardadas por mes: **ARS** = IPC del INDEC vía [ArgentinaDatos](https://argentinadatos.com) (sin clave); **USD** = IPC-U de EE.UU. (serie `CUUR0000SA0`) de la API pública de [BLS](https://www.bls.gov/developers/) (sin clave hasta 25 consultas/día). Si una fuente no responde (sin internet), se registra un aviso y se siguen usando los datos guardados.
- **Inflación de cada mes**: el dato oficial si existe; si no (meses recientes y futuros), la **proyectada**:
  1. la **manual** del usuario para esa moneda, si la cargó;
  2. si no, la **estimada** = promedio geométrico de los últimos 3 meses oficiales (`INFLATION_ESTIMATE_MONTHS`);
  3. sin datos ni manual: 0 %.
- **Inflación acumulada** entre dos meses = Π (1 + inflación mensual) − 1.
- Se usa en: aumentos de gastos fijos e ingresos **atados a inflación**, proyecciones en **valores de hoy** e historial en **términos reales**.

## Historial y comparaciones ✅

- Se arma con los datos reales de cada mes pasado (y el actual): ingresos vigentes y gastos generados. El monto de un gasto es lo pagado si está pago; si no, lo previsto (con los ajustes del mes).
- **Ahorro del mes** = ingresos − todos los gastos. Cuando exista el cierre de mes y el ahorro declarado (etapa de pagos) se usará ese valor.
- Serie mensual de un período (por defecto, los últimos 12 meses): ingresos, gastos, ahorro y gastos por categoría.
- **Filtro por categoría**: afecta solo a los gastos mostrados; el ahorro siempre considera todos los gastos.
- Resumen: ahorro del último mes vs. el anterior (monto y %), ahorro promedio, ahorro total y variación de gastos del primer al último mes.
- **Comparar dos meses**: por cada gasto, monto en cada mes, diferencia y %; estado *subió / bajó / igual / nuevo / terminó*. También por categoría, ingresos, gastos y ahorro, y el ahorro promedio desde el mes elegido.
- **Inflación**: cada mes muestra su inflación (oficial o estimada) y un factor para llevarlo a moneda del último mes; la pantalla puede mostrar todo **ajustado por inflación**. El resumen y la comparación informan la inflación acumulada del período, la variación de gastos descontada la inflación, el ahorro en valores reales y, por gasto, cuánto subió **por encima o por debajo de la inflación**.
- Máximo 120 meses por consulta.

## Proyecciones ✅

Desde el mes actual (incluido) y durante N meses (1 a 120):

- **Ingresos**: fijos y extras recurrentes vigentes; los extras de un solo mes solo cuentan en su mes. Si tienen regla de aumentos (% o inflación) se aplica en los meses futuros; si no, se mantienen constantes.
- **Gastos**: el mes actual usa lo ya generado (con sus ajustes). Los meses futuros usan las plantillas: cuotas restantes, gastos fijos con **aumentos estimados** (cada N meses, % o inflación) y los de **monto variable con el promedio de los últimos 3 meses**.
- **Saldo acumulado** = dinero disponible hoy (opcional) + ahorro de cada mes.
- **En valores de hoy**: el ahorro de cada mes se divide por la inflación acumulada desde el mes actual, usando la [inflación de la moneda](#inflación-). En la pantalla se puede probar otra inflación solo para esa simulación.
- **Novedades por mes**: última cuota de cada compra, aumentos estimados de gastos e ingresos (cuando el monto sube, incluso si el aumento del mes actual no se registró) y fin de ingresos recurrentes.
- Resumen: saldo final (nominal y real), mes en que terminan las cuotas, ahorro mensual promedio y promedio después de las cuotas, meses con saldo negativo.
- Se proyecta una sola moneda (la principal del usuario); si hay movimientos en otra, se avisa.

## Línea de tiempo del inicio ✅

- Gráfico de ingresos y gastos por mes **desde un mes elegido y durante N meses** (1 a 120); por defecto, 6 meses antes del actual, el actual y 6 después.
- Los meses anteriores salen del historial (reales); el actual y los siguientes, de la proyección. El día de hoy queda marcado.

## Recuperación de contraseña ✅

- Desde el login, «¿Olvidaste tu contraseña?» pide el email y siempre responde lo mismo, exista o no la cuenta.
- Si existe, se envía un email (en el idioma del usuario) con un link de un solo uso válido **30 minutos**. Pedir uno nuevo invalida el anterior; máximo 3 pedidos cada 15 minutos.
- Al elegir la nueva contraseña se cierran todas las sesiones abiertas del usuario. También sirve para que una cuenta creada con Google agregue una contraseña.

## Simulador de inversiones 🛠️

- Tasa anual como **TNA o TEA**, con capitalización mensual.
- Aportes: el ahorro declarado de cada mes o, si no hay, el sobrante.
- Resultado nominal y real.
- Varios escenarios guardados por usuario; el período (inicio-fin) se recuerda al volver a entrar.

## Informes 🛠️

Vista imprimible en el frontend, PDF generado en el backend y exportación a Excel/CSV.
