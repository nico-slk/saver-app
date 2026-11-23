# Preparar Saver para la nube sin elegir proveedor

Saver ya tiene varias piezas que permiten comparar proveedores sin rediseñar el dominio: el backend se construye como contenedor Java, la configuración llega por variables de entorno, Flyway versiona el esquema y el almacenamiento de objetos se abstrae detrás de `StorageService`. La API usa PostgreSQL. El frontend es una SPA que puede servirse desde hosting estático.

## Arquitectura objetivo portable

| Responsabilidad | Necesidad de Saver | Requisito para comparar proveedores |
|---|---|---|
| Frontend | Archivos estáticos, SPA y fallback de rutas | Hosting estático, HTTPS, dominio y rewrite `/api` o CORS seguro |
| API | Contenedor Java 21 sin estado local persistente | Runtime de contenedores, escalado a cero o instancia mínima baja, logs y health check |
| Base de datos | PostgreSQL persistente | PostgreSQL administrado, backups, restauración y conexión TLS desde la API |
| Comprobantes | Object storage privado | API S3 compatible o adaptación de `StorageService`, URLs firmadas y política de ciclo de vida |
| Secretos | Credenciales de DB, JWT, storage y correo | Secret manager o variables secretas, nunca incluidas en el frontend |
| Trabajos programados | Sincronización de inflación y futuras notificaciones | Scheduler externo o bloqueo distribuido si se ejecuta más de una instancia |

## Criterios de costo

No comparar solo el precio anunciado del servidor. Para una app pequeña, revisar:

1. Si API y frontend pueden escalar a cero o si existe un mínimo mensual obligatorio.
2. El costo base, límites de conexión, backups y retención de PostgreSQL. La base suele ser el costo persistente más importante.
3. Cuotas gratuitas y precio al excederlas para almacenamiento, egreso de datos, logs, builds y solicitudes.
4. Región cercana a las personas usuarias y a la base de datos para evitar latencia y transferencia entre regiones.
5. Si el proveedor permite exportar PostgreSQL y objetos sin formatos propietarios.
6. Qué alertas de presupuesto, límites de gasto y backups automáticos ofrece.

La opción de menor costo depende del uso real, región, tráfico y política de backups. Conviene comparar una arquitectura mínima con los mismos supuestos y verificar los precios vigentes en las calculadoras oficiales antes de decidir.

## Preparación técnica y decisiones pendientes

- Mantener un único artefacto de backend (Dockerfile) y configurar puertos, JDBC, correo y storage exclusivamente desde entorno/secretos.
- Mantener la API sin estado local: los comprobantes no deben depender del disco efímero del contenedor.
- Mantener PostgreSQL estándar y migraciones aditivas de Flyway; validar exportación y restauración en el proveedor elegido.
- Preferir hosting de frontend con CDN y un rewrite `/api` cuando esté disponible. Si frontend y API usan dominios diferentes, ajustar CORS y la política de cookie `SameSite`/`Secure`.
- Añadir adaptador de push y entrega de email antes de activar notificaciones. Para push web hacen falta service worker, claves VAPID, suscripciones por dispositivo, expiración y baja; para email, proveedor, límites, rebotes y plantillas.
- Ejecutar trabajos de horario con scheduler administrado o una estrategia de exclusión distribuida. No asumir que `@Scheduled` corre una sola vez al escalar horizontalmente.
- Definir retención de logs, límites de gasto, alertas, rotación de secretos y restauración probada antes de uso real.

## Evaluación reproducible

Al comparar opciones, completar para cada una: región, frontend, runtime de contenedor, PostgreSQL, storage, scheduler, backups, costo mensual mínimo, costo a 1.000 y 10.000 usuarios activos, costos de egreso y mecanismo de salida/exportación. Usar los mismos supuestos de uso evita comparar planes incompatibles.

La guía de [`despliegue.md`](despliegue.md) documenta el camino GCP existente; es una opción de referencia, no una decisión tomada.
