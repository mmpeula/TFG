import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { currentPath, renderApp } from '@/test/app'

describe('App: rutas y sesion', () => {
  it('sin sesion manda al login', async () => {
    renderApp('/workspaces')
    expect(await screen.findByRole('heading', { name: 'Acceder' })).toBeInTheDocument()
    expect(currentPath()).toBe('/login')
  })

  it('el alumno entra en sus espacios de trabajo', async () => {
    renderApp('/', { as: 'lucia' })
    expect(await screen.findByRole('heading', { name: 'Mis espacios de trabajo' })).toBeInTheDocument()
    expect(await screen.findByText('Algorítmica, práctica 3')).toBeInTheDocument()
  })

  it('/workspaces/:id lleva al plan', async () => {
    renderApp('/workspaces/100', { as: 'lucia' })
    await waitFor(() => expect(currentPath()).toBe('/workspaces/100/plan'))
  })

  it('el administrador no puede entrar en los espacios de trabajo', async () => {
    renderApp('/workspaces', { as: 'admin' })
    await waitFor(() => expect(currentPath()).toBe('/administracion'))
  })

  it('una ruta desconocida vuelve a los espacios de trabajo', async () => {
    renderApp('/no-existe', { as: 'pablo' })
    await waitFor(() => expect(currentPath()).toBe('/workspaces'))
  })
})
