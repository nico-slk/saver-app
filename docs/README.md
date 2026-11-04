# Saver — documentación consolidada

Esta carpeta reúne la documentación de los dos proyectos que forman Saver: `frontend/` (la aplicación que usa la persona) y `backend/` (la API y sus datos).

| Tema | Español | English |
|---|---|---|
| Negocio | [Qué es Saver y qué ofrece hoy](es/negocio.md) | [What Saver is and delivers today](en/business.md) |
| Técnica | [Arquitectura, flujos e integraciones](es/tecnica.md) | [Architecture, flows and integrations](en/technical.md) |
| Operación | [Despliegue: local, GCP y Supabase](es/despliegue.md) | [Deployment: local, GCP and Supabase](en/deployment.md) |

## Estado documentado

La documentación describe el código disponible al momento de su creación. El producto tiene implementados el registro/inicio de sesión, Google Sign-In opcional, sesión segura, perfil, idioma y tema. También están operativos ingresos, gastos (únicos, en cuotas y fijos), categorías, balance mensual, historial con comparación de meses, proyecciones (nominales y en valores de hoy), inflación oficial por moneda (aumentos atados a inflación e historial en términos reales), gráfico del inicio con rango pasado/futuro y recuperación de contraseña por email. Pagos con comprobantes, cierre de mes, inversiones e informes están previstos en el roadmap y aparece visible como próxima función; todavía no cuenta con endpoints ni pantallas operativas.

Los documentos dentro de `frontend/docs` y `backend/docs` siguen siendo referencias más específicas por proyecto; esta carpeta es el punto de entrada transversal.
