import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { currentPath, renderApp } from '@/test/app'
import { setMobile } from '@/test/setup'
import { server } from '@/test/server'

const PLAN = '/workspaces/100/plan'
const sprint = (n: number) => screen.findByRole('region', { name: `Sprint ${n}` })

describe('PlanScreen', () => {
  it('reparte las tareas propias en sprints y marca el actual', async () => {
    renderApp(PLAN, { as: 'lucia' })
    const primero = await sprint(1)
    expect(within(primero).getByText('ahora')).toBeInTheDocument()
    expect(within(primero).getByText('Leer el enunciado')).toBeInTheDocument()
    expect(within(primero).getByText('Por hacer')).toBeInTheDocument()
    expect(screen.getByText(/8 semanas en 4 sprints/)).toBeInTheDocument()
    // «Mis tareas»: la de Pablo no sale.
    expect(screen.queryByText('Implementar el algoritmo voraz')).not.toBeInTheDocument()
    expect(within(await sprint(3)).getByText('Sin tareas en estas fechas.')).toBeInTheDocument()
  })

  it('la responsable cambia a «Todo el equipo» y lo recuerda', async () => {
    const { user } = renderApp(PLAN, { as: 'lucia' })
    await user.click(await screen.findByRole('tab', { name: 'Todo el equipo' }))

    const resumen = screen.getByRole('region', { name: 'Cómo va el equipo' })
    expect(within(resumen).getByText('Sprint 1, en curso')).toBeInTheDocument()
    expect(within(resumen).getByText('1/2')).toBeInTheDocument()
    expect(within(resumen).getByText('quedan 4 días · hasta 11 oct 2026')).toBeInTheDocument()
    expect(within(resumen).getByText('Van tarde')).toBeInTheDocument()
    expect(within(resumen).getByText('Pablo Ortega')).toBeInTheDocument()
    expect(within(resumen).getByText('Sin asignar')).toBeInTheDocument()
    expect(localStorage.getItem('planifia.planScope')).toBe('team')
  })

  it('un alumno solo ve lo suyo y no puede cambiarlo', async () => {
    renderApp(PLAN, { as: 'pablo' })
    expect(within(await sprint(2)).getByText('Implementar el algoritmo voraz')).toBeInTheDocument()
    expect(screen.queryByRole('tab', { name: 'Todo el equipo' })).not.toBeInTheDocument()
  })

  it('sin tareas propias lo dice; la responsable puede pasar a ver al equipo', async () => {
    server.db.tasks = server.db.tasks.filter((task) => task.id !== 501)
    const { user } = renderApp(PLAN, { as: 'lucia' })
    expect(await screen.findByText('No tienes tareas asignadas aquí')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Ver todo el equipo' }))
    expect(screen.getByRole('region', { name: 'Cómo va el equipo' })).toBeInTheDocument()
  })

  it('un alumno sin tareas espera a que se las asignen', async () => {
    server.db.tasks = server.db.tasks.filter((task) => task.id !== 502)
    renderApp(PLAN, { as: 'pablo' })
    expect(await screen.findByText('Cuando te asignen alguna tarea, aparecerá aquí en su sprint.')).toBeInTheDocument()
  })

  it('las tareas sin fecha quedan aparte', async () => {
    server.db.tasks[0].dueDate = undefined
    renderApp(PLAN, { as: 'lucia' })
    const sinFecha = await screen.findByRole('region', { name: 'Tareas sin fecha' })
    expect(within(sinFecha).getByText('Leer el enunciado')).toBeInTheDocument()
  })

  it('sin fecha de inicio ni duracion lo explica y lleva a los ajustes', async () => {
    server.db.workspace.startDate = undefined
    server.db.workspace.sprintDays = undefined
    const { user } = renderApp(PLAN, { as: 'lucia' })
    expect(await screen.findByText(/El inicio sale de la primera tarea/)).toBeInTheDocument()
    expect(screen.getByText(/Sprints de 2 semanas, por defecto/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Cambiar la duración' }))
    await waitFor(() => expect(currentPath()).toBe('/workspaces/100/ajustes'))
  })

  it('«Fijar la fecha de inicio» tambien lleva a los ajustes', async () => {
    server.db.workspace.startDate = undefined
    const { user } = renderApp(PLAN, { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: 'Fijar la fecha de inicio' }))
    await waitFor(() => expect(currentPath()).toBe('/workspaces/100/ajustes'))
  })

  it('sin tareas invita a ir al tablero', async () => {
    server.db.tasks = []
    const { user } = renderApp(PLAN, { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: 'Ir al tablero' }))
    await waitFor(() => expect(currentPath()).toBe('/workspaces/100/tablero'))
  })

  it('la docente ve el panel de progreso del equipo', async () => {
    renderApp(PLAN, { as: 'carmen' })
    expect(await screen.findByText('Velocidad')).toBeInTheDocument()
    // El docente no ve el tiempo medio por tarea.
    expect(screen.queryByText('Tiempo medio')).not.toBeInTheDocument()
    expect(screen.queryByRole('tab', { name: 'Todo el equipo' })).not.toBeInTheDocument()
  })

  it('en el movil se ve un sprint y se cambia con las pestañas', async () => {
    setMobile(true)
    server.db.tasks[1].assignees = server.db.tasks[0].assignees
    const { user } = renderApp(PLAN, { as: 'lucia' })
    expect(await sprint(1)).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Sprint 2' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('tab', { name: 'Sprint 2' }))
    expect(within(await sprint(2)).getByText('Implementar el algoritmo voraz')).toBeInTheDocument()
  })

  it('abre el panel de una tarea y la borra desde su tarjeta', async () => {
    const { user } = renderApp(PLAN, { as: 'lucia' })
    await user.click(await screen.findByRole('button', { name: 'Leer el enunciado' }))
    expect(await screen.findByRole('dialog', { name: 'Leer el enunciado' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Cerrar el panel' }))

    await user.click(screen.getByRole('button', { name: 'Acciones de Leer el enunciado' }))
    await user.click(screen.getByRole('button', { name: 'Borrar' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Borrar' }))
    await waitFor(() => expect(server.callsTo('delete', '/tasks/501')).toHaveLength(1))
  })
})
