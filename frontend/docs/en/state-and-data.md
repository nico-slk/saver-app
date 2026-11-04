# State and data

[Español](../es/estado-y-datos.md) · [← Back](../../README.en.md)

There are two kinds of state, each with its own tool.

| Kind | Tool | Examples |
|---|---|---|
| **Server data** (fetched from the API, cached, invalidated) | **TanStack Query** | Month expenses, history, categories, profile |
| **Client state** (lives in the browser) | **Zustand** | Session, theme, selected filters, simulator period |

Rule: **do not copy server data into Zustand**. If it comes from the API, read it with `useQuery`.

## Zustand

| Store | Content | Persistence |
|---|---|---|
| `authStore` | `status`, `accessToken`, `user`, actions `bootstrap`, `refresh`, `logout` | None (memory) |
| `uiStore` | `theme` (`system` / `light` / `dark`) | `localStorage` (`saver.ui`) |
| `useProjectionPrefs` | Projection horizon and money available | `localStorage` (`saver.projection`) |

Good practices:

```ts
// ✅ select only what you need (avoids extra renders)
const user = useAuthStore((s) => s.user);

// ❌ subscribe to the whole store
const { user } = useAuthStore();
```

Preferences that must follow the user across devices (language, currency, simulator period) are stored **in the backend**, not in `localStorage`.

## TanStack Query

Global config in `main.tsx`: `retry: 1`, `staleTime: 30s`, no refetch on window focus.

Query key convention: `[feature, resource, ...params]`.

```ts
useQuery({ queryKey: ['expenses', 'month', '2026-10'], queryFn: () => expensesApi.month('2026-10') });

const pay = useMutation({
  mutationFn: paymentsApi.create,
  onSuccess: () => queryClient.invalidateQueries({ queryKey: ['expenses'] }),
});
```

## API clients

Each feature has its own `xxxApi.ts` with typed functions that use the `api` instance from `lib/api.ts` and return `response.data`. Request/response types are declared there (they may later be generated from the backend OpenAPI spec).
