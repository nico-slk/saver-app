# Convenciones

[English](../en/conventions.md) · [← Volver](../../README.md)

## Código

- Paquetes por funcionalidad (`com.saver.<feature>`), no por capa.
- DTOs como `record`, agrupados en `<Feature>Dtos`. Nunca exponer entidades JPA en la API.
- Inyección por constructor. Sin Lombok.
- Errores de negocio: `throw new ApiException(HttpStatus.X, "feature.reason")`. El `code` es la clave i18n del frontend.
- Validación con Jakarta Validation; el `message` de la anotación es un `code` (ej. `validation.required`).
- Montos siempre `BigDecimal`. Fechas con `Instant`/`LocalDate`; usar el `Clock` inyectado en lugar de `Instant.now()` en la lógica de negocio.
- Todo acceso a datos filtra por el `userId` de `@CurrentUser`.
- Comentarios y Javadoc en español; nombres de código en inglés.

## Tests

- Unitarios con JUnit 5 + AssertJ para servicios y cálculos (cuotas, proyecciones, interés compuesto).
- Integración con `@SpringBootTest` + Testcontainers (se sumará en la etapa 2).
- Nombre: `<Clase>Test`.

## Ramas y commits

- `main` siempre desplegable. Ramas: `feat/…`, `fix/…`, `docs/…`, `chore/…`.
- Commits con [Conventional Commits](https://www.conventionalcommits.org/es/):
  `feat(expense): soporte de gastos en cuotas`, `fix(auth): …`, `docs: …`.
- Pull request con descripción, cómo probarlo y checklist de migraciones.

## Versionado

[SemVer](https://semver.org/lang/es/). La versión está en `pom.xml`.
