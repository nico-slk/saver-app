import { api } from '@/lib/api';

export type Category = {
  id: string;
  systemKey: string | null;
  name: string | null;
  color: string;
  icon: string | null;
  system: boolean;
  deleted: boolean;
};

export type CategoryInput = { name: string; color: string };

export const categoriesApi = {
  list: (includeDeleted = false) =>
    api.get<Category[]>('/api/categories', { params: { includeDeleted } }).then((r) => r.data),
  create: (input: CategoryInput) => api.post<Category>('/api/categories', input).then((r) => r.data),
  update: (id: string, input: Partial<CategoryInput>) => api.patch<Category>(`/api/categories/${id}`, input).then((r) => r.data),
  remove: (id: string) => api.delete<void>(`/api/categories/${id}`),
};
