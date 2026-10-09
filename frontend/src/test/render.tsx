import type { ReactElement, ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

/**
 * Pinta un componente con los proveedores que usa la app (React Query para los avatares).
 * Van como `wrapper`, asi `rerender` los conserva.
 */
export function renderWithProviders(ui: ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  )
  return { user: userEvent.setup(), ...render(ui, { wrapper }) }
}
