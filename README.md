# Saver

🇪🇸 Español · [🇬🇧 English](README.en.md)

Saver es una aplicación web responsive para organizar las finanzas personales en un solo lugar. Ayuda a entender qué dinero entra, qué gastos hay que cubrir y cómo podrían evolucionar las cuentas, para tomar decisiones cotidianas con más claridad.

## El problema que resuelve

Los ingresos, las cuotas, los servicios y los vencimientos suelen quedar repartidos entre notas, planillas, recordatorios y aplicaciones bancarias. Eso dificulta saber cuánto queda disponible, qué pagos se acercan y cómo impacta un gasto en los meses siguientes. Saver reúne esa información en una vista mensual y permite relacionar los movimientos con cuentas y fechas concretas.

## Qué ofrece hoy

- Registrar ingresos fijos, extras y aumentos; cargar gastos únicos, recurrentes y en cuotas.
- Consultar balance mensual, vencimientos, deudas pendientes y gastos por categoría.
- Comparar meses e investigar cambios en ingresos, gastos y ahorro, también en términos reales según inflación.
- Proyectar ingresos, cuotas, gastos e inflación; explorar distintos períodos y valores nominales o de hoy.
- Administrar cuentas en ARS y USD, tarjetas, transferencias, ajustes de saldo y patrimonio.
- Registrar pagos parciales y cobros, con tipo de cambio y comprobantes cuando corresponde.
- Configurar idioma, moneda, zona horaria, tema claro/oscuro y preferencias de notificación.
- Usar registro e inicio de sesión con contraseña, Google opcional, sesión protegida y recuperación de contraseña por email.

## Beneficios e impacto que puede aportar

Al concentrar la información, Saver puede reducir olvidos y la carga mental de recordar cada pago; mostrar con anticipación meses más exigentes; ayudar a detectar gastos que crecen por encima de la inflación; y facilitar conversaciones y decisiones sobre consumo, ahorro o ajustes de presupuesto. A medida que la persona registra más movimientos, las proyecciones pueden ayudar a anticipar compromisos y comparar escenarios.

El impacto depende de la constancia y exactitud de los datos ingresados. Saver ofrece organización y estimaciones para apoyar decisiones; no reemplaza el criterio personal ni constituye asesoramiento financiero.

## Estado

Las funciones operativas están enumeradas arriba. El panel de preferencias de notificación ya permite guardar por cuenta qué avisos se desean y si se prefieren por email o navegador; la entrega automática por esos canales todavía está en preparación. El menú puede anticipar capacidades futuras —como cierre de mes, presupuestos, importación bancaria, simulación de inversiones e informes exportables—; no se consideran disponibles hasta contar con reglas, pantallas y servicios operativos.

## Tecnología y estructura

| Carpeta | Contenido |
|---|---|
| [`backend/`](backend/README.md) | API REST — Java 21, Spring Boot, PostgreSQL y Flyway |
| [`frontend/`](frontend/README.md) | SPA — React, TypeScript, Vite, Tailwind CSS y Zustand |
| [`docs/`](docs/README.md) | Documentación de producto, arquitectura y despliegue |
| `run.sh` | Levanta la infraestructura local y el frontend; el backend se corre aparte |

El diseño separa interfaz, API, base de datos y almacenamiento de objetos para permitir elegir más adelante un proveedor de nube según costo, región, límites gratuitos, copias de seguridad y volumen esperado. No se presupone un proveedor definitivo.

## Inicio rápido

Requisitos: JDK 21, Maven 3.9+, Node 20+ y Docker Desktop.

```bash
cp backend/.env.example backend/.env    # solo la primera vez
./run.sh                                # Git Bash: Docker + frontend (el backend, aparte)
```

O por separado:

```bash
cd backend && docker compose up -d && mvn spring-boot:run   # API en http://localhost:8080
cd frontend && npm install && npm run dev                   # App en http://localhost:5173
```

| Servicio | Puerto local |
|---|---|
| Frontend (Vite) | 5173 |
| Backend (Spring Boot) | 8080 |
| PostgreSQL (Docker) | 5433 |
| SeaweedFS, API S3 (Docker) | 8333 |
| Mailpit, emails de desarrollo | 8025 |

Los archivos `.env` contienen configuración local y no deben incluirse en el repositorio; usar los `.env.example` como referencia.
