# Dominio y reglas de negocio

[English](../en/domain.md) · [← Volver](../../README.md)

Este documento describe **qué** hace Saver. Es la referencia funcional para el backend y el frontend.
Las secciones marcadas con ✅ están implementadas; las marcadas con 🛠️ todavía no. Las parciales lo aclaran.

## Monedas y cotización ✅

- Monedas soportadas: **ARS** y **USD**. Cada usuario elige su moneda principal.
- Cotizaciones de [dolarapi.com](https://dolarapi.com) (oficial, MEP, tarjeta, blue, CCL…), guardadas por día. Se descargan 3 veces por día, al arrancar y al pedirlas si tienen más de 3 horas. Sin internet se carga la cotización a mano en cada movimiento.
- **Qué dólar se usa según el uso**:

| Operación | Dólar | Precio |
|---|---|---|
| Gasto en USD pagado desde una cuenta en pesos, **con tarjeta** | Tarjeta | Venta |
| Gasto en USD pagado desde una cuenta en pesos, sin tarjeta | Oficial | Venta |
| Gasto en pesos pagado desde una cuenta en dólares | Oficial | Compra |
| Ingreso en USD cobrado en una cuenta en pesos | Oficial | Compra |
| Transferencia entre cuentas de distinta moneda (comprar/vender dólares) | MEP | Venta al comprar, compra al vender |
| Valuación del patrimonio | MEP | Compra |

- Cada pago, cobro y transferencia guarda la cotización usada (tipo y valor); siempre se puede indicar otra a mano (queda como `MANUAL`).
- Las proyecciones se muestran en valores **nominales** y **reales** (descontando la inflación de la moneda, ver [Inflación](#inflación-)).

## Cuentas ✅

- Tipos: **cuenta bancaria**, **efectivo**, **billetera virtual**, **ahorro** e **inversión** (estas dos cuentan como ahorro), en ARS o USD.
- **Saldo** = saldo inicial a una fecha + cobros + transferencias entrantes − pagos − transferencias salientes ± ajustes. Los movimientos anteriores a la fecha del saldo inicial no lo modifican (ya están incluidos en él).
- **Patrimonio**: suma de todas las cuentas activas en la moneda principal; los dólares se valúan a MEP compra.
- **Transferencias** entre cuentas propias; si las monedas difieren, es una compra o venta de dólares.
- **Ajustar saldo**: se carga el saldo real y se registra la diferencia (intereses, comisiones, gastos no cargados).
- Una cuenta con movimientos no se borra: se **archiva** (deja de aparecer para nuevos movimientos, conserva el historial).

## Tarjetas ✅

- Solo **agrupan** gastos: un gasto puede indicar con qué tarjeta se paga y en «Gastos del mes» se muestran juntos con el total pendiente. No hay resumen, cierre ni pago del resumen: **cada cuota se paga por separado**.
- Un gasto en USD con tarjeta se paga con el dólar tarjeta. Borrar una tarjeta la archiva.

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

## Pagos y comprobantes ✅

- Estados de una ocurrencia: **PENDIENTE → PARCIAL → PAGADO**, o **VENCIDO** si pasó el vencimiento sin completarse. El estado sale de la suma de sus pagos (se puede pagar de más, ej. una factura de luz más alta).
- Una ocurrencia admite **varios pagos**. Cada pago tiene cuenta de origen, monto (en la moneda del gasto), lo que salió de la cuenta (en su moneda), cotización, fecha, observaciones y **hasta 10 comprobantes** (PDF, PNG, JPEG, WEBP; 10 MB c/u). El tipo se valida por el contenido del archivo; se descargan a través de la API con la sesión.
- Borrar un pago devuelve el monto a la cuenta, borra sus comprobantes y recalcula el estado.
- **Meses pasados**: al crear un gasto que empieza antes del mes actual hay que responder si esos meses ya están pagos (sin valor por defecto). Si sí, se registran **pagos asumidos** (sin cuenta ni comprobante, no mueven saldos). Un gasto con solo pagos asumidos se puede borrar.

## Cobros de ingresos ✅

- «Cobrar» registra que un ingreso del mes entró a una cuenta (total o en partes), con fecha, cotización si la cuenta es de otra moneda y observaciones.
- Un ingreso con cobros no se borra (los saldos dependen de ellos): se da de baja o se borran los cobros primero.

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
