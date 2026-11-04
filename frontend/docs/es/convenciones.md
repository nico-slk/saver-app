# Convenciones

[English](../en/conventions.md) · [← Volver](../../README.md)

## Código

- Componentes funcionales con nombre (`export function ExpenseList()`), un componente principal por archivo.
- Archivos de componentes en `PascalCase.tsx`; utilidades y stores en `camelCase.ts`.
- Importar con el alias `@/` en lugar de rutas relativas largas.
- `import type` para tipos (`verbatimModuleSyntax` está activo).
- Nada de textos fijos en la UI: todo pasa por `t('…')`.
- Montos y fechas siempre con `lib/format.ts`.
- Datos del servidor con TanStack Query; Zustand solo para estado del cliente.
- Comentarios en español; nombres de código en inglés.

## Tests

- Vitest. Archivos `*.test.ts(x)` junto al código que prueban.
- Priorizar lógica pura: cálculos, formateos, manejo de errores, stores.

## Ramas y commits

- `main` siempre desplegable. Ramas: `feat/…`, `fix/…`, `docs/…`, `chore/…`.
- [Conventional Commits](https://www.conventionalcommits.org/es/): `feat(expenses): lista de vencimientos`, `fix(auth): …`.
- Antes de abrir un PR: `npm run build` y `npm test` sin errores.

## Versionado

[SemVer](https://semver.org/lang/es/). La versión está en `package.json`.
