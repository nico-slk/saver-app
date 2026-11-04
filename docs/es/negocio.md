# Saver: documentación de negocio

## Propósito

Saver es una aplicación web pensada para que una persona pueda ordenar su vida financiera en un solo lugar. Su objetivo de producto es ayudar a registrar y entender ingresos, gastos, ahorro e inversiones, y facilitar la toma de decisiones. La solución está en construcción por etapas: hoy ofrece una base de acceso y personalización segura sobre la cual se incorporarán las herramientas financieras.

## Qué puede hacer una persona hoy

1. Crear una cuenta con nombre, correo electrónico y contraseña.
2. Iniciar y cerrar sesión con correo y contraseña.
3. Iniciar sesión con una cuenta de Google si el administrador habilitó esa opción.
4. Mantener la sesión iniciada de forma segura al volver a abrir la aplicación.
5. Consultar y actualizar su perfil: nombre, idioma, moneda principal y zona horaria.
6. Elegir español o inglés, y cambiar entre tema claro, oscuro o el que sugiera el dispositivo.
7. Cargar **ingresos**: fijos (sueldo), extras de un solo mes o extras que se repiten N meses o sin fin, y registrar aumentos desde un mes.
8. Cargar **gastos**: únicos, **en cuotas** (ej. 15.000 × 3 meses, con «cuota n/N») y **fijos mensuales** (alquiler, servicios) con vencimiento propio o el último día del mes, monto variable y aumentos periódicos estimados (ej. +10 % cada 4 meses).
9. Recuperar la contraseña con un link enviado por **email** (válido 30 minutos, de un solo uso).
10. Revisar el **historial**: ingresos, gastos y ahorro por mes, filtrar por categoría y **comparar dos meses** (qué gastos subieron o bajaron, cuánto y en qué %, ahorro vs. el mes anterior y promedio).
11. Ver **proyecciones** a 6–60 meses: cuánto va a tener ahorrado, cuándo termina de pagar las cuotas, aumentos estimados y el resultado en valores de hoy según la inflación estimada.
12. Usar la **inflación oficial** de cada moneda (INDEC para pesos, IPC de EE.UU. para dólares), descargada sola todos los días: para proyectar en valores de hoy, para gastos e ingresos que **aumentan según inflación** y para ver el historial en **términos reales** (qué gastos subieron más que la inflación). Se puede cargar una inflación manual para los meses sin dato.
13. Ver en el inicio un gráfico de ingresos y gastos **desde cualquier mes y durante N meses**, con el pasado real, el futuro proyectado y el día de hoy marcado.
14. Crear, editar y eliminar **categorías** propias (ej. «Comida»); las eliminadas se conservan en el historial.
15. Ver el **balance del mes** (ingresos, gastos, pendiente, vencido), los **próximos vencimientos** ordenados por fecha con días restantes, un gráfico de gastos por categoría y el gráfico pasado + proyección. Navegar a cualquier mes; los futuros se muestran como estimación.

Cada persona solo puede acceder a su propia cuenta. El correo se conserva como identificador del perfil y no se edita desde la pantalla de configuración.

## Recorrido principal

Una persona nueva entra, selecciona el idioma que prefiere y crea su cuenta o usa Google. Al ingresar carga su sueldo y sus gastos fijos y en cuotas, y desde **Inicio** ve el balance del mes, lo que vence primero y cómo vienen los próximos meses. En **Ajustes** para definir cómo quiere ver el producto: moneda (peso argentino o dólar), idioma, zona horaria y apariencia. Al cerrar sesión, su acceso queda finalizado en ese navegador.

## Funciones que se están preparando

El menú muestra la dirección funcional del producto, pero las siguientes secciones aún no están activas:

| Próxima capacidad | Valor esperado para la persona usuaria |
|---|---|
| Pagos y comprobantes | Marcar gastos como pagados (también en partes), adjuntar facturas y cerrar el mes automáticamente. El botón «Pagar» ya está visible pero deshabilitado. |
| Ahorro declarado | Registrar cuánto se separó para ahorrar cada mes. |
| Inversiones | Simular escenarios y guardarlos. |
| Informes | Compartir o descargar resúmenes en PDF, Excel o CSV. |

No se deben comunicar estas funciones como disponibles hasta que sus pantallas, API y reglas estén implementadas y probadas.

## Reglas de experiencia y privacidad

- La contraseña debe tener al menos 8 caracteres. Para evitar problemas de seguridad con caracteres multibyte, el servidor limita su tamaño a 72 bytes.
- No se guardan contraseñas legibles: se guarda una representación segura de ellas.
- La aplicación recuerda la sesión mediante un mecanismo protegido del navegador; la clave de acceso de corta duración no queda guardada de forma permanente en el navegador.
- El idioma del perfil prevalece después de iniciar sesión. Antes de eso, se usa el idioma elegido o detectado en el navegador.
- La moneda y la zona horaria son preferencias del perfil. Hoy no existen movimientos financieros a convertir ni cierres mensuales ejecutándose.

## Indicadores sugeridos cuando avance el producto

Para validar el valor de las próximas etapas conviene seguir: cuentas que completan su perfil, personas activas mensualmente, movimientos registrados por persona, porcentaje de meses cerrados, uso de proyecciones e informes exportados. Estos indicadores son recomendaciones de negocio, no métricas que el sistema actual ya recolecte.
