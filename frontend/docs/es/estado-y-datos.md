# Estado y datos

[English](../en/state-and-data.md) · [← Volver](../../README.md)

Hay dos tipos de estado y cada uno tiene su herramienta.

| Tipo | Herramienta | Ejemplos |
|---|---|---|
| **Datos del servidor** (se piden a la API, se cachean, se invalidan) | **TanStack Query** | Gastos del mes, historial, categorías, perfil |
| **Estado del cliente** (vive en el navegador) | **Zustand** | Sesión, tema, filtros elegidos, período del simulador |

Regla: **no copiar datos del servidor a Zustand**. Si algo viene de la API, se lee con `useQuery`.

## Zustand

| Store | Contenido | Persistencia |
|---|---|---|
| `authStore` | `status`, `accessToken`, `user`, acciones `bootstrap`, `refresh`, `logout` | Ninguna (en memoria) |
| `uiStore` | `theme` (`system` / `light` / `dark`) | `localStorage` (`saver.ui`) |
| `useProjectionPrefs` | Horizonte y dinero disponible de la proyección | `localStorage` (`saver.projection`) |

Buenas prácticas:

```ts
// ✅ seleccionar solo lo necesario (evita renders de más)
const user = useAuthStore((s) => s.user);

// ❌ suscribirse a todo el store
const { user } = useAuthStore();
```

Las preferencias que deben seguir al usuario entre dispositivos (idioma, moneda, período del simulador) se guardan **en el backend**, no en `localStorage`.

## TanStack Query

Configuración global en `main.tsx`: `retry: 1`, `staleTime: 30s`, sin refetch al enfocar la ventana.

Convención de claves (`queryKey`): `[feature, recurso, ...parámetros]`.

```ts
useQuery({ queryKey: ['expenses', 'month', '2026-10'], queryFn: () => expensesApi.month('2026-10') });

const pay = useMutation({
  mutationFn: paymentsApi.create,
  onSuccess: () => queryClient.invalidateQueries({ queryKey: ['expenses'] }),
});
```

## Clientes de API

Cada feature tiene su `xxxApi.ts` con funciones tipadas que usan la instancia `api` de `lib/api.ts` y devuelven `response.data`. Los tipos de request/response se declaran ahí mismo (más adelante se pueden generar desde el OpenAPI del backend).
