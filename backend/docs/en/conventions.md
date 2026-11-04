# Conventions

[Español](../es/convenciones.md) · [← Back](../../README.en.md)

## Code

- Feature packages (`com.saver.<feature>`), not layer packages.
- DTOs as `record`s, grouped in `<Feature>Dtos`. Never expose JPA entities through the API.
- Constructor injection. No Lombok.
- Business errors: `throw new ApiException(HttpStatus.X, "feature.reason")`. The `code` is the frontend i18n key.
- Validation with Jakarta Validation; the annotation `message` is a `code` (e.g. `validation.required`).
- Amounts are always `BigDecimal`. Dates with `Instant`/`LocalDate`; use the injected `Clock` instead of `Instant.now()` in business logic.
- All data access filters by the `userId` from `@CurrentUser`.
- Comments and Javadoc in Spanish; code identifiers in English.

## Tests

- Unit tests with JUnit 5 + AssertJ for services and calculations (installments, projections, compound interest).
- Integration tests with `@SpringBootTest` + Testcontainers (added in stage 2).
- Naming: `<Class>Test`.

## Branches and commits

- `main` is always deployable. Branches: `feat/…`, `fix/…`, `docs/…`, `chore/…`.
- [Conventional Commits](https://www.conventionalcommits.org/):
  `feat(expense): installment expenses support`, `fix(auth): …`, `docs: …`.
- Pull requests include a description, how to test it and a migrations checklist.

## Versioning

[SemVer](https://semver.org/). The version lives in `pom.xml`.
