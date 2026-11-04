# Conventions

[Español](../es/convenciones.md) · [← Back](../../README.en.md)

## Code

- Named function components (`export function ExpenseList()`), one main component per file.
- Component files in `PascalCase.tsx`; utilities and stores in `camelCase.ts`.
- Import with the `@/` alias instead of long relative paths.
- `import type` for types (`verbatimModuleSyntax` is on).
- No hard-coded UI text: everything goes through `t('…')`.
- Amounts and dates always via `lib/format.ts`.
- Server data with TanStack Query; Zustand only for client state.
- Comments in Spanish; code identifiers in English.

## Tests

- Vitest. `*.test.ts(x)` files next to the code they test.
- Prioritize pure logic: calculations, formatting, error handling, stores.

## Branches and commits

- `main` is always deployable. Branches: `feat/…`, `fix/…`, `docs/…`, `chore/…`.
- [Conventional Commits](https://www.conventionalcommits.org/): `feat(expenses): due-date list`, `fix(auth): …`.
- Before opening a PR: `npm run build` and `npm test` pass.

## Versioning

[SemVer](https://semver.org/). The version lives in `package.json`.
