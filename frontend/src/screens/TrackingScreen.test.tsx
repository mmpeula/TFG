import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { currentPath, renderApp } from '@/test/app'
import { server } from '@/test/server'

const SEGUIMIENTO = '/workspaces/100/seguimiento'

describe('TrackingScreen', () => {
  it('ensena las cifras del tablero y la evolucion semanal', async () => {
    renderApp(SEGUIMIENTO, { as: 'lucia' })
    expect(await screen.findByText('Velocidad')).toBeInTheDocument()
    expect(screen.getByText('Tiempo medio')).toBeInTheDocument()
    expect(screen.getByText('Pendientes')).toBeInTheDocument()
    expect(screen.getByText('Evolución temporal')).toBeInTheDocument()
    expect(screen.getByText('S10')).toBeInTheDocument()
  })

  it('cambia las semanas de la grafica', async () => {
    const { user } = renderApp(SEGUIMIENTO, { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: /Últimas 10 semanas/ }))
    await user.click(screen.getByRole('button', { name: 'Últimas 4 semanas' }))
    expect(screen.getByRole('button', { name: /Últimas 4 semanas/ })).toBeInTheDocument()
    expect(screen.queryByText('S10')).not.toBeInTheDocument()
  })

  it('los insights explican como se calculan', async () => {
    const { user } = renderApp(SEGUIMIENTO, { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: 'Cómo se calculan los insights' }))
    expect(screen.getByText(/no los inventa la IA/)).toBeInTheDocument()
  })

  it('el insight de avisos lista las tareas y lleva al tablero filtrado', async () => {
    const { user } = renderApp(SEGUIMIENTO, { as: 'lucia' })
    expect(await screen.findByText('Hay 3 tareas con aviso')).toBeInTheDocument()
    expect(screen.getByText('Tareas afectadas')).toBeInTheDocument()
    expect(screen.getByText('9 días sin avance')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Verlas en el tablero/ }))
    await waitFor(() => expect(currentPath()).toBe('/workspaces/100/tablero'))
    expect(await screen.findByText('Leer el enunciado')).toBeInTheDocument()
    expect(screen.queryByText('Memoria final')).not.toBeInTheDocument()
  })

  it('el insight de entrega resume plazo y ritmo', async () => {
    renderApp(SEGUIMIENTO, { as: 'lucia' })
    const resumen = (await screen.findAllByText('Resumen'))[0].closest('aside')!
    expect(within(resumen).getByText(/Entrega en/)).toBeInTheDocument()
  })

  it('sin tareas no hay nada que medir', async () => {
    server.db.tasks = []
    renderApp(SEGUIMIENTO, { as: 'lucia' })
    expect(await screen.findByText('Todavía no hay datos que medir')).toBeInTheDocument()
  })
})
