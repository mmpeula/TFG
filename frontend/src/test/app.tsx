import { MemoryRouter } from 'react-router-dom'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '@/App'
import { AppProviders } from '@/app/providers'
import { setStoredToken } from '@/lib/api-client'
import { server } from './server'
import { Where } from './Where'

export type Who = 'lucia' | 'pablo' | 'elena' | 'carmen' | 'admin'

/**
 * La aplicacion entera, como en main.tsx, en la ruta pedida y con la sesion de
 * quien se diga (Lucia es la responsable del espacio 100; Pablo y Elena, alumnos;
 * Carmen, la docente). Sin `as`, sin sesion.
 */
export function renderApp(path: string | { pathname: string; state: unknown }, { as }: { as?: Who } = {}) {
  if (as) setStoredToken(`tok-${server.db.users[as].id}`)
  return {
    user: userEvent.setup(),
    ...render(
      <MemoryRouter initialEntries={[path]}>
        <AppProviders>
          <App />
          <Where />
        </AppProviders>
      </MemoryRouter>,
    ),
  }
}

export const currentPath = () => screen.getByTestId('ruta').textContent
